package com.iumrah.beta.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.iumrah.beta.IumrahApplication
import com.iumrah.beta.MainActivity
import com.iumrah.beta.R

class IumrahFirebaseMessagingService : FirebaseMessagingService() {
    private val appContainer get() = (application as IumrahApplication).container

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        appContainer.pushManager.updateToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val payload = buildMap {
            putAll(message.data)
            message.notification?.title?.takeIf { it.isNotBlank() }?.let { put("pushTitle", it) }
            message.notification?.body?.takeIf { it.isNotBlank() }?.let { put("pushBody", it) }
        }
        appContainer.pushManager.receiveRemotePayload(payload, opened = false)
        val event = appContainer.pushManager.state.value.lastEvent ?: return
        val bookings = appContainer.bookingStore
        val id = event.bookingID ?: event.destinationBookingID
        // Old-account payloads must not display private text after logout or account change.
        if (!bookings.acceptsPushOwner(event.pilgrimID, id)) return
        val silent = event.type.startsWith("chat_") && id != null &&
            bookings.careChatKeyForPush(id, event.pilgrimID)?.let { it == appContainer.pushManager.activeChatBookingID } == true
        postVisibleNotification(payload, silent)
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        // A full feed refresh happens at the next foreground/resume sync.
        appContainer.pushManager.refreshToken()
    }

    private fun postVisibleNotification(payload: Map<String, String>, silent: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        createNotificationChannel(this)
        val title = payload["pushTitle"] ?: payload["title"] ?: "iumrah"
        val body = payload["pushBody"] ?: payload["body"] ?: "Your trip has an update."
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            IumrahPushManager.KNOWN_KEYS.forEach { key -> payload[key]?.let { putExtra(key, it) } }
        }
        val requestCode = (payload["notificationID"] ?: payload["bookingID"] ?: payload.toString()).hashCode()
        val pending = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, IumrahPushManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_iumrah_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSilent(silent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(this).notify(requestCode, notification)
    }

    companion object {
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                IumrahPushManager.CHANNEL_ID,
                IumrahPushManager.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Booking changes, trip updates and iumrah Signal"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
