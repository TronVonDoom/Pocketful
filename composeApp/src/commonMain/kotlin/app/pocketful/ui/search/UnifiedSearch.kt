package app.pocketful.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.pocketful.data.CatalogSet
import app.pocketful.data.SearchHit
import app.pocketful.domain.CardBrief
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.CopyRow
import app.pocketful.domain.Money
import app.pocketful.domain.VariantId
import app.pocketful.domain.copyRows
import app.pocketful.state.CardLookup
import app.pocketful.state.CatalogBrowser
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.CardResultRow
import app.pocketful.ui.components.CardRowItem
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.Note
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SetRow
import app.pocketful.ui.components.trendChip
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Motion
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space

/**
 * One search box for the whole app.
 *
 * There used to be two, and which one you got depended on where you happened to be
 * standing. The Search tab looked outward at the catalog and could not tell you whether you
 * already owned the card it was offering; the field inside the card list looked inward and
 * could not tell you the card existed if you did not. Between them they made the single
 * most common question a collector asks -- "do I have this one?" -- into a question about
 * app navigation.
 *
 * So there is one box now, reachable from the top of every screen, and it answers in three
 * bands, always in this order:
 *
 *   Your cards   copies you own, with where each one is filed
 *   Sets         because "151" and "evolving skies" are sets before they are cards
 *   Catalog      everything else that exists, ready to be added
 *
 * The order is the answer to the question. Your own cards come first because that is what
 * you were asking about; the catalog comes last because it is what you do when the answer
 * was no. Nothing is merged into a single ranked list: a card you own and a card you could
 * own are different kinds of result, and blending them makes "why did that one take two
 * seconds to tap" unanswerable.
 */
@Composable
fun UnifiedSearch(
    visible: Boolean,
    snapshot: CollectionSnapshot,
    lookup: CardLookup,
    browser: CatalogBrowser,
    onDismiss: () -> Unit,
    onOpenCopy: (CopyRow) -> Unit,
    onOpenSet: (CatalogSet) -> Unit,
    onAddLocalCard: (CardBrief) -> Unit,
    onAddRemoteCard: (SearchHit) -> Unit,
    onQuickAddLocal: (CardBrief) -> Unit,
    onQuickAddRemote: (SearchHit) -> Unit,
    importing: Boolean,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    // Cleared on the way out rather than on the way in, so the results are not visibly
    // emptied behind the closing animation.
    LaunchedEffect(visible) {
        if (visible) {
            browser.load()
            // The keyboard should already be up. Opening a search that then asks you to tap
            // the box you just tapped to get here is a wasted gesture on the app's busiest
            // control.
            runCatching { focus.requestFocus() }
        } else {
            query = ""
        }
    }

    LaunchedEffect(query, visible) { if (visible) lookup.onQueryChanged(query) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.fast()) + slideInVertically(Motion.entering()) { it / 12 },
        exit = fadeOut(Motion.leaving()) + slideOutVertically(Motion.leaving()) { it / 12 },
        modifier = modifier,
    ) {
        SearchSurface(
            query = query,
            onQueryChange = { query = it },
            focus = focus,
            snapshot = snapshot,
            lookup = lookup,
            browser = browser,
            onDismiss = onDismiss,
            onOpenCopy = onOpenCopy,
            onOpenSet = onOpenSet,
            onAddLocalCard = onAddLocalCard,
            onAddRemoteCard = onAddRemoteCard,
            onQuickAddLocal = onQuickAddLocal,
            onQuickAddRemote = onQuickAddRemote,
            importing = importing,
        )
    }
}

