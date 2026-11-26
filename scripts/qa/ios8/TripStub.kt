package com.iumrah.beta.domain.trip
import java.time.LocalDate
// Minimal shape of TripDraft used only by extracted ranking and compatibility rules.
enum class JourneyScope { MAKKAH_ONLY, MAKKAH_AND_MADINAH }
enum class Cabin(val wireValue:String){ ECONOMY("economy"), BUSINESS("business") }
data class Filters(val cabinClass:Cabin=Cabin.ECONOMY)
data class TripDraft(val originCode:String,val outboundDestinationCode:String,val returnOriginCode:String,val departureDate:LocalDate,val returnDate:LocalDate,val scope:JourneyScope=JourneyScope.MAKKAH_AND_MADINAH,val effectiveFlightFilters:Filters=Filters())
