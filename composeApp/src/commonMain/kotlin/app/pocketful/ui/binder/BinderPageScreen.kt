package app.pocketful.ui.binder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.pocketful.domain.Binder
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Money
import app.pocketful.domain.SheetSide
import app.pocketful.domain.SlotContent
import app.pocketful.domain.SlotView
import app.pocketful.domain.ValueSummary
import app.pocketful.state.display
import app.pocketful.state.displayOrDash
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.CARD_ASPECT_RATIO
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.PocketDrag
import app.pocketful.ui.components.PocketSlot
import app.pocketful.ui.components.SelectionAction
import app.pocketful.ui.components.SelectionConfirm
import app.pocketful.ui.components.SelectionIsland
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.TopBar
import app.pocketful.ui.components.cardShape
import app.pocketful.ui.components.tappable
import app.pocketful.ui.nav.IslandSurface
import app.pocketful.ui.nav.SystemBackHandler
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Motion
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One binder, one face at a time.
 *
 * A phone has no room for a two-page spread, so the page turn is a horizontal swipe and the
 * bar at the bottom carries the sheet and side context a spread would otherwise make
 * obvious.
 *
 * Three things changed in the rebuild, all of them about the job this screen actually gets
 * used for -- working through a set binder with a stack of cards on the desk.
 *
 * **The header is a progress bar now.** A set binder is a progress bar by nature: it is
 * forty pockets and a count of how many you have found. The old header spent four cells of a
 * stat rail restating figures that the bar says in one glance, and the height it gave back
 * is height the page itself needed.
 *
 * **There is a way to the next gap.** Filling a binder means finding the next pocket that is
 * still empty or still wanted, and the only way to do that was to swipe through fourteen
 * pages looking. One control now jumps straight to it, and it is the single thing on this
 * screen that turns a chore into a loop.
 *
 * **The page overview shows pages, not numbers.** Jumping to page nine was a grid of numbered
 * chips, which asks you to remember what is on page nine. It is a grid of actual page
 * thumbnails instead, because the question is never "where is page nine", it is "where is
 * the page with the hole in it".
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
    /** Exchanges two pockets, which is both the move and the swap. */
    onSwapSlots: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialOrdinal: Int? = null,
) {
    val summary = remember(binder, snapshot) { snapshot.summarize(binder) }

    // Opening the binder to reach one specific pocket should land on that pocket's page, not
    // on page one with the sheet floating over the wrong spread.
    val initialFace = remember(binder.id, initialOrdinal) {
        val face = initialOrdinal?.let { binder.layout.locate(it).faceIndex } ?: 0
        face.coerceIn(0, (binder.faceCount - 1).coerceAtLeast(0))
    }
    val pagerState = rememberPagerState(initialPage = initialFace, pageCount = { binder.faceCount })
    val scope = rememberCoroutineScope()
    var overviewVisible by remember { mutableStateOf(false) }

    // Which pockets a bulk action is about to touch.
    //
    // A set binder opens with every pocket wanted, and the way that binder gets filled in
    // real life is a stack of cards on the desk and forty pockets to tick off. One sheet per
    // card would be forty open-confirm-dismiss cycles, so pockets select the way everything
    // else in the app selects -- hold one, tap the rest -- and the bar that replaces the page
    // control acts on all of them at once.
    var selection by remember(binder.id) { mutableStateOf(emptySet<Int>()) }

    // The pocket picked up to be moved to another page.
    //
    // Dragging cannot cross a page: the gesture belongs to the card the moment it is held,
    // and the pager needs that same sideways movement to turn the page, so one of the two has
    // to give. Carrying sidesteps the fight entirely -- the card waits while you turn to
    // wherever it is going, by swipe or by the page control, and lands on a tap. It is also
    // the only thing that works at all when the destination is fourteen pages away.
    var carrying by remember(binder.id) { mutableStateOf<Int?>(null) }
    var confirmingWanted by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()

    // How the selection breaks down, so the bar can say what each action will do rather than
    // offering five buttons of which three are no-ops.
    val selectedWanted = remember(selection, binder) {
        selection.count { binder.paddedSlots.getOrNull(it) is SlotContent.Wanted }
    }
    val selectedCopies = remember(selection, binder) {
        selection.mapNotNull { (binder.paddedSlots.getOrNull(it) as? SlotContent.Filled)?.copyId }
    }
    val selectedOwned = selectedCopies.size
    // Which way the trade action should go. Already-offered cards flip off, so sweeping a
    // page you have changed your mind about is the same gesture as flagging it was -- an
    // action that could only ever add would leave no way to take a page back off the table
    // short of opening every pocket in it.
    val selectedAllForTrade = remember(selectedCopies, snapshot) {
        selectedCopies.isNotEmpty() && selectedCopies.all { snapshot.copies[it]?.forTrade == true }
    }

    // Shrinking a binder can leave the pager parked past the last page.
    LaunchedEffect(binder.faceCount) {
        if (pagerState.currentPage >= binder.faceCount && binder.faceCount > 0) {
            pagerState.scrollToPage(binder.faceCount - 1)
        }
    }

    val spine = Color(binder.spineColor)

    /** Turns to whichever page holds the next pocket still waiting for a card. */
    fun jumpToNextGap() {
        val target = binder.nextGapAfter(pagerState.currentPage) ?: return
        scope.launch { pagerState.animateScrollToPage(target) }
    }

    Box(modifier.fillMaxSize().background(Ink.Canvas)) {
        ScreenBackdropFor(spine)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                title = binder.name,
                subtitle = binder.subtitle ?: binder.layout.fullLabel,
                onBack = onBack,
                backDescription = "Back to your collection",
                actions = {
                    // The way in to bulk editing, and the reason it is discoverable at all. A
                    // long press on a pocket does the same thing, but nobody finds a long
                    // press on a screen whose every other gesture is a tap or a swipe.
                    HeaderAction(
                        icon = AppIcons.Check,
                        contentDescription = "Select everything on this page",
                        onClick = {
                            // Everything on this face a bulk action can do something with.
                            // Empty pockets are left out: selecting them would only pad the
                            // count with pockets every action would then skip.
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
                        tint = if (selecting) Ink.Accent else Ink.TextSecondary,
                    )
                    HeaderAction(AppIcons.Edit, "Edit binder", onEditBinder)
                },
            )

            BinderProgress(
                binder = binder,
                summary = summary,
                spine = spine,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = Space.md,
            ) { faceIndex ->
                BinderFace(
                    binder = binder,
                    snapshot = snapshot,
                    faceIndex = faceIndex,
                    selection = selection,
                    selecting = selecting,
                    carrying = carrying,
                    onSlotClick = { ordinal ->
                        val held = carrying
                        when {
                            // Dropping comes first. While a card is in hand every pocket is a
                            // destination, and opening a pocket sheet under a held card would
                            // leave it held behind a sheet with no way to put it down.
                            held != null -> {
                                if (held != ordinal) onSwapSlots(held, ordinal)
                                carrying = null
                            }
                            selecting -> selection = selection.toggled(ordinal)
                            else -> onSlotClick(ordinal)
                        }
                    },
                    onSlotLongClick = { ordinal -> selection = selection.toggled(ordinal) },
                    onSwap = onSwapSlots,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = Space.md,
                            end = Space.md,
                            top = Space.xs,
                            bottom = islandBottomInset(),
                        ),
                )
            }
        }

        if (selecting) {
            // The selection bar takes the page control's place rather than stacking over it,
            // the same way it takes the navigation dock's place everywhere else. Pages still
            // turn -- the pager is swiped, not driven by that control -- so a selection can be
            // swept across a whole binder without being dropped.
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
                // Five verbs, in the order a page gets worked through: record what you found,
                // mark what you are still missing, pick out the spares, move one somewhere
                // else, clear the rest. The count each one would act on lives on the bar's own
                // chip, so the labels stay one word and five of them fit a phone.
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
                    label = "Trade",
                    enabled = selectedOwned > 0,
                    onClick = {
                        onSetForTrade(selection, !selectedAllForTrade)
                        selection = emptySet()
                    },
                    // Lit while the selection is already offered, so the button says which way
                    // the next press will go before it is pressed.
                    tint = when {
                        selectedOwned == 0 -> Ink.TextTertiary
                        selectedAllForTrade -> Ink.Gain
                        else -> Ink.TextSecondary
                    },
                )
                SelectionAction(
                    icon = AppIcons.Move,
                    label = "Move",
                    // One pocket only. Two cards cannot be put down in the same place, and a
                    // rule for where the rest land would be a rule nobody asked for -- moving
                    // a run is a different feature with a different gesture.
                    enabled = selection.size == 1,
                    onClick = {
                        carrying = selection.firstOrNull()
                        selection = emptySet()
                    },
                    tint = if (selection.size == 1) Ink.Accent else Ink.TextTertiary,
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
            Column(
                Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                carrying?.let { held ->
                    CarryBanner(
                        view = snapshot.view(binder.paddedSlots.getOrElse(held) { SlotContent.Empty }),
                        onCancel = { carrying = null },
                    )
                }
                PageBar(
                    binder = binder,
                    faceIndex = pagerState.currentPage,
                    // What the page you are looking at is worth, as opposed to the whole
                    // binder in the header. A page is the unit this screen actually deals in
                    // -- it is what you turn to, select across and photograph -- and until now
                    // the only way to total one was to read sixteen pocket chips.
                    pageValue = snapshot.pageValue(binder, pagerState.currentPage)
                        .displayOrNull(summary.currency),
                    hasGap = binder.nextGapAfter(pagerState.currentPage) != null,
                    onPrevious = {
                        val target = pagerState.currentPage - 1
                        if (target >= 0) scope.launch { pagerState.animateScrollToPage(target) }
                    },
                    onNext = {
                        val target = pagerState.currentPage + 1
                        if (target < binder.faceCount) scope.launch { pagerState.animateScrollToPage(target) }
                    },
                    onOverview = { overviewVisible = true },
                    onNextGap = { jumpToNextGap() },
                )
            }
        }
    }

    // Ahead of the app's own handler, so backing out of a selection leaves you in the binder
    // you were working in rather than back on the shelf.
    SystemBackHandler(enabled = selecting) {
        confirmingWanted = false
        selection = emptySet()
    }

    // Ahead of the binder's own back handler for the same reason the selection one is: backing
    // out of "I am moving this card" should put the card down, not leave the page.
    SystemBackHandler(enabled = carrying != null) { carrying = null }

    SystemBackHandler(enabled = overviewVisible) { overviewVisible = false }

    PageOverviewSheet(
        visible = overviewVisible,
        binder = binder,
        snapshot = snapshot,
        currentFace = pagerState.currentPage,
        onDismiss = { overviewVisible = false },
        onSelect = { face ->
            overviewVisible = false
            scope.launch { pagerState.scrollToPage(face) }
        },
    )
}

