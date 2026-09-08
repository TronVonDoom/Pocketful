package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pocketful.data.CardImport
import app.pocketful.data.CatalogSync
import app.pocketful.data.RemoteCard
import app.pocketful.data.SearchHit
import app.pocketful.domain.Binder
import app.pocketful.domain.BinderId
import app.pocketful.domain.BinderLayout
import app.pocketful.domain.Card
import app.pocketful.domain.CardId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Condition
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerId
import app.pocketful.domain.ContainerKind
import app.pocketful.domain.Copy
import app.pocketful.domain.CopyId
import app.pocketful.domain.Edition
import app.pocketful.domain.Finish
import app.pocketful.domain.Grade
import app.pocketful.domain.Location
import app.pocketful.domain.Money
import app.pocketful.domain.PokemonType
import app.pocketful.domain.PriceSnapshot
import app.pocketful.domain.Printing
import app.pocketful.domain.PrintingId
import app.pocketful.domain.ReflowMode
import app.pocketful.domain.SampleData
import app.pocketful.domain.SlotContent
import app.pocketful.domain.Supertype
import app.pocketful.domain.Variant
import app.pocketful.domain.VariantId

/**
 * Display preferences. Every one of these changes something visible; nothing here is a
 * switch that only writes itself to disk.
 */
data class AppSettings(
    val holoShimmer: Boolean = true,
    val showPocketPrices: Boolean = true,
    val showWantedGhosts: Boolean = true,
    val abbreviateValues: Boolean = true,
    // The layout itself, not an id to look up: a custom page shape is as valid a default
    // as a preset one, and an id-keyed lookup can only ever find the presets.
    val defaultLayout: BinderLayout = BinderLayout.POCKET_9,
    val defaultSheetCount: Int = 10,
)

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/**
 * The single writable copy of the collection.
 *
 * Every mutation rebuilds the whole [CollectionSnapshot]. That is wasteful in the abstract
 * and completely free at this size, and it means the read model the UI renders can never
 * drift from the data behind it. Swapping this for a repository backed by a real database
 * is a change to the bodies of these methods only.
 *
 * The one invariant worth stating: a [Copy] is a physical object, so it lives in exactly
 * one place. Every path that puts a copy in a pocket first takes it out of wherever it
 * was, and [Copy.location] is kept in step with the binder slots rather than being a
 * second source of truth that can disagree.
 */
class CollectionStore(initial: CollectionSnapshot = SampleData.snapshot) {

    // Reconciled on the way in: a snapshot assembled by hand (the bundled sample, or
    // anything a future importer produces) sets binder slots but leaves every Copy at its
    // default Unassigned, which made the whole collection look unfiled.
    var snapshot by mutableStateOf(initial.reconcileLocations())
        private set

