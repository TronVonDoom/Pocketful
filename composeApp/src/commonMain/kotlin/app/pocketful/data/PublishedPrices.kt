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
import kotlinx.serialization.json.Json

/**
 * What every card is worth, as one file.
 *
 * The mirror image of [PublishedCatalog], and deliberately a separate document with its
 * own schedule. The catalog is a thing with no expiry date -- a set has not changed since
 * the day it was printed -- and that is precisely what lets the app download it once and
 * simply keep it. A price is true for about a day. Putting the two in one file would give
 * the whole thing the shorter of the two lifetimes, so they never share a file, a tag or
 * a TTL.
 *
 * Built nightly by [Pocketful-Catalog](https://github.com/TronVonDoom/Pocketful-Catalog)
 * from TCGplayer's own catalog by way of tcgcsv.com. One client asks and everybody
 * downloads the answer, which is both far gentler than one request per card per user and
 * the arrangement that service's terms actually require.
 *
 * The gain over asking TCGdex per card is not subtle. TCGdex carries a TCGplayer price
 * for some of the catalog; this covers 20,064 of 23,548 cards, which is essentially every
 * card TCGplayer sells -- the remainder being Pokemon TCG Pocket, which is a phone game,
 * and Trainer Kits, which were never sold as singles. Neither has a market price to miss.
 */
@Serializable
data class PublishedPrices(
    val schema: Int = 0,
    val fetchedAt: String? = null,
    val source: String = "tcgplayer",
    val unit: String = "usd_cents",
    /**
     * Card id to a price per printing.
     *
     * The inner keys are TCGplayer's own printing names in the spelling TCGdex uses --
     * "normal", "holofoil", "reverseHolofoil" -- which is not a coincidence and not a
     * translation layer: the build writes them that way so that [CardImport.priceKeys],
     * which already knew how to choose between them, needs no idea where the figure came
     * from.
     */
    val cards: Map<String, Map<String, Long>> = emptyMap(),
    /**
     * Card id to special printing to a price per printing: `mep-070` -> `holo~pokemon-center`
     * -> `holofoil`. See [PublishedSpecial.priceKey].
     *
     * Separate from [cards] so that a stamped copy's figure can never be read as the plain
     * card's, by this build or by one from before stamps existed.
     */
    val special: Map<String, Map<String, Map<String, Long>>> = emptyMap(),
    /**
     * Every figure from the last day the price job recorded before this one, in the same
     * shape as [cards] and [special]. What a price tag measures its daily move against,
     * without the app downloading any history.
     */
    val previous: PreviousPrices? = null,
) {
    val isUsable: Boolean get() = cards.isNotEmpty() && schema == SCHEMA

    /**
     * Cents for one special printing, in the caller's order of preference.
     *
     * Falls back only within that printing's own quotes -- never to the plain card's. A
     * Pokémon Center Tyrunt with no quote of its own is unpriced, not worth a tenth of itself.
     */
    fun centsForSpecial(cardId: String, priceKey: String, finishKeys: List<String>): Long? {
        val quotes = special[cardId]?.get(priceKey) ?: return null
        return finishKeys.firstNotNullOfOrNull { quotes[it] } ?: quotes.values.firstOrNull()
    }

    /** The same card's figure on the previous day, chosen by the same keys. */
    fun previousCentsFor(cardId: String, finishKeys: List<String>): Long? {
        val quotes = previous?.cards?.get(cardId) ?: return null
        return finishKeys.firstNotNullOfOrNull { quotes[it] } ?: quotes.values.firstOrNull()
    }

    fun previousCentsForSpecial(cardId: String, priceKey: String, finishKeys: List<String>): Long? {
        val quotes = previous?.special?.get(cardId)?.get(priceKey) ?: return null
        return finishKeys.firstNotNullOfOrNull { quotes[it] } ?: quotes.values.firstOrNull()
    }

    /** Cents for the first printing that matches, in the caller's order of preference. */
    fun centsFor(cardId: String, finishKeys: List<String>): Long? {
        val quotes = cards[cardId] ?: return null
        finishKeys.firstNotNullOfOrNull { quotes[it] }?.let { return it }
        // The card is priced, just not in the printing that was asked for. One figure is a
        // far better answer than none -- a reverse holo with only a normal quote is worth
        // roughly a normal, and certainly not nothing.
        return quotes.values.firstOrNull()
    }

    companion object {
        const val SCHEMA: Int = 1

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        fun parse(text: String): PublishedPrices? =
            runCatching { json.decodeFromString<PublishedPrices>(text) }
                .getOrNull()
                ?.takeIf { it.isUsable }
    }
}

/** One earlier day of the price file. See [PublishedPrices.previous]. */
@Serializable
data class PreviousPrices(
    val date: String? = null,
    val cards: Map<String, Map<String, Long>> = emptyMap(),
    val special: Map<String, Map<String, Map<String, Long>>> = emptyMap(),
)

/**
 * Getting that file onto the device and keeping it current.
 *
 * Shaped like [CatalogDownload] with one deliberate difference: this document *does*
 * expire, so the refresh window is a day rather than the better part of a week. What is on
 * disk is still preferred over a fetch that fails, because yesterday's prices are a good
 * answer and no prices is not one.
 */
class PriceDownload(
    private val storage: SaveStorage,
    private val client: HttpClient = defaultClient(),
) {

    enum class Outcome { FromDisk, Downloaded, Refreshed, Unavailable }

    var outcome: Outcome = Outcome.Unavailable
        private set

    suspend fun ensure(nowSeconds: Long): PublishedPrices? {
        val onDisk = read()

        if (onDisk != null) {
            val age = nowSeconds - (readStampSeconds() ?: 0L)
            if (age in 0 until REFRESH_AFTER_SECONDS) {
                outcome = Outcome.FromDisk
                return onDisk
            }
            val fresh = download()
            if (fresh != null) {
                outcome = Outcome.Refreshed
                return fresh
            }
            // Stale but real. A price from yesterday is within a rounding error of today's
            // on almost every card, and is incomparably better than a dash.
            outcome = Outcome.FromDisk
            return onDisk
        }

        val fetched = download()
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

    private suspend fun readStampSeconds(): Long? =
        runCatching { storage.read(STAMP_FILE)?.trim()?.toLongOrNull() }.getOrNull()

    /** Fetch, decompress, parse, and only then write -- so a bad download costs nothing. */
    private suspend fun download(): PublishedPrices? = runCatching {
        val response = client.get(ASSET_URL)
        if (!response.status.isSuccess()) return null

        val bytes = response.readRawBytes()
        val parsed = withContext(Dispatchers.Default) {
            val text = gunzipToText(bytes) ?: return@withContext null
            PublishedPrices.parse(text)?.let { it to text }
        } ?: return null

        runCatching {
            storage.write(FILE, parsed.second)
            storage.write(STAMP_FILE, nowEpochSeconds().toString())
        }
        parsed.first
    }.getOrNull()

    companion object {
        /** A fixed tag whose asset is replaced nightly, so this URL means "today's prices". */
        const val ASSET_URL: String =
            "https://github.com/TronVonDoom/Pocketful-Catalog/releases/download/prices/" +
                "prices-v${PublishedPrices.SCHEMA}.json.gz"

        const val FILE: String = "prices-published.json"
        const val STAMP_FILE: String = "prices-published.stamp"

        /** A day, because that is how often the figures behind it are rebuilt. */
        const val REFRESH_AFTER_SECONDS: Long = 20 * 60 * 60

        fun defaultClient(): HttpClient = HttpClient {
            install(UserAgent) { agent = TcgDex.USER_AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 60_000
            }
        }
    }
}
