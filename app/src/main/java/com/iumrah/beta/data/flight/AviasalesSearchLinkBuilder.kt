package com.iumrah.beta.data.flight

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Port of the iOS AviasalesSearchLinkBuilder (2026-10-09).
 * A cached Data API offer is NEVER a confirmed ticket. We search afresh for
 * the selected airport/date/passenger combination rather than reuse bookingUrl.
 * This raw search URL does not imply affiliate tracking or a guaranteed fare.
 */
object AviasalesSearchLinkBuilder {
    fun searchUrl(
        origin: String, destination: String, departureAt: String, returnAt: String?,
        adults: Int, children: Int, infants: Int,
    ): String? {
        val from = origin.trim().uppercase(Locale.ROOT)
        val to = destination.trim().uppercase(Locale.ROOT)
        if (!from.matches(Regex("[A-Z]{3}")) || !to.matches(Regex("[A-Z]{3}")) || from == to) return null
        if (adults !in 1..9 || children !in 0..9 || infants !in 0..9) return null
        val outbound = date(departureAt) ?: return null
        val inbound = if (returnAt != null) date(returnAt) ?: return null else null
        if (inbound != null && inbound < outbound) return null
        val query = buildString {
            append(from)
            append(outbound.token())
            append(to)
            if (inbound != null) append(inbound.token())
            append(adults)
            if (children > 0 || infants > 0) append(children)
            if (infants > 0) append(infants)
        }
        return "https://www.aviasales.ru/search/$query"
    }

    private fun date(raw: String): LocalDate? {
        if (raw.length < 10) return null
        val date = raw.take(10)
        if (!date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return null
        return try {
            LocalDate.parse(date).takeIf { it.year in 2000..2100 }
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun LocalDate.token(): String = "%02d%02d".format(Locale.ROOT, dayOfMonth, monthValue)
}
