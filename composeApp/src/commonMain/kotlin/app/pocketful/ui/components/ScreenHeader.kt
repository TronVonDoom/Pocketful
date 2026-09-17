package app.pocketful.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import app.pocketful.state.NO_PRICE
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Motion
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space
import app.pocketful.ui.theme.headerBloom
import app.pocketful.ui.theme.headerGlow

/**
 * The bar across the top of a screen.
 *
 * Every screen in the app now opens with this and only this, at exactly one height, with
 * the back affordance always in the same place and the actions always in the other. That
 * sounds obvious and it is the thing the old headers did not do: a tab screen had no bar at
 * all and hung its actions off the side of a big number, a detail screen had a rail of a
 * different height, and the binder page had a third arrangement again. Three grammars for
 * "where am I and how do I leave" is three things to learn.
 *
 * The title can be absent. A screen whose first content block names itself -- a portfolio
 * opening on its own total -- does not need the bar to say it twice, so the bar carries
 * just the controls and gets out of the way.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Back",
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Size.topBar)
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            CircleIconButton(
                icon = app.pocketful.ui.theme.AppIcons.ChevronLeft,
                contentDescription = backDescription,
                onClick = onBack,
                size = Size.control,
            )
            Spacer(Modifier.width(Space.md))
        }

        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (actions != null) {
            Spacer(Modifier.width(Space.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * The one figure a screen is about, at the size it deserves.
 *
 * Separated from [TopBar] on purpose. A headline is *content* -- it scrolls away, it
 * changes under you, it is the answer to the question the screen was opened to ask -- and
 * for as long as it lived inside the header component it dragged the navigation controls
 * along with it, which is why the old headers were four blocks tall and the first real
 * content on any screen started below the fold.
 *
 * [caption] says what the figure is of. [trailing] is where a change chip goes, sharing the
 * figure's baseline rather than stacking under it.
 */
@Composable
fun Headline(
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    label: String? = null,
    centered: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(modifier.fillMaxWidth(), horizontalAlignment = align) {
        if (label != null) {
            Eyebrow(label)
            Spacer(Modifier.height(Space.sm))
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // The one number that changes under you: a card is added, a sync lands, a price
            // moves. Counting it up reads as the figure being recalculated; swapping the
            // glyphs instantly reads as the screen having been rebuilt behind your back.
            // Upwards for a rise and downwards for a fall, so the direction is legible
            // before the digits are.
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    // Compared as text, because that is all a headline has: the figure
                    // arrives already formatted, grouped and possibly abbreviated. A longer
                    // string is a larger number ("$1,204.00" over "$980.00") and same-length
                    // strings order correctly digit by digit, which is right often enough
                    // for a cue about direction.
                    val rising = compareValuesBy(targetState, initialState, { it.length }, { it }) > 0
                    val distance = if (rising) 1 else -1
                    (
                        slideInVertically(tween(Motion.BASE, easing = Motion.enter)) { h -> distance * h / 3 } +
                            fadeIn(tween(Motion.BASE))
                        ).togetherWith(
                        slideOutVertically(tween(Motion.BASE, easing = Motion.exit)) { h -> -distance * h / 3 } +
                            fadeOut(tween(Motion.FAST)),
                    )
                },
                label = "headline",
            ) { shown ->
                Text(
                    text = shown,
                    // The dash that stands for "no price yet" is not a figure, and setting it
                    // in the same weight and colour as one turns it into a short solid bar
                    // where the number should be -- which reads as a headline that failed to
                    // load rather than as an empty collection. Dropping it to the disabled
                    // ink keeps it honest and stops it impersonating content.
                    color = if (shown == NO_PRICE) Ink.TextDisabled else Ink.TextPrimary,
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(Space.md))
                Box(Modifier.padding(bottom = Space.xs + 2.dp)) { trailing() }
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = caption,
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = textAlign,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The top of a detail screen, in one shape.
 *
 * Retained with its original signature because the screens that push onto a detail view --
 * a set, a container -- were already saying exactly the right things in exactly the right
 * order, and rewriting their call sites would have changed a lot of text to change nothing
 * a user sees. What did change is underneath: every measurement now comes from the scale,
 * so this block sits on the same grid as the screens around it.
 *
 * A header answers four questions, always in the same order:
 *
 *   1. where am I, and how do I get out            (rail: eyebrow, back, actions)
 *   2. what is this                                (identity: cover, title, subtitle)
 *   3. the one number it is about                  (headline, with its gain)
 *   4. the figures that qualify that number        (stat rail)
 *
 * Anything else a screen wants goes *below* the header as content, with two exceptions that
 * are part of the header by right: [tags], which describe the thing named in the identity
 * block, and [footer], which is the screen's primary action.
 */
@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    railTitle: String? = null,
    title: String? = null,
    subtitle: String? = null,
    headline: String? = null,
    headlineCaption: String? = null,
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
        modifier.fillMaxWidth().padding(top = Space.xs, bottom = Space.md),
        horizontalAlignment = align,
    ) {
        // The rail is drawn only when it carries something. On a screen with no back button
        // and no actions it would otherwise be an invisible band of padding above the
        // number -- exactly the height the old titles were removed to reclaim.
        if (leading != null || actions != null || eyebrow != null || railTitle != null) {
            Row(
                Modifier.fillMaxWidth().height(Size.control),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(Space.md))
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
                    Spacer(Modifier.width(Space.md))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }

        // Drawn for a subtitle on its own too: a header that names its subject in the rail
        // still has somewhere to put the line describing it.
        if (title != null || cover != null || subtitle != null) {
            Spacer(Modifier.height(if (title == null && cover == null) Space.xs else Space.md))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
            ) {
                if (cover != null) {
                    cover()
                    Spacer(Modifier.width(Space.md + 2.dp))
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
                        Spacer(Modifier.height(Space.xs))
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
            Spacer(Modifier.height(if (title != null || cover != null) Space.lg else Space.sm))
            Headline(
                value = headline,
                caption = headlineCaption,
                centered = centered,
                trailing = summary?.let { { GainChip(it) } },
            )
        }

        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(Space.lg))
            StatRail(stats)
        }

        if (tags != null) {
            Spacer(Modifier.height(Space.md))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (centered) {
                    Arrangement.spacedBy(Space.xs + 2.dp, Alignment.CenterHorizontally)
                } else {
                    Arrangement.spacedBy(Space.xs + 2.dp)
                },
                verticalArrangement = Arrangement.spacedBy(Space.xs + 2.dp),
                content = tags,
            )
        }

        if (footer != null) {
            Spacer(Modifier.height(Space.lg))
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
            .background(Ink.Surface.copy(alpha = 0.6f))
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Hairline()
            Row(Modifier.fillMaxWidth().height(58.dp)) {
                repeat(columns) { column ->
                    val stat = row.getOrNull(column)
                    if (stat == null) {
                        // The empty half of a short last row is held open so the columns
                        // above it stay in line -- but without a rule in front of it, which
                        // would draw a cell around nothing and read as a figure that failed
                        // to load.
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
        modifier.fillMaxHeight().padding(horizontal = Space.xs + 2.dp),
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
        Spacer(Modifier.height(Space.xs))
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
 * one flat colour at low opacity. Every screen sets [accent] to whatever it is about -- a
 * binder's spine, gold for the card list, green for trade -- which makes the colour a piece
 * of information rather than decoration.
 */
@Composable
fun ScreenBackdrop(
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 280.dp,
) {
    Box(modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().background(headerGlow(accent)))
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-70).dp, y = (-110).dp)
                .size(300.dp)
                .background(headerBloom(accent, strength = 0.24f), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-60).dp)
                .size(260.dp)
                .background(headerBloom(Ink.Aura, strength = 0.16f), CircleShape),
        )
    }
}

