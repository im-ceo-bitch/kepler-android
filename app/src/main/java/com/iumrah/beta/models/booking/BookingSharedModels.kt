package com.iumrah.beta.models.booking

import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.domain.pricing.GeneratorPricingSnapshot
import com.iumrah.beta.models.hotel.HotelRoom
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class IumrahRoomCategory {
    @SerialName("DOUBLE") DOUBLE,
    @SerialName("TRIPLE") TRIPLE,
    @SerialName("QUADRUPLE") QUADRUPLE,
}

@Serializable
data class BookingCreateEnvelope(val lang: String, val booking: BookingDraftRequest)

@Serializable
data class BookingDraftRequest(
    val planId: String,
    val totalUsd: Double,
    val perPilgrimUsd: Double,
    val input: BookingInput,
    val route: BookingRoute,
    val stay: BookingStay,
    val selection: BookingSelection,
    val customization: BookingCustomization,
    val includedServices: List<String>,
    val hotelNames: BookingHotelNames,
    val flight: String,
    val pilgrimProfile: BookingPilgrimProfile? = null,
    val generatorTrace: BookingGeneratorTrace? = null,
    val pricingSnapshot: GeneratorPricingSnapshot? = null,
)

@Serializable
data class BookingInput(
    val from: String,
    val originCode: String,
    val arrivalAirportCode: String,
    val cabinClass: String,
    val preferredPlan: String,
    val startDate: String,
    val endDate: String,
    val flexibleDays: Int,
    val hotelPreference: String,
    val includeMadinah: Boolean,
    val flightTripType: String,
    val travelers: BookingTravelers,
)

@Serializable
data class BookingSelection(
    val flightId: String,
    val makkahHotelId: String,
    val madinahHotelId: String? = null,
    val makkahRoomId: String? = null,
    val makkahRoomCategory: IumrahRoomCategory? = null,
    val madinahRoomId: String? = null,
    val madinahRoomCategory: IumrahRoomCategory? = null,
)

@Serializable
data class BookingGeneratorTrace(
    val quoteId: String? = null,
    val outbound: BookingGeneratorFlightSnapshot,
    val inbound: BookingGeneratorFlightSnapshot? = null,
    val makkahHotel: BookingGeneratorHotelSnapshot,
    val madinahHotel: BookingGeneratorHotelSnapshot? = null,
)

@Serializable
data class BookingGeneratorFlightSnapshot(
    val candidateId: String? = null,
    val airline: String,
    val flightNumbers: String,
    val origin: String,
    val destination: String,
    val departureAt: String,
    val arrivalAt: String,
    val source: String,
    val stops: Int? = null,
    val durationMinutes: Int? = null,
    val segments: List<BookingGeneratorFlightSegmentSnapshot>? = null,
    val connectionAirports: List<String>? = null,
)

@Serializable
data class BookingGeneratorFlightSegmentSnapshot(
    val airline: String,
    val airlineCode: String? = null,
    val flightNumber: String,
    val origin: String,
    val destination: String,
    val departureAt: String,
    val arrivalAt: String,
    val originTerminal: String? = null,
    val destinationTerminal: String? = null,
    val aircraft: String? = null,
    val operatingCarrier: String? = null,
    val cabin: String? = null,
)

@Serializable
data class BookingGeneratorHotelSnapshot(
    val hotelId: String,
    val hotelName: String,
    val city: String,
    val roomId: String? = null,
    val roomName: String? = null,
    val roomCategory: String? = null,
)

@Serializable
data class BookingPilgrimProfile(
    val firstName: String,
    val lastName: String,
    val telegram: String,
    val whatsapp: String,
) {
    val displayName: String get() = listOf(firstName, lastName).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}

@Serializable
data class BookingTravelers(val adults: Int, val children: Int, val infants: Int, val rooms: Int) {
    val totalPeople: Int get() = adults + children + infants
}

@Serializable data class BookingRoute(val originCode: String, val outboundDestination: String, val returnOrigin: String)

@Serializable
data class BookingStay(
    val totalDays: Int,
    val totalNights: Int,
    val makkahCheckIn: String,
    val makkahCheckOut: String,
    val makkahNights: Int,
    val madinahCheckIn: String? = null,
    val madinahCheckOut: String? = null,
    val madinahNights: Int? = null,
)

