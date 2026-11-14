package com.iumrah.beta.models.notification

import kotlinx.serialization.Serializable

@Serializable
data class ClientSystemNotification(
    val id: String,
    val title: String,
    val body: String,
    val targetScope: String,
    val destination: String,
    val destinationBookingID: String? = null,
    val createdBy: String,
    val status: String,
    val matchedDevices: Int = 0,
    val pushSentCount: Int = 0,
    val pushFailedCount: Int = 0,
    val createdAt: String,
    val sentAt: String? = null,
    val expiresAt: String,
    val isRead: Boolean = false,
)

@Serializable data class ClientNotificationDeviceRegistration(
    val installationID: String,
    val deviceToken: String? = null,
    val environment: String = "production",
    val appBundleID: String = com.iumrah.beta.BuildConfig.APPLICATION_ID,
    val locale: String,
    val hasTrip: Boolean,
)
@Serializable data class ClientNotificationDeviceResponse(val ok: Boolean, val ready: Boolean? = null)
@Serializable data class ClientNotificationFeedResponse(val ok: Boolean, val notifications: List<ClientSystemNotification>)
@Serializable data class ClientNotificationReadRequest(val installationID: String)
@Serializable data class ClientNotificationReadResponse(val ok: Boolean)
