package com.iumrah.beta.domain.booking

import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.domain.trip.DateFlexibility
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.domain.trip.TripStayPlanner
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.models.flight.LiveFlightCandidate
import com.iumrah.beta.models.flight.LiveFlightJourneyCandidate
import com.iumrah.beta.models.hotel.HotelRoom
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption

/** Exact booking-payload parity boundary with iOS BookingDraftBuilder.swift. */
object BookingDraftBuilder {
    fun make(
        state: JourneyState,
        quote: PackageQuote,
        language: AppLanguage,
        pilgrimProfile: BookingPilgrimProfile?,
    ): BookingCreateEnvelope {
        val trip = state.trip
        val makkahHotel = requireNotNull(state.makkahHotel)
        val journey = requireNotNull(state.selectedJourney)
        val includeMadinah = trip.scope == JourneyScope.MAKKAH_AND_MADINAH
        val madinahHotel = if (includeMadinah) state.madinahHotel else null
        val stay = TripStayPlanner.breakdown(trip)
        val windows = TripStayPlanner.windows(trip)
        val usesHaramain = includeMadinah && state.haramainTrainSelected
        val services = buildList {
            add("flight")
            add("makkahHotel")
            if (includeMadinah) add("madinahHotel")
            add("visa")
            add("meals")
            add("transfer")
            if (usesHaramain) add("haramainTrain")
            add("accompaniment")
            add("ziyaratMakkah")
            if (includeMadinah) add("ziyaratMadinah")
            add("care")
            add("esim")
        }

        val makkahRoomID = state.makkahRoom?.id ?: state.makkahRoomCategory?.id
        val madinahRoomID = state.madinahRoom?.id ?: state.madinahRoomCategory?.id
        val outbound = journey.outbound
        val inbound = journey.inbound

        val draft = BookingDraftRequest(
            planId = trip.packageTier.wireValue,
            totalUsd = quote.totalPackagePrice.toDouble(),
            perPilgrimUsd = quote.pricePerPerson.toDouble(),
            input = BookingInput(
                from = trip.originAirport?.city ?: trip.originCode,
                originCode = trip.originCode,
                arrivalAirportCode = trip.outboundDestinationCode,
                cabinClass = outbound.cabinClass ?: trip.effectiveFlightFilters.cabinClass.wireValue,
                preferredPlan = trip.packageTier.wireValue,
                startDate = trip.departureDate.toString(),
                endDate = trip.returnDate.toString(),
                flexibleDays = flexibleDays(trip.flexibility),
                hotelPreference = trip.hotelStars.toString(),
                includeMadinah = includeMadinah,
                flightTripType = trip.resolvedFlightTripType.wireValue,
                travelers = BookingTravelers(trip.adults, trip.children, trip.infants, trip.rooms),
            ),
            route = BookingRoute(trip.originCode, trip.outboundDestinationCode, trip.returnOriginCode),
            stay = BookingStay(
                totalDays = stay.totalDays,
                totalNights = stay.totalNights,
                makkahCheckIn = windows.makkah.checkIn.toString(),
                makkahCheckOut = windows.makkah.checkOut.toString(),
                makkahNights = stay.makkahNights,
                madinahCheckIn = windows.madinah?.checkIn?.toString(),
                madinahCheckOut = windows.madinah?.checkOut?.toString(),
                madinahNights = windows.madinah?.let { stay.madinahNights },
            ),
            selection = BookingSelection(
                flightId = listOfNotNull(outbound.id, inbound?.id).joinToString("|"),
                makkahHotelId = makkahHotel.id,
                madinahHotelId = madinahHotel?.id,
                makkahRoomId = makkahRoomID,
                makkahRoomCategory = state.makkahRoomCategory?.category?.name?.let(IumrahRoomCategory::valueOf),
                madinahRoomId = if (includeMadinah) madinahRoomID else null,
                madinahRoomCategory = if (includeMadinah) state.madinahRoomCategory?.category?.name?.let(IumrahRoomCategory::valueOf) else null,
            ),
            customization = BookingCustomization(
                accompaniment = true,
                guideMeetingPoint = "airport",
                ziyaratMakkah = true,
                ziyaratMadinah = includeMadinah,
                meals = true,
                esim = true,
            ),
            includedServices = services,
            hotelNames = BookingHotelNames(makkahHotel.name, madinahHotel?.name.orEmpty()),
            flight = flightSummary(outbound, inbound),
            pilgrimProfile = pilgrimProfile,
            generatorTrace = BookingGeneratorTrace(
                quoteId = quote.quoteId,
                outbound = generatorFlight(outbound, journey.sourceName),
                inbound = inbound?.let { generatorFlight(it, journey.sourceName) },
                makkahHotel = generatorHotel(makkahHotel, state.makkahRoom, state.makkahRoomCategory),
                madinahHotel = madinahHotel?.let { generatorHotel(it, state.madinahRoom, state.madinahRoomCategory) },
            ),
        )
        return BookingCreateEnvelope(lang = language.code, booking = draft, quoteProof = quote.quoteProof)
    }

    private fun generatorFlight(offer: LiveFlightCandidate, source: String): BookingGeneratorFlightSnapshot =
        BookingGeneratorFlightSnapshot(
            candidateId = offer.id,
            airline = offer.airline,
            flightNumbers = offer.segments.orEmpty().map { it.flightNumber }.filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { offer.flightNumber },
            origin = offer.origin,
            destination = offer.destination,
            departureAt = offer.departureAt.toString(),
            arrivalAt = offer.arrivalAt.toString(),
            source = source,
            stops = offer.stops,
            durationMinutes = offer.durationMinutes.takeIf { it > 0 },
            segments = offer.segments?.map { segment ->
                BookingGeneratorFlightSegmentSnapshot(
                    airline = segment.airline,
                    airlineCode = segment.airlineCode,
                    flightNumber = segment.flightNumber,
                    origin = segment.origin.code,
                    destination = segment.destination.code,
                    departureAt = segment.departureAt.toString(),
                    arrivalAt = segment.arrivalAt.toString(),
                    originTerminal = segment.origin.terminal,
                    destinationTerminal = segment.destination.terminal,
                    aircraft = segment.aircraft,
                    operatingCarrier = segment.operatingCarrier,
                    cabin = segment.cabin,
                )
            },
            connectionAirports = offer.connectionAirports?.map { it.code },
        )

    private fun generatorHotel(hotel: HotelSummary, room: HotelRoom?, category: IumrahRoomCategoryOption?): BookingGeneratorHotelSnapshot =
        BookingGeneratorHotelSnapshot(
            hotelId = hotel.id,
            hotelName = hotel.name,
            city = hotel.city,
            roomId = room?.id ?: category?.id,
            roomName = room?.name ?: category?.displayName,
            roomCategory = category?.category?.name,
        )

    private fun flightSummary(outbound: LiveFlightCandidate, inbound: LiveFlightCandidate?): String {
        fun value(leg: LiveFlightCandidate): String {
            val numbers = leg.segments.orEmpty().map { it.flightNumber }.filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { leg.flightNumber }
            return listOf(leg.airline, numbers).filter { it.isNotBlank() }.joinToString(" · ")
        }
        return listOfNotNull(value(outbound), inbound?.let(::value)).filter { it.isNotBlank() }.joinToString(" / ")
    }

    private fun flexibleDays(value: DateFlexibility): Int = when (value) {
        DateFlexibility.EXACT, DateFlexibility.WEEKEND -> 0
        DateFlexibility.PLUS_MINUS_ONE, DateFlexibility.PLUS_MINUS_TWO -> 3
    }
}
