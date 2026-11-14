package com.iumrah.beta.data.nuitee

import android.content.Context
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.models.nuitee.*
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** Same Nuitee routes as SwiftUI; no separate Android pricing or invented inventory. */
class NuiteeHotelService(context: Context, private val api: APIClient = APIClient()) {
    private val cache = NuiteeDailyHotelCache.shared(context)

    suspend fun cachedHotels(city: String, checkin: LocalDate, checkout: LocalDate, adults: Int,
                             catalogOnly: Boolean = false): NuiteeHotelListEnvelope? =
        cache.read(NuiteeDailyCachePolicy.key(city, checkin, checkout, adults, catalogOnly))

    suspend fun hotels(city: String, checkin: LocalDate, checkout: LocalDate, adults: Int, catalogOnly: Boolean = false, forceRefresh: Boolean = false): NuiteeHotelListEnvelope {
        require(city == "makkah" || city == "madinah")
        require(checkout.isAfter(checkin) && adults in 1..9)
        val key = NuiteeDailyCachePolicy.key(city, checkin, checkout, adults, catalogOnly)
        if (!forceRefresh) cache.read(key)?.let { return it }
        val response = api.get<NuiteeHotelListEnvelope>(
            "/api/package/nuitee/hotels",
            query = mapOf("city" to city, "checkin" to checkin.toString(), "checkout" to checkout.toString(),
                "adults" to adults.toString(), "catalogOnly" to if (catalogOnly) "1" else "0"),
            timeoutSeconds = 100,
        )
        if (!response.ok) throw APIException.InvalidResponse
        if (NuiteeDailyCachePolicy.cacheable(response.ok, catalogOnly, response.rateError)) cache.write(key, response)
        return response
    }

    suspend fun detail(hotelId: String, language: String = "en"): NuiteeHotelDetail {
        val result = api.get<NuiteeHotelDetailEnvelope>(
            "/api/package/nuitee/hotel", mapOf("hotelId" to hotelId, "language" to language), timeoutSeconds = 35,
        )
        if (!result.ok) throw APIException.InvalidResponse
        return result.hotel
    }

    suspend fun rates(hotelId: String, checkin: LocalDate, checkout: LocalDate, adults: Int,
                      childrenAges: List<Int> = emptyList(), language: String = "en"): NuiteeRatesEnvelope {
        require(checkout.isAfter(checkin) && adults in 1..9 && childrenAges.all { it in 0..17 })
        val result = api.post<NuiteeRatesEnvelope, NuiteeRatesRequest>(
            "/api/package/nuitee/rates", NuiteeRatesRequest(hotelId, checkin.toString(), checkout.toString(),
                adults, childrenAges, language), timeoutSeconds = 100,
        )
        if (!result.ok) throw APIException.InvalidResponse
        return result
    }
}

@Serializable
private data class NuiteeRatesRequest(val hotelId: String, val checkin: String, val checkout: String,
                                      val adults: Int, val children: List<Int>, val language: String)
