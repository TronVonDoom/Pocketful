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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.pocketful.AppVersion
import app.pocketful.data.CatalogSync
import app.pocketful.domain.CollectionSnapshot
import app.pocketful.state.AppSettings
import app.pocketful.ui.binder.LayoutPicker
import app.pocketful.ui.components.AppButton
import app.pocketful.ui.components.AppOutlineButton
import app.pocketful.ui.components.ButtonTone
import app.pocketful.ui.components.DetailRow
import app.pocketful.ui.components.FieldLabel
import app.pocketful.ui.components.Hairline
import app.pocketful.ui.components.Panel
import app.pocketful.ui.components.ScreenBackdrop
import app.pocketful.ui.components.ScreenHeader
import app.pocketful.ui.components.Stat
import app.pocketful.ui.components.SectionHeader
import app.pocketful.ui.components.ProgressTrack
import app.pocketful.ui.components.Stepper
import app.pocketful.ui.components.ToggleSwitch
import app.pocketful.ui.nav.islandBottomInset
import app.pocketful.ui.theme.AppIcons
import app.pocketful.ui.theme.Ink

/**
 * Preferences, and only ones that change something.
 *
 * Every switch here feeds a composable that reads it. There is deliberately no "coming
 * soon" section: a settings screen full of inert rows is worse than a short one.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    snapshot: CollectionSnapshot,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onReset: () -> Unit,
    onSyncCatalog: suspend ((done: Int, total: Int) -> Unit) -> CatalogSync.Result,
    modifier: Modifier = Modifier,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    var sync by remember { mutableStateOf<SyncState>(SyncState.Idle) }
    val scope = rememberCoroutineScope()

    val withArt = remember(snapshot) { snapshot.printings.values.count { it.imageUrl != null } }
    val priced = remember(snapshot) { snapshot.prices.values.count { it.source == "tcgplayer" } }

    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize().background(Ink.Background)) {
        ScreenBackdrop(Ink.Accent, height = 260.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = islandBottomInset()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // The header earns its place here now that it carries something: how much of
            // the collection the catalog has actually resolved. That is the one figure a
            // settings screen can state that its switches cannot, and it is the answer to
            // "why does this card still have no picture" -- which is what brings most
            // people to this screen in the first place.
            item {
                ScreenHeader(
                    eyebrow = "Settings",
                    centered = true,
                    headline = "${snapshot.copies.size}",
                    headlineCaption = "cards on file",
                    stats = listOf(
                        Stat("$withArt", "with art", if (withArt > 0) Ink.Gain else Ink.TextPrimary),
                        Stat("$priced", "priced", if (priced > 0) Ink.Gold else Ink.TextPrimary),
                        Stat("${snapshot.printings.size}", "printings"),
                    ),
                )
            }

            item { SectionHeader("Binder page", Modifier.padding(top = 2.dp)) }
            item {
                Panel(padding = 4.dp) {
                    SettingToggle(
                        title = "Holo shimmer",
                        description = "Animated sheen on foil cards. Turn it off to save battery.",
                        checked = settings.holoShimmer,
                        onCheckedChange = { value -> onUpdate { it.copy(holoShimmer = value) } },
                    )
                    Hairline(Modifier.padding(horizontal = 14.dp))
                    SettingToggle(
                        title = "Prices in pockets",
                        description = "Show each card's value on the page.",
                        checked = settings.showPocketPrices,
                        onCheckedChange = { value -> onUpdate { it.copy(showPocketPrices = value) } },
                    )
                    Hairline(Modifier.padding(horizontal = 14.dp))
                    SettingToggle(
                        title = "Ghost wanted cards",
                        description = "Draw wanted cards as outlines instead of leaving the pocket blank.",
                        checked = settings.showWantedGhosts,
                        onCheckedChange = { value -> onUpdate { it.copy(showWantedGhosts = value) } },
                    )
                    Hairline(Modifier.padding(horizontal = 14.dp))
                    SettingToggle(
                        title = "Abbreviate large values",
                        description = "Show \$1.2k instead of \$1,200.00 in tight spaces.",
                        checked = settings.abbreviateValues,
                        onCheckedChange = { value -> onUpdate { it.copy(abbreviateValues = value) } },
                    )
                }
            }

            item { SectionHeader("New binders", Modifier.padding(top = 4.dp)) }
            item {
                Panel {
                    // The same picker the binder editor uses, so a default page shape can
                    // be any shape a binder can be -- not just one off the preset list.
                    LayoutPicker(
                        layout = settings.defaultLayout,
                        onLayoutChange = { layout -> onUpdate { it.copy(defaultLayout = layout) } },
                    )

                    Spacer(Modifier.height(20.dp))
                    FieldLabel("Default sheet count")
                    Spacer(Modifier.height(10.dp))
                    Stepper(
                        value = settings.defaultSheetCount,
                        onValueChange = { value -> onUpdate { it.copy(defaultSheetCount = value) } },
                        range = 1..60,
                        suffix = if (settings.defaultSheetCount == 1) "sheet" else "sheets",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${settings.defaultLayout.capacity(settings.defaultSheetCount)} pockets " +
                            "across ${settings.defaultLayout.faceCount(settings.defaultSheetCount)} pages.",
                        color = Ink.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item { SectionHeader("Card data") }
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailRow("Catalog", "TCGdex")
                        DetailRow("With artwork", "$withArt of ${snapshot.printings.size}")
                        DetailRow("Live prices", "$priced of ${snapshot.prices.size}")
                    }

                    Spacer(Modifier.height(14.dp))

                    when (val state = sync) {
                        is SyncState.Running -> {
                            Text(
                                text = "Matching your cards against the catalog…",
                                color = Ink.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(10.dp))
                            ProgressTrack(
                                fraction = if (state.total == 0) 0f else {
                                    state.done.toFloat() / state.total
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "${state.done} of ${state.total}",
                                color = Ink.TextTertiary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        is SyncState.Done -> {
                            Text(
                                text = state.message,
                                color = if (state.failed) Ink.Loss else Ink.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            AppOutlineButton(
                                label = "Update again",
                                onClick = { sync = startSync(scope, onSyncCatalog) { sync = it } },
                                modifier = Modifier.fillMaxWidth(),
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
                            Spacer(Modifier.height(12.dp))
                            AppButton(
                                label = "Update card data",
                                onClick = { sync = startSync(scope, onSyncCatalog) { sync = it } },
                                modifier = Modifier.fillMaxWidth(),
                                icon = AppIcons.Cards,
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Collection") }
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailRow("Binders", "${snapshot.binders.size}")
                        DetailRow("Containers", "${snapshot.containers.size}")
                        DetailRow("Cards owned", "${snapshot.copies.size}")
                        DetailRow("Catalog entries", "${snapshot.variants.size}")
                        DetailRow("Pockets", "${snapshot.binders.sumOf { it.capacity }}")
                    }

                    Spacer(Modifier.height(18.dp))

                    if (confirmingReset) {
                        Text(
                            text = "This deletes every binder, box and card in the app and leaves you " +
                                "with an empty collection. There is no undo.",
                            color = Ink.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        AppOutlineButton(
                            label = "Delete everything and start over",
                            onClick = { confirmingReset = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { SectionHeader("Updates") }
            item { UpdatePanel() }

            item { SectionHeader("About") }
            item {
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Read from the generated AppVersion rather than typed here, so
                        // the number on this row is the number the updater compares
                        // against a release tag and cannot drift from it.
                        DetailRow("Pocketful", "v${AppVersion.NAME} (${AppVersion.CODE})")
                        DetailRow("Price data", if (priced > 0) "TCGplayer via TCGdex" else "none yet")
                        DetailRow("Storage", "in memory")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Prices are whatever the catalog last quoted, and only for cards it " +
                            "could match. Changes live for as long as the app is open and are not " +
                            "written to disk yet.",
                        color = Ink.TextTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Ink.TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(14.dp))
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
                result.matched == 0 -> SyncState.Done(
                    "None of your cards could be matched to a catalog entry.",
                    failed = true,
                )
                else -> SyncState.Done(
                    buildString {
                        append("Matched ")
                        append(result.matched)
                        append(" of ")
                        append(result.attempted)
                        append(" cards")
                        if (result.pricesUpdated > 0) {
                            append(" and repriced ")
                            append(result.pricesUpdated)
                        }
                        append(".")
                        if (result.rejected > 0) {
                            append(" ")
                            append(result.rejected)
                            append(
                                if (result.rejected == 1) {
                                    " card matched an entry under a different name and was skipped."
                                } else {
                                    " cards matched entries under different names and were skipped."
                                },
                            )
                        } else if (result.unmatched > 0) {
                            append(" The rest were left untouched.")
                        }
                    },
                    failed = false,
                )
            },
        )
    }
    return SyncState.Running(0, 0)
}
