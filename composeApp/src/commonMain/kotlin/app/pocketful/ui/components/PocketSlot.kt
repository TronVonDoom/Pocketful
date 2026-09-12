package app.pocketful.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketful.domain.SlotView
import app.pocketful.state.LocalAppSettings
import app.pocketful.state.display
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.tint

/** A real TCG card is 63 x 88 mm. Everything in the page view derives from this. */
const val CARD_ASPECT_RATIO = 63f / 88f

/**
 * A card's corner radius as a fraction of its width.
 *
 * The cut on a real card is an eighth of an inch -- 3.18mm across a 63mm edge, almost
 * exactly a twentieth of it. Every rounded card surface in the app derives its radius
 * from this rather than naming a dp value, because a fixed radius is only right at one
 * size: 9dp reads as a soft-cornered tile on a 40dp thumbnail and as a barely-rounded
 * rectangle on a full-page pocket, and neither looks like the object in your hand.
 */
const val CARD_CORNER_FRACTION = 0.05f

/** The corner a card of this width would actually be cut with. */
fun cardCorner(width: Dp): Dp = width * CARD_CORNER_FRACTION

/** The shape a card of this width would actually be cut to. */
fun cardShape(width: Dp): RoundedCornerShape = RoundedCornerShape(cardCorner(width))

/**
 * One pocket of a binder page. Every state renders at exactly the same size so the grid
 * stays true to the physical sheet whether or not a pocket is filled.
 *
 * The corner is measured from the pocket's own width rather than fixed, so a 4-pocket
 * page and a 16-pocket page both draw cards cut the way real ones are.
 */
/**
 * What a pocket reports while it is being dragged out of place.
 *
 * Deltas rather than absolute positions, because a pocket has no idea where it sits on the
 * page -- the grid does, and it is the grid that has to decide which pocket the finger is
 * over. Passed as one object so enabling drag is one argument rather than four, and so
 * every screen that draws a pocket without rearranging it ignores a single default.
 */
data class PocketDrag(
    val onStart: () -> Unit,
    val onDelta: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
fun PocketSlot(
    view: SlotView,
    modifier: Modifier = Modifier,
    /** True while a multi-select is running anywhere on the page. */
    selecting: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
    /**
     * Drag-to-rearrange. Null leaves the pocket exactly as it was.
     *
     * When it is set, the long press belongs to the drag detector rather than to
     * [onLongClick] -- both cannot own the same gesture, and a hold that both selected the
     * pocket *and* picked it up would do two things at once. What a hold *means* is then
     * decided by whether it moves, which only the grid can see, so [onLongClick] is left
     * for the grid to call from [PocketDrag.onEnd].
     */
    drag: PocketDrag? = null,
) {
    val settings = LocalAppSettings.current

    // Whether the long-press detector has claimed the touch that is currently down.
    //
    // Needed because a `combinedClickable` with no long-click handler treats a long hold
    // as an ordinary click and fires it on release -- so without this, holding a pocket
    // and letting go opened the pocket sheet instead of selecting, and the hold that
    // starts a drag would fire a click as well.
    val heldForDrag = remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.aspectRatio(CARD_ASPECT_RATIO)) {
        val corner = cardCorner(maxWidth)
        val shape = RoundedCornerShape(corner)

        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .then(
                    if (drag == null) {
                        Modifier
                    } else {
                        // Reset first, and passively: every fresh touch starts as "not a
                        // drag", so a flag left set by a gesture that ended without a
                        // click cannot go on to swallow the next real tap. Nothing is
                        // consumed here, so the detectors below still see everything.
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                heldForDrag.value = false
                            }
                        }.pointerInput(drag) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    heldForDrag.value = true
                                    drag.onStart()
                                },
                                onDrag = { change, delta ->
                                    // Claimed, or the pager underneath reads the sideways
                                    // part of the drag as a page turn and the card gets
                                    // dragged off a page that is sliding out from under it.
                                    change.consume()
                                    drag.onDelta(delta)
                                },
                                onDragEnd = { drag.onEnd() },
                                onDragCancel = { drag.onCancel() },
                            )
                        }
                    },
                )
                .tappable(
                    pressScale = 0.94f,
                    onLongClick = if (drag == null) onLongClick else null,
                    onClick = { if (!heldForDrag.value) onClick() },
                ),
        ) {
            // Unpicked pockets step back rather than picked ones lighting up. A page of
            // sixteen cards is already the brightest thing in the app; adding a highlight
            // on top of it is invisible, and taking one away is not.
            Box(Modifier.fillMaxSize().alpha(if (selecting && !selected) 0.42f else 1f)) {
                when (view) {
                    SlotView.Empty -> EmptyPocket(shape)
                    is SlotView.Spacer -> SpacerPocket(view, shape)
                    is SlotView.Broken -> BrokenPocket(view, shape)
                    is SlotView.CardSlot -> when {
                        view.owned -> FilledPocket(view, shape)
                        settings.showWantedGhosts -> WantedPocket(view, corner)
                        else -> EmptyPocket(shape)
                    }
                }
            }

            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink.Accent.copy(alpha = 0.16f))
                        .border(2.dp, Ink.Accent, shape),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Check, "Selected", Modifier.size(10.dp), tint = Color.White)
                }
            }
        }
    }
}

