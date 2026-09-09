package app.pocketful.domain

import kotlinx.serialization.Serializable

/**
 * Identity is deliberately split into four levels. Conflating any two of these is the
 * mistake that makes collection apps unusable once a user owns more than one printing
 * of the same card:
 *
 *   Card      the game object.        "Charizard" -- HP, attacks, retreat cost
 *   Printing  where it was printed.   base1 #4 -- set, number, rarity, illustrator
 *   Variant   which press run.        1st Edition / Shadowless / Reverse / Japanese
 *   Copy      the physical object.    condition, grade, what you paid, where it lives
 *
 * Ids are human-legible composites rather than surrogate keys so that two devices can
 * merge collections without a server handing out identifiers. The levels reach that from
 * opposite directions, and which direction a given id took is worth knowing before
 * comparing two of them:
 *
 *  - [CardId], [PrintingId] and [VariantId] are *derived*. They are built from what the
 *    thing is -- `ptcg-base1-004-1sted` -- so two devices that have never met agree on
 *    them by construction, and an id in common means the same card.
 *  - [CopyId], [BinderId] and [ContainerId] are *minted*, because nothing about a
 *    physical card in a physical binder is derivable: two Charizards in identical
 *    condition are still two cards. They carry a random tail, so an id in common means
 *    the two records share an ancestor rather than merely a birth order. See
 *    `CollectionStore.mintId`, which is where that reasoning is written down.
 */

@Serializable
@JvmInline
value class CardId(val value: String) {
    override fun toString() = value
}

/** e.g. `ptcg-base1-004` */
@Serializable
@JvmInline
value class PrintingId(val value: String) {
    override fun toString() = value
}

/** e.g. `ptcg-base1-004-1sted` */
@Serializable
@JvmInline
value class VariantId(val value: String) {
    override fun toString() = value
}

/** Local to this collection; a copy is a physical object, so it never syncs upstream. */
@Serializable
@JvmInline
value class CopyId(val value: String) {
    override fun toString() = value
}

@Serializable
@JvmInline
value class BinderId(val value: String) {
    override fun toString() = value
}

/** Storage that is not a binder: a box, a deck, a case of slabs. */
@Serializable
@JvmInline
value class ContainerId(val value: String) {
    override fun toString() = value
}

/**
 * Minor units (cents). Never use floating point for money: summing a 900-card binder
 * accumulates enough error to be visibly wrong.
 */
@Serializable
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    operator fun times(factor: Int) = Money(cents * factor)
    override fun compareTo(other: Money) = cents.compareTo(other.cents)

    val isZero: Boolean get() = cents == 0L

    /** Compact display: $1.2k above four figures, otherwise grouped dollars and cents. */
    fun format(compact: Boolean = false): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val body = when {
            compact && abs >= 1_000_000_00L -> {
                val millions = abs / 1_000_000_00L
                val remainder = (abs % 1_000_000_00L) / 100_000_00L
                "$millions.${remainder}M"
            }
            compact && abs >= 1_000_00L -> {
                val thousands = abs / 1_000_00L
                val remainder = (abs % 1_000_00L) / 100_00L
                "$thousands.${remainder}k"
            }
            else -> group(abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
        }
        return if (negative) "-$$body" else "$$body"
    }

    companion object {
        val ZERO = Money(0)
        fun dollars(value: Double) = Money((value * 100).toLong())

        /**
         * Thousands separators, written out rather than delegated to a platform formatter:
         * `java.text.NumberFormat` is not available in common code, and a locale-aware
         * currency formatter would also fight the fixed "$" the rest of the app assumes.
         */
        private fun group(value: Long): String {
            val digits = value.toString()
            if (digits.length <= 3) return digits
            return buildString {
                digits.forEachIndexed { index, char ->
                    if (index > 0 && (digits.length - index) % 3 == 0) append(',')
                    append(char)
                }
            }
        }
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }
