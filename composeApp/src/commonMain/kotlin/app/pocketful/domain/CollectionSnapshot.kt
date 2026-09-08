package app.pocketful.domain

/**
 * An immutable read-model joining the three layers (catalog / collection / market) into
 * something the UI can render without knowing how any of them are stored. Swapping the
 * in-memory store for a real database means replacing what builds this, nothing else.
 */
data class CollectionSnapshot(
    val cards: Map<CardId, Card> = emptyMap(),
    val printings: Map<PrintingId, Printing> = emptyMap(),
    val variants: Map<VariantId, Variant> = emptyMap(),
    val copies: Map<CopyId, Copy> = emptyMap(),
    val prices: Map<VariantId, PriceSnapshot> = emptyMap(),
    val binders: List<Binder> = emptyList(),
    val containers: List<Container> = emptyList(),
) {
    fun binder(id: BinderId): Binder? = binders.firstOrNull { it.id == id }

    fun container(id: ContainerId): Container? = containers.firstOrNull { it.id == id }

    fun marketValue(variantId: VariantId): Money =
        prices[variantId]?.market ?: Money.ZERO

    /**
     * Condition scales the market price. Graded copies are deliberately *not* scaled --
     * a PSA 10 does not trade at raw NM price, and pretending otherwise is worse than
     * showing the raw number until real graded pricing is wired up.
     */
    fun valueOf(copy: Copy): Money {
        val base = marketValue(copy.variantId)
        if (copy.isGraded) return base
        return Money((base.cents * copy.condition.multiplier).toLong())
    }

    fun summarize(binder: Binder): ValueSummary {
        var market = Money.ZERO
        var basis = Money.ZERO
        var owned = 0
        var wanted = 0
        var toComplete = Money.ZERO

        for (slot in binder.paddedSlots) {
            when (slot) {
                is SlotContent.Filled -> copies[slot.copyId]?.let { copy ->
                    owned++
                    market += valueOf(copy)
                    basis += copy.acquiredPrice ?: Money.ZERO
                }
                is SlotContent.Wanted -> {
                    wanted++
                    toComplete += slot.targetPrice ?: marketValue(slot.variantId)
                }
                SlotContent.Empty, is SlotContent.Spacer -> Unit
            }
        }
        return ValueSummary(market, basis, owned, wanted, toComplete)
    }

    /** A container's contribution: real cards, but no pockets and so nothing wanted. */
    fun summarize(container: Container): ValueSummary {
        var market = Money.ZERO
        var basis = Money.ZERO
        var owned = 0
        for (copyId in container.copyIds) {
            val copy = copies[copyId] ?: continue
            owned++
            market += valueOf(copy)
            basis += copy.acquiredPrice ?: Money.ZERO
        }
        return ValueSummary(market, basis, owned, wantedCount = 0, costToComplete = Money.ZERO)
    }

    /**
     * The whole portfolio.
     *
     * Summed over every copy rather than over the binders, so a card sitting in a box --
     * or one that is not filed anywhere yet -- still counts toward what the collection is
     * worth. Wants are binder-only because a pocket is what holds the gap open.
     */
    fun summarizeAll(): ValueSummary {
        var market = Money.ZERO
        var basis = Money.ZERO
        for (copy in copies.values) {
            market += valueOf(copy)
            basis += copy.acquiredPrice ?: Money.ZERO
        }

        var wanted = 0
        var toComplete = Money.ZERO
        for (binder in binders) {
            for (slot in binder.paddedSlots) {
                if (slot !is SlotContent.Wanted) continue
                wanted++
                toComplete += slot.targetPrice ?: marketValue(slot.variantId)
            }
        }
        return ValueSummary(market, basis, copies.size, wanted, toComplete)
    }

    /** Copies that are not in a binder pocket and not in a container. */
    fun unfiledCopies(): List<Copy> {
        val filed = buildSet {
            for (binder in binders) {
                for (slot in binder.paddedSlots) if (slot is SlotContent.Filled) add(slot.copyId)
            }
            for (container in containers) addAll(container.copyIds)
        }
        return copies.values.filter { it.id !in filed }
    }

    /** Flatten a slot into everything the pocket needs to draw itself. */
    fun view(slot: SlotContent): SlotView = when (slot) {
        SlotContent.Empty -> SlotView.Empty
        is SlotContent.Spacer -> SlotView.Spacer(slot.label)

        is SlotContent.Filled -> {
            val copy = copies[slot.copyId]
            val variant = copy?.let { variants[it.variantId] }
            val printing = variant?.let { printings[it.printingId] }
            val card = printing?.let { cards[it.cardId] }
            if (copy == null || variant == null || printing == null || card == null) {
                SlotView.Broken(slot.copyId.value)
            } else {
                SlotView.CardSlot(
                    name = card.name,
                    collectorNumber = printing.collectorNumber,
                    setName = printing.setName,
                    type = card.types.firstOrNull(),
                    finish = variant.finish,
                    badge = variant.badge,
                    imageUrl = printing.imageUrl,
                    value = valueOf(copy),
                    conditionShort = copy.condition.short,
                    gradeLabel = copy.grade?.label,
                    owned = true,
                )
            }
        }

        is SlotContent.Wanted -> {
            val variant = variants[slot.variantId]
            val printing = variant?.let { printings[it.printingId] }
            val card = printing?.let { cards[it.cardId] }
            if (variant == null || printing == null || card == null) {
                SlotView.Broken(slot.variantId.value)
            } else {
                SlotView.CardSlot(
                    name = card.name,
                    collectorNumber = printing.collectorNumber,
                    setName = printing.setName,
                    type = card.types.firstOrNull(),
                    finish = variant.finish,
                    badge = variant.badge,
                    imageUrl = printing.imageUrl,
                    value = slot.targetPrice ?: marketValue(slot.variantId),
                    conditionShort = null,
                    gradeLabel = null,
                    owned = false,
                )
            }
        }
    }
}

/** What a single pocket needs in order to render. */
sealed interface SlotView {
    data object Empty : SlotView
    data class Spacer(val label: String?) : SlotView
    data class Broken(val reference: String) : SlotView

    data class CardSlot(
        val name: String,
        val collectorNumber: String,
        val setName: String,
        val type: PokemonType?,
        val finish: Finish,
        val badge: String?,
        val imageUrl: String?,
        val value: Money,
        val conditionShort: String?,
        val gradeLabel: String?,
        val owned: Boolean,
    ) : SlotView {
        val isHolo: Boolean
            get() = finish != Finish.NON_HOLO
    }
}
