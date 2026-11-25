package com.iumrah.beta.domain.booking

/** iOS IumrahBookingConfirmationView.canRequestCode, before requesting an OTP. */
object BookingIdentityPolicy {
    fun contactValid(channel: String, normalized: String): Boolean = when (channel) {
        "sms" -> normalized.matches(Regex("^\\+998[0-9]{9}$"))
        "email" -> normalized.matches(Regex("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$"))
        else -> false
    }
    fun canRequestCode(first: String, last: String, channel: String, normalized: String): Boolean =
        first.trim().length >= 2 && last.trim().length >= 2 && contactValid(channel, normalized)
    fun initialChannel(phone: String, email: String) = if (phone.isBlank() && email.isNotBlank()) "email" else "sms"
}
