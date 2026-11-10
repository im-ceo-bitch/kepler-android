package com.iumrah.beta.domain.nuitee

import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.models.nuitee.NuiteeMinimumRate
import com.iumrah.beta.models.nuitee.NuiteeRoomRate
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NuiteeJourneyParityTest {
    private val start = LocalDate.of(2026, 12, 1)
    private val end = start.plusDays(7)
    private val hotel = NuiteeHotel("hotel-1", "Hilton Makkah", "makkah", stars = 4.8,
        minimumRate = NuiteeMinimumRate("hotel-1", 510.0))
    private val rate = NuiteeRoomRate("offer-1", "room-1", "Deluxe", price = 510.0)
    private val room = NuiteeRoomSelection("makkah:0", "makkah", 0, hotel, rate, start, end, 2, emptyList())
    private val outbound = NuiteeFlightSnapshot("out", "TAS", "JED", "HY", "HY10", "HY", 210, 0,
        "2026-12-01T10:00:00Z", null, 250.0)
    private val inbound = NuiteeFlightSnapshot("in", "JED", "TAS", "HY", "HY11", "HY", 210, 0,
        "2026-12-08T10:00:00Z", null, 275.0)
    private val trip = TripDraft(adults = 2, scope = JourneyScope.MAKKAH_ONLY)
    private val selected = NuiteeJourneyDraft(flights = listOf(outbound, inbound), rooms = mapOf(room.key to room))

    @Test fun `catalog never quotes stale or invalid supplier rates`() {
        assertEquals(5, hotel.roundedStars)
        assertEquals(1, NuiteeRules.filterHotels(listOf(hotel), 5, "hilton makka").size)
        assertEquals(510.0, NuiteeRules.listedRate(hotel, stale = false)!!, 0.01)
        assertNull(NuiteeRules.listedRate(hotel, stale = true))
        assertFalse(NuiteeRules.validRoomRate(rate.copy(price = Double.NaN)))
        assertFalse(NuiteeRules.validRoomRate(rate.copy(currency = "EUR")))
        assertFalse(NuiteeRules.validRoomRate(rate.copy(price = 0.0)))
        assertTrue(NuiteeRules.validRoomRate(rate))
    }

    @Test fun `room count and pending statuses are explicit`() {
        assertTrue(selected.roomsComplete(trip))
        assertFalse(selected.roomsComplete(trip.copy(rooms = 2)))
        val pending = NuiteePendingHotel(room.key, room.city, 0, hotel, start, end, 2, emptyList())
        val unpriced = selected.copy(rooms = emptyMap(), pending = mapOf(pending.key to pending))
        assertTrue(unpriced.roomsComplete(trip))
        val tentative = NuiteeJourneyPricing.estimate(trip, unpriced)!!
        assertTrue(tentative.incompleteHotels)
        assertTrue(tentative.totalUsd < NuiteeJourneyPricing.estimate(trip, selected)!!.totalUsd)
    }

    @Test fun `local estimate needs fully priced adult flights and never creates a booking quote`() {
        val value = NuiteeJourneyPricing.estimate(trip, selected)!!
        assertTrue(value.totalUsd > BigDecimal.ZERO)
        assertEquals(value.totalUsd, value.lines.fold(BigDecimal.ZERO) { total, line -> total + line.usd })
        assertNull(NuiteeJourneyPricing.estimate(trip.copy(children = 1), selected))
        assertNull(NuiteeJourneyPricing.estimate(trip.copy(infants = 1), selected))
        assertNull(NuiteeJourneyPricing.estimate(trip, selected.copy(flights = listOf(outbound.copy(adultFareUSD = null), inbound))))
        val vip = NuiteeJourneyPricing.estimate(trip, selected, vehicle = TransferVehicleKind.YUKON)!!
        // Upgrade for Makkah-only is zero, matching the authoritative transfer policy.
        assertEquals(0, vip.totalUsd.compareTo(value.totalUsd))
    }
}
