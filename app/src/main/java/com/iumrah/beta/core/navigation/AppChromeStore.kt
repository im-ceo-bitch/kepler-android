package com.iumrah.beta.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageMealSelection
import com.iumrah.beta.domain.trip.SaudiArrivalAirport

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

sealed interface AppRoute {
    data object Root : AppRoute
    data object TripBuilder : AppRoute
    data object HotelSelection : AppRoute
    data class HotelDetail(val hotelId: String, val openConfigurator: Boolean = false, val sharedConfiguration: HotelConfiguratorDeepLink? = null) : AppRoute
    data class ConfiguratorHotelSelection(val role: String) : AppRoute
    data class ConfiguratorHotelDetail(val hotelId: String, val role: String) : AppRoute
    data class FlightPackageDetail(val packageId: String, val sharedConfiguration: HotelConfiguratorDeepLink? = null) : AppRoute
    data object Flights : AppRoute
    data object StorefrontFlights : AppRoute
    data object StorefrontSunday : AppRoute
    data object ReturnFlights : AppRoute
    data class FlightDetails(val journeyId: String, val direction: String) : AppRoute
    data object TransferSelection : AppRoute
    data object FinalPackage : AppRoute
    data object BookingCheckout : AppRoute
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
    data object UmrahPlan : AppRoute
    data object UmrahAdvisor : AppRoute
    data object TelegramIntegration : AppRoute
    data object CareRequest : AppRoute
    data object TransferService : AppRoute
    data object BackendSystem : AppRoute
    data object IumrahStory : AppRoute
    data object Notifications : AppRoute
    data object AccountTravelers : AppRoute
    data class AccountTripsHistory(val initialPast: Boolean = false) : AppRoute
    data object AccountPasswordRecovery : AppRoute
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
}

data class AppChromeState(
    val currentTab: AppTab = AppTab.HOME,
    val route: AppRoute = AppRoute.Root,
    val backStack: List<AppRoute> = emptyList(),
    val isImmersive: Boolean = false,
    val isSidebarOpen: Boolean = false,
)

class AppChromeStore {
    private val _state = MutableStateFlow(AppChromeState())
    val state: StateFlow<AppChromeState> = _state

    fun navigate(tab: AppTab) {
        _state.update { it.copy(currentTab = tab, route = AppRoute.Root, backStack = emptyList(), isImmersive = false, isSidebarOpen = false) }
    }
    fun startNewTrip() = push(AppRoute.TripBuilder, tab = AppTab.BOOKING)
    fun openHotelSelection() = push(AppRoute.HotelSelection, tab = AppTab.BOOKING)
    fun openHotel(id: String, openConfigurator: Boolean = false, sharedConfiguration: HotelConfiguratorDeepLink? = null) = push(AppRoute.HotelDetail(id, openConfigurator, sharedConfiguration), tab = AppTab.HOTELS)
    fun openConfiguratorHotelSelection(role: String) = push(AppRoute.ConfiguratorHotelSelection(role), tab = AppTab.BOOKING)
    fun openConfiguratorHotelDetail(id: String, role: String) = push(AppRoute.ConfiguratorHotelDetail(id, role), tab = AppTab.BOOKING)
    fun openFlightPackage(id: String, sharedConfiguration: HotelConfiguratorDeepLink? = null) = push(AppRoute.FlightPackageDetail(id, sharedConfiguration), tab = AppTab.HOTELS)
    fun openFlights() = push(AppRoute.Flights, tab = AppTab.BOOKING)
    fun openStorefrontFlights() = push(AppRoute.StorefrontFlights, tab = AppTab.HOTELS)
    fun openSundayClub() = push(AppRoute.StorefrontSunday, tab = AppTab.HOTELS)
    fun openReturnFlights() = push(AppRoute.ReturnFlights, tab = AppTab.BOOKING)
    fun openFlightDetails(id: String, direction: String) = push(AppRoute.FlightDetails(id, direction), tab = AppTab.BOOKING)
    fun openTransferSelection() = push(AppRoute.TransferSelection, tab = AppTab.BOOKING)
    fun openFinalPackage() = push(AppRoute.FinalPackage, tab = AppTab.BOOKING)
    fun openBookingCheckout() = push(AppRoute.BookingCheckout, tab = AppTab.BOOKING)
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
    fun openUmrahPlan() = push(AppRoute.UmrahPlan, tab = AppTab.HOME)
    fun openUmrahAdvisor() = push(AppRoute.UmrahAdvisor, tab = AppTab.HOME)
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
    fun openGiftCards() = push(AppRoute.GiftCards, tab = AppTab.HOME)

    private fun push(route: AppRoute, tab: AppTab? = null) {
        _state.update { current ->
            val previous = current.route
            current.copy(
                currentTab = tab ?: current.currentTab,
                route = route,
                backStack = if (previous == route) current.backStack else current.backStack + previous,
                isImmersive = false,
                isSidebarOpen = false,
            )
        }
    }

    fun back(): Boolean {
        val current = _state.value
        val previous = current.backStack.lastOrNull() ?: return false
        _state.value = current.copy(route = previous, backStack = current.backStack.dropLast(1), isImmersive = false, isSidebarOpen = false)
        return true
    }

    fun setImmersive(value: Boolean) { _state.update { it.copy(isImmersive = value) } }
    fun openSidebar() { _state.update { it.copy(isSidebarOpen = true) } }
    fun closeSidebar() { _state.update { it.copy(isSidebarOpen = false) } }
}
