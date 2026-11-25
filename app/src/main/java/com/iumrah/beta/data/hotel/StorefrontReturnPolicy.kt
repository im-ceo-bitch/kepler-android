package com.iumrah.beta.data.hotel

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** iOS HotelStorefrontCompatibility: edited return must match route and 2…15 Saudi calendar days. */
internal object StorefrontReturnPolicy {
    private val saudi = ZoneId.of("Asia/Riyadh")
    private fun instant(raw: String): Instant? = runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(raw) }.getOrNull()
    fun chronological(arrival: String, departure: String): Boolean {
        val a = instant(arrival) ?: return false
        val d = instant(departure) ?: return false
        return d.isAfter(a)
    }
    fun compatible(arrival: String, departure: String, outboundOrigin: String, expectedSaudi: String, returnOrigin: String, returnDestination: String): Boolean {
        if (!returnOrigin.equals(expectedSaudi, true)) return false
        if (!returnDestination.equals(outboundOrigin, true) && !( !outboundOrigin.equals("TAS", true) && returnDestination.equals("TAS", true))) return false
        val a = instant(arrival) ?: return false
        val d = instant(departure) ?: return false
        return d.isAfter(a) && ChronoUnit.DAYS.between(a.atZone(saudi).toLocalDate(), d.atZone(saudi).toLocalDate()) in 2L..15L
    }
}
