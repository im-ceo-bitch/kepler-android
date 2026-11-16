package com.iumrah.beta.data.nuitee
import com.iumrah.beta.models.nuitee.*
import java.time.LocalDate
class NuiteeHotelService {
    var cached: NuiteeHotelListEnvelope? = null
    var cachedCatalog: NuiteeHotelListEnvelope? = null
    suspend fun cachedHotels(city: String, checkin: LocalDate, checkout: LocalDate, adults: Int, catalogOnly: Boolean = false): NuiteeHotelListEnvelope? = if (catalogOnly) cachedCatalog else cached
    var catalog: suspend (String, LocalDate, LocalDate, Int) -> NuiteeHotelListEnvelope = { _,_,_,_ -> error("Unexpected hotel catalog") }
    var rate: suspend (String, LocalDate, LocalDate, Int, List<Int>) -> NuiteeRatesEnvelope = { _,_,_,_,_ -> error("Unexpected rates") }
    suspend fun hotels(city: String, checkin: LocalDate, checkout: LocalDate, adults: Int, catalogOnly: Boolean = false, forceRefresh: Boolean = false, smartTimeout: Boolean = false): NuiteeHotelListEnvelope { check(smartTimeout); return catalog(city,checkin,checkout,adults) }
    suspend fun rates(hotelId: String, checkin: LocalDate, checkout: LocalDate, adults: Int, childrenAges: List<Int> = emptyList(), language: String = "en", smartTimeout: Boolean = false): NuiteeRatesEnvelope { check(smartTimeout); return rate(hotelId,checkin,checkout,adults,childrenAges) }
}
