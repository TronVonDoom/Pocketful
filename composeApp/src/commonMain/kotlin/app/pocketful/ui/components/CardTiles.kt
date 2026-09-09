package app.pocketful.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketful.data.SearchHit
import app.pocketful.domain.CardBrief
import app.pocketful.domain.Finish
import app.pocketful.domain.PokemonType
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/**
 * A card, as a tile, with the art at the size it was drawn to be looked at.
 *
 * This replaces the full-width list row for every screen that is *browsing* cards. A row
 * spends the whole width of a phone on a 38dp thumbnail and three short strings, which
 * is a lot of space to give up in exchange for a picture too small to recognise -- and
 * recognising the picture is the entire task when you are working through a set or
 * hunting a spare. Two to a row, the art is roughly ten times the area, and the screen
 * shows six cards instead of eight while actually answering "is that the one".
 *
 * The proportions are the card's own. Cropping to a shorter box would fit more on screen
 * and would also cut the frame off the illustration, which is the part that says which
 * printing you are looking at.
 *
 * Everything that is not the card's identity is drawn *on* the art rather than under it:
 * the price, the status badge, the selection tick. That keeps the caption block exactly
 * two lines on every tile, which is what lets two tiles sit level in a row without the
 * grid measuring anything.
 */
@Composable
fun CardArtTile(
    name: String,
    caption: String,
    artStem: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: PokemonType? = null,
    value: String? = null,
    valueColor: Color = Ink.Gold,
    badge: String? = null,
    badgeColor: Color = Ink.TextSecondary,
    /** Its place in a ranking, drawn in the corner an ordered grid needs one. */
    rank: Int? = null,
    /** Renders the art as a ghost: a card the user does not own yet. */
    ghosted: Boolean = false,
    holo: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val outline by animateColorAsState(
        targetValue = if (selected) Ink.Accent else Ink.OutlineFaint,
        label = "cardTileOutline",
    )

    Column(
        modifier
            .clip(AppShape.Card)
            .background(if (selected) Ink.Accent.copy(alpha = 0.10f) else Ink.Surface)
            .border(if (selected) 2.dp else 1.dp, outline, AppShape.Card)
            .alpha(if (enabled) 1f else 0.5f)
            .tappable(enabled = enabled, pressScale = 0.975f, onLongClick = onLongClick, onClick = onClick)
            .padding(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT_RATIO)) {
            val shape = cardShape(maxWidth)
            Box(Modifier.fillMaxSize().clip(shape)) {
                CardArtwork(
                    artStem = artStem,
                    type = type,
                    modifier = Modifier.fillMaxSize().alpha(if (ghosted) 0.34f else 1f),
                    holo = holo && !ghosted,
                )

                // A wanted card is drawn faint, and a faint card on a near-black ground
                // loses its edges entirely -- so the ghost gets a floor to sit on.
                if (ghosted) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Ink.Background.copy(alpha = 0.55f)),
                            ),
                        ),
                    )
                }

                when {
                    selected -> TileMark(
                        Modifier.align(Alignment.TopEnd).padding(5.dp),
                    ) {
                        Icon(AppIcons.Check, "Selected", Modifier.size(12.dp), tint = Color.White)
                    }

                    // Dark plate, gold numeral -- the same treatment as the price chip
                    // rather than a solid gold disc. Card borders are yellow more often
                    // than they are anything else, and gold on yellow is a badge you have
                    // to hunt for on exactly the cards a ranking is most about.
                    rank != null -> ArtChip(
                        text = "$rank",
                        foreground = Ink.Gold,
                        background = Color.Black.copy(alpha = 0.74f),
                        modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                    )

                    badge != null -> ArtChip(
                        text = badge,
                        foreground = Color.White,
                        background = badgeColor.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                    )
                }

                // Both corners can be occupied at once when a ranked card is also
                // flagged, so the badge falls back to the opposite corner rather than
                // being dropped.
                if (rank != null && badge != null) {
                    ArtChip(
                        text = badge,
                        foreground = Color.White,
                        background = badgeColor.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                    )
                }

                value?.let {
                    ArtChip(
                        text = it,
                        foreground = valueColor,
                        background = Color.Black.copy(alpha = 0.74f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, Color.White.copy(alpha = 0.11f), shape),
                )
            }
        }

        Spacer(Modifier.height(9.dp))
        Text(
            text = name,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A card already in the catalog, as a tile. */
@Composable
fun CardTile(
    brief: CardBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String = "${brief.setName} · ${brief.collectorNumber}",
    value: String? = null,
    valueColor: Color = Ink.Gold,
    badge: String? = null,
    badgeColor: Color = Ink.TextSecondary,
    rank: Int? = null,
    ghosted: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    CardArtTile(
        name = brief.name,
        caption = caption,
        artStem = brief.artUrl,
        type = brief.type,
        value = value,
        valueColor = valueColor,
        badge = badge,
        badgeColor = badgeColor,
        rank = rank,
        ghosted = ghosted,
        holo = brief.finish != Finish.NON_HOLO,
        selected = selected,
        onLongClick = onLongClick,
        onClick = onClick,
        modifier = modifier,
    )
}

/** A card the collection does not have yet, as a tile. */
@Composable
fun CatalogCardTile(
    hit: SearchHit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String = hit.collectorNumber,
    badge: String? = null,
    badgeColor: Color = Ink.TextSecondary,
    value: String? = null,
    valueColor: Color = Ink.Gold,
    ghosted: Boolean = false,
    enabled: Boolean = true,
) {
    CardArtTile(
        name = hit.name,
        caption = caption,
        artStem = hit.artStem,
        value = value,
        valueColor = valueColor,
        badge = badge,
        badgeColor = badgeColor,
        ghosted = ghosted,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    )
}

/** A small solid label drawn over card art. Reads on any illustration. */
@Composable
private fun ArtChip(
    text: String,
    foreground: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(AppShape.Chip)
            .background(background)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The accented disc a selected tile wears. */
@Composable
private fun TileMark(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.size(20.dp).clip(AppShape.Pill).background(Ink.Accent),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

// --------------------------------------------------------------------- grids

/**
 * A list of things, two to a row, as lazy items.
 *
 * Every grid in the app was writing the same six lines -- chunk into pairs, emit a
 * [TilePair], hold the empty half open when the last row is short -- and each copy had
 * its own idea of what the item key should be. One of them keyed on the row index, which
 * is how a grid ends up animating the wrong tile when something above it is deleted.
 */
fun <T> LazyListScope.tileRows(
    items: List<T>,
    keyPrefix: String,
    key: (T) -> Any,
    content: @Composable RowScope.(T) -> Unit,
) {
    val rows = items.chunked(2)
    items(
        count = rows.size,
        key = { index -> "$keyPrefix-${key(rows[index].first())}" },
    ) { index ->
        TilePair {
            rows[index].forEach { entry -> content(entry) }
            if (rows[index].size == 1) TileGap()
        }
    }
}

/** A row of two tiles that are not part of a lazy list. */
@Composable
fun <T> TileRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(T) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { pair ->
            TilePair {
                pair.forEach { entry -> content(entry) }
                if (pair.size == 1) TileGap()
            }
        }
    }
}
