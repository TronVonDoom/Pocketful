package app.pocketful.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Motion
import app.pocketful.ui.theme.Radius
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space

/**
 * A tap target that acknowledges the press by shrinking slightly.
 *
 * Compose's default ripple reads as an Android system control; on a screen that is mostly
 * card art, a scale response is quieter and makes every surface in the app feel like the
 * same material.
 *
 * [onLongClick] is what starts a multi-select anywhere in the app. It lives here rather
 * than being bolted onto each list so that the press feedback is the same gesture object
 * -- a row that shrinks under a tap and a row that shrinks under a hold are the same
 * control, and holding one thing that selects while holding its neighbour does nothing is
 * the bug this avoids.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tappable(
    enabled: Boolean = true,
    pressScale: Float = 0.97f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressScale",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

// -------------------------------------------------------------------- buttons

/**
 * What a button means, which decides what it looks like.
 *
 * Three, and the app should never need a fourth. [Primary] is the one thing this screen is
 * for and there is at most one of them in view; [Neutral] is everything else you might do;
 * [Danger] destroys something. A tone is not a colour preference -- it is a claim about
 * consequence, which is why they are named for the consequence.
 */
enum class ButtonTone { Primary, Neutral, Danger;

    companion object {
        /** The old name for [Primary], kept so existing sheets still read correctly. */
        val Accent: ButtonTone get() = Primary
    }
}

@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    // The primary button is the only filled thing on most screens, so it carries a gradient
    // rather than a flat fill -- one step of it, barely visible, which is the difference
    // between a button that looks pressable and a coloured rectangle with a word on it.
    val ground: Brush = when {
        !enabled -> SolidColor(Ink.SurfaceRaised)
        tone == ButtonTone.Primary -> Brush.verticalGradient(listOf(Ink.AccentBright, Ink.Accent))
        tone == ButtonTone.Danger -> SolidColor(Ink.Loss.copy(alpha = 0.15f))
        else -> SolidColor(Ink.SurfaceHigh)
    }
    val foreground = when {
        !enabled -> Ink.TextDisabled
        tone == ButtonTone.Primary -> Color.White
        tone == ButtonTone.Danger -> Ink.Loss
        else -> Ink.TextPrimary
    }

    Row(
        modifier
            .heightIn(min = Size.field)
            .clip(AppShape.Small)
            .background(ground)
            .then(
                if (tone == ButtonTone.Danger && enabled) {
                    Modifier.border(1.dp, Ink.Loss.copy(alpha = 0.3f), AppShape.Small)
                } else {
                    Modifier
                },
            )
            .tappable(enabled = enabled, onClick = onClick)
            // Deliberately tighter than it looks like it wants to be. A button that fills
            // its row does not notice the difference -- its content is centred either way
            // -- but two of these sharing a row on a small phone are about 110dp wide
            // each, and the dp this gives back is the difference between a label and an
            // ellipsis.
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Size.iconSm), tint = foreground)
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A bordered, transparent button for the secondary action in a pair.
 *
 * [maxLines] is what lets two of these share a row. At half width a sentence-length label
 * ellipsises to nothing useful, and the alternative -- cutting the words down until they
 * fit on one line -- costs the button the thing it was saying.
 */
@Composable
fun AppOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    maxLines: Int = 2,
) {
    val foreground = if (enabled) Ink.TextSecondary else Ink.TextDisabled

    Row(
        modifier
            .heightIn(min = Size.field)
            .clip(AppShape.Small)
            .border(1.dp, if (enabled) Ink.Outline else Ink.OutlineFaint, AppShape.Small)
            .tappable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Size.iconSm), tint = foreground)
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Size.control,
    tint: Color = Ink.TextSecondary,
    background: Color = Ink.SurfaceRaised,
    border: Color = Ink.OutlineSoft,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .alpha(if (enabled) 1f else 0.4f)
            .tappable(enabled = enabled, pressScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(size * 0.45f), tint = tint)
    }
}

// ---------------------------------------------------------------------- chips

