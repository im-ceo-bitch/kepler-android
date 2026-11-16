package com.iumrah.beta.data.hotel
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.pricing.PackageQuote
import java.math.BigDecimal
class RemotePackageEngineClient {
    var quote: suspend (JourneyState) -> PackageQuote = { state -> PackageQuote(BigDecimal(2000), BigDecimal(1000), "USD", false, "server-test", "proof", bookingAllowed = true, resolvedTier = state.trip.packageTier.wireValue) }
    suspend fun packageQuote(state: JourneyState) = quote(state)
}