@Composable
private fun SearchSurface(
    query: String,
    onQueryChange: (String) -> Unit,
    focus: FocusRequester,
    snapshot: CollectionSnapshot,
    lookup: CardLookup,
    browser: CatalogBrowser,
    onDismiss: () -> Unit,
    onOpenCopy: (CopyRow) -> Unit,
    onOpenSet: (CatalogSet) -> Unit,
    onAddLocalCard: (CardBrief) -> Unit,
    onAddRemoteCard: (SearchHit) -> Unit,
    onQuickAddLocal: (CardBrief) -> Unit,
    onQuickAddRemote: (SearchHit) -> Unit,
    importing: Boolean,
) {
    val trimmed = query.trim()
    val searching = trimmed.length >= 2

    val owned = remember(snapshot) { snapshot.copyRows() }
    val yours = remember(owned, trimmed) { if (searching) owned.matching(trimmed) else emptyList() }
    val sets = remember(browser.sets, trimmed) {
        if (searching) browser.sets.matchingSets(trimmed) else emptyList()
    }
    // Cards already in the collection are filtered out of the catalog band: they are listed
    // above, and the same card appearing twice on one screen reads as a bug. Matched on name
    // and printed number rather than on id, because ids only line up for cards that came
    // from this catalog in the first place.
    val known = remember(owned) {
        owned.map { "${it.brief.name.lowercase()}|${it.brief.collectorNumber.lowercase()}" }.toSet()
    }
    val catalog = remember(lookup.results, known) {
        lookup.results.filterNot { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" in known }
    }

    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize().background(Ink.Canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                SearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search cards, sets, anything",
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                Spacer(Modifier.width(Space.md))
                CircleIconButton(
                    icon = AppIcons.Close,
                    contentDescription = "Close search",
                    onClick = onDismiss,
                    size = Size.tap,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.lg,
                    end = Space.lg,
                    top = Space.sm,
                    bottom = Space.huge,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (!searching) {
                    item {
                        EmptyState(
                            icon = AppIcons.Search,
                            title = "Search everything",
                            message = "Your own cards come first, then sets, then every card " +
                                "in the catalog. Two letters is enough to start.",
                        )
                    }
                    return@LazyColumn
                }

                if (importing) item { Note("Fetching card details…", icon = AppIcons.Clock) }

                yourCards(
                    rows = yours,
                    snapshot = snapshot,
                    onOpen = onOpenCopy,
                )

                if (sets.isNotEmpty()) {
                    item {
                        SectionHeader(
                            "Sets · ${sets.size}",
                            Modifier.padding(top = Space.sm),
                            icon = AppIcons.Bookmark,
                        )
                    }
                    items(sets, key = { it.id }) { set ->
                        SetRow(set = set, onClick = { onOpenSet(set) })
                    }
                }

                catalogCards(
                    hits = catalog,
                    searching = lookup.searching,
                    error = lookup.error,
                    importing = importing,
                    query = trimmed,
                    emptyEverywhere = yours.isEmpty() && sets.isEmpty(),
                    onOpen = onAddRemoteCard,
                    onQuickAdd = onQuickAddRemote,
                )
            }
        }
    }
}

/**
 * The cards you already have, one row per printing.
 *
 * Collapsed by printing rather than listed one row per copy. Someone who owns four of a card
 * asked "do I have this" once, and four identical rows is the app answering four times --
 * the count says it better and leaves room for the rows that are actually different.
 *
 * The location is the payload. "Yes" is not a useful answer to "do I have this"; "yes, in
 * the Ember binder, pocket 4" is, and it is the one thing no other search in the app could
 * ever have told you.
 */
private fun LazyListScope.yourCards(
    rows: List<CopyRow>,
    snapshot: CollectionSnapshot,
    onOpen: (CopyRow) -> Unit,
) {
    if (rows.isEmpty()) return

    val grouped = rows.groupBy { it.brief.variantId }
        .map { (id, sameCard) -> OwnedGroup(id, sameCard) }
        .sortedByDescending { it.total.cents }

    item {
        SectionHeader(
            "Your cards · ${rows.size}",
            Modifier.padding(top = Space.xs),
            icon = AppIcons.Cards,
        )
    }
    items(grouped, key = { it.variantId.value }) { group ->
        val first = group.rows.first()
        CardRowItem(
            brief = first.brief,
            // Where it is, which is the whole reason to search your own collection. More
            // than one copy in more than one place says so rather than naming the first.
            caption = if (group.rows.size > 1 && group.places > 1) {
                "${first.brief.collectorNumber} · in ${group.places} places"
            } else {
                "${first.brief.collectorNumber} · ${first.locationLabel}"
            },
            value = group.total.displayOrNull(),
            valueColor = Ink.Gold,
            trend = trendChip(first.brief.change, first.brief.marketValue),
            count = group.rows.size,
            badge = if (group.rows.any { it.copy.forTrade }) "TRADE" else null,
            badgeColor = Ink.Gain,
            onClick = { onOpen(first) },
            modifier = Modifier.animateItem(),
        )
    }
}

