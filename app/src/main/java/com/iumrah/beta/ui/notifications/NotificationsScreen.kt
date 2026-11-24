package com.iumrah.beta.ui.notifications
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.IumrahApplication

import com.iumrah.beta.ui.cupertino.CupertinoSymbol

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.notification.ClientNotificationStore
import com.iumrah.beta.models.notification.ClientSystemNotification
import com.iumrah.beta.ui.components.IumrahPill
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    language: AppLanguage,
    notifications: ClientNotificationStore,
    accountStore: IumrahAccountStore,
    bookingStore: BookingStore,
    chrome: AppChromeStore,
) {
    val state by notifications.state.collectAsState()
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as IumrahApplication
    val pushState by app.container.pushManager.state.collectAsState()
    var permissionRequested by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRequested = true
        app.container.pushManager.markNotificationPermissionRequested()
        app.container.pushManager.refreshToken()
    }

    LaunchedEffect(pushState.deviceToken) {
        notifications.sync(
            deviceToken = pushState.deviceToken,
            accountToken = accountStore.bearerToken,
            hasTrip = bookingStore.state.value.sessions.isNotEmpty(),
            locale = language.code,
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(24.dp, 10.dp, 24.dp, 44.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IumrahPressable(onClick = chrome::back, modifier = Modifier.size(44.dp), cornerRadius = 99.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.ChevronLeft, "Back") }
            }
        }
        item {
            Text("Trip notifications", style = MaterialTheme.typography.headlineLarge)
            Text("Booking updates from the same iumrah backend.", color = MaterialTheme.colorScheme.onBackground.copy(alpha=.54f))
        }
        if (Build.VERSION.SDK_INT >= 33 && !permissionRequested) {
            item { IumrahPrimaryButton("Enable Android notifications") { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
        }
        if (state.isSyncing && state.inbox.isEmpty()) item { CircularProgressIndicator() }
        if (state.inbox.isEmpty() && !state.isSyncing) item { Text("No notifications yet", color = MaterialTheme.colorScheme.onBackground.copy(alpha=.52f)) }
        items(state.inbox, key = { it.id }) { notification ->
            NotificationCard(notification) {
                scope.launch { notifications.markOpened(notification, accountStore.bearerToken) }
                routeNotification(notification, bookingStore, chrome)
            }
        }
        state.lastError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable private fun NotificationCard(value: ClientSystemNotification, onOpen: () -> Unit) {
    IumrahPressable(onClick = onOpen, modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp, background = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.BellBadge, null) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (!value.isRead) IumrahPill("New")
                }
                Text(value.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.58f), style = MaterialTheme.typography.bodyMedium)
                Text(value.sentAt ?: value.createdAt, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.38f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

internal fun routeNotification(value: ClientSystemNotification, bookings: BookingStore, chrome: AppChromeStore) {
    when (value.destination) {
        "hotels" -> chrome.navigate(AppTab.HOTELS)
        "bookings" -> chrome.navigate(AppTab.BOOKING)
        "care", "chat" -> {
            val id = value.destinationBookingID
            if (id == null) chrome.navigate(AppTab.CARE)
            else bookings.careChatKey(id)?.let(chrome::openBookingChat)
        }
        "account" -> chrome.navigate(AppTab.ACCOUNT)
        "booking" -> value.destinationBookingID?.takeIf { bookings.canOpenBookingFromPush(it) }?.let(chrome::openBookingDetail)
        else -> chrome.navigate(AppTab.HOME)
    }
}
