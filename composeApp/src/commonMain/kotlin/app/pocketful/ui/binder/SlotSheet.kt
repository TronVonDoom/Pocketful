package app.pocketful.ui.binder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pocketful.domain.Binder
import app.pocketful.domain.BinderId
import app.pocketful.domain.CardBrief
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Condition
import app.pocketful.domain.Copy
import app.pocketful.domain.CopyId
import app.pocketful.domain.Edition
import app.pocketful.domain.Finish
import app.pocketful.domain.Grade
import app.pocketful.domain.GradingCompany
import app.pocketful.domain.Location
import app.pocketful.domain.Money
import app.pocketful.domain.PokemonType
import app.pocketful.domain.SlotContent
import app.pocketful.domain.Supertype
import app.pocketful.domain.allBriefs
import app.pocketful.domain.brief
import app.pocketful.domain.search
import app.pocketful.data.SearchHit
import app.pocketful.data.TcgDex
import app.pocketful.state.CardLookup
import app.pocketful.state.CollectionStore
import app.pocketful.state.display
import app.pocketful.state.filterPriceInput
import app.pocketful.state.toMoneyOrNull
import app.pocketful.state.toPriceInput
import app.pocketful.state.rememberCardLookup
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.AppTextField
import app.pocketful.ui.components.ButtonTone
import app.pocketful.ui.components.CardHero
import app.pocketful.ui.components.CardListRow
import app.pocketful.ui.components.CatalogCardRow
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.Hairline
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SegmentedControl
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.ToggleSwitch
import app.pocketful.ui.components.TradeToggleRow
import app.pocketful.ui.components.ValueTrailing
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.label
import kotlinx.coroutines.launch

/** Which pocket the sheet is acting on. */
data class SlotTarget(val binderId: BinderId, val ordinal: Int)

private sealed interface Step {
    /** What is already in the pocket. */
    data object Detail : Step

    /** Pick a card to put in it. */
    data object Browse : Step

    /** Record the physical details of a card being added. */
    data class Acquire(val brief: CardBrief) : Step

    /** Change the details of a copy already in the pocket. */
    data class EditCopy(val copyId: CopyId) : Step

    /** Add a card the bundled catalog does not have. */
    data object CreateCard : Step
}

private enum class Intent(val label: String) { Own("I own it"), Want("I want it") }

/**
 * Everything that happens to one pocket.
 *
 * A pocket has more states than a list row does -- empty, owned, wanted, deliberately
 * blank -- and each one leads somewhere different. Rather than four separate sheets that
 * each get one third of the shared behaviour, this is one sheet with an explicit [Step],
 * so "I got the card I was hunting" is a step transition instead of a dismissal followed
 * by the user finding the pocket again.
 */
