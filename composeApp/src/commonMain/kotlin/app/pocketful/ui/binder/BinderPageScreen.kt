package app.pocketful.ui.binder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pocketful.domain.Binder
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Money
import app.pocketful.domain.SheetSide
import app.pocketful.domain.SlotContent
import app.pocketful.domain.ValueSummary
import app.pocketful.state.display
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.CARD_ASPECT_RATIO
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.PocketSlot
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SelectionAction
import app.pocketful.ui.components.SelectionConfirm
import app.pocketful.ui.components.SelectionIsland
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.tappable
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.nav.IslandSurface
import app.pocketful.ui.nav.SystemBackHandler
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import kotlinx.coroutines.launch

/**
 * One binder, one face at a time. A phone has no room for a two-page spread, so the
 * page turn is a horizontal swipe and the island at the bottom carries the sheet/side
 * context that a spread would otherwise make obvious.
 */
@Composable
fun BinderPageScreen(
    binder: Binder,
    snapshot: CollectionSnapshot,
    onBack: () -> Unit,
    onSlotClick: (Int) -> Unit,
    onEditBinder: () -> Unit,
    /** Records the selected wanted pockets as cards now owned. */
    onMarkOwned: (Set<Int>) -> Unit,
    /** Puts the selected filled pockets back on the want list, deleting their copies. */
    onMarkWanted: (Set<Int>) -> Unit,
    /** Flags or unflags the selected filled pockets as up for trade. */
    onSetForTrade: (Set<Int>, Boolean) -> Unit,
    onClearSlots: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    initialOrdinal: Int? = null,
) {
    val summary = remember(binder, snapshot) { snapshot.summarize(binder) }

    // Opening the binder to reach one specific pocket should land on that pocket's page,
    // not on page one with the sheet floating over the wrong spread.
    val initialFace = remember(binder.id, initialOrdinal) {
        val face = initialOrdinal?.let { binder.layout.locate(it).faceIndex } ?: 0
        face.coerceIn(0, (binder.faceCount - 1).coerceAtLeast(0))
    }
    val pagerState = rememberPagerState(initialPage = initialFace, pageCount = { binder.faceCount })
    val scope = rememberCoroutineScope()
    var jumpVisible by remember { mutableStateOf(false) }

    // Which pockets a bulk action is about to touch.
    //
    // A set binder opens with every pocket wanted, and the way that binder gets filled in
    // real life is a stack of cards on the desk and forty pockets to tick off. One sheet
    // per card would be forty open-confirm-dismiss cycles, so pockets select the way
    // everything else in the app selects -- hold one, tap the rest -- and the island that
    // replaces the page control acts on all of them at once.
    var selection by remember(binder.id) { mutableStateOf(emptySet<Int>()) }
    var confirmingWanted by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()

    // How the selection breaks down, so the island can say what each action will do
    // rather than offering three buttons of which two are no-ops.
    val selectedWanted = remember(selection, binder) {
        selection.count { binder.paddedSlots.getOrNull(it) is SlotContent.Wanted }
    }
    val selectedCopies = remember(selection, binder) {
        selection.mapNotNull { (binder.paddedSlots.getOrNull(it) as? SlotContent.Filled)?.copyId }
    }
    val selectedOwned = selectedCopies.size
    // Which way the trade action should go. Already-offered cards flip off, so sweeping a
    // page you have changed your mind about is the same gesture as flagging it was --
    // an action that could only ever add would leave no way to take a page back off the
    // table short of opening every pocket in it.
    val selectedAllForTrade = remember(selectedCopies, snapshot) {
        selectedCopies.isNotEmpty() && selectedCopies.all { snapshot.copies[it]?.forTrade == true }
    }

    // Shrinking a binder can leave the pager parked past the last page.
    LaunchedEffect(binder.faceCount) {
        if (pagerState.currentPage >= binder.faceCount && binder.faceCount > 0) {
            pagerState.scrollToPage(binder.faceCount - 1)
        }
    }

    Box(modifier.fillMaxSize().background(Ink.Background)) {
        ScreenBackdrop(Color(binder.spineColor), height = 300.dp)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BinderHeader(
                binder = binder,
                summary = summary,
                selecting = selecting,
                onBack = onBack,
                onEdit = onEditBinder,
                onSelectPage = {
                    // Everything on this face that a bulk action can do something with.
                    // Empty pockets are left out: selecting them would only pad the count
                    // with pockets every action would then skip.
                    val ordinals = binder.layout.ordinalsOnFace(pagerState.currentPage)
                        .filter { ordinal ->
                            when (binder.paddedSlots.getOrNull(ordinal)) {
                                is SlotContent.Filled, is SlotContent.Wanted -> true
                                else -> false
                            }
                        }
                        .toSet()
                    selection = if (selection.containsAll(ordinals) && ordinals.isNotEmpty()) {
                        selection - ordinals
                    } else {
                        selection + ordinals
                    }
                },
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 10.dp,
            ) { faceIndex ->
                BinderFace(
                    binder = binder,
                    snapshot = snapshot,
                    faceIndex = faceIndex,
                    selection = selection,
                    selecting = selecting,
                    onSlotClick = { ordinal ->
                        if (selecting) selection = selection.toggled(ordinal) else onSlotClick(ordinal)
                    },
                    onSlotLongClick = { ordinal -> selection = selection.toggled(ordinal) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = islandBottomInset()),
                )
            }
        }

        if (selecting) {
            // The selection island takes the page control's place rather than stacking
            // over it, the same way it takes the navigation bar's place everywhere else.
            // Pages still turn -- the pager is swiped, not driven by that control -- so a
            // selection can be swept across a whole binder without being dropped.
            SelectionIsland(
                count = selection.size,
                onClear = {
                    confirmingWanted = false
                    selection = emptySet()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                confirm = if (!confirmingWanted) {
                    null
                } else {
                    {
                        SelectionConfirm(
                            message = "Put $selectedOwned " +
                                (if (selectedOwned == 1) "card" else "cards") +
                                " back on the want list? The " +
                                (if (selectedOwned == 1) "copy leaves" else "copies leave") +
                                " your collection -- this is the undo for having marked " +
                                "them found, not a way to move them elsewhere.",
                            confirmLabel = "Put back",
                            onCancel = { confirmingWanted = false },
                            onConfirm = {
                                confirmingWanted = false
                                onMarkWanted(selection)
                                selection = emptySet()
                            },
                        )
                    }
                },
            ) {
                // Four verbs, in the order a page gets worked through: record what you
                // found, mark what you are still missing, pick out the spares, clear the
                // rest. The count each one would act on lives on the island's own chip,
                // so the labels stay one word and four of them fit a phone.
                SelectionAction(
                    icon = AppIcons.Check,
                    label = "Have",
                    enabled = selectedWanted > 0,
                    onClick = {
                        onMarkOwned(selection)
                        selection = emptySet()
                    },
                    tint = if (selectedWanted > 0) Ink.Gain else Ink.TextTertiary,
                )
                SelectionAction(
                    icon = AppIcons.Target,
                    label = "Want",
                    enabled = selectedOwned > 0,
                    onClick = { confirmingWanted = true },
                    tint = if (selectedOwned > 0) Ink.Wanted else Ink.TextTertiary,
                )
                SelectionAction(
                    icon = AppIcons.Trade,
                    label = "For Trade",
                    enabled = selectedOwned > 0,
                    onClick = {
                        onSetForTrade(selection, !selectedAllForTrade)
                        selection = emptySet()
                    },
                    // Lit while the selection is already offered, so the button says
                    // which way the next press will go before it is pressed.
                    tint = when {
                        selectedOwned == 0 -> Ink.TextTertiary
                        selectedAllForTrade -> Ink.Gain
                        else -> Ink.TextSecondary
                    },
                )
                SelectionAction(
                    icon = AppIcons.Minus,
                    label = "Empty",
                    onClick = {
                        onClearSlots(selection)
                        selection = emptySet()
                    },
                )
            }
        } else {
            PageIsland(
                binder = binder,
                faceIndex = pagerState.currentPage,
                // What the page you are looking at is worth, as opposed to the whole
                // binder in the header. A page is the unit this screen actually deals in
                // -- it is what you turn to, select across and photograph -- and until
                // now the only way to total one was to read sixteen pocket chips.
                pageValue = snapshot.pageValue(binder, pagerState.currentPage).displayOrNull(),
                onPrevious = {
                    val target = pagerState.currentPage - 1
                    if (target >= 0) scope.launch { pagerState.animateScrollToPage(target) }
                },
                onNext = {
                    val target = pagerState.currentPage + 1
                    if (target < binder.faceCount) scope.launch { pagerState.animateScrollToPage(target) }
                },
                onJump = { jumpVisible = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // Ahead of the app's own handler, so backing out of a selection leaves you in the
    // binder you were working in rather than back on the shelf.
    SystemBackHandler(enabled = selecting) {
        confirmingWanted = false
        selection = emptySet()
    }

    SystemBackHandler(enabled = jumpVisible) { jumpVisible = false }

    PageJumpSheet(
        visible = jumpVisible,
        binder = binder,
        currentFace = pagerState.currentPage,
        onDismiss = { jumpVisible = false },
        onSelect = { face ->
            jumpVisible = false
            scope.launch { pagerState.scrollToPage(face) }
        },
    )
}

/**
 * The binder, named once and summed up on one line.
 *
 * This screen is the only one in the app whose content has a *shape* -- a page of pockets
 * that has to sit on a phone without its bottom row falling off -- so it is the one place
 * a header cannot spend four blocks saying what it is about. The name moves into the rail
 * beside the back button, the display-size total and the row of tags collapse into a
 * single stat rail, and the eyebrow goes entirely: "Binder · 9-pocket" was a label naming
 * the screen you were already looking at, and the page underneath draws its own grid more
 * plainly than any words could.
 */
@Composable
private fun BinderHeader(
    binder: Binder,
    summary: ValueSummary,
    selecting: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSelectPage: () -> Unit,
) {
    ScreenHeader(
        railTitle = binder.name,
        subtitle = binder.subtitle,
        modifier = Modifier.padding(horizontal = 16.dp),
        leading = { CircleIconButton(AppIcons.ChevronLeft, "Back to shelf", onBack, size = 34.dp) },
        actions = {
            // The way in to bulk editing, and the reason it is discoverable at all. A
            // long press on a pocket does the same thing, but nobody finds a long press
            // on a screen whose every other gesture is a tap or a swipe.
            HeaderAction(
                icon = AppIcons.Check,
                contentDescription = "Select everything on this page",
                onClick = onSelectPage,
                tint = if (selecting) Ink.Accent else Ink.TextSecondary,
            )
            HeaderAction(AppIcons.Edit, "Edit binder", onEdit)
        },
        // Four cells at most, so this stays one row. Gain and wanted drop out when there
        // is nothing to report rather than showing a dash, which leaves a young binder
        // with the two figures it actually has instead of four, half of them empty.
        stats = buildList {
            add(Stat(summary.marketValue.display(), "value", Ink.Gold))
            summary.gainPercentLabel?.let {
                add(Stat(it, "gain", if (summary.unrealizedGain.cents >= 0) Ink.Gain else Ink.Loss))
            }
            add(Stat("${summary.ownedCount}/${binder.capacity}", "filled"))
            if (summary.wantedCount > 0) {
                add(
                    Stat(
                        value = listOfNotNull(
                            "${summary.wantedCount}",
                            summary.costToComplete.displayOrNull(),
                        ).joinToString(" · "),
                        label = "wanted",
                        accent = Ink.Wanted,
                    ),
                )
            }
        },
    )
}

/**
 * The page itself. Pocket size is solved from whichever axis runs out first, so a
 * 16-pocket page and a 4-pocket page both sit correctly on the same screen.
 */
@Composable
private fun BinderFace(
    binder: Binder,
    snapshot: CollectionSnapshot,
    faceIndex: Int,
    selection: Set<Int>,
    selecting: Boolean,
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = binder.layout
    val contents = remember(binder, faceIndex) { binder.face(faceIndex) }

    // Top-aligned rather than centred. A page narrower than the space it is given -- any
    // page, on a phone, since width always runs out first -- was leaving its slack split
    // above and below, and the half above sat as a band of nothing between the header and
    // the binder. All of it belongs at the bottom, where the floating page control is
    // already covering that part of the screen.
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val gap = 7.dp
        val pagePadding = 11.dp

        val usableW = maxWidth - pagePadding * 2 - gap * (layout.cols - 1)
        val usableH = maxHeight - pagePadding * 2 - gap * (layout.rows - 1)
        val cell = minOf(usableW / layout.cols, (usableH / layout.rows) * CARD_ASPECT_RATIO)

        Column(
            Modifier
                .clip(AppShape.Card)
                .background(Brush.verticalGradient(listOf(Ink.SurfaceRaised, Ink.Surface)))
                .border(1.dp, Ink.OutlineSoft, AppShape.Card)
                .padding(pagePadding),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            repeat(layout.rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(layout.cols) { col ->
                        val indexInFace = row * layout.cols + col
                        val ordinal = layout.ordinalOf(faceIndex, row, col)
                        PocketSlot(
                            view = snapshot.view(contents[indexInFace]),
                            modifier = Modifier.width(cell),
                            selecting = selecting,
                            selected = ordinal in selection,
                            onLongClick = { onSlotLongClick(ordinal) },
                            onClick = { onSlotClick(ordinal) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the cards on one page are worth.
 *
 * Owned copies only. A page half full of wants would otherwise total up the cost of
 * finishing it and print that in the same place as money you have actually spent, which
 * is the one arithmetic error a collection tracker must never make.
 */
private fun CollectionSnapshot.pageValue(binder: Binder, faceIndex: Int): Money =
    Money(
        binder.face(faceIndex)
            .filterIsInstance<SlotContent.Filled>()
            .sumOf { slot -> copies[slot.copyId]?.let { valueOf(it).cents } ?: 0L },
    )

/** Adds or removes one pocket. The whole vocabulary a selection needs. */
private fun Set<Int>.toggled(ordinal: Int): Set<Int> =
    if (ordinal in this) this - ordinal else this + ordinal

/**
 * The page-turn island. Deliberately the same object as the navigation island it replaces
 * -- one floating control at the bottom of the screen, whatever the screen happens to be.
 */
@Composable
private fun PageIsland(
    binder: Binder,
    faceIndex: Int,
    pageValue: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val location = binder.layout.locate(binder.layout.ordinalOf(faceIndex, 0, 0))
    val sideLabel = if (binder.layout.doubleSided) {
        if (location.side == SheetSide.FRONT) "front" else "back"
    } else {
        null
    }

    IslandSurface(modifier, horizontalPadding = 36.dp) {
        CircleIconButton(
            icon = AppIcons.ChevronLeft,
            contentDescription = "Previous page",
            onClick = onPrevious,
            size = 40.dp,
            background = if (faceIndex > 0) Ink.SurfaceHigh else Ink.SurfaceRaised,
            tint = if (faceIndex > 0) Ink.TextPrimary else Ink.TextDisabled,
            border = Color.Transparent,
        )

        Column(
            Modifier
                .weight(1f)
                .clip(AppShape.Pill)
                .tappable(pressScale = 0.96f, onClick = onJump)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Page ${faceIndex + 1} of ${binder.faceCount}",
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Text(
                text = listOfNotNull("sheet ${location.sheetIndex + 1}", sideLabel, pageValue)
                    .joinToString(" · "),
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }

        CircleIconButton(
            icon = AppIcons.ChevronRight,
            contentDescription = "Next page",
            onClick = onNext,
            size = 40.dp,
            background = if (faceIndex < binder.faceCount - 1) Ink.SurfaceHigh else Ink.SurfaceRaised,
            tint = if (faceIndex < binder.faceCount - 1) Ink.TextPrimary else Ink.TextDisabled,
            border = Color.Transparent,
        )
    }
}

/**
 * Jump straight to a page. Swiping through a 24-page binder to reach the back is the
 * single most tedious thing about a page-at-a-time view, so the page label is a button.
 */
@Composable
private fun PageJumpSheet(
    visible: Boolean,
    binder: Binder,
    currentFace: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AppSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(
            title = "Jump to page",
            subtitle = "${binder.faceCount} pages · ${binder.layout.displayName}",
            onClose = onDismiss,
        )
        SheetBody {
            val perRow = 6
            val rows = (binder.faceCount + perRow - 1) / perRow
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(rows) { rowIndex ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        repeat(perRow) { colIndex ->
                            val face = rowIndex * perRow + colIndex
                            if (face < binder.faceCount) {
                                PageChip(
                                    number = face + 1,
                                    filled = binder.face(face).any { it !is SlotContent.Empty },
                                    selected = face == currentFace,
                                    accent = Color(binder.spineColor),
                                    onClick = { onSelect(face) },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageChip(
    number: Int,
    filled: Boolean,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Ink.SurfaceRaised)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.7f) else Ink.OutlineFaint,
                shape = RoundedCornerShape(10.dp),
            )
            .tappable(pressScale = 0.93f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$number",
            color = when {
                selected -> Ink.TextPrimary
                filled -> Ink.TextSecondary
                else -> Ink.TextDisabled
            },
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
    }
}
