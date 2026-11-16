package com.iumrah.beta.models.flight

import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class FlightCabinClass(val wireValue: String) { ECONOMY("economy"), PREMIUM_ECONOMY("premium_economy"), BUSINESS("business"), FIRST("first") }
@Serializable
enum class FlightStopsPreference(val wireValue: String, val maxStops: Int?) { ANY("any", null), NONSTOP("nonstop", 0), UP_TO_ONE("upToOne", 1), UP_TO_TWO("upToTwo", 2) }
@Serializable
enum class FlightTimeWindow(val wireValue: String, val range: IntRange?) { ANY("any", null), NIGHT("night", 0..5), MORNING("morning", 6..11), AFTERNOON("afternoon", 12..17), EVENING("evening", 18..23) }
@Serializable
enum class FlightInfantSeating(val wireValue: String) { LAP("lap"), SEAT("seat") }

@Serializable
data class FlightSearchFilters(
    val cabinClass: FlightCabinClass = FlightCabinClass.ECONOMY,
    val stops: FlightStopsPreference = FlightStopsPreference.ANY,
    val minCarryOnBags: Int = 0,
    val minCheckedBags: Int = 0,
    val maxPriceUSD: Int? = null,
    val departureWindow: FlightTimeWindow = FlightTimeWindow.ANY,
    val arrivalWindow: FlightTimeWindow = FlightTimeWindow.ANY,
    val airlinesInclude: List<String> = emptyList(),
    val airlinesExclude: List<String> = emptyList(),
    val allowSelfTransfer: Boolean = false,
    val infantSeating: FlightInfantSeating = FlightInfantSeating.LAP,
) {
    val normalizedAirlinesInclude: List<String> get() = normalize(airlinesInclude)
    val normalizedAirlinesExclude: List<String> get() = normalize(airlinesExclude)
    private fun normalize(values: List<String>) = values.map { it.trim().uppercase() }.filter { it.matches(Regex("^[A-Z0-9]{2}$")) }.distinct()
}

data class FlightJourneySearchRequest(
    val outboundOrigin: String,
    val outboundDestination: String,
    val inboundOrigin: String?,
    val inboundDestination: String?,
    val adults: Int,
    val children: Int,
    val infants: Int,
    val cabin: String,
    val filters: FlightSearchFilters,
)

data class FlightJourneyDatePair(val outbound: LocalDate, val inbound: LocalDate?)
