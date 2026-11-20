package com.iumrah.beta.ui.home

import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Contract tests for the staff-published flight carousel, not live inventory. */
class PublishedHomeRulesTest {
    private fun leg(from: String, to: String, depart: String, arrive: String) =
        CuratedFlightRecommendation.Leg(
            airline = "Airline", flightNumber = "AB100", airlineCode = "AB",
            origin = from, destination = to, departureAt = depart, arrivalAt = arrive,
            durationMinutes = 300, stops = 0, cabinClass = "economy",
        )

    private fun flight(id: String, from: String, to: String, depart: String, arrive: String, paired: Boolean = false) =
        PublishedHomeLeg(id, leg(from, to, depart, arrive), paired, 240.0)

    @Test fun `departure day is calculated at departure airport, not device local date`() {
        assertEquals(LocalDate.of(2027, 1, 1), PublishedHomeRules.day("2026-12-31T20:10:00Z", "TAS"))
        assertEquals(LocalDate.of(2026, 12, 31), PublishedHomeRules.day("2026-12-31T20:10:00Z", "JED"))
    }

    @Test fun `different published one-way rows may form an independent round trip`() {
        val outbound = flight("out", "TAS", "JED", "2027-01-07T08:00:00Z", "2027-01-07T13:00:00Z")
        val inbound = flight("back", "JED", "TAS", "2027-01-16T09:00:00Z", "2027-01-16T14:00:00Z")
        assertTrue(PublishedHomeRules.compatible(outbound, inbound, JourneyScope.MAKKAH_ONLY))
    }

    @Test fun `paired flights cannot mix different canonical publication IDs`() {
        val outbound = flight("pair-A", "TAS", "JED", "2027-01-07T08:00:00Z", "2027-01-07T13:00:00Z", true)
        val different = flight("pair-B", "JED", "TAS", "2027-01-16T09:00:00Z", "2027-01-16T14:00:00Z", true)
        val matching = different.copy(rowID="pair-A")
        assertFalse(PublishedHomeRules.compatible(outbound, different, JourneyScope.MAKKAH_ONLY))
        assertTrue(PublishedHomeRules.compatible(outbound, matching, JourneyScope.MAKKAH_ONLY))
    }

    @Test fun `invalid airport and reversed chronology must not be accepted`() {
        val outbound = flight("out", "TAS", "JED", "2027-01-10T08:00:00Z", "2027-01-10T13:00:00Z")
        val wrongAirport = flight("back", "MED", "NMA", "2027-01-16T09:00:00Z", "2027-01-16T14:00:00Z")
        val sameDay = flight("back", "JED", "TAS", "2027-01-10T15:00:00Z", "2027-01-10T20:00:00Z")
        assertFalse(PublishedHomeRules.compatible(outbound, wrongAirport, JourneyScope.MAKKAH_ONLY))
        assertFalse(PublishedHomeRules.compatible(outbound, sameDay, JourneyScope.MAKKAH_ONLY))
    }

    @Test fun `two-city trip needs at least two calendar days before return`() {
        val outbound = flight("out", "TAS", "JED", "2027-01-10T08:00:00Z", "2027-01-10T13:00:00Z")
        val nextDay = flight("back", "JED", "TAS", "2027-01-11T09:00:00Z", "2027-01-11T14:00:00Z")
        val twoDays = nextDay.copy(leg = leg("JED", "TAS", "2027-01-12T09:00:00Z", "2027-01-12T14:00:00Z"))
        assertFalse(PublishedHomeRules.compatible(outbound, nextDay, JourneyScope.MAKKAH_AND_MADINAH))
        assertTrue(PublishedHomeRules.compatible(outbound, twoDays, JourneyScope.MAKKAH_AND_MADINAH))
    }
}
