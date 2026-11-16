package com.iumrah.beta.data.smart

import android.content.Context
import com.iumrah.beta.domain.smart.*
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.flight.Airport
import com.iumrah.beta.models.flight.FlightSearchFilters
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Only preferences and search parameters; never stores a quote, booking proof, contact or payment data. */
@Serializable data class RecentFlightSearch(val origin: String, val airport: Airport?, val arrival: String,
    val departure: String, val returning: String, val both: Boolean, val adults: Int,
    val children: Int, val infants: Int, val rooms: Int, val filters: FlightSearchFilters? = null) {
    fun trip(today: LocalDate = LocalDate.now()): TripDraft? = runCatching {
        TripDraft(origin = origin, originAirport = airport,
            arrivalAirport = if (arrival == "MED") SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH,
            departureDate = LocalDate.parse(departure), returnDate = LocalDate.parse(returning),
            scope = if (both) JourneyScope.MAKKAH_AND_MADINAH else JourneyScope.MAKKAH_ONLY,
            adults = adults, children = children, infants = infants, rooms = rooms, flightFilters = filters)
            .takeIf { it.canContinue && it.departureDate >= today }
    }.getOrNull()
    companion object {
        fun from(trip: TripDraft) = RecentFlightSearch(trip.originCode, trip.originAirport, trip.arrivalAirport.iata,
            trip.departureDate.toString(), trip.returnDate.toString(), trip.scope == JourneyScope.MAKKAH_AND_MADINAH,
            trip.adults, trip.children, trip.infants, trip.rooms, trip.flightFilters)
    }
}
@Serializable data class HomeActivity(val smart: SmartDraft? = null, val flight: RecentFlightSearch? = null)
class HomeActivityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("iumrah.home.activity.v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision
    val enabled get() = prefs.getBoolean("enabled", true)
    fun snapshot(owner: String?): HomeActivity {
        if (!enabled || owner == null) return HomeActivity()
        val value = prefs.getString("history:$owner", null)?.let { runCatching { json.decodeFromString(HomeActivity.serializer(), it) }.getOrNull() } ?: HomeActivity()
        return HomeActivity(value.smart?.let { SmartRules.resume(it) }, value.flight?.takeIf { it.trip() != null })
    }
    fun saveSmart(owner: String?, draft: SmartDraft?) = update(owner) { it.copy(smart = draft) }
    fun saveFlight(owner: String?, trip: TripDraft) = update(owner) { it.copy(flight = RecentFlightSearch.from(trip)) }
    fun clear(owner: String?) { if (owner != null) { prefs.edit().remove("history:$owner").apply(); _revision.value++ } }
    fun setEnabled(value: Boolean) {
        val edit = prefs.edit()
        if (!value) prefs.all.keys.filter { it.startsWith("history:") }.forEach { edit.remove(it) }
        edit.putBoolean("enabled", value).apply(); _revision.value++
    }
    private fun update(owner: String?, transform: (HomeActivity) -> HomeActivity) {
        if (!enabled || owner == null) return
        prefs.edit().putString("history:$owner", json.encodeToString(HomeActivity.serializer(), transform(snapshot(owner)))).apply()
        _revision.value++
    }
}
