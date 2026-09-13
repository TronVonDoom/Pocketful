package app.pocketful.data

import app.pocketful.domain.Copy
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Currency
import app.pocketful.domain.VariantId
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a set's cards have been worth, day by day.
 *
 * Published nightly by Pocketful-Catalog (tools/price_history.py) as one small file per set
 * on the `price-history` release: every day for the last five weeks and one day a week
 * before that, back to February 2024. Each series is aligned with [dates], null where the
 * card had no quote, and carries exactly the figures the price file did on that day -- the
 * same keys, the same plain and special split -- so a chart can never disagree with the
 * price printed above it.
 */
@Serializable
data class PriceHistoryDoc(
    val schema: Int = 0,
    val set: String = "",
    val dates: List<String> = emptyList(),
    val cards: Map<String, Map<String, List<Long?>>> = emptyMap(),
    val special: Map<String, Map<String, Map<String, List<Long?>>>> = emptyMap(),
)

/** One day's figure. [date] is `yyyy-MM-dd`, which sorts chronologically as text. */
data class PricePoint(val date: String, val cents: Long)

/**
 * Getting those files onto the device, one set at a time and only when asked.
 *
 * A card's page wants one set; the portfolio chart wants the sets a collection spans. Nobody
 * wants all two hundred, so nothing is fetched until a screen asks, and what was fetched is
 * kept on disk for most of a day -- the files are rebuilt nightly, so anything younger is
 * already the newest there is.
 */
class PriceHistory(
    private val storage: SaveStorage,
    private val client: HttpClient = PriceDownload.defaultClient(),
) {
    private val memory = mutableMapOf<String, PriceHistoryDoc>()
    private val lock = Mutex()

    suspend fun forSet(setId: String): PriceHistoryDoc? {
        if (setId.isBlank()) return null
        lock.withLock { memory[setId] }?.let { return it }

        val file = "history-$setId.json"
        val stamp = "history-$setId.stamp"
        val onDisk = runCatching { storage.read(file) }.getOrNull()?.let(::parse)
        val age = nowEpochSeconds() - (runCatching { storage.read(stamp)?.trim()?.toLongOrNull() }.getOrNull() ?: 0L)

        val doc = if (onDisk != null && age in 0 until REFRESH_AFTER_SECONDS) {
            onDisk
        } else {
            download(setId)?.also { (parsed, text) ->
                runCatching {
                    storage.write(file, text)
                    storage.write(stamp, nowEpochSeconds().toString())
                }
            }?.first ?: onDisk
        }
        if (doc != null) lock.withLock { memory[setId] = doc }
        return doc
    }

    /** Several sets at once, a few at a time. Sets with no file are simply absent. */
    suspend fun forSets(setIds: Collection<String>): Map<String, PriceHistoryDoc> = coroutineScope {
        val gate = Semaphore(4)
        setIds.distinct().map { id -> async { id to gate.withPermit { forSet(id) } } }
            .awaitAll()
            .mapNotNull { (id, doc) -> doc?.let { id to it } }
            .toMap()
    }

    /** The series for one variant, or empty when the history does not know it. */
    suspend fun seriesFor(variantId: VariantId): List<PricePoint> {
        val parts = CardImport.decompose(variantId) ?: return emptyList()
        val doc = forSet(setOf(parts.remoteId)) ?: return emptyList()
        return doc.series(variantId)
    }

    private suspend fun download(setId: String): Pair<PriceHistoryDoc, String>? = runCatching {
        val response = client.get("$BASE/history-$setId.json.gz")
        if (!response.status.isSuccess()) return null
        val bytes = response.readRawBytes()
        withContext(Dispatchers.Default) {
            val text = gunzipToText(bytes) ?: return@withContext null
            parse(text)?.let { it to text }
        }
    }.getOrNull()

    private fun parse(text: String): PriceHistoryDoc? =
        runCatching { json.decodeFromString<PriceHistoryDoc>(text) }.getOrNull()?.takeIf { it.schema == SCHEMA }

    companion object {
        const val SCHEMA: Int = 1
        const val BASE: String = "https://github.com/TronVonDoom/Pocketful-Catalog/releases/download/price-history"
        const val REFRESH_AFTER_SECONDS: Long = 20 * 60 * 60

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        /** The set a catalog card id belongs to: everything before its last dash. */
        fun setOf(remoteId: String): String = remoteId.substringBeforeLast('-', missingDelimiterValue = "")
    }
}

