package app.pocketful.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
import app.pocketful.state.display
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AddTile
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.BinderCover
import app.pocketful.ui.components.ConfirmToggle
import app.pocketful.ui.components.ContainerCover
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.HeaderChipAction
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SelectionAction
import app.pocketful.ui.components.SelectionConfirm
import app.pocketful.ui.components.SelectionIsland
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.StorageTile
import app.pocketful.ui.components.TileGap
import app.pocketful.ui.components.TilePair
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * What a multi-select on this screen is holding.
 *
 * Binders and containers are kept in separate sets rather than one set of some common id
 * type. They are deleted through different calls and count their contents differently, so
 * a single bag would only be unpacked again at every use -- and an id that could mean
 * either is exactly the kind of thing that eventually deletes the wrong object.
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
 * Everything the collection is kept in.
 *
 * Binders were the whole of this screen once, which quietly meant a card had to be in a
 * binder to be anywhere at all. Cards live in boxes, decks and slab cases too, and those
 * are not lesser storage -- so both kinds are listed here, in the same shape of tile, and
 * the screen is named for what it holds rather than for one of the things it holds.
 *
 * This is also where the flat card list is reached from. A list of every card is a way of
 * looking at what this screen holds rather than a separate place to be, so it opens from
 * here instead of occupying a slot in the navigation bar.
 *
 * Holding any tile starts a selection. Once one is running, a tap picks rather than opens
 * -- the alternative, making the user aim for a checkbox that appeared under their thumb,
 * is how a five-binder cleanup becomes a five-binder mis-tap.
 */
