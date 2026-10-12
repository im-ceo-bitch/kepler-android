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
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
import java.time.ZoneId
import java.time.Instant
import java.time.OffsetDateTime
import java.math.BigDecimal
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
) {
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
        state.trip, state.packageFlightPath,
        state.makkahHotel?.id, state.makkahRoom?.id, state.makkahRoomCategory?.id,
        state.madinahHotel?.id, state.madinahRoom?.id, state.madinahRoomCategory?.id,
        state.selectedPublishedCompleteID, state.selectedPublishedOutboundID, state.selectedPublishedReturnID,
        state.stagedAviasalesRoundTrip, state.stagedAviasalesOutbound, state.stagedAviasalesReturn,
        state.selectedTransferVehicle, state.haramainTrainSelected,
        state.haramainFareClass, state.haramainAdultTickets, state.haramainChildTickets,
    )

    private val _state = MutableStateFlow(JourneyState())
    val state: StateFlow<JourneyState> = _state

    fun updateTrip(value: TripDraft) {
        _state.update { current ->
            if (current.trip == value) current else JourneyState(
                trip = value,
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
                quote = null, packageError = null, transferSelectionConfirmed = false,
            )
        }
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
            val routeChanged = current.trip.originCode != value.originCode ||
                current.trip.outboundDestinationCode != value.outboundDestinationCode ||
                current.trip.returnOriginCode != value.returnOriginCode ||
                current.trip.scope != value.scope
            val keepPublished = !routeChanged && current.packageFlightPath == path
            current.copy(
                trip = value,
                packageFlightPath = path,
                selectedPublishedCompleteID = completeID ?: if (keepPublished) current.selectedPublishedCompleteID else null,
                selectedPublishedOutboundID = outboundID ?: if (keepPublished) current.selectedPublishedOutboundID else null,
                selectedPublishedReturnID = returnID ?: if (keepPublished) current.selectedPublishedReturnID else null,
                flightResults = emptyList(),
                selectedJourneyId = null,
                selectedOutboundJourneyId = null,
                quote = null,
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
                quote = null,
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
            quote = null,
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
            quote = null, transferSelectionConfirmed = false,
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
            _state.update { it.copy(quote = quote, packageError = null) }
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
                quote = null, packageError = null, transferSelectionConfirmed = false,
            )
        }
    }

    fun clearStagedAviasalesFlights() {
        quoteRequestGeneration++
        _state.update { current ->
            if (current.packageFlightPath == PackageFlightPath.AVIASALES_SELECTED) current.copy(
                stagedAviasalesRoundTrip = null, stagedAviasalesOutbound = null, stagedAviasalesReturn = null,
                flightResults = emptyList(), selectedJourneyId = null, selectedOutboundJourneyId = null,
                quote = null, packageError = null, transferSelectionConfirmed = false,
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

    suspend fun prepareAviasalesSelectedPackage(packageEngine: RemotePackageEngineClient): Result<PackageQuote> {
        val snapshot = _state.value
        if (snapshot.packageFlightPath != PackageFlightPath.AVIASALES_SELECTED || !snapshot.hasCompleteStagedFlightSelection) {
            return Result.failure(IllegalStateException("Complete both flight directions before proceeding."))
        }
        val generation = ++quoteRequestGeneration
        val initialInputs = quoteInputs(snapshot)
        var activeInputs = initialInputs
        return runCatching {
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
            val journey = LiveFlightJourneyCandidate(
                id = providerID, sourceID = "aviasales-data", sourceName = "Aviasales Data",
                totalFare = BigDecimal.valueOf(outbound.price + (if (outbound.isRoundTrip) 0.0 else inbound?.price ?: 0.0)),
                currency = "USD", fareScope = FlightFareScope.PER_PASSENGER, observedAt = at,
                providerItineraryID = providerID, outbound = outCandidate, inbound = reverse,
            )
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
            _state.update { it.copy(quote = quote, packageError = null) }
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
                    quote = null,
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
                    quote = null,
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

    fun setQuote(quote: PackageQuote) { _state.update { it.copy(quote = quote, packageError = null) } }

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
            quote = quote,
            isSearchingFlights = false,
            flightError = null,
            packageError = null,
        )
    }

    fun setPackageError(message: String?) { _state.update { it.copy(packageError = message, quote = if (message != null) null else it.quote) } }
}
