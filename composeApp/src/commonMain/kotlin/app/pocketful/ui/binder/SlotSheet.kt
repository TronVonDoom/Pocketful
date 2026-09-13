package app.pocketful.ui.binder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketful.data.CardImport
import app.pocketful.data.PriceHistory
import app.pocketful.data.CatalogSet
import app.pocketful.data.SearchHit
import app.pocketful.data.CardCatalog
import app.pocketful.domain.Binder
import app.pocketful.domain.CardBrief
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Copy
import app.pocketful.domain.CopyId
import app.pocketful.domain.Edition
import app.pocketful.domain.Finish
import app.pocketful.domain.Location
import app.pocketful.domain.Money
import app.pocketful.domain.PokemonType
import app.pocketful.domain.PrintingId
import app.pocketful.domain.SlotContent
import app.pocketful.domain.Supertype
import app.pocketful.domain.TcgGame
import app.pocketful.domain.allBriefs
import app.pocketful.domain.brief
import app.pocketful.domain.byPrinting
import app.pocketful.domain.search
import app.pocketful.domain.variantBriefs
import app.pocketful.state.CardLookup
import app.pocketful.state.CatalogBrowser
import app.pocketful.state.CollectionStore
import app.pocketful.state.display
import app.pocketful.state.displayOrDash
import app.pocketful.state.filterPriceInput
import app.pocketful.state.rememberCardLookup
import app.pocketful.state.toMoneyOrNull
import app.pocketful.state.toPriceInput
import app.pocketful.ui.card.CardPlace
import app.pocketful.ui.card.CopyFormFields
import app.pocketful.ui.card.OwnedCardContent
import app.pocketful.ui.card.VariantHistory
import app.pocketful.ui.card.VariationMark
import app.pocketful.ui.card.VariationsSection
import app.pocketful.ui.card.rememberCopyForm
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.AppTextField
import app.pocketful.ui.components.CardHero
import app.pocketful.ui.components.CardListRow
import app.pocketful.ui.components.CatalogCardRow
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.Hairline
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SegmentedControl
import app.pocketful.ui.components.SheetActions
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.ValueTrailing
import app.pocketful.ui.components.tappable
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.label
import kotlinx.coroutines.launch

/** Which pocket the sheet is acting on. */
data class SlotTarget(val binderId: app.pocketful.domain.BinderId, val ordinal: Int)

private sealed interface Step {
    /** What is already in the pocket. */
    data object Detail : Step

    /** Pick a card to put in it. */
    data object Browse : Step

    /** Record the physical details of a card being added. */
    data class Acquire(val brief: CardBrief) : Step

    /** Add a card the bundled catalog does not have. */
    data object CreateCard : Step
}

private enum class Intent(val label: String, val accent: Color) {
    Own("I own it", Ink.Gain),
    Want("I want it", Ink.Wanted),
}

/** How the picker finds a card: by typing, or by walking the catalog game, era and set. */
private enum class Finder(val label: String) { Search("Search"), Browse("Browse sets") }

/**
 * How wide a search casts. [Mine] keeps only cards the collection already has a copy of,
 * [All] adds everything the catalogs know about.
 */
private enum class Scope(val label: String) { All("All cards"), Mine("My collection") }

/** How many unfiled cards the picker lists before it asks to show the rest. */
private const val UNFILED_PREVIEW = 6

/**
 * Everything that happens to one pocket.
 *
 * What is *in* the pocket uses the same card menu as everywhere else in the app -- an owned
 * card is [OwnedCardContent], with the pocket adding "take out" and "swap"; a wanted card
 * is the same layout with the hunt's actions. What is particular to a pocket is getting a
 * card into it, which is [Step.Browse]: by search, or by browsing the catalog game by game
 * and set by set, so a pocket can be filled without wading through every loose card.
 */
