package app.pocketful.data

import app.pocketful.domain.Language
import app.pocketful.domain.TcgGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The card catalog, on the device.
 *
 * Everything the app knows about what cards exist comes from here, and here comes only from
 * the published catalog: the index and the set files [CatalogDownload] keeps on disk. There is
 * no live API behind it. A set reaches the app when it is published in the Pocketful Editor
 * and not before, so an empty catalog is a real answer -- nothing has been published yet --
 * rather than a failure to reach something.
 *
 * Today's prices ride along ([usePrices]) because every question that turns a card into a row
 * wants both, and a second object every caller had to remember to consult was how cards used
 * to get filed unpriced.
 *
 * Nothing here touches the collection. Turning a card into catalog rows is [CardImport]'s job,
 * and owning one is the store's.
 */
class CardCatalog {

    private var index: IndexDoc? = null
    private var setDocs: Map<String, SetDoc> = emptyMap()

    private var entries: Map<String, SetEntry> = emptyMap()
    private var cardsById: Map<String, CatalogCard> = emptyMap()
    private var allHits: List<Pair<String, SearchHit>> = emptyList()

    var prices: PublishedPrices? = null
        private set

    /** Whether a catalog has been loaded at all, even an empty one. */
    val isLoaded: Boolean get() = index != null

    val setCount: Int get() = entries.size
    val cardCount: Int get() = cardsById.size

    /** Where the catalog's files are served from. */
    val publicUrl: String get() = index?.publicUrl?.trimEnd('/') ?: CatalogDownload.BUILT_IN_BASE

    /**
     * Loads a catalog, replacing whatever was loaded. Runs its indexing off the main thread:
     * it is a few thousand cards now and tens of thousands once the catalog is whole.
     */
    suspend fun use(loaded: LoadedCatalog?) {
        if (loaded == null) return
        withContext(Dispatchers.Default) {
            val newEntries = mutableMapOf<String, SetEntry>()
            for (catalog in loaded.index.catalogs) {
                for (series in catalog.series) {
                    for (set in series.sets) {
                        val doc = loaded.sets[set.id] ?: continue
                        newEntries[set.id] = SetEntry(catalog, series, set, doc)
                    }
                }
            }
            val newCards = mutableMapOf<String, CatalogCard>()
            val hits = mutableListOf<Pair<String, SearchHit>>()
            for (entry in newEntries.values) {
                for (card in entry.doc.cards) {
                    val catalogCard = CatalogCard(card, entry)
                    newCards[card.id] = catalogCard
                    val hit = catalogCard.toHit()
                    hits += searchText(catalogCard) to hit
                }
            }
            index = loaded.index
            setDocs = loaded.sets
            entries = newEntries
            cardsById = newCards
            allHits = hits
        }
        CardArt.base = publicUrl
    }

    fun usePrices(published: PublishedPrices?) {
        prices = published?.takeIf { it.isUsable }
    }

    // ------------------------------------------------------------------ browsing

    /** Every published series and set, flat, for the browse screen to arrange. */
    fun catalogIndex(): CatalogIndex {
        val loaded = index ?: return CatalogIndex()
        val series = mutableListOf<CatalogSeries>()
        val sets = mutableListOf<CatalogSet>()
        for (catalog in loaded.catalogs) {
            for (s in catalog.series) {
                val members = s.sets.mapNotNull { entries[it.id]?.toSet() }
                if (members.isEmpty()) continue
                series += CatalogSeries(id = s.id, name = s.name, logo = s.logo, game = gameOf(catalog))
                sets += members
            }
        }
        return CatalogIndex(series, sets)
    }

    fun set(id: String): CatalogSet? = entries[id]?.toSet()

    /** Every card in one set, in the order the set prints them. */
    fun cardsInSet(setId: String): List<SearchHit> =
        entries[setId]?.doc?.cards?.mapNotNull { cardsById[it.id]?.toHit() }.orEmpty()

    /** One card, whole, with the set, series and catalog it belongs to. */
    fun card(id: String): CatalogCard? = cardsById[id]

    /**
     * Cards whose name, number or set matches, ranked so exact and prefix name matches come first.
     *
     * Local, so it answers while the keyboard is still moving and works with no network at all.
     */
    suspend fun search(query: String, limit: Int = 60): List<SearchHit> = withContext(Dispatchers.Default) {
        val needle = query.trim().lowercase()
        if (needle.length < 2) return@withContext emptyList()
        allHits.asSequence()
            .mapNotNull { (text, hit) ->
                val name = hit.name.lowercase()
                val rank = when {
                    name == needle -> 0
                    name.startsWith(needle) -> 1
                    name.split(' ').any { it.startsWith(needle) } -> 2
                    hit.collectorNumber.lowercase().startsWith(needle) -> 3
                    name.contains(needle) -> 4
                    text.contains(needle) -> 5
                    else -> null
                }
                rank?.let { Triple(it, entries[hit.setId]?.set?.releaseDate ?: "", hit) }
            }
            .sortedWith(compareBy<Triple<Int, String, SearchHit>> { it.first }.thenByDescending { it.second })
            .map { it.third }
            .take(limit)
            .toList()
    }