@Serializable
data class BookingCustomization(
    val accompaniment: Boolean,
    val guideMeetingPoint: String,
    val ziyaratMakkah: Boolean,
    val ziyaratMadinah: Boolean,
    val meals: Boolean,
    val esim: Boolean,
)

@Serializable data class BookingHotelNames(val makkah: String, val madinah: String)

@Serializable data class BookingCreateResponse(val booking: RemoteBooking, val accessToken: String? = null)

@Serializable
data class BookingInputRecord(
    val startDate: String,
    val endDate: String,
    val from: String,
    val originCode: String,
    val arrivalAirportCode: String,
    val includeMadinah: Boolean,
    val travelers: BookingTravelers,
)

@Serializable
data class BookingHotelSelectionSnapshot(
    val hotelId: String,
    val hotelName: String,
    val city: String,
    val coverImageURL: String? = null,
    val roomId: String? = null,
    val roomName: String? = null,
    val roomBeds: String? = null,
    val roomSizeM2: Double? = null,
    val roomMaxGuests: Int? = null,
    val roomCategory: IumrahRoomCategory? = null,
    val roomSource: String? = null,
) {
    companion object {
        fun from(hotel: HotelSummary, room: HotelRoom?, category: IumrahRoomCategoryOption?, authoritativeRoomId: String? = null) =
            BookingHotelSelectionSnapshot(
                hotelId = hotel.id,
                hotelName = hotel.name,
                city = hotel.city,
                coverImageURL = hotel.coverImageURL,
                roomId = room?.id ?: authoritativeRoomId,
                roomName = room?.name ?: category?.displayName,
                roomBeds = room?.beds ?: category?.bedConfiguration,
                roomSizeM2 = room?.sizeM2,
                roomMaxGuests = room?.maxGuests ?: category?.maxGuests,
                roomCategory = category?.category?.name?.let(IumrahRoomCategory::valueOf),
                roomSource = when {
                    category != null || authoritativeRoomId != null -> "iumrahPrimary"
                    room != null -> "hotelInventory"
                    else -> null
                },
            )
    }
}

@Serializable
data class RemoteBooking(
    val id: String,
    val status: String,
    val planId: String,
    val totalUsd: Double,
    val perPilgrimUsd: Double,
    val input: BookingInputRecord,
    val route: BookingRoute,
    val stay: BookingStay,
    val hotelNames: BookingHotelNames,
    val flight: String,
    val createdAt: String,
    val updatedAt: String,
    val pilgrimProfile: BookingPilgrimProfile? = null,
    val hotelSelection: BookingHotelSelectionSnapshot? = null,
    val madinahHotelSelection: BookingHotelSelectionSnapshot? = null,
    val customization: BookingCustomization? = null,
    val includedServices: List<String>? = null,
    val generatorTrace: BookingGeneratorTrace? = null,
)

@Serializable
data class ClientESIMProfile(
    val id: String,
    val bookingID: String,
    val travelerPosition: Int? = null,
    val label: String,
    val provider: String,
    val providerEsimID: String? = null,
    val iccid: String,
    val planName: String,
    val countryCode: String,
    val totalMB: Double,
    val usedMB: Double,
    val remainingMB: Double,
    val validityDays: Int? = null,
    val status: String,
    val providerStatus: String? = null,
    val providerSmdpStatus: String? = null,
    val smdpAddress: String,
    val activationCode: String,
    val lpaString: String,
    val qrCodeURL: String? = null,
    val activatedAt: String? = null,
    val expiresAt: String? = null,
    val lastUsageSyncAt: String? = null,
    val usageSource: String,
    val createdAt: String,
    val updatedAt: String,
) {
    val totalGB: Double get() = totalMB / 1024.0
    val usedGB: Double get() = usedMB / 1024.0
    val remainingGB: Double get() = remainingMB / 1024.0
    val usageAvailable: Boolean get() = usageSource == "provider" || lastUsageSyncAt != null
    val remainingFraction: Double get() = if (usageAvailable && totalMB > 0) (remainingMB / totalMB).coerceIn(0.0, 1.0) else 0.0
    val hasActivationData: Boolean get() = lpaString.trim().isNotEmpty() || (smdpAddress.isNotEmpty() && activationCode.isNotEmpty())
}

