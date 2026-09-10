package app.pocketful.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Getting the published catalog onto the device, and keeping it there.
 *
 * The catalog is downloaded once and then simply *had*. It is not a cache with a TTL,
 * because nothing in it expires: a set has not changed since the day it was printed. The
 * only reason to fetch again is that a new set was released, which happens four to eight
 * times a year and is what [REFRESH_AFTER_SECONDS] is for -- a floor on how often the app
 * is willing to ask, not a deadline after which what it holds stops being true.
 *
 * That distinction decides the whole failure story. A cache that expires has to be
 * refetched before it can be trusted, so a dead network means an app with no catalog. A
 * catalog that is merely old is still completely correct about every set that existed when
 * it was built, so a dead network means an app that is missing this month's release and
 * nothing else. Every path here therefore prefers what is on disk and treats the network
 * as an improvement rather than a requirement.
 */
class CatalogDownload(
    private val storage: SaveStorage,
    private val client: HttpClient = defaultClient(),
) {

    /** What the last [ensure] actually did, for the launch screen to report honestly. */
    enum class Outcome { FromDisk, Downloaded, Refreshed, Unavailable }

    var outcome: Outcome = Outcome.Unavailable
        private set

    /**
     * The catalog, from disk if it is there and from the network if it is not.
     *
     * Never throws. The worst case is null, which puts [TcgDex] back on the live API --
     * exactly the app that existed before this file, rather than a broken one.
     */
    suspend fun ensure(nowSeconds: Long): PublishedCatalog? {
        val onDisk = read()

        if (onDisk != null) {
            val age = nowSeconds - (readStampSeconds() ?: 0L)
            if (age in 0 until REFRESH_AFTER_SECONDS) {
                outcome = Outcome.FromDisk
                return onDisk
            }
            // Old enough to be worth asking about, but still perfectly good. Fetch, and
            // keep what we have if the fetch does not work out.
            val fresh = download()
            if (fresh != null) {
                outcome = Outcome.Refreshed
                return fresh
            }
            outcome = Outcome.FromDisk
            return onDisk
        }

        val fetched = download()
        outcome = if (fetched != null) Outcome.Downloaded else Outcome.Unavailable
        return fetched
    }

    /** Reads and parses the stored document, or null if there is not a usable one. */
    private suspend fun read(): PublishedCatalog? {
        val text = runCatching { storage.read(CATALOG_FILE) }.getOrNull() ?: return null
        // Six megabytes and twenty-three thousand cards. On the launch screen's dispatcher
        // that is a visibly frozen progress bar, so it happens off it -- the one place in
        // this class where the work is CPU rather than a socket.
        val parsed = withContext(Dispatchers.Default) { PublishedCatalog.parse(text) }
        if (parsed == null) {
            // Unreadable or built for a schema this app does not know. Thrown away rather
            // than quarantined, unlike the collection: this one can always be fetched
            // again, and keeping a broken copy would only mean parsing it every launch.
            runCatching { storage.delete(CATALOG_FILE) }
            runCatching { storage.delete(CATALOG_STAMP_FILE) }
        }
        return parsed
    }

    private suspend fun readStampSeconds(): Long? =
        runCatching { storage.read(CATALOG_STAMP_FILE)?.trim()?.toLongOrNull() }.getOrNull()

    /**
     * Fetches, decompresses, parses, and only then writes.
     *
     * In that order on purpose. Writing first and parsing afterwards would mean a
     * truncated download replacing a working catalog with something the next launch has to
     * discover is broken; parsing first means a bad fetch costs nothing at all.
     */
    private suspend fun download(): PublishedCatalog? = runCatching {
        val response = client.get(ASSET_URL)
        if (!response.status.isSuccess()) return null

        val bytes = response.readRawBytes()
        val parsed = withContext(Dispatchers.Default) {
            val text = gunzipToText(bytes) ?: return@withContext null
            PublishedCatalog.parse(text)?.let { it to text }
        } ?: return null

        runCatching {
            storage.write(CATALOG_FILE, parsed.second)
            storage.write(CATALOG_STAMP_FILE, nowEpochSeconds().toString())
        }
        parsed.first
    }.getOrNull()

    companion object {
        /**
         * Where the catalog comes from.
         *
         * A fixed tag whose asset is replaced in place, so this URL always means "the
         * current catalog" and the app never has to search a release list to find it. The
         * schema version is in the *filename*: a build that understands v1 asks for v1
         * forever, and publishing a v2 alongside it cannot reach back and break installs
         * that predate it.
         */
        const val ASSET_URL: String =
            "https://github.com/TronVonDoom/Pocketful-Catalog/releases/download/catalog/" +
                "catalog-v${PublishedCatalog.SCHEMA}.json.gz"

        const val CATALOG_FILE: String = "catalog-published.json"
        const val CATALOG_STAMP_FILE: String = "catalog-published.stamp"

        /**
         * How long the app waits before asking whether a new set exists.
         *
         * Six days. Sets arrive a handful of times a year, so this is not a staleness
         * budget -- what is on disk stays true regardless -- it is a courtesy limit on how
         * often one install pings GitHub for a file that usually has not changed.
         */
        const val REFRESH_AFTER_SECONDS: Long = 6 * 24 * 60 * 60

        fun defaultClient(): HttpClient = HttpClient {
            install(UserAgent) { agent = TcgDex.USER_AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                // Generous next to the catalog API's: this is half a megabyte over
                // whatever connection the user has, not a JSON query.
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}
