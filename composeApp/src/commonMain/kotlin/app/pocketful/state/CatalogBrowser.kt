package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.CardImport
import app.pocketful.data.CatalogIndex
import app.pocketful.data.RemoteSeries
import app.pocketful.data.RemoteSet
import app.pocketful.data.RemoteVariants
import app.pocketful.data.SearchHit
import app.pocketful.data.SetPocket
import app.pocketful.data.TcgDex
import app.pocketful.data.catalogFailureMessage
import app.pocketful.data.game
import app.pocketful.data.gameOfSeries
import app.pocketful.domain.TcgGame

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
    /** The game this era belongs to. Read from the era, not from the sets under it. */
    val game: TcgGame,
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

    /**
     * The same sets keyed by id, so a card already in the collection can be asked which
     * game it came from without a linear scan per row.
     *
     * Snapshot-backed like the list it mirrors. It is only ever written beside [sets], but
     * a plain field here would make that adjacency load-bearing -- a screen reading this
     * one would redraw on the other's write and there would be nothing saying why.
     */
    private var setsById by mutableStateOf<Map<String, RemoteSet>>(emptyMap())

    /** True while the index is being fetched for the first time. */
    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Why the last master set could not be worked out, if it could not be. */
    var variantsError by mutableStateOf<String?>(null)
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

    /**
     * How much of the catalog is filed under one game, or null before the index lands.
     *
     * Offered so a game tile can say what is behind it -- "203 sets · 20 eras" is the
     * difference between a button and a promise -- and so the two Pokémon entries, which
     * otherwise share a publisher and half a name, are told apart on the one figure that
     * actually differs between them.
     */
    fun sizeOf(game: TcgGame): CatalogSize? {
        if (groups.isEmpty()) return null
        val eras = groups.filter { it.game == game }
        if (eras.isEmpty()) return null
        return CatalogSize(sets = eras.sumOf { it.sets.size }, series = eras.size)
    }

    /** Every set in one game, for matching a typed set name without leaving it. */
    fun setsOf(game: TcgGame): List<RemoteSet> = sets.filter { it.game == game }

    /**
     * The game a set belongs to, by the id the collection stores as a printing's set code.
     *
     * Cards already in the collection carry no game of their own -- they are keyed to a
     * set, and the set is what knows. A set the index has never heard of is a card typed
     * in by hand or imported from elsewhere, and those belong to the printed game.
     */
    fun gameOfSet(setCode: String): TcgGame = gameOfSeries(setsById[setCode]?.serie?.id)

    /** Fetched once. The whole index is small and static enough to hold for the session. */
    suspend fun load() {
        if (groups.isNotEmpty()) return
        loading = true
        error = null
        runCatching { api.catalogIndex() }
            .onSuccess { index ->
                if (index.sets.isEmpty()) {
                    // Answered, but with nothing in it. Both transports failed softly --
                    // GraphQL puts its errors in a 200 body, and the REST fallback
                    // returns an empty list rather than throwing -- so this is the one
                    // failure that arrives as a success and needs saying separately.
                    error = "The card catalog answered, but sent no sets. " +
                        "It may be having trouble; try again in a few minutes."
                } else {
                    groups = index.toGroups()
                    sets = index.sets
                    setsById = index.sets.associateBy { it.id }
                }
            }
            .onFailure { error = catalogFailureMessage(it, "the set index") }
        loading = false
    }

    /**
     * One game's catalog, in the order the screen should draw it.
     *
     * Filtered before it is sorted rather than after, because the two orderings are over
     * the list that is drawn: an era's date is the earliest set in it, and the count in
     * its caption is the number of sets under it, and neither means anything if half the
     * list is about to be dropped.
     *
     * Returns a new list rather than mutating in place so the caller can remember it
     * against the things it depends on; re-sorting two hundred sets on every frame of a
     * scroll would otherwise be the cost of having a sort control at all.
     */
    fun arrange(game: TcgGame): List<SeriesGroup> {
        val within = setOrdering(setOrder)
        return groups
            .filter { it.game == game }
            .map { group -> group.copy(sets = group.sets.sortedWith(within)) }
            .sortedWith(seriesOrdering(seriesOrder))
    }

    /** Every card in one set, for browsing a set's contents. */
    suspend fun cardsInSet(setId: String): List<SearchHit> =
        runCatching { api.cardsInSet(setId) }
            .onFailure { error = catalogFailureMessage(it, "that set's cards") }
            .getOrDefault(emptyList())

    /**
     * Warms the press-run cache for a set, without needing the answer.
     *
     * Both binder shapes are built out of the same fact -- which press runs each card was
     * printed in -- and that fact costs a few seconds to gather the first time a set is
     * asked. Started when the set screen opens, it is usually in hand by the time anyone
     * has read the header and decided; the buttons wait on it only if it is not.
     *
     * Failure is silent here on purpose. This is speculative work for a button nobody may
     * press, and the button itself reports the problem if it turns out to matter.
     */
    suspend fun prefetchVariants(setId: String) {
        runCatching { api.variantsInSet(setId) }
    }

    /**
     * One pocket per card in a set, each in the press run that card actually exists in.
     *
     * Never fails. Press runs the catalog would not give up leave a card filed as a
     * normal, which is what every pocket in this binder used to be regardless -- so the
     * worst case here is the binder people were already getting.
     */
    suspend fun checklistOf(setId: String, cards: List<SearchHit>): List<SetPocket> {
        variantsError = null
        return CardImport.checklist(cards, variantsOf(setId))
    }

    /**
     * Every pocket a master set of one set needs: each card once per press run it exists in.
     *
     * Kept here rather than in the screen because it is the checklist and the press runs
     * behind it having to agree, and because the screen that asks for it is torn down the
     * moment the binder it produces is opened.
     *
     * Unlike [checklistOf] this one refuses rather than degrades. A binder called "master
     * set" that quietly turned out to be one pocket per card is only discovered a hundred
     * pockets in.
     *
     * Failure is reported separately from [error]. That one is the catalog being
     * unreachable, which the search tab renders as a dead end; this one is a binder that
     * could not be worked out on a catalog that is otherwise answering fine, and the only
     * screen that should say so is the one with the button on it.
     */
    suspend fun masterSetOf(setId: String, cards: List<SearchHit>): List<SetPocket> {
        variantsError = null
        val variants = runCatching { api.variantsInSet(setId) }
            .onFailure { variantsError = catalogFailureMessage(it, "this set's variations") }
            .getOrDefault(emptyMap())
        if (variants.isEmpty()) {
            // Reached only when nothing threw: [TcgDex.variantsInSet] drops a batch that
            // failed rather than failing the set, so the usual way to get here is the
            // catalog answering every batch badly. That is not something this can blame
            // on the user's connection, so it no longer does.
            if (variantsError == null) {
                variantsError = "The card catalog would not say which variations this " +
                    "set was printed in. Try again in a few minutes."
            }
            return emptyList()
        }
        return CardImport.masterSet(cards, variants)
    }

    private suspend fun variantsOf(setId: String): Map<String, RemoteVariants> =
        runCatching { api.variantsInSet(setId) }.getOrDefault(emptyMap())
}

/** What one game's slice of the catalog amounts to, for the tile that opens it. */
data class CatalogSize(val sets: Int, val series: Int) {
    /**
     * "203 sets · 20 eras", counted in words rather than in numbers alone.
     *
     * Pluralised here rather than at the tile because Pokémon TCG Pocket is genuinely one
     * era, and "1 eras" on the tile beside it is the kind of seam that makes a screen look
     * generated rather than written.
     */
    val caption: String
        get() = "$sets ${if (sets == 1) "set" else "sets"} · " +
            "$series ${if (series == 1) "era" else "eras"}"
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
                game = named.game,
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
