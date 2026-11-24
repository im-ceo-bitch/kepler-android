package com.iumrah.beta.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageMealSelection
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.domain.journey.JourneyStore

enum class AppTab { HOME, HOTELS, BOOKING, CARE, ACCOUNT }

data class HotelConfiguratorDeepLink(
    val hotelId: String,
    val adults: Int? = null,
    val children: Int? = null,
    val infants: Int? = null,
    val rooms: Int? = null,
    val scope: JourneyScope? = null,
    val firstSaudiCity: SaudiArrivalAirport? = null,
    val mealSelection: PackageMealSelection? = null,
    val outboundOptionId: String? = null,
    val inboundOptionId: String? = null,
)

/**
 * iOS AppChromeStore.IumrahHotelTabSearch contract (Sources/AppShell/AppChromeStore.swift).
 * A transfer belongs to one search event: repeating the same city/date still
 * needs to re-open results, and a previously applied request must not replay
 * when the user revisits the Hotels tab.
 */
data class HotelTabSearchRequest(
    val revision: Long,
    val city: String,
    val checkin: LocalDate,
    val nights: Int,
    val adults: Int,
    val childrenAges: List<Int> = emptyList(),
) {
    init {
        require(city in setOf("makkah", "madinah"))
        require(nights in 1..28)
        require(adults in 1..6)
        require(childrenAges.all { it in 0..17 })
    }
}

/** One independent navigation identity for each of the five iOS TabView tabs. */
data class TabNavigationState(
    val route: AppRoute = AppRoute.Root,
    val backStack: List<AppRoute> = emptyList(),
)

sealed interface AppRoute {
    data object Root : AppRoute
    data object TripBuilder : AppRoute
    data object HotelSelection : AppRoute
    data class HotelDetail(val hotelId: String, val openConfigurator: Boolean = false, val sharedConfiguration: HotelConfiguratorDeepLink? = null) : AppRoute
    data class ConfiguratorHotelSelection(val role: String) : AppRoute
    data class ConfiguratorHotelDetail(val hotelId: String, val role: String) : AppRoute
    data class FlightPackageDetail(val packageId: String, val sharedConfiguration: HotelConfiguratorDeepLink? = null) : AppRoute
    data object GuidedFlights : AppRoute
    data object NuiteeJourneyHotels : AppRoute
    data object NuiteeJourneyReview : AppRoute
    data object Flights : AppRoute
    data object StorefrontFlights : AppRoute
    data object StorefrontSunday : AppRoute
    data object ReturnFlights : AppRoute
    data class FlightDetails(val journeyId: String, val direction: String) : AppRoute
    data object TransferSelection : AppRoute
    data object FinalPackage : AppRoute
    data object SavedJourneys : AppRoute
    data object ManualPackage : AppRoute
    data object BookingCheckout : AppRoute
    data class BusinessOffer(val token: String) : AppRoute
    data class PublicTrip(val token: String) : AppRoute
    data class BookingDetail(val bookingID: String) : AppRoute
    data class BookingCelebration(val bookingID: String) : AppRoute
    data class BookingHotelChange(val bookingID: String, val role: String) : AppRoute
    data class PilgrimCheckout(val bookingID: String) : AppRoute
    data class BookingGuideTransfer(val bookingID: String) : AppRoute
    data class BookingChat(val bookingID: String) : AppRoute
    data object BookingZiyarats : AppRoute
    data object Ziyarats : AppRoute
    data object ESIM : AppRoute
    data object LiveFlights : AppRoute
    data class TripLiveFlights(val bookingID: String) : AppRoute
    data object UmrahPlan : AppRoute
    data object UmrahAdvisor : AppRoute
    data object UmrahFlow : AppRoute
    data object TelegramIntegration : AppRoute
    data object CareRequest : AppRoute
    data object TransferService : AppRoute
    data object BackendSystem : AppRoute
    data object IumrahStory : AppRoute
    data object Notifications : AppRoute
    data object AccountTravelers : AppRoute
    data class AccountTripsHistory(val initialPast: Boolean = false) : AppRoute
    data object AccountPasswordRecovery : AppRoute
    data object AccountPasswordSecurity : AppRoute
    data class AccountPolicy(val kind: String) : AppRoute
    data object AccountSecurity : AppRoute
    data object AccountAppearance : AppRoute
    data object AccountLanguage : AppRoute
    data object AccountSignals : AppRoute
    data object AccountProfileEditor : AppRoute
    data object AccountUserData : AppRoute
    data class AccountKyc(val bookingID: String) : AppRoute
    data object PrayerTimes : AppRoute
    data object HomeWeather : AppRoute
    data object GiftCards : AppRoute
    data class Rewards(val page: String) : AppRoute
    data object SavedHotels : AppRoute
    data class IumrahServices(val initialService: String = "visa") : AppRoute
    data object AccountAnalytics : AppRoute
}

