package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.CardCatalog
import app.pocketful.data.CardImport
import app.pocketful.data.CatalogIndex
import app.pocketful.data.CatalogSeries
import app.pocketful.data.CatalogSet
import app.pocketful.data.SearchHit
import app.pocketful.data.SetPocket
import app.pocketful.domain.TcgGame

/**
 * The four ways a list of series or of sets can be put in order. The same four for both levels,
 * so sorting one and then the other needs no second vocabulary.
 */
enum class CatalogOrder(val label: String) {
    Newest("Newest"),
    Oldest("Oldest"),
    NameAsc("A–Z"),
    NameDesc("Z–A"),
}

/**
 * One series and everything published under it. [releaseDate] is the earliest release in the
 * group, so a series can never sort ahead of a set it contains.
 */
data class SeriesGroup(
    val series: CatalogSeries,
    val sets: List<CatalogSet>,
    val releaseDate: String?,
    val game: TcgGame,
) {
    /** The span a series covers, for the line under its name. */
    val years: String?
        get() {
            val stamps = sets.mapNotNull { it.releaseYear }.sorted()
            val first = stamps.firstOrNull() ?: return null
            val last = stamps.last()
            return if (first == last) first else "$first–$last"
        }
}

/**
 * Browsing the catalog by structure rather than by name, as screen state.
 *
 * [CardLookup] answers "find me this card"; this answers "show me what exists". Everything here
 * is read from the catalog on the device, so every arrangement of it is a local sort.
 *
 * Nothing here writes to the collection.
 */
class CatalogBrowser(private val catalog: CardCatalog) {

    /** Every series, with the sets in it. Unsorted -- [arrange] decides the order. */
    var groups by mutableStateOf<List<SeriesGroup>>(emptyList())
        private set

    /** Every set in the catalog, flat, for searching by set name. */
    var sets by mutableStateOf<List<CatalogSet>>(emptyList())
        private set

    private var setsById by mutableStateOf<Map<String, CatalogSet>>(emptyMap())

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Kept for the set screen, which reports a binder that could not be worked out. */
    var variantsError by mutableStateOf<String?>(null)
        private set

    var seriesOrder by mutableStateOf(CatalogOrder.Newest)

    var setOrder by mutableStateOf(CatalogOrder.Newest)

    val isEmpty: Boolean get() = groups.isEmpty()

    /** How much of the catalog is filed under one game, or null when there is none. */
    fun sizeOf(game: TcgGame): CatalogSize? {
        if (groups.isEmpty()) return null
        val eras = groups.filter { it.game == game }
        if (eras.isEmpty()) return null
        return CatalogSize(sets = eras.sumOf { it.sets.size }, series = eras.size)
    }

    fun setsOf(game: TcgGame): List<CatalogSet> = sets.filter { it.game == game }

    /** The game a set belongs to, by the set ID a printing stores. A set the catalog does not list is a hand-added card's. */
    fun gameOfSet(setId: String): TcgGame = setsById[setId]?.game ?: TcgGame.POKEMON

    /** Reads the catalog's shape. Cheap: it is already on the device. */
    suspend fun load() {
        if (groups.isNotEmpty()) return
        refresh()
    }

    /** Reads the catalog's shape again, after a refresh brought new sets. */
    fun refresh() {
        loading = true
        error = null
        val index = catalog.catalogIndex()
        groups = index.toGroups()
        sets = index.sets
        setsById = index.sets.associateBy { it.id }
        error = when {
            !catalog.isLoaded -> "The card catalog has not downloaded yet. Check the connection and open the app again."
            index.sets.isEmpty() -> "No sets have been published to the catalog yet."
            else -> null
        }
        loading = false
    }

    /** One game's catalog, in the order the screen should draw it. */
    fun arrange(game: TcgGame): List<SeriesGroup> {
        val within = setOrdering(setOrder)
        return groups
            .filter { it.game == game }
            .map { group -> group.copy(sets = group.sets.sortedWith(within)) }
            .sortedWith(seriesOrdering(seriesOrder))
    }

    /** Every card in one set, in printed order. */
    fun cardsInSet(setId: String): List<SearchHit> = catalog.cardsInSet(setId)

    /** One pocket per card, each in its plainest printing. */
    fun checklistOf(setId: String, cards: List<SearchHit>): List<SetPocket> {
        variantsError = null
        return CardImport.checklist(catalog, cards)
    }

    /** Every printing of every card, card by card. */
    fun masterSetOf(setId: String, cards: List<SearchHit>): List<SetPocket> {
        variantsError = null
        val pockets = CardImport.masterSet(catalog, cards)
        if (pockets.isEmpty()) variantsError = "This set has no printings listed in the catalog."
        return pockets
    }
}

/** What one game's slice of the catalog amounts to, for the tile that opens it. */
data class CatalogSize(val sets: Int, val series: Int) {
    val caption: String
        get() = "$sets ${if (sets == 1) "set" else "sets"} · $series series"
}

private fun CatalogIndex.toGroups(): List<SeriesGroup> {
    val bySeries = sets.groupBy { it.serie?.id }
    return series.mapNotNull { s ->
        val members = bySeries[s.id].orEmpty()
        if (members.isEmpty()) return@mapNotNull null
        SeriesGroup(
            series = s,
            sets = members,
            releaseDate = members.mapNotNull { it.releaseDate }.minOrNull(),
            game = s.game,
        )
    }
}

private fun setOrdering(order: CatalogOrder): Comparator<CatalogSet> = when (order) {
    CatalogOrder.Oldest ->
        compareBy<CatalogSet, String?>(nullsLast(naturalOrder())) { it.releaseDate }
            .thenBy { it.name.lowercase() }

    CatalogOrder.Newest ->
        compareBy<CatalogSet, String?>(nullsLast(reverseOrder())) { it.releaseDate }
            .thenBy { it.name.lowercase() }

    CatalogOrder.NameAsc -> compareBy { it.name.lowercase() }
    CatalogOrder.NameDesc -> compareByDescending { it.name.lowercase() }
}

private fun seriesOrdering(order: CatalogOrder): Comparator<SeriesGroup> = when (order) {
    CatalogOrder.Oldest ->
        compareBy<SeriesGroup, String?>(nullsLast(naturalOrder())) { it.releaseDate }
            .thenBy { it.series.name.lowercase() }

    CatalogOrder.Newest ->
        compareBy<SeriesGroup, String?>(nullsLast(reverseOrder())) { it.releaseDate }
            .thenBy { it.series.name.lowercase() }

    CatalogOrder.NameAsc -> compareBy { it.series.name.lowercase() }
    CatalogOrder.NameDesc -> compareByDescending { it.series.name.lowercase() }
}

/** One browser per app. */
@Composable
fun rememberCatalogBrowser(catalog: CardCatalog): CatalogBrowser =
    remember(catalog) { CatalogBrowser(catalog) }
