package app.pocketful.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketful.ui.nav.IslandSurface
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/**
 * The bar that appears while a multi-select is running.
 *
 * Deliberately the same floating pill as the navigation island, and it takes that
 * island's place rather than stacking on top of it: while a selection is live, switching
 * tabs is not what the bottom of the screen is for, and two islands fighting for the same
 * corner is how a selection gets abandoned by accident.
 *
 * Actions are icons with permanent labels, at the same size as a navigation destination,
 * so the bar can be read before it is tapped. What appears in it is the caller's business
 * -- a list of cards and a shelf of binders can be acted on in genuinely different ways,
 * and pretending otherwise would mean a "move to container" button that does nothing on
 * half the screens that show it.
 *
 * [confirm] is a panel floated directly above the pill. Destructive actions ask there
 * rather than in a sheet: a scrim over a selection hides the very thing being confirmed,
 * and having to remember what was picked in order to answer "delete these?" is the part
 * people get wrong.
 */
@Composable
fun SelectionIsland(
    count: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    confirm: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (confirm != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(elevation = 18.dp, shape = AppShape.Card, clip = false)
                    .clip(AppShape.Card)
                    .background(Ink.Glass)
                    .border(1.dp, Ink.Outline.copy(alpha = 0.8f), AppShape.Card)
                    .padding(16.dp),
                content = confirm,
            )
        }

        IslandSurface {
            Row(
                Modifier
                    .height(40.dp)
                    .clip(AppShape.Pill)
                    .tappable(pressScale = 0.94f, onClick = onClear)
                    .padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(24.dp).clip(AppShape.Pill).background(Ink.SurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Close, "Clear selection", Modifier.size(12.dp), tint = Ink.TextSecondary)
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "$count",
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            }

            actions()
        }
    }
}

/**
 * One action in a [SelectionIsland], shaped like a navigation destination so the bar that
 * replaced the navigation island is visibly the same object.
 */
@Composable
fun RowScope.SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Ink.TextSecondary,
) {
    Column(
        Modifier
            .weight(1f)
            .height(40.dp)
            .clip(AppShape.Pill)
            .alpha(if (enabled) 1f else 0.4f)
            .tappable(enabled = enabled, pressScale = 0.94f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, Modifier.size(17.dp), tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            // Matched to the navigation island's label, tracking and all: two bars that
            // swap places should not differ in the size of their type.
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The question a destructive selection action asks, with its two answers.
 *
 * [extra] is where a delete puts its follow-up choice -- "and the cards inside?" -- so
 * that the consequence and the switch that changes it are read in one glance rather than
 * one being a setting somewhere else.
 */
@Composable
fun ColumnScope.SelectionConfirm(
    message: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Text(
        text = message,
        color = Ink.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (extra != null) {
        Spacer(Modifier.height(12.dp))
        extra()
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppOutlineButton("Cancel", onCancel, Modifier.weight(1f))
        AppButton(
            label = confirmLabel,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            tone = ButtonTone.Danger,
            icon = AppIcons.Trash,
        )
    }
}

/**
 * A switch with a line of explanation, for use inside a [SelectionConfirm].
 *
 * Same shape as the rows in Settings, because it is the same kind of control and a
 * destructive dialog is the worst possible place to invent a new one.
 */
@Composable
fun ConfirmToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Small)
            .background(Ink.SurfaceRaised)
            .border(1.dp, Ink.OutlineSoft, AppShape.Small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(description, color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        ToggleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
