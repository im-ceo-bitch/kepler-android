package com.iumrah.beta.ui.hotels

import androidx.compose.runtime.*
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.domain.nuitee.NuiteeRules
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.iumrah.beta.core.network.APIException

/** Static city inventory is shown before independent price hydration. */
internal class NuiteePagedCatalogState {
    var hotels by mutableStateOf<List<NuiteeHotel>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var rateError by mutableStateOf<String?>(null); private set
    var stale by mutableStateOf(false); private set
    var sandbox by mutableStateOf(false); private set
    val nextOffset: Int? get() = null
    internal var request by mutableIntStateOf(0); private set
    fun loadMore() = Unit // complete catalogue, no server UI paging
    fun refresh() { loading = true; request++ }
    fun retry() = refresh()
    private var loadedRefresh: Int? = null
    suspend fun load(service: NuiteeHotelService, city: String, checkin: LocalDate, checkout: LocalDate, adults: Int,
                     stars: Int, query: String, favorites: Set<String>?, refresh: Int, childrenAges: List<Int>) {
        val force = loadedRefresh != null && (loadedRefresh != refresh || request > 0)
        loadedRefresh = refresh
        loading = true; error = null; rateError = null; stale = false
        fun filter(rows: List<NuiteeHotel>) = NuiteeRules.filterHotels(rows.distinctBy { it.id },stars,query)
            .filter { favorites == null || it.id in favorites }
            .map { it.copy(minimumRate = null,rateStatus = "not_requested") }
        try {
            val cached = service.cachedHotels(city,checkin,checkout,adults,catalogOnly = true)
            if(cached != null) hotels = filter(cached.hotels)
            val catalog = service.hotels(city,checkin,checkout,adults,catalogOnly = true,forceRefresh = force)
            currentCoroutineContext().ensureActive()
            sandbox = catalog.sandbox; stale = catalog.hotelCatalogStale
            hotels = filter(catalog.hotels)
            loading = false
            if(sandbox || childrenAges.any { it !in 0..17 }) return
            // Do not remove static entries on a rate error. Every batch updates the existing list in-place.
            val ids = hotels.map { it.id }
            for(batch in ids.chunked(40)) {
                currentCoroutineContext().ensureActive()
                try {
                    val page = service.pricePage(city,checkin,checkout,adults,batch,childrenAges)
                    currentCoroutineContext().ensureActive()
                    val rows = page.hotels.associateBy { it.id }
                    hotels = hotels.map { hotel ->
                        if(hotel.id !in batch) hotel else rows[hotel.id]?.let { row ->
                            hotel.copy(minimumRate = row.minimumRate.takeUnless { page.pricesStale },rateStatus = if(page.pricesStale) "cached" else row.rateStatus)
                        } ?: hotel.copy(minimumRate = null,rateStatus = "provider_error")
                    }
                    if(page.rateError != null) rateError = page.rateError
                    if(page.rateError == "NUITEE_RATE_LIMIT") break
                } catch(cancel: CancellationException) { throw cancel }
                catch(e: Exception) {
                    rateError = e.localizedMessage ?: "RATES_UNAVAILABLE"
                    hotels = hotels.map { if(it.id in batch) it.copy(minimumRate = null,rateStatus = "provider_error") else it }
                    if((e is APIException.Status && e.code == 429) || (e is APIException.Server && e.code == 429)) break
                }
            }
        } catch(cancel: CancellationException) { throw cancel }
        catch(e: Exception) { error = e.localizedMessage ?: "HOTEL_CATALOG_UNAVAILABLE"; stale = true }
        finally { loading = false }
    }
}

@Composable internal fun rememberNuiteePagedCatalog(service: NuiteeHotelService, city: String, checkin: LocalDate,
    checkout: LocalDate, adults: Int, stars: Int = 0, query: String = "", favorites: Set<String>? = null,
    refresh: Int = 0, childrenAges: List<Int> = emptyList()): NuiteePagedCatalogState {
    val state = remember(service,city,checkin,checkout,adults,stars,query,favorites,childrenAges) { NuiteePagedCatalogState() }
    LaunchedEffect(state,state.request,refresh) { state.load(service,city,checkin,checkout,adults,stars,query,favorites,refresh,childrenAges) }
    return state
}
