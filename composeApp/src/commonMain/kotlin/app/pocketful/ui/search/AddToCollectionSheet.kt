package app.pocketful.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketful.data.PriceHistory
import app.pocketful.data.PricePoint
import app.pocketful.domain.CardBrief
import app.pocketful.domain.Condition
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerId
import app.pocketful.domain.Grade
import app.pocketful.domain.GradingCompany
import app.pocketful.domain.Money
import app.pocketful.domain.VariantId
import app.pocketful.state.displayOrDash
import app.pocketful.state.filterPriceInput
import app.pocketful.state.toMoneyOrNull
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.AppTextField
import app.pocketful.ui.components.CardHero
import app.pocketful.ui.components.ChangeLabel
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.Hairline
import app.pocketful.ui.components.PriceHistoryPanel
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.Stepper
import app.pocketful.ui.components.ToggleSwitch
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * A card found by searching: what each printing of it is worth, how many you have, and a
 * way to add more.
 *
 * The top half is the quick path. Every variation -- the normal, the reverse, the Pokémon
 * Center stamp -- is a row with its price, its move since yesterday and a stepper holding
 * how many sit in the place search is adding to. A tap on plus is a near-mint copy filed
 * there; a tap on minus takes one back out. Most cards are recorded that way, without the
 * form.
 *
 * The form below is for the copies worth describing: the condition, what was paid and when,
 * a grade, a value of your own. It records one copy of whichever variation is selected.
 *
 * Binders are deliberately absent from the destinations. A binder is not a bag -- a card in
 * one is in a numbered pocket -- and picking a pocket needs the page view, which is one tap
 * away once the card exists.
 */
