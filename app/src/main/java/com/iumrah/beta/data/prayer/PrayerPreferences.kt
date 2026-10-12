package com.iumrah.beta.data.prayer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.iumrah.beta.R
import com.iumrah.beta.domain.prayer.*
import java.time.Instant
import java.time.LocalDate

/** Device-local preferences mirroring iOS IumrahPrayerState (four existing app languages). */
data class PrayerAlert(val enabled: Boolean = false, val offset: Int = 0, val sound: Boolean = true, val earlyMinutes: Int = 0)

data class PrayerOptions(
    val place: PrayerPlace = PrayerPlace.presets[0],
    val method: PrayerMethod = PrayerMethod.UMM_AL_QURA,
    val hanafi: Boolean = false,
    val wallpaper: Int = 4,
    val alerts: Map<PrayerKind, PrayerAlert> = emptyMap(),
)

class PrayerPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("iumrah_prayer_times_v2", Context.MODE_PRIVATE)
    fun read(): PrayerOptions {
        val placeId = prefs.getString("place", "makkah")
        val place = PrayerPlace.presets.firstOrNull { it.id == placeId }
            ?: runCatching {
                PrayerPlace(
                    "custom", prefs.getString("name", "Current location") ?: "Current location",
                    java.lang.Double.longBitsToDouble(prefs.getLong("lat", 0)),
                    java.lang.Double.longBitsToDouble(prefs.getLong("lon", 0)),
                    prefs.getString("zone", "Asia/Riyadh") ?: "Asia/Riyadh",
                ).takeIf { it.valid && runCatching { it.zone }.isSuccess }
            }.getOrNull() ?: PrayerPlace.presets[0]
        val method = runCatching { PrayerMethod.valueOf(prefs.getString("method", "UMM_AL_QURA") ?: "UMM_AL_QURA") }.getOrDefault(PrayerMethod.UMM_AL_QURA)
        val alerts = PrayerKind.entries.associateWith { kind ->
            val k = kind.name
            PrayerAlert(prefs.getBoolean("$k.on", false), prefs.getInt("$k.offset", 0).coerceIn(-30, 30), prefs.getBoolean("$k.sound", true), prefs.getInt("$k.early", 0).takeIf { it in setOf(0,5,10,15,30) } ?: 0)
        }
        return PrayerOptions(place, method, prefs.getBoolean("hanafi", false), prefs.getInt("wallpaper", 4).coerceIn(0, 6), alerts)
    }
    fun write(value: PrayerOptions) {
        prefs.edit().apply {
            putString("place", value.place.id)
            putString("name", value.place.name)
            putLong("lat", java.lang.Double.doubleToLongBits(value.place.latitude))
            putLong("lon", java.lang.Double.doubleToLongBits(value.place.longitude))
            putString("zone", value.place.zoneId)
            putString("method", value.method.name)
            putBoolean("hanafi", value.hanafi)
            putInt("wallpaper", value.wallpaper)
            value.alerts.forEach { (kind, pref) ->
                putBoolean("${kind.name}.on", pref.enabled)
                putInt("${kind.name}.offset", pref.offset.coerceIn(-30,30))
                putBoolean("${kind.name}.sound", pref.sound)
                putInt("${kind.name}.early", pref.earlyMinutes)
            }
            apply()
        }
    }
}

/** Best-effort alarms (no exact-alarm privilege or background location required). */
object PrayerAlarmScheduler {
    private const val slots = 8 * 8 * 2 // eight days, eight prayer kinds, regular + early
    private const val channel = "iumrah_prayer_times"
    private fun pending(context: Context, slot: Int, kind: PrayerKind?, early: Boolean = false, sound: Boolean = true, city: String = ""): PendingIntent {
        val intent = Intent(context, PrayerAlertReceiver::class.java).apply {
            action = "com.iumrah.beta.PRAYER_ALERT"
            putExtra("kind", kind?.name ?: "")
            putExtra("early", early)
            putExtra("sound", sound)
            putExtra("city", city)
            putExtra("lead", if (early) 1 else 0)
        }
        return PendingIntent.getBroadcast(context, 49000 + slot, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun sync(context: Context, options: PrayerOptions) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Stable slot IDs mean rescheduling cancels outdated city/method/datetime alerts.
        repeat(slots) { slot -> alarm.cancel(pending(context, slot, null)) }
        if (options.alerts.none { it.value.enabled }) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= 33) return
        val today = LocalDate.now(options.place.zone)
        val now = Instant.now().plusSeconds(10)
        for (day in 0L until 8L) {
            val schedule = PrayerCalculator.calculate(today.plusDays(day), options.place, options.method, options.hanafi)
            for (kind in PrayerKind.entries) {
                val pref = options.alerts[kind] ?: continue
                if (!pref.enabled) continue
                val fire = schedule.at(kind) ?: continue
                val kindIndex = kind.ordinal
                val baseSlot = (day.toInt() * PrayerKind.entries.size + kindIndex) * 2
                fun scheduleAlert(slot: Int, at: Instant, early: Boolean) {
                    if (at <= now) return
                    try { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(context, slot, kind, early, pref.sound, options.place.name)) }
                    catch (_: RuntimeException) { /* Alarm restrictions must not crash the UI. */ }
                }
                scheduleAlert(baseSlot, fire.plusSeconds(pref.offset.coerceIn(-30,30) * 60L), false)
                if (pref.earlyMinutes in setOf(5,10,15,30)) scheduleAlert(baseSlot + 1, fire.minusSeconds(pref.earlyMinutes * 60L), true)
            }
        }
    }
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channel) == null) {
            manager.createNotificationChannel(NotificationChannel(channel, "Prayer times", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    fun deliver(context: Context, kind: PrayerKind, early: Boolean, city: String, sound: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val displayName = kind.name.lowercase().replaceFirstChar { it.uppercase() }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_iumrah_notification)
            .setContentTitle(displayName)
            .setContentText(if (early) "Prayer begins soon · $city" else "Prayer time in $city")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setSilent(!sound)
            .build()
        NotificationManagerCompat.from(context).notify((System.currentTimeMillis() / 1000).toInt(), notification)
    }
}

class PrayerAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED ->
                PrayerAlarmScheduler.sync(context, PrayerPreferences(context).read())
            "com.iumrah.beta.PRAYER_ALERT" -> {
                val kind = runCatching { PrayerKind.valueOf(intent.getStringExtra("kind") ?: "") }.getOrNull() ?: return
                PrayerAlarmScheduler.deliver(context, kind, intent.getBooleanExtra("early", false), intent.getStringExtra("city").orEmpty(), intent.getBooleanExtra("sound", true))
            }
        }
    }
}

class PrayerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) {
            PrayerAlarmScheduler.sync(context, PrayerPreferences(context).read())
        }
    }
}
