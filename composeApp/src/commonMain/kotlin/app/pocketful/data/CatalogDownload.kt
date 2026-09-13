package app.pocketful.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Getting the published catalog onto the device, and keeping it there.
 *
 * Two kinds of file, with two different reasons to be fetched:
 *
 *  - **The index** says which sets are published and at which version. It is small and it is
 *    the only thing that changes when a set is published, so it is asked about often -- at most
 *    every [INDEX_REFRESH_SECONDS] -- and a failed ask keeps the one on disk.
 *  - **A set file** is immutable: `ptcg-en-base01.v3.json.gz` says the same thing forever. So
 *    a set is downloaded only when the index lists a version the device does not have, and
 *    then kept. Browsing and search never touch the network.
 *
 * Every path prefers what is on disk and treats the network as an improvement. A dead
 * connection means an app missing the newest publish, never an app without its catalog.
 */
class CatalogDownload(
    private val storage: SaveStorage,
    private val client: HttpClient = defaultClient(),
) {

    enum class Outcome { FromDisk, Downloaded, Refreshed, Unavailable }

    var outcome: Outcome = Outcome.Unavailable
        private set

    /** How many set files the last [ensure] downloaded. */
    var setsDownloaded: Int = 0
        private set

    /**
     * The catalog, from disk where it is current and from the network where it is not.
     * [force] asks for the index regardless of its age, for a manual refresh.
     *
     * Never throws. Null only when there is no index on the device and none could be fetched.
     */
    suspend fun ensure(nowSeconds: Long, force: Boolean = false): LoadedCatalog? {
        setsDownloaded = 0
        val onDisk = readIndex()
        val age = nowSeconds - (readStamp(INDEX_STAMP_FILE) ?: 0L)
        val stale = force || onDisk == null || age !in 0 until INDEX_REFRESH_SECONDS

        var index = onDisk
        var fetchedIndex = false
        if (stale) {
            val fresh = downloadIndex(nowSeconds)
            if (fresh != null) {
                index = fresh
                fetchedIndex = true
            }
        }
        if (index == null) {
            outcome = Outcome.Unavailable
            return null
        }

        val base = index.publicUrl?.trimEnd('/') ?: BUILT_IN_BASE
        val wanted = index.catalogs.flatMap { c -> c.series.flatMap { it.sets } }
        val gate = Semaphore(4)
        val sets = coroutineScope {
            wanted.map { entry ->
                async { entry.id to gate.withPermit { setFor(base, entry) } }
            }.awaitAll()
        }.mapNotNull { (id, doc) -> doc?.let { id to it } }.toMap()

        outcome = when {
            onDisk == null && fetchedIndex -> Outcome.Downloaded
            fetchedIndex && (setsDownloaded > 0 || index != onDisk) -> Outcome.Refreshed
            else -> Outcome.FromDisk
        }
        return LoadedCatalog(index, sets)
    }

    /** A set at the version the index lists: from disk if it has it, else downloaded. */
    private suspend fun setFor(base: String, entry: IndexSet): SetDoc? {
        val file = setFile(entry.id)
        val onDisk = runCatching { storage.read(file) }.getOrNull()?.let(::parseSet)
        if (onDisk != null && onDisk.version == entry.version) return onDisk

        val fetched = runCatching {
            val response = client.get("$base/${entry.file}")
            if (!response.status.isSuccess()) return@runCatching null
            val bytes = response.readRawBytes()
            withContext(Dispatchers.Default) {
                val text = gunzipToText(bytes) ?: return@withContext null
                parseSet(text)?.takeIf { it.id == entry.id && it.version == entry.version }?.let { it to text }
            }
        }.getOrNull()

        if (fetched != null) {
            runCatching { storage.write(file, fetched.second) }
            setsDownloaded++
            return fetched.first
        }
        // An older version is still a correct description of every card it lists. Better than
        // dropping the set until the network comes back.
        return onDisk
    }

    private suspend fun readIndex(): IndexDoc? {
        val text = runCatching { storage.read(INDEX_FILE) }.getOrNull() ?: return null
        return withContext(Dispatchers.Default) { parseIndex(text) }
    }

    /**
     * Fetches the index, and writes it only once it has parsed. A 404 means nothing has been
     * published yet, which is an empty catalog rather than a failure.
     */
    private suspend fun downloadIndex(nowSeconds: Long): IndexDoc? = runCatching {
        val response = client.get(INDEX_URL)
        if (response.status == HttpStatusCode.NotFound) {
            val empty = IndexDoc(schema = CATALOG_SCHEMA, publicUrl = BUILT_IN_BASE)
            storage.write(INDEX_STAMP_FILE, nowSeconds.toString())
            return@runCatching empty
        }
        if (!response.status.isSuccess()) return@runCatching null
        val text = response.readRawBytes().decodeToString()
        val parsed = withContext(Dispatchers.Default) { parseIndex(text) } ?: return@runCatching null
        storage.write(INDEX_FILE, text)
        storage.write(INDEX_STAMP_FILE, nowSeconds.toString())
        parsed
    }.getOrNull()

    private suspend fun readStamp(name: String): Long? =
        runCatching { storage.read(name)?.trim()?.toLongOrNull() }.getOrNull()

    companion object {
        /**
         * Where the catalog's files are served from until its index says otherwise.
         *
         * Cloudflare's development address for the public bucket. It moves to the catalog's
         * own domain before launch, and that move is this constant plus one value in the index.
         */
        const val BUILT_IN_BASE: String = "https://pub-91194bbc9ec448f9a380bf3c84ba4978.r2.dev"

        /** The one address built into the app. Everything else is named by the index. */
        const val INDEX_URL: String = "$BUILT_IN_BASE/catalog/index.json"

        const val INDEX_FILE: String = "catalog-index.json"
        const val INDEX_STAMP_FILE: String = "catalog-index.stamp"

        fun setFile(setId: String): String = "catalog-set-$setId.json"

        /**
         * How often the app asks whether something new was published. Fifteen minutes: the
         * index is a few kilobytes, and a set published while the app is closed should be there
         * when it opens.
         */
        const val INDEX_REFRESH_SECONDS: Long = 15 * 60

        private fun parseIndex(text: String): IndexDoc? =
            runCatching { CatalogJson.decodeFromString<IndexDoc>(text) }.getOrNull()
                ?.takeIf { it.schema == CATALOG_SCHEMA }

        private fun parseSet(text: String): SetDoc? =
            runCatching { CatalogJson.decodeFromString<SetDoc>(text) }.getOrNull()
                ?.takeIf { it.schema == CATALOG_SCHEMA }

        fun defaultClient(): HttpClient = HttpClient {
            install(UserAgent) { agent = Network.USER_AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}

/** The index and every set file it lists that the device has. */
data class LoadedCatalog(val index: IndexDoc, val sets: Map<String, SetDoc>)
