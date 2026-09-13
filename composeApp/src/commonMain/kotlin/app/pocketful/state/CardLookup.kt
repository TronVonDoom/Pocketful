package app.pocketful.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketful.data.CardCatalog
import app.pocketful.data.CatalogCard
import app.pocketful.data.SearchHit
import kotlinx.coroutines.delay

/**
 * Searching the catalog, as a piece of screen state.
 *
 * Held apart from [CollectionStore] on purpose: this is transient UI state about a query in
 * progress. Nothing here writes anything -- filing a card is an explicit call the picker makes
 * once the user has chosen a row.
 */
class CardLookup(private val catalog: CardCatalog) {

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchHit>>(emptyList())
        private set

    var searching by mutableStateOf(false)
        private set

    /** Set when the catalog has nothing to search yet. */
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
        error = if (!catalog.isLoaded) "The card catalog has not downloaded yet." else null
        results = catalog.search(text)
        searching = false
    }

    /** The whole card behind a search row. On the device, so it never waits on the network. */
    fun fetch(hit: SearchHit): Result<CatalogCard> =
        catalog.card(hit.id)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("That card is no longer in the catalog."))
}

/** A lookup bound to the composition, with the query debounced. */
@Composable
fun rememberCardLookup(catalog: CardCatalog): CardLookup {
    val lookup = remember(catalog) { CardLookup(catalog) }
    LaunchedEffect(lookup.query) {
        val text = lookup.query
        if (text.trim().length < 2) return@LaunchedEffect
        delay(200)
        lookup.runSearch(text)
    }
    return lookup
}

/** One catalog per app. */
@Composable
fun rememberCardCatalog(): CardCatalog = remember { CardCatalog() }
