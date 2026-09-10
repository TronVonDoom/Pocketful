package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.CardArt
import app.pocketful.data.CatalogDownload
import app.pocketful.data.CatalogSync
import app.pocketful.data.TcgDex
import app.pocketful.data.nowEpochSeconds
import app.pocketful.domain.Printing
import app.pocketful.domain.PrintingId
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Everything the app would otherwise do badly on its first screen, done once up front.
 *
 * Before this existed the app opened straight onto Home and then repaired itself in
 * public: the set index arrived a second later, prices a few seconds after that, and card
 * art filled in tile by tile while you were already scrolling past it. Every one of those
 * is a *fetch*, not a computation, so none of them can be made instant -- but they can all
 * happen behind one screen that is honest about waiting, which is the difference between
 * an app that is loading and an app that looks broken.
 *
 * Three rules hold the whole thing together:
 *
 *  - **Nothing here is allowed to fail the launch.** Every step is wrapped, and a step
 *    that throws is a step that reports itself skipped. The worst case is the app you had
 *    before this class existed.
 *  - **Nothing here is allowed to hang the launch.** [BUDGET] caps the whole sequence. A
 *    phone on a captive-portal wifi that accepts connections and never answers is the
 *    normal case this protects against, and it is exactly the case a per-request timeout
 *    misses.
 *  - **It never finishes faster than the eye can follow.** [FLOOR] holds the screen up
 *    long enough to read as a launch rather than a flicker, on the warm starts where
 *    every step above returns from cache in fifty milliseconds.
 *
 * The first step is the collection itself, off disk, and it is the one step here that is
 * not a network call and not allowed to be skipped for time. Everything after it is a
 * refinement of data the app already has.
 */
class AppBootstrap {

    /** How far along the whole sequence is, 0..1. */
    var progress by mutableStateOf(0f)
        private set

    /** What is happening, in the user's words rather than the network's. */
    var status by mutableStateOf(FIRST_STATUS)
        private set

    /** True once the app may be shown, whether the steps succeeded or not. */
    var ready by mutableStateOf(false)
        private set

    /**
     * What the launch actually managed to do, for the line under the progress bar.
     *
     * Worth keeping because the answer is genuinely interesting on a cold start:
     * "Repriced 148 cards" is the app saying the figures you are about to read are today's
     * rather than the ones it shipped with.
     */
    var summary by mutableStateOf<String?>(null)
        private set

    /**
     * Whether the save file was read before anything else ran.
     *
     * Watched rather than assumed, because everything downstream of it is conditional on
     * it: autosave must not start against a store that failed to restore, or the first
     * edit of the session would write an empty collection over a full one.
     */
    var restoredFromDisk by mutableStateOf(false)
        private set

    /** Whether prices came from the cache rather than the network. Reported, not hidden. */
    var usedCachedPrices by mutableStateOf(false)
        private set

    /** What the catalog step managed, so the launch can say which app this is. */
    var catalogOutcome by mutableStateOf(CatalogDownload.Outcome.Unavailable)
        private set

    /** How many printings gained artwork off the local catalog. Reported, not hidden. */
    var artFilled by mutableStateOf(0)
        private set

