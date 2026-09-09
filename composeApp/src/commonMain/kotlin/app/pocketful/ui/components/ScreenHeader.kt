package app.pocketful.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketful.domain.ValueSummary
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.headerBloom
import app.pocketful.ui.theme.headerGlow

/**
 * The top of every screen, in one shape.
 *
 * A header answers four questions, always in the same order, and each has exactly one
 * place to be:
 *
 *   1. where am I, and how do I get out            (rail: eyebrow, back, actions)
 *   2. what is this                                (identity: cover, title, subtitle)
 *   3. the one number it is about                  (headline, with its gain)
 *   4. the figures that qualify that number        (stat rail)
 *
 * Anything else a screen wants goes *below* the header as content, with two exceptions
 * that are part of the header by right: [tags], which describe the thing named in the
 * identity block, and [footer], which is the screen's primary action. A "make a binder
 * from this set" button belongs against the set it would be made from, not floating in
 * the list underneath it.
 *
 * The stat rail is the piece that changed most. Free-floating figures under a headline
 * are cheap in height and read as a caption that ran long; the rail gives the header a
 * floor, which is what makes the block above it look composed rather than merely stacked.
 * It costs one line of height over the loose strip and it is the difference between a
 * screen that opens on a number and a screen that opens on a summary.
 */
