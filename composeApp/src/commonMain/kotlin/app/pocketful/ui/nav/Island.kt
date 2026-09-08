package app.pocketful.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/**
 * Top-level destinations, in the order a thumb sweeps them.
 *
 * Stats is not one of them any more. It and Home were two readings of the same numbers --
 * both opened on the collection total, both ranked the same cards -- so the tab bar was
 * asking people to choose between a summary and a slightly longer summary. They are one
 * scroll now, and the slot that freed up went to Search, which is the thing the app could
 * not do at all: look outward at what exists rather than inward at what you have.
 */
enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", AppIcons.Home),
    Collections("Collections", AppIcons.Collections),
    Search("Search", AppIcons.Search),
    Settings("Settings", AppIcons.Settings),
}

/** The island's own height: 40dp of item inside 5dp of padding inside 10dp of margin. */
private val IslandHeight: Dp = 40.dp + 5.dp * 2 + 10.dp * 2

/**
 * How much room a scrolling screen must leave at the bottom so the island never covers
 * its last row.
 *
 * Screens add this to their content padding rather than being inset by a Scaffold,
 * because the binder page deliberately draws its page surface *under* the island. The
 * gesture bar has to be counted here as well as inside the island: the island is pushed
 * up by `navigationBarsPadding`, so the space a screen must reserve grows by the same
 * amount, and hard-coding a single dp value left the last row half-covered on any device
 * with gesture navigation.
 */
@Composable
fun islandBottomInset(): Dp =
    IslandHeight + 10.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * The shared floating shell: a shadowed, bordered pill that hovers over the content.
 *
 * Both bottom islands in the app are this container with different children, which is
 * what keeps the page-turn control on the binder screen feeling like the same object as
 * the navigation bar it replaces.
 */
@Composable
fun IslandSurface(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .shadow(elevation = 18.dp, shape = AppShape.Pill, clip = false)
                .clip(AppShape.Pill)
                .background(Ink.Glass)
                .border(1.dp, Ink.Outline.copy(alpha = 0.8f), AppShape.Pill)
                .padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

/**
 * The navigation island.
 *
 * Four destinations, not five. The card list came out of the bar because it is not a
 * place you go -- it is a way of looking at what Collections already holds, so it is
 * reached from there and from Home instead. What is left are four things you genuinely
 * switch between, at a size a thumb can hit without aiming.
 *
 * Every tab carries its own label under its icon rather than the selected one growing to
 * reveal it: a permanent label means the bar can be read without first tapping through
 * it, which is also what lets the whole thing be shorter than a single-row version.
 */
@Composable
fun IslandNavBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    IslandSurface(modifier) {
        Destination.entries.forEach { destination ->
            NavIslandItem(
                destination = destination,
                selected = destination == current,
                onClick = { onSelect(destination) },
            )
        }
    }
}

@Composable
private fun RowScope.NavIslandItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) Ink.Accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "islandBackground",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Ink.Accent else Ink.TextTertiary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "islandTint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) Ink.TextPrimary else Ink.TextTertiary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "islandLabel",
    )

    Column(
        Modifier
            .weight(1f)
            .height(40.dp)
            .clip(AppShape.Pill)
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(destination.icon, destination.label, Modifier.size(17.dp), tint = tint)
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.label,
            color = labelColor,
            // A step below labelSmall, with the tracking taken back out. "Collections"
            // is still the label that decides the size, even with four tabs rather
            // than five.
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.sp),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
