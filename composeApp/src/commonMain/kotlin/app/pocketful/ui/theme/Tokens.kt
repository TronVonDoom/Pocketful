package app.pocketful.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The measurements the interface is built from.
 *
 * Every dp in this app used to be typed at the place it was needed, which is how one screen
 * ended up with 9dp between its rows, the next with 10dp, and a third with 11dp -- all of
 * them defensible on their own and none of them agreeing. Nobody reads a layout and notices
 * "that gap is two dp bigger than the one above it"; they read the screen as slightly
 * unresolved and cannot say why.
 *
 * So the scale is fixed here and the screens spend it. Four units is the grain, because it
 * is the smallest step a phone can show as a deliberate difference, and every value below
 * is a multiple of it. Nothing outside this file should be inventing a number.
 */
object Space {
    /** Hairline separation -- a label from the figure it belongs to. */
    val xxs: Dp = 2.dp

    /** Within a component: an icon from its label, a badge from its edge. */
    val xs: Dp = 4.dp

    /** Between the parts of one thing: two lines of an identity block. */
    val sm: Dp = 8.dp

    /** Between sibling items: tiles in a grid, rows in a panel. */
    val md: Dp = 12.dp

    /** The screen gutter, and the inside of a panel. The default unless there is a reason. */
    val lg: Dp = 16.dp

    /** Between sections that are about different things. */
    val xl: Dp = 24.dp

    /** Around something that needs to be alone: an empty state, a hero figure. */
    val xxl: Dp = 32.dp

    /** Vertical air at the top and bottom of a screen's content. */
    val huge: Dp = 48.dp
}

/**
 * How round things are, by how big they are.
 *
 * Radius has to track size or it reads as a different material: 20dp on a chip is a
 * lozenge, and 20dp on a full-width panel is barely a curve. Each step here belongs to a
 * size band rather than to a component, so a new component picks its radius by asking how
 * big it is rather than by copying whatever the thing next to it used.
 *
 * Card art is the exception and does not appear here -- a card's corner is cut as a
 * fraction of its own width, because a real one is. See `cardShape`.
 */
object Radius {
    /** Badges, tags, count discs -- anything under about 24dp tall. */
    val xs: Dp = 6.dp

    /** Buttons, fields, segments. */
    val sm: Dp = 12.dp

    /** Tiles and list rows. */
    val md: Dp = 16.dp

    /** Panels and cards -- the standard container. */
    val lg: Dp = 20.dp

    /** Sheets, and the floating dock. */
    val xl: Dp = 28.dp

    /** Fully round. Pills, avatars, circular buttons. */
    val pill: Dp = 999.dp
}

/**
 * Sizes that must agree across the app, mostly because a finger has to hit them.
 *
 * The tap floor is 48dp and is not negotiable -- it is the size of a fingertip, not a style
 * choice. Where a control looks smaller than that, it is drawn smaller and padded back out
 * to the floor, rather than actually being smaller.
 */
object Size {
    /** Inside a chip or over card art. */
    val iconXs: Dp = 12.dp

    /** In a label, a button, a list row. The default icon. */
    val iconSm: Dp = 16.dp

    /** Leading a section, or in the navigation dock. */
    val icon: Dp = 20.dp

    /** The subject of an empty state, or a hero glyph. */
    val iconLg: Dp = 28.dp

    /** The smallest a control may be *drawn*. Always inside a [tap] of padding. */
    val controlSm: Dp = 32.dp

    /** A round icon button, a header action. */
    val control: Dp = 40.dp

    /** The floor for anything a finger has to land on. */
    val tap: Dp = 48.dp

    /** A full-width button, a text field, a stepper. */
    val field: Dp = 52.dp

    /** The height of a tile in a two-up grid. */
    val tile: Dp = 168.dp

    /** The height of a row in a list. */
    val row: Dp = 72.dp

    /** The top bar that every screen wears. */
    val topBar: Dp = 56.dp

    /** One destination in the navigation dock. */
    val navItem: Dp = 52.dp

    /** The raised add button at the centre of the dock. */
    val navAdd: Dp = 56.dp
}

/**
 * How long things take, and the shape of the change.
 *
 * Three durations, because an interface only needs three: one too fast to watch, one you
 * follow, and one that is a journey. Anything that does not fit is a signal that it should
 * be a different kind of transition rather than a fourth number.
 *
 * The easings are asymmetric on purpose -- things enter decelerating, as if arriving from
 * somewhere, and leave accelerating, as if going somewhere. A symmetric curve in both
 * directions is what makes an interface feel mechanical.
 */
object Motion {
    /** A state change you should not have to watch: a colour, a tick, a press. */
    const val FAST: Int = 120

    /** The default. A sheet, a tab, a panel expanding. */
    const val BASE: Int = 220

    /** Something crossing the screen, or a screen replacing another. */
    const val SLOW: Int = 380

    /** Arriving. Fast at first, settling at the end. */
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leaving. Slow to commit, then gone. */
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Both, for something that moves without entering or leaving. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** The spring everything physical uses: a press, a drag settling, a card landing. */
    fun <T> springy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    /** The spring for something that must not overshoot: a bar sliding into place. */
    fun <T> firm(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST, easing = standard)
    fun <T> base(): FiniteAnimationSpec<T> = tween(BASE, easing = standard)
    fun <T> entering(): FiniteAnimationSpec<T> = tween(BASE, easing = enter)
    fun <T> leaving(): FiniteAnimationSpec<T> = tween(FAST, easing = exit)
}

/**
 * How far off the page something sits.
 *
 * Named for what floats rather than for a number, so "the dock and a sheet cast the same
 * shadow" is a fact this file states instead of a coincidence two call sites arrived at.
 */
object Lift {
    /** A tile or panel: no shadow at all, it is on the page. */
    val flat: Dp = 0.dp

    /** A menu or popover, just off the surface below it. */
    val raised: Dp = 8.dp

    /** The navigation dock and the selection bar -- floating over scrolling content. */
    val floating: Dp = 20.dp

    /** A modal sheet, over everything. */
    val modal: Dp = 28.dp
}
