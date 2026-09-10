package app.pocketful

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.pocketful.domain.Binder
import app.pocketful.domain.BinderId
import app.pocketful.domain.CardBrief
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerId
import app.pocketful.domain.CopyId
import app.pocketful.domain.Finish
import app.pocketful.domain.brief
import app.pocketful.domain.variantBriefs
import app.pocketful.data.CatalogSync
import app.pocketful.data.RemoteSet
import app.pocketful.data.rememberDocumentTransfer
import app.pocketful.data.CatalogDownload
import app.pocketful.data.rememberSaveStorage
import app.pocketful.data.SearchHit
import app.pocketful.state.AutosaveEffect
import app.pocketful.state.LocalAppSettings
import app.pocketful.state.rememberCardLookup
import app.pocketful.state.rememberCollectionSaver
import app.pocketful.state.rememberCollectionTransfer
import app.pocketful.state.rememberCatalogBrowser
import app.pocketful.state.rememberAppBootstrap
import app.pocketful.state.rememberCollectionStore
import app.pocketful.state.rememberTcgDex
import app.pocketful.ui.binder.BinderEditorSheet
import app.pocketful.ui.binder.BinderPageScreen
import app.pocketful.ui.binder.SlotSheet
import app.pocketful.ui.binder.SlotTarget
import app.pocketful.ui.cards.CardsScreen
import app.pocketful.ui.cards.CopySheet
import app.pocketful.ui.collections.AddToContainerSheet
import app.pocketful.ui.collections.CollectionsScreen
import app.pocketful.ui.collections.ContainerEditorSheet
import app.pocketful.ui.collections.ContainerScreen
import app.pocketful.ui.collections.StorageSelection
import app.pocketful.domain.TcgGame
import app.pocketful.ui.home.HomeScreen
import app.pocketful.ui.launch.LoadingScreen
import app.pocketful.ui.nav.Destination
import app.pocketful.ui.nav.IslandNavBar
import app.pocketful.ui.nav.SystemBackHandler
import app.pocketful.ui.search.AddToCollectionSheet
import app.pocketful.ui.search.SearchScreen
import app.pocketful.ui.search.SetBuild
import app.pocketful.ui.search.SetScreen
import app.pocketful.ui.search.sheetsToHold
import app.pocketful.ui.settings.SettingsScreen
import app.pocketful.ui.trade.TradeScreen
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.PocketfulTheme
import app.pocketful.ui.theme.SpineSwatches
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.launch

/**
 * Where the app is.
 *
 * A binder page, a container's contents, the flat card list and the trade sheet are all
 * detail views over whichever tab you were on, not tabs of their own -- which is why they
 * are modelled as alternatives to [Route.Root] rather than as more [Destination]s. The
 * card list and trade view earn that treatment for the same reason the binder page does:
 * you open them to answer one question and then come back, and a tab that you always
 * leave immediately is a tab that should have been a push.
 */
private sealed interface Route {
    data class Root(val destination: Destination) : Route
    data class BinderDetail(val id: BinderId) : Route
    data class ContainerDetail(val id: ContainerId) : Route
    /** One catalog set, whole: its information and its checklist. */
    data class SetDetail(val set: RemoteSet) : Route
    data object AllCards : Route
    data object Trade : Route
}

/** Which editor sheet is open, if any. */
private sealed interface Editor {
    data object NewBinder : Editor
    data class EditBinder(val id: BinderId) : Editor
    data object NewContainer : Editor
    data class EditContainer(val id: ContainerId) : Editor
}

/**
 * The whole app.
 *
 * Navigation is four top-level destinations plus the detail views, and every modal is
 * mounted here rather than inside the screen that triggers it. That is what lets a card
 * tapped in the card list open its binder *and* its pocket in one gesture: the sheet
 * outlives the screen transition underneath it.
 *
 * Multi-selections are hoisted here for a related reason. A live selection has to be able
 * to push the navigation island out of the way and to swallow the back gesture before it
 * leaves the screen, and neither is something a screen can do about itself from the
 * inside without the two bars fighting over the same corner.
 */