    /**
     * Runs the sequence and returns the catalog delta for the caller to apply.
     *
     * The sync result is handed back rather than written from in here for the reason
     * [CatalogSync.Result] exists at all: the store is the only thing allowed to change
     * the collection, and a bootstrap that reached into it would be a second writer.
     */
    suspend fun run(
        store: CollectionStore,
        saver: CollectionSaver,
        catalogSync: CatalogSync,
        browser: CatalogBrowser,
        api: TcgDex,
        catalogDownload: CatalogDownload,
        imageLoader: ImageLoader,
        imageContext: PlatformContext,
    ): CatalogSync.Result? {
        val started = TimeSource.Monotonic.markNow()
        var syncResult: CatalogSync.Result? = null
        var artPrintings: Map<PrintingId, Printing> = emptyMap()

        // 0. The collection, off disk. Outside the budget below on purpose: that budget
        //    exists to stop a dead network from holding the launch, and this step does
        //    not touch the network. Timing it out would mean opening onto an empty app
        //    that still had a full save file -- the one failure this whole class is
        //    supposed to prevent.
        step(RESTORE, 0f, 0.10f) {
            saver.restoreInto(store)
        }
        restoredFromDisk = saver.restored

        withTimeoutOrNull(BUDGET) {
            // 1. The published catalog, off disk or off GitHub.
            //
            //    Ahead of everything that reads the catalog, because it decides whether
            //    any of those cost a round trip at all. Once it is in hand the set index,
            //    every set's contents and every card's press runs are local, which is the
            //    difference between a browse tab that opens and one that loads.
            //
            //    Failing here is survivable by design: TcgDex simply keeps asking the
            //    network, which is the app that existed before this step.
            step(CATALOG_FILE_STEP, 0.10f, 0.26f) {
                api.usePublished(catalogDownload.ensure(nowEpochSeconds()))
            }
            catalogOutcome = catalogDownload.outcome

            // 1a. Artwork for the collection that already exists, straight off the
            //     catalog just loaded. No network, so it runs on every launch rather than
            //     being rationed behind the price TTL below -- which is the difference
            //     between a card gaining its picture now and gaining it in six hours, and
            //     the only way the ~1,700 cards TCGdex has no artwork for ever get one,
            //     since the document the price sync fetches does not know they exist.
            step(ARTWORK_FILL, 0.26f, 0.30f) {
                artPrintings = catalogSync.fillArtFromCatalog(store.snapshot)
                artFilled = artPrintings.size
            }

            // 1b. The catalog's shape. Everything else that reads a set name -- the sync
            //    below, the search tab, every "Base Set · 102 cards" caption -- is served
            //    from the index this fills, so it goes first and the rest come free.
            step(CATALOG, 0.30f, 0.32f) {
                browser.load()
            }

            // 2. The collection against that catalog: artwork and real prices for cards
            //    that were, until this ran, coloured rectangles with sample values on them.
            //    Skipped outright when the cache is recent enough to still be true. This
            //    is the step persistence was worth building for: before there was a file
            //    to date, every cold start re-fetched the whole collection card by card
            //    because the app had no way to know it had done exactly that a minute
            //    ago. Prices are the only thing here that goes stale, and they do not
            //    move fast enough to be worth a round trip per launch.
            val cachedAt = saver.cachedAtEpochSeconds
            val cacheAge = cachedAt?.let { nowEpochSeconds() - it }
            usedCachedPrices = cacheAge != null && cacheAge in 0 until PRICE_TTL
            if (!usedCachedPrices) {
                step(MATCHING, 0.32f, 0.80f) {
                    syncResult = catalogSync.run(store.snapshot, nowEpochSeconds()) { done, total ->
                        if (total > 0) {
                            progress = 0.32f + 0.48f * (done.toFloat() / total)
                            status = "$MATCHING · $done of $total"
                        }
                    }
                }
            } else {
                progress = 0.80f
            }

            // 3. The thumbnails those matches just pointed at. Decoding art is the one
            //    remaining thing that would otherwise happen while the user scrolls, and
            //    it is the most visible: a grid of tiles popping in one at a time.
            step(ARTWORK, 0.80f, 0.98f) {
                // The sync's own results first: they are the printings that had no art a
                // moment ago, so they are the ones the first screen would have drawn empty.
                val stems = syncResult?.printings?.values.orEmpty().mapNotNull { it.imageUrl } +
                    store.snapshot.printings.values.mapNotNull { it.imageUrl }
                val urls = stems.distinct().mapNotNull(CardArt::thumb).take(ART_PREFETCH)
                warmArtwork(urls, imageLoader, imageContext)
            }
        }

        // Handed back rather than written from in here, for the reason CatalogSync.Result
        // exists at all: the store is the only thing allowed to change the collection.
        // The two deltas are merged rather than applied in turn, so the caller still makes
        // exactly one write. Where both touched a printing the network sync wins, and they
        // agree anyway -- both read the fallback artwork from the same published catalog.
        if (artPrintings.isNotEmpty()) {
            syncResult = (syncResult ?: CatalogSync.Result(matched = 0, unmatched = 0)).let {
                it.copy(printings = artPrintings + it.printings)
            }
        }

        summary = describe(syncResult)
        progress = 1f
        status = READY

        // Measured from the start rather than added to the end, so a launch that already
        // took longer than the floor does not pay it twice.
        val remaining = FLOOR.milliseconds - started.elapsedNow()
        if (remaining.isPositive()) delay(remaining)

        ready = true
        return syncResult
    }

