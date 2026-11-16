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
    enum class Progress { FLIGHTS, HOTELS, TRANSFER, PRICE, READY }
    private suspend fun plan(p: SmartPreferences, source: String): Plan = api.post<Plan, PlanRequest>(
        "/api/package/smart/plan", PlanRequest(p.origin, p.month, p.duration, p.both, p.firstCity, source),
        timeoutSeconds = if (source == "published") 25 else 90).also { check(it.ok) { "SMART_PLAN_UNAVAILABLE" } }

    suspend fun assemble(p: SmartPreferences, progress: (Progress, String?) -> Unit): JourneyStore {
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
        val windows = TripStayPlanner.windows(journey.state.value.trip)
        val stays = listOfNotNull("makkah" to windows.makkah, windows.madinah?.let { "madinah" to it }).sortedBy { it.second.checkIn }
        for ((city, window) in stays) {
            progress(Progress.HOTELS, city)
            selectRooms(p, city, window).forEach(journey::chooseNuiteeRoom)
        }
        currentCoroutineContext().ensureActive()
        progress(Progress.TRANSFER, null)
        journey.chooseTransferVehicle(TransferVehicleKind.CARNIVAL); journey.confirmTransferSelection()
        progress(Progress.PRICE, null)
        journey.prepareNuiteePackage(curated, engine).getOrThrow()
        currentCoroutineContext().ensureActive()
        check(journey.state.value.hasFinalGeneratorQuote && journey.state.value.quote?.bookingAllowed == true) { "SMART_QUOTE_UNAVAILABLE" }
        progress(Progress.READY, null)
        return journey
    }

    private suspend fun selectRooms(p: SmartPreferences, city: String, window: TripStayWindow): List<NuiteeRoomSelection> {
        check(window.checkOut > window.checkIn) { "SMART_ROOM_UNAVAILABLE" }
        val catalog = hotels.hotels(city, window.checkIn, window.checkOut, (0 until p.roomCount).maxOf { p.occupancy(it).first }, smartTimeout = true)
        check(catalog.ok && !catalog.sandbox) { "SMART_HOTEL_UNAVAILABLE" }
        fun hint(h: NuiteeHotel): Double = h.minimumRate?.takeIf { it.currency.equals("USD", true) && it.price.isFinite() && it.price > 0 }?.price ?: Double.POSITIVE_INFINITY
        val candidates = catalog.hotels.filter { SmartRules.starEligible(it.stars, p.category) }.sortedWith(compareBy<NuiteeHotel> { hint(it) }.thenByDescending { it.rating ?: 0.0 }).take(12)
        var consecutiveErrors = 0; var hadError = false
        for (hotel in candidates) {
            currentCoroutineContext().ensureActive()
            try {
                val rooms = mutableListOf<NuiteeRoomSelection>()
                for (i in 0 until p.roomCount) {
                    currentCoroutineContext().ensureActive()
                    val (adults, ages) = p.occupancy(i)
                    val response = hotels.rates(hotel.id, window.checkIn, window.checkOut, adults, ages, smartTimeout = true)
                    if (!response.ok || response.sandbox) break
                    val rate = response.rates.filter(NuiteeRules::validRoomRate).minByOrNull { it.price } ?: break
                    rooms.add(NuiteeRoomSelection("$city:$i", city, i, hotel, rate, window.checkIn, window.checkOut, adults, ages))
                }
                consecutiveErrors = 0
                if (rooms.size == p.roomCount) return rooms
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { hadError = true; if (++consecutiveErrors >= 3) break }
        }
        error(if (hadError || catalog.rateError != null) "SMART_HOTEL_NETWORK_UNAVAILABLE" else "SMART_ROOM_UNAVAILABLE")
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
