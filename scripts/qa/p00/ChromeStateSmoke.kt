package com.iumrah.beta.core.navigation

import java.time.LocalDate

fun main() {
    val c = AppChromeStore()
    fun assertState(ok: Boolean, msg: String) { check(ok) { msg } }
    assertState(c.state.value.route == AppRoute.Root, "initial root")
    c.startNewTrip()
    assertState(c.state.value.currentTab == AppTab.HOME, "new trip opens Home")
    assertState(c.state.value.discoveryBuild, "new trip requests Home search")
    c.consumeDiscoveryBuild()
    assertState(!c.state.value.discoveryBuild, "request consumed")
    val checkin = LocalDate.of(2026, 12, 14)
    c.findHotels("madinah", checkin, 9, 3, listOf(8))
    val request = c.state.value.hotelSearch ?: error("search not handed off")
    assertState(request.city == "madinah" && request.checkin == checkin && request.nights == 9 && request.adults == 3 && request.childrenAges == listOf(8), "search handoff exact")
    assertState(c.state.value.currentTab == AppTab.HOTELS && c.state.value.route == AppRoute.Root, "search opens Hotels root")
    c.consumeHotelSearch(request.revision - 1)
    assertState(c.state.value.hotelSearch == request, "stale acknowledgement must not clear a newer request")
    c.consumeHotelSearch(request.revision)
    assertState(c.state.value.hotelSearch == null, "search consumed exactly once")
    c.findHotels("madinah", checkin, 9, 3, listOf(8))
    assertState(c.state.value.hotelSearch!!.revision > request.revision, "repeating the same query is a distinct event")
    c.consumeHotelSearch(c.state.value.hotelSearch!!.revision)
    c.openHotel("hotel-1")
    assertState(c.state.value.route == AppRoute.HotelDetail("hotel-1"), "hotels detail")
    c.navigate(AppTab.HOME)
    assertState(c.state.value.route == AppRoute.Root, "Home should not inherit hotels detail")
    c.openAccountSecurity()
    assertState(c.state.value.currentTab == AppTab.ACCOUNT, "account destination owns the route")
    assertState(c.back() && c.state.value.route == AppRoute.Root && c.state.value.currentTab == AppTab.ACCOUNT, "account back never enters Home")
    c.navigate(AppTab.HOTELS)
    assertState(c.state.value.route == AppRoute.HotelDetail("hotel-1"), "returning to Hotels restores own detail")
    assertState(c.back() && c.state.value.route == AppRoute.Root, "back from hotels detail returns to hotels root")
    c.navigate(AppTab.BOOKING)
    c.openFinalPackage()
    c.navigate(AppTab.HOME)
    c.navigate(AppTab.BOOKING)
    assertState(c.state.value.route == AppRoute.FinalPackage, "Booking tab preserves own route")
    c.openGuidedFlights()
    assertState(c.state.value.currentTab == AppTab.HOME && c.state.value.route == AppRoute.GuidedFlights,
        "guided flights belong to Home NavigationStack")
    assertState(c.back() && c.state.value.route == AppRoute.Root && c.state.value.currentTab == AppTab.HOME,
        "guided flights return to Home")
    // iOS HotelsHomeView presents a second, independently owned journey.
    val normalJourney = com.iumrah.beta.domain.journey.JourneyStore()
    val familyJourney = com.iumrah.beta.domain.journey.JourneyStore(adults=7)
    c.navigate(AppTab.HOTELS)
    c.beginFamilyJourney(familyJourney)
    assertState(c.state.value.currentTab == AppTab.HOTELS && c.state.value.route == AppRoute.GuidedFlights, "family starts inside Hotels")
    assertState(c.journeyForRoute(c.state.value.route, c.state.value.currentTab, normalJourney) === familyJourney,
        "family flow never reuses main journey")
    c.openNuiteeJourneyHotels()
    assertState(c.state.value.currentTab == AppTab.HOTELS && c.state.value.route == AppRoute.NuiteeJourneyHotels, "family hotels remain in Hotels stack")
    c.openNuiteeJourneyReview()
    assertState(c.state.value.currentTab == AppTab.HOTELS && c.state.value.route == AppRoute.NuiteeJourneyReview, "family review preserves owner")
    c.openBookingCheckout()
    assertState(c.state.value.currentTab == AppTab.HOTELS && c.journeyForRoute(c.state.value.route,c.state.value.currentTab,normalJourney) === familyJourney,
        "checkout owns family data")
    assertState(c.back() && c.state.value.route == AppRoute.NuiteeJourneyReview, "family checkout back returns review")
    c.navigate(AppTab.HOME)
    assertState(c.journeyForRoute(AppRoute.GuidedFlights, AppTab.HOME, normalJourney) === normalJourney,
        "Home guided journey remains isolated")
    c.openDiscoveryFlights()
    val firstCommand = c.state.value.discoveryFlightsRevision
    c.openDiscoveryFlights()
    assertState(c.state.value.discoveryFlightsRevision == firstCommand + 1, "repeated Flights command not lost")
    c.reportMastheadVisibility(AppTab.HOTELS, false)
    assertState(c.rootMastheadVisible.value[AppTab.HOTELS] == false, "status surface tracks header")
    c.reportMastheadVisibility(AppTab.HOTELS, true)
    assertState(c.rootMastheadVisible.value[AppTab.HOTELS] == true, "status surface restores when masthead visible")
    var failed = 0
    listOf({ c.findHotels("invalid", checkin, 7, 2) }, { c.findHotels("makkah", checkin, 29, 2) }, { c.findHotels("makkah", checkin, 7, 0) }, { c.findHotels("makkah", checkin, 7, 2, listOf(-1)) }).forEach { f ->
        try { f() } catch (x: IllegalArgumentException) { failed++ }
    }
    assertState(failed == 4, "invalid hotel searches rejected")
    println("P00/P02 chrome and hotel search smoke: PASSED")
}
