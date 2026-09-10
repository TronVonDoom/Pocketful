package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.RemoteCard
import app.pocketful.data.SearchHit
import app.pocketful.data.TcgDex
import app.pocketful.data.catalogFailureMessage
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
                error = catalogFailureMessage(it, "that search")
            }
        searching = false
    }

    /** The full document behind a search row, fetched only once a row is actually chosen. */
    suspend fun fetch(hit: SearchHit): Result<RemoteCard> =
        runCatching { api.card(hit.id) ?: error("That card could not be loaded.") }
            .onFailure { error = catalogFailureMessage(it, "that search") }
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