/** The wash behind the page, in the binder's own colour. */
@Composable
private fun ScreenBackdropFor(spine: Color) {
    app.pocketful.ui.components.ScreenBackdrop(spine, height = 300.dp)
}

/**
 * How far through the binder you are, as one strip.
 *
 * This replaces a four-cell stat rail, and says more in a quarter of the height. A binder is
 * a container with a known capacity, so "12 of 40" is a fraction, and a fraction drawn as a
 * bar is read without being parsed. The figures that survive are the two a rail could not
 * draw: what the binder is worth, and what finishing it would cost.
 */
@Composable
private fun BinderProgress(
    binder: Binder,
    summary: ValueSummary,
    spine: Color,
    modifier: Modifier = Modifier,
) {
    val capacity = binder.capacity.coerceAtLeast(1)
    val ownedShare = summary.ownedCount.toFloat() / capacity
    val wantedShare = summary.wantedCount.toFloat() / capacity

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A dash is the right answer where a layout has a slot reserved for a price. It
            // is the wrong answer here: on a binder nobody has put a card in yet it renders
            // as a single gold hyphen floating in the corner, which reads as a glyph that
            // failed to load rather than as "no value". An empty binder says so in words.
            val value = summary.marketValue.displayOrNull(summary.currency)
            if (value != null) {
                Text(
                    text = value,
                    color = Ink.Gold,
                    style = MaterialTheme.typography.titleLarge,
                )
                summary.gainPercentLabel?.let {
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = it,
                        color = if (summary.unrealizedGain.cents >= 0) Ink.Gain else Ink.Loss,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                Text(
                    text = if (summary.ownedCount == 0) "No cards yet" else "Not priced yet",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${summary.ownedCount} of ${binder.capacity}",
                color = Ink.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.height(Space.sm))

        // Two segments on one track: what is in, then what is still wanted, then the rest.
        // Stacking them rather than drawing two bars is what makes "you are here, and this
        // much is accounted for" a single shape instead of a comparison.
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(AppShape.Pill)
                .background(Ink.Well),
        ) {
            Row(Modifier.fillMaxWidth()) {
                if (ownedShare > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(ownedShare.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(spine),
                    )
                }
                if (wantedShare > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(
                                // Measured against what is left after the owned segment, since
                                // each child's fraction is of the space its siblings have not
                                // already taken.
                                (wantedShare / (1f - ownedShare).coerceAtLeast(0.0001f))
                                    .coerceIn(0f, 1f),
                            )
                            .height(6.dp)
                            .background(Ink.Wanted.copy(alpha = 0.55f)),
                    )
                }
            }
        }

        if (summary.wantedCount > 0) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = listOfNotNull(
                    "${summary.wantedCount} still wanted",
                    summary.costToComplete.displayOrNull()?.let { "$it to finish" },
                ).joinToString(" · "),
                color = Ink.Wanted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The page itself. Pocket size is solved from whichever axis runs out first, so a 16-pocket
 * page and a 4-pocket page both sit correctly on the same screen.
 */
@Composable
private fun BinderFace(
    binder: Binder,
    snapshot: CollectionSnapshot,
    faceIndex: Int,
    selection: Set<Int>,
    selecting: Boolean,
    /** The pocket being carried across pages, if one is, so it can be shown lifted. */
    carrying: Int?,
    onSlotClick: (Int) -> Unit,
    onSlotLongClick: (Int) -> Unit,
    onSwap: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = binder.layout
    val contents = remember(binder, faceIndex) { binder.face(faceIndex) }

    // Which pocket is being dragged, and how far it has travelled from where it started. Held
    // here rather than in the screen because it dies with the page: a drag cannot outlive the
    // face it started on, since the pager owns sideways movement everywhere except inside a
    // drag that has already claimed it.
    var dragFrom by remember(binder.id, faceIndex) { mutableStateOf<Int?>(null) }
    var dragOffset by remember(binder.id, faceIndex) { mutableStateOf(Offset.Zero) }

    // A card let go over nothing -- the margin, the header, the gap below the page -- travels
    // back to the pocket it came from instead of blinking out of existence. The whole point of
    // a drag is that the card is a thing you are holding, and a thing you drop either lands
    // somewhere or comes back; vanishing is neither.
    val settleScope = rememberCoroutineScope()
    var settleFrom by remember(binder.id, faceIndex) { mutableStateOf<Int?>(null) }
    var settleFrom0 by remember(binder.id, faceIndex) { mutableStateOf(Offset.Zero) }
    val settle = remember(binder.id, faceIndex) { Animatable(0f) }

    fun releaseHome(from: Int, offset: Offset) {
        settleFrom = from
        settleFrom0 = offset
        settleScope.launch {
            settle.snapTo(1f)
            // Slightly bouncy, because it is a card landing rather than a panel closing.
            settle.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
            settleFrom = null
        }
    }

    // Centred in what is left after the bar is accounted for.
    //
    // This was top-aligned, on the argument that a page narrower than its space -- any page,
    // on a phone, since width always runs out first -- should put all its slack at the bottom
    // where the floating control already sits. That held while the header was four blocks
    // tall and there was barely any slack to place. With the header down to a bar and a
    // progress strip there is a great deal of it, and pinning the page to the top now leaves
    // it hanging off the header with an empty half-screen underneath. The caller's bottom
    // padding already reserves the bar's height, so centring here centres within the space
    // the page can actually occupy.
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gap = Space.sm
        val pagePadding = Space.md

        val usableW = maxWidth - pagePadding * 2 - gap * (layout.cols - 1)
        val usableH = maxHeight - pagePadding * 2 - gap * (layout.rows - 1)
        val cell = minOf(usableW / layout.cols, (usableH / layout.rows) * CARD_ASPECT_RATIO)
        val cellH = cell / CARD_ASPECT_RATIO

        // The same geometry the grid is laid out from, in pixels, so working out which pocket
        // a finger is over is arithmetic rather than a question for the layout system. Nothing
        // has to register its bounds and no drop target has to exist as a separate thing --
        // the grid already knows exactly where every pocket is.
        val density = LocalDensity.current
        val cellPx = with(density) { cell.toPx() }
        val cellHPx = with(density) { cellH.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val padPx = with(density) { pagePadding.toPx() }
        // The page is centred in both axes within whatever space it is given, so everything
        // measured from its corner has to start there rather than at the screen's. Both
        // offsets matter: the drag works out which pocket a finger is over by arithmetic
        // rather than by asking the layout system, so if this disagrees with where the page is
        // actually drawn by even one axis, cards get dropped into the wrong pocket.
        val pageW = pagePadding * 2 + cell * layout.cols + gap * (layout.cols - 1)
        val pageH = pagePadding * 2 + cellH * layout.rows + gap * (layout.rows - 1)
        val leftPx = with(density) { ((maxWidth - pageW) / 2).coerceAtLeast(0.dp).toPx() }
        val topPx = with(density) { ((maxHeight - pageH) / 2).coerceAtLeast(0.dp).toPx() }

        /** Where a pocket's top-left corner sits, in the same space the drag is measured in. */
        fun cornerOf(ordinal: Int): Offset {
            val at = layout.locate(ordinal)
            return Offset(
                leftPx + padPx + at.col * (cellPx + gapPx),
                topPx + padPx + at.row * (cellHPx + gapPx),
            )
        }

        /** The pocket under a point, or null if it is off the grid or in a gutter. */
        fun pocketAt(point: Offset): Int? {
            val col = ((point.x - leftPx - padPx) / (cellPx + gapPx)).toInt()
            val row = ((point.y - topPx - padPx) / (cellHPx + gapPx)).toInt()
            if (col < 0 || col >= layout.cols || row < 0 || row >= layout.rows) return null
            return layout.ordinalOf(faceIndex, row, col)
        }

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
                        val lifted = ordinal == dragFrom || ordinal == carrying || ordinal == settleFrom
                        PocketSlot(
                            view = snapshot.view(contents[indexInFace]),
                            // The pocket a card has left reads as empty while it is out, so
                            // the page shows where it would land rather than showing the card
                            // in two places at once.
                            modifier = Modifier.width(cell).alpha(if (lifted) 0.25f else 1f),
                            selecting = selecting,
                            selected = ordinal in selection,
                            onLongClick = { onSlotLongClick(ordinal) },
                            onClick = { onSlotClick(ordinal) },
                            drag = PocketDrag(
                                onStart = {
                                    dragFrom = ordinal
                                    dragOffset = Offset.Zero
                                },
                                onDelta = { dragOffset += it },
                                // Both endings run the same test, because a hold that is
                                // released without moving arrives here as a *cancel* rather
                                // than an end -- the detector reports "no drag happened", which
                                // is exactly the gesture that means select. Reading only onEnd
                                // left a long press doing nothing at all.
                                onEnd = {
                                    val from = dragFrom
                                    dragFrom = null
                                    if (from != null) {
                                        // A hold that never moved is a hold, not a drag. That
                                        // one test is what lets the same gesture both start a
                                        // selection and pick a card up.
                                        if (dragOffset.getDistance() < TAP_SLOP_PX) {
                                            onSlotLongClick(from)
                                        } else {
                                            val centre = cornerOf(from) +
                                                Offset(cellPx / 2f, cellHPx / 2f) + dragOffset
                                            val onto = pocketAt(centre)
                                            if (onto != null) onSwap(from, onto)
                                            else releaseHome(from, dragOffset)
                                        }
                                    }
                                    dragOffset = Offset.Zero
                                },
                                onCancel = {
                                    val from = dragFrom
                                    dragFrom = null
                                    // Only the motionless case selects. A drag that is genuinely
                                    // interrupted -- a second finger, a system gesture -- puts
                                    // the card back rather than dropping it somewhere nobody
                                    // aimed at.
                                    if (from != null) {
                                        if (dragOffset.getDistance() < TAP_SLOP_PX) {
                                            onSlotLongClick(from)
                                        } else {
                                            releaseHome(from, dragOffset)
                                        }
                                    }
                                    dragOffset = Offset.Zero
                                },
                            ),
                        )
                    }
                }
            }
        }

        // The card in hand, drawn over the grid rather than inside the pocket it came from: a
        // child cannot paint outside its parent, and a card that stopped at the edge of its own
        // pocket would not be a drag at all. Non-interactive, so the pointer stays with the
        // pocket that started the gesture.
        val lifting = dragFrom ?: settleFrom
        if (lifting != null) {
            val travel = if (dragFrom != null) dragOffset else settleFrom0 * settle.value
            val corner = cornerOf(lifting) + travel
            val over = if (dragFrom == null) null else pocketAt(corner + Offset(cellPx / 2f, cellHPx / 2f))
            Box(
                Modifier
                    // Measured from the page's own top-left. Without this the box inherits the
                    // parent's TopCenter alignment and is centred *before* the offset lands, so
                    // the card floats a column to the right of the finger while dropping
                    // correctly -- the drawing and the arithmetic disagreeing.
                    .align(Alignment.TopStart)
                    .offset { IntOffset(corner.x.roundToInt(), corner.y.roundToInt()) }
                    .width(cell)
                    .zIndex(1f)
                    // Scaled up a little and made slightly transparent, which is the whole
                    // vocabulary of "picked up" -- the pocket underneath stays readable, so you
                    // can see what you are about to swap with.
                    .graphicsLayer {
                        scaleX = 1.06f
                        scaleY = 1.06f
                        alpha = 0.92f
                    },
            ) {
                PocketSlot(
                    view = snapshot.view(binder.paddedSlots.getOrElse(lifting) { SlotContent.Empty }),
                    modifier = Modifier.width(cell),
                )
            }

            // Ringed rather than filled, so the card being dragged over it stays legible.
            if (over != null && over != lifting) {
                val target = cornerOf(over)
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(target.x.roundToInt(), target.y.roundToInt()) }
                        .width(cell)
                        .aspectRatio(CARD_ASPECT_RATIO)
                        .border(2.dp, Ink.Accent, cardShape(cell)),
                )
            }
        }
    }
}

