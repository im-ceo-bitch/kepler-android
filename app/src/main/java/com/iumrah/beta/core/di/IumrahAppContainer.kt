package com.iumrah.beta.core.di

import android.content.Context
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.push.IumrahPushManager
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.IumrahConnectivityMonitor
import com.iumrah.beta.core.security.IumrahAccountDeviceIdentity
import com.iumrah.beta.core.security.SecureJsonStore
import com.iumrah.beta.core.settings.AppSettingsStore
import com.iumrah.beta.data.account.IumrahAccountService
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingService
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.chat.ChatService
import com.iumrah.beta.data.flight.*
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.data.notification.ClientNotificationStore
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.pricing.PackageGenerator

class IumrahAppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val secureStore = SecureJsonStore(appContext)

    val apiClient = APIClient()
    val connectivityMonitor = IumrahConnectivityMonitor(appContext)
    val deviceIdentity = IumrahAccountDeviceIdentity(appContext, secureStore)
    val referralStore = com.iumrah.beta.data.account.ReferralStore(appContext)
    val accountService = IumrahAccountService(apiClient, deviceIdentity, referralStore::headers)
    val accountStore = IumrahAccountStore(accountService, secureStore, referralStore::clear)
    val hotelCatalogService = HotelCatalogService(apiClient)
    val packageEngine = RemotePackageEngineClient(apiClient)
    val airportSearchService = AirportSearchService(apiClient)
    val flightFareCalendarService = FlightFareCalendarService(apiClient)
    val aviasalesFlightDiscoveryService = AviasalesFlightDiscoveryService(apiClient)
    val curatedFlightRecommendationService = CuratedFlightRecommendationService(apiClient)
    val flightInventoryProvider = IgnavFlightInventoryProvider(apiClient)
    val journeyStore = JourneyStore()
    internal val manualJourney = com.iumrah.beta.domain.journey.ManualJourneyStore()
    val packageGenerator = PackageGenerator(packageEngine)
    val bookingService = BookingService(apiClient)
    val bookingStore = BookingStore(bookingService, accountStore, secureStore, packageEngine)
    val savedPackages = com.iumrah.beta.data.packageflow.SavedPackageStore(apiClient, secureStore)
    val chatService = ChatService(apiClient)
    val notificationStore = ClientNotificationStore(appContext, apiClient, { accountStore.bearerToken }, { bookingStore.state.value.sessions.mapNotNull { bookingStore.booking(it.id)?.id } })
    val pushManager = IumrahPushManager(appContext)
    val settingsStore = AppSettingsStore(appContext)
    val chromeStore = AppChromeStore()
}
