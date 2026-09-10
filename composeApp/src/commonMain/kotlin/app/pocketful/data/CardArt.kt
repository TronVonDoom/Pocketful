package app.pocketful.data

/**
 * Card art, addressed by size.
 *
 * TCGdex serves every rendition of a card off one stem -- `.../en/base/base1/4` -- and
 * the caller appends a quality and a format. Keeping that suffix logic here rather than
 * storing a finished URL means a list row can ask for the 20KB webp and a detail sheet
 * for the 360KB png without the catalog holding two strings that can disagree about
 * which card they point at.
 *
 * Some cards have no stem at all. TCGdex has no artwork for about 7% of the catalog in
 * any language, and the published catalog fills what it can from a second source -- those
 * arrive as complete URLs on somebody else's CDN, which take no quality suffix and come
 * in one size. So every accessor here takes both and prefers the stem: a real TCGdex
 * rendition is the right size for the job, and the fallback is what there is otherwise.
 */
object CardArt {
    /** A list thumbnail. Small enough that a screen of forty rows is one screenful of data. */
    fun thumb(stem: String?, url: String? = null): String? =
        stem?.let { "$it/low.webp" } ?: url

    /** Full render, for a pocket blown up or a detail sheet. */
    fun full(stem: String?, url: String? = null): String? =
        stem?.let { "$it/high.png" } ?: url

    /**
     * A set or series logo.
     *
     * Logos are the one asset TCGdex serves without a quality step -- the stem takes a
     * format directly. PNG rather than webp because these are drawn on a dark ground and
     * the PNG is the one with a transparent background.
     */
    fun logo(stem: String?): String? = stem?.let { "$it.png" }

    /**
     * What a pocket shows. A binder page draws nine to sixteen of these at once, so it
     * takes the small one -- at pocket size the high render is invisible detail paid for
     * in megabytes.
     */
    fun pocket(stem: String?, url: String? = null): String? = thumb(stem, url)
}
