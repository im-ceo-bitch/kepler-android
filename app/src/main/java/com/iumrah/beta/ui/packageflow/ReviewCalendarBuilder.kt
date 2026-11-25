package com.iumrah.beta.ui.packageflow

import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.domain.pricing.PackageItineraryPreviewItem
import com.iumrah.beta.domain.nuitee.NuiteeFlightSnapshot
import com.iumrah.beta.data.flight.FlightDataParity
import com.iumrah.beta.models.flight.LiveFlightCandidate
import java.time.LocalDate

/** No estimated event times: use itinerary preview, selected flight timestamps and known stay dates only. */
internal fun buildReviewCalendar(state:JourneyState,quote:PackageQuote?):List<PackageItineraryPreviewItem> {
    fun snapshot(f:LiveFlightCandidate)=NuiteeFlightSnapshot(f.id,f.origin,f.destination,f.airline,f.flightNumber,f.airlineCode.orEmpty(),f.durationMinutes,f.stops,f.departureAt.toString(),f.arrivalAt.toString(),null)
    val flights=state.nuitee?.flights.orEmpty().ifEmpty { listOfNotNull(state.selectedJourney?.outbound,state.selectedJourney?.inbound).map(::snapshot) }
    val outbound=flights.firstOrNull();val inbound=flights.getOrNull(1).takeIf { state.trip.isRoundTripFlight }
    val entries=mutableListOf<PackageItineraryPreviewItem>()
    fun flight(f:NuiteeFlightSnapshot?,arrival:Boolean,kind:String,id:String) {
        if(f==null)return
        val timestamp=if(arrival)f.arrivalAt else f.departureAt
        if(timestamp==null)return
        val airport=if(arrival)f.destination else f.origin
        val day=FlightDataParity.localDay(timestamp,airport) ?: return
        val time=FlightDataParity.localTime(timestamp,airport).takeIf { it.matches(Regex("[0-9]{2}:[0-9]{2}")) }
        entries+=PackageItineraryPreviewItem(id,day.toString(),kind,"", "airplane",airport,timeLocal=time,kind=kind)
    }
    fun stay(id:String,day:String?,city:String,kind:String) {
        if(day!=null && runCatching { LocalDate.parse(day) }.isSuccess) entries+=PackageItineraryPreviewItem(id,day,kind,"","bed.double",city,kind=kind)
    }
    flight(outbound,false,"departure","client-out")
    val preview=quote?.itineraryPreview.orEmpty().filter { runCatching { LocalDate.parse(it.dateLocal) }.isSuccess }
    if(preview.isNotEmpty()) entries+=preview else {
        flight(outbound,true,"arrival","client-arrival")
        quote?.stayPlan?.let { plan ->
            stay("client-m-in",plan.makkahCheckIn,"Makkah","hotelCheckIn")
            stay("client-m-out",plan.makkahCheckOut,"Makkah","hotelCheckOut")
            stay("client-d-in",plan.madinahCheckIn,"Madinah","hotelCheckIn")
            stay("client-d-out",plan.madinahCheckOut,"Madinah","hotelCheckOut")
        } ?: state.nuitee?.rooms?.values?.groupBy { it.city }?.forEach { (city,rooms) ->
            val name=if(city=="madinah")"Madinah" else "Makkah"
            stay("client-$city-in",rooms.minOfOrNull { it.checkin }?.toString(),name,"hotelCheckIn")
            stay("client-$city-out",rooms.maxOfOrNull { it.checkout }?.toString(),name,"hotelCheckOut")
        }
        flight(inbound,false,"flightHome","client-back")
    }
    flight(inbound,true,"arrivalHome","client-home")
    return entries.distinctBy { listOf(it.kind,it.dateLocal,it.timeLocal,it.location,it.title) }.sortedWith(compareBy<PackageItineraryPreviewItem>{it.dateLocal}.thenBy{it.timeLocal ?: "99:99"}.thenBy{it.id})
}
