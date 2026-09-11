package app.pocketful.data

import app.pocketful.domain.TcgGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The whole card catalog, as one document the app already has.
 *
 * Built and checked by [TronVonDoom/Pocketful-Catalog](https://github.com/TronVonDoom/Pocketful-Catalog)
 * and published as a release asset, because none of what is in here ever changes. A set
 * has not changed since the day it was printed, so asking TCGdex for Base Set on every
 * launch was paying a round trip for an answer that was already true in 1999 -- and at any
 * real number of users, one request per card per sync is a great deal to ask of a free API
 * that sets `Cache-Control: no-store` and therefore has nothing absorbing repeats.
 *
 * 23,548 cards across 218 sets, 1.1MB on the wire. It carries only the thirteen fields the
 * app actually draws; attacks, abilities and the detailed variant breakdown stay in the
 * build repository where they cost nothing and a phone never downloads them.
 *
 * Prices are deliberately absent, and not merely postponed: this document answers what a
 * card *is*, which was settled the day it was printed, and a price is what a card is
 * *worth*, which is settled by whoever is quoting it today. Putting one in here would give
 * the whole file the shortest lifetime in it -- "downloaded once and kept" would become
 * "re-downloaded on a TTL". [TcgDex.card] fetches prices live instead, and that one call
 * is now the only thing in the app that needs the network to add a card.
 */
@Serializable
data class PublishedCatalog(
    val schema: Int = 0,
    val generatedAt: String? = null,
    val series: List<PublishedSeries> = emptyList(),
    val sets: List<PublishedSet> = emptyList(),
) {
    /** Sets by id, built once. Every lookup below goes through it. */
    private val byId: Map<String, PublishedSet> by lazy { sets.associateBy { it.id } }

    /** Every card in the catalog, flattened, for searching. Built on first search only. */
    private val allCards: List<Pair<PublishedSet, PublishedCard>> by lazy {
        sets.flatMap { set -> set.cards.map { set to it } }
    }

    /**
     * Every card by id, with the set it was printed in, built on first use.
     *
     * Exists for the sync, which resolves a collection card by card and needs to know
     * whether the one in someone's binder has fallback artwork. Twenty-three thousand
     * entries, built once per process rather than scanned per card.
     *
     * The set is kept alongside because [remoteCard] cannot answer without it: a card
     * document carries the name and printed total of the set it belongs to, and a card
     * record on its own knows only its own id.
     */
    private val cardsById: Map<String, Pair<PublishedSet, PublishedCard>> by lazy {
        buildMap { for (set in sets) for (card in set.cards) put(card.id, set to card) }
    }

    fun card(id: String): PublishedCard? = cardsById[id]?.second

    /**
     * One card in the shape the live API would have returned it -- minus the prices.
     *
     * This is what lets [TcgDex.card] stop fetching static data. Every field the app draws
     * off a card document is in this catalog, so the network is left with exactly one job:
     * saying what the card is worth today. A card added with no connection at all still
     * gets its name, artwork, HP, types, flavour text and press runs; it simply arrives
     * without a price, which is the one part that was never ours to know offline.
     */
    fun remoteCard(id: String): RemoteCard? {
        val (set, card) = cardsById[id] ?: return null
        return RemoteCard(
            id = card.id,
            name = card.name,
            localId = card.localId,
            image = card.image,
            rarity = card.rarity,
            illustrator = card.illustrator,
            category = card.category,
            hp = card.hp,
            types = card.types,
            description = card.description,
            set = RemoteSetRef(id = set.id, name = set.name, cardCount = set.cardCount),
            variants = card.variants,
            // Left null on purpose. A price is the one thing this document does not carry,
            // and inventing an empty object here would read as "quoted at nothing".
            pricing = null,
        )
    }

    /** The set index in the shape the sync's id-resolution already expects. */
    private val remoteSetsById: Map<String, RemoteSet> by lazy {
        sets.associate { it.id to it.toRemoteSet() }
    }

    fun setIndex(): Map<String, RemoteSet> = remoteSetsById

    val cardCount: Int get() = sets.sumOf { it.cards.size }

    val isUsable: Boolean get() = sets.isNotEmpty() && schema == SCHEMA

    /** The shape [TcgDex.catalogIndex] would have fetched. */
    fun index(): CatalogIndex = CatalogIndex(
        series = series.map { RemoteSeries(id = it.id, name = it.name ?: it.id) },
        sets = sets.map { it.toRemoteSet() },
    )

    fun set(id: String): PublishedSet? = byId[id]

    /** The rows a set's checklist is built from, or null if this catalog has no such set. */
    fun cardsInSet(id: String): List<SearchHit>? {
        val set = byId[id] ?: return null
        val total = set.cardCount?.printed?.toString()
        val game = gameOfSeries(set.serie?.id)
        return set.cards.map { it.toHit(set, total, game) }
    }

    /**
     * Which press runs each card in a set exists in.
     *
     * This is the expensive question the app used to ask over batched GraphQL -- about six
     * seconds and a few dozen requests per set, repeated every process because the answer
     * was only ever held in memory. It is a printed fact that has not changed in years, so
     * it now costs a map lookup.
     */
    fun variantsInSet(id: String): Map<String, RemoteVariants>? {
        val set = byId[id] ?: return null
        val known = set.cards.mapNotNull { card -> card.variants?.let { card.id to it } }
        return if (known.isEmpty()) null else known.toMap()
    }

    /**
     * Cards whose name or number matches, ranked so exact matches come first.
     *
     * Local, so it answers while the keyboard is still moving and works with no network at
     * all. The old path was one request per keystroke past a 350ms debounce, and returned
     * nothing whatsoever when the catalog was unreachable.
     */
    suspend fun search(query: String, limit: Int): List<SearchHit> = withContext(Dispatchers.Default) {
        val needle = query.trim().lowercase()
        if (needle.length < 2) return@withContext emptyList()

        val hits = mutableListOf<Triple<Int, PublishedSet, PublishedCard>>()
        for ((set, card) in allCards) {
            val name = card.name.lowercase()
            val rank = when {
                name == needle -> 0
                name.startsWith(needle) -> 1
                name.contains(needle) -> 2
                card.localId?.lowercase() == needle -> 3
                else -> continue
            }
            hits += Triple(rank, set, card)
        }

        // Rank first, then newest set -- so "charizard" opens on the one someone most
        // likely just pulled rather than on a 1999 promo. ISO dates sort chronologically
        // as text, so newest-first is a descending string compare and needs no parsing.
        hits
            .sortedWith(
                compareBy<Triple<Int, PublishedSet, PublishedCard>> { it.first }
                    .thenByDescending { it.second.releaseDate ?: "" },
            )
            .take(limit)
            .map { (_, set, card) ->
                card.toHit(set, set.cardCount?.printed?.toString(), gameOfSeries(set.serie?.id))
            }
    }

    companion object {
        /**
         * The document shape this build understands.
         *
         * Matched against the file's own `schema`, and present in the asset's *filename*
         * as well, so an app built for v1 asks for v1 forever and publishing a v2 cannot
         * reach back and break installs that predate it.
         */
        const val SCHEMA: Int = 1

        /** Lenient for the same reason [TcgDex] is: a new field upstream is not a crash. */
        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        fun parse(text: String): PublishedCatalog? =
            runCatching { json.decodeFromString<PublishedCatalog>(text) }
                .getOrNull()
                ?.takeIf { it.isUsable }
    }
}

@Serializable
data class PublishedSeries(val id: String, val name: String? = null)

@Serializable
data class PublishedSet(
    val id: String,
    val name: String = "",
    val logo: String? = null,
    val symbol: String? = null,
    val releaseDate: String? = null,
    val cardCount: RemoteCardCount? = null,
    val serie: RemoteSeriesRef? = null,
    val abbreviation: RemoteAbbreviation? = null,
    val cards: List<PublishedCard> = emptyList(),
) {
    fun toRemoteSet(): RemoteSet = RemoteSet(
        id = id,
        name = name,
        logo = logo,
        symbol = symbol,
        cardCount = cardCount,
        releaseDate = releaseDate,
        serie = serie,
    )
}

/**
 * A card, reduced to what a pocket and a detail sheet draw.
 *
 * [imageAlt] is the half of this that did not exist before. TCGdex has no artwork at all
 * for about 7% of the catalog -- whole Trainer Kits, Shining Fates' Shiny Vault, Crown
 * Zenith's Galarian Gallery, Ancient Mew -- and the build resolves what it can from a
 * second and third source. It is kept beside [image] rather than written into it so that
 * a later upstream pull which *does* have the scan wins automatically, and so the app can
 * say where a picture came from.
 */
@Serializable
data class PublishedCard(
    val id: String,
    val localId: String? = null,
    val name: String = "",
    val rarity: String? = null,
    val illustrator: String? = null,
    val category: String? = null,
    /** A TCGdex stem, which takes a quality and a format appended. Null if they have none. */
    val image: String? = null,
    /** A complete URL from a fallback source. Already a finished image, not a stem. */
    val imageAlt: String? = null,
    val imageAltSource: String? = null,
    val variants: RemoteVariants? = null,
    /**
     * The printed facts the detail sheet and the collection rows draw.
     *
     * Added once it was clear the app was paying a live card fetch for them. [types] is
     * the expensive one to be without -- it colours the chip on every row in a collection
     * -- and [description] is the expensive one to carry, at roughly half the packed
     * catalog by itself. Both are printed on the card, so both belong here rather than on
     * the wire.
     */
    val hp: Int? = null,
    val types: List<String> = emptyList(),
    val description: String? = null,
) {
    fun toHit(set: PublishedSet, total: String?, game: TcgGame): SearchHit = SearchHit(
        id = id,
        name = name,
        number = localId ?: id.substringAfterLast('-'),
        setId = set.id,
        setName = set.name,
        setTotal = total,
        artStem = image,
        artUrl = imageAlt,
        game = game,
    )
}

@Serializable
data class RemoteAbbreviation(val official: String? = null)
