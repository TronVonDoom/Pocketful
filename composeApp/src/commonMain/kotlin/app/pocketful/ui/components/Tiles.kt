package app.pocketful.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketful.state.NO_PRICE
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Motion
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space

/**
 * The height every tile in the app is drawn at.
 *
 * Fixed rather than wrapped. A grid whose rows each measure themselves lands on tiles a few
 * pixels apart in height -- one binder has a fill bar and the next does not, one card has a
 * badge and the next does not -- and the eye reads that unevenness as sloppiness long
 * before it works out the cause. One height for every tile also means the "add another"
 * tile is the same object as the things it sits beside instead of a shorter button wedged
 * into the grid.
 */
val TileHeight: Dp = Size.tile

/**
 * Two tiles to a row, with a gap where a second one would go.
 *
 * The odd-one-out case is the reason this exists: letting a lone final tile stretch to full
 * width makes it read as a different, more important kind of thing than the ones above it,
 * so the empty half is held open instead.
 */
@Composable
fun TilePair(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        content = content,
    )
}

/** Fills the leftover half of a [TilePair] that only got one tile. */
@Composable
fun RowScope.TileGap() {
    Spacer(Modifier.weight(1f))
}

/**
 * The panel every tile is drawn on. Same surface and radius as a list row, so switching a
 * section from rows to tiles is a change of arrangement, not of material.
 *
 * Selection is a border colour rather than an overlay or a checkbox. The border is always
 * drawn -- faint when unselected, accented when selected -- so picking a tile never changes
 * its size and the grid does not twitch as a selection is swept across it.
 */
@Composable
internal fun TileSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    // A tile for something the app cannot open yet -- an unconnected game -- rather than one
    // that is merely unselected. It dims and stops responding instead of being left out of
    // the grid, because "this game exists and is not wired up" is information.
    enabled: Boolean = true,
    /**
     * The colour of the thing on the tile, washed faintly into its ground and edge.
     *
     * A shelf of binders is recognised by colour long before it is read, and a grid of
     * identical grey rectangles throws that away. Kept deliberately faint: enough that six
     * tiles read as six different objects at arm's length, not so much that the grid turns
     * into a colour chart and the values stop being the brightest thing on it.
     */
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outline by animateColorAsState(
        targetValue = when {
            selected -> Ink.Accent
            accent != null -> accent.copy(alpha = 0.32f)
            else -> Ink.OutlineFaint
        },
        animationSpec = Motion.fast(),
        label = "tileOutline",
    )
    val ground = when {
        selected -> SolidColor(Ink.Accent.copy(alpha = 0.10f))
        // Top-weighted, so the wash reads as light falling across the tile rather than as a
        // panel somebody filled in.
        accent != null -> Brush.verticalGradient(
            0f to accent.copy(alpha = 0.16f),
            0.55f to Ink.Surface,
            1f to Ink.Surface,
        )
        else -> SolidColor(Ink.Surface)
    }
    Column(
        modifier
            .height(TileHeight)
            .clip(AppShape.Card)
            .background(ground)
            .border(if (selected) 2.dp else 1.dp, outline, AppShape.Card)
            .alpha(if (enabled) 1f else 0.45f)
            .tappable(enabled = enabled, pressScale = 0.975f, onLongClick = onLongClick, onClick = onClick)
            .padding(Space.md),
        content = content,
    )
}

/**
 * A binder or a container, as a tile.
 *
 * Two blocks with air between them: who this is along the top, what it holds along the
 * bottom. The name takes the top edge at full width -- it is the thing being hunted for --
 * and the cover drops to the floor to anchor the figures, where a drawing of a 3x3 page
 * sits against the count of how many of those pockets are full.
 *
 * Everything below the name is pushed to that floor, so the values across a row of tiles
 * line up whether or not their neighbours have a fill bar, a gain or a badge.
 */
