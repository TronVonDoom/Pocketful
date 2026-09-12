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
import app.pocketful.data.SetPocket
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
import app.pocketful.domain.SlotContent
import app.pocketful.domain.Supertype
import app.pocketful.domain.Variant
import app.pocketful.domain.VariantId
import kotlin.random.Random

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
class CollectionStore(initial: CollectionSnapshot = CollectionSnapshot()) {

    // Empty by default. The app used to open onto a demonstration collection -- five
    // sets, a stack of binders, invented prices -- which made the first screen look like
    // someone else's shelf and gave every number on it a reason to be distrusted. A new
    // install now starts with nothing, and the first binder in it is one the user made.
    //
    // Reconciled on the way in anyway: a snapshot assembled by hand -- anything a future
    // importer or a restored save produces -- sets binder slots but leaves every Copy at
    // its default Unassigned, which would make the whole collection look unfiled.
    private var snapshotState by mutableStateOf(initial.reconcileLocations())

    var snapshot: CollectionSnapshot
        get() = snapshotState
        private set(value) {
            snapshotState = value
            revision++
        }

    private var settingsState by mutableStateOf(AppSettings())

    var settings: AppSettings
        get() = settingsState
        private set(value) {
            settingsState = value
            revision++
        }

    /**
     * How many times anything here has changed.
     *
     * Exists so a watcher can tell "something happened" from "nothing happened" without
     * comparing two collections field by field. [CollectionSnapshot] is a data class, so
     * observing the snapshot itself would deep-compare every map on it against the
     * previous one on every edit -- a full scan of the catalog to answer a question a
     * counter answers exactly. See [app.pocketful.state.AutosaveEffect], the only reader.
     */
    var revision by mutableStateOf(0)
        private set

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
    }

    /**
     * Replaces everything with a collection read back off disk.
     *
     * Goes through [CollectionSnapshot.reconcileLocations] like any other snapshot
     * assembled from outside: a save file records binder slots *and* each copy's
     * location, and while the app keeps those two in step, a file that was hand-edited or
     * written by an older build might not. The slots win, as they do everywhere else.
     *
     * Deliberately not a merge. This runs once, at launch, against a store nothing has
     * touched yet -- and a "restore" that tried to reconcile against live edits would be
     * inventing a conflict resolution policy for a case that cannot happen.
     */
    fun restore(snapshot: CollectionSnapshot, settings: AppSettings) {
        this.snapshot = snapshot.reconcileLocations()
        this.settings = settings
    }

    /**
     * Replaces the collection with an imported one, keeping the catalog it already had.
     *
     * The two halves are treated differently on purpose. Binders, boxes and copies are
     * *replaced*: a collection is a claim about what one person owns, and merging two of
     * them would invent someone who owns both, with no way to tell afterwards which cards
     * came from where. The catalog is *merged*: its entries are upstream facts under
     * upstream ids, so two copies of one never disagree, and keeping what was already
     * here means an import cannot cost the device artwork and prices it had already
     * fetched for cards the backup happens not to mention.
     *
     * [restore] is the launch-time sibling of this, and the difference is exactly that
     * one: restore is the whole state arriving at a store nothing has touched, this is a
     * collection arriving at one that is already in use.
     */
    fun importCollection(imported: CollectionSnapshot, settings: AppSettings) {
        val current = snapshot
        snapshot = imported.copy(
            cards = current.cards + imported.cards,
            printings = current.printings + imported.printings,
            variants = current.variants + imported.variants,
            prices = current.prices + imported.prices,
        ).reconcileLocations()
        this.settings = settings
    }

    /** Back to a new install: every binder, box and card gone. */
    fun reset() {
        snapshot = CollectionSnapshot()
    }

    // ---------------------------------------------------------------- binders

    fun createBinder(
        name: String,
        subtitle: String?,
        layout: BinderLayout,
        sheetCount: Int,
        spineColor: Long,
    ): BinderId {
        val id = BinderId(mintId("binder") { candidate -> snapshot.binders.any { it.id.value == candidate } })
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
        pockets: List<SetPocket>,
    ): BinderId {
        val safeSheets = sheetCount.coerceAtLeast(1)
        val (withCatalog, variantIds) = CardImport.stubAll(snapshot, pockets)

        val id = BinderId(mintId("binder") { candidate -> snapshot.binders.any { it.id.value == candidate } })
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
            .pruneOrphanedCatalog()
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
            mintId("container") { candidate -> snapshot.containers.any { it.id.value == candidate } },
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
            .pruneOrphanedCatalog()
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
        val copyId = CopyId(mintId("copy") { it in snapshot.copies.keys.map(CopyId::value) })
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
        val copyId = CopyId(mintId("copy") { it in snapshot.copies.keys.map(CopyId::value) })
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

    /**
     * Exchanges what is in two pockets.
     *
     * Move and swap are the same operation, which is the whole reason this is one method
     * rather than two. Dropping a card on an empty pocket is an exchange with
     * [SlotContent.Empty]; dropping it on an occupied one exchanges the two cards. Writing
     * it as "move, and if something was already there, displace it" would need somewhere
     * to put the displaced card and would invent a rule about where -- the card you were
     * holding came *from* somewhere, and that somewhere is exactly the right place for it.
     *
     * Content-level rather than copy-level, so it carries wanted pockets and spacers as
     * readily as owned cards. A binder is an arrangement, and rearranging it should not
     * care which kind of thing is in the pocket.
     */
    fun swapSlots(binderId: BinderId, from: Int, to: Int) {
        if (from == to) return
        val binder = snapshot.binder(binderId) ?: return
        if (from !in 0 until binder.capacity || to !in 0 until binder.capacity) return
        val slots = binder.paddedSlots
        val moving = slots.getOrNull(from) ?: return
        val displaced = slots.getOrNull(to) ?: return
        // Two empties exchange to exactly what was there. Caught here so the gesture can
        // be forgiving about where it lands without every stray drop writing a snapshot.
        if (moving == SlotContent.Empty && displaced == SlotContent.Empty) return
        snapshot = snapshot
            .copy(
                binders = snapshot.binders.map { candidate ->
                    if (candidate.id == binderId) candidate.swap(from, to) else candidate
                },
            )
            // Both copies changed pocket, and Copy.location has to be told. Skipping this
            // is how a card ends up drawn in its new pocket and filed under its old one.
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
            val copyId = CopyId(mintId("copy") { it in taken })
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

    /**
     * Flags or unflags a run of filled pockets as up for trade.
     *
     * The bulk half of the switch on the pocket sheet, and the one selection action that
     * changes nothing about where a card lives -- which is the whole point of the flag.
     * Deciding what you would part with is done by looking at a page and picking the
     * spares off it, so it belongs in the same gesture as the rest of that page's edits
     * rather than in twelve separate sheets.
     *
     * Pockets that are empty, wanted or already in the asked-for state are skipped, so
     * the count that comes back is what actually changed.
     */
    fun setSlotsForTrade(binderId: BinderId, ordinals: Collection<Int>, forTrade: Boolean): Int {
        val binder = snapshot.binder(binderId) ?: return 0
        val copies = snapshot.copies.toMutableMap()
        var changed = 0

        for (ordinal in ordinals.distinct()) {
            val slot = binder.paddedSlots.getOrNull(ordinal) as? SlotContent.Filled ?: continue
            val copy = copies[slot.copyId] ?: continue
            if (copy.forTrade == forTrade) continue
            copies[slot.copyId] = copy.copy(forTrade = forTrade)
            changed++
        }
        if (changed == 0) return 0

        // No reconcile: the flag lives on the copy and moves nothing.
        snapshot = snapshot.copy(copies = copies)
        return changed
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

    /**
     * Edits a copy in place.
     *
     * [variantId] re-points it at another press run of the same card -- correcting a
     * reverse holo that was filed as a normal. It is an edit rather than a delete and
     * re-add because everything else on the record (what it cost, what pocket it is in,
     * whether it is on the trade table) is about the physical card, and none of that
     * changes just because the app had the finish wrong. Ignored if it names a variant
     * the catalog does not have.
     */
    fun updateCopy(
        copyId: CopyId,
        condition: Condition,
        acquiredPrice: Money?,
        grade: Grade?,
        notes: String?,
        variantId: VariantId? = null,
    ) {
        val existing = snapshot.copies[copyId] ?: return
        snapshot = snapshot.copy(
            copies = snapshot.copies + (
                copyId to existing.copy(
                    variantId = variantId?.takeIf { it in snapshot.variants } ?: existing.variantId,
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
            // The catalog rows go with it when they were only there for this card. A
            // delete that left them behind meant the card kept turning up in the pickers
            // afterwards, which reads as the app not having deleted it.
            .pruneOrphanedCatalog()
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
        snapshot = next.copy(copies = next.copies - doomed)
            .reconcileLocations()
            .pruneOrphanedCatalog()
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

    /**
     * A fresh id for a binder, box or copy -- something this device is inventing rather
     * than naming.
     *
     * The random tail is the whole point, and it is not about collisions within one
     * collection: a counter handled those fine. It is about what it *means* when the same
     * id turns up in two collections at once.
     *
     * Numbered from one, every device independently mints `copy`, `copy-2`, `binder` --
     * so the first card anyone ever adds is called `copy` on every phone on earth. Merging
     * two collections then finds an id in common on essentially every record while none of
     * them are the same card, and the only safe thing left to do is renumber everything on
     * one side, which turns a re-import of your own backup into a second copy of your
     * entire collection.
     *
     * With a random tail, a shared id means what it should: these two records have a
     * common ancestor, because one collection was exported from the other. A merge can
     * then keep what matches and renumber only what genuinely clashes. Eight base-36
     * characters is about 2.8e12 possibilities, which is far more headroom than a person
     * filing cards on a phone will ever need, and [exists] still settles the rest.
     *
     * Ids already in a collection are left exactly as they are. They are opaque strings
     * and nothing reads them, so an old `copy-2` sitting beside a new `copy-k3f9a2m1`
     * costs nothing -- and rewriting every id in a live collection, along with the binder
     * slots, container lists and locations that point at them, would risk a great deal to
     * buy nothing that is not already bought by minting the next one properly.
     */
    private fun mintId(kind: String, exists: (String) -> Boolean): String {
        while (true) {
            val candidate = buildString(kind.length + 1 + TOKEN_LENGTH) {
                append(kind)
                append('-')
                repeat(TOKEN_LENGTH) { append(TOKEN_ALPHABET[Random.nextInt(TOKEN_ALPHABET.length)]) }
            }
            if (!exists(candidate)) return candidate
        }
    }

    /**
     * An id derived from what the thing *is*, deduplicated by counting.
     *
     * The opposite of [mintId] and deliberately so. This names catalog entries, whose ids
     * are built from set code and card name, and two devices deriving the same id for the
     * same promo is the correct answer rather than a collision -- it is the same card.
     */
    private fun uniqueId(base: String, exists: (String) -> Boolean): String {
        if (!exists(base)) return base
        var n = 2
        while (exists("$base-$n")) n++
        return "$base-$n"
    }

    private companion object {
        const val TOKEN_LENGTH = 8
        const val TOKEN_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
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
