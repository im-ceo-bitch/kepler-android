package com.iumrah.beta.data.packageflow

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.security.SecureJsonStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.booking.BookingDraftBuilder
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.nuitee.NuiteeJourneyDraft
import com.iumrah.beta.domain.pricing.TripSummaryPublicPrice
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.models.flight.LiveFlightJourneyCandidate
import com.iumrah.beta.models.hotel.*
import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.models.account.IumrahSimpleResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.net.URI

@Serializable data class SavedPackageSnapshot(val totalUsd: Double, val perPilgrimUsd: Double, val input: BookingInputRecord,
    val hotelNames: BookingHotelNames, val flight: String, val includedServices: List<String>, val tripSummary: TripSummaryPublicPrice? = null)
@Serializable data class SavedPackageReceipt(val packageID: String, val revision: Int, val status: String,
    val expiresAt: String, val shareURL: String? = null, @kotlinx.serialization.SerialName("package") val snapshot: SavedPackageSnapshot? = null)
@Serializable data class PackageThread(val packageID: String, val revision: Int, val messages: List<Message>, val roomRequest: RoomRequest? = null) {
    @Serializable data class Message(val id: Int, val revision: Int, val sender: String, val body: String, val createdAt: String)
    @Serializable data class RoomRequest(val revision: Int, val adults: Int, val rooms: Int, val state: String, val note: String)
}
@Serializable private data class ThreadMessage(val revision: Int, val body: String, val requestID: String, val requestRooms: Int? = null)
@Serializable private data class BookingLink(val revision: Int, val bookingID: String)

/** Selection restoration only. No quote, quoteProof, booking access token, or supplier authorization. */
@Serializable data class SavedJourneyDraft(
    val trip: TripDraft, val makkahHotel: HotelSummary?, val makkahRoom: HotelRoom?, val makkahCategory: IumrahRoomCategoryOption?,
    val madinahHotel: HotelSummary?, val madinahRoom: HotelRoom?, val madinahCategory: IumrahRoomCategoryOption?,
    val flight: LiveFlightJourneyCandidate?, val path: PackageFlightPath, val complete: String?, val outbound: String?, val inbound: String?,
    val stagedRound: FlightDiscoveryOffer?, val stagedOut: FlightDiscoveryOffer?, val stagedIn: FlightDiscoveryOffer?,
    val vehicle: TransferVehicleKind?, val train: Boolean, val trainClass: HaramainFareClass, val trainAdults: Int, val trainChildren: Int,
    val transferConfirmed: Boolean, val nuitee: NuiteeJourneyDraft?, val mixedIdentity: String? = null, val childAges: List<Int> = emptyList(),
) {
    fun restore() = JourneyState(trip=trip, makkahHotel=makkahHotel, makkahRoom=makkahRoom, makkahRoomCategory=makkahCategory,
        madinahHotel=madinahHotel, madinahRoom=madinahRoom, madinahRoomCategory=madinahCategory,
        flightResults=listOfNotNull(flight), selectedJourneyId=flight?.id, packageFlightPath=path,
        selectedPublishedCompleteID=complete, selectedPublishedOutboundID=outbound, selectedPublishedReturnID=inbound,
        stagedAviasalesRoundTrip=stagedRound, stagedAviasalesOutbound=stagedOut, stagedAviasalesReturn=stagedIn,
        selectedTransferVehicle=vehicle, haramainTrainSelected=train, haramainFareClass=trainClass, haramainAdultTickets=trainAdults,
        haramainChildTickets=trainChildren, transferSelectionConfirmed=transferConfirmed, nuitee=nuitee,mixedFlightIdentity=mixedIdentity,childAges=childAges)
    companion object {
        fun from(s: JourneyState) = SavedJourneyDraft(s.trip, s.makkahHotel, s.makkahRoom, s.makkahRoomCategory,
            s.madinahHotel,s.madinahRoom,s.madinahRoomCategory,s.selectedJourney,s.packageFlightPath,s.selectedPublishedCompleteID,
            s.selectedPublishedOutboundID,s.selectedPublishedReturnID,s.stagedAviasalesRoundTrip,s.stagedAviasalesOutbound,
            s.stagedAviasalesReturn,s.selectedTransferVehicle,s.haramainTrainSelected,s.haramainFareClass,s.haramainAdultTickets,
            s.haramainChildTickets,s.transferSelectionConfirmed,s.nuitee,s.mixedFlightIdentity,s.childAges)
    }
}
@Serializable data class SavedPackageEntry(val owner: String, val receipt: SavedPackageReceipt? = null,
    val draft: SavedJourneyDraft? = null, val savedQuoteID: String? = null, val pendingBookingID: String? = null)

