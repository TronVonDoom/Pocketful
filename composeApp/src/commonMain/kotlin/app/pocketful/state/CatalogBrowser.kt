package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.CatalogIndex
import app.pocketful.data.RemoteSeries
import app.pocketful.data.RemoteSet
import app.pocketful.data.SearchHit
import app.pocketful.data.TcgDex

/**
 * The four ways a list of eras or of sets can be put in order.
 *
 * The same four for both levels on purpose. Someone who has just sorted the eras oldest
 * first and then wants the sets inside them the same way should not have to learn a
 * second vocabulary one line down the screen.
 */
enum class CatalogOrder(val label: String) {
    Newest("Newest"),
    Oldest("Oldest"),
    NameAsc("A–Z"),
    NameDesc("Z–A"),
}

/**
 * One era and everything printed under it.
 *
 * [releaseDate] is the earliest release in the group rather than a date the era itself
 * carries. Deriving it means an era can never sort ahead of a set it contains, which is
 * exactly the kind of inconsistency that makes a chronological list look wrong without
 * anyone being able to say why.
 */
data class SeriesGroup(
    val series: RemoteSeries,
    val sets: List<RemoteSet>,
    val releaseDate: String?,
) {
    /** The span an era covers, for the line under its name. */
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
 * [CardLookup] answers "find me this card"; this answers "show me what exists". They are
 * kept apart because their failure modes are different: a card search that returns
 * nothing is a normal answer, whereas an empty set index means the catalog could not be
 * reached at all, and collapsing the two into one holder produced a screen that could not
 * tell "no such set" from "no network".
 *
 * The index is fetched once and every arrangement of it after that is local. That is a
 * deliberate change from the version that fetched a series' members when you tapped it:
 * sorting 218 sets four ways at two levels is only a sort if the whole catalog is already
 * in hand, and a network round trip behind a sort control is a sort control that people
 * stop touching.
 *
 * Nothing here writes to the collection. Adding a card is an explicit call the screen
 * makes once something has actually been chosen.
 */
class CatalogBrowser(private val api: TcgDex) {

    /** Every era, with the sets in it. Unsorted -- [arrange] decides the order. */
    var groups by mutableStateOf<List<SeriesGroup>>(emptyList())
        private set

    /** Every set in the catalog, flat, for searching by set name. */
    var sets by mutableStateOf<List<RemoteSet>>(emptyList())
        private set

    /** True while the index is being fetched for the first time. */
    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /**
     * How the two levels are ordered, held here rather than in the screen so that leaving
     * the tab and coming back does not silently undo a choice the user made.
     *
     * Newest first at both levels, because the catalog is opened to look something up far
     * more often than to read it end to end, and what people are looking up is what they
     * just pulled out of a pack. Oldest first put Base Set at the top and buried the
     * current era under twenty-five years of scrolling.
     */
    var seriesOrder by mutableStateOf(CatalogOrder.Newest)

    var setOrder by mutableStateOf(CatalogOrder.Newest)

    /** Whether there is anything to draw yet. */
    val isEmpty: Boolean get() = groups.isEmpty()

    /** Fetched once. The whole index is small and static enough to hold for the session. */
    suspend fun load() {
        if (groups.isNotEmpty()) return
        loading = true
        error = null
        runCatching { api.catalogIndex() }
            .onSuccess { index ->
                if (index.sets.isEmpty()) {
                    error = "Could not reach the card catalog. Check your connection and try again."
                } else {
                    groups = index.toGroups()
                    sets = index.sets
                }
            }
            .onFailure {
                error = "Could not reach the card catalog. Check your connection and try again."
            }
        loading = false
    }

    /**
     * The catalog in the order the screen should draw it.
     *
     * Returns a new list rather than mutating in place so the caller can remember it
     * against the three things it depends on; re-sorting 218 sets on every frame of a
     * scroll would otherwise be the cost of having a sort control at all.
     */
    fun arrange(): List<SeriesGroup> {
        val within = setOrdering(setOrder)
        return groups
            .map { group -> group.copy(sets = group.sets.sortedWith(within)) }
            .sortedWith(seriesOrdering(seriesOrder))
    }

    /** Every card in one set, for browsing a set's contents. */
    suspend fun cardsInSet(setId: String): List<SearchHit> =
        runCatching { api.cardsInSet(setId) }
            .onFailure { error = "Could not load that set's cards." }
            .getOrDefault(emptyList())
}

// ------------------------------------------------------------------- grouping

/** The id sets land under when the catalog does not say which era they belong to. */
private const val UNFILED = "unfiled"

private fun CatalogIndex.toGroups(): List<SeriesGroup> {
    val known = series.associateBy { it.id }
    return sets
        .groupBy { it.serie?.id ?: UNFILED }
        .map { (id, members) ->
            val named = known[id] ?: RemoteSeries(
                id = id,
                name = members.firstNotNullOfOrNull { it.serie?.name } ?: "Other sets",
            )
            SeriesGroup(
                // An era with no wordmark of its own borrows the one from its first set,
                // which is where TCGdex takes most series logos from anyway. A header
                // with a logo beside a header without one reads as a loading failure.
                series = named.copy(logo = named.logo ?: members.earliestLogo()),
                sets = members,
                releaseDate = members.mapNotNull { it.releaseDate }.minOrNull(),
            )
        }
}

private fun List<RemoteSet>.earliestLogo(): String? =
    sortedWith(compareBy<RemoteSet, String?>(nullsLast(naturalOrder())) { it.releaseDate })
        .firstNotNullOfOrNull { it.logo }

// -------------------------------------------------------------------- ordering

/**
 * Undated entries sort last in both directions rather than clumping at whichever end
 * "no date" happens to compare toward. They only occur on the REST fallback path, and a
 * promo set of unknown age landing between two numbered eras is worse than it trailing
 * the list where its absence of a date is visible.
 */
private fun setOrdering(order: CatalogOrder): Comparator<RemoteSet> = when (order) {
    CatalogOrder.Oldest ->
        compareBy<RemoteSet, String?>(nullsLast(naturalOrder())) { it.releaseDate }
            .thenBy { it.name.lowercase() }

    CatalogOrder.Newest ->
        compareBy<RemoteSet, String?>(nullsLast(reverseOrder())) { it.releaseDate }
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

/**
 * One browser per app, so the index is fetched once rather than once per visit.
 *
 * Deliberately does not fetch on creation. This is remembered at the app root so it
 * survives tab switches, and loading there would mean a request on every cold start for a
 * screen the user may never open. [CatalogBrowser.load] is idempotent, so the search
 * screen calls it when it first appears and every visit after that is free.
 */
@Composable
fun rememberCatalogBrowser(api: TcgDex): CatalogBrowser =
    remember(api) { CatalogBrowser(api) }