@Composable
fun AddToCollectionSheet(
    brief: CardBrief?,
    variants: List<CardBrief>,
    containers: List<Container>,
    destination: ContainerId?,
    /** How many copies of each variation sit in [destination]. */
    counts: Map<VariantId, Int>,
    history: PriceHistory,
    onDestinationChange: (ContainerId?) -> Unit,
    onStep: (CardBrief, Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (CardBrief, CopyDetails) -> Unit,
) {
    // Latched so the sheet still has a card to draw while it animates out. The chosen
    // press run and the list to choose from are latched alongside it for the same reason:
    // all three have to survive the card being cleared by the dismiss.
    var latched by remember { mutableStateOf<CardBrief?>(null) }
    var options by remember { mutableStateOf<List<CardBrief>>(emptyList()) }
    var openCount by remember { mutableStateOf(0) }
    LaunchedEffect(brief, variants) {
        if (brief != null) {
            if (latched?.printingId != brief.printingId) {
                latched = brief
                openCount++
            }
            options = variants
            // Keep the selected variation's figures current as the collection changes.
            latched = variants.firstOrNull { it.variantId == latched?.variantId } ?: latched
        } else {
            latched = null
        }
    }

    val formKey = openCount
    var condition by remember(formKey) { mutableStateOf(Condition.NEAR_MINT) }
    var paid by remember(formKey) { mutableStateOf("") }
    var acquired by remember(formKey) { mutableStateOf("") }
    var yourValue by remember(formKey) { mutableStateOf("") }
    var graded by remember(formKey) { mutableStateOf(false) }
    var company by remember(formKey) { mutableStateOf(GradingCompany.PSA) }
    var score by remember(formKey) { mutableStateOf("") }
    var cert by remember(formKey) { mutableStateOf("") }
    var showDetails by remember(formKey) { mutableStateOf(false) }

    val active = latched

    var points by remember { mutableStateOf<List<PricePoint>?>(null) }
    LaunchedEffect(active?.variantId) {
        points = null
        active?.let { points = history.seriesFor(it.variantId) }
    }

    AppSheet(visible = brief != null && active != null, onDismiss = onDismiss) {
        if (active == null) return@AppSheet

        SheetHeader(
            title = active.name,
            subtitle = "${active.setName} · ${active.collectorNumber}",
            onClose = onDismiss,
        )

        SheetBody {
            CardHero(
                brief = active,
                valueLabel = active.marketValue.displayOrDash(active.currency),
                caption = active.badge ?: active.finish.label,
            )

            Column {
                FieldLabel("Adding to")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip(label = "Unfiled", selected = destination == null, onClick = { onDestinationChange(null) })
                    containers.forEach { container ->
                        ChoiceChip(
                            label = container.name,
                            selected = destination == container.id,
                            onClick = { onDestinationChange(container.id) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(if (options.size > 1) "Variations" else "Quantity")
                options.ifEmpty { listOf(active) }.forEach { option ->
                    VariationRow(
                        option = option,
                        selected = option.variantId == active.variantId,
                        count = counts[option.variantId] ?: 0,
                        onSelect = { latched = option },
                        onStep = { delta -> onStep(option, delta) },
                    )
                }
            }

            PriceHistoryPanel(points = points, title = "${active.badge ?: active.finish.label} price", currency = active.currency)

            Hairline()

            if (!showDetails) {
                AppOutlineButton(
                    label = "Add a copy with details",
                    onClick = { showDetails = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcons.Edit,
                )
            } else {
                Text(
                    text = "One copy of ${active.badge ?: active.finish.label}, with what you know about it.",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Column {
                    FieldLabel("Condition")
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Condition.entries.forEach { option ->
                            ChoiceChip(label = option.short, selected = option == condition, onClick = { condition = option })
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = paid,
                        onValueChange = { paid = it.filterPriceInput() },
                        label = "What you paid",
                        placeholder = "0.00",
                        prefix = "$",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = acquired,
                        onValueChange = { acquired = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
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
                    value = yourValue,
                    onValueChange = { yourValue = it.filterPriceInput() },
                    label = if (graded) "Its value (TCGplayer prices raw cards only)" else "Your value (optional)",
                    placeholder = "market",
                    prefix = "$",
                    keyboardType = KeyboardType.Decimal,
                )
            }
        }

        if (showDetails) {
            SheetActions {
                AppOutlineButton("Back", { showDetails = false }, Modifier.weight(1f))
                AppButton(
                    label = "Add copy",
                    onClick = {
                        onConfirm(
                            active,
                            CopyDetails(
                                condition = condition,
                                paid = paid.toMoneyOrNull(),
                                acquiredDate = acquired.takeIf { it.length == 10 },
                                grade = if (graded && score.isNotBlank()) {
                                    Grade(company, score.trim(), cert.trim().takeIf { it.isNotEmpty() })
                                } else {
                                    null
                                },
                                valueOverride = yourValue.toMoneyOrNull(),
                            ),
                        )
                        showDetails = false
                    },
                    modifier = Modifier.weight(1.5f),
                    icon = AppIcons.Plus,
                )
            }
        }
    }
}

/** What the detailed form records about one copy. */
data class CopyDetails(
    val condition: Condition,
    val paid: Money?,
    val acquiredDate: String?,
    val grade: Grade?,
    val valueOverride: Money?,
)

@Composable
private fun VariationRow(
    option: CardBrief,
    selected: Boolean,
    count: Int,
    onSelect: () -> Unit,
    onStep: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            ChoiceChip(label = option.badge ?: option.finish.label, selected = selected, onClick = onSelect)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.marketValue.displayOrDash(option.currency),
                    color = Ink.Gold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                option.change?.let { change ->
                    Spacer(Modifier.width(8.dp))
                    ChangeLabel(change = change, base = option.marketValue - change, currency = option.currency)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Stepper(
            value = count,
            onValueChange = { next -> onStep(next - count) },
            range = 0..999,
            modifier = Modifier.width(132.dp),
        )
    }
}

/**
 * Whether a copy is slabbed, and by whom at what grade.
 *
 * Shared by the add sheet and a copy's own sheet, so a grade is recorded the same way
 * wherever it is typed.
 */
@Composable
fun GradeFields(
    graded: Boolean,
    onGradedChange: (Boolean) -> Unit,
    company: GradingCompany,
    onCompanyChange: (GradingCompany) -> Unit,
    score: String,
    onScoreChange: (String) -> Unit,
    cert: String,
    onCertChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Graded", color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text("In a slab from a grading company", color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)
            }
            ToggleSwitch(checked = graded, onCheckedChange = onGradedChange)
        }
        if (graded) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GradingCompany.entries.forEach { option ->
                    ChoiceChip(label = option.name, selected = option == company, onClick = { onCompanyChange(option) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(
                    value = score,
                    onValueChange = { onScoreChange(it.filter { c -> c.isDigit() || c == '.' }.take(4)) },
                    label = "Grade",
                    placeholder = "10",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                AppTextField(
                    value = cert,
                    onValueChange = { onCertChange(it.take(20)) },
                    label = "Cert number",
                    placeholder = "optional",
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}
