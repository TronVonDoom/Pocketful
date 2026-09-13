package app.pocketful.domain

import kotlinx.serialization.Serializable

/**
 * Catalog types: upstream, read-only, replaced wholesale when a catalog update lands.
 * Nothing a user types is ever stored here -- that is what [Copy] is for.
 */

@Serializable
data class Card(
    val id: CardId,
    val name: String,
    val supertype: Supertype,
    val subtypes: List<String> = emptyList(),
    val hp: Int? = null,
    val types: List<PokemonType> = emptyList(),
    val attacks: List<Attack> = emptyList(),
    val weakness: String? = null,
    val resistance: String? = null,
    val retreatCost: Int? = null,
    val flavorText: String? = null,
    val rules: List<String> = emptyList(),
)

enum class Supertype { POKEMON, TRAINER, ENERGY }

enum class PokemonType {
    GRASS, FIRE, WATER, LIGHTNING, PSYCHIC, FIGHTING, DARKNESS,
    METAL, FAIRY, DRAGON, COLORLESS
}

@Serializable
data class Attack(
    val name: String,
    val cost: List<PokemonType>,
    val damage: String?,
    val text: String?,
)

@Serializable
data class Printing(
    val id: PrintingId,
    val cardId: CardId,
    val setCode: String,
    val setName: String,
    val number: String,
    val setTotal: String?,
    val rarity: String?,
    val illustrator: String?,
    val releaseYear: Int?,
    /**
     * The card's picture, as a stem: its path in the catalog without the extension. See
     * [app.pocketful.data.CardArt], which turns it into a thumbnail or a full picture.
     */
    val image: String? = null,
    /**
     * The card back to show when there is no [image], as a stem.
     *
     * A card published without a picture anywhere is drawn as its back rather than as a blank
     * pocket. Which back is decided by the catalog -- the series' own where it has one, else the
     * catalog's -- and resolved once, when the card is filed.
     */
    val back: String? = null,
    /** The number exactly as printed, "4/102" or "TG01/TG30". Preferred over [number] with [setTotal]. */
    val printedNumber: String? = null,
) {
    /** What is actually printed in the corner, and the most reliable OCR target. */
    val collectorNumber: String get() = printedNumber ?: setTotal?.let { "$number/$it" } ?: number
}

@Serializable
data class Variant(
    val id: VariantId,
    val printingId: PrintingId,
    val finish: Finish,
    val edition: Edition = Edition.UNLIMITED,
    val language: Language = Language.EN,
    val note: String? = null,
    /**
     * Which special printing this is, as the catalog keys it -- `pokemon-center`,
     * `pokeball`, `1st-edition` -- or null for the plain press run of its finish.
     *
     * A string rather than another enum, because the hobby has a long tail of these and a
     * new stamp should reach the app with the catalog rather than with a release. Built from
     * the catalog printing's words that [finish] and [edition] do not already say, joined
     * with "+": `pokeball`, `1999-2000-copyright`, `pre-release+staff`.
     */
    val special: String? = null,
    /** How [special] reads to a person: "Poké Ball Pattern". Carried, not derived. */
    val specialLabel: String? = null,
    /** This printing's own picture, as a stem, when it looks different from its card's. */
    val image: String? = null,
) {
    /** Short badge text, omitting anything that is the unremarkable default. */
    val badge: String?
        get() = listOfNotNull(
            edition.takeIf { it != Edition.UNLIMITED }?.label,
            finish.takeIf { it != Finish.NON_HOLO || special != null }?.label,
            specialLabel ?: special,
            language.takeIf { it != Language.EN }?.name,
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Declared plainest-first: pickers and badges list these in order, and a collector reads
 * "Normal, Holo, Reverse Holo" as the natural sequence. The labels are the words printed
 * on price guides rather than the words in the enum -- nobody asks for a "non-holo".
 */
enum class Finish(val label: String) {
    NON_HOLO("Normal"),
    HOLO("Holo"),
    REVERSE_HOLO("Reverse Holo"),
    FULL_ART("Full Art"),
    TEXTURED("Textured"),
    GOLD("Gold"),
    OTHER("Variant"),
}

enum class Edition(val label: String) {
    UNLIMITED("Unlimited"),
    FIRST_EDITION("1st Ed"),
    SHADOWLESS("Shadowless"),
    PROMO_STAMP("Promo"),
    PRERELEASE("Prerelease"),
    STAFF("Staff"),
}

enum class Language { EN, JA, DE, FR, IT, ES, PT, KO, ZH }