    /**
     * One step, with its slice of the bar and its own failure contained.
     *
     * The progress it reports is deliberately coarse -- a step sits at its start value
     * until it finishes and then jumps to its end value -- except where the work itself
     * can count, as the catalog match can. A bar that interpolates smoothly through a step
     * it cannot measure is animating a number it does not have.
     */
    private suspend fun step(label: String, from: Float, to: Float, body: suspend () -> Unit) {
        status = label
        progress = from
        runCatching { body() }
        progress = to
    }

    /**
     * Pulls thumbnails into Coil's cache, [ART_BATCH] at a time.
     *
     * `execute` rather than `enqueue`: enqueueing returns the moment the request is
     * accepted, which would make this step finish before it had warmed anything -- a
     * progress bar for work that had not started. Eight at a time is the same compromise
     * the catalog sync makes at six: enough to saturate a phone's connection, few enough
     * not to look like a scraper.
     */
    private suspend fun warmArtwork(
        urls: List<String>,
        loader: ImageLoader,
        context: PlatformContext,
    ) = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope
        val batches = (urls.size + ART_BATCH - 1) / ART_BATCH
        urls.chunked(ART_BATCH).forEachIndexed { index, batch ->
            batch.map { url ->
                async {
                    runCatching { loader.execute(ImageRequest.Builder(context).data(url).build()) }
                }
            }.awaitAll()
            progress = 0.80f + 0.18f * ((index + 1).toFloat() / batches)
        }
    }

    /** The one line the loading screen leaves behind about what the launch achieved. */
    private fun describe(result: CatalogSync.Result?): String? = when {
        // Said first, because it is the most interesting thing a launch can report: the
        // app just stopped needing the network to browse.
        artFilled > 0 -> "Found artwork for $artFilled cards"
        catalogOutcome == CatalogDownload.Outcome.Downloaded -> "Card catalog downloaded"
        catalogOutcome == CatalogDownload.Outcome.Refreshed -> "Card catalog updated"
        // Said out loud rather than passed off as a fetch. A launch that skipped the
        // network is the good case, and an app that silently reports nothing on its
        // fastest starts reads as one that quietly did less.
        usedCachedPrices -> "Prices from your last sync"
        result == null || result.failure != null -> null
        result.pricesUpdated > 0 -> "Repriced ${result.pricesUpdated} cards"
        result.matched > 0 -> "Matched ${result.matched} cards"
        else -> null
    }

    private companion object {
        const val FIRST_STATUS = "Opening your collection"
        const val RESTORE = "Opening your collection"
        const val CATALOG_FILE_STEP = "Getting the card catalog"
        const val ARTWORK_FILL = "Matching your cards to it"
        const val CATALOG = "Reading the set catalog"
        const val MATCHING = "Matching your cards"
        const val ARTWORK = "Fetching artwork"
        const val READY = "Ready"

        /** The whole sequence, capped. Past this the app opens with whatever it has. */
        const val BUDGET = 12_000L

        /** The shortest a launch is allowed to look like one. */
        const val FLOOR = 1_100L

        /**
         * How long a cached price stays good enough to open with.
         *
         * Six hours, which is a judgement about card prices rather than about caching:
         * a TCGplayer market quote does not move enough between breakfast and lunch to
         * be worth making someone wait for it, and anyone who wants today's number to
         * the minute has the manual refresh in Settings.
         */
        const val PRICE_TTL = 6 * 60 * 60L

        const val ART_PREFETCH = 96
        const val ART_BATCH = 8
    }
}

/** One bootstrap per app. Remembered so a recomposition never re-runs the launch. */
@Composable
fun rememberAppBootstrap(): AppBootstrap = remember { AppBootstrap() }
