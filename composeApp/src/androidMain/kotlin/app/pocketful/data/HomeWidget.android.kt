package app.pocketful.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.pocketful.widget.PortfolioWidget

@Composable
actual fun rememberHomeWidget(): HomeWidget {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        object : HomeWidget {
            override fun publish(summary: WidgetSummary) = PortfolioWidget.publish(context, summary)
        }
    }
}
