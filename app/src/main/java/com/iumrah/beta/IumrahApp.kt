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
import com.iumrah.beta.core.design.IumrahMotion
import com.iumrah.beta.core.design.IumrahTheme
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.push.IumrahPushEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import com.iumrah.beta.ui.onboarding.OnboardingFlow
import com.iumrah.beta.ui.shell.AppShell

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
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.pushManager.markNotificationPermissionRequested()
        permissionRevision += 1
    }

    LaunchedEffect(settings.hasCompletedOnboarding) {
        if (settings.hasCompletedOnboarding) {
            container.accountStore.restore()
        }
    }

    LaunchedEffect(settings.hasCompletedOnboarding, accountState.iumrahID) {
        if (settings.hasCompletedOnboarding && accountState.isAuthenticated) {
            runCatching { container.bookingStore.restoreAccountTrips() }
        }
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
        bookingState.sessions.map { it.id },
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

    LaunchedEffect(pushState.eventRevision) {
        if (!settings.hasCompletedOnboarding || pushState.eventRevision == 0L) return@LaunchedEffect
        container.bookingStore.refreshAll()
        container.notificationStore.refresh(container.accountStore.bearerToken)
    }

    LaunchedEffect(pushState.openRevision) {
        if (!settings.hasCompletedOnboarding || pushState.openRevision == 0L) return@LaunchedEffect
        routeOpenedPush(pushState.lastOpenedEvent, container)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!settings.hasCompletedOnboarding) return@LifecycleEventEffect
        container.pushManager.refreshToken()
        scope.launch {
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

    IumrahTheme(appearance = settings.appearance) {
        if (!settings.isLoaded) {
            LaunchSurface()
            return@IumrahTheme
        }

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
                OnboardingFlow(
                    language = settings.language,
                    onLanguageChange = container.settingsStore::setLanguage,
                    onFinished = container.settingsStore::completeOnboarding,
                )
            }
        }
    }
}

private suspend fun routeOpenedPush(event: IumrahPushEvent?, container: com.iumrah.beta.core.di.IumrahAppContainer) {
    event ?: return
    if (event.type == "system_notification") {
        when (event.destination) {
            "hotels" -> container.chromeStore.navigate(AppTab.HOTELS)
            "bookings" -> container.chromeStore.navigate(AppTab.BOOKING)
            "care" -> container.chromeStore.navigate(AppTab.CARE)
            "account" -> container.chromeStore.navigate(AppTab.ACCOUNT)
            "booking" -> {
                val id = event.destinationBookingID
                if (!id.isNullOrBlank() && container.bookingStore.booking(id) != null) container.chromeStore.openBookingDetail(id)
                else container.chromeStore.navigate(AppTab.BOOKING)
            }
            else -> container.chromeStore.navigate(AppTab.HOME)
        }
        event.notificationID?.let { id ->
            container.notificationStore.notification(id)?.let { notification ->
                container.notificationStore.markOpened(notification, container.accountStore.bearerToken)
            }
        }
        return
    }
    event.bookingID?.let { id ->
        if (event.type.startsWith("chat_")) container.chromeStore.navigate(AppTab.CARE)
        else if (container.bookingStore.booking(id) != null) container.chromeStore.openBookingDetail(id)
        else container.chromeStore.navigate(AppTab.BOOKING)
    }
}

@Composable
private fun LaunchSurface() {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF1B1D20) else Color(0xFFF6F7F8)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.iumrah_launch_wordmark),
            contentDescription = "iumrah",
            modifier = Modifier.width(220.dp).height(90.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
