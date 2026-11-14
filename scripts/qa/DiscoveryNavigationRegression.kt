package com.iumrah.beta.core.navigation

fun main() {
    var count = 0
    fun verify(condition: Boolean, name: String) { check(condition) { name }; count++ }
    val chrome = AppChromeStore()
    fun state() = chrome.state.value
    verify(state().currentTab == AppTab.HOME && state().route == AppRoute.Root, "default Home")
    chrome.openDiscoveryFlights()
    verify(state().route == AppRoute.Root && state().currentTab == AppTab.HOTELS && state().discoveryFlights, "Home → root Flights preserves tab bar")
    verify(state().backStack.isEmpty(), "root navigation clears previous stack")
    chrome.openDiscoveryTransfer()
    verify(state().route == AppRoute.TransferService && state().currentTab == AppTab.HOTELS, "transfer preserves originating tab")
    verify(chrome.back() && state().route == AppRoute.Root && state().discoveryFlights, "return to selected Flights board")
    chrome.setDiscoveryFlights(false)
    verify(!state().discoveryFlights && state().currentTab == AppTab.HOTELS, "hotel board selection")
    chrome.openDiscoveryPackage()
    verify(state().route == AppRoute.FinalPackage && state().currentTab == AppTab.HOTELS, "package uses existing review")
    verify(chrome.back() && state().route == AppRoute.Root && !state().discoveryFlights, "return to Hotels board")
    chrome.openDiscoveryBuild()
    verify(state().route == AppRoute.Root && state().currentTab == AppTab.HOME && state().discoveryBuild, "build opens Home search")
    chrome.consumeDiscoveryBuild()
    verify(!state().discoveryBuild, "one shot build request")
    chrome.openDiscoveryBuild()
    chrome.navigate(AppTab.BOOKING)
    verify(!state().discoveryBuild && !state().discoveryFlights && state().currentTab == AppTab.BOOKING, "normal tab clears discovery request")
    chrome.openDiscoveryFlights()
    chrome.navigate(AppTab.HOTELS)
    verify(!state().discoveryFlights, "normal hotel tab defaults to hotels")
    chrome.startNewTrip()
    verify(state().route == AppRoute.TripBuilder && state().currentTab == AppTab.BOOKING, "existing trip builder unchanged")
    println("V8 discovery navigation: $count checks passed")
}
