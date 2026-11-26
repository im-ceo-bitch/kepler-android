package com.iumrah.beta.ui.home
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import java.time.*
fun main(){
 val now=Instant.parse("2027-01-01T00:00:00Z")
 val trip=TripDraft("TAS","MED","JED",LocalDate.parse("2027-01-05"),LocalDate.parse("2027-01-12"))
 fun f(id:String,from:String,to:String,dt:String,cabin:String="economy",price:Double?=100.0)=GlobalPublishedLeg(id,CuratedFlightRecommendation.Leg(airline="Test",flightNumber="T1",airlineCode="HY",origin=from,destination=to,departureAt=dt,arrivalAt=dt,durationMinutes=240,stops=0,cabinClass=cabin),false,price)
 val chosen=f("exact","TAS","MED","2027-01-04T22:30:00Z")
 val near=f("near","TAS","MED","2027-01-03T09:00:00Z")
 val wrong=f("wrong","TAS","JED","2027-01-02T09:00:00Z")
 val noPrice=f("noPrice","TAS","MED","2027-01-05T09:00:00Z",price=null)
 val past=f("past","TAS","MED","2026-12-31T00:00:00Z")
 val back=f("back","JED","TAS","2027-01-12T10:00:00Z")
 val short=f("short","JED","TAS","2027-01-05T10:00:00Z")
 val wrongCabin=f("business","JED","TAS","2027-01-12T10:00:00Z","business")
 val all=listOf(wrong,near,past,short,back,chosen,wrongCabin,noPrice)
 check(prioritizePublished(all,trip,0,null,now).map{it.rowID}==listOf("exact","noPrice","near","wrong"))
 check(prioritizePublished(all,trip,1,chosen,now).map{it.rowID}==listOf("back","short","business"))
 check(prioritizePublished(all,trip,0,back,now).take(2).map{it.rowID}==listOf("exact","noPrice"))
 check(noPrice.selectable()==null)
 check(!past.future(now)); check(chosen.future(now)); check(back.direction()==1); check(chosen.direction()==0)
 check(prioritizePublished(all,trip.copy(outboundDestinationCode="JED"),0,null,now).first().rowID=="wrong")
 println("iOS8 ranking JVM: 9 checks passed (extracted production rules; minimal trip stub)")
}