data class AppChromeState(
    val currentTab: AppTab = AppTab.HOME,
    val route: AppRoute = AppRoute.Root,
    val backStack: List<AppRoute> = emptyList(),
    val isImmersive: Boolean = false,
    val isSidebarOpen: Boolean = false,
    val discoveryFlights: Boolean = false,
    val discoveryFlightsRevision: Long = 0,
    val discoveryBuild: Boolean = false,
    val homeHotelCity: String? = null,
    val hotelSearch: HotelTabSearchRequest? = null,
    val hotelSearchRevision: Long = 0,
    val tabNavigation: Map<AppTab, TabNavigationState> = emptyMap(),
    val requestedBookingID: String? = null,
)

class AppChromeStore {
    private val _state = MutableStateFlow(AppChromeState())
    val state: StateFlow<AppChromeState> = _state

    // iOS HotelsHomeView owns @State private var familyJourney: JourneyStore?.
    // Keep one private instance for its navigation chain; never reuse the main journey.
    private var familyJourney: JourneyStore? = null
    // Per-tab status-bar surface follows the actual moving masthead.
    // SwiftUI has no permanent black plate when the header scrolls away.
    private val _rootMastheadVisible = MutableStateFlow<Map<AppTab, Boolean>>(emptyMap())
    val rootMastheadVisible: StateFlow<Map<AppTab, Boolean>> = _rootMastheadVisible

    fun reportMastheadVisibility(tab: AppTab, visible: Boolean) {
        _rootMastheadVisible.update { current ->
            if (current[tab] == visible) current else current + (tab to visible)
        }
    }
    private val familyRoutes: Set<AppRoute> = setOf(AppRoute.GuidedFlights,
        AppRoute.NuiteeJourneyHotels, AppRoute.NuiteeJourneyReview,
        AppRoute.FinalPackage, AppRoute.BookingCheckout)

    fun beginFamilyJourney(value: JourneyStore) {
        require(value.state.value.trip.adults in 1..12)
        familyJourney = value
        _state.update { old ->
            old.copy(currentTab = AppTab.HOTELS, route = AppRoute.GuidedFlights,
                backStack = listOf(AppRoute.Root),
                tabNavigation = old.tabNavigation + (old.currentTab to TabNavigationState(old.route, old.backStack)),
                isImmersive = false, isSidebarOpen = false)
        }
    }

    fun journeyForRoute(route: AppRoute, tab: AppTab, ordinary: JourneyStore): JourneyStore =
        if (tab == AppTab.HOTELS && route in familyRoutes) familyJourney ?: ordinary else ordinary

    /**
     * Native iOS TabView retains an independent NavigationStack per tab.
     * Switching tabs must not turn the prior tab's detail into another tab's
     * back destination; returning to a tab restores its own stack.
     */
    fun navigate(tab: AppTab) {
        _state.update { old ->
            if (tab == old.currentTab && old.route == AppRoute.Root) return@update old.copy(isSidebarOpen = false)
            val history = old.tabNavigation + (old.currentTab to TabNavigationState(old.route, old.backStack))
            val next = history[tab] ?: TabNavigationState()
            old.copy(currentTab = tab, route = next.route, backStack = next.backStack,
                tabNavigation = history, isImmersive = false, isSidebarOpen = false,
                discoveryFlights = false, discoveryBuild = false, homeHotelCity = null,
                hotelSearch = null, requestedBookingID = null)
        }
    }

    /** Matches the iOS Home destination cards: 14 days ahead, seven nights, two adults. */
    fun openHomeHotelSearch(city: String) = findHotels(
        city = city, checkin = LocalDate.now().plusDays(14), nights = 7, adults = 2,
    )

