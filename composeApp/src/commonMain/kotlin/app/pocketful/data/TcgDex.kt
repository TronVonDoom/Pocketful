package app.pocketful.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
    /** 218 sets, ~35KB, and it changes a few times a year. Fetched once per process. */
    private var setIndex: Map<String, RemoteSet>? = null

    /** A dozen entries that change once a year. Same deal. */
    private var seriesIndex: List<RemoteSeries>? = null

    /** Sets and series together, each set carrying the era and date it is filed under. */
    private var catalogIndex: CatalogIndex? = null

    /** Full set documents, kept as they are opened rather than fetched up front. */
    private val setDetails = mutableMapOf<String, RemoteSetDetail>()

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

        val rows: List<RemoteCardBrief> = client
            .get("$BASE/$language/cards") {
                parameter("name", trimmed)
                parameter("pagination:page", 1)
                parameter("pagination:itemsPerPage", limit)
            }
            .body()

        val sets = runCatching { sets() }.getOrDefault(emptyMap())
        return rows.map { row ->
            val setId = row.id.substringBeforeLast('-', missingDelimiterValue = "")
            SearchHit(
                id = row.id,
                name = row.name,
                number = row.localId ?: row.id.substringAfterLast('-'),
                setId = setId,
                setName = sets[setId]?.name ?: setId,
                setTotal = sets[setId]?.cardCount?.official?.toString(),
                artStem = row.image,
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
        val detail = setDetail(setId) ?: return emptyList()
        val total = detail.cardCount?.official?.toString()
        return detail.cards.map { row ->
            SearchHit(
                id = row.id,
                name = row.name,
                number = row.localId ?: row.id.substringAfterLast('-'),
                setId = detail.id,
                setName = detail.name,
                setTotal = total,
                artStem = row.image,
            )
        }
    }

    /** The set index, fetched at most once and then held for the life of the process. */
    suspend fun sets(): Map<String, RemoteSet> {
        setIndex?.let { return it }
        val fetched: List<RemoteSet> = client.get("$BASE/$language/sets").body()
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
        val fetched: List<RemoteSeries> = client.get("$BASE/$language/series").body()
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

/** A row in the search picker: enough to identify a card, not enough to file one. */
data class SearchHit(
    val id: String,
    val name: String,
    val number: String,
    val setId: String,
    val setName: String,
    val setTotal: String?,
    val artStem: String?,
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
    val officialCount: Int? get() = cardCount?.official ?: cardCount?.total

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
data class RemoteCardCount(val total: Int? = null, val official: Int? = null)

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
