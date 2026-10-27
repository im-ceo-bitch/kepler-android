package com.iumrah.beta.data.flight

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.iumrah.beta.R
import com.iumrah.beta.core.settings.AppLanguage
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class FlightFavoriteRecord(
    val id: String,
    val offer: FlightDiscoveryOffer,
    val currency: String,
    val lastKnownPrice: Double,
    val addedAtEpochMs: Long,
)

class FlightFavoritesStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("iumrah.flight-favorites.v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(FlightFavoriteRecord.serializer())

    fun records(): List<FlightFavoriteRecord> = runCatching {
        json.decodeFromString(serializer, prefs.getString("records", "[]") ?: "[]")
    }.getOrDefault(emptyList())

    fun isFavorite(offer: FlightDiscoveryOffer): Boolean = records().any { it.offer.monitorKey == offer.monitorKey }

    fun toggle(offer: FlightDiscoveryOffer, currency: String): List<FlightFavoriteRecord> {
        val rows = records().toMutableList()
        val index = rows.indexOfFirst { it.offer.monitorKey == offer.monitorKey }
        if (index >= 0) rows.removeAt(index)
        else rows.add(0, FlightFavoriteRecord(UUID.randomUUID().toString(), offer, currency, offer.price, System.currentTimeMillis()))
        save(rows)
        return rows
    }

    fun remove(id: String): List<FlightFavoriteRecord> {
        val rows = records().filterNot { it.id == id }
        save(rows)
        return rows
    }

    fun reconcile(offers: List<FlightDiscoveryOffer>, currency: String, language: AppLanguage): List<FlightFavoriteRecord> {
        if (offers.isEmpty()) return records()
        var changed = false
        val updated = records().map { record ->
            val fresh = offers.firstOrNull { it.monitorKey == record.offer.monitorKey } ?: return@map record
            val old = record.lastKnownPrice
            val next = fresh.price
            if (old > 0 && next > 0 && kotlin.math.abs(next - old) >= 1.0) {
                postPriceChange(record.offer, old, next, currency, language)
                changed = true
                record.copy(offer = fresh, currency = currency, lastKnownPrice = next)
            } else if (record.offer != fresh || record.currency != currency || old != next) {
                changed = true
                record.copy(offer = fresh, currency = currency, lastKnownPrice = next)
            } else record
        }
        if (changed) save(updated)
        return updated
    }

    private fun save(rows: List<FlightFavoriteRecord>) {
        prefs.edit().putString("records", json.encodeToString(serializer, rows)).apply()
    }

    private fun postPriceChange(offer: FlightDiscoveryOffer, oldPrice: Double, newPrice: Double, currency: String, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Flight price changes", NotificationManager.IMPORTANCE_DEFAULT))
        val route = offer.routeTitle
        val title = when (language) {
            AppLanguage.RUSSIAN -> if (newPrice < oldPrice) "Авиабилет подешевел" else "Цена авиабилета изменилась"
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> if (newPrice < oldPrice) "Flight price dropped" else "Flight price changed"
            AppLanguage.UZBEK -> if (newPrice < oldPrice) "Aviachipta arzonlashdi" else "Aviachipta narxi o‘zgardi"
            AppLanguage.UZBEK_CYRILLIC -> if (newPrice < oldPrice) "Авиачипта арзонлашди" else "Авиачипта нархи ўзгарди"
        }
        val body = "$route: ${money(oldPrice, currency)} → ${money(newPrice, currency)}"
        val notification = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify((offer.monitorKey.hashCode() xor newPrice.toInt()).and(0x7fffffff), notification)
    }

    private fun money(value: Double, currency: String): String =
        if (currency.equals("usd", true)) "$${value.toInt()}" else "${currency.uppercase()} ${value.toInt()}"

    companion object { private const val CHANNEL = "iumrah_flight_price_changes" }
}
