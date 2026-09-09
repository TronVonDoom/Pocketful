package app.pocketful.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.CopyId
import app.pocketful.domain.CopyRow
import app.pocketful.domain.Location
import app.pocketful.domain.Money
import app.pocketful.domain.WantRow
import app.pocketful.domain.copyRows
import app.pocketful.domain.wantRows
import app.pocketful.state.display
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SelectionAction
import app.pocketful.ui.components.SelectionConfirm
import app.pocketful.ui.components.SelectionIsland
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

private enum class CardFilter(val label: String) {
    Owned("Owned"),
    Wanted("Wanted"),
    ForTrade("For trade"),
}

private enum class CardSort(val label: String) {
    Value("Value"),
    Name("Name"),
    Set("Set"),
    Location("Binder"),
}

/**
 * Every card, out of the binder and in a list.
 *
 * The binder view answers "what is on this page"; this answers "where is my Charizard"
 * and "what did I actually pay". Those are different enough questions that trying to
 * serve both from the page view is what makes collection apps feel like spreadsheets.
 *
 * Opened from Collections and from Home rather than from the navigation bar: it is a way
 * of looking at storage that already exists, not a fifth place to be.
 *
 * Holding a row starts a multi-select. This is the screen that most needed one: flagging
 * eleven spares for trade after a bulk buy meant eleven sheets opened and closed, and the
 * flag is the one thing about a card you almost never change one at a time.
 */
