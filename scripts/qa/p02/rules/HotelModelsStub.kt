package com.iumrah.beta.models.nuitee

data class NuiteeHotel(
    val id: String, val name: String, val address: String? = null,
    val roundedStars: Int = 0, val minimumRate: NuiteeMinimumRate? = null, val rateStatus: String? = null,
)
data class NuiteeMinimumRate(val hotelId: String, val price: Double, val currency: String)
data class NuiteeRoomRate(val offerId: String, val roomId: String, val price: Double, val currency: String)