/**
 * An empty pocket carries a faint plus. Without it the page reads as a finished object
 * and nobody discovers that pockets are tappable; with it at full contrast the page looks
 * like a form to fill in rather than a binder.
 */
@Composable
private fun EmptyPocket(shape: Shape) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.PocketWell)
            .border(1.dp, Ink.OutlineSoft, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Plus,
            contentDescription = "Add a card",
            modifier = Modifier.size(16.dp),
            tint = Ink.TextDisabled.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun SpacerPocket(view: SlotView.Spacer, shape: Shape) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.PocketWell.copy(alpha = 0.6f))
            .border(1.dp, Ink.OutlineFaint, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = view.label ?: "—",
            color = Ink.TextDisabled,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun BrokenPocket(view: SlotView.Broken, shape: Shape) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.PocketWell)
            .border(1.dp, Ink.Loss.copy(alpha = 0.4f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "missing\n${view.reference}",
            color = Ink.Loss.copy(alpha = 0.7f),
            fontSize = 8.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun FilledPocket(view: SlotView.CardSlot, shape: Shape) {
    Box(Modifier.fillMaxSize()) {
        CardArtwork(
            artStem = view.imageUrl,
            artUrl = view.imageAltUrl,
            type = view.type,
            modifier = Modifier.fillMaxSize(),
            holo = view.isHolo,
        )

        // With real art, the card prints its own name and number -- captioning it again
        // covers the illustration to repeat what is already there. So the full caption is
        // for cards the catalog has no scan of, and a card with art gets only the one
        // thing it cannot tell you itself: what it is worth.
        val hasArt = view.imageUrl != null || view.imageAltUrl != null

        if (!hasArt) {
            // Bottom scrim keeps the caption legible over the placeholder gradient.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.42f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = view.name,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 12.sp,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = view.collectorNumber,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                    // Same pill as a card with art wears, in the same corner. The scrim
                    // is already doing the contrast work here, so it arrives without its
                    // own ground rather than as a darker patch on a dark strip.
                    PocketStatusPill(view, grounded = false)
                }
            }
        } else {
            PocketStatusPill(view, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }

        view.gradeLabel?.let { grade ->
            PocketChip(
                text = grade,
                background = Ink.Gold,
                foreground = Color.Black,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            )
        }

        // Condition is worth calling out only when it is not the default.
        view.conditionShort?.takeIf { it != "NM" }?.let { condition ->
            PocketChip(
                text = condition,
                background = Color.Black.copy(alpha = 0.62f),
                foreground = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            )
        }

        // Graded slabs get a warm ring; everything else a plain pocket edge.
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    width = if (view.gradeLabel != null) 1.5.dp else 1.dp,
                    color = if (view.gradeLabel != null) Ink.Gold.copy(alpha = 0.55f)
                    else Color.White.copy(alpha = 0.10f),
                    shape = shape,
                ),
        )
    }
}

