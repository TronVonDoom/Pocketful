package app.pocketful.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a euro is worth in dollars.
 *
 * The app quotes one currency, because a collection is worth a number and two numbers is
 * not an answer. Most cards are priced by TCGplayer in dollars already; the ones that are
 * not -- promos sold outside North America, Japanese printings, anything never listed as
 * an English single -- have only a Cardmarket figure in euros, and that figure is real
 * money the app should not be throwing away. So it is converted, once, on the way in.
 *
 * A converted figure is approximate and the app says so wherever it shows one. It is also
 * *far* closer to the truth than the two alternatives it replaces: showing nothing, or
 * showing a euro figure with a dollar sign in front of it.
 *
 * Rates are cached like the catalog and for the same reason: the app should work on a
 * plane. A rate from last week converts a card to within a percent or so, which is well
 * inside the noise on a trading-card price -- so a stale rate is used without hesitation
 * and refreshed when the network is there.
 */
class ExchangeRates(
    private val storage: SaveStorage,
    private val client: HttpClient = defaultClient(),
) {

    /** The last rate read or fetched this process, so a screen never waits twice. */
    private var cached: Double? = null

    /**
     * Euros to dollars, or null if the app has never once managed to find out.
     *
     * Null is survivable everywhere it is used: an unconverted price keeps its own
     * currency and the per-currency totals carry it, which is exactly the behaviour that
     * existed before conversion. A missing rate costs a card its dollar figure, never its
     * price.
     */
    suspend fun eurToUsd(nowSeconds: Long): Double? {
        cached?.let { return it }

        val onDisk = read()
        if (onDisk != null) {
            cached = onDisk.rate
            // Old enough to be worth asking about, and still perfectly usable meanwhile.
            if (nowSeconds - onDisk.fetchedAt >= REFRESH_AFTER_SECONDS) {
                download(nowSeconds)?.let { cached = it }
            }
            return cached
        }

        return download(nowSeconds)?.also { cached = it }
    }

    private suspend fun read(): Stored? = runCatching {
        storage.read(FILE)?.let { json.decodeFromString<Stored>(it) }?.takeIf { it.rate > 0.0 }
    }.getOrNull()

    private suspend fun download(nowSeconds: Long): Double? = runCatching {
        val response = client.get(URL)
        if (!response.status.isSuccess()) return null
        val rate = json.decodeFromString<Response>(response.bodyAsText()).rates["USD"]
        // A rate of zero, or one wild enough to be a parsing accident rather than a
        // currency, is refused. EUR/USD has not left this range in the euro's lifetime,
        // and filing a bad rate would misprice every converted card in the collection.
        if (rate == null || rate <= 0.4 || rate >= 4.0) return null
        runCatching { storage.write(FILE, json.encodeToString(Stored(rate, nowSeconds))) }
        rate
    }.getOrNull()

    @Serializable
    private data class Stored(val rate: Double, val fetchedAt: Long)

    @Serializable
    private data class Response(val rates: Map<String, Double> = emptyMap())

    companion object {
        /**
         * The European Central Bank's own daily reference rate, by way of Frankfurter.
         *
         * No key, no account, and a source whose numbers are the ones banks quote against
         * -- which matters more than it sounds: a card converted at a rate somebody can
         * look up is a figure that can be checked.
         */
        private const val URL = "https://api.frankfurter.dev/v1/latest?base=EUR&symbols=USD"

        const val FILE: String = "fx-eur-usd.json"

        /** Reference rates move once a working day, so asking more often is just noise. */
        const val REFRESH_AFTER_SECONDS: Long = 24 * 60 * 60

        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): HttpClient = HttpClient {
            install(UserAgent) { agent = TcgDex.USER_AGENT }
            install(HttpTimeout) {
                connectTimeoutMillis = 8_000
                requestTimeoutMillis = 8_000
            }
        }
    }
}
