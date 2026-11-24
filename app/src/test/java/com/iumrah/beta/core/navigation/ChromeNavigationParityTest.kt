package com.iumrah.beta.core.navigation

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** iOS reference: AppShell/AppChromeStore.swift + Views/Home/HomePersonalizationSections.swift. */
class ChromeNavigationParityTest {
    @Test fun `new trip opens the existing Home configurator instead of legacy builder`() {
        val chrome = AppChromeStore()
        chrome.navigate(AppTab.ACCOUNT)
        chrome.startNewTrip()
        assertEquals(AppTab.HOME, chrome.state.value.currentTab)
        assertEquals(AppRoute.Root, chrome.state.value.route)
        assertTrue(chrome.state.value.discoveryBuild)
        chrome.consumeDiscoveryBuild()
        assertFalse(chrome.state.value.discoveryBuild)
    }

    @Test fun `hotel search hands off entire request and consumes matching revision only`() {
        val chrome = AppChromeStore()
        val day = LocalDate.of(2027, 1, 14)
        chrome.findHotels("madinah", day, 9, 3, listOf(6))
        val first = chrome.state.value.hotelSearch!!
        assertEquals(AppTab.HOTELS, chrome.state.value.currentTab)
        assertEquals(day, first.checkin)
        assertEquals(9, first.nights)
        assertEquals(3, first.adults)
        assertEquals(listOf(6), first.childrenAges)
        chrome.findHotels("madinah", day, 9, 3, listOf(6))
        val second = chrome.state.value.hotelSearch!!
        assertTrue(second.revision > first.revision)
        chrome.consumeHotelSearch(first.revision)
        assertEquals(second, chrome.state.value.hotelSearch)
        chrome.consumeHotelSearch(second.revision)
        assertNull(chrome.state.value.hotelSearch)
    }

    @Test fun `tab switches retain only their own navigation stack`() {
        val chrome = AppChromeStore()
        chrome.openHotel("makkah-h1")
        assertEquals(AppTab.HOTELS, chrome.state.value.currentTab)
        chrome.navigate(AppTab.ACCOUNT)
        chrome.openAccountSecurity()
        assertEquals(AppRoute.AccountSecurity, chrome.state.value.route)
        assertTrue(chrome.back())
        assertEquals(AppRoute.Root, chrome.state.value.route)
        chrome.navigate(AppTab.HOTELS)
        assertEquals(AppRoute.HotelDetail("makkah-h1"), chrome.state.value.route)
        assertTrue(chrome.back())
        assertEquals(AppRoute.Root, chrome.state.value.route)
    }

    @Test fun `guided flights are owned by the Home tab`() {
        val chrome = AppChromeStore()
        chrome.navigate(AppTab.BOOKING)
        chrome.openGuidedFlights()
        assertEquals(AppTab.HOME, chrome.state.value.currentTab)
        assertEquals(AppRoute.GuidedFlights, chrome.state.value.route)
        assertTrue(chrome.back())
        assertEquals(AppTab.HOME, chrome.state.value.currentTab)
        assertEquals(AppRoute.Root, chrome.state.value.route)
    }

    @Test fun `invalid hotel search never reaches the catalogue`() {
        val chrome = AppChromeStore()
        assertThrows(IllegalArgumentException::class.java) {
            chrome.findHotels("makkah", LocalDate.now(), 0, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            chrome.findHotels("other", LocalDate.now(), 7, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            chrome.findHotels("makkah", LocalDate.now(), 7, 2, listOf(25))
        }
        assertNull(chrome.state.value.hotelSearch)
    }
}
