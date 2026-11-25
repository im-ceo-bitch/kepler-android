package com.iumrah.beta.models.hotel

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StorefrontFlightLeg(
    val airline: String,
    @SerialName("flight_number") @JsonNames("flightNumber") val flightNumber: String,
    @SerialName("airline_code") @JsonNames("airlineCode") val airlineCode: String,
    val origin: String,
    val destination: String,
    @SerialName("departure_at") @JsonNames("departureAt") val departureAt: String,
    @SerialName("arrival_at") @JsonNames("arrivalAt") val arrivalAt: String,
    @SerialName("duration_minutes") @JsonNames("durationMinutes") val durationMinutes: Int,
    val stops: Int,
    @SerialName("cabin_class") @JsonNames("cabinClass") val cabinClass: String,
)

@Serializable
data class StorefrontFlightBaggage(
    val carryOn: Int? = null,
    val checked: Int? = null,
)

@Serializable
data class StorefrontFlightOption(
    val id: String,
    val kind: String,
    val priority: Int = 0,
    val currency: String,
    val travelerCount: Int,
    val totalFare: Double,
    val perTravelerFare: Double,
    val observedAt: String,
    val outbound: StorefrontFlightLeg,
    val inbound: StorefrontFlightLeg? = null,
    val baggage: StorefrontFlightBaggage? = null,
)

@Serializable
data class StorefrontFlightBoardResponse(
    val ok: Boolean,
    val origin: String,
    val generatedAt: String,
    val options: List<StorefrontFlightOption> = emptyList(),
)

@Serializable
data class StorefrontPackageConfiguration(
    val adults: Int = 2,
    val children: Int = 0,
    val infants: Int = 0,
    val rooms: Int = 1,
    val makkahLunch: Boolean = false,
    val makkahDinner: Boolean = false,
    val madinahDinner: Boolean = false,
    val transferVehicle: String? = null,
    val haramainEnabled: Boolean = false,
    val haramainFareClass: String = "economy",
    val haramainTicketCount: Int = 0,
    val makkahRoomId: String? = null,
    val madinahRoomId: String? = null,
)

@Serializable
data class StorefrontPackageSnapshot(
    val id: String,
    val entryMode: String,
    val status: String,
    val originCode: String,
    val originCity: String? = null,
    val destinationCode: String? = null,
    val tier: String? = null,
    val kind: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val totalDays: Int? = null,
    val totalNights: Int? = null,
    val makkahNights: Int? = null,
    val madinahNights: Int? = null,
    val hotelFirstVariant: String? = null,
    val hotelFirstVariantIndex: Int? = null,
    val hotelFirstVariantMinDays: Int? = null,
    val hotelFirstVariantMaxDays: Int? = null,
    val hotelFirstAnchorCity: String? = null,
    val hotelFirstAnchorHotelId: String? = null,
    val hotelFirstEngineVersion: Int? = null,
    val outbound: StorefrontFlightLeg? = null,
    val inbound: StorefrontFlightLeg? = null,
    val providerItineraryId: String? = null,
    val outboundOfferId: String? = null,
    val inboundOfferId: String? = null,
    val imageUrl: String? = null,
    val hotelImages: List<String> = emptyList(),
    val hotelName: String? = null,
    val hotelSecondaryName: String? = null,
    val hotelCity: String? = null,
    val hotelStars: Int? = null,
    val hotelRating: Double? = null,
    val hotelReviewCount: Int? = null,
    val makkahHotelId: String? = null,
    val madinahHotelId: String? = null,
    val routeSummary: String? = null,
    val pricePerPerson: Double? = null,
    val totalPackagePrice: Double? = null,
    val currency: String = "USD",
    val isEstimated: Boolean = false,
    val configuration: StorefrontPackageConfiguration? = null,
)

@Serializable
data class StorefrontPackageEnvelope(
    val ok: Boolean,
    val `package`: StorefrontPackageSnapshot,
    val generatedAt: String? = null,
    val expiresAt: String? = null,
    val expired: Boolean? = null,
)

@Serializable
data class StorefrontPackagesEnvelope(
    val ok: Boolean,
    val cacheState: String? = null,
    val refreshRecommended: Boolean? = null,
    val generatedAt: String? = null,
    val expiresAt: String? = null,
    val itemCount: Int? = null,
    val totalItemCount: Int? = null,
    val expectedItemCount: Int? = null,
    val complete: Boolean? = null,
    val failedHotelCount: Int? = null,
    val nextRefreshCursor: Int? = null,
    val items: List<StorefrontPackageSnapshot> = emptyList(),
)

@Serializable
data class StorefrontPackageRefreshEnvelope(
    val ok: Boolean,
    val itemCount: Int? = null,
    val expectedItemCount: Int? = null,
    val complete: Boolean? = null,
    val failedHotelCount: Int? = null,
    val nextRefreshCursor: Int? = null,
)
