package com.iumrah.beta.data.booking

import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.domain.booking.ItineraryResolution
import com.iumrah.beta.models.account.IumrahFriendCreditApplyRequest
import com.iumrah.beta.models.account.IumrahFriendGiftRedeemRequest
import com.iumrah.beta.models.account.IumrahFriendsBookingSummary
import com.iumrah.beta.models.account.IumrahSecurityConfirmationRequest
import com.iumrah.beta.models.account.IumrahSecurityConfirmationResponse
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.models.hotel.HotelRoom
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
import kotlinx.serialization.Serializable

class BookingService(private val api: APIClient) {
    @Serializable private data class RecoveryRequest(val bookingNumber:String,val channel:String,val verificationToken:String)
    suspend fun recoverBooking(number:String,channel:String,proof:String):BookingCreateResponse {
        require(number.isNotBlank() && channel in setOf("email","sms") && proof.isNotBlank())
        return api.post("/api/package/booking-contact/recover",RecoveryRequest(number,channel,proof))
    }
    suspend fun createBooking(request: BookingCreateEnvelope): BookingCreateResponse =
        api.post(AppConfig.PACKAGE_BOOKING_PATH, request)

    suspend fun startGuestContact(channel: String, contact: String, locale: String): GuestContactChallenge {
        val result: GuestContactChallenge = when (channel) {
            "sms" -> api.post("/api/package/booking-contact/sms/start", GuestPhoneStart(contact))
            "email" -> api.post("/api/package/booking-contact/email/start", GuestEmailStart(contact, locale))
            else -> error("Unsupported contact channel")
        }
        check(result.ok && result.challengeID.isNotBlank()) { "Verification could not start" }
        return result
    }

    suspend fun confirmGuestContact(channel: String, challengeID: String, code: String): GuestContactProof {
        require(channel == "email" || channel == "sms")
        val result: GuestContactProof = api.post("/api/package/booking-contact/$channel/confirm", GuestContactConfirm(challengeID, code))
        check(result.ok && result.verificationToken.isNotBlank()) { "Verification rejected" }
        return result
    }

    suspend fun attachGuestContact(id: String, bookingToken: String, channel: String, proof: String, locale: String) {
        val suffix = when (channel) { "sms" -> "phone"; "email" -> "email"; else -> error("Unsupported contact channel") }
        val result: com.iumrah.beta.models.account.IumrahSimpleResponse = api.post(
            "/api/package/booking/$id/verified-$suffix", GuestContactAttach(proof, locale), mapOf("x-booking-token" to bookingToken),
        )
        check(result.ok) { "Contact attachment rejected" }
    }

    suspend fun fetchBooking(id: String, accessToken: String): RemoteBooking =
        api.get<BookingCreateResponse>("/api/bookings/$id", headers = mapOf("x-booking-token" to accessToken)).booking

    suspend fun syncBookingProfile(
        id: String,
        accessToken: String,
        profile: BookingPilgrimProfile,
        generatorTrace: BookingGeneratorTrace? = null,
    ): ClientTripResponse = api.post(
        "/api/catalog/hotels/client/trips/$id/sync",
        BookingProfileSyncRequest(profile.firstName, profile.lastName, profile.displayName, profile.telegram, profile.whatsapp, generatorTrace),
        headers = mapOf("x-booking-token" to accessToken),
    )

    suspend fun syncGeneratorReport(
        id: String,
        accessToken: String,
        generatorTrace: BookingGeneratorTrace?,
    ): ClientTripResponse = api.post(
        "/api/catalog/hotels/client/trips/$id/sync",
        BookingGeneratorReportSyncRequest(generatorTrace),
        headers = mapOf("x-booking-token" to accessToken),
    )

    suspend fun fetchOperationalTrip(id: String, headers: Map<String, String>): ClientTripResponse =
        api.get("/api/catalog/hotels/client/trips/$id", headers = headers)

    suspend fun fetchItineraryResponse(id: String, headers: Map<String, String>, language: String): BookingItineraryResponse {
        require(headers.isNotEmpty()) { "Booking access required" }
        return ItineraryResolution.resolve(id,
            operational = { localized -> api.get("/api/catalog/hotels/client/trips/$id/itinerary", query = if (localized) mapOf("lang" to language) else emptyMap(), headers = headers) },
            packageSchedule = { api.get("/api/package/booking/$id/itinerary", query = mapOf("lang" to language), headers = headers) },
        )
    }
    suspend fun fetchItinerary(id: String, headers: Map<String, String>, language: String): List<BookingItineraryItem> = fetchItineraryResponse(id, headers, language).items

    suspend fun updateHotelSelection(
        id: String,
        headers: Map<String, String>,
        role: String,
        hotel: HotelSummary,
        room: HotelRoom?,
        roomCategory: IumrahRoomCategoryOption?,
    ): BookingMutationResponse = api.patch(
        "/api/package/booking/$id",
        BookingHotelUpdateRequest.from(role, hotel, room, roomCategory),
        headers = headers,
    )

