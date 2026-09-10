package app.pocketful.data

import app.pocketful.AppVersion
import app.pocketful.domain.TcgGame
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.doubleOrNull

/**
 * The card catalog, live.
 *
 * TCGdex was picked over the more commonly used pokemontcg.io because it needs no API
 * key, serves art off a CDN at three sizes, and carries TCGplayer market prices in the
 * same document as the card -- so filing a card and pricing it is one request rather
 * than two services to keep in step.
 *
 * Nothing here touches the collection. This layer only knows how to answer "what cards
 * exist and what are they worth"; turning an answer into catalog rows is [CardImport]'s
 * job, and turning it into something you own is the store's.
 */
class TcgDex(
    private val client: HttpClient = defaultClient(),
    private val language: String = "en",
) {
    /**
     * The published catalog, once the launch has it.
     *
     * Held here rather than handed to every caller because this class already *is* the
     * question "what cards exist". Every method below answers from this when it can and
     * falls through to the network when it cannot, so CatalogBrowser, CardLookup and
     * CatalogSync did not have to change at all -- and an install with no catalog yet is
     * exactly the app that existed before it, rather than a broken one.
     *
     * Only the immutable half is here. Prices are not in this document and never will be,
     * so [card] still goes to the network: it is the one call that has to.
     */
    private var published: PublishedCatalog? = null

    /** Whether answers are coming off the device rather than the wire. */
    val hasPublishedCatalog: Boolean get() = published != null

    fun usePublished(catalog: PublishedCatalog?) {
        published = catalog?.takeIf { it.isUsable }
    }

    /**
     * The published record for one card, if the catalog is here and knows it.
     *
     * Offered so the sync can find the fallback artwork for a card already in someone's
     * collection. The live [card] document cannot answer that -- TCGdex does not know
     * that a card it has no picture of has one on TCGplayer.
     */
    fun publishedCard(id: String): PublishedCard? = published?.card(id)

    /** The published set index, or null if the catalog is not here. Never touches the network. */
    fun publishedSetIndex(): Map<String, RemoteSet>? = published?.setIndex()

    /** 218 sets, ~35KB, and it changes a few times a year. Fetched once per process. */
    private var setIndex: Map<String, RemoteSet>? = null

    /** A dozen entries that change once a year. Same deal. */
    private var seriesIndex: List<RemoteSeries>? = null

    /** Sets and series together, each set carrying the era and date it is filed under. */
    private var catalogIndex: CatalogIndex? = null

    /** Full set documents, kept as they are opened rather than fetched up front. */
    private val setDetails = mutableMapOf<String, RemoteSetDetail>()

    /** Press runs per card, per set. Only ever filled with a complete answer. */
    private val setVariants = mutableMapOf<String, Map<String, RemoteVariants>>()

    /**
     * Cards matching a name fragment.
     *
     * The list endpoint returns a deliberately thin row -- id, name, and sometimes art --
     * so a search is one request no matter how many hits it has. The set name each row
     * displays comes from the cached set index rather than from N follow-up requests.
     */
    suspend fun search(query: String, limit: Int = 30): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        // Answered on the device when the catalog is here, which is both faster and
        // honest: the old path returned nothing at all when the network was down, for a
        // question the app had every card needed to answer.
        published?.let { return it.search(trimmed, limit) }

        val response = client.get("$BASE/$language/cards") {
            parameter("name", trimmed)
            parameter("pagination:page", 1)
            parameter("pagination:itemsPerPage", limit)
        }
        if (!response.status.isSuccess()) throw CatalogUnavailable(response.status.value)
        val rows: List<RemoteCardBrief> = response.body()

        val sets = runCatching { sets() }.getOrDefault(emptyMap())
        return rows.map { row ->
            val setId = row.id.substringBeforeLast('-', missingDelimiterValue = "")
            val set = sets[setId]
            SearchHit(
                id = row.id,
                name = row.name,
                number = row.localId ?: row.id.substringAfterLast('-'),
                setId = setId,
                setName = set?.name ?: setId,
                setTotal = set?.cardCount?.printed?.toString(),
                artStem = row.image,
                // The name endpoint answers across the whole catalog and cannot be asked
                // for one game, so a search for "pikachu" comes back holding both. Which
                // game each hit belongs to is settled here, from the index, rather than
                // left to the screen to guess at from a set name.
                game = gameOfSeries(set?.serie?.id),
            )
        }
    }

    /** Everything known about one card, including its prices. */
    suspend fun card(id: String): RemoteCard? {
        val response = client.get("$BASE/$language/cards/$id")
        if (!response.status.isSuccess()) return null
        return runCatching { response.body<RemoteCard>() }.getOrNull()
    }

    /** Every card in one set, as picker rows. One request, however big the set. */
    suspend fun cardsInSet(setId: String): List<SearchHit> {
        published?.cardsInSet(setId)?.let { return it }
        val detail = setDetail(setId) ?: return emptyList()
        val total = detail.cardCount?.printed?.toString()
        val game = detail.game
        return detail.cards.map { row ->
            SearchHit(
                id = row.id,
                name = row.name,
                number = row.localId ?: row.id.substringAfterLast('-'),
                setId = detail.id,
                setName = detail.name,
                setTotal = total,
                artStem = row.image,
                game = game,
            )
        }
    }

    /**
     * Which press runs each card in a set was printed in, keyed by card id.
     *
     * This is the one question the list endpoints cannot answer. A set document carries
     * only a thin row per card -- id, name, number, art -- and the `variants` flags saying
     * whether a card exists as a holo or a reverse live on the full card document, which
     * is one request each. For a 250-card set that is 250 requests, and the whole point of
     * a master-set binder is that it is one button.
     *
     * GraphQL takes them in batches instead: an aliased `card(id:)` per card in a single
     * POST, a few dozen at a time so no one request is enormous and the batches overlap on
     * the wire. A 200-card set lands in about six seconds and ~25KB, and is then held for
     * the life of the process like every other index here.
     *
     * A batch that fails is dropped rather than failing the set, so a flaky connection
     * costs a card its holo pocket instead of costing the whole binder. Nothing is cached
     * unless every card answered, so the next attempt is a real retry rather than a replay
     * of a bad afternoon.
     */
    suspend fun variantsInSet(setId: String): Map<String, RemoteVariants> {
        // The whole reason the published catalog carries `variants`. This used to be a few
        // dozen batched GraphQL requests and about six seconds per set, repeated every
        // process because the answer only ever lived in memory. It is a printed fact that
        // has not changed in years; now it is a map lookup.
        published?.variantsInSet(setId)?.let { return it }
        setVariants[setId]?.let { return it }

        // Catalog-issued ids only. These are interpolated into a query string unescaped,
        // and the one thing that must never be true is that a card id can close a literal.
        val ids = cardsInSet(setId).map { it.id }
            .filter { id -> id.isNotEmpty() && id.all { it.isLetterOrDigit() || it in "-._" } }
        if (ids.isEmpty()) return emptyMap()

        val fetched = coroutineScope {
            ids.chunked(VARIANT_BATCH).map { chunk -> async { variantBatch(chunk) } }.awaitAll()
        }.fold(emptyMap<String, RemoteVariants>()) { all, batch -> all + batch }

        if (fetched.size == ids.size) setVariants[setId] = fetched
        return fetched
    }

    /** One POST, one aliased `card` query per id. Empty if the batch could not be read. */
    private suspend fun variantBatch(ids: List<String>): Map<String, RemoteVariants> =
        runCatching {
            val query = ids.mapIndexed { index, id ->
                "c$index: card(id: \"$id\") { id variants { normal holo reverse firstEdition wPromo } }"
            }.joinToString(separator = " ", prefix = "{ ", postfix = " }")

            val response = client.post("$BASE/graphql") {
                contentType(ContentType.Application.Json)
                setBody(GraphQlRequest(query))
            }
            if (!response.status.isSuccess()) return emptyMap()

            response.body<GraphQlVariantsEnvelope>().data.orEmpty().values
                .filterNotNull()
                .mapNotNull { row -> row.variants?.let { row.id to it } }
                .toMap()
        }.getOrDefault(emptyMap())

    /**
     * The set index, fetched at most once and then held for the life of the process.
     *
     * Asks for the whole catalog index rather than the bare `/sets` list, because that
     * one omits the era each set belongs to -- and the era is the only thing separating a
     * printed set from a Pokémon TCG Pocket one. [catalogIndex] fills this field itself
     * when it succeeds, so the common path is one request either way; the bare list is
     * kept underneath it as the answer for a catalog that would not give up the rest.
     */
    suspend fun sets(): Map<String, RemoteSet> {
        setIndex?.let { return it }
        runCatching { catalogIndex() }
        setIndex?.let { return it }
        val setsResponse = client.get("$BASE/$language/sets")
        if (!setsResponse.status.isSuccess()) throw CatalogUnavailable(setsResponse.status.value)
        val fetched: List<RemoteSet> = setsResponse.body()
        return fetched.associateBy { it.id }.also { setIndex = it }
    }

    /**
     * One set in full, including the list of cards in it.
     *
     * Cached per set for the life of the process. Browsing a series means opening four or
     * five sets and going back, and re-downloading a 250-card list to answer "what was in
     * that one again" is the difference between browsing and waiting.
     */
    suspend fun setDetail(id: String): RemoteSetDetail? {
        setDetails[id]?.let { return it }
        val response = client.get("$BASE/$language/sets/$id")
        if (!response.status.isSuccess()) return null
        return runCatching { response.body<RemoteSetDetail>() }.getOrNull()
            ?.also { setDetails[id] = it }
    }

    /**
     * The series index: the eras a set belongs to -- Base, EX, Sword & Shield, Scarlet &
     * Violet. Cheap, static, and the only thing that makes 218 sets navigable rather than
     * a wall to scroll.
     */
    suspend fun series(): List<RemoteSeries> {
        seriesIndex?.let { return it }
        val seriesResponse = client.get("$BASE/$language/series")
        if (!seriesResponse.status.isSuccess()) throw CatalogUnavailable(seriesResponse.status.value)
        val fetched: List<RemoteSeries> = seriesResponse.body()
        return fetched.also { seriesIndex = it }
    }

    /** The sets making up one series, newest listing order as upstream returns them. */
    suspend fun setsInSeries(id: String): List<RemoteSet> {
        val response = client.get("$BASE/$language/series/$id")
        if (!response.status.isSuccess()) return emptyList()
        return runCatching { response.body<RemoteSeriesDetail>().sets }.getOrDefault(emptyList())
    }

    /**
     * The whole browsable catalog -- every series, every set -- in one request.
     *
     * This exists because the REST index cannot answer the two questions the browse
     * screen is built on. `/sets` returns neither a release date nor which era a set
     * belongs to, so ordering by age was impossible and grouping by era cost one request
     * per series. The GraphQL endpoint returns both fields on every set, so 218 sets
     * arrive grouped and dated in ~55KB and every sort the screen offers is then a local
     * comparison rather than a round trip.
     *
     * Held for the life of the process like the indexes it supersedes -- it is the same
     * data, and it changes when a set is released, not while the app is open.
     */
    suspend fun catalogIndex(): CatalogIndex {
        catalogIndex?.let { return it }
        published?.let { local ->
            val index = local.index()
            catalogIndex = index
            setIndex = index.sets.associateBy { it.id }
            seriesIndex = index.series
            return index
        }
        val fetched = graphCatalogIndex()?.takeIf { it.sets.isNotEmpty() } ?: restCatalogIndex()
        if (fetched.sets.isNotEmpty()) {
            catalogIndex = fetched
            // The card search names a set from this map, so filling it here means the
            // first search after a browse does not re-fetch an index we already hold.
            setIndex = fetched.sets.associateBy { it.id }
            seriesIndex = fetched.series
        }
        return fetched
    }

    /** One POST, both indexes, dates and eras included. Null if the endpoint is unusable. */
    private suspend fun graphCatalogIndex(): CatalogIndex? {
        // The GraphQL schema is not language-parameterised the way the REST paths are, and
        // it answers in English. Asking it for a French catalog and filing the English
        // names under it would be worse than the extra requests the fallback costs.
        if (language != "en") return null
        return runCatching {
            val response = client.post("$BASE/graphql") {
                contentType(ContentType.Application.Json)
                setBody(GraphQlRequest(CATALOG_INDEX_QUERY))
            }
            if (!response.status.isSuccess()) return null
            response.body<GraphQlEnvelope>().data
        }.getOrNull()
    }

    /**
     * The same shape, assembled from REST, for when GraphQL cannot be reached.
     *
     * Costs one request per series and comes back without release dates, so the two
     * chronological sorts degrade to alphabetical. That is a worse browse screen than the
     * one above and a much better one than an empty tab, which is what a single-path
     * implementation would leave behind the first time the schema moved.
     */
    private suspend fun restCatalogIndex(): CatalogIndex = coroutineScope {
        val allSeries = runCatching { series() }.getOrDefault(emptyList())
        val members = allSeries
            .map { entry -> async { entry to runCatching { setsInSeries(entry.id) }.getOrDefault(emptyList()) } }
            .awaitAll()
        CatalogIndex(
            series = allSeries,
            // Stamped with the era they were fetched under, because that membership is the
            // whole reason for the round trip and the set rows do not carry it themselves.
            sets = members.flatMap { (entry, sets) ->
                sets.map { it.copy(serie = RemoteSeriesRef(entry.id, entry.name)) }
            },
        )
    }

    companion object {
        private const val BASE = "https://api.tcgdex.net/v2"

        /**
         * How this app introduces itself to every host it talks to.
         *
         * Built from [AppVersion] rather than hard-coded so a bumped release says so
         * without anyone remembering to edit a second place.
         */
        val USER_AGENT: String =
            "Pocketful/${AppVersion.NAME} (+https://github.com/TronVonDoom/Pocketful)"

        /**
         * How many cards go into one batched variants query.
         *
         * Small enough that a dropped batch costs a handful of pockets rather than a
         * quarter of the binder, large enough that a big set is five or six overlapping
         * requests rather than fifty.
         */
        private const val VARIANT_BATCH = 40

        /**
         * Everything the browse screen needs and nothing else.
         *
         * Card lists are deliberately absent: they are two orders of magnitude bigger than
         * the rest of this document put together, and a set's contents are only wanted
         * once someone has opened that one set.
         */
        private const val CATALOG_INDEX_QUERY = """
            {
              series { id name logo }
              sets {
                id
                name
                logo
                symbol
                releaseDate
                cardCount { official total }
                serie { id name }
              }
            }
        """

        /**
         * `ignoreUnknownKeys` is not laziness here: the payload carries attacks,
         * legalities and per-market price histories this app has no use for, and the
         * schema grows whenever a new mechanic ships. Declaring only what is read means
         * a new field upstream is not a crash on device.
         */
        fun defaultClient(): HttpClient = HttpClient {
            /*
             * Who is calling, and where to complain.
             *
             * TCGdex is a free service with no API key, which means the User-Agent is the
             * only thing that identifies this app in their logs. Without it every install
             * is an anonymous Ktor client indistinguishable from a scraper, and the only
             * remedy available to them is an IP block that catches innocent traffic too.
             * With it they can see how much of their load is Pocketful, and reach the
             * person responsible before resorting to that.
             *
             * The version is included because "which release is doing this" is the first
             * question anyone looking at a traffic spike asks, and the URL because a
             * user agent nobody can act on is decoration.
             */
            install(UserAgent) { agent = USER_AGENT }

            /*
             * Bounded waiting, stated rather than inherited.
             *
             * Without this the app waits on whatever the platform engine happens to
             * default to, which differs per engine and is not written down anywhere the
             * reader of this file can see. The case that matters is not a host that
             * refuses a connection -- that fails instantly -- but one that accepts it and
             * then says nothing, which is exactly how an overloaded API behaves and how
             * a captive portal behaves all the time. Someone watching "Searching the full
             * catalog..." deserves to be told it failed within a sensible span.
             *
             * Kept under AppBootstrap's own launch budget so a cold start is still capped
             * by the thing that is supposed to cap it.
             */
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 12_000
                socketTimeoutMillis = 12_000
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        explicitNulls = false
                        coerceInputValues = true
                    },
                )
            }
        }
    }
}

