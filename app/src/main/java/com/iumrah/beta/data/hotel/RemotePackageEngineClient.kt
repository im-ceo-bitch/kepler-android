package com.iumrah.beta.data.hotel

import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.core.serialization.BigDecimalJsonSerializer
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.domain.trip.FlightTripType
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.domain.trip.TripStayPlanner
import com.iumrah.beta.models.flight.FlightInfantSeating
import com.iumrah.beta.models.flight.LiveFlightCandidate
import com.iumrah.beta.models.hotel.HotelPricingSourceIdentity
import com.iumrah.beta.models.hotel.HotelPricingSourcesResponse
import com.iumrah.beta.models.hotel.HotelRoomCategoriesResponse
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
import com.iumrah.beta.models.hotel.PackageEngineHealthResponse
import com.iumrah.beta.models.hotel.PrimaryHotelResolutionResponse
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.Serializable

/** Same server-authoritative PackageEngine boundary as iOS. */
class RemotePackageEngineClient(private val api: APIClient) {
    suspend fun health(): PackageEngineHealthResponse =
        api.get(AppConfig.PACKAGE_HEALTH_PATH, timeoutSeconds = 8)

    suspend fun roomCategories(hotelID: String): List<IumrahRoomCategoryOption> =
        api.get<HotelRoomCategoriesResponse>("/api/package/hotel/$hotelID/room-categories")
            .categories.sortedBy { it.position }

    suspend fun hotelPricingSources(hotelID: String): List<HotelPricingSourceIdentity> {
        val response = api.get<HotelPricingSourcesResponse>("/api/package/hotel/$hotelID/pricing-sources", timeoutSeconds = 10)
        return if (response.ok) response.sources else emptyList()
    }

    suspend fun primaryHotel(tier: PackageTier, stars: Int, city: String): PrimaryHotelResolutionResponse =
        api.get(
            "/api/package/primary-hotel",
            query = mapOf("tier" to tier.wireValue, "stars" to stars.toString(), "city" to city),
        )

    suspend fun packageQuote(state: JourneyState): PackageQuote {
        val trip = state.trip
        val journey = state.selectedJourney ?: throw IllegalStateException("Select a verified flight itinerary first.")
        val makkahHotel = state.makkahHotel ?: throw IllegalStateException("Select a Makkah hotel first.")
        val madinahHotel = if (trip.scope == com.iumrah.beta.domain.trip.JourneyScope.MAKKAH_AND_MADINAH) {
            state.madinahHotel ?: throw IllegalStateException("Select a Madinah hotel first.")
        } else null

        // Exact iOS server identity contract. Published Business/storefront rows
        // are not priced from a client fare: PackageEngine re-resolves the selected
        // immutable row ids through the curated identity. Sending the raw Android
        // provider id here made a valid published selection look disconnected from
        // D1/PackageEngine.
        val rawProviderItineraryId = journey.providerItineraryID.trim().takeIf { it.isNotEmpty() }
        val providerItineraryId = when {
            rawProviderItineraryId?.startsWith("curated:") == true -> rawProviderItineraryId
            journey.sourceName == "iumrah Flights Scanner" -> {
                val outboundId = journey.outbound.id.trim()
                val inboundId = journey.inbound?.id?.trim().orEmpty()
                if (outboundId.isBlank() || (trip.isRoundTripFlight && inboundId.isBlank())) {
                    throw IllegalStateException("Published flight identity is missing.")
                }
                if (!trip.isRoundTripFlight || outboundId == inboundId) "curated:$outboundId"
                else "curated:$outboundId+$inboundId"
            }
            !rawProviderItineraryId.isNullOrBlank() -> rawProviderItineraryId
            else -> throw IllegalStateException("Verified flight itinerary ID is missing.")
        }
        val filters = trip.effectiveFlightFilters
        val infantsOnLap = if (filters.infantSeating == FlightInfantSeating.LAP) minOf(trip.infants, trip.adults) else 0
        val infantsInSeat = if (filters.infantSeating == FlightInfantSeating.LAP) maxOf(0, trip.infants - trip.adults) else trip.infants
        val stay = TripStayPlanner.breakdown(trip)
        val legs = buildList {
            add(ServerPackageQuoteRequest.Flight.Leg(journey.outbound.origin, journey.outbound.destination, flightDay(journey.outbound)))
            if (trip.resolvedFlightTripType == FlightTripType.ROUND_TRIP) {
                val inbound = journey.inbound ?: throw IllegalStateException("Verified return flight is missing.")
                add(ServerPackageQuoteRequest.Flight.Leg(inbound.origin, inbound.destination, flightDay(inbound)))
            }
        }
        val meals = trip.effectiveMealSelection
        val request = ServerPackageQuoteRequest(
            tier = trip.packageTier.wireValue,
            tripType = trip.resolvedFlightTripType.wireValue,
            includeMadinah = madinahHotel != null,
            travelers = ServerPackageQuoteRequest.Travelers(trip.adults, trip.children, trip.infants, trip.rooms),
            meals = ServerPackageQuoteRequest.Meals(meals.makkahLunch, meals.makkahDinner, meals.madinahDinner),
            transferVehicle = state.resolvedTransferVehicle.wireValue,
            haramain = ServerPackageQuoteRequest.Haramain(
                enabled = state.haramainTrainSelected,
                fareClass = state.haramainFareClass.wireValue,
                ticketCount = maxOf(0, state.haramainTicketCount),
            ),
            flight = ServerPackageQuoteRequest.Flight(
                providerItineraryId = providerItineraryId,
                cabinClass = journey.outbound.cabinClass ?: filters.cabinClass.wireValue,
                infantsInSeat = infantsInSeat,
                infantsOnLap = infantsOnLap,
                legs = legs,
            ),
            hotels = ServerPackageQuoteRequest.Hotels(
                makkah = ServerPackageQuoteRequest.Hotel(
                    hotelId = makkahHotel.id,
                    roomId = state.makkahRoom?.id ?: state.makkahRoomCategory?.id,
                    nights = maxOf(1, stay.makkahNights),
                ),
                madinah = madinahHotel?.let {
                    ServerPackageQuoteRequest.Hotel(
                        hotelId = it.id,
                        roomId = state.madinahRoom?.id ?: state.madinahRoomCategory?.id,
                        nights = maxOf(0, stay.madinahNights ?: 0),
                    )
                },
            ),
        )
        val envelope: ServerPackageQuoteEnvelope = api.post("/api/package/quote", request, timeoutSeconds = 15)
        if (!envelope.ok) throw APIException.InvalidResponse
        return PackageQuote(
            totalPackagePrice = envelope.quote.totalPackagePrice,
            pricePerPerson = envelope.quote.pricePerPerson,
            currency = envelope.quote.currency,
            isEstimated = envelope.quote.isEstimated,
            quoteId = envelope.quote.quoteId,
            quoteProof = envelope.quote.quoteProof,
            pricingSnapshot = null,
            bookingAllowed = envelope.quote.bookingAllowed,
        )
    }

