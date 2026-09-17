package app.pocketful

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import app.pocketful.data.CardImport
import app.pocketful.data.CatalogDownload
import app.pocketful.data.CatalogSet
import app.pocketful.data.CatalogSync
import app.pocketful.data.PriceDownload
import app.pocketful.data.PriceHistory
import app.pocketful.data.SearchHit
import app.pocketful.data.WidgetSummary
import app.pocketful.data.nowEpochSeconds
import app.pocketful.data.rememberDocumentTransfer
import app.pocketful.data.rememberHomeWidget
import app.pocketful.data.rememberSaveStorage
import app.pocketful.domain.Binder
import app.pocketful.domain.BinderId
import app.pocketful.domain.CardBrief
import app.pocketful.domain.Container
import app.pocketful.domain.ContainerId
import app.pocketful.domain.CopyId
import app.pocketful.domain.Finish
import app.pocketful.domain.TcgGame
import app.pocketful.domain.brief
import app.pocketful.domain.variantBriefs
import app.pocketful.state.AutosaveEffect
import app.pocketful.state.LocalAppSettings
import app.pocketful.state.rememberAppBootstrap
import app.pocketful.state.rememberCardCatalog
import app.pocketful.state.rememberCardLookup
import app.pocketful.state.rememberCatalogBrowser
import app.pocketful.state.rememberCollectionSaver
import app.pocketful.state.rememberCollectionStore
import app.pocketful.state.rememberCollectionTransfer
import app.pocketful.ui.add.AddSheet
import app.pocketful.ui.binder.BinderEditorSheet
import app.pocketful.ui.binder.BinderPageScreen
import app.pocketful.ui.binder.SlotSheet
import app.pocketful.ui.binder.SlotTarget
import app.pocketful.ui.cards.CopySheet
import app.pocketful.ui.collections.AddToContainerSheet
import app.pocketful.ui.collections.CardLayout
import app.pocketful.ui.collections.CollectionScreen
import app.pocketful.ui.collections.CollectionView
import app.pocketful.ui.collections.ContainerEditorSheet
import app.pocketful.ui.collections.ContainerScreen
import app.pocketful.ui.collections.StorageSelection
import app.pocketful.ui.components.changeText
import app.pocketful.ui.components.percentText
import app.pocketful.ui.home.HomeScreen
import app.pocketful.ui.launch.LoadingScreen
import app.pocketful.ui.nav.Destination
import app.pocketful.ui.nav.IslandNavBar
import app.pocketful.ui.nav.SystemBackHandler
import app.pocketful.ui.search.AddToCollectionSheet
import app.pocketful.ui.search.BrowseScreen
import app.pocketful.ui.search.SetBuild
import app.pocketful.ui.search.SetScreen
import app.pocketful.ui.search.UnifiedSearch
import app.pocketful.ui.search.sheetsToHold
import app.pocketful.ui.settings.SettingsScreen
import app.pocketful.ui.theme.Ink
import app.pocketful.ui.theme.Motion
import app.pocketful.ui.theme.PocketfulTheme
import app.pocketful.ui.theme.SpineSwatches
import app.pocketful.ui.trade.TradeScreen
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
 * Four destinations and four pushes. A binder page, a container's contents, a set's page and
 * Settings are all detail views over whichever tab you were on rather than tabs of their own
 * -- which is why they are modelled as alternatives to [Route.Root] rather than as more
 * [Destination]s. Settings earns that treatment for exactly the reason the binder page does:
 * you open it to do one thing and then leave, and a tab you always leave immediately is a tab
 * that should have been a push.
 */