@Composable
fun CollectionsScreen(
    snapshot: CollectionSnapshot,
    selection: StorageSelection,
    onSelectionChange: (StorageSelection) -> Unit,
    onOpenBinder: (Binder) -> Unit,
    onOpenContainer: (Container) -> Unit,
    onCreateBinder: () -> Unit,
    onCreateContainer: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenTrade: () -> Unit,
    onDeleteSelected: (StorageSelection, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = remember(snapshot) { snapshot.summarizeAll() }
    val unfiled = remember(snapshot) { snapshot.unfiledCopies().size }
    val tradeCount = remember(snapshot) { snapshot.copies.values.count { it.forTrade } }

    var confirmingDelete by remember { mutableStateOf(false) }
    var deleteCards by remember { mutableStateOf(false) }

    // How many cards the current selection is actually holding, so the question asked
    // before deleting names a real number rather than a vague "and its contents".
    val heldCards = remember(selection, snapshot) {
        val inBinders = selection.binders.sumOf { id ->
            snapshot.binder(id)?.let { binder -> snapshot.summarize(binder).ownedCount } ?: 0
        }
        val inContainers = selection.containers.sumOf { id -> snapshot.container(id)?.count ?: 0 }
        inBinders + inContainers
    }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Background)) {
        ScreenBackdrop(Ink.Accent, height = 280.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = islandBottomInset()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Collection",
                    centered = true,
                    headline = total.marketValue.format(),
                    headlineCaption = "stored across " +
                        "${snapshot.binders.size + snapshot.containers.size} " +
                        (if (snapshot.binders.size + snapshot.containers.size == 1) "place" else "places"),
                    summary = total,
                    stats = buildList {
                        add(Stat("${total.ownedCount}", "cards"))
                        add(Stat("${snapshot.binders.size}", "binders"))
                        add(Stat("${snapshot.containers.size}", "containers"))
                        if (total.wantedCount > 0) {
                            add(Stat("${total.wantedCount}", "wanted", Ink.Wanted))
                            total.costToComplete.displayOrNull()?.let {
                                add(Stat(it, "to finish", Ink.Gold))
                            }
                        }
                        if (unfiled > 0) add(Stat("$unfiled", "unfiled"))
                    },
                )
            }

            // The two ways of looking across all storage rather than into one piece of
            // it. Kept as one row directly under the header so they read as views over
            // what follows, not as a third and fourth kind of storage.
            item {
                TilePair(Modifier.padding(bottom = 2.dp)) {
                    HeaderChipAction(
                        icon = AppIcons.Cards,
                        label = "All cards",
                        onClick = onOpenCards,
                        modifier = Modifier.weight(1f),
                    )
                    HeaderChipAction(
                        icon = AppIcons.Trade,
                        label = if (tradeCount > 0) "Trade · $tradeCount" else "Trade",
                        onClick = onOpenTrade,
                        modifier = Modifier.weight(1f),
                        tint = if (tradeCount > 0) Ink.Gain else Ink.TextSecondary,
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Binders · ${snapshot.binders.size}",
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }

            if (snapshot.binders.isEmpty()) {
                item {
                    EmptyState(
                        icon = AppIcons.Binders,
                        title = "No binders yet",
                        message = "Create a binder, set its page shape, and start filling pockets.",
                        action = { AppButton("New binder", onCreateBinder, icon = AppIcons.Plus) },
                    )
                }
            } else {
                // The add tile rides along in the grid as one more entry, so it lands in
                // the empty half of the last row instead of forcing a row of its own.
                val binderRows = (snapshot.binders.map<Binder, Any> { it } + AddSlot).chunked(2)
                items(binderRows.size, key = { index -> "binder-row-$index" }) { index ->
                    TilePair {
                        binderRows[index].forEach { entry ->
                            when (entry) {
                                is Binder -> BinderTile(
                                    binder = entry,
                                    snapshot = snapshot,
                                    selected = entry.id in selection.binders,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (selection.isActive) onSelectionChange(selection.toggle(entry.id))
                                        else onOpenBinder(entry)
                                    },
                                    onLongClick = { onSelectionChange(selection.toggle(entry.id)) },
                                )
                                else -> AddTile(
                                    icon = AppIcons.Plus,
                                    label = "New binder",
                                    onClick = onCreateBinder,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (binderRows[index].size == 1) TileGap()
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Containers · ${snapshot.containers.size}",
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }

            if (snapshot.containers.isEmpty()) {
                item {
                    EmptyState(
                        icon = AppIcons.Box,
                        title = "No containers yet",
                        message = "Boxes, decks and slab cases hold the cards that never make it into a pocket.",
                        action = { AppButton("New container", onCreateContainer, icon = AppIcons.Plus) },
                    )
                }
            } else {
                val containerRows = (snapshot.containers.map<Container, Any> { it } + AddSlot).chunked(2)
                items(containerRows.size, key = { index -> "container-row-$index" }) { index ->
                    TilePair {
                        containerRows[index].forEach { entry ->
                            when (entry) {
                                is Container -> ContainerTile(
                                    container = entry,
                                    snapshot = snapshot,
                                    selected = entry.id in selection.containers,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        if (selection.isActive) onSelectionChange(selection.toggle(entry.id))
                                        else onOpenContainer(entry)
                                    },
                                    onLongClick = { onSelectionChange(selection.toggle(entry.id)) },
                                )
                                else -> AddTile(
                                    icon = AppIcons.Plus,
                                    label = "New container",
                                    onClick = onCreateContainer,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (containerRows[index].size == 1) TileGap()
                    }
                }
            }
        }

        if (selection.isActive) {
            SelectionIsland(
                count = selection.size,
                onClear = {
                    confirmingDelete = false
                    onSelectionChange(StorageSelection.EMPTY)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                confirm = if (!confirmingDelete) {
                    null
                } else {
                    {
                        SelectionConfirm(
                            message = deleteMessage(selection, heldCards),
                            confirmLabel = "Delete",
                            onCancel = { confirmingDelete = false },
                            onConfirm = {
                                confirmingDelete = false
                                onDeleteSelected(selection, deleteCards)
                                onSelectionChange(StorageSelection.EMPTY)
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
                    onClick = { confirmingDelete = true },
                    tint = Ink.Loss,
                )
            }
        }
    }
}

/** What is about to happen, counted rather than described. */
private fun deleteMessage(selection: StorageSelection, heldCards: Int): String {
    val what = buildList {
        if (selection.binders.isNotEmpty()) {
            add(if (selection.binders.size == 1) "1 binder" else "${selection.binders.size} binders")
        }
        if (selection.containers.isNotEmpty()) {
            add(
                if (selection.containers.size == 1) "1 container"
                else "${selection.containers.size} containers",
            )
        }
    }.joinToString(" and ")
    return if (heldCards == 0) "Delete $what? Nothing is filed in them."
    else "Delete $what, holding $heldCards ${if (heldCards == 1) "card" else "cards"}?"
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
        cover = { BinderCover(binder, width = 32.dp) },
        name = binder.name,
        // The grid is drawn on the cover, so the caption does not spell it out again --
        // "9-pocket - 3x3 - 12/40" was two facts and a picture of the third.
        caption = "${binder.layout.displayName} · ${summary.ownedCount}/${binder.capacity}",
        value = summary.marketValue.display(),
        accent = spine,
        gainLabel = summary.gainPercentLabel,
        gainPositive = summary.unrealizedGain.cents >= 0,
        fillFraction = if (binder.capacity == 0) 0f else summary.ownedCount.toFloat() / binder.capacity,
        badge = if (summary.wantedCount > 0) "${summary.wantedCount} Wanted" else null,
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
        cover = { ContainerCover(container, width = 32.dp) },
        name = container.name,
        caption = container.kind.label + " · " +
            if (container.count == 1) "1 card" else "${container.count} cards",
        value = summary.marketValue.display(),
        accent = Color(container.color),
        gainLabel = summary.gainPercentLabel,
        gainPositive = summary.unrealizedGain.cents >= 0,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        modifier = modifier,
    )
}
