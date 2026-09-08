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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

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

enum class ButtonTone { Accent, Danger }

@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Accent,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val background = when (tone) {
        ButtonTone.Accent -> Ink.Accent
        ButtonTone.Danger -> Ink.Loss.copy(alpha = 0.16f)
    }
    val foreground = when (tone) {
        ButtonTone.Accent -> Color.White
        ButtonTone.Danger -> Ink.Loss
    }

    Row(
        modifier
            .height(48.dp)
            .clip(AppShape.Small)
            .background(if (enabled) background else Ink.SurfaceRaised)
            .tappable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (enabled) foreground else Ink.TextDisabled)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = if (enabled) foreground else Ink.TextDisabled,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** A bordered, transparent button for the secondary action in a pair. */
@Composable
fun AppOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(AppShape.Small)
            .border(1.dp, Ink.Outline, AppShape.Small)
            .tappable(onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(17.dp), tint = Ink.TextSecondary)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    tint: Color = Ink.TextSecondary,
    background: Color = Ink.SurfaceRaised,
    border: Color = Ink.OutlineSoft,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .tappable(pressScale = 0.90f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(size * 0.46f), tint = tint)
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
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
) {
    val background by animateColorAsState(
        if (selected) accent.copy(alpha = 0.18f) else Ink.SurfaceRaised,
        label = "chipBackground",
    )
    val outline by animateColorAsState(
        if (selected) accent.copy(alpha = 0.55f) else Ink.OutlineSoft,
        label = "chipOutline",
    )

    Box(
        modifier
            .clip(AppShape.Pill)
            .background(background)
            .border(1.dp, outline, AppShape.Pill)
            .tappable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Ink.TextPrimary else Ink.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/** A run of mutually exclusive options that share one track. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(AppShape.Small)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineFaint, AppShape.Small)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Ink.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) Ink.TextPrimary else Ink.TextTertiary,
                    style = MaterialTheme.typography.labelMedium,
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
            Spacer(Modifier.height(6.dp))
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
                        .heightIn(min = 48.dp)
                        .clip(AppShape.Small)
                        .background(Ink.SurfaceRaised)
                        .border(1.dp, Ink.OutlineSoft, AppShape.Small)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(leadingIcon, null, Modifier.size(17.dp), tint = Ink.TextTertiary)
                        Spacer(Modifier.width(10.dp))
                    }
                    if (prefix != null) {
                        Text(prefix, color = Ink.TextTertiary, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(4.dp))
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
                    .height(46.dp)
                    .clip(AppShape.Pill)
                    .background(Ink.SurfaceRaised)
                    .border(1.dp, Ink.OutlineSoft, AppShape.Pill)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Search, null, Modifier.size(17.dp), tint = Ink.TextTertiary)
                Spacer(Modifier.width(10.dp))
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
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Ink.SurfaceHigh)
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Close, "Clear search", Modifier.size(12.dp), tint = Ink.TextSecondary)
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
            .height(48.dp)
            .clip(AppShape.Small)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineSoft, AppShape.Small)
            .padding(horizontal = 6.dp),
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
            .size(36.dp)
            .clip(AppShape.Chip)
            .background(Ink.SurfaceHigh)
            .alpha(if (enabled) 1f else 0.35f)
            .tappable(enabled = enabled, pressScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(15.dp), tint = Ink.TextPrimary)
    }
}

@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track by animateColorAsState(
        if (checked) Ink.Accent.copy(alpha = 0.85f) else Ink.SurfaceHigh,
        label = "switchTrack",
    )
    val knobOffset by animateDpAsState(if (checked) 20.dp else 2.dp, label = "switchKnob")

    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(AppShape.Pill)
            .background(track)
            .border(1.dp, if (checked) Color.Transparent else Ink.OutlineSoft, AppShape.Pill)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .align(Alignment.CenterStart)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else Ink.TextTertiary),
        )
    }
}

// ------------------------------------------------------------------- structure

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** The standard container: a raised, hairline-bordered panel. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
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

@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Ink.Accent,
    height: Dp = 5.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShape.Pill)
            .background(Ink.OutlineSoft),
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
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Ink.SurfaceRaised)
                .border(1.dp, Ink.OutlineSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = Ink.TextTertiary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

/** A hairline rule for separating rows inside a panel. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ink.OutlineFaint))
}
