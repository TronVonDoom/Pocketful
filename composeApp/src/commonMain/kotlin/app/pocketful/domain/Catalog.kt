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
    /** A TCGdex stem, which takes a quality and a format appended. */
    val imageUrl: String? = null,
    /**
     * A finished image URL, for the cards TCGdex has no stem for at all.
     *
     * About 7% of the catalog has no TCGdex asset in any language -- whole Trainer Kits,
     * Shining Fates' Shiny Vault, Ancient Mew -- and the published catalog resolves what
     * it can from a second source. Those are complete URLs on somebody else's CDN, so
     * they take no quality suffix and cannot be stored in [imageUrl] without every reader
     * of that field having to guess which kind of string it holds.
     *
     * Kept beside rather than instead, so a card that later gains real TCGdex art starts
     * using it without anything needing to notice.
     */
    val imageAltUrl: String? = null,
) {
    /** What is actually printed in the corner, and the most reliable OCR target. */
    val collectorNumber: String get() = setTotal?.let { "$number/$it" } ?: number
}

@Serializable
data class Variant(
    val id: VariantId,
    val printingId: PrintingId,
    val finish: Finish,
    val edition: Edition = Edition.UNLIMITED,
    val language: Language = Language.EN,
    val note: String? = null,
) {
    /** Short badge text, omitting anything that is the unremarkable default. */
    val badge: String?
        get() = listOfNotNull(
            edition.takeIf { it != Edition.UNLIMITED }?.label,
            finish.takeIf { it != Finish.NON_HOLO }?.label,
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
