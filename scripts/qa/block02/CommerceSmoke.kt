package com.iumrah.beta.ui.flights
import com.iumrah.beta.data.flight.*
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.*
import java.time.LocalDate
fun main() {
 val today=LocalDate.parse("2026-11-25"); val date=today.plusDays(10)
 val days=GuidedFarePolicy.window(listOf(date to 230.0,date to 180.0,date.plusDays(1) to Double.NaN,date.plusDays(2) to 0.0),date,today,today)
 check(days.first().date==today); check(days.size==25); check(days.first{it.date==date}.price==180.0);check(days.count{it.price!=null}==1)
 check(GuidedFarePolicy.window(emptyList(),today.plusDays(30),today,today).size==29)
 check(GuidedFilters(true,"HY").accepts(0,"hy"));check(!GuidedFilters(true).accepts(1,"HY"));check(!GuidedFilters(airline="HY").accepts(0,"SV"))
 check(FlightDataParity.localDay("2026-12-04T22:00:00Z","TAS")==date)
 check(FlightDataParity.localTime("2026-12-04T22:00:00Z","TAS")=="03:00")
 val trip=TripDraft("TAS","JED","MED",date,date.plusDays(7))
 val out=FlightDiscoveryOffer("out","TAS","JED",price=190.0,airlineCode="HY",flightNumber="HY100",departureAt="2026-12-04T22:00:00Z",durationMinutes=300)
 val ret=out.copy(id="back",origin="MED",destination="TAS",departureAt="2026-12-12T08:00:00Z")
 val rows=GuidedFlightRules.options(trip,emptyList(),listOf(out,out.copy(id="wrong",destination="DXB"),out.copy(id="invalid",price=0.0)),emptyList(),false,null,today=today)
 check(rows.size==1);check(rows.first().id=="api:out")
 check(GuidedFlightRules.compatible(rows.first(),GuidedFlightChoice.Data(ret)));check(!GuidedFlightRules.compatible(rows.first(),GuidedFlightChoice.Data(ret.copy(destination="SKD"))))
 check(GuidedFlightRules.options(trip.copy(effectiveFlightFilters=FlightSearchFilters(cabinClass=FlightCabinClass.BUSINESS)),emptyList(),listOf(out),emptyList(),false,null,today=today).isEmpty())
 check(GuidedFlightChoice.Data(out.copy(transfers=1,outboundSegments=listOf(FlightDiscoveryVerifiedSegment("TAS","DXB","2026-12-04T22:00:00Z","2026-12-05T01:00:00Z","HY","HY100")))).arrivalAt==null)
 println("PASS: fare window, price safety, filters, airport local date/time, route matching, cabin, compatible legs and segment validation")
}
