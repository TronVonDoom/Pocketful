package app.pocketful.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.pocketful.MainActivity
import app.pocketful.R
import app.pocketful.data.WidgetSummary

/**
 * The portfolio on the home screen: what the collection is worth, how it moved today, and
 * the card that moved most.
 *
 * A plain RemoteViews widget rather than Glance. It draws four lines of text, and Glance
 * would add a Compose runtime to the launcher process to do it. It never fetches anything:
 * the app writes the figures here whenever its total changes, so the widget always shows
 * exactly what Home last showed, and costs nothing between launches.
 */
class PortfolioWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    companion object {
        private const val PREFS = "portfolio_widget"

        fun publish(context: Context, summary: WidgetSummary) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("value", summary.value)
                .putString("change", summary.change)
                .putString("changeUp", summary.changeUp?.toString())
                .putString("caption", summary.caption)
                .putString("mover", summary.mover)
                .apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PortfolioWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_portfolio)

            views.setTextViewText(R.id.widget_value, prefs.getString("value", null) ?: "—")
            views.setTextViewText(R.id.widget_caption, prefs.getString("caption", null) ?: "Open Pocketful to load your collection")

            val change = prefs.getString("change", null)
            if (change == null) {
                views.setViewVisibility(R.id.widget_change, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_change, View.VISIBLE)
                views.setTextViewText(R.id.widget_change, change)
                val color = when (prefs.getString("changeUp", null)) {
                    "true" -> 0xFF4ADE80.toInt()
                    "false" -> 0xFFF87171.toInt()
                    else -> 0xFF98A2B3.toInt()
                }
                views.setTextColor(R.id.widget_change, color)
            }

            val mover = prefs.getString("mover", null)
            views.setViewVisibility(R.id.widget_mover, if (mover == null) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_mover, mover.orEmpty())

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
