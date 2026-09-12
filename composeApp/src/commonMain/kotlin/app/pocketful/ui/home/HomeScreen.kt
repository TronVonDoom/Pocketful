package app.pocketful.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketful.domain.Currency
import app.pocketful.domain.Binder
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Container
import app.pocketful.domain.CopyRow
import app.pocketful.domain.Money
import app.pocketful.domain.WantRow
import app.pocketful.domain.copyRows
import app.pocketful.domain.tradeRows
import app.pocketful.domain.wantRows
import app.pocketful.state.display
import app.pocketful.state.displayOrDash
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.components.tappable
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/**
 * Where the app opens, and the whole portfolio in one scroll.
 *
 * Home and Stats used to be two tabs. They opened on the same figure, ranked the same
 * cards, and linked to the same places -- the only real difference was that one went two
 * sections further -- so the navigation bar was asking people to choose between a summary
 * and a longer summary, which is not a choice worth a tab. They are one screen now, read
 * top to bottom: what it is worth, where that value sits, what is still missing, what is
 * on the table, and what the best cards are.
 *
 * Storage appears once, as the value ranking. An earlier draft kept Home's grid of
 * shortcut tiles *and* the ranking, which listed the same four binders twice on one
 * screen; the bars won because they are also shortcuts -- tapping one opens it -- and
 * they answer a question the tiles could not, which is how lopsided the collection is.
 * The tile grid still lives on Collections, the screen that actually owns storage.
 */
