package app.pocketful.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.PriceHistory
import app.pocketful.domain.BinderId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.CopyId
import app.pocketful.domain.Location
import app.pocketful.domain.Money
import app.pocketful.ui.card.CardPlace
import app.pocketful.ui.card.CopyDetails
import app.pocketful.ui.card.OwnedCardContent
import app.pocketful.ui.components.AppSheet

/**
 * One owned card, opened from anywhere that is not its pocket: Home, the card list, the
 * trade table, a box.
 *
 * The menu itself is [OwnedCardContent], the same one a binder pocket shows; what this adds
 * is the way back to the card's pocket when it has one.
 */
@Composable
fun CopySheet(
    copyId: CopyId?,
    snapshot: CollectionSnapshot,
    history: PriceHistory,
    onDismiss: () -> Unit,
    onShowInBinder: (BinderId, Int) -> Unit,
    onSetForTrade: (CopyId, Boolean) -> Unit,
    onDelete: (CopyId) -> Unit,
    onSave: (CopyId, CopyDetails) -> Unit,
    onMarkSold: (CopyId, Money, String?) -> Unit,
) {
    var latched by remember { mutableStateOf<CopyId?>(null) }
    LaunchedEffect(copyId) { if (copyId != null) latched = copyId }

    val copy = latched?.let { snapshot.copies[it] }

    AppSheet(visible = copyId != null && copy != null, onDismiss = onDismiss) {
        if (copy == null) return@AppSheet
        OwnedCardContent(
            snapshot = snapshot,
            copy = copy,
            history = history,
            place = placeOf(snapshot, copy, onShowInBinder),
            onClose = onDismiss,
            onSetForTrade = { onSetForTrade(copy.id, it) },
            onSave = { onSave(copy.id, it) },
            onSell = { price, date -> onMarkSold(copy.id, price, date) },
            onDelete = { onDelete(copy.id) },
        )
    }
}

/** Where a copy is, in words, with the way to its pocket when it has one. */
fun placeOf(
    snapshot: CollectionSnapshot,
    copy: app.pocketful.domain.Copy,
    onShowInBinder: ((BinderId, Int) -> Unit)?,
): CardPlace = when (val location = copy.location) {
    is Location.BinderSlot -> {
        val name = snapshot.binder(location.binderId)?.name ?: "A binder"
        CardPlace(
            where = "$name · Pocket ${location.ordinal + 1}",
            onShowInBinder = onShowInBinder?.let { show -> { show(location.binderId, location.ordinal) } },
        )
    }
    is Location.InContainer -> {
        val container = snapshot.container(location.containerId)
        CardPlace(where = container?.let { "${it.name} · ${it.kind.label.lowercase()}" } ?: "A container")
    }
    Location.Unassigned -> CardPlace(where = "Unfiled")
    is Location.AtGrading -> CardPlace(where = "At ${location.company.name} for grading")
    is Location.Lent -> CardPlace(where = "Lent to ${location.toWhom}")
}
