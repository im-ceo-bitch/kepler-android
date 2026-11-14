package com.iumrah.beta.data.nuitee

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Same local-day and 24-hour boundary as Swift NuiteeDailyHotelCache. */
internal object NuiteeDailyCachePolicy {
    fun key(city: String, checkin: LocalDate, checkout: LocalDate, adults: Int, catalogOnly: Boolean): String =
        "${city.lowercase(Locale.ROOT)}-$checkin-$checkout-$adults-${if (catalogOnly) "catalog" else "rates"}"

    fun fresh(savedAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        savedAt >= 0 && now >= savedAt && now - savedAt < 86_400_000L &&
            Instant.ofEpochMilli(savedAt).atZone(zone).toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    fun cacheable(ok: Boolean, catalogOnly: Boolean, rateError: String?): Boolean =
        ok && (catalogOnly || rateError == null)
}

/** Access-order, same four-entry hot cache as iOS. Caller serializes access. */
internal class NuiteeHotCache<T> {
    private val values = LinkedHashMap<String, T>(4, .75f, true)
    fun get(key: String): T? = values[key]
    fun remove(key: String) { values.remove(key) }
    fun put(key: String, value: T) {
        values[key] = value
        while (values.size > 4) values.remove(values.keys.first())
    }
}