@Composable
fun CardsScreen(
    snapshot: CollectionSnapshot,
    selection: Set<CopyId>,
    onSelectionChange: (Set<CopyId>) -> Unit,
    onBack: () -> Unit,
    onOpenCopy: (CopyRow) -> Unit,
    onOpenWant: (WantRow) -> Unit,
    onSetForTrade: (Set<CopyId>, Boolean) -> Unit,
    onDeleteSelected: (Set<CopyId>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(CardFilter.Owned) }
    var sort by remember { mutableStateOf(CardSort.Value) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // The want list is made of pockets, not copies, so nothing on it can be selected.
    // Leaving a selection alive behind that switch would leave the island reporting a
    // count against rows that are not on screen and cannot be acted on.
    LaunchedEffect(filter) { if (filter == CardFilter.Wanted) onSelectionChange(emptySet()) }

    val owned = remember(snapshot) { snapshot.copyRows() }
    val wanted = remember(snapshot) { snapshot.wantRows() }

    val visibleOwned = remember(owned, query, sort, filter) {
        owned.filter { filter != CardFilter.ForTrade || it.copy.forTrade }
            .filterAndSort(query, sort)
    }
    val visibleWanted = remember(wanted, query) {
        wanted.filter { it.brief.searchIndex.contains(query.trim().lowercase()) }
            .sortedByDescending { it.targetPrice.cents }
    }

    val ownedValue = remember(owned) { owned.fold(Money.ZERO) { acc, row -> acc + row.value } }
    val unfiledCount = remember(owned) { owned.count { it.copy.location == Location.Unassigned } }
    val tradeCount = remember(owned) { owned.count { it.copy.forTrade } }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Background)) {
        ScreenBackdrop(Ink.Gold, height = 290.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = islandBottomInset()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Your cards",
                    title = "Every card",
                    subtitle = "Everything you own, wherever it is filed",
                    headline = ownedValue.format(),
                    headlineCaption = "at market",
                    leading = { CircleIconButton(AppIcons.ChevronLeft, "Back", onBack, size = 34.dp) },
                    stats = buildList {
                        add(Stat("${owned.size}", "owned"))
                        if (wanted.isNotEmpty()) add(Stat("${wanted.size}", "wanted", Ink.Wanted))
                        if (tradeCount > 0) add(Stat("$tradeCount", "for trade", Ink.Gain))
                        if (unfiledCount > 0) add(Stat("$unfiledCount", "unfiled"))
                    },
                )
            }

            item {
                Column(Modifier.padding(bottom = 4.dp)) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search your cards",
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CardFilter.entries.forEach { option ->
                            ChoiceChip(
                                label = option.label,
                                selected = option == filter,
                                onClick = { filter = option },
                                accent = when (option) {
                                    CardFilter.Wanted -> Ink.Wanted
                                    CardFilter.ForTrade -> Ink.Gain
                                    CardFilter.Owned -> Ink.Accent
                                },
                            )
                        }
                        // Sort applies to any list of copies, so it stays visible for the
                        // trade filter too -- only the want list, which has its own fixed
                        // order, drops it.
                        if (filter != CardFilter.Wanted) {
                            Box(Modifier.width(1.dp).height(34.dp).background(Ink.OutlineSoft))
                            CardSort.entries.forEach { option ->
                                ChoiceChip(
                                    label = option.label,
                                    selected = option == sort,
                                    onClick = { sort = option },
                                    accent = Ink.Gold,
                                )
                            }
                        }
                    }
                }
            }

            if (filter != CardFilter.Wanted) {
                if (visibleOwned.isEmpty()) {
                    item {
                        if (filter == CardFilter.ForTrade && query.isBlank()) {
                            EmptyState(
                                icon = AppIcons.Trade,
                                title = "Nothing up for trade",
                                message = "Open a card you own and switch on " +
                                    "\"Up for trade\" to put it on the table.",
                            )
                        } else {
                            NoResults(query, "cards")
                        }
                    }
                } else {
                    tileRows(items = visibleOwned, keyPrefix = "owned", key = { it.copy.id.value }) { row ->
                        CardTile(
                            brief = row.brief,
                            // The set name is already implied by the collector number, and
                            // repeating it next to a binder that shares its name read as
                            // "Base Set · Base Set · Pocket 4".
                            caption = "${row.brief.collectorNumber} · ${row.locationLabel}",
                            value = row.value.displayOrNull(),
                            valueColor = Ink.Gold,
                            badge = if (row.copy.forTrade) {
                                "TRADE"
                            } else {
                                row.copy.grade?.label ?: row.copy.condition.short.takeIf { it != "NM" }
                            },
                            badgeColor = if (row.copy.forTrade) Ink.Gain else Ink.TextTertiary,
                            selected = row.copy.id in selection,
                            onClick = {
                                if (selection.isEmpty()) onOpenCopy(row)
                                else onSelectionChange(selection.toggled(row.copy.id))
                            },
                            onLongClick = { onSelectionChange(selection.toggled(row.copy.id)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                if (visibleWanted.isEmpty()) {
                    item {
                        if (query.isBlank()) {
                            EmptyState(
                                icon = AppIcons.Target,
                                title = "Nothing on the want list",
                                message = "Mark a pocket as wanted from inside a binder and it shows up here.",
                            )
                        } else {
                            NoResults(query, "wants")
                        }
                    }
                } else {
                    tileRows(
                        items = visibleWanted,
                        keyPrefix = "wanted",
                        key = { "${it.binderId.value}-${it.ordinal}" },
                    ) { row ->
                        CardTile(
                            brief = row.brief,
                            caption = "${row.binderName} · Pocket ${row.ordinal + 1}",
                            value = row.targetPrice.displayOrNull(),
                            valueColor = Ink.Wanted,
                            badge = "WANT",
                            badgeColor = Ink.Wanted,
                            ghosted = true,
                            onClick = { onOpenWant(row) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (selection.isNotEmpty()) {
            // What "Trade" does is decided by what is already flagged: if every selected
            // card is on the table the button takes them off, otherwise it puts them all
            // on. A single button that always means "make these the same" is easier to
            // predict than two that are each wrong half the time.
            val allForTrade = remember(selection, snapshot) {
                selection.isNotEmpty() && selection.all { snapshot.copies[it]?.forTrade == true }
            }

            SelectionIsland(
                count = selection.size,
                onClear = {
                    confirmingDelete = false
                    onSelectionChange(emptySet())
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                confirm = if (!confirmingDelete) {
                    null
                } else {
                    {
                        SelectionConfirm(
                            message = "Delete ${selection.size} " +
                                (if (selection.size == 1) "card" else "cards") +
                                " from your collection? Any pocket holding one becomes empty.",
                            confirmLabel = "Delete",
                            onCancel = { confirmingDelete = false },
                            onConfirm = {
                                confirmingDelete = false
                                onDeleteSelected(selection)
                                onSelectionChange(emptySet())
                            },
                        )
                    }
                },
            ) {
                SelectionAction(
                    icon = AppIcons.Trade,
                    label = if (allForTrade) "Untrade" else "Trade",
                    onClick = {
                        onSetForTrade(selection, !allForTrade)
                        onSelectionChange(emptySet())
                    },
                    tint = if (allForTrade) Ink.TextSecondary else Ink.Gain,
                )
                SelectionAction(
                    icon = AppIcons.Trash,
                    label = "Delete",
                    onClick = { confirmingDelete = true },
                    tint = Ink.Loss,
                )
            }
        }
    }
}

/** Adds or removes one id. The whole vocabulary a selection needs. */
private fun Set<CopyId>.toggled(id: CopyId): Set<CopyId> =
    if (id in this) this - id else this + id

@Composable
private fun NoResults(query: String, noun: String) {
    EmptyState(
        icon = AppIcons.Search,
        title = "No $noun found",
        message = if (query.isBlank()) {
            "Add cards to a binder pocket and they will be listed here."
        } else {
            "Nothing matches \"$query\"."
        },
    )
}

private fun List<CopyRow>.filterAndSort(query: String, sort: CardSort): List<CopyRow> {
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) this else filter { it.brief.searchIndex.contains(q) }
    return when (sort) {
        CardSort.Value -> filtered.sortedByDescending { it.value.cents }
        CardSort.Name -> filtered.sortedBy { it.brief.name.lowercase() }
        CardSort.Set -> filtered.sortedWith(
            compareBy({ it.brief.setName }, { it.brief.numericOrder }, { it.brief.number }),
        )
        // Unfiled copies have no binder to sort under, so they collect at the end rather
        // than at the top where they would look like the most important rows.
        CardSort.Location -> filtered.sortedWith(
            compareBy<CopyRow> { it.holderName == null }
                .thenBy { it.holderName.orEmpty() }
                .thenBy { it.ordinal ?: Int.MAX_VALUE },
        )
    }
}