    /** Complete, one-shot Home -> Hotels handoff. No hidden defaults on the destination. */
    fun findHotels(city: String, checkin: LocalDate, nights: Int, adults: Int,
                   childrenAges: List<Int> = emptyList()) {
        require(city in setOf("makkah", "madinah"))
        require(nights in 1..28 && adults in 1..6 && childrenAges.all { it in 0..17 })
        _state.update { old ->
            val revision = old.hotelSearchRevision + 1L
            old.copy(currentTab = AppTab.HOTELS, route = AppRoute.Root, backStack = emptyList(),
                tabNavigation = old.tabNavigation +
                    (old.currentTab to TabNavigationState(old.route, old.backStack)) +
                    (AppTab.HOTELS to TabNavigationState()),
                isImmersive = false, isSidebarOpen = false, discoveryFlights = false,
                discoveryBuild = false, homeHotelCity = city,
                hotelSearchRevision = revision,
                hotelSearch = HotelTabSearchRequest(revision, city, checkin, nights, adults, childrenAges),
                requestedBookingID = null)
        }
    }

    /** Acknowledgement only after the catalogue applied the search to actual UI state. */
    fun consumeHotelSearch(revision: Long) {
        _state.update { old ->
            if (old.hotelSearch?.revision != revision) old
            else old.copy(hotelSearch = null, homeHotelCity = null)
        }
    }