    // ------------------------------------------------------------------ words

    /** How a variation word reads in the app: "Poké Ball Pattern" for `pokeball`. */
    fun wordLabel(word: String): String =
        index?.words?.get(word)?.label?.takeIf { it.isNotBlank() } ?: word.split('-').joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }

    /** Where a variation word sorts among words of its kind, per the catalog's word list. */
    fun wordSort(word: String?): Int = word?.let { index?.words?.get(it)?.sort } ?: 0

    /** How a rarity, type or subtype reads in a catalog's language, falling back to English, then the code. */
    fun termLabel(kind: String, code: String, language: String = "en"): String {
        val labels = index?.terms?.get(kind)?.get(code)
        return labels?.get(language) ?: labels?.get("en") ?: code
    }

    private fun searchText(card: CatalogCard): String = buildString {
        append(card.card.name.lowercase()); append(' ')
        card.card.nameEn?.let { append(it.lowercase()); append(' ') }
        append(card.entry.set.name.lowercase()); append(' ')
        append(card.entry.set.code.lowercase()); append(' ')
        card.entry.set.abbreviation?.let { append(it.lowercase()); append(' ') }
        append((card.card.printedNumber ?: card.card.number).lowercase()); append(' ')
        card.card.rarity?.let { append(termLabel("rarity", it, card.entry.catalog.language).lowercase()); append(' ') }
        card.card.illustrator?.let { append(it.lowercase()) }
    }

    companion object {
        fun gameOf(catalog: IndexCatalog): TcgGame = when (catalog.game) {
            "ptcg" -> TcgGame.POKEMON
            else -> TcgGame.POKEMON
        }

        fun languageOf(code: String): Language = when (code) {
            "jp", "ja" -> Language.JA
            "cht", "chs", "zh" -> Language.ZH
            else -> Language.EN
        }
    }
}

/** One published set, with what it sits under. */
data class SetEntry(
    val catalog: IndexCatalog,
    val series: IndexSeries,
    val set: IndexSet,
    val doc: SetDoc,
) {
    fun toSet(): CatalogSet = CatalogSet(
        id = set.id,
        code = set.code,
        name = set.name,
        logo = set.logo,
        symbol = set.symbol,
        releaseDate = set.releaseDate,
        printedTotal = set.printedTotal,
        cards = doc.cards.size,
        serie = CatalogSeriesRef(series.id, series.name),
        game = CardCatalog.gameOf(catalog),
        language = catalog.language,
        version = set.version,
    )
}

/** One card, whole, and everything above it that a row needs. */
data class CatalogCard(val card: SetCard, val entry: SetEntry) {
    val id: String get() = card.id

    /** The back drawn when the card has no picture: its series' own, else its catalog's. */
    val back: String? get() = CardArt.stemOf(entry.series.back ?: entry.catalog.back)

    fun toHit(): SearchHit = SearchHit(
        id = card.id,
        name = card.name,
        number = card.number,
        printedNumber = card.printedNumber,
        setId = entry.set.id,
        setName = entry.set.name,
        image = CardArt.stemOf(card.image),
        back = back,
        game = CardCatalog.gameOf(entry.catalog),
    )
}

/**
 * A row in a card picker: enough to identify a card and draw it, not the card itself.
 * [CardCatalog.card] has the rest, and on the device, so turning a row into a filed card
 * costs nothing.
 */
data class SearchHit(
    val id: String,
    val name: String,
    val number: String,
    val printedNumber: String?,
    val setId: String,
    val setName: String,
    /** The card's picture, as a stem. See [CardArt]. */
    val image: String?,
    /** The card back to draw when there is no [image]. */
    val back: String? = null,
    val game: TcgGame = TcgGame.POKEMON,
) {
    val collectorNumber: String get() = printedNumber ?: number
}

data class CatalogSeriesRef(val id: String, val name: String)

/** A published series: the grouping sets are browsed by. */
data class CatalogSeries(
    val id: String,
    val name: String,
    val logo: String? = null,
    val game: TcgGame = TcgGame.POKEMON,
)

/** A published set, as the browse screens list it. */
data class CatalogSet(
    val id: String,
    val code: String,
    val name: String,
    val logo: String? = null,
    val symbol: String? = null,
    /** ISO `yyyy-MM-dd`. Compared as text, which sorts it chronologically. */
    val releaseDate: String? = null,
    val printedTotal: Int? = null,
    val cards: Int = 0,
    val serie: CatalogSeriesRef? = null,
    val game: TcgGame = TcgGame.POKEMON,
    val language: String = "en",
    val version: Int = 0,
) {
    /** How many cards the set is printed as holding, or how many it has when that is not printed. */
    val officialCount: Int? get() = printedTotal ?: cards.takeIf { it > 0 }

    /** The four digits a tile has room for. */
    val releaseYear: String? get() = releaseDate?.take(4)?.takeIf { it.length == 4 }
}

/** Every published series and every set under them, flat. */
data class CatalogIndex(
    val series: List<CatalogSeries> = emptyList(),
    val sets: List<CatalogSet> = emptyList(),
)
