package app.pocketful.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import app.pocketful.data.RemoteSet
import app.pocketful.data.SearchHit
import app.pocketful.domain.Binder
import app.pocketful.domain.BinderLayout
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Printing
import app.pocketful.domain.SlotContent
import app.pocketful.state.CatalogBrowser
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.CardArtTile
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.CircleIconButton
import app.pocketful.ui.components.EmptyState
import app.pocketful.ui.components.ProgressTrack
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SetLogo
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.Tag
import app.pocketful.ui.components.tileRows
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink

/** Which slice of a set is being looked at. */
private enum class SetFilter(val label: String) {
    All("Every card"),
    Have("I have"),
    Want("I want"),
    Missing("Untracked"),
}

/** What this collection knows about one card in a set. */
private enum class CardStanding { Have, Want, Untracked }

/**
 * One set, whole.
 *
 * A set is the unit people actually collect toward -- "I am working on 151" -- and until
 * now the app could only show one as a summary card in a sheet, with its contents behind
 * a second tap that turned the search tab into a filtered list. That made the set a
 * *filter* rather than a place, which is backwards: the set is the thing, and what you own
 * of it is a property of it.
 *
 * So this is a screen. It states what the set is at the top, offers the one action that
 * turns a set into work you can do -- a binder sized for it with every pocket already
 * naming the card that belongs there -- and then shows the checklist itself: every card,
 * two to a row, at a size where the artwork is what identifies them.
 *
 * The status of each card is read from the collection rather than from the binder, so a
 * card you own in a box counts as owned here. Anything else would mean the checklist
 * disagreed with the rest of the app about what you have.
 */
