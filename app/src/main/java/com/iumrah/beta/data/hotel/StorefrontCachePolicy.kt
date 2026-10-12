package com.iumrah.beta.data.hotel

/** A cached public catalogue is only displayed while its saved timestamp is valid. */
internal fun catalogCacheIsFresh(
    savedAt: Long,
    now: Long,
    maxAgeMillis: Long = 48L * 60L * 60L * 1000L,
): Boolean = savedAt > 0L && savedAt <= now && (now - savedAt) <= maxAgeMillis

/** Public hotel amenities/details can survive longer than pricing, but never forever. */
internal fun hotelDetailCacheIsFresh(savedAt: Long, now: Long): Boolean =
    catalogCacheIsFresh(savedAt, now, maxAgeMillis = 7L * 24L * 60L * 60L * 1000L)
