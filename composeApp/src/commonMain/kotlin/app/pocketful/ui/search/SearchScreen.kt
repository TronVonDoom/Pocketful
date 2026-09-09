package app.pocketful.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketful.data.RemoteSet
import app.pocketful.data.SearchHit
import app.pocketful.data.game
import app.pocketful.domain.CardBrief
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.PrintingId
import app.pocketful.domain.TcgGame
import app.pocketful.domain.allBriefs
import app.pocketful.domain.search
import app.pocketful.state.CardLookup
import app.pocketful.state.CatalogBrowser
import app.pocketful.state.CatalogOrder
import app.pocketful.state.CatalogSize
import app.pocketful.state.SeriesGroup
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.CatalogCardTile
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.GameTile
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SegmentedControl
import app.pocketful.ui.components.SeriesBanner
import app.pocketful.ui.components.SetTile
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * Looking outward at what exists, rather than inward at what you own.
 *
 * Every other screen in the app is a view over the collection. This one is a view over the
 * catalog, which is what makes it worth a tab of its own: until now the only way to see a
 * card the app did not already know about was to open an empty pocket and search from
 * inside it, which meant you had to decide where a card was going before you were allowed
 * to look it up.
 *
 * There are two ways in and one box. The search field is a card search, and what sits
 * below it is a browse: games, then that game's sets grouped into eras. A set opens as a
 * screen of its own rather than filtering this one, which is why the set filter that used
 * to live here is gone: a set is a place, not a narrowing of the search box.
 *
 * The game is the frame for both. At the top of the tab the search box reaches the whole
 * catalog; inside a game it reaches only that game. That distinction exists because one
 * upstream catalog answers for both the printed Pokémon TCG and Pokémon TCG Pocket, and a
 * search that quietly mixed them was the same fault as an era list that did -- a Genetic
 * Apex Pikachu offered beside a Base Set one, with nothing on the tile to tell them
 * apart.
 *
 * Results from the local catalog and the live one are shown in separate sections rather
 * than merged. They mean different things -- one is a card the app can add instantly, the
 * other is a card it has to fetch first -- and a single blended list would make "why did
 * that one take two seconds" unanswerable.
 */
