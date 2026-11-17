package com.iumrah.beta.data.nuitee

import android.content.Context
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.models.nuitee.*
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.iumrah.beta.domain.nuitee.NuiteePaging

/** Same Nuitee routes as SwiftUI; no separate Android pricing or invented inventory. */
class NuiteeHotelService(context: Context, private val api: APIClient = APIClient()) {
    private val cache = NuiteeDailyHotelCache.shared(context)

    suspend fun cachedHotels(city: String, checkin: LocalDate, checkout: LocalDate, adults: Int,
                             catalogOnly: Boolean = false): NuiteeHotelListEnvelope? =
        cache.read(NuiteeDailyCachePolicy.key(city, checkin, checkout, adults, catalogOnly))

    suspend fun hotels(city: String, checkin: LocalDate, checkout: LocalDate, adults: Int, catalogOnly: Boolean = false, forceRefresh: Boolean = false, smartTimeout: Boolean = false): NuiteeHotelListEnvelope {
        require(city == "makkah" || city == "madinah")
        require(checkout.isAfter(checkin) && adults in 1..9)
        val key = NuiteeDailyCachePolicy.key(city, checkin, checkout, adults, catalogOnly)
        if (!forceRefresh) cache.read(key)?.let { return it }
        val response = api.get<NuiteeHotelListEnvelope>(
            "/api/package/nuitee/hotels",
            query = mapOf("city" to city, "checkin" to checkin.toString(), "checkout" to checkout.toString(),
                "adults" to adults.toString(), "catalogOnly" to if (catalogOnly) "1" else "0"),
            timeoutSeconds = if (smartTimeout) 45 else 100,
        )
        if (!response.ok) throw APIException.InvalidResponse
        if (NuiteeDailyCachePolicy.cacheable(response.ok, catalogOnly, response.rateError)) cache.write(key, response)
        return response
    }

    // Cache only legacy full-catalogue results, scoped by exact filters and stay.
    private val legacyPages = linkedMapOf<Map<String, String>, NuiteeHotelListEnvelope>()

    suspend fun hotelPage(city: String, checkin: LocalDate, checkout: LocalDate, adults: Int,
                          stars: Int = 0, offset: Int = 0, query: String = "", favorites: Set<String>? = null,
                          catalogOnly: Boolean = false, allowCatalogFallback: Boolean = true): NuiteeHotelListEnvelope {
        require(city in setOf("makkah", "madinah") && checkout > checkin && adults in 1..9 && offset >= 0)
        val search = mapOf("city" to city, "checkin" to checkin.toString(), "checkout" to checkout.toString(),
            "adults" to adults.toString(), "stars" to stars.toString(), "query" to query.trim(),
            "catalogOnly" to if (catalogOnly) "1" else "0") +
            (favorites?.let { mapOf("hotelIds" to it.sorted().joinToString(",")) } ?: emptyMap())
        if (favorites != null && favorites.isEmpty()) return NuiteeHotelListEnvelope(ok = true, total = 0)
        val cached = synchronized(legacyPages) { if (offset == 0) { legacyPages.remove(search); null } else legacyPages[search] }
        if (cached != null) return NuiteePaging.normalize(cached, offset, stars, query, favorites)
        try {
            val result = api.get<NuiteeHotelListEnvelope>("/api/package/nuitee/hotels", query = search +
                mapOf("offset" to offset.toString(), "pageSize" to NuiteePaging.PAGE_SIZE.toString()), timeoutSeconds = 45)
            if (!result.ok) throw APIException.InvalidResponse
            currentCoroutineContext().ensureActive()
            if (result.total == null && result.nextOffset == null) synchronized(legacyPages) {
                legacyPages[search] = result
                while (legacyPages.size > 8) legacyPages.remove(legacyPages.keys.first())
            }
            return NuiteePaging.normalize(result, offset, stars, query, favorites)
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (catalogOnly || !allowCatalogFallback) throw error
            return NuiteePaging.unpriced(hotelPage(city, checkin, checkout, adults, stars, offset, query, favorites,
                catalogOnly = true, allowCatalogFallback = false))
        }
    }

    suspend fun detail(hotelId: String, language: String = "en"): NuiteeHotelDetail {
        val result = api.get<NuiteeHotelDetailEnvelope>(
            "/api/package/nuitee/hotel", mapOf("hotelId" to hotelId, "language" to language), timeoutSeconds = 35,
        )
        if (!result.ok) throw APIException.InvalidResponse
        return result.hotel
    }

    suspend fun reviews(hotelId: String, language: String, offset: Int = 0): NuiteeReviewsEnvelope {
        require(offset >= 0)
        val result = api.get<NuiteeReviewsEnvelope>("/api/package/nuitee/reviews",
            mapOf("hotelId" to hotelId, "language" to language, "offset" to offset.toString()), timeoutSeconds = 35)
        if (!result.ok) throw APIException.InvalidResponse
        return result
    }

    suspend fun rates(hotelId: String, checkin: LocalDate, checkout: LocalDate, adults: Int,
                      childrenAges: List<Int> = emptyList(), language: String = "en", smartTimeout: Boolean = false): NuiteeRatesEnvelope {
        require(checkout.isAfter(checkin) && adults in 1..9 && childrenAges.all { it in 0..17 })
        val result = api.post<NuiteeRatesEnvelope, NuiteeRatesRequest>(
            "/api/package/nuitee/rates", NuiteeRatesRequest(hotelId, checkin.toString(), checkout.toString(),
                adults, childrenAges, language), timeoutSeconds = if (smartTimeout) 35 else 100,
        )
        if (!result.ok) throw APIException.InvalidResponse
        return result
    }
}

@Serializable
private data class NuiteeRatesRequest(val hotelId: String, val checkin: String, val checkout: String,
                                      val adults: Int, val children: List<Int>, val language: String)