private fun LazyListScope.catalogCards(
    hits: List<SearchHit>,
    searching: Boolean,
    error: String?,
    importing: Boolean,
    query: String,
    emptyEverywhere: Boolean,
    onOpen: (SearchHit) -> Unit,
    onQuickAdd: (SearchHit) -> Unit,
) {
    item {
        SectionHeader(
            title = when {
                searching -> "Searching the catalog…"
                hits.isEmpty() -> "Catalog"
                else -> "Catalog · ${hits.size}"
            },
            modifier = Modifier.padding(top = Space.sm),
            icon = AppIcons.Search,
        )
    }

    error?.let { message -> item { Note(message, error = true) } }

    // One block per game, headed only when there is more than one block to tell apart. A
    // search for "Pikachu" really does span both Pokémon catalogs, and an unlabelled run of
    // rows from each does not say which game either belongs to -- a Genetic Apex Pikachu is
    // not something you can sleeve, and nothing prices one.
    val byGame = hits.groupBy { it.game }
    byGame.entries.sortedBy { it.key.ordinal }.forEach { (game, rows) ->
        if (byGame.size > 1) {
            item(key = "catalog-head-${game.name}") {
                SectionHeader("${game.label} · ${rows.size}", Modifier.padding(top = Space.xs))
            }
        }
        items(rows, key = { it.id }) { hit ->
            CardResultRow(
                name = hit.name,
                caption = "${hit.setName} · ${hit.collectorNumber}",
                art = hit.image,
                back = hit.back,
                enabled = !importing,
                onQuickAdd = { onQuickAdd(hit) },
                onClick = { onOpen(hit) },
                modifier = Modifier.animateItem(),
            )
        }
    }

    if (!searching && hits.isEmpty() && error == null) {
        item {
            if (emptyEverywhere) {
                EmptyState(
                    icon = AppIcons.Search,
                    title = "Nothing found",
                    message = "Nothing in your collection or the catalog matches \"$query\".",
                )
            } else {
                Note("Nothing else in the catalog matches \"$query\".")
            }
        }
    }
}

/** Copies of one printing, however many places they are filed in. */
private class OwnedGroup(val variantId: VariantId, val rows: List<CopyRow>) {
    val total: Money = rows.fold(Money.ZERO) { acc, row -> acc + row.value }
    val places: Int = rows.map { it.locationLabel }.distinct().size
}

private fun List<CopyRow>.matching(query: String): List<CopyRow> {
    val q = query.lowercase()
    return filter { it.brief.searchIndex.contains(q) }.take(YOURS_MAX)
}

/** The handful of sets worth offering as a shortcut, best guess first. */
private fun List<CatalogSet>.matchingSets(query: String): List<CatalogSet> {
    val q = query.lowercase()
    return asSequence()
        .filter { it.name.lowercase().contains(q) || it.id.lowercase() == q }
        .sortedWith(
            // A prefix match is what was meant; among equals the newest set is the one most
            // likely being looked for, since that is what people are opening.
            compareByDescending<CatalogSet> { it.name.lowercase().startsWith(q) }
                .thenByDescending { it.releaseDate ?: "" },
        )
        .take(SETS_MAX)
        .toList()
}

/** Enough to answer the question, not so many that the catalog band is pushed off screen. */
private const val YOURS_MAX = 24
private const val SETS_MAX = 5