/**
 * The catalog answered, but not with an answer.
 *
 * Exists so a refused request stops being mistaken for an unreadable one. Every endpoint
 * here decodes straight into a data class, so an unchecked non-2xx meant handing an error
 * page to the JSON parser and reporting whatever it said about the shape -- which reads
 * as "this app cannot understand the catalog" when the truth was "the catalog is down".
 * Those two want opposite reactions from whoever sees them: one is worth waiting out, the
 * other means the app needs fixing.
 */
class CatalogUnavailable(val status: Int) : Exception("The card catalog returned $status.")

/** A row in the search picker: enough to identify a card, not enough to file one. */
data class SearchHit(
    val id: String,
    val name: String,
    val number: String,
    val setId: String,
    val setName: String,
    val setTotal: String?,
    val artStem: String?,
    /**
     * A finished image URL, for cards TCGdex has no artwork for at all.
     *
     * About 7% of the catalog has no TCGdex asset in any language -- whole Trainer Kits,
     * Shining Fates' Shiny Vault, Ancient Mew -- and the published catalog resolves what
     * it can from a second source. Those arrive as complete URLs rather than as a stem,
     * because they are somebody else's CDN and do not take a quality suffix.
     *
     * Carried beside [artStem] rather than replacing it so that a card which later gains
     * real TCGdex art uses it without anything having to notice.
     */
    val artUrl: String? = null,
    /**
     * Which game the card is from, resolved where the set was.
     *
     * Carried on the row rather than looked up again by whoever draws it: a hit outlives
     * the index lookup that produced it, and a screen re-deriving this from a set name
     * would be a second answer to a question already settled.
     */
    val game: TcgGame = TcgGame.POKEMON,
) {
    val collectorNumber: String get() = setTotal?.let { "$number/$it" } ?: number
}

