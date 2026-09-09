package app.pocketful.data

import app.pocketful.domain.Binder
import app.pocketful.domain.BinderLayout
import app.pocketful.domain.Card
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Container
import app.pocketful.domain.Copy
import app.pocketful.domain.PriceSnapshot
import app.pocketful.domain.Printing
import app.pocketful.domain.SlotContent
import app.pocketful.domain.Variant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the app writes to disk, and how it reads it back.
 *
 * Two files rather than one, split by what it costs to lose them:
 *
 *  - [CollectionSave] is the binders, boxes and cards. It is the only thing here that
 *    cannot be fetched again, so it is the only thing that is genuinely precious.
 *  - [CatalogCache] is the card catalog and its prices -- upstream data the app already
 *    knows how to re-fetch. Losing it costs one slow launch.
 *
 * Keeping them apart is the point of the split: a cache file that fails to parse must
 * never be able to take a collection down with it, and the cache can be rewritten or
 * dropped on a whim precisely because nothing in it is the user's.
 *
 * Neither file stores a [CollectionSnapshot] directly. The snapshot is a read model that
 * joins these two halves together, and persisting it whole would mean writing a
 * collection and a catalog into one document that has to be re-read in full to change
 * either.
 */

/** The current on-disk shape. Bump when a change cannot be read by the code before it. */
const val SAVE_SCHEMA: Int = 1

const val COLLECTION_FILE: String = "collection.json"
const val CATALOG_CACHE_FILE: String = "catalog-cache.json"

/**
 * The user's own data.
 *
 * Stored as lists rather than as the maps the snapshot holds. Every record already
 * carries its own id, so a map would write each id twice and invite the two copies to
 * disagree -- and JSON object keys would have to be strings anyway. The maps are rebuilt
 * on the way in, keyed off the record itself.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CollectionSave(
    @EncodeDefault val schema: Int = SAVE_SCHEMA,
    val copies: List<Copy> = emptyList(),
    val binders: List<Binder> = emptyList(),
    val containers: List<Container> = emptyList(),
    val settings: SavedSettings = SavedSettings(),
)

/**
 * Display preferences, as written down.
 *
 * A mirror of `AppSettings` rather than that class itself: the settings type lives beside
 * the store and is free to gain a field that is only meaningful while the app is running,
 * and the file format should not follow it there. Adding a preference that is *not*
 * persisted stays possible because these are two types.
 */
@Serializable
data class SavedSettings(
    val holoShimmer: Boolean = true,
    val showPocketPrices: Boolean = true,
    val showWantedGhosts: Boolean = true,
    val abbreviateValues: Boolean = true,
    val defaultLayout: BinderLayout = BinderLayout.POCKET_9,
    val defaultSheetCount: Int = 10,
)

/**
 * The catalog, cached.
 *
 * [fetchedAtEpochSeconds] is what makes the launch a cache check rather than a re-sync.
 * Before this file existed the app re-matched and re-priced the entire collection on
 * every cold start, because it had no way to know it had already done so a minute ago.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CatalogCache(
    @EncodeDefault val schema: Int = SAVE_SCHEMA,
    val fetchedAtEpochSeconds: Long = 0L,
    val cards: List<Card> = emptyList(),
    val printings: List<Printing> = emptyList(),
    val variants: List<Variant> = emptyList(),
    val prices: List<PriceSnapshot> = emptyList(),
)

/**
 * The reader and writer for both files.
 *
 * `ignoreUnknownKeys` so a file written by a newer build opens on an older one with the
 * fields it does not understand dropped, rather than refusing to open at all. That is the
 * friendlier half of forward compatibility; the other half is [SAVE_SCHEMA], which is
 * what a change that *cannot* be read this way announces itself with.
 *
 * `encodeDefaults = false` keeps the file to what actually differs from a fresh record.
 * A 360-pocket set binder is mostly empty pockets and unremarkable near-mint copies, and
 * writing every default would triple the file for nothing.
 *
 * The version fields are marked [EncodeDefault] to opt back *in*, because a default that
 * is not written is a default that cannot be read. A file whose schema is omitted reads
 * back as whatever the running build calls current -- so the day [SAVE_SCHEMA] becomes 2,
 * every file written under 1 would silently claim to be a 2 and any migration keyed on
 * that number would skip exactly the files that needed it.
 */
val SaveJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    prettyPrint = false
}

// ------------------------------------------------------------------ conversion

fun CollectionSnapshot.toSave(settings: SavedSettings): CollectionSave = CollectionSave(
    copies = copies.values.toList(),
    binders = binders.map { it.copy(slots = it.slots.trimmedForSave()) },
    containers = containers,
    settings = settings,
)

