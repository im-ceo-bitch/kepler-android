package com.iumrah.beta.domain.journey

import com.iumrah.beta.domain.trip.TripDraft
class FakeFlow<T>(var value: T)
class JourneyStore {
    val state = FakeFlow(TripDraft())
    var childrenAges: List<Int> = emptyList(); private set
    fun updateTrip(trip: TripDraft) { state.value = trip }
    fun updateGuestAges(ages: List<Int>) { check(ages.size == state.value.children + state.value.infants);childrenAges=ages }
}