@Composable
fun SetScreen(
    set: RemoteSet,
    browser: CatalogBrowser,
    snapshot: CollectionSnapshot,
    defaultLayout: BinderLayout,
    /** The binder already built from this set, if there is one. */
    existingBinder: Binder?,
    importing: Boolean,
    onBack: () -> Unit,
    onCreateBinder: (List<SearchHit>) -> Unit,
    onOpenBinder: (Binder) -> Unit,
    onAddCard: (SearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cards by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SetFilter.All) }

    // Cached per set for the life of the process by the client underneath, so coming back
    // to a set already opened is instant rather than another 250-card download.
    LaunchedEffect(set.id) {
        loading = true
        cards = browser.cardsInSet(set.id).sortedWith(
            compareBy({ it.number.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it.number }),
        )
        loading = false
    }

    // Which cards this collection holds, and which it is holding a pocket open for.
    //
    // Matched on set and printed number rather than on catalog id. Ids only line up for
    // cards that arrived through this catalog in the first place, and a checklist that
    // could not see a Charizard someone had typed in by hand -- or that came from an
    // import, or was in the collection before the catalog was ever reached -- would be a
    // checklist reporting zero to somebody looking straight at their own binder.
    val owned = remember(snapshot) {
        buildSet {
            for (copy in snapshot.copies.values) {
                snapshot.variants[copy.variantId]
                    ?.let { snapshot.printings[it.printingId] }
                    ?.let { addAll(it.checklistKeys()) }
            }
        }
    }
    val wanted = remember(snapshot) {
        buildSet {
            for (binder in snapshot.binders) {
                for (slot in binder.paddedSlots) {
                    if (slot !is SlotContent.Wanted) continue
                    snapshot.variants[slot.variantId]
                        ?.let { snapshot.printings[it.printingId] }
                        ?.let { addAll(it.checklistKeys()) }
                }
            }
        }
    }

    val standings = remember(cards, owned, wanted) {
        cards.associate { hit ->
            val keys = hit.checklistKeys()
            hit.id to when {
                keys.any { it in owned } -> CardStanding.Have
                keys.any { it in wanted } -> CardStanding.Want
                else -> CardStanding.Untracked
            }
        }
    }

    val haveCount = standings.values.count { it == CardStanding.Have }
    val wantCount = standings.values.count { it == CardStanding.Want }
    val total = set.officialCount ?: cards.size

    val visible = remember(cards, standings, filter, query) {
        val q = query.trim().lowercase()
        cards.filter { hit ->
            val matchesQuery = q.isEmpty() ||
                hit.name.lowercase().contains(q) ||
                hit.number.lowercase().startsWith(q)
            val matchesFilter = when (filter) {
                SetFilter.All -> true
                SetFilter.Have -> standings[hit.id] == CardStanding.Have
                SetFilter.Want -> standings[hit.id] == CardStanding.Want
                SetFilter.Missing -> standings[hit.id] == CardStanding.Untracked
            }
            matchesQuery && matchesFilter
        }
    }

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
                    eyebrow = listOfNotNull("Set", set.serie?.name).joinToString(" · "),
                    title = set.name,
                    subtitle = listOfNotNull(set.id.uppercase(), set.releaseDate).joinToString(" · "),
                    leading = { CircleIconButton(AppIcons.ChevronLeft, "Back to the catalog", onBack, size = 34.dp) },
                    cover = { SetLogo(set.logo, width = 86.dp) },
                    stats = buildList {
                        add(Stat("$total", "cards"))
                        add(Stat("$haveCount", "have", if (haveCount > 0) Ink.Gain else Ink.TextPrimary))
                        add(Stat("$wantCount", "want", if (wantCount > 0) Ink.Wanted else Ink.TextPrimary))
                        set.releaseYear?.let { add(Stat(it, "released")) }
                    },
                    tags = if (existingBinder == null) null else {
                        {
                            Tag(
                                text = "Tracked in ${existingBinder.name}",
                                color = Ink.Accent,
                                background = Ink.Accent.copy(alpha = 0.16f),
                            )
                        }
                    },
                    footer = {
                        SetActions(
                            set = set,
                            cards = cards,
                            loading = loading,
                            layout = defaultLayout,
                            existingBinder = existingBinder,
                            onCreateBinder = { onCreateBinder(cards) },
                            onOpenBinder = { existingBinder?.let(onOpenBinder) },
                        )
                    },
                )
            }

            // Drawn as soon as the set is being tracked at all, including at zero. A
            // want-list binder just built is 0% complete, and that is exactly the moment
            // the bar is worth having -- it is the thing that will move.
            if (total > 0 && (haveCount > 0 || wantCount > 0)) {
                item {
                    CompletionBar(have = haveCount, want = wantCount, total = total)
                }
            }

            item {
                Column(Modifier.padding(top = 4.dp)) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Find a card in ${set.name}",
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SetFilter.entries.forEach { option ->
                            ChoiceChip(
                                label = option.label,
                                selected = option == filter,
                                onClick = { filter = option },
                                accent = when (option) {
                                    SetFilter.Have -> Ink.Gain
                                    SetFilter.Want -> Ink.Wanted
                                    else -> Ink.Accent
                                },
                            )
                        }
                    }
                }
            }

            if (importing) {
                item {
                    Text(
                        text = "Fetching card details…",
                        color = Ink.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                SectionHeader(
                    title = when {
                        loading -> "Loading the checklist…"
                        filter == SetFilter.All -> "Checklist · ${visible.size}"
                        else -> "${filter.label} · ${visible.size}"
                    },
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (visible.isEmpty() && !loading) {
                item {
                    EmptyState(
                        icon = AppIcons.Search,
                        title = if (cards.isEmpty()) "No checklist for this set" else "Nothing here",
                        message = when {
                            cards.isEmpty() ->
                                "The catalog did not return any cards for ${set.name}. " +
                                    "It may be a set that has been announced but not yet listed."
                            query.isNotBlank() -> "Nothing in this set matches \"$query\"."
                            filter == SetFilter.Have -> "You have not recorded any cards from this set yet."
                            filter == SetFilter.Want ->
                                "Nothing from this set is on your want list. " +
                                    "Building a binder for it opens a pocket for every card."
                            else -> "Every card in this set is already tracked."
                        },
                    )
                }
            }

            tileRows(items = visible, keyPrefix = "set-card", key = { it.id }) { hit ->
                val standing = standings[hit.id] ?: CardStanding.Untracked
                CardArtTile(
                    name = hit.name,
                    caption = hit.collectorNumber,
                    artStem = hit.artStem,
                    badge = when (standing) {
                        CardStanding.Have -> "HAVE"
                        CardStanding.Want -> "WANT"
                        CardStanding.Untracked -> null
                    },
                    badgeColor = when (standing) {
                        CardStanding.Have -> Ink.Gain
                        else -> Ink.Wanted
                    },
                    // Untracked cards are the ones still to be dealt with, so they are the
                    // ones drawn at full strength. A checklist where the finished entries
                    // shout is a checklist you have to read backwards.
                    ghosted = standing == CardStanding.Want,
                    enabled = !importing,
                    onClick = { onAddCard(hit) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The one thing worth doing with a set, and the way back to it once it is done.
 *
 * The button changes rather than multiplying. Offering "make a binder" next to "open the
 * binder you already made" is how someone ends up with three binders for one set and no
 * idea which one has their cards in it.
 */
@Composable
private fun SetActions(
    set: RemoteSet,
    cards: List<SearchHit>,
    loading: Boolean,
    layout: BinderLayout,
    existingBinder: Binder?,
    onCreateBinder: () -> Unit,
    onOpenBinder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = cards.size.takeIf { it > 0 } ?: set.officialCount ?: 0
    val sheets = sheetsToHold(count.coerceAtLeast(1), layout)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (existingBinder != null) {
            AppButton(
                label = "Open ${existingBinder.name}",
                onClick = onOpenBinder,
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Binders,
            )
            AppOutlineButton(
                label = "Build another binder for it",
                onClick = onCreateBinder,
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Plus,
            )
        } else {
            AppButton(
                label = if (loading) "Loading the checklist…" else "Build a want-list binder",
                onClick = onCreateBinder,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && cards.isNotEmpty(),
                icon = AppIcons.Plus,
            )
            Text(
                text = if (cards.isEmpty()) {
                    "A binder can be built once the checklist has loaded."
                } else {
                    "$sheets ${if (sheets == 1) "sheet" else "sheets"} of ${layout.displayName} " +
                        "pages -- one pocket per card, every one marked wanted."
                },
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** How far through the set the collection is, as one bar rather than two fractions. */
@Composable
private fun CompletionBar(have: Int, want: Int, total: Int, modifier: Modifier = Modifier) {
    val fraction = if (total <= 0) 0f else have.toFloat() / total
    val percent = (fraction * 100).toInt()

    Column(
        modifier
            .fillMaxWidth()
            .clip(AppShape.Medium)
            .background(Ink.Surface)
            .border(1.dp, Ink.OutlineFaint, AppShape.Medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$percent% complete",
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$have of $total",
                color = Ink.TextTertiary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(9.dp))
        ProgressTrack(fraction = fraction, color = Ink.Gain, height = 6.dp)
        if (want > 0) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = "$want more on the want list",
                color = Ink.Wanted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * How a card is looked up across two records that were never keyed the same way.
 *
 * Catalog ids are no use here: they only line up for cards that arrived through this
 * catalog in the first place, and a checklist blind to a Charizard someone typed in by
 * hand would report zero to a collector looking straight at their own binder.
 *
 * So a card is identified by its printed number plus the set it is in -- and the set is
 * offered twice, once by code and once by name, because those are keyed independently.
 * TCGdex files Jungle under `base2` while every human record of it says "Jungle", so
 * either key alone misses half the cases. Both are generated and any match counts; two
 * different sets would have to share a name *and* a card number to collide.
 */
private fun Printing.checklistKeys(): List<String> = keysFor(setCode, setName, number)

private fun SearchHit.checklistKeys(): List<String> = keysFor(setId, setName, number)

private fun keysFor(code: String, name: String, number: String): List<String> {
    val printed = number.trim().lowercase()
    return listOf(
        "${code.trim().lowercase()}|$printed",
        "${name.lowercase().filter(Char::isLetterOrDigit)}|$printed",
    )
}

/**
 * How many sheets it takes to hold a set, rounding up.
 *
 * Rounding up rather than to the nearest: a binder one sheet short of the set is a binder
 * you have to resize the moment you reach the end, and a spare sheet costs nothing.
 */
fun sheetsToHold(cardCount: Int, layout: BinderLayout): Int {
    val perSheet = layout.pocketsPerSheet.coerceAtLeast(1)
    return ((cardCount + perSheet - 1) / perSheet).coerceIn(1, 60)
}