@Composable
fun StorageTile(
    cover: @Composable () -> Unit,
    name: String,
    caption: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Ink.Accent,
    gainLabel: String? = null,
    gainPositive: Boolean = true,
    fillFraction: Float? = null,
    badge: String? = null,
    // Separate from [accent] on purpose. The accent identifies the binder; a badge means
    // something ("4 wanted"), and tinting it with the spine colour made the same status read
    // as an alarm on a red binder and as decoration on a green one.
    badgeColor: Color = accent,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    TileSurface(onClick, modifier, onLongClick = onLongClick, selected = selected, accent = accent) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = caption,
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Beside the name rather than above it, so a binder with four cards still to
            // find says so on the line already being read.
            when {
                selected -> {
                    Spacer(Modifier.width(Space.sm))
                    SelectionCheck()
                }
                badge != null -> {
                    Spacer(Modifier.width(Space.sm))
                    Tag(badge, color = badgeColor, background = badgeColor.copy(alpha = 0.16f))
                }
            }
        }

        // All of the slack in one place, above the floor. One weighted spacer, not two: a
        // second one further down would share the leftover height with this rather than sit
        // under it, and the fill bar would drift into the middle of the tile.
        Spacer(Modifier.weight(1f))

        // A fill bar is a fact about the figures it touches, not a third line of the
        // identity block, so it rides directly on top of them.
        if (fillFraction != null) {
            ProgressTrack(fraction = fillFraction, color = accent, height = 4.dp)
            Spacer(Modifier.height(Space.md))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            cover()
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(Space.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = value,
                    // Same rule as a headline: the "no price yet" dash is not a figure, and
                    // at full contrast it reads as a value that failed rather than as one
                    // that does not exist.
                    color = if (value == NO_PRICE) Ink.TextDisabled else Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                gainLabel?.let {
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = it,
                        color = if (gainPositive) Ink.Gain else Ink.Loss,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The "add another one of these" tile that closes a grid of storage.
 *
 * The same size and shape as the tiles it follows -- that is the whole point of it being a
 * tile -- with a dashed border and a half-strength ground, so it sits in the grid as an
 * obvious gap to fill rather than competing with real content for attention.
 */
@Composable
fun AddTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .height(TileHeight)
            .clip(AppShape.Card)
            .background(Ink.Surface.copy(alpha = 0.4f))
            .border(1.dp, Ink.OutlineSoft, AppShape.Card)
            .tappable(pressScale = 0.975f, onClick = onClick)
            .padding(vertical = Space.lg, horizontal = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(Size.control)
                .clip(AppShape.Pill)
                .background(Ink.Accent.copy(alpha = 0.14f))
                .border(1.dp, Ink.Accent.copy(alpha = 0.3f), AppShape.Pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(Size.iconSm), tint = Ink.Accent)
        }
        Spacer(Modifier.height(Space.md))
        Text(
            text = label,
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A place you keep cards, at a third of the screen's width.
 *
 * The shelf format, for Home. Everything here has to survive being about 100dp wide, which
 * is what decides the content: the name, the one count that matters, and what it is worth.
 * The layout name goes -- "9-pocket · 3x3" is a fact about a binder you already recognise
 * by name, and it was the first thing to wrap and shove the value off the tile.
 */
@Composable
fun ShelfTile(
    name: String,
    value: String,
    count: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillFraction: Float? = null,
) {
    Column(
        modifier
            .clip(AppShape.Medium)
            .background(Ink.Surface)
            .border(1.dp, accent.copy(alpha = 0.25f), AppShape.Medium)
            .tappable(pressScale = 0.97f, onClick = onClick)
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(14.dp).clip(AppShape.Pill).background(accent))
            Spacer(Modifier.width(Space.sm))
            Text(
                text = name,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(Space.md))
        Text(
            text = value,
            color = moneyInk(value),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = count,
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )

        fillFraction?.let { fraction ->
            Spacer(Modifier.height(Space.sm))
            ProgressTrack(fraction = fraction, color = accent, height = 3.dp)
        }
    }
}

/**
 * The tick a selected tile wears, in the corner a badge would otherwise occupy.
 *
 * It replaces the badge rather than joining it: while a selection is being made, whether
 * this tile is in it matters more than how many cards it is still missing.
 */
@Composable
private fun SelectionCheck() {
    Box(
        Modifier
            .size(22.dp)
            .clip(AppShape.Pill)
            .background(Ink.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Check, "Selected", Modifier.size(Size.iconXs), tint = Color.White)
    }
}
