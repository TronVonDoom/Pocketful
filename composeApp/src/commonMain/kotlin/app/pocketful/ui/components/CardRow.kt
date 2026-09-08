package app.pocketful.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import app.pocketful.domain.CardBrief
import app.pocketful.domain.Finish
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.tint

/**
 * The card itself at list-row scale, falling back to a type-tinted swatch.
 *
 * The corner comes from [cardShape] rather than a fixed radius, so a thumbnail is cut to
 * the same proportion as the pocket and the sheet hero -- one card at three sizes rather
 * than three differently-rounded rectangles.
 */
@Composable
fun CardThumb(
    brief: CardBrief,
    modifier: Modifier = Modifier,
    width: Dp = 38.dp,
) {
    val shape = cardShape(width)
    Box(
        modifier
            .width(width)
            .aspectRatio(CARD_ASPECT_RATIO)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
    ) {
        CardArtwork(
            artStem = brief.artUrl,
            type = brief.type,
            modifier = Modifier.fillMaxSize(),
        )
        // A static highlight rather than the animated sheen: a scrolling list of forty
        // rows running forty infinite animations is a battery cost with no payoff at
        // 38dp wide.
        if (brief.finish != Finish.NON_HOLO && brief.artUrl == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.20f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.10f),
                            ),
                        ),
                    ),
            )
        }
    }
}

/**
 * The one row shape used by search results, the card list and the stats leaderboard.
 *
 * Trailing content is a slot rather than a fixed value + caption pair, because the three
 * callers want genuinely different things on the right and faking it with optional
 * strings produced a row with five nullable parameters and no clear contract.
 *
 * [onLongClick] is how a card list starts a multi-select, and [selected] is drawn as a
 * tinted ground plus an accented border that is always there -- faint when unselected --
 * so a row never changes height as it is picked and a list does not jump under a sweep.
 */
@Composable
fun CardListRow(
    brief: CardBrief,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingBadge: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val outline by animateColorAsState(
        targetValue = if (selected) Ink.Accent else Ink.OutlineFaint,
        label = "rowOutline",
    )
    val base = modifier
        .clip(AppShape.Medium)
        .background(if (selected) Ink.Accent.copy(alpha = 0.10f) else Ink.Surface)
        .border(if (selected) 2.dp else 1.dp, outline, AppShape.Medium)

    Row(
        modifier = (
            if (onClick != null) {
                base.tappable(pressScale = 0.985f, onLongClick = onLongClick, onClick = onClick)
            } else {
                base
            }
            ).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardThumb(brief)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = brief.name,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle ?: "${brief.setName} · ${brief.collectorNumber}",
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val badges = listOfNotNull(leadingBadge, brief.badge)
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    leadingBadge?.let {
                        Tag(it, color = brief.type.tint, background = brief.type.tint.copy(alpha = 0.14f))
                    }
                    brief.badge?.let { Tag(it) }
                }
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) { trailing() }
        }

        if (selected) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(AppShape.Pill)
                    .background(Ink.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Check, "Selected", Modifier.size(13.dp), tint = Color.White)
            }
        }
    }
}

/** Right-hand price plus a caption, the trailing block most rows want. */
@Composable
fun ValueTrailing(
    value: String,
    caption: String? = null,
    valueColor: Color = Ink.TextPrimary,
    captionColor: Color = Ink.TextTertiary,
) {
    Text(value, color = valueColor, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    if (caption != null) {
        Spacer(Modifier.height(2.dp))
        Text(caption, color = captionColor, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * The card a sheet is acting on, stated once at the top so every step of a multi-step
 * flow keeps its subject visible.
 */
@Composable
fun CardHero(brief: CardBrief, valueLabel: String, caption: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CardThumb(brief, width = 58.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = brief.name,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${brief.setName} · ${brief.collectorNumber}",
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Tag(caption)
                brief.rarity?.let { Tag(it) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(valueLabel, color = Ink.Gold, style = MaterialTheme.typography.titleLarge, maxLines = 1)
    }
}

/** Label on the left, value on the right. The shape every detail readout in the app uses. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink.TextPrimary,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(value, color = valueColor, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}