// ------------------------------------------------------------------------ wire

@Serializable
data class RemoteCardBrief(
    val id: String,
    val name: String,
    val localId: String? = null,
    val image: String? = null,
)

@Serializable
data class RemoteSet(
    val id: String,
    val name: String,
    val logo: String? = null,
    val symbol: String? = null,
    val cardCount: RemoteCardCount? = null,
    /**
     * ISO `yyyy-MM-dd`, from the GraphQL index only -- the REST set list omits it.
     *
     * Left as the string it arrives as rather than parsed into a date. Every use is a
     * comparison or a year, and ISO dates already sort chronologically as text, so
     * parsing would buy nothing and cost the app a date library on every platform.
     */
    val releaseDate: String? = null,
    /** Which era the set belongs to. Absent on the REST index; stamped on by the fallback. */
    val serie: RemoteSeriesRef? = null,
) {
    /** What the set index can say about size before the full document is fetched. */
    val officialCount: Int? get() = cardCount?.printed

    /** The four digits a tile has room for. */
    val releaseYear: String? get() = releaseDate?.take(4)?.takeIf { it.length == 4 }
}

/**
 * The catalog's shape: which eras exist, and which sets sit in each.
 *
 * Sets are held flat rather than nested inside their series because the browse screen
 * regroups and reorders them on every sort change, and a nested structure would have to
 * be taken apart before each one.
 */
