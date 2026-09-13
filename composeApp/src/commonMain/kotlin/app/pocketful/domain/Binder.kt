package app.pocketful.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * A binder is an ordered, dense list of slot contents plus a geometry. Storing it dense
 * and ordered (rather than keyed by page/row/col) is what makes re-flowing between
 * layouts a list operation instead of a migration.
 */
@Serializable
data class Binder(
    val id: BinderId,
    val name: String,
    val subtitle: String? = null,
    val layout: BinderLayout,
    val sheetCount: Int,
    val spineColor: Long = 0xFF3B82F6,
    /**
     * The catalog set this binder was built from, if it was built from one.
     *
     * Kept so the set's own page can say "you already have a binder for this" and open it,
     * rather than offering to build a second one. Matching on the name instead would break
     * the moment someone renames a binder, which is the first thing people do.
     */
    val sourceSetId: String? = null,
    val slots: List<SlotContent> = emptyList(),
) {
    val capacity: Int get() = layout.capacity(sheetCount)
    val faceCount: Int get() = layout.faceCount(sheetCount)

    /** Padded to capacity so callers never index out of bounds on a short list. */
    val paddedSlots: List<SlotContent>
        get() = if (slots.size >= capacity) slots.take(capacity)
        else slots + List(capacity - slots.size) { SlotContent.Empty }

    fun face(faceIndex: Int): List<SlotContent> {
        val all = paddedSlots
        return layout.ordinalsOnFace(faceIndex).map { all.getOrElse(it) { SlotContent.Empty } }
    }

    fun withSlot(ordinal: Int, content: SlotContent): Binder =
        copy(slots = paddedSlots.toMutableList().also { it[ordinal] = content })

    /** Swap two slots -- the primitive behind drag-and-drop rearranging. */
    fun swap(a: Int, b: Int): Binder {
        val next = paddedSlots.toMutableList()
        val tmp = next[a]
        next[a] = next[b]
        next[b] = tmp
        return copy(slots = next)
    }

    /**
     * Change geometry without scrambling the binder. [ReflowMode.PRESERVE_ORDER] lets the
     * sequence re-wrap into the new grid; [ReflowMode.PRESERVE_PAGES] keeps each face's
     * contents on its own face. Spacers survive both, which is the point of having them.
     */
    fun reflow(
        newLayout: BinderLayout,
        newSheetCount: Int = sheetCount,
        mode: ReflowMode = ReflowMode.PRESERVE_ORDER,
    ): Binder {
        val target = newLayout.capacity(newSheetCount)
        val next = when (mode) {
            ReflowMode.PRESERVE_ORDER -> paddedSlots.take(target)

            ReflowMode.PRESERVE_PAGES -> buildList {
                val faces = minOf(faceCount, newLayout.faceCount(newSheetCount))
                for (f in 0 until faces) {
                    val contents = face(f).take(newLayout.pocketsPerFace)
                    addAll(contents)
                    repeat(newLayout.pocketsPerFace - contents.size) { add(SlotContent.Empty) }
                }
            }
        }
        return copy(
            layout = newLayout,
            sheetCount = newSheetCount,
            slots = next + List((target - next.size).coerceAtLeast(0)) { SlotContent.Empty },
        )
    }
}

/**
 * Three kinds of "nothing here", not one. The distinction is what turns a binder from a
 * record of what you own into a working want-list.
 */
@Serializable
sealed interface SlotContent {
    /** Nothing here, and nothing planned. */
    @Serializable
    @SerialName("empty")
    data object Empty : SlotContent

    /** A card you own, sitting in this pocket. */
    @Serializable
    @SerialName("filled")
    data class Filled(val copyId: CopyId) : SlotContent

    /** A card you are hunting. Renders as a ghost and feeds cost-to-complete. */
    @Serializable
    @SerialName("wanted")
    data class Wanted(val variantId: VariantId, val targetPrice: Money? = null) : SlotContent