@Composable
fun SlotSheet(
    target: SlotTarget?,
    store: CollectionStore,
    catalog: CardCatalog,
    browser: CatalogBrowser,
    history: PriceHistory,
    onDismiss: () -> Unit,
) {
    // Latched so the sheet still has something to draw while it animates out.
    var latched by remember { mutableStateOf<SlotTarget?>(null) }
    var step by remember { mutableStateOf<Step>(Step.Browse) }

    // Its own query state, but the app's one API client.
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
        val where = "Pocket ${ordinal + 1} · ${pocketLocationLabel(binder, ordinal)}"

        when (val current = step) {
            Step.Detail -> when (slot) {
                is SlotContent.Filled -> {
                    val copy = snapshot.copies[slot.copyId]
                    if (copy == null || snapshot.brief(copy.variantId) == null) {
                        MissingRecord(
                            message = "This pocket points at a card that is no longer in your collection.",
                            actionLabel = "Empty the pocket",
                            onAction = {
                                store.clearSlot(binder.id, ordinal)
                                onDismiss()
                            },
                            onClose = onDismiss,
                        )
                    } else {
                        OwnedCardContent(
                            snapshot = snapshot,
                            copy = copy,
                            history = history,
                            place = CardPlace(
                                where = where,
                                onTakeOut = {
                                    store.clearSlot(binder.id, ordinal)
                                    onDismiss()
                                },
                                onSwap = { step = Step.Browse },
                            ),
                            onClose = onDismiss,
                            onSetForTrade = { store.setForTrade(copy.id, it) },
                            onSave = { details ->
                                store.editCopy(
                                    copyId = copy.id,
                                    variantId = details.variantId,
                                    condition = details.condition,
                                    acquiredPrice = details.paid,
                                    acquiredDate = details.acquiredDate,
                                    grade = details.grade,
                                    valueOverride = details.valueOverride,
                                    notes = details.notes,
                                )
                            },
                            onSell = { price, date ->
                                store.markSold(copy.id, price, date)
                                onDismiss()
                            },
                            onDelete = {
                                store.deleteCopy(copy.id)
                                onDismiss()
                            },
                        )
                    }
                }

                is SlotContent.Wanted -> {
                    val brief = snapshot.brief(slot.variantId)
                    if (brief == null) {
                        MissingRecord(
                            message = "This want points at a card that is no longer in the catalog.",
                            actionLabel = "Empty the pocket",
                            onAction = {
                                store.clearSlot(binder.id, ordinal)
                                onDismiss()
                            },
                            onClose = onDismiss,
                        )
                    } else {
                        WantedCardContent(
                            snapshot = snapshot,
                            brief = brief,
                            targetPrice = slot.targetPrice,
                            where = where,
                            history = history,
                            onClose = onDismiss,
                            // Re-aims the want at another press run of the same card, keeping
                            // whatever price it was being hunted at.
                            onChangeWanted = { chosen -> store.markWanted(binder.id, ordinal, chosen.variantId, slot.targetPrice) },
                            onSetTarget = { price -> store.markWanted(binder.id, ordinal, slot.variantId, price) },
                            onGotIt = { step = Step.Acquire(brief) },
                            onPlaceUnfiled = { copyId ->
                                store.placeCopy(binder.id, ordinal, copyId)
                                onDismiss()
                            },
                            onSwap = { step = Step.Browse },
                            onClear = {
                                store.clearSlot(binder.id, ordinal)
                                onDismiss()
                            },
                        )
                    }
                }

                is SlotContent.Spacer -> {
                    SheetHeader(title = "Pocket ${ordinal + 1}", subtitle = pocketLocationLabel(binder, ordinal), onClose = onDismiss)
                    SheetBody(scrollable = false) {
                        Text(
                            text = slot.label?.let { "Deliberately blank, labelled \"$it\"." }
                                ?: "This pocket is deliberately blank. Reflowing the binder moves it but never fills it.",
                            color = Ink.TextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    SheetActions {
                        AppOutlineButton("Clear", {
                            store.clearSlot(binder.id, ordinal)
                            onDismiss()
                        }, Modifier.weight(1f))
                        AppButton("Put a card here", { step = Step.Browse }, Modifier.weight(1.3f), icon = AppIcons.Plus)
                    }
                }

                SlotContent.Empty -> {
                    SheetHeader(title = "Pocket ${ordinal + 1}", subtitle = pocketLocationLabel(binder, ordinal), onClose = onDismiss)
                    SheetActions { AppButton("Add a card", { step = Step.Browse }, Modifier.weight(1f), icon = AppIcons.Plus) }
                }
            }

            Step.Browse -> BrowseStep(
                binder = binder,
                ordinal = ordinal,
                snapshot = snapshot,
                lookup = lookup,
                browser = browser,
                importing = importing,
                onClose = onDismiss,
                // Importing is the only thing in this sheet that can fail or take time. It
                // runs here rather than inside the browse step so a card half-fetched when
                // the user backs out does not leave a job writing into a screen that is gone.
                onPickRemote = { hit, intent ->
                    importing = true
                    scope.launch {
                        val card = lookup.fetch(hit).getOrNull()
                        importing = false
                        val variantId = card?.let {
                            store.importCatalogRows(CardImport.rowsFor(it, catalog), CardImport.preferredVariant(it, Finish.NON_HOLO, catalog))
                        }
                        if (variantId != null) {
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

            is Step.Acquire -> AcquireStep(
                snapshot = snapshot,
                brief = snapshot.variantBriefs(current.brief.variantId).firstOrNull { it.variantId == current.brief.variantId } ?: current.brief,
                ordinal = ordinal,
                history = history,
                onBack = { step = if (slot is SlotContent.Empty) Step.Browse else Step.Detail },
                onClose = onDismiss,
                onPlaceExisting = { copyId ->
                    store.placeCopy(binder.id, ordinal, copyId)
                    onDismiss()
                },
                onConfirm = { details ->
                    val copyId = store.addCopyToSlot(
                        binderId = binder.id,
                        ordinal = ordinal,
                        variantId = details.variantId,
                        condition = details.condition,
                        acquiredPrice = details.paid,
                        grade = details.grade,
                        notes = details.notes,
                    )
                    store.updateCopyMoney(copyId, details.paid, details.acquiredDate, details.valueOverride)
                    onDismiss()
                },
            )

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

/** Shown when a pocket outlives the record it points at -- deleted from elsewhere. */
@Composable
private fun ColumnScope.MissingRecord(message: String, actionLabel: String, onAction: () -> Unit, onClose: () -> Unit) {
    SheetHeader(title = "Not found", onClose = onClose)
    SheetBody(scrollable = false) {
        Text(message, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
    SheetActions { AppButton(actionLabel, onAction, Modifier.weight(1f)) }
}

/** Copies of a printing that are not filed anywhere, plainest first. */
private fun CollectionSnapshot.unfiledCopiesOf(printingId: PrintingId): List<Copy> =
    copies.values
        .filter { it.location == Location.Unassigned && variants[it.variantId]?.printingId == printingId }
        .sortedBy { listOf(it.acquiredPrice, it.grade, it.notes).count { field -> field != null } }

// ------------------------------------------------------------------- wanted

/**
 * A pocket held open for a card: the same card menu, with the hunt's actions.
 *
 * Every variation is listed with its price and move, and tapping one re-aims the want at
 * it. The price it is being hunted at can be set, and "I got it" goes straight to recording
 * the copy -- or, when a copy of it is already lying unfiled, to placing that one.
 */
@Composable
private fun ColumnScope.WantedCardContent(
    snapshot: CollectionSnapshot,
    brief: CardBrief,
    targetPrice: Money?,
    where: String,
    history: PriceHistory,
    onClose: () -> Unit,
    onChangeWanted: (CardBrief) -> Unit,
    onSetTarget: (Money?) -> Unit,
    onGotIt: () -> Unit,
    onPlaceUnfiled: (CopyId) -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
) {
    val variations = remember(snapshot.variants, snapshot.prices, brief.variantId) { snapshot.variantBriefs(brief.variantId) }
    val unfiled = remember(snapshot.copies, brief.printingId) { snapshot.unfiledCopiesOf(brief.printingId) }
    var target by remember(brief.variantId) { mutableStateOf(targetPrice?.toPriceInput().orEmpty()) }

    SheetHeader(title = brief.name, subtitle = "Wanted · $where", onClose = onClose)

    SheetBody {
        CardHero(
            brief = brief,
            valueLabel = (targetPrice ?: brief.marketValue).displayOrDash(brief.currency),
            caption = brief.badge ?: brief.finish.label,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailRow("Market value", brief.marketValue.displayOrDash(brief.currency))
            DetailRow("Hunting at", targetPrice?.display() ?: "market price", valueColor = Ink.Wanted)
            DetailRow("Counted toward", "cost to complete", valueColor = Ink.TextTertiary)
            brief.rarity?.let { DetailRow("Rarity", it) }
        }

        VariationsSection(
            options = variations,
            selected = brief.variantId,
            onSelect = onChangeWanted,
            label = "Which one you are after",
            trailing = { option -> if (option.variantId == brief.variantId) VariationMark("Hunting", Ink.Wanted) },
        )

        VariantHistory(history, brief)

        AppTextField(
            value = target,
            onValueChange = {
                target = it.filterPriceInput()
                onSetTarget(target.toMoneyOrNull())
            },
            label = "Your target price (blank uses the market)",
            placeholder = "market",
            prefix = "$",
            keyboardType = KeyboardType.Decimal,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Where it is")
            DetailRow("Held open", where)
            if (unfiled.isNotEmpty()) {
                Text(
                    text = "You have ${unfiled.size} of this card unfiled. Put one straight into this pocket:",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
                AppOutlineButton(
                    label = "Place your unfiled copy",
                    onClick = { onPlaceUnfiled(unfiled.first().id) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcons.Move,
                )
            }
        }
    }

    SheetActions {
        AppOutlineButton("Clear", onClear, Modifier.weight(1f))
        AppOutlineButton("Swap", onSwap, Modifier.weight(1f))
        AppButton("I got it", onGotIt, Modifier.weight(1.3f), icon = AppIcons.Check)
    }
}

// ------------------------------------------------------------------- acquire

/**
 * Recording a copy into this pocket: the same card layout, with the shared copy form.
 *
 * When a copy of this card is already lying unfiled, the first thing offered is placing that
 * one -- filing a card you had put down is not the same event as buying another.
 */
@Composable
private fun ColumnScope.AcquireStep(
    snapshot: CollectionSnapshot,
    brief: CardBrief,
    ordinal: Int,
    history: PriceHistory,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPlaceExisting: (CopyId) -> Unit,
    onConfirm: (app.pocketful.ui.card.CopyDetails) -> Unit,
) {
    val variations = remember(snapshot.variants, snapshot.prices, brief.variantId) { snapshot.variantBriefs(brief.variantId) }
    val form = rememberCopyForm(key = brief.variantId, copy = null, variant = brief)
    val unfiled = remember(snapshot.copies, brief.printingId) { snapshot.unfiledCopiesOf(brief.printingId) }

    SheetHeader(title = brief.name, subtitle = "Add to Pocket ${ordinal + 1}", onClose = onClose)

    SheetBody {
        CardHero(
            brief = form.variant,
            valueLabel = form.variant.marketValue.displayOrDash(form.variant.currency),
            caption = form.variant.badge ?: form.variant.finish.label,
        )

        if (unfiled.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Already yours, unfiled · ${unfiled.size}")
                unfiled.take(3).forEach { copy ->
                    val copyBrief = snapshot.brief(copy.variantId) ?: return@forEach
                    CardListRow(
                        brief = copyBrief,
                        subtitle = listOfNotNull(copyBrief.badge ?: copyBrief.finish.label, copy.condition.short, copy.grade?.label).joinToString(" · "),
                        onClick = { onPlaceExisting(copy.id) },
                        trailing = { ValueTrailing("Place", valueColor = Ink.Accent) },
                    )
                }
                Text(
                    text = "Or record a new copy below.",
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        VariationsSection(
            options = variations,
            selected = form.variant.variantId,
            onSelect = { form.variant = it },
            label = "Which variation you have",
        )

        CopyFormFields(form)

        VariantHistory(history, form.variant)
    }

    SheetActions {
        AppOutlineButton("Back", onBack, Modifier.weight(1f))
        AppButton("Add to binder", { onConfirm(form.result()) }, Modifier.weight(1.4f), icon = AppIcons.Plus)
    }
}

// ------------------------------------------------------------------- browse

@Composable
private fun ColumnScope.BrowseStep(
    binder: Binder,
    ordinal: Int,
    snapshot: CollectionSnapshot,
    lookup: CardLookup,
    browser: CatalogBrowser,
    importing: Boolean,
    onClose: () -> Unit,
    onPickRemote: (SearchHit, Intent) -> Unit,
    onPickOwned: (CardBrief) -> Unit,
    onPickWanted: (CardBrief) -> Unit,
    onPlaceExisting: (CopyId) -> Unit,
    onCreateCard: () -> Unit,
    onSpacer: () -> Unit,
) {
    LaunchedEffect(browser) { browser.load() }

    // A binder built from a set opens on that set: filling a set binder is picking from
    // its checklist, not searching the whole catalog for each card by name.
    val sourceSet = remember(browser.sets, binder.sourceSetId) {
        binder.sourceSetId?.let { id -> browser.sets.firstOrNull { it.id == id } }
    }
    var finder by remember(binder.id) { mutableStateOf(if (binder.sourceSetId != null) Finder.Browse else Finder.Search) }
    var intent by remember { mutableStateOf(Intent.Own) }

    SheetHeader(
        title = "Pocket ${ordinal + 1}",
        subtitle = pocketLocationLabel(binder, ordinal),
        onClose = onClose,
    )

    SheetBody {
        // Each side wears the colour it means everywhere else -- green for a card you have,
        // violet for one you are hunting -- because it decides what the next tap does.
        SegmentedControl(
            options = Intent.entries.toList(),
            selected = intent,
            onSelect = { intent = it },
            label = { it.label },
            accent = { it.accent },
        )
        SegmentedControl(
            options = Finder.entries.toList(),
            selected = finder,
            onSelect = { finder = it },
            label = { it.label },
        )

        when (finder) {
            Finder.Search -> SearchFinder(
                snapshot = snapshot,
                lookup = lookup,
                intent = intent,
                importing = importing,
                onPickRemote = onPickRemote,
                onPickOwned = onPickOwned,
                onPickWanted = onPickWanted,
                onPlaceExisting = onPlaceExisting,
            )

            Finder.Browse -> CatalogFinder(
                snapshot = snapshot,
                browser = browser,
                startSet = sourceSet,
                intent = intent,
                importing = importing,
                onPickRemote = onPickRemote,
            )
        }

        Hairline()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlineButton(label = "Add a missing card", onClick = onCreateCard, modifier = Modifier.weight(1f), maxLines = 2)
            AppOutlineButton(label = "Leave this pocket blank", onClick = onSpacer, modifier = Modifier.weight(1f), maxLines = 2)
        }
    }
}

/** The owned count of each printing in the collection, for "Own 2" badges. */
@Composable
private fun rememberOwnedCounts(snapshot: CollectionSnapshot): Map<PrintingId, Int> = remember(snapshot.copies, snapshot.variants) {
    snapshot.copies.values
        .mapNotNull { snapshot.variants[it.variantId]?.printingId }
        .groupingBy { it }
        .eachCount()
}

@Composable
private fun SearchFinder(
    snapshot: CollectionSnapshot,
    lookup: CardLookup,
    intent: Intent,
    importing: Boolean,
    onPickRemote: (SearchHit, Intent) -> Unit,
    onPickOwned: (CardBrief) -> Unit,
    onPickWanted: (CardBrief) -> Unit,
    onPlaceExisting: (CopyId) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(Scope.All) }
    var showAllUnfiled by remember { mutableStateOf(false) }

    LaunchedEffect(query) { lookup.onQueryChanged(query) }

    // One row per printing, not per press run; the variation is chosen on the next step.
    val catalog = remember(snapshot.variants, snapshot.prices) { snapshot.allBriefs().byPrinting() }
    val results = remember(catalog, query) { catalog.search(query, limit = 30) }
    val ownedCounts = rememberOwnedCounts(snapshot)
    val unfiled = remember(snapshot.copies, snapshot.variants) {
        snapshot.copies.values
            .filter { it.location == Location.Unassigned }
            .mapNotNull { copy -> snapshot.brief(copy.variantId)?.let { copy to it } }
            .sortedByDescending { snapshot.valueOf(it.first).cents }
    }

    val searching = query.isNotBlank()
    val mineOnly = searching && scope == Scope.Mine

    val yours = remember(unfiled, query) {
        if (query.isBlank()) {
            unfiled
        } else {
            val rank = unfiled.map { it.second }.search(query, limit = 60).withIndex()
                .associate { (index, brief) -> brief.variantId to index }
            unfiled.filter { it.second.variantId in rank }.sortedBy { rank[it.second.variantId] }
        }
    }
    val catalogRows = remember(results, mineOnly, ownedCounts) {
        if (mineOnly) results.filter { (ownedCounts[it.printingId] ?: 0) > 0 } else results
    }

    SearchField(value = query, onValueChange = { query = it }, placeholder = "Search cards, sets, numbers")

    if (searching) {
        SegmentedControl(options = Scope.entries.toList(), selected = scope, onSelect = { scope = it }, label = { it.label })
    }

    // Loose cards place the copy that already exists rather than recording a second one.
    // Capped until asked, so a collection with hundreds of bulk cards unfiled does not bury
    // the rest of this sheet under them -- search narrows them, and Browse sets skips them.
    if (intent == Intent.Own && yours.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Unfiled In Your Collection · ${yours.size}")
            val shown = if (showAllUnfiled || searching) yours.take(60) else yours.take(UNFILED_PREVIEW)
            shown.forEach { (copy, brief) ->
                CardListRow(
                    brief = brief,
                    subtitle = "${brief.setName} · ${copy.condition.short}",
                    onClick = { onPlaceExisting(copy.id) },
                    trailing = { ValueTrailing(snapshot.valueOf(copy).display()) },
                )
            }
            if (!searching && !showAllUnfiled && yours.size > UNFILED_PREVIEW) {
                AppOutlineButton(
                    label = "Show all ${yours.size} unfiled",
                    onClick = { showAllUnfiled = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (searching && catalogRows.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(if (catalogRows.size == 1) "1 Match" else "${catalogRows.size} Matches")
            catalogRows.forEach { brief ->
                val ownedCount = ownedCounts[brief.printingId] ?: 0
                CardListRow(
                    brief = brief,
                    leadingBadge = if (ownedCount > 0) "Own $ownedCount" else null,
                    onClick = { if (intent == Intent.Own) onPickOwned(brief) else onPickWanted(brief) },
                    trailing = { ValueTrailing(value = brief.marketValue.displayOrDash(brief.currency), valueColor = Ink.Gold) },
                )
            }
        }
    }

    if (!searching) {
        Text(
            text = if (intent == Intent.Own) {
                "Search for the card you are holding, or browse its set."
            } else {
                "Search for the card you are hunting, or browse its set."
            },
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (!mineOnly && query.trim().length >= 2) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = when {
                    lookup.searching -> "Searching The Full Catalog…"
                    lookup.results.isEmpty() -> "Full Catalog"
                    else -> "Full Catalog · ${lookup.results.size} Found"
                },
            )
            lookup.error?.let { Text(it, color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall) }
            if (importing) Text("Fetching card details…", color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)

            val known = remember(catalog) { catalog.map { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" }.toSet() }
            lookup.results
                .filterNot { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" in known }
                .forEach { hit ->
                    CatalogCardRow(hit = hit, onClick = { onPickRemote(hit, intent) }, enabled = !importing, trailingIcon = AppIcons.Plus)
                }
            if (!lookup.searching && lookup.results.isEmpty() && lookup.error == null) {
                Text(
                    text = if (catalogRows.isEmpty()) {
                        "No card matches \"$query\", here or online. If you are holding one anyway, add it by hand below."
                    } else {
                        "Nothing more in the online catalog."
                    },
                    color = Ink.TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Walking the catalog to a card: game, then era and set, then the set's cards.
 *
 * The same order the Search tab browses in, compressed into the sheet, with a trail at the
 * top to step back up. A card you already own is badged with how many, and one you have
 * lying unfiled says so, since placing that copy is one tap on the next step.
 */
@Composable
private fun CatalogFinder(
    snapshot: CollectionSnapshot,
    browser: CatalogBrowser,
    startSet: CatalogSet?,
    intent: Intent,
    importing: Boolean,
    onPickRemote: (SearchHit, Intent) -> Unit,
) {
    val connected = remember { TcgGame.browsable.filter { it.connected } }
    var game by remember(startSet?.id) { mutableStateOf(startSet?.let { browser.gameOfSet(it.id) }) }
    var set by remember(startSet?.id) { mutableStateOf(startSet) }
    var filter by remember(game, set?.id) { mutableStateOf("") }

    // The trail: every step back up is one tap.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceChip(label = "Games", selected = game == null, onClick = {
            game = null
            set = null
        })
        game?.let { chosen ->
            Icon(AppIcons.ChevronRight, null, Modifier.size(14.dp), tint = Ink.TextTertiary)
            ChoiceChip(label = chosen.wordmark, selected = set == null, onClick = { set = null })
        }
        set?.let { chosen ->
            Icon(AppIcons.ChevronRight, null, Modifier.size(14.dp), tint = Ink.TextTertiary)
            ChoiceChip(label = chosen.name, selected = true, onClick = {})
        }
    }

    when {
        browser.loading && browser.sets.isEmpty() -> Text("Loading the catalog…", color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)

        game == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            browser.error?.let { Text(it, color = Ink.Loss, style = MaterialTheme.typography.bodySmall) }
            connected.forEach { option ->
                val size = browser.sizeOf(option)
                BrowseRow(
                    title = option.label,
                    subtitle = size?.caption ?: option.note,
                    onClick = { game = option },
                )
            }
        }

        set == null -> {
            val chosenGame = game!!
            val groups = remember(browser.groups, chosenGame) { browser.arrange(chosenGame) }
            SearchField(value = filter, onValueChange = { filter = it }, placeholder = "Filter sets")
            val needle = filter.trim().lowercase()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { group ->
                    val sets = group.sets.filter { needle.isEmpty() || it.name.lowercase().contains(needle) || it.code.lowercase() == needle }
                    if (sets.isEmpty()) return@forEach
                    SectionHeader(group.series.name + (group.years?.let { " · $it" } ?: ""))
                    sets.forEach { option ->
                        BrowseRow(
                            title = option.name,
                            subtitle = listOfNotNull(option.code.uppercase(), option.releaseYear, option.officialCount?.let { "$it cards" }).joinToString(" · "),
                            onClick = { set = option },
                        )
                    }
                }
            }
        }

        else -> {
            val chosenSet = set!!
            var cards by remember(chosenSet.id) { mutableStateOf<List<SearchHit>?>(null) }
            LaunchedEffect(chosenSet.id) {
                cards = browser.cardsInSet(chosenSet.id).sortedWith(
                    compareBy({ it.number.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it.number }),
                )
            }
            val ownedCounts = rememberOwnedCounts(snapshot)
            val unfiledCounts = remember(snapshot.copies, snapshot.variants) {
                snapshot.copies.values
                    .filter { it.location == Location.Unassigned }
                    .mapNotNull { snapshot.variants[it.variantId]?.printingId }
                    .groupingBy { it }
                    .eachCount()
            }
            SearchField(value = filter, onValueChange = { filter = it }, placeholder = "Filter by name or number")
            if (importing) Text("Fetching card details…", color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            val loaded = cards
            if (loaded == null) {
                Text("Loading ${chosenSet.name}…", color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)
            } else {
                val needle = filter.trim().lowercase().removePrefix("#")
                val shown = loaded.filter {
                    needle.isEmpty() || it.name.lowercase().contains(needle) ||
                        it.number.lowercase().trimStart('0') == needle.trimStart('0')
                }
                SectionHeader("${shown.size} of ${loaded.size} cards")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { hit ->
                        val printing = PrintingId(hit.id)
                        val owned = ownedCounts[printing] ?: 0
                        val loose = unfiledCounts[printing] ?: 0
                        CatalogCardRow(
                            hit = hit,
                            onClick = { onPickRemote(hit, intent) },
                            enabled = !importing,
                            trailingIcon = AppIcons.Plus,
                            badge = when {
                                loose > 0 -> "Unfiled $loose"
                                owned > 0 -> "Own $owned"
                                else -> null
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One step of the catalog trail: a game, or a set. */
@Composable
private fun BrowseRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(Ink.Surface)
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium)
            .tappable(pressScale = 0.99f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(AppIcons.ChevronRight, null, Modifier.size(16.dp), tint = Ink.TextTertiary)
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

/**
 * Where in the binder this pocket is, as one line of equal parts.
 *
 * It used to lead with the binder's name and then mix its separators -- "Fresh - page 1 -
 * row 1, column 1" -- so the row and column read as a subordinate clause of the page
 * rather than as two more coordinates of the same address. They are all the same kind of
 * fact, so they are all separated the same way.
 *
 * The binder's name comes off the front. It is already in the header of the screen this
 * sheet is sitting on top of, and repeating it here spent the widest words on the one
 * thing that cannot have changed since you tapped.
 */
private fun pocketLocationLabel(binder: Binder, ordinal: Int): String {
    val location = binder.layout.locate(ordinal)
    return listOf(
        "Row ${location.row + 1}",
        "Column ${location.col + 1}",
        "Page ${location.faceIndex + 1}",
    ).joinToString(" · ")
}

