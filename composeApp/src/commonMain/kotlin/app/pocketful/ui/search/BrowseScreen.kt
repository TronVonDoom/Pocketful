package app.pocketful.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketful.data.CatalogSet
import app.pocketful.domain.TcgGame
import app.pocketful.state.CatalogBrowser
import app.pocketful.state.CatalogOrder
import app.pocketful.state.SeriesGroup
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.GameTile
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.Headline
import app.pocketful.ui.components.Note
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SegmentedControl
import app.pocketful.ui.components.SeriesBanner
import app.pocketful.ui.components.SetTile
import app.pocketful.ui.components.TopBar
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Space

/**
 * Looking outward at what exists, rather than inward at what you have.
 *
 * Every other screen in the app is a view over the collection. This one is a view over the
 * catalog: games, then that game's sets grouped into the eras they were printed in, then a
 * set's own page with its checklist.
 *
 * It used to carry a search box as well, and that box was the problem this rebuild set out
 * to fix -- a field that reached the catalog but could not see your own cards, sitting on a
 * tab you had to be on to use it. Search is the app's now, reachable from the top of every
 * screen, and what is left here is the thing search is bad at: working through a set
 * methodically, which is how binders actually get filled.
 *
 * The game is the frame. One upstream catalog answers for both the printed Pokémon TCG and
 * Pokémon TCG Pocket, and a browse that quietly mixed them would offer a Genetic Apex
 * Pikachu beside a Base Set one with nothing on the tile to tell them apart.
 */
@Composable
fun BrowseScreen(
    browser: CatalogBrowser,
    /**
     * Which game's sets are being browsed. Owned by the app rather than by this screen: it
     * is one step of a drill-down, so the back gesture has to be able to unwind it.
     */
    game: TcgGame?,
    onGameChange: (TcgGame?) -> Unit,
    onOpenSet: (CatalogSet) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fetched on first arrival rather than at launch: the app should not spend a request on
    // a screen nobody has opened. Idempotent, so coming back is free.
    LaunchedEffect(browser) { browser.load() }

    // Re-sorting a game's whole catalog is cheap once and wasteful sixty times a second, so
    // it is tied to the things that can change the answer rather than to the frame.
    val arranged = remember(browser.groups, browser.seriesOrder, browser.setOrder, game) {
        game?.let { browser.arrange(it) }.orEmpty()
    }
    val size = remember(browser.sets, browser.groups, game) {
        game?.let { browser.sizeOf(it) }
    }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Canvas)) {
        ScreenBackdrop(Ink.Aura, height = 300.dp)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // One bar in two moods. Drilled into a game it names the game and wears a way
            // back out; at the top of the tab it names what the tab is for.
            TopBar(
                title = game?.label ?: "Browse",
                subtitle = game?.publisher,
                onBack = if (game == null) null else ({ onGameChange(null) }),
                backDescription = "Back to games",
                actions = { HeaderAction(AppIcons.Search, "Search everything", onOpenSearch) },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.lg,
                    end = Space.lg,
                    top = Space.sm,
                    bottom = islandBottomInset(),
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                item {
                    Headline(
                        value = when {
                            game == null -> "${TcgGame.browsable.size}"
                            size != null -> "${size.sets}"
                            else -> "—"
                        },
                        label = if (game == null) "Catalog" else "Sets",
                        caption = when {
                            game == null -> "games listed · " +
                                "${browser.sets.size} ${if (browser.sets.size == 1) "set" else "sets"} " +
                                "you can browse today"
                            size != null ->
                                "across ${size.series} ${if (size.series == 1) "era" else "eras"}"
                            else -> "still loading"
                        },
                    )
                }

                when {
                    game == null -> gameGrid(browser = browser, onPick = onGameChange)
                    else -> setGrid(browser = browser, groups = arranged, onOpenSet = onOpenSet)
                }
            }
        }
    }
}

/**
 * The games, as the front door.
 *
 * Two to a row with the connected ones first, and the ones without a catalog left in place
 * rather than hidden. "Can I track my Lorcana in this" is a real question, and an app that
 * silently omits it reads as one that has never considered it.
 *
 * A connected game is captioned with what is actually behind it rather than with its
 * publisher. That began as a nicety and became necessary: the Pokémon TCG and Pokémon TCG
 * Pocket share a publisher, so two tiles both reading "The Pokémon Company" would leave the
 * very split this screen exists to make looking like a duplicate row.
 */
private fun LazyListScope.gameGrid(browser: CatalogBrowser, onPick: (TcgGame) -> Unit) {
    val games = TcgGame.browsable
    item {
        SectionHeader("Games · ${games.size}", Modifier.padding(top = Space.xs), icon = AppIcons.Collections)
    }

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
        Note(
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
 * Grouped rather than flat because two hundred sets in one list is a wall to scroll, and the
 * era is the thing collectors actually navigate by -- "it's a Sun & Moon set" is how people
 * remember where a card lives.
 */
private fun LazyListScope.setGrid(
    browser: CatalogBrowser,
    groups: List<SeriesGroup>,
    onOpenSet: (CatalogSet) -> Unit,
) {
    if (groups.isEmpty()) {
        item {
            Note(
                text = browser.error ?: if (browser.loading) {
                    "Loading the catalog index…"
                } else {
                    "The catalog came back empty."
                },
                error = browser.error != null,
                icon = if (browser.loading) AppIcons.Clock else AppIcons.Info,
            )
        }
        return
    }

    item { SortPanel(browser, eras = groups.size) }
    browser.error?.let { message -> item { Note(message, error = true) } }

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
                modifier = Modifier.padding(top = Space.sm),
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
 * Pokémon TCG Pocket. Four ways to sort a list of one is a control that cannot do anything,
 * and one of those on screen teaches people that the other three might be the same.
 */
@Composable
private fun SortPanel(browser: CatalogBrowser, eras: Int, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth(), padding = Space.md) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                AppIcons.Sort,
                null,
                Modifier.height(14.dp).width(14.dp),
                tint = Ink.TextTertiary,
            )
            Spacer(Modifier.width(Space.sm))
            FieldLabel(if (eras > 1) "Series" else "Sets")
        }
        Spacer(Modifier.height(Space.sm))
        if (eras > 1) {
            SegmentedControl(
                options = CatalogOrder.entries,
                selected = browser.seriesOrder,
                onSelect = { browser.seriesOrder = it },
                label = { it.label },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            FieldLabel("Sets within a series")
            Spacer(Modifier.height(Space.sm))
        }
        SegmentedControl(
            options = CatalogOrder.entries,
            selected = browser.setOrder,
            onSelect = { browser.setOrder = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