@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.TextSecondary,
    background: Color = Ink.SurfaceHigh,
) {
    Box(
        modifier
            .clip(AppShape.Chip)
            .background(background)
            .padding(horizontal = Space.xs + 1.dp, vertical = Space.xxs),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** A chip that can be picked. Used for layouts, conditions, finishes and sort orders. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Ink.Accent,
    icon: ImageVector? = null,
    count: Int? = null,
) {
    val background by animateColorAsState(
        if (selected) accent.copy(alpha = 0.18f) else Ink.SurfaceRaised,
        Motion.fast(),
        label = "chipBackground",
    )
    val outline by animateColorAsState(
        if (selected) accent.copy(alpha = 0.55f) else Ink.OutlineSoft,
        Motion.fast(),
        label = "chipOutline",
    )
    val foreground = if (selected) accent else Ink.TextSecondary

    Row(
        modifier
            .heightIn(min = Size.controlSm + Space.xs)
            .clip(AppShape.Pill)
            .background(background)
            .border(1.dp, outline, AppShape.Pill)
            .tappable(onClick = onClick)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Size.iconXs + 2.dp), tint = foreground)
            Spacer(Modifier.width(Space.xs + 2.dp))
        }
        Text(
            text = label,
            color = if (selected) Ink.TextPrimary else Ink.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        // The tally rides inside the chip rather than being appended to its label with a
        // separator. "Owned · 312" reads as one long name; a distinct numeral reads as a
        // count, which is what it is, and it can go grey when the chip is not picked.
        if (count != null) {
            Spacer(Modifier.width(Space.xs + 2.dp))
            Text(
                text = "$count",
                color = if (selected) accent else Ink.TextTertiary,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

/**
 * A run of mutually exclusive options that share one track.
 *
 * The selection is a tinted, outlined pill with the label in the accent's own colour, not
 * one grey on another. On a control like "I own it / I want it", where picking the wrong
 * side quietly files a card you are holding as one you are hunting, contrast is not a thing
 * to economise on -- and [accent] lets a caller give each option the colour it already
 * means elsewhere, so wanted stays violet when it becomes a segment.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    accent: (T) -> Color = { Ink.Accent },
) {
    Row(
        modifier
            .clip(AppShape.Small)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineFaint, AppShape.Small)
            .padding(Space.xs - 1.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val tint = accent(option)
            val background by animateColorAsState(
                if (isSelected) tint.copy(alpha = 0.20f) else Color.Transparent,
                Motion.fast(),
                label = "segmentBackground",
            )
            val outline by animateColorAsState(
                if (isSelected) tint.copy(alpha = 0.65f) else Color.Transparent,
                Motion.fast(),
                label = "segmentOutline",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(Size.controlSm + Space.xs)
                    .clip(AppShape.Chip)
                    .background(background)
                    .border(1.dp, outline, AppShape.Chip)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) tint else Ink.TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else null,
                    maxLines = 1,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ text input

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    prefix: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    singleLine: Boolean = true,
) {
    Column(modifier) {
        if (label != null) {
            FieldLabel(label)
            Spacer(Modifier.height(Space.sm))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.TextPrimary),
            cursorBrush = SolidColor(Ink.Accent),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Size.field)
                        .clip(AppShape.Small)
                        .background(Ink.SurfaceRaised)
                        .border(1.dp, Ink.OutlineSoft, AppShape.Small)
                        .padding(horizontal = Space.md + 2.dp, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, null, Modifier.size(Size.iconSm), tint = Ink.TextTertiary)
                        Spacer(Modifier.width(Space.sm + 2.dp))
                    }
                    if (prefix != null) {
                        Text(prefix, color = Ink.TextTertiary, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(Space.xs))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = Ink.TextDisabled,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

/** Search is frequent enough to deserve its own shape rather than a text field with a hint. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.TextPrimary),
        cursorBrush = SolidColor(Ink.Accent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Size.tap)
                    .clip(AppShape.Pill)
                    .background(Ink.SurfaceRaised)
                    .border(1.dp, Ink.OutlineSoft, AppShape.Pill)
                    .padding(horizontal = Space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Search, null, Modifier.size(Size.iconSm), tint = Ink.TextTertiary)
                Spacer(Modifier.width(Space.md))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Ink.TextDisabled,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Spacer(Modifier.width(Space.sm))
                    Box(
                        Modifier
                            .size(Size.iconLg - 4.dp)
                            .clip(CircleShape)
                            .background(Ink.SurfaceHigh)
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Close, "Clear search", Modifier.size(Size.iconXs), tint = Ink.TextSecondary)
                    }
                }
            }
        },
    )
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Ink.TextTertiary,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}

/** Plus/minus around a number. Cheaper to hit than a text field for a value under 30. */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..60,
    suffix: String = "",
) {
    Row(
        modifier
            .height(Size.field)
            .clip(AppShape.Small)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineSoft, AppShape.Small)
            .padding(horizontal = Space.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(AppIcons.Minus, "Decrease", value > range.first) {
            onValueChange((value - 1).coerceIn(range))
        }
        Text(
            text = if (suffix.isEmpty()) "$value" else "$value $suffix",
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        StepperButton(AppIcons.Plus, "Increase", value < range.last) {
            onValueChange((value + 1).coerceIn(range))
        }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Size.controlSm + Space.xs)
            .clip(AppShape.Chip)
            .background(Ink.SurfaceHigh)
            .alpha(if (enabled) 1f else 0.35f)
            .tappable(enabled = enabled, pressScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(Size.iconSm), tint = Ink.TextPrimary)
    }
}

@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track by animateColorAsState(
        if (checked) Ink.Accent else Ink.SurfaceHigh,
        Motion.fast(),
        label = "switchTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = Motion.springy(),
        label = "switchKnob",
    )

    Box(
        modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(AppShape.Pill)
            .background(track)
            .border(1.dp, if (checked) Color.Transparent else Ink.OutlineSoft, AppShape.Pill)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .align(Alignment.CenterStart)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else Ink.TextTertiary),
        )
    }
}

