package com.iumrah.beta.ui.hotels

import androidx.compose.runtime.*
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.models.nuitee.NuiteeMinimumRate
import com.iumrah.beta.domain.nuitee.NuiteeRules
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.iumrah.beta.core.network.APIException

/**
 * The static inventory and the room rate results are one search identity.
 * Star/name/favorites filters NEVER own a network request (iOS NuiteeCatalogModel).
 */
internal class NuiteePagedCatalogState {
    private data class PriceSnapshot(val atMs: Long, val rows: Map<String, NuiteeHotelService.PriceRow>)
    private companion object {
        private const val RATE_CACHE_TTL_MS = 15 * 60 * 1000L
        private val priceCache = linkedMapOf<String, PriceSnapshot>()
        // Monotonic time avoids a clock adjustment making yesterday's quotes look fresh.
        private fun nowMs(): Long = System.nanoTime() / 1_000_000L
        private fun cached(scope: String, refresh: Boolean): Map<String, NuiteeHotelService.PriceRow> =
            synchronized(priceCache) {
                if (refresh) priceCache.remove(scope)
                val snapshot = priceCache[scope]
                if (snapshot != null && nowMs() - snapshot.atMs in 0 until RATE_CACHE_TTL_MS)
                    snapshot.rows else { priceCache.remove(scope); emptyMap() }
            }
        private fun rememberRates(scope: String, entries: Map<String, NuiteeHotelService.PriceRow>) {
            if (entries.isEmpty()) return
            synchronized(priceCache) {
                val now = nowMs()
                val existing = priceCache[scope]?.takeIf { now - it.atMs in 0 until RATE_CACHE_TTL_MS }
                if (existing == null && priceCache.size >= 8) priceCache.clear() // iOS bound
                priceCache[scope] = PriceSnapshot(existing?.atMs ?: now, existing?.rows.orEmpty() + entries)
            }
        }
    }
    private var inventory by mutableStateOf<List<NuiteeHotel>>(emptyList())
    private var filterStars by mutableIntStateOf(0)
    private var filterQuery by mutableStateOf("")
    private var filterFavorites by mutableStateOf<Set<String>?>(null)
    val hotels: List<NuiteeHotel> get() = NuiteeRules.filterHotels(inventory, filterStars, filterQuery)
        .filter { filterFavorites == null || it.id in filterFavorites.orEmpty() }
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var rateError by mutableStateOf<String?>(null); private set
    var stale by mutableStateOf(false); private set
    var sandbox by mutableStateOf(false); private set
    val nextOffset: Int? get() = null
    internal var request by mutableIntStateOf(0); private set
    fun loadMore() = Unit
    fun refresh() { loading = true; request++ }
    fun retry() = refresh()
    fun setLocalFilters(stars: Int, query: String, favorites: Set<String>?) {
        filterStars = stars; filterQuery = query; filterFavorites = favorites?.toSet()
    }

    private fun isValid(hotelId: String, rate: NuiteeMinimumRate?): Boolean =
        rate != null && rate.hotelId == hotelId && rate.currency.equals("USD", true) &&
            rate.price.isFinite() && rate.price > 0

    private fun merge(rows: Map<String, NuiteeHotelService.PriceRow>, requested: Set<String>, missingStatus: String) {
        inventory = inventory.map { old ->
            if (old.id !in requested) old
            else {
                val row = rows[old.id]
                when {
                    row == null -> if (old.minimumRate != null) old else old.copy(rateStatus = missingStatus)
                    row.rateStatus == "no_availability" -> old.copy(minimumRate = null, rateStatus = "no_availability")
                    isValid(old.id, row.minimumRate) -> old.copy(minimumRate = row.minimumRate, rateStatus = "available")
                    old.minimumRate != null -> old // never overwrite a verified rate with an incomplete reply
                    else -> old.copy(minimumRate = null, rateStatus = row.rateStatus ?: missingStatus)
                }
            }
        }
    }

    private fun markMissing(status: String) {
        inventory = inventory.map { if (it.minimumRate == null && it.rateStatus != "no_availability")
            it.copy(rateStatus = status) else it }
    }

