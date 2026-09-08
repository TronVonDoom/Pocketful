package app.pocketful.ui.search

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pocketful.domain.CardBrief
import app.pocketful.domain.Condition
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerId
import app.pocketful.domain.Money
import app.pocketful.state.filterPriceInput
import app.pocketful.state.toMoneyOrNull
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.AppTextField
import app.pocketful.ui.components.CardHero
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * Recording a card found by searching, rather than by opening a pocket.
 *
 * The pocket flow already knows where the card is going -- you tapped the pocket. This one
 * does not, and the honest default is that it is going nowhere in particular: on the desk,
 * in the pile, to be filed later. So "Unfiled" leads the destination list rather than
 * being a fallback, and a container can be chosen in the same step because "straight into
 * the bulk box" is the other answer people actually give.
 *
 * Binders are deliberately absent from that list. A binder is not a bag -- a card in one
 * is in a numbered pocket -- and picking a pocket needs the page view, which is one tap
 * away once the card exists.
 */
@Composable
fun AddToCollectionSheet(
    brief: CardBrief?,
    containers: List<Container>,
    onDismiss: () -> Unit,
    onConfirm: (Condition, Money?, ContainerId?) -> Unit,
) {
    // Latched so the sheet still has a card to draw while it animates out.
    var latched by remember { mutableStateOf<CardBrief?>(null) }
    var openCount by remember { mutableStateOf(0) }
    LaunchedEffect(brief) {
        if (brief != null) {
            latched = brief
            openCount++
        }
    }

    val formKey = openCount
    var condition by remember(formKey) { mutableStateOf(Condition.NEAR_MINT) }
    var paid by remember(formKey) { mutableStateOf("") }
    var destination by remember(formKey) { mutableStateOf<ContainerId?>(null) }

    val active = latched

    AppSheet(visible = brief != null && active != null, onDismiss = onDismiss) {
        if (active == null) return@AppSheet

        SheetHeader(
            title = "Add to collection",
            subtitle = "${active.setName} · ${active.collectorNumber}",
            onClose = onDismiss,
        )

        SheetBody {
            CardHero(
                brief = active,
                valueLabel = active.marketValue.format(),
                caption = active.finish.label,
            )

            Column {
                FieldLabel("Condition")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Condition.entries.forEach { option ->
                        ChoiceChip(
                            label = option.short,
                            selected = option == condition,
                            onClick = { condition = option },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = condition.label,
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            AppTextField(
                value = paid,
                onValueChange = { paid = it.filterPriceInput() },
                label = "What you paid",
                placeholder = "0.00",
                prefix = "$",
                keyboardType = KeyboardType.Decimal,
            )

            Column {
                FieldLabel("Where it goes")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip(
                        label = "Unfiled",
                        selected = destination == null,
                        onClick = { destination = null },
                    )
                    containers.forEach { container ->
                        ChoiceChip(
                            label = container.name,
                            selected = destination == container.id,
                            onClick = { destination = container.id },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (destination == null) {
                        "It will sit unfiled until you drop it into a pocket or a box. " +
                            "Any empty pocket offers unfiled cards first."
                    } else {
                        "Filed straight into the container. You can move it later without " +
                            "recording it again."
                    },
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SheetActions {
            AppOutlineButton("Cancel", onDismiss, Modifier.weight(1f))
            AppButton(
                label = "Add card",
                onClick = { onConfirm(condition, paid.toMoneyOrNull(), destination) },
                modifier = Modifier.weight(1.5f),
                icon = AppIcons.Plus,
            )
        }
    }
}
