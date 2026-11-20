package com.iumrah.beta

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.widget.IumrahWidgetSync
import com.iumrah.beta.core.analytics.IumrahIntelligence
import com.iumrah.beta.core.navigation.AppRoute
import com.iumrah.beta.core.design.IumrahMotion
import com.iumrah.beta.core.design.IumrahTheme
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.push.IumrahPushEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import com.iumrah.beta.ui.onboarding.BrandWelcomeOnboarding
import com.iumrah.beta.ui.shell.AppShell
import com.iumrah.beta.ui.components.IumrahSignatureLaunchHost

@Composable
fun IumrahApp() {
    val app = LocalContext.current.applicationContext as IumrahApplication
    val container = remember(app) { app.container }
    val settings by container.settingsStore.state.collectAsState()
    val chrome by container.chromeStore.state.collectAsState()
    val accountState by container.accountStore.state.collectAsState()
    val bookingState by container.bookingStore.state.collectAsState()
    val pushState by container.pushManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var permissionRevision by remember { mutableIntStateOf(0) }
    var accountRestored by remember { androidx.compose.runtime.mutableStateOf(false) }
    val telemetryScreen = if (!settings.hasCompletedOnboarding) "onboarding" else when (chrome.route) {
        AppRoute.Root -> when (chrome.currentTab) {
            AppTab.HOME -> "home"
            AppTab.HOTELS -> "hotels"
            AppTab.BOOKING -> "bookings"
            AppTab.CARE -> "care"
            AppTab.ACCOUNT -> "account"
        }
        AppRoute.BookingCheckout -> "checkout"
        AppRoute.TripBuilder, AppRoute.GuidedFlights -> "flight_builder"
        AppRoute.NuiteeJourneyHotels -> "hotel_builder"
        AppRoute.NuiteeJourneyReview -> "journey_review"
        is AppRoute.HotelDetail -> "hotel_details"
        else -> "navigation"
    }
    LaunchedEffect(settings.isLoaded) {
        if (settings.isLoaded) IumrahIntelligence.start(app, telemetryScreen, settings.hasCompletedOnboarding)
    }
    LaunchedEffect(settings.isLoaded, telemetryScreen) {
        if (settings.isLoaded) IumrahIntelligence.screenChanged(telemetryScreen)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { IumrahIntelligence.enterBackground() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.pushManager.markNotificationPermissionRequested()
        permissionRevision += 1
    }

    LaunchedEffect(settings.hasCompletedOnboarding) {
        if (settings.hasCompletedOnboarding) {
            container.accountStore.restore()
            accountRestored = true
        }
    }

    LaunchedEffect(settings.hasCompletedOnboarding, accountState.iumrahID, accountState.isRestoring) {
        container.bookingStore.reconcileAccountScope()
        if (settings.hasCompletedOnboarding && accountState.isAuthenticated && !accountState.isRestoring) {
            container.bookingStore.linkLocalBookingsToAccount()
            container.bookingStore.restoreAccountTrips()
        }
    }
    LaunchedEffect(settings.hasCompletedOnboarding, accountState.iumrahID, bookingState.sessions.map { it.id to it.accessToken }) {
        if (settings.hasCompletedOnboarding && accountState.isAuthenticated) container.bookingStore.linkLocalBookingsToAccount()
    }

    LaunchedEffect(settings.hasCompletedOnboarding, pushState.isConfigured) {
        if (!settings.hasCompletedOnboarding) return@LaunchedEffect
        container.pushManager.initialize()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && container.pushManager.shouldRequestNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(
        settings.hasCompletedOnboarding,
        accountState.iumrahID,
        bookingState.sessions.map { Triple(it.id, it.accessToken, it.pilgrimID) },
        settings.language.code,
        pushState.deviceToken,
        permissionRevision,
    ) {
        if (!settings.hasCompletedOnboarding) return@LaunchedEffect
        val token = pushState.deviceToken
        if (!token.isNullOrBlank()) {
            container.bookingStore.syncPushSubscriptions(token, settings.language.code)
        }
        container.notificationStore.sync(
            deviceToken = token,
            accountToken = container.accountStore.bearerToken,
            hasTrip = bookingState.sessions.isNotEmpty(),
            locale = settings.language.code,
        )
    }

    // Widgets only receive non-secret summaries; refresh after booking, identity or locale changes.
    LaunchedEffect(settings.isLoaded, settings.language.code, bookingState.sessions, accountState.account) {
        if (settings.isLoaded) {
            IumrahWidgetSync.sync(app, bookingState.sessions, accountState.account, settings.language.code)
        }
    }

    // Keep iOS-local booking receipts synchronized with the same scoped sessions shown in Booking.
    LaunchedEffect(settings.isLoaded, settings.language, bookingState.sessions) {
        if (settings.isLoaded) container.notificationStore.syncBookingReceipts(bookingState.sessions, settings.language)
    }

    LaunchedEffect(accountState.account?.iumrahID) { container.chatService.clearCache() }

    LaunchedEffect(pushState.eventRevision) {
        if (!settings.hasCompletedOnboarding || pushState.eventRevision == 0L) return@LaunchedEffect
        container.chatService.requestRefresh()
        container.bookingStore.refreshAll()
        container.notificationStore.refresh(container.accountStore.bearerToken)
    }

    LaunchedEffect(pushState.openRevision, accountRestored, settings.hasCompletedOnboarding, accountState.isRestoring) {
        if (!settings.hasCompletedOnboarding || !accountRestored || accountState.isRestoring) return@LaunchedEffect
        val event = pushState.lastOpenedEvent ?: return@LaunchedEffect
        val revision = pushState.openRevision
        val id = event.bookingID ?: event.destinationBookingID
        if (id != null && container.bookingStore.booking(id) == null && container.accountStore.bearerToken != null) {
            container.bookingStore.restoreAccountTrips()
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (container.pushManager.state.value.openRevision != revision) return@LaunchedEffect
        routeOpenedPush(event, container)
        container.pushManager.acknowledgeOpened(revision)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        IumrahIntelligence.resume(telemetryScreen)
        container.connectivityMonitor.refresh()
        if (!settings.hasCompletedOnboarding) return@LifecycleEventEffect
        container.pushManager.refreshToken()
        scope.launch {
            container.bookingStore.refreshAll()
            if (container.accountStore.state.value.isAuthenticated) container.bookingStore.linkLocalBookingsToAccount()
            val token = container.pushManager.state.value.deviceToken
            if (!token.isNullOrBlank()) container.bookingStore.syncPushSubscriptions(token, settings.language.code)
            container.notificationStore.sync(
                deviceToken = token,
                accountToken = container.accountStore.bearerToken,
                hasTrip = container.bookingStore.state.value.sessions.isNotEmpty(),
                locale = settings.language.code,
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides
        if (settings.language.isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
    IumrahTheme(appearance = settings.appearance) {
        if (!settings.isLoaded) {
            LaunchSurface()
            return@IumrahTheme
        }

        IumrahSignatureLaunchHost(settings.hasCompletedOnboarding, skip = chrome.route != AppRoute.Root || chrome.currentTab != AppTab.HOME || pushState.openRevision != 0L) {
        AnimatedContent(
            targetState = settings.hasCompletedOnboarding,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState) {
                    (fadeIn(IumrahMotion.rootFade) + scaleIn(IumrahMotion.content, initialScale = .985f))
                        .togetherWith(fadeOut(IumrahMotion.rootFade) + scaleOut(IumrahMotion.content, targetScale = 1.015f))
                } else {
                    (fadeIn(IumrahMotion.rootFade) + scaleIn(IumrahMotion.content, initialScale = 1.015f))
                        .togetherWith(fadeOut(IumrahMotion.fastFade) + scaleOut(IumrahMotion.content, targetScale = .985f))
                }
            },
            label = "onboarding-root-transition",
        ) { completed ->
            if (completed) {
                AppShell(
                    language = settings.language,
                    chrome = container.chromeStore,
                    chromeState = chrome,
                    accountStore = container.accountStore,
                    hotelCatalog = container.hotelCatalogService,
                    packageEngine = container.packageEngine,
                    journey = container.journeyStore,
                    airports = container.airportSearchService,
                    flightDiscovery = container.aviasalesFlightDiscoveryService,
                    flightInventory = container.flightInventoryProvider,
                    curatedFlights = container.curatedFlightRecommendationService,
                    packageGenerator = container.packageGenerator,
                    bookingStore = container.bookingStore,
                    accountService = container.accountService,
                    chatService = container.chatService,
                    notifications = container.notificationStore,
                    settingsStore = container.settingsStore,
                )
            } else {
                BrandWelcomeOnboarding(
                    language = settings.language,
                    onLanguageChange = container.settingsStore::setLanguage,
                    onFinished = { IumrahIntelligence.record("onboarding_completed", "onboarding"); container.settingsStore.completeOnboarding() },
                )
            }
        }
        }
    }
    }
}

private suspend fun routeOpenedPush(event: IumrahPushEvent?, container: com.iumrah.beta.core.di.IumrahAppContainer) {
    event ?: return
    val store = container.bookingStore
    val chrome = container.chromeStore
    val id = event.bookingID ?: event.destinationBookingID
    if (event.type.startsWith("chat_")) {
        val key = id?.let { store.careChatKeyForPush(it, event.pilgrimID) } ?: return
        chrome.openBookingChat(key)
        return
    }
    if (!store.acceptsPushOwner(event.pilgrimID, id)) return
    fun openBooking(bookingID: String?) {
        if (bookingID != null && store.canOpenBookingFromPush(bookingID)) chrome.openBookingPackageFromLink(bookingID)
        else chrome.navigate(AppTab.BOOKING)
    }
    if (event.type == "system_notification") {
        when (event.destination) {
            "hotels" -> chrome.navigate(AppTab.HOTELS)
            "bookings" -> chrome.navigate(AppTab.BOOKING)
            "care" -> chrome.navigate(AppTab.CARE)
            "account" -> chrome.navigate(AppTab.ACCOUNT)
            "booking" -> openBooking(event.destinationBookingID ?: event.bookingID)
            else -> chrome.navigate(AppTab.HOME)
        }
        event.notificationID?.let { notificationID ->
            val token = container.accountStore.bearerToken
            if (container.notificationStore.notification(notificationID) == null) container.notificationStore.refresh(token)
            if (token == container.accountStore.bearerToken) container.notificationStore.notification(notificationID)?.let {
                container.notificationStore.markOpened(it, token)
            }
        }
        return
    }
    if (id != null) openBooking(id)
}

@Composable
private fun LaunchSurface() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF080808)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.iumrah_header_wordmark_dark),
            contentDescription = "iumrah",
            modifier = Modifier.width(220.dp).height(90.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
