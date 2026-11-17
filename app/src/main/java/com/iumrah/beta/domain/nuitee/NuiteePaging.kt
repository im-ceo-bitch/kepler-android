package com.iumrah.beta.domain.nuitee

import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.models.nuitee.NuiteeHotelListEnvelope

object NuiteePaging {
    const val PAGE_SIZE = 12
    fun normalize(response: NuiteeHotelListEnvelope, offset: Int, stars: Int, query: String, favorites: Set<String>?): NuiteeHotelListEnvelope {
        require(offset >= 0)
        if (response.total != null || response.nextOffset != null) return response
        val term = query.trim()
        val filtered = response.hotels.distinctBy { it.id }.filter {
            (stars == 0 || it.roundedStars == stars) && (favorites == null || it.id in favorites) &&
                (term.isEmpty() || it.name.contains(term, true) || it.address.orEmpty().contains(term, true))
        }
        val start = offset.coerceAtMost(filtered.size)
        val end = (start + PAGE_SIZE).coerceAtMost(filtered.size)
        return response.copy(hotels = filtered.subList(start, end), total = filtered.size, nextOffset = end.takeIf { it < filtered.size })
    }
    fun next(response: NuiteeHotelListEnvelope, offset: Int): Int? {
        if (response.hotels.isEmpty()) return null
        if (response.nextOffset != null) return response.nextOffset.takeIf { it > offset && (response.total == null || it < response.total) }
        val end = offset.toLong() + response.hotels.size
        return end.toInt().takeIf { end <= Int.MAX_VALUE && end > offset && response.total != null && end < response.total }
    }
    fun append(existing: List<NuiteeHotel>, incoming: List<NuiteeHotel>): List<NuiteeHotel> = (existing + incoming).distinctBy { it.id }
    fun unpriced(response: NuiteeHotelListEnvelope): NuiteeHotelListEnvelope = response.copy(
        hotels = response.hotels.map { it.copy(minimumRate = null, rateStatus = "provider_error") },
        pricesStale = true, rateError = "RATES_UNAVAILABLE")
}
