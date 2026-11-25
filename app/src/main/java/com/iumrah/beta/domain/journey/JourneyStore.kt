package com.iumrah.beta.domain.journey

import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.data.flight.IgnavFlightInventoryProvider
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.domain.trip.HaramainFareClass
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageFlightPath
import com.iumrah.beta.domain.trip.FlightTripType
import com.iumrah.beta.domain.trip.DateFlexibility
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.domain.trip.FlightFareScope
import com.iumrah.beta.domain.trip.PackageMealSelection
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.FlightJourneyDatePair
import com.iumrah.beta.models.flight.FlightJourneySearchRequest
import com.iumrah.beta.models.flight.CuratedPublishedFlightSelection
import com.iumrah.beta.models.flight.LiveFlightJourneyCandidate
import com.iumrah.beta.models.flight.FlightDirection
import com.iumrah.beta.models.flight.FlightAirportSnapshot
import com.iumrah.beta.models.hotel.HotelRoom
import com.iumrah.beta.domain.nuitee.NuiteeJourneyDraft
import com.iumrah.beta.domain.nuitee.NuiteeFlightSnapshot
import com.iumrah.beta.domain.nuitee.NuiteeRoomSelection
import com.iumrah.beta.domain.nuitee.NuiteePendingHotel
import com.iumrah.beta.domain.nuitee.NuiteeRules
import com.iumrah.beta.domain.trip.TripStayPlanner
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
import java.time.ZoneId
import java.time.Instant
import java.time.OffsetDateTime
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class JourneyState(
    val trip: TripDraft = TripDraft(),
    val makkahHotel: HotelSummary? = null,
    val makkahRoom: HotelRoom? = null,
    val makkahRoomCategory: IumrahRoomCategoryOption? = null,
    val madinahHotel: HotelSummary? = null,
    val madinahRoom: HotelRoom? = null,
    val madinahRoomCategory: IumrahRoomCategoryOption? = null,
    val flightResults: List<LiveFlightJourneyCandidate> = emptyList(),
    val selectedJourneyId: String? = null,
    val selectedOutboundJourneyId: String? = null,
    val packageFlightPath: PackageFlightPath = PackageFlightPath.PUBLISHED_DIRECT,
    val selectedPublishedCompleteID: String? = null,
    val selectedPublishedOutboundID: String? = null,
    val selectedPublishedReturnID: String? = null,
    val stagedAviasalesRoundTrip: FlightDiscoveryOffer? = null,
    val stagedAviasalesOutbound: FlightDiscoveryOffer? = null,
    val stagedAviasalesReturn: FlightDiscoveryOffer? = null,
    val selectedTransferVehicle: TransferVehicleKind? = null,
    val haramainTrainSelected: Boolean = false,
    val haramainFareClass: HaramainFareClass = HaramainFareClass.ECONOMY,
    val haramainAdultTickets: Int = 0,
    val haramainChildTickets: Int = 0,
    val transferSelectionConfirmed: Boolean = false,
    val quote: PackageQuote? = null,
    val isSearchingFlights: Boolean = false,
    val flightError: String? = null,
    val packageError: String? = null,
    val nuitee: NuiteeJourneyDraft? = null,
    val savedPackageID: String? = null,
    val packageApprovalOwner: String? = null,
    val mixedFlightIdentity: String? = null,
    val childAges: List<Int> = emptyList(),
    val acceptedReviewTotal: BigDecimal? = null,
) {
    val priceChangeAccepted: Boolean get() = quote?.let { acceptedReviewTotal == null || acceptedReviewTotal.compareTo(it.totalPackagePrice) == 0 } ?: false
    val selectedJourney: LiveFlightJourneyCandidate? get() = flightResults.firstOrNull { it.id == selectedJourneyId }
    val selectedOutboundJourney: LiveFlightJourneyCandidate? get() = flightResults.firstOrNull { it.id == selectedOutboundJourneyId }
    val haramainTicketCount: Int get() = maxOf(0, haramainAdultTickets) + maxOf(0, haramainChildTickets)
    val resolvedTransferVehicle: TransferVehicleKind get() = selectedTransferVehicle ?: TransferVehicleKind.CARNIVAL
    val hasMakkahRoomSelection: Boolean get() = makkahRoom != null || makkahRoomCategory != null
    val hasMadinahRoomSelection: Boolean get() = madinahRoom != null || madinahRoomCategory != null
    // Match iOS PrimaryHotelView: selection is not blocked by a stale room-list cache.
    // PackageEngine re-reads current hotel detail and accepts a null room/category id.
    val hasRequiredHotels: Boolean get() =
        makkahHotel != null &&
            (trip.scope != JourneyScope.MAKKAH_AND_MADINAH || madinahHotel != null)
    val readyForPackage: Boolean get() = hasRequiredHotels && selectedJourney != null
    val hasCompletePublishedFlightSelection: Boolean get() =
        !selectedPublishedCompleteID.isNullOrBlank() ||
            (!selectedPublishedOutboundID.isNullOrBlank() && !selectedPublishedReturnID.isNullOrBlank())
    val hasCompleteStagedFlightSelection: Boolean get() {
        val roundTrip = stagedAviasalesRoundTrip
        if (roundTrip != null) {
            return roundTrip.isRoundTrip && roundTrip.origin.uppercase() !in setOf("JED", "MED") &&
                roundTrip.destination.uppercase() in setOf("JED", "MED") &&
                runCatching { java.time.LocalDate.parse(roundTrip.returnAt!!.take(10)) > java.time.LocalDate.parse(roundTrip.departureAt.take(10)) }.getOrDefault(false)
        }
        val out = stagedAviasalesOutbound ?: return false
        val back = stagedAviasalesReturn ?: return false
        return out.origin.equals(back.destination, true) &&
            out.destination.uppercase() in setOf("JED", "MED") &&
            back.origin.uppercase() in setOf("JED", "MED") &&
            runCatching { java.time.LocalDate.parse(back.departureAt.take(10)) > java.time.LocalDate.parse(out.departureAt.take(10)) }.getOrDefault(false)
    }
    val hasSelectableHotelMeals: Boolean get() = trip.packageTier == PackageTier.COMFORT || trip.packageTier == PackageTier.LUXURY
    val hasFinalGeneratorQuote: Boolean get() =
        quote?.quoteId?.startsWith("server-") == true &&
            !quote?.quoteProof.isNullOrBlank() &&
            (quote?.totalPackagePrice?.signum() ?: 0) > 0 &&
            (quote?.pricePerPerson?.signum() ?: 0) > 0
}

class JourneyStore {
    // A slow quote must never replace a newer route, room or flight selection.
    // The remote quote is authoritative, but only for the exact inputs it priced.
    private var quoteRequestGeneration = 0L

    private fun quoteInputs(state: JourneyState): List<Any?> = listOf(
        state.trip, state.packageFlightPath, state.selectedJourney, state.selectedOutboundJourney, state.savedPackageID, state.packageApprovalOwner, state.mixedFlightIdentity, state.childAges,
        state.makkahHotel?.id, state.makkahRoom?.id, state.makkahRoomCategory?.id,
        state.madinahHotel?.id, state.madinahRoom?.id, state.madinahRoomCategory?.id,
        state.selectedPublishedCompleteID, state.selectedPublishedOutboundID, state.selectedPublishedReturnID,
        state.stagedAviasalesRoundTrip, state.stagedAviasalesOutbound, state.stagedAviasalesReturn,
        state.selectedTransferVehicle, state.haramainTrainSelected,
        state.haramainFareClass, state.haramainAdultTickets, state.haramainChildTickets, state.nuitee,
    )

    private val _state = MutableStateFlow(JourneyState())
    val state: StateFlow<JourneyState> = _state

    fun acceptReviewPrice(expected: PackageQuote) {
        _state.update { if (it.quote == expected && expected.hasVerifiedBreakdown) it.copy(acceptedReviewTotal = expected.totalPackagePrice) else it }
    }

