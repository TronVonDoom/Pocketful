package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.CatalogUnavailable
import app.pocketful.data.RemoteCard
import app.pocketful.data.SearchHit
import app.pocketful.data.TcgDex
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.delay

/**
 * Searching the live catalog, as a piece of screen state.
 *
 * Held apart from [CollectionStore] on purpose: this is transient UI state about a query
 * in progress, and folding it into the store would mean a failed network call showing up
 * as a change to the collection. Nothing here writes anything -- an import is an explicit
 * call the picker makes once the user has chosen a row.
 */
class CardLookup(private val api: TcgDex) {

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchHit>>(emptyList())
        private set

    var searching by mutableStateOf(false)
        private set

    /** Set when the last attempt failed. Cleared by the next successful one. */
    var error by mutableStateOf<String?>(null)
        private set

    fun onQueryChanged(value: String) {
        query = value
        if (value.trim().length < 2) {
            results = emptyList()
            error = null
            searching = false
        }
    }

    suspend fun runSearch(text: String) {
        if (text.trim().length < 2) return
        searching = true
        error = null
        runCatching { api.search(text) }
            .onSuccess { results = it }
            .onFailure {
                results = emptyList()
                error = offlineMessage(it)
            }
        searching = false
    }

    /** The full document behind a search row, fetched only once a row is actually chosen. */
    suspend fun fetch(hit: SearchHit): Result<RemoteCard> =
        runCatching { api.card(hit.id) ?: error("That card could not be loaded.") }
            .onFailure { error = offlineMessage(it) }

    /**
     * Failures reach the user as one sentence about what to do, not as a stack of Ktor
     * exception names.
     *
     * Four outcomes, because they want four different reactions and merging them wastes
     * the only thing the user has to go on. The catalog being down is worth waiting out;
     * being rate-limited is worth slowing down; being offline is worth checking the
     * wifi; and an answer this app could not read is none of those -- it means the app
     * needs updating, and saying "try again in a moment" to that is advice that will
     * never once work.
     *
     * The first two are decided on a type rather than by reading the exception's message,
     * which is what this used to do. Substring-matching English out of whatever the
     * platform's socket layer happened to say was a guess even when it was right, and it
     * put every failure the guess missed into the same reassuring sentence. Only the
     * offline case still guesses, because the exceptions for it are platform types that
     * common code cannot name.
     */
    private fun offlineMessage(cause: Throwable): String = when {
        cause is CatalogUnavailable && cause.status == TOO_MANY_REQUESTS ->
            "The card catalog is asking for fewer searches at once. Try again in a moment."

        cause is CatalogUnavailable && cause.status >= SERVER_ERROR ->
            "The card catalog is down right now -- nothing to do with your collection, " +
                "which is safe on this device. Try again in a few minutes."

        cause is CatalogUnavailable ->
            "The card catalog would not answer that search (${cause.status})."

        cause is HttpRequestTimeoutException ->
            "The card catalog took too long to answer. Try again in a moment."

        looksOffline(cause) ->
            "Could not reach the card catalog. Check your connection and try again."

        else ->
            "The card catalog sent back something this app could not read. This one is " +
                "worth reporting -- searching may need an app update to work again."
    }

    /**
     * A guess, and the only one left.
     *
     * There is no common-code type for "the socket could not be opened": it is a
     * `java.net` exception on Android and something else everywhere else. So this reads
     * the message, which is what the whole of this function used to do.
     */
    private fun looksOffline(cause: Throwable): Boolean {
        val text = (cause.message ?: "") + " " + (cause.cause?.message ?: "")
        return listOf("resolve", "connect", "unreachable", "network", "host")
            .any { text.contains(it, ignoreCase = true) }
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500
    }
}

/**
 * A lookup bound to the composition, with the query debounced.
 *
 * 350ms is long enough that typing "charizard" is one request rather than nine, and short
 * enough that the list still feels like it is following the keyboard.
 */
@Composable
fun rememberCardLookup(api: TcgDex): CardLookup {
    val lookup = remember(api) { CardLookup(api) }
    LaunchedEffect(lookup.query) {
        val text = lookup.query
        if (text.trim().length < 2) return@LaunchedEffect
        delay(350)
        lookup.runSearch(text)
    }
    return lookup
}

/** One API client per app, so the set index is fetched once rather than once per sheet. */
@Composable
fun rememberTcgDex(): TcgDex = remember { TcgDex() }
