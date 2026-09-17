package app.pocketful.ui.collections

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pocketful.domain.Binder
import app.pocketful.domain.BinderId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerId
import app.pocketful.domain.CopyId
import app.pocketful.domain.CopyRow
import app.pocketful.domain.Location
import app.pocketful.domain.WantRow
import app.pocketful.domain.copyRows
import app.pocketful.domain.marketTotal
import app.pocketful.domain.wantRows
import app.pocketful.state.display
import app.pocketful.state.displayOrDash
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AddTile
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.BinderCover
import app.pocketful.ui.components.CardRowItem
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.ConfirmToggle
import app.pocketful.ui.components.ContainerCover
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.GainChip
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.Headline
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.SelectionAction
import app.pocketful.ui.components.SelectionConfirm
import app.pocketful.ui.components.SelectionIsland
import app.pocketful.ui.components.StorageTile
import app.pocketful.ui.components.TileGap
import app.pocketful.ui.components.TilePair
import app.pocketful.ui.components.TopBar
import app.pocketful.ui.components.ViewTabs
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.components.trendChip
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space

/**
 * Which slice of the collection is on screen.
 *
 * These were four separate places before: a Collections tab holding binders and boxes, an
 * "All cards" screen reached from a nameless icon, and a want list that existed only as a
 * filter chip inside that screen. All four are views of one thing -- the cards you have and
 * the cards you are chasing -- and splitting them across the app meant "where is my
 * Charizard" and "what binders do I own" were answered in different places by different
 * screens that did not look related.
 */
enum class CollectionView(val label: String) {
    Binders("Binders"),
    Boxes("Boxes"),
    Cards("Cards"),
    Wants("Wants"),
}

/** How a list of cards is laid out. Remembered, because it is a lasting preference. */
enum class CardLayout { Grid, Rows }

private enum class CardSort(val label: String) {
    Value("Value"),
    Name("Name"),
    Set("Set"),
    Location("Place"),
}

/**
 * What a multi-select on the storage views is holding.
 *
 * Binders and containers are kept in separate sets rather than one set of some common id
 * type. They are deleted through different calls and count their contents differently, so a
 * single bag would only be unpacked again at every use -- and an id that could mean either
 * is exactly the kind of thing that eventually deletes the wrong object.
 */
data class StorageSelection(
    val binders: Set<BinderId> = emptySet(),
    val containers: Set<ContainerId> = emptySet(),
) {
    val size: Int get() = binders.size + containers.size
    val isActive: Boolean get() = size > 0

    fun toggle(id: BinderId): StorageSelection =
        copy(binders = if (id in binders) binders - id else binders + id)

    fun toggle(id: ContainerId): StorageSelection =
        copy(containers = if (id in containers) containers - id else containers + id)

    companion object {
        val EMPTY = StorageSelection()
    }
}

/**
 * Everything you have, and everything you are still after.
 *
 * One screen with four views rather than three screens and a filter chip. The argument for
 * the split was that binders and loose cards are different kinds of thing; the argument
 * against it is that a person looking for a card does not know or care which of those it is
 * filed as, and was being asked to guess which screen to open before they were allowed to
 * look.
 *
 * The view survives leaving and coming back, and Home links straight into a specific one --
 * tapping "312 cards" lands on Cards, tapping "14 wanted" lands on Wants -- which is what
 * makes folding them in an improvement rather than a burial.
 *
 * Holding any tile starts a selection. Once one is running, a tap picks rather than opens --
 * the alternative, making the user aim for a checkbox that appeared under their thumb, is
 * how a five-binder cleanup becomes a five-binder mis-tap.
 */
