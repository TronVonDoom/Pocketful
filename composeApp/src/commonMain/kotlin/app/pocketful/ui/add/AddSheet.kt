package app.pocketful.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketful.data.SearchHit
import app.pocketful.domain.CardBrief
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerId
import app.pocketful.domain.PrintingId
import app.pocketful.domain.allBriefs
import app.pocketful.domain.byPrinting
import app.pocketful.domain.search
import app.pocketful.state.CardLookup
import app.pocketful.state.displayOrNull
import app.pocketful.ui.components.AppSheet
import app.pocketful.ui.components.CardResultRow
import app.pocketful.ui.components.CardRowItem
import app.pocketful.ui.components.ChoiceChip
import app.pocketful.ui.components.Note
import app.pocketful.ui.components.SearchField
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.SheetBody
import app.pocketful.ui.components.SheetHeader
import app.pocketful.ui.components.tappable
import app.pocketful.ui.components.trendChip
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.AppShape
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Size
import app.pocketful.ui.theme.Space

/**
 * Recording a card, from anywhere, in one place.
 *
 * This is the sheet behind the plus in the middle of the navigation dock, and it is the
 * single biggest thing the rebuild added. Before it, adding a card meant knowing one of
 * three unmarked routes: tap a tile on the search tab, tap an empty pocket inside a binder,
 * or find the quick-add plus drawn in the corner of some card art. The most common thing
 * anyone does with a collection tracker had no button.
 *
 * Three decisions shape it:
 *
 * **Where it lands is chosen first, and remembered.** Someone sorting a stack of forty cards
 * into one box answers "which box" forty times with the same answer, so the app asks once
 * and keeps the answer across launches. The chip row is at the top, before the search field,
 * because a destination picked after the card has already been added is a correction rather
 * than a choice.
 *
 * **Your own catalog is searched before the network.** A card you have added once can be
 * added again instantly; a card the app has never seen has to be fetched. Showing them in
 * separate bands is what makes "why did that one take two seconds" answerable.
 *
 * **One tap is the whole interaction.** The plus on a row records a near-mint copy of the
 * plainest printing and leaves the sheet open, because the gesture that matters is the
 * thirtieth one, not the first. Tapping the row itself opens the full sheet for the times
 * the condition, the grade or the price paid actually matter.
 */
