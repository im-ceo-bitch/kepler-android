// Pure Kotlin regression harness stand-in. Actual API DTO remains unchanged in app/src.
package com.iumrah.beta.data.flight

data class Stage10Segment(val arrivalAt: String)
data class FlightDiscoveryOffer(
    val id: String,
    val origin: String,
    val destination: String,
    val price: Double,
    val airlineCode: String = "HY",
    val flightNumber: String = "HY100",
    val departureAt: String,
    val transfers: Int = 0,
    val durationMinutes: Int = 210,
    val returnAt: String? = null,
    val outboundSegments: List<Stage10Segment>? = null,
    val arrivalAt: String? = null,
) {
    val isRoundTrip: Boolean get() = !returnAt.isNullOrBlank()
}