    suspend fun updateContacts(id: String, headers: Map<String, String>, telegram: String, whatsapp: String): BookingMutationResponse =
        api.patch("/api/package/booking/$id/contact", BookingContactUpdateRequest(telegram, whatsapp), headers = headers)

    suspend fun updateZiyarat(id: String, headers: Map<String, String>, makkah: Boolean, madinah: Boolean): BookingMutationResponse =
        api.patch("/api/package/booking/$id/customization", BookingCustomizationUpdateRequest(ziyaratMakkah = makkah, ziyaratMadinah = madinah), headers = headers)

    suspend fun updateESIM(id: String, headers: Map<String, String>, enabled: Boolean): BookingMutationResponse =
        api.patch("/api/package/booking/$id/customization", BookingCustomizationUpdateRequest(esim = enabled), headers = headers)

    suspend fun securityConfirmation(id: String, accessToken: String): IumrahSecurityConfirmationResponse =
        api.get("/api/catalog/hotels/client/trips/$id/security", headers = mapOf("x-booking-token" to accessToken))

    suspend fun uploadSecurityPassport(id: String, accessToken: String, data: ByteArray, contentType: String): IumrahSecurityConfirmationResponse =
        api.upload("/api/catalog/hotels/client/trips/$id/security/passport", data, contentType, mapOf("x-booking-token" to accessToken), 60)

    suspend fun submitSecurityConfirmation(
        id: String,
        accessToken: String,
        firstName: String,
        lastName: String,
        passportNumber: String,
    ): IumrahSecurityConfirmationResponse = api.put(
        "/api/catalog/hotels/client/trips/$id/security",
        IumrahSecurityConfirmationRequest(firstName, lastName, passportNumber, true),
        headers = mapOf("x-booking-token" to accessToken),
    )

    suspend fun friendsSummary(id: String, headers: Map<String, String>): IumrahFriendsBookingSummary =
        api.get("/api/package/booking/$id/friends", headers = headers)

    suspend fun redeemFriendGift(id: String, headers: Map<String, String>, code: String): IumrahFriendsBookingSummary =
        api.post("/api/package/booking/$id/friends/redeem", IumrahFriendGiftRedeemRequest(code), headers)

    suspend fun applyFriendCredit(id: String, headers: Map<String, String>, amountUsd: Int): IumrahFriendsBookingSummary =
        api.post("/api/package/booking/$id/friends/credit", IumrahFriendCreditApplyRequest(amountUsd), headers)

    suspend fun registerPushDevice(
        id: String,
        headers: Map<String, String>,
        deviceToken: String,
        locale: String,
    ): ClientPushRegistrationResponse = api.post(
        "/api/catalog/hotels/client/push/devices",
        ClientPushRegistrationRequest(
            deviceToken = deviceToken,
            bookingID = id,
            environment = "production",
            appBundleID = com.iumrah.beta.BuildConfig.APPLICATION_ID,
            locale = locale,
        ),
        headers,
    )

    suspend fun refundPreview(id: String, headers: Map<String, String>): BookingRefundPreview =
        api.get("/api/catalog/hotels/client/bookings/$id/refund-preview", headers = headers)

    suspend fun requestCancellation(id: String, headers: Map<String, String>): BookingMutationResponse =
        api.post("/api/catalog/hotels/client/bookings/$id/cancel-request", emptyMap<String,String>(), headers)

    suspend fun submitTravelFeedback(id: String, rating: Int, comment: String, headers: Map<String, String>): com.iumrah.beta.models.account.IumrahTravelFeedbackResponse {
        require(rating in 1..5)
        return api.post("/api/catalog/hotels/client/trips/$id/feedback", com.iumrah.beta.models.account.IumrahTravelFeedbackRequest(rating,comment),headers)
    }

    suspend fun deleteBooking(id: String, headers: Map<String, String>): BookingMutationResponse =
        api.delete("/api/catalog/hotels/client/bookings/$id", headers)
}

@Serializable
private data class BookingProfileSyncRequest(
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val telegram: String,
    val whatsapp: String,
    val generatorTrace: BookingGeneratorTrace? = null,
)

@Serializable
private data class BookingGeneratorReportSyncRequest(
    val generatorTrace: BookingGeneratorTrace? = null,
)

@Serializable
data class ClientPushRegistrationRequest(
    val deviceToken: String,
    val bookingID: String,
    val environment: String,
    val appBundleID: String,
    val locale: String,
)

@Serializable
data class ClientPushRegistrationResponse(
    val ok: Boolean,
    val ready: Boolean? = null,
    val bookingID: String? = null,
)

@Serializable private data class GuestPhoneStart(val phone: String)
@Serializable private data class GuestEmailStart(val email: String, val locale: String)
@Serializable private data class GuestContactConfirm(val challengeID: String, val code: String)
@Serializable private data class GuestContactAttach(val verificationToken: String, val locale: String)
@Serializable data class GuestContactChallenge(val ok: Boolean = true, val challengeID: String, val expiresAt: String)
@Serializable data class GuestContactProof(val ok: Boolean = true, val verificationToken: String, val email: String? = null, val phone: String? = null)