    fun restoreSavedDraft(value: JourneyState, packageID: String, previousTotal: BigDecimal? = null) {
        quoteRequestGeneration++
        _state.value = value.copy(quote = null, isSearchingFlights = false, flightError = null, packageError = null,
            savedPackageID = packageID, packageApprovalOwner = null, acceptedReviewTotal = previousTotal)
    }

    fun attachSavedPackage(expected: JourneyState, packageID: String): Boolean {
        if (_state.value != expected) return false
        _state.value = expected.copy(savedPackageID = packageID)
        return true
    }

    fun applyApprovedRooms(packageID: String, owner: String, adults: Int, rooms: Int) {
        val current = _state.value
        require(current.savedPackageID == packageID && current.nuitee != null && current.trip.adults == adults && rooms in 1 until current.trip.rooms)
        quoteRequestGeneration++
        _state.value = current.copy(trip = current.trip.copy(rooms = rooms),
            nuitee = current.nuitee.copy(rooms = emptyMap(), pending = emptyMap()),
            quote = null, packageError = null, transferSelectionConfirmed = false, packageApprovalOwner = owner, childAges = resolvedGuestAges(current))
    }

    /** Smart is assembled privately. Publish only a complete verified journey on explicit review. */
    fun adoptSmartResult(result: JourneyStore) {
        val value = result.state.value
        require(value.hasFinalGeneratorQuote && value.quote?.bookingAllowed == true &&
            value.nuitee?.let { com.iumrah.beta.domain.nuitee.NuiteeBookingRules.complete(value.trip, it) } == true)
        quoteRequestGeneration++
        _state.value = value
    }

    /** Review edits own a separate store; cancel simply drops it. Never overwrite a changed parent. */
    fun beginReviewEdit(): JourneyStore = JourneyStore().also {
        it._state.value = state.value.copy(quote = null, packageError = null, isSearchingFlights = false)
    }

    fun commitReviewEdit(expected: JourneyState, editor: JourneyStore): Boolean {
        if (editor === this || _state.value != expected) return false
        val next = editor.state.value
        require(next.savedPackageID == expected.savedPackageID && (next.packageApprovalOwner == null || next.packageApprovalOwner == expected.packageApprovalOwner))
        quoteRequestGeneration++
        _state.value = next.copy(quote = null, packageError = null, isSearchingFlights = false,
            acceptedReviewTotal = expected.quote?.totalPackagePrice ?: expected.acceptedReviewTotal)
        return true
    }

    /** A legacy catalog picker may reset flight selection internally. Apply only the selected hotels. */
    fun commitReviewCatalogEdit(expected: JourneyState, editor: JourneyStore): Boolean {
        if (editor === this || _state.value != expected) return false
        val next = editor.state.value
        require(next.savedPackageID == expected.savedPackageID && next.packageApprovalOwner == expected.packageApprovalOwner)
        quoteRequestGeneration++
        _state.value = expected.copy(makkahHotel = next.makkahHotel, makkahRoom = next.makkahRoom, makkahRoomCategory = next.makkahRoomCategory,
            madinahHotel = next.madinahHotel, madinahRoom = next.madinahRoom, madinahRoomCategory = next.madinahRoomCategory,
            quote = null, packageError = null, acceptedReviewTotal = expected.quote?.totalPackagePrice ?: expected.acceptedReviewTotal)
        return true
    }

    fun commitReviewFlightEdit(expected: JourneyState, editor: JourneyStore): Boolean {
        if (editor === this || _state.value != expected) return false
        val next = editor.state.value
        require(next.nuitee?.flights?.size == 2 && next.trip.travelerCount == expected.trip.travelerCount && next.trip.rooms == expected.trip.rooms)
        val old = expected.trip; val new = next.trip
        val sameStay = old.departureDate == new.departureDate && old.returnDate == new.returnDate &&
            old.hotelStayStartDate == new.hotelStayStartDate && old.arrivalAirport == new.arrivalAirport && old.scope == new.scope && old.returnOriginCode == new.returnOriginCode
        val restored = next.copy(
            nuitee = requireNotNull(next.nuitee).copy(rooms = if (sameStay) expected.nuitee?.rooms.orEmpty() else emptyMap(), pending = if (sameStay) expected.nuitee?.pending.orEmpty() else emptyMap()),
            selectedTransferVehicle = expected.selectedTransferVehicle, haramainTrainSelected = expected.haramainTrainSelected && new.scope == JourneyScope.MAKKAH_AND_MADINAH,
            haramainFareClass = expected.haramainFareClass, haramainAdultTickets = expected.haramainAdultTickets, haramainChildTickets = expected.haramainChildTickets,
            transferSelectionConfirmed = sameStay && old.isRoundTripFlight && expected.transferSelectionConfirmed,
            savedPackageID = expected.savedPackageID, packageApprovalOwner = null, childAges = resolvedGuestAges(expected))
        quoteRequestGeneration++
        _state.value = restored.copy(quote = null, packageError = null, isSearchingFlights = false,
            acceptedReviewTotal = expected.quote?.totalPackagePrice ?: expected.acceptedReviewTotal)
        return true
    }

    /** A private editable copy must not carry a bookable quote. */
    fun smartEditCopy(): JourneyStore {
        val value = state.value
        require(value.hasFinalGeneratorQuote && value.quote?.bookingAllowed == true)
        return JourneyStore().also { it._state.value = value.copy(quote = null, packageError = null) }
    }

    /** Resolve already selected flight identities before entering the shared hotel picker. */
    suspend fun prepareUnifiedHotelSelection(curated: CuratedFlightRecommendationService) {
        val snapshot = _state.value
        if (snapshot.nuitee?.flights?.size == 2) return
        val resolved = when (snapshot.packageFlightPath) {
            PackageFlightPath.PUBLISHED_DIRECT -> curated.resolvePublishedSelection(snapshot.trip,
                CuratedPublishedFlightSelection(snapshot.selectedPublishedCompleteID,snapshot.selectedPublishedOutboundID,snapshot.selectedPublishedReturnID))
            PackageFlightPath.AVIASALES_SELECTED -> aviasalesPair(snapshot)
            else -> error("FLIGHT_SELECTION_REQUIRED")
        }
        currentCoroutineContext().ensureActive()
        check(_state.value == snapshot) { "PACKAGE_SELECTION_CHANGED" }
        val out=resolved.outbound; val back=requireNotNull(resolved.inbound)
        require(back.departureAt>out.arrivalAt && out.origin==back.destination && out.destination in setOf("JED","MED") && back.origin in setOf("JED","MED")) { "INVALID_FLIGHT_PAIR" }
        require(if(snapshot.packageFlightPath==PackageFlightPath.PUBLISHED_DIRECT) resolved.isDisplayable() else out.durationMinutes>0 && back.durationMinutes>0)
        fun day(at:Instant, code:String) = at.atZone(com.iumrah.beta.data.flight.FlightReferenceCatalog.timeZone(code) ?: java.time.ZoneOffset.UTC).toLocalDate()
        val arrival=day(out.arrivalAt,out.destination);val end=day(back.departureAt,back.origin)
        require(java.time.temporal.ChronoUnit.DAYS.between(arrival,end)>=(if(snapshot.trip.scope==JourneyScope.MAKKAH_AND_MADINAH) 2 else 1)) { "INVALID_STAY" }
        fun local(f:com.iumrah.beta.models.flight.LiveFlightCandidate)=NuiteeFlightSnapshot(f.id,f.origin,f.destination,f.airline,f.flightNumber,f.airlineCode.orEmpty(),f.durationMinutes,f.stops,f.departureAt.toString(),f.arrivalAt.toString(),f.observedFare.toDouble().takeIf{it>0})
        _state.value=snapshot.copy(trip=snapshot.trip.copy(departureDate=day(out.departureAt,out.origin),saudiArrivalDate=arrival,returnDate=end,
            arrivalAirport=if(out.destination=="MED") SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH,
            selectedReturnAirport=if(back.origin=="MED") SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH,flightTripType=FlightTripType.ROUND_TRIP),
            flightResults=listOf(resolved),selectedJourneyId=resolved.id,selectedOutboundJourneyId=resolved.id,
            nuitee=NuiteeJourneyDraft(flights=listOf(local(out),local(back))),quote=null,
            makkahHotel=null,makkahRoom=null,makkahRoomCategory=null,madinahHotel=null,madinahRoom=null,madinahRoomCategory=null,
            packageError=null)
    }

