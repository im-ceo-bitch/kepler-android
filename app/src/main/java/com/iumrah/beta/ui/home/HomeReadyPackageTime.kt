package com.iumrah.beta.ui.home

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// iOS uses ISO8601DateFormatter with the device-local time zone. Do not display
// fabricated flight times when the upstream timestamp has no valid offset.
internal fun homeFlightTime(raw: String): String = parseHomeFlightInstant(raw)?.let {
    DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(ZoneId.systemDefault()).format(it)
} ?: "—"

internal fun homeFlightDay(raw: String): String = parseHomeFlightInstant(raw)?.let {
    DateTimeFormatter.ofPattern("d MMM", Locale.US).withZone(ZoneId.systemDefault()).format(it)
} ?: raw.take(10).takeIf { it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) } ?: "—"

private fun parseHomeFlightInstant(raw: String): Instant? = runCatching {
    OffsetDateTime.parse(raw).toInstant()
}.getOrNull() ?: runCatching { Instant.parse(raw) }.getOrNull()

