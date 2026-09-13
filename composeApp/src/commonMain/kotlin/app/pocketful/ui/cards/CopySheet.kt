package app.pocketful.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pocketful.data.PriceHistory
import app.pocketful.data.PricePoint
import app.pocketful.data.todayStamp
import app.pocketful.state.display
import app.pocketful.state.displayOrDash
import app.pocketful.state.filterPriceInput
import app.pocketful.state.toMoneyOrNull
import app.pocketful.state.toPriceInput
import app.pocketful.domain.BinderId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Currency
import app.pocketful.domain.CopyId
import app.pocketful.domain.Grade
import app.pocketful.domain.GradingCompany
import app.pocketful.domain.Location
import app.pocketful.domain.Money
import app.pocketful.domain.brief
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.AppTextField
import app.pocketful.ui.components.ButtonTone
import app.pocketful.ui.components.CardHero
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.Hairline
import app.pocketful.ui.components.PriceHistoryPanel
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.TradeToggleRow
import app.pocketful.ui.components.changeText
import app.pocketful.ui.search.GradeFields
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * One owned card, opened from the card list rather than from a pocket.
 *
 * The list is the only place an *unfiled* copy can be reached -- a card taken out of a
 * binder has no pocket to tap -- so this sheet has to work whether or not the copy has a
 * home. It is also where the money side of a copy lives: what it cost and when, what it is
 * worth if the market does not know (a slab, most obviously), how it has moved, and
 * recording that it was sold.
 */