@Composable
fun HomeScreen(
    snapshot: CollectionSnapshot,
    onOpenBinder: (Binder) -> Unit,
    onOpenContainer: (Container) -> Unit,
    /**
     * Into Collections, which is where storage is made and managed.
     *
     * Home used to carry its own "New binder" and "New container" tiles. Two screens
     * offering the same creation is two places to keep in step, and the one that owns the
     * thing should be the one that makes it -- so Home links there instead.
     */
    onOpenCollections: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenTrade: () -> Unit,
    onOpenCopy: (CopyRow) -> Unit,
    onOpenWant: (WantRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = remember(snapshot) { snapshot.summarizeAll() }
    val unfiled = remember(snapshot) { snapshot.unfiledCopies().size }
    val copies = remember(snapshot) { snapshot.copyRows() }
    // Carried with their positions rather than looked up later. Ranking by index-of
    // would compare rows by value, and two identical spares of the same card would both
    // be numbered whichever of them came first.
    val topCards = remember(copies) {
        copies.sortedByDescending { it.value.cents }.take(6).mapIndexed { index, row -> index + 1 to row }
    }
    // Two apiece, not four. These are shortcuts into screens that hold the whole list,
    // and at art-tile size four of them is two full rows -- which stops being a glance at
    // what is outstanding and becomes a second copy of the want list.
    // A shelf rather than two tiles, so it holds a real stack. Capped all the same: this
    // is a glance at what is on the table with a way through to the screen that owns it,
    // and a scroller with ninety cards in it is the trade screen wearing a disguise.
    val trades = remember(snapshot) { snapshot.tradeRows().take(TRADE_SHELF_MAX) }
    val tradeTotal = remember(snapshot) { snapshot.tradeRows().size }
    val wants = remember(snapshot) {
        snapshot.wantRows().sortedByDescending { it.targetPrice.cents }.take(2)
    }

    // Binders and containers ranked together: "where the value sits" is a question about
    // the whole collection, and answering it with binders alone would hide a box of slabs
    // worth more than any page in the app.
    val places = remember(snapshot) {
        val fromBinders = snapshot.binders.map { binder ->
            val summary = snapshot.summarize(binder)
            StoragePlace(
                name = binder.name,
                tint = Color(binder.spineColor),
                value = summary.marketValue,
                currency = summary.currency,
                countLabel = "${binder.layout.fullLabel} · ${summary.ownedCount}/${binder.capacity}",
                countShort = "${summary.ownedCount}/${binder.capacity}",
                fillFraction = if (binder.capacity == 0) null
                else summary.ownedCount.toFloat() / binder.capacity,
                wantedCount = summary.wantedCount,
                onOpen = { onOpenBinder(binder) },
            )
        }
        val fromContainers = snapshot.containers.map { container ->
            val summary = snapshot.summarize(container)
            StoragePlace(
                name = container.name,
                tint = Color(container.color),
                value = summary.marketValue,
                currency = summary.currency,
                countLabel = "${container.kind.label.lowercase()} · ${summary.ownedCount} cards",
                countShort = "${summary.ownedCount} ${if (summary.ownedCount == 1) "card" else "cards"}",
                fillFraction = null,
                wantedCount = 0,
                onOpen = { onOpenContainer(container) },
            )
        }
        (fromBinders + fromContainers).sortedByDescending { it.value.cents }
    }

    val bySet = remember(copies) {
        copies.groupBy { it.brief.setName }
            .map { (setName, rows) ->
                SetTally(
                    setName = setName,
                    count = rows.size,
                    value = rows.fold(Money.ZERO) { acc, row -> acc + row.value },
                )
            }
            .sortedByDescending { it.value.cents }
    }
    val averageValue = remember(copies, total) {
        total.averageValue
    }
    val livePrices = remember(snapshot) { snapshot.prices.values.count { it.source == "tcgplayer" } }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Background)) {
        ScreenBackdrop(Ink.Accent, height = 300.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = islandBottomInset()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Portfolio",
                    centered = true,
                    headline = total.marketValue.displayOrDash(total.currency),
                    headlineCaption = when {
                        copies.isEmpty() -> "Nothing recorded yet"
                        // Says out loud what the headline cannot include. A collection
                        // holding both markets has two totals and no single number that
                        // is the sum of them, so the second one is printed rather than
                        // folded in or quietly dropped.
                        total.alsoLabel != null ->
                            "across ${copies.size} cards · also ${total.alsoLabel}"
                        else -> "across ${copies.size} ${if (copies.size == 1) "card" else "cards"}"
                    },
                    summary = total,
                    stats = buildList {
                        add(Stat("${total.ownedCount}", "cards"))
                        add(Stat("${snapshot.binders.size + snapshot.containers.size}", "places"))
                        averageValue?.let { add(Stat(it.display(total.currency), "average")) }
                        if (total.wantedCount > 0) {
                            add(Stat("${total.wantedCount}", "wanted", Ink.Wanted))
                            total.costToComplete.displayOrNull(total.currency)?.let {
                                add(Stat(it, "to finish", Ink.Gold))
                            }
                        }
                        if (unfiled > 0) add(Stat("$unfiled", "unfiled", Ink.Gold))
                    },
                    actions = {
                        // Icon-only, because these two are the app's own furniture --
                        // a labelled row of them read as the point of the screen.
                        HeaderAction(
                            icon = AppIcons.Cards,
                            contentDescription = "Every card you own",
                            onClick = onOpenCards,
                        )
                        HeaderAction(
                            icon = AppIcons.Trade,
                            contentDescription = "Cards up for trade",
                            onClick = onOpenTrade,
                        )
                    },
                )
            }

            if (places.isEmpty()) {
                item {
                    EmptyState(
                        icon = AppIcons.Collections,
                        title = "Nothing stored yet",
                        message = "Start with a binder for the set you are working on, " +
                            "or a box for everything else.",
                        // Sends you to the screen that makes them rather than making one
                        // here. An empty app still needs a way forward, and that way is
                        // the same one it will be tomorrow when Home is full.
                        action = {
                            AppButton("Open collections", onOpenCollections, icon = AppIcons.Collections)
                        },
                    )
                }
            } else {
                item {
                    SectionHeader("Where the value sits", Modifier.padding(top = 2.dp, bottom = 2.dp)) {
                        LinkText("All", onOpenCollections)
                    }
                }
                item {
                    // Three across, and scrolling once there are more than three. Stacked
                    // full-width rows gave every binder the same visual weight as the
                    // portfolio above it and pushed the rest of the screen below the fold
                    // at four binders; a shelf holds any number in one band of height.
                    BoxWithConstraints {
                        val gap = 9.dp
                        val tileWidth = (maxWidth - gap * 2) / 3
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            items(places, key = { it.name + it.countLabel }) { place ->
                                StorageStatTile(place, Modifier.width(tileWidth))
                            }
                        }
                    }
                }
            }

            if (wants.isNotEmpty()) {
                item {
                    SectionHeader("Still hunting", Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                        LinkText("All", onOpenCards)
                    }
                }
                tileRows(
                    items = wants,
                    keyPrefix = "want",
                    key = { "${it.binderId.value}-${it.ordinal}" },
                ) { want ->
                    CardTile(
                        brief = want.brief,
                        caption = "${want.binderName} · Pocket ${want.ordinal + 1}",
                        value = want.targetPrice.displayOrNull(),
                        valueColor = Ink.Wanted,
                        badge = "WANT",
                        badgeColor = Ink.Wanted,
                        ghosted = true,
                        onClick = { onOpenWant(want) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (trades.isNotEmpty()) {
                item {
                    SectionHeader("Up for trade", Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                        LinkText(if (tradeTotal > trades.size) "View all · $tradeTotal" else "View all", onOpenTrade)
                    }
                }
                item {
                    // Four across, which is what fits a phone at a size where the art is
                    // still the card and not a swatch. More than four scrolls, and the
                    // partial fifth at the edge is what says so.
                    BoxWithConstraints {
                        val gap = 8.dp
                        val tileWidth = (maxWidth - gap * 3) / 4
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            items(trades, key = { it.copy.id.value }) { row ->
                                CardTile(
                                    brief = row.brief,
                                    caption = row.locationLabel,
                                    value = row.value.displayOrNull(row.brief.currency),
                                    valueColor = Ink.Gain,
                                    badge = "TRADE",
                                    badgeColor = Ink.Gain,
                                    onClick = { onOpenCopy(row) },
                                    modifier = Modifier.width(tileWidth),
                                )
                            }
                        }
                    }
                }
            }

            if (topCards.isNotEmpty()) {
                item {
                    SectionHeader("Most valuable", Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                        LinkText("All", onOpenCards)
                    }
                }
                // Tiles, with the position printed on each. A ranking laid out as a
                // grid loses its order unless the order is stated, and once it is stated
                // there is no reason to keep spending a full row per card to imply the
                // thing the number now says outright -- least of all on the section whose
                // whole subject is which card is worth looking at.
                tileRows(items = topCards, keyPrefix = "top", key = { it.second.copy.id.value }) { entry ->
                    val (position, row) = entry
                    CardTile(
                        brief = row.brief,
                        caption = row.locationLabel,
                        value = row.value.display(),
                        valueColor = Ink.Gold,
                        rank = position,
                        badge = row.copy.grade?.label,
                        badgeColor = Ink.Gold,
                        onClick = { onOpenCopy(row) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (bySet.isNotEmpty()) {
                item { SectionHeader("By set", Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                item {
                    Panel {
                        bySet.forEachIndexed { index, tally ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = tally.setName,
                                        color = Ink.TextPrimary,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (tally.count == 1) "1 card" else "${tally.count} cards",
                                        color = Ink.TextTertiary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(
                                    text = tally.value.display(),
                                    color = Ink.TextSecondary,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }
            }

            if (copies.isNotEmpty()) {
                item {
                    Text(
                        // Says which it is rather than always claiming the pessimistic
                        // case: once a catalog update has run, most of these figures are
                        // real quotes, and hedging on all of them would be the app
                        // underselling the one number it works hardest to get right.
                        text = if (livePrices > 0) {
                            "Prices are the last TCGplayer market quote the catalog returned. " +
                                "Cards it could not match keep whatever value they had."
                        } else {
                            "No market prices yet. Run a catalog update in Settings to pull " +
                                "quotes for the cards you have recorded."
                        },
                        color = Ink.TextDisabled,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}

/** The "see the rest of these" affordance on a section header. */
@Composable
private fun LinkText(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Ink.Accent,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(AppShape.Chip)
            .tappable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * How many trades the home shelf carries before it stops being a glance.
 *
 * Twelve: enough that the shelf scrolls for anyone who actually trades, few enough that
 * the screen does not spend a second building tiles nobody will swipe to.
 */
private const val TRADE_SHELF_MAX = 12

private data class SetTally(val setName: String, val count: Int, val value: Money)

/**
 * One row of the value ranking, flattened from whichever kind of storage it came from.
 *
 * The way to open it is carried as a lambda rather than the binder or container itself:
 * this row does not care which of the two it came from, and holding one of each as
 * nullable fields is how a list like this grows a `when` at every call site.
 */
private data class StoragePlace(
    val name: String,
    val tint: Color,
    val value: Money,
    val currency: Currency,
    val countLabel: String,
    /** "12/40" for a binder, "12 cards" for a box -- the stat the tile leads with. */
    val countShort: String,
    /** How full, for the bar under a binder. Null for storage with no capacity. */
    val fillFraction: Float?,
    val wantedCount: Int,
    val onOpen: () -> Unit,
)

/**
 * One place you keep cards, at a third of the screen's width.
 *
 * Everything here had to survive being 100dp wide, which is what decided the content: the
 * name, the one count that matters, and what it is worth. The era label and the layout
 * name went -- "9-pocket · 3x3" is a fact about a binder you already recognise by name,
 * and it was the first thing to wrap to two lines and shove the value off the tile.
 *
 * The fill bar is the binder's stat rather than a decoration. A set binder is a progress
 * bar by nature, and reading 12/40 as a bar is faster than reading it as a fraction.
 */
@Composable
private fun StorageStatTile(place: StoragePlace, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(AppShape.Medium)
            .background(Ink.Surface)
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium)
            .tappable(pressScale = 0.97f, onClick = place.onOpen)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(12.dp).clip(AppShape.Pill).background(place.tint))
            Spacer(Modifier.width(6.dp))
            Text(
                text = place.name,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = place.value.displayOrDash(place.currency),
            color = Ink.Gold,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = place.countShort,
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )

        place.fillFraction?.let { fraction ->
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(AppShape.Pill)
                    .background(Ink.SurfaceHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(AppShape.Pill)
                        .background(place.tint),
                )
            }
        }
    }
}
