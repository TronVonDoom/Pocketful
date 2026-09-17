package app.pocketful.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketful.data.CatalogIds
import app.pocketful.data.PriceHistory
import app.pocketful.data.PriceHistoryDoc
import app.pocketful.data.portfolioSeries
import app.pocketful.domain.Binder
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Container
import app.pocketful.domain.CopyRow
import app.pocketful.domain.Currency
import app.pocketful.domain.Money
import app.pocketful.domain.VariantId
import app.pocketful.domain.WantRow
import app.pocketful.domain.brief
import app.pocketful.domain.copyRows
import app.pocketful.domain.tradeRows
import app.pocketful.domain.wantRows
import app.pocketful.state.display
import app.pocketful.state.displayOrDash
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.ChangeLabel
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.GainChip
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.Headline
import app.pocketful.ui.components.LinkAction
import app.pocketful.ui.components.MetricCell
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.PriceHistoryPanel
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.ShelfTile
import app.pocketful.ui.components.TopBar
import app.pocketful.ui.components.percentText
import app.pocketful.ui.components.tappable
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space
import kotlin.math.abs

/**
 * Where the app opens.
 *
 * Home answers one question -- "what is my collection doing?" -- and everything on it earns
 * its place by answering some part of that. It is read top to bottom as a narrowing: what
 * the whole thing is worth, what moved today, where the value sits, what is outstanding,
 * and finally the long tail of history.
 *
 * What changed in the rebuild is mostly what is *not* here. Home used to carry the app's
 * only route to the flat card list and the trade table, as two unlabelled circles in the
 * corner of a header; both are destinations in the dock now, so the corner is free for the
 * two things that genuinely belong to the whole app rather than to any one screen -- search
 * and settings.
 *
 * And one section is new. "Moving today" is the thing a portfolio screen is *for* and the
 * old Home could not tell you: it printed a day's change on the total and then never said
 * which cards caused it. The figure is the headline; this is the explanation.
 */
