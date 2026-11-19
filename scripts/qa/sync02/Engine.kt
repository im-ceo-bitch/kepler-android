package com.iumrah.beta.data.hotel
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.pricing.PackageQuote
import java.math.BigDecimal
import com.iumrah.beta.domain.pricing.TripSummaryPublicPrice
class RemotePackageEngineClient {
    var quote: suspend (JourneyState) -> PackageQuote = { state -> PackageQuote(BigDecimal(2000), BigDecimal(1000), "USD", false, "server-test", "proof", bookingAllowed = true, resolvedTier = state.trip.packageTier.wireValue, tripSummary = testPrice(2000)) }
    suspend fun packageQuote(state: JourneyState) = quote(state)
}

fun testPrice(total: Int): TripSummaryPublicPrice {
    val value=BigDecimal(total); val services=value.multiply(BigDecimal("0.90")).setScale(2); val support=services.multiply(BigDecimal("0.09")).setScale(2); val payment=value.multiply(BigDecimal("0.02")).setScale(2); val rounding=value-services-support-payment
    return TripSummaryPublicPrice("trip-summary-v2","USD",listOf(TripSummaryPublicPrice.Component("hotel","Hotel",services)),services,BigDecimal("0.09"),support,BigDecimal("0.02"),payment,rounding,value,value.divide(BigDecimal(2)))
}