@Composable
fun SlotSheet(
    target: SlotTarget?,
    store: CollectionStore,
    catalog: TcgDex,
    onDismiss: () -> Unit,
) {
    // Latched so the sheet still has something to draw while it animates out.
    var latched by remember { mutableStateOf<SlotTarget?>(null) }
    var step by remember { mutableStateOf<Step>(Step.Browse) }

    // Its own query state, but the app's one API client: two search boxes are correct,
    // two copies of the 218-set index are not.
    val lookup = rememberCardLookup(catalog)
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        latched = target
        val slot = store.snapshot.binder(target.binderId)?.paddedSlots?.getOrNull(target.ordinal)
        step = when (slot) {
            is SlotContent.Filled, is SlotContent.Wanted, is SlotContent.Spacer -> Step.Detail
            else -> Step.Browse
        }
    }

    val active = latched
    val snapshot = store.snapshot
    val binder = active?.let { snapshot.binder(it.binderId) }

    AppSheet(visible = target != null && binder != null, onDismiss = onDismiss) {
        if (active == null || binder == null) return@AppSheet
        val ordinal = active.ordinal
        val slot = binder.paddedSlots.getOrNull(ordinal) ?: SlotContent.Empty

        when (val current = step) {
            Step.Detail -> SlotDetailStep(
                binder = binder,
                ordinal = ordinal,
                slot = slot,
                snapshot = snapshot,
                onClose = onDismiss,
                onSetForTrade = { copyId, forTrade -> store.setForTrade(copyId, forTrade) },
                onReplace = { step = Step.Browse },
                onEditCopy = { step = Step.EditCopy(it) },
                onAcquireWanted = { step = Step.Acquire(it) },
                onClear = {
                    store.clearSlot(binder.id, ordinal)
                    onDismiss()
                },
                onDeleteCopy = { copyId ->
                    store.deleteCopy(copyId)
                    onDismiss()
                },
            )

            Step.Browse -> BrowseStep(
                binder = binder,
                ordinal = ordinal,
                snapshot = snapshot,
                lookup = lookup,
                importing = importing,
                onClose = onDismiss,
                // Importing is the only thing in this sheet that can fail or take time.
                // It runs here rather than inside the browse step so a card that is
                // half-fetched when the user backs out does not leave a dangling job
                // writing into a screen that has gone.
                onPickRemote = { hit, intent ->
                    importing = true
                    scope.launch {
                        val card = lookup.fetch(hit).getOrNull()
                        importing = false
                        if (card != null) {
                            val variantId = store.importRemoteCard(card, Finish.NON_HOLO)
                            val brief = store.snapshot.brief(variantId)
                            when {
                                brief == null -> Unit
                                intent == Intent.Own -> step = Step.Acquire(brief)
                                else -> {
                                    store.markWanted(binder.id, ordinal, variantId)
                                    onDismiss()
                                }
                            }
                        }
                    }
                },
                onPickOwned = { step = Step.Acquire(it) },
                onPickWanted = { brief ->
                    store.markWanted(binder.id, ordinal, brief.variantId)
                    onDismiss()
                },
                onPlaceExisting = { copyId ->
                    store.placeCopy(binder.id, ordinal, copyId)
                    onDismiss()
                },
                onCreateCard = { step = Step.CreateCard },
                onSpacer = {
                    store.setSpacer(binder.id, ordinal, null)
                    onDismiss()
                },
            )

            is Step.Acquire -> CopyDetailsStep(
                brief = current.brief,
                existing = null,
                title = "Add to pocket ${ordinal + 1}",
                confirmLabel = "Add to binder",
                onBack = { step = Step.Browse },
                onClose = onDismiss,
                onConfirm = { condition, paid, grade, notes ->
                    store.addCopyToSlot(
                        binderId = binder.id,
                        ordinal = ordinal,
                        variantId = current.brief.variantId,
                        condition = condition,
                        acquiredPrice = paid,
                        grade = grade,
                        notes = notes,
                    )
                    onDismiss()
                },
            )

            is Step.EditCopy -> {
                val copy = snapshot.copies[current.copyId]
                val brief = copy?.let { snapshot.brief(it.variantId) }
                if (copy == null || brief == null) {
                    MissingRecord(
                        message = "That card is no longer in your collection.",
                        onBack = { step = Step.Detail },
                        onClose = onDismiss,
                    )
                } else {
                    CopyDetailsStep(
                        brief = brief,
                        existing = copy,
                        title = "Edit card",
                        confirmLabel = "Save changes",
                        onBack = { step = Step.Detail },
                        onClose = onDismiss,
                        onConfirm = { condition, paid, grade, notes ->
                            store.updateCopy(copy.id, condition, paid, grade, notes)
                            step = Step.Detail
                        },
                    )
                }
            }

            Step.CreateCard -> CreateCardStep(
                onBack = { step = Step.Browse },
                onClose = onDismiss,
                onCreate = { draft ->
                    val variantId = store.addCatalogCard(
                        name = draft.name,
                        setName = draft.setName,
                        number = draft.number,
                        setTotal = draft.setTotal,
                        type = draft.type,
                        finish = draft.finish,
                        edition = Edition.UNLIMITED,
                        rarity = draft.rarity,
                        marketValue = draft.marketValue,
                        supertype = draft.supertype,
                    )
                    val brief = store.snapshot.brief(variantId)
                    step = if (brief != null) Step.Acquire(brief) else Step.Browse
                },
            )
        }
    }
}

