package com.iumrah.beta.domain.nuitee

import com.iumrah.beta.models.nuitee.*
import java.time.LocalDate

fun main() {
    fun okay(value: Boolean, label: String) = check(value) { label }
    val hotels = listOf(
        NuiteeHotel("unknown-a", "Hilton Makkah Tower", "Ibrahim Al Khalil", 0),
        NuiteeHotel("star4-a", "Makkah Hilton Tower", "King Fahd Road", 4),
        NuiteeHotel("star5", "Marriott Makkah", "King Abdul Aziz", 5),
        NuiteeHotel("star4-b", "The Hilton Makkah", "Ibrahim Al Khalil", 4),
        NuiteeHotel("unknown-b", "Makkah City", "Hilton Boulevard", 0),
    )
    okay(NuiteeRules.filterHotels(hotels,0,"").map { it.id } == hotels.map { it.id }, "unfiltered original order")
    okay(NuiteeRules.filterHotels(hotels,4,"").map { it.id } == listOf("star4-a","star4-b","unknown-a","unknown-b"),
        "star 4 shows confirmed stars then unknown-star hotels")
    okay(NuiteeRules.filterHotels(hotels,5,"Hilton").map { it.id } == listOf("unknown-a","unknown-b"),
        "no false 5 stars for 4-star Hilton, unknown remains discoverable")
    okay(NuiteeRules.filterHotels(hotels,0,"Makkah Hilton").map { it.id } == listOf("star4-a"),
        "search matches exact contiguous phrase, not unordered bag of terms")
    okay(NuiteeRules.filterHotels(hotels,0,"ibrahim al khalil").map { it.id } == listOf("unknown-a","star4-b"),
        "case insensitive phrase also matches address")
    okay(NuiteeRules.filterHotels(hotels,4,"  hilton  ").map { it.id } == listOf("star4-a","star4-b","unknown-a","unknown-b"),
        "trimmed search preserves stable grouping")
    val valid = NuiteeRoomRate("offer","room",105.0,"usd")
    okay(NuiteeRules.validRoomRate(valid),"valid priced offer")
    okay(!NuiteeRules.validRoomRate(valid.copy(offerId="")) && !NuiteeRules.validRoomRate(valid.copy(price=-1.0)) &&
        !NuiteeRules.validRoomRate(valid.copy(currency="EUR")),"invalid offers rejected")
    val today=LocalDate.now()
    okay(NuiteeRules.validStay(today,today.plusDays(1),2,listOf(8)) &&
        !NuiteeRules.validStay(today,today,2,emptyList()),"stay validation uses positive nights")
    println("P02 Hotel rules: PASS (9 SwiftUI parity assertions)")
}
