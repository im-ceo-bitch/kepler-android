package com.iumrah.beta.ui.shell
import com.iumrah.beta.ui.cupertino.Icon
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.luminance
import com.iumrah.beta.core.design.IOSLayoutProfile
import com.iumrah.beta.core.design.IOSParityScope
import com.iumrah.beta.core.design.IOSContinuousShape
import com.iumrah.beta.core.design.iosChromeSurface
import com.iumrah.beta.core.design.rememberIumrahMotionAllowed
import com.iumrah.beta.core.navigation.RouteStateKeys
import kotlinx.coroutines.delay
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import com.iumrah.beta.core.design.IumrahFontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.IumrahMotion
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppChromeState
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppRoute
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.settings.AppSettingsStore
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.account.IumrahAccountService
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.chat.ChatService
import com.iumrah.beta.data.notification.ClientNotificationStore
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.flight.AviasalesFlightDiscoveryService
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.data.flight.IgnavFlightInventoryProvider
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.pricing.PackageGenerator
import com.iumrah.beta.ui.flights.IumrahGuidedFlightsScreen
import com.iumrah.beta.ui.flights.FlightSearchScreen
import com.iumrah.beta.ui.flights.ReturnFlightScreen
import com.iumrah.beta.ui.flights.FlightDetailsScreen
import com.iumrah.beta.ui.packageflow.TransferSelectionScreen
import com.iumrah.beta.ui.packageflow.NuiteeJourneyReviewScreen
import com.iumrah.beta.ui.trip.NuiteeJourneyHotelsScreen
import com.iumrah.beta.ui.packageflow.FinalPackageScreen
import com.iumrah.beta.ui.booking.BookingCheckoutScreen
import com.iumrah.beta.ui.booking.BusinessOfferScreen
import com.iumrah.beta.ui.booking.IumrahBookingCelebrationScreen
import com.iumrah.beta.ui.booking.BookingDetailScreen
import com.iumrah.beta.ui.booking.BookingHotelChangeScreen
import com.iumrah.beta.ui.booking.BookingsHomeScreen
import com.iumrah.beta.ui.booking.PilgrimCheckoutScreen
import com.iumrah.beta.ui.booking.IumrahGuideTransferScreen
import com.iumrah.beta.ui.care.CareHomeScreen
import com.iumrah.beta.ui.ziyarats.ZiyaratJourneyScreen
import com.iumrah.beta.ui.ziyarats.BookingZiyaratCatalogScreen
import com.iumrah.beta.ui.esim.ESIMScreen
import com.iumrah.beta.ui.liveflights.IumrahFlightsScreen
import com.iumrah.beta.ui.planning.UmrahPlanScreen
import com.iumrah.beta.ui.umrahflow.UmrahFlowScreen
import com.iumrah.beta.ui.telegram.TelegramIntegrationScreen
import com.iumrah.beta.ui.carerequest.IumrahCareRequestScreen
import com.iumrah.beta.ui.transferservice.IumrahTransferServiceScreen
import com.iumrah.beta.ui.backendsystem.IumrahBackendSystemScreen
import com.iumrah.beta.ui.story.IumrahStoryScreen
import com.iumrah.beta.ui.account.AccountRootScreen
import com.iumrah.beta.ui.account.AccountTravelersScreen
import com.iumrah.beta.ui.account.AccountTripsHistoryScreen
import com.iumrah.beta.ui.account.AccountTravelCompanionsParityScreen
import com.iumrah.beta.ui.account.AccountPasswordRecoveryScreen
import com.iumrah.beta.ui.account.AccountPasswordSecurityScreen
import com.iumrah.beta.ui.account.AccountPolicyParityScreen
import com.iumrah.beta.ui.account.AccountPolicyScreen
import com.iumrah.beta.ui.account.AccountSecurityScreen
import com.iumrah.beta.ui.account.AccountAppearanceScreen
import com.iumrah.beta.ui.advisor.UmrahAdvisorScreen
import com.iumrah.beta.ui.account.AccountAppearanceParityScreen
import com.iumrah.beta.ui.account.AccountLanguageScreen
import com.iumrah.beta.ui.account.AccountSignalsScreen
import com.iumrah.beta.ui.account.AccountSignalsParityScreen
import com.iumrah.beta.ui.account.AccountProfileEditorParityScreen
import com.iumrah.beta.ui.account.IumrahUserDataScreen
import com.iumrah.beta.ui.account.AccountKycScreen
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.chat.BookingChatScreen
import com.iumrah.beta.ui.notifications.NotificationsScreen
import com.iumrah.beta.ui.home.IumrahServicesScreen
import com.iumrah.beta.ui.account.AccountAnalyticsScreen
import com.iumrah.beta.ui.home.HomeScreen
import com.iumrah.beta.ui.home.HomeWeatherScreen
import com.iumrah.beta.ui.home.IumrahGiftCardsScreen
import com.iumrah.beta.ui.prayer.PrayerTimesScreen
import com.iumrah.beta.ui.hotels.FlightFirstPackageDetailScreen
import com.iumrah.beta.ui.hotels.HotelDetailScreen
import com.iumrah.beta.ui.hotels.HotelsScreen
import com.iumrah.beta.ui.hotels.HotelsBoard
import com.iumrah.beta.ui.trip.HotelSelectionScreen
import com.iumrah.beta.ui.trip.ConfiguratorHotelSelectionScreen
import com.iumrah.beta.ui.trip.TripBuilderScreen

