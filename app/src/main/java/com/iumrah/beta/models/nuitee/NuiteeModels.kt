package com.iumrah.beta.models.nuitee

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Mirrors the live /api/package/nuitee contract; prices refer to ROOM STAYS, not pilgrimage packages. */
@Serializable
data class NuiteeHotel(
    val id: String,
    val name: String,
    val city: String = "",
    val address: String? = null,
    val stars: Double? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val coverImageURL: String? = null,
    val previewImages: List<String>? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val minimumRate: NuiteeMinimumRate? = null,
    val rateStatus: String? = null,
) {
    val roundedStars: Int get() = (stars ?: 0.0).roundToInt().coerceIn(0, 5)
}

@Serializable
data class NuiteeMinimumRate(val hotelId: String = "", val price: Double, val currency: String = "USD", val offerId: String? = null)

@Serializable
data class NuiteeHotelListEnvelope(
    val ok: Boolean = false, val sandbox: Boolean = false,
    val city: String = "", val checkin: String = "", val checkout: String = "", val adults: Int = 2,
    val hotelCatalogStale: Boolean = false, val pricesStale: Boolean = false,
    val pricesUpdatedAt: Double? = null, val rateError: String? = null,
    val hotels: List<NuiteeHotel> = emptyList(),
    val total: Int? = null, val nextOffset: Int? = null,
)

@Serializable data class NuiteePolicy(val title: String = "", val description: String = "")
@Serializable data class NuiteeRoomDescription(
    val id: String, val name: String = "", val description: String = "", val maxOccupancy: Int? = null,
    val images: List<String> = emptyList(), val amenities: List<String> = emptyList(),
)
@Serializable data class NuiteeHotelDetail(
    val id: String, val name: String, val city: String = "", val address: String? = null,
    val stars: Double? = null, val rating: Double? = null, val reviewCount: Int? = null,
    val coverImageURL: String? = null, val latitude: Double? = null, val longitude: Double? = null,
    val description: String = "", val importantInformation: String = "",
    val images: List<String> = emptyList(), val rooms: List<NuiteeRoomDescription> = emptyList(),
    val amenities: List<String> = emptyList(), val policies: List<NuiteePolicy> = emptyList(),
    val checkin: String? = null, val checkout: String? = null,
)
@Serializable data class NuiteeHotelDetailEnvelope(val ok: Boolean = false, val sandbox: Boolean = false, val hotel: NuiteeHotelDetail)
@Serializable data class NuiteeCancellationDeadline(val until: String = "", val amount: Double? = null)
@Serializable data class NuiteeArrivalCharge(val amount: Double? = null, val currency: String = "USD")
@Serializable data class NuiteeRoomRate(
    val offerId: String, val roomId: String, val roomName: String = "", val mealPlan: String = "",
    val price: Double, val currency: String = "USD", val refundable: Boolean = false,
    val cancellationTag: String = "", val cancellationDescription: String = "",
    val cancellationDeadlines: List<NuiteeCancellationDeadline> = emptyList(),
    val chargeOnArrival: List<NuiteeArrivalCharge> = emptyList(),
    val includedMeals: NuiteeMealChoices? = null,
) {
    val id: String get() = "$offerId|$roomId|$roomName"
}
@Serializable data class NuiteeRatesEnvelope(
    val ok: Boolean = false, val sandbox: Boolean = false,
    val checkin: String = "", val checkout: String = "", val adults: Int = 2,
    val rates: List<NuiteeRoomRate> = emptyList(), val cached: Boolean = false,
    /** Only explicit confirmation permits a sold-out state. Null is unknown. */
    val availabilityConfirmed: Boolean? = null,
)

@Serializable data class NuiteeMealChoices(val breakfast: Boolean = false, val lunch: Boolean = false, val dinner: Boolean = false)
@Serializable data class BookingNuiteeRoomSelection(
    val id: String, val city: String, val hotelId: String, val offerId: String, val roomId: String,
    val checkin: String, val checkout: String, val adults: Int, val children: List<Int>,
    val meals: NuiteeMealChoices, val mealBundleRequested: Boolean? = null,
)
@Serializable data class NuiteeGuestReview(
    val id: String, val name: String = "", val country: String = "", val date: String = "",
    val rating: Double? = null, val headline: String = "", val pros: String = "", val cons: String = "",
    val source: String = "", val language: String = "", val originalHeadline: String? = null,
    val originalPros: String? = null, val originalCons: String? = null,
    val isTranslated: Boolean? = null, val translationUnavailable: Boolean? = null,
)
@Serializable data class NuiteeReviewsEnvelope(val ok: Boolean = false, val reviews: List<NuiteeGuestReview> = emptyList(),
    val total: Int? = null, val nextOffset: Int? = null, val stale: Boolean = false)

/** /api/package/estimate: partial manual assemblies, never a booking proof. */
@Serializable data class NuiteeManualEstimateRequest(
    val adults: Int, val includeMadinah: Boolean, val start: String, val end: String,
    val transferVehicle: String? = null, val haramain: Train,
    val rooms: List<BookingNuiteeRoomSelection>, val flights: List<Flight>,
) {
    @Serializable data class Train(val enabled: Boolean, val fareClass: String, val ticketCount: Int)
    @Serializable data class Flight(val identity: String, val origin: String, val destination: String, val departureDate: String, val cabin: String)
}
@Serializable data class NuiteeManualEstimate(
    val totalPackagePrice: Double, val pricePerPerson: Double, val currency: String,
    val bookingAllowed: Boolean, val pairedFareIncluded: Boolean, val provisionalTier: Boolean,
)
@Serializable data class NuiteeManualEstimateEnvelope(val ok: Boolean, val estimate: NuiteeManualEstimate)