@Composable
private fun WantedPocket(view: SlotView.CardSlot, corner: Dp) {
    val settings = LocalAppSettings.current
    val accent = view.type.tint

    Box(Modifier.fillMaxSize().background(Ink.PocketWell)) {
        // A ghost of the card that belongs here, so the gap reads as a target, not a hole.
        // Where the catalog has a scan, the ghost is the actual card at low opacity --
        // far more useful when hunting than a name, because you are matching against what
        // is in the case in front of you.
        if (view.imageUrl != null || view.imageAltUrl != null) {
            CardArtwork(
                artStem = view.imageUrl,
                artUrl = view.imageAltUrl,
                type = view.type,
                modifier = Modifier.fillMaxSize().alpha(0.22f),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                        ),
                    ),
            )
        }

        DashedBorder(color = accent.copy(alpha = 0.45f), corner = corner)

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = view.name,
                color = accent.copy(alpha = 0.88f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = view.collectorNumber,
                color = Ink.TextTertiary,
                fontSize = 8.sp,
            )
        }

        if (settings.showPocketPrices && !view.value.isZero) {
            Text(
                text = view.value.display(),
                color = Ink.TextTertiary,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
            )
        }
    }
}

@Composable
private fun DashedBorder(color: Color, corner: Dp) {
    Canvas(Modifier.fillMaxSize()) {
        val inset = 1.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(corner.toPx()),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                ),
            ),
        )
    }
}

/**
 * What a pocket says about the copy in it, in one object in the bottom corner.
 *
 * Three facts that used to have nowhere to live -- is it foil, is it on the table, what is
 * it worth -- and giving each its own corner chip would have put four chips on a card the
 * size of a stamp. Grouped instead: they are all answers to "what is this copy", as
 * opposed to the grade and condition marks up top, which are answers to "what state is it
 * in". Each piece appears only when it has something to say, so a plain near-mint card you
 * are keeping still wears nothing but its price, and a card with nothing to report wears
 * nothing at all.
 *
 * [grounded] is false where a caption scrim is already behind it -- a second dark patch on
 * an already-dark strip reads as a rendering fault rather than as a pill.
 */
@Composable
private fun PocketStatusPill(
    view: SlotView.CardSlot,
    modifier: Modifier = Modifier,
    grounded: Boolean = true,
) {
    val settings = LocalAppSettings.current
    val showPrice = settings.showPocketPrices && !view.value.isZero
    if (!view.forTrade && !view.isHolo && !showPrice) return

    // Tight vertically, generous horizontally. The pill sits over the bottom edge of the
    // artwork, so every dp of its height is a dp of a card it is covering -- while its
    // width costs nothing, the corner it occupies being the card's own margin. Which is
    // also why the marks inside can be bigger than the pill got shorter: the constraint
    // was never how much room they needed, it was how much card the box around them ate.
    Row(
        modifier
            .clip(AppShape.Pill)
            .background(if (grounded) Color.Black.copy(alpha = 0.72f) else Color.Transparent)
            .padding(horizontal = if (grounded) 5.dp else 0.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (view.forTrade) {
            Icon(AppIcons.Trade, "Up for trade", Modifier.size(11.dp), tint = Ink.Gain)
        }
        if (view.isHolo) {
            Icon(AppIcons.Sparkle, view.finish.label, Modifier.size(11.dp), tint = Ink.Foil)
        }
        if (showPrice) {
            Text(
                text = view.value.display(),
                color = Ink.Gold,
                fontSize = 10.sp,
                // The line box, not the glyphs. Left at its default the text carried
                // four dp of leading the icons beside it did not have, and that padding
                // was most of the pill's height.
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PocketChip(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