@Composable
fun AppShell(
    language: AppLanguage,
    chrome: AppChromeStore,
    chromeState: AppChromeState,
    accountStore: IumrahAccountStore,
    hotelCatalog: HotelCatalogService,
    packageEngine: RemotePackageEngineClient,
    journey: JourneyStore,
    airports: AirportSearchService,
    flightDiscovery: AviasalesFlightDiscoveryService,
    flightInventory: IgnavFlightInventoryProvider,
    curatedFlights: CuratedFlightRecommendationService,
    packageGenerator: PackageGenerator,
    bookingStore: BookingStore,
    accountService: IumrahAccountService,
    chatService: ChatService,
    notifications: ClientNotificationStore,
    settingsStore: AppSettingsStore,
) {
    val hapticView = LocalView.current
    val context = LocalContext.current
    val isRootTab = chromeState.route == AppRoute.Root
    // Root headers own their top inset just like IumrahRootScrollingPage.
    // An opaque status strip masks scroll content when the masthead leaves
    // the viewport, preventing labels from appearing behind the clock.
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // iOS TabView reserves its bar's safe area. Android renders our five-slot
    // bar as an overlay, so reserve its full footprint in every ROOT tab's
    // scroll viewport instead of asking each page to guess a bottom spacer.
    val bottomNavigationFootprint = 82.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val visibleHeaders by chrome.rootMastheadVisible.collectAsState()
    val rootHeaderVisible = visibleHeaders[chromeState.currentTab] ?: true
    val window = (context as? android.app.Activity)?.window
    val isHomeRoot = isRootTab && chromeState.currentTab == AppTab.HOME
    val darkPage = MaterialTheme.colorScheme.background.luminance() < .5f
    DisposableEffect(window, hapticView, isRootTab, rootHeaderVisible, isHomeRoot, darkPage) {
        val controller = window?.let { WindowCompat.getInsetsController(it, hapticView) }
        val previous = controller?.isAppearanceLightStatusBars
        if (isRootTab) controller?.isAppearanceLightStatusBars = !rootHeaderVisible && (isHomeRoot || !darkPage)
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    val shellNotificationState by notifications.state.collectAsState()
    // SwiftUI TabView preserves each NavigationStack's scroll/form state.
    // AnimatedContent otherwise disposes a tab on every switch. Save Android's
    // rememberSaveable state by tab identity without composing hidden tabs
    // (which would accidentally start five background API requests).
    val tabSaveableStates = rememberSaveableStateHolder()
    val routeSaveableStates = rememberSaveableStateHolder()
    val routeKeys = remember { RouteStateKeys() }
    LaunchedEffect(chromeState.currentTab, chromeState.route, chromeState.backStack, chromeState.tabNavigation) {
        // Let the outgoing transition finish before removing popped destinations.
        delay(500)
        routeKeys.prune(chromeState).forEach(routeSaveableStates::removeState)
    }
    val motionAllowed = rememberIumrahMotionAllowed()
    BackHandler(enabled = chromeState.isSidebarOpen || chromeState.route != AppRoute.Root) {
        if (chromeState.isSidebarOpen) chrome.closeSidebar() else chrome.back()
    }

    SidebarDrawerHost(open = chromeState.isSidebarOpen, language = language, chrome = chrome, preserveHome = isHomeRoot) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val useSidebar = !isHomeRoot && !chromeState.isImmersive && IOSLayoutProfile.resolve(maxWidth.value).usesSidebar
        Row(Modifier.fillMaxSize()) {
            if (useSidebar) {
                Box {
                    IOSWideSidebar(language, chromeState.currentTab, shellNotificationState.unreadCount,
                        onSelect = { IumrahHaptics.selection(hapticView); chrome.navigate(it) },
                        onESIM = chrome::openESIM, onNotifications = chrome::openNotifications)
                    if (isRootTab) Box(Modifier.align(Alignment.TopCenter).width(268.dp).height(statusInset)
                        .background(if (rootHeaderVisible) com.iumrah.beta.core.design.IumrahColors.SignatureBlack
                        else MaterialTheme.colorScheme.background))
                }
                Box(Modifier.width(.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface.copy(alpha = .1f)))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val profile = IOSLayoutProfile.resolve(maxWidth.value)
        AnimatedContent(
            targetState = chromeState.route to chromeState.currentTab,
            // iOS IumrahRootScrollingPage draws the scrolling masthead under
            // the cutout, and puts safeAreaInsets.top INSIDE that masthead.
            // Do not pre-consume the status inset here: doing so removes the
            // child header's compensation and clips its scrolling background.
            modifier = Modifier.fillMaxSize()
                .then(if (isRootTab && !chromeState.isImmersive && !useSidebar) Modifier.padding(bottom = bottomNavigationFootprint) else Modifier)
                .clipToBounds(),
            transitionSpec = {
                val homeInvolved = (initialState.first == AppRoute.Root && initialState.second == AppTab.HOME) ||
                    (targetState.first == AppRoute.Root && targetState.second == AppTab.HOME)
                when {
                    homeInvolved -> rootTransform() // Preserve existing Home transition.
                    !motionAllowed -> fadeIn(snap()).togetherWith(fadeOut(snap()))
                    initialState.second != targetState.second -> fadeIn(tween(180)).togetherWith(fadeOut(tween(180)))
                    else -> {
                        val popping = initialState.first != targetState.first && initialState.first !in chromeState.backStack
                        val direction = if (popping) -1 else 1
                        (slideInHorizontally(tween(280, easing = IumrahMotion.iosButtonEaseOut)) { it * direction } + fadeIn(tween(180)))
                            .togetherWith(slideOutHorizontally(tween(280, easing = IumrahMotion.iosButtonEaseOut)) { -it * direction / 3 } + fadeOut(tween(220)))
                    }
                }
            },
            label = "iumrah-root-navigation",
        ) { (route, tab) ->
            // AnimatedContent can render an outgoing route concurrently with the new one.
            val guidedJourney = chrome.journeyForRoute(route, tab, journey)
            val render: @Composable () -> Unit = {
            IOSParityScope(tab, route, profile, chromeState.isImmersive, useSidebar) {
            when (route) {
                AppRoute.Root -> tabSaveableStates.SaveableStateProvider(tab) {
                    when (tab) {
                    AppTab.HOME -> HomeScreen(language, chrome, hotelCatalog, journey, airports, curatedFlights, bookingStore, notifications, accountStore.bearerToken, accountStore, packageEngine, settingsStore::setLanguage)
                    AppTab.HOTELS -> HotelsScreen(language, hotelCatalog, journey, airports, flightDiscovery, chrome, initialBoard = if (chromeState.discoveryFlights) HotelsBoard.FLIGHTS else HotelsBoard.HOTELS, unreadCount = shellNotificationState.unreadCount)
                    AppTab.BOOKING -> BookingsHomeScreen(language, bookingStore, accountService, accountStore, chatService, notifications, chrome)
                    AppTab.CARE -> CareHomeScreen(language, bookingStore, accountStore, chatService, notifications, chrome)
                    AppTab.ACCOUNT -> AccountRootScreen(language, accountStore, bookingStore, settingsStore, notifications, chrome)
                    }
                }

                is AppRoute.IumrahServices -> IumrahServicesScreen(language, route.initialService, chrome)
                AppRoute.AccountAnalytics -> AccountAnalyticsScreen(language, chrome)

                AppRoute.TripBuilder -> TripBuilderScreen(
                    language = language,
                    journey = journey,
                    airports = airports,
                    curatedFlights = curatedFlights,
                    chrome = chrome,
                )

                AppRoute.HotelSelection -> com.iumrah.beta.ui.trip.UnifiedHotelEntry(language,journey,curatedFlights,chrome)

                is AppRoute.HotelDetail -> HotelDetailScreen(
                    hotelId = route.hotelId,
                    language = language,
                    catalog = hotelCatalog,
                    packageEngine = packageEngine,
                    journey = journey,
                    onBack = chrome::back,
                    onOpenConfigurator = { packageId -> chrome.openFlightPackage(packageId, route.sharedConfiguration) },
                    autoOpenConfigurator = route.openConfigurator,
                    sharedConfiguration = route.sharedConfiguration,
                    onOpenRefundPolicy = { chrome.openAccountPolicy("refund") },
                )

                is AppRoute.ConfiguratorHotelSelection -> ConfiguratorHotelSelectionScreen(
                    role = route.role,
                    language = language,
                    journey = journey,
                    catalog = hotelCatalog,
                    chrome = chrome,
                )

                is AppRoute.ConfiguratorHotelDetail -> HotelDetailScreen(
                    hotelId = route.hotelId,
                    language = language,
                    catalog = hotelCatalog,
                    packageEngine = packageEngine,
                    journey = journey,
                    onBack = chrome::back,
                    onOpenConfigurator = {},
                    selectionRole = route.role,
                    onSelectionDone = {
                        val previous = chrome.state.value.backStack.lastOrNull()
                        chrome.back()
                        if (previous is AppRoute.ConfiguratorHotelSelection) chrome.back()
                    },
                    onOpenRefundPolicy = { chrome.openBookingPolicy("refund") },
                )

                is AppRoute.FlightPackageDetail -> FlightFirstPackageDetailScreen(
                    packageId = route.packageId,
                    language = language,
                    service = hotelCatalog,
                    journey = journey,
                    chrome = chrome,
                    sharedConfiguration = route.sharedConfiguration,
                )

                AppRoute.StorefrontFlights -> HotelsScreen(
                    language = language,
                    service = hotelCatalog,
                    journey = journey,
                    airports = airports,
                    flightDiscovery = flightDiscovery,
                    chrome = chrome,
                    initialBoard = HotelsBoard.FLIGHTS,
                    unreadCount = shellNotificationState.unreadCount,
                )

                AppRoute.StorefrontGuides -> HotelsScreen(
                    language = language, service = hotelCatalog, journey = journey, airports = airports,
                    flightDiscovery = flightDiscovery, chrome = chrome, initialBoard = HotelsBoard.GUIDE,
                    unreadCount = shellNotificationState.unreadCount,
                )

                AppRoute.StorefrontSunday -> HotelsScreen(
                    language = language,
                    service = hotelCatalog,
                    journey = journey,
                    airports = airports,
                    flightDiscovery = flightDiscovery,
                    chrome = chrome,
                    initialBoard = HotelsBoard.SUNDAY,
                    unreadCount = shellNotificationState.unreadCount,
                )

                AppRoute.GuidedFlights -> IumrahGuidedFlightsScreen(
                    language = language, journey = guidedJourney, curated = curatedFlights,
                    discovery = flightDiscovery, airports = airports, chrome = chrome,
                )
                AppRoute.NuiteeJourneyHotels -> NuiteeJourneyHotelsScreen(language, guidedJourney, chrome)
                AppRoute.NuiteeJourneyReview -> NuiteeJourneyReviewScreen(language, guidedJourney, accountStore, chrome, curatedFlights, packageEngine, unreadCount = shellNotificationState.unreadCount)
                is AppRoute.SmartFlow -> {
                    val history = remember(context) { com.iumrah.beta.data.smart.HomeActivityStore(context) }
                    val service = remember(context, curatedFlights, packageEngine) {
                        com.iumrah.beta.data.smart.SmartConfiguratorService(curatedFlights, com.iumrah.beta.data.nuitee.NuiteeHotelService(context), packageEngine)
                    }
                    val account by accountStore.state.collectAsState()
                    val owner = if (account.isRestoring) null else com.iumrah.beta.domain.smart.SmartRules.owner(account.isAuthenticated, account.iumrahID)
                    androidx.compose.runtime.key(owner, route) {
                        com.iumrah.beta.ui.smart.SmartConfiguratorScreen(language, airports, service, route.draft,
                            onSave = { history.saveSmart(owner, it) }, onClose = { if (chrome.state.value.route == route) chrome.back() },
                            onReview = { }, onLanguage = settingsStore::setLanguage)
                    }
                }
                is AppRoute.SmartReview -> NuiteeJourneyReviewScreen(language, route.journey, accountStore, chrome, curatedFlights, packageEngine, unreadCount = shellNotificationState.unreadCount)
                is AppRoute.SmartHotels -> NuiteeJourneyHotelsScreen(language, route.journey, chrome)
                is AppRoute.SmartCheckout -> BookingCheckoutScreen(language, route.journey, bookingStore, accountStore, chrome, curatedFlights, packageEngine)
                AppRoute.Flights -> FlightSearchScreen(
                    language = language,
                    journey = journey,
                    provider = flightInventory,
                    packageEngine = packageEngine,
                    chrome = chrome,
                )
                AppRoute.ReturnFlights -> ReturnFlightScreen(language, journey, flightInventory, packageEngine, chrome)
                is AppRoute.FlightDetails -> FlightDetailsScreen(route.journeyId, route.direction, language, journey, chrome)
                AppRoute.TransferSelection -> TransferSelectionScreen(language, journey, packageGenerator, chrome)

                AppRoute.ManualPackage -> com.iumrah.beta.ui.packageflow.ManualPackageScreen(language,journey,chrome,curatedFlights,flightDiscovery,packageEngine)
                AppRoute.FinalPackage -> {
                    val reviewState by guidedJourney.state.collectAsState()
                    if (reviewState.nuitee != null) NuiteeJourneyReviewScreen(language, guidedJourney, accountStore, chrome, curatedFlights, packageEngine, unreadCount = shellNotificationState.unreadCount)
                    else FinalPackageScreen(language, guidedJourney, hotelCatalog, packageEngine, chrome, unreadCount = shellNotificationState.unreadCount)
                }
                AppRoute.SavedJourneys -> com.iumrah.beta.ui.packageflow.SavedJourneysScreen(language, journey, bookingStore, chrome)
                is AppRoute.BusinessOffer -> BusinessOfferScreen(route.token, language, bookingStore, chrome)
                is AppRoute.PublicTrip -> com.iumrah.beta.ui.booking.PublicTripShareScreen(route.token, language, chrome::back)
                AppRoute.BookingCheckout -> BookingCheckoutScreen(language, guidedJourney, bookingStore, accountStore, chrome, curatedFlights, packageEngine)
                is AppRoute.BookingCelebration -> IumrahBookingCelebrationScreen(route.bookingID, language, bookingStore, chrome)
                is AppRoute.BookingDetail -> BookingDetailScreen(
                    route.bookingID,
                    language,
                    bookingStore,
                    accountStore,
                    accountService,
                    chrome,
                )
                is AppRoute.BookingHotelChange -> BookingHotelChangeScreen(route.bookingID, route.role, language, bookingStore, hotelCatalog, packageEngine, chrome)
                is AppRoute.PilgrimCheckout -> PilgrimCheckoutScreen(route.bookingID, language, bookingStore, accountStore, accountService, chrome)
                is AppRoute.BookingGuideTransfer -> IumrahGuideTransferScreen(route.bookingID, language, bookingStore, chatService, chrome)
                is AppRoute.BookingChat -> {
                    val chatAccount by accountStore.state.collectAsState()
                    androidx.compose.runtime.key(route.bookingID, chatAccount.account?.iumrahID, accountStore.bearerToken) {
                        BookingChatScreen(bookingStore.careChatKey(route.bookingID) ?: route.bookingID, language, bookingStore, chatService, chrome)
                    }
                }
                AppRoute.BookingZiyarats -> BookingZiyaratCatalogScreen(language, chrome)
                AppRoute.Ziyarats -> ZiyaratJourneyScreen(language, chrome)
                AppRoute.ESIM -> ESIMScreen(language, bookingStore, chrome)
                AppRoute.LiveFlights -> IumrahFlightsScreen(language, bookingStore, chrome)
                is AppRoute.TripLiveFlights -> IumrahFlightsScreen(language, bookingStore, chrome, route.bookingID)
                AppRoute.UmrahPlan -> UmrahPlanScreen(language, chrome)
                AppRoute.UmrahAdvisor -> UmrahAdvisorScreen(language, chrome)
                AppRoute.UmrahFlow -> UmrahFlowScreen(language, chrome)
                AppRoute.TelegramIntegration -> TelegramIntegrationScreen(language, bookingStore, accountStore, chrome)
                AppRoute.CareRequest -> IumrahCareRequestScreen(language, airports, accountStore, settingsStore, chrome)
                AppRoute.TransferService -> IumrahTransferServiceScreen(language, chrome)
                AppRoute.BackendSystem -> IumrahBackendSystemScreen(language, chrome)
                AppRoute.IumrahStory -> IumrahStoryScreen(language, chrome)
                AppRoute.Notifications -> AccountSignalsParityScreen(language, notifications, accountStore, bookingStore, chrome)
                AppRoute.AccountTravelers -> AccountTravelCompanionsParityScreen(language, accountStore, bookingStore, accountService, chrome)
                is AppRoute.AccountTripsHistory -> AccountTripsHistoryScreen(language, bookingStore, chrome, route.initialPast)
                AppRoute.AccountPasswordRecovery -> AccountPasswordRecoveryScreen(language, accountStore, chrome)
                AppRoute.AccountPasswordSecurity -> AccountPasswordSecurityScreen(language, accountStore, chrome)
                is AppRoute.AccountPolicy -> AccountPolicyParityScreen(route.kind, language, chrome)
                AppRoute.AccountSecurity -> AccountSecurityScreen(language, accountStore, chrome)
                AppRoute.AccountAppearance -> AccountAppearanceParityScreen(language, settingsStore, chrome)
                AppRoute.AccountLanguage -> AccountLanguageScreen(language, settingsStore, chrome)
                AppRoute.AccountSignals -> AccountSignalsParityScreen(language, notifications, accountStore, bookingStore, chrome)
                AppRoute.AccountProfileEditor -> AccountProfileEditorParityScreen(language, accountStore, chrome)
                AppRoute.AccountUserData -> IumrahUserDataScreen(language, accountStore, bookingStore, settingsStore, chrome)
                is AppRoute.AccountKyc -> AccountKycScreen(route.bookingID, language, bookingStore, chrome)
                AppRoute.PrayerTimes -> PrayerTimesScreen(language, chrome)
                AppRoute.HomeWeather -> HomeWeatherScreen(language, chrome)
                AppRoute.SavedHotels -> com.iumrah.beta.ui.hotels.NuiteeHotelsCatalogScreen(language, chrome, onBoard = {}, favoritesOnly = true)
                is AppRoute.Rewards -> com.iumrah.beta.ui.account.IumrahRewardsScreen(route.page, language, accountStore, chrome)
                AppRoute.GiftCards -> IumrahGiftCardsScreen(language, accountStore, chrome)
            }
            }
            }
            if (!RouteStateKeys.canRetain(route)) render()
            else routeSaveableStates.SaveableStateProvider(routeKeys.key(tab, route), content = render)
        }

        // Mirror the moving iOS masthead: after it scrolls away, the status
        // area becomes the page background, not a permanent black strip.
        if (isRootTab) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusInset)
                    .background(if (rootHeaderVisible)
                        com.iumrah.beta.core.design.IumrahColors.SignatureBlack
                        else MaterialTheme.colorScheme.background)
            )
        }

        if (!chromeState.isImmersive && chromeState.route == AppRoute.Root && !useSidebar) {
            IumrahBottomBar(
                language = language,
                selected = chromeState.currentTab,
                preserveHome = isHomeRoot,
                onSelect = {
                    if (it != chromeState.currentTab) {
                        IumrahHaptics.selection(hapticView)
                        chrome.navigate(it)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
            }
        }
    }
    }
}