@Composable
fun AddSheet(
    visible: Boolean,
    snapshot: CollectionSnapshot,
    lookup: CardLookup,
    /** Where a card added from here goes: a container, or null for unfiled. */
    destination: ContainerId?,
    onDestinationChange: (ContainerId?) -> Unit,
    importing: Boolean,
    onDismiss: () -> Unit,
    onOpenLocalCard: (CardBrief) -> Unit,
    onOpenRemoteCard: (SearchHit) -> Unit,
    onQuickAddLocal: (CardBrief) -> Unit,
    onQuickAddRemote: (SearchHit) -> Unit,
    onCreateBinder: () -> Unit,
    onCreateContainer: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    LaunchedEffect(visible) { if (!visible) query = "" }
    LaunchedEffect(query, visible) { if (visible) lookup.onQueryChanged(query) }

    val trimmed = query.trim()
    val searching = trimmed.isNotEmpty()

    val localCatalog = remember(snapshot) { snapshot.allBriefs() }
    // One row per printing, not per press run. The finish is a choice made in the sheet that
    // opens, where the price for each one is visible, and that sheet opens on whichever
    // variant the row stands for -- so the representative row is also the default answer.
    val localResults = remember(localCatalog, trimmed) {
        if (trimmed.isEmpty()) emptyList()
        else localCatalog.search(trimmed, limit = 40).byPrinting().take(20)
    }
    // Counted per printing to match, so a row that stands for three variants does not claim
    // you own none of it because you own the reverse rather than the normal.
    val ownedCounts: Map<PrintingId, Int> = remember(snapshot) {
        snapshot.copies.values
            .mapNotNull { snapshot.variants[it.variantId]?.printingId }
            .groupingBy { it }
            .eachCount()
    }
    val known = remember(localCatalog) {
        localCatalog.map { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" }.toSet()
    }
    val remote = remember(lookup.results, known) {
        lookup.results.filterNot { "${it.name.lowercase()}|${it.collectorNumber.lowercase()}" in known }
    }

    val destinationName = remember(destination, snapshot.containers) {
        destination?.let { id -> snapshot.containers.firstOrNull { it.id == id }?.name } ?: "Unfiled"
    }

    AppSheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader(
            title = "Add cards",
            subtitle = "Going into $destinationName",
            onClose = onDismiss,
        )

        SheetBody {
            DestinationRow(
                containers = snapshot.containers,
                selected = destination,
                onSelect = onDestinationChange,
            )

            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search for a card by name",
            )

            if (!searching) {
                // What the plus can make when it is not making a card. Keeping these here
                // rather than on a menu of their own is what lets one button be the app's
                // whole answer to "create something": the common case is the field above,
                // and the two rarer ones are a tap away instead of a tab away.
                SectionHeader("Or make somewhere to put them", icon = AppIcons.Collections)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    CreateCard(
                        icon = AppIcons.Binders,
                        title = "New binder",
                        caption = "Pages of pockets you turn",
                        tint = Ink.Accent,
                        onClick = onCreateBinder,
                        modifier = Modifier.weight(1f),
                    )
                    CreateCard(
                        icon = AppIcons.Box,
                        title = "New box",
                        caption = "An ordered pile with a label",
                        tint = Ink.Gold,
                        onClick = onCreateContainer,
                        modifier = Modifier.weight(1f),
                    )
                }

                Note(
                    "Tap the plus on any result to record one near-mint copy straight away. " +
                        "Tap the card itself to set a condition, a grade or what you paid.",
                )
                return@SheetBody
            }

            if (importing) Note("Fetching card details…", icon = AppIcons.Clock)

            if (localResults.isNotEmpty()) {
                SectionHeader("Already in your catalog · ${localResults.size}", icon = AppIcons.Cards)
                localResults.forEach { brief ->
                    val owned = ownedCounts[brief.printingId] ?: 0
                    CardRowItem(
                        brief = brief,
                        value = brief.marketValue.displayOrNull(brief.currency),
                        trend = trendChip(brief.change, brief.marketValue),
                        count = owned,
                        badge = if (owned > 0) "OWN" else null,
                        badgeColor = Ink.Gain,
                        onQuickAdd = { onQuickAddLocal(brief) },
                        onClick = { onOpenLocalCard(brief) },
                    )
                }
            }

            if (trimmed.length < 2) {
                Note("Type at least two letters to search the full catalog.")
                return@SheetBody
            }

            SectionHeader(
                title = when {
                    lookup.searching -> "Searching the catalog…"
                    remote.isEmpty() -> "Catalog"
                    else -> "Catalog · ${remote.size}"
                },
                icon = AppIcons.Search,
            )

            lookup.error?.let { Note(it, error = true) }

            // One block per game, headed only when there is more than one to tell apart. A
            // search for "Pikachu" really does span both Pokémon catalogs, and an unlabelled
            // run of rows from each does not say which game either belongs to.
            val byGame = remote.groupBy { it.game }
            byGame.entries.sortedBy { it.key.ordinal }.forEach { (game, rows) ->
                if (byGame.size > 1) SectionHeader("${game.label} · ${rows.size}")
                rows.forEach { hit ->
                    CardResultRow(
                        name = hit.name,
                        caption = "${hit.setName} · ${hit.collectorNumber}",
                        art = hit.image,
                        back = hit.back,
                        enabled = !importing,
                        onQuickAdd = { onQuickAddRemote(hit) },
                        onClick = { onOpenRemoteCard(hit) },
                    )
                }
            }

            if (!lookup.searching && remote.isEmpty() && lookup.error == null && localResults.isEmpty()) {
                Note("Nothing in the catalog matches \"$trimmed\".")
            }
        }
    }
}

/**
 * "Adding to: Unfiled · Bulk box · Slabs" -- the place every add from here lands.
 *
 * Above the search field rather than below the results. A destination chosen after the card
 * has already gone somewhere is not a choice, it is a correction, and this is the control
 * that stops forty cards ending up unfiled because nobody scrolled far enough to see it.
 */
@Composable
private fun DestinationRow(
    containers: List<Container>,
    selected: ContainerId?,
    onSelect: (ContainerId?) -> Unit,
) {
    Column {
        SectionHeader("Adding to", icon = AppIcons.Box)
        Spacer(Modifier.height(Space.sm))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceChip(
                label = "Unfiled",
                selected = selected == null,
                onClick = { onSelect(null) },
            )
            containers.forEach { container ->
                ChoiceChip(
                    label = container.name,
                    selected = selected == container.id,
                    accent = Color(container.color),
                    onClick = { onSelect(container.id) },
                )
            }
        }
    }
}

/** One of the two things the plus can make that is not a card. */
@Composable
private fun CreateCard(
    icon: ImageVector,
    title: String,
    caption: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .heightIn(min = 108.dp)
            .clip(AppShape.Medium)
            .background(Ink.SurfaceRaised)
            .border(1.dp, tint.copy(alpha = 0.25f), AppShape.Medium)
            .tappable(pressScale = 0.97f, onClick = onClick)
            .padding(Space.md),
    ) {
        Box(
            Modifier
                .size(Size.control - 4.dp)
                .clip(AppShape.Small)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(Size.iconSm), tint = tint)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            text = caption,
            color = Ink.TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
