package com.iumrah.beta.core.navigation

import com.iumrah.beta.core.design.IOSLayoutClass
import com.iumrah.beta.core.design.IOSLayoutProfile

fun main() {
    fun profile(width: Float, forceCompact: Boolean = false) = IOSLayoutProfile.resolve(width, forceCompact)
    check(!profile(699f).usesSidebar && profile(699f).rootMaxWidth == null)
    check(profile(700f).layoutClass == IOSLayoutClass.REGULAR && profile(700f).rootMaxWidth == 760)
    check(profile(999f).detailMaxWidth == 760 && profile(999f).gridColumns == 2)
    check(profile(1000f).layoutClass == IOSLayoutClass.WIDE && profile(1000f).rootMaxWidth == 1040)
    check(profile(1000f).detailMaxWidth == 920 && profile(1000f).gridColumns == 3)
    check(profile(1600f, true).pagePadding == 18 && !profile(1600f, true).usesSidebar)
    check(profile(700f).pagePadding == 24 && profile(1000f).pagePadding == 30)
    val chrome = AppChromeStore()
    val keys = RouteStateKeys()
    chrome.openAccountSecurity()
    val security = keys.key(AppTab.ACCOUNT, chrome.state.value.route)
    chrome.openAccountPasswordSecurity()
    val password = keys.key(AppTab.ACCOUNT, chrome.state.value.route)
    check(security != password && keys.prune(chrome.state.value).isEmpty())
    chrome.navigate(AppTab.HOTELS)
    chrome.openHotel("private-hotel-123")
    val hotel = keys.key(AppTab.HOTELS, chrome.state.value.route)
    check(!hotel.contains("private-hotel-123") && keys.prune(chrome.state.value).isEmpty())
    chrome.navigate(AppTab.ACCOUNT)
    check(keys.key(AppTab.ACCOUNT, chrome.state.value.route) == password)
    check(chrome.back() && keys.prune(chrome.state.value) == listOf(password))
    check(keys.key(AppTab.ACCOUNT, chrome.state.value.route) == security)
    chrome.openAccountPasswordSecurity()
    check(keys.key(AppTab.ACCOUNT, chrome.state.value.route) != password)
    val same = AppRoute.Notifications
    check(keys.key(AppTab.ACCOUNT, same) != keys.key(AppTab.HOTELS, same))
    val token = "secret-token-never-in-state-key"
    check(!keys.key(AppTab.BOOKING, AppRoute.PublicTrip(token)).contains(token))
    check(RouteStateKeys().key(AppTab.ACCOUNT, AppRoute.AccountSecurity) != security)
    chrome.navigate(AppTab.HOTELS)
    chrome.openESIM()
    check(chrome.state.value.currentTab == AppTab.HOME && chrome.state.value.route == AppRoute.ESIM)
    check(RouteStateKeys.canRetain(AppRoute.HotelDetail("example")))
    check(!RouteStateKeys.canRetain(AppRoute.PublicTrip(token)))
    check(!RouteStateKeys.canRetain(AppRoute.AccountSecurity))
    check(!RouteStateKeys.canRetain(AppRoute.BookingCheckout))
    println("Block 01 layout and route-state contracts: PASS")
}