@Composable
fun App() {
    val store = rememberCollectionStore()
    val storage = rememberSaveStorage()
    val saver = rememberCollectionSaver(storage)
    // One per app, like the API client: it owns the downloaded catalog file and the stamp
    // saying when the app last bothered to ask whether a new set exists.
    val catalogDownload = remember(storage) { CatalogDownload(storage) }
    val transfer = rememberCollectionTransfer(rememberDocumentTransfer())
    val snapshot = store.snapshot
    val catalog = rememberTcgDex()
    val catalogSync = remember(catalog) { CatalogSync(catalog) }
    // One client for the whole app, so the 218-set index is fetched once rather than once
    // per screen that wants to name a set.
    val lookup = rememberCardLookup(catalog)
    val browser = rememberCatalogBrowser(catalog)
    val scope = rememberCoroutineScope()

    var destination by remember { mutableStateOf(Destination.Home) }
    var openBinderId by remember { mutableStateOf<BinderId?>(null) }
    var openContainerId by remember { mutableStateOf<ContainerId?>(null) }
    // The two list-shaped detail views. Held as flags rather than as a stack: they are
    // reached from a root tab and dismissed back to it, never from each other.
    var showAllCards by remember { mutableStateOf(false) }
    var showTrade by remember { mutableStateOf(false) }
    var landingOrdinal by remember { mutableStateOf<Int?>(null) }
    var slotTarget by remember { mutableStateOf<SlotTarget?>(null) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var openCopyId by remember { mutableStateOf<CopyId?>(null) }
    var addingToContainer by remember { mutableStateOf<ContainerId?>(null) }

    // Search: the card waiting to be recorded, and whether one is still being fetched.
    var addingCard by remember { mutableStateOf<CardBrief?>(null) }
    var importingCard by remember { mutableStateOf(false) }
    // Which binder a set is currently being turned into, if either. Held here rather than
    // in the set screen because the screen goes away when the binder it made opens.
    var buildingBinder by remember { mutableStateOf<SetBuild?>(null) }
    // The set being looked at, as a route rather than a sheet. A set is a place -- it has
    // information, a checklist and an action of its own -- and a summary card floating
    // over the tab you found it on could carry none of that without becoming a screen
    // with a scrim behind it.
    var openSet by remember { mutableStateOf<RemoteSet?>(null) }
    // How far into the catalog the search tab has been drilled. Hoisted for the same
    // reason the detail routes are -- back has to unwind it, and a screen cannot take the
    // gesture off the tab it is sitting in.
    var searchGame by remember { mutableStateOf<TcgGame?>(null) }

    // Live multi-selections. Cards and storage are separate because they are acted on by
    // different screens with different verbs, and one bag holding both would need
    // unpacking at every use.
    var cardSelection by remember { mutableStateOf(emptySet<CopyId>()) }
    var storageSelection by remember { mutableStateOf(StorageSelection.EMPTY) }

    // Read once so the route below can name it without a null assertion: the value is a
    // snapshot-backed var, which never smart-casts however obviously non-null it is.
    val browsingSet = openSet

    val route: Route = openBinderId
        ?.takeIf { snapshot.binder(it) != null }
        ?.let { Route.BinderDetail(it) }
        ?: openContainerId
            ?.takeIf { snapshot.container(it) != null }
            ?.let { Route.ContainerDetail(it) }
        ?: when {
            showTrade -> Route.Trade
            showAllCards -> Route.AllCards
            // Below the storage routes on purpose: a binder opened from a set's page
            // leaves that page underneath it, so backing out of the binder returns to the
            // set rather than all the way to the search tab.
            browsingSet != null -> Route.SetDetail(browsingSet)
            else -> Route.Root(destination)
        }

    /** A selection belongs to the screen that made it, so leaving that screen ends it. */
    fun clearSelections() {
        cardSelection = emptySet()
        storageSelection = StorageSelection.EMPTY
    }

    /**
     * Leaves every detail view, so opening one is never layered on top of another.
     *
     * Deliberately does not close an open set. The set page is the one detail view that
     * things are launched *from* -- building a binder for a set is the point of it -- and
     * dropping it as the binder opens would mean the way back from that binder was the
     * search tab rather than the set you were working on.
     */
    fun closeDetails() {
        openBinderId = null
        openContainerId = null
        showAllCards = false
        showTrade = false
        clearSelections()
    }

    val openBinder: (Binder) -> Unit = { binder ->
        landingOrdinal = null
        closeDetails()
        openBinderId = binder.id
    }

    val openContainer: (Container) -> Unit = { container ->
        closeDetails()
        openContainerId = container.id
    }

    val openAllCards: () -> Unit = {
        closeDetails()
        showAllCards = true
    }

    val openTrade: () -> Unit = {
        closeDetails()
        showTrade = true
    }

    /** Jump from anywhere to one specific pocket, page and sheet together. */
    val revealPocket: (BinderId, Int) -> Unit = { binderId, ordinal ->
        openCopyId = null
        openSet = null
        closeDetails()
        destination = Destination.Collections
        landingOrdinal = ordinal
        openBinderId = binderId
        slotTarget = SlotTarget(binderId, ordinal)
    }

    /**
     * Builds a binder for a set found in Search, with a pocket held open for every card
     * in it, and opens it.
     *
     * Filled, not merely sized -- which is the reverse of what this used to do. The old
     * argument was that pre-marking a hundred pockets as wanted decided on the user's
     * behalf which printings they were chasing. In practice the alternative decided
     * something worse: that they would type a hundred card names in by hand. A set is a
     * published checklist, and a want list *is* that checklist minus what you already
     * have, so the honest starting point is the whole set with everything still to find.
     * Ticking cards off is one gesture per handful from inside the binder.
     */
    /**
     * Turns a set into a binder, in one of its two shapes, and opens it.
     *
     * Filled, not merely sized -- which is the reverse of what this used to do. The old
     * argument was that pre-marking a hundred pockets as wanted decided on the user's
     * behalf which printings they were chasing. In practice the alternative decided
     * something worse: that they would type a hundred card names in by hand. A set is a
     * published checklist, and a want list *is* that checklist minus what you already
     * have, so the honest starting point is the whole set with everything still to find.
     * Ticking cards off is one gesture per handful from inside the binder.
     *
     * Both shapes are asynchronous, and unavoidably so: a set listing says which cards
     * exist but not which press runs each was printed in, and neither binder can be filled
     * without that. The set screen starts fetching it on the way in, so the usual case is
     * that this returns immediately; the wait is paid once per set per session.
     */
    val buildBinderForSet: (RemoteSet, List<SearchHit>, SetBuild) -> Unit = { set, cards, shape ->
        buildingBinder = shape
        scope.launch {
            val master = shape == SetBuild.MasterSet
            val pockets = if (master) browser.masterSetOf(set.id, cards)
            else browser.checklistOf(set.id, cards)
            buildingBinder = null
            // Empty only when a master set could not be worked out at all. The screen has
            // the reason and says so; there is nothing worth opening here.
            if (pockets.isEmpty()) return@launch

            val layout = store.settings.defaultLayout
            val count = pockets.size
            val id = store.createSetBinder(
                name = if (master) "${set.name} master set" else set.name,
                // A master set is counted in pockets rather than in cards, because it holds
                // more pockets than the set has cards and a subtitle claiming 358 cards for
                // a 201-card set reads as a bug in the checklist.
                subtitle = "$count ${if (master) "pockets" else "cards"} · ${set.id.uppercase()}",
                layout = layout,
                sheetCount = sheetsToHold(count, layout),
                spineColor = SpineSwatches.random().value,
                sourceSetId = set.id,
                pockets = pockets,
            )
            landingOrdinal = null
            closeDetails()
            openBinderId = id
        }
    }

    /**
     * Fetches a card found in the catalog and opens the sheet that records owning one.
     *
     * Hoisted here because it is launched from two screens now -- the search tab and a
     * set's checklist -- and because it is the only thing in the flow that can fail or
     * take time. Run from inside a screen, a card still being fetched when that screen
     * goes leaves a dangling job writing into a composition that no longer exists.
     */
    val importRemoteCard: (SearchHit) -> Unit = { hit ->
        importingCard = true
        scope.launch {
            val card = lookup.fetch(hit).getOrNull()
            importingCard = false
            if (card != null) {
                val variantId = store.importRemoteCard(
                    card,
                    Finish.NON_HOLO,
                    published = catalog.publishedCard(card.id),
                )
                addingCard = store.snapshot.brief(variantId)
            }
        }
    }

    // One image loader for the whole app, wired to the same Ktor stack the catalog uses.
    // Registered here rather than left to service discovery so the network fetcher is a
    // stated dependency of the app instead of something that happens to be on the
    // classpath -- if art stops loading, this is the line to look at.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    // Bring the collection up to date with the live catalog once per launch, behind the
    // loading screen.
    //
    // This is deliberately automatic rather than a button someone has to find. Artwork and
    // prices are the two things the app cannot know on its own, and a collection app that
    // opens showing coloured rectangles and last year's prices until you visit Settings is
    // one that looks broken. It fails silently and leaves every card it cannot match
    // exactly as it was -- so the worst case is the app you had before. Settings keeps the
    // manual control for refreshing on demand.
    //
    // What changed is where it happens. Run in the background against a screen that was
    // already up, the same work rewrote prices and dropped artwork in under someone who
    // was reading them; [AppBootstrap] gives it somewhere to happen instead, and caps
    // itself so a dead network delays the launch rather than preventing it.
    //
    // The obvious follow-up is persistence: with the collection still held in memory this
    // has to re-run every launch, where it should be a cache check.
    val bootstrap = rememberAppBootstrap()
    val imageContext = LocalPlatformContext.current
    LaunchedEffect(Unit) {
        val result = bootstrap.run(
            store = store,
            saver = saver,
            catalogSync = catalogSync,
            browser = browser,
            api = catalog,
            catalogDownload = catalogDownload,
            imageLoader = SingletonImageLoader.get(imageContext),
            imageContext = imageContext,
        )
        if (result != null) store.applyCatalogSync(result)
    }

    // Writes every change back, from the moment there is something on disk worth not
    // overwriting. Mounted here rather than inside the store because saving is something
    // done *to* a collection by the app it lives in, and a store that saved itself would
    // need to know what a file was.
    AutosaveEffect(store, saver)

    PocketfulTheme {
        CompositionLocalProvider(LocalAppSettings provides store.settings) {
            Box(Modifier.fillMaxSize().background(Ink.Background)) {
                // One handler rather than one per layer: back always unwinds the topmost
                // thing on screen, and only falls through to the system once the app is
                // sitting on its home tab with nothing open.
                val sheetOpen = slotTarget != null || openCopyId != null || editor != null ||
                    addingToContainer != null || addingCard != null
                val selecting = cardSelection.isNotEmpty() || storageSelection.isActive
                // Drilling into a game is navigation the user did on purpose, so backing
                // out of it returns to the list of games rather than to Home.
                val browsingCatalog = destination == Destination.Search && searchGame != null
                SystemBackHandler(
                    // Off until the launch screen has gone: nothing under it is reachable
                    // yet, so a back press there should leave the app rather than quietly
                    // unwind a navigation stack the user cannot see.
                    enabled = bootstrap.ready &&
                        (sheetOpen || selecting || browsingCatalog || route !is Route.Root ||
                            destination != Destination.Home),
                ) {
                    when {
                        addingToContainer != null -> addingToContainer = null
                        slotTarget != null -> slotTarget = null
                        openCopyId != null -> openCopyId = null
                        addingCard != null -> addingCard = null
                        editor != null -> editor = null
                        // Ahead of the route, so backing out of a selection leaves you on
                        // the screen you were selecting on rather than two steps away.
                        selecting -> clearSelections()
                        route is Route.BinderDetail -> openBinderId = null
                        route is Route.ContainerDetail -> openContainerId = null
                        route is Route.Trade -> showTrade = false
                        route is Route.AllCards -> showAllCards = false
                        route is Route.SetDetail -> openSet = null
                        // Behind the routes: a binder opened from a set's page leaves that
                        // page underneath it, and back should close the binder you are
                        // looking at before it unwinds the browse you are not.
                        browsingCatalog -> searchGame = null
                        else -> destination = Destination.Home
                    }
                }

                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val enteringDetail = targetState !is Route.Root && initialState is Route.Root
                        val leavingDetail = targetState is Route.Root && initialState !is Route.Root
                        when {
                            enteringDetail ->
                                (slideInHorizontally { it / 4 } + fadeIn(tween(220))) togetherWith
                                    (fadeOut(tween(160)) + scaleOut(targetScale = 0.96f))

                            leavingDetail ->
                                (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith
                                    (slideOutHorizontally { it / 4 } + fadeOut(tween(200)))

                            // Between tabs: no direction to imply, so do not imply one.
                            else -> fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                        } using SizeTransform(clip = false)
                    },
                    label = "route",
                ) { current ->
                    when (current) {
                        is Route.BinderDetail -> {
                            val binder = snapshot.binder(current.id)
                            if (binder != null) {
                                BinderPageScreen(
                                    binder = binder,
                                    snapshot = snapshot,
                                    initialOrdinal = landingOrdinal,
                                    onBack = { openBinderId = null },
                                    onSlotClick = { ordinal -> slotTarget = SlotTarget(binder.id, ordinal) },
                                    onEditBinder = { editor = Editor.EditBinder(binder.id) },
                                    onMarkOwned = { ordinals -> store.markSlotsOwned(binder.id, ordinals) },
                                    onMarkWanted = { ordinals -> store.markSlotsWanted(binder.id, ordinals) },
                                    onSetForTrade = { ordinals, forTrade ->
                                        store.setSlotsForTrade(binder.id, ordinals, forTrade)
                                    },
                                    onClearSlots = { ordinals -> store.clearSlots(binder.id, ordinals) },
                                )
                            }
                        }

                        is Route.ContainerDetail -> {
                            val container = snapshot.container(current.id)
                            if (container != null) {
                                ContainerScreen(
                                    container = container,
                                    snapshot = snapshot,
                                    selection = cardSelection,
                                    onSelectionChange = { cardSelection = it },
                                    onBack = { openContainerId = null },
                                    onEdit = { editor = Editor.EditContainer(container.id) },
                                    onAddCards = { addingToContainer = container.id },
                                    onOpenCopy = { row -> openCopyId = row.copy.id },
                                    onRemoveSelected = { ids ->
                                        ids.forEach { store.removeFromContainer(container.id, it) }
                                    },
                                    onDeleteSelected = { ids -> store.deleteCopies(ids) },
                                )
                            }
                        }

                        is Route.SetDetail -> SetScreen(
                            set = current.set,
                            browser = browser,
                            snapshot = snapshot,
                            defaultLayout = store.settings.defaultLayout,
                            existingBinder = snapshot.binders
                                .firstOrNull { it.sourceSetId == current.set.id },
                            importing = importingCard,
                            building = buildingBinder,
                            onBack = { openSet = null },
                            onCreateBinder = { cards ->
                                buildBinderForSet(current.set, cards, SetBuild.SetBinder)
                            },
                            onCreateMasterSet = { cards ->
                                buildBinderForSet(current.set, cards, SetBuild.MasterSet)
                            },
                            onOpenBinder = openBinder,
                            onAddCard = { hit -> importRemoteCard(hit) },
                        )

                        Route.AllCards -> CardsScreen(
                            snapshot = snapshot,
                            selection = cardSelection,
                            onSelectionChange = { cardSelection = it },
                            onBack = { showAllCards = false },
                            onOpenCopy = { row -> openCopyId = row.copy.id },
                            onOpenWant = { row -> revealPocket(row.binderId, row.ordinal) },
                            onSetForTrade = { ids, forTrade -> store.setForTrade(ids, forTrade) },
                            onDeleteSelected = { ids -> store.deleteCopies(ids) },
                        )

                        Route.Trade -> TradeScreen(
                            snapshot = snapshot,
                            onBack = { showTrade = false },
                            onOpenCopy = { row -> openCopyId = row.copy.id },
                            onOpenWant = { row -> revealPocket(row.binderId, row.ordinal) },
                        )

                        is Route.Root -> when (current.destination) {
                            Destination.Home -> HomeScreen(
                                snapshot = snapshot,
                                onOpenBinder = openBinder,
                                onOpenContainer = openContainer,
                                onCreateBinder = { editor = Editor.NewBinder },
                                onCreateContainer = { editor = Editor.NewContainer },
                                onOpenCards = openAllCards,
                                onOpenTrade = openTrade,
                                onOpenCopy = { row -> openCopyId = row.copy.id },
                                onOpenWant = { row -> revealPocket(row.binderId, row.ordinal) },
                            )

                            Destination.Collections -> CollectionsScreen(
                                snapshot = snapshot,
                                selection = storageSelection,
                                onSelectionChange = { storageSelection = it },
                                onOpenBinder = openBinder,
                                onOpenContainer = openContainer,
                                onCreateBinder = { editor = Editor.NewBinder },
                                onCreateContainer = { editor = Editor.NewContainer },
                                onOpenCards = openAllCards,
                                onOpenTrade = openTrade,
                                onDeleteSelected = { selection, deleteCards ->
                                    selection.binders.forEach { store.deleteBinder(it, deleteCards) }
                                    selection.containers.forEach { store.deleteContainer(it, deleteCards) }
                                },
                            )

                            Destination.Search -> SearchScreen(
                                snapshot = snapshot,
                                lookup = lookup,
                                browser = browser,
                                importing = importingCard,
                                game = searchGame,
                                onGameChange = { searchGame = it },
                                onOpenSet = { openSet = it },
                                onAddLocalCard = { brief -> addingCard = brief },
                                onAddRemoteCard = { hit -> importRemoteCard(hit) },
                            )

                            Destination.Settings -> SettingsScreen(
                                settings = store.settings,
                                snapshot = snapshot,
                                onUpdate = { transform -> store.updateSettings(transform) },
                                onSyncCatalog = { onProgress ->
                                    catalogSync
                                        .run(store.snapshot, onProgress = onProgress)
                                        .also { store.applyCatalogSync(it) }
                                },
                                transfer = transfer,
                                onExport = {
                                    scope.launch { transfer.export(store.snapshot, store.settings) }
                                },
                                onImportChoose = { scope.launch { transfer.choose() } },
                                onImportApply = {
                                    // The same unwinding a reset does, and for the same
                                    // reason: the binder or box on screen behind Settings
                                    // may not exist in the collection that is arriving.
                                    closeDetails()
                                    openSet = null
                                    slotTarget = null
                                    transfer.apply(store)
                                },
                                onReset = {
                                    closeDetails()
                                    openSet = null
                                    slotTarget = null
                                    store.reset()
                                    // The saved files as well as the live collection.
                                    // Autosave would eventually write the empty store
                                    // over them anyway; deleting them says it now, and
                                    // drops the cached catalog with it so the next launch
                                    // starts genuinely fresh rather than merely blank.
                                    scope.launch { saver.clear() }
                                },
                            )
                        }
                    }
                }

                // The detail screens bring their own island, and a live storage selection
                // brings one too -- so the navigation bar steps aside for both rather than
                // stacking under a bar that has taken its place.
                AnimatedVisibility(
                    visible = route is Route.Root && !storageSelection.isActive,
                    enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(140)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    IslandNavBar(
                        current = destination,
                        onSelect = {
                            clearSelections()
                            destination = it
                        },
                    )
                }

                SlotSheet(
                    target = slotTarget,
                    store = store,
                    catalog = catalog,
                    onDismiss = { slotTarget = null },
                )

                CopySheet(
                    copyId = openCopyId,
                    snapshot = snapshot,
                    onDismiss = { openCopyId = null },
                    onShowInBinder = revealPocket,
                    onSetForTrade = { copyId, forTrade -> store.setForTrade(copyId, forTrade) },
                    onDelete = { copyId ->
                        store.deleteCopy(copyId)
                        openCopyId = null
                    },
                )

                // Recomputed only when the card being added changes: it walks the whole
                // variant table, and the sheet is open across every recomposition the
                // screen behind it makes.
                val addingVariants = remember(addingCard, snapshot.variants) {
                    addingCard?.let { snapshot.variantBriefs(it.variantId) }.orEmpty()
                }
                AddToCollectionSheet(
                    brief = addingCard,
                    variants = addingVariants,
                    containers = snapshot.containers,
                    onDismiss = { addingCard = null },
                    onConfirm = { brief, condition, paid, containerId ->
                        store.addCopy(
                            variantId = brief.variantId,
                            condition = condition,
                            acquiredPrice = paid,
                            container = containerId,
                        )
                        addingCard = null
                    },
                )

                val editingBinder = (editor as? Editor.EditBinder)?.let { snapshot.binder(it.id) }
                BinderEditorSheet(
                    visible = editor is Editor.NewBinder || editor is Editor.EditBinder,
                    existing = editingBinder,
                    settings = store.settings,
                    cardsInside = editingBinder?.let { store.cardsInside(it.id) } ?: 0,
                    onDismiss = { editor = null },
                    onSave = { draft ->
                        if (editingBinder == null) {
                            val id = store.createBinder(
                                name = draft.name,
                                subtitle = draft.subtitle,
                                layout = draft.layout,
                                sheetCount = draft.sheetCount,
                                spineColor = draft.spineColor,
                            )
                            editor = null
                            landingOrdinal = null
                            closeDetails()
                            openBinderId = id
                        } else {
                            store.updateBinder(
                                id = editingBinder.id,
                                name = draft.name,
                                subtitle = draft.subtitle,
                                layout = draft.layout,
                                sheetCount = draft.sheetCount,
                                spineColor = draft.spineColor,
                            )
                            editor = null
                        }
                    },
                    onDelete = editingBinder?.let { binder ->
                        { deleteCards ->
                            editor = null
                            openBinderId = null
                            slotTarget = null
                            store.deleteBinder(binder.id, deleteCards)
                        }
                    },
                )

                val editingContainer = (editor as? Editor.EditContainer)?.let { snapshot.container(it.id) }
                ContainerEditorSheet(
                    visible = editor is Editor.NewContainer || editor is Editor.EditContainer,
                    existing = editingContainer,
                    onDismiss = { editor = null },
                    onSave = { draft ->
                        if (editingContainer == null) {
                            val id = store.createContainer(
                                name = draft.name,
                                subtitle = draft.subtitle,
                                kind = draft.kind,
                                color = draft.color,
                            )
                            editor = null
                            closeDetails()
                            openContainerId = id
                        } else {
                            store.updateContainer(
                                id = editingContainer.id,
                                name = draft.name,
                                subtitle = draft.subtitle,
                                kind = draft.kind,
                                color = draft.color,
                            )
                            editor = null
                        }
                    },
                    onDelete = editingContainer?.let { container ->
                        { deleteCards ->
                            editor = null
                            openContainerId = null
                            store.deleteContainer(container.id, deleteCards)
                        }
                    },
                )

                AddToContainerSheet(
                    visible = addingToContainer != null,
                    container = addingToContainer?.let { snapshot.container(it) },
                    snapshot = snapshot,
                    onDismiss = { addingToContainer = null },
                    onConfirm = { copyIds ->
                        addingToContainer?.let { containerId ->
                            store.placeInContainer(containerId, copyIds)
                        }
                        addingToContainer = null
                    },
                )

                // The launch screen, over the app rather than instead of it.
                //
                // Drawn last so it covers everything, and mounted inside the same Box so
                // the app underneath composes and measures while the network work is
                // still running -- by the time this fades, Home is not being built, it is
                // being revealed. It shares the background colour with both the window
                // behind it and the screen under it, so the only thing the fade changes
                // is which type is on screen.
                AnimatedVisibility(
                    visible = !bootstrap.ready,
                    // No enter transition: this is the first thing composed, and a launch
                    // screen that fades *in* over the window background it already
                    // matches is a flicker with extra steps.
                    enter = fadeIn(tween(0)),
                    exit = fadeOut(tween(420)),
                ) {
                    LoadingScreen(
                        progress = bootstrap.progress,
                        status = bootstrap.status,
                        summary = bootstrap.summary,
                    )
                }
            }
        }
    }
}
