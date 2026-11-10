package com.iumrah.beta.data.nuitee

import android.content.Context
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.models.nuitee.NuiteeRoomRate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Standalone catalogue selection is not a booking; persisted until the client changes the stay. */
@Serializable
data class NuiteeSavedStay(
    val provider: String = "nuitee", val hotelId: String, val hotelName: String,
    val city: String, val checkin: String, val checkout: String, val adults: Int,
    val offerId: String, val roomId: String, val roomName: String,
    val totalUSD: Double, val mealPlan: String, val cancellationTag: String,
    val selectedAtEpochMillis: Long,
)

@Serializable
data class NuiteePendingStay(
    val hotelId: String, val hotelName: String, val city: String,
    val checkin: String, val checkout: String, val adults: Int,
)

class NuiteeStaySelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("iumrah_nuitee_stays_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun cityKey(value: String) = if (value.contains("mad", true) || value.contains("med", true)) "madinah" else "makkah"
    fun save(hotel: NuiteeHotel, checkin: String, checkout: String, adults: Int, rate: NuiteeRoomRate) {
        val city = cityKey(hotel.city)
        val stay = NuiteeSavedStay(hotelId = hotel.id, hotelName = hotel.name, city = city,
            checkin = checkin, checkout = checkout, adults = adults,
            offerId = rate.offerId, roomId = rate.roomId, roomName = rate.roomName,
            totalUSD = rate.price, mealPlan = rate.mealPlan, cancellationTag = rate.cancellationTag,
            selectedAtEpochMillis = System.currentTimeMillis())
        preferences.edit().putString("stay.$city", json.encodeToString(NuiteeSavedStay.serializer(), stay))
            .remove("pending.$city").apply()
    }
    fun savePending(hotel: NuiteeHotel, checkin: String, checkout: String, adults: Int) {
        val city = cityKey(hotel.city)
        val request = NuiteePendingStay(hotel.id, hotel.name, city, checkin, checkout, adults)
        preferences.edit().putString("pending.$city", json.encodeToString(NuiteePendingStay.serializer(), request))
            .remove("stay.$city").apply()
    }
    fun selected(city: String): NuiteeSavedStay? = preferences.getString("stay.${cityKey(city)}", null)?.let {
        runCatching { json.decodeFromString(NuiteeSavedStay.serializer(), it) }.getOrNull()
    }
    fun pending(city: String): NuiteePendingStay? = preferences.getString("pending.${cityKey(city)}", null)?.let {
        runCatching { json.decodeFromString(NuiteePendingStay.serializer(), it) }.getOrNull()
    }
}
