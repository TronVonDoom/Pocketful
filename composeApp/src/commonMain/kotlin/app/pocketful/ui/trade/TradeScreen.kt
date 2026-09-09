package app.pocketful.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.CopyRow
import app.pocketful.domain.Money
import app.pocketful.domain.WantRow
import app.pocketful.domain.tradeRows
import app.pocketful.domain.wantRows
import app.pocketful.state.display
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * Both halves of a deal, on one screen.
 *
 * A trade is not a list of spares -- it is a comparison. What you are offering means
 * nothing without what you are chasing next to it, and having to remember one while
 * scrolling to the other is exactly the part people do badly. So the two lists sit on the
 * same screen with their totals stated, and the gap between the totals is the answer to
 * "am I trading up or down".
 *
 * Nothing here is a separate record. Offers are copies you have flagged and wants are the
 * pockets already held open in your binders, so this screen cannot disagree with the rest
 * of the app about what you have or what you need.
 */
@Composable
fun TradeScreen(
    snapshot: CollectionSnapshot,
    onBack: () -> Unit,
    onOpenCopy: (CopyRow) -> Unit,
    onOpenWant: (WantRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offers = remember(snapshot) { snapshot.tradeRows() }
    val wants = remember(snapshot) {
        snapshot.wantRows().sortedByDescending { it.targetPrice.cents }
    }

    val offerValue = remember(offers) { offers.fold(Money.ZERO) { acc, row -> acc + row.value } }
    val wantValue = remember(wants) { wants.fold(Money.ZERO) { acc, row -> acc + row.targetPrice } }
    val difference = remember(offerValue, wantValue) { offerValue - wantValue }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Background)) {
        ScreenBackdrop(Ink.Gain, height = 280.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = islandBottomInset()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Trade",
                    title = "On the table",
                    subtitle = "What you can give, and what you want back",
                    headline = offerValue.format(),
                    headlineCaption = "offered",
                    leading = { CircleIconButton(AppIcons.ChevronLeft, "Back", onBack, size = 34.dp) },
                    stats = buildList {
                        add(Stat("${offers.size}", "on offer", Ink.Gain))
                        add(Stat("${wants.size}", "sought", Ink.Wanted))
                        add(Stat(wantValue.display(), "to acquire"))
                        // Stated as a single signed figure rather than left for the user
                        // to subtract two numbers they can only see one of at a time.
                        if (!difference.isZero) {
                            add(
                                Stat(
                                    value = (if (difference.cents >= 0) "+" else "") +
                                        difference.display(),
                                    label = "spread",
                                    accent = if (difference.cents >= 0) Ink.Gain else Ink.Loss,
                                ),
                            )
                        }
                    },
                )
            }

            item {
                SectionHeader(
                    title = "You are offering · ${offers.size}",
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                )
            }

            if (offers.isEmpty()) {
                item {
                    EmptyState(
                        icon = AppIcons.Trade,
                        title = "Nothing on the table",
                        message = "Open any card you own and switch on \"Up for trade\". " +
                            "It stays exactly where it is filed -- the flag is a note " +
                            "about what you are willing to part with.",
                    )
                }
            } else {
                tileRows(items = offers, keyPrefix = "offer", key = { it.copy.id.value }) { row ->
                    CardTile(
                        brief = row.brief,
                        caption = row.locationLabel,
                        value = row.value.displayOrNull(),
                        valueColor = Ink.Gain,
                        badge = row.copy.grade?.label ?: row.copy.condition.short,
                        badgeColor = Ink.Gain,
                        onClick = { onOpenCopy(row) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SectionHeader(
                    title = "You are looking for · ${wants.size}",
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }

            if (wants.isEmpty()) {
                item {
                    Text(
                        text = "Mark a pocket as wanted inside a binder and it shows up here, " +
                            "so the other side of a trade is already written down when you need it.",
                        color = Ink.TextTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
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
        }
    }
}