class SavedPackageStore(private val api: APIClient, private val vault: SecureJsonStore) {
    private val codec = ListSerializer(SavedPackageEntry.serializer())
    private val mutex = Mutex()
    private val initialLoad = runCatching { vault.readStrict(KEY, codec).orEmpty() }
    private val _entries = MutableStateFlow(initialLoad.getOrDefault(emptyList()))
    val entries = _entries.asStateFlow()
    private fun persist(value: List<SavedPackageEntry>) { check(initialLoad.isSuccess) { "Saved package storage unavailable" }; vault.write(KEY,value,codec); _entries.value=value }
    private fun put(entry: SavedPackageEntry) = persist(_entries.value.filterNot { it.owner == entry.owner } + entry)
    fun entry(id: String?) = _entries.value.firstOrNull { id != null && it.receipt?.packageID == id }
    private fun path(id: String): String { require(id.matches(Regex("[A-Za-z0-9_-]{1,128}"))); return "/api/offers/saved/$id" }
    private fun headers(entry: SavedPackageEntry) = mapOf("x-package-owner" to entry.owner)
    private fun newOwner() = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun validated(receipt: SavedPackageReceipt, expected: String? = null): SavedPackageReceipt {
        path(receipt.packageID); require(receipt.revision > 0 && (expected == null || expected == receipt.packageID))
        // Never allow a capability or arbitrary host into share text.
        val url = receipt.shareURL?.takeIf { raw -> runCatching {
            val uri=URI(raw); uri.scheme == "https" && uri.userInfo == null && uri.rawQuery?.contains(Regex("(?i)(owner|token|proof|authorization)=")) != true && uri.rawFragment == null && uri.host?.lowercase() in setOf("iumrah.app","www.iumrah.app","iumrah.uz","www.iumrah.uz","iumrahproject.com","iumrah.us","bookings.iumrah.uz","status.iumrah.uz")
        }.getOrDefault(false) }
        return receipt.copy(shareURL=url)
    }
    suspend fun save(state: JourneyState, language: AppLanguage): SavedPackageEntry = mutex.withLock {
        val quote = requireNotNull(state.quote)
        require(state.hasFinalGeneratorQuote && state.priceChangeAccepted && quote.hasVerifiedBreakdown && quote.bookingAllowed != false && quote.allowsOccupancy(state.trip.adults,state.trip.rooms))
        val price = requireNotNull(quote.tripSummary)
        val draft = SavedJourneyDraft.from(state)
        val existing = entry(state.savedPackageID)
        require(state.savedPackageID == null || existing != null) { "Saved package access unavailable" }
        require(existing?.pendingBookingID == null && existing?.receipt?.status != "converted") { "Package already booked" }
        if (existing != null && existing.savedQuoteID == quote.quoteId && existing.draft == draft && existing.receipt?.status != "expired") return@withLock existing
        // Reuse an unresolved creation capability for the exact same selection, rather than creating another owner.
        val initial = existing ?: _entries.value.firstOrNull { it.receipt == null && it.draft == draft } ?: SavedPackageEntry(newOwner(), draft=draft).also(::put)
        val payload = BookingDraftBuilder.make(state,quote,language,null)
        val body = savedPackageBody(api.json,payload,price,initial.receipt?.revision)
        val receipt = validated(if (initial.receipt == null) api.post<SavedPackageReceipt,JsonObject>("/api/offers/saved",body,headers(initial),180)
            else api.patch<SavedPackageReceipt,JsonObject>(path(initial.receipt.packageID),body,headers(initial),180),initial.receipt?.packageID)
        initial.copy(receipt=receipt.copy(shareURL=receipt.shareURL?.takeUnless { it.contains(initial.owner,ignoreCase=true) }),draft=draft,savedQuoteID=quote.quoteId).also(::put)
    }
    suspend fun read(id: String): SavedPackageEntry = mutex.withLock {
        val e = requireNotNull(entry(id)); val receipt=validated(api.get<SavedPackageReceipt>(path(id),headers=headers(e)),id)
        e.copy(receipt=receipt.copy(shareURL=receipt.shareURL?.takeUnless { it.contains(e.owner,ignoreCase=true) }), savedQuoteID=e.savedQuoteID.takeIf { receipt.revision == e.receipt?.revision }).also(::put)
    }
    suspend fun thread(id: String): PackageThread = mutex.withLock {
        val e=requireNotNull(entry(id)); api.get<PackageThread>(path(id)+"/thread",headers=headers(e)).also { require(it.packageID==id) }
    }
    suspend fun send(id: String, revision: Int, body: String, requestID: String, rooms: Int?): PackageThread = mutex.withLock {
        val e=requireNotNull(entry(id)); require(body.isNotBlank() && requestID.isNotBlank())
        api.post<PackageThread,ThreadMessage>(path(id)+"/thread",ThreadMessage(revision,body,requestID,rooms),headers(e)).also { require(it.packageID==id) }
    }
    suspend fun link(id: String, booking: StoredBookingSession) = mutex.withLock {
        val e=requireNotNull(entry(id)); val receipt=requireNotNull(e.receipt)
        require(booking.accessToken.isNotBlank())
        val pending=e.copy(pendingBookingID=booking.id); put(pending)
        val response=api.post<IumrahSimpleResponse,BookingLink>(path(id)+"/booking",BookingLink(receipt.revision,booking.id),headers(e)+("x-booking-token" to booking.accessToken))
        check(response.ok)
        put(pending.copy(receipt=receipt.copy(status="converted"),pendingBookingID=null))
    }
    fun restore(id: String, journey: JourneyStore) {
        val e=requireNotNull(entry(id)); require(e.receipt?.status != "converted" && e.pendingBookingID == null)
        journey.restoreSavedDraft(requireNotNull(e.draft).restore(),id,e.receipt?.snapshot?.totalUsd?.takeIf { it.isFinite() && it>0 }?.let(java.math.BigDecimal::valueOf))
    }
    companion object { private const val KEY="saved-packages-v1" }
}

