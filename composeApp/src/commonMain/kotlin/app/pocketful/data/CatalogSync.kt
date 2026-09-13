package app.pocketful.data

import app.pocketful.domain.Card
import app.pocketful.domain.CardId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Money
import app.pocketful.domain.PriceSnapshot
import app.pocketful.domain.Printing
import app.pocketful.domain.PrintingId
import app.pocketful.domain.SlotContent
import app.pocketful.domain.Variant
import app.pocketful.domain.VariantId

/**
 * Keeping the collection's catalog rows in step with the published catalog.
 *
 * Every card the collection holds carries a copy of what the catalog said about it when it was
 * filed -- its name, picture, printings -- so the collection can be drawn without the catalog.
 * When a set is published again with a correction, a new picture or a printing that was
 * missing, this is what carries the change into the collection. It asks the network nothing:
 * the catalog and the price file are already on the device.
 *
 * IDs never change here. A copy points at a variant by the catalog's own ID, and the catalog
 * never renames what it has published, so a refresh only ever replaces rows under the same IDs
 * and adds printings a card did not have.
 */
class CatalogSync(private val catalog: CardCatalog) {

    /**
     * What a refresh found, as a delta rather than a finished snapshot, so a refresh that
     * started before an edit and finished after it cannot undo that edit.
     */
    data class Result(
        val matched: Int,
        val unmatched: Int,
        val cards: Map<CardId, Card> = emptyMap(),
        val printings: Map<PrintingId, Printing> = emptyMap(),
        val variants: Map<VariantId, Variant> = emptyMap(),
        val prices: Map<VariantId, PriceSnapshot> = emptyMap(),
        val failure: String? = null,
    ) {
        val pricesUpdated: Int get() = prices.size
        val attempted: Int get() = matched + unmatched
    }

    /**
     * Every printing in the collection, refreshed from the catalog and repriced from the price
     * file. Cards the catalog does not have -- ones added by hand -- are left exactly as they are.
     */
    fun refresh(snapshot: CollectionSnapshot, nowEpochSeconds: Long): Result {
        var rows = CatalogRows()
        var matched = 0
        for (printingId in snapshot.printings.keys) {
            val card = catalog.card(printingId.value) ?: continue
            matched++
            rows += CardImport.rowsFor(card, catalog)
        }
        val cards = rows.cards.filter { (id, card) -> snapshot.cards[id] != card }
        val printings = rows.printings.filter { (id, printing) -> snapshot.printings[id] != printing }
        val variants = rows.variants.filter { (id, variant) -> snapshot.variants[id] != variant }
        val allVariants = snapshot.variants.keys + rows.variants.keys
        return Result(
            matched = matched,
            unmatched = snapshot.printings.size - matched,
            cards = cards,
            printings = printings,
            variants = variants,
            prices = priceFromPublished(allVariants, nowEpochSeconds),
        )
    }

    /** Prices for these variants from the price file on the device, today's and the day before's. */
    fun priceFromPublished(variantIds: Collection<VariantId>, nowEpochSeconds: Long): Map<VariantId, PriceSnapshot> {
        val prices = catalog.prices ?: return emptyMap()
        val priced = mutableMapOf<VariantId, PriceSnapshot>()
        for (id in variantIds) {
            val cents = prices.cents(id.value) ?: continue
            val before = prices.previousCents(id.value)
            priced[id] = PriceSnapshot(
                variantId = id,
                market = Money(cents),
                source = "tcgplayer",
                fetchedAtEpochSeconds = nowEpochSeconds,
                previous = before?.let { Money(it) },
                previousDate = before?.let { prices.previous?.date },
            )
        }
        return priced
    }

    fun priceFromPublished(snapshot: CollectionSnapshot, nowEpochSeconds: Long): Map<VariantId, PriceSnapshot> =
        priceFromPublished(snapshot.variants.keys, nowEpochSeconds)

    /**
     * Rebuilds catalog rows for cards the collection points at but has no rows for.
     *
     * The collection is stored as two files -- what you own, and the slice of the catalog those
     * things are described by -- and losing the second leaves a list of IDs pointing at nothing.
     * Every variant ID names its own card, and the catalog on the device has that card, so the
     * rows come back without asking the network anything.
     */
    fun recoverOrphans(snapshot: CollectionSnapshot): Recovered {
        val referenced = buildSet {
            snapshot.copies.values.forEach { add(it.variantId) }
            for (binder in snapshot.binders) {
                for (slot in binder.paddedSlots) if (slot is SlotContent.Wanted) add(slot.variantId)
            }
        }
        val orphans = referenced.filter { it !in snapshot.variants }
        if (orphans.isEmpty()) return Recovered()

        var rows = CatalogRows()
        for (cardId in orphans.map { CatalogIds.cardOf(it.value) }.distinct()) {
            val card = catalog.card(cardId) ?: continue
            rows += CardImport.rowsFor(card, catalog)
        }
        return Recovered(rows.cards, rows.printings, rows.variants)
    }

    /** Catalog rows rebuilt from the catalog, for a collection that lost them. */
    data class Recovered(
        val cards: Map<CardId, Card> = emptyMap(),
        val printings: Map<PrintingId, Printing> = emptyMap(),
        val variants: Map<VariantId, Variant> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = variants.isEmpty()
        val count: Int get() = variants.size
    }
}