@Composable
fun CollectionScreen(
    snapshot: CollectionSnapshot,
    view: CollectionView,
    onViewChange: (CollectionView) -> Unit,
    layout: CardLayout,
    onLayoutChange: (CardLayout) -> Unit,
    storageSelection: StorageSelection,
    onStorageSelectionChange: (StorageSelection) -> Unit,
    cardSelection: Set<CopyId>,
    onCardSelectionChange: (Set<CopyId>) -> Unit,
    onOpenBinder: (Binder) -> Unit,
    onOpenContainer: (Container) -> Unit,
    onCreateBinder: () -> Unit,
    onCreateContainer: () -> Unit,
    onOpenCopy: (CopyRow) -> Unit,
    onOpenWant: (WantRow) -> Unit,
    onOpenSearch: () -> Unit,
    onSetForTrade: (Set<CopyId>, Boolean) -> Unit,
    onDeleteCopies: (Set<CopyId>) -> Unit,
    onDeleteStorage: (StorageSelection, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = remember(snapshot) { snapshot.summarizeAll() }
    val owned = remember(snapshot) { snapshot.copyRows() }
    val wanted = remember(snapshot) { snapshot.wantRows() }
    val unfiled = remember(owned) { owned.count { it.copy.location == Location.Unassigned } }
    val tradeCount = remember(owned) { owned.count { it.copy.forTrade } }

    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(CardSort.Value) }
    var onlyTrade by remember { mutableStateOf(false) }
    var onlyUnfiled by remember { mutableStateOf(false) }
    var confirmingStorageDelete by remember { mutableStateOf(false) }
    var deleteCards by remember { mutableStateOf(false) }
    var confirmingCardDelete by remember { mutableStateOf(false) }

    // A selection belongs to the view that made it. Carrying a set of copy ids into the
    // binder grid would leave the bar counting rows that are not on screen and cannot be
    // acted on by anything the bar offers.
    LaunchedEffect(view) {
        onCardSelectionChange(emptySet())
        onStorageSelectionChange(StorageSelection.EMPTY)
        confirmingCardDelete = false
        confirmingStorageDelete = false
    }

    val visibleCards = remember(owned, query, sort, onlyTrade, onlyUnfiled) {
        owned.asSequence()
            .filter { !onlyTrade || it.copy.forTrade }
            .filter { !onlyUnfiled || it.copy.location == Location.Unassigned }
            .toList()
            .filterAndSort(query, sort)
    }
    val visibleWants = remember(wanted, query) {
        val q = query.trim().lowercase()
        wanted.filter { q.isEmpty() || it.brief.searchIndex.contains(q) }
            .sortedByDescending { it.targetPrice.cents }
    }

    // How many cards the current storage selection is actually holding, so the question
    // asked before deleting names a real number rather than a vague "and its contents".
    val heldCards = remember(storageSelection, snapshot) {
        val inBinders = storageSelection.binders.sumOf { id ->
            snapshot.binder(id)?.let { binder -> snapshot.summarize(binder).ownedCount } ?: 0
        }
        val inContainers = storageSelection.containers.sumOf { id -> snapshot.container(id)?.count ?: 0 }
        inBinders + inContainers
    }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Canvas)) {
        ScreenBackdrop(
            accent = when (view) {
                CollectionView.Cards -> Ink.Gold
                CollectionView.Wants -> Ink.Wanted
                else -> Ink.Accent
            },
            height = 320.dp,
        )

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                title = "Collection",
                actions = {
                    HeaderAction(AppIcons.Search, "Search everything", onOpenSearch)
                    // Only where it can do anything. A layout toggle above a grid of binders
                    // would be a control that changes nothing, which teaches people that the
                    // other controls might too.
                    if (view == CollectionView.Cards || view == CollectionView.Wants) {
                        HeaderAction(
                            icon = if (layout == CardLayout.Grid) AppIcons.Rows else AppIcons.Grid,
                            contentDescription = if (layout == CardLayout.Grid) {
                                "Show as a list"
                            } else {
                                "Show as a grid"
                            },
                            onClick = {
                                onLayoutChange(
                                    if (layout == CardLayout.Grid) CardLayout.Rows else CardLayout.Grid,
                                )
                            },
                        )
                    }
                },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.lg,
                    end = Space.lg,
                    top = Space.sm,
                    bottom = islandBottomInset(),
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                item {
                    Headline(
                        value = total.marketValue.displayOrDash(total.currency),
                        label = "Total value",
                        caption = buildString {
                            val places = snapshot.binders.size + snapshot.containers.size
                            append("${total.ownedCount} ")
                            append(if (total.ownedCount == 1) "card" else "cards")
                            append(" across $places ")
                            append(if (places == 1) "place" else "places")
                            // Same rule as the portfolio: a total that cannot include every
                            // currency says so rather than letting the headline stand for
                            // more than it covers.
                            total.alsoLabel?.let { append(" · also $it") }
                        },
                        trailing = { GainChip(total) },
                    )
                }

                item {
                    ViewTabs(
                        options = CollectionView.entries,
                        selected = view,
                        onSelect = onViewChange,
                        label = { it.label },
                        count = {
                            when (it) {
                                CollectionView.Binders -> snapshot.binders.size
                                CollectionView.Boxes -> snapshot.containers.size
                                CollectionView.Cards -> owned.size
                                CollectionView.Wants -> wanted.size
                            }
                        },
                        modifier = Modifier.padding(top = Space.sm, bottom = Space.xs),
                    )
                }

                when (view) {
                    CollectionView.Binders -> binderGrid(
                        snapshot = snapshot,
                        selection = storageSelection,
                        onSelectionChange = onStorageSelectionChange,
                        onOpen = onOpenBinder,
                        onCreate = onCreateBinder,
                    )

                    CollectionView.Boxes -> containerGrid(
                        snapshot = snapshot,
                        selection = storageSelection,
                        onSelectionChange = onStorageSelectionChange,
                        onOpen = onOpenContainer,
                        onCreate = onCreateContainer,
                    )

                    CollectionView.Cards -> {
                        item {
                            CardControls(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = "Search your cards",
                                sort = sort,
                                onSortChange = { sort = it },
                                tradeCount = tradeCount,
                                onlyTrade = onlyTrade,
                                onTradeChange = { onlyTrade = it },
                                unfiledCount = unfiled,
                                onlyUnfiled = onlyUnfiled,
                                onUnfiledChange = { onlyUnfiled = it },
                            )
                        }
                        cardList(
                            rows = visibleCards,
                            layout = layout,
                            selection = cardSelection,
                            onSelectionChange = onCardSelectionChange,
                            onOpen = onOpenCopy,
                            query = query,
                            filtered = onlyTrade || onlyUnfiled,
                        )
                    }

                    CollectionView.Wants -> {
                        item {
                            SearchField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = "Search your want list",
                                modifier = Modifier.padding(bottom = Space.xs),
                            )
                        }
                        wantList(
                            rows = visibleWants,
                            layout = layout,
                            onOpen = onOpenWant,
                            query = query,
                        )
                    }
                }
            }
        }

        if (storageSelection.isActive) {
            SelectionIsland(
                count = storageSelection.size,
                onClear = {
                    confirmingStorageDelete = false
                    onStorageSelectionChange(StorageSelection.EMPTY)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                confirm = if (!confirmingStorageDelete) {
                    null
                } else {
                    {
                        SelectionConfirm(
                            message = deleteMessage(storageSelection, heldCards),
                            confirmLabel = "Delete",
                            onCancel = { confirmingStorageDelete = false },
                            onConfirm = {
                                confirmingStorageDelete = false
                                onDeleteStorage(storageSelection, deleteCards)
                                onStorageSelectionChange(StorageSelection.EMPTY)
                            },
                            extra = if (heldCards == 0) {
                                null
                            } else {
                                {
                                    ConfirmToggle(
                                        title = "Delete the cards too",
                                        description = if (deleteCards) {
                                            "All $heldCards leave your collection for good."
                                        } else {
                                            "All $heldCards stay, and become unfiled."
                                        },
                                        checked = deleteCards,
                                        onCheckedChange = { deleteCards = it },
                                    )
                                }
                            },
                        )
                    }
                },
            ) {
                SelectionAction(
                    icon = AppIcons.Trash,
                    label = "Delete",
                    onClick = { confirmingStorageDelete = true },
                    tint = Ink.Loss,
                )
            }
        }

        if (cardSelection.isNotEmpty()) {
            // What "Trade" does is decided by what is already flagged: if every selected card
            // is on the table the button takes them off, otherwise it puts them all on. A
            // single button that always means "make these the same" is easier to predict than
            // two that are each wrong half the time.
            val allForTrade = remember(cardSelection, snapshot) {
                cardSelection.isNotEmpty() && cardSelection.all { snapshot.copies[it]?.forTrade == true }
            }

            SelectionIsland(
                count = cardSelection.size,
                onClear = {
                    confirmingCardDelete = false
                    onCardSelectionChange(emptySet())
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                confirm = if (!confirmingCardDelete) {
                    null
                } else {
                    {
                        SelectionConfirm(
                            message = "Delete ${cardSelection.size} " +
                                (if (cardSelection.size == 1) "card" else "cards") +
                                " from your collection? Any pocket holding one becomes empty.",
                            confirmLabel = "Delete",
                            onCancel = { confirmingCardDelete = false },
                            onConfirm = {
                                confirmingCardDelete = false
                                onDeleteCopies(cardSelection)
                                onCardSelectionChange(emptySet())
                            },
                        )
                    }
                },
            ) {
                SelectionAction(
                    icon = AppIcons.Trade,
                    label = if (allForTrade) "Untrade" else "Trade",
                    onClick = {
                        onSetForTrade(cardSelection, !allForTrade)
                        onCardSelectionChange(emptySet())
                    },
                    tint = if (allForTrade) Ink.TextSecondary else Ink.Gain,
                )
                SelectionAction(
                    icon = AppIcons.Trash,
                    label = "Delete",
                    onClick = { confirmingCardDelete = true },
                    tint = Ink.Loss,
                )
            }
        }
    }
}

