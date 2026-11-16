package com.iumrah.beta.domain.booking

import java.time.Instant
import java.time.OffsetDateTime

/** Customer-facing estimate only; not a payment lock or operational deadline. */
object BookingAvailabilityEstimate {
    const val DURATION_MS = 30L * 60 * 1000
    fun deadline(start: String?, created: String?, serverDeadline: String?): Long? {
        val anchor = parse(start) ?: parse(created) ?: return null
        val estimate = anchor + DURATION_MS
        return parse(serverDeadline)?.let { minOf(it, estimate) } ?: estimate
    }
    fun remainingSeconds(deadline: Long, now: Long): Long =
        ((deadline - now).coerceIn(0L, DURATION_MS) + 999L) / 1000L
    private fun parse(raw: String?): Long? = raw?.let {
        runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
    }
}
