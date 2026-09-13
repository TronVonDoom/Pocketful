package app.pocketful.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketful.data.PriceHistory
import app.pocketful.data.todayStamp
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Copy
import app.pocketful.domain.Money
import app.pocketful.domain.brief
import app.pocketful.domain.variantBriefs
import app.pocketful.state.displayOrDash
import app.pocketful.state.toMoneyOrNull
import app.pocketful.state.toPriceInput
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.ButtonTone
import app.pocketful.ui.components.CardHero
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.Hairline
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.TradeToggleRow
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * Where an owned card was opened, and what that place lets you do with it.
 *
 * The only part of the owned-card menu that differs between Home, a box and a binder
 * pocket. [where] is the line under the card's name; the three actions are offered only
 * where they mean something -- "show in binder" from outside the binder, "take out" and
 * "swap" from inside its pocket.
 */
data class CardPlace(
    val where: String,
    val onShowInBinder: (() -> Unit)? = null,
    val onTakeOut: (() -> Unit)? = null,
    val onSwap: (() -> Unit)? = null,
)

private enum class Mode { View, Edit, Sell, Delete }

/**
 * A card you own, the same menu wherever it is opened.
 *
 * Header, hero, the copy's figures, every variation of the card with its price, the price
 * history, then what the place it was opened from adds, then the actions. Edit changes
 * everything about the copy in one form -- its variation, condition, cost, grade, value and
 * notes -- and Sold records a sale, from any of those places alike.
 */
@Composable
fun ColumnScope.OwnedCardContent(
    snapshot: CollectionSnapshot,
    copy: Copy,
    history: PriceHistory,
    place: CardPlace,
    onClose: () -> Unit,
    onSetForTrade: (Boolean) -> Unit,
    onSave: (CopyDetails) -> Unit,
    onSell: (Money, String?) -> Unit,
    onDelete: () -> Unit,
) {
    val brief = snapshot.brief(copy.variantId) ?: return
    var mode by remember(copy.id) { mutableStateOf(Mode.View) }
    val variations = remember(snapshot.variants, snapshot.prices, copy.variantId) { snapshot.variantBriefs(copy.variantId) }
    // Which variation the chart is showing. Starts on the copy's own, and follows a tap
    // without changing the copy -- that is what Edit is for.
    var charted by remember(copy.id, copy.variantId) { mutableStateOf(brief) }
    val form = rememberCopyForm(key = copy.id to (mode == Mode.Edit), copy = copy, variant = brief)
    var soldPrice by remember(copy.id, mode) { mutableStateOf(snapshot.valueOf(copy).takeIf { !it.isZero }?.toPriceInput().orEmpty()) }
    var soldDate by remember(copy.id, mode) { mutableStateOf(todayStamp()) }

    SheetHeader(title = brief.name, subtitle = place.where, onClose = onClose)

    SheetBody {
        val shown = if (mode == Mode.Edit) form.variant else brief
        CardHero(
            brief = shown,
            valueLabel = snapshot.valueOf(copy).displayOrDash(brief.currency),
            caption = shown.badge ?: shown.finish.label,
        )

        when (mode) {
            Mode.Edit -> {
                VariationsSection(
                    options = variations,
                    selected = form.variant.variantId,
                    onSelect = { form.variant = it },
                    label = "Which variation this copy is",
                )
                CopyFormFields(form)
            }

            else -> {
                CopyFacts(snapshot, copy, brief)
                VariationsSection(
                    options = variations,
                    selected = charted.variantId,
                    onSelect = { charted = it },
                    trailing = { option ->
                        if (option.variantId == copy.variantId) VariationMark("This copy", Ink.Gain)
                    },
                )
                VariantHistory(history, variations.firstOrNull { it.variantId == charted.variantId } ?: brief)

                TradeToggleRow(checked = copy.forTrade, onCheckedChange = onSetForTrade)

                PlaceSection(place)

                if (mode == Mode.Sell) {
                    Hairline()
                    SaleFields(soldPrice, { soldPrice = it }, soldDate, { soldDate = it })
                }

                if (mode == Mode.Delete) {
                    Hairline()
                    Text(
                        text = "Delete this copy from your collection? Wherever it is filed becomes empty. " +
                            "To keep a record of it, mark it sold instead.",
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    when (mode) {
        Mode.View -> SheetActions {
            // An icon alone: delete is the action that should be least inviting, and four
            // labelled buttons do not fit a phone.
            CircleIconButton(
                icon = AppIcons.Trash,
                contentDescription = "Delete this copy",
                onClick = { mode = Mode.Delete },
                size = 48.dp,
            )
            AppOutlineButton("Edit", { mode = Mode.Edit }, Modifier.weight(1f), icon = AppIcons.Edit)
            AppOutlineButton("Sold", { mode = Mode.Sell }, Modifier.weight(1f), icon = AppIcons.Trade)
            place.onShowInBinder?.let { show ->
                AppButton("Binder", show, Modifier.weight(1.1f), icon = AppIcons.Binders)
            }
        }

        Mode.Edit -> SheetActions {
            AppOutlineButton("Cancel", { mode = Mode.View }, Modifier.weight(1f))
            AppButton(
                label = "Save",
                onClick = {
                    onSave(form.result())
                    mode = Mode.View
                },
                modifier = Modifier.weight(1.5f),
                icon = AppIcons.Check,
            )
        }

        Mode.Sell -> SheetActions {
            AppOutlineButton("Cancel", { mode = Mode.View }, Modifier.weight(1f))
            AppButton(
                label = "Record sale",
                onClick = { soldPrice.toMoneyOrNull()?.let { onSell(it, soldDate.takeIf { d -> d.length == 10 }) } },
                enabled = soldPrice.toMoneyOrNull() != null,
                modifier = Modifier.weight(1.5f),
                icon = AppIcons.Check,
            )
        }

        Mode.Delete -> SheetActions {
            AppOutlineButton("Keep", { mode = Mode.View }, Modifier.weight(1f))
            AppButton("Delete", onDelete, Modifier.weight(1f), tone = ButtonTone.Danger, icon = AppIcons.Trash)
        }
    }
}

/** Where the card is, and what that place offers: take it out, swap it, go and see it. */
@Composable
private fun PlaceSection(place: CardPlace) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Where it is")
        DetailRow("Filed", place.where)
        if (place.onTakeOut != null || place.onSwap != null) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                place.onTakeOut?.let { AppOutlineButton("Take out of pocket", it, Modifier.weight(1f), maxLines = 2) }
                place.onSwap?.let { AppOutlineButton("Swap for another card", it, Modifier.weight(1f), maxLines = 2) }
            }
        }
    }
}
