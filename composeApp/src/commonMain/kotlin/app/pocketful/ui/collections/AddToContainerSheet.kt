package app.pocketful.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Container
import app.pocketful.domain.CopyId
import app.pocketful.domain.CopyRow
import app.pocketful.domain.copyRows
import app.pocketful.state.display
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.CardListRow
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.ValueTrailing
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

private enum class Source(val label: String) {
    Unfiled("Unfiled"),
    Everything("Everything"),
}

/**
 * Move cards you already own into a container.
 *
 * This files rather than creates. Every card in the list is a copy that already exists
 * somewhere, so choosing one moves it -- out of a pocket, out of another box -- instead
 * of recording a second physical card you do not own. [Source] defaults to unfiled
 * because filing the pile that has no home yet is what this sheet is for; "Everything" is
 * there for the rarer case of pulling a card out of a binder into a deck.
 */
@Composable
fun AddToContainerSheet(
    visible: Boolean,
    container: Container?,
    snapshot: CollectionSnapshot,
    onDismiss: () -> Unit,
    onConfirm: (List<CopyId>) -> Unit,
) {
    var openCount by remember { mutableStateOf(0) }
    LaunchedEffect(visible) { if (visible) openCount++ }
    val formKey = openCount

    var query by remember(formKey) { mutableStateOf("") }
    var source by remember(formKey) { mutableStateOf(Source.Unfiled) }
    var picked by remember(formKey) { mutableStateOf(emptySet<CopyId>()) }

    val rows = remember(snapshot, container) {
        val held = container?.copyIds?.toSet().orEmpty()
        snapshot.copyRows().filterNot { it.copy.id in held }
    }
    val visibleRows = remember(rows, query, source) {
        val q = query.trim().lowercase()
        rows.asSequence()
            .filter { source == Source.Everything || it.isUnfiled }
            .filter { q.isEmpty() || it.brief.searchIndex.contains(q) }
            .sortedByDescending { it.value.cents }
            .take(120)
            .toList()
    }

    AppSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(
            title = "Add to ${container?.name ?: "container"}",
            subtitle = if (picked.isEmpty()) {
                "Pick cards to move in"
            } else {
                "${picked.size} selected"
            },
            onClose = onDismiss,
        )

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search your cards",
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Source.entries.forEach { option ->
                    ChoiceChip(
                        label = option.label,
                        selected = option == source,
                        onClick = { source = option },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (visibleRows.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 140.dp).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (source == Source.Unfiled) {
                        "Nothing unfiled. Switch to Everything to pull a card out of a binder."
                    } else {
                        "No cards match that search."
                    },
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visibleRows, key = { it.copy.id.value }) { row ->
                    PickRow(
                        row = row,
                        selected = row.copy.id in picked,
                        onToggle = {
                            picked = if (row.copy.id in picked) picked - row.copy.id else picked + row.copy.id
                        },
                    )
                }
            }
        }

        SheetActions {
            AppOutlineButton("Cancel", onDismiss, Modifier.weight(1f))
            AppButton(
                label = if (picked.isEmpty()) "Add" else "Add ${picked.size}",
                onClick = { onConfirm(picked.toList()) },
                modifier = Modifier.weight(1.4f),
                enabled = picked.isNotEmpty(),
            )
        }
    }
}

/**
 * A selectable card row. Selection is a ring around the row rather than a checkbox: the
 * rows are already dense, and one more control per row would crowd out the value it is
 * there to help you judge.
 *
 * The ring is drawn on a wrapper that always reserves its width, because [CardListRow]
 * paints its own opaque surface -- a tinted background behind it is simply not visible,
 * and a border that appears only when selected would nudge every row on each tap.
 */
@Composable
private fun PickRow(
    row: CopyRow,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .border(2.dp, if (selected) Ink.Accent else Color.Transparent, AppShape.Medium)
            .padding(2.dp),
    ) {
        CardListRow(
            brief = row.brief,
            subtitle = "${row.brief.setName} · ${row.locationLabel}",
            leadingBadge = row.copy.grade?.label ?: row.copy.condition.short,
            onClick = onToggle,
            trailing = {
                ValueTrailing(
                    value = row.value.display(),
                    caption = if (selected) "selected" else null,
                    captionColor = Ink.Accent,
                )
            },
        )
    }
}
