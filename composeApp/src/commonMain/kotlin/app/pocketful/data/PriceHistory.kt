package app.pocketful.data

import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Copy
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

/**
 * What a set's printings have been worth, day by day.
 *
 * Published nightly by Pocketful-Catalog beside the price file, one small file per published
 * set: `prices/history/<set id>.json.gz`. Each printing's series is aligned with [dates], null
 * where it had no figure, and carries exactly what the price file did on each day, so a chart
 * can never disagree with the price printed above it.
 */
@Serializable
data class PriceHistoryDoc(
    val schema: Int = 0,
    val set: String = "",
    val dates: List<String> = emptyList(),
    /** Printing ID to cents per day, aligned with [dates]. */
    val printings: Map<String, List<Long?>> = emptyMap(),
)

/** One day's figure. [date] is `yyyy-MM-dd`, which sorts chronologically as text. */
data class PricePoint(val date: String, val cents: Long)

/**
 * Getting those files onto the device, one set at a time and only when a screen asks. What was
 * fetched is kept for most of a day: the files are rebuilt nightly.
 */
class PriceHistory(
    private val storage: SaveStorage,
    private val baseUrl: () -> String,
    private val client: HttpClient = PriceDownload.defaultClient(),
) {
    private val memory = mutableMapOf<String, PriceHistoryDoc>()
    private val lock = Mutex()

    suspend fun forSet(setId: String): PriceHistoryDoc? {
        if (setId.isBlank()) return null
        lock.withLock { memory[setId] }?.let { return it }

        val file = "history-v2-$setId.json"
        val stamp = "history-v2-$setId.stamp"
        val onDisk = runCatching { storage.read(file) }.getOrNull()?.let(::parse)
        val age = nowEpochSeconds() - (runCatching { storage.read(stamp)?.trim()?.toLongOrNull() }.getOrNull() ?: 0L)

        val doc = if (onDisk != null && age in 0 until REFRESH_AFTER_SECONDS) {
            onDisk
        } else {
            download(setId)?.also { (_, text) ->
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
        setIds.filter { it.isNotBlank() }.distinct()
            .map { id -> async { id to gate.withPermit { forSet(id) } } }
            .awaitAll()
            .mapNotNull { (id, doc) -> doc?.let { id to it } }
            .toMap()
    }

    /** The series for one variant, or empty when the history does not know it. */
    suspend fun seriesFor(variantId: VariantId): List<PricePoint> {
        val doc = forSet(CatalogIds.setOf(variantId.value)) ?: return emptyList()
        return doc.series(variantId)
    }

    private suspend fun download(setId: String): Pair<PriceHistoryDoc, String>? = runCatching {
        val response = client.get("${baseUrl().trimEnd('/')}/prices/history/$setId.json.gz")
        if (!response.status.isSuccess()) return@runCatching null
        val bytes = response.readRawBytes()
        withContext(Dispatchers.Default) {
            val text = gunzipToText(bytes) ?: return@withContext null
            parse(text)?.let { it to text }
        }
    }.getOrNull()

    private fun parse(text: String): PriceHistoryDoc? =
        runCatching { CatalogJson.decodeFromString<PriceHistoryDoc>(text) }.getOrNull()?.takeIf { it.schema == SCHEMA }

    companion object {
        const val SCHEMA: Int = 2
        const val REFRESH_AFTER_SECONDS: Long = 20 * 60 * 60
    }
}

/** One variant's figures out of its set's history. */
fun PriceHistoryDoc.series(variantId: VariantId): List<PricePoint> {
    val values = printings[variantId.value] ?: return emptyList()
    return dates.indices.mapNotNull { i -> values.getOrNull(i)?.let { PricePoint(dates[i], it) } }
}

/**
 * What the copies in a collection were worth on every day the history covers.
 *
 * Today's collection, priced at each earlier day's figures: the answer to "how has what I hold
 * moved", not a record of what was held then. A copy counts from the first day its printing was
 * quoted and carries its last known figure across a missing day, so a gap in one card's quotes
 * is not drawn as the whole portfolio dropping. Condition scales it the way it scales today's
 * total, and a value set by hand counts at that value throughout. Dollars only, as the headline
 * total is.
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
        val points = docs[CatalogIds.setOf(variantId.value)]
            ?.series(variantId)
            ?.associate { it.date to it.cents }
            .orEmpty()
        if (points.isEmpty()) {
            // No history for this card: counted at today's value throughout, so the chart ends
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
