package app.pocketful.domain

/**
 * A flattened variant, ready to be listed.
 *
 * Search, the card list and the stats leaderboard all need the same four-level join
 * resolved down to one row. Doing it once here keeps three screens from each inventing
 * their own slightly different idea of what a card is called.
 */
data class CardBrief(
    val variantId: VariantId,
    /**
     * The printing this variant is one press run of. Carried so a screen holding a brief
     * can ask what else the same card was printed as without walking back through the
     * variant table itself -- see [CollectionSnapshot.variantBriefs].
     */
    val printingId: PrintingId,
    val name: String,
    val setName: String,
    val setCode: String,
    val number: String,
    val collectorNumber: String,
    val rarity: String?,
    val type: PokemonType?,
    val finish: Finish,
    val badge: String?,
    val marketValue: Money,
    /** What [marketValue] is counted in. See CollectionSnapshot.currencyOf. */
    val currency: Currency = Currency.USD,
    /**
     * The picture, as a stem: the printing's own where it has one, else its card's. The row
     * that wants a thumbnail and the sheet that wants the full picture ask [app.pocketful.data.CardArt]
     * for different sizes of the same string.
     */
    val art: String? = null,
    /** The card back to draw when there is no [art]. See Printing.back. */
    val back: String? = null,
    /** How far [marketValue] moved since the price file's previous day, or null. */
    val change: Money? = null,
) {
    /** Everything a text query should be able to hit, lowercased once. */
    val searchIndex: String = buildString {
        append(name.lowercase())
        append(' ')
        append(setName.lowercase())
        append(' ')
        append(setCode.lowercase())
        append(' ')
        append(collectorNumber.lowercase())
        rarity?.let { append(' '); append(it.lowercase()) }
        append(' ')
        append(finish.label.lowercase())
        badge?.let { append(' '); append(it.lowercase()) }
    }

    /** Leading digits of the collector number, for ordering a set the way it was printed. */
    val numericOrder: Int = number.takeWhile { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
}

fun CollectionSnapshot.brief(variantId: VariantId): CardBrief? {
    val variant = variants[variantId] ?: return null
    val printing = printings[variant.printingId] ?: return null
    val card = cards[printing.cardId] ?: return null
    return CardBrief(
        variantId = variantId,
        printingId = printing.id,
        name = card.name,
        setName = printing.setName,
        setCode = printing.setCode,
        number = printing.number,
        collectorNumber = printing.collectorNumber,
        rarity = printing.rarity,
        type = card.types.firstOrNull(),
        finish = variant.finish,
        badge = variant.badge,
        marketValue = marketValue(variantId),
        currency = currencyOf(variantId),
        art = variant.image ?: printing.image,
        back = printing.back,
        change = changeOf(variantId),
    )
}

/**
 * Every finish the same card was printed in, as rows.
 *
 * The catalog import fans one upstream document out into a variant per press run, so a
 * modern rare already exists in the collection as a normal, a holo and a reverse the
 * moment it is fetched -- but only one of those is the card in your hand. This is what
 * the acquire sheets offer as a choice, which is why it returns the *whole* set including
 * the one asked about rather than the others: a picker missing the option it is currently
 * showing reads as a bug.
 *
 * Ordered by [Finish] and then [Edition], both of which are declared plainest-first, so
 * "Normal" leads and the exotic finishes trail in the order a collector would list them.
 * The special printings -- the stamped and pattern copies -- come after every plain one,
 * in the order the catalog lists them.
 */
fun CollectionSnapshot.variantBriefs(variantId: VariantId): List<CardBrief> {
    val printingId = variants[variantId]?.printingId ?: return listOfNotNull(brief(variantId))
    return variants.values
        .filter { it.printingId == printingId }
        .sortedWith(
            compareBy(
                { it.special != null },
                { it.finish.ordinal },
                { it.edition.ordinal },
                { it.language.ordinal },
            ),
        )
        .mapNotNull { brief(it.id) }
}

/** The whole catalog as rows, in set then printed order. One row per press run. */
fun CollectionSnapshot.allBriefs(): List<CardBrief> =
    variants.keys.mapNotNull { brief(it) }
        .sortedWith(compareBy({ it.setName }, { it.numericOrder }, { it.number }))

/**
 * The same rows, collapsed to one per printing.
 *
 * The catalog fans a single upstream card out into a variant per press run, so importing
 * one Grubbin can put a normal *and* a reverse holo in the catalog -- and a picker that
 * lists variants shows two rows with the same art, the same name and the same number
 * under them, which reads as the app having saved the card twice.
 *
 * So anything whose job is "pick a card" lists printings, and the finish is chosen in the
 * sheet that opens afterwards, where each one's price is visible next to it. The plainest
 * finish stands for the group, matching the order [variantBriefs] offers them in, so the
 * representative row is also the picker's default answer.
 */
fun List<CardBrief>.byPrinting(): List<CardBrief> =
    groupBy { it.printingId }.values.map { group ->
        // A stamped copy shares its finish with the plain one and badges longer than it, so
        // the shorter badge is what keeps the Pokémon Center Tyrunt from standing for Tyrunt.
        group.minWith(compareBy({ it.finish.ordinal }, { it.badge?.length ?: 0 }))
    }

/**
 * Ranked search. Exact and prefix name matches are floated above substring hits, because
 * typing "cha" and getting Machamp before Charizard is the thing that makes a card picker
 * feel broken even when every result technically matches.
 */
fun List<CardBrief>.search(query: String, limit: Int = 60): List<CardBrief> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return take(limit)
    return asSequence()
        .mapNotNull { brief ->
            val name = brief.name.lowercase()
            val rank = when {
                name == q -> 0
                name.startsWith(q) -> 1
                name.split(' ').any { it.startsWith(q) } -> 2
                brief.collectorNumber.lowercase().startsWith(q) -> 3
                name.contains(q) -> 4
                brief.searchIndex.contains(q) -> 5
                else -> null
            }
            rank?.let { it to brief }
        }
        .sortedWith(compareBy({ it.first }, { -it.second.marketValue.cents }))
        .map { it.second }
        .take(limit)
        .toList()
}

