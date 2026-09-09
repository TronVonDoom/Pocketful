package app.pocketful.domain

import kotlinx.serialization.Serializable

/**
 * The physical geometry of a binder page.
 *
 * A *sheet* is one piece of plastic. A *face* is one side of a sheet -- what you see
 * when the binder is open at a given page. Everything the UI shows is addressed by face,
 * because we only ever render one face at a time on a phone.
 *
 * Dimensions are stated the way people describe a binder out loud: columns across, rows
 * down. A 12-pocket page is four across and three down, which is [cols] = 4, [rows] = 3.
 * Naming a layout only by its pocket count is not enough -- twelve pockets could be 4x3,
 * 3x4 or 6x2 -- so the shape, not the total, is the identity.
 */
@Serializable
data class BinderLayout(
    val cols: Int,
    val rows: Int,
    val doubleSided: Boolean = true,
) {
    init {
        require(rows in 1..MAX_SIDE && cols in 1..MAX_SIDE) {
            "Binder pages are between 1 and $MAX_SIDE pockets on a side"
        }
    }

    /**
     * Derived rather than stored: a geometry is its shape, so any two layouts with the
     * same shape are interchangeable and compare equal, whether they came from the preset
     * list or from the custom picker.
     */
    val id: String get() = "${cols}x$rows" + if (doubleSided) "" else "s"

    val pocketsPerFace: Int get() = rows * cols
    val facesPerSheet: Int get() = if (doubleSided) 2 else 1
    val pocketsPerSheet: Int get() = pocketsPerFace * facesPerSheet

    /** What the binder is called on the shelf: "12-pocket". */
    val displayName: String get() = "$pocketsPerFace-pocket"

    /** What the binder actually looks like: "4 x 3". Disambiguates same-count shapes. */
    val gridLabel: String get() = "$cols × $rows"

    /** Both, for the one place that has room to be unambiguous. */
    val fullLabel: String get() = "$displayName · $gridLabel"

    /** True for the shapes people buy off a shelf, which the picker offers as chips. */
    val isPreset: Boolean get() = presets.any { it == this }

    fun faceCount(sheetCount: Int): Int = sheetCount * facesPerSheet
    fun capacity(sheetCount: Int): Int = sheetCount * pocketsPerSheet

    /** Global slot ordinal -> where it physically sits. */
    fun locate(ordinal: Int): SlotLocation {
        val faceIndex = ordinal / pocketsPerFace
        val withinFace = ordinal % pocketsPerFace
        return SlotLocation(
            faceIndex = faceIndex,
            sheetIndex = faceIndex / facesPerSheet,
            side = if (!doubleSided || faceIndex % 2 == 0) SheetSide.FRONT else SheetSide.BACK,
            row = withinFace / cols,
            col = withinFace % cols,
        )
    }

    fun ordinalOf(faceIndex: Int, row: Int, col: Int): Int =
        faceIndex * pocketsPerFace + row * cols + col

    fun ordinalsOnFace(faceIndex: Int): IntRange {
        val start = faceIndex * pocketsPerFace
        return start until (start + pocketsPerFace)
    }

    companion object {
        /** Twelve across is already absurd; this only exists to keep the steppers sane. */
        const val MAX_SIDE = 8

        // Named by pocket count because that is what the packaging says, defined by shape
        // because that is what the pages are.
        val POCKET_1 = BinderLayout(cols = 1, rows = 1)
        val POCKET_2 = BinderLayout(cols = 2, rows = 1)
        val POCKET_3 = BinderLayout(cols = 3, rows = 1)
        val POCKET_4 = BinderLayout(cols = 2, rows = 2)
        val POCKET_6 = BinderLayout(cols = 3, rows = 2)
        val POCKET_8 = BinderLayout(cols = 4, rows = 2)
        val POCKET_9 = BinderLayout(cols = 3, rows = 3)
        val POCKET_12 = BinderLayout(cols = 4, rows = 3)
        val POCKET_16 = BinderLayout(cols = 4, rows = 4)
        val POCKET_20 = BinderLayout(cols = 5, rows = 4)

        /** Ordered for a picker; 9-pocket is the default everyone expects. */
        val presets = listOf(
            POCKET_9, POCKET_12, POCKET_4, POCKET_16, POCKET_6,
            POCKET_8, POCKET_20, POCKET_3, POCKET_2, POCKET_1,
        )

        /** Clamps anything a stepper or an import can produce into a legal page. */
        fun of(cols: Int, rows: Int, doubleSided: Boolean = true) = BinderLayout(
            cols = cols.coerceIn(1, MAX_SIDE),
            rows = rows.coerceIn(1, MAX_SIDE),
            doubleSided = doubleSided,
        )
    }
}

enum class SheetSide { FRONT, BACK }

data class SlotLocation(
    val faceIndex: Int,
    val sheetIndex: Int,
    val side: SheetSide,
    val row: Int,
    val col: Int,
)

/** How to redistribute existing contents when a binder's layout changes. */
enum class ReflowMode {
    /** Keep the sequence intact and let it re-wrap into the new grid. */
    PRESERVE_ORDER,

    /** Keep each face's contents on its own face, padding or overflowing as needed. */
    PRESERVE_PAGES,
}