    fun openDiscoveryFlights() {
        _state.update { old ->
            old.copy(currentTab = AppTab.HOTELS, route = AppRoute.Root, backStack = emptyList(),
                tabNavigation = old.tabNavigation +
                    (old.currentTab to TabNavigationState(old.route, old.backStack)) +
                    (AppTab.HOTELS to TabNavigationState()),
                isImmersive = false, isSidebarOpen = false,
                discoveryFlights = true, discoveryFlightsRevision = old.discoveryFlightsRevision + 1,
                discoveryBuild = false, homeHotelCity = null,
                hotelSearch = null, requestedBookingID = null)
        }
    }
    fun openDiscoveryBuild() {
        _state.update { old ->
            old.copy(currentTab = AppTab.HOME, route = AppRoute.Root, backStack = emptyList(),
                tabNavigation = old.tabNavigation +
                    (old.currentTab to TabNavigationState(old.route, old.backStack)) +
                    (AppTab.HOME to TabNavigationState()),
                isImmersive = false, isSidebarOpen = false,
                discoveryFlights = false, discoveryBuild = true, homeHotelCity = null,
                hotelSearch = null)
        }
    }
    fun setDiscoveryFlights(value: Boolean) { _state.update { it.copy(discoveryFlights = value) } }
    fun consumeDiscoveryBuild() { _state.update { it.copy(discoveryBuild = false) } }
    fun openDiscoveryTransfer() = push(AppRoute.TransferService)
    fun openDiscoveryPackage() = push(AppRoute.ManualPackage)
    // Current iOS routes all public "new trip" entry points to the Home search.
    fun startNewTrip() = openDiscoveryBuild()
    fun openHotelSelection() = push(AppRoute.HotelSelection, tab = AppTab.BOOKING)
    fun openHotel(id: String, openConfigurator: Boolean = false, sharedConfiguration: HotelConfiguratorDeepLink? = null) = push(AppRoute.HotelDetail(id, openConfigurator, sharedConfiguration), tab = AppTab.HOTELS)
    fun openConfiguratorHotelSelection(role: String) = push(AppRoute.ConfiguratorHotelSelection(role), tab = AppTab.BOOKING)
    fun openConfiguratorHotelDetail(id: String, role: String) = push(AppRoute.ConfiguratorHotelDetail(id, role), tab = AppTab.BOOKING)
    fun openFlightPackage(id: String, sharedConfiguration: HotelConfiguratorDeepLink? = null) = push(AppRoute.FlightPackageDetail(id, sharedConfiguration), tab = AppTab.HOTELS)
    // HomeDashboardView presents guided flight selection in its Home NavigationStack.
    // The previous Booking-tab ownership broke Back and tab restoration.
    fun openGuidedFlights() = push(AppRoute.GuidedFlights, tab = AppTab.HOME)
    fun openNuiteeJourneyHotels() = push(AppRoute.NuiteeJourneyHotels,
        tab = if (state.value.route == AppRoute.GuidedFlights) state.value.currentTab else AppTab.BOOKING)
    fun openNuiteeJourneyReview() = push(AppRoute.NuiteeJourneyReview,
        tab = if (state.value.route == AppRoute.NuiteeJourneyHotels) state.value.currentTab else AppTab.BOOKING)
    fun openFlights() = push(AppRoute.Flights, tab = AppTab.BOOKING)
    fun openStorefrontFlights() = push(AppRoute.StorefrontFlights, tab = AppTab.HOTELS)
    fun openSundayClub() = push(AppRoute.StorefrontSunday, tab = AppTab.HOTELS)
    fun openReturnFlights() = push(AppRoute.ReturnFlights, tab = AppTab.BOOKING)
    fun openFlightDetails(id: String, direction: String) = push(AppRoute.FlightDetails(id, direction), tab = AppTab.BOOKING)
    fun openTransferSelection() = push(AppRoute.TransferSelection, tab = AppTab.BOOKING)
    fun openFinalPackage() = push(AppRoute.FinalPackage,
        tab = if (state.value.currentTab == AppTab.HOTELS && state.value.route in familyRoutes) AppTab.HOTELS else AppTab.BOOKING)
    fun openSavedJourneys() = push(AppRoute.SavedJourneys, tab = AppTab.BOOKING)
    fun openBusinessOffer(token: String) = push(AppRoute.BusinessOffer(token), tab = AppTab.BOOKING)
    fun openPublicTrip(token: String) {
        if (token.matches(Regex("^[A-Za-z0-9_-]{43}$"))) push(AppRoute.PublicTrip(token), tab = AppTab.BOOKING)
    }
    fun openBookingCheckout() = push(AppRoute.BookingCheckout,
        tab = if (state.value.currentTab == AppTab.HOTELS && state.value.route in familyRoutes) AppTab.HOTELS else AppTab.BOOKING)
    fun openBookingPackageFromLink(id: String) {
        _state.update { old ->
            old.copy(currentTab = AppTab.BOOKING, route = AppRoute.Root, backStack = emptyList(),
                tabNavigation = old.tabNavigation +
                    (old.currentTab to TabNavigationState(old.route, old.backStack)) +
                    (AppTab.BOOKING to TabNavigationState()),
                isImmersive = false, isSidebarOpen = false, requestedBookingID = id)
        }
    }
    fun consumeBookingPackageRequest(id: String) { _state.update { if (it.requestedBookingID == id) it.copy(requestedBookingID = null) else it } }
    fun openBookingDetail(id: String) = push(AppRoute.BookingDetail(id), tab = AppTab.BOOKING)
    fun openBookingCelebration(id: String) = push(AppRoute.BookingCelebration(id), tab = AppTab.BOOKING)
    fun openBookingHotelChange(id: String, role: String) = push(AppRoute.BookingHotelChange(id, role), tab = AppTab.BOOKING)
    fun openPilgrimCheckout(id: String) = push(AppRoute.PilgrimCheckout(id), tab = AppTab.BOOKING)
    fun openBookingGuideTransfer(id: String) = push(AppRoute.BookingGuideTransfer(id), tab = AppTab.BOOKING)
    fun openBookingChat(id: String) = push(AppRoute.BookingChat(id), tab = AppTab.CARE)
    fun openBookingZiyarats() = push(AppRoute.BookingZiyarats, tab = AppTab.BOOKING)
    fun openZiyarats() = push(AppRoute.Ziyarats, tab = AppTab.HOME)
    fun openESIM() = push(AppRoute.ESIM, tab = AppTab.HOME)
    fun openLiveFlights() = push(AppRoute.LiveFlights, tab = AppTab.HOME)
    fun openTripLiveFlights(id: String) = push(AppRoute.TripLiveFlights(id), tab = AppTab.BOOKING)
    fun openUmrahPlan() = push(AppRoute.UmrahPlan, tab = AppTab.HOME)
    fun openUmrahAdvisor() = push(AppRoute.UmrahAdvisor, tab = AppTab.HOME)
    fun openUmrahFlow() = push(AppRoute.UmrahFlow, tab = AppTab.HOME)
    fun openTelegramIntegration() = push(AppRoute.TelegramIntegration, tab = AppTab.HOME)
    fun openAccountTelegramIntegration() = push(AppRoute.TelegramIntegration, tab = AppTab.ACCOUNT)
    fun openBookingTelegramIntegration() = push(AppRoute.TelegramIntegration, tab = AppTab.BOOKING)
    fun openCareRequest() = push(AppRoute.CareRequest, tab = AppTab.HOME)
    fun openTransferService() = push(AppRoute.TransferService, tab = AppTab.HOME)
    fun openBackendSystem() = push(AppRoute.BackendSystem, tab = AppTab.HOME)
    fun openIumrahStory() = push(AppRoute.IumrahStory, tab = AppTab.HOME)
    fun openNotifications() = push(AppRoute.Notifications)
    fun openAccountTravelers() = push(AppRoute.AccountTravelers, tab = AppTab.ACCOUNT)
    fun openAccountTripsHistory(initialPast: Boolean = false) = push(AppRoute.AccountTripsHistory(initialPast), tab = AppTab.ACCOUNT)
    fun openAccountPasswordRecovery() = push(AppRoute.AccountPasswordRecovery, tab = AppTab.ACCOUNT)
    fun openAccountPasswordSecurity() = push(AppRoute.AccountPasswordSecurity, tab = AppTab.ACCOUNT)
    fun openPasswordRecovery() = push(AppRoute.AccountPasswordRecovery)
    fun openAccountPolicy(kind: String) = push(AppRoute.AccountPolicy(kind), tab = AppTab.ACCOUNT)
    fun openBookingPolicy(kind: String) = push(AppRoute.AccountPolicy(kind), tab = AppTab.BOOKING)
    fun openAccountSecurity() = push(AppRoute.AccountSecurity, tab = AppTab.ACCOUNT)
    fun openAccountAppearance() = push(AppRoute.AccountAppearance, tab = AppTab.ACCOUNT)
    fun openAccountLanguage() = push(AppRoute.AccountLanguage, tab = AppTab.ACCOUNT)
    fun openAccountSignals() = push(AppRoute.AccountSignals, tab = AppTab.ACCOUNT)
    fun openAccountProfileEditor() = push(AppRoute.AccountProfileEditor, tab = AppTab.ACCOUNT)
    fun openAccountUserData() = push(AppRoute.AccountUserData, tab = AppTab.ACCOUNT)
    fun openAccountKyc(id: String) = push(AppRoute.AccountKyc(id), tab = AppTab.ACCOUNT)
    fun openBookingSecurity(id: String) = push(AppRoute.AccountKyc(id), tab = AppTab.BOOKING)
    fun openPrayerTimes() = push(AppRoute.PrayerTimes, tab = AppTab.HOME)
    fun openHomeWeather() = push(AppRoute.HomeWeather, tab = AppTab.HOME)
    fun openSavedHotels() = push(AppRoute.SavedHotels)
    fun openRewards(page: String = "rewards") = push(AppRoute.Rewards(page))
    fun openGiftCards() = push(AppRoute.GiftCards, tab = AppTab.HOME)
    fun openIumrahServices(initial: String = "visa") = push(AppRoute.IumrahServices(initial), tab = AppTab.HOME)
    fun openAccountAnalytics() = push(AppRoute.AccountAnalytics, tab = AppTab.ACCOUNT)