// -------------------------------------------------------------------- pieces

/**
 * Unrealised gain as one chip, next to the number it is a gain on.
 *
 * Percentage and amount together: the percentage alone hides whether +40% is forty dollars
 * or four thousand, and the amount alone hides whether it is a good year.
 */
@Composable
fun GainChip(summary: ValueSummary, modifier: Modifier = Modifier) {
    val percentLabel = summary.gainPercentLabel ?: return
    val up = summary.unrealizedGain.cents >= 0
    val color = if (up) Ink.Gain else Ink.Loss

    Row(
        modifier
            .clip(AppShape.Chip)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.28f), AppShape.Chip)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (up) app.pocketful.ui.theme.AppIcons.ArrowUp
            else app.pocketful.ui.theme.AppIcons.ArrowDown,
            contentDescription = null,
            modifier = Modifier.size(Size.iconXs - 1.dp),
            tint = color,
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            text = "$percentLabel · ${summary.unrealizedGain.format(compact = true)}",
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/**
 * An action in a header rail.
 *
 * Deliberately a touch smaller than a standalone control. Header actions are secondary to
 * the content below them -- three of them at full size were as tall as the headline figure
 * they sat next to, which made every screen open on a row of buttons.
 */
@Composable
fun HeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink.TextSecondary,
    badge: Boolean = false,
) {
    Box(modifier.size(Size.control), contentAlignment = Alignment.Center) {
        CircleIconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            size = Size.control,
            tint = tint,
        )
        // A dot rather than a count. This marks "there is something here worth your
        // attention" -- an update waiting, cards unfiled -- and a number would invite
        // someone to work out what it is counting instead of just tapping.
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Ink.Gold)
                    .border(2.dp, Ink.Canvas, CircleShape),
            )
        }
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
            .height(Size.tap)
            .clip(AppShape.Pill)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineSoft, AppShape.Pill)
            .tappable(pressScale = 0.96f, onClick = onClick)
            .padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(Size.iconSm), tint = tint)
        Spacer(Modifier.width(Space.sm))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