private fun rootTransform(): ContentTransform =
    fadeIn(IumrahMotion.rootFade).togetherWith(fadeOut(IumrahMotion.fastFade))

/**
 * Native iOS TabView is the reference for non-Home tabs, including its adaptive
 * teal tint. Home deliberately keeps the legacy monochrome bar. Native glass
 * rasterization and the exact system tab geometry need device comparison.
 */
@Composable
private fun IumrahBottomBar(
    language: AppLanguage,
    selected: AppTab,
    preserveHome: Boolean,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Triple(AppTab.HOME, CupertinoSymbol.Home, L10n.text("tab_home", language)),
        Triple(AppTab.HOTELS, CupertinoSymbol.Building, L10n.text("tab_hotels", language)),
        Triple(AppTab.BOOKING, CupertinoSymbol.Suitcase, L10n.text("tab_booking", language)),
        Triple(AppTab.CARE, CupertinoSymbol.HeartFill, L10n.text("tab_care", language)),
        Triple(AppTab.ACCOUNT, CupertinoSymbol.PersonCircle, when (language) {
            AppLanguage.RUSSIAN, AppLanguage.UZBEK_CYRILLIC -> "Профиль"
            AppLanguage.UZBEK, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Profil"
            AppLanguage.ARABIC -> "الملف الشخصي"
            AppLanguage.ENGLISH -> "Profile"
        }),
    )
    val foreground = MaterialTheme.colorScheme.onSurface
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .shadow(20.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .055f), spotColor = Color.Black.copy(alpha = .055f))
                .clip(shape)
                .then(if (preserveHome) Modifier.background(Color.White.copy(alpha = .96f))
                    .border(.8.dp, Color.Black.copy(alpha = .10f), shape) else Modifier.iosChromeSurface(shape))
                .padding(5.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (tab, symbol, label) ->
                BottomTabItem(
                    selected = selected == tab,
                    icon = symbol,
                    label = label,
                    selectedTint = if (preserveHome) Color.White else if (dark) Color(0xFF40C8E0) else com.iumrah.beta.core.design.IumrahColors.SystemTeal,
                    unselectedTint = if (preserveHome) Color.Black.copy(alpha = .75f) else foreground.copy(alpha = .8f),
                    preserveHome = preserveHome,
                ) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun RowScope.BottomTabItem(
    selected: Boolean,
    icon: CupertinoSymbol,
    label: String,
    selectedTint: Color,
    unselectedTint: Color,
    preserveHome: Boolean,
    onClick: () -> Unit,
) {
    val shape = if (preserveHome) RoundedCornerShape(25.dp) else IOSContinuousShape(25.dp)
    val activeColor = if (preserveHome) com.iumrah.beta.core.design.IumrahColors.SignatureBlack else MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
    val motion = if (preserveHome) true else rememberIumrahMotionAllowed()
    val selectionColor by animateColorAsState(if (selected) activeColor else Color.Transparent,
        if (motion) tween(180, easing = CubicBezierEasing(.42f, 0f, .58f, 1f)) else snap(), label = "tab-selection")
    Column(
        modifier = Modifier
            .weight(1f)
            .height(65.dp)
            .clip(shape)
            .background(selectionColor)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.height(25.dp), contentAlignment = Alignment.Center) {
            CupertinoIcon(
                symbol = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (selected) selectedTint else unselectedTint,
            )
        }
        // iOS uses a 10.5pt label with minimumScaleFactor(0.72).  Measure
        // actual glyphs against the available tab width, rather than guessing
        // from the string length (which truncated Uzbek/Cyrillic labels).
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val weight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                         else androidx.compose.ui.text.font.FontWeight.Medium
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val fontTenths = remember(label, maxWidth, weight, density) {
                val maxWidthPx = with(density) { maxWidth.roundToPx() }
                (105 downTo 76).firstOrNull { candidate ->
                    textMeasurer.measure(
                        AnnotatedString(label),
                        style = TextStyle(fontSize = (candidate / 10f).sp,
                            fontFamily = IumrahFontFamily, fontWeight = weight),
                        maxLines = 1, softWrap = false,
                    ).size.width <= maxWidthPx
                } ?: 76
            }
            Text(
                text = label,
                fontSize = (fontTenths / 10f).sp,
                lineHeight = 12.sp,
                fontWeight = weight,
                color = if (selected) selectedTint else unselectedTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
