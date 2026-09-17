package app.pocketful.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.CopyRow
import app.pocketful.domain.Money
import app.pocketful.domain.WantRow
import app.pocketful.domain.marketTotal
import app.pocketful.domain.tradeRows
import app.pocketful.domain.wantRows
import app.pocketful.state.display
import app.pocketful.state.displayOrDash
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.CardTile
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.HeaderAction
import app.pocketful.ui.components.Headline
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.TopBar
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space
import kotlin.math.abs

/**
 * Both halves of a deal, on one screen.
 *
 * A trade is not a list of spares -- it is a comparison. What you are offering means nothing
 * without what you are chasing next to it, and having to remember one while scrolling to the
 * other is exactly the part people do badly. So the two lists sit on the same screen with
 * their totals stated, and the gap between the totals is the answer to "am I trading up or
 * down".
 *
 * That gap is now a drawn object rather than a figure in a stat cell. Two bars against each
 * other, scaled to the larger, say which side is heavier before either number is read --
 * which is the whole question, and the one thing a column of formatted currency is worst at
 * answering.
 *
 * Nothing here is a separate record. Offers are copies you have flagged and wants are the
 * pockets already held open in your binders, so this screen cannot disagree with the rest of
 * the app about what you have or what you need.
 */
@Composable
fun TradeScreen(
    snapshot: CollectionSnapshot,
    onOpenCopy: (CopyRow) -> Unit,
    onOpenWant: (WantRow) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offers = remember(snapshot) { snapshot.tradeRows() }
    val wants = remember(snapshot) {
        snapshot.wantRows().sortedByDescending { it.targetPrice.cents }
    }

    val offerValue = remember(offers, snapshot) { offers.marketTotal(snapshot) }
    val wantValue = remember(wants) { wants.fold(Money.ZERO) { acc, row -> acc + row.targetPrice } }
    val difference = remember(offerValue, wantValue) { offerValue.amount - wantValue }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Canvas)) {
        ScreenBackdrop(Ink.Gain, height = 300.dp)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                title = "Trade",
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
                        value = offerValue.amount.displayOrDash(offerValue.currency),
                        label = "On the table",
                        caption = when {
                            offers.isEmpty() -> "Nothing offered yet"
                            offerValue.alsoLabel != null ->
                                "${offers.size} cards offered · also ${offerValue.alsoLabel}"
                            else -> "${offers.size} ${if (offers.size == 1) "card" else "cards"} offered"
                        },
                    )
                }

                // The comparison, drawn. Only once both sides exist -- a balance with one
                // empty pan is a picture of nothing, and it would be the first thing on the
                // screen for anyone who has not started.
                if (offers.isNotEmpty() || wants.isNotEmpty()) {
                    item {
                        Balance(
                            offerValue = offerValue.amount,
                            wantValue = wantValue,
                            difference = difference,
                            offerCount = offers.size,
                            wantCount = wants.size,
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "You are offering · ${offers.size}",
                        modifier = Modifier.padding(top = Space.sm),
                        icon = AppIcons.Trade,
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
                            action = {
                                app.pocketful.ui.components.AppButton(
                                    label = "Pick cards to offer",
                                    onClick = onOpenCards,
                                    icon = AppIcons.Cards,
                                )
                            },
                        )
                    }
                } else {
                    tileRows(items = offers, keyPrefix = "offer", key = { it.copy.id.value }) { row ->
                        CardTile(
                            brief = row.brief,
                            caption = row.locationLabel,
                            // Gold, the same as everywhere else a price is shown -- the icon
                            // in the pill beside it is what says this one is on the table,
                            // the same as a binder pocket never tints its own price green.
                            value = row.value.displayOrNull(),
                            valueColor = Ink.Gold,
                            forTrade = true,
                            badge = row.copy.grade?.label ?: row.copy.condition.short,
                            badgeColor = if (row.copy.grade != null) Ink.Gold else Ink.TextTertiary,
                            onClick = { onOpenCopy(row) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "You are looking for · ${wants.size}",
                        modifier = Modifier.padding(top = Space.md),
                        icon = AppIcons.Target,
                    )
                }

                if (wants.isEmpty()) {
                    item {
                        Text(
                            text = "Mark a pocket as wanted inside a binder and it shows up here, " +
                                "so the other side of a trade is already written down when you need it.",
                            color = Ink.TextTertiary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = Space.sm),
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
}

/**
 * The two sides of the deal, weighed against each other.
 *
 * The bars are scaled to the larger of the two, so the shorter one is read as a fraction of
 * it rather than against an invisible maximum. The verdict line underneath states the gap as
 * one signed figure, because subtracting two currency amounts is the arithmetic people are
 * doing in their head at a trade table and getting wrong.
 */
@Composable
private fun Balance(
    offerValue: Money,
    wantValue: Money,
    difference: Money,
    offerCount: Int,
    wantCount: Int,
) {
    val widest = maxOf(offerValue.cents, wantValue.cents).takeIf { it > 0L }
    val offerShare = widest?.let { offerValue.cents.toFloat() / it } ?: 0f
    val wantShare = widest?.let { wantValue.cents.toFloat() / it } ?: 0f

    Panel(padding = Space.lg) {
        BalanceSide(
            label = "Offering",
            count = offerCount,
            value = offerValue.displayOrDash(),
            share = offerShare,
            tint = Ink.Gain,
        )
        Spacer(Modifier.height(Space.md))
        BalanceSide(
            label = "Seeking",
            count = wantCount,
            value = wantValue.displayOrDash(),
            share = wantShare,
            tint = Ink.Wanted,
        )

        if (widest != null) {
            Spacer(Modifier.height(Space.lg))
            val even = difference.isZero
            val up = difference.cents > 0
            val tint = when {
                even -> Ink.TextSecondary
                up -> Ink.Gain
                else -> Ink.Loss
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(AppShape.Small)
                    .background(tint.copy(alpha = 0.10f))
                    .border(1.dp, tint.copy(alpha = 0.22f), AppShape.Small)
                    .padding(Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        even -> AppIcons.Check
                        up -> AppIcons.ArrowUp
                        else -> AppIcons.ArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(Size.iconSm),
                    tint = tint,
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    text = when {
                        even -> "Even money, at market."
                        up -> "You are offering ${abs(difference.cents).let { Money(it) }.display()} more than you are asking for."
                        else -> "You are asking for ${abs(difference.cents).let { Money(it) }.display()} more than you are offering."
                    },
                    color = tint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BalanceSide(
    label: String,
    count: Int,
    value: String,
    share: Float,
    tint: Color,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = "$count",
                color = Ink.TextDisabled,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                color = app.pocketful.ui.components.moneyInk(value, tint),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(Space.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(AppShape.Pill)
                .background(Ink.Well),
        ) {
            if (share > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(share.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .clip(AppShape.Pill)
                        .background(tint),
                )
            }
        }
    }
}
