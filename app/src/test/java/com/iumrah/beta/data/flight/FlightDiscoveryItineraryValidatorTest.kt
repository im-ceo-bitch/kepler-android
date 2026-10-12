package com.iumrah.beta.data.flight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlightDiscoveryItineraryValidatorTest {
    private val one = FlightDiscoveryVerifiedSegment(
        "TAS", "IST", "2026-10-12T10:00:00+05:00", "2026-10-12T12:00:00+03:00", "TK", "371",
    )
    private val two = FlightDiscoveryVerifiedSegment(
        "IST", "JED", "2026-10-12T15:30:00+03:00", "2026-10-12T19:30:00+03:00", "TK", "126",
    )

    @Test fun `verified itinerary must prove every leg and layover`() {
        assertEquals(listOf(one, two), FlightDiscoveryItineraryValidator.verified(listOf(one, two), "TAS", "JED", 1))
        assertEquals(210L, FlightDiscoveryItineraryValidator.connectionMinutes(one, two))
    }

    @Test fun `do not fabricate missing connection airports`() {
        assertNull(FlightDiscoveryItineraryValidator.verified(null, "TAS", "JED", 1))
        assertNull(FlightDiscoveryItineraryValidator.verified(listOf(one, two), "TAS", "JED", 0))
        assertNull(FlightDiscoveryItineraryValidator.verified(listOf(one, two.copy(originAirport = "DXB")), "TAS", "JED", 1))
        assertNull(FlightDiscoveryItineraryValidator.verified(listOf(one, two.copy(departureAt = "2026-10-12T11:00:00+03:00")), "TAS", "JED", 1))
    }
}
