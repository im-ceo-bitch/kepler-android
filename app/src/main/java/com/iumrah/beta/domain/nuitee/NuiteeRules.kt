package com.iumrah.beta.domain.nuitee

import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.models.nuitee.NuiteeRoomRate
import java.time.LocalDate

/** Pure, testable parity rules: never turn a provider cache or pending request into a verified quote. */
object NuiteeRules {
    fun validRoomRate(rate: NuiteeRoomRate): Boolean =
        rate.offerId.isNotBlank() && rate.roomId.isNotBlank() && rate.currency.equals("USD", true) &&
            rate.price.isFinite() && rate.price > 0.0

    fun listedRate(hotel: NuiteeHotel, stale: Boolean): Double? = hotel.minimumRate?.let { rate ->
        if (stale || hotel.rateStatus in setOf("cached", "provider_error", "not_requested", "loading") ||
            !rate.currency.equals("USD", true) || !rate.price.isFinite() || rate.price <= 0) null else rate.price
    }

    fun filterHotels(hotels: List<NuiteeHotel>, stars: Int, search: String): List<NuiteeHotel> {
        val terms = search.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return hotels.filter { hotel -> (stars == 0 || hotel.roundedStars == stars) &&
            terms.all { term -> (hotel.name + " " + hotel.address.orEmpty()).contains(term, ignoreCase = true) }
        }
    }

    fun validStay(checkin: LocalDate, checkout: LocalDate, adults: Int, roomChildren: List<Int>): Boolean =
        checkout.isAfter(checkin) && adults in 1..9 && roomChildren.all { it in 0..17 }

    fun sameOffer(a: NuiteeRoomRate, b: NuiteeRoomRate): Boolean = a.offerId == b.offerId && a.roomId == b.roomId
}
