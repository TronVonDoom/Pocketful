package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.pocketful.data.CATALOG_CACHE_FILE
import app.pocketful.data.COLLECTION_FILE
import app.pocketful.data.CatalogCache
import app.pocketful.data.CollectionSave
import app.pocketful.data.SAVE_SCHEMA
import app.pocketful.data.SaveJson
import app.pocketful.data.SaveStorage
import app.pocketful.data.SavedSettings
import app.pocketful.data.buildSnapshot
import app.pocketful.data.nowEpochSeconds
import app.pocketful.data.toCatalogCache
import app.pocketful.data.toSave
import app.pocketful.domain.Card
import app.pocketful.domain.CardId
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.PriceSnapshot
import app.pocketful.domain.Printing
import app.pocketful.domain.PrintingId
import app.pocketful.domain.Variant
import app.pocketful.domain.VariantId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * Reading the collection off disk at launch, and writing it back as it changes.
 *
 * The store stays the only thing that may change a collection; this only ever hands it a
 * restored snapshot and watches what it does afterwards. That split is why autosave needs
 * no cooperation from the thirty-odd mutation methods on the store: every one of them
 * ends in a new snapshot, and one observer of that is enough.
 *
 * Two rules are load-bearing here, and both exist because getting them wrong destroys a
 * collection rather than inconveniencing someone:
 *
 *  - **Nothing is written until something has been read.** A store that has not been
 *    restored yet is an *empty* store, and an autosave that fired against it would
 *    overwrite a real save with nothing. [restored] gates the writer, and the writer is
 *    not started until it is true.
 *  - **A file that will not parse is kept, not replaced.** A collection that fails to
 *    read is quarantined under another name. Starting empty over the top of it would
 *    destroy the only record of what the user had at exactly the moment it became
 *    valuable.
 */
class CollectionSaver(private val storage: SaveStorage) {

    /**
     * Whether the launch has finished trying to read the save.
     *
     * True after the attempt, not after a success: a first run with no file to read is
     * just as restored as one that loaded four hundred cards, and both are equally
     * entitled to start saving.
     */
    var restored by mutableStateOf(false)
        private set

    /** When the cached catalog was written, if there was one. Null means no cache. */
    var cachedAtEpochSeconds by mutableStateOf<Long?>(null)
        private set

    /** True if the collection file existed but could not be read. Quarantined, not lost. */
    var recoveredFromDamage by mutableStateOf(false)
        private set

    // What the last write put in the cache file. Compared by reference, which is exact
    // here: the store replaces these maps wholesale on a catalog change and never mutates
    // one in place, so identical references genuinely mean nothing has changed. This is
    // what keeps a card being filed from rewriting a twelve-thousand-entry catalog.
    private var savedCards: Map<CardId, Card>? = null
    private var savedPrintings: Map<PrintingId, Printing>? = null
    private var savedVariants: Map<VariantId, Variant>? = null
    private var savedPrices: Map<VariantId, PriceSnapshot>? = null

    /**
     * Reads both files and hands the result to [store].
     *
     * Either file may be absent, and neither is an error: a first run has no files at
     * all, and a collection that reads while its cache does not is one the next sync
     * refills. What the two never do is fail together -- an unreadable cache leaves the
     * collection alone, which is the whole reason they are two files.
     */
    suspend fun restoreInto(store: CollectionStore) {
        val save = readCollection()
        val cache = readCache()

        if (save != null || cache != null) {
            store.restore(
                snapshot = buildSnapshot(save ?: CollectionSave(), cache),
                settings = (save?.settings ?: SavedSettings()).toAppSettings(),
            )
        }

        cachedAtEpochSeconds = cache?.fetchedAtEpochSeconds
        // Seed the change detector from what was actually loaded, so a launch that
        // changes nothing about the catalog does not rewrite the cache it just read.
        store.snapshot.let {
            savedCards = it.cards
            savedPrintings = it.printings
            savedVariants = it.variants
            savedPrices = it.prices
        }
        restored = true
    }

    private suspend fun readCollection(): CollectionSave? {
        val text = storage.read(COLLECTION_FILE) ?: return null
        val parsed = runCatching { SaveJson.decodeFromString<CollectionSave>(text) }.getOrNull()
        if (parsed == null || parsed.schema > SAVE_SCHEMA) {
            // Unreadable, or written by a build from the future. Either way this app
            // cannot honour it, and writing over it is not this app's decision to make.
            storage.quarantine(COLLECTION_FILE)
            recoveredFromDamage = true
            return null
        }
        return parsed
    }

