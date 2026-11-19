package com.iumrah.beta.domain.booking

/** Account-only cached trips must never be visible to a different account. */
object BookingSessionPolicy {
    // Canonical numeric pilgrim IDs; unlike arbitrary account labels, zero and malformed values are not identities.
    fun sameIdentity(left: String?, right: String?): Boolean =
        pilgrimDisplay(left)?.let { it == pilgrimDisplay(right) } == true

    fun acceptsPushOwner(eventOwner: String?, accountOwner: String?, bookingOwner: String?, bookingToken: String?): Boolean {
        if (eventOwner == null) return true
        if (sameIdentity(eventOwner, accountOwner)) return true
        return !bookingToken.isNullOrBlank() && sameIdentity(eventOwner, bookingOwner)
    }

    fun careIsActive(status: String): Boolean = status.trim().uppercase().let {
        it.isNotEmpty() && it !in setOf("COMPLETED", "CANCELLED", "CANCELED", "DELETED")
    }

    fun careScope(status: String, bookingID: String, bookingToken: String, owner: String?, accountOwner: String?, accountToken: String?): String? {
        if (!careIsActive(status) || bookingID.isBlank() || bookingID == "account") return null
        if (!accountToken.isNullOrBlank() && sameIdentity(owner, accountOwner)) return "account"
        return bookingID.takeIf { bookingToken.isNotBlank() }
    }

    fun normalizedEmail(value: String) = value.trim().lowercase(java.util.Locale.ROOT)
    fun normalizedPhone(value: String) = "+" + value.filter { it in '0'..'9' }
    fun matchesContact(channel: String, expected: String, actual: String?): Boolean = when (channel) {
        "email" -> !actual.isNullOrBlank() && normalizedEmail(expected) == normalizedEmail(actual)
        "sms" -> !actual.isNullOrBlank() && normalizedPhone(expected).length >= 9 && normalizedPhone(expected) == normalizedPhone(actual)
        else -> false
    }

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