    fun setMixedFlightIdentity(identity:String) {
        require(identity.startsWith("mixed:") && identity.removePrefix("mixed:").split('~').let { it.size==2 && it.count{p->p.startsWith("curated:") && p.length>8}==1 && it.count{p->p.startsWith("aviasales:ow:") && p.length>13}==1 })
        quoteRequestGeneration++
        _state.update { it.copy(mixedFlightIdentity=identity,quote=null) }
    }

    private fun mixedJourney(state:JourneyState): LiveFlightJourneyCandidate {
        val identity=requireNotNull(state.mixedFlightIdentity)
        val ids=identity.removePrefix("mixed:").split('~');val flights=requireNotNull(state.nuitee).flights
        require(ids.size==2 && flights.size==2)
        val now=Instant.now()
        val legs=flights.mapIndexed { index,f ->
            val departure=requireNotNull(parseAviasalesInstant(f.departureAt)); val arrival=parseAviasalesInstant(f.arrivalAt.orEmpty()) ?: departure.plusSeconds(f.durationMinutes.toLong()*60)
            val source=if(ids[index].startsWith("curated:")) "iumrah Published" else "Aviasales Data"
            require(f.durationMinutes>0 && f.stops>=0 && arrival>departure && f.flightNumber.isNotBlank() && f.airline.isNotBlank())
            com.iumrah.beta.models.flight.LiveFlightCandidate(id=f.id,sourceID=ids[index],sourceName=source,direction=if(index==0) FlightDirection.outbound else FlightDirection.inbound,
                airline=f.airline,flightNumber=f.flightNumber,origin=f.origin,destination=f.destination,departureAt=departure,arrivalAt=arrival,
                stops=f.stops,durationMinutes=f.durationMinutes,observedFare=BigDecimal.valueOf(f.adultFareUSD ?: 0.0),observedCurrency="USD",
                fareScope=FlightFareScope.PER_PASSENGER,observedAt=now,airlineCode=f.airlineCode,providerItineraryID=identity,cabinClass="economy")
        }
        val out=legs[0];val back=legs[1]
        require(out.origin==back.destination && out.destination in setOf("JED","MED") && back.origin in setOf("JED","MED") && back.departureAt>out.arrivalAt)
        // Only the backend may turn these independent identity hints into a bookable fare.
        return LiveFlightJourneyCandidate(id=identity,sourceID="mixed",sourceName="mixed",totalFare=legs.fold(BigDecimal.ZERO){v,f->v+f.observedFare},currency="USD",
            fareScope=FlightFareScope.PER_PASSENGER,observedAt=now,providerItineraryID=identity,outbound=out,inbound=back)
    }

    fun resolvedGuestAges(current: JourneyState = state.value): List<Int> {
        val count = current.trip.children + current.trip.infants
        if (current.childAges.size == count) return current.childAges
        val rooms = current.nuitee?.rooms?.values?.filter { it.city == "makkah" }?.sortedBy { it.roomIndex }.orEmpty()
        return List(count) { index -> rooms.firstOrNull { it.roomIndex == index % current.trip.rooms.coerceAtLeast(1) }?.childrenAges?.getOrNull(index / current.trip.rooms.coerceAtLeast(1)) ?: -1 }
    }

    fun applyReviewGuests(adults: Int, rooms: Int, ages: List<Int>) {
        require(adults in 1..9 && adults + ages.size <= 9 && ages.all { it in 0..17 })
        require(rooms in ((adults + 4) / 5)..adults)
        val current = _state.value
        val next = current.trip.copy(adults=adults, rooms=rooms, children=ages.count { it>=2 }, infants=ages.count { it<2 })
        if (next == current.trip && ages == resolvedGuestAges(current)) return
        quoteRequestGeneration++
        _state.value = current.copy(trip=next, childAges=ages, nuitee=current.nuitee?.copy(rooms=emptyMap(),pending=emptyMap()),
            quote=null, packageError=null, packageApprovalOwner=null, transferSelectionConfirmed=false,
            haramainAdultTickets=if(current.haramainTrainSelected) adults else 0,
            haramainChildTickets=if(current.haramainTrainSelected) next.children else 0)
    }

    fun updateGuestAges(ages:List<Int>) {
        val current=_state.value
        require(ages.size==current.trip.children+current.trip.infants && ages.all{it in -1..17})
        if(ages==current.childAges)return
        quoteRequestGeneration++
        _state.value=current.copy(childAges=ages,nuitee=current.nuitee?.copy(rooms=emptyMap(),pending=emptyMap()),quote=null,transferSelectionConfirmed=false,packageApprovalOwner=null)
    }