    /** Deliberately blank. Reflow moves it but never fills it. */
    @Serializable
    @SerialName("spacer")
    data class Spacer(val label: String? = null) : SlotContent
}

/** Rolled-up numbers for a binder or a whole collection. */
data class ValueSummary(
    val marketValue: Money,
    val costBasis: Money,
    val ownedCount: Int,
    val wantedCount: Int,
    val costToComplete: Money,
    /**
     * What [marketValue] is counted in.
     *
     * A collection can hold cards quoted in two markets -- TCGplayer in dollars, Cardmarket
     * in euros -- and a total has to pick one, because adding them produces a number that
     * is not money in any currency. So the total is struck in whichever currency the
     * collection is mostly worth, and [alsoIn] carries the rest rather than losing them.
     */
    val currency: Currency = Currency.USD,
    /** Totals in every other currency present, which [marketValue] does not include. */
    val alsoIn: Map<Currency, Money> = emptyMap(),
    /** How many cards are behind [marketValue] -- the honest divisor for an average. */
    val pricedCount: Int = 0,
    /**
     * The market value of only the copies with a price paid on record: the figure
     * [costBasis] is actually comparable with. Comparing what four cards cost with what
     * forty are worth was reporting a gain on every card nobody had typed a price for.
     */
    val basisMarketValue: Money = Money.ZERO,
    /** How far [marketValue] moved since the price file's previous day, over the copies quoted on both. */
    val dayChange: Money = Money.ZERO,
    /** What those same copies were worth on that previous day: the base [dayChange] is a share of. */
    val dayChangeBase: Money = Money.ZERO,
) {
    /** The day's move as a percentage, or null when nothing was quoted on both days. */
    val dayChangePercent: Double?
        get() = if (dayChangeBase.isZero || currency != Currency.USD) null
        else dayChange.cents.toDouble() / dayChangeBase.cents.toDouble() * 100.0

    /**
     * What the average card in [currency] is worth, or null when nothing is priced.
     *
     * Divided by the cards that actually have a price in this currency rather than by the
     * size of the collection, which is the difference between "your cards average 7 euros"
     * and a number diluted by every card nobody has ever quoted.
     */
    val averageValue: Money?
        get() = if (pricedCount <= 0) null else Money(marketValue.cents / pricedCount)

    /** "also $0.18" -- the totals this summary's currency does not cover. */
    val alsoLabel: String?
        get() = alsoIn.takeIf { it.isNotEmpty() }
            ?.entries
            ?.sortedByDescending { it.value.cents }
            ?.joinToString(" · ") { it.value.format(currency = it.key) }

    val unrealizedGain: Money get() = basisMarketValue - costBasis

    /**
     * Gain against what was paid, or null when the comparison would be meaningless.
     *
     * What someone paid is a figure they typed, in their own money. Subtracting it from a
     * total struck in euros is not a smaller gain or a bigger one -- it is not a quantity
     * at all. Null there, and every screen already treats a null gain as "do not show a
     * gain", so the number simply does not appear rather than appearing wrong.
     */
    val gainPercent: Double?
        get() = when {
            costBasis.isZero -> null
            currency != Currency.USD || alsoIn.isNotEmpty() -> null
            else -> (unrealizedGain.cents.toDouble() / costBasis.cents.toDouble()) * 100.0
        }

    /**
     * Formatted here rather than at each call site: the shelf and the binder header show
     * the same binder, and truncating in one place while rounding in the other made the
     * same holding read as +38% and +39% on adjacent screens.
     */
    val gainPercentLabel: String?
        get() = gainPercent?.roundToInt()?.let { rounded ->
            (if (rounded >= 0) "+" else "") + rounded + "%"
        }

    companion object {
        val EMPTY = ValueSummary(Money.ZERO, Money.ZERO, 0, 0, Money.ZERO)
    }
}