/**
 * How far a finger may wander during a hold and still count as a hold.
 *
 * This is the whole boundary between the two gestures a long press carries: under it the press
 * selects the pocket, over it the card has been picked up. Generous, because a finger resting
 * on glass for half a second is never perfectly still and the cost of guessing wrong is a card
 * that moves when someone meant to tick it.
 */
private const val TAP_SLOP_PX = 24f

/**
 * What the cards on one page are worth.
 *
 * Owned copies only. A page half full of wants would otherwise total up the cost of finishing
 * it and print that in the same place as money you have actually spent, which is the one
 * arithmetic error a collection tracker must never make.
 */
private fun CollectionSnapshot.pageValue(binder: Binder, faceIndex: Int): Money =
    Money(
        binder.face(faceIndex)
            .filterIsInstance<SlotContent.Filled>()
            .sumOf { slot -> copies[slot.copyId]?.let { valueOf(it).cents } ?: 0L },
    )

/**
 * The next page holding a pocket that is still waiting for a card, wrapping round the end.
 *
 * "Waiting" is empty *or* wanted. Both are holes in the binder from the point of view of
 * someone standing at a desk with a stack of cards, and an app that skipped the wanted ones
 * would walk you past exactly the pockets you are trying to fill.
 *
 * Returns null when there is nothing to find, which is how the control that uses it knows to
 * go quiet rather than to bounce you back to where you already are.
 */
