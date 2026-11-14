package com.iumrah.beta.data.booking

import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.network.APIClient
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
    suspend fun createBooking(request: BookingCreateEnvelope): BookingCreateResponse =
        api.post(AppConfig.PACKAGE_BOOKING_PATH, request)

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

    suspend fun fetchItinerary(id: String, headers: Map<String, String>): List<BookingItineraryItem> =
        api.get<BookingItineraryResponse>("/api/catalog/hotels/client/trips/$id/itinerary", headers = headers)
            .items.sortedWith(compareBy<BookingItineraryItem> { it.dateLocal }.thenBy { it.sortOrder })

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
            appBundleID = "com.iumrah.beta",
            locale = locale,
        ),
        headers,
    )

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