    suspend fun commitPricingReport(bookingID: String, bookingToken: String, quoteProof: String) {
        val response: QuoteCommitResponse = api.post(
            "/api/package/quote/commit/$bookingID",
            QuoteCommitBody(quoteProof),
            headers = mapOf("x-booking-token" to bookingToken),
            timeoutSeconds = 12,
        )
        if (!response.ok) throw APIException.InvalidResponse
    }

    private fun flightDay(leg: LiveFlightCandidate): String {
        val zone = leg.segments?.firstOrNull()?.origin?.timeZoneIdentifier
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneOffset.UTC
        return leg.departureAt.atZone(zone).toLocalDate().toString()
    }
}

@Serializable
private data class ServerPackageQuoteEnvelope(val ok: Boolean, val quote: ServerPackageQuote)

@Serializable
private data class ServerPackageQuote(
    @Serializable(with = BigDecimalJsonSerializer::class) val totalPackagePrice: BigDecimal,
    @Serializable(with = BigDecimalJsonSerializer::class) val pricePerPerson: BigDecimal,
    val currency: String,
    val isEstimated: Boolean,
    val quoteId: String,
    val quoteProof: String,
    val bookingAllowed: Boolean? = null,
)

@Serializable
private data class ServerPackageQuoteRequest(
    val tier: String,
    val tripType: String,
    val includeMadinah: Boolean,
    val travelers: Travelers,
    val meals: Meals,
    val transferVehicle: String?,
    val haramain: Haramain,
    val flight: Flight,
    val hotels: Hotels,
) {
    @Serializable data class Travelers(val adults: Int, val children: Int, val infants: Int, val rooms: Int)
    @Serializable data class Meals(val makkahLunch: Boolean, val makkahDinner: Boolean, val madinahDinner: Boolean)
    @Serializable data class Haramain(val enabled: Boolean, val fareClass: String, val ticketCount: Int)
    @Serializable data class Flight(
        val providerItineraryId: String,
        val cabinClass: String,
        val infantsInSeat: Int,
        val infantsOnLap: Int,
        val legs: List<Leg>,
    ) { @Serializable data class Leg(val origin: String, val destination: String, val departureDate: String) }
    @Serializable data class Hotel(val hotelId: String, val roomId: String?, val nights: Int)
    @Serializable data class Hotels(val makkah: Hotel, val madinah: Hotel?)
}

@Serializable private data class QuoteCommitBody(val quoteProof: String)
@Serializable private data class QuoteCommitResponse(val ok: Boolean, val bookingID: String? = null, val quoteId: String? = null, val pricingVersion: String? = null)
