package app.pocketful.ui.home

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
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AddTile
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.ProgressTrack
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.TilePair
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
    onCreateBinder: () -> Unit,
    onCreateContainer: () -> Unit,
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
    val trades = remember(snapshot) { snapshot.tradeRows().take(2) }
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
                countLabel = "${binder.layout.fullLabel} · ${summary.ownedCount}/${binder.capacity}",
                onOpen = { onOpenBinder(binder) },
            )
        }
        val fromContainers = snapshot.containers.map { container ->
            val summary = snapshot.summarize(container)
            StoragePlace(
                name = container.name,
                tint = Color(container.color),
                value = summary.marketValue,
                countLabel = "${container.kind.label.lowercase()} · ${summary.ownedCount} cards",
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
        if (copies.isEmpty()) Money.ZERO else Money(total.marketValue.cents / copies.size)
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
                    headline = total.marketValue.format(),
                    headlineCaption = if (copies.isEmpty()) {
                        "Nothing recorded yet"
                    } else {
                        "across ${copies.size} ${if (copies.size == 1) "card" else "cards"}"
                    },
                    summary = total,
                    stats = buildList {
                        add(Stat("${total.ownedCount}", "cards"))
                        add(Stat("${snapshot.binders.size + snapshot.containers.size}", "places"))
                        add(Stat(averageValue.display(), "average"))
                        if (total.wantedCount > 0) {
                            add(Stat("${total.wantedCount}", "wanted", Ink.Wanted))
                            total.costToComplete.displayOrNull()?.let {
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
                        action = { AppButton("New binder", onCreateBinder, icon = AppIcons.Plus) },
                    )
                }
            } else {
                item { SectionHeader("Where the value sits", Modifier.padding(top = 2.dp, bottom = 2.dp)) }
                item {
                    val peak = places.firstOrNull()?.value?.cents ?: 0L
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        places.forEach { place ->
                            StorageValueBar(
                                place = place,
                                fraction = if (peak <= 0L) 0f else place.value.cents.toFloat() / peak,
                            )
                        }
                    }
                }
            }

            item {
                TilePair(Modifier.padding(top = 4.dp)) {
                    AddTile(
                        icon = AppIcons.Plus,
                        label = "New binder",
                        onClick = onCreateBinder,
                        modifier = Modifier.weight(1f),
                    )
                    AddTile(
                        icon = AppIcons.Plus,
                        label = "New container",
                        onClick = onCreateContainer,
                        modifier = Modifier.weight(1f),
                    )
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
                        LinkText("All", onOpenTrade)
                    }
                }
                tileRows(items = trades, keyPrefix = "trade", key = { it.copy.id.value }) { row ->
                    CardTile(
                        brief = row.brief,
                        caption = row.locationLabel,
                        value = row.value.displayOrNull(),
                        valueColor = Ink.Gain,
                        badge = "TRADE",
                        badgeColor = Ink.Gain,
                        onClick = { onOpenCopy(row) },
                        modifier = Modifier.weight(1f),
                    )
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
    val countLabel: String,
    val onOpen: () -> Unit,
)

@Composable
private fun StorageValueBar(place: StoragePlace, fraction: Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(Ink.Surface)
            .tappable(pressScale = 0.985f, onClick = place.onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(14.dp).clip(AppShape.Pill).background(place.tint))
            Spacer(Modifier.width(9.dp))
            Text(
                text = place.name,
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(place.value.display(), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(9.dp))
        ProgressTrack(fraction = fraction, color = place.tint, height = 5.dp)
        Spacer(Modifier.height(6.dp))
        Text(place.countLabel, color = Ink.TextTertiary, style = MaterialTheme.typography.labelSmall)
    }
}
