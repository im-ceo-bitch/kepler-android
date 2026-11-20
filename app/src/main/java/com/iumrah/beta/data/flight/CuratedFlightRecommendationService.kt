package com.iumrah.beta.data.flight

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.core.serialization.BigDecimalJsonSerializer
import com.iumrah.beta.domain.trip.FlightFareScope
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.models.flight.CuratedFlightRecommendationsResponse
import com.iumrah.beta.models.flight.CuratedPublishedFlightSelection
import com.iumrah.beta.models.flight.FlightAirportSnapshot
import com.iumrah.beta.models.flight.FlightDirection
import com.iumrah.beta.models.flight.FlightSegment
import com.iumrah.beta.models.flight.LiveFlightCandidate
import com.iumrah.beta.models.flight.LiveFlightJourneyCandidate
import com.iumrah.beta.models.hotel.StorefrontFlightBoardResponse
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Exact Android counterpart of iOS CuratedFlightRecommendationService.
 *
 * Published direct flights come from Business/HOTELS_DB through two public D1
 * projections. We always merge both endpoints just like iOS so one stale
 * projection cannot hide a Business-published row.
 */
class CuratedFlightRecommendationService(private val api: APIClient) {
    suspend fun load(trip: TripDraft, from: LocalDate = LocalDate.now(), days: Int = 365): List<CuratedFlightRecommendation> {
        val start = from
        val end = start.plusDays(days.coerceIn(1, 365).toLong())
        val merged = linkedMapOf<String, CuratedFlightRecommendation>()
        var firstError: Throwable? = null

        runCatching {
            api.get<CuratedFlightRecommendationsResponse>(
                "/api/package/flights/recommendations",
                query = mapOf(
                    "umrah_origin" to trip.originCode.uppercase(),
                    "from" to start.toString(),
                    "to" to end.toString(),
                ),
                timeoutSeconds = 10,
            )
        }.onSuccess { response ->
            if (response.ok) response.recommendations.filter { it.nonstop }.forEach { merged[it.id] = it }
        }.onFailure { firstError = it }

        runCatching { storefrontFallback(trip.originCode.uppercase(), start, end) }
            .onSuccess { values -> values.filter { it.nonstop }.forEach { value ->
                val existing = merged[value.id]
                merged[value.id] = if (existing == null) value else if (existing.fareUsdPerTraveler == null)
                    existing.copy(fareUsdPerTraveler = value.fareUsdPerTraveler) else existing
            } }
            .onFailure { if (firstError == null) firstError = it }

        if (merged.isNotEmpty()) {
            return merged.values.sortedWith(compareBy<CuratedFlightRecommendation> { it.outboundDate }.thenBy { it.id })
        }
        firstError?.let { throw it }
        return emptyList()
    }

    private suspend fun storefrontFallback(origin: String, from: LocalDate, to: LocalDate): List<CuratedFlightRecommendation> {
        val board: StorefrontFlightBoardResponse = api.get(
            "/api/package/storefront/flights",
            query = mapOf("origin" to origin),
            timeoutSeconds = 10,
        )
        if (!board.ok) return emptyList()
        return board.options.mapNotNull { option ->
            val outboundDate = option.outbound.departureAt.take(10)
            val day = runCatching { LocalDate.parse(outboundDate) }.getOrNull() ?: return@mapNotNull null
            if (day.isBefore(from) || day.isAfter(to)) return@mapNotNull null
            val inboundDate = option.inbound?.departureAt?.take(10)
            val outbound = CuratedFlightRecommendation.Leg(
                airline = option.outbound.airline,
                flightNumber = option.outbound.flightNumber,
                airlineCode = option.outbound.airlineCode,
                origin = option.outbound.origin,
                destination = option.outbound.destination,
                departureAt = option.outbound.departureAt,
                arrivalAt = option.outbound.arrivalAt,
                durationMinutes = option.outbound.durationMinutes,
                stops = option.outbound.stops,
                cabinClass = option.outbound.cabinClass,
            )
            val inbound = option.inbound?.let { leg ->
                CuratedFlightRecommendation.Leg(
                    airline = leg.airline,
                    flightNumber = leg.flightNumber,
                    airlineCode = leg.airlineCode,
                    origin = leg.origin,
                    destination = leg.destination,
                    departureAt = leg.departureAt,
                    arrivalAt = leg.arrivalAt,
                    durationMinutes = leg.durationMinutes,
                    stops = leg.stops,
                    cabinClass = leg.cabinClass,
                )
            }
            val oneWay = inbound == null
            val role = when {
                oneWay && option.outbound.origin.equals(origin, true) -> "outbound"
                oneWay && option.outbound.destination.equals(origin, true) -> "return"
                !oneWay && option.outbound.origin.equals(origin, true) && option.inbound?.destination.equals(origin, true) -> "complete"
                else -> return@mapNotNull null
            }
            val codes = listOfNotNull(option.outbound.airlineCode.takeIf { it.isNotBlank() }, option.inbound?.airlineCode?.takeIf { it.isNotBlank() }).distinct()
            val names = listOfNotNull(option.outbound.airline.takeIf { it.isNotBlank() }, option.inbound?.airline?.takeIf { it.isNotBlank() }).distinct()
            val numbers = listOfNotNull(option.outbound.flightNumber.takeIf { it.isNotBlank() }, option.inbound?.flightNumber?.takeIf { it.isNotBlank() })
            CuratedFlightRecommendation(
                id = option.id,
                outboundDate = outboundDate,
                inboundDate = inboundDate,
                cabinClass = option.outbound.cabinClass,
                airlineCodes = codes,
                airlineNames = names,
                flightNumbers = numbers,
                observedAt = option.observedAt,
                outbound = outbound,
                inbound = inbound,
                nonstop = option.outbound.stops == 0 && (option.inbound?.stops ?: 0) == 0,
                recommendationLabel = "iumrah recommends",
                offerType = if (oneWay) "one_way" else "paired_one_way",
                journeyRole = role,
                fareUsdPerTraveler = option.perTravelerFare.takeIf { option.currency.equals("USD", true) && it.isFinite() && it > 0.0 },
            )
        }
    }