@Composable
fun SearchScreen(
    snapshot: CollectionSnapshot,
    lookup: CardLookup,
    browser: CatalogBrowser,
    /**
     * Which game's sets are being browsed. Owned by the app rather than by this screen:
     * it is one step of a drill-down, so the back gesture has to be able to unwind it.
     */
    game: TcgGame?,
    onGameChange: (TcgGame?) -> Unit,
    onOpenSet: (RemoteSet) -> Unit,
    onAddRemoteCard: (SearchHit) -> Unit,
    onAddLocalCard: (CardBrief) -> Unit,
    importing: Boolean,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    // Fetched on first arrival rather than at launch: the app should not spend a request
    // on a screen nobody has opened. Idempotent, so coming back is free.
    LaunchedEffect(browser) { browser.load() }

    LaunchedEffect(query) { lookup.onQueryChanged(query) }

    val trimmed = query.trim()
    val searching = trimmed.isNotEmpty()

    // Re-sorting a game's whole catalog is cheap once and wasteful sixty times a second,
    // so it is tied to the things that can change the answer rather than to the frame.
    val arranged = remember(browser.groups, browser.seriesOrder, browser.setOrder, game) {
        game?.let { browser.arrange(it) }.orEmpty()
    }
    // Scoped to the browsed game when there is one. A set search run from inside Scarlet
    // and Violet that offered a mobile-game set would be the mixing this screen just undid.
    val searchable = remember(browser.sets, game) {
        game?.let { browser.setsOf(it) } ?: browser.sets
    }
    val matchingSets = remember(searchable, trimmed) { searchable.matching(trimmed) }

    val localCatalog = remember(snapshot) { snapshot.allBriefs() }
    // One row per printing, not per press run. The catalog holds a normal, a holo and a
    // reverse of the same card as three variants, and three tiles with the same art and
    // the same number under them is a list that looks broken -- the finish is a choice
    // made in the sheet that opens, where the price for each one is visible.
    val localResults = remember(localCatalog, trimmed, game, browser.sets) {
        if (trimmed.isEmpty()) {
            emptyList()
        } else {
            localCatalog.search(trimmed, limit = 40)
                // Your own cards, narrowed to the game you are standing in. A printing
                // knows its set and the set knows its game, so this is a lookup rather
                // than a guess -- and a card whose set the catalog has never heard of
                // stays visible, because a set nobody upstream recognises is far more
                // likely to be a hand-typed printed card than anything digital.
                .filter { game == null || browser.gameOfSet(it.setCode) == game }
                .groupBy { it.printingId }
                // The plainest finish stands for the group. Ranking inside a printing is
                // by price, which would otherwise put a card's reverse holo forward as
                // the face of it -- and the sheet opens on whichever one the tile is, so
                // the representative is also the default answer.
                .values
                .map { group -> group.minBy { it.finish.ordinal } }
                .take(20)
        }
    }
    // Counted per printing to match, so a tile that stands for three variants does not
    // claim you own none of it because you own the reverse rather than the normal.
    val ownedCounts = remember(snapshot) {
        snapshot.copies.values
            .mapNotNull { snapshot.variants[it.variantId]?.printingId }
            .groupingBy { it }
            .eachCount()
    }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Background)) {
        ScreenBackdrop(Ink.Accent, height = 280.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = islandBottomInset()),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                // One header in two moods. Drilled into a game it names the game and wears
                // a way back out; at the top of the tab it names what the tab is for. The
                // search field is part of it either way -- it is the primary control here,
                // and a control that scrolls away from its own heading reads as debris.
                ScreenHeader(
                    eyebrow = if (game == null) "Catalog" else "Browsing",
                    // Not "every card ever printed" any more: one of the six games in
                    // this list was never printed at all.
                    title = game?.label ?: "Every card, every set",
                    subtitle = game?.publisher
                        ?: "Search by name, or work through a game set by set.",
                    leading = if (game == null) null else {
                        {
                            CircleIconButton(
                                icon = AppIcons.ChevronLeft,
                                contentDescription = "Back to games",
                                onClick = { onGameChange(null) },
                                size = 34.dp,
                            )
                        }
                    },
                    stats = buildList {
                        // Inside a game these count that game. At the top of the tab they
                        // count the catalog whole, which is the only place a total is the
                        // answer to anything: "218 sets" under a Scarlet and Violet header
                        // would be counting fifteen sets that header does not cover.
                        val size = game?.let { browser.sizeOf(it) }
                            ?: browser.sets.size.takeIf { it > 0 }
                                ?.let { CatalogSize(it, browser.groups.size) }
                        if (size != null) {
                            add(Stat("${size.sets}", "sets"))
                            add(Stat("${size.series}", "eras"))
                        }
                        if (game == null) add(Stat("${TcgGame.browsable.size}", "games"))
                        if (localCatalog.isNotEmpty()) add(Stat("${localCatalog.size}", "yours"))
                    },
                    footer = {
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            // Says what the box will actually reach. Inside a game it is
                            // that game's cards, and a field still offering "every card"
                            // would be promising results it has just been told to hide.
                            placeholder = game?.let { "Search ${it.wordmark}" }
                                ?: "Search every card in the catalog",
                        )
                    },
                )
            }

            when {
                searching -> {
                    setMatches(matchingSets, onOpenSet)
                    cardResults(
                        query = trimmed,
                        game = game,
                        localResults = localResults,
                        ownedCounts = ownedCounts,
                        lookup = lookup,
                        localCatalog = localCatalog,
                        importing = importing,
                        onAddLocalCard = onAddLocalCard,
                        onAddRemoteCard = onAddRemoteCard,
                    )
                }

                game == null -> gameGrid(browser = browser, onPick = onGameChange)

                else -> setGrid(browser = browser, groups = arranged, onOpenSet = onOpenSet)
            }
        }
    }
}

// ------------------------------------------------------------------- browsing

/**
 * The games, as the front door.
 *
 * Two to a row with the connected ones first, and the ones without a catalog left in place
 * rather than hidden. "Can I track my Lorcana in this" is a real question, and an app that
 * silently omits it reads as one that has never considered it.
 *
 * A connected game is captioned with what is actually behind it rather than with its
 * publisher. That began as a nicety and became necessary: the Pokémon TCG and Pokémon TCG
 * Pocket share a publisher, so two tiles both reading "The Pokémon Company" would leave
 * the very split this screen exists to make looking like a duplicate row.
 */
