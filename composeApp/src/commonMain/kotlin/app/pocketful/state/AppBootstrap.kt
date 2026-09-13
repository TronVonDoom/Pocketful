package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.CardArt
import app.pocketful.data.CardCatalog
import app.pocketful.data.CatalogDownload
import app.pocketful.data.CatalogSync
import app.pocketful.data.PriceDownload
import app.pocketful.data.nowEpochSeconds
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
 * Three rules hold it together:
 *
 *  - **Nothing here is allowed to fail the launch.** Every step is wrapped, and a step that
 *    throws is a step that reports itself skipped.
 *  - **Nothing here is allowed to hang the launch.** [BUDGET] caps everything after the
 *    collection is off disk.
 *  - **It never finishes faster than the eye can follow.** [FLOOR] holds the screen long enough
 *    to read as a launch rather than a flicker.
 *
 * The catalog and its prices come from the published files on Cloudflare R2, kept on the
 * device; once they are loaded, filing, pricing and refreshing the collection is all local.
 */
class AppBootstrap {

    var progress by mutableStateOf(0f)
        private set

    var status by mutableStateOf(FIRST_STATUS)
        private set

    var ready by mutableStateOf(false)
        private set

    /** What the launch managed, for the line under the progress bar. */
    var summary by mutableStateOf<String?>(null)
        private set

    var restoredFromDisk by mutableStateOf(false)
        private set

    var catalogOutcome by mutableStateOf(CatalogDownload.Outcome.Unavailable)
        private set

    /** How many cards had their catalog rows rebuilt. Reported, because it means damage. */
    var rebuilt by mutableStateOf(0)
        private set

    /**
     * Runs the sequence and returns the catalog delta for the caller to apply. Handed back
     * rather than written from here: the store is the only thing allowed to change the collection.
     */
    suspend fun run(
        store: CollectionStore,
        saver: CollectionSaver,
        catalogSync: CatalogSync,
        browser: CatalogBrowser,
        catalog: CardCatalog,
        catalogDownload: CatalogDownload,
        priceDownload: PriceDownload,
        imageLoader: ImageLoader,
        imageContext: PlatformContext,
    ): CatalogSync.Result? {
        val started = TimeSource.Monotonic.markNow()
        var result: CatalogSync.Result? = null

        // 0. The collection, off disk. Outside the budget: it does not touch the network, and
        //    timing it out would open an empty app over a full save file.
        step(RESTORE, 0f, 0.15f) {
            saver.restoreInto(store)
        }
        restoredFromDisk = saver.restored

        withTimeoutOrNull(BUDGET) {
            // 1. The catalog and today's prices, from disk where current and from R2 where not.
            step(CATALOG_STEP, 0.15f, 0.55f) {
                catalog.use(catalogDownload.ensure(nowEpochSeconds()))
                catalog.usePrices(priceDownload.ensure(catalog.publicUrl, nowEpochSeconds()))
            }
            catalogOutcome = catalogDownload.outcome

            // 2. Cards the collection points at but has no rows for, rebuilt from the catalog.
            step(REPAIR, 0.55f, 0.60f) {
                val recovered = catalogSync.recoverOrphans(store.snapshot)
                store.applyRecovery(recovered)
                rebuilt = recovered.count
            }

            // 3. The collection brought up to date with what is published, and repriced.
            step(REFRESH, 0.60f, 0.70f) {
                result = catalogSync.refresh(store.snapshot, nowEpochSeconds())
            }

            step(BROWSE, 0.70f, 0.75f) {
                browser.refresh()
            }

            // 4. The thumbnails the first screens will draw, so tiles do not pop in one by one.
            step(ARTWORK, 0.75f, 0.98f) {
                val stems = store.snapshot.variants.values.mapNotNull { it.image } +
                    store.snapshot.printings.values.mapNotNull { it.image ?: it.back }
                val urls = stems.distinct().mapNotNull { CardArt.thumb(it) }.take(ART_PREFETCH)
                warmArtwork(urls, imageLoader, imageContext)
            }
        }

        summary = describe(result)
        progress = 1f
        status = READY

        val remaining = FLOOR.milliseconds - started.elapsedNow()
        if (remaining.isPositive()) delay(remaining)

        ready = true
        return result
    }

    private suspend fun step(label: String, from: Float, to: Float, body: suspend () -> Unit) {
        status = label
        progress = from
        runCatching { body() }
        progress = to
    }

    /** Pulls thumbnails into the image cache, a batch at a time, keyed by path. */
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
                    runCatching {
                        val key = CardArt.cacheKey(url)
                        loader.execute(
                            ImageRequest.Builder(context).data(url).diskCacheKey(key).memoryCacheKey(key).build(),
                        )
                    }
                }
            }.awaitAll()
            progress = 0.75f + 0.23f * ((index + 1).toFloat() / batches)
        }
    }

    private fun describe(result: CatalogSync.Result?): String? = when {
        rebuilt > 0 -> "Rebuilt $rebuilt ${if (rebuilt == 1) "card" else "cards"} from the catalog"
        catalogOutcome == CatalogDownload.Outcome.Downloaded -> "Card catalog downloaded"
        catalogOutcome == CatalogDownload.Outcome.Refreshed -> "Card catalog updated"
        result == null -> null
        result.pricesUpdated > 0 -> "Priced ${result.pricesUpdated} ${if (result.pricesUpdated == 1) "card" else "cards"}"
        else -> null
    }

    private companion object {
        const val FIRST_STATUS = "Opening your collection"
        const val RESTORE = "Opening your collection"
        const val CATALOG_STEP = "Getting the card catalog"
        const val REPAIR = "Checking your collection"
        const val REFRESH = "Pricing your collection"
        const val BROWSE = "Reading the set catalog"
        const val ARTWORK = "Fetching artwork"
        const val READY = "Ready"

        /** Everything after the collection, capped. Past this the app opens with whatever it has. */
        const val BUDGET = 20_000L

        const val FLOOR = 1_100L
        const val ART_PREFETCH = 96
        const val ART_BATCH = 8
    }
}

@Composable
fun rememberAppBootstrap(): AppBootstrap = remember { AppBootstrap() }