@Composable
fun HomeScreen(
    snapshot: CollectionSnapshot,
    onOpenBinder: (Binder) -> Unit,
    onOpenContainer: (Container) -> Unit,
    onOpenCollection: () -> Unit,
    onOpenTrade: () -> Unit,
    onOpenWants: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCopy: (CopyRow) -> Unit,
    onOpenWant: (WantRow) -> Unit,
    history: PriceHistory,
    onDeleteSale: (String) -> Unit,
    /** Whether Settings has something waiting -- an update, most often. */
    settingsBadge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val total = remember(snapshot) { snapshot.summarizeAll() }

    // The portfolio chart: the history of every set the collection spans, fetched once and
    // kept, then re-summed whenever the collection changes. Fetching is keyed on the sets
    // rather than the snapshot, so adding a card from a set already loaded costs no request.
    val setIds = remember(snapshot.copies, snapshot.variants) {
        snapshot.copies.values.map { copy -> CatalogIds.setOf(copy.variantId.value) }
            .filter { it.isNotEmpty() }
            .toSortedSet()
    }
    var historyDocs by remember { mutableStateOf<Map<String, PriceHistoryDoc>?>(null) }
    LaunchedEffect(setIds) {
        historyDocs = if (setIds.isEmpty()) emptyMap() else history.forSets(setIds)
    }
    val portfolioPoints = remember(historyDocs, snapshot) {
        historyDocs?.let { portfolioSeries(snapshot, it) }
    }

    val realized = remember(snapshot.sales) { snapshot.realizedGains() }
    val graded = remember(snapshot.copies) { snapshot.copies.values.count { it.isGraded } }
    val unfiled = remember(snapshot) { snapshot.unfiledCopies().size }
    val copies = remember(snapshot) { snapshot.copyRows() }

    // Grouped by printing before they are ranked, not after. Four identical Pikachus used to
    // fill four of the six slots here, each wearing its own rank number -- which at a glance
    // read as a quantity ("2, 3, 4, 5") rather than the position it actually was, and crowded
    // out cards that were genuinely different. One entry per card you own, worth what all of
    // your copies of it add up to, with its own count when there is more than one.
    val topCards = remember(copies) {
        copies.groupBy { it.brief.variantId }
            .map { (_, rows) -> ValueEntry(rows) }
            .sortedByDescending { it.totalValue.cents }
            .take(6)
            .mapIndexed { index, entry -> index + 1 to entry }
    }
    val movers = remember(snapshot, copies) { moversOf(snapshot, copies) }
    val trades = remember(snapshot) { snapshot.tradeRows() }
    val wants = remember(snapshot) {
        snapshot.wantRows().sortedByDescending { it.targetPrice.cents }
    }

    // Binders and containers ranked together: "where the value sits" is a question about the
    // whole collection, and answering it with binders alone would hide a box of slabs worth
    // more than any page in the app.
    val places = remember(snapshot) {
        val fromBinders = snapshot.binders.map { binder ->
            val summary = snapshot.summarize(binder)
            StoragePlace(
                name = binder.name,
                tint = Color(binder.spineColor),
                value = summary.marketValue,
                currency = summary.currency,
                countShort = "${summary.ownedCount}/${binder.capacity}",
                fillFraction = if (binder.capacity == 0) null
                else summary.ownedCount.toFloat() / binder.capacity,
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
                countShort = "${summary.ownedCount} ${if (summary.ownedCount == 1) "card" else "cards"}",
                fillFraction = null,
                onOpen = { onOpenContainer(container) },
            )
        }
        (fromBinders + fromContainers).sortedByDescending { it.value.cents }
    }

    val bySet = remember(copies) {
        copies.groupBy { it.brief.setName }
            .map { (setName, rows) ->
                SetTally(setName, rows.size, rows.fold(Money.ZERO) { acc, row -> acc + row.value })
            }
            .sortedByDescending { it.value.cents }
    }
    val livePrices = remember(snapshot) { snapshot.prices.values.count { it.source == "tcgplayer" } }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Canvas)) {
        ScreenBackdrop(Ink.Accent, height = 340.dp)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // The two things that belong to the app rather than to a screen. Search is the
            // way into everything the app knows about; the gear is the way into how it
            // behaves. Neither is a destination, which is exactly why neither is a tab.
            TopBar(
                title = "Pocketful",
                subtitle = "Every card, in its pocket",
                actions = {
                    HeaderAction(AppIcons.Search, "Search everything", onOpenSearch)
                    HeaderAction(AppIcons.Gear, "Settings", onOpenSettings, badge = settingsBadge)
                },
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
                    Panel(padding = Space.lg) {
                        Headline(
                            value = total.marketValue.displayOrDash(total.currency),
                            label = "Portfolio",
                            caption = when {
                                copies.isEmpty() -> "Nothing recorded yet"
                                // Says out loud what the headline cannot include. A
                                // collection holding both markets has two totals and no
                                // single number that is the sum of them, so the second one
                                // is printed rather than folded in or quietly dropped.
                                total.alsoLabel != null ->
                                    "across ${copies.size} cards · also ${total.alsoLabel}"
                                else -> "across ${copies.size} ${if (copies.size == 1) "card" else "cards"}"
                            },
                            trailing = { GainChip(total) },
                        )

                        if (copies.isNotEmpty()) {
                            Spacer(Modifier.height(Space.lg))
                            if (total.dayChangeBase.cents > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Today",
                                        color = Ink.TextTertiary,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ChangeLabel(
                                        change = total.dayChange,
                                        base = total.dayChangeBase,
                                        currency = total.currency,
                                    )
                                }
                                Spacer(Modifier.height(Space.md))
                            }
                            PriceHistoryPanel(
                                points = portfolioPoints,
                                title = "Portfolio value",
                                currency = total.currency,
                            )
                        }
                    }
                }

                // The figures that qualify the headline, as four cells you can act on.
                // Every one of these used to be a read-only number in a stat rail, which
                // meant "12 unfiled" was a fact the app stated and then gave you no way to
                // do anything about. A count of something outstanding should be a door.
                if (copies.isNotEmpty() || places.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                            MetricCell(
                                value = "${total.ownedCount}",
                                label = "cards",
                                icon = AppIcons.Cards,
                                onClick = onOpenCards,
                                modifier = Modifier.weight(1f),
                            )
                            MetricCell(
                                value = "${snapshot.binders.size + snapshot.containers.size}",
                                label = "places",
                                icon = AppIcons.Collections,
                                onClick = onOpenCollection,
                                modifier = Modifier.weight(1f),
                            )
                            if (total.wantedCount > 0) {
                                MetricCell(
                                    value = "${total.wantedCount}",
                                    label = "wanted",
                                    accent = Ink.Wanted,
                                    icon = AppIcons.Target,
                                    onClick = onOpenWants,
                                    modifier = Modifier.weight(1f),
                                )
                            } else if (trades.isNotEmpty()) {
                                MetricCell(
                                    value = "${trades.size}",
                                    label = "on offer",
                                    accent = Ink.Gain,
                                    icon = AppIcons.Trade,
                                    onClick = onOpenTrade,
                                    modifier = Modifier.weight(1f),
                                )
                            } else if (graded > 0) {
                                MetricCell(
                                    value = "$graded",
                                    label = "graded",
                                    accent = Ink.Gold,
                                    icon = AppIcons.Slab,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                // Unfiled cards are the app's one genuine loose end, so they get a line of
                // their own rather than a number in a rail. It is a job, and a job on a
                // dashboard should be a button.
                if (unfiled > 0) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(AppShape.Medium)
                                .background(Ink.Gold.copy(alpha = 0.08f))
                                .border(1.dp, Ink.Gold.copy(alpha = 0.25f), AppShape.Medium)
                                .tappable(pressScale = 0.985f, onClick = onOpenCards)
                                .padding(Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AppIcons.Box, null, Modifier.size(Size.iconSm), tint = Ink.Gold)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (unfiled == 1) "1 card is unfiled" else "$unfiled cards are unfiled",
                                    color = Ink.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = "They are counted and priced, but not in a binder or box yet.",
                                    color = Ink.TextTertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(Space.sm))
                            Icon(AppIcons.ChevronRight, null, Modifier.size(Size.iconSm), tint = Ink.Gold)
                        }
                    }
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
                                AppButton("Open collection", onOpenCollection, icon = AppIcons.Collections)
                            },
                        )
                    }
                } else {
                    item {
                        SectionHeader("Where the value sits", icon = AppIcons.Wallet) {
                            LinkAction("All", onOpenCollection)
                        }
                    }
                    item {
                        // Three across, and scrolling once there are more than three. Stacked
                        // full-width rows gave every binder the same visual weight as the
                        // portfolio above it and pushed the rest of the screen below the fold
                        // at four binders; a shelf holds any number in one band of height.
                        BoxWithConstraints {
                            val tileWidth = (maxWidth - Space.md * 2) / 3
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                items(places, key = { it.name + it.countShort }) { place ->
                                    ShelfTile(
                                        name = place.name,
                                        value = place.value.displayOrDash(place.currency),
                                        count = place.countShort,
                                        accent = place.tint,
                                        fillFraction = place.fillFraction,
                                        onClick = place.onOpen,
                                        modifier = Modifier.width(tileWidth),
                                    )
                                }
                            }
                        }
                    }
                }

                // The section the old Home was missing. A day's change on the total is a
                // number with no story; this is the story.
                if (movers.isNotEmpty()) {
                    item {
                        SectionHeader("Moving today", Modifier.padding(top = Space.sm), icon = AppIcons.Bolt)
                    }
                    item {
                        Panel(padding = Space.md) {
                            movers.forEachIndexed { index, mover ->
                                if (index > 0) Spacer(Modifier.height(Space.md))
                                MoverRow(mover, onClick = { mover.row?.let(onOpenCopy) })
                            }
                        }
                    }
                }

                if (wants.isNotEmpty()) {
                    item {
                        SectionHeader("Still hunting", Modifier.padding(top = Space.sm), icon = AppIcons.Target) {
                            LinkAction(if (wants.size > 2) "All · ${wants.size}" else "All", onOpenWants)
                        }
                    }
                    // Two, not four. This is a glance at what is outstanding with a way
                    // through to the screen that owns it; four is a second want list.
                    tileRows(
                        items = wants.take(2),
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
                        SectionHeader("Up for trade", Modifier.padding(top = Space.sm), icon = AppIcons.Trade) {
                            LinkAction(
                                if (trades.size > TRADE_SHELF_MAX) "All · ${trades.size}" else "All",
                                onOpenTrade,
                            )
                        }
                    }
                    item {
                        // Four across, which is what fits a phone at a size where the art is
                        // still the card and not a swatch. More than four scrolls, and the
                        // partial fifth at the edge is what says so.
                        BoxWithConstraints {
                            val tileWidth = (maxWidth - Space.md * 3) / 4
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                items(trades.take(TRADE_SHELF_MAX), key = { it.copy.id.value }) { row ->
                                    CardTile(
                                        brief = row.brief,
                                        caption = row.locationLabel,
                                        // Gold, not green -- the same rule a binder pocket
                                        // follows. A price is a price regardless of status;
                                        // it is the icon in the pill beside it that says
                                        // this one is on the table.
                                        value = row.value.displayOrNull(row.brief.currency),
                                        valueColor = Ink.Gold,
                                        forTrade = true,
                                        badge = row.copy.grade?.label
                                            ?: row.copy.condition.short.takeIf { it != "NM" },
                                        badgeColor = if (row.copy.grade != null) Ink.Gold else Ink.TextTertiary,
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
                        SectionHeader("Most valuable", Modifier.padding(top = Space.sm), icon = AppIcons.Tag) {
                            LinkAction("All", onOpenCards)
                        }
                    }
                    // Tiles, with the position printed on each. A ranking laid out as a grid
                    // loses its order unless the order is stated, and once it is stated there
                    // is no reason to spend a full row per card to imply what the number now
                    // says outright.
                    tileRows(items = topCards, keyPrefix = "top", key = { it.second.variantId.value }) { entry ->
                        val (position, group) = entry
                        val row = group.representative
                        CardTile(
                            brief = row.brief,
                            caption = group.caption,
                            value = group.totalValue.display(),
                            valueColor = Ink.Gold,
                            rank = position,
                            // A single physical copy's own count as a disc; a group's own
                            // grade badge only when every copy in it actually carries the
                            // same grade -- a "PSA 9" plate on a tile standing for four
                            // mixed-condition spares would be a badge that is only true of
                            // one of them.
                            count = group.count.takeIf { it > 1 },
                            badge = if (group.count == 1) row.copy.grade?.label else null,
                            badgeColor = Ink.Gold,
                            forTrade = group.allForTrade,
                            onClick = { onOpenCopy(row) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (snapshot.sales.isNotEmpty()) {
                    item {
                        SectionHeader("Sold", Modifier.padding(top = Space.sm), icon = AppIcons.Clock) {
                            Text(
                                text = (if (realized.cents >= 0) "+" else "") + realized.display() + " realized",
                                color = if (realized.cents >= 0) Ink.Gain else Ink.Loss,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    item {
                        Panel(padding = Space.md) {
                            snapshot.sales.take(SOLD_SHOWN).forEachIndexed { index, sale ->
                                if (index > 0) Spacer(Modifier.height(Space.md))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = sale.name + (sale.badge?.let { " · $it" } ?: ""),
                                            color = Ink.TextPrimary,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(Space.xxs))
                                        Text(
                                            text = listOfNotNull(
                                                sale.setName.takeIf { it.isNotBlank() },
                                                sale.soldDate,
                                            ).joinToString(" · "),
                                            color = Ink.TextTertiary,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = sale.soldPrice.display(),
                                            color = Ink.TextSecondary,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        sale.realizedGain?.let { gain ->
                                            Text(
                                                text = (if (gain.cents >= 0) "+" else "") + gain.display(),
                                                color = if (gain.cents >= 0) Ink.Gain else Ink.Loss,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(Space.sm))
                                    CircleIconButton(
                                        icon = AppIcons.Close,
                                        contentDescription = "Forget this sale",
                                        onClick = { onDeleteSale(sale.id) },
                                        size = Size.controlSm,
                                    )
                                }
                            }
                        }
                    }
                }

                if (bySet.isNotEmpty()) {
                    item {
                        SectionHeader("By set", Modifier.padding(top = Space.sm), icon = AppIcons.Bookmark)
                    }
                    item {
                        Panel(padding = Space.md) {
                            bySet.forEachIndexed { index, tally ->
                                if (index > 0) Spacer(Modifier.height(Space.md))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = tally.setName,
                                            color = Ink.TextPrimary,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(Space.xxs))
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
                            modifier = Modifier.padding(top = Space.sm, bottom = Space.sm),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One card whose price moved, and by how much.
 *
 * The bar is the point of the row. Five percentages in a column are five numbers to compare
 * by reading; five bars are a shape, and the largest mover is the one you see rather than
 * the one you work out. Scaled against the biggest move on screen rather than against a
 * fixed range, because what matters here is which of *these* moved most.
 */
@Composable
private fun MoverRow(mover: Mover, onClick: () -> Unit) {
    val up = mover.change.cents >= 0
    val tint = if (up) Ink.Gain else Ink.Loss

    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppShape.Small)
            .tappable(pressScale = 0.985f, onClick = onClick)
            .padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = mover.name,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.xs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(AppShape.Pill)
                    .background(Ink.Well),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(mover.share.coerceIn(0.04f, 1f))
                        .height(4.dp)
                        .clip(AppShape.Pill)
                        .background(tint),
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (up) "▲" else "▼") + percentText(mover.percent),
                color = tint,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Spacer(Modifier.height(Space.xxs))
            Text(
                text = (if (up) "+" else "") + mover.change.display(),
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/**
 * The cards that moved most today, by percentage.
 *
 * Restricted to cards worth a dollar or more, where a percentage means something: a bulk
 * common going from four cents to five is a genuine +25% and a completely useless headline.
 * One entry per printing, because five copies of the same card all moving the same way is
 * one piece of news, not five.
 */
private fun moversOf(snapshot: CollectionSnapshot, copies: List<CopyRow>): List<Mover> {
    if (copies.isEmpty()) return emptyList()

    val byVariant = copies.associateBy { it.brief.variantId }
    val candidates = copies
        .asSequence()
        .map { it.brief.variantId }
        .distinct()
        .mapNotNull { id -> moverOf(snapshot, id, byVariant[id]) }
        .sortedByDescending { abs(it.percent) }
        .take(MOVERS_SHOWN)
        .toList()

    if (candidates.isEmpty()) return emptyList()
    val widest = candidates.maxOf { abs(it.percent) }.takeIf { it > 0.0 } ?: return candidates
    return candidates.map { it.copy(share = (abs(it.percent) / widest).toFloat()) }
}

private fun moverOf(snapshot: CollectionSnapshot, id: VariantId, row: CopyRow?): Mover? {
    val change = snapshot.changeOf(id) ?: return null
    if (change.isZero) return null
    val market = snapshot.marketValue(id)
    val before = market - change
    if (market.cents < MOVER_FLOOR_CENTS || before.isZero) return null
    val percent = change.cents.toDouble() / before.cents * 100
    if (abs(percent) < MOVER_MIN_PERCENT) return null
    val name = snapshot.brief(id)?.name ?: return null
    return Mover(name = name, change = change, percent = percent, share = 1f, row = row)
}

private data class Mover(
    val name: String,
    val change: Money,
    val percent: Double,
    /** How long this row's bar is, against the biggest move on screen. */
    val share: Float,
    val row: CopyRow?,
)

/** How many movers the section lists. Five is a glance; ten is a table. */
private const val MOVERS_SHOWN = 5

/** Below this, a percentage move is arithmetic rather than news. */
private const val MOVER_FLOOR_CENTS = 100L

/** And below this much movement, it is noise in the quote rather than a move. */
private const val MOVER_MIN_PERCENT = 1.0

/**
 * How many trades the home shelf carries before it stops being a glance.
 *
 * Twelve: enough that the shelf scrolls for anyone who actually trades, few enough that the
 * screen does not spend a second building tiles nobody will swipe to.
 */
private const val TRADE_SHELF_MAX = 12

/** How many sales Home lists. The rest are still counted in the realized total. */
private const val SOLD_SHOWN = 5

private data class SetTally(val setName: String, val count: Int, val value: Money)

/**
 * Every copy you own of one printing, folded into a single entry for the "Most valuable"
 * ranking.
 *
 * A ranking is a list of *cards*, not a list of *copies* -- "your second most valuable card"
 * has one answer even when you own four of it. Grouping here rather than after the fact is
 * what keeps a shelf of identical spares from crowding six genuinely different cards down to
 * two: the group counts as one entry worth what all of its copies add up to, with its own
 * quantity mark, rather than one entry per copy each restating the same name.
 */
private data class ValueEntry(val rows: List<CopyRow>) {
    val variantId: VariantId get() = representative.brief.variantId
    val representative: CopyRow = rows.maxByOrNull { it.value.cents } ?: rows.first()
    val totalValue: Money = rows.fold(Money.ZERO) { acc, row -> acc + row.value }
    val count: Int get() = rows.size
    val allForTrade: Boolean = rows.all { it.copy.forTrade }

    /** Where it is, unless "it" is actually several places -- then how many, and of what. */
    val caption: String = when {
        rows.size == 1 -> representative.locationLabel
        else -> {
            val places = rows.map { it.locationLabel }.distinct()
            val countLabel = "${rows.size} copies"
            if (places.size == 1) "$countLabel · ${places.first()}" else countLabel
        }
    }
}

/**
 * One row of the value ranking, flattened from whichever kind of storage it came from.
 *
 * The way to open it is carried as a lambda rather than the binder or container itself: this
 * row does not care which of the two it came from, and holding one of each as nullable
 * fields is how a list like this grows a `when` at every call site.
 */
private data class StoragePlace(
    val name: String,
    val tint: Color,
    val value: Money,
    val currency: Currency,
    /** "12/40" for a binder, "12 cards" for a box -- the stat the tile leads with. */
    val countShort: String,
    /** How full, for the bar under a binder. Null for storage with no capacity. */
    val fillFraction: Float?,
    val onOpen: () -> Unit,
)
