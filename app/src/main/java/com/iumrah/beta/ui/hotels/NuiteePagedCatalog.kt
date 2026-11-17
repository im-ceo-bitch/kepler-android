package com.iumrah.beta.ui.hotels

import androidx.compose.runtime.*
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.domain.nuitee.NuiteePaging
import com.iumrah.beta.models.nuitee.NuiteeHotel
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay

internal class NuiteePagedCatalogState {
    var hotels by mutableStateOf<List<NuiteeHotel>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var rateError by mutableStateOf<String?>(null); private set
    var stale by mutableStateOf(false); private set
    var sandbox by mutableStateOf(false); private set
    var nextOffset by mutableStateOf<Int?>(0); private set
    private var requestedOffset by mutableIntStateOf(0)
    internal var request by mutableIntStateOf(0); private set
    fun loadMore() {
        val next = nextOffset ?: return
        if (loading) return
        requestedOffset = next; loading = true; request++
    }
    fun refresh() { requestedOffset = 0; loading = true; request++ }
    fun retry() { if (!loading) { loading = true; request++ } }
    private var loadedRefresh: Int? = null
    suspend fun load(service: NuiteeHotelService, city: String, checkin: LocalDate, checkout: LocalDate, adults: Int,
                     stars: Int, query: String, favorites: Set<String>?, refresh: Int) {
        if (loadedRefresh != refresh) { requestedOffset = 0; loadedRefresh = refresh }
        val offset = requestedOffset
        loading = true; error = null
        try {
            if (offset == 0 && query.isNotBlank()) delay(300)
            val response = service.hotelPage(city, checkin, checkout, adults, stars, offset, query, favorites)
            currentCoroutineContext().ensureActive()
            val rows = if (offset == 0) response.hotels.distinctBy { it.id } else NuiteePaging.append(hotels, response.hotels)
            hotels = rows
            rateError = if (offset == 0) response.rateError else rateError ?: response.rateError
            stale = (offset > 0 && stale) || response.hotelCatalogStale || response.pricesStale || rateError != null
            sandbox = (offset > 0 && sandbox) || response.sandbox
            nextOffset = NuiteePaging.next(response, offset)
        } catch (cancel: CancellationException) { throw cancel }
        catch (ex: Exception) { error = ex.localizedMessage ?: "HOTEL_PAGE_UNAVAILABLE"; if (offset == 0) stale = true }
        finally { loading = false }
    }
}

@Composable internal fun rememberNuiteePagedCatalog(service: NuiteeHotelService, city: String, checkin: LocalDate,
    checkout: LocalDate, adults: Int, stars: Int = 0, query: String = "", favorites: Set<String>? = null,
    refresh: Int = 0): NuiteePagedCatalogState {
    val state = remember(service, city, checkin, checkout, adults, stars, query, favorites) { NuiteePagedCatalogState() }
    LaunchedEffect(state, state.request, refresh) { state.load(service, city, checkin, checkout, adults, stars, query, favorites, refresh) }
    return state
}