private fun Binder.nextGapAfter(faceIndex: Int): Int? {
    if (faceCount <= 0) return null
    fun faceHasGap(face: Int): Boolean = this.face(face).any {
        it is SlotContent.Empty || it is SlotContent.Wanted
    }
    for (step in 1..faceCount) {
        val candidate = (faceIndex + step) % faceCount
        if (faceHasGap(candidate)) return candidate
    }
    // The page you are on may be the only one with a gap left, and jumping to it is a no-op
    // rather than a lie -- but saying so lets the control stay lit while there is still work.
    return if (faceHasGap(faceIndex)) faceIndex else null
}

/** Adds or removes one pocket. The whole vocabulary a selection needs. */
private fun Set<Int>.toggled(ordinal: Int): Set<Int> =
    if (ordinal in this) this - ordinal else this + ordinal

/**
 * The card in hand, waiting for a pocket.
 *
 * Above the page control rather than instead of it. Carrying exists to cross pages, so taking
 * away the thing that turns pages while a card is being carried would remove the only reason
 * to carry one.
 */
@Composable
private fun CarryBanner(
    view: SlotView,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = (view as? SlotView.CardSlot)?.name
    IslandSurface(modifier.padding(bottom = Space.xs), horizontalPadding = Space.md) {
        Box(Modifier.width(28.dp)) { PocketSlot(view = view, modifier = Modifier.width(28.dp)) }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = name ?: "Moving this pocket",
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Turn to a page and tap a pocket",
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space.md))
        app.pocketful.ui.components.CircleIconButton(
            icon = AppIcons.Close,
            contentDescription = "Stop moving this card",
            onClick = onCancel,
            size = Size.controlSm,
            background = Ink.SurfaceHigh,
            tint = Ink.TextSecondary,
            border = Color.Transparent,
        )
    }
}