    suspend fun resolvePublishedSelection(trip: TripDraft, selection: CuratedPublishedFlightSelection): LiveFlightJourneyCandidate {
        if (!selection.isComplete) throw APIException.InvalidResponse
        val response: ResolveResponse = api.post(
            "/api/package/flights/recommendations/resolve",
            ResolveRequest(
                completeID = selection.completeID?.trim()?.takeIf { it.isNotEmpty() },
                outboundID = selection.outboundID?.trim()?.takeIf { it.isNotEmpty() },
                returnID = selection.returnID?.trim()?.takeIf { it.isNotEmpty() },
                travelerCount = trip.travelerCount,
                origin = trip.originCode,
                outboundDestination = trip.outboundDestinationCode,
                returnOrigin = trip.returnOriginCode,
                returnDestination = trip.originCode,
            ),
            timeoutSeconds = 12,
        )
        if (!response.ok || response.totalFare <= BigDecimal.ZERO) throw APIException.InvalidResponse
        val observedAt = runCatching { Instant.parse(response.resolvedAt) }.getOrElse { throw APIException.InvalidResponse }
        val fareScope = if (response.fareScope.equals("perPassenger", true)) FlightFareScope.PER_PASSENGER else FlightFareScope.TOTAL_PARTY
        val outbound = candidate(response.outbound, FlightDirection.outbound, response, observedAt, fareScope)
        val inbound = candidate(response.inbound, FlightDirection.inbound, response, observedAt, fareScope)
        return LiveFlightJourneyCandidate(
            id = "published:${response.providerItineraryID}",
            sourceID = "iumrah-published",
            sourceName = response.sourceName,
            totalFare = response.totalFare,
            currency = response.currency.uppercase(),
            fareScope = fareScope,
            observedAt = observedAt,
            providerItineraryID = response.providerItineraryID,
            outbound = outbound,
            inbound = inbound,
            baggage = null,
            requiresSelfTransfer = false,
        )
    }

    private fun candidate(
        leg: ResolvedLeg,
        direction: FlightDirection,
        response: ResolveResponse,
        observedAt: Instant,
        fareScope: FlightFareScope,
    ): LiveFlightCandidate {
        val departure = runCatching { Instant.parse(leg.departureAt) }.getOrElse { throw APIException.InvalidResponse }
        val arrival = runCatching { Instant.parse(leg.arrivalAt) }.getOrElse { throw APIException.InvalidResponse }
        if (!departure.isBefore(arrival)) throw APIException.InvalidResponse
        val segment = FlightSegment(
            id = leg.id,
            airline = leg.airline,
            airlineCode = leg.airlineCode,
            flightNumber = leg.flightNumber,
            origin = FlightAirportSnapshot(leg.origin.uppercase()),
            destination = FlightAirportSnapshot(leg.destination.uppercase()),
            departureAt = departure,
            arrivalAt = arrival,
            durationMinutes = leg.durationMinutes,
            cabin = leg.cabinClass,
        )
        return LiveFlightCandidate(
            id = leg.id,
            sourceID = "iumrah-published",
            sourceName = response.sourceName,
            direction = direction,
            airline = leg.airline,
            flightNumber = leg.flightNumber,
            origin = leg.origin.uppercase(),
            destination = leg.destination.uppercase(),
            departureAt = departure,
            arrivalAt = arrival,
            stops = 0,
            durationMinutes = leg.durationMinutes,
            observedFare = response.totalFare,
            observedCurrency = response.currency.uppercase(),
            fareScope = fareScope,
            observedAt = observedAt,
            rawFingerprint = response.providerItineraryID,
            airlineCode = leg.airlineCode,
            segments = listOf(segment),
            connectionAirports = emptyList(),
            providerItineraryID = response.providerItineraryID,
            cabinClass = leg.cabinClass,
            baggage = null,
            requiresSelfTransfer = false,
        )
    }

    @Serializable
    private data class ResolveRequest(
        val completeID: String? = null,
        val outboundID: String? = null,
        val returnID: String? = null,
        val travelerCount: Int,
        val origin: String,
        val outboundDestination: String,
        val returnOrigin: String,
        val returnDestination: String,
    )

    @Serializable
    private data class ResolvedLeg(
        val id: String,
        val airline: String,
        val flightNumber: String,
        val airlineCode: String? = null,
        val origin: String,
        val destination: String,
        val departureAt: String,
        val arrivalAt: String,
        val durationMinutes: Int,
        val cabinClass: String? = null,
    )

    @Serializable
    private data class ResolveResponse(
        val ok: Boolean,
        val resolvedAt: String,
        val currency: String,
        @Serializable(with = BigDecimalJsonSerializer::class) val totalFare: BigDecimal,
        val fareScope: String,
        val providerItineraryID: String,
        val sourceName: String,
        val outbound: ResolvedLeg,
        val inbound: ResolvedLeg,
    )
}
