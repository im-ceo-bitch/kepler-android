package com.iumrah.beta.domain.trip
import java.time.LocalDate
import com.iumrah.beta.models.flight.FlightSearchFilters
data class TripDraft(val originCode:String,val outboundDestinationCode:String,val returnOriginCode:String,val departureDate:LocalDate,val returnDate:LocalDate,val effectiveFlightFilters:FlightSearchFilters=FlightSearchFilters())
