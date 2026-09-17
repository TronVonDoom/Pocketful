package app.pocketful.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketful.ui.components.tappable
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Lift
import app.pocketful.ui.theme.Motion
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space

/**
 * Where the app can be.
 *
 * Four, and not the four it used to be. The bar previously spent a quarter of itself on
 * Settings -- the one screen in the app a person visits twice and then not again for a
 * month -- while Trade and the flat card list, both of which are real destinations people
 * go to on purpose, were reachable only as unlabelled icons in the corner of a header.
 * That is backwards, and no amount of styling fixes it.
 *
 * So Settings moved to a gear on Home, where a rarely-wanted, easily-found thing belongs;
 * Trade took the seat it vacated; and the card list folded into Collection, which is the
 * screen that owns cards in the first place.
 *
 * The order is deliberate: inward first (what you have), outward last (what exists), with
 * the thing you do most sitting between them in the middle of the bar.
 */
enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", AppIcons.Home),
    Collection("Collection", AppIcons.Collections),
    Trade("Trade", AppIcons.Trade),
    Browse("Browse", AppIcons.Search),
}

/**
 * The two destinations that sit to the left of the add button, and the two to its right.
 *
 * Split here rather than at the call site so the dock cannot be assembled with three on one
 * side by accident -- the add button is only centred if the halves are equal.
 */
private val LeftOfAdd = listOf(Destination.Home, Destination.Collection)
private val RightOfAdd = listOf(Destination.Trade, Destination.Browse)

/** The dock's own height: an item, inside its padding, inside its margin. */
private val DockHeight: Dp = Size.navItem + Space.xs * 2 + Space.md * 2

/**
 * How much room a scrolling screen must leave at the bottom so the dock never covers its
 * last row.
 *
 * Screens add this to their content padding rather than being inset by a Scaffold, because
 * the binder page deliberately draws its page surface *under* the dock. The gesture bar has
 * to be counted here as well as inside the dock: the dock is pushed up by
 * `navigationBarsPadding`, so the space a screen must reserve grows by the same amount, and
 * a single hard-coded value left the last row half-covered on any device with gesture
 * navigation.
 */
@Composable
fun islandBottomInset(): Dp =
    DockHeight + Space.sm + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * The shared floating shell: a shadowed, bordered surface hovering over the content.
 *
 * Every bar that floats at the bottom of this app is this container with different children
 * -- the navigation dock, the page-turn control on a binder, the bar a multi-select puts up
 * -- which is what makes them read as one object changing its mind rather than as three
 * unrelated widgets taking turns.
 */
@Composable
fun IslandSurface(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Space.lg,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = horizontalPadding, vertical = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .shadow(elevation = Lift.floating, shape = AppShape.Large, clip = false)
                .clip(AppShape.Large)
                .background(Ink.Glass)
                .border(1.dp, Ink.Outline.copy(alpha = 0.7f), AppShape.Large)
                .padding(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
            content = content,
        )
    }
}

/**
 * The navigation dock.
 *
 * Four destinations around one raised button, which is the whole argument for this shape.
 * Recording a card is what people open this app to do, and until now it had no button
 * anywhere -- you reached it by remembering that a tile in the search tab was tappable, or
 * that an empty pocket opened a sheet. A primary action nobody can point at is not a
 * primary action.
 *
 * Putting it in the middle of the bar costs a destination slot and buys three things: it is
 * the easiest point on the screen for either thumb, it is the same distance from every tab
 * so adding never means navigating first, and its size says outright which of the five
 * things down here is the important one.
 *
 * Labels are permanent rather than appearing under the current tab. A bar that has to be
 * tapped before it can be read is a bar that gets learned by trial, and the height saved by
 * hiding them is height the raised button needed anyway.
 */
@Composable
fun IslandNavBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.lg, vertical = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .shadow(elevation = Lift.floating, shape = AppShape.Large, clip = false)
                .clip(AppShape.Large)
                .background(Ink.Glass)
                .border(1.dp, Ink.Outline.copy(alpha = 0.7f), AppShape.Large)
                .padding(horizontal = Space.xs, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeftOfAdd.forEach { destination ->
                NavItem(destination, destination == current) { onSelect(destination) }
            }

            AddButton(onAdd)

            RightOfAdd.forEach { destination ->
                NavItem(destination, destination == current) { onSelect(destination) }
            }
        }
    }
}

/**
 * One destination.
 *
 * The selected state is a tinted pill behind the icon rather than a colour change alone.
 * Tint on its own is the weakest signal an interface has -- it fails in sunlight, it fails
 * for the eight percent of men who cannot separate these two hues, and it fails on a
 * screenshot someone is squinting at. The pill is legible before the colour is read.
 */
@Composable
private fun RowScope.NavItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) Ink.Accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = Motion.base(),
        label = "navBackground",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) Ink.AccentBright else Ink.TextTertiary,
        animationSpec = Motion.base(),
        label = "navTint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) Ink.TextPrimary else Ink.TextTertiary,
        animationSpec = Motion.base(),
        label = "navLabel",
    )

    Column(
        Modifier
            .weight(1f)
            .height(Size.navItem)
            .clip(AppShape.Medium)
            .background(background)
            .tappable(pressScale = 0.92f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(destination.icon, null, Modifier.size(Size.icon), tint = tint)
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = destination.label,
            color = labelColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Record a card. The one thing this app is for, finally given a button.
 *
 * Drawn as a filled disc that breaks the dock's own height, because the point of a primary
 * action is that it is not one of the options -- it is the thing the options are arranged
 * around. It carries no label: a plus in the middle of a navigation bar has meant "make a
 * new one" on every phone for fifteen years, and a word under it would only push the
 * destinations either side out of alignment with each other.
 */
@Composable
private fun RowScope.AddButton(onAdd: () -> Unit) {
    val scale by animateFloatAsState(1f, Motion.springy(), label = "addScale")

    Box(
        Modifier.width(Size.navAdd + Space.sm).height(Size.navItem),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Size.navAdd)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(Lift.raised, CircleShape, clip = false)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(Ink.AccentBright, Ink.Accent)),
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .tappable(pressScale = 0.88f, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Plus, "Add cards", Modifier.size(Size.iconLg), tint = Color.White)
        }
    }
}
