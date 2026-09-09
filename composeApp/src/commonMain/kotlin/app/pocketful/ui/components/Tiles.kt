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
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/**
 * The height every tile in the app is drawn at.
 *
 * Fixed rather than wrapped. A grid whose rows each measure themselves lands on tiles a
 * few pixels apart in height -- one binder has a fill bar and the next does not, one card
 * has a badge and the next does not -- and the eye reads that unevenness as sloppiness
 * long before it works out the cause. One height for every tile also means the "add
 * another" tile is the same object as the things it sits beside instead of a shorter
 * button wedged into the grid.
 *
 * Sized for the tallest thing any tile carries -- a card thumbnail with a three-line
 * identity and a badge beside it -- plus a little headroom, since a fixed height is the
 * one layout choice that has nowhere to put the overflow when a user turns their system
 * font up.
 */
val TileHeight: Dp = 156.dp

/**
 * Two tiles to a row, with a gap where a second one would go.
 *
 * The odd-one-out case is the reason this exists: letting a lone final tile stretch to
 * full width makes it read as a different, more important kind of thing than the ones
 * above it, so the empty half is held open instead.
 */
@Composable
fun TilePair(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
 * drawn -- faint when unselected, accented when selected -- so picking a tile never
 * changes its size and the grid does not twitch as a selection is swept across it.
 */
@Composable
internal fun TileSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    // A tile for something the app cannot open yet -- an unconnected game -- rather than
    // one that is merely unselected. It dims and stops responding instead of being left
    // out of the grid, because "this game exists and is not wired up" is information.
    enabled: Boolean = true,
    /**
     * The colour of the thing on the tile, washed faintly into its ground and edge.
     *
     * A shelf of binders is recognised by colour long before it is read, and a grid of
     * identical grey rectangles throws that away -- the spine colour was doing its work
     * inside one 32dp drawing and nowhere else. Kept deliberately faint: enough that six
     * tiles read as six different objects at arm's length, not so much that the grid
     * turns into a colour chart and the values stop being the brightest thing on it.
     */
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val outline by animateColorAsState(
        targetValue = when {
            selected -> Ink.Accent
            accent != null -> accent.copy(alpha = 0.30f)
            else -> Ink.OutlineFaint
        },
        label = "tileOutline",
    )
    val ground = when {
        selected -> SolidColor(Ink.Accent.copy(alpha = 0.10f))
        // Top-weighted, so the wash reads as light falling across the tile rather than as
        // a panel somebody filled in.
        accent != null -> Brush.verticalGradient(
            0f to accent.copy(alpha = 0.14f),
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
            .padding(11.dp),
        content = content,
    )
}

/**
 * A binder or a container, as a tile.
 *
 * Two blocks with air between them: who this is along the top, what it holds along the
 * bottom. The cover used to open the tile on a line of its own, which spent a whole row
 * of height saying something the tile then repeated in words underneath, and left the
 * name sharing its line with nothing. So the name takes the top edge at full width -- it
 * is the thing being hunted for, and it no longer truncates to two words -- and the cover
 * drops to the floor to anchor the figures, where a drawing of a 3x3 page sits against
 * the count of how many of those pockets are full.
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
    // something ("4 wanted"), and tinting it with the spine colour made the same status
    // read as an alarm on a red binder and as decoration on a green one.
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
                Spacer(Modifier.height(2.dp))
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
                    Spacer(Modifier.width(8.dp))
                    SelectionCheck()
                }
                badge != null -> {
                    Spacer(Modifier.width(8.dp))
                    Tag(badge, color = badgeColor, background = badgeColor.copy(alpha = 0.16f))
                }
            }
        }

        // All of the slack in one place, above the floor. One weighted spacer, not two:
        // a second one further down would share the leftover height with this rather than
        // sit under it, and the fill bar would drift into the middle of the tile.
        Spacer(Modifier.weight(1f))

        // A fill bar is a fact about the figures it touches, not a third line of the
        // identity block, so it rides directly on top of them.
        if (fillFraction != null) {
            ProgressTrack(fraction = fillFraction, color = accent, height = 3.dp)
            Spacer(Modifier.height(9.dp))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            cover()
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            gainLabel?.let {
                Spacer(Modifier.width(6.dp))
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

/**
 * The "add another one of these" tile that closes a grid of storage.
 *
 * The same size and shape as the tiles it follows -- that is the whole point of it being
 * a tile -- with a softer border and a half-strength ground so it sits in the grid
 * without competing with real content for attention.
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
            .background(Ink.Surface.copy(alpha = 0.5f))
            .border(1.dp, Ink.OutlineSoft, AppShape.Card)
            .tappable(pressScale = 0.975f, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(AppShape.Pill)
                .background(Ink.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = Ink.TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
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
 * The tick a selected tile wears, in the corner a badge would otherwise occupy.
 *
 * It replaces the badge rather than joining it: while a selection is being made, whether
 * this tile is in it matters more than how many cards it is still missing.
 */
@Composable
private fun SelectionCheck() {
    Box(
        Modifier
            .size(20.dp)
            .clip(AppShape.Pill)
            .background(Ink.Accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Check, "Selected", Modifier.size(12.dp), tint = Color.White)
    }
}