private fun LazyListScope.gameGrid(browser: CatalogBrowser, onPick: (TcgGame) -> Unit) {
    val games = TcgGame.browsable
    item { SectionHeader("Games · ${games.size}", Modifier.padding(top = 4.dp, bottom = 2.dp)) }

    tileRows(items = games, keyPrefix = "game", key = { it.name }) { entry ->
        val size = if (entry.connected) browser.sizeOf(entry) else null
        GameTile(
            game = entry,
            caption = size?.caption ?: entry.note,
            onClick = { onPick(entry) },
            modifier = Modifier.weight(1f),
        )
    }

    item {
        Notice(
            "Pokémon is the game with a catalog behind it, and its printed sets and its " +
                "mobile game are kept apart -- a TCG Pocket card is not something you can " +
                "sleeve or sell, and nothing prices one. The rest are listed so the app is " +
                "honest about what it does not cover yet.",
        )
    }
}

/**
 * One game's whole catalog: every set, under the era it was printed in.
 *
 * Grouped rather than flat because two hundred sets in one list is a wall to scroll, and
 * the era is the thing collectors actually navigate by -- "it's a Sun & Moon set" is how
 * people remember where a card lives.
 */
private fun LazyListScope.setGrid(
    browser: CatalogBrowser,
    groups: List<SeriesGroup>,
    onOpenSet: (RemoteSet) -> Unit,
) {
    if (groups.isEmpty()) {
        item {
            Notice(
                text = browser.error ?: if (browser.loading) {
                    "Loading the catalog index…"
                } else {
                    "The catalog came back empty."
                },
                error = browser.error != null,
            )
        }
        return
    }

    item { SortPanel(browser, eras = groups.size, modifier = Modifier.padding(top = 2.dp)) }
    browser.error?.let { message -> item { Notice(message, error = true) } }

    groups.forEach { group ->
        val id = group.series.id
        item(key = "series-$id") {
            SeriesBanner(
                name = group.series.name,
                caption = listOfNotNull(
                    "${group.sets.size} ${if (group.sets.size == 1) "set" else "sets"}",
                    group.years,
                ).joinToString(" · "),
                logoStem = group.series.logo,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
        }

        tileRows(items = group.sets, keyPrefix = "set-$id", key = { it.id }) { set ->
            SetTile(set = set, onClick = { onOpenSet(set) }, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * How the eras are ordered, and how the sets inside them are ordered.
 *
 * Two controls rather than one because they answer different questions. Someone working
 * through the modern game wants the newest era at the top but the sets inside it in the
 * order they were released, and a single ordering cannot give them that.
 *
 * The first of the two disappears for a game with a single era, which is the whole of
 * Pokémon TCG Pocket. Four ways to sort a list of one is a control that cannot do
 * anything, and one of those on screen teaches people that the other three might be the
 * same.
 */
@Composable
private fun SortPanel(browser: CatalogBrowser, eras: Int, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth(), padding = 12.dp) {
        if (eras > 1) {
            FieldLabel("Series")
            Box(Modifier.height(6.dp))
            SegmentedControl(
                options = CatalogOrder.entries,
                selected = browser.seriesOrder,
                onSelect = { browser.seriesOrder = it },
                label = { it.label },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.height(11.dp))
        }
        FieldLabel(if (eras > 1) "Sets within a series" else "Sets")
        Box(Modifier.height(6.dp))
        SegmentedControl(
            options = CatalogOrder.entries,
            selected = browser.setOrder,
            onSelect = { browser.setOrder = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- result lists

/**
 * Sets whose name matches what is being typed.
 *
 * Shown above the cards rather than instead of them: "151" is both a set and a run of
 * card numbers, and someone typing "evolving skies" is almost certainly after the set
 * while someone typing "charizard" is not. Ranking exact prefixes first and capping the
 * list keeps this a shortcut rather than a second wall of results.
 */
private fun LazyListScope.setMatches(sets: List<RemoteSet>, onOpenSet: (RemoteSet) -> Unit) {
    if (sets.isEmpty()) return
    item { SectionHeader("Sets · ${sets.size}", Modifier.padding(top = 4.dp)) }
    // Split by game on the same rule the card results use, and for the same reason: at
    // the top of the tab "promo" matches both a printed promo set and Promos-A, and two
    // logo tiles side by side do not say which game either belongs to.
    val byGame = sets.groupBy { it.game }
    byGame.entries.sortedBy { it.key.ordinal }.forEach { (game, rows) ->
        if (byGame.size > 1) {
            item(key = "match-head-${game.name}") {
                SectionHeader("${game.label} · ${rows.size}", Modifier.padding(top = 2.dp))
            }
        }
        tileRows(items = rows, keyPrefix = "match-${game.name}", key = { it.id }) { set ->
            SetTile(set = set, onClick = { onOpenSet(set) }, modifier = Modifier.weight(1f))
        }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.cardResults(
    query: String,
    /** The game being browsed, if any. Narrows the search to it and names it in the header. */
    game: TcgGame?,
    localResults: List<CardBrief>,
    ownedCounts: Map<PrintingId, Int>,
    lookup: CardLookup,
    localCatalog: List<CardBrief>,
    importing: Boolean,
    onAddLocalCard: (CardBrief) -> Unit,
    onAddRemoteCard: (SearchHit) -> Unit,
) {
    if (importing) item { Notice("Fetching card details…") }

    if (localResults.isNotEmpty()) {
        item { SectionHeader("Already in your catalog · ${localResults.size}", Modifier.padding(top = 4.dp)) }
        tileRows(items = localResults, keyPrefix = "local", key = { it.variantId.value }) { brief ->
            val owned = ownedCounts[brief.printingId] ?: 0
            CardTile(
                brief = brief,
                value = brief.marketValue.displayOrNull(),
                badge = if (owned > 0) "OWN $owned" else null,
                badgeColor = Ink.Gain,
                onClick = { onAddLocalCard(brief) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (query.length < 2) {
        item { Notice("Type at least two letters to search the full catalog.") }
        return
    }

    // Cards already in the local catalog are filtered out rather than shown greyed: they
    // are listed above, and a card that appears twice in one screen reads as a bug.
    // Matched on name and printed number rather than on id, because ids only line up for
    // cards that came from this catalog in the first place.
    val known = localCatalog.map { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" }.toSet()
    val remote = lookup.results
        .filterNot { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" in known }
        // The name endpoint answers across the whole catalog, so narrowing to the browsed
        // game happens here. Upstream cannot be asked the narrower question.
        .filter { game == null || it.game == game }

    item {
        SectionHeader(
            title = when {
                lookup.searching -> "Searching ${game?.label ?: "the full catalog"}…"
                remote.isEmpty() -> game?.label ?: "Full catalog"
                else -> "${game?.label ?: "Full catalog"} · ${remote.size} found"
            },
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    lookup.error?.let { message -> item { Notice(message, error = true) } }

    // One block per game, headed only when there is more than one block to tell apart.
    // Inside a game there never is; at the top of the tab a search for "Pikachu" really
    // does span both Pokémon catalogs, and an unlabelled run of tiles from each is the
    // same mixing the browse below just stopped doing.
    val byGame = remote.groupBy { it.game }
    byGame.entries.sortedBy { it.key.ordinal }.forEach { (hitGame, rows) ->
        if (byGame.size > 1) {
            item(key = "remote-head-${hitGame.name}") {
                SectionHeader("${hitGame.label} · ${rows.size}", Modifier.padding(top = 2.dp))
            }
        }
        tileRows(items = rows, keyPrefix = "remote-${hitGame.name}", key = { it.id }) { hit ->
            CatalogCardTile(
                hit = hit,
                caption = "${hit.setName} · ${hit.collectorNumber}",
                enabled = !importing,
                onClick = { onAddRemoteCard(hit) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (!lookup.searching && remote.isEmpty() && lookup.error == null && localResults.isEmpty()) {
        item { Notice("Nothing in the online catalog matches \"$query\".") }
    }
}

/** The handful of sets worth offering as a shortcut, best guess first. */
private fun List<RemoteSet>.matching(query: String): List<RemoteSet> {
    if (query.length < 2) return emptyList()
    val q = query.lowercase()
    return asSequence()
        .filter { it.name.lowercase().contains(q) || it.id.lowercase() == q }
        .sortedWith(
            // A prefix match is what was meant; among equals the newest set is the one
            // most likely being looked for, since that is what people are opening.
            compareByDescending<RemoteSet> { it.name.lowercase().startsWith(q) }
                .thenByDescending { it.releaseDate ?: "" },
        )
        .take(6)
        .toList()
}

/** A line of explanation between result sections. Not an error unless it says so. */
@Composable
private fun Notice(text: String, error: Boolean = false) {
    Text(
        text = text,
        color = if (error) Ink.Loss else Ink.TextTertiary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}