@Serializable data class ClientESIMListResponse(val ok: Boolean, val bookingID: String, val esims: List<ClientESIMProfile>)
@Serializable data class ClientBookingAssignment(val guide: BookingGuideSnapshot? = null)

@Serializable
data class BookingGuideSnapshot(
    val id: String,
    val displayName: String,
    val roleTitle: String,
    val phoneUZ: String,
    val phoneSA: String,
    val telegram: String,
    val whatsapp: String,
    val bio: String,
)

@Serializable
data class ClientTripSnapshot(
    val tripID: String,
    val bookingID: String,
    val bookingNumber: Int? = null,
    val bookingDisplayNumber: String? = null,
    val pilgrimID: String? = null,
    val status: String,
    val paymentStatus: String? = null,
    val confirmationNumber: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val completedAt: String? = null,
    val availabilityStartedAt: String? = null,
    val availabilityDeadlineAt: String? = null,
    val priceLockStartedAt: String? = null,
    val priceLockExpiresAt: String? = null,
    val paymentReceivedAt: String? = null,
    val paymentConfirmationDeadlineAt: String? = null,
    val documentsStartedAt: String? = null,
    val documentsDeadlineAt: String? = null,
)

@Serializable
data class BookingStatusHistoryEntry(
    val oldStatus: String? = null,
    val newStatus: String,
    val createdAt: String,
)

@Serializable
data class ClientTripResponse(
    val ok: Boolean? = null,
    val trip: ClientTripSnapshot,
    val assignment: ClientBookingAssignment? = null,
    val esims: List<ClientESIMProfile>? = null,
    val statusHistory: List<BookingStatusHistoryEntry>? = null,
)

@Serializable
data class StoredBookingSession(
    val id: String,
    val accessToken: String,
    var booking: RemoteBooking,
    var travelerName: String? = null,
    var telegram: String? = null,
    var whatsapp: String? = null,
    var hotelSelection: BookingHotelSelectionSnapshot? = null,
    var madinahHotelSelection: BookingHotelSelectionSnapshot? = null,
    var guide: BookingGuideSnapshot? = null,
    var transferVehicle: TransferVehicleKind? = null,
    var ziyaratMakkahOverride: Boolean? = null,
    var ziyaratMadinahOverride: Boolean? = null,
    var esimOverride: Boolean? = null,
    var pendingChangeConfirmation: Boolean? = null,
    var operationStatus: String? = null,
    var pilgrimID: String? = null,
    var bookingNumber: Int? = null,
    var bookingDisplayNumber: String? = null,
    var availabilityStartedAt: String? = null,
    var availabilityDeadlineAt: String? = null,
    var priceLockStartedAt: String? = null,
    var priceLockExpiresAt: String? = null,
    var paymentReceivedAt: String? = null,
    var paymentConfirmationDeadlineAt: String? = null,
    var documentsStartedAt: String? = null,
    var documentsDeadlineAt: String? = null,
    var statusHistory: List<BookingStatusHistoryEntry>? = null,
) {
    /** UI-only iOS parity: never alter the raw server pilgrimID. */
    val displayPilgrimID: String? get() {
        val raw = pilgrimID?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 8) return null
        return digits.padStart(8, '0')
    }

    val displayBookingNumber: String get() = bookingDisplayNumber?.takeIf { it.isNotBlank() }
        ?: bookingNumber?.takeIf { it > 0 }?.let { "#%04d".format(it) } ?: "#----"

    val orderedStatusHistory: List<BookingStatusHistoryEntry>
        get() = statusHistory.orEmpty().sortedBy { it.createdAt }

    fun latestStatusTimestamp(statuses: Set<String>): String? {
        val normalized = statuses.map { it.trim().lowercase() }.toSet()
        return orderedStatusHistory
            .lastOrNull { it.newStatus.trim().lowercase() in normalized }
            ?.createdAt
    }

    fun mergeOperationalTrip(
        trip: ClientTripSnapshot,
        history: List<BookingStatusHistoryEntry>? = null,
        assignment: ClientBookingAssignment? = null,
    ): StoredBookingSession = copy(
        operationStatus = trip.status,
        pilgrimID = trip.pilgrimID ?: pilgrimID,
        bookingNumber = trip.bookingNumber ?: bookingNumber,
        bookingDisplayNumber = trip.bookingDisplayNumber ?: bookingDisplayNumber,
        guide = assignment?.guide ?: guide,
        availabilityStartedAt = trip.availabilityStartedAt,
        availabilityDeadlineAt = trip.availabilityDeadlineAt,
        priceLockStartedAt = trip.priceLockStartedAt,
        priceLockExpiresAt = trip.priceLockExpiresAt,
        paymentReceivedAt = trip.paymentReceivedAt,
        paymentConfirmationDeadlineAt = trip.paymentConfirmationDeadlineAt,
        documentsStartedAt = trip.documentsStartedAt,
        documentsDeadlineAt = trip.documentsDeadlineAt,
        statusHistory = history ?: statusHistory,
    )

    val effectiveStatus: String get() = when ((operationStatus ?: booking.status).lowercase()) {
        "new", "availability_check" -> "AVAILABILITY_CHECK"
        "payment_pending" -> "PAYMENT_PENDING"
        "paid", "booking_confirmed" -> "BOOKING_CONFIRMED"
        "documents_ready", "ready_to_travel" -> "READY_TO_TRAVEL"
        "in_trip" -> "IN_TRIP"
        "completed" -> "COMPLETED"
        "cancelled" -> "CANCELLED"
        else -> booking.status
    }
}

