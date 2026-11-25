package com.iumrah.beta.data.notification

import android.content.Context
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.models.booking.StoredBookingSession
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.models.notification.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json


data class ClientNotificationState(
    val notifications: List<ClientSystemNotification> = emptyList(),
    val dismissedHomeIDs: Set<String> = emptySet(),
    val bookingReceipts: List<ClientSystemNotification> = emptyList(),
    val isSyncing: Boolean = false,
    val lastError: String? = null,
    val pushProviderReady: Boolean? = null,
) {
    val inbox: List<ClientSystemNotification> get() = (bookingReceipts + notifications).distinctBy { it.id }.sortedWith(
        compareBy<ClientSystemNotification> { it.isRead }
            .thenByDescending { it.sentAt ?: it.createdAt }
            .thenByDescending { it.id },
    )
    val home: List<ClientSystemNotification> get() = inbox.filterNot { dismissedHomeIDs.contains(it.id) }
    val unreadCount: Int get() = inbox.count { !it.isRead }
}

class ClientNotificationStore(
    context: Context, private val api: APIClient,
    private val liveToken: () -> String?, private val visibleTripIDs: () -> List<String>,
) {
    private fun liveScope() = NotificationScopeGate.fingerprint(liveToken(), visibleTripIDs())
    private val gate = NotificationScopeGate(liveScope())
    private val registrationMutex = Mutex()
    private val localRead = mutableSetOf<String>()
    private val prefs = context.applicationContext.getSharedPreferences("iumrah.notifications.v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    val installationID: String = prefs.getString(KEY_INSTALLATION, null)?.takeIf { it.length >= 16 }
        ?: UUID.randomUUID().toString().lowercase().also { prefs.edit().putString(KEY_INSTALLATION, it).apply() }

    private val initialNotifications = runCatching {
        prefs.getString(KEY_CACHE, null)?.takeIf { prefs.getString(KEY_SCOPE, null) == gate.scope }?.let { json.decodeFromString(ListSerializer(ClientSystemNotification.serializer()), it) }
    }.getOrNull().orEmpty()
    private val initialDismissed = if (prefs.getString(KEY_SCOPE, null) == gate.scope) prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty().toSet() else emptySet()
    private val _state = MutableStateFlow(ClientNotificationState(initialNotifications, initialDismissed))
    val state: StateFlow<ClientNotificationState> = _state.asStateFlow()
    private val readReceiptIDs = prefs.getStringSet(KEY_READ_RECEIPTS, emptySet()).orEmpty().toMutableSet()

    /** iOS Booking & Notifications 14: a locally derived receipt, not a fabricated backend push.
     * Only sessions currently visible to this device may generate receipts, and the token is
     * never placed into notification text or cached in notification preferences. */
    fun syncBookingReceipts(sessions: List<StoredBookingSession>, language: AppLanguage) {
        reconcileScope()
        val now = Instant.now()
        val receipts = sessions.asSequence().filter { it.hasServerIdentity &&
            (it.accessToken.isNotBlank() || it.displayPilgrimID != null) && it.effectiveStatus != "DELETED" }
            .mapNotNull { session ->
                val created = parseServerInstant(session.booking.createdAt) ?: return@mapNotNull null
                // Only the last 30 days. Reject future timestamps instead of pinning a receipt forever.
                if (created.isAfter(now.plusSeconds(300)) || created.isBefore(now.minus(30, ChronoUnit.DAYS))) return@mapNotNull null
                val id = "local-booking-created:${session.id}"
                val title = when (language) {
                    AppLanguage.RUSSIAN -> "Бронирование создано "
                    AppLanguage.UZBEK -> "Bron yaratildi "
                    AppLanguage.UZBEK_CYRILLIC -> "Брон яратилди "
                    else -> "Booking created "
                } + session.displayBookingNumber
                val body = when (language) {
                    AppLanguage.RUSSIAN -> "Ваше путешествие сохранено. Откройте бронирование, чтобы узнать актуальный статус."
                    AppLanguage.UZBEK -> "Safaringiz saqlandi. Bron holatini ko‘rish uchun uni oching."
                    AppLanguage.UZBEK_CYRILLIC -> "Сафарингиз сақланди. Брон ҳолатини кўриш учун уни очинг."
                    else -> "Your trip is saved. Open your booking for its current status."
                }
                ClientSystemNotification(id=id,title=title,body=body,targetScope="booking",
                    destination="booking",destinationBookingID=session.id,createdBy="iumrah",status="published",
                    createdAt=session.booking.createdAt,sentAt=session.booking.createdAt,
                    expiresAt=created.plus(30, ChronoUnit.DAYS).toString(),isRead=id in readReceiptIDs)
            }.sortedByDescending { it.sentAt }.toList()
        _state.update { it.copy(bookingReceipts = receipts) }
    }

    private fun parseServerInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()


    /** Clear cached private feed on account/trip changes, even if offline. */
    fun reconcileScope() {
        if (gate.switch(liveScope())) {
            localRead.clear()
            _state.update { it.copy(notifications = emptyList(), dismissedHomeIDs = emptySet(), bookingReceipts = emptyList(), isSyncing = false, lastError = null, pushProviderReady = null) }
            persist()
        }
    }

    suspend fun sync(deviceToken: String?, accountToken: String?, hasTrip: Boolean, locale: String) {
        reconcileScope()
        if (accountToken != liveToken()) return
        val ticket = gate.register()
        _state.update { it.copy(isSyncing = true, lastError = null) }
        fun current() = gate.registrationCurrent(ticket, liveScope())
        try {
            registrationMutex.withLock {
                if (!current()) return@withLock
                val request = ClientNotificationDeviceRegistration(
                    installationID = installationID,
                    // FCM tokens are case-sensitive.
                    deviceToken = deviceToken?.trim()?.takeIf(String::isNotEmpty),
                    locale = locale, hasTrip = hasTrip,
                )
                var failure: Throwable? = null
                for (attempt in 0..2) {
                    if (!current()) return@withLock
                    try {
                        val response = api.post<ClientNotificationDeviceResponse, ClientNotificationDeviceRegistration>(
                            "/api/catalog/hotels/client/notifications/devices", request, authorizationHeaders(accountToken),
                        )
                        currentCoroutineContext().ensureActive()
                        if (!current()) return@withLock
                        if (!response.ok) throw APIException.InvalidResponse
                        _state.update { it.copy(pushProviderReady = response.ready, lastError = null) }
                        refresh(accountToken)
                        return@withLock
                    } catch (cancelled: CancellationException) { throw cancelled }
                      catch (error: Exception) {
                        failure = error
                        if (attempt < 2 && current()) kotlinx.coroutines.delay(650L * (attempt + 1))
                    }
                }
                if (current()) _state.update { it.copy(lastError = failure?.message, pushProviderReady = null) }
            }
        } finally {
            if (current()) _state.update { it.copy(isSyncing = false) }
        }
    }

    suspend fun refresh(accountToken: String?) {
        reconcileScope()
        if (accountToken != liveToken()) return
        val ticket = gate.refresh()
        fun current() = gate.feedCurrent(ticket, liveScope())
        try {
            val response = api.get<ClientNotificationFeedResponse>(
                "/api/catalog/hotels/client/notifications/feed", query = mapOf("installationID" to installationID),
                headers = authorizationHeaders(accountToken),
            )
            currentCoroutineContext().ensureActive()
            if (!current()) return
            if (!response.ok) throw APIException.InvalidResponse
            _state.update { it.copy(notifications = response.notifications.map { n -> if (n.id in localRead) n.copy(isRead = true) else n }, lastError = null) }
            persist()
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (error: Exception) { if (current()) _state.update { it.copy(lastError = error.message) } }
    }

    suspend fun markOpened(notification: ClientSystemNotification, accountToken: String?) {
        reconcileScope()
        if (accountToken != liveToken() || state.value.inbox.none { it.id == notification.id }) return
        if (notification.id.startsWith("local-booking-created:")) {
            readReceiptIDs.add(notification.id)
            prefs.edit().putStringSet(KEY_READ_RECEIPTS, readReceiptIDs.toSet()).apply()
            _state.update { current -> current.copy(bookingReceipts = current.bookingReceipts.map {
                if (it.id == notification.id) it.copy(isRead=true) else it
            }) }
            return // Local receipt must not call remote /read API.
        }
        localRead.add(notification.id)
        _state.update { current -> current.copy(notifications = current.notifications.map { if (it.id == notification.id) it.copy(isRead = true) else it }) }
        persist()
        try {
            api.post<ClientNotificationReadResponse, ClientNotificationReadRequest>(
                "/api/catalog/hotels/client/notifications/feed/${notification.id}/read",
                ClientNotificationReadRequest(installationID), authorizationHeaders(accountToken),
            )
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { /* Keep optimistic read for this scope. */ }
    }

    fun dismissFromHome(id: String) {
        reconcileScope()
        if (state.value.inbox.none { it.id == id }) return
        _state.update { it.copy(dismissedHomeIDs = it.dismissedHomeIDs + id) }
        persist()
    }

    fun restoreToHome(id: String) {
        reconcileScope()
        _state.update { it.copy(dismissedHomeIDs = it.dismissedHomeIDs - id) }
        persist()
    }

    fun notification(id: String): ClientSystemNotification? = if (gate.scope == liveScope()) _state.value.inbox.firstOrNull { it.id == id } else null

    private fun authorizationHeaders(token: String?): Map<String, String> = token?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

    private fun persist() {
        val snapshot = _state.value
        prefs.edit()
            .putString(KEY_SCOPE, gate.scope)
            .putString(KEY_CACHE, json.encodeToString(ListSerializer(ClientSystemNotification.serializer()), snapshot.notifications.take(20)))
            .putStringSet(KEY_DISMISSED, snapshot.dismissedHomeIDs)
            .apply()
    }

    companion object {
        private const val KEY_INSTALLATION = "installation"
        private const val KEY_CACHE = "cache"
        private const val KEY_SCOPE = "cache_scope_v2"
        private const val KEY_DISMISSED = "dismissed"
        private const val KEY_READ_RECEIPTS = "local_booking_receipts_read"
    }
}