@Serializable
data class CatalogIndex(
    val series: List<RemoteSeries> = emptyList(),
    val sets: List<RemoteSet> = emptyList(),
)

@Serializable
private data class GraphQlRequest(val query: String)

/** One row of a batched variants query. Only the flags are asked for, so only they arrive. */
@Serializable
private data class VariantRow(val id: String, val variants: RemoteVariants? = null)

/** An aliased batch answers as an object keyed by alias, so `data` is a map, not a list. */
@Serializable
private data class GraphQlVariantsEnvelope(val data: Map<String, VariantRow?>? = null)

/** GraphQL answers 200 with the errors inside the body; a missing `data` is the failure. */
@Serializable
private data class GraphQlEnvelope(val data: CatalogIndex? = null)

/** An era of the game: the grouping a set index is only navigable through. */
@Serializable
data class RemoteSeries(
    val id: String,
    val name: String,
    val logo: String? = null,
)

@Serializable
data class RemoteSeriesDetail(
    val id: String,
    val name: String,
    val logo: String? = null,
    val sets: List<RemoteSet> = emptyList(),
)

/**
 * One set in full.
 *
 * The card rows are the same thin brief the search endpoint returns, so a set's contents
 * and a name search produce the same kind of row and the picker does not need two shapes
 * of result.
 */
