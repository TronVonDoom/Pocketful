package app.pocketful.data

import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Finish
import app.pocketful.domain.Money
import app.pocketful.domain.PriceSnapshot
import app.pocketful.domain.Printing
import app.pocketful.domain.PrintingId
import app.pocketful.domain.VariantId
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Matching what is already in the collection against the live catalog.
 *
 * This is the difference between an app that can show art for cards you add *from now on*
 * and one that can show art for the collection you already have. It resolves each existing
 * printing to an upstream card and writes back the two things the local record cannot
 * invent -- the artwork and the real market price -- while leaving every id alone.
 *
 * Ids are deliberately preserved rather than re-derived. A copy points at a variant, and
 * re-keying the catalog underneath it would strand every card anyone has filed. So a sync
 * only ever *fills in* printings and prices that already exist.
 */
class CatalogSync(private val api: TcgDex) {

    /**
     * What the sync found, as a delta rather than a finished snapshot.
     *
     * Returning the whole snapshot would mean a sync started before an edit and finished
     * after it silently reverts that edit. A delta is applied to whatever the collection
     * looks like when the answers actually arrive.
     */
    data class Result(
        val matched: Int,
        val unmatched: Int,
        /** Matched a real card whose name disagreed, and was therefore left alone. */
        val rejected: Int = 0,
        val printings: Map<PrintingId, Printing> = emptyMap(),
        val prices: Map<VariantId, PriceSnapshot> = emptyMap(),
        val failure: String? = null,
    ) {
        val pricesUpdated: Int get() = prices.size
        val attempted: Int get() = matched + unmatched
    }

    /**
     * Resolves and merges every printing in [snapshot].
     *
     * Requests run six at a time. Serially, a forty-card collection is forty round trips
     * and most of a minute of staring at a spinner; unbounded, it is forty simultaneous
     * connections and a rate limit.
     */
    suspend fun run(
        snapshot: CollectionSnapshot,
        nowEpochSeconds: Long = 0L,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result {
        val sets = runCatching { api.sets() }.getOrElse {
            return Result(matched = 0, unmatched = snapshot.printings.size, failure = UNREACHABLE)
        }

        val targets = snapshot.printings.values.map { printing ->
            printing.id to remoteIdFor(printing, sets)
        }
        val total = targets.size
        val gate = Semaphore(6)

        val fetched: List<Pair<PrintingId, RemoteCard?>> = coroutineScope {
            val inFlight = targets.map { (printingId, remoteId) ->
                async {
                    val card = if (remoteId == null) null else {
                        gate.withPermit { runCatching { api.card(remoteId) }.getOrNull() }
                    }
                    printingId to card
                }
            }
            // Progress is reported here rather than inside each coroutine: awaiting in
            // submission order gives a counter that only ever climbs, and needs no
            // synchronisation to be correct.
            inFlight.mapIndexed { index, deferred ->
                deferred.await().also { onProgress(index + 1, total) }
            }
        }

        val variantsByPrinting = snapshot.variants.values.groupBy { it.printingId }

        val printings = mutableMapOf<PrintingId, Printing>()
        val prices = mutableMapOf<VariantId, PriceSnapshot>()
        var matched = 0
        var rejected = 0

        for ((printingId, card) in fetched) {
            if (card == null) continue
            val existing = snapshot.printings[printingId] ?: continue

            // Set and number identified a real card -- but if it is not the card this
            // record says it is, the lookup was wrong, not the record. Numbering shifts
            // between a set and its reprints, and secret rares sit past the printed
            // total, so `<set>-<number>` lands on the wrong card often enough to matter.
            // Attaching the wrong picture and the wrong price to something in a user's
            // binder is far worse than leaving it as it was, so a name that disagrees
            // means the whole match is dropped.
            val localName = snapshot.cards[existing.cardId]?.name
            if (localName != null && !namesAgree(localName, card.name)) {
                rejected++
                continue
            }
            matched++

            printings[printingId] = (
                existing.copy(
                    // Only ever fills gaps. A rarity someone corrected by hand is a
                    // deliberate act; the upstream value is not more true than it.
                    imageUrl = card.image ?: existing.imageUrl,
                    rarity = existing.rarity ?: card.rarity,
                    illustrator = existing.illustrator ?: card.illustrator,
                )
                )

            for (variant in variantsByPrinting[printingId].orEmpty()) {
                val cents = card.marketPriceCents(priceKeysFor(variant.finish)) ?: continue
                prices[variant.id] = PriceSnapshot(
                    variantId = variant.id,
                    market = Money(cents),
                    source = "tcgplayer",
                    fetchedAtEpochSeconds = nowEpochSeconds,
                )
            }
        }

        return Result(
            matched = matched,
            unmatched = total - matched,
            rejected = rejected,
            printings = printings,
            prices = prices,
        )
    }

    /**
     * Whether two names describe the same card.
     *
     * Containment rather than equality, because suffixes drift: a record saying
     * "Charizard" and a catalog saying "Charizard ex" are the same card described at
     * different lengths, whereas "Blastoise ex" and "Alakazam ex" share only the suffix
     * and are not.
     */
    private fun namesAgree(local: String, remote: String): Boolean {
        val a = local.normalized()
        val b = remote.normalized()
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    /**
     * The upstream id for a local printing, or null if no set could be identified.
     *
     * TCGdex addresses a card as `<setId>-<number>`, so the whole problem is knowing which
     * set. Three checks, cheapest and most certain first -- and none of them guesses on
     * name alone, because "Base Set", "Base Set 2" and "Expedition Base Set" are three
     * different sets whose names all contain each other.
     */
    private fun remoteIdFor(printing: Printing, sets: Map<String, RemoteSet>): String? {
        val number = printing.number.trim().ifBlank { return null }
        val setId = resolveSetId(printing, sets) ?: return null
        return "$setId-$number"
    }

    private fun resolveSetId(printing: Printing, sets: Map<String, RemoteSet>): String? {
        // 1. The local set code already is an upstream id.
        sets[printing.setCode]?.let { return it.id }

        val localName = printing.setName.normalized()
        val localTotal = printing.setTotal?.trim()?.toIntOrNull()

        // 2. The names agree exactly.
        sets.values.firstOrNull { it.name.normalized() == localName }?.let { return it.id }

        // 3. One name contains the other *and* the sets are the same size. Either test
        //    alone is wrong: "151" is a substring of half the catalog, and dozens of sets
        //    share a card count.
        if (localTotal != null) {
            sets.values.firstOrNull { candidate ->
                val name = candidate.name.normalized()
                val sameSize = candidate.cardCount?.official == localTotal
                sameSize && (name.contains(localName) || localName.contains(name))
            }?.let { return it.id }
        }

        return null
    }

    /** Lowercased, stripped of punctuation and spacing, so "Scarlet & Violet" == "scarletviolet". */
    private fun String.normalized(): String = lowercase().filter { it.isLetterOrDigit() }

    private fun priceKeysFor(finish: Finish): List<String> = when (finish) {
        Finish.NON_HOLO -> listOf("normal", "1stEditionNormal")
        Finish.HOLO -> listOf("holofoil", "1stEditionHolofoil")
        Finish.REVERSE_HOLO -> listOf("reverseHolofoil", "holofoil")
        Finish.FULL_ART, Finish.TEXTURED, Finish.GOLD, Finish.OTHER ->
            listOf("holofoil", "normal", "reverseHolofoil")
    }

    private companion object {
        const val UNREACHABLE = "Could not reach the card catalog. Check your connection."
    }
}
