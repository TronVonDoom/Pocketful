package app.pocketful.ui.binder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pocketful.domain.Binder
import app.pocketful.domain.BinderLayout
import app.pocketful.state.AppSettings
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.AppTextField
import app.pocketful.ui.components.ButtonTone
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.ColorSwatchRow
import app.pocketful.ui.components.ConfirmToggle
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.PageGrid
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.Stepper
import app.pocketful.ui.components.ToggleSwitch
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.SpineSwatches

/** Everything a binder needs to exist, gathered in one sheet. */
data class BinderDraft(
    val name: String,
    val subtitle: String?,
    val layout: BinderLayout,
    val sheetCount: Int,
    val spineColor: Long,
)

/**
 * Create or edit a binder.
 *
 * One sheet for both, because the fields are identical and the only difference worth
 * showing the user is the verb on the save button. Delete confirms inline rather than in
 * a second sheet -- stacking two scrims to answer one question reads as a bug.
 *
 * [onDelete] carries whether the cards inside go too. Keeping them is the default and the
 * safe answer, but a binder sold as a lot is a real thing that happens, and the only way
 * to record it before was to delete forty cards by hand first -- tedious enough that most
 * people would not, leaving a phantom forty cards in a portfolio total they then stopped
 * trusting.
 */
@Composable
fun BinderEditorSheet(
    visible: Boolean,
    existing: Binder?,
    settings: AppSettings,
    /** How many cards are filed in [existing], so the question can name a real number. */
    cardsInside: Int = 0,
    onDismiss: () -> Unit,
    onSave: (BinderDraft) -> Unit,
    onDelete: ((deleteCards: Boolean) -> Unit)? = null,
) {
    // The form is rebuilt on each *opening*, never on closing. Keying it on `visible`
    // directly meant dismissing the sheet blanked every field a frame before the slide-out
    // animation ran, so the sheet visibly emptied itself on the way off screen.
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
    var layout by remember(formKey) { mutableStateOf(record?.layout ?: settings.defaultLayout) }
    var sheetCount by remember(formKey) { mutableStateOf(record?.sheetCount ?: settings.defaultSheetCount) }
    var spineColor by remember(formKey) {
        mutableStateOf(record?.spineColor ?: SpineSwatches.random().value)
    }
    var confirmingDelete by remember(formKey) { mutableStateOf(false) }
    var deleteCards by remember(formKey) { mutableStateOf(false) }

    AppSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(
            title = if (record == null) "New binder" else "Edit binder",
            subtitle = "${layout.capacity(sheetCount)} pockets · ${layout.faceCount(sheetCount)} pages",
            onClose = onDismiss,
        )

        SheetBody {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                placeholder = "Base Set",
            )

            AppTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = "Subtitle",
                placeholder = "1999 · Unlimited master set",
            )

            LayoutPicker(layout = layout, onLayoutChange = { layout = it })

            Column {
                FieldLabel("Sheets")
                Spacer(Modifier.height(8.dp))
                Stepper(
                    value = sheetCount,
                    onValueChange = { sheetCount = it },
                    range = 1..60,
                    suffix = if (sheetCount == 1) "sheet" else "sheets",
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${layout.pocketsPerSheet} pockets per sheet · " +
                        "${layout.capacity(sheetCount)} in the binder",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Column {
                FieldLabel("Spine colour")
                Spacer(Modifier.height(10.dp))
                ColorSwatchRow(
                    swatches = SpineSwatches,
                    selected = spineColor,
                    onSelect = { spineColor = it },
                )
            }

            if (record != null && onDelete != null) {
                if (confirmingDelete) {
                    Column {
                        Text(
                            text = when {
                                cardsInside == 0 -> "Delete \"${record.name}\"? Nothing is filed in it."
                                deleteCards ->
                                    "Delete \"${record.name}\" and the $cardsInside " +
                                        (if (cardsInside == 1) "card" else "cards") +
                                        " inside? They leave your collection for good."
                                else ->
                                    "Delete \"${record.name}\"? The $cardsInside " +
                                        (if (cardsInside == 1) "card" else "cards") +
                                        " inside stay in your collection and become unfiled."
                            },
                            color = Ink.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (cardsInside > 0) {
                            Spacer(Modifier.height(12.dp))
                            ConfirmToggle(
                                title = "Delete the cards too",
                                description = "For a binder sold or traded away whole.",
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
                        label = "Delete binder",
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
                label = if (record == null) "Create binder" else "Save changes",
                onClick = {
                    onSave(
                        BinderDraft(
                            name = name,
                            subtitle = subtitle,
                            layout = layout,
                            sheetCount = sheetCount,
                            spineColor = spineColor,
                        ),
                    )
                },
                modifier = Modifier.weight(1.4f),
                enabled = name.isNotBlank(),
            )
        }
    }
}

/**
 * Choosing the page shape.
 *
 * The chips are shortcuts, not the whole vocabulary -- the two steppers are, because the
 * shapes people own do not stop at the ones sold in bulk, and "12-pocket" on its own does
 * not say whether the page is four across or three. Dimensions are asked for the way a
 * binder gets described out loud: across first, then down. A preset chip lights up
 * whenever the steppers happen to land on its shape, so the two controls never disagree.
 */
@Composable
fun LayoutPicker(
    layout: BinderLayout,
    onLayoutChange: (BinderLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        FieldLabel("Page layout")
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BinderLayout.presets.forEach { preset ->
                ChoiceChip(
                    label = preset.displayName,
                    selected = preset.cols == layout.cols && preset.rows == layout.rows,
                    onClick = { onLayoutChange(layout.copy(cols = preset.cols, rows = preset.rows)) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PagePreview(layout)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DimensionStepper(
                    label = "Across",
                    value = layout.cols,
                    onValueChange = { onLayoutChange(layout.copy(cols = it)) },
                )
                DimensionStepper(
                    label = "Down",
                    value = layout.rows,
                    onValueChange = { onLayoutChange(layout.copy(rows = it)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppShape.Small)
                .background(Ink.SurfaceRaised)
                .border(1.dp, Ink.OutlineSoft, AppShape.Small)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Double-sided sheets",
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (layout.doubleSided) "Two pages per sheet" else "One page per sheet",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            ToggleSwitch(
                checked = layout.doubleSided,
                onCheckedChange = { onLayoutChange(layout.copy(doubleSided = it)) },
            )
        }
    }
}

/** A stepper with its own caption, so two of them stacked stay distinguishable. */
@Composable
private fun DimensionStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(56.dp),
        )
        Stepper(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            range = 1..BinderLayout.MAX_SIDE,
        )
    }
}

/** The page you are describing, drawn at the size of a thumbnail. */
@Composable
private fun PagePreview(layout: BinderLayout) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(86.dp)
                .aspectRatio(0.78f)
                .clip(AppShape.Small)
                .background(Ink.PocketWell)
                .border(1.dp, Ink.OutlineSoft, AppShape.Small)
                .padding(7.dp),
        ) {
            PageGrid(
                layout = layout,
                pocketColor = Ink.Accent.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxSize(),
                gap = 2.dp,
                pocketCorner = 2.dp,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = "${layout.pocketsPerFace} per page",
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