    private fun push(route: AppRoute, tab: AppTab? = null) {
        _state.update { old ->
            val destinationTab = tab ?: old.currentTab
            if (destinationTab == old.currentTab) {
                old.copy(route = route,
                    backStack = if (old.route == route) old.backStack else old.backStack + old.route,
                    isImmersive = false, isSidebarOpen = false)
            } else {
                // Open a destination within its owning tab, never append a
                // Home/Account page to a Booking/Care back stack.
                val saved = old.tabNavigation[destinationTab] ?: TabNavigationState()
                old.copy(currentTab = destinationTab, route = route,
                    backStack = if (saved.route == route) saved.backStack else saved.backStack + saved.route,
                    tabNavigation = old.tabNavigation +
                        (old.currentTab to TabNavigationState(old.route, old.backStack)),
                    isImmersive = false, isSidebarOpen = false)
            }
        }
    }

    fun back(): Boolean {
        var didPop = false
        _state.update { old ->
            val previous = old.backStack.lastOrNull() ?: return@update old
            didPop = true
            old.copy(route = previous, backStack = old.backStack.dropLast(1),
                isImmersive = false, isSidebarOpen = false)
        }
        return didPop
    }

    fun setImmersive(value: Boolean) { _state.update { it.copy(isImmersive = value) } }
    fun openSidebar() { _state.update { it.copy(isSidebarOpen = true) } }
    fun closeSidebar() { _state.update { it.copy(isSidebarOpen = false) } }
}