@Serializable
data class RemoteSetDetail(
    val id: String,
    val name: String,
    val logo: String? = null,
    val symbol: String? = null,
    val cardCount: RemoteCardCount? = null,
    val serie: RemoteSeriesRef? = null,
    val cards: List<RemoteCardBrief> = emptyList(),
)

@Serializable
data class RemoteSeriesRef(val id: String, val name: String)

@Serializable
data class RemoteCardCount(val total: Int? = null, val official: Int? = null) {
    /**
     * How many cards the set is said to hold, or null if the catalog does not know.
     *
     * A zero is the catalog declining to answer rather than a set with no cards in it --
     * TCGdex files every Pokémon TCG Pocket promo set that way, and reading it literally
     * put "0 cards" on the tile and "009/0" under a Pikachu. Falls through to the full
     * count, which includes secret rares and is the better of two imperfect answers.
     */
    val printed: Int? get() = official?.takeIf { it > 0 } ?: total?.takeIf { it > 0 }
}

@Serializable
data class RemoteSetRef(
    val id: String,
    val name: String,
    val cardCount: RemoteCardCount? = null,
)

@Serializable
data class RemoteVariants(
    val normal: Boolean = false,
    val holo: Boolean = false,
    val reverse: Boolean = false,
    val firstEdition: Boolean = false,
    val wPromo: Boolean = false,
)

