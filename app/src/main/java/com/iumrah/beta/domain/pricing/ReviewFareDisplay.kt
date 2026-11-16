package com.iumrah.beta.domain.pricing

import com.iumrah.beta.domain.trip.FlightFareScope
import com.iumrah.beta.models.flight.FlightDirection
import com.iumrah.beta.models.flight.LiveFlightCandidate
import java.math.BigDecimal
import java.math.MathContext

/** Display only the recorded fare; never split a shared round-trip fare or infer an unknown fare. */
object ReviewFareDisplay {
    data class Price(val amount:BigDecimal,val currency:String,val average:Boolean,val roundTrip:Boolean)

    fun flight(offer:LiveFlightCandidate,outbound:LiveFlightCandidate?,inbound:LiveFlightCandidate?,travelers:Int):Price? {
        val amount=offer.observedFare
        if(amount.signum()<=0 || !offer.observedCurrency.matches(Regex("[A-Z]{3}")) || offer.fareScope==FlightFareScope.UNKNOWN) return null
        var roundTrip=false
        val outId=outbound?.providerItineraryID
        val backId=inbound?.providerItineraryID
        if(outbound!=null && inbound!=null) {
            if(!outId.isNullOrBlank() && outId==backId && !outId.startsWith("aviasales:pair:") && !outId.startsWith("mixed:")) {
                if(outbound.observedFare.compareTo(inbound.observedFare)!=0 || outbound.observedCurrency!=inbound.observedCurrency || outbound.fareScope!=inbound.fareScope || offer.direction!=FlightDirection.inbound) return null
                roundTrip=true
            } else if(outId?.startsWith("aviasales:rt:")==true || backId?.startsWith("aviasales:rt:")==true) return null
        } else if(offer.providerItineraryID?.startsWith("aviasales:rt:")==true) roundTrip=true
        val average=offer.fareScope==FlightFareScope.TOTAL_PARTY
        if(average && travelers<=0) return null
        return Price(if(average) amount.divide(BigDecimal(travelers),MathContext.DECIMAL128) else amount,offer.observedCurrency,average,roundTrip)
    }
}