// ------------------------------------------------------------------- structure

/**
 * The label that opens a section.
 *
 * Tracked, uppercase and tertiary -- deliberately quiet. A section heading's job is to be
 * findable while scanning and invisible while reading, and a heading set at the same weight
 * as the content under it turns a scroll into a list of announcements.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Size.controlSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Size.iconSm - 2.dp), tint = Ink.TextTertiary)
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            text = title.uppercase(),
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke(this)
    }
}

/** The "see the rest of these" affordance on a section header. */
@Composable
fun LinkAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(AppShape.Pill)
            .tappable(pressScale = 0.94f, onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Ink.Accent,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Spacer(Modifier.width(Space.xxs))
        Icon(AppIcons.ChevronRight, null, Modifier.size(Size.iconXs), tint = Ink.Accent)
    }
}

/** The standard container: a raised, hairline-bordered panel. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(AppShape.Card)
            .background(Ink.Surface)
            .border(1.dp, Ink.OutlineFaint, AppShape.Card)
            .padding(padding),
        content = content,
    )
}

/**
 * A panel that is also a button.
 *
 * Distinct from [Panel] because the difference matters: a panel you can press must say so
 * before it is pressed, and the way it says so is the chevron and the press response, not a
 * colour nobody will connect to interactivity.
 */
@Composable
fun ActionPanel(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(AppShape.Card)
            .background(Ink.Surface)
            .border(1.dp, Ink.OutlineFaint, AppShape.Card)
            .tappable(pressScale = 0.985f, onClick = onClick)
            .padding(padding),
        content = content,
    )
}

@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Ink.Accent,
    height: Dp = 6.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShape.Pill)
            .background(Ink.Well),
    ) {
        val safe = fraction.coerceIn(0f, 1f)
        if (safe > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(safe)
                    .fillMaxHeight()
                    .clip(AppShape.Pill)
                    .background(color),
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Ink.SurfaceHigh, Ink.SurfaceRaised),
                    ),
                )
                .border(1.dp, Ink.OutlineSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(Size.iconLg), tint = Ink.TextTertiary)
        }
        Spacer(Modifier.height(Space.lg))
        Text(
            text = title,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = message,
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Space.xl))
            action()
        }
    }
}