/**
 * The page control.
 *
 * Deliberately the same floating object as the navigation dock it replaces, and carrying one
 * thing the old one did not: a jump to the next pocket still waiting for a card. That control
 * is the difference between filling a binder and hunting for the place to fill it -- it is lit
 * while there is a gap anywhere and goes quiet when the binder is done, which is also the only
 * congratulation this screen offers.
 */
@Composable
private fun PageBar(
    binder: Binder,
    faceIndex: Int,
    pageValue: String?,
    hasGap: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOverview: () -> Unit,
    onNextGap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val location = binder.layout.locate(binder.layout.ordinalOf(faceIndex, 0, 0))
    val sideLabel = if (binder.layout.doubleSided) {
        if (location.side == SheetSide.FRONT) "front" else "back"
    } else {
        null
    }

    IslandSurface(modifier, horizontalPadding = Space.lg) {
        app.pocketful.ui.components.CircleIconButton(
            icon = AppIcons.ChevronLeft,
            contentDescription = "Previous page",
            onClick = onPrevious,
            size = Size.control,
            background = if (faceIndex > 0) Ink.SurfaceHigh else Ink.SurfaceRaised,
            tint = if (faceIndex > 0) Ink.TextPrimary else Ink.TextDisabled,
            border = Color.Transparent,
        )

        Column(
            Modifier
                .weight(1f)
                .clip(AppShape.Medium)
                .tappable(pressScale = 0.96f, onClick = onOverview)
                .padding(horizontal = Space.sm, vertical = Space.xs),
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

        if (hasGap) {
            app.pocketful.ui.components.CircleIconButton(
                icon = AppIcons.Target,
                contentDescription = "Jump to the next pocket still to fill",
                onClick = onNextGap,
                size = Size.control,
                background = Ink.Wanted.copy(alpha = 0.18f),
                tint = Ink.Wanted,
                border = Color.Transparent,
            )
            Spacer(Modifier.width(Space.xxs))
        }

        app.pocketful.ui.components.CircleIconButton(
            icon = AppIcons.ChevronRight,
            contentDescription = "Next page",
            onClick = onNext,
            size = Size.control,
            background = if (faceIndex < binder.faceCount - 1) Ink.SurfaceHigh else Ink.SurfaceRaised,
            tint = if (faceIndex < binder.faceCount - 1) Ink.TextPrimary else Ink.TextDisabled,
            border = Color.Transparent,
        )
    }
}

/**
 * Every page at once, drawn rather than numbered.
 *
 * This was a grid of numbered chips, which asks the reader to already know what is on page
 * nine. Nobody knows what is on page nine. What they know is that there is a page near the
 * back that is still half empty, and that is a shape, not a number -- so each page is drawn as
 * the grid of dots it actually is: filled, wanted, or empty.
 *
 * At a glance the whole binder's state is legible, which is also the only place in the app
 * that answers "how much of this is left" without arithmetic.
 */
@Composable
private fun PageOverviewSheet(
    visible: Boolean,
    binder: Binder,
    snapshot: CollectionSnapshot,
    currentFace: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AppSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(
            title = "Pages",
            subtitle = "${binder.faceCount} pages · ${binder.layout.displayName}",
            onClose = onDismiss,
        )
        SheetBody(scrollable = false) {
            val listState = rememberLazyListState()
            val perRow = 4
            val rows = (binder.faceCount + perRow - 1) / perRow
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(rows) { rowIndex ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        repeat(perRow) { colIndex ->
                            val face = rowIndex * perRow + colIndex
                            if (face < binder.faceCount) {
                                PageThumb(
                                    binder = binder,
                                    snapshot = snapshot,
                                    faceIndex = face,
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

/** One page, as the grid of dots it is. */
@Composable
private fun PageThumb(
    binder: Binder,
    snapshot: CollectionSnapshot,
    faceIndex: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = binder.layout
    val contents = remember(binder, faceIndex) { binder.face(faceIndex) }

    Column(
        modifier
            .clip(AppShape.Small)
            .background(if (selected) accent.copy(alpha = 0.18f) else Ink.SurfaceRaised)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.7f) else Ink.OutlineFaint,
                shape = AppShape.Small,
            )
            .tappable(pressScale = 0.94f, onClick = onClick)
            .padding(Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(layout.rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(layout.cols) { col ->
                        val slot = contents.getOrNull(row * layout.cols + col)
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(AppShape.Chip)
                                .background(
                                    when (slot) {
                                        is SlotContent.Filled -> accent
                                        is SlotContent.Wanted -> Ink.Wanted.copy(alpha = 0.7f)
                                        else -> Ink.Well
                                    },
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            text = "${faceIndex + 1}",
            color = if (selected) Ink.TextPrimary else Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
