package com.iumrah.beta.domain.journey

import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.domain.nuitee.*
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.flight.FlightCabinClass
import com.iumrah.beta.models.nuitee.NuiteeManualEstimateRequest
import com.iumrah.beta.ui.flights.GuidedFlightChoice
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Independent component basket; never produces a local bookable quote. */
internal data class ManualJourneyState(
    val plan: TripDraft = TripDraft(departureDate=LocalDate.now().plusDays(14),returnDate=LocalDate.now().plusDays(21)),
    val outbound: GuidedFlightChoice? = null, val inbound: GuidedFlightChoice? = null,
    val rooms: Map<String,NuiteeRoomSelection> = emptyMap(), val vehicle: TransferVehicleKind? = null,
    val train: Boolean = false, val fare: HaramainFareClass = HaramainFareClass.ECONOMY, val tickets:Int = 1,
) {
    val cities get()=if(plan.scope==JourneyScope.MAKKAH_ONLY) listOf("makkah") else listOf("makkah","madinah")
    val hasPriceComponent get()=outbound!=null || inbound!=null || rooms.isNotEmpty()
    val componentCount get()=listOfNotNull(outbound,inbound,vehicle).size+rooms.size
    private fun instant(raw:String?)=raw?.let{value->runCatching{Instant.parse(value)}.getOrElse{runCatching{OffsetDateTime.parse(value).toInstant()}.getOrNull()}}
    private fun day(raw:String?,airport:String)=instant(raw)?.atZone(FlightReferenceCatalog.timeZone(airport)?:ZoneOffset.UTC)?.toLocalDate()
    val trip:TripDraft? get(){
        val a=outbound?:return null;val b=inbound?:return null
        val start=day(a.departureAt,a.origin)?:return null;val arrival=day(a.arrivalAt,a.destination)?:return null;val end=day(b.departureAt,b.origin)?:return null
        if(a.origin!=b.destination || a.destination !in setOf("JED","MED") || b.origin !in setOf("JED","MED") || start<LocalDate.now() || arrival<start || end<=arrival || (plan.scope==JourneyScope.MAKKAH_ONLY && (a.destination!="JED" || b.origin!="JED")) || ChronoUnit.DAYS.between(arrival,end)<cities.size) return null
        return plan.copy(origin=a.origin,originAirport=null,departureDate=start,saudiArrivalDate=arrival,returnDate=end,
            arrivalAirport=airport(a.destination),selectedReturnAirport=airport(b.origin),rooms=1,children=0,infants=0,flightTripType=FlightTripType.ROUND_TRIP)
    }
    val sourceCompatible:Boolean get(){
        val a=outbound?:return false;val b=inbound?:return false;val cabin=plan.effectiveFlightFilters.cabinClass
        if(listOf(a,b).any{it is GuidedFlightChoice.Published && !it.recommendation.cabinClass.replace(' ','_').equals(cabin.wireValue,true)})return false
        return when {
            a is GuidedFlightChoice.Published && b is GuidedFlightChoice.Published -> if(a.publishedPair || b.publishedPair) a.publishedPair && b.publishedPair && a.recommendation.id==b.recommendation.id else a.recommendation.id!=b.recommendation.id
            else -> cabin==FlightCabinClass.ECONOMY && !a.publishedPair && !b.publishedPair
        }
    }
    fun validRoom(city:String):Boolean {
        val t=trip?:plan;val r=rooms[city]?:return false;val w=TripStayPlanner.windows(t).let{if(city=="madinah")it.madinah else it.makkah}?:return false
        return r.adults==t.adults && r.childrenAges.isEmpty() && r.checkin==w.checkIn && r.checkout==w.checkOut && NuiteeRules.validRoomRate(r.rate)
    }
    val complete get()=trip!=null && sourceCompatible && plan.adults in 1..6 && (vehicle?.passengerCapacity?:0)>=plan.adults && cities.all(::validRoom)
    fun estimateRequest():NuiteeManualEstimateRequest? {
        if(!hasPriceComponent)return null
        val t=trip?:plan;val selected=listOfNotNull(outbound,inbound)
        val flights=selected.mapNotNull{f->val d=day(f.departureAt,f.origin)?:return@mapNotNull null;NuiteeManualEstimateRequest.Flight(identity(f),f.origin,f.destination,d.toString(),t.effectiveFlightFilters.cabinClass.wireValue)}
        if(flights.size!=selected.size)return null
        return NuiteeManualEstimateRequest(t.adults,t.scope==JourneyScope.MAKKAH_AND_MADINAH,t.departureDate.toString(),t.returnDate.toString(),vehicle?.wireValue,
            NuiteeManualEstimateRequest.Train(train && vehicle!=null && cities.size==2,fare.wireValue,tickets.coerceIn(1,t.adults)),rooms.values.sortedBy{it.key}.map(NuiteeBookingRules::bookingSelection),flights)
    }
    fun makeQuoteJourney():JourneyStore? {
        if(!complete)return null
        val t=trip?:return null;val a=outbound?:return null;val b=inbound?:return null;val v=vehicle?:return null
        return JourneyStore().also{ j->
            when {
                a is GuidedFlightChoice.Published && b is GuidedFlightChoice.Published -> j.commitTripBuilder(t,PackageFlightPath.PUBLISHED_DIRECT,
                    completeID=a.recommendation.id.takeIf{a.publishedPair},outboundID=a.recommendation.id.takeUnless{a.publishedPair},returnID=b.recommendation.id.takeUnless{a.publishedPair})
                a is GuidedFlightChoice.Data && b is GuidedFlightChoice.Data -> {j.commitTripBuilder(t,PackageFlightPath.AVIASALES_SELECTED);j.stageAviasalesFlight(a.offer);j.stageAviasalesFlight(b.offer,returnLeg=true)}
                else -> {j.commitTripBuilder(t,PackageFlightPath.AVIASALES_SELECTED);j.setMixedFlightIdentity("mixed:${identity(a)}~${identity(b)}")}
            }
            j.startNuiteeJourney(listOf(a,b).map{NuiteeFlightSnapshot(it.id,it.origin,it.destination,it.airlineName,it.flightNumber,it.airlineCode,it.durationMinutes,it.stops,it.departureAt,it.arrivalAt,it.priceUsd)})
            cities.forEach{j.chooseNuiteeRoom(rooms.getValue(it))};j.chooseTransferVehicle(v);j.setHaramainFareClass(fare);j.setHaramainAdultTickets(tickets);j.setHaramainTrainSelected(train);j.confirmTransferSelection()
        }
    }
    fun chooseFlight(f:GuidedFlightChoice, returning:Boolean):ManualJourneyState {
        if(returning){require(f.origin in setOf("JED","MED") && (plan.scope!=JourneyScope.MAKKAH_ONLY || f.origin=="JED"));val d=requireNotNull(day(f.departureAt,f.origin));return copy(inbound=f,plan=plan.copy(origin=outbound?.origin?:f.destination,returnDate=d,selectedReturnAirport=airport(f.origin),departureDate=if(outbound==null && plan.departureDate>=d) d.minusDays(7).coerceAtLeast(LocalDate.now()) else plan.departureDate))}
        require(f.destination in setOf("JED","MED") && (plan.scope!=JourneyScope.MAKKAH_ONLY || f.destination=="JED"));val d=requireNotNull(day(f.departureAt,f.origin));return copy(outbound=f,plan=plan.copy(origin=f.origin,arrivalAirport=airport(f.destination),departureDate=d,saudiArrivalDate=day(f.arrivalAt,f.destination),returnDate=if(plan.returnDate<=d)d.plusDays(7) else plan.returnDate))
    }
    companion object {
        fun airport(code:String)=if(code=="MED")SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH
        fun identity(f:GuidedFlightChoice)=when(f){is GuidedFlightChoice.Published->"curated:${f.recommendation.id}";is GuidedFlightChoice.Data->listOf("aviasales","ow",f.origin,f.destination,f.departureAt.take(10),"-",f.airlineCode.uppercase(),f.flightNumber.uppercase().filter{it.isLetterOrDigit()},"-","-").joinToString(":")}
    }
}

internal class ManualJourneyStore {
    private val _state=MutableStateFlow(ManualJourneyState());val state=_state.asStateFlow()
    fun update(expected:ManualJourneyState, transform:(ManualJourneyState)->ManualJourneyState) { _state.update{if(it==expected)transform(it) else it} }
}