/**
 * One owned card, joined to its catalog row and to wherever it currently lives.
 *
 * [holderName] is whatever is holding it -- a binder or a container -- resolved here so
 * that the card list never has to look up two different collections to caption a row.
 */
/**
 * A market total for an arbitrary handful of cards, struck per currency.
 *
 * The same rule [ValueSummary] follows, in the shape a screen needs when it is totalling a
 * list it assembled itself rather than a binder or a container -- the All Cards screen and
 * the trade table both do. Folding `value` across the rows was the obvious thing to write
 * and it adds euros to dollars, which is the one arithmetic error this app must not make.
 */
data class MarketTotal(
    val amount: Money,
    val currency: Currency = Currency.USD,
    val alsoIn: Map<Currency, Money> = emptyMap(),
) {
    /** "also EUR 39.55" -- what this total does not cover. */
    val alsoLabel: String?
        get() = alsoIn.takeIf { it.isNotEmpty() }
            ?.entries
            ?.sortedByDescending { it.value.cents }
            ?.joinToString(" · ") { it.value.format(currency = it.key) }
}

/** Totals rows the way a summary totals a binder: by currency, never across them. */
fun List<CopyRow>.marketTotal(snapshot: CollectionSnapshot): MarketTotal {
    val totals = mutableMapOf<Currency, Long>()
    for (row in this) {
        if (row.value.isZero) continue
        val currency = snapshot.currencyOf(row.copy.variantId)
        totals[currency] = (totals[currency] ?: 0L) + row.value.cents
    }
    val leading = totals.maxByOrNull { it.value } ?: return MarketTotal(Money.ZERO)
    return MarketTotal(
        amount = Money(leading.value),
        currency = leading.key,
        alsoIn = totals.filterKeys { it != leading.key }.mapValues { Money(it.value) },
    )
}

data class CopyRow(
    val copy: Copy,
    val brief: CardBrief,
    val value: Money,
    val holderName: String?,
    val ordinal: Int?,
) {
    val locationLabel: String
        get() = when (val location = copy.location) {
            is Location.BinderSlot -> holderName?.let { "$it · Pocket ${(ordinal ?: 0) + 1}" } ?: "Filed"
            is Location.InContainer -> holderName ?: "Stored"
            Location.Unassigned -> "Unfiled"
            is Location.AtGrading -> "At ${location.company.name}"
            is Location.Lent -> "Lent to ${location.toWhom}"
        }

    val isUnfiled: Boolean get() = copy.location == Location.Unassigned
}

/**
 * One copy you are willing to part with, plus where it currently sits.
 *
 * A trade row is a [CopyRow] you have flagged, so it is derived rather than stored: the
 * flag lives on the copy, and there is no second list that can disagree with it about
 * what is actually on the table.
 */
fun CollectionSnapshot.tradeRows(): List<CopyRow> =
    copyRows().filter { it.copy.forTrade }.sortedByDescending { it.value.cents }

fun CollectionSnapshot.copyRows(): List<CopyRow> {
    val binderNames = binders.associate { it.id to it.name }
    val containerNames = containers.associate { it.id to it.name }
    return copies.values.mapNotNull { copy ->
        val brief = brief(copy.variantId) ?: return@mapNotNull null
        val slot = copy.location as? Location.BinderSlot
        CopyRow(
            copy = copy,
            brief = brief,
            value = valueOf(copy),
            holderName = when (val location = copy.location) {
                is Location.BinderSlot -> binderNames[location.binderId]
                is Location.InContainer -> containerNames[location.containerId]
                else -> null
            },
            ordinal = slot?.ordinal,
        )
    }
}

/** Every card marked wanted anywhere, with the binder and pocket that is holding the gap. */
data class WantRow(
    val brief: CardBrief,
    val binderId: BinderId,
    val binderName: String,
    val ordinal: Int,
    val targetPrice: Money,
)

fun CollectionSnapshot.wantRows(): List<WantRow> = buildList {
    for (binder in binders) {
        binder.paddedSlots.forEachIndexed { ordinal, slot ->
            if (slot !is SlotContent.Wanted) return@forEachIndexed
            val brief = brief(slot.variantId) ?: return@forEachIndexed
            add(
                WantRow(
                    brief = brief,
                    binderId = binder.id,
                    binderName = binder.name,
                    ordinal = ordinal,
                    targetPrice = slot.targetPrice ?: brief.marketValue,
                ),
            )
        }
    }
}