@Composable
fun CopySheet(
    copyId: CopyId?,
    snapshot: CollectionSnapshot,
    history: PriceHistory,
    onDismiss: () -> Unit,
    onShowInBinder: (BinderId, Int) -> Unit,
    onSetForTrade: (CopyId, Boolean) -> Unit,
    onDelete: (CopyId) -> Unit,
    onUpdateMoney: (CopyId, Money?, String?, Money?) -> Unit,
    onSetGrade: (CopyId, Grade?) -> Unit,
    onMarkSold: (CopyId, Money, String?) -> Unit,
) {
    var latched by remember { mutableStateOf<CopyId?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var selling by remember { mutableStateOf(false) }

    LaunchedEffect(copyId) {
        if (copyId != null) {
            latched = copyId
            confirmingDelete = false
            editing = false
            selling = false
        }
    }

    val active = latched
    val copy = active?.let { snapshot.copies[it] }
    val brief = copy?.let { snapshot.brief(it.variantId) }

    var points by remember { mutableStateOf<List<PricePoint>?>(null) }
    LaunchedEffect(copy?.variantId) {
        points = null
        copy?.let { points = history.seriesFor(it.variantId) }
    }

    AppSheet(visible = copyId != null && copy != null && brief != null, onDismiss = onDismiss) {
        if (copy == null || brief == null) return@AppSheet

        val value = snapshot.valueOf(copy)
        val paid = copy.acquiredPrice
        // Only meaningful against a price in the same money. What someone paid is a figure
        // they typed in their own currency, and subtracting it from a euro quote is not a
        // smaller gain -- it is not a quantity at all.
        val gain = paid?.takeIf { brief.currency == Currency.USD }?.let { value - it }
        val before = snapshot.previousValueOf(copy)
        val priceSource = when {
            copy.valueOverride != null -> "your value"
            else -> snapshot.prices[copy.variantId]
                ?.takeIf { !it.market.isZero }
                ?.let { quote -> if (quote.source == "tcgplayer") "TCGplayer" else quote.source }
        }
        val slot = copy.location as? Location.BinderSlot
        val binderName = slot?.let { location -> snapshot.binders.firstOrNull { it.id == location.binderId }?.name }
        val container = (copy.location as? Location.InContainer)
            ?.let { location -> snapshot.container(location.containerId) }

        // The edit form, seeded from the copy each time it opens.
        var paidInput by remember(copy.id, editing) { mutableStateOf(paid?.toPriceInput().orEmpty()) }
        var dateInput by remember(copy.id, editing) { mutableStateOf(copy.acquiredDate.orEmpty()) }
        var valueInput by remember(copy.id, editing) { mutableStateOf(copy.valueOverride?.toPriceInput().orEmpty()) }
        var graded by remember(copy.id, editing) { mutableStateOf(copy.grade != null) }
        var company by remember(copy.id, editing) { mutableStateOf(copy.grade?.company ?: GradingCompany.PSA) }
        var score by remember(copy.id, editing) { mutableStateOf(copy.grade?.score.orEmpty()) }
        var cert by remember(copy.id, editing) { mutableStateOf(copy.grade?.certNumber.orEmpty()) }
        var soldInput by remember(copy.id, selling) { mutableStateOf(value.takeIf { !it.isZero }?.toPriceInput().orEmpty()) }
        var soldDate by remember(copy.id, selling) { mutableStateOf(todayStamp()) }

        SheetHeader(
            title = brief.name,
            subtitle = when {
                binderName != null -> "$binderName · Pocket ${slot.ordinal + 1}"
                container != null -> "${container.name} · ${container.kind.label.lowercase()}"
                else -> "Not filed anywhere"
            },
            onClose = onDismiss,
        )

        SheetBody {
            CardHero(
                brief = brief,
                valueLabel = value.displayOrDash(brief.currency),
                caption = brief.badge ?: brief.finish.label,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Condition", copy.condition.label)
                copy.grade?.let { grade ->
                    DetailRow("Grade", grade.label + (grade.certNumber?.let { " · $it" } ?: ""))
                }
                DetailRow(
                    label = "Value",
                    value = value.displayOrDash(brief.currency),
                    // Says which shop quoted it whenever that is not the obvious one. A
                    // euro figure with no explanation reads as a bug; "Cardmarket" reads
                    // as the reason there is a price here at all.
                    caption = priceSource,
                )
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
            }

            if (copy.isGraded && copy.valueOverride == null) {
                Text(
                    text = "This value is the raw card's market price. TCGplayer does not price graded " +
                        "cards, so set the slab's own value under Edit to count it properly.",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            PriceHistoryPanel(points = points, currency = brief.currency)

            TradeToggleRow(
                checked = copy.forTrade,
                onCheckedChange = { onSetForTrade(copy.id, it) },
            )

            if (editing) {
                Hairline()
                FieldLabel("Edit")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = paidInput,
                        onValueChange = { paidInput = it.filterPriceInput() },
                        label = "What you paid",
                        placeholder = "0.00",
                        prefix = "$",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                        label = "Acquired",
                        placeholder = "yyyy-mm-dd",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
                GradeFields(
                    graded = graded,
                    onGradedChange = { graded = it },
                    company = company,
                    onCompanyChange = { company = it },
                    score = score,
                    onScoreChange = { score = it },
                    cert = cert,
                    onCertChange = { cert = it },
                )
                AppTextField(
                    value = valueInput,
                    onValueChange = { valueInput = it.filterPriceInput() },
                    label = "Your value (blank uses the market)",
                    placeholder = "market",
                    prefix = "$",
                    keyboardType = KeyboardType.Decimal,
                )
            }

            if (selling) {
                Hairline()
                FieldLabel("Mark as sold")
                Text(
                    text = "Takes the card out of your collection and keeps a record of the sale, " +
                        "with what it made over what you paid.",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = soldInput,
                        onValueChange = { soldInput = it.filterPriceInput() },
                        label = "Sold for",
                        placeholder = "0.00",
                        prefix = "$",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = soldDate,
                        onValueChange = { soldDate = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                        label = "Date",
                        placeholder = "yyyy-mm-dd",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            copy.notes?.let { notes ->
                Column {
                    FieldLabel("Notes")
                    Spacer(Modifier.height(6.dp))
                    Text(notes, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (slot == null && container == null && !editing && !selling) {
                Hairline()
                Text(
                    text = "This copy is not filed anywhere. Open any empty pocket and it will be offered " +
                        "under \"unfiled\", or add it to a container from that container's page.",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (confirmingDelete) {
                Column {
                    Text(
                        text = "Delete this copy from your collection? The pocket it sits in becomes empty.",
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppOutlineButton("Keep", { confirmingDelete = false }, Modifier.weight(1f))
                        AppButton(
                            label = "Delete",
                            onClick = { onDelete(copy.id) },
                            modifier = Modifier.weight(1f),
                            tone = ButtonTone.Danger,
                            icon = AppIcons.Trash,
                        )
                    }
                }
            }
        }

        when {
            editing -> SheetActions {
                AppOutlineButton("Cancel", { editing = false }, Modifier.weight(1f))
                AppButton(
                    label = "Save",
                    onClick = {
                        onUpdateMoney(
                            copy.id,
                            paidInput.toMoneyOrNull(),
                            dateInput.takeIf { it.length == 10 },
                            valueInput.toMoneyOrNull(),
                        )
                        onSetGrade(
                            copy.id,
                            if (graded && score.isNotBlank()) {
                                Grade(company, score.trim(), cert.trim().takeIf { it.isNotEmpty() })
                            } else {
                                null
                            },
                        )
                        editing = false
                    },
                    modifier = Modifier.weight(1.5f),
                    icon = AppIcons.Check,
                )
            }

            selling -> SheetActions {
                AppOutlineButton("Cancel", { selling = false }, Modifier.weight(1f))
                AppButton(
                    label = "Record sale",
                    onClick = { soldInput.toMoneyOrNull()?.let { onMarkSold(copy.id, it, soldDate.takeIf { d -> d.length == 10 }) } },
                    enabled = soldInput.toMoneyOrNull() != null,
                    modifier = Modifier.weight(1.5f),
                    icon = AppIcons.Check,
                )
            }

            !confirmingDelete -> SheetActions {
                // An icon alone: four labelled actions do not fit a phone's width, and delete
                // is the one that should be least inviting anyway.
                CircleIconButton(
                    icon = AppIcons.Trash,
                    contentDescription = "Delete this copy",
                    onClick = { confirmingDelete = true },
                    size = 48.dp,
                )
                AppOutlineButton(
                    label = "Edit",
                    onClick = { editing = true },
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.Edit,
                )
                AppOutlineButton(
                    label = "Sold",
                    onClick = { selling = true },
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.Trade,
                )
                if (slot != null) {
                    AppButton(
                        label = "Binder",
                        onClick = { onShowInBinder(slot.binderId, slot.ordinal) },
                        modifier = Modifier.weight(1.1f),
                        icon = AppIcons.Binders,
                    )
                }
            }
        }
    }
}