private sealed interface Route {
    data class Root(val destination: Destination) : Route
    data class BinderDetail(val id: BinderId) : Route
    data class ContainerDetail(val id: ContainerId) : Route
    /** One catalog set, whole: its information and its checklist. */
    data class SetDetail(val set: CatalogSet) : Route
    data object Settings : Route
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
 * Navigation is four top-level destinations plus the detail views, and every modal is mounted
 * here rather than inside the screen that triggers it. That is what lets a card tapped in a
 * search result open its binder *and* its pocket in one gesture: the sheet outlives the
 * screen transition underneath it.
 *
 * Multi-selections are hoisted here for a related reason. A live selection has to be able to
 * push the navigation dock out of the way and to swallow the back gesture before it leaves
 * the screen, and neither is something a screen can do about itself from the inside without
 * the two bars fighting over the same corner.
 */
@Composable
fun App() {
    val store = rememberCollectionStore()
    val storage = rememberSaveStorage()
    val saver = rememberCollectionSaver(storage)
    // One per app, like the API client: it owns the downloaded catalog file and the stamp
    // saying when the app last bothered to ask whether a new set exists.
    val catalogDownload = remember(storage) { CatalogDownload(storage) }
    val priceDownload = remember(storage) { PriceDownload(storage) }
    val catalog = rememberCardCatalog()
    // Price history is fetched a set at a time as screens ask for it, and kept on disk.
    val priceHistory = remember(storage, catalog) { PriceHistory(storage, baseUrl = { catalog.publicUrl }) }
    val transfer = rememberCollectionTransfer(rememberDocumentTransfer())
    val snapshot = store.snapshot
    val catalogSync = remember(catalog) { CatalogSync(catalog) }
    // One client for the whole app, so the set index is fetched once rather than once per
    // screen that wants to name a set.
    val lookup = rememberCardLookup(catalog)
    val browser = rememberCatalogBrowser(catalog)
    val scope = rememberCoroutineScope()

    var destination by remember { mutableStateOf(Destination.Home) }
    var openBinderId by remember { mutableStateOf<BinderId?>(null) }
    var openContainerId by remember { mutableStateOf<ContainerId?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var landingOrdinal by remember { mutableStateOf<Int?>(null) }
    var slotTarget by remember { mutableStateOf<SlotTarget?>(null) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    var openCopyId by remember { mutableStateOf<CopyId?>(null) }
    var addingToContainer by remember { mutableStateOf<ContainerId?>(null) }

    // The two app-wide overlays. Neither is a place you navigate to -- they open over
    // whatever you were doing and hand it straight back -- so neither is a route.
    var searchVisible by remember { mutableStateOf(false) }
    var addVisible by remember { mutableStateOf(false) }

    // Which slice of the collection is showing, hoisted so Home can link into a specific one:
    // tapping "312 cards" should land on the cards, not on whichever view was last open.
    var collectionView by remember { mutableStateOf(CollectionView.Binders) }
    var cardLayout by remember { mutableStateOf(CardLayout.Grid) }

    // Search: the card waiting to be recorded, and whether one is still being fetched.
    var addingCard by remember { mutableStateOf<CardBrief?>(null) }
    var importingCard by remember { mutableStateOf(false) }
    // Which binder a set is currently being turned into, if either. Held here rather than in
    // the set screen because the screen goes away when the binder it made opens.
    var buildingBinder by remember { mutableStateOf<SetBuild?>(null) }
    // The set being looked at, as a route rather than a sheet. A set is a place -- it has
    // information, a checklist and an action of its own -- and a summary card floating over
    // the tab you found it on could carry none of that without becoming a screen with a scrim
    // behind it.
    var openSet by remember { mutableStateOf<CatalogSet?>(null) }
    // How far into the catalog Browse has been drilled. Hoisted for the same reason the detail
    // routes are -- back has to unwind it, and a screen cannot take the gesture off the tab it
    // is sitting in.
    var browseGame by remember { mutableStateOf<TcgGame?>(null) }

    // Live multi-selections. Cards and storage are separate because they are acted on with
    // different verbs, and one bag holding both would need unpacking at every use.
    var cardSelection by remember { mutableStateOf(emptySet<CopyId>()) }
    var storageSelection by remember { mutableStateOf(StorageSelection.EMPTY) }

    // Read once so the route below can name it without a null assertion: the value is a
    // snapshot-backed var, which never smart-casts however obviously non-null it is.
    val browsingSet = openSet

    val route: Route = when {
        showSettings -> Route.Settings
        else -> openBinderId
            ?.takeIf { snapshot.binder(it) != null }
            ?.let { Route.BinderDetail(it) }
            ?: openContainerId
                ?.takeIf { snapshot.container(it) != null }
                ?.let { Route.ContainerDetail(it) }
            // Below the storage routes on purpose: a binder opened from a set's page leaves
            // that page underneath it, so backing out of the binder returns to the set rather
            // than all the way to Browse.
            ?: browsingSet?.let { Route.SetDetail(it) }
            ?: Route.Root(destination)
    }

    /** A selection belongs to the screen that made it, so leaving that screen ends it. */
    fun clearSelections() {
        cardSelection = emptySet()
        storageSelection = StorageSelection.EMPTY
    }

    /**
     * Leaves every detail view, so opening one is never layered on top of another.
     *
     * Deliberately does not close an open set. The set page is the one detail view that things
     * are launched *from* -- building a binder for a set is the point of it -- and dropping it
     * as the binder opens would mean the way back from that binder was Browse rather than the
     * set you were working on.
     */
    fun closeDetails() {
        openBinderId = null
        openContainerId = null
        showSettings = false
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

    /** Into the collection, on a named view. The link Home's metric cells follow. */
    val openCollection: (CollectionView) -> Unit = { which ->
        closeDetails()
        openSet = null
        collectionView = which
        destination = Destination.Collection
    }

    /** Jump from anywhere to one specific pocket, page and sheet together. */
    val revealPocket: (BinderId, Int) -> Unit = { binderId, ordinal ->
        openCopyId = null
        openSet = null
        searchVisible = false
        closeDetails()
        destination = Destination.Collection
        landingOrdinal = ordinal
        openBinderId = binderId
        slotTarget = SlotTarget(binderId, ordinal)
    }

    /**
     * Turns a set into a binder, in one of its two shapes, and opens it.
     *
     * Filled, not merely sized. A set is a published checklist, and a want list *is* that
     * checklist minus what you already have, so the honest starting point is the whole set
     * with everything still to find. Ticking cards off is one gesture per handful from inside
     * the binder.
     *
     * Both shapes are asynchronous, and unavoidably so: a set listing says which cards exist
     * but not which press runs each was printed in, and neither binder can be filled without
     * that. The set screen starts fetching it on the way in, so the usual case is that this
     * returns immediately; the wait is paid once per set per session.
     */
    val buildBinderForSet: (CatalogSet, List<SearchHit>, SetBuild) -> Unit = { set, cards, shape ->
        buildingBinder = shape
        scope.launch {
            val master = shape == SetBuild.MasterSet
            val pockets = if (master) browser.masterSetOf(set.id, cards)
            else browser.checklistOf(set.id, cards)
            buildingBinder = null
            // Empty only when a master set could not be worked out at all. The screen has the
            // reason and says so; there is nothing worth opening here.
            if (pockets.isEmpty()) return@launch

            val (rows, variantIds) = CardImport.rowsForPockets(catalog, pockets)
            val layout = store.settings.defaultLayout
            val count = variantIds.size
            val id = store.createSetBinder(
                name = if (master) "${set.name} master set" else set.name,
                // A master set is counted in pockets rather than in cards, because it holds
                // more pockets than the set has cards and a subtitle claiming 358 cards for a
                // 201-card set reads as a bug in the checklist.
                subtitle = "$count ${if (master) "pockets" else "cards"} · ${set.code.uppercase()}",
                layout = layout,
                sheetCount = sheetsToHold(count, layout),
                spineColor = SpineSwatches.random().value,
                sourceSetId = set.id,
                rows = rows,
                variantIds = variantIds,
            )
            landingOrdinal = null
            closeDetails()
            openBinderId = id
        }
    }

    /**
     * Prices whatever was just imported from the published file, yesterday's figure included.
     * An import prices from the card document, which carries today's quote only, so without
     * this a freshly added card could not say which way it had moved until the next launch.
     */
    fun repricePublished() {
        val prices = catalogSync.priceFromPublished(store.snapshot, nowEpochSeconds())
        if (prices.isNotEmpty()) {
            store.applyCatalogSync(CatalogSync.Result(matched = 0, unmatched = 0, prices = prices))
        }
    }

    /**
     * Fetches a card found in the catalog and opens the sheet that records owning one.
     *
     * Hoisted here because it is launched from three places now -- a set's checklist, the
     * global search and the add sheet -- and because it is the only thing in the flow that can
     * fail or take time. Run from inside a screen, a card still being fetched when that screen
     * goes leaves a dangling job writing into a composition that no longer exists.
     */
    val importRemoteCard: (SearchHit) -> Unit = { hit ->
        importingCard = true
        scope.launch {
            val card = lookup.fetch(hit).getOrNull()
            importingCard = false
            if (card != null) {
                val variantId = store.importCatalogRows(
                    CardImport.rowsFor(card, catalog),
                    CardImport.preferredVariant(card, Finish.NON_HOLO, catalog),
                )
                repricePublished()
                addingCard = variantId?.let { store.snapshot.brief(it) }
            }
        }
    }

    /**
     * Where an add lands, if that place still exists. A box deleted since it was chosen falls
     * back to unfiled rather than filing cards into nothing.
     */
    fun addingTo(): ContainerId? = store.settings.addingTo?.takeIf { store.snapshot.container(it) != null }

    // One tap on a result: a near-mint copy of the plainest printing, straight into wherever
    // adding is pointed. The count on the row going up is the confirmation.
    val quickAddLocal: (CardBrief) -> Unit = { brief ->
        store.addCopy(variantId = brief.variantId, container = addingTo())
    }
    val quickAddRemote: (SearchHit) -> Unit = { hit ->
        importingCard = true
        scope.launch {
            val card = lookup.fetch(hit).getOrNull()
            importingCard = false
            if (card != null) {
                val variantId = store.importCatalogRows(
                    CardImport.rowsFor(card, catalog),
                    CardImport.preferredVariant(card, Finish.NON_HOLO, catalog),
                )
                repricePublished()
                if (variantId != null) store.addCopy(variantId = variantId, container = addingTo())
            }
        }
    }

    // One image loader for the whole app, wired to the same Ktor stack the catalog uses.
    // Registered here rather than left to service discovery so the network fetcher is a stated
    // dependency of the app instead of something that happens to be on the classpath -- if art
    // stops loading, this is the line to look at.
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
    // opens showing coloured rectangles and last year's prices until you visit Settings is one
    // that looks broken. It fails silently and leaves every card it cannot match exactly as it
    // was -- so the worst case is the app you had before. Settings keeps the manual control
    // for refreshing on demand.
    val bootstrap = rememberAppBootstrap()
    val imageContext = LocalPlatformContext.current
    LaunchedEffect(Unit) {
        val result = bootstrap.run(
            store = store,
            saver = saver,
            catalogSync = catalogSync,
            browser = browser,
            catalog = catalog,
            catalogDownload = catalogDownload,
            priceDownload = priceDownload,
            imageLoader = SingletonImageLoader.get(imageContext),
            imageContext = imageContext,
        )
        if (result != null) store.applyCatalogSync(result)
    }

    // Writes every change back, from the moment there is something on disk worth not
    // overwriting. Mounted here rather than inside the store because saving is something done
    // *to* a collection by the app it lives in, and a store that saved itself would need to
    // know what a file was.
    AutosaveEffect(store, saver)

    // The home-screen widget, kept in step with Home's own total. Only once the launch has
    // restored the collection, so a cold start never tells the widget the portfolio is empty.
    val homeWidget = rememberHomeWidget()
    val widgetSummary = remember(snapshot, bootstrap.ready) {
        if (!bootstrap.ready) null else widgetSummaryOf(snapshot)
    }
    LaunchedEffect(widgetSummary) { widgetSummary?.let(homeWidget::publish) }

    PocketfulTheme {
        CompositionLocalProvider(LocalAppSettings provides store.settings) {
            Box(Modifier.fillMaxSize().background(Ink.Canvas)) {
                // One handler rather than one per layer: back always unwinds the topmost thing
                // on screen, and only falls through to the system once the app is sitting on
                // its home tab with nothing open.
                val sheetOpen = slotTarget != null || openCopyId != null || editor != null ||
                    addingToContainer != null || addingCard != null || addVisible
                val selecting = cardSelection.isNotEmpty() || storageSelection.isActive
                // Drilling into a game is navigation the user did on purpose, so backing out
                // of it returns to the list of games rather than to Home.
                val browsingCatalog = destination == Destination.Browse && browseGame != null
                SystemBackHandler(
                    // Off until the launch screen has gone: nothing under it is reachable yet,
                    // so a back press there should leave the app rather than quietly unwind a
                    // navigation stack the user cannot see.
                    enabled = bootstrap.ready &&
                        (searchVisible || sheetOpen || selecting || browsingCatalog ||
                            route !is Route.Root || destination != Destination.Home),
                ) {
                    when {
                        addVisible -> addVisible = false
                        searchVisible -> searchVisible = false
                        addingToContainer != null -> addingToContainer = null
                        slotTarget != null -> slotTarget = null
                        openCopyId != null -> openCopyId = null
                        addingCard != null -> addingCard = null
                        editor != null -> editor = null
                        // Ahead of the route, so backing out of a selection leaves you on the
                        // screen you were selecting on rather than two steps away.
                        selecting -> clearSelections()
                        route is Route.Settings -> showSettings = false
                        route is Route.BinderDetail -> openBinderId = null
                        route is Route.ContainerDetail -> openContainerId = null
                        route is Route.SetDetail -> openSet = null
                        // Behind the routes: a binder opened from a set's page leaves that page
                        // underneath it, and back should close the binder you are looking at
                        // before it unwinds the browse you are not.
                        browsingCatalog -> browseGame = null
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
                                (
                                    slideInHorizontally { it / 4 } +
                                        fadeIn(tween(Motion.BASE, easing = Motion.enter))
                                    ) togetherWith
                                    (
                                        fadeOut(tween(Motion.FAST)) +
                                            scaleOut(targetScale = 0.96f)
                                        )

                            leavingDetail ->
                                (
                                    fadeIn(tween(Motion.BASE, easing = Motion.enter)) +
                                        scaleIn(initialScale = 0.96f)
                                    ) togetherWith
                                    (
                                        slideOutHorizontally { it / 4 } +
                                            fadeOut(tween(Motion.BASE, easing = Motion.exit))
                                        )

                            // Between tabs: no direction to imply, so do not imply one.
                            else -> fadeIn(tween(Motion.FAST)) togetherWith fadeOut(tween(Motion.FAST))
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
                                    onSwapSlots = { from, to -> store.swapSlots(binder.id, from, to) },
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

                        Route.Settings -> SettingsScreen(
                            settings = store.settings,
                            snapshot = snapshot,
                            onBack = { showSettings = false },
                            onUpdate = { transform -> store.updateSettings(transform) },
                            onSyncCatalog = { onProgress ->
                                // Asks R2 for the index and prices now rather than when they
                                // are due, then carries whatever changed into the collection.
                                // Everything after the two downloads is local.
                                onProgress(0, 3)
                                catalog.use(catalogDownload.ensure(nowEpochSeconds(), force = true))
                                onProgress(1, 3)
                                catalog.usePrices(
                                    priceDownload.ensure(catalog.publicUrl, nowEpochSeconds(), force = true),
                                )
                                browser.refresh()
                                onProgress(2, 3)
                                catalogSync.refresh(store.snapshot, nowEpochSeconds())
                                    .also {
                                        store.applyCatalogSync(it)
                                        onProgress(3, 3)
                                    }
                            },
                            transfer = transfer,
                            onExport = {
                                scope.launch { transfer.export(store.snapshot, store.settings) }
                            },
                            onImportChoose = { scope.launch { transfer.choose() } },
                            onImportApply = {
                                // The same unwinding a reset does, and for the same reason: the
                                // binder or box on screen behind Settings may not exist in the
                                // collection that is arriving.
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
                                // The saved files as well as the live collection. Autosave
                                // would eventually write the empty store over them anyway;
                                // deleting them says it now, and drops the cached catalog with
                                // it so the next launch starts genuinely fresh rather than
                                // merely blank.
                                scope.launch { saver.clear() }
                            },
                        )

                        is Route.Root -> when (current.destination) {
                            Destination.Home -> HomeScreen(
                                snapshot = snapshot,
                                onOpenBinder = openBinder,
                                onOpenContainer = openContainer,
                                onOpenCollection = { openCollection(CollectionView.Binders) },
                                onOpenCards = { openCollection(CollectionView.Cards) },
                                onOpenWants = { openCollection(CollectionView.Wants) },
                                onOpenTrade = {
                                    closeDetails()
                                    destination = Destination.Trade
                                },
                                onOpenSearch = { searchVisible = true },
                                onOpenSettings = { showSettings = true },
                                onOpenCopy = { row -> openCopyId = row.copy.id },
                                onOpenWant = { row -> revealPocket(row.binderId, row.ordinal) },
                                history = priceHistory,
                                onDeleteSale = { id -> store.deleteSale(id) },
                            )

                            Destination.Collection -> CollectionScreen(
                                snapshot = snapshot,
                                view = collectionView,
                                onViewChange = { collectionView = it },
                                layout = cardLayout,
                                onLayoutChange = { cardLayout = it },
                                storageSelection = storageSelection,
                                onStorageSelectionChange = { storageSelection = it },
                                cardSelection = cardSelection,
                                onCardSelectionChange = { cardSelection = it },
                                onOpenBinder = openBinder,
                                onOpenContainer = openContainer,
                                onCreateBinder = { editor = Editor.NewBinder },
                                onCreateContainer = { editor = Editor.NewContainer },
                                onOpenCopy = { row -> openCopyId = row.copy.id },
                                onOpenWant = { row -> revealPocket(row.binderId, row.ordinal) },
                                onOpenSearch = { searchVisible = true },
                                onSetForTrade = { ids, forTrade -> store.setForTrade(ids, forTrade) },
                                onDeleteCopies = { ids -> store.deleteCopies(ids) },
                                onDeleteStorage = { selection, deleteCards ->
                                    selection.binders.forEach { store.deleteBinder(it, deleteCards) }
                                    selection.containers.forEach { store.deleteContainer(it, deleteCards) }
                                },
                            )

                            Destination.Trade -> TradeScreen(
                                snapshot = snapshot,
                                onOpenCopy = { row -> openCopyId = row.copy.id },
                                onOpenWant = { row -> revealPocket(row.binderId, row.ordinal) },
                                onOpenSearch = { searchVisible = true },
                                onOpenCards = { openCollection(CollectionView.Cards) },
                            )

                            Destination.Browse -> BrowseScreen(
                                browser = browser,
                                game = browseGame,
                                onGameChange = { browseGame = it },
                                onOpenSet = { openSet = it },
                                onOpenSearch = { searchVisible = true },
                            )
                        }
                    }
                }

                // The detail screens bring their own bottom bar, and a live storage selection
                // brings one too -- so the navigation dock steps aside for both rather than
                // stacking under a bar that has taken its place.
                AnimatedVisibility(
                    visible = route is Route.Root && !storageSelection.isActive &&
                        cardSelection.isEmpty() && !searchVisible,
                    enter = slideInVertically(Motion.springy()) { it } + fadeIn(),
                    exit = slideOutVertically(Motion.leaving()) { it } + fadeOut(Motion.leaving()),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    IslandNavBar(
                        current = destination,
                        onSelect = {
                            clearSelections()
                            openSet = null
                            destination = it
                        },
                        onAdd = { addVisible = true },
                    )
                }

                UnifiedSearch(
                    visible = searchVisible,
                    snapshot = snapshot,
                    lookup = lookup,
                    browser = browser,
                    importing = importingCard,
                    onDismiss = { searchVisible = false },
                    onOpenCopy = { row ->
                        searchVisible = false
                        openCopyId = row.copy.id
                    },
                    onOpenSet = { set ->
                        searchVisible = false
                        closeDetails()
                        destination = Destination.Browse
                        openSet = set
                    },
                    onAddLocalCard = { brief ->
                        searchVisible = false
                        addingCard = brief
                    },
                    onAddRemoteCard = { hit ->
                        searchVisible = false
                        importRemoteCard(hit)
                    },
                    onQuickAddLocal = quickAddLocal,
                    onQuickAddRemote = quickAddRemote,
                )

                AddSheet(
                    visible = addVisible,
                    snapshot = snapshot,
                    lookup = lookup,
                    destination = addingTo(),
                    onDestinationChange = { id -> store.updateSettings { it.copy(addingTo = id) } },
                    importing = importingCard,
                    onDismiss = { addVisible = false },
                    onOpenLocalCard = { brief ->
                        addVisible = false
                        addingCard = brief
                    },
                    onOpenRemoteCard = { hit ->
                        addVisible = false
                        importRemoteCard(hit)
                    },
                    onQuickAddLocal = quickAddLocal,
                    onQuickAddRemote = quickAddRemote,
                    onCreateBinder = {
                        addVisible = false
                        editor = Editor.NewBinder
                    },
                    onCreateContainer = {
                        addVisible = false
                        editor = Editor.NewContainer
                    },
                )

                SlotSheet(
                    target = slotTarget,
                    store = store,
                    catalog = catalog,
                    browser = browser,
                    history = priceHistory,
                    onDismiss = { slotTarget = null },
                )

                CopySheet(
                    copyId = openCopyId,
                    snapshot = snapshot,
                    history = priceHistory,
                    onSave = { id, details ->
                        store.editCopy(
                            copyId = id,
                            variantId = details.variantId,
                            condition = details.condition,
                            acquiredPrice = details.paid,
                            acquiredDate = details.acquiredDate,
                            grade = details.grade,
                            valueOverride = details.valueOverride,
                            notes = details.notes,
                        )
                    },
                    onMarkSold = { id, price, date ->
                        store.markSold(id, price, date)
                        openCopyId = null
                    },
                    onDismiss = { openCopyId = null },
                    onShowInBinder = revealPocket,
                    onSetForTrade = { copyId, forTrade -> store.setForTrade(copyId, forTrade) },
                    onDelete = { copyId ->
                        store.deleteCopy(copyId)
                        openCopyId = null
                    },
                )

                // Recomputed only when the card being added changes: it walks the whole variant
                // table, and the sheet is open across every recomposition the screen behind it
                // makes.
                val addingVariants = remember(addingCard, snapshot.variants) {
                    addingCard?.let { snapshot.variantBriefs(it.variantId) }.orEmpty()
                }
                val cardDestination = addingTo()
                val addingCounts = remember(snapshot.copies, addingVariants, cardDestination) {
                    addingVariants.associate { it.variantId to store.countIn(it.variantId, cardDestination) }
                }
                val ownedAnywhere = remember(snapshot.copies, addingCard) {
                    addingCard?.let { card ->
                        snapshot.copies.values.count { snapshot.variants[it.variantId]?.printingId == card.printingId }
                    } ?: 0
                }
                AddToCollectionSheet(
                    brief = addingCard,
                    variants = addingVariants,
                    containers = snapshot.containers,
                    destination = cardDestination,
                    counts = addingCounts,
                    ownedAnywhere = ownedAnywhere,
                    history = priceHistory,
                    onDestinationChange = { id -> store.updateSettings { it.copy(addingTo = id) } },
                    onStep = { brief, delta ->
                        if (delta > 0) {
                            repeat(delta) { store.addCopy(variantId = brief.variantId, container = cardDestination) }
                        } else {
                            repeat(-delta) { store.removeOneCopy(brief.variantId, cardDestination) }
                        }
                    },
                    onDismiss = {
                        addingCard = null
                        // The stepper can take a card back to none, and its catalog rows are
                        // left in place while the sheet is open so the plus still works.
                        store.pruneCatalog()
                    },
                    onConfirm = { _, details ->
                        store.addCopy(
                            variantId = details.variantId,
                            condition = details.condition,
                            acquiredPrice = details.paid,
                            grade = details.grade,
                            notes = details.notes,
                            container = cardDestination,
                            acquiredDate = details.acquiredDate,
                            valueOverride = details.valueOverride,
                        )
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
                // Drawn last so it covers everything, and mounted inside the same Box so the
                // app underneath composes and measures while the network work is still running
                // -- by the time this fades, Home is not being built, it is being revealed. It
                // shares the background colour with both the window behind it and the screen
                // under it, so the only thing the fade changes is which type is on screen.
                AnimatedVisibility(
                    visible = !bootstrap.ready,
                    // No enter transition: this is the first thing composed, and a launch
                    // screen that fades *in* over the window background it already matches is a
                    // flicker with extra steps.
                    enter = fadeIn(tween(0)),
                    exit = fadeOut(tween(Motion.SLOW)),
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

/**
 * Home's headline, worded for the widget: the total, the day's move, how many cards, and the
 * card that moved most. The mover is picked among cards worth a dollar or more, where a
 * percentage means something -- a bulk common going from four cents to five is +25%.
 */
private fun widgetSummaryOf(snapshot: app.pocketful.domain.CollectionSnapshot): WidgetSummary {
    val total = snapshot.summarizeAll()
    val count = snapshot.copies.size
    val mover = snapshot.copies.values
        .asSequence()
        .map { it.variantId }
        .distinct()
        .mapNotNull { id ->
            val change = snapshot.changeOf(id) ?: return@mapNotNull null
            val market = snapshot.marketValue(id)
            val before = market - change
            if (market.cents < 100 || before.isZero) null
            else Triple(id, change, change.cents.toDouble() / before.cents * 100)
        }
        .maxByOrNull { kotlin.math.abs(it.third) }
        ?.takeIf { kotlin.math.abs(it.third) >= 1.0 }
        ?.let { (id, change, percent) ->
            val name = snapshot.brief(id)?.name ?: return@let null
            name + " " + (if (change.cents > 0) "▲" else "▼") + percentText(percent)
        }
    return WidgetSummary(
        value = if (total.marketValue.isZero) "—" else total.marketValue.format(currency = total.currency),
        change = total.dayChangeBase.takeIf { it.cents > 0 }?.let {
            changeText(total.dayChange, it, total.currency) + " today"
        },
        changeUp = total.dayChangeBase.takeIf { it.cents > 0 }?.let {
            if (total.dayChange.cents == 0L) null else total.dayChange.cents > 0
        },
        caption = if (count == 1) "1 card" else "$count cards",
        mover = mover,
    )
}
