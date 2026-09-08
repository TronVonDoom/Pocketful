package app.pocketful.ui.collections

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
import androidx.compose.ui.unit.dp
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerKind
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.AppTextField
import app.pocketful.ui.components.ButtonTone
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.ColorSwatchRow
import app.pocketful.ui.components.ConfirmToggle
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.SpineSwatches

/** Everything a container needs to exist. */
data class ContainerDraft(
    val name: String,
    val subtitle: String?,
    val kind: ContainerKind,
    val color: Long,
)

/**
 * Create or edit a container.
 *
 * Shorter than the binder editor by exactly the fields a box does not have: no page
 * shape, no sheet count, no capacity. That absence is the whole reason containers are a
 * separate type rather than a binder with the grid switched off.
 *
 * [onDelete] carries whether the contents go too, the same choice a binder gets: emptying
 * a box and selling one sealed are different events, and the app should record either.
 */
@Composable
fun ContainerEditorSheet(
    visible: Boolean,
    existing: Container?,
    onDismiss: () -> Unit,
    onSave: (ContainerDraft) -> Unit,
    onDelete: ((deleteCards: Boolean) -> Unit)? = null,
) {
    // Rebuilt on each opening, never on closing, so dismissing does not visibly blank
    // every field a frame before the slide-out animation runs.
    var openCount by remember { mutableStateOf(0) }
    var subject by remember { mutableStateOf(existing) }
    LaunchedEffect(visible) {
        if (visible) {
            subject = existing
            openCount++
        }
    }

    val record = if (visible) existing else subject
    val formKey = openCount

    var name by remember(formKey) { mutableStateOf(record?.name ?: "") }
    var subtitle by remember(formKey) { mutableStateOf(record?.subtitle ?: "") }
    var kind by remember(formKey) { mutableStateOf(record?.kind ?: ContainerKind.BOX) }
    var color by remember(formKey) { mutableStateOf(record?.color ?: SpineSwatches.random().value) }
    var confirmingDelete by remember(formKey) { mutableStateOf(false) }
    var deleteCards by remember(formKey) { mutableStateOf(false) }

    AppSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(
            title = if (record == null) "New container" else "Edit container",
            subtitle = kind.hint,
            onClose = onDismiss,
        )

        SheetBody {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                placeholder = kind.label,
            )

            AppTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = "Subtitle",
                placeholder = "Sorted by set",
            )

            Column {
                FieldLabel("Kind")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ContainerKind.entries.forEach { option ->
                        ChoiceChip(
                            label = option.label,
                            selected = option == kind,
                            onClick = { kind = option },
                        )
                    }
                }
            }

            Column {
                FieldLabel("Colour")
                Spacer(Modifier.height(10.dp))
                ColorSwatchRow(swatches = SpineSwatches, selected = color, onSelect = { color = it })
            }

            if (record != null && onDelete != null) {
                if (confirmingDelete) {
                    Column {
                        Text(
                            text = when {
                                record.count == 0 -> "Delete \"${record.name}\"? It is empty."
                                deleteCards ->
                                    "Delete \"${record.name}\" and the ${record.count} " +
                                        (if (record.count == 1) "card" else "cards") +
                                        " inside? They leave your collection for good."
                                else ->
                                    "Delete \"${record.name}\"? The ${record.count} " +
                                        (if (record.count == 1) "card" else "cards") +
                                        " inside stay in your collection and become unfiled."
                            },
                            color = Ink.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (record.count > 0) {
                            Spacer(Modifier.height(12.dp))
                            ConfirmToggle(
                                title = "Delete the cards too",
                                description = "For a box sold or traded away whole.",
                                checked = deleteCards,
                                onCheckedChange = { deleteCards = it },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppOutlineButton("Keep", { confirmingDelete = false }, Modifier.weight(1f))
                            AppButton(
                                label = "Delete",
                                onClick = { onDelete(deleteCards) },
                                modifier = Modifier.weight(1f),
                                tone = ButtonTone.Danger,
                                icon = AppIcons.Trash,
                            )
                        }
                    }
                } else {
                    AppOutlineButton(
                        label = "Delete container",
                        onClick = { confirmingDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = AppIcons.Trash,
                    )
                }
            }
        }

        SheetActions {
            AppOutlineButton("Cancel", onDismiss, Modifier.weight(1f))
            AppButton(
                label = if (record == null) "Create container" else "Save changes",
                onClick = {
                    onSave(
                        ContainerDraft(
                            name = name,
                            subtitle = subtitle,
                            kind = kind,
                            color = color,
                        ),
                    )
                },
                modifier = Modifier.weight(1.4f),
                enabled = name.isNotBlank(),
            )
        }
    }
}
