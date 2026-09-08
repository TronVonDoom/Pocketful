package app.pocketful.domain

/**
 * Catalog types: upstream, read-only, replaced wholesale when a catalog update lands.
 * Nothing a user types is ever stored here -- that is what [Copy] is for.
 */

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

data class Attack(
    val name: String,
    val cost: List<PokemonType>,
    val damage: String?,
    val text: String?,
)

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
    val imageUrl: String? = null,
) {
    /** What is actually printed in the corner, and the most reliable OCR target. */
    val collectorNumber: String get() = setTotal?.let { "$number/$it" } ?: number
}

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

enum class Finish(val label: String) {
    NON_HOLO("Non-holo"),
    HOLO("Holo"),
    REVERSE_HOLO("Reverse"),
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
