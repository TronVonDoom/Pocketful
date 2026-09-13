package app.pocketful.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pocketful.data.PricePoint
import app.pocketful.domain.Currency
import app.pocketful.domain.Money
import app.pocketful.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.roundToInt

/** How far back a chart looks. [days] null is everything the history holds. */
enum class ChartRange(val label: String, val days: Int?) {
    MONTH("1M", 31),
    QUARTER("3M", 92),
    HALF("6M", 183),
    YEAR("12M", 366),
    MAX("MAX", null),
}

/**
 * A price over time, with the ranges to look at it through.
 *
 * Drawn here rather than with a chart library: it is one line, a fill, and two reference
 * figures, and a dependency to draw that would outweigh everything else this screen does.
 * The high and low of the visible range are printed against dashed rules, the way a
 * collector reads a chart -- "it has been as high as" -- and the move across the range is
 * written above it, so the shape and the number say the same thing.
 */
@Composable
fun PriceHistoryPanel(
    points: List<PricePoint>?,
    modifier: Modifier = Modifier,
    title: String = "Price history",
    currency: Currency = Currency.USD,
    initialRange: ChartRange = ChartRange.QUARTER,
) {
    var range by remember { mutableStateOf(initialRange) }
    val visible = remember(points, range) { points.orEmpty().within(range) }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(title, Modifier.weight(1f))
            if (visible.size >= 2) {
                val first = Money(visible.first().cents)
                val last = Money(visible.last().cents)
                ChangeLabel(change = last - first, base = first, currency = currency, suffix = " · ${range.label}")
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            points == null -> ChartMessage("Loading price history…")
            visible.size < 2 -> ChartMessage(
                if (points.isEmpty()) "No price history for this card yet."
                else "Not enough history in this range yet.",
            )
            else -> LineChart(visible, currency)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            ChartRange.entries.forEach { option ->
                ChoiceChip(label = option.label, selected = option == range, onClick = { range = option })
            }
        }
    }
}

@Composable
private fun ChartMessage(text: String) {
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LineChart(points: List<PricePoint>, currency: Currency) {
    val high = points.maxOf { it.cents }
    val low = points.minOf { it.cents }
    val rising = points.last().cents >= points.first().cents
    val line = if (rising) Ink.Gain else Ink.Loss

    Column(Modifier.fillMaxWidth()) {
        Text(Money(high).format(currency = currency), color = Ink.TextTertiary, style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(vertical = 6.dp)) {
            val span = (high - low).takeIf { it > 0 } ?: 1L
            val first = epochDay(points.first().date)
            val daySpan = (epochDay(points.last().date) - first).coerceAtLeast(1)
            fun x(p: PricePoint) = (epochDay(p.date) - first).toFloat() / daySpan * size.width
            fun y(p: PricePoint) = size.height - (p.cents - low).toFloat() / span * size.height

            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            drawLine(Ink.OutlineSoft, Offset(0f, 0f), Offset(size.width, 0f), 1.5f, pathEffect = dash)
            drawLine(Ink.OutlineSoft, Offset(0f, size.height), Offset(size.width, size.height), 1.5f, pathEffect = dash)

            val path = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p), y(p)) else lineTo(x(p), y(p)) }
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(x(points.last()), size.height)
                lineTo(x(points.first()), size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(path, line, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(line, radius = 6f, center = Offset(x(points.last()), y(points.last())))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Money(low).format(currency = currency), color = Ink.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(
                "${shortDate(points.first().date)} – ${shortDate(points.last().date)}",
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * A move in price, as a collector reads one: "▲ $0.07 (2.8%)".
 *
 * Green up, red down, and grey for no move at all, which is a fact worth stating rather than
 * a reason to hide the label. The percentage is left off where there is no base to take it of.
 */
@Composable
fun ChangeLabel(
    change: Money,
    base: Money?,
    modifier: Modifier = Modifier,
    currency: Currency = Currency.USD,
    suffix: String = "",
) {
    val color = when {
        change.cents > 0 -> Ink.Gain
        change.cents < 0 -> Ink.Loss
        else -> Ink.TextTertiary
    }
    Text(
        text = changeText(change, base, currency) + suffix,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = modifier,
    )
}

fun changeText(change: Money, base: Money?, currency: Currency = Currency.USD): String {
    val arrow = when {
        change.cents > 0 -> "▲"
        change.cents < 0 -> "▼"
        else -> "–"
    }
    val amount = Money(abs(change.cents)).format(currency = currency)
    val percent = base?.takeIf { !it.isZero }?.let { " (${percentText(change.cents.toDouble() / it.cents * 100)})" }.orEmpty()
    return "$arrow $amount$percent"
}

/** "2.8%" under ten, "14%" above it: a tenth of a percent is noise on a large move. */
fun percentText(percent: Double): String {
    val magnitude = abs(percent)
    return if (magnitude < 10) "${(magnitude * 10).roundToInt() / 10.0}%" else "${magnitude.roundToInt()}%"
}

/** A price's move as a short chip for card art: "▲3%". Null when there is no move to show. */
fun trendChip(change: Money?, value: Money): String? {
    if (change == null || value.isZero) return null
    val before = value - change
    if (before.isZero || change.isZero) return null
    val percent = change.cents.toDouble() / before.cents * 100
    if (abs(percent) < 0.5) return null
    return (if (change.cents > 0) "▲" else "▼") + percentText(percent)
}

private fun List<PricePoint>.within(range: ChartRange): List<PricePoint> {
    val days = range.days ?: return this
    if (isEmpty()) return this
    val cutoff = epochDay(last().date) - days
    return filter { epochDay(it.date) >= cutoff }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun shortDate(iso: String): String {
    val parts = iso.split("-")
    val month = parts.getOrNull(1)?.toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return iso
    return "$month ${parts.getOrNull(2)?.trimStart('0').orEmpty()} '${parts[0].takeLast(2)}"
}

/**
 * Days since 1970-01-01 for a `yyyy-MM-dd` date, by the civil-calendar formula.
 *
 * Written out rather than pulling in a date library for one subtraction; the history's
 * points are dates, and the chart only needs how far apart they are.
 */
internal fun epochDay(iso: String): Int {
    val parts = iso.split("-")
    var y = parts.getOrNull(0)?.toIntOrNull() ?: return 0
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val d = parts.getOrNull(2)?.toIntOrNull() ?: 1
    if (m <= 2) y -= 1
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}
