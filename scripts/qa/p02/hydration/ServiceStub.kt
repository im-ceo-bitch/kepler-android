package com.iumrah.beta.data.nuitee

import com.iumrah.beta.models.nuitee.*
import com.iumrah.beta.core.network.APIException
import java.time.LocalDate

class NuiteeHotelService(var list:List<NuiteeHotel>) {
    data class PriceRow(val id:String, val minimumRate:NuiteeMinimumRate?=null, val rateStatus:String?=null)
    data class PricePage(val ok:Boolean=true, val sandbox:Boolean=false,val pricesStale:Boolean=false,
        val rateError:String?=null,val hotels:List<PriceRow> = emptyList())
    data class CatalogRateResult(val row:PriceRow,val rateLimited:Boolean=false)
    var cachedCount=0;var catalogCount=0;var bulkCount=0;var detailCount=0
    var bulkResponse:(List<String>)->PricePage = { ids -> PricePage(hotels=ids.map { PriceRow(it,NuiteeMinimumRate(it,60.0)) }) }
    var detailResponse:(String)->CatalogRateResult = { id -> CatalogRateResult(PriceRow(id,NuiteeMinimumRate(id,70.0),"available")) }
    suspend fun cachedHotels(city:String,checkin:LocalDate,checkout:LocalDate,adults:Int,catalogOnly:Boolean):NuiteeHotelListEnvelope? {
        cachedCount++;return null
    }
    suspend fun hotels(city:String,checkin:LocalDate,checkout:LocalDate,adults:Int,catalogOnly:Boolean,forceRefresh:Boolean):NuiteeHotelListEnvelope {
        catalogCount++;return NuiteeHotelListEnvelope(list)
    }
    suspend fun pricePage(city:String,checkin:LocalDate,checkout:LocalDate,adults:Int,ids:List<String>,ages:List<Int>):PricePage {
        bulkCount++;return bulkResponse(ids)
    }
    suspend fun catalogRate(id:String,checkin:LocalDate,checkout:LocalDate,adults:Int,ages:List<Int>):CatalogRateResult {
        detailCount++;return detailResponse(id)
    }
}