/**
 * The colour a money string should be set in.
 *
 * Every figure in this app is gold, green or red -- except when there is no figure. A card
 * the catalog has never quoted renders as an em dash, and an em dash set in the same weight
 * and hue as a real price is a short bright bar sitting exactly where the number belongs,
 * which reads as a value that failed to load rather than as one that does not exist.
 *
 * Routed through here rather than tested at each call site, because "is this a price or the
 * absence of one" is one question and it was being answered in about a dozen places, most of
 * which were not answering it at all.
 */
@Composable
fun moneyInk(text: String, accent: Color = Ink.Gold): Color =
    if (text == app.pocketful.state.NO_PRICE) Ink.TextDisabled else accent

/** A hairline rule for separating rows inside a panel. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ink.OutlineFaint))
}

/**
 * A line of explanation. Not an error unless it says so.
 *
 * Bordered and inset rather than loose text, because an unframed paragraph between two
 * lists reads as content that failed to render into whatever the lists are made of.
 */
@Composable
fun Note(
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    icon: ImageVector? = AppIcons.Info,
) {
    val tint = if (error) Ink.Loss else Ink.TextTertiary
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(if (error) Ink.Loss.copy(alpha = 0.07f) else Ink.Surface.copy(alpha = 0.6f))
            .border(1.dp, if (error) Ink.Loss.copy(alpha = 0.25f) else Ink.OutlineFaint, AppShape.Medium)
            .padding(Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Size.iconSm).padding(top = 1.dp), tint = tint)
            Spacer(Modifier.width(Space.sm + 2.dp))
        }
        Text(text = text, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * A number and what it counts, side by side in a bordered cell.
 *
 * The unit is always present and always below the figure. A bare number in an interface is
 * a riddle, and the two lines cost less height than the caption someone writes underneath
 * to explain the row.
 */
@Composable
fun MetricCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = Ink.TextPrimary,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(AppShape.Medium)
            .background(Ink.Surface)
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium)
            .then(if (onClick != null) Modifier.tappable(pressScale = 0.97f, onClick = onClick) else Modifier)
            .padding(horizontal = Space.md, vertical = Space.md),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Size.iconSm - 2.dp), tint = accent.copy(alpha = 0.8f))
            Spacer(Modifier.height(Space.sm))
        }
        Text(
            text = value,
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = label.uppercase(),
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The bar of tabs that switches what a screen is showing.
 *
 * Distinct from [SegmentedControl], which picks a *setting*. This picks a *view*: the
 * screen stays the same screen and shows you a different slice of it, so the selected tab
 * is marked with a rule beneath it -- the same signal a browser tab uses -- rather than
 * with a filled pill that would read as a chosen value.
 */
@Composable
fun <T> ViewTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    count: (T) -> Int? = { null },
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val tint by animateColorAsState(
                if (isSelected) Ink.TextPrimary else Ink.TextTertiary,
                Motion.fast(),
                label = "tabTint",
            )
            val rule by animateColorAsState(
                if (isSelected) Ink.Accent else Color.Transparent,
                Motion.fast(),
                label = "tabRule",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(AppShape.Chip)
                    .clickable { onSelect(option) }
                    .padding(vertical = Space.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label(option),
                        color = tint,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    count(option)?.takeIf { it > 0 }?.let {
                        Spacer(Modifier.width(Space.xs + 1.dp))
                        Text(
                            text = "$it",
                            color = if (isSelected) Ink.Accent else Ink.TextDisabled,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(AppShape.Pill)
                        .background(rule),
                )
            }
        }
    }
}

/**
 * A row that opens something else. The workhorse of a settings screen.
 *
 * Always carries its chevron, because the alternative -- some rows navigate and some do not
 * and you find out by tapping -- is the single most common reason a settings screen feels
 * untrustworthy.
 */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = Ink.TextSecondary,
    trailing: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Size.tap + Space.sm)
            .clip(AppShape.Medium)
            .tappable(pressScale = 0.985f, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(Size.control - 4.dp)
                    .clip(AppShape.Small)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(Size.iconSm), tint = tint)
            }
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.xxs))
                Text(
                    text = subtitle,
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            Text(
                text = trailing,
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space.sm))
        Icon(AppIcons.ChevronRight, null, Modifier.size(Size.iconSm), tint = Ink.TextDisabled)
    }
}