    private suspend fun readCache(): CatalogCache? {
        val text = storage.read(CATALOG_CACHE_FILE) ?: return null
        val parsed = runCatching { SaveJson.decodeFromString<CatalogCache>(text) }.getOrNull()
        if (parsed == null || parsed.schema > SAVE_SCHEMA) {
            // Kept rather than deleted, even though the cache is the disposable half.
            // A collection's copies point at variant ids that only the catalog resolves,
            // so a collection that outlives its cache is one whose cards cannot be named
            // or priced until a sync refills it. That makes an unreadable cache worth
            // holding on to for the same reason an unreadable collection is.
            storage.quarantine(CATALOG_CACHE_FILE)
            return null
        }
        return parsed
    }

    /**
     * Writes whatever has changed.
     *
     * The collection is written every time; it is small -- a few hundred records of a few
     * fields each -- and it is the file whose staleness costs something. The catalog cache
     * is written only when the catalog actually moved, because it is the big one and most
     * saves are triggered by a card changing pockets.
     */
    suspend fun save(snapshot: CollectionSnapshot, settings: AppSettings) {
        runCatching {
            storage.write(
                COLLECTION_FILE,
                SaveJson.encodeToString(snapshot.toSave(settings.toSaved())),
            )
        }

        val catalogChanged = snapshot.cards !== savedCards ||
            snapshot.printings !== savedPrintings ||
            snapshot.variants !== savedVariants ||
            snapshot.prices !== savedPrices
        if (!catalogChanged) return

        val stamp = nowEpochSeconds()
        val written = runCatching {
            storage.write(
                CATALOG_CACHE_FILE,
                SaveJson.encodeToString(snapshot.toCatalogCache(stamp)),
            )
        }
        // Only remember it as saved if it was. A failed write that updated these would
        // make every later save skip the file that never got written.
        if (written.isSuccess) {
            savedCards = snapshot.cards
            savedPrintings = snapshot.printings
            savedVariants = snapshot.variants
            savedPrices = snapshot.prices
            cachedAtEpochSeconds = stamp
        }
    }

    /** Throws both files away. Used by Settings' reset, which means it about the data. */
    suspend fun clear() {
        storage.delete(COLLECTION_FILE)
        storage.delete(CATALOG_CACHE_FILE)
        savedCards = null
        savedPrintings = null
        savedVariants = null
        savedPrices = null
        cachedAtEpochSeconds = null
    }

    companion object {
        /**
         * How long a burst of edits is allowed to run before it is written.
         *
         * Short enough that no realistic pause between two gestures outlives it, long
         * enough that dragging a card across a page is one write rather than forty. The
         * risk it trades against is a process kill inside the window, which costs the
         * last edit and never more -- the file on disk is always a complete earlier
         * version, never a partial current one.
         */
        const val DEBOUNCE_MS: Long = 400
    }
}

/**
 * Writes the collection back whenever it changes, once it is safe to.
 *
 * `collectLatest` is the debounce: a newer edit cancels the pending [delay] of the one
 * before it, so a burst collapses into a single write of the final state rather than a
 * queue of writes of every intermediate one. Cancelling a save that has already started
 * is safe for the same reason -- the write is atomic, so a cancelled one leaves the
 * previous file exactly as it was.
 *
 * `drop(1)` skips the value that is already on screen when collection starts. Without it
 * every launch would open by writing back the file it has just finished reading.
 */
@Composable
fun AutosaveEffect(store: CollectionStore, saver: CollectionSaver) {
    val ready = saver.restored
    LaunchedEffect(store, saver, ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { store.revision }
            .drop(1)
            .collectLatest {
                delay(CollectionSaver.DEBOUNCE_MS)
                saver.save(store.snapshot, store.settings)
            }
    }
}

@Composable
fun rememberCollectionSaver(storage: SaveStorage): CollectionSaver =
    remember(storage) { CollectionSaver(storage) }

// ------------------------------------------------------------------ settings
//
// Not private: the same two shapes have to cross the same boundary when a collection is
// exported to a document rather than saved to disk, and a second pair of converters that
// had to be kept in step with these would be one pair too many.

internal fun SavedSettings.toAppSettings() = AppSettings(
    holoShimmer = holoShimmer,
    showPocketPrices = showPocketPrices,
    showWantedGhosts = showWantedGhosts,
    abbreviateValues = abbreviateValues,
    defaultLayout = defaultLayout,
    defaultSheetCount = defaultSheetCount,
)

internal fun AppSettings.toSaved() = SavedSettings(
    holoShimmer = holoShimmer,
    showPocketPrices = showPocketPrices,
    showWantedGhosts = showWantedGhosts,
    abbreviateValues = abbreviateValues,
    defaultLayout = defaultLayout,
    defaultSheetCount = defaultSheetCount,
)
