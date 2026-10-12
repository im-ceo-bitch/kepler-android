package com.iumrah.beta.data.flight

import java.time.OffsetDateTime
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.Serializable

/** Optional future authorized itinerary feed; NOT synthesized from cached fares. */
@Serializable
data class FlightDiscoveryVerifiedSegment(
    val originAirport: String,
    val destinationAirport: String,
    val departureAt: String,
    val arrivalAt: String,
    val airlineCode: String,
    val flightNumber: String,
)

object FlightDiscoveryItineraryValidator {
    fun verified(
        segments: List<FlightDiscoveryVerifiedSegment>?, origin: String,
        destination: String, transfers: Int,
    ): List<FlightDiscoveryVerifiedSegment>? {
        if (segments.isNullOrEmpty() || segments.size > 8) return null
        if (transfers >= 0 && segments.size != transfers + 1) return null
        if (!segments.first().originAirport.equals(origin, ignoreCase = true) ||
            !segments.last().destinationAirport.equals(destination, ignoreCase = true)) return null
        var arrival: Instant? = null
        var endAirport: String? = null
        for (leg in segments) {
            if (!iata(leg.originAirport) || !iata(leg.destinationAirport)) return null
            val dep = parse(leg.departureAt) ?: return null
            val arr = parse(leg.arrivalAt) ?: return null
            if (arr <= dep) return null
            if (arrival != null && (dep < arrival ||
                !leg.originAirport.equals(endAirport, ignoreCase = true))) return null
            arrival = arr
            endAirport = leg.destinationAirport
        }
        return segments
    }

    fun connectionMinutes(first: FlightDiscoveryVerifiedSegment, second: FlightDiscoveryVerifiedSegment): Long? {
        if (!first.destinationAirport.equals(second.originAirport, ignoreCase = true)) return null
        val arrival = parse(first.arrivalAt) ?: return null
        val departure = parse(second.departureAt) ?: return null
        if (departure < arrival) return null
        return java.time.Duration.between(arrival, departure).toMinutes()
    }

    private fun iata(code: String) = code.uppercase(Locale.ROOT).matches(Regex("[A-Z]{3}"))

    private fun parse(value: String): Instant? = try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        try { Instant.parse(value) } catch (_: DateTimeParseException) { null }
    }
}
