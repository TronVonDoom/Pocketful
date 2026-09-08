package app.pocketful.state

import androidx.compose.runtime.Composable
import app.pocketful.domain.Money

/**
 * How a money value renders in a list, tile or caption.
 *
 * Routed through the settings rather than hard-coding `compact = true` at forty call
 * sites, so "abbreviate large values" is a real preference instead of a switch that
 * writes itself to a field nobody reads. Headline figures deliberately keep calling
 * [Money.format] directly: the one number a screen is about is always shown in full.
 */
@Composable
fun Money.display(): String =
    if (LocalAppSettings.current.abbreviateValues) format(compact = true) else format()

/**
 * The price, or nothing at all when there is not one.
 *
 * Zero is not a price in this app -- it is what a card is worth when the catalog has
 * never quoted it, which is every card in a want list built from a set listing until the
 * next catalog update lands. Rendering that as "$0.00" tells a collector their Charizard
 * is worthless, in the same gold type used for real figures. Better to print nothing and
 * let the absence say the app does not know yet.
 */
@Composable
fun Money.displayOrNull(): String? = if (isZero) null else display()

/**
 * Price fields, in both directions.
 *
 * These began as private helpers inside the pocket sheet, which was fine while that was
 * the only place a price could be typed. It is not any more -- the search tab records
 * what you paid for a card it has just found -- and two independent ideas of what counts
 * as a valid price is exactly how one screen starts accepting "12.345" and the other does
 * not.
 */

/** Digits and a single decimal point, at most two places. Anything else is a typo. */
fun String.filterPriceInput(): String {
    val cleaned = filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot < 0) return cleaned
    val head = cleaned.substring(0, firstDot + 1)
    val tail = cleaned.substring(firstDot + 1).filter { it.isDigit() }.take(2)
    return head + tail
}

/** Null rather than zero for an empty field: "not recorded" is not the same as "free". */
fun String.toMoneyOrNull(): Money? =
    trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.let { Money.dollars(it) }

/** The plain form a price field edits, with no currency symbol or grouping. */
fun Money.toPriceInput(): String = "${cents / 100}." + (cents % 100).toString().padStart(2, '0')