/**
 * One variant's figures out of a set's history, keyed the way the price file keys them.
 *
 * The finish picks the series exactly as it picks today's price, first matching key first,
 * so the last point of the chart is the price on the tag.
 */
fun PriceHistoryDoc.series(variantId: VariantId): List<PricePoint> {
    val parts = CardImport.decompose(variantId) ?: return emptyList()
    val keys = CardImport.priceKeys(parts.finish, firstEdition = false)
    val special = parts.special
    val byFinish = if (special == null) {
        cards[parts.remoteId]
    } else {
        this.special[parts.remoteId]?.get(CardImport.specialPriceKey(parts.finish, special))
    } ?: return emptyList()
    val values = keys.firstNotNullOfOrNull { byFinish[it] } ?: byFinish.values.firstOrNull() ?: return emptyList()
    return dates.indices.mapNotNull { i -> values.getOrNull(i)?.let { PricePoint(dates[i], it) } }
}

/**
 * What the copies in a collection were worth on every day the history covers.
 *
 * Today's collection, priced at each earlier day's figures: the answer to "how has what I
 * hold moved", not a record of what was held then. A copy counts from the first day its
 * card was quoted and carries its last known figure across a missing day, so a gap in one
 * card's quotes is not drawn as the whole portfolio dropping. Condition scales it the way it
 * scales today's total, and a value set by hand counts at that value throughout. Dollars
 * only, as the headline total is.
 */
fun portfolioSeries(snapshot: CollectionSnapshot, docs: Map<String, PriceHistoryDoc>): List<PricePoint> {
    val dates = docs.values.flatMap { it.dates }.toSortedSet().toList()
    if (dates.isEmpty()) return emptyList()

    val byVariant: Map<VariantId, List<Copy>> = snapshot.copies.values
        .filter { snapshot.currencyOf(it.variantId) == Currency.USD }
        .groupBy { it.variantId }
    val totals = LongArray(dates.size)
    var anything = false

    for ((variantId, copies) in byVariant) {
        val overrides = copies.mapNotNull { it.valueOverride?.cents }
        overrides.forEach { cents -> for (i in dates.indices) totals[i] += cents }
        if (overrides.isNotEmpty()) anything = true

        val scaled = copies.filter { it.valueOverride == null }
        if (scaled.isEmpty()) continue
        val points = CardImport.decompose(variantId)
            ?.let { docs[PriceHistory.setOf(it.remoteId)] }
            ?.series(variantId)
            ?.associate { it.date to it.cents }
            .orEmpty()
        if (points.isEmpty()) {
            // No history for this card -- a price from somewhere other than the price file,
            // or a set with no file. Counted at today's value throughout, so the chart ends
            // on the same total as the headline above it instead of a few cards short.
            val today = scaled.sumOf { snapshot.valueOf(it).cents }
            if (today > 0) {
                anything = true
                for (i in dates.indices) totals[i] += today
            }
            continue
        }
        anything = true
        val factor = scaled.sumOf { if (it.isGraded) 1.0 else it.condition.multiplier }
        var last: Long? = null
        for (i in dates.indices) {
            last = points[dates[i]] ?: last
            last?.let { totals[i] += (it * factor).toLong() }
        }
    }
    if (!anything) return emptyList()
    return dates.indices.map { PricePoint(dates[it], totals[it]) }.dropWhile { it.cents == 0L }
}
