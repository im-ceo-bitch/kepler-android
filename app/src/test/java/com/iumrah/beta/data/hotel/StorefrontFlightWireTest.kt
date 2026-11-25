package com.iumrah.beta.data.hotel

import com.iumrah.beta.models.hotel.StorefrontFlightLeg
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StorefrontFlightWireTest {
    @Test fun `snapshot camel case and published board snake case decode equally`() {
        val camel = """{"airline":"Uzbekistan Airways","flightNumber":"HY 1","airlineCode":"HY","origin":"TAS","destination":"JED","departureAt":"2026-11-25T08:00:00+05:00","arrivalAt":"2026-11-25T11:00:00+03:00","durationMinutes":300,"stops":0,"cabinClass":"economy"}"""
        val snake = camel.replace("flightNumber", "flight_number").replace("airlineCode", "airline_code").replace("departureAt", "departure_at").replace("arrivalAt", "arrival_at").replace("durationMinutes", "duration_minutes").replace("cabinClass", "cabin_class")
        val a = Json.decodeFromString<StorefrontFlightLeg>(camel)
        val b = Json.decodeFromString<StorefrontFlightLeg>(snake)
        assertEquals("HY 1", a.flightNumber)
        assertEquals(a, b)
    }
}
