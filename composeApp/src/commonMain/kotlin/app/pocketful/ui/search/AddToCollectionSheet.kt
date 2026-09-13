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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketful.data.PriceHistory
import app.pocketful.domain.CardBrief
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerId
import app.pocketful.domain.VariantId
import app.pocketful.state.displayOrDash
import app.pocketful.ui.card.CopyDetails
import app.pocketful.ui.card.CopyFormFields
import app.pocketful.ui.card.VariantHistory
import app.pocketful.ui.card.VariationsSection
import app.pocketful.ui.card.rememberCopyForm
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.CardHero
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.Stepper
import app.pocketful.ui.theme.AppIcons

/**
 * A card found by searching or in a set's checklist -- the same card menu as everywhere
 * else, with what adding needs.
 *
 * Header, hero, then how many you already have and where search is adding to, every
 * variation with a quantity stepper for that place, the price history, and one action:
 * add a copy with details. Most cards are recorded with the steppers alone; the form is for
 * the copies worth describing, and it is the same form a card you own is edited with.
 *
 * Binders are deliberately absent from the destinations. A binder is not a bag -- a card in
 * one is in a numbered pocket -- and picking a pocket needs the page view.
 */
@Composable
fun AddToCollectionSheet(
    brief: CardBrief?,
    variants: List<CardBrief>,
    containers: List<Container>,
    destination: ContainerId?,
    /** How many copies of each variation sit in [destination]. */
    counts: Map<VariantId, Int>,
    /** How many copies of this printing the whole collection holds, wherever they are. */
    ownedAnywhere: Int,
    history: PriceHistory,
    onDestinationChange: (ContainerId?) -> Unit,
    onStep: (CardBrief, Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (CardBrief, CopyDetails) -> Unit,
) {
    // Latched so the sheet still has a card to draw while it animates out.
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
        }
    }

    var showDetails by remember(openCount) { mutableStateOf(false) }
    val active = latched

    AppSheet(visible = brief != null && active != null, onDismiss = onDismiss) {
        if (active == null) return@AppSheet
        val form = rememberCopyForm(key = openCount to showDetails, copy = null, variant = active)

        SheetHeader(title = active.name, subtitle = "${active.setName} · ${active.collectorNumber}", onClose = onDismiss)

        SheetBody {
            CardHero(
                brief = active,
                valueLabel = active.marketValue.displayOrDash(active.currency),
                caption = active.badge ?: active.finish.label,
            )

            DetailRow(
                "In your collection",
                when (ownedAnywhere) {
                    0 -> "none yet"
                    1 -> "1 copy"
                    else -> "$ownedAnywhere copies"
                },
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
                        ChoiceChip(label = container.name, selected = destination == container.id, onClick = { onDestinationChange(container.id) })
                    }
                }
            }

            VariationsSection(
                options = options.ifEmpty { listOf(active) },
                selected = active.variantId,
                onSelect = {
                    latched = it
                    form.variant = it
                },
                trailing = if (showDetails) null else { option ->
                    val count = counts[option.variantId] ?: 0
                    Stepper(
                        value = count,
                        onValueChange = { next -> onStep(option, next - count) },
                        range = 0..999,
                        modifier = Modifier.width(128.dp),
                    )
                },
            )

            if (showDetails) {
                CopyFormFields(form)
            } else {
                VariantHistory(history, active)
            }
        }

        SheetActions {
            if (showDetails) {
                AppOutlineButton("Back", { showDetails = false }, Modifier.weight(1f))
                AppButton(
                    label = "Add copy",
                    onClick = {
                        onConfirm(active, form.result())
                        showDetails = false
                    },
                    modifier = Modifier.weight(1.5f),
                    icon = AppIcons.Plus,
                )
            } else {
                AppButton(
                    label = "Add a copy with details",
                    onClick = { showDetails = true },
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.Edit,
                )
            }
        }
    }
}
