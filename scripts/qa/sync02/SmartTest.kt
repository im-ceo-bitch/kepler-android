import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.data.flight.*
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.data.smart.*
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.smart.*
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.flight.*
import com.iumrah.beta.models.nuitee.*
import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
fun main() = runBlocking {
    val day = LocalDate.now().plusDays(10); val back = day.plusDays(6)
    val p = SmartPreferences(month = YearMonth.from(day).toString(), adults = 3, childAges = listOf(7, 3))
    var checks = 0
    fun verify(value: Boolean) { check(value) { "Check ${checks+1} failed" }; checks++ }
    verify(p.valid()); verify(p.roomCount == 3); verify((0 until p.roomCount).sumOf { p.occupancy(it).first } == 3)
    verify((0 until p.roomCount).flatMap { p.occupancy(it).second } == listOf(7,3))
    verify(!p.copy(childAges=listOf(0,0,0,0)).valid()); verify(!p.copy(origin="JED").valid())
    verify(SmartRules.steps(false) == listOf(0,1,2,3,4,6,7,8)); verify(SmartRules.owner(true,null)==null)
    verify(SmartRules.owner(true,"111")!=SmartRules.owner(true,"222")); verify(SmartRules.resume(SmartDraft(p.copy(month="2020-01"),3))==null)
    verify(SmartRules.starEligible(3.0, PackageTier.STANDARD)); verify(!SmartRules.starEligible(4.0, PackageTier.STANDARD))
    val api = APIClient(); val curated = CuratedFlightRecommendationService(); val hotels = NuiteeHotelService(); val engine = RemotePackageEngineClient()
    fun leg(out: Boolean, departure: LocalDate, origin: String, dest: String): LiveFlightCandidate {
        val from = departure.atTime(10,0).toInstant(ZoneOffset.UTC); val to = from.plusSeconds(4*3600)
        val id = if (out) "out" else "back"
        return LiveFlightCandidate(id,"published","Published",if(out) FlightDirection.outbound else FlightDirection.inbound,"Airline","HY123",origin,dest,from,to,0,240,BigDecimal(500),"USD",FlightFareScope.PER_PASSENGER,Instant.now(),airlineCode="HY",segments=listOf(FlightSegment(id,"Airline","HY","HY123",FlightAirportSnapshot(origin),FlightAirportSnapshot(dest),from,to,240)))
    }
    val pair = LiveFlightJourneyCandidate("pair","published","Published",BigDecimal(1000),"USD",FlightFareScope.PER_PASSENGER,Instant.now(),"pair",leg(true,day,"TAS","MED"),leg(false,back,"JED","TAS"))
    val plan = Plan(true,listOf(PublishedPair(completeID="pair",departureDay=day.toString(),returnDay=back.toString())))
    val requests = mutableListOf<String>(); var rateCalls = 0
    api.reply = { path, body, timeout -> val r = body as PlanRequest; check(path=="/api/package/smart/plan"); check(timeout==if(r.source=="published")25L else 90L); requests.add(r.source); plan }
    curated.resolver = { _,_ -> pair }
    hotels.catalog = { city,_,_,_ -> NuiteeHotelListEnvelope(ok=true,hotels=listOf(NuiteeHotel("bad","Wrong stars",city,stars=5.0),NuiteeHotel(city,"Hotel",city,stars=3.0))) }
    val rate = NuiteeRoomRate("offer","room","Double",price=250.0)
    hotels.rate = { _,_,_,_,_ -> rateCalls++; NuiteeRatesEnvelope(ok=true,rates=listOf(rate.copy(price=0.0),rate)) }
    val service = SmartConfiguratorService(curated,hotels,engine,api); val progress = mutableListOf<SmartConfiguratorService.Progress>()
    val manual = JourneyStore(); val original = manual.state.value
    val result = service.assemble(p) { stage,_ -> progress.add(stage) }
    verify(requests == listOf("published")); verify(rateCalls==6); verify(result.state.value.hasFinalGeneratorQuote)
    verify(result.state.value.nuitee?.rooms?.size==6); verify(result.state.value.quote?.bookingAllowed==true)
    verify(progress.first()==SmartConfiguratorService.Progress.FLIGHTS && progress.last()==SmartConfiguratorService.Progress.READY)
    verify(manual.state.value==original); verify(runCatching { manual.adoptSmartResult(JourneyStore()) }.isFailure)
    manual.adoptSmartResult(result); verify(manual.state.value==result.state.value)
    // Block 03C: changed room/meal input invalidates the quote, stale sheet callbacks are ignored.
    val mealRoom = manual.state.value.nuitee!!.rooms.getValue("makkah:0")
    val changedMeals = NuiteeMealChoices(breakfast=true,lunch=true,dinner=true)
    manual.updateNuiteeRoomMeals(mealRoom, changedMeals)
    verify(manual.state.value.quote == null)
    verify(manual.state.value.nuitee!!.rooms.getValue(mealRoom.key).meals == changedMeals)
    verify(manual.state.value.transferSelectionConfirmed)
    val editedState=manual.state.value
    manual.updateNuiteeRoomMeals(mealRoom, NuiteeMealChoices())
    verify(manual.state.value==editedState)
    manual.replaceNuiteeRoom(mealRoom,mealRoom.copy(rate=mealRoom.rate.copy(roomId="stale")))
    verify(manual.state.value==editedState)
    val currentRoom=editedState.nuitee!!.rooms.getValue(mealRoom.key)
    manual.replaceNuiteeRoom(currentRoom,currentRoom.copy(rate=currentRoom.rate.copy(roomId="new-room")))
    verify(manual.state.value.nuitee!!.rooms.getValue(mealRoom.key).rate.roomId=="new-room")
    verify(manual.state.value.quote==null && manual.state.value.transferSelectionConfirmed)
    val fare=com.iumrah.beta.domain.pricing.ReviewFareDisplay
    val out=pair.outbound.copy(providerItineraryID="shared",observedFare=BigDecimal(600),fareScope=FlightFareScope.TOTAL_PARTY)
    val ret=requireNotNull(pair.inbound).copy(providerItineraryID="shared",observedFare=BigDecimal(600),fareScope=FlightFareScope.TOTAL_PARTY)
    verify(fare.flight(out,out,ret,2)==null)
    verify(fare.flight(ret,out,ret,2)?.let { it.roundTrip && it.average && it.amount.compareTo(BigDecimal(300))==0 }==true)
    verify(fare.flight(ret,out,ret.copy(observedFare=BigDecimal(500)),2)==null)
    verify(fare.flight(ret.copy(fareScope=FlightFareScope.UNKNOWN),null,null,2)==null)
    verify(fare.flight(out,null,null,0)==null)
    verify(fare.flight(out.copy(providerItineraryID="aviasales:pair:1"),out.copy(providerItineraryID="aviasales:pair:1"),ret.copy(providerItineraryID="aviasales:pair:1"),2)?.roundTrip==false)
    manual.adoptSmartResult(result)
    val priorRooms = manual.state.value.nuitee!!.rooms
    val hotelSwitch = priorRooms.getValue("makkah:0")
    manual.chooseNuiteeRoom(hotelSwitch.copy(hotel=hotelSwitch.hotel.copy(id="another-hotel")))
    verify(manual.state.value.nuitee!!.rooms.filterKeys { it.startsWith("makkah:") }.keys == setOf("makkah:0"))
    verify(manual.state.value.nuitee!!.rooms.filterKeys { it.startsWith("madinah:") } == priorRooms.filterKeys { it.startsWith("madinah:") })
    verify(manual.state.value.quote == null && !manual.state.value.transferSelectionConfirmed)
    manual.adoptSmartResult(result)
    val previousTrip = manual.state.value.trip
    manual.startHomePublishedSearch(previousTrip)
    verify(manual.state.value.trip == previousTrip.copy(saudiArrivalDate=null, flightTripType=FlightTripType.ROUND_TRIP, flexibility=DateFlexibility.EXACT))
    verify(manual.state.value.packageFlightPath == PackageFlightPath.PUBLISHED_DIRECT)
    verify(manual.state.value.nuitee == null && manual.state.value.quote == null)
    verify(manual.state.value.selectedPublishedCompleteID == null && manual.state.value.selectedPublishedOutboundID == null && manual.state.value.selectedPublishedReturnID == null)
    verify(manual.state.value.stagedAviasalesRoundTrip == null && manual.state.value.stagedAviasalesOutbound == null && manual.state.value.stagedAviasalesReturn == null)
    verify(!manual.state.value.transferSelectionConfirmed && manual.state.value.flightResults.isEmpty())
    verify(result.state.value.hasFinalGeneratorQuote)
    // Sync 03: replacement must be atomic and retain the exact previously priced journey on failure.
    val previousSnapshot = result.state.value
    val previousHotel = previousSnapshot.nuitee!!.rooms.getValue("makkah:0").hotel.id
    hotels.catalog = { city,_,_,_ -> NuiteeHotelListEnvelope(ok=true,hotels=listOf(NuiteeHotel(city,"Old",city,stars=3.0),NuiteeHotel("new-$city","Replacement",city,stars=3.0))) }
    val replacement = service.replaceHotel(p,result,"makkah",emptySet())
    verify(result.state.value == previousSnapshot)
    verify(replacement.state.value.nuitee!!.rooms.filterKeys { it.startsWith("makkah:") }.values.all { it.hotel.id == "new-makkah" })
    verify(replacement.state.value.nuitee!!.rooms.filterKeys { it.startsWith("madinah:") } == previousSnapshot.nuitee!!.rooms.filterKeys { it.startsWith("madinah:") })
    verify(replacement.state.value.nuitee!!.flights == previousSnapshot.nuitee!!.flights)
    verify(replacement.state.value.hasFinalGeneratorQuote)
    verify(runCatching { service.replaceHotel(p,result,"makkah",setOf("new-makkah",previousHotel)) }.isFailure)
    verify(result.state.value == previousSnapshot)
    engine.quote = { _ -> error("quote failure") }
    verify(runCatching { service.replaceHotel(p,result,"makkah",emptySet()) }.isFailure)
    verify(result.state.value == previousSnapshot)
    engine.quote = { state -> com.iumrah.beta.domain.pricing.PackageQuote(BigDecimal(100), BigDecimal(50), "USD",false,"server-test","proof",bookingAllowed=true,resolvedTier=state.trip.packageTier.wireValue) }
    val previews = mutableListOf<com.iumrah.beta.domain.journey.JourneyState>()
    service.assemble(p,preview={ previews.add(it) }) { _,_-> }
    verify(previews.first().nuitee!!.flights.size==2 && previews.first().nuitee!!.rooms.isEmpty())
    verify(previews.last().nuitee!!.rooms.size==6 && previews.last().transferSelectionConfirmed)
    verify(previews.all { it.quote == null })
    engine.quote = { state -> com.iumrah.beta.domain.pricing.PackageQuote(BigDecimal(100), BigDecimal(50), "USD",false,"server-test",null,bookingAllowed=true,resolvedTier=state.trip.packageTier.wireValue) }
    verify(runCatching { service.assemble(p) { _,_-> } }.isFailure)
    engine.quote = { state -> com.iumrah.beta.domain.pricing.PackageQuote(BigDecimal(100), BigDecimal(50), "USD",false,"server-test","proof",bookingAllowed=true,resolvedTier=state.trip.packageTier.wireValue) }
    hotels.catalog = { city,_,_,_ -> NuiteeHotelListEnvelope(ok=true,sandbox=true,hotels=listOf(NuiteeHotel(city,"Hotel",city,stars=3.0))) }
    verify(runCatching { service.assemble(p) { _,_-> } }.isFailure)
    hotels.catalog = { city,_,_,_ -> NuiteeHotelListEnvelope(ok=true,hotels=(1..20).map{ NuiteeHotel("$city$it","Hotel",city,stars=3.0) }) }
    rateCalls=0; hotels.rate = { _,_,_,_,_ -> rateCalls++; NuiteeRatesEnvelope(ok=true,rates=listOf(rate.copy(price=0.0))) }
    verify(runCatching { service.assemble(p) { _,_-> } }.isFailure); verify(rateCalls==12)
    rateCalls=0; hotels.rate = { _,_,_,_,_ -> rateCalls++; error("network") }
    verify(runCatching { service.assemble(p) { _,_-> } }.isFailure); verify(rateCalls==3)
    hotels.rate = { _,_,_,_,_ -> throw CancellationException("cancel") }
    verify(runCatching { service.assemble(p) { _,_-> } }.exceptionOrNull() is CancellationException)
    requests.clear(); api.reply = { _,body,_-> requests.add((body as PlanRequest).source); Plan(true) }
    verify(runCatching { service.assemble(p) { _,_-> } }.isFailure); verify(requests==listOf("published"))
    requests.clear(); verify(runCatching { service.assemble(p.copy(childAges=emptyList())) { _,_-> } }.isFailure); verify(requests==listOf("published","data"))
    hotels.catalog = { city,_,_,_ -> NuiteeHotelListEnvelope(ok=true,hotels=listOf(NuiteeHotel(city,"Hotel",city,stars=3.0))) }
    hotels.rate = { _,_,_,_,_ -> NuiteeRatesEnvelope(ok=true,rates=listOf(rate)) }
    fun dataOffer(leg: LiveFlightCandidate) = FlightDiscoveryOffer(leg.id,leg.origin,leg.destination,price=500.0,airlineCode="HY",flightNumber="HY123",departureAt=leg.departureAt.toString(),durationMinutes=240,arrivalAt=leg.arrivalAt.toString())
    val dataPair = DataPair(dataOffer(pair.outbound),dataOffer(requireNotNull(pair.inbound)),day.toString(),back.toString())
    requests.clear(); api.reply = { _,body,_-> val source=(body as PlanRequest).source; requests.add(source); if(source=="published") Plan(true) else Plan(true,dataPairs=listOf(dataPair)) }
    val fallback = service.assemble(p.copy(childAges=emptyList())) { _,_-> }
    verify(requests==listOf("published","data")); verify(fallback.state.value.packageFlightPath==PackageFlightPath.AVIASALES_SELECTED)
    verify(fallback.state.value.hasFinalGeneratorQuote); verify(fallback.state.value.nuitee?.rooms?.size==4)
    api.reply = { _,body,_-> if((body as PlanRequest).source=="published") Plan(true) else Plan(true,dataPairs=listOf(dataPair.copy(outbound=dataPair.outbound.copy(transfers=1)))) }
    verify(runCatching { service.assemble(p.copy(childAges=emptyList())) { _,_-> } }.isFailure)
    verify(SmartRules.resume(SmartDraft(p.copy(adults=1,childAges=listOf(0,0)),8))?.step==6)
    val cacheService = NuiteeHotelService()
    val envelope = NuiteeHotelListEnvelope(ok=true, hotels=listOf(NuiteeHotel("cached","Cached","makkah")))
    val publicCache = com.iumrah.beta.data.smart.HomePublicContentCache
    cacheService.cached = envelope
    verify(publicCache.hotels(cacheService,"makkah",day,7,2)==envelope)
    cacheService.cached = null
    verify(publicCache.hotels(cacheService,"makkah",day,7,2)==envelope)
    cacheService.cachedCatalog = envelope
    verify(publicCache.hotels(cacheService,"makkah",day.plusDays(1),7,2)==envelope)
    cacheService.cachedCatalog = null
    var publicFetches=0
    cacheService.catalog = { _,_,_,_ -> publicFetches++; envelope }
    verify(publicCache.hotels(cacheService,"makkah",day.plusDays(2),7,2)==envelope)
    verify(publicCache.hotels(cacheService,"makkah",day.plusDays(2),7,2)==envelope && publicFetches==1)
    println("Smart Sync 02–03: $checks JVM assertions passed (actual service + JourneyStore, mocked network).")
}
