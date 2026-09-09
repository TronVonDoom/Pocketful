package app.pocketful.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketful.domain.CardBrief
import app.pocketful.ui.theme.Ink

/**
 * Which press run of a card you are actually holding.
 *
 * The same card in the same set is several different cards to a collector: a Reverse Holo
 * is a different pull, a different price and a different pocket from the plain one. The
 * app has always modelled that -- the catalog import fans a printing out into a variant
 * per finish -- but every acquire flow silently took the first one, so a binder built by
 * searching was a binder of normals whatever was in the sleeves.
 *
 * Drawn only when there is a choice to make. A promo that exists in one finish showing a
 * single unpressable chip is noise, and the card's own caption already names its finish.
 */
@Composable
fun VariantPicker(
    variants: List<CardBrief>,
    selected: CardBrief,
    onSelect: (CardBrief) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Variation",
) {
    if (variants.size < 2) return

    Column(modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            variants.forEach { option ->
                ChoiceChip(
                    label = option.variantLabel,
                    selected = option.variantId == selected.variantId,
                    onClick = { onSelect(option) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            // Prices are quoted per finish, so the number above the chips moves as they
            // are pressed. Saying so is cheaper than leaving someone to wonder whether
            // the value they just watched change was a glitch.
            text = if (selected.marketValue.isZero) {
                "Each variation is priced separately. This one has no quote yet."
            } else {
                "${selected.variantLabel} · ${selected.marketValue.format()} market"
            },
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** How a press run names itself in a picker: its finish, plus anything else unusual. */
val CardBrief.variantLabel: String
    get() = badge ?: finish.label
