package com.iumrah.beta.models.booking

import kotlinx.serialization.Serializable

@Serializable data class BusinessOfferPayload(val packageID: String, val revision: Int, val status: String,
    val expiresAt: String, val `package`: BusinessOfferPackage)
@Serializable data class BusinessOfferPackage(val totalUsd: Double, val input: BookingInputRecord,
    val hotelNames: BookingHotelNames, val flight: String, val includedServices: List<String> = emptyList())
@Serializable data class BusinessOfferAccept(val revision: Int, val firstName: String, val lastName: String,
    val channel: String, val verificationToken: String, val requestSecret: String)

object BusinessOfferPolicy {
    fun validToken(token: String): Boolean = token.matches(Regex("[a-f0-9]{64}"))
    fun path(token: String): String { require(validToken(token)); return "/api/bookings/offers/$token" }
    fun validReceipt(expectedPackage: String, response: BookingCreateResponse): Boolean =
        expectedPackage.isNotBlank() && response.booking.offerPackageID == expectedPackage &&
            response.booking.id.isNotBlank() && !response.accessToken.isNullOrBlank()
}