@Serializable data class BookingMutationResponse(val ok: Boolean? = null, val deleted: Boolean? = null, val updatedAt: String? = null)

@Serializable
data class BookingHotelUpdateRequest(
    val role: String,
    val hotelId: String,
    val coverImageURL: String? = null,
    val roomId: String? = null,
    val roomName: String? = null,
    val roomBeds: String? = null,
    val roomSizeM2: Double? = null,
    val roomMaxGuests: Int? = null,
    val roomCategory: String? = null,
    val roomSource: String? = null,
) {
    companion object {
        fun from(role: String, hotel: HotelSummary, room: HotelRoom?, category: IumrahRoomCategoryOption?) = BookingHotelUpdateRequest(
            role = role,
            hotelId = hotel.id,
            coverImageURL = hotel.coverImageURL,
            roomId = room?.id,
            roomName = room?.name ?: category?.displayName,
            roomBeds = room?.beds ?: category?.bedConfiguration,
            roomSizeM2 = room?.sizeM2,
            roomMaxGuests = room?.maxGuests ?: category?.maxGuests,
            roomCategory = category?.category?.name,
            roomSource = if (category != null) "iumrahPrimary" else if (room != null) "hotelInventory" else null,
        )
    }
}

@Serializable data class BookingContactUpdateRequest(val telegram: String, val whatsapp: String)
@Serializable data class BookingCustomizationUpdateRequest(val ziyaratMakkah: Boolean? = null, val ziyaratMadinah: Boolean? = null, val esim: Boolean? = null)

@Serializable data class ChatListResponse(val ok: Boolean? = null, val bookingID: String? = null, val messages: List<ChatMessage>)
@Serializable data class ChatMessagePostResponse(val ok: Boolean? = null, val message: ChatMessage)
@Serializable
data class ChatMessage(
    val id: String,
    val bookingID: String,
    val senderType: String,
    val senderName: String? = null,
    val body: String,
    val messageType: String? = null,
    val attachmentID: String? = null,
    val attachmentURL: String? = null,
    val createdAt: String,
    val readByStaff: Boolean? = null,
)

@Serializable
data class BookingItineraryItem(
    val id: String,
    val bookingID: String,
    val dateLocal: String,
    val sortOrder: Int,
    val title: String,
    val subtitle: String,
    val icon: String,
    val location: String,
    val notes: String,
    val createdAt: String,
    val updatedAt: String,
    val timeLocal: String? = null,
    val endTimeLocal: String? = null,
    val kind: String? = null,
)
@Serializable data class BookingItineraryResponse(val ok: Boolean, val bookingID: String, val items: List<BookingItineraryItem>)