/** Exact iOS TripSummarySavedClient JSON contract. Adds public metadata to the booking draft, never creates a booking. */
internal fun savedPackageBody(json: Json, payload: BookingCreateEnvelope, price: TripSummaryPublicPrice, revision: Int?): JsonObject {
    val b=payload.booking; val trace=requireNotNull(b.generatorTrace); val o=trace.outbound; val i=trace.inbound
    fun cost(code:String)=price.components.firstOrNull { it.code==code }?.amountUsd?.toDouble() ?: 0.0
    val flight=buildJsonObject {
        put("id",b.selection.flightId); put("airline",o.airline); put("outboundOrigin",o.origin); put("outboundDestination",o.destination)
        put("outboundDate",b.input.startDate); put("outboundDepartureTime",o.departureAt); put("outboundArrivalTime",o.arrivalAt); put("outboundStops",o.stops ?: 0); put("outboundCurrency","USD"); put("returnCurrency","USD")
        i?.let { put("returnOrigin",it.origin);put("returnDestination",it.destination);put("returnDate",b.input.endDate);put("returnDepartureTime",it.departureAt);put("returnArrivalTime",it.arrivalAt);it.stops?.let { n -> put("returnStops",n) } }
    }
    fun hotel(id:String,name:String,city:String,start:String,end:String,amount:Double)=buildJsonObject {
        put("id",id);put("name",name);put("city",city);put("checkIn",start);put("checkOut",end);put("totalPrice",amount);put("currency","USD")
    }
    val details=buildJsonObject {
        put("pricingMode","live");put("flight",flight)
        put("makkahHotel",hotel(b.selection.makkahHotelId,b.hotelNames.makkah,"makkah",b.stay.makkahCheckIn,b.stay.makkahCheckOut,cost("makkah_hotel")))
        val id=b.selection.madinahHotelId;val start=b.stay.madinahCheckIn;val end=b.stay.madinahCheckOut
        if(id!=null && start!=null && end!=null) put("madinahHotel",hotel(id,b.hotelNames.madinah,"madinah",start,end,cost("madinah_hotel")))
    }
    val booking=JsonObject(json.encodeToJsonElement(BookingDraftRequest.serializer(),b).jsonObject + ("selectionDetails" to details))
    return buildJsonObject {put("lang",payload.lang);put("booking",booking);put("quoteProof",requireNotNull(payload.quoteProof));revision?.let{put("revision",it)}}
}