// ------------------------------------------------------------------- storage

private fun LazyListScope.binderGrid(
    snapshot: CollectionSnapshot,
    selection: StorageSelection,
    onSelectionChange: (StorageSelection) -> Unit,
    onOpen: (Binder) -> Unit,
    onCreate: () -> Unit,
) {
    if (snapshot.binders.isEmpty()) {
        item {
            EmptyState(
                icon = AppIcons.Binders,
                title = "No binders yet",
                message = "A binder is a page of pockets you turn. Make one for the set you " +
                    "are working on and fill it in as you find cards.",
                action = { AppButton("New binder", onCreate, icon = AppIcons.Plus) },
            )
        }
        return
    }

    // The add tile rides along in the grid as one more entry, so it lands in the empty half
    // of the last row instead of forcing a row of its own.
    val rows = (snapshot.binders.map<Binder, Any> { it } + AddSlot).chunked(2)
    items(rows.size, key = { index -> "binder-row-$index" }) { index ->
        TilePair(Modifier.animateItem()) {
            rows[index].forEach { entry ->
                when (entry) {
                    is Binder -> BinderTile(
                        binder = entry,
                        snapshot = snapshot,
                        selected = entry.id in selection.binders,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (selection.isActive) onSelectionChange(selection.toggle(entry.id))
                            else onOpen(entry)
                        },
                        onLongClick = { onSelectionChange(selection.toggle(entry.id)) },
                    )
                    else -> AddTile(
                        icon = AppIcons.Plus,
                        label = "New binder",
                        onClick = onCreate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (rows[index].size == 1) TileGap()
        }
    }
}

private fun LazyListScope.containerGrid(
    snapshot: CollectionSnapshot,
    selection: StorageSelection,
    onSelectionChange: (StorageSelection) -> Unit,
    onOpen: (Container) -> Unit,
    onCreate: () -> Unit,
) {
    if (snapshot.containers.isEmpty()) {
        item {
            EmptyState(
                icon = AppIcons.Box,
                title = "No boxes yet",
                message = "Boxes, decks and slab cases hold the cards that never make it " +
                    "into a pocket. A box is an ordered pile with a label, not a grid.",
                action = { AppButton("New box", onCreate, icon = AppIcons.Plus) },
            )
        }
        return
    }

    val rows = (snapshot.containers.map<Container, Any> { it } + AddSlot).chunked(2)
    items(rows.size, key = { index -> "container-row-$index" }) { index ->
        TilePair(Modifier.animateItem()) {
            rows[index].forEach { entry ->
                when (entry) {
                    is Container -> ContainerTile(
                        container = entry,
                        snapshot = snapshot,
                        selected = entry.id in selection.containers,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (selection.isActive) onSelectionChange(selection.toggle(entry.id))
                            else onOpen(entry)
                        },
                        onLongClick = { onSelectionChange(selection.toggle(entry.id)) },
                    )
                    else -> AddTile(
                        icon = AppIcons.Plus,
                        label = "New box",
                        onClick = onCreate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (rows[index].size == 1) TileGap()
        }
    }
}

// --------------------------------------------------------------------- cards

/**
 * The search box, the ordering, and the two filters worth having.
 *
 * Filters and sorts are deliberately different shapes. The filters are toggles that carry
 * their own count -- "Trade · 11" is both the switch and the reason to flip it -- while the
 * sort is one choice among four. Putting them in one undifferentiated strip of chips, which
 * is what this screen used to do, meant a row where some chips were exclusive and some were
 * not, with nothing to say which was which.
 */
@Composable
private fun CardControls(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    sort: CardSort,
    onSortChange: (CardSort) -> Unit,
    tradeCount: Int,
    onlyTrade: Boolean,
    onTradeChange: (Boolean) -> Unit,
    unfiledCount: Int,
    onlyUnfiled: Boolean,
    onUnfiledChange: (Boolean) -> Unit,
) {
    Column(Modifier.padding(bottom = Space.xs)) {
        SearchField(value = query, onValueChange = onQueryChange, placeholder = placeholder)

        if (tradeCount > 0 || unfiledCount > 0) {
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (tradeCount > 0) {
                    ChoiceChip(
                        label = "For trade",
                        count = tradeCount,
                        selected = onlyTrade,
                        accent = Ink.Gain,
                        icon = AppIcons.Trade,
                        onClick = { onTradeChange(!onlyTrade) },
                    )
                }
                if (unfiledCount > 0) {
                    ChoiceChip(
                        label = "Unfiled",
                        count = unfiledCount,
                        selected = onlyUnfiled,
                        accent = Ink.Gold,
                        icon = AppIcons.Box,
                        onClick = { onUnfiledChange(!onlyUnfiled) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.md))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                AppIcons.Sort,
                null,
                Modifier.width(Size.iconSm).height(Size.iconSm),
                tint = Ink.TextTertiary,
            )
            Spacer(Modifier.width(Space.xxs))
            CardSort.entries.forEach { option ->
                ChoiceChip(
                    label = option.label,
                    selected = option == sort,
                    onClick = { onSortChange(option) },
                    accent = Ink.Gold,
                )
            }
        }
    }
}

private fun LazyListScope.cardList(
    rows: List<CopyRow>,
    layout: CardLayout,
    selection: Set<CopyId>,
    onSelectionChange: (Set<CopyId>) -> Unit,
    onOpen: (CopyRow) -> Unit,
    query: String,
    filtered: Boolean,
) {
    if (rows.isEmpty()) {
        item {
            EmptyState(
                icon = if (query.isBlank() && !filtered) AppIcons.Cards else AppIcons.Search,
                title = when {
                    query.isNotBlank() -> "No cards found"
                    filtered -> "Nothing matches those filters"
                    else -> "No cards yet"
                },
                message = when {
                    query.isNotBlank() -> "Nothing matches \"$query\"."
                    filtered -> "Turn a filter off to see the rest of your cards."
                    else -> "Tap the plus in the bar below to record your first card."
                },
            )
        }
        return
    }

    when (layout) {
        CardLayout.Grid -> tileRows(items = rows, keyPrefix = "owned", key = { it.copy.id.value }) { row ->
            CardTile(
                brief = row.brief,
                // The set name is already implied by the collector number, and repeating it
                // next to a binder that shares its name read as "Base Set · Base Set · 4".
                caption = "${row.brief.collectorNumber} · ${row.locationLabel}",
                value = row.value.displayOrNull(),
                valueColor = Ink.Gold,
                trend = trendChip(row.brief.change, row.brief.marketValue),
                badge = if (row.copy.forTrade) {
                    "TRADE"
                } else {
                    row.copy.grade?.label ?: row.copy.condition.short.takeIf { it != "NM" }
                },
                badgeColor = if (row.copy.forTrade) Ink.Gain else Ink.TextTertiary,
                selected = row.copy.id in selection,
                onClick = {
                    if (selection.isEmpty()) onOpen(row)
                    else onSelectionChange(selection.toggled(row.copy.id))
                },
                onLongClick = { onSelectionChange(selection.toggled(row.copy.id)) },
                modifier = Modifier.weight(1f),
            )
        }

        CardLayout.Rows -> items(rows, key = { it.copy.id.value }) { row ->
            CardRowItem(
                brief = row.brief,
                caption = "${row.brief.collectorNumber} · ${row.locationLabel}",
                value = row.value.displayOrNull(),
                valueColor = Ink.Gold,
                trend = trendChip(row.brief.change, row.brief.marketValue),
                badge = if (row.copy.forTrade) {
                    "TRADE"
                } else {
                    row.copy.grade?.label ?: row.copy.condition.short.takeIf { it != "NM" }
                },
                badgeColor = if (row.copy.forTrade) Ink.Gain else Ink.TextTertiary,
                selected = row.copy.id in selection,
                onClick = {
                    if (selection.isEmpty()) onOpen(row)
                    else onSelectionChange(selection.toggled(row.copy.id))
                },
                onLongClick = { onSelectionChange(selection.toggled(row.copy.id)) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

private fun LazyListScope.wantList(
    rows: List<WantRow>,
    layout: CardLayout,
    onOpen: (WantRow) -> Unit,
    query: String,
) {
    if (rows.isEmpty()) {
        item {
            EmptyState(
                icon = AppIcons.Target,
                title = if (query.isBlank()) "Nothing on the want list" else "No wants found",
                message = if (query.isBlank()) {
                    "A want is a pocket held open for a card you do not have yet. Mark one " +
                        "inside a binder, or build a binder from a set and start with the " +
                        "whole checklist."
                } else {
                    "Nothing matches \"$query\"."
                },
            )
        }
        return
    }

    when (layout) {
        CardLayout.Grid -> tileRows(
            items = rows,
            keyPrefix = "want",
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
                onClick = { onOpen(row) },
                modifier = Modifier.weight(1f),
            )
        }

        CardLayout.Rows -> items(rows, key = { "${it.binderId.value}-${it.ordinal}" }) { row ->
            CardRowItem(
                brief = row.brief,
                caption = "${row.binderName} · Pocket ${row.ordinal + 1}",
                value = row.targetPrice.displayOrNull(),
                valueColor = Ink.Wanted,
                badge = "WANT",
                badgeColor = Ink.Wanted,
                ghosted = true,
                onClick = { onOpen(row) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

// --------------------------------------------------------------------- pieces

/** Adds or removes one id. The whole vocabulary a selection needs. */
private fun Set<CopyId>.toggled(id: CopyId): Set<CopyId> =
    if (id in this) this - id else this + id

/** What is about to happen, counted rather than described. */
private fun deleteMessage(selection: StorageSelection, heldCards: Int): String {
    val what = buildList {
        if (selection.binders.isNotEmpty()) {
            add(if (selection.binders.size == 1) "1 binder" else "${selection.binders.size} binders")
        }
        if (selection.containers.isNotEmpty()) {
            add(if (selection.containers.size == 1) "1 box" else "${selection.containers.size} boxes")
        }
    }.joinToString(" and ")
    return if (heldCards == 0) "Delete $what? Nothing is filed in them."
    else "Delete $what, holding $heldCards ${if (heldCards == 1) "card" else "cards"}?"
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

/** Stands in for the "add one more" tile while a grid row is being assembled. */
private object AddSlot

@Composable
private fun BinderTile(
    binder: Binder,
    snapshot: CollectionSnapshot,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val summary = remember(binder, snapshot) { snapshot.summarize(binder) }
    val spine = Color(binder.spineColor)

    StorageTile(
        cover = { BinderCover(binder, width = 34.dp) },
        name = binder.name,
        // The grid is drawn on the cover, so the caption does not spell it out again --
        // "9-pocket · 3x3 · 12/40" was two facts and a picture of the third.
        caption = "${binder.layout.displayName} · ${summary.ownedCount}/${binder.capacity}",
        value = summary.marketValue.displayOrDash(summary.currency),
        accent = spine,
        gainLabel = summary.gainPercentLabel,
        gainPositive = summary.unrealizedGain.cents >= 0,
        fillFraction = if (binder.capacity == 0) 0f else summary.ownedCount.toFloat() / binder.capacity,
        // Uppercase like every other status label in the app. "3 Wanted" was the only badge
        // anywhere written in sentence case, which made it read as a caption that had
        // wandered into a badge rather than as the same kind of mark.
        badge = if (summary.wantedCount > 0) "${summary.wantedCount} WANTED" else null,
        badgeColor = Ink.Wanted,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        modifier = modifier,
    )
}

@Composable
private fun ContainerTile(
    container: Container,
    snapshot: CollectionSnapshot,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val summary = remember(container, snapshot) { snapshot.summarize(container) }

    StorageTile(
        cover = { ContainerCover(container, width = 34.dp) },
        name = container.name,
        caption = container.kind.label + " · " +
            if (container.count == 1) "1 card" else "${container.count} cards",
        value = summary.marketValue.displayOrDash(summary.currency),
        accent = Color(container.color),
        gainLabel = summary.gainPercentLabel,
        gainPositive = summary.unrealizedGain.cents >= 0,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        modifier = modifier,
    )
}
