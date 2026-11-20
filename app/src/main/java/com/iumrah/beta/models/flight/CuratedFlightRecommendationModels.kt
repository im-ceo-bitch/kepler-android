package com.iumrah.beta.models.flight

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Exact Android port of iOS CuratedFlightRecommendationModels.swift. */
@Serializable
data class CuratedFlightRecommendation(
    val id: String,
    val outboundDate: String,
    val inboundDate: String? = null,
    val cabinClass: String,
    val airlineCodes: List<String> = emptyList(),
    val airlineNames: List<String> = emptyList(),
    val flightNumbers: List<String> = emptyList(),
    val observedAt: String,
    val outbound: Leg,
    val inbound: Leg? = null,
    val nonstop: Boolean,
    val recommendationLabel: String,
    val offerType: String? = null,
    val journeyRole: String? = null,
    /** Optional server-authoritative per-traveler price. Never invent missing fares. */
    val fareUsdPerTraveler: Double? = null,
) {
    @Serializable
    data class Leg(
        val airline: String,
        @SerialName("flight_number") val flightNumber: String,
        @SerialName("airline_code") val airlineCode: String,
        val origin: String,
        val destination: String,
        @SerialName("departure_at") val departureAt: String,
        @SerialName("arrival_at") val arrivalAt: String,
        @SerialName("duration_minutes") val durationMinutes: Int,
        val stops: Int,
        @SerialName("cabin_class") val cabinClass: String,
    )

    val effectiveOfferType: String
        get() = offerType?.takeIf { it.isNotBlank() } ?: if (inbound == null) "one_way" else "paired_one_way"

    val effectiveJourneyRole: String
        get() = journeyRole?.takeIf { it.isNotBlank() } ?: if (effectiveOfferType == "one_way") "outbound" else "complete"

    val primaryAirlineCode: String?
        get() = outbound.airlineCode.trim().takeIf { it.isNotEmpty() } ?: airlineCodes.firstOrNull()

    val primaryAirlineName: String
        get() = outbound.airline.trim().takeIf { it.isNotEmpty() } ?: airlineNames.firstOrNull() ?: primaryAirlineCode ?: "Airline"
}

@Serializable
data class CuratedFlightRecommendationsResponse(
    val ok: Boolean,
    val recommendations: List<CuratedFlightRecommendation> = emptyList(),
    val generatedAt: String,
)

data class CuratedPublishedFlightSelection(
    val completeID: String? = null,
    val outboundID: String? = null,
    val returnID: String? = null,
) {
    val isComplete: Boolean get() = !completeID.isNullOrBlank() || (!outboundID.isNullOrBlank() && !returnID.isNullOrBlank())
}