/** Shown when a step outlives the record it was opened for -- deleted from elsewhere. */
@Composable
private fun ColumnScope.MissingRecord(message: String, onBack: () -> Unit, onClose: () -> Unit) {
    SheetHeader(title = "Not found", onClose = onClose)
    SheetBody(scrollable = false) {
        Text(message, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
    SheetActions { AppButton("Back", onBack, Modifier.weight(1f)) }
}

// ------------------------------------------------------------------- detail

@Composable
private fun ColumnScope.SlotDetailStep(
    binder: Binder,
    ordinal: Int,
    slot: SlotContent,
    snapshot: CollectionSnapshot,
    onClose: () -> Unit,
    onSetForTrade: (CopyId, Boolean) -> Unit,
    onReplace: () -> Unit,
    onEditCopy: (CopyId) -> Unit,
    onAcquireWanted: (CardBrief) -> Unit,
    onClear: () -> Unit,
    onDeleteCopy: (CopyId) -> Unit,
) {
    SheetHeader(
        title = "Pocket ${ordinal + 1}",
        subtitle = pocketLocationLabel(binder, ordinal),
        onClose = onClose,
    )

    when (slot) {
        is SlotContent.Filled -> {
            val copy = snapshot.copies[slot.copyId]
            val brief = copy?.let { snapshot.brief(it.variantId) }
            if (copy == null || brief == null) {
                SheetBody(scrollable = false) {
                    Text(
                        text = "This pocket points at a card that is no longer in your collection.",
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                SheetActions { AppButton("Empty the pocket", onClear, Modifier.weight(1f)) }
                return
            }

            val value = snapshot.valueOf(copy)
            val paid = copy.acquiredPrice
            val gain = paid?.let { value - it }

            SheetBody {
                CardHero(brief = brief, valueLabel = value.format(), caption = brief.finish.label)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Condition", copy.condition.label)
                    copy.grade?.let { DetailRow("Grade", it.label + (it.certNumber?.let { c -> " · $c" } ?: "")) }
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

                Hairline()

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppOutlineButton(
                        label = "Edit card details",
                        onClick = { onEditCopy(copy.id) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = AppIcons.Edit,
                    )
                    AppOutlineButton(
                        label = "Swap for another card",
                        onClick = onReplace,
                        modifier = Modifier.fillMaxWidth(),
                        icon = AppIcons.Cards,
                    )
                }
            }

            SheetActions {
                AppOutlineButton("Take out", onClear, Modifier.weight(1f))
                AppButton(
                    label = "Delete copy",
                    onClick = { onDeleteCopy(copy.id) },
                    modifier = Modifier.weight(1f),
                    tone = ButtonTone.Danger,
                    icon = AppIcons.Trash,
                )
            }
        }

        is SlotContent.Wanted -> {
            val brief = snapshot.brief(slot.variantId)
            if (brief == null) {
                SheetBody(scrollable = false) {
                    Text(
                        text = "This want points at a card that is no longer in the catalog.",
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                SheetActions { AppButton("Empty the pocket", onClear, Modifier.weight(1f)) }
                return
            }

            val target = slot.targetPrice ?: brief.marketValue
            SheetBody {
                CardHero(brief = brief, valueLabel = target.format(), caption = "Hunting")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Market value", brief.marketValue.format())
                    DetailRow("Counted toward", "cost to complete", valueColor = Ink.TextTertiary)
                    brief.rarity?.let { DetailRow("Rarity", it) }
                }
            }
            SheetActions {
                AppOutlineButton("Clear", onClear, Modifier.weight(1f))
                AppButton(
                    label = "I got it",
                    onClick = { onAcquireWanted(brief) },
                    modifier = Modifier.weight(1.3f),
                    icon = AppIcons.Check,
                )
            }
        }

        is SlotContent.Spacer -> {
            SheetBody(scrollable = false) {
                Text(
                    text = slot.label?.let { "Deliberately blank, labelled \"$it\"." }
                        ?: "This pocket is deliberately blank. Reflowing the binder moves it but never fills it.",
                    color = Ink.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            SheetActions {
                AppOutlineButton("Clear", onClear, Modifier.weight(1f))
                AppButton("Put a card here", onReplace, Modifier.weight(1.3f), icon = AppIcons.Plus)
            }
        }

        SlotContent.Empty -> {
            SheetActions { AppButton("Add a card", onReplace, Modifier.weight(1f), icon = AppIcons.Plus) }
        }
    }
}

// ------------------------------------------------------------------- browse

@Composable
private fun ColumnScope.BrowseStep(
    binder: Binder,
    ordinal: Int,
    snapshot: CollectionSnapshot,
    lookup: CardLookup,
    importing: Boolean,
    onClose: () -> Unit,
    onPickRemote: (SearchHit, Intent) -> Unit,
    onPickOwned: (CardBrief) -> Unit,
    onPickWanted: (CardBrief) -> Unit,
    onPlaceExisting: (CopyId) -> Unit,
    onCreateCard: () -> Unit,
    onSpacer: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var intent by remember { mutableStateOf(Intent.Own) }

    // The one search box drives both catalogs. Typing twice to look in two places for the
    // same card would be the app admitting it has two catalogs, which is not the user's
    // problem -- local results appear instantly and online ones arrive a moment later.
    LaunchedEffect(query) { lookup.onQueryChanged(query) }

    val catalog = remember(snapshot) { snapshot.allBriefs() }
    val results = remember(catalog, query) { catalog.search(query, limit = 30) }
    val ownedCounts = remember(snapshot) {
        snapshot.copies.values.groupingBy { it.variantId }.eachCount()
    }
    val unfiled = remember(snapshot) {
        snapshot.copies.values
            .filter { it.location == Location.Unassigned }
            .mapNotNull { copy -> snapshot.brief(copy.variantId)?.let { copy to it } }
    }

    SheetHeader(
        title = "Pocket ${ordinal + 1}",
        subtitle = pocketLocationLabel(binder, ordinal),
        onClose = onClose,
    )

    SheetBody {
        SegmentedControl(
            options = Intent.entries.toList(),
            selected = intent,
            onSelect = { intent = it },
            label = { it.label },
        )

        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search cards, sets, numbers",
        )

        if (query.isBlank() && unfiled.isNotEmpty() && intent == Intent.Own) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("In your collection, unfiled · ${unfiled.size}")
                unfiled.take(6).forEach { (copy, brief) ->
                    CardListRow(
                        brief = brief,
                        subtitle = "${brief.setName} · ${copy.condition.short}",
                        onClick = { onPlaceExisting(copy.id) },
                        trailing = { ValueTrailing(snapshot.valueOf(copy).display()) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                when {
                    query.isBlank() -> "Catalog · ${catalog.size} cards"
                    results.size == 1 -> "1 match"
                    else -> "${results.size} matches"
                },
            )
            if (results.isEmpty()) {
                EmptyState(
                    icon = AppIcons.Search,
                    title = "No matches",
                    message = "Nothing in the catalog matches \"$query\". You can add it by hand.",
                    action = { AppButton("Add this card", onCreateCard, icon = AppIcons.Plus) },
                )
            } else {
                results.forEach { brief ->
                    val ownedCount = ownedCounts[brief.variantId] ?: 0
                    CardListRow(
                        brief = brief,
                        leadingBadge = if (ownedCount > 0) "own $ownedCount" else null,
                        onClick = {
                            if (intent == Intent.Own) onPickOwned(brief) else onPickWanted(brief)
                        },
                        trailing = {
                            ValueTrailing(
                                value = brief.marketValue.display(),
                                valueColor = Ink.Gold,
                            )
                        },
                    )
                }
            }
        }

        if (query.trim().length >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = when {
                        lookup.searching -> "Searching the full catalog…"
                        lookup.results.isEmpty() -> "Full catalog"
                        else -> "Full catalog · ${lookup.results.size} found"
                    },
                )

                lookup.error?.let { message ->
                    Text(message, color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)
                }

                if (importing) {
                    Text(
                        text = "Fetching card details…",
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Cards already in the local catalog are filtered out rather than shown
                // greyed: they are listed above under the local results, and a row that
                // appears twice in one sheet reads as a bug.
                //
                // Matched on name and printed number rather than on id. Ids only line up
                // for cards that came from this catalog in the first place, so an id
                // check would let every card the app shipped with appear twice.
                val known = remember(catalog) {
                    catalog.map { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" }.toSet()
                }
                lookup.results
                    .filterNot { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" in known }
                    .forEach { hit ->
                        CatalogCardRow(
                            hit = hit,
                            onClick = { onPickRemote(hit, intent) },
                            enabled = !importing,
                            trailingIcon = AppIcons.Plus,
                        )
                    }

                if (!lookup.searching && lookup.results.isEmpty() && lookup.error == null) {
                    Text(
                        text = "Nothing in the online catalog matches that either.",
                        color = Ink.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Hairline()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlineButton(
                label = "Add a card the catalog is missing",
                onClick = onCreateCard,
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Plus,
            )
            AppOutlineButton(
                label = "Leave this pocket deliberately blank",
                onClick = onSpacer,
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Minus,
            )
        }
    }
}

// ------------------------------------------------------------- copy details

@Composable
private fun ColumnScope.CopyDetailsStep(
    brief: CardBrief,
    existing: Copy?,
    title: String,
    confirmLabel: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onConfirm: (Condition, Money?, Grade?, String?) -> Unit,
) {
    var condition by remember(existing?.id) { mutableStateOf(existing?.condition ?: Condition.NEAR_MINT) }
    var paid by remember(existing?.id) { mutableStateOf(existing?.acquiredPrice?.toPriceInput() ?: "") }
    var graded by remember(existing?.id) { mutableStateOf(existing?.grade != null) }
    var company by remember(existing?.id) { mutableStateOf(existing?.grade?.company ?: GradingCompany.PSA) }
    var score by remember(existing?.id) { mutableStateOf(existing?.grade?.score ?: "") }
    var cert by remember(existing?.id) { mutableStateOf(existing?.grade?.certNumber ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }

    SheetHeader(title = title, onClose = onClose)

    SheetBody {
        CardHero(brief = brief, valueLabel = brief.marketValue.format(), caption = brief.finish.label)

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
                text = condition.label + " · " + conditionMultiplierLabel(condition),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Graded", color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Slabbed copies are valued at the raw price until graded pricing lands.",
                        color = Ink.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ToggleSwitch(checked = graded, onCheckedChange = { graded = it })
            }

            if (graded) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GradingCompany.entries.forEach { option ->
                        ChoiceChip(
                            label = option.name,
                            selected = option == company,
                            onClick = { company = option },
                            accent = Ink.Gold,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = score,
                        onValueChange = { score = it },
                        label = "Score",
                        placeholder = "10",
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = cert,
                        onValueChange = { cert = it },
                        label = "Cert number",
                        placeholder = "optional",
                        modifier = Modifier.weight(1.6f),
                    )
                }
            }
        }

        AppTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes",
            placeholder = "Where it came from, anything worth remembering",
            singleLine = false,
        )
    }

    SheetActions {
        AppOutlineButton("Back", onBack, Modifier.weight(1f))
        AppButton(
            label = confirmLabel,
            onClick = {
                val grade = if (graded && score.isNotBlank()) {
                    Grade(company, score.trim(), cert.trim().takeIf { it.isNotBlank() })
                } else {
                    null
                }
                onConfirm(condition, paid.toMoneyOrNull(), grade, notes)
            },
            modifier = Modifier.weight(1.4f),
        )
    }
}

// -------------------------------------------------------------- create card

private data class CardDraft(
    val name: String,
    val setName: String,
    val number: String,
    val setTotal: String?,
    val rarity: String?,
    val type: PokemonType?,
    val finish: Finish,
    val supertype: Supertype,
    val marketValue: Money,
)

@Composable
private fun ColumnScope.CreateCardStep(
    onBack: () -> Unit,
    onClose: () -> Unit,
    onCreate: (CardDraft) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var setName by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var setTotal by remember { mutableStateOf("") }
    var rarity by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<PokemonType?>(null) }
    var finish by remember { mutableStateOf(Finish.NON_HOLO) }
    var supertype by remember { mutableStateOf(Supertype.POKEMON) }

    SheetHeader(
        title = "Add a card",
        subtitle = "Goes into your catalog and can be filed again later",
        onClose = onClose,
    )

    SheetBody {
        AppTextField(name, { name = it }, label = "Card name", placeholder = "Charizard")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = setName,
                onValueChange = { setName = it },
                label = "Set",
                placeholder = "Base Set",
                modifier = Modifier.weight(1.6f),
            )
            AppTextField(
                value = number,
                onValueChange = { number = it },
                label = "Number",
                placeholder = "4",
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppTextField(
                value = setTotal,
                onValueChange = { setTotal = it },
                label = "Set size",
                placeholder = "102",
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = rarity,
                onValueChange = { rarity = it },
                label = "Rarity",
                placeholder = "Rare Holo",
                modifier = Modifier.weight(1.6f),
            )
        }

        AppTextField(
            value = value,
            onValueChange = { value = it.filterPriceInput() },
            label = "Market value",
            placeholder = "0.00",
            prefix = "$",
            keyboardType = KeyboardType.Decimal,
        )

        Column {
            FieldLabel("Card kind")
            Spacer(Modifier.height(8.dp))
            SegmentedControl(
                options = Supertype.entries.toList(),
                selected = supertype,
                onSelect = { supertype = it },
                label = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            )
        }

        Column {
            FieldLabel("Type")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChoiceChip("None", type == null, { type = null })
                PokemonType.entries.forEach { option ->
                    ChoiceChip(
                        label = option.label,
                        selected = option == type,
                        onClick = { type = option },
                    )
                }
            }
        }

        Column {
            FieldLabel("Finish")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Finish.entries.forEach { option ->
                    ChoiceChip(
                        label = option.label,
                        selected = option == finish,
                        onClick = { finish = option },
                    )
                }
            }
        }
    }

    SheetActions {
        AppOutlineButton("Back", onBack, Modifier.weight(1f))
        AppButton(
            label = "Create card",
            onClick = {
                onCreate(
                    CardDraft(
                        name = name,
                        setName = setName,
                        number = number,
                        setTotal = setTotal.takeIf { it.isNotBlank() },
                        rarity = rarity.takeIf { it.isNotBlank() },
                        type = type,
                        finish = finish,
                        supertype = supertype,
                        marketValue = value.toMoneyOrNull() ?: Money.ZERO,
                    ),
                )
            },
            modifier = Modifier.weight(1.4f),
            enabled = name.isNotBlank(),
        )
    }
}

// ------------------------------------------------------------------ pieces

private fun pocketLocationLabel(binder: Binder, ordinal: Int): String {
    val location = binder.layout.locate(ordinal)
    return buildString {
        append(binder.name)
        append(" · page ")
        append(location.faceIndex + 1)
        append(" · row ")
        append(location.row + 1)
        append(", column ")
        append(location.col + 1)
    }
}

private fun conditionMultiplierLabel(condition: Condition): String = when {
    condition.multiplier > 1.0 -> "valued above market"
    condition.multiplier == 1.0 -> "valued at market"
    else -> "valued at ${(condition.multiplier * 100).toInt()}% of market"
}