@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    /**
     * The small tracked line above everything. Names the *section*, not the thing --
     * "COLLECTION", "SET", "TRADE" -- so the title underneath is free to be the thing's
     * own name rather than a signpost repeating the tab you just tapped.
     */
    eyebrow: String? = null,
    /**
     * The thing's name, in the rail beside the back button, instead of the tracked
     * eyebrow above it.
     *
     * For a screen whose identity is one short line and whose content wants the height --
     * a binder page, where the page itself is the screen. Naming the binder in the rail
     * and putting its figures straight underneath saves the eyebrow, the headline block
     * and the gap between them, which on a phone is the difference between a 3x3 page
     * that fits and one you scroll to see the bottom row of.
     */
    railTitle: String? = null,
    title: String? = null,
    subtitle: String? = null,
    headline: String? = null,
    /** One line under the headline, for what the figure is *of*. */
    headlineCaption: String? = null,
    /**
     * Centres the identity and headline. For the tab screens, where there is no back
     * button anchoring a left edge and a big number hugging the left margin reads as a
     * layout missing its heading.
     */
    centered: Boolean = false,
    summary: ValueSummary? = null,
    stats: List<Stat> = emptyList(),
    leading: @Composable (() -> Unit)? = null,
    cover: @Composable (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    tags: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        horizontalAlignment = align,
    ) {
        // The rail is drawn only when it carries something. On a tab screen with no back
        // button and no actions it would otherwise be an invisible band of padding above
        // the number -- exactly the height the old titles were removed to reclaim.
        if (leading != null || actions != null || eyebrow != null || railTitle != null) {
            Row(
                Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(10.dp))
                }
                when {
                    railTitle != null -> Text(
                        text = railTitle,
                        color = Ink.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    eyebrow != null -> Eyebrow(eyebrow, Modifier.weight(1f))
                    else -> Spacer(Modifier.weight(1f))
                }
                if (actions != null) {
                    Spacer(Modifier.width(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }

        // Drawn for a subtitle on its own too: a header that names its subject in the rail
        // still has somewhere to put the line describing it.
        if (title != null || cover != null || subtitle != null) {
            Spacer(Modifier.height(if (title == null && cover == null) 4.dp else 10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
            ) {
                if (cover != null) {
                    cover()
                    Spacer(Modifier.width(13.dp))
                }
                Column(
                    modifier = if (centered && cover == null) Modifier else Modifier.weight(1f),
                    horizontalAlignment = align,
                ) {
                    title?.let {
                        Text(
                            text = it,
                            color = Ink.TextPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = textAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    subtitle?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = it,
                            color = Ink.TextTertiary,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = textAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (headline != null) {
            Spacer(Modifier.height(if (title != null || cover != null) 14.dp else 8.dp))
            // The headline and its gain share a baseline rather than stacking. Two lines
            // here is the single biggest thing that used to push content off the bottom
            // of the first screenful.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = headline,
                    color = Ink.TextPrimary,
                    style = if (centered && title == null) {
                        MaterialTheme.typography.displayMedium
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                summary?.let {
                    Spacer(Modifier.width(9.dp))
                    GainChip(it, Modifier.padding(bottom = 4.dp))
                }
            }
            headlineCaption?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = textAlign,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(if (headline != null) 14.dp else 12.dp))
            StatRail(stats)
        }

        if (tags != null) {
            Spacer(Modifier.height(11.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (centered) {
                    Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
                } else {
                    Arrangement.spacedBy(5.dp)
                },
                verticalArrangement = Arrangement.spacedBy(5.dp),
                content = tags,
            )
        }

        if (footer != null) {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth(), content = footer)
        }
    }
}

/** The tracked label a header opens with. Also the section label inside a sheet. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Ink.TextTertiary) {
    Text(
        text = text.uppercase(),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ------------------------------------------------------------------ stat rail

/** One figure and what it means. The unit a [StatRail] is assembled from. */
data class Stat(
    val value: String,
    val label: String,
    val accent: Color = Ink.TextPrimary,
)

/**
 * The figures under a headline, as one object.
 *
 * Laid out on a grid rather than flowed, because the alternative -- a wrapping strip of
 * value/label pairs -- lands the second row's figures at whatever x the first row happened
 * to end at, and a column of numbers that does not line up is the thing that makes a
 * summary look computed rather than designed.
 *
 * At most six, in rows of at most four. Beyond that a header is a table, and a screen that
 * opens on a table has buried whatever it was actually for.
 */
@Composable
fun StatRail(stats: List<Stat>, modifier: Modifier = Modifier) {
    val shown = stats.take(6)
    if (shown.isEmpty()) return

    val columns = if (shown.size <= 4) shown.size else 3
    val rows = shown.chunked(columns)

    Column(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(Ink.Surface.copy(alpha = 0.55f))
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Hairline()
            Row(Modifier.fillMaxWidth().height(52.dp)) {
                repeat(columns) { column ->
                    val stat = row.getOrNull(column)
                    if (stat == null) {
                        // The empty half of a short last row is held open so the columns
                        // above it stay in line -- but without a rule in front of it,
                        // which would draw a cell around nothing and read as a figure
                        // that failed to load.
                        Spacer(Modifier.weight(1f))
                    } else {
                        if (column > 0) VerticalHairline()
                        StatCell(stat, dense = columns >= 4, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(stat: Stat, dense: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stat.value,
            color = stat.accent,
            style = if (dense) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stat.label.uppercase(),
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The rule between two cells of a [StatRail]. */
@Composable
private fun VerticalHairline() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Ink.OutlineFaint))
}

// ------------------------------------------------------------------- backdrop

/**
 * The light behind a screen's header.
 *
 * A wash the width of the screen, with a bloom in the corner giving it somewhere to come
 * from, and a second bloom in a cooler colour so the tint has depth rather than reading as
 * one flat colour at low opacity. Every screen sets [accent] to whatever it is about --
 * a binder's spine, gold for the card list, green for trade -- which makes the colour a
 * piece of information rather than decoration.
 */
@Composable
fun ScreenBackdrop(
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
) {
    Box(modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().background(headerGlow(accent)))
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-70).dp, y = (-110).dp)
                .size(280.dp)
                .background(headerBloom(accent, strength = 0.22f), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-60).dp)
                .size(240.dp)
                .background(headerBloom(Ink.Aura, strength = 0.16f), CircleShape),
        )
    }
}

// -------------------------------------------------------------------- pieces

/**
 * Unrealised gain as one chip, next to the number it is a gain on.
 *
 * Percentage and amount together: the percentage alone hides whether +40% is forty
 * dollars or four thousand, and the amount alone hides whether it is a good year.
 */
@Composable
fun GainChip(summary: ValueSummary, modifier: Modifier = Modifier) {
    val percentLabel = summary.gainPercentLabel ?: return
    val up = summary.unrealizedGain.cents >= 0
    val color = if (up) Ink.Gain else Ink.Loss

    Row(
        modifier
            .clip(AppShape.Chip)
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.26f), AppShape.Chip)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$percentLabel · ${if (up) "+" else ""}${summary.unrealizedGain.format(compact = true)}",
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/**
 * An action in a header rail.
 *
 * 34dp rather than the 38dp used elsewhere. Header actions are secondary to the content
 * below them -- three of them at full size were as tall as the headline figure they sat
 * next to, which made every screen open on a row of buttons.
 */
@Composable
fun HeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink.TextSecondary,
) {
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineSoft, CircleShape)
            .tappable(pressScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(15.dp), tint = tint)
    }
}

/**
 * A labelled action under a header. Small enough to be a caption, big enough to hit.
 *
 * Used where an icon alone would not say what it does -- "New container" has no obvious
 * glyph, and a header full of ambiguous circles is worse than one short row of words.
 */
@Composable
fun HeaderChipAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink.TextSecondary,
) {
    Row(
        modifier
            .height(38.dp)
            .clip(AppShape.Pill)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineSoft, AppShape.Pill)
            .tappable(pressScale = 0.95f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
