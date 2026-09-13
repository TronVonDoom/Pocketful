package app.pocketful.ui.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketful.data.PriceHistory
import app.pocketful.data.PricePoint
import app.pocketful.domain.CardBrief
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Condition
import app.pocketful.domain.Copy
import app.pocketful.domain.Currency
import app.pocketful.domain.Grade
import app.pocketful.domain.GradingCompany
import app.pocketful.domain.Money
import app.pocketful.domain.VariantId
import app.pocketful.state.display
import app.pocketful.state.displayOrDash
import app.pocketful.state.filterPriceInput
import app.pocketful.state.toMoneyOrNull
import app.pocketful.state.toPriceInput
import app.pocketful.ui.components.AppTextField
import app.pocketful.ui.components.ChangeLabel
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.PriceHistoryPanel
import app.pocketful.ui.components.ToggleSwitch
import app.pocketful.ui.components.changeText
import app.pocketful.ui.components.tappable
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/*
 * The pieces every card sheet is built from.
 *
 * A card opened from Home, from a binder pocket, from a box, from search or from a set's
 * checklist is the same card, and it used to be five different sheets with five different
 * ideas of what to show: one had a price chart and another did not, one could record a
 * sale, one could change the variation, one could say what the card cost. Now each sheet is
 * these parts in the same order --
 *
 *   header        the card, and where it was opened from
 *   hero          art, set and number, variation, value
 *   facts         the copy in hand, or the card's market
 *   variations    every printing of it, each with its price and today's move
 *   history       the selected variation's price over time
 *   context       what this place adds: the trade toggle, the pocket, where search adds to
 *   actions       what can be done here
 *
 * -- and only the last two change with where you are.
 */

/**
 * Every variation of a printing, one row each: its name, its price, its move today, and a
 * trailing control that belongs to the sheet -- a quantity stepper while adding, a "this
 * copy" mark on a card you own, "hunting" on a wanted pocket.
 *
 * Tapping a row selects it; what selecting means is also the sheet's to say, but the price
 * history below always follows the selection.
 */
@Composable
fun VariationsSection(
    options: List<CardBrief>,
    selected: VariantId,
    onSelect: (CardBrief) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    trailing: (@Composable (CardBrief) -> Unit)? = null,
) {
    if (options.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label ?: if (options.size > 1) "Variations · ${options.size}" else "Variation")
        options.forEach { option ->
            val isSelected = option.variantId == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(AppShape.Medium)
                    .background(if (isSelected) Ink.Accent.copy(alpha = 0.10f) else Ink.SurfaceRaised)
                    .border(1.dp, if (isSelected) Ink.Accent.copy(alpha = 0.6f) else Ink.OutlineFaint, AppShape.Medium)
                    .tappable(pressScale = 0.99f, onClick = { onSelect(option) })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = option.badge ?: option.finish.label,
                        color = if (isSelected) Ink.TextPrimary else Ink.TextSecondary,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = option.marketValue.displayOrDash(option.currency),
                            color = Ink.Gold,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        option.change?.let { change ->
                            Spacer(Modifier.width(8.dp))
                            ChangeLabel(change = change, base = option.marketValue - change, currency = option.currency)
                        }
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(10.dp))
                    trailing(option)
                }
            }
        }
    }
}

/** The selected variation's price over time, fetched when the selection changes. */
@Composable
fun VariantHistory(history: PriceHistory, variant: CardBrief, modifier: Modifier = Modifier) {
    var points by remember { mutableStateOf<List<PricePoint>?>(null) }
    LaunchedEffect(variant.variantId) {
        points = null
        points = history.seriesFor(variant.variantId)
    }
    PriceHistoryPanel(
        points = points,
        title = "${variant.badge ?: variant.finish.label} price",
        currency = variant.currency,
        modifier = modifier,
    )
}

/**
 * The copy in hand, as figures: condition, grade, what it is worth and why, how it moved,
 * what it cost, and how far ahead that leaves you.
 */
