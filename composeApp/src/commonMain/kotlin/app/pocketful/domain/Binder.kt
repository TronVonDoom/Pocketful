package app.pocketful.domain

import kotlin.math.roundToInt

/**
 * A binder is an ordered, dense list of slot contents plus a geometry. Storing it dense
 * and ordered (rather than keyed by page/row/col) is what makes re-flowing between
 * layouts a list operation instead of a migration.
 */
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
sealed interface SlotContent {
    /** Nothing here, and nothing planned. */
    data object Empty : SlotContent

    /** A card you own, sitting in this pocket. */
    data class Filled(val copyId: CopyId) : SlotContent

    /** A card you are hunting. Renders as a ghost and feeds cost-to-complete. */
    data class Wanted(val variantId: VariantId, val targetPrice: Money? = null) : SlotContent

    /** Deliberately blank. Reflow moves it but never fills it. */
    data class Spacer(val label: String? = null) : SlotContent
}

/** Rolled-up numbers for a binder or a whole collection. */
data class ValueSummary(
    val marketValue: Money,
    val costBasis: Money,
    val ownedCount: Int,
    val wantedCount: Int,
    val costToComplete: Money,
) {
    val unrealizedGain: Money get() = marketValue - costBasis
    val gainPercent: Double?
        get() = if (costBasis.isZero) null
        else (unrealizedGain.cents.toDouble() / costBasis.cents.toDouble()) * 100.0

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
