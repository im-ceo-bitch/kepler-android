package com.iumrah.beta.data.flight

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Aviasales returns observations, not live inventory. Never treat a different
 * route/flight as a refresh of the selected fare. */
object FlightDataParity {
    fun instant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    }

    fun matchesInstant(expected: String, candidate: String): Boolean {
        val left = instant(expected)
        val right = instant(candidate)
        return if (left != null && right != null) abs(left.epochSecond - right.epochSecond) <= 120
        else expected == candidate
    }

    /** Never use the device's timezone for known airport flight times. If the
     * airport is not catalogued, preserve the upstream ISO offset instead. */
    private fun zone(airport: String, iso: String): ZoneId? = FlightReferenceCatalog.timeZone(airport)
        ?: runCatching { OffsetDateTime.parse(iso).offset }.getOrNull()

    fun localDay(iso: String, airport: String): java.time.LocalDate? {
        val parsed = instant(iso)
        val airportZone = zone(airport, iso)
        return if (parsed != null && airportZone != null) parsed.atZone(airportZone).toLocalDate()
        else runCatching { java.time.LocalDate.parse(iso.take(10)) }.getOrNull()
    }

    fun localTime(iso: String, airport: String): String {
        val parsed = instant(iso) ?: return iso.substringAfter('T', "").take(5).ifBlank { "—" }
        val airportZone = zone(airport, iso) ?: return "—"
        return DateTimeFormatter.ofPattern("HH:mm").withZone(airportZone).format(parsed)
    }

    fun localDate(iso: String, airport: String, locale: Locale): String {
        val parsed = instant(iso) ?: return iso.take(10)
        val airportZone = zone(airport, iso) ?: return iso.take(10)
        return DateTimeFormatter.ofPattern("d MMM, EEE", locale).withZone(airportZone).format(parsed)
    }

    fun arrivalTime(departureIso: String, durationMinutes: Int, destination: String): String {
        if (durationMinutes <= 0) return "—"
        val departure = instant(departureIso) ?: return "—"
        val airportZone = zone(destination, departureIso) ?: return "—"
        return DateTimeFormatter.ofPattern("HH:mm").withZone(airportZone)
            .format(departure.plusSeconds(durationMinutes.toLong() * 60))
    }

    fun arrivalDate(departureIso: String, durationMinutes: Int, destination: String, locale: Locale): String {
        if (durationMinutes <= 0) return "—"
        val departure = instant(departureIso) ?: return "—"
        val airportZone = zone(destination, departureIso) ?: return "—"
        return DateTimeFormatter.ofPattern("d MMM, EEE", locale).withZone(airportZone)
            .format(departure.plusSeconds(durationMinutes.toLong() * 60))
    }

    fun canonicalFlight(value: String?, carrier: String?): String {
        val flight = value.orEmpty().uppercase().filter(Char::isLetterOrDigit)
        val airline = carrier.orEmpty().uppercase().filter(Char::isLetterOrDigit)
        return if (airline.isNotBlank() && flight.startsWith(airline)) flight.removePrefix(airline) else flight
    }

    private fun sameKnownFlight(expectedCarrier: String?, expectedNumber: String?,
                                candidateCarrier: String?, candidateNumber: String?): Boolean {
        if (!expectedCarrier.isNullOrBlank() && !expectedCarrier.trim().equals(candidateCarrier?.trim(), ignoreCase = true)) return false
        val expected = canonicalFlight(expectedNumber, expectedCarrier)
        return expected.isBlank() || expected == canonicalFlight(candidateNumber, candidateCarrier)
    }

    fun isSameFare(expected: FlightDiscoveryOffer, candidate: FlightDiscoveryOffer): Boolean {
        if (!expected.origin.equals(candidate.origin, true) || !expected.destination.equals(candidate.destination, true)) return false
        if (!matchesInstant(expected.departureAt, candidate.departureAt)) return false
        if (!sameKnownFlight(expected.airlineCode, expected.flightNumber, candidate.airlineCode, candidate.flightNumber)) return false
        val expectedReturn = expected.returnAt?.takeIf(String::isNotBlank)
        val candidateReturn = candidate.returnAt?.takeIf(String::isNotBlank)
        if (expectedReturn == null && candidateReturn != null) return false
        if (expectedReturn != null && (candidateReturn == null || !matchesInstant(expectedReturn, candidateReturn))) return false
        return sameKnownFlight(expected.returnAirlineCode, expected.returnFlightNumber,
                              candidate.returnAirlineCode, candidate.returnFlightNumber)
    }

    /** Retain richer return-carrier identity when several observations match. */
    fun matchingFare(expected: FlightDiscoveryOffer, observations: List<FlightDiscoveryOffer>): FlightDiscoveryOffer? =
        observations.asSequence().filter { it.price.isFinite() && it.price > 0 && isSameFare(expected, it) }
            .maxWithOrNull(compareBy<FlightDiscoveryOffer> { candidate ->
                var score = 0
                if (!expected.returnAirlineCode.isNullOrBlank() && expected.returnAirlineCode.equals(candidate.returnAirlineCode, true)) score += 2
                if (!expected.returnFlightNumber.isNullOrBlank() && canonicalFlight(expected.returnFlightNumber, expected.returnAirlineCode) == canonicalFlight(candidate.returnFlightNumber, candidate.returnAirlineCode)) score += 4
                if (!candidate.returnAt.isNullOrBlank()) score += 1
                score
            })
}