@Composable
fun CopyFacts(snapshot: CollectionSnapshot, copy: Copy, brief: CardBrief) {
    val value = snapshot.valueOf(copy)
    val paid = copy.acquiredPrice
    // What someone paid is a figure typed in their own currency; subtracting it from a euro
    // quote is not a smaller gain -- it is not a quantity at all.
    val gain = paid?.takeIf { brief.currency == Currency.USD }?.let { value - it }
    val before = snapshot.previousValueOf(copy)
    val source = when {
        copy.valueOverride != null -> "your value"
        else -> snapshot.prices[copy.variantId]
            ?.takeIf { !it.market.isZero }
            ?.let { quote -> if (quote.source == "tcgplayer") "TCGplayer" else quote.source }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailRow("Condition", copy.condition.label)
        copy.grade?.let { grade -> DetailRow("Grade", grade.label + (grade.certNumber?.let { " · $it" } ?: "")) }
        DetailRow("Value", value.displayOrDash(brief.currency), caption = source)
        if (before != null && !value.isZero) {
            val change = value - before
            DetailRow(
                label = "Since yesterday",
                value = changeText(change, before, brief.currency),
                valueColor = when {
                    change.cents > 0 -> Ink.Gain
                    change.cents < 0 -> Ink.Loss
                    else -> Ink.TextSecondary
                },
            )
        }
        DetailRow("Paid", paid?.display() ?: "not recorded", caption = copy.acquiredDate)
        if (gain != null) {
            DetailRow(
                label = "Unrealised",
                value = (if (gain.cents >= 0) "+" else "") + gain.display(),
                valueColor = if (gain.cents >= 0) Ink.Gain else Ink.Loss,
            )
        }
        copy.notes?.let { DetailRow("Notes", it, valueColor = Ink.TextSecondary) }
    }
    if (copy.isGraded && copy.valueOverride == null) {
        Text(
            text = "Valued at the raw card's price. TCGplayer does not price graded cards, so give " +
                "the slab its own value under Edit to count it properly.",
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** What one copy is being recorded or edited as. See [CopyFormFields]. */
@Stable
class CopyForm(copy: Copy?, variant: CardBrief) {
    var variant by mutableStateOf(variant)
    var condition by mutableStateOf(copy?.condition ?: Condition.NEAR_MINT)
    var paid by mutableStateOf(copy?.acquiredPrice?.toPriceInput().orEmpty())
    var acquired by mutableStateOf(copy?.acquiredDate.orEmpty())
    var graded by mutableStateOf(copy?.grade != null)
    var company by mutableStateOf(copy?.grade?.company ?: GradingCompany.PSA)
    var score by mutableStateOf(copy?.grade?.score.orEmpty())
    var cert by mutableStateOf(copy?.grade?.certNumber.orEmpty())
    var value by mutableStateOf(copy?.valueOverride?.toPriceInput().orEmpty())
    var notes by mutableStateOf(copy?.notes.orEmpty())

    fun result(): CopyDetails = CopyDetails(
        variantId = variant.variantId,
        condition = condition,
        paid = paid.toMoneyOrNull(),
        acquiredDate = acquired.takeIf { it.length == 10 },
        grade = if (graded && score.isNotBlank()) Grade(company, score.trim(), cert.trim().takeIf { it.isNotEmpty() }) else null,
        valueOverride = value.toMoneyOrNull(),
        notes = notes.trim().takeIf { it.isNotEmpty() },
    )
}

/** Everything the form records about one copy. */
data class CopyDetails(
    val variantId: VariantId,
    val condition: Condition,
    val paid: Money?,
    val acquiredDate: String?,
    val grade: Grade?,
    val valueOverride: Money?,
    val notes: String?,
)

/** A form for [copy] (null for a new one), rebuilt whenever [key] changes. */
@Composable
fun rememberCopyForm(key: Any?, copy: Copy?, variant: CardBrief): CopyForm = remember(key) { CopyForm(copy, variant) }

/**
 * Recording or editing one copy, the same fields wherever it happens: condition, what was
 * paid and when, a grade, a value of your own, notes. The variation is chosen in the
 * [VariationsSection] above rather than here, so a sheet never asks it twice.
 */
@Composable
fun CopyFormFields(form: CopyForm) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            FieldLabel("Condition")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Condition.entries.forEach { option ->
                    ChoiceChip(label = option.short, selected = option == form.condition, onClick = { form.condition = option })
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = form.condition.label + " · " + when {
                    form.condition.multiplier > 1.0 -> "valued above market"
                    form.condition.multiplier == 1.0 -> "valued at market"
                    else -> "valued at ${(form.condition.multiplier * 100).toInt()}% of market"
                },
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = form.paid,
                onValueChange = { form.paid = it.filterPriceInput() },
                label = "What you paid",
                placeholder = "0.00",
                prefix = "$",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = form.acquired,
                onValueChange = { form.acquired = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                label = "Acquired",
                placeholder = "yyyy-mm-dd",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Graded", color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                    Text("In a slab from a grading company", color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
                ToggleSwitch(checked = form.graded, onCheckedChange = { form.graded = it })
            }
            if (form.graded) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GradingCompany.entries.forEach { option ->
                        ChoiceChip(label = option.name, selected = option == form.company, onClick = { form.company = option }, accent = Ink.Gold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = form.score,
                        onValueChange = { form.score = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                        label = "Grade",
                        placeholder = "10",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = form.cert,
                        onValueChange = { form.cert = it.take(20) },
                        label = "Cert number",
                        placeholder = "optional",
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
        }

        AppTextField(
            value = form.value,
            onValueChange = { form.value = it.filterPriceInput() },
            label = if (form.graded) "Its value (TCGplayer prices raw cards only)" else "Your value (blank uses the market)",
            placeholder = "market",
            prefix = "$",
            keyboardType = KeyboardType.Decimal,
        )

        AppTextField(
            value = form.notes,
            onValueChange = { form.notes = it },
            label = "Notes",
            placeholder = "Where it came from, anything worth remembering",
            singleLine = false,
        )
    }
}

/** What a sale is recorded as. */
@Composable
fun SaleFields(price: String, onPrice: (String) -> Unit, date: String, onDate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FieldLabel("Mark as sold")
        Text(
            text = "Takes the card out of your collection and keeps a record of the sale, with what it " +
                "made over what you paid.",
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = price,
                onValueChange = { onPrice(it.filterPriceInput()) },
                label = "Sold for",
                placeholder = "0.00",
                prefix = "$",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = date,
                onValueChange = { onDate(it.filter { c -> c.isDigit() || c == '-' }.take(10)) },
                label = "Date",
                placeholder = "yyyy-mm-dd",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A small label for the trailing slot of a variation row: "This copy", "Hunting". */
@Composable
fun VariationMark(text: String, color: androidx.compose.ui.graphics.Color = Ink.Accent) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(AppShape.Chip)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
