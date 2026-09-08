package app.pocketful.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.SpineSwatch
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A modal bottom sheet.
 *
 * Written here rather than using `ModalBottomSheet` so the scrim, the corner radius and
 * the drag behaviour match the rest of the app exactly, and so the sheet composes inside
 * the same root Box as the island bar -- which is what lets the bar stay put while a
 * sheet is open.
 *
 * Mount this once, near the root, and drive it with [visible]. When closed it contributes
 * no pointer-input nodes, so it cannot swallow taps meant for the screen behind it.
 */
@Composable
fun AppSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val dismissThreshold = with(density) { 110.dp.toPx() }

    // Reset the drag only once the exit animation has carried the sheet off screen,
    // otherwise a flick-to-dismiss snaps back up before it slides away.
    LaunchedEffect(visible) {
        if (!visible) {
            delay(260)
            dragY.snapTo(0f)
        }
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink.Scrim.copy(alpha = 0.62f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(240)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BoxWithConstraints {
                val maxSheetHeight = maxHeight * 0.92f
                Column(
                    Modifier
                        .offset { IntOffset(0, dragY.value.roundToInt()) }
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight)
                        .clip(AppShape.Sheet)
                        .background(Ink.Glass)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (dragY.value > dismissThreshold) onDismiss()
                                        else dragY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                    }
                                },
                                onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                            ) { _, delta ->
                                scope.launch { dragY.snapTo((dragY.value + delta).coerceAtLeast(0f)) }
                            }
                        }
                        .imePadding(),
                ) {
                    GrabHandle()
                    content()
                    Spacer(Modifier.height(18.dp).navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun GrabHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Ink.Outline),
        )
    }
}

/** Title row for a sheet, with the close affordance in the place everyone reaches for. */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Ink.TextTertiary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onClose != null) {
            Spacer(Modifier.width(12.dp))
            CircleIconButton(
                icon = AppIcons.Close,
                contentDescription = "Close",
                onClick = onClose,
                size = 34.dp,
                background = Ink.SurfaceRaised,
            )
        }
    }
}

/**
 * The body of a sheet: scrolls when it needs to, padded consistently when it does not.
 *
 * Weighted with `fill = false` rather than left to wrap. An unweighted scrolling child is
 * measured before its siblings and will happily take every remaining pixel, which pushed
 * [SheetActions] off the bottom of any sheet whose body was long -- or, worse, whenever
 * the keyboard opened and halved the available height. Weighting it means the buttons are
 * measured first and the body gets what is left.
 */
@Composable
fun ColumnScope.SheetBody(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .then(scrollModifier)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Buttons pinned under a sheet body, on their own ground so a long body scrolls behind. */
@Composable
fun SheetActions(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Spine colour picker. The ring lives on an outer box rather than as a border on the
 * swatch itself, so selecting a colour does not visibly shrink it.
 */
@Composable
fun ColorSwatchRow(
    swatches: List<SpineSwatch>,
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        swatches.forEach { swatch ->
            val isSelected = swatch.value == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) Color(swatch.value) else Color.Transparent,
                        shape = CircleShape,
                    )
                    .tappable(pressScale = 0.88f) { onSelect(swatch.value) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isSelected) 28.dp else 32.dp)
                        .clip(CircleShape)
                        .background(Color(swatch.value)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(AppIcons.Check, swatch.label, Modifier.size(15.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}
