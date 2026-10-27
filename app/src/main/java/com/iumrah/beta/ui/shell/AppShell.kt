package com.iumrah.beta.ui.shell
import com.iumrah.beta.ui.cupertino.Icon
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
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
import com.iumrah.beta.ui.flights.FlightSearchScreen
import com.iumrah.beta.ui.flights.ReturnFlightScreen
import com.iumrah.beta.ui.flights.FlightDetailsScreen
import com.iumrah.beta.ui.packageflow.TransferSelectionScreen
import com.iumrah.beta.ui.packageflow.FinalPackageScreen
import com.iumrah.beta.ui.booking.BookingCheckoutScreen
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
    val shellNotificationState by notifications.state.collectAsState()
    BackHandler(enabled = chromeState.isSidebarOpen || chromeState.route != AppRoute.Root) {
        if (chromeState.isSidebarOpen) chrome.closeSidebar() else chrome.back()
    }

    SidebarDrawerHost(open = chromeState.isSidebarOpen, language = language, chrome = chrome) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = chromeState.route to chromeState.currentTab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { rootTransform() },
            label = "iumrah-root-navigation",
        ) { (route, tab) ->
            when (route) {
                AppRoute.Root -> when (tab) {
                    AppTab.HOME -> HomeScreen(language, chrome, hotelCatalog, journey, notifications, accountStore.bearerToken)
                    AppTab.HOTELS -> HotelsScreen(language, hotelCatalog, journey, airports, flightDiscovery, chrome, unreadCount = shellNotificationState.unreadCount)
                    AppTab.BOOKING -> BookingsHomeScreen(language, bookingStore, accountService, chatService, notifications, chrome)
                    AppTab.CARE -> CareHomeScreen(language, bookingStore, chatService, notifications, chrome)
                    AppTab.ACCOUNT -> AccountRootScreen(language, accountStore, bookingStore, settingsStore, notifications, chrome)
                }

                AppRoute.TripBuilder -> TripBuilderScreen(
                    language = language,
                    journey = journey,
                    airports = airports,
                    curatedFlights = curatedFlights,
                    chrome = chrome,
                )

                AppRoute.HotelSelection -> HotelSelectionScreen(
                    language = language,
                    journey = journey,
                    catalog = hotelCatalog,
                    packageEngine = packageEngine,
                    curatedFlights = curatedFlights,
                    chrome = chrome,
                )

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

                AppRoute.FinalPackage -> FinalPackageScreen(language, journey, hotelCatalog, packageEngine, chrome)
                AppRoute.BookingCheckout -> BookingCheckoutScreen(language, journey, bookingStore, accountStore, chrome)
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
                is AppRoute.BookingChat -> BookingChatScreen(route.bookingID, language, bookingStore, chatService, chrome)
                AppRoute.BookingZiyarats -> BookingZiyaratCatalogScreen(language, chrome)
                AppRoute.Ziyarats -> ZiyaratJourneyScreen(language, chrome)
                AppRoute.ESIM -> ESIMScreen(language, bookingStore, chrome)
                AppRoute.LiveFlights -> IumrahFlightsScreen(language, bookingStore, chrome)
                AppRoute.UmrahPlan -> UmrahPlanScreen(language, chrome)
                AppRoute.UmrahAdvisor -> UmrahAdvisorScreen(language, chrome)
                AppRoute.TelegramIntegration -> TelegramIntegrationScreen(language, bookingStore, accountStore, chrome)
                AppRoute.CareRequest -> IumrahCareRequestScreen(language, airports, accountStore, settingsStore, chrome)
                AppRoute.TransferService -> IumrahTransferServiceScreen(language, chrome)
                AppRoute.BackendSystem -> IumrahBackendSystemScreen(language, chrome)
                AppRoute.IumrahStory -> IumrahStoryScreen(language, chrome)
                AppRoute.Notifications -> AccountSignalsParityScreen(language, notifications, accountStore, chrome)
                AppRoute.AccountTravelers -> AccountTravelCompanionsParityScreen(language, accountStore, bookingStore, accountService, chrome)
                is AppRoute.AccountTripsHistory -> AccountTripsHistoryScreen(language, bookingStore, chrome, route.initialPast)
                AppRoute.AccountPasswordRecovery -> AccountPasswordRecoveryScreen(language, accountStore, chrome)
                is AppRoute.AccountPolicy -> AccountPolicyParityScreen(route.kind, language, chrome)
                AppRoute.AccountSecurity -> AccountSecurityScreen(language, accountStore, chrome)
                AppRoute.AccountAppearance -> AccountAppearanceParityScreen(language, settingsStore, chrome)
                AppRoute.AccountLanguage -> AccountLanguageScreen(language, settingsStore, chrome)
                AppRoute.AccountSignals -> AccountSignalsParityScreen(language, notifications, accountStore, chrome)
                AppRoute.AccountProfileEditor -> AccountProfileEditorParityScreen(language, accountStore, chrome)
                AppRoute.AccountUserData -> IumrahUserDataScreen(language, accountStore, bookingStore, settingsStore, chrome)
                is AppRoute.AccountKyc -> AccountKycScreen(route.bookingID, language, bookingStore, chrome)
                AppRoute.PrayerTimes -> PrayerTimesScreen(language, chrome)
                AppRoute.HomeWeather -> HomeWeatherScreen(language, chrome)
                AppRoute.GiftCards -> IumrahGiftCardsScreen(language, accountStore, chrome)
            }
        }

        if (!chromeState.isImmersive && chromeState.route == AppRoute.Root) {
            IumrahBottomBar(
                language = language,
                selected = chromeState.currentTab,
                onSelect = {
                    if (it != chromeState.currentTab) IumrahHaptics.selection(hapticView)
                    chrome.navigate(it)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    }
}

private fun rootTransform(): ContentTransform =
    fadeIn(IumrahMotion.rootFade).togetherWith(fadeOut(IumrahMotion.fastFade))

/** Visual mirror of iOS IumrahStoreTabBar: stable five equal slots, no drag-only Android UI. */
@Composable
private fun IumrahBottomBar(
    language: AppLanguage,
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Triple(AppTab.HOME, CupertinoSymbol.Home, L10n.text("tab_home", language)),
        Triple(AppTab.HOTELS, CupertinoSymbol.Hotel, L10n.text("tab_hotels", language)),
        Triple(AppTab.BOOKING, CupertinoSymbol.Suitcase, L10n.text("tab_booking", language)),
        Triple(AppTab.CARE, CupertinoSymbol.HeartFill, L10n.text("tab_care", language)),
        Triple(AppTab.ACCOUNT, CupertinoSymbol.PersonCircle, when (language) {
            AppLanguage.RUSSIAN, AppLanguage.UZBEK_CYRILLIC -> "Аккаунт"
            AppLanguage.UZBEK -> "Akkaunt"
            AppLanguage.ENGLISH -> "Account"
        }),
    )
    val black = com.iumrah.beta.core.design.IumrahColors.SignatureBlack
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .shadow(8.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .055f), spotColor = Color.Black.copy(alpha = .055f))
                .clip(shape)
                .background(Color.White.copy(alpha = .96f))
                .border(.8.dp, Color.Black.copy(alpha = .10f), shape)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (tab, symbol, label) ->
                BottomTabItem(
                    selected = selected == tab,
                    icon = symbol,
                    label = label,
                    selectedTint = Color.White,
                    unselectedTint = Color.Black.copy(alpha = .75f),
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
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .height(65.dp)
            .clip(shape)
            .background(if (selected) com.iumrah.beta.core.design.IumrahColors.SignatureBlack else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        CupertinoIcon(
            symbol = icon,
            contentDescription = label,
            modifier = Modifier.size(21.dp),
            tint = if (selected) selectedTint else unselectedTint,
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
            color = if (selected) selectedTint else unselectedTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
