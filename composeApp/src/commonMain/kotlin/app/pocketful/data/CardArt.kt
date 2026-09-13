package app.pocketful.data

/**
 * Card art, addressed by size.
 *
 * The catalog keeps every picture on Cloudflare R2 beside a thumbnail of it:
 * `images/cards/ptcg-en-base01/ptcg-en-base01-4.3f9a2c.webp` and the same name ending
 * `.thumb.webp`. The app holds the part the two share -- the *stem*, without the extension --
 * so a list row asks for the thumbnail and a detail sheet for the full picture without the
 * catalog carrying two strings that could disagree about which card they show.
 *
 * Stems are relative. The public address in front of them is the catalog's, read from its
 * index, so the day the pictures move to another domain nothing stored on the device changes:
 * only [base] does. [cacheKey] is what the image cache files a picture under for the same
 * reason -- a new address must not mean downloading every picture again.
 *
 * A card with no picture has a card back instead, which is a stem like any other; every
 * accessor takes both and prefers the card's own.
 */
object CardArt {
    /** Where the catalog's files are served from. Replaced by the index's own address once it loads. */
    var base: String = CatalogDownload.BUILT_IN_BASE

    /** A list thumbnail. */
    fun thumb(stem: String?, back: String? = null): String? =
        (stem ?: back)?.let { "$base/$it.thumb.webp" }

    /** The full picture, for a pocket blown up or a detail sheet. */
    fun full(stem: String?, back: String? = null): String? =
        (stem ?: back)?.let { "$base/$it.webp" }

    /** What a pocket shows: the thumbnail, since a page draws nine to sixteen at once. */
    fun pocket(stem: String?, back: String? = null): String? = thumb(stem, back)

    /** A set or series logo, or a set symbol. These are whole paths; they have no thumbnail. */
    fun logo(path: String?): String? = path?.let { "$base/$it" }

    /** The key a picture is cached under: its path, not its address. */
    fun cacheKey(url: String): String = url.removePrefix(base).trimStart('/')

    /** A picture's path without its extension, as the catalog documents' `image` fields are turned into stems. */
    fun stemOf(path: String?): String? = path?.removeSuffix(".webp")
}