    var settings by mutableStateOf(AppSettings())
        private set

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
    }

    fun reset() {
        snapshot = SampleData.snapshot.reconcileLocations()
    }

    // ---------------------------------------------------------------- binders

    fun createBinder(
        name: String,
        subtitle: String?,
        layout: BinderLayout,
        sheetCount: Int,
        spineColor: Long,
    ): BinderId {
        val id = BinderId(uniqueId("binder") { candidate -> snapshot.binders.any { it.id.value == candidate } })
        val binder = Binder(
            id = id,
            name = name.trim().ifBlank { "Untitled binder" },
            subtitle = subtitle?.trim()?.takeIf { it.isNotBlank() },
            layout = layout,
            sheetCount = sheetCount.coerceAtLeast(1),
            spineColor = spineColor,
            slots = List(layout.capacity(sheetCount.coerceAtLeast(1))) { SlotContent.Empty },
        )
        snapshot = snapshot.copy(binders = snapshot.binders + binder)
        return id
    }

    /**
     * A binder sized for a catalog set, with every pocket already held open for the card
     * that belongs in it.
     *
     * This is the one flow in the app that starts from what *exists* rather than from what
     * you have. Building a set binder by hand means opening a hundred empty pockets and
     * searching for a card whose name you can read off the checklist -- work the app can
     * do in one pass, because the checklist is exactly what a set listing is.
     *
     * Wanted, not owned. Marking the whole set as owned would be the app deciding on the
     * user's behalf that they have finished it, and unpicking that is far more work than
     * ticking off the ones they actually have. The reverse is one gesture: select the
     * pockets and mark them.
     *
     * Cards past the binder's capacity are dropped rather than silently truncating the
     * set: [sheetCount] is expected to have been sized for [cards] by the caller, and a
     * short binder is a resize away from holding the rest.
     */
    fun createSetBinder(
        name: String,
        subtitle: String?,
        layout: BinderLayout,
        sheetCount: Int,
        spineColor: Long,
        sourceSetId: String?,
        cards: List<SearchHit>,
    ): BinderId {
        val safeSheets = sheetCount.coerceAtLeast(1)
        val (withCatalog, variantIds) = CardImport.stubAll(snapshot, cards)

        val id = BinderId(uniqueId("binder") { candidate -> snapshot.binders.any { it.id.value == candidate } })
        val capacity = layout.capacity(safeSheets)
        val binder = Binder(
            id = id,
            name = name.trim().ifBlank { "Untitled binder" },
            subtitle = subtitle?.trim()?.takeIf { it.isNotBlank() },
            layout = layout,
            sheetCount = safeSheets,
            spineColor = spineColor,
            sourceSetId = sourceSetId,
            slots = List(capacity) { ordinal ->
                variantIds.getOrNull(ordinal)?.let { SlotContent.Wanted(it) } ?: SlotContent.Empty
            },
        )

        snapshot = withCatalog.copy(binders = withCatalog.binders + binder)
        return id
    }

    /**
     * Shrinking a binder can strand copies that no longer have a pocket, so anything that
     * falls off the end is unfiled rather than quietly deleted.
     */
    fun updateBinder(
        id: BinderId,
        name: String,
        subtitle: String?,
        layout: BinderLayout,
        sheetCount: Int,
        spineColor: Long,
        reflowMode: ReflowMode = ReflowMode.PRESERVE_ORDER,
    ) {
        val existing = snapshot.binder(id) ?: return
        val safeSheets = sheetCount.coerceAtLeast(1)
        val reflowed = existing
            .reflow(layout, safeSheets, reflowMode)
            .copy(
                name = name.trim().ifBlank { existing.name },
                subtitle = subtitle?.trim()?.takeIf { it.isNotBlank() },
                spineColor = spineColor,
            )

        snapshot = snapshot
            .copy(binders = snapshot.binders.map { if (it.id == id) reflowed else it })
            .reconcileLocations()
    }

    /**
     * Removes a binder, and optionally everything filed in it.
     *
     * Keeping the cards is the safe default and stays the default: a binder is a piece of
     * furniture, and throwing away the furniture is not a statement about what was in it.
     * But the other case is real -- a binder sold as a lot, a set traded away whole -- and
     * without this the only way to record it was to delete forty cards by hand first,
     * which is tedious enough that people instead leave a phantom forty cards in their
     * portfolio and quietly stop trusting the total.
     *
     * Only copies actually in this binder's pockets are taken. A copy lives in exactly one
     * place, so there is no risk of removing something a container is still holding.
     */
    fun deleteBinder(id: BinderId, deleteCards: Boolean = false) {
        val binder = snapshot.binder(id) ?: return
        val inside = binder.paddedSlots.filterIsInstance<SlotContent.Filled>().map { it.copyId }.toSet()
        val withoutBinder = snapshot.copy(binders = snapshot.binders.filterNot { it.id == id })
        snapshot = (if (deleteCards) withoutBinder.copy(copies = withoutBinder.copies - inside) else withoutBinder)
            .reconcileLocations()
    }

    /** How many cards [deleteBinder] would take with it, for the sentence that asks. */
    fun cardsInside(id: BinderId): Int =
        snapshot.binder(id)?.paddedSlots?.count { it is SlotContent.Filled } ?: 0

    // ------------------------------------------------------------- containers

    fun createContainer(
        name: String,
        subtitle: String?,
        kind: ContainerKind,
        color: Long,
    ): ContainerId {
        val id = ContainerId(
            uniqueId("container") { candidate -> snapshot.containers.any { it.id.value == candidate } },
        )
        val container = Container(
            id = id,
            name = name.trim().ifBlank { kind.label },
            subtitle = subtitle?.trim()?.takeIf { it.isNotBlank() },
            kind = kind,
            color = color,
        )
        snapshot = snapshot.copy(containers = snapshot.containers + container)
        return id
    }

    fun updateContainer(
        id: ContainerId,
        name: String,
        subtitle: String?,
        kind: ContainerKind,
        color: Long,
    ) {
        val existing = snapshot.container(id) ?: return
        val next = existing.copy(
            name = name.trim().ifBlank { existing.name },
            subtitle = subtitle?.trim()?.takeIf { it.isNotBlank() },
            kind = kind,
            color = color,
        )
        snapshot = snapshot.copy(containers = snapshot.containers.map { if (it.id == id) next else it })
    }

    /**
     * Removes a container, and optionally its contents.
     *
     * The same choice a binder gets, for the same reason: a sealed box sold on is not the
     * same event as unpacking one, and the app should be able to record either.
     */
    fun deleteContainer(id: ContainerId, deleteCards: Boolean = false) {
        val container = snapshot.container(id) ?: return
        val inside = container.copyIds.toSet()
        val withoutContainer = snapshot.copy(containers = snapshot.containers.filterNot { it.id == id })
        snapshot = (
            if (deleteCards) withoutContainer.copy(copies = withoutContainer.copies - inside)
            else withoutContainer
            ).reconcileLocations()
    }

    /** How many cards [deleteContainer] would take with it. */
    fun cardsInside(id: ContainerId): Int = snapshot.container(id)?.count ?: 0

    /** Files an existing copy into a container, taking it out of wherever it was. */
    fun placeInContainer(containerId: ContainerId, copyId: CopyId) {
        if (snapshot.copies[copyId] == null) return
        if (snapshot.container(containerId) == null) return
        snapshot = snapshot
            .unfile(copyId)
            .let { unfiled ->
                unfiled.copy(
                    containers = unfiled.containers.map {
                        if (it.id == containerId) it.with(copyId) else it
                    },
                )
            }
            .reconcileLocations()
    }

    /** Takes a copy back out of its container. It stays in the collection, just unfiled. */
    fun removeFromContainer(containerId: ContainerId, copyId: CopyId) {
        snapshot = snapshot
            .copy(
                containers = snapshot.containers.map {
                    if (it.id == containerId) it.without(copyId) else it
                },
            )
            .reconcileLocations()
    }

    // ------------------------------------------------------------------ slots

    /** Files a copy that already exists, taking it out of any pocket it currently sits in. */
    fun placeCopy(binderId: BinderId, ordinal: Int, copyId: CopyId) {
        if (snapshot.copies[copyId] == null) return
        snapshot = snapshot
            .unfile(copyId)
            .writeSlot(binderId, ordinal, SlotContent.Filled(copyId))
            .reconcileLocations()
    }

    /** Records a brand new physical copy of [variantId] straight into a pocket. */
    fun addCopyToSlot(
        binderId: BinderId,
        ordinal: Int,
        variantId: VariantId,
        condition: Condition = Condition.NEAR_MINT,
        acquiredPrice: Money? = null,
        grade: Grade? = null,
        notes: String? = null,
    ): CopyId {
        val copyId = CopyId(uniqueId("copy") { it in snapshot.copies.keys.map(CopyId::value) })
        val copy = Copy(
            id = copyId,
            variantId = variantId,
            condition = condition,
            grade = grade,
            acquiredPrice = acquiredPrice,
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
            location = Location.BinderSlot(binderId, ordinal),
        )
        snapshot = snapshot
            .copy(copies = snapshot.copies + (copyId to copy))
            .writeSlot(binderId, ordinal, SlotContent.Filled(copyId))
            .reconcileLocations()
        return copyId
    }

    /**
     * Records a copy without giving it a pocket.
     *
     * The pocket-first flow assumes you are filling a binder. Search does not: you are
     * looking a card up and deciding you own it, and the question of where it goes is
     * often "in the pile on the desk" -- which is what unfiled means. Passing [container]
     * files it into a box in the same step, because "into the bulk box" is the other
     * honest answer and making the user file it afterwards is how cards get lost.
     */
    fun addCopy(
        variantId: VariantId,
        condition: Condition = Condition.NEAR_MINT,
        acquiredPrice: Money? = null,
        grade: Grade? = null,
        notes: String? = null,
        container: ContainerId? = null,
    ): CopyId {
        val copyId = CopyId(uniqueId("copy") { it in snapshot.copies.keys.map(CopyId::value) })
        val copy = Copy(
            id = copyId,
            variantId = variantId,
            condition = condition,
            grade = grade,
            acquiredPrice = acquiredPrice,
            notes = notes?.trim()?.takeIf { it.isNotBlank() },
            location = Location.Unassigned,
        )
        snapshot = snapshot.copy(copies = snapshot.copies + (copyId to copy))
        if (container != null) placeInContainer(container, copyId)
        return copyId
    }

    fun markWanted(binderId: BinderId, ordinal: Int, variantId: VariantId, targetPrice: Money? = null) {
        snapshot = snapshot
            .writeSlot(binderId, ordinal, SlotContent.Wanted(variantId, targetPrice))
            .reconcileLocations()
    }

    fun setSpacer(binderId: BinderId, ordinal: Int, label: String?) {
        snapshot = snapshot
            .writeSlot(binderId, ordinal, SlotContent.Spacer(label?.trim()?.takeIf { it.isNotBlank() }))
            .reconcileLocations()
    }

    /** Empties the pocket. A copy that was in it stays in the collection, just unfiled. */
    fun clearSlot(binderId: BinderId, ordinal: Int) {
        snapshot = snapshot
            .writeSlot(binderId, ordinal, SlotContent.Empty)
            .reconcileLocations()
    }

    /**
     * Records that a run of wanted pockets have been found.
     *
     * The bulk half of "I have it now". Working through a set binder with a stack of
     * cards in hand is the moment this app exists for, and doing it a pocket at a time
     * means opening, confirming and dismissing a sheet once per card -- forty gestures to
     * record forty cards already sitting on the desk.
     *
     * Everything is recorded at the pocket's own card, near mint, with no purchase price:
     * a bulk action can only honestly say *that* they are owned, and condition and cost
     * are per-card facts the pocket sheet still edits one at a time.
     *
     * Pockets that are not wanted are skipped rather than refused, so a selection swept
     * across a page that already has cards in it does the sensible thing.
     */
    fun markSlotsOwned(
        binderId: BinderId,
        ordinals: Collection<Int>,
        condition: Condition = Condition.NEAR_MINT,
    ): Int {
        val binder = snapshot.binder(binderId) ?: return 0
        val slots = binder.paddedSlots.toMutableList()
        val copies = snapshot.copies.toMutableMap()
        // One running set rather than a scan of the map per card: filling a 360-pocket
        // binder in one gesture is the case this has to not be quadratic in.
        val taken = copies.keys.mapTo(mutableSetOf()) { it.value }
        var filled = 0

        for (ordinal in ordinals.distinct().sorted()) {
            val slot = slots.getOrNull(ordinal) as? SlotContent.Wanted ?: continue
            val copyId = CopyId(uniqueId("copy") { it in taken })
            taken += copyId.value
            copies[copyId] = Copy(
                id = copyId,
                variantId = slot.variantId,
                condition = condition,
                location = Location.BinderSlot(binderId, ordinal),
            )
            slots[ordinal] = SlotContent.Filled(copyId)
            filled++
        }
        if (filled == 0) return 0

        snapshot = snapshot
            .copy(
                copies = copies,
                binders = snapshot.binders.map { if (it.id == binderId) it.copy(slots = slots) else it },
            )
            .reconcileLocations()
        return filled
    }

    /**
     * Puts filled pockets back on the want list.
     *
     * The copy is deleted, not unfiled. This is the undo for "I have it", and the honest
     * reading of it is that the card was never there -- leaving an orphan copy floating in
     * the collection would mean a portfolio total that still counts a card the user has
     * just said they do not have.
     */
    fun markSlotsWanted(binderId: BinderId, ordinals: Collection<Int>): Int {
        val binder = snapshot.binder(binderId) ?: return 0
        val slots = binder.paddedSlots.toMutableList()
        val copies = snapshot.copies.toMutableMap()
        var moved = 0

        for (ordinal in ordinals.distinct()) {
            val slot = slots.getOrNull(ordinal) as? SlotContent.Filled ?: continue
            val copy = copies[slot.copyId] ?: continue
            copies -= slot.copyId
            slots[ordinal] = SlotContent.Wanted(copy.variantId)
            moved++
        }
        if (moved == 0) return 0

        snapshot = snapshot
            .copy(
                copies = copies,
                binders = snapshot.binders.map { if (it.id == binderId) it.copy(slots = slots) else it },
            )
            .reconcileLocations()
        return moved
    }

    /** Empties a run of pockets. Cards that were in them stay in the collection, unfiled. */
    fun clearSlots(binderId: BinderId, ordinals: Collection<Int>) {
        val binder = snapshot.binder(binderId) ?: return
        val slots = binder.paddedSlots.toMutableList()
        var changed = false
        for (ordinal in ordinals.distinct()) {
            val slot = slots.getOrNull(ordinal) ?: continue
            if (slot == SlotContent.Empty) continue
            slots[ordinal] = SlotContent.Empty
            changed = true
        }
        if (!changed) return
        snapshot = snapshot
            .copy(binders = snapshot.binders.map { if (it.id == binderId) it.copy(slots = slots) else it })
            .reconcileLocations()
    }

    fun updateCopy(
        copyId: CopyId,
        condition: Condition,
        acquiredPrice: Money?,
        grade: Grade?,
        notes: String?,
    ) {
        val existing = snapshot.copies[copyId] ?: return
        snapshot = snapshot.copy(
            copies = snapshot.copies + (
                copyId to existing.copy(
                    condition = condition,
                    acquiredPrice = acquiredPrice,
                    grade = grade,
                    notes = notes?.trim()?.takeIf { it.isNotBlank() },
                )
                ),
        )
    }

    /**
     * Offers this copy in trade, or takes it back off the table.
     *
     * Deliberately does not move the card. A binder page with a gap where a tradeable
     * card used to be would be a lie about what is physically in the binder -- the flag
     * is a note about intent, and the Trade screen is a view over it.
     */
    fun setForTrade(copyId: CopyId, forTrade: Boolean) {
        val existing = snapshot.copies[copyId] ?: return
        if (existing.forTrade == forTrade) return
        snapshot = snapshot.copy(
            copies = snapshot.copies + (copyId to existing.copy(forTrade = forTrade)),
        )
    }

    /** Removes the physical copy from the collection entirely, wherever it was filed. */
    fun deleteCopy(copyId: CopyId) {
        snapshot = snapshot
            .unfile(copyId)
            .let { it.copy(copies = it.copies - copyId) }
    }

    /**
     * The bulk forms of the three things a multi-select does.
     *
     * Separate methods rather than the caller looping, because each one rebuilds the whole
     * snapshot: forty single calls means forty rebuilds and forty recompositions of every
     * screen watching it, which is what turns "delete these forty cards" into a visible
     * stall. One pass, one new snapshot, one frame.
     */
    fun deleteCopies(copyIds: Collection<CopyId>) {
        if (copyIds.isEmpty()) return
        val doomed = copyIds.toSet()
        var next = snapshot
        for (copyId in doomed) next = next.unfile(copyId)
        snapshot = next.copy(copies = next.copies - doomed).reconcileLocations()
    }

    fun setForTrade(copyIds: Collection<CopyId>, forTrade: Boolean) {
        if (copyIds.isEmpty()) return
        val touched = copyIds.toSet()
        snapshot = snapshot.copy(
            copies = snapshot.copies.mapValues { (id, copy) ->
                if (id in touched && copy.forTrade != forTrade) copy.copy(forTrade = forTrade) else copy
            },
        )
    }

    fun placeInContainer(containerId: ContainerId, copyIds: Collection<CopyId>) {
        if (copyIds.isEmpty()) return
        if (snapshot.container(containerId) == null) return
        val moving = copyIds.filter { it in snapshot.copies }
        var next = snapshot
        for (copyId in moving) next = next.unfile(copyId)
        snapshot = next
            .copy(
                containers = next.containers.map { container ->
                    if (container.id != containerId) container
                    else moving.fold(container) { acc, copyId -> acc.with(copyId) }
                },
            )
            .reconcileLocations()
    }

    // ---------------------------------------------------------------- catalog

    /**
     * Files a card fetched from the live catalog, and returns the variant matching
     * [finish] so the caller can put a copy of it somewhere.
     *
     * Re-importing the same card is an update, not a duplicate: the ids are derived from
     * the upstream id, so a second import refreshes art and prices in place.
     */
    fun importRemoteCard(
        card: RemoteCard,
        finish: Finish,
        edition: Edition = Edition.UNLIMITED,
    ): VariantId {
        snapshot = CardImport.into(snapshot, card, edition)
        val exact = CardImport.variantId(card, finish, edition)
        if (exact in snapshot.variants) return exact
        // The requested finish was not printed. Fall back to whichever one was, rather
        // than handing back an id that resolves to nothing.
        return CardImport.finishesOf(card)
            .firstNotNullOfOrNull { CardImport.variantId(card, it, edition).takeIf { id -> id in snapshot.variants } }
            ?: exact
    }

    /**
     * Merges the result of a catalog sync into the live collection.
     *
     * Applied as a delta against whatever the snapshot is *now*, not against the one the
     * sync was started from: a sync takes seconds, and a card filed while it was running
     * must not be undone by its arrival.
     */
    fun applyCatalogSync(result: CatalogSync.Result) {
        if (result.printings.isEmpty() && result.prices.isEmpty()) return
        snapshot = snapshot.copy(
            // Only printings the collection still has. Anything deleted mid-sync stays
            // deleted rather than being resurrected by its own price update.
            printings = snapshot.printings + result.printings.filterKeys { it in snapshot.printings },
            prices = snapshot.prices + result.prices.filterKeys { id ->
                snapshot.variants[id] != null
            },
        )
    }

    /** Writes a freshly quoted market price over whatever was cached for that variant. */
    fun applyPrice(variantId: VariantId, market: Money, fetchedAtEpochSeconds: Long) {
        if (variantId !in snapshot.variants) return
        snapshot = snapshot.copy(
            prices = snapshot.prices + (
                variantId to PriceSnapshot(
                    variantId = variantId,
                    market = market,
                    source = "tcgplayer",
                    fetchedAtEpochSeconds = fetchedAtEpochSeconds,
                )
                ),
        )
    }

    /**
     * Adds a card the bundled catalog does not know about. Users hit this constantly --
     * promos, regional prints, anything newer than the last catalog drop -- and an app
     * that can only file cards it already knows about is useless the first week.
     */
    fun addCatalogCard(
        name: String,
        setName: String,
        number: String,
        setTotal: String?,
        type: PokemonType?,
        finish: Finish,
        edition: Edition,
        rarity: String?,
        marketValue: Money,
        supertype: Supertype = Supertype.POKEMON,
    ): VariantId {
        val setCode = setName.lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "custom" }
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "card" }
        val cleanNumber = number.trim().ifBlank { "?" }

        val cardId = CardId(uniqueId("ptcg-$setCode-$slug") { it in snapshot.cards.keys.map(CardId::value) })
        val printingId = PrintingId(
            uniqueId("ptcg-$setCode-$cleanNumber") { it in snapshot.printings.keys.map(PrintingId::value) },
        )
        val variantId = VariantId(
            uniqueId("${printingId.value}-${finish.name.lowercase()}") {
                it in snapshot.variants.keys.map(VariantId::value)
            },
        )

        snapshot = snapshot.copy(
            cards = snapshot.cards + (
                cardId to Card(
                    id = cardId,
                    name = name.trim().ifBlank { "Unnamed card" },
                    supertype = supertype,
                    types = listOfNotNull(type),
                )
                ),
            printings = snapshot.printings + (
                printingId to Printing(
                    id = printingId,
                    cardId = cardId,
                    setCode = setCode,
                    setName = setName.trim().ifBlank { "Custom" },
                    number = cleanNumber,
                    setTotal = setTotal?.trim()?.takeIf { it.isNotBlank() },
                    rarity = rarity?.trim()?.takeIf { it.isNotBlank() },
                    illustrator = null,
                    releaseYear = null,
                )
                ),
            variants = snapshot.variants + (
                variantId to Variant(id = variantId, printingId = printingId, finish = finish, edition = edition)
                ),
            prices = snapshot.prices + (
                variantId to PriceSnapshot(
                    variantId = variantId,
                    market = marketValue,
                    source = "manual",
                    fetchedAtEpochSeconds = 0L,
                )
                ),
        )
        return variantId
    }

    // ---------------------------------------------------------------- helpers

    private fun uniqueId(base: String, exists: (String) -> Boolean): String {
        if (!exists(base)) return base
        var n = 2
        while (exists("$base-$n")) n++
        return "$base-$n"
    }
}

