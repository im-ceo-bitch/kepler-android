package com.iumrah.beta.domain.journey

import com.iumrah.beta.domain.trip.TripDraft
import java.time.LocalDate

fun main() {
    fun okay(value: Boolean, description: String) = check(value) { description }
    val dates = LocalDate.now().plusDays(20)
    val room = FamilyHotelJourneyFactory.firstRoom(12, listOf(1, 4, 8, 16), 1)
    okay(room.rooms == 3 && room.adults == 4 && room.childrenAges == listOf(1, 16), "first-room iOS round-robin")
    okay(FamilyHotelJourneyFactory.firstRoom(6, emptyList(), 4).let { it.rooms==4 && it.adults==2 }, "requested rooms override minimum")
    okay(FamilyHotelJourneyFactory.firstRoom(5, listOf(2), 1).let { it.rooms==1 && it.adults==5 && it.childrenAges==listOf(2) }, "single room")
    val plan = TripDraft(departureDate=dates, returnDate=dates.plusDays(8))
    val ages = listOf(0, 1, 4, 9)
    val first = FamilyHotelJourneyFactory.create(plan, 7, ages, 1, dates.plusDays(3), 9, routeConfigured = true)
    val second = FamilyHotelJourneyFactory.create(plan, 7, ages, 1, dates.plusDays(3), 9, routeConfigured = false)
    okay(first !== second, "new independent store per family flow")
    okay(first.state.value.let { it.rooms==2 && it.adults==7 && it.infants==2 && it.children==2 }, "family draft")
    okay(first.childrenAges == ages && second.childrenAges == ages, "child ages not lost")
    okay(first.state.value.departureDate == dates && first.state.value.returnDate == dates.plusDays(8), "configured route retained")
    okay(second.state.value.departureDate == dates.plusDays(3) && second.state.value.returnDate == dates.plusDays(12), "unconfigured route uses hotel dates")
    okay(plan.adults == 2 && plan.rooms == 1, "input plan not mutated")
    val before = TripDraft(adults=3,children=1,infants=1,rooms=2)
    okay(JourneyGuestAgePolicy.afterTripEdit(before,before.copy(rooms=3),listOf(7,1))==listOf(7,1), "room edit retains ages")
    okay(JourneyGuestAgePolicy.afterTripEdit(before,before.copy(departureDate=dates),listOf(7,1))==listOf(7,1), "date edit retains ages")
    okay(JourneyGuestAgePolicy.afterTripEdit(before,before.copy(adults=4),listOf(7,1))==listOf(7,1), "changing adults retains the children ages")
    okay(JourneyGuestAgePolicy.afterTripEdit(before,before.copy(children=0),listOf(7,1))==listOf(-1), "child composition invalidates ages")
    var invalid=0
    listOf<()->Any>(
        { FamilyHotelJourneyFactory.firstRoom(13, emptyList(), 1) },
        { FamilyHotelJourneyFactory.firstRoom(5, listOf(18), 1) },
        { FamilyHotelJourneyFactory.firstRoom(5, emptyList(), 6) },
        { FamilyHotelJourneyFactory.firstRoom(5, listOf(1,2,3,4,5), 1) },
        { FamilyHotelJourneyFactory.create(plan,3,ages,1,dates,0,false) },
        { FamilyHotelJourneyFactory.create(plan,3,ages,1,LocalDate.now().minusDays(1),4,false) }
    ).forEach { try { it() } catch (_: IllegalArgumentException) { invalid++ } }
    okay(invalid==6,"invalid occupancy and dates rejected")
    println("P02 family occupancy and child age policy: PASS (15 scenario checks)")
}
