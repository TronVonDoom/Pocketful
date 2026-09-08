package app.pocketful.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Container
import app.pocketful.domain.CopyId
import app.pocketful.domain.CopyRow
import app.pocketful.domain.brief
import app.pocketful.state.display
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AddStorageRow
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.ContainerCover
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SelectionAction
import app.pocketful.ui.components.SelectionConfirm
import app.pocketful.ui.components.SelectionIsland
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.Tag
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * One container, and what is in it.
 *
 * Deliberately a list, not a grid. A binder page is worth drawing because the arrangement
 * is real -- you can point at the pocket. The order of a box is whatever order you happen
 * to have put things in, so the useful view is the one that sorts and scans.
 *
 * Cards come in here in handfuls, so they go out in handfuls too: holding a row starts a
 * selection, and emptying half a deck back onto the desk is one gesture rather than
 * twenty taps on twenty small minus buttons.
 */
@Composable
fun ContainerScreen(
    container: Container,
    snapshot: CollectionSnapshot,
    selection: Set<CopyId>,
    onSelectionChange: (Set<CopyId>) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddCards: () -> Unit,
    onOpenCopy: (CopyRow) -> Unit,
    onRemoveSelected: (Set<CopyId>) -> Unit,
    onDeleteSelected: (Set<CopyId>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    val summary = remember(container, snapshot) { snapshot.summarize(container) }
    val tint = Color(container.color)

    // Kept in the container's own order rather than sorted by value: this is the one
    // screen where the list is the physical stack, and reordering it under the user
    // would make "the third one down" mean nothing.
    val rows = remember(container, snapshot) {
        container.copyIds.mapNotNull { copyId ->
            val copy = snapshot.copies[copyId] ?: return@mapNotNull null
            val brief = snapshot.brief(copy.variantId) ?: return@mapNotNull null
            CopyRow(
                copy = copy,
                brief = brief,
                value = snapshot.valueOf(copy),
                holderName = container.name,
                ordinal = null,
            )
        }
    }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Background)) {
        ScreenBackdrop(tint, height = 300.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = islandBottomInset()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = container.kind.label,
                    title = container.name,
                    subtitle = container.subtitle,
                    headline = summary.marketValue.format(),
                    summary = summary,
                    cover = { ContainerCover(container, width = 44.dp) },
                    leading = {
                        CircleIconButton(AppIcons.ChevronLeft, "Back to collections", onBack, size = 34.dp)
                    },
                    actions = { HeaderAction(AppIcons.Edit, "Edit container", onEdit) },
                    stats = buildList {
                        add(Stat("${container.count}", "cards"))
                        if (!summary.costBasis.isZero) add(Stat(summary.costBasis.display(), "cost"))
                    },
                    tags = {
                        Tag(container.kind.label, color = tint, background = tint.copy(alpha = 0.16f))
                        Tag(container.kind.hint)
                    },
                )
            }

            if (rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = AppIcons.Box,
                        title = "Nothing in here yet",
                        message = "Move cards in from anywhere else in your collection, " +
                            "including anything still unfiled.",
                        action = { AppButton("Add cards", onAddCards, icon = AppIcons.Plus) },
                    )
                }
            } else {
                item { SectionHeader("Contents · ${rows.size}", Modifier.padding(top = 2.dp, bottom = 2.dp)) }
                // The per-row "take this out" button is gone with the rows it lived on.
                // Emptying a box is something people do in handfuls, so it belongs to the
                // selection that already handles handfuls -- and a minus button on every
                // tile would put a destructive control under the thumb of someone doing
                // nothing more than scrolling through their cards.
                tileRows(items = rows, keyPrefix = "held", key = { it.copy.id.value }) { row ->
                    CardTile(
                        brief = row.brief,
                        value = row.value.displayOrNull(),
                        valueColor = Ink.Gold,
                        badge = row.copy.grade?.label ?: row.copy.condition.short.takeIf { it != "NM" },
                        badgeColor = if (row.copy.grade != null) Ink.Gold else Ink.TextTertiary,
                        selected = row.copy.id in selection,
                        onClick = {
                            if (selection.isEmpty()) onOpenCopy(row)
                            else onSelectionChange(selection.toggled(row.copy.id))
                        },
                        onLongClick = { onSelectionChange(selection.toggled(row.copy.id)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                item { AddStorageRow("Add cards", onAddCards, Modifier.padding(top = 6.dp)) }
            }
        }

        if (selection.isNotEmpty()) {
            SelectionIsland(
                count = selection.size,
                onClear = {
                    confirmingDelete = false
                    onSelectionChange(emptySet())
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                confirm = if (!confirmingDelete) {
                    null
                } else {
                    {
                        SelectionConfirm(
                            message = "Delete ${selection.size} " +
                                (if (selection.size == 1) "card" else "cards") +
                                " from your collection entirely? Taking them out of " +
                                "${container.name} instead keeps them.",
                            confirmLabel = "Delete",
                            onCancel = { confirmingDelete = false },
                            onConfirm = {
                                confirmingDelete = false
                                onDeleteSelected(selection)
                                onSelectionChange(emptySet())
                            },
                        )
                    }
                },
            ) {
                SelectionAction(
                    icon = AppIcons.Minus,
                    label = "Take out",
                    onClick = {
                        onRemoveSelected(selection)
                        onSelectionChange(emptySet())
                    },
                )
                SelectionAction(
                    icon = AppIcons.Trash,
                    label = "Delete",
                    onClick = { confirmingDelete = true },
                    tint = Ink.Loss,
                )
            }
        }
    }
}

/** Adds or removes one id. The whole vocabulary a selection needs. */
private fun Set<CopyId>.toggled(id: CopyId): Set<CopyId> =
    if (id in this) this - id else this + id
