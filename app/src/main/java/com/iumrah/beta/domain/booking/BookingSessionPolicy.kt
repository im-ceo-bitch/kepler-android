package com.iumrah.beta.domain.booking

/** Account-only cached trips must never be visible to a different account. */
object BookingSessionPolicy {
    fun visible(bookingToken: String, ownerID: String?, accountID: String?): Boolean =
        bookingToken.isNotBlank() || (!ownerID.isNullOrBlank() && ownerID == accountID)

    fun pilgrimDisplay(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        return value.takeIf { it.length in 1..8 && it.all { digit -> digit in '0'..'9' } && (it.toIntOrNull() ?: 0) > 0 }
            ?.padStart(8, '0')
    }

    fun preserveRoom(localHotel: String?, localCategory: Boolean, remoteHotel: String, remoteCategory: Boolean, remoteRoom: String?): Boolean =
        localHotel == remoteHotel && localCategory && !remoteCategory && remoteRoom == null
}
