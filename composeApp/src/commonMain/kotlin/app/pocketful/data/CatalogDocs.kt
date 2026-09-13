package app.pocketful.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The published catalog, as the files on Cloudflare R2 spell it.
 *
 * Written by the Pocketful Editor (Pocketful-Catalog, editor/backend/publish.py) when a set is
 * published, and designed in that repository's docs/database.md. Two kinds of file:
 *
 *  - `catalog/index.json`, the one file the app asks for by a built-in address. It lists every
 *    published catalog, series and set, with the version and file of each set, and names the
 *    public address every other path is relative to.
 *  - `catalog/sets/<set id>.v<version>.json.gz`, one per published version of a set. A version
 *    is never rewritten, so a file on the device is correct for as long as its version is the
 *    one the index lists.
 *
 * Every class here ignores fields it does not know and defaults fields that may be absent,
 * because the publisher leaves out anything empty and will grow fields over time.
 */

const val CATALOG_SCHEMA: Int = 2

@Serializable
data class IndexDoc(
    val schema: Int = 0,
    val generatedAt: String? = null,
    val publicUrl: String? = null,
    val catalogs: List<IndexCatalog> = emptyList(),
    /** Every variation word, with the label the app shows for it. */
    val words: Map<String, IndexWord> = emptyMap(),
    /** kind -> code -> language -> label, for rarities, types and subtypes. */
    val terms: Map<String, Map<String, Map<String, String>>> = emptyMap(),
)

@Serializable
data class IndexCatalog(
    val id: String,
    val game: String = "ptcg",
    val language: String = "en",
    val name: String = "",
    val nativeName: String? = null,
    /** The card back, shown for a card published without a picture. */
    val back: String? = null,
    val series: List<IndexSeries> = emptyList(),
)

@Serializable
data class IndexSeries(
    val id: String,
    val code: String = "",
    val name: String = "",
    val nameEn: String? = null,
    val logo: String? = null,
    /** A card back of the series' own, where its cards' back differs from the catalog's. */
    val back: String? = null,
    val sets: List<IndexSet> = emptyList(),
)

@Serializable
data class IndexSet(
    val id: String,
    val code: String = "",
    val name: String = "",
    val nameEn: String? = null,
    val kind: String = "expansion",
    val releaseDate: String? = null,
    val printedTotal: Int? = null,
    val abbreviation: String? = null,
    val logo: String? = null,
    val symbol: String? = null,
    val version: Int = 0,
    val publishedAt: String? = null,
    val file: String = "",
    val sha256: String? = null,
    val cards: Int = 0,
    val printings: Int = 0,
)

/** A variation word: which part of a printing it names, how it reads, and where it sorts among its kind. */
@Serializable
data class IndexWord(val kind: String = "", val label: String = "", val sort: Int = 0)

@Serializable
data class SetDoc(
    val schema: Int = 0,
    val id: String,
    val version: Int = 0,
    val publishedAt: String? = null,
    val series: String = "",
    val code: String = "",
    val name: String = "",
    val nameEn: String? = null,
    val kind: String = "expansion",
    val releaseDate: String? = null,
    val printedTotal: Int? = null,
    val abbreviation: String? = null,
    val logo: String? = null,
    val symbol: String? = null,
    val cards: List<SetCard> = emptyList(),
)

@Serializable
data class SetCard(
    val id: String,
    val number: String = "",
    val printedNumber: String? = null,
    val numberAssigned: Boolean = false,
    /** The subset a card belongs to inside its set (Trainer Gallery), or null. */
    val section: String? = null,
    val name: String = "",
    val nameEn: String? = null,
    val category: String = "pokemon",
    val subtypes: List<String> = emptyList(),
    val hp: Int? = null,
    val types: List<String> = emptyList(),
    val evolvesFrom: String? = null,
    val retreat: Int? = null,
    val rarity: String? = null,
    val regulationMark: String? = null,
    val illustrator: String? = null,
    val dexNumbers: List<Int> = emptyList(),
    val flavorText: String? = null,
    val image: String? = null,
    val thumb: String? = null,
    val printings: List<SetPrinting> = emptyList(),
)

@Serializable
data class SetPrinting(
    val id: String,
    val variant: String = "",
    val edition: String? = null,
    val pattern: String? = null,
    val finish: String = "normal",
    val stamps: List<String> = emptyList(),
    val error: String? = null,
    /** How to tell this printing apart from its siblings. */
    val identify: String? = null,
    /** Its own picture, when it looks different from the card's. */
    val image: String? = null,
    val thumb: String? = null,
)

/** Lenient for the same reason every reader here is: a new field upstream is not a crash. */
val CatalogJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * IDs in the published catalog, read for what they say.
 *
 * `ptcg-en-base01-4_1st-edition-holo` is a printing; everything before the underscore is its
 * card, and the first three dash-separated parts of that are its set. That holds by the
 * catalog's own rules: game, language and set code never contain a dash, and the card number
 * after them may.
 */
object CatalogIds {
    fun cardOf(printingId: String): String = printingId.substringBefore('_')

    fun setOf(cardOrPrintingId: String): String {
        val parts = cardOf(cardOrPrintingId).split('-')
        return if (parts.size >= 4) parts.take(3).joinToString("-") else ""
    }

    /** Whether an id belongs to the published catalog at all, rather than to a hand-added card. */
    fun isCatalogId(id: String): Boolean = setOf(id).isNotEmpty() && !id.startsWith("custom-")
}