/**
 * Drops the run of empty pockets at the end of a binder.
 *
 * `Binder.paddedSlots` already pads a short list back out to capacity on the way in, and
 * the geometry that decides that capacity -- layout and sheet count -- is stored beside
 * the slots. So the trailing empties carry no information: they are re-derivable, and a
 * fresh 180-pocket binder was spending four kilobytes saying "empty" a hundred and eighty
 * times. Only the *trailing* run goes; a gap in the middle is a position, and positions
 * are the whole point of a binder.
 */
private fun List<SlotContent>.trimmedForSave(): List<SlotContent> =
    dropLastWhile { it == SlotContent.Empty }

/**
 * A collection as a document, meant to leave the device.
 *
 * Carries its own catalog, which is the whole difference between this and the save file.
 * A copy names a variant id and nothing else -- what that card is *called*, what it is
 * worth and what it looks like all live in the catalog -- so a collection imported onto a
 * phone that has never seen those cards would be a list of ids with no names, no prices
 * and no art, and no sync could repair it because a sync only fills in printings the app
 * already knows about. Locally that is an unlikely edge case. Through an export it would
 * be every single import, so the document carries what it needs to stand on its own.
 *
 * [app] is checked on the way in. It is the difference between "this is not a Pocketful
 * backup" and a stack of confusing field-level errors from some other application's JSON.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CollectionExport(
    /**
     * Deliberately has no default, which is what makes it a marker at all.
     *
     * A field with a default is filled in for any document that omits it, so a version of
     * this that defaulted to [EXPORT_MARKER] would stamp the name "pocketful" onto some
     * other application's JSON and then congratulate itself for finding it there. Required
     * means a file without it fails to decode, which is the honest answer: that is not one
     * of these.
     */
    val app: String,
    @EncodeDefault val schema: Int = SAVE_SCHEMA,
    /** The day it was written, for whoever is looking at a folder of these later. */
    val exportedOn: String? = null,
    val collection: CollectionSave = CollectionSave(),
    val catalog: CatalogCache = CatalogCache(),
)

const val EXPORT_MARKER: String = "pocketful"

/** What the save dialog suggests. The date is what makes two backups tellable apart. */
fun exportFileName(day: String): String = "pocketful-$day.json"

fun CollectionSnapshot.toExport(settings: SavedSettings, day: String): CollectionExport =
    CollectionExport(
        app = EXPORT_MARKER,
        exportedOn = day,
        collection = toSave(settings),
        catalog = referencedCatalog(),
    )

/**
 * The slice of the catalog this collection actually depends on.
 *
 * Not the whole catalog: browsing a few thousand sets fills the local one with cards
 * nobody owns, and a backup is a record of a collection rather than a copy of TCGdex.
 *
 * Every variant of a referenced *printing* is kept, though, not only the one owned. The
 * finish picker offers "you own the reverse holo, did you mean the holo" by looking at the
 * other press runs of the same printing, and an import that kept only what was owned would
 * silently lose that choice on every card.
 */
private fun CollectionSnapshot.referencedCatalog(): CatalogCache {
    val referenced = buildSet {
        copies.values.forEach { add(it.variantId) }
        binders.forEach { binder ->
            binder.paddedSlots.forEach { if (it is SlotContent.Wanted) add(it.variantId) }
        }
    }
    val printingIds = referenced.mapNotNullTo(mutableSetOf()) { variants[it]?.printingId }
    val keptPrintings = printingIds.mapNotNull { printings[it] }
    val keptVariants = variants.values.filter { it.printingId in printingIds }
    val cardIds = keptPrintings.mapTo(mutableSetOf()) { it.cardId }

    return CatalogCache(
        cards = cardIds.mapNotNull { cards[it] },
        printings = keptPrintings,
        variants = keptVariants,
        prices = keptVariants.mapNotNull { prices[it.id] },
    )
}

fun CollectionSnapshot.toCatalogCache(fetchedAtEpochSeconds: Long): CatalogCache = CatalogCache(
    fetchedAtEpochSeconds = fetchedAtEpochSeconds,
    cards = cards.values.toList(),
    printings = printings.values.toList(),
    variants = variants.values.toList(),
    prices = prices.values.toList(),
)

/**
 * Rebuilds the read model from both halves.
 *
 * The copies are filed by `CollectionStore` on the way in rather than here -- a restored
 * snapshot sets binder slots but leaves every `Copy.location` at whatever was written,
 * and reconciling the two is the store's job because the store is what owns that
 * invariant.
 */
fun buildSnapshot(save: CollectionSave, cache: CatalogCache?): CollectionSnapshot =
    CollectionSnapshot(
        cards = cache?.cards.orEmpty().associateBy { it.id },
        printings = cache?.printings.orEmpty().associateBy { it.id },
        variants = cache?.variants.orEmpty().associateBy { it.id },
        prices = cache?.prices.orEmpty().associateBy { it.variantId },
        copies = save.copies.associateBy { it.id },
        binders = save.binders,
        containers = save.containers,
    )
