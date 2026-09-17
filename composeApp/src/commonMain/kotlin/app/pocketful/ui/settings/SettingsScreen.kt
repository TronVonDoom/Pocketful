package app.pocketful.ui.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketful.AppVersion
import app.pocketful.data.CatalogSync
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.state.AppSettings
import app.pocketful.state.CollectionTransfer
import app.pocketful.ui.binder.LayoutPicker
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.ButtonTone
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.Hairline
import app.pocketful.ui.components.MetricCell
import app.pocketful.ui.components.Note
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.ProgressTrack
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.Stepper
import app.pocketful.ui.components.ToggleSwitch
import app.pocketful.ui.components.TopBar
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Space
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Preferences, and only ones that change something.
 *
 * Settings is no longer a tab. It was taking a quarter of the navigation bar to be the
 * screen people open twice and then not again for a month, while Trade and the card list --
 * both places people go on purpose -- had no seat at all. It lives behind the gear on Home
 * now, which is where a rarely-wanted, easily-found thing belongs.
 *
 * The order changed with it. The old screen opened on binder-page toggles and buried the
 * catalog update four sections down, which is exactly backwards: almost nobody opens
 * Settings to turn off holo shimmer, and almost everybody who opens it is there because a
 * card has no picture or no price. So the two things that fetch data come first, the
 * preferences sit in the middle, and everything that destroys something is at the bottom
 * where it cannot be hit on the way past.
 *
 * There is deliberately no "coming soon" section: a settings screen full of inert rows is
 * worse than a short one.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    snapshot: CollectionSnapshot,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onSyncCatalog: suspend ((done: Int, total: Int) -> Unit) -> CatalogSync.Result,
    transfer: CollectionTransfer,
    onExport: () -> Unit,
    onImportChoose: () -> Unit,
    onImportApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    // Nothing to back up is a reason to grey the button out rather than to let someone save
    // an empty file and find out later that it was empty.
    val hasSomethingToExport = snapshot.binders.isNotEmpty() ||
        snapshot.containers.isNotEmpty() ||
        snapshot.copies.isNotEmpty()
    var sync by remember { mutableStateOf<SyncState>(SyncState.Idle) }
    val scope = rememberCoroutineScope()

    val withArt = remember(snapshot) { snapshot.printings.values.count { it.image != null } }
    val priced = remember(snapshot) { snapshot.prices.values.count { it.source == "tcgplayer" } }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Canvas)) {
        ScreenBackdrop(Ink.Accent, height = 240.dp)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(title = "Settings", onBack = onBack, backDescription = "Back to home")

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
                // How much of the collection the catalog has actually resolved. That is the
                // one figure a settings screen can state that its switches cannot, and it is
                // the answer to "why does this card still have no picture" -- which is what
                // brings most people to this screen in the first place.
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        MetricCell(
                            value = "${snapshot.copies.size}",
                            label = "cards",
                            icon = AppIcons.Cards,
                            modifier = Modifier.weight(1f),
                        )
                        MetricCell(
                            value = "$withArt",
                            label = "with art",
                            accent = if (withArt > 0) Ink.Gain else Ink.TextPrimary,
                            icon = AppIcons.Grid,
                            modifier = Modifier.weight(1f),
                        )
                        MetricCell(
                            value = "$priced",
                            label = "priced",
                            accent = if (priced > 0) Ink.Gold else Ink.TextPrimary,
                            icon = AppIcons.Tag,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ------------------------------------------------ what people come for

                item { SectionHeader("Card data", Modifier.padding(top = Space.sm), icon = AppIcons.Refresh) }
                item {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            DetailRow("Catalog", "Pocketful catalog")
                            DetailRow("With artwork", "$withArt of ${snapshot.printings.size}")
                            DetailRow("Live prices", "$priced of ${snapshot.prices.size}")
                        }

                        Spacer(Modifier.height(Space.lg))

                        when (val state = sync) {
                            is SyncState.Running -> {
                                Text(
                                    text = "Updating the catalog and your cards…",
                                    color = Ink.TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(Space.md))
                                ProgressTrack(
                                    fraction = if (state.total == 0) 0f else {
                                        state.done.toFloat() / state.total
                                    },
                                )
                                Spacer(Modifier.height(Space.sm))
                                Text(
                                    text = "${state.done} of ${state.total}",
                                    color = Ink.TextTertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            is SyncState.Done -> {
                                Note(state.message, error = state.failed)
                                Spacer(Modifier.height(Space.md))
                                AppOutlineButton(
                                    label = "Update again",
                                    onClick = { sync = startSync(scope, onSyncCatalog) { sync = it } },
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = AppIcons.Refresh,
                                )
                            }

                            SyncState.Idle -> {
                                Text(
                                    text = "Pulls real artwork and current TCGplayer market prices for " +
                                        "every card in your collection. Cards it cannot match are left " +
                                        "exactly as they are.",
                                    color = Ink.TextTertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(Space.md))
                                AppButton(
                                    label = "Update card data",
                                    onClick = { sync = startSync(scope, onSyncCatalog) { sync = it } },
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = AppIcons.Refresh,
                                )
                            }
                        }
                    }
                }

                item { SectionHeader("App updates", Modifier.padding(top = Space.sm), icon = AppIcons.Download) }
                item { UpdatePanel() }

                // ------------------------------------------------------- preferences

                item { SectionHeader("Binder page", Modifier.padding(top = Space.sm), icon = AppIcons.Binders) }
                item {
                    Panel(padding = Space.xs) {
                        SettingToggle(
                            title = "Holo shimmer",
                            description = "Animated sheen on foil cards, with the glitter that " +
                                "catches it. Turn it off to save battery.",
                            checked = settings.holoShimmer,
                            onCheckedChange = { value -> onUpdate { it.copy(holoShimmer = value) } },
                        )
                        Hairline(Modifier.padding(horizontal = Space.md))
                        SettingToggle(
                            title = "Prices in pockets",
                            description = "Show each card's value on the page.",
                            checked = settings.showPocketPrices,
                            onCheckedChange = { value -> onUpdate { it.copy(showPocketPrices = value) } },
                        )
                        Hairline(Modifier.padding(horizontal = Space.md))
                        SettingToggle(
                            title = "Ghost wanted cards",
                            description = "Draw wanted cards as outlines instead of leaving the pocket blank.",
                            checked = settings.showWantedGhosts,
                            onCheckedChange = { value -> onUpdate { it.copy(showWantedGhosts = value) } },
                        )
                        Hairline(Modifier.padding(horizontal = Space.md))
                        SettingToggle(
                            title = "Abbreviate large values",
                            description = "Show \$1.2k instead of \$1,200.00 in tight spaces.",
                            checked = settings.abbreviateValues,
                            onCheckedChange = { value -> onUpdate { it.copy(abbreviateValues = value) } },
                        )
                    }
                }

                item { SectionHeader("New binders", Modifier.padding(top = Space.sm), icon = AppIcons.Plus) }
                item {
                    Panel {
                        // The same picker the binder editor uses, so a default page shape can
                        // be any shape a binder can be -- not just one off the preset list.
                        LayoutPicker(
                            layout = settings.defaultLayout,
                            onLayoutChange = { layout -> onUpdate { it.copy(defaultLayout = layout) } },
                        )

                        Spacer(Modifier.height(Space.xl))
                        FieldLabel("Default sheet count")
                        Spacer(Modifier.height(Space.sm))
                        Stepper(
                            value = settings.defaultSheetCount,
                            onValueChange = { value -> onUpdate { it.copy(defaultSheetCount = value) } },
                            range = 1..60,
                            suffix = if (settings.defaultSheetCount == 1) "sheet" else "sheets",
                        )
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            text = "${settings.defaultLayout.capacity(settings.defaultSheetCount)} pockets " +
                                "across ${settings.defaultLayout.faceCount(settings.defaultSheetCount)} pages.",
                            color = Ink.TextTertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // ------------------------------------------------------ your own data

                item { SectionHeader("Backup", Modifier.padding(top = Space.sm), icon = AppIcons.Download) }
                item {
                    Panel {
                        val pending = transfer.pending
                        if (pending != null) {
                            // What is in the file, before the button that acts on it. The
                            // counts are the question: "replace everything" is unanswerable,
                            // "replace everything with 4 binders and 312 cards" is not.
                            Text(
                                text = "${pending.fileName} holds " +
                                    listOfNotNull(
                                        plural(pending.binders, "binder"),
                                        plural(pending.containers, "container"),
                                        plural(pending.cards, "card"),
                                    ).joinToString(", ") + ".",
                                color = Ink.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(Space.md))
                            Note(
                                "Importing replaces every binder, box and card in the app with " +
                                    "what is in this file. There is no undo, so export what you " +
                                    "have first if you want to keep it.",
                                error = true,
                            )
                            Spacer(Modifier.height(Space.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                AppOutlineButton("Cancel", { transfer.dismiss() }, Modifier.weight(1f))
                                AppButton(
                                    label = "Replace",
                                    onClick = onImportApply,
                                    modifier = Modifier.weight(1f),
                                    tone = ButtonTone.Danger,
                                    icon = AppIcons.Download,
                                )
                            }
                        } else {
                            Text(
                                text = "A backup is one file holding your binders, boxes and cards, " +
                                    "and the catalog entries they need to be readable on a phone " +
                                    "that has never seen them.",
                                color = Ink.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(Space.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                AppOutlineButton(
                                    label = "Export",
                                    onClick = onExport,
                                    modifier = Modifier.weight(1f),
                                    enabled = !transfer.busy && hasSomethingToExport,
                                )
                                AppOutlineButton(
                                    label = "Import",
                                    onClick = onImportChoose,
                                    modifier = Modifier.weight(1f),
                                    enabled = !transfer.busy,
                                )
                            }
                        }

                        when (val status = transfer.status) {
                            CollectionTransfer.Status.Idle -> Unit
                            is CollectionTransfer.Status.Done -> {
                                Spacer(Modifier.height(Space.md))
                                Note(status.message)
                            }
                            is CollectionTransfer.Status.Problem -> {
                                Spacer(Modifier.height(Space.md))
                                Note(status.message, error = true)
                            }
                        }
                    }
                }

                item { SectionHeader("About", Modifier.padding(top = Space.sm), icon = AppIcons.Info) }
                item {
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            // Read from the generated AppVersion rather than typed here, so
                            // the number on this row is the number the updater compares
                            // against a release tag and cannot drift from it.
                            DetailRow("Pocketful", AppVersion.label)
                            DetailRow(
                                label = "Price data",
                                value = if (priced > 0) "TCGplayer" else "none yet",
                                caption = if (priced > 0) "market price, updated nightly" else null,
                            )
                            DetailRow("Storage", "on this device")
                            DetailRow("Binders", "${snapshot.binders.size}")
                            DetailRow("Boxes", "${snapshot.containers.size}")
                            DetailRow("Pockets", "${snapshot.binders.sumOf { it.capacity }}")
                            DetailRow("Catalog entries", "${snapshot.variants.size}")
                        }
                        Spacer(Modifier.height(Space.lg))
                        Text(
                            text = "Cards come from the Pocketful catalog, a set at a time as each is " +
                                "reviewed and published. Prices are TCGplayer's market price, updated " +
                                "nightly, for every printing linked to a TCGplayer product; one with no " +
                                "product shows no price rather than a guess. Your collection is written " +
                                "to this device as you change it, and lives nowhere else -- so a backup " +
                                "is the only copy of it that survives losing the phone.",
                            color = Ink.TextTertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // ----------------------------------------------------- the last resort
                //
                // At the very bottom, behind a scroll, behind a confirmation. Nothing else
                // on this screen can lose data, and this one can lose all of it.

                item {
                    SectionHeader("Danger zone", Modifier.padding(top = Space.xl), icon = AppIcons.Trash)
                }
                item {
                    Panel {
                        if (confirmingReset) {
                            Note(
                                "This deletes every binder, box and card in the app and leaves you " +
                                    "with an empty collection. There is no undo.",
                                error = true,
                            )
                            Spacer(Modifier.height(Space.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                AppOutlineButton("Cancel", { confirmingReset = false }, Modifier.weight(1f))
                                AppButton(
                                    label = "Delete all",
                                    onClick = {
                                        confirmingReset = false
                                        onReset()
                                    },
                                    modifier = Modifier.weight(1f),
                                    tone = ButtonTone.Danger,
                                    icon = AppIcons.Trash,
                                )
                            }
                        } else {
                            Text(
                                text = "Export a backup first if there is anything here you would " +
                                    "miss. Starting over cannot be undone.",
                                color = Ink.TextTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(Space.md))
                            AppOutlineButton(
                                label = "Delete everything and start over",
                                onClick = { confirmingReset = true },
                                modifier = Modifier.fillMaxWidth(),
                                icon = AppIcons.Trash,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Space.xxs))
            Text(description, color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(Space.md))
        ToggleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Where a catalog update has got to. */
private sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val done: Int, val total: Int) : SyncState
    data class Done(val message: String, val failed: Boolean) : SyncState
}

/**
 * Kicks off a catalog update and reports back through [publish].
 *
 * Returns the state to show immediately so the button cannot be pressed twice before the
 * first progress callback lands.
 */
private fun startSync(
    scope: CoroutineScope,
    run: suspend ((done: Int, total: Int) -> Unit) -> CatalogSync.Result,
    publish: (SyncState) -> Unit,
): SyncState {
    scope.launch {
        val result = run { done, total -> publish(SyncState.Running(done, total)) }
        publish(
            when {
                result.failure != null -> SyncState.Done(result.failure!!, failed = true)
                result.matched == 0 && result.attempted > 0 -> SyncState.Done(
                    "The catalog is up to date. None of your cards are in it yet.",
                    failed = false,
                )
                result.attempted == 0 -> SyncState.Done("The catalog is up to date.", failed = false)
                else -> SyncState.Done(
                    buildString {
                        append("Refreshed ")
                        append(result.matched)
                        append(" of ")
                        append(result.attempted)
                        append(" cards from the catalog")
                        if (result.pricesUpdated > 0) {
                            append(" and priced ")
                            append(result.pricesUpdated)
                        }
                        append(".")
                        if (result.unmatched > 0) {
                            append(" Cards you added by hand were left as they are.")
                        }
                    },
                    failed = false,
                )
            },
        )
    }
    return SyncState.Running(0, 0)
}

/**
 * "3 binders", or nothing at all when there are none.
 *
 * Returns null rather than "0 binders" so a caller can drop the empty categories from a list
 * instead of reciting them: a backup of loose cards should say "312 cards", not "0 binders,
 * 0 containers, 312 cards".
 */
private fun plural(count: Int, noun: String): String? = when (count) {
    0 -> null
    1 -> "1 $noun"
    else -> "$count ${noun}s"
}
