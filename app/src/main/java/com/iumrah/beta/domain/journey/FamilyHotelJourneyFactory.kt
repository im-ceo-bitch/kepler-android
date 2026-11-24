package com.iumrah.beta.domain.journey

import com.iumrah.beta.domain.trip.TripDraft
import java.time.LocalDate

/**
 * Matches HotelsHomeView.roomSearch/manualHotelQuote. A supplier room price is
 * never the family's full package price. This creates a NEW guided JourneyStore:
 * it must never mutate the package currently open in the Home tab.
 */
internal data class FirstHotelRoomQuote(val rooms: Int, val adults: Int, val childrenAges: List<Int>)

internal object FamilyHotelJourneyFactory {
    fun firstRoom(adults: Int, childrenAges: List<Int>, requestedRooms: Int): FirstHotelRoomQuote {
        require(adults in 1..12 && requestedRooms in 1..minOf(4, adults))
        require(childrenAges.size <= 4 && childrenAges.all { it in 0..17 })
        val count = minOf(adults, maxOf(requestedRooms, (adults + 4) / 5))
        return FirstHotelRoomQuote(
            rooms = count,
            adults = (adults + count - 1) / count,
            childrenAges = childrenAges.filterIndexed { index, _ -> index % count == 0 },
        )
    }

    fun create(
        planned: TripDraft,
        adults: Int,
        childrenAges: List<Int>,
        requestedRooms: Int,
        checkin: LocalDate,
        nights: Int,
        routeConfigured: Boolean,
    ): JourneyStore {
        require(nights in 1..28 && !checkin.isBefore(LocalDate.now()))
        val quote = firstRoom(adults, childrenAges, requestedRooms)
        val trip = planned.copy(
            adults = adults,
            children = childrenAges.count { it >= 2 },
            infants = childrenAges.count { it < 2 },
            rooms = quote.rooms,
            departureDate = if (routeConfigured) planned.departureDate else checkin,
            returnDate = if (routeConfigured) planned.returnDate else checkin.plusDays(maxOf(2, nights).toLong()),
        )
        return JourneyStore().also {
            it.updateTrip(trip)
            it.updateGuestAges(childrenAges)
        }
    }
}
