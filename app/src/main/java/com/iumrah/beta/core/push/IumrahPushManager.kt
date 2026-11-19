package com.iumrah.beta.core.push

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.iumrah.beta.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Android counterpart of the iOS PushNotificationManager. */
data class IumrahPushEvent(
    val type: String = "notification",
    val bookingID: String? = null,
    val status: String? = null,
    val notificationID: String? = null,
    val destination: String? = null,
    val destinationBookingID: String? = null,
    val pilgrimID: String? = null,
)

data class IumrahPushState(
    val isConfigured: Boolean = false,
    val isFetchingToken: Boolean = false,
    val deviceToken: String? = null,
    val lastError: String? = null,
    val lastEvent: IumrahPushEvent? = null,
    val lastOpenedEvent: IumrahPushEvent? = null,
    val eventRevision: Long = 0,
    val openRevision: Long = 0,
)

class IumrahPushManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        IumrahPushState(
            isConfigured = hasFirebaseConfig(),
            deviceToken = prefs.getString(KEY_TOKEN, null)?.trim()?.takeIf(String::isNotEmpty),
        ),
    )
    val state: StateFlow<IumrahPushState> = _state.asStateFlow()

    /**
     * Firebase is initialized manually so this repository keeps compiling before the
     * Android Firebase app is provisioned. No fake project identifiers are ever used.
     */
    fun initialize() {
        if (!hasFirebaseConfig()) {
            _state.update { it.copy(isConfigured = false, isFetchingToken = false) }
            return
        }
        runCatching {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.IUMRAH_FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.IUMRAH_FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.IUMRAH_FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.IUMRAH_FIREBASE_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(appContext, options)
                    ?: error("Firebase could not be initialized")
            }
            _state.update { it.copy(isConfigured = true, lastError = null) }
            refreshToken()
        }.onFailure { error ->
            _state.update { it.copy(isConfigured = true, isFetchingToken = false, lastError = error.message) }
        }
    }

    fun refreshToken() {
        if (!hasFirebaseConfig()) return
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            initialize()
            return
        }
        _state.update { it.copy(isFetchingToken = true, lastError = null) }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.trim().orEmpty()
                if (token.isNotEmpty()) updateToken(token)
                else _state.update { it.copy(isFetchingToken = false, lastError = "FCM returned an empty device token") }
            } else {
                _state.update { it.copy(isFetchingToken = false, lastError = task.exception?.message ?: "FCM token registration failed") }
            }
        }
    }

    fun updateToken(rawToken: String) {
        val token = rawToken.trim()
        if (token.isEmpty()) return
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _state.update { it.copy(deviceToken = token, isFetchingToken = false, lastError = null, isConfigured = true) }
    }

    fun receiveRemotePayload(payload: Map<String, String>, opened: Boolean = false) {
        val event = eventFrom(payload)
        _state.update { current ->
            current.copy(
                lastEvent = event,
                eventRevision = current.eventRevision + 1,
                lastOpenedEvent = if (opened) event else current.lastOpenedEvent,
                openRevision = if (opened) current.openRevision + 1 else current.openRevision,
            )
        }
    }

    fun acknowledgeOpened(revision: Long) {
        _state.update { if (it.openRevision == revision) it.copy(lastOpenedEvent = null) else it }
    }

    /** Accepts both our own PendingIntent extras and FCM background data extras. */
    fun receiveOpenedIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        val payload = KNOWN_KEYS.mapNotNull { key ->
            extras.get(key)?.toString()?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()
        if (payload.isEmpty()) return
        receiveRemotePayload(payload, opened = true)
    }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun shouldRequestNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            _state.value.isConfigured &&
            !hasNotificationPermission() &&
            !prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)

    fun markNotificationPermissionRequested() {
        prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    private fun eventFrom(payload: Map<String, String>): IumrahPushEvent = IumrahPushEvent(
        type = payload["type"]?.trim()?.takeIf(String::isNotEmpty) ?: "notification",
        bookingID = payload["bookingID"]?.trim()?.takeIf(String::isNotEmpty),
        status = payload["status"]?.trim()?.takeIf(String::isNotEmpty),
        notificationID = payload["notificationID"]?.trim()?.takeIf(String::isNotEmpty),
        destination = payload["destination"]?.trim()?.takeIf(String::isNotEmpty),
        destinationBookingID = payload["destinationBookingID"]?.trim()?.takeIf(String::isNotEmpty),
        pilgrimID = payload["pilgrimID"]?.trim()?.takeIf(String::isNotEmpty),
    )

    private fun hasFirebaseConfig(): Boolean = listOf(
        BuildConfig.IUMRAH_FIREBASE_API_KEY,
        BuildConfig.IUMRAH_FIREBASE_APP_ID,
        BuildConfig.IUMRAH_FIREBASE_PROJECT_ID,
        BuildConfig.IUMRAH_FIREBASE_SENDER_ID,
    ).all { it.isNotBlank() }

    companion object {
        const val CHANNEL_ID = "iumrah_trip_updates"
        const val CHANNEL_NAME = "iumrah trip updates"
        private const val PREFS = "iumrah.push.android.v1"
        private const val KEY_TOKEN = "fcm-device-token"
        private const val KEY_PERMISSION_REQUESTED = "notification-permission-requested"
        val KNOWN_KEYS = listOf("type", "bookingID", "status", "notificationID", "destination", "destinationBookingID", "pilgrimID")
    }
}
