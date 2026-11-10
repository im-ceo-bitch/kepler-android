package com.iumrah.beta.data.care

import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.nuitee.NuiteeJourneyPricing
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.models.nuitee.NuiteeRoomRate
import kotlinx.serialization.Serializable

/** Configuration JSON attached to an existing /api/package/care-requests enquiry, not a booking. */
@Serializable
data class NuiteeCareConfiguration(
    val version: Int = 2,
    val trip: NuiteeTripInfo,
    val flights: List<NuiteeFlightInfo>,
    val rooms: List<NuiteeRoomInfo>,
    val pendingHotels: List<NuiteePendingInfo>,
    val transfer: String,
    val trainTickets: Int,
    val trainClass: String,
    val estimatedTotalUSD: Double? = null,
) {
    companion object {
        fun from(state: JourneyState): NuiteeCareConfiguration {
            val draft = requireNotNull(state.nuitee)
            val trip = state.trip
            val estimate = NuiteeJourneyPricing.estimate(trip, draft, state.selectedTransferVehicle,
                state.haramainTrainSelected, state.haramainTicketCount, state.haramainFareClass)
            return NuiteeCareConfiguration(
                trip = NuiteeTripInfo(trip.originCode, trip.departureDate.toString(), trip.returnDate.toString(),
                    trip.adults, trip.children, trip.infants, trip.rooms, trip.scope.wireValue,
                    trip.arrivalAirport.name.lowercase(), trip.packageTier.wireValue, trip.hotelStars),
                flights = draft.flights.map { NuiteeFlightInfo(it.id, it.origin, it.destination, it.airline,
                    it.flightNumber, it.airlineCode, it.durationMinutes, it.stops,
                    it.departureAt, it.arrivalAt, it.adultFareUSD) },
                rooms = draft.rooms.toSortedMap().values.map { NuiteeRoomInfo(it.key, it.city, it.hotel, it.rate,
                    it.checkin.toString(), it.checkout.toString(), it.adults, it.childrenAges) },
                pendingHotels = draft.pending.toSortedMap().values.map { NuiteePendingInfo(it.key, it.city, it.hotel,
                    it.checkin.toString(), it.checkout.toString(), it.adults, it.childrenAges) },
                transfer = state.resolvedTransferVehicle.wireValue,
                trainTickets = if (state.haramainTrainSelected) state.haramainTicketCount else 0,
                trainClass = state.haramainFareClass.wireValue,
                estimatedTotalUSD = estimate?.totalUsd?.toDouble(),
            )
        }
    }
}

@Serializable data class NuiteeTripInfo(
    val origin: String, val departureDate: String, val returnDate: String,
    val adults: Int, val children: Int, val infants: Int, val rooms: Int,
    val scope: String, val arrivalAirport: String, val packageTier: String, val hotelStars: Int,
)
@Serializable data class NuiteeFlightInfo(
    val id: String, val origin: String, val destination: String, val airline: String,
    val flightNumber: String, val airlineCode: String, val durationMinutes: Int, val stops: Int,
    val departureAt: String, val arrivalAt: String?, val displayedPrice: Double?,
)
@Serializable data class NuiteeRoomInfo(
    val id: String, val city: String, val hotel: NuiteeHotel, val rate: NuiteeRoomRate,
    val checkin: String, val checkout: String, val adults: Int, val childrenAges: List<Int>,
)
@Serializable data class NuiteePendingInfo(
    val id: String, val city: String, val hotel: NuiteeHotel,
    val checkin: String, val checkout: String, val adults: Int, val childrenAges: List<Int>,
)