@Serializable
data class RemoteCard(
    val id: String,
    val name: String,
    val localId: String? = null,
    val image: String? = null,
    val rarity: String? = null,
    val illustrator: String? = null,
    val category: String? = null,
    val hp: Int? = null,
    val types: List<String> = emptyList(),
    val description: String? = null,
    val set: RemoteSetRef? = null,
    val variants: RemoteVariants? = null,
    // Left as a raw object: TCGplayer keys its prices by finish name, and which keys
    // exist differs card to card ("holofoil", "reverseHolofoil", "1stEditionNormal"...).
    // A typed class here would have to enumerate every finish the hobby has ever had.
    @SerialName("pricing") val pricing: JsonObject? = null,
) {
    /**
     * Market price in USD cents, for the TCGplayer finish that best matches [finishKeys].
     *
     * Only TCGplayer is read. The same document carries Cardmarket figures, but those are
     * in euros, and the app renders one currency symbol -- quietly filing a EUR number
     * under a "$" is worse than showing no price at all.
     */
    fun marketPriceCents(finishKeys: List<String>): Long? {
        // Every step here can be JSON null rather than absent -- a card the catalog knows
        // about but has never seen sold carries `"pricing": {"tcgplayer": null}`, and
        // reading that as an object is a crash, not a missing price.
        val tcgplayer = (pricing?.get("tcgplayer") as? JsonObject) ?: return null
        val preferred = finishKeys.firstNotNullOfOrNull { key -> tcgplayer[key]?.marketPrice() }
        val quoted = preferred ?: tcgplayer.values.firstNotNullOfOrNull { it.marketPrice() }
        // A quote of zero is the catalog saying it has no figure, not a free card.
        return quoted?.takeIf { it > 0.0 }?.let { (it * 100).toLong() }
    }
}

private fun JsonElement.marketPrice(): Double? =
    (this as? JsonObject)?.get("marketPrice")?.let { it as? JsonPrimitive }?.doubleOrNull
