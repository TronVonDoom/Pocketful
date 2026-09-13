package app.pocketful.data

import androidx.compose.runtime.Composable

/**
 * What the home-screen widget shows, already worded.
 *
 * Formatted here in the app rather than by the widget, because the widget runs in the
 * launcher's process with none of the app's money formatting, and because a widget that
 * computed its own total could disagree with the one on Home.
 */
data class WidgetSummary(
    val value: String,
    /** "▲ $12.40 (1.2%) today", or null when there is no earlier day to compare with. */
    val change: String?,
    val changeUp: Boolean?,
    /** "214 cards". */
    val caption: String,
    /** The biggest mover in the collection today, worded: "Tyrunt ▲12%". */
    val mover: String?,
)

/** Where the app hands the widget its figures. A no-op on platforms without widgets. */
interface HomeWidget {
    fun publish(summary: WidgetSummary)
}

@Composable
expect fun rememberHomeWidget(): HomeWidget
