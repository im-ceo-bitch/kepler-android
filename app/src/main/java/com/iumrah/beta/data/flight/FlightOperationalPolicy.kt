package com.iumrah.beta.data.flight

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Verified carrier designator + 1–4 digits, not an internal provider ID. */
internal object FlightOperationalPolicy {
    fun normalize(number: String, carrier: String = ""): String? {
        val compact = number.uppercase(Locale.ROOT).trim().filterNot { it.isWhitespace() || it == '-' }
        val full = if (compact.matches(Regex("[0-9]{1,4}[A-Z]?"))) {
            if (carrier.length != 2) return null
            carrier.uppercase(Locale.ROOT) + compact
        } else compact
        if (!full.matches(Regex("[A-Z0-9]{2}[0-9]{1,4}[A-Z]?"))) return null
        return full.take(2) + " " + full.drop(2)
    }
    fun key(number: String, day: String, time: String, origin: String, destination: String): String? {
        val verified = normalize(number) ?: return null
        if (day.length != 10 || time.length != 5 || runCatching { LocalDate.parse(day); LocalTime.parse(time) }.isFailure) return null
        val from = origin.uppercase(Locale.ROOT); val to = destination.uppercase(Locale.ROOT)
        if (!from.matches(Regex("[A-Z]{3}")) || !to.matches(Regex("[A-Z]{3}")) || from == to) return null
        return listOf(verified, day, time, from, to).joinToString("|")
    }
    fun fresh(savedAt: Long, now: Long): Boolean = now >= savedAt && now - savedAt < 300_000L
}
