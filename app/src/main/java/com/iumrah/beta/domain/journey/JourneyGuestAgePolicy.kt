package com.iumrah.beta.domain.journey

import com.iumrah.beta.domain.trip.TripDraft

/**
 * Mirrors SwiftUI TripSummaryGuestDraft: child ages remain attached to the
 * same number of minors even when adults, route or rooms change. When the
 * number of minors changes, use the same -1 unknown-age placeholders as iOS
 * rather than inventing supplier-eligible ages.
 */
internal object JourneyGuestAgePolicy {
    fun afterTripEdit(before: TripDraft, next: TripDraft, ages: List<Int>): List<Int> {
        val minors = next.children + next.infants
        return if (ages.size == minors) ages.toList() else List(minors) { -1 }
    }
}
