package com.iumrah.beta.ui.hotels

import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.models.nuitee.NuiteeMinimumRate
import com.iumrah.beta.core.network.APIException
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    fun okay(value:Boolean,label:String) = check(value){label}
    val today=LocalDate.now().plusDays(10)
    val rows=(0 until 85).map { NuiteeHotel("hotel-$it","Hotel $it",if(it%2==0)4 else 3) }
    val service=NuiteeHotelService(rows)
    val state=NuiteePagedCatalogState()
    service.bulkResponse = { ids -> NuiteeHotelService.PricePage(hotels=ids.filter {
        it.substringAfterLast('-').toInt()%3==0
    }.map { id -> NuiteeHotelService.PriceRow(id,NuiteeMinimumRate(id,90.0),"available") }) }
    state.load(service,"makkah",today,today.plusDays(7),2,0,emptyList())
    okay(!state.loading && service.catalogCount==1 && service.bulkCount==3,"85 hotels loaded in three bulk batches")
    okay(service.detailCount>0 && state.hotels.size==85,"missing hotels receive individual requests")
    okay(state.hotels.all { it.minimumRate != null },"missing prices recovered")
    val calls=service.catalogCount to service.bulkCount
    state.setLocalFilters(4,"hotel 4",null)
    okay(state.hotels.isNotEmpty() && state.hotels.all{it.roundedStars==4 && it.name.contains("hotel 4",true)},"local stars and name filter")
    state.setLocalFilters(0,"",setOf("hotel-8","hotel-10"))
    okay(state.hotels.size==2,"local favorites filter")
    okay(calls==(service.catalogCount to service.bulkCount),"filters never hit API")

    // iOS priceScope cache is 15 minutes, keyed by city, dates, room occupancy.
    val cachedService=NuiteeHotelService(rows)
    val reopened=NuiteePagedCatalogState()
    reopened.load(cachedService,"makkah",today,today.plusDays(7),2,0,emptyList())
    okay(reopened.hotels.all { it.minimumRate != null } && cachedService.bulkCount==0 && cachedService.detailCount==0,
        "reopened same search reuses verified scoped prices without supplier flood")
    val refreshed=NuiteePagedCatalogState()
    refreshed.load(cachedService,"makkah",today,today.plusDays(7),2,1,emptyList())
    okay(cachedService.bulkCount>0,"explicit refresh bypasses scoped price cache")

    val cachedOnly=NuiteeHotelService(rows.take(3)); val recovered=NuiteePagedCatalogState()
    cachedOnly.bulkResponse = { ids -> NuiteeHotelService.PricePage(hotels=ids.map { id ->
        NuiteeHotelService.PriceRow(id,NuiteeMinimumRate(id,5.0),"cached") }) }
    cachedOnly.detailResponse = { id -> NuiteeHotelService.CatalogRateResult(
        NuiteeHotelService.PriceRow(id,NuiteeMinimumRate(id,120.0),"available")) }
    recovered.load(cachedOnly,"madinah",today,today.plusDays(7),2,0,emptyList())
    okay(cachedOnly.detailCount==3 && recovered.hotels.all { it.minimumRate?.price==120.0 },
        "supplier cached flags must be reconciled against individual offers")

    val corrupted=NuiteeHotelService(rows.take(4));val bad=NuiteePagedCatalogState()
    corrupted.bulkResponse = { ids -> NuiteeHotelService.PricePage(hotels=ids.map { id ->
        NuiteeHotelService.PriceRow(id,NuiteeMinimumRate("wrong-hotel",-15.0,"EUR"))
    }) }
    corrupted.detailResponse = { id -> NuiteeHotelService.CatalogRateResult(NuiteeHotelService.PriceRow(id,null,"not_returned")) }
    bad.load(corrupted,"makkah",today.plusDays(1),today.plusDays(8),2,0,emptyList())
    okay(bad.hotels.all{it.minimumRate==null},"invalid/cross-hotel/negative price never published")
    okay(bad.hotels.all{it.rateStatus=="not_returned"},"unconfirmed price not called sold out")

    val unavailable=NuiteeHotelService(rows.take(3));val soldout=NuiteePagedCatalogState()
    unavailable.bulkResponse={ ids -> NuiteeHotelService.PricePage(hotels=ids.map { NuiteeHotelService.PriceRow(it,null,"no_availability") }) }
    unavailable.detailResponse={ id -> NuiteeHotelService.CatalogRateResult(NuiteeHotelService.PriceRow(id,null,"no_availability")) }
    soldout.load(unavailable,"makkah",today.plusDays(2),today.plusDays(9),2,0,emptyList())
    okay(soldout.hotels.all { it.rateStatus=="no_availability" && it.minimumRate==null },"confirmed no availability")
    okay(unavailable.detailCount==3,"a bulk sold-out is rechecked by the individual room endpoint")

    val limited=NuiteeHotelService(rows);val throttled=NuiteePagedCatalogState()
    limited.bulkResponse={ throw APIException.Status(429) }
    throttled.load(limited,"makkah",today.plusDays(3),today.plusDays(10),2,0,emptyList())
    okay(throttled.rateError=="NUITEE_RATE_LIMIT","429 accurately distinguished")
    okay(limited.detailCount==0,"limit never causes N individual requests")
    okay(throttled.hotels.all { it.minimumRate==null && it.rateStatus=="provider_error" },"all interrupted rates terminate loading")
    println("P02 Nuitee hydrate: PASS (17 scoped scenario checks)")
}
