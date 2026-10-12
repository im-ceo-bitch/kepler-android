package com.iumrah.beta.domain.booking

import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.models.flight.*
import com.iumrah.beta.models.hotel.HotelSummary
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Contract tests for a synthetic two-passenger route: no payment, production API or private account. */
class BookingFlowContractParityTest {
    private val outbound = LiveFlightCandidate(
        id = "outbound-01", sourceID = "curated", sourceName = "iumrah Flights Scanner",
        direction = FlightDirection.outbound, airline = "Air Test", flightNumber = "T101",
        origin = "TAS", destination = "JED", departureAt = Instant.parse("2026-11-10T05:00:00Z"),
        arrivalAt = Instant.parse("2026-11-10T10:00:00Z"), stops = 0, durationMinutes = 300,
        observedFare = BigDecimal("400"), observedCurrency = "USD", fareScope = FlightFareScope.PER_PASSENGER,
        observedAt = Instant.parse("2026-11-01T00:00:00Z"), providerItineraryID = "curated:test-out",
    )
    private val inbound = outbound.copy(
        id = "inbound-01", direction = FlightDirection.inbound,
        origin = "JED", destination = "TAS", departureAt = Instant.parse("2026-11-18T06:00:00Z"),
        arrivalAt = Instant.parse("2026-11-18T11:00:00Z"), flightNumber = "T102",
    )
    private val journey = LiveFlightJourneyCandidate(
        id = "pair-01", sourceID = "curated", sourceName = "iumrah Flights Scanner",
        totalFare = BigDecimal("800"), currency = "USD", fareScope = FlightFareScope.PER_PASSENGER,
        observedAt = Instant.parse("2026-11-01T00:00:00Z"), providerItineraryID = "curated:pair-01",
        outbound = outbound, inbound = inbound,
    )
    private val hotel = HotelSummary(id = "makkah-01", name = "Hotel Test", city = "Makkah", status = "active", updatedAt = "2026-11-01")
    private val quote = PackageQuote(
        totalPackagePrice = BigDecimal("3000"), pricePerPerson = BigDecimal("1500"),
        currency = "USD", isEstimated = false, quoteId = "server-contract-test", quoteProof = "test-proof",
    )
    private fun trip() = TripDraft(
        origin = "TAS", arrivalAirport = SaudiArrivalAirport.JEDDAH,
        departureDate = LocalDate.parse("2026-11-10"), returnDate = LocalDate.parse("2026-11-18"),
        scope = JourneyScope.MAKKAH_ONLY, flightTripType = FlightTripType.ROUND_TRIP,
        adults = 2, children = 0, infants = 0, rooms = 1,
    )

    @Test fun signedServerQuoteRequiredToProceedToCheckout() {
        assertFalse(JourneyState(quote = quote.copy(quoteId = "local-demo")).hasFinalGeneratorQuote)
        assertFalse(JourneyState(quote = quote.copy(quoteProof = null)).hasFinalGeneratorQuote)
        assertFalse(JourneyState(quote = quote.copy(totalPackagePrice = BigDecimal.ZERO)).hasFinalGeneratorQuote)
        assertTrue(JourneyState(quote = quote).hasFinalGeneratorQuote)
    }

    @Test fun bookingDraftPreservesVerifiedRoutePassengersHotelsAndServerPrice() {
        val trip = trip()
        val state = JourneyState(
            trip = trip, flightResults = listOf(journey), selectedJourneyId = journey.id,
            makkahHotel = hotel, quote = quote,
        )
        val payload = BookingDraftBuilder.make(state, quote, AppLanguage.RUSSIAN, null)
        assertEquals("ru", payload.lang)
        assertEquals("TAS", payload.booking.route.originCode)
        assertEquals("JED", payload.booking.route.outboundDestination)
        assertEquals("JED", payload.booking.route.returnOrigin)
        assertEquals(2, payload.booking.input.travelers.adults)
        assertEquals("makkah-01", payload.booking.selection.makkahHotelId)
        assertEquals("outbound-01|inbound-01", payload.booking.selection.flightId)
        assertEquals("server-contract-test", payload.booking.generatorTrace?.quoteId)
        assertEquals(3000.0, payload.booking.totalUsd, 0.001)
        assertEquals(1500.0, payload.booking.perPilgrimUsd, 0.001)
        assertFalse(payload.booking.includedServices.contains("madinahHotel"))
    }

    @Test fun changingPublishedFlightsInvalidatesThePreviousPrice() {
        val store = JourneyStore()
        store.updateTrip(trip())
        store.applyPackageTierComparison(PackageTier.STANDARD, quote, hotel, null)
        assertTrue(store.state.value.hasFinalGeneratorQuote)
        store.setPublishedSelection(completeID = "different-flight")
        assertFalse(store.state.value.hasFinalGeneratorQuote)
        store.applyPackageTierComparison(PackageTier.STANDARD, quote, hotel, null)
        store.clearPublishedFlightSelection()
        assertNull(store.state.value.quote)
        assertNull(store.state.value.selectedJourneyId)
    }
}