    /** Flight-first Nuitee selections are resolved by PackageEngine before booking. */
    fun startNuiteeJourney(flights: List<NuiteeFlightSnapshot>) {
        require(flights.size == 2)
        _state.update { current ->
            // Arrival at JED/MED may be after midnight. Hotel check-in must use
            // the local Saudi arrival date, not blindly the origin departure date.
            val saudiArrivalDay = flights.first().arrivalAt?.let { value ->
                runCatching { java.time.OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.of("Asia/Riyadh")).toLocalDate() }.getOrNull()
            }?.takeIf { !it.isBefore(current.trip.departureDate) && it.isBefore(current.trip.returnDate) }
            current.copy(
                trip = current.trip.copy(saudiArrivalDate = saudiArrivalDay ?: current.trip.saudiArrivalDate),
                nuitee = NuiteeJourneyDraft(flights = flights), quote = null,
                makkahHotel = null, makkahRoom = null, makkahRoomCategory = null,
                madinahHotel = null, madinahRoom = null, madinahRoomCategory = null,
                packageError = null, transferSelectionConfirmed = false,
            )
        }
    }

    /** Change occupancy without discarding the already verified flight route. */
    fun setSmartRoomCount(count: Int) {
        val current = _state.value
        require(count in 1..current.trip.adults && current.nuitee != null)
        if (count == current.trip.rooms) return
        quoteRequestGeneration++
        _state.value = current.copy(trip = current.trip.copy(rooms = count),
            nuitee = current.nuitee.copy(rooms = emptyMap(), pending = emptyMap()),
            quote = null, packageError = null, transferSelectionConfirmed = false)
    }

    fun chooseNuiteeRoom(selection: NuiteeRoomSelection) {
        _state.update { current ->
            val draft = current.nuitee ?: return@update current
            val windows = TripStayPlanner.windows(current.trip)
            val expected = if (selection.city == "madinah") windows.madinah else windows.makkah
            if (expected == null || selection.roomIndex !in 0 until current.trip.rooms ||
                selection.key != "${selection.city}:${selection.roomIndex}" ||
                selection.checkin != expected.checkIn || selection.checkout != expected.checkOut ||
                !NuiteeRules.validStay(selection.checkin, selection.checkout, selection.adults, selection.childrenAges) ||
                !NuiteeRules.validRoomRate(selection.rate)) return@update current
            val sameCityHotel = draft.rooms.filterValues { it.city != selection.city || it.hotel.id == selection.hotel.id }
            val compatiblePending = draft.pending.filterValues { it.city != selection.city || it.hotel.id == selection.hotel.id }
            current.copy(nuitee = draft.copy(rooms = sameCityHotel + (selection.key to selection),
                pending = compatiblePending - selection.key), quote = null, transferSelectionConfirmed = false)
        }
    }

    /** Update one room's optional meals without losing the selected supplier rate or transfer. */
    fun updateNuiteeRoomMeals(expected: NuiteeRoomSelection, meals: com.iumrah.beta.models.nuitee.NuiteeMealChoices) {
        _state.update { current ->
            val draft = current.nuitee ?: return@update current
            val room = draft.rooms[expected.key] ?: return@update current
            if (room != expected || room.meals == meals) return@update current
            current.copy(nuitee = draft.copy(rooms = draft.rooms + (room.key to room.copy(meals = meals))),
                quote = null, packageError = null)
        }
    }

    /** Replace a room only if it is still the offer that opened the details sheet. */
    fun replaceNuiteeRoom(expected: NuiteeRoomSelection, replacement: NuiteeRoomSelection) {
        _state.update { current ->
            val draft = current.nuitee ?: return@update current
            if (draft.rooms[expected.key] != expected || replacement.copy(rate = expected.rate, meals = expected.meals) != expected ||
                !NuiteeRules.validRoomRate(replacement.rate) || replacement == expected) return@update current
            current.copy(nuitee = draft.copy(rooms = draft.rooms + (replacement.key to replacement)), quote = null, packageError = null)
        }
    }

    /** Map only selected supplier rooms, then reuse the existing authoritative flight resolver. */
    suspend fun prepareNuiteePackage(curated: CuratedFlightRecommendationService, engine: RemotePackageEngineClient): Result<PackageQuote> {
        val snapshot = _state.value
        val draft = snapshot.nuitee ?: return Result.failure(IllegalStateException("NUITEE_ROOMS_REQUIRED"))
        val rules = com.iumrah.beta.domain.nuitee.NuiteeBookingRules
        if (!rules.complete(snapshot.trip, draft) || snapshot.resolvedTransferVehicle.passengerCapacity < snapshot.trip.travelerCount) {
            return Result.failure(IllegalStateException("NUITEE_ROOMS_REQUIRED"))
        }
        val tier = rules.tier(draft.rooms.values) ?: return Result.failure(IllegalStateException("HOTEL_STARS_REQUIRED"))
        fun hotel(room: NuiteeRoomSelection) = HotelSummary(id = room.hotel.id, name = room.hotel.name,
            city = room.city, stars = room.hotel.roundedStars, status = "available", coverImageURL = room.hotel.coverImageURL,
            roomCount = 1, updatedAt = "")
        fun room(value: NuiteeRoomSelection) = HotelRoom(id = value.rate.roomId, name = value.rate.roomName,
            maxGuests = value.adults + value.childrenAges.size, description = value.rate.mealPlan)
        val makkah = draft.rooms.getValue("makkah:0")
        val madinah = draft.rooms["madinah:0"]
        _state.value = snapshot.copy(trip = snapshot.trip.copy(packageTier = tier, hotelStars = draft.rooms.values.maxOf { it.hotel.roundedStars }),
            makkahHotel = hotel(makkah), makkahRoom = room(makkah), makkahRoomCategory = null,
            madinahHotel = madinah?.let(::hotel), madinahRoom = madinah?.let(::room), madinahRoomCategory = null,
            selectedTransferVehicle = snapshot.resolvedTransferVehicle, transferSelectionConfirmed = true, quote = null)
        val result = if(snapshot.mixedFlightIdentity!=null) runCatching {
            val mixed=mixedJourney(_state.value)
            _state.update { it.copy(flightResults=listOf(mixed),selectedJourneyId=mixed.id,selectedOutboundJourneyId=mixed.id) }
            refreshTransferQuote(engine::packageQuote)
        } else when (snapshot.packageFlightPath) {
            PackageFlightPath.PUBLISHED_DIRECT -> preparePublishedDirectPackage(curated, engine)
            PackageFlightPath.AVIASALES_SELECTED -> prepareAviasalesSelectedPackage(engine)
            else -> Result.failure(IllegalStateException("FLIGHT_SELECTION_REQUIRED"))
        }
        val quote = result.getOrNull() ?: return result
        val current = _state.value
        if (current.nuitee != draft || current.quote != quote) return Result.failure(IllegalStateException("PACKAGE_SELECTION_CHANGED"))
        if (quote.bookingAllowed != true || !current.hasFinalGeneratorQuote || quote.currency != "USD" ||
            quote.resolvedTier != tier.wireValue || !rules.complete(current.trip, draft) ||
            (quote.stayPlan != null && draft.rooms.values.any { room ->
                val plan = requireNotNull(quote.stayPlan)
                val start = if (room.city == "madinah") plan.madinahCheckIn else plan.makkahCheckIn
                val end = if (room.city == "madinah") plan.madinahCheckOut else plan.makkahCheckOut
                start != room.checkin.toString() || end != room.checkout.toString()
            })) {
            _state.update { if (it.quote == quote) it.copy(quote = null) else it }
            return Result.failure(IllegalStateException("PACKAGE_QUOTE_NOT_BOOKABLE"))
        }
        return result
    }

    fun chooseNuiteePending(selection: NuiteePendingHotel) {
        _state.update { current ->
            val draft = current.nuitee ?: return@update current
            val windows = TripStayPlanner.windows(current.trip)
            val expected = if (selection.city == "madinah") windows.madinah else windows.makkah
            if (expected == null || selection.roomIndex !in 0 until current.trip.rooms ||
                selection.key != "${selection.city}:${selection.roomIndex}" ||
                selection.checkin != expected.checkIn || selection.checkout != expected.checkOut ||
                !NuiteeRules.validStay(selection.checkin, selection.checkout, selection.adults, selection.childrenAges)) return@update current
            current.copy(nuitee = draft.copy(pending = draft.pending + (selection.key to selection),
                rooms = draft.rooms - selection.key), quote = null, transferSelectionConfirmed = false)
        }
    }

    fun changeNuiteeRoomCount(count: Int) {
        _state.update { current ->
            if (current.nuitee == null || count !in ((current.trip.adults + 4) / 5)..current.trip.adults) current else current.copy(
                trip = current.trip.copy(rooms = count),
                childAges = resolvedGuestAges(current), packageApprovalOwner = null,
                nuitee = current.nuitee.copy(rooms = emptyMap(), pending = emptyMap()),
                quote = null, transferSelectionConfirmed = false,
            )
        }
    }

    fun clearNuiteeRooms() {
        _state.update { current -> current.copy(
            nuitee = current.nuitee?.copy(rooms = emptyMap(), pending = emptyMap()), quote = null,
            transferSelectionConfirmed = false,
        ) }
    }

    /** Revalidation failure removes ONLY the original unchanged offer; it never installs a stale rate. */
    fun revalidateNuiteeRoom(key: String, originalOfferId: String, originalRoomId: String,
                             refreshed: com.iumrah.beta.models.nuitee.NuiteeRoomRate?, expected: NuiteeRoomSelection? = null) {
        _state.update { current ->
            val draft = current.nuitee ?: return@update current
            val previous = draft.rooms[key] ?: return@update current
            if (previous.rate.offerId != originalOfferId || previous.rate.roomId != originalRoomId || (expected != null && previous != expected)) return@update current
            val replacement = refreshed?.takeIf { NuiteeRules.validRoomRate(it) && NuiteeRules.sameOffer(previous.rate, it) }
            current.copy(nuitee = draft.copy(rooms = if (replacement != null) draft.rooms +
                (key to previous.copy(rate = replacement)) else draft.rooms - key), quote = null)
        }
    }

    /** A guide preference does not change server-owned accommodation or fare pricing. */
    fun setGuidePreference(value: com.iumrah.beta.domain.trip.IumrahGuidePreference?) {
        _state.update { current ->
            if (current.trip.guidePreference == value) current else current.copy(
                trip = current.trip.copy(guidePreference = value))
        }
    }

    fun updateTrip(value: TripDraft) {
        _state.update { current ->
            if (current.trip == value) current else JourneyState(
                trip = value,
                childAges = JourneyGuestAgePolicy.afterTripEdit(current.trip, value, current.childAges),
                packageFlightPath = if (value.isWeekendUmrah) PackageFlightPath.WEEKEND else PackageFlightPath.PUBLISHED_DIRECT,
            )
        }
    }

    /** Changing package destination scope in Flights must not erase a staged outbound
     * ticket. No implicit change of the first flight's origin or selected fare. */
    fun updateFlightScopePreservingSelection(scope: JourneyScope) {
        _state.update { current ->
            if (current.trip.scope == scope) current else current.copy(
                trip = current.trip.copy(scope = scope,
                    arrivalAirport = if (scope == JourneyScope.MAKKAH_ONLY) SaudiArrivalAirport.JEDDAH else current.trip.arrivalAirport),
                makkahHotel = null, makkahRoom = null, makkahRoomCategory = null,
                madinahHotel = null, madinahRoom = null, madinahRoomCategory = null,
                nuitee = null, quote = null, packageError = null, transferSelectionConfirmed = false,
            )
        }
    }

    /** Home starts a genuinely new search even when its trip parameters did not change. */
    fun startHomePublishedSearch(value: TripDraft) {
        quoteRequestGeneration++
        _state.value = JourneyState(trip = value.copy(saudiArrivalDate = null,
            flightTripType = FlightTripType.ROUND_TRIP, flexibility = DateFlexibility.EXACT),
            packageFlightPath = PackageFlightPath.PUBLISHED_DIRECT)
    }

    /** Commits the first configurator step while preserving the iOS published-flight path/selection. */
    fun commitTripBuilder(
        value: TripDraft,
        path: PackageFlightPath,
        completeID: String? = null,
        outboundID: String? = null,
        returnID: String? = null,
    ) {
        _state.update { current ->
            // A commit is the authoritative selection snapshot from the active
            // configurator. Null means *cleared*, not "keep an old selection".
            // Otherwise a stale complete-ID can override newly chosen one-way
            // flights when the route happens to remain the same.
            current.copy(
                trip = value,
                packageFlightPath = path,
                selectedPublishedCompleteID = completeID,
                selectedPublishedOutboundID = outboundID,
                selectedPublishedReturnID = returnID,
                flightResults = emptyList(),
                selectedJourneyId = null,
                selectedOutboundJourneyId = null,
                nuitee = null, quote = null, mixedFlightIdentity = null, savedPackageID = null, packageApprovalOwner = null, childAges = JourneyGuestAgePolicy.afterTripEdit(current.trip, value, current.childAges), acceptedReviewTotal = null,
                packageError = null,
                transferSelectionConfirmed = false,
            )
        }
    }

    fun setPackageFlightPath(path: PackageFlightPath) {
        _state.update { current ->
            if (current.packageFlightPath == path) current else current.copy(
                packageFlightPath = path,
                selectedPublishedCompleteID = null,
                selectedPublishedOutboundID = null,
                selectedPublishedReturnID = null,
                flightResults = emptyList(),
                selectedJourneyId = null,
                selectedOutboundJourneyId = null,
                nuitee = null, quote = null,
                packageError = null,
                transferSelectionConfirmed = false,
            )
        }
    }

    fun setPublishedSelection(completeID: String? = null, outboundID: String? = null, returnID: String? = null) {
        _state.update { current -> current.copy(
            selectedPublishedCompleteID = completeID,
            selectedPublishedOutboundID = outboundID,
            selectedPublishedReturnID = returnID,
            flightResults = emptyList(),
            selectedJourneyId = null,
            selectedOutboundJourneyId = null,
            nuitee = null, quote = null,
            packageError = null,
            transferSelectionConfirmed = false,
        ) }
    }

    fun clearPublishedFlightSelection() {
        quoteRequestGeneration++
        _state.update { it.copy(
            selectedPublishedCompleteID = null,
            selectedPublishedOutboundID = null,
            selectedPublishedReturnID = null,
            flightResults = emptyList(), selectedJourneyId = null, selectedOutboundJourneyId = null,
            nuitee = null, quote = null, transferSelectionConfirmed = false,
            packageError = null,
        ) }
    }

    suspend fun preparePublishedDirectPackage(
        curated: CuratedFlightRecommendationService,
        packageEngine: RemotePackageEngineClient,
    ): Result<PackageQuote> {
        val snapshot = _state.value
        if (snapshot.packageFlightPath != PackageFlightPath.PUBLISHED_DIRECT || !snapshot.hasCompletePublishedFlightSelection) {
            val error = IllegalStateException("Select published outbound and return flights first.")
            _state.update { it.copy(packageError = error.message) }
            return Result.failure(error)
        }
        val generation = ++quoteRequestGeneration
        val initialInputs = quoteInputs(snapshot)
        var activeInputs = initialInputs
        return runCatching {
            val resolved = curated.resolvePublishedSelection(
                snapshot.trip,
                CuratedPublishedFlightSelection(
                    completeID = snapshot.selectedPublishedCompleteID,
                    outboundID = snapshot.selectedPublishedOutboundID,
                    returnID = snapshot.selectedPublishedReturnID,
                ),
            )
            check(generation == quoteRequestGeneration && quoteInputs(_state.value) == initialInputs) {
                "Flight selection changed while resolving the package."
            }
            val arrivalSegment = resolved.outbound.segments?.lastOrNull()
            val zone = arrivalSegment?.destination?.timeZoneIdentifier
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("Asia/Riyadh")
            val arrivalDate = resolved.outbound.arrivalAt.atZone(zone).toLocalDate()
            val resolvedState = _state.value.copy(
                trip = _state.value.trip.copy(saudiArrivalDate = arrivalDate),
                flightResults = listOf(resolved),
                selectedJourneyId = resolved.id,
                selectedOutboundJourneyId = resolved.id,
                packageError = null,
                quote = null,
            )
            _state.value = resolvedState
            val resolvedInputs = quoteInputs(resolvedState)
            activeInputs = resolvedInputs
            val quote = packageEngine.packageQuote(resolvedState)
            check(generation == quoteRequestGeneration && quoteInputs(_state.value) == resolvedInputs) {
                "Package selection changed while fetching the quote."
            }
            _state.update { it.copy(quote = quote, packageError = null, acceptedReviewTotal = it.acceptedReviewTotal ?: quote.totalPackagePrice) }
            quote
        }.onFailure { error ->
            if (generation == quoteRequestGeneration && quoteInputs(_state.value) == activeInputs) {
                _state.update { it.copy(packageError = error.message ?: "PACKAGE_QUOTE_FAILED", quote = null) }
            }
        }
    }


    /** The public Data API fare is only a snapshot. Identity and price are
     * re-resolved by /api/package/quote; the client never trusts this amount. */
    fun stageAviasalesFlight(offer: FlightDiscoveryOffer, returnLeg: Boolean = false, adults: Int? = null, children: Int? = null, infants: Int? = null) {
        val isSaudiOrigin = offer.origin.uppercase() in setOf("JED", "MED")
        val isSaudiDestination = offer.destination.uppercase() in setOf("JED", "MED")
        if (!isSaudiOrigin && !isSaudiDestination) return
        _state.update { current ->
            val asRoundTrip = offer.isRoundTrip && !returnLeg
            val asReturn = returnLeg || (isSaudiOrigin && !isSaudiDestination)
            val matchingOutbound = current.stagedAviasalesOutbound?.takeIf { it.origin.equals(offer.destination, true) }
            val matchingReturn = current.stagedAviasalesReturn?.takeIf { it.destination.equals(offer.origin, true) }
            val update = if (asRoundTrip) {
                Triple(offer, null, null)
            } else if (asReturn) {
                Triple(null, matchingOutbound, offer)
            } else {
                Triple(null, offer, matchingReturn)
            }
            val newTrip = current.trip.copy(
                origin = if (!asReturn) offer.origin.uppercase() else current.trip.origin,
                originAirport = if (!asReturn && current.trip.originCode != offer.origin.uppercase()) null else current.trip.originAirport,
                arrivalAirport = if (isSaudiDestination) (if (offer.destination.equals("MED", true)) SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH) else current.trip.arrivalAirport,
                departureDate = if (!asReturn) parseAviasalesDay(offer.departureAt) ?: current.trip.departureDate else current.trip.departureDate,
                returnDate = if (asRoundTrip) offer.returnAt?.let(::parseAviasalesDay) ?: current.trip.returnDate else if (asReturn) parseAviasalesDay(offer.departureAt) ?: current.trip.returnDate else current.trip.returnDate,
                flightTripType = FlightTripType.ROUND_TRIP,
                adults = adults ?: current.trip.adults,
                children = children ?: current.trip.children,
                infants = infants ?: current.trip.infants,
                saudiArrivalDate = null,
            )
            current.copy(
                trip = newTrip,
                packageFlightPath = PackageFlightPath.AVIASALES_SELECTED,
                stagedAviasalesRoundTrip = update.first,
                stagedAviasalesOutbound = update.second,
                stagedAviasalesReturn = update.third,
                selectedPublishedCompleteID = null, selectedPublishedOutboundID = null, selectedPublishedReturnID = null,
                flightResults = emptyList(), selectedJourneyId = null, selectedOutboundJourneyId = null,
                makkahHotel = null, makkahRoom = null, makkahRoomCategory = null,
                madinahHotel = null, madinahRoom = null, madinahRoomCategory = null,
                nuitee = null, quote = null, packageError = null, transferSelectionConfirmed = false,
            )
        }
    }

    fun clearStagedAviasalesFlights() {
        quoteRequestGeneration++
        _state.update { current ->
            if (current.packageFlightPath == PackageFlightPath.AVIASALES_SELECTED) current.copy(
                stagedAviasalesRoundTrip = null, stagedAviasalesOutbound = null, stagedAviasalesReturn = null,
                flightResults = emptyList(), selectedJourneyId = null, selectedOutboundJourneyId = null,
                nuitee = null, quote = null, packageError = null, transferSelectionConfirmed = false,
            ) else current.copy(stagedAviasalesRoundTrip = null, stagedAviasalesOutbound = null, stagedAviasalesReturn = null)
        }
    }

    private fun parseAviasalesDay(raw: String): java.time.LocalDate? =
        runCatching { java.time.LocalDate.parse(raw.take(10)) }.getOrNull()

    private fun parseAviasalesInstant(raw: String): Instant? =
        runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()

    private fun identityToken(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "-" }

    private fun aviasalesIdentity(out: FlightDiscoveryOffer, back: FlightDiscoveryOffer?): String = listOf(
        "aviasales", if (out.isRoundTrip) "rt" else if (back == null) "ow" else "pair",
        identityToken(out.origin), identityToken(out.destination), out.departureAt.take(10),
        if (out.isRoundTrip) out.returnAt?.take(10) ?: "-" else back?.departureAt?.take(10) ?: "-",
        identityToken(out.airlineCode), identityToken(out.flightNumber),
        identityToken(if (out.isRoundTrip) out.returnAirlineCode.orEmpty() else back?.airlineCode.orEmpty()),
        identityToken(if (out.isRoundTrip) out.returnFlightNumber.orEmpty() else back?.flightNumber.orEmpty()),
    ).joinToString(":")

    private fun aviasalesPair(snapshot: JourneyState): LiveFlightJourneyCandidate {
            val outbound = snapshot.stagedAviasalesRoundTrip ?: snapshot.stagedAviasalesOutbound
                ?: error("Outbound flight not selected")
            val inbound = snapshot.stagedAviasalesReturn
            val providerID = aviasalesIdentity(outbound, inbound)
            val at = Instant.now()
            fun candidate(row: FlightDiscoveryOffer, departure: Instant, direction: FlightDirection, origin: String, destination: String, duration: Int, carrier: String, flightNumber: String): com.iumrah.beta.models.flight.LiveFlightCandidate {
                require(duration > 0) { "AVIASALES_UNVERIFIED_DURATION" }
                require(row.price.isFinite() && row.price > 0) { "AVIASALES_INVALID_FARE" }
                return com.iumrah.beta.models.flight.LiveFlightCandidate(
                    id = "$providerID:${direction.name}", sourceID = "aviasales-data", sourceName = "Aviasales Data",
                    direction = direction, airline = carrier, flightNumber = flightNumber,
                    origin = origin, destination = destination,
                    departureAt = departure, arrivalAt = departure.plusSeconds(duration.toLong() * 60L),
                    stops = if (direction == FlightDirection.inbound && row.isRoundTrip) (row.returnTransfers
                        ?: error("AVIASALES_UNVERIFIED_RETURN_STOPS"))
                        else row.transfers,
                    durationMinutes = duration, observedFare = BigDecimal.valueOf(row.price), observedCurrency = "USD",
                    fareScope = FlightFareScope.PER_PASSENGER, observedAt = at,
                    sourceURL = row.bookingUrl, airlineCode = carrier.takeIf { it.isNotBlank() },
                    segments = null, providerItineraryID = providerID, cabinClass = "economy",
                )
            }
            val outAt = parseAviasalesInstant(outbound.departureAt) ?: error("Invalid outbound date")
            val outCandidate = candidate(outbound, outAt, FlightDirection.outbound, outbound.origin, outbound.destination, outbound.durationMinutes, outbound.airlineCode, outbound.flightNumber)
            val reverse = when {
                outbound.isRoundTrip -> {
                    val returnAt = parseAviasalesInstant(outbound.returnAt ?: "") ?: error("Invalid return date")
                    candidate(outbound, returnAt, FlightDirection.inbound, outbound.destination, outbound.origin,
                        outbound.returnDurationMinutes ?: 0, outbound.returnAirlineCode.orEmpty(), outbound.returnFlightNumber.orEmpty())
                }
                inbound != null -> {
                    val returnAt = parseAviasalesInstant(inbound.departureAt) ?: error("Invalid return date")
                    candidate(inbound, returnAt, FlightDirection.inbound, inbound.origin, inbound.destination, inbound.durationMinutes, inbound.airlineCode, inbound.flightNumber)
                }
                else -> error("Return flight missing")
            }
            return LiveFlightJourneyCandidate(
                id = providerID, sourceID = "aviasales-data", sourceName = "Aviasales Data",
                totalFare = BigDecimal.valueOf(outbound.price + (if (outbound.isRoundTrip) 0.0 else inbound?.price ?: 0.0)),
                currency = "USD", fareScope = FlightFareScope.PER_PASSENGER, observedAt = at,
                providerItineraryID = providerID, outbound = outCandidate, inbound = reverse,
            )
    }

    suspend fun prepareAviasalesSelectedPackage(packageEngine: RemotePackageEngineClient): Result<PackageQuote> {
        val snapshot = _state.value
        if (snapshot.packageFlightPath != PackageFlightPath.AVIASALES_SELECTED || !snapshot.hasCompleteStagedFlightSelection) {
            return Result.failure(IllegalStateException("Complete both flight directions before proceeding."))
        }
        val generation = ++quoteRequestGeneration
        val initialInputs = quoteInputs(snapshot)
        var activeInputs = initialInputs
        return runCatching {
            val journey = aviasalesPair(snapshot)
            val providerID = journey.providerItineraryID
            check(generation == quoteRequestGeneration && quoteInputs(_state.value) == initialInputs) {
                "Flight selection changed while preparing the package."
            }
            val prepared = _state.value.copy(
                flightResults = listOf(journey), selectedJourneyId = providerID, selectedOutboundJourneyId = providerID,
                trip = _state.value.trip.copy(flightTripType = FlightTripType.ROUND_TRIP),
                quote = null, packageError = null,
            )
            _state.value = prepared
            val preparedInputs = quoteInputs(prepared)
            activeInputs = preparedInputs
            val quote = packageEngine.packageQuote(prepared) // authoritative server-side fare re-resolution
            check(generation == quoteRequestGeneration && quoteInputs(_state.value) == preparedInputs) {
                "Package selection changed while fetching the quote."
            }
            _state.update { it.copy(quote = quote, packageError = null, acceptedReviewTotal = it.acceptedReviewTotal ?: quote.totalPackagePrice) }
            quote
        }.onFailure { error ->
            if (generation == quoteRequestGeneration && quoteInputs(_state.value) == activeInputs) {
                _state.update { it.copy(quote = null, packageError = error.message) }
            }
        }
    }

    /** Flight First package tier changes must not erase the selected Data API flight.
     * Primary hotels and their room categories are resolved afresh for each tier. */
    fun setFlightFirstHotelTier(tier: PackageTier) {
        _state.update { current ->
            if (current.packageFlightPath != PackageFlightPath.AVIASALES_SELECTED || current.trip.packageTier == tier) current
            else current.copy(
                trip = current.trip.copy(packageTier = tier, hotelStars = tier.primaryHotelStars,
                    mealSelection = if (tier == PackageTier.COMFORT || tier == PackageTier.LUXURY) PackageMealSelection() else null),
                makkahHotel = null, makkahRoom = null, makkahRoomCategory = null,
                madinahHotel = null, madinahRoom = null, madinahRoomCategory = null,
                quote = null, packageError = null, transferSelectionConfirmed = false,
            )
        }
    }

    fun setMealSelection(value: PackageMealSelection) {
        _state.update { current ->
            if (!current.hasSelectableHotelMeals || current.trip.mealSelection == value) current
            else current.copy(trip = current.trip.copy(mealSelection = value), quote = null, packageError = null, transferSelectionConfirmed = false)
        }
    }

    /** Exact counterpart of iOS JourneyStore.applyPackageTierComparison.
     * Keeps the verified flight and transfer choices fixed while replacing only
     * the tier, primary hotels and the already server-authoritative quote. */
    fun applyPackageTierComparison(
        tier: PackageTier,
        comparisonQuote: PackageQuote,
        makkahHotel: HotelSummary,
        madinahHotel: HotelSummary?,
    ) {
        _state.update { current ->
            if (current.trip.scope == JourneyScope.MAKKAH_AND_MADINAH && madinahHotel == null) return@update current
            val updatedTrip = current.trip.copy(
                packageTier = tier,
                hotelStars = tier.primaryHotelStars,
                mealSelection = if (tier == PackageTier.COMFORT || tier == PackageTier.LUXURY) PackageMealSelection() else null,
            )
            current.copy(
                trip = updatedTrip,
                makkahHotel = makkahHotel,
                makkahRoom = null,
                makkahRoomCategory = null,
                madinahHotel = madinahHotel,
                madinahRoom = null,
                madinahRoomCategory = null,
                quote = comparisonQuote,
                packageError = null,
            )
        }
    }

    fun selectHotel(hotel: HotelSummary) {
        _state.update { current ->
            val normalizedCity = hotel.city.trim().lowercase()
            val isMadinah = normalizedCity in setOf(
                "madinah", "medina", "madina", "medinah",
                "al madinah", "al medina", "madinah al munawwarah", "al madinah al munawwarah",
            ) || normalizedCity.contains("madinah") || normalizedCity.contains("medina")
            if (isMadinah) {
                if (current.madinahHotel?.id == hotel.id) current
                else current.copy(
                    madinahHotel = hotel,
                    madinahRoom = null,
                    madinahRoomCategory = null,
                    flightResults = emptyList(),
                    selectedJourneyId = null,
                    selectedOutboundJourneyId = null,
                    nuitee = null, quote = null,
                    packageError = null,
                )
            } else {
                if (current.makkahHotel?.id == hotel.id) current
                else current.copy(
                    makkahHotel = hotel,
                    makkahRoom = null,
                    makkahRoomCategory = null,
                    flightResults = emptyList(),
                    selectedJourneyId = null,
                    selectedOutboundJourneyId = null,
                    nuitee = null, quote = null,
                    packageError = null,
                )
            }
        }
    }

    fun selectRoom(room: HotelRoom?, forMadinah: Boolean) {
        _state.update { current ->
            if (forMadinah) current.copy(madinahRoom = room, madinahRoomCategory = if (room != null) null else current.madinahRoomCategory, quote = null)
            else current.copy(makkahRoom = room, makkahRoomCategory = if (room != null) null else current.makkahRoomCategory, quote = null)
        }
    }

    fun selectRoomCategory(category: IumrahRoomCategoryOption?, forMadinah: Boolean) {
        _state.update { current ->
            if (forMadinah) current.copy(madinahRoomCategory = category, madinahRoom = if (category != null) null else current.madinahRoom, quote = null)
            else current.copy(makkahRoomCategory = category, makkahRoom = if (category != null) null else current.makkahRoom, quote = null)
        }
    }

    fun clearFlights() {
        _state.update { it.copy(flightResults = emptyList(), selectedJourneyId = null, selectedOutboundJourneyId = null, flightError = null, quote = null, transferSelectionConfirmed = false) }
    }

    suspend fun searchFlights(provider: IgnavFlightInventoryProvider) {
        val trip = _state.value.trip
        if (!trip.canContinue) {
            _state.update { it.copy(flightError = "INVALID_TRIP") }
            return
        }
        if (!_state.value.hasRequiredHotels) {
            _state.update { it.copy(flightError = "HOTEL_ROOM_REQUIRED") }
            return
        }
        _state.update { it.copy(isSearchingFlights = true, flightError = null, flightResults = emptyList(), selectedJourneyId = null, selectedOutboundJourneyId = null, quote = null, transferSelectionConfirmed = false) }
        val filters = trip.effectiveFlightFilters
        val request = FlightJourneySearchRequest(
            outboundOrigin = trip.originCode,
            outboundDestination = trip.outboundDestinationCode,
            inboundOrigin = if (trip.isRoundTripFlight) trip.returnOriginCode else null,
            inboundDestination = if (trip.isRoundTripFlight) trip.originCode else null,
            adults = trip.adults,
            children = trip.children,
            infants = trip.infants,
            cabin = filters.cabinClass.wireValue,
            filters = filters,
        )
        val pairs = listOf(FlightJourneyDatePair(trip.departureDate, if (trip.isRoundTripFlight) trip.returnDate else null))
        runCatching {
            provider.searchJourney(request, pairs) { partial ->
                _state.update { it.copy(flightResults = partial) }
            }
        }.onSuccess { values ->
            _state.update { it.copy(flightResults = values, isSearchingFlights = false, flightError = if (values.isEmpty()) "NO_RESULTS" else null) }
        }.onFailure { error ->
            _state.update { it.copy(isSearchingFlights = false, flightError = error.message ?: "SEARCH_FAILED") }
        }
    }

    /** iOS FlightSearchProgressCard parity: refresh inventory without discarding
     * already discovered/selected rows. This is used by both outbound and return
     * "Continue search" actions. */
    suspend fun continueSearchFlights(provider: IgnavFlightInventoryProvider) {
        val snapshot = _state.value
        val trip = snapshot.trip
        if (!trip.canContinue || !snapshot.hasRequiredHotels || snapshot.isSearchingFlights) return
        _state.update { it.copy(isSearchingFlights = true, flightError = null) }
        val filters = trip.effectiveFlightFilters
        val request = FlightJourneySearchRequest(
            outboundOrigin = trip.originCode,
            outboundDestination = trip.outboundDestinationCode,
            inboundOrigin = if (trip.isRoundTripFlight) trip.returnOriginCode else null,
            inboundDestination = if (trip.isRoundTripFlight) trip.originCode else null,
            adults = trip.adults,
            children = trip.children,
            infants = trip.infants,
            cabin = filters.cabinClass.wireValue,
            filters = filters,
        )
        val pairs = listOf(FlightJourneyDatePair(trip.departureDate, if (trip.isRoundTripFlight) trip.returnDate else null))
        fun merge(current: List<LiveFlightJourneyCandidate>, incoming: List<LiveFlightJourneyCandidate>): List<LiveFlightJourneyCandidate> =
            (current + incoming).associateBy { it.id }.values.sortedBy { it.outbound.departureAt }
        runCatching {
            provider.searchJourney(request, pairs) { partial ->
                _state.update { current -> current.copy(flightResults = merge(current.flightResults, partial)) }
            }
        }.onSuccess { values ->
            _state.update { current ->
                val merged = merge(current.flightResults, values)
                current.copy(
                    flightResults = merged,
                    isSearchingFlights = false,
                    flightError = if (merged.isEmpty()) "NO_RESULTS" else null,
                )
            }
        }.onFailure { error ->
            _state.update { current -> current.copy(isSearchingFlights = false, flightError = error.message ?: "SEARCH_FAILED") }
        }
    }

    /** Server-authoritative package-price previews used by the iOS flight cards.
     * Each row is quoted as a complete itinerary; raw component airfare never becomes UI.
     */
    suspend fun packagePricePreviews(
        packageEngine: RemotePackageEngineClient,
        journeyIDs: Collection<String>,
    ): Map<String, java.math.BigDecimal> {
        val snapshot = _state.value
        if (snapshot.makkahHotel == null || (snapshot.trip.scope == JourneyScope.MAKKAH_AND_MADINAH && snapshot.madinahHotel == null)) return emptyMap()
        val unique = journeyIDs.distinct()
        val result = linkedMapOf<String, java.math.BigDecimal>()
        for (id in unique) {
            val candidate = snapshot.flightResults.firstOrNull { it.id == id } ?: continue
            val previewState = snapshot.copy(
                selectedJourneyId = candidate.id,
                selectedOutboundJourneyId = candidate.id,
                quote = null,
                packageError = null,
            )
            runCatching { packageEngine.packageQuote(previewState).pricePerPerson }
                .getOrNull()?.let { result[id] = it }
        }
        return result
    }

    fun selectJourney(id: String) {
        _state.update { current ->
            val journey = current.flightResults.firstOrNull { it.id == id } ?: return@update current
            val arrivalSegment = journey.outbound.segments?.lastOrNull()
            val zone = arrivalSegment?.destination?.timeZoneIdentifier?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("Asia/Riyadh")
            val arrivalDate = journey.outbound.arrivalAt.atZone(zone).toLocalDate()
            current.copy(selectedJourneyId = id, selectedOutboundJourneyId = id, trip = current.trip.copy(saudiArrivalDate = arrivalDate), quote = null, packageError = null, transferSelectionConfirmed = false)
        }
    }


    fun selectOutboundJourney(id: String) {
        _state.update { current ->
            val journey = current.flightResults.firstOrNull { it.id == id } ?: return@update current
            val arrivalSegment = journey.outbound.segments?.lastOrNull()
            val zone = arrivalSegment?.destination?.timeZoneIdentifier?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("Asia/Riyadh")
            val arrivalDate = journey.outbound.arrivalAt.atZone(zone).toLocalDate()
            current.copy(
                selectedOutboundJourneyId = id,
                selectedJourneyId = null,
                trip = current.trip.copy(saudiArrivalDate = arrivalDate),
                quote = null,
                packageError = null,
                transferSelectionConfirmed = false,
            )
        }
    }

    fun selectReturnJourney(id: String) = selectJourney(id)

    fun chooseTransferVehicle(vehicle: TransferVehicleKind) {
        _state.update { current ->
            if (current.selectedTransferVehicle == vehicle) current
            else current.copy(selectedTransferVehicle = vehicle, transferSelectionConfirmed = false, quote = null, packageError = null)
        }
    }

    fun setHaramainTrainSelected(selected: Boolean) {
        _state.update { current ->
            val enabled = current.trip.scope == JourneyScope.MAKKAH_AND_MADINAH && selected
            val adults = if (enabled && current.haramainAdultTickets == 0 && current.haramainChildTickets == 0) maxOf(1, current.trip.adults) else current.haramainAdultTickets
            val children = if (enabled && current.haramainAdultTickets == 0 && current.haramainChildTickets == 0) maxOf(0, current.trip.children) else current.haramainChildTickets
            current.copy(haramainTrainSelected = enabled, haramainAdultTickets = adults, haramainChildTickets = children, transferSelectionConfirmed = false, quote = null, packageError = null)
        }
    }

    fun ensureHaramainTicketDefaults() {
        _state.update { current ->
            if (current.trip.scope != JourneyScope.MAKKAH_AND_MADINAH) current
            else {
                val needsDefaults = current.haramainAdultTickets == 0 && current.haramainChildTickets == 0
                if (!needsDefaults) current
                else current.copy(
                    haramainAdultTickets = maxOf(1, current.trip.adults),
                    haramainChildTickets = maxOf(0, current.trip.children),
                )
            }
        }
    }

    fun setHaramainFareClass(value: HaramainFareClass) {
        _state.update { current ->
            if (current.haramainFareClass == value) current
            else current.copy(haramainFareClass = value, transferSelectionConfirmed = false, quote = null, packageError = null)
        }
    }

    fun setHaramainAdultTickets(value: Int) {
        _state.update { current -> current.copy(haramainAdultTickets = value.coerceIn(1, maxOf(1, current.trip.adults)), transferSelectionConfirmed = false, quote = null) }
    }

    fun setHaramainChildTickets(value: Int) {
        _state.update { current -> current.copy(haramainChildTickets = value.coerceIn(0, maxOf(0, current.trip.children)), transferSelectionConfirmed = false, quote = null) }
    }

    fun confirmTransferSelection() {
        _state.update { current -> current.copy(selectedTransferVehicle = current.selectedTransferVehicle ?: TransferVehicleKind.CARNIVAL, transferSelectionConfirmed = true) }
    }

    fun resetTransferSelection() {
        _state.update { it.copy(selectedTransferVehicle = null, haramainTrainSelected = false, haramainFareClass = HaramainFareClass.ECONOMY, haramainAdultTickets = 0, haramainChildTickets = 0, transferSelectionConfirmed = false, quote = null) }
    }

    /** Only the latest request for the exact priced inputs may publish a transfer quote. */
    suspend fun refreshTransferQuote(generate: suspend (JourneyState) -> PackageQuote): PackageQuote {
        val snapshot = _state.value
        val inputs = quoteInputs(snapshot)
        val generation = ++quoteRequestGeneration
        try {
            val quote = generate(snapshot)
            currentCoroutineContext().ensureActive()
            check(generation == quoteRequestGeneration && quoteInputs(_state.value) == inputs) {
                "PACKAGE_SELECTION_CHANGED"
            }
            check(snapshot.copy(quote = quote).hasFinalGeneratorQuote && quote.bookingAllowed != false &&
                (snapshot.nuitee == null || quote.bookingAllowed == true)) { "PACKAGE_QUOTE_UNAVAILABLE" }
            _state.update { it.copy(quote = quote, packageError = null, acceptedReviewTotal = it.acceptedReviewTotal ?: quote.totalPackagePrice) }
            return quote
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            if (generation == quoteRequestGeneration && quoteInputs(_state.value) == inputs) {
                _state.update { it.copy(quote = null, packageError = error.message) }
            }
            throw error
        }
    }

    fun setQuote(quote: PackageQuote) { _state.update { it.copy(quote = quote, packageError = null, acceptedReviewTotal = it.acceptedReviewTotal ?: quote.totalPackagePrice) } }

    /** Applies a server-owned storefront package without recalculating its public price locally. */
    fun applyStorefrontPackage(
        trip: TripDraft,
        makkahHotel: HotelSummary,
        madinahHotel: HotelSummary?,
        flight: LiveFlightJourneyCandidate,
        quote: PackageQuote,
    ) {
        _state.value = JourneyState(
            trip = trip,
            makkahHotel = makkahHotel,
            madinahHotel = madinahHotel,
            flightResults = listOf(flight),
            selectedJourneyId = flight.id,
            selectedOutboundJourneyId = flight.id,
            quote = quote, acceptedReviewTotal = quote.totalPackagePrice,
            isSearchingFlights = false,
            flightError = null,
            packageError = null,
        )
    }

    fun setPackageError(message: String?) { _state.update { it.copy(packageError = message, quote = if (message != null) null else it.quote) } }
}
