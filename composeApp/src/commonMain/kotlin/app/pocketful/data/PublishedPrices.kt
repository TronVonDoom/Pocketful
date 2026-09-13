package app.pocketful.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * What every published printing is worth, as one file.
 *
 * Built nightly by Pocketful-Catalog from TCGplayer's own figures (by way of tcgcsv.com) for
 * every printing that has a TCGplayer product linked in the catalog, and published beside the
 * catalog as `prices/prices.json.gz`. Keyed by printing ID -- `ptcg-en-base01-4_1st-edition-holo`
 * -- which is exactly the app's variant ID, so pricing a collection is a map lookup per card.
 *
 * A separate file from the catalog on purpose. A set file never changes; a price is true for
 * about a day. Kept together, the catalog would inherit the price's lifetime.
 */
@Serializable
data class PublishedPrices(
    val schema: Int = 0,
    val fetchedAt: String? = null,
    /** The day the figures are for, `yyyy-MM-dd`. */
    val date: String? = null,
    val currency: String = "USD",
    /** Printing ID to market price, in cents. */
    val printings: Map<String, Long> = emptyMap(),
    /** The same figures on the last day before this one that the job recorded. */
    val previous: PreviousPrices? = null,
) {
    val isUsable: Boolean get() = schema == SCHEMA

    fun cents(printingId: String): Long? = printings[printingId]?.takeIf { it > 0 }

    fun previousCents(printingId: String): Long? = previous?.printings?.get(printingId)?.takeIf { it > 0 }

    companion object {
        const val SCHEMA: Int = 2

        fun parse(text: String): PublishedPrices? =
            runCatching { CatalogJson.decodeFromString<PublishedPrices>(text) }
                .getOrNull()
                ?.takeIf { it.isUsable }
    }
}

@Serializable
data class PreviousPrices(
    val date: String? = null,
    val printings: Map<String, Long> = emptyMap(),
)

/**
 * Getting the price file onto the device and keeping it current.
 *
 * Shaped like [CatalogDownload], with a day for a refresh window: the file is rebuilt nightly.
 * What is on disk is still preferred over a fetch that fails, because yesterday's prices are a
 * good answer and no prices is not one.
 */
class PriceDownload(
    private val storage: SaveStorage,
    private val client: HttpClient = defaultClient(),
) {

    enum class Outcome { FromDisk, Downloaded, Refreshed, Unavailable }

    var outcome: Outcome = Outcome.Unavailable
        private set

    suspend fun ensure(base: String, nowSeconds: Long, force: Boolean = false): PublishedPrices? {
        val onDisk = read()
        if (onDisk != null) {
            val age = nowSeconds - (readStamp() ?: 0L)
            if (!force && age in 0 until REFRESH_AFTER_SECONDS) {
                outcome = Outcome.FromDisk
                return onDisk
            }
            val fresh = download(base, nowSeconds)
            if (fresh != null) {
                outcome = Outcome.Refreshed
                return fresh
            }
            outcome = Outcome.FromDisk
            return onDisk
        }
        val fetched = download(base, nowSeconds)
        outcome = if (fetched != null) Outcome.Downloaded else Outcome.Unavailable
        return fetched
    }

    private suspend fun read(): PublishedPrices? {
        val text = runCatching { storage.read(FILE) }.getOrNull() ?: return null
        val parsed = withContext(Dispatchers.Default) { PublishedPrices.parse(text) }
        if (parsed == null) {
            runCatching { storage.delete(FILE) }
            runCatching { storage.delete(STAMP_FILE) }
        }
        return parsed
    }

    private suspend fun readStamp(): Long? =
        runCatching { storage.read(STAMP_FILE)?.trim()?.toLongOrNull() }.getOrNull()

    /** Fetch, decompress, parse, and only then write -- so a bad download costs nothing. */
    private suspend fun download(base: String, nowSeconds: Long): PublishedPrices? = runCatching {
        val response = client.get("${base.trimEnd('/')}/$PATH")
        if (!response.status.isSuccess()) return@runCatching null
        val bytes = response.readRawBytes()
        val parsed = withContext(Dispatchers.Default) {
            val text = gunzipToText(bytes) ?: return@withContext null
            PublishedPrices.parse(text)?.let { it to text }
        } ?: return@runCatching null
        runCatching {
            storage.write(FILE, parsed.second)
            storage.write(STAMP_FILE, nowSeconds.toString())
        }
        parsed.first
    }.getOrNull()

    companion object {
        const val PATH: String = "prices/prices.json.gz"
        const val FILE: String = "prices-v2.json"
        const val STAMP_FILE: String = "prices-v2.stamp"

        /** Twenty hours: the figures behind it are rebuilt once a day. */
        const val REFRESH_AFTER_SECONDS: Long = 20 * 60 * 60

        fun defaultClient(): HttpClient = HttpClient {
            install(UserAgent) { agent = Network.USER_AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 60_000
            }
        }
    }
}