// ------------------------------------------------------------- snapshot edits

private fun CollectionSnapshot.writeSlot(
    binderId: BinderId,
    ordinal: Int,
    content: SlotContent,
): CollectionSnapshot {
    val binder = binder(binderId) ?: return this
    if (ordinal !in 0 until binder.capacity) return this
    return copy(binders = binders.map { if (it.id == binderId) it.withSlot(ordinal, content) else it })
}

/**
 * Takes a copy out of whatever is holding it, anywhere in the collection. Both storage
 * kinds are swept, because "a copy lives in exactly one place" has to hold across them --
 * filing a card from a box into a pocket must empty the box slot, not clone the card.
 */
private fun CollectionSnapshot.unfile(copyId: CopyId): CollectionSnapshot = copy(
    binders = binders.map { binder ->
        val hit = binder.paddedSlots.indexOfFirst { it is SlotContent.Filled && it.copyId == copyId }
        if (hit < 0) binder else binder.withSlot(hit, SlotContent.Empty)
    },
    containers = containers.map { it.without(copyId) },
)

/**
 * Rewrites every [Copy.location] from the binder slots. Called after any slot edit so the
 * two representations cannot disagree -- the slots are authoritative, the location field
 * is a denormalised index for the card list to read.
 */
private fun CollectionSnapshot.reconcileLocations(): CollectionSnapshot {
    // A container can outlive the copies it lists (delete the card, keep the box), so the
    // membership list is pruned here rather than leaving ids that resolve to nothing.
    val prunedContainers = containers.map { container ->
        val live = container.copyIds.filter { it in copies }
        if (live.size == container.copyIds.size) container else container.copy(copyIds = live)
    }

    val filed = mutableMapOf<CopyId, Location>()
    for (binder in binders) {
        binder.paddedSlots.forEachIndexed { ordinal, slot ->
            if (slot is SlotContent.Filled) filed[slot.copyId] = Location.BinderSlot(binder.id, ordinal)
        }
    }
    for (container in prunedContainers) {
        for (copyId in container.copyIds) {
            // A pocket wins over a box: the binder slots are the authoritative record.
            filed.getOrPut(copyId) { Location.InContainer(container.id) }
        }
    }

    var changed = prunedContainers != containers
    val next = copies.mapValues { (id, copy) ->
        val held = copy.location is Location.BinderSlot || copy.location is Location.InContainer
        val target = filed[id] ?: if (held) Location.Unassigned else copy.location
        if (target == copy.location) copy else { changed = true; copy.copy(location = target) }
    }
    return if (changed) copy(copies = next, containers = prunedContainers) else this
}

@Composable
fun rememberCollectionStore(): CollectionStore = remember { CollectionStore() }
