package com.iumrah.beta.domain.journey

/** Only used by the isolated ChromeStore JVM harness. Never packaged in the app. */
data class TripStub(val adults: Int = 2)
data class JourneyStubState(val trip: TripStub = TripStub())
class FlowStub<T>(val value: T)
class JourneyStore(adults: Int = 2) {
    val state = FlowStub(JourneyStubState(TripStub(adults)))
}
