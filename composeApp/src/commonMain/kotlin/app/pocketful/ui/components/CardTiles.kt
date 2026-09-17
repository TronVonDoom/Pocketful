package app.pocketful.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
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
import app.pocketful.ui.theme.Motion
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space

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
    /** The picture, as a stem. See [app.pocketful.data.CardArt]. */
    art: String?,
    onClick: () -> Unit,
    /** The card back to draw when there is no [art]. */
    back: String? = null,
    modifier: Modifier = Modifier,
    type: PokemonType? = null,
    value: String? = null,
    valueColor: Color = Ink.Gold,
    badge: String? = null,
    badgeColor: Color = Ink.TextSecondary,
    /**
     * How many of this card you have.
     *
     * Its own mark rather than a word in [badge], because a quantity and a status are
     * different kinds of fact and were sharing one slot: a tile could say "OWN 2" or it
     * could say "TRADE", never both, and the two read as the same kind of label while
     * meaning entirely different things. A count now has its own corner and its own shape
     * -- a disc, so it reads as a tally at a glance the way a notification count does.
     *
     * Null or 1 draws nothing. A tile in a list of cards you own does not need a badge on
     * every single one saying "1"; the number is only news when there is more than one.
     */
    count: Int? = null,
    /** Its place in a ranking, drawn in the corner an ordered grid needs one. */
    rank: Int? = null,
    /** Renders the art as a ghost: a card the user does not own yet. */
    ghosted: Boolean = false,
    holo: Boolean = false,
    /** What [holo] is, for the icon's content description -- "Holo", "Reverse Holo". */
    holoLabel: String = "Foil",
    /** Up for trade. Drawn as the same icon a binder pocket wears, in the same pill. */
    forTrade: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    /**
     * Adds one of this card straight to wherever search is adding to, without opening
     * anything. Drawn as a plus in the bottom-left corner of the art when set.
     */
    onQuickAdd: (() -> Unit)? = null,
) {
    val outline by animateColorAsState(
        targetValue = if (selected) Ink.Accent else Ink.OutlineFaint,
        animationSpec = Motion.fast(),
        label = "cardTileOutline",
    )

    Column(
        modifier
            .clip(AppShape.Card)
            .background(if (selected) Ink.Accent.copy(alpha = 0.10f) else Ink.Surface)
            .border(if (selected) 2.dp else 1.dp, outline, AppShape.Card)
            .alpha(if (enabled) 1f else 0.5f)
            .tappable(enabled = enabled, pressScale = 0.975f, onLongClick = onLongClick, onClick = onClick)
            .padding(Space.sm),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT_RATIO)) {
            val shape = cardShape(maxWidth)
            Box(Modifier.fillMaxSize().clip(shape)) {
                CardArtwork(
                    art = art,
                    back = back,
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

                // Top-right is the tile's tally corner: the selection tick while a
                // selection is running, the quantity otherwise. Both answer "how many of
                // this am I dealing with", and a tick over a count would be two answers.
                when {
                    selected -> TileMark(
                        Modifier.align(Alignment.TopEnd).padding(5.dp),
                    ) {
                        Icon(AppIcons.Check, "Selected", Modifier.size(12.dp), tint = Color.White)
                    }

                    (count ?: 0) > 1 -> CountBadge(
                        count = count!!,
                        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                    )
                }

                when {

                    // Dark plate, gold numeral, prefixed so it cannot be misread as a count
                    // -- the same treatment as the price chip rather than a solid gold disc.
                    // Card borders are yellow more often than they are anything else, and
                    // gold on yellow is a badge you have to hunt for on exactly the cards a
                    // ranking is most about.
                    rank != null -> ArtChip(
                        text = "#$rank",
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

                // A ranked tile that is also flagged puts the flag at the bottom-left,
                // not the top-right: that corner belongs to the count now, and a badge
                // that sometimes appears there and sometimes does not is precisely the
                // inconsistency this corner grammar exists to remove.
                if (rank != null && badge != null) {
                    ArtChip(
                        text = badge,
                        foreground = Color.White,
                        background = badgeColor.copy(alpha = 0.92f),
                        modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
                    )
                }

                // Foil, for trade, worth -- the same single pill a binder pocket wears in
                // the same corner, so a card looks like the same object whether it is found
                // on a shelf of tiles or turned to on a page.
                CardStatusPill(
                    forTrade = forTrade,
                    holo = holo && !ghosted,
                    holoLabel = holoLabel,
                    price = value,
                    priceColor = valueColor,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                )

                if (onQuickAdd != null && !selected) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(5.dp)
                            .size(28.dp)
                            .clip(AppShape.Pill)
                            .background(Ink.Accent)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), AppShape.Pill)
                            .tappable(enabled = enabled, pressScale = 0.85f, onClick = onQuickAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Plus, "Add one", Modifier.size(14.dp), tint = Color.White)
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.dp, Color.White.copy(alpha = 0.11f), shape),
                )
            }
        }

        Spacer(Modifier.height(Space.sm + 2.dp))
        Text(
            text = name,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Space.xxs))
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
    count: Int? = null,
    rank: Int? = null,
    ghosted: Boolean = false,
    forTrade: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onQuickAdd: (() -> Unit)? = null,
) {
    CardArtTile(
        name = brief.name,
        caption = caption,
        art = brief.art,
        back = brief.back,
        type = brief.type,
        value = value,
        valueColor = valueColor,
        badge = badge,
        badgeColor = badgeColor,
        count = count,
        rank = rank,
        ghosted = ghosted,
        holo = brief.finish != Finish.NON_HOLO,
        holoLabel = brief.finish.label,
        forTrade = forTrade,
        selected = selected,
        onLongClick = onLongClick,
        onQuickAdd = onQuickAdd,
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
    count: Int? = null,
    value: String? = null,
    valueColor: Color = Ink.Gold,
    ghosted: Boolean = false,
    enabled: Boolean = true,
    onQuickAdd: (() -> Unit)? = null,
) {
    CardArtTile(
        name = hit.name,
        caption = caption,
        art = hit.image,
        back = hit.back,
        value = value,
        valueColor = valueColor,
        badge = badge,
        badgeColor = badgeColor,
        count = count,
        ghosted = ghosted,
        enabled = enabled,
        onQuickAdd = onQuickAdd,
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

/**
 * What a card carries beyond its own art: is it foil, is it on the table, what is it worth.
 *
 * The one piece of chrome shared between a binder pocket and a card tile, because the two are
 * answering the same question about the same kind of object -- "what is this copy" -- and a
 * card that wore a different costume depending on which screen happened to be showing it
 * would read as two apps stitched together rather than one. Each piece appears only when it
 * has something to say, so a plain near-mint card you are keeping wears nothing but its
 * price, and a card with nothing to report wears nothing at all.
 *
 * [grounded] is false where a caption scrim is already behind it -- a second dark patch on
 * an already-dark strip reads as a rendering fault rather than as a pill. See [PocketSlot]'s
 * own use of this for the case that started it.
 */
@Composable
fun CardStatusPill(
    modifier: Modifier = Modifier,
    forTrade: Boolean = false,
    holo: Boolean = false,
    holoLabel: String = "Foil",
    price: String? = null,
    priceColor: Color = Ink.Gold,
    grounded: Boolean = true,
) {
    if (!forTrade && !holo && price == null) return

    // Tight vertically, generous horizontally. The pill sits over the bottom edge of the
    // artwork, so every dp of its height is a dp of a card it is covering -- while its width
    // costs nothing, the corner it occupies being the card's own margin.
    Row(
        modifier
            .clip(AppShape.Pill)
            .background(if (grounded) Color.Black.copy(alpha = 0.72f) else Color.Transparent)
            .padding(horizontal = if (grounded) 5.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (forTrade) {
            Icon(AppIcons.Trade, "Up for trade", Modifier.size(11.dp), tint = Ink.Gain)
        }
        if (holo) {
            Icon(AppIcons.Sparkle, holoLabel, Modifier.size(11.dp), tint = Ink.Foil)
        }
        if (price != null) {
            Text(
                text = price,
                color = priceColor,
                fontSize = 10.sp,
                // The line box, not the glyphs. Left at its default the text carried four dp
                // of leading the icons beside it did not have, and that padding was most of
                // the pill's height.
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * A quantity, as a disc.
 *
 * Written "×4" rather than a bare "4". The rank chip in the opposite corner is also a bare
 * number, and a tile that could carry both at once -- your second most valuable card, of
 * which you own four -- needs the two to be unmistakable at a glance rather than each other's
 * near-twin. "×" is the one glyph that only ever means multiplication.
 *
 * Round at one digit and stretching to a pill at two or more, which is the whole of the
 * shape rule: a circle is the right frame for a short glyph and the wrong one for three,
 * so the minimum size holds the circle and the padding takes over once the text outgrows
 * it. Same treatment as the price chip -- dark plate, no colour of its own -- because a
 * count is a fact about the card rather than a status worth a hue.
 */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val text = if (count > 99) "×99+" else "×$count"
    Box(
        modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(AppShape.Pill)
            .background(Color.Black.copy(alpha = 0.74f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), AppShape.Pill)
            .padding(horizontal = if (text.length > 1) 5.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
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

// ---------------------------------------------------------------------- rows

/**
 * A card as a full-width row: art, identity, figure.
 *
 * The counterpart to [CardArtTile], for the two places a grid is the wrong shape. Search
 * results are one -- you are reading names against what you typed, and a name is a line of
 * text, so a list reads faster than a grid of pictures whose captions you have to hunt for.
 * The list view of a collection is the other, where the point is to compare figures down a
 * column rather than to recognise illustrations.
 *
 * The thumbnail is deliberately generous. A 38dp stamp beside three strings was the old
 * row's failure: it spent the whole width of a phone and still could not answer "is that
 * the one", which is the question a picture is there to answer at all.
 */
@Composable
fun CardResultRow(
    name: String,
    caption: String,
    art: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    back: String? = null,
    type: PokemonType? = null,
    holo: Boolean = false,
    value: String? = null,
    valueColor: Color = Ink.Gold,
    trend: String? = null,
    badge: String? = null,
    badgeColor: Color = Ink.TextSecondary,
    count: Int? = null,
    ghosted: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onQuickAdd: (() -> Unit)? = null,
) {
    val outline by animateColorAsState(
        targetValue = if (selected) Ink.Accent else Ink.OutlineFaint,
        animationSpec = Motion.fast(),
        label = "rowOutline",
    )

    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(if (selected) Ink.Accent.copy(alpha = 0.10f) else Ink.Surface)
            .border(if (selected) 2.dp else 1.dp, outline, AppShape.Medium)
            .alpha(if (enabled) 1f else 0.5f)
            .tappable(enabled = enabled, pressScale = 0.985f, onLongClick = onLongClick, onClick = onClick)
            .padding(Space.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(46.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT_RATIO)) {
                val shape = cardShape(maxWidth)
                Box(Modifier.fillMaxSize().clip(shape)) {
                    CardArtwork(
                        art = art,
                        back = back,
                        type = type,
                        modifier = Modifier.fillMaxSize().alpha(if (ghosted) 0.4f else 1f),
                        holo = holo && !ghosted,
                    )
                    Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.11f), shape))
                }
            }
        }

        Spacer(Modifier.width(Space.md))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // The quantity rides with the name rather than in a corner, because in a row
                // there is no corner -- and "Charizard x3" is how anyone would say it aloud.
                if ((count ?: 0) > 1) {
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = "×$count",
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(Space.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = caption,
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Spacer(Modifier.width(Space.sm))
                    Tag(badge, color = badgeColor, background = badgeColor.copy(alpha = 0.16f))
                }
            }
        }

        Spacer(Modifier.width(Space.md))

        // Value and its move, right-aligned in their own column, so figures line up down the
        // list. That column is the whole reason a list view exists.
        if (value != null || trend != null) {
            Column(horizontalAlignment = Alignment.End) {
                value?.let {
                    Text(
                        text = it,
                        color = valueColor,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }
                trend?.let {
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = it,
                        color = if (it.startsWith("▲")) Ink.Gain else Ink.Loss,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }

        if (onQuickAdd != null) {
            Spacer(Modifier.width(Space.md))
            Box(
                Modifier
                    .size(Size.control - 4.dp)
                    .clip(AppShape.Pill)
                    .background(Ink.Accent.copy(alpha = 0.16f))
                    .border(1.dp, Ink.Accent.copy(alpha = 0.35f), AppShape.Pill)
                    .tappable(enabled = enabled, pressScale = 0.85f, onClick = onQuickAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Plus, "Add one", Modifier.size(Size.iconSm), tint = Ink.Accent)
            }
        }

        if (selected) {
            Spacer(Modifier.width(Space.md))
            Box(
                Modifier.size(22.dp).clip(AppShape.Pill).background(Ink.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Check, "Selected", Modifier.size(Size.iconXs), tint = Color.White)
            }
        }
    }
}

/** A card already in the catalog, as a row. */
@Composable
fun CardRowItem(
    brief: CardBrief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String = "${brief.setName} · ${brief.collectorNumber}",
    value: String? = null,
    valueColor: Color = Ink.Gold,
    trend: String? = null,
    badge: String? = null,
    badgeColor: Color = Ink.TextSecondary,
    count: Int? = null,
    ghosted: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onQuickAdd: (() -> Unit)? = null,
) {
    CardResultRow(
        name = brief.name,
        caption = caption,
        art = brief.art,
        back = brief.back,
        type = brief.type,
        holo = brief.finish != Finish.NON_HOLO,
        value = value,
        valueColor = valueColor,
        trend = trend,
        badge = badge,
        badgeColor = badgeColor,
        count = count,
        ghosted = ghosted,
        selected = selected,
        onLongClick = onLongClick,
        onQuickAdd = onQuickAdd,
        onClick = onClick,
        modifier = modifier,
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
        // Rows slide rather than jump when the list behind them changes. Every screen
        // built on this grid is one you filter, sort or delete from, and a tile that
        // teleports into the gap left by a deleted neighbour reads as a redraw rather
        // than as the list settling.
        TilePair(Modifier.animateItem()) {
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        items.chunked(2).forEach { pair ->
            TilePair {
                pair.forEach { entry -> content(entry) }
                if (pair.size == 1) TileGap()
            }
        }
    }
}
