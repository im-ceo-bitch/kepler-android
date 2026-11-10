package com.iumrah.beta.ui.flights

import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.models.flight.FlightCabinClass
import com.iumrah.beta.models.flight.FlightSearchFilters
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedFlightSelectionEngineTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val start = today.plusDays(12)
    private val end = start.plusDays(7)
    private val trip = TripDraft(origin = "TAS", arrivalAirport = SaudiArrivalAirport.MADINAH,
        departureDate = start, returnDate = end)

    private fun leg(from: String, to: String, day: LocalDate, number: String = "HY201") =
        CuratedFlightRecommendation.Leg(
            airline = "Uzbekistan Airways", flightNumber = number, airlineCode = "HY",
            origin = from, destination = to,
            departureAt = "${day}T10:00:00Z", arrivalAt = "${day}T14:00:00Z",
            durationMinutes = 240, stops = 0, cabinClass = "economy",
        )

    private fun published(id: String, outbound: CuratedFlightRecommendation.Leg,
        inbound: CuratedFlightRecommendation.Leg? = null, role: String? = null) =
        CuratedFlightRecommendation(
            id = id, outboundDate = outbound.departureAt.take(10),
            inboundDate = inbound?.departureAt?.take(10), cabinClass = "economy",
            observedAt = "${today}T00:00:00Z", outbound = outbound, inbound = inbound,
            nonstop = true, recommendationLabel = "published", journeyRole = role,
        )

    private fun api(id: String, from: String, to: String, date: LocalDate, fare: Double = 290.0) =
        FlightDiscoveryOffer(id = id, origin = from, destination = to, price = fare,
            airlineCode = "HY", flightNumber = id, departureAt = "${date}T07:00:00Z",
            durationMinutes = 240)

    @Test fun `paired publication only exposes its matching published return`() {
        val paired = published("p1", leg("TAS", "MED", start), leg("JED", "TAS", end))
        val other = published("p2", leg("TAS", "MED", start.plusDays(1), "HY202"),
            leg("JED", "TAS", end.plusDays(1)))
        val departures = GuidedFlightRules.options(trip, listOf(paired, other), emptyList(),
            emptyList(), false, null, today = today)
        assertEquals(2, departures.size)
        val selected = departures.first { it is GuidedFlightChoice.Published && it.recommendation.id == "p1" }
        val returns = GuidedFlightRules.options(trip, listOf(paired, other), emptyList(),
            emptyList(), true, selected, today = today)
        assertEquals(1, returns.size)
        assertTrue(GuidedFlightRules.compatible(selected, returns.single()))
        assertEquals(null, returns.single().priceUsd) // no fabricated published fare
    }

    @Test fun `published and data api sources never mix`() {
        val out = GuidedFlightChoice.Published(published("o", leg("TAS", "MED", start)), false)
        val back = GuidedFlightChoice.Data(api("r", "JED", "TAS", end))
        assertFalse(GuidedFlightRules.compatible(out, back))
    }

    @Test fun `one way api pair only uses exact route and valid order`() {
        val outbound = api("out", "TAS", "MED", start)
        val reverse = api("return", "JED", "TAS", end)
        val wrong = api("wrong", "JED", "FEG", end)
        val choices = GuidedFlightRules.options(trip, emptyList(), listOf(outbound),
            listOf(reverse, wrong), true, GuidedFlightChoice.Data(outbound), today = today)
        assertEquals(1, choices.size)
        assertTrue(GuidedFlightRules.compatible(GuidedFlightChoice.Data(outbound), choices.single()))
        assertFalse(GuidedFlightRules.compatible(GuidedFlightChoice.Data(reverse), GuidedFlightChoice.Data(outbound)))
    }

    @Test fun `bad snapshot and economy only restriction prevent unsafe api fare`() {
        val economyRows = listOf(
            api("no-price", "TAS", "MED", start, fare = 0.0),
            api("valid", "TAS", "MED", start),
        )
        val options = GuidedFlightRules.options(trip, emptyList(), economyRows,
            emptyList(), false, null, today = today)
        assertEquals(1, options.size)
        val businessTrip = trip.copy(flightFilters = FlightSearchFilters(cabinClass = FlightCabinClass.BUSINESS))
        assertTrue(GuidedFlightRules.options(businessTrip, emptyList(), economyRows,
            emptyList(), false, null, today = today).isEmpty())
    }
}
