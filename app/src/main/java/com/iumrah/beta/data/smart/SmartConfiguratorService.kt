package com.iumrah.beta.data.smart

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.data.flight.*
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.nuitee.*
import com.iumrah.beta.domain.smart.*
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.flight.*
import com.iumrah.beta.models.nuitee.NuiteeHotel
import java.time.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable

@Serializable internal data class PlanRequest(val origin: String, val month: String, val durationDays: Int, val both: Boolean, val firstCity: String, val source: String)
@Serializable internal data class PublishedPair(val completeID: String? = null, val outboundID: String? = null, val returnID: String? = null, val departureDay: String, val returnDay: String)
@Serializable internal data class DataPair(val outbound: FlightDiscoveryOffer, val inbound: FlightDiscoveryOffer, val departureDay: String, val returnDay: String)
@Serializable internal data class Plan(val ok: Boolean, val published: List<PublishedPair> = emptyList(), val dataPairs: List<DataPair> = emptyList())

/** Isolated assembly: only a completed, server-authorized journey is returned. */
class SmartConfiguratorService(private val curated: CuratedFlightRecommendationService, private val hotels: NuiteeHotelService,
                               private val engine: RemotePackageEngineClient, private val api: APIClient = APIClient()) {
    internal val editHotels get() = hotels
    internal val editCurated get() = curated
    internal val editDiscovery get() = AviasalesFlightDiscoveryService(api)
    enum class Progress { FLIGHTS, HOTELS, TRANSFER, PRICE, READY }
    private suspend fun plan(p: SmartPreferences, source: String): Plan = api.post<Plan, PlanRequest>(
        "/api/package/smart/plan", PlanRequest(p.origin, p.month, p.duration, p.both, p.firstCity, source),
        timeoutSeconds = if (source == "published") 25 else 90).also { check(it.ok) { "SMART_PLAN_UNAVAILABLE" } }

    suspend fun assemble(p: SmartPreferences, preview: (com.iumrah.beta.domain.journey.JourneyState) -> Unit = {}, progress: (Progress, String?) -> Unit): JourneyStore {
        require(p.valid()) { "SMART_INVALID_PREFERENCES" }
        progress(Progress.FLIGHTS, null)
        val journey = JourneyStore()
        var selected = false
        for (pair in plan(p, "published").published) {
            currentCoroutineContext().ensureActive()
            val out = day(pair.departureDay) ?: continue
            val back = day(pair.returnDay) ?: continue
            val trip = p.trip(out, back)
            val selection = CuratedPublishedFlightSelection(pair.completeID, pair.outboundID, pair.returnID)
            if (!selection.isComplete) continue
            val resolved = try { curated.resolvePublishedSelection(trip, selection) }
            catch (e: APIException) {
                val code = when (e) { is APIException.Status -> e.code; is APIException.Server -> e.code; else -> throw e }
                if (code in setOf(400, 404, 409, 422)) continue else throw e
            }
            val inbound = resolved.inbound ?: continue
            val outbound = resolved.outbound
            if (!resolved.isDisplayable() || outbound.stops != 0 || inbound.stops != 0 || !valid(p, snapshot(outbound), snapshot(inbound))) continue
            val flights = listOf(snapshot(outbound), snapshot(inbound))
            if (localDay(flights[0].departureAt, p.origin) != out || localDay(flights[1].departureAt, p.returning) != back) continue
            journey.commitTripBuilder(trip, PackageFlightPath.PUBLISHED_DIRECT, pair.completeID, pair.outboundID, pair.returnID)
            journey.startNuiteeJourney(flights); selected = true; break
        }
        if (!selected) {
            check(p.childAges.isEmpty()) { "SMART_AVIASALES_PARTY_FARE_UNAVAILABLE" }
            for (pair in plan(p, "data").dataPairs) {
                currentCoroutineContext().ensureActive()
                val out = day(pair.departureDay) ?: continue
                val back = day(pair.returnDay) ?: continue
                val first = snapshot(pair.outbound) ?: continue
                val last = snapshot(pair.inbound) ?: continue
                if (!valid(p, first, last) || localDay(first.departureAt, p.origin) != out || localDay(last.departureAt, p.returning) != back) continue
                journey.updateTrip(p.trip(out, back)); journey.clearStagedAviasalesFlights()
                journey.stageAviasalesFlight(pair.outbound); journey.stageAviasalesFlight(pair.inbound, returnLeg = true)
                if (!journey.state.value.hasCompleteStagedFlightSelection || journey.state.value.trip.departureDate != out || journey.state.value.trip.returnDate != back) continue
                journey.startNuiteeJourney(listOf(first, last)); selected = true; break
            }
        }
        check(selected) { "SMART_NO_MATCHING_FLIGHTS" }
        preview(journey.state.value)
        val windows = TripStayPlanner.windows(journey.state.value.trip)
        val stays = listOfNotNull("makkah" to windows.makkah, windows.madinah?.let { "madinah" to it }).sortedBy { it.second.checkIn }
        var selectedRooms: List<NuiteeRoomSelection>? = null
        for (count in p.roomCount..p.fallbackRoomCount) {
            val candidate = mutableListOf<NuiteeRoomSelection>()
            try {
                for ((city, window) in stays) {
                    progress(Progress.HOTELS, city)
                    candidate += selectRooms(p, city, window, count = count)
                }
                selectedRooms = candidate
                journey.setSmartRoomCount(count)
                break
            } catch (e: RoomUnavailable) {
                currentCoroutineContext().ensureActive()
                if (count == p.fallbackRoomCount) throw e
            }
        }
        requireNotNull(selectedRooms).forEach(journey::chooseNuiteeRoom)
        preview(journey.state.value)
        currentCoroutineContext().ensureActive()
        progress(Progress.TRANSFER, null)
        journey.chooseTransferVehicle(TransferVehicleKind.CARNIVAL); journey.confirmTransferSelection()
        preview(journey.state.value)
        progress(Progress.PRICE, null)
        journey.prepareNuiteePackage(curated, engine).getOrThrow()
        currentCoroutineContext().ensureActive()
        check(journey.state.value.hasFinalGeneratorQuote && journey.state.value.quote?.bookingAllowed == true && journey.state.value.quote?.hasVerifiedBreakdown == true) { "SMART_QUOTE_UNAVAILABLE" }
        progress(Progress.READY, null)
        return journey
    }

    data class EditResult(val journey: JourneyStore, val hotelsChanged: Boolean)

    // The editor never publishes to the previous store. A new server quote is the commit point.
    suspend fun applyEdit(p: SmartPreferences, previous: JourneyStore, candidate: JourneyStore, flightsChanged: Boolean): EditResult {
        require(previous !== candidate)
        val old = previous.state.value
        val next = candidate.state.value
        require(old.hasFinalGeneratorQuote && old.quote?.bookingAllowed == true)
        require(next.trip.adults == old.trip.adults && next.trip.children == old.trip.children &&
            next.trip.infants == old.trip.infants && next.trip.rooms == old.trip.rooms && next.trip.scope == old.trip.scope)
        require(p.valid() && next.trip.rooms in p.roomCount..p.adults)
        var changed = false
        if (flightsChanged) {
            require(next.nuitee?.flights?.size == 2)
            if (p.childAges.isNotEmpty()) require(next.packageFlightPath == PackageFlightPath.PUBLISHED_DIRECT)
            candidate.chooseTransferVehicle(old.resolvedTransferVehicle)
            candidate.setHaramainTrainSelected(old.haramainTrainSelected)
            candidate.setHaramainFareClass(old.haramainFareClass)
            if (old.haramainTrainSelected) {
                candidate.setHaramainAdultTickets(old.haramainAdultTickets)
                candidate.setHaramainChildTickets(old.haramainChildTickets)
            }
            val windows = TripStayPlanner.windows(next.trip)
            for ((city, window) in listOfNotNull("makkah" to windows.makkah, windows.madinah?.let { "madinah" to it })) {
                val prior = old.nuitee?.rooms.orEmpty().values.filter { it.city == city }.sortedBy { it.roomIndex }
                val refreshed = if (prior.size == old.trip.rooms && prior.all { it.checkin == window.checkIn && it.checkout == window.checkOut }) prior
                    else refreshPreviousRooms(prior, window, old.trip.rooms)
                val rooms = refreshed ?: run {
                    val category = when (prior.firstOrNull()?.hotel?.roundedStars) {
                        1, 2 -> PackageTier.ECONOMY; 3 -> PackageTier.STANDARD; 4 -> PackageTier.COMFORT; 5 -> PackageTier.LUXURY; else -> p.category
                    }
                    val replacement = selectRooms(p.copy(tier = category.wireValue), city, window, count = next.trip.rooms)
                    changed = changed || replacement.firstOrNull()?.hotel?.id != prior.firstOrNull()?.hotel?.id
                    replacement
                }
                rooms.forEach(candidate::chooseNuiteeRoom)
            }
        }
        currentCoroutineContext().ensureActive()
        candidate.confirmTransferSelection()
        candidate.prepareNuiteePackage(curated, engine).getOrThrow()
        currentCoroutineContext().ensureActive()
        check(candidate.state.value.hasFinalGeneratorQuote && candidate.state.value.quote?.bookingAllowed == true && candidate.state.value.quote?.hasVerifiedBreakdown == true) { "SMART_QUOTE_UNAVAILABLE" }
        return EditResult(candidate, changed)
    }

    private suspend fun refreshPreviousRooms(old: List<NuiteeRoomSelection>, window: TripStayWindow, count: Int): List<NuiteeRoomSelection>? {
        if (old.size != count || window.checkOut <= window.checkIn) return null
        val result = mutableListOf<NuiteeRoomSelection>()
        for (room in old) {
            currentCoroutineContext().ensureActive()
            val response = hotels.rates(room.hotel.id, window.checkIn, window.checkOut, room.adults, room.childrenAges, smartTimeout = true)
            if (!response.ok || response.sandbox) return null
            val rate = response.rates.filter { it.roomId == room.rate.roomId && NuiteeRules.validRoomRate(it) }.minByOrNull { it.price } ?: return null
            result += room.copy(rate = rate, checkin = window.checkIn, checkout = window.checkOut)
        }
        return result
    }

    /** Preserve the previous verified result until an independent replacement is fully quoted. */
    suspend fun replaceHotel(p: SmartPreferences, previous: JourneyStore, city: String, excluding: Set<String>): JourneyStore {
        val old = previous.state.value
        val current = requireNotNull(old.nuitee?.rooms?.get("$city:0"))
        require(old.hasFinalGeneratorQuote && old.quote?.bookingAllowed == true && old.trip.rooms in p.roomCount..p.adults)
        val rooms = selectRooms(p, city, TripStayWindow(current.checkin, current.checkout), excluding + current.hotel.id, count = old.trip.rooms)
        currentCoroutineContext().ensureActive()
        val next = JourneyStore()
        next.adoptSmartResult(previous)
        rooms.forEach(next::chooseNuiteeRoom)
        next.confirmTransferSelection()
        next.prepareNuiteePackage(curated, engine).getOrThrow()
        currentCoroutineContext().ensureActive()
        check(next.state.value.hasFinalGeneratorQuote && next.state.value.quote?.bookingAllowed == true && next.state.value.quote?.hasVerifiedBreakdown == true &&
            next.state.value.nuitee?.rooms?.get("$city:0")?.hotel?.id != current.hotel.id) { "SMART_QUOTE_UNAVAILABLE" }
        return next
    }

    private class RoomUnavailable : IllegalStateException("SMART_ROOM_UNAVAILABLE")
    private fun rateLimited(e: Exception): Boolean = when (e) {
        is APIException.Status -> e.code == 429
        is APIException.Server -> e.code == 429
        else -> false
    }

    private suspend fun selectRooms(p: SmartPreferences, city: String, window: TripStayWindow,
                                    excluding: Set<String> = emptySet(), count: Int = p.roomCount): List<NuiteeRoomSelection> {
        if (window.checkOut <= window.checkIn || count !in 1..p.adults) throw RoomUnavailable()
        val occupancy = (0 until count).map { p.occupancy(it, count) }
        val largest = occupancy.maxBy { it.first + it.second.size }
        val catalog = hotels.hotels(city, window.checkIn, window.checkOut, largest.first, catalogOnly = true)
        check(catalog.ok && !catalog.sandbox) { "SMART_HOTEL_NETWORK_UNAVAILABLE" }
        fun hint(h: NuiteeHotel): Double = h.minimumRate?.takeIf { it.currency.equals("USD", true) && it.price.isFinite() && it.price > 0 }?.price ?: Double.POSITIVE_INFINITY
        val eligible = catalog.hotels.distinctBy { it.id }.filter { it.id !in excluding &&
            (if (it.roundedStars == 0) 3 else it.roundedStars) in p.category.selectableHotelStars }
        var consecutiveErrors = 0; var hadError = false
        for (batch in eligible.chunked(40)) {
            currentCoroutineContext().ensureActive()
            var candidates = batch.map { it.copy(minimumRate = null, rateStatus = null) }
            try {
                val page = hotels.pricePage(city, window.checkIn, window.checkOut, largest.first, batch.map { it.id }, largest.second)
                if (page.rateError == "NUITEE_RATE_LIMIT") error("SMART_HOTEL_RATE_LIMIT")
                if (page.rateError != null) hadError = true
                val rows = page.hotels.associateBy { it.id }
                candidates = candidates.map { hotel -> hotel.copy(minimumRate = rows[hotel.id]?.minimumRate, rateStatus = rows[hotel.id]?.rateStatus) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (rateLimited(e) || e.message == "SMART_HOTEL_RATE_LIMIT") error("SMART_HOTEL_NETWORK_UNAVAILABLE")
                hadError = true
            }
            for (hotel in candidates.sortedWith(compareBy<NuiteeHotel> { hint(it) }.thenByDescending { it.rating ?: 0.0 })) {
                currentCoroutineContext().ensureActive()
                if (hotel.rateStatus == "no_availability") continue
                try {
                    val rooms = mutableListOf<NuiteeRoomSelection>()
                    val rateCache = mutableMapOf<Pair<Int, List<Int>>, com.iumrah.beta.models.nuitee.NuiteeRatesEnvelope>()
                    for (i in 0 until count) {
                        currentCoroutineContext().ensureActive()
                        val (adults, ages) = occupancy[i]
                        val response = rateCache[occupancy[i]] ?: hotels.rates(hotel.id, window.checkIn, window.checkOut, adults, ages, smartTimeout = true).also { rateCache[occupancy[i]] = it }
                        if (!response.ok || response.sandbox || response.checkin != window.checkIn.toString() || response.checkout != window.checkOut.toString() || response.adults != adults) break
                        val rate = response.rates.filter(NuiteeRules::validRoomRate).minByOrNull { it.price } ?: break
                        rooms += NuiteeRoomSelection("$city:$i", city, i, hotel, rate, window.checkIn, window.checkOut, adults, ages)
                    }
                    consecutiveErrors = 0
                    if (rooms.size == count) return rooms
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { hadError = true; if (rateLimited(e) || ++consecutiveErrors >= 3) error("SMART_HOTEL_NETWORK_UNAVAILABLE") }
            }
        }
        if (hadError || catalog.rateError != null) error("SMART_HOTEL_NETWORK_UNAVAILABLE")
        throw RoomUnavailable()
    }
    private fun valid(p: SmartPreferences, out: NuiteeFlightSnapshot, back: NuiteeFlightSnapshot): Boolean {
        val departure = instant(out.departureAt) ?: return false
        val arrival = instant(out.arrivalAt) ?: return false
        val returned = instant(back.departureAt) ?: return false
        val end = instant(back.arrivalAt) ?: return false
        return out.origin == p.origin && out.destination == p.arrival && back.origin == p.returning && back.destination == p.origin &&
            departure > Instant.now() && arrival > departure && returned > arrival && end > returned &&
            p.matchesDays(localDay(out.departureAt, out.origin) ?: return false, localDay(out.arrivalAt, out.destination) ?: return false, localDay(back.departureAt, back.origin) ?: return false)
    }
    private fun snapshot(f: LiveFlightCandidate) = NuiteeFlightSnapshot(f.id, f.origin, f.destination, f.airline, f.flightNumber, f.airlineCode.orEmpty(), f.durationMinutes, f.stops, f.departureAt.toString(), f.arrivalAt.toString(), null)
    private fun snapshot(f: FlightDiscoveryOffer): NuiteeFlightSnapshot? {
        if (f.isRoundTrip || !f.price.isFinite() || f.price <= 0 || f.durationMinutes <= 0 || f.transfers < 0 || f.airlineCode.isBlank() || f.flightNumber.isBlank()) return null
        val departure = instant(f.departureAt) ?: return null
        val segments = FlightDiscoveryItineraryValidator.verified(f.outboundSegments, f.origin, f.destination, f.transfers)
        if (f.transfers > 0 && segments == null) return null
        val arrival = instant(segments?.lastOrNull()?.arrivalAt ?: f.arrivalAt) ?: departure.plusSeconds(f.durationMinutes.toLong() * 60)
        return NuiteeFlightSnapshot(f.id, f.origin, f.destination, f.airlineCode, f.flightNumber, f.airlineCode, f.durationMinutes, f.transfers, departure.toString(), arrival.toString(), f.price)
    }
    private fun day(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()
    private fun instant(s: String?) = s?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    private fun localDay(s: String?, code: String) = instant(s)?.atZone(FlightReferenceCatalog.timeZone(code) ?: ZoneOffset.UTC)?.toLocalDate()
}
