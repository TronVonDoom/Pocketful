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
import androidx.compose.ui.unit.dp
import app.pocketful.domain.BinderId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.CopyId
import app.pocketful.domain.Location
import app.pocketful.domain.brief
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.ButtonTone
import app.pocketful.ui.components.CardHero
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
 * One owned card, opened from the card list rather than from a pocket.
 *
 * The list is the only place an *unfiled* copy can be reached -- a card taken out of a
 * binder has no pocket to tap -- so this sheet has to work whether or not the copy has a
 * home, and its main job is getting a filed card back on screen in its binder.
 */
@Composable
fun CopySheet(
    copyId: CopyId?,
    snapshot: CollectionSnapshot,
    onDismiss: () -> Unit,
    onShowInBinder: (BinderId, Int) -> Unit,
    onSetForTrade: (CopyId, Boolean) -> Unit,
    onDelete: (CopyId) -> Unit,
) {
    var latched by remember { mutableStateOf<CopyId?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(copyId) {
        if (copyId != null) {
            latched = copyId
            confirmingDelete = false
        }
    }

    val active = latched
    val copy = active?.let { snapshot.copies[it] }
    val brief = copy?.let { snapshot.brief(it.variantId) }

    AppSheet(visible = copyId != null && copy != null && brief != null, onDismiss = onDismiss) {
        if (copy == null || brief == null) return@AppSheet

        val value = snapshot.valueOf(copy)
        val paid = copy.acquiredPrice
        val gain = paid?.let { value - it }
        val slot = copy.location as? Location.BinderSlot
        val binderName = slot?.let { location -> snapshot.binders.firstOrNull { it.id == location.binderId }?.name }
        val container = (copy.location as? Location.InContainer)
            ?.let { location -> snapshot.container(location.containerId) }

        SheetHeader(
            title = brief.name,
            subtitle = when {
                binderName != null -> "$binderName · pocket ${slot.ordinal + 1}"
                container != null -> "${container.name} · ${container.kind.label.lowercase()}"
                else -> "Not filed anywhere"
            },
            onClose = onDismiss,
        )

        SheetBody {
            CardHero(brief = brief, valueLabel = value.format(), caption = brief.finish.label)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Condition", copy.condition.label)
                copy.grade?.let { grade ->
                    DetailRow("Grade", grade.label + (grade.certNumber?.let { " · $it" } ?: ""))
                }
                DetailRow("Market value", value.format())
                DetailRow("Paid", paid?.format() ?: "not recorded")
                if (gain != null) {
                    DetailRow(
                        label = "Unrealised",
                        value = (if (gain.cents >= 0) "+" else "") + gain.format(),
                        valueColor = if (gain.cents >= 0) Ink.Gain else Ink.Loss,
                    )
                }
            }

            TradeToggleRow(
                checked = copy.forTrade,
                onCheckedChange = { onSetForTrade(copy.id, it) },
            )

            copy.notes?.let { notes ->
                Column {
                    FieldLabel("Notes")
                    Spacer(Modifier.height(6.dp))
                    Text(notes, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (slot == null && container == null) {
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

        if (!confirmingDelete) {
            SheetActions {
                AppOutlineButton(
                    label = "Delete",
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.weight(1f),
                    icon = AppIcons.Trash,
                )
                if (slot != null) {
                    AppButton(
                        label = "Show in binder",
                        onClick = { onShowInBinder(slot.binderId, slot.ordinal) },
                        modifier = Modifier.weight(1.6f),
                        icon = AppIcons.Binders,
                    )
                }
            }
        }
    }
}