    suspend fun load(service: NuiteeHotelService, city: String, checkin: LocalDate, checkout: LocalDate,
                     adults: Int, refresh: Int, childrenAges: List<Int>) {
        loading = true; error = null; rateError = null; stale = false
        try {
            service.cachedHotels(city,checkin,checkout,adults,catalogOnly = true)?.let { snapshot ->
                inventory = snapshot.hotels.distinctBy { it.id }.map { it.copy(minimumRate = null, rateStatus = "not_requested") }
            }
            val catalog = service.hotels(city,checkin,checkout,adults,catalogOnly = true,
                forceRefresh = refresh > 0 || request > 0)
            currentCoroutineContext().ensureActive()
            sandbox = catalog.sandbox; stale = catalog.hotelCatalogStale
            inventory = catalog.hotels.distinctBy { it.id }.map { it.copy(minimumRate = null, rateStatus = "not_requested") }
            loading = false // inventory is usable before all rate calls finish
            if (sandbox || childrenAges.any { it !in 0..17 }) {
                markMissing(if (sandbox) "sandbox" else "invalid_occupancy")
                return
            }
            // Price cache is EXACTLY scoped to stay and first-room occupancy;
            // changing dates/guests never reuses a displayed rate.
            val scope = listOf(city, checkin, checkout, adults, childrenAges).joinToString("|")
            val known = cached(scope, refresh > 0 || request > 0)
                .filter { (id, row) -> isValid(id, row.minimumRate) }
            merge(known, inventory.map { it.id }.toSet(), "not_requested")
            val unresolved = mutableListOf<String>()
            // Two requests in parallel, max 40 hotels per batch, matching iOS hydrate.
            for (group in inventory.map { it.id }.filter { it !in known }.chunked(80)) {
                currentCoroutineContext().ensureActive()
                val batches = group.chunked(40)
                val responses = coroutineScope {
                    batches.map { ids -> async {
                        try { Pair(ids, Result.success(service.pricePage(city,checkin,checkout,adults,ids,childrenAges))) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) { Pair(ids, Result.failure<NuiteeHotelService.PricePage>(error)) }
                    } }.awaitAll()
                }
                var limited = false
                for ((ids, outcome) in responses) {
                    val response = outcome.getOrNull()
                    val rows = response?.hotels.orEmpty().filter { it.id in ids }.associateBy { it.id }
                    if (response != null && response.rateError == "NUITEE_RATE_LIMIT") limited = true
                    val errorValue = outcome.exceptionOrNull()
                    if ((errorValue is APIException.Status && errorValue.code == 429) ||
                        (errorValue is APIException.Server && errorValue.code == 429)) limited = true
                    // iOS does not accept batch rows marked "cached" as final: reopen
                    // the concrete offer via individual room rates before promotion.
                    val verified = rows.filterValues { !response!!.pricesStale && it.rateStatus != "cached" &&
                        isValid(it.id, it.minimumRate) }
                    merge(verified, ids.toSet(), "loading")
                    rememberRates(scope, verified)
                    // iOS only treats the individual /rates response as a
                    // confirmed sold-out result. A bulk "no_availability" is
                    // insufficient evidence and must reach catalogRate().
                    unresolved += ids.filter { it !in verified }
                }
                if (limited) {
                    rateError = "NUITEE_RATE_LIMIT"
                    markMissing("provider_error")
                    return
                }
            }
            // A missing batch result is NOT "no availability". Ask the same
            // individual rate endpoint as the room detail page, 3 at a time.
            for (group in unresolved.distinct().chunked(3)) {
                currentCoroutineContext().ensureActive()
                val outcomes = coroutineScope { group.map { id -> async {
                    service.catalogRate(id, checkin, checkout, adults, childrenAges)
                } }.awaitAll() }
                val indexed = outcomes.associate { it.row.id to it.row }
                merge(indexed, group.toSet(), "provider_error")
                rememberRates(scope, indexed.filterValues { isValid(it.id, it.minimumRate) })
                if (outcomes.any { it.rateLimited }) {
                    rateError = "NUITEE_RATE_LIMIT"
                    markMissing("provider_error")
                    return
                }
            }
            markMissing("not_returned")
            if (inventory.any { it.rateStatus == "provider_error" || it.rateStatus == "not_returned" })
                rateError = "RATES_INCOMPLETE"
        } catch (cancel: CancellationException) { throw cancel }
        catch (errorValue: Exception) {
            error = errorValue.localizedMessage ?: "HOTEL_CATALOG_UNAVAILABLE"
            stale = true; markMissing("provider_error")
        } finally { loading = false }
    }
}

@Composable internal fun rememberNuiteePagedCatalog(service: NuiteeHotelService, city: String,
    checkin: LocalDate, checkout: LocalDate, adults: Int, stars: Int = 0, query: String = "",
    favorites: Set<String>? = null, refresh: Int = 0, childrenAges: List<Int> = emptyList()): NuiteePagedCatalogState {
    // Filters intentionally omitted. Filtering is local and must not force a
    // new static catalogue / tariff request on every keystroke or star tap.
    val state = remember(service,city,checkin,checkout,adults,childrenAges) { NuiteePagedCatalogState() }
    SideEffect { state.setLocalFilters(stars, query, favorites) }
    LaunchedEffect(state,state.request,refresh) {
        state.load(service,city,checkin,checkout,adults,refresh,childrenAges)
    }
    return state
}
