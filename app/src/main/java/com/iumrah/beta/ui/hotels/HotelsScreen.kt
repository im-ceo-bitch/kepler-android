@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.hotels

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.share.IumrahPackageShare
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.flight.AviasalesFlightDiscoveryService
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.data.hotel.StorefrontCatalogCache
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.models.flight.Airport
import com.iumrah.beta.models.hotel.HotelDetail
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.StorefrontFlightLeg
import com.iumrah.beta.models.hotel.StorefrontFlightOption
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import com.iumrah.beta.ui.flights.FlightDiscoveryPanel
import com.iumrah.beta.ui.flights.FlightAssemblyCard
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.components.IumrahRootPageHeader
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.CupertinoRatingStars
import com.iumrah.beta.ui.trip.IumrahAirportMapDialog
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class HotelsBoard { HOTELS, FLIGHTS, SUNDAY }

private data class HotelPreview(
    val images: List<String> = emptyList(),
    val detail: HotelDetail? = null,
)

private data class StorefrontInitialLoad(
    val makkah: Result<List<HotelSummary>>,
    val madinah: Result<List<HotelSummary>>,
    val flights: Result<List<StorefrontFlightOption>>,
    val hotelPackages: Result<com.iumrah.beta.models.hotel.StorefrontPackagesEnvelope>,
    val flightPackages: Result<List<StorefrontPackageSnapshot>>,
)

@Composable
fun HotelsScreen(
    language: AppLanguage,
    service: HotelCatalogService,
    journey: JourneyStore,
    airports: AirportSearchService,
    flightDiscovery: AviasalesFlightDiscoveryService,
    chrome: AppChromeStore,
    initialBoard: HotelsBoard = HotelsBoard.HOTELS,
    unreadCount: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val journeyState by journey.state.collectAsState()
    val origin = journeyState.trip.originCode.ifBlank { "TAS" }.uppercase()

    var board by rememberSaveable { mutableStateOf(initialBoard) }
    var refreshSeed by remember { mutableIntStateOf(0) }
    var assemblyExpanded by rememberSaveable { mutableStateOf(false) }
    var displayedOrigin by remember { mutableStateOf(origin) }
    var makkah by remember { mutableStateOf<List<HotelSummary>>(emptyList()) }
    var madinah by remember { mutableStateOf<List<HotelSummary>>(emptyList()) }
    var flights by remember { mutableStateOf<List<StorefrontFlightOption>>(emptyList()) }
    var hotelPackages by remember { mutableStateOf<List<StorefrontPackageSnapshot>>(emptyList()) }
    var flightPackages by remember { mutableStateOf<List<StorefrontPackageSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var airportPicker by remember { mutableStateOf(false) }
    var carePresented by remember { mutableStateOf(false) }
    var packageShareError by remember { mutableStateOf<String?>(null) }
    var locatingAirport by remember { mutableStateOf(false) }
    var flightOriginFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var flightDestinationFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var friendsCalculatorPresented by remember { mutableStateOf(false) }
    val previews = remember { mutableStateMapOf<String, HotelPreview>() }
    // Public hotel listings may be displayed offline. Package prices are never
    // restored from disk; they must be validated by the server for this session.
    val catalogCache = remember(context) { StorefrontCatalogCache(context) }

    val favoritePrefs = remember { context.getSharedPreferences("iumrah_storefront_favorites", Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(favoritePrefs.getStringSet("hotel_ids", emptySet()).orEmpty().toSet()) }

    fun resolveLocation() {
        if (locatingAirport) return
        locatingAirport = true
        scope.launch {
            val airport = runCatching { resolveNearestDepartureAirport(context, airports) }.getOrNull()
            if (airport != null) journey.updateTrip(journeyState.trip.copy(origin = airport.iata.uppercase(), originAirport = airport))
            locatingAirport = false
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) resolveLocation()
    }
    fun locateAirport() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            resolveLocation()
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    suspend fun load(originCode: String) {
        loading = true
        error = null
        val normalizedOrigin = originCode.trim().uppercase()
        // Like iOS HotelStorefrontStore, perform independent catalog operations
        // concurrently. Per-request failures do not cancel unrelated sections.
        // coroutineScope retains the cancellation contract of LaunchedEffect(origin).
        val initial = coroutineScope {
            val m = async {
                runCatching { service.listHotels(listOf("Makkah", "Mecca", "Makka")) }
            }
            val d = async {
                runCatching { service.listHotels(listOf(
                    "Madinah", "Medina", "Madina", "Medinah", "Al Madinah",
                    "Al Medina", "Madinah Al Munawwarah", "Al Madinah Al Munawwarah",
                )) }
            }
            val flightsTask = async { runCatching { service.storefrontFlightBoard(normalizedOrigin).options } }
            val hotelsTask = async { runCatching { service.storefrontPackages("hotel-first", normalizedOrigin, 300) } }
            val packagesTask = async { runCatching { service.storefrontPackages("flight-first", normalizedOrigin, 500).items } }
            val makkahResult = m.await()
            val madinahResult = d.await()
            currentCoroutineContext().ensureActive()
            // Display the hotel lists as soon as their requests finish, without
            // waiting for the slower flight/Package Engine endpoints (iOS order).
            makkahResult.getOrNull()?.let { makkah = it }
            madinahResult.getOrNull()?.let { madinah = it }
            if (makkahResult.isSuccess || madinahResult.isSuccess) catalogCache.save(makkah, madinah)
            StorefrontInitialLoad(makkahResult, madinahResult, flightsTask.await(), hotelsTask.await(), packagesTask.await())
        }
        currentCoroutineContext().ensureActive() // No stale result after the origin changes.
        initial.makkah.getOrNull()?.let { makkah = it }
        initial.madinah.getOrNull()?.let { madinah = it }
        initial.flights.getOrNull()?.let { flights = it }
        val initialHotelPage = initial.hotelPackages.getOrNull()
        // A progressive empty page must never erase a previously loaded ready
        // package list for this same departure origin.
        initialHotelPage?.items?.takeIf { it.isNotEmpty() }?.let { hotelPackages = it }
        initial.flightPackages.getOrNull()?.let { flightPackages = it }
        if (initial.makkah.isFailure && initial.madinah.isFailure && makkah.isEmpty() && madinah.isEmpty()) {
            error = hotelText(language, "load_error")
        }
        loading = false

        // Hotel First is a server-owned progressive cache. Its bounded refresh
        // is independent of the initial render. Never replace a non-empty ready
        // page with a temporarily empty response.
        var page = initialHotelPage
        if (page != null && page.complete != true && (page.refreshRecommended == true || page.complete == false)) {
            var cursor = maxOf(0, page.nextRefreshCursor ?: 0)
            var maxPasses = 24
            page.expectedItemCount?.takeIf { it > 0 }?.let { expected ->
                val expectedHotels = kotlin.math.ceil(expected / 3.0).toInt()
                maxPasses = minOf(24, maxOf(2, kotlin.math.ceil(expectedHotels / 3.0).toInt() * 2))
            }
            repeat(maxPasses) {
                currentCoroutineContext().ensureActive()
                if (journey.state.value.trip.originCode.uppercase() != normalizedOrigin) return
                val refresh = runCatching { service.refreshStorefrontPackages("hotel-first", normalizedOrigin, cursor) }.getOrNull() ?: return
                currentCoroutineContext().ensureActive()
                val refreshedPage = runCatching { service.storefrontPackages("hotel-first", normalizedOrigin, 300) }.getOrNull() ?: return
                currentCoroutineContext().ensureActive()
                page = refreshedPage
                if (refreshedPage.items.isNotEmpty()) hotelPackages = refreshedPage.items
                if (refreshedPage.complete == true || refresh.complete == true) return
                cursor = maxOf(0, refresh.nextRefreshCursor ?: refreshedPage.nextRefreshCursor ?: cursor)
                delay(150)
            }
        }
    }

    LaunchedEffect(origin, refreshSeed) {
        // Package snapshots are scoped to one airport. Never let TAS prices
        // survive an airport switch to SKD or the inverse.
        if (displayedOrigin != origin) {
            flights = emptyList()
            flightPackages = emptyList()
            hotelPackages = emptyList()
            displayedOrigin = origin
        }
        // Display the last published hotel *catalog* quickly on weak networks.
        // Stale room/package prices are never cached or shown as current.
        val cached = catalogCache.restore()
        currentCoroutineContext().ensureActive()
        if (cached != null) {
            if (makkah.isEmpty()) makkah = cached.makkah
            if (madinah.isEmpty()) madinah = cached.madinah
        }
        load(origin)
    }

    val pageTitle = when (board) {
        HotelsBoard.HOTELS -> L10n.text("tab_hotels", language)
        HotelsBoard.FLIGHTS -> hotelText(language, "flights")
        HotelsBoard.SUNDAY -> "Sunday Umrah Club"
    }

    // iOS HotelsHomeView clips scroll content to the safe-area and keeps its
    // Flight-First assembly bar independent of the vertical scroll position.
    // The Android root tab bar is an overlay; leave clearance for both bars.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { refreshSeed += 1 },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 10.dp,
            bottom = if (board == HotelsBoard.FLIGHTS) (if (assemblyExpanded) 410.dp else 220.dp) else 118.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { IumrahRootPageHeader(pageTitle, chrome, unreadCount = unreadCount) }
        // The airport button is intentionally absent from iOS' Flights board;
        // that board owns a dedicated search-origin selector inside discovery.
        if (board != HotelsBoard.FLIGHTS) item {
            AirportSelector(
                language = language,
                airport = journeyState.trip.originAirport,
                fallbackCode = origin,
                locating = locatingAirport,
                onClick = { airportPicker = true },
                onLocate = ::locateAirport,
            )
        }
        item { StorefrontSegmentedControl(language, board) { board = it } }

        when (board) {
            HotelsBoard.HOTELS -> {
                item {
                    ShowcaseHero(
                        image = R.drawable.iumrah_hotels_showcase,
                        title = "iumrah Hotels",
                        description = hotelText(language, "hotel_body"),
                        note = null,
                        imageBackground = Color.Black,
                    )
                }
                item { IumrahFriendsShowcaseCard(language) { friendsCalculatorPresented = true } }

                if (makkah.isNotEmpty()) {
                    item { StorefrontSectionHeader(L10n.text("hotels_makkah", language), L10n.text("hotels_selected_badge", language)) }
                    items(makkah, key = { "m-${it.id}" }) { hotel ->
                        LaunchedEffect(hotel.id) {
                            if (previews[hotel.id] == null) {
                                runCatching { service.hotelDetail(hotel.id) }.getOrNull()?.let { detail ->
                                    previews[hotel.id] = HotelPreview(detail.images.sortedBy { it.position }.map { it.url }, detail)
                                }
                            }
                        }
                        val quote = bestHotelPackage(hotel, hotelPackages)
                        HotelStorefrontCard(
                            hotel = hotel,
                            preview = previews[hotel.id],
                            quote = quote,
                            language = language,
                            favorite = hotel.id in favorites,
                            onFavorite = {
                                favorites = if (hotel.id in favorites) favorites - hotel.id else favorites + hotel.id
                                favoritePrefs.edit().putStringSet("hotel_ids", favorites).apply()
                            },
                            onShare = { quote?.let { IumrahPackageShare.share(context, it, null, language) } ?: run { packageShareError = hotelText(language, "share_unavailable") } },
                            onOpen = { chrome.openHotel(hotel.id) },
                        )
                    }
                }

                if (madinah.isNotEmpty()) {
                    item { StorefrontSectionHeader(L10n.text("hotels_madinah", language), L10n.text("hotels_selected_badge", language)) }
                    items(madinah, key = { "d-${it.id}" }) { hotel ->
                        LaunchedEffect(hotel.id) {
                            if (previews[hotel.id] == null) {
                                runCatching { service.hotelDetail(hotel.id) }.getOrNull()?.let { detail ->
                                    previews[hotel.id] = HotelPreview(detail.images.sortedBy { it.position }.map { it.url }, detail)
                                }
                            }
                        }
                        val quote = bestHotelPackage(hotel, hotelPackages)
                        HotelStorefrontCard(
                            hotel = hotel,
                            preview = previews[hotel.id],
                            quote = quote,
                            language = language,
                            favorite = hotel.id in favorites,
                            onFavorite = {
                                favorites = if (hotel.id in favorites) favorites - hotel.id else favorites + hotel.id
                                favoritePrefs.edit().putStringSet("hotel_ids", favorites).apply()
                            },
                            onShare = { quote?.let { IumrahPackageShare.share(context, it, null, language) } ?: run { packageShareError = hotelText(language, "share_unavailable") } },
                            onOpen = { chrome.openHotel(hotel.id) },
                        )
                    }
                }

                if (loading && makkah.isEmpty() && madinah.isEmpty()) {
                    item { LoadingStorefront(language) }
                }
                if (error != null && makkah.isEmpty() && madinah.isEmpty()) {
                    item { StorefrontInfoCard(error!!) }
                }
                item { HotelCareShowcaseCard(language) { carePresented = true } }
            }

            HotelsBoard.FLIGHTS -> {
                // iOS Flights v15: single discovery surface (no duplicate showcase
                // hero or competing published-flight filter below it).
                item {
                    FlightDiscoveryPanel(
                        language = language,
                        service = flightDiscovery,
                        airports = airports,
                        journey = journey,
                        chrome = chrome,
                        showEmbeddedAssembly = false,
                    )
                }
                // The second iOS section contains only ready, already-generated
                // flight-first packages; unpriced catalogue rows stay in the
                // iumrah-recommends carousel instead of pretending to be bookable.
                val ready = flights.mapNotNull { option ->
                    bestFlightPackage(option, flightPackages)?.let { preview -> option to preview }
                }
                if (ready.isNotEmpty()) {
                    item { StorefrontSectionHeader(hotelText(language, "published"), hotelText(language, "current")) }
                    items(ready, key = { "ready-flight-${it.first.id}" }) { (option, preview) ->
                        StorefrontFlightCard(
                            option = option,
                            packageSnapshot = preview,
                            language = language,
                            loading = loading,
                            onClick = { chrome.openFlightPackage(preview.id) },
                        )
                    }
                }
            }

            HotelsBoard.SUNDAY -> {
                item {
                    ShowcaseHero(
                        image = R.drawable.iumrah_sunday_club_showcase,
                        title = "Sunday Umrah Club",
                        description = hotelText(language, "weekend_body"),
                        note = hotelText(language, "weekend_note"),
                        imageBackground = Color.White,
                    )
                }
                item { SundayClubComingSoonCard(language) }
            }
        }
    }

    } // pull-to-refresh content

    // Mirrors iOS safeAreaInset(.bottom) on the flight-discovery board.
    // Lift the existing assembly UI above the floating root navigation; do
    // not duplicate it in the scrolling FlightDiscoveryPanel.
    if (board == HotelsBoard.FLIGHTS) {
        FlightAssemblyCard(
            language = language,
            state = journeyState,
            onContinue = { chrome.openHotelSelection() },
            onClear = {
                journey.clearStagedAviasalesFlights()
                journey.clearPublishedFlightSelection()
                assemblyExpanded = false
            },
            expandedState = assemblyExpanded,
            onExpandedChange = { assemblyExpanded = it },
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(start = 18.dp, end = 18.dp, bottom = 104.dp),
        )
    }
    } // storefront overlay root

    if (airportPicker) {
        AirportPickerDialog(
            language = language,
            service = airports,
            current = journeyState.trip.originAirport,
            fallbackCode = origin,
            onDismiss = { airportPicker = false },
            onSelect = { airport ->
                journey.updateTrip(journeyState.trip.copy(origin = airport.iata.uppercase(), originAirport = airport))
                airportPicker = false
            },
        )
    }
    if (carePresented) {
        HotelCareContactSheet(language = language, onDismiss = { carePresented = false })
    }
    if (friendsCalculatorPresented) {
        IumrahFriendsCalculatorSheet(
            language = language,
            service = service,
            flightPackages = flightPackages,
            makkahHotels = makkah,
            madinahHotels = madinah,
            onDismiss = { friendsCalculatorPresented = false },
        )
    }
    packageShareError?.let { message ->
        AlertDialog(
            onDismissRequest = { packageShareError = null },
            title = { Text(hotelText(language, "share_error_title"), fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { packageShareError = null }) { Text(hotelText(language, "share_error_ok")) } },
        )
    }
}

@Composable
private fun AirportSelector(
    language: AppLanguage,
    airport: Airport?,
    fallbackCode: String,
    locating: Boolean,
    onClick: () -> Unit,
    onLocate: () -> Unit,
) {
    IumrahPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 17.dp,
        background = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(17.dp), Color(0xFF007AFF))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = L10n.text("airport_title", language),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                )
                Text(
                    text = airport?.compactTitle ?: fallbackCode,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (airport != null) {
                    Text(
                        text = airport.name,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IumrahPressable(
                onClick = onLocate,
                modifier = Modifier.size(42.dp),
                cornerRadius = 21.dp,
                background = Color.Transparent,
                shadowElevation = 0.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (locating) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else CupertinoIcon(CupertinoSymbol.Location, null, Modifier.size(17.dp), MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun AirportPickerDialog(
    language: AppLanguage,
    service: AirportSearchService,
    current: Airport?,
    fallbackCode: String,
    onDismiss: () -> Unit,
    onSelect: (Airport) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(current?.iata ?: fallbackCode) }
    var results by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            loading = true
            results = runCatching { service.search(query.trim(), 12) }.getOrDefault(emptyList())
            loading = false
        } else results = emptyList()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L10n.text("airport_title", language), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(hotelText(language, "airport_search_hint"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    IumrahPressable(onClick = { showMap = true }, modifier = Modifier.size(42.dp), cornerRadius = 21.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Globe, null, Modifier.size(18.dp)) }
                    }
                    IumrahPressable(onClick = onDismiss, modifier = Modifier.size(42.dp), cornerRadius = 21.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(18.dp)) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CupertinoIcon(CupertinoSymbol.Location, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                )
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                results.forEach { airport ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onSelect(airport) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(airport.compactTitle, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(airport.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (airport.iata.equals(current?.iata ?: fallbackCode, true)) CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(17.dp))
                    }
                }
            }
        }
    }
    if (showMap) {
        IumrahAirportMapDialog(
            language = language,
            service = service,
            current = current,
            fallbackCode = fallbackCode,
            onDismiss = { showMap = false },
            onSelect = { airport ->
                showMap = false
                onSelect(airport)
            },
        )
    }
}

@Composable
private fun StorefrontSegmentedControl(language: AppLanguage, selected: HotelsBoard, onSelect: (HotelsBoard) -> Unit) {
    val view = LocalView.current
    val segments = listOf(
        HotelsBoard.HOTELS to L10n.text("tab_hotels", language),
        HotelsBoard.FLIGHTS to hotelText(language, "flights"),
        HotelsBoard.SUNDAY to hotelText(language, "weekend"),
    )
    Row(
        Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { (board, label) ->
            val active = board == selected
            val fill by animateColorAsState(if (active) MaterialTheme.colorScheme.surface else Color.Transparent, label = "storefront-segment")
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(8.dp)).background(fill).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (board != selected) IumrahHaptics.selection(view)
                    onSelect(board)
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShowcaseHero(image: Int, title: String, description: String, note: String? = null, imageBackground: Color) {
    val shape = RoundedCornerShape(34.dp)
    Column(
        Modifier.fillMaxWidth().shadow(8.dp, shape, clip = false).clip(shape).background(MaterialTheme.colorScheme.surface).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), shape),
    ) {
        Box(Modifier.fillMaxWidth().background(imageBackground), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(image),
                contentDescription = title,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
            )
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Text(description, fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            note?.takeIf { it.isNotBlank() }?.let { noteText ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                    CupertinoIcon(CupertinoSymbol.ShieldCheck, null, Modifier.size(15.dp), Color(0xFF30B0C7))
                    Text(noteText, modifier = Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
                }
            }
        }
    }
}

@Composable
private fun StorefrontSectionHeader(title: String, eyebrow: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(eyebrow.uppercase(), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), letterSpacing = .7.sp)
        Text(title, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
    }
}

@Composable
private fun HotelStorefrontCard(
    hotel: HotelSummary,
    preview: HotelPreview?,
    quote: StorefrontPackageSnapshot?,
    language: AppLanguage,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().height(204.dp)) {
        val mediaWidth = (maxWidth * .31f).coerceIn(108.dp, 122.dp)
        Row(
            Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.surface)
                .border(.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), shape)
                .clickable { onOpen() },
        ) {
            Box(Modifier.width(mediaWidth).fillMaxHeight()) {
                HotelCollage(preview?.images.orEmpty(), hotel.coverImageURL)
                Row(Modifier.padding(9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MediaCircle(onFavorite) {
                        CupertinoIcon(if (favorite) CupertinoSymbol.HeartFill else CupertinoSymbol.Heart, null, Modifier.size(14.dp), if (favorite) Color(0xFFFF5C77) else Color.White)
                    }
                    MediaCircle(onShare) { CupertinoIcon(CupertinoSymbol.Share, null, Modifier.size(14.dp), Color.White) }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(hotel.name, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    hotel.stars?.let { stars ->
                        CupertinoRatingStars(stars)
                    }
                    hotel.rating?.let { Text(String.format(Locale.US, "%.1f", it), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    CupertinoIcon(CupertinoSymbol.Location, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Text(L10n.city(hotel.city, language), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                if (quote?.pricePerPerson != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money(quote.pricePerPerson), fontSize = 25.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.55).sp, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text(hotelText(language, "per_pilgrim"), fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), maxLines = 1)
                    }
                    quote.totalPackagePrice?.let {
                        Text(hotelText(language, "package_total").replace("%@", money(it)), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.height(38.dp)) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        Text(hotelText(language, "calculating"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 2)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(hotelText(language, "includes"), Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .28f))
                }
            }
        }
    }
}

@Composable
private fun HotelCollage(images: List<String>, fallback: String?) {
    val urls = buildList {
        addAll(images.filter { it.isNotBlank() }.take(3))
        if (isEmpty() && !fallback.isNullOrBlank()) add(fallback)
        while (size < 3 && isNotEmpty()) add(first())
    }
    if (urls.isEmpty()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            CupertinoIcon(CupertinoSymbol.Hotel, null, Modifier.size(30.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .25f))
        }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        StorefrontImage(urls[0], Modifier.fillMaxWidth().weight(.63f))
        Row(Modifier.fillMaxWidth().weight(.37f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            StorefrontImage(urls.getOrElse(1) { urls[0] }, Modifier.weight(1f).fillMaxSize())
            StorefrontImage(urls.getOrElse(2) { urls[0] }, Modifier.weight(1f).fillMaxSize())
        }
    }
}

@Composable
private fun StorefrontImage(raw: String?, modifier: Modifier = Modifier) {
    val url = AppConfig.absoluteUrl(raw)
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun MediaCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = .34f)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun FlightFilters(
    language: AppLanguage,
    options: List<StorefrontFlightOption>,
    origin: String?,
    destination: String?,
    onOrigin: (String?) -> Unit,
    onDestination: (String?) -> Unit,
) {
    val origins = buildSet {
        options.forEach { add(it.outbound.origin.uppercase()); it.inbound?.let { leg -> add(leg.origin.uppercase()) } }
    }.sorted()
    val destinations = buildSet {
        options.forEach { add(it.outbound.destination.uppercase()); it.inbound?.let { leg -> add(leg.destination.uppercase()) } }
    }.sorted()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FlightFilterMenu(Modifier.weight(1f), hotelText(language, "from"), origin, origins, hotelText(language, "all"), onOrigin)
        FlightFilterMenu(Modifier.weight(1f), hotelText(language, "to"), destination, destinations, hotelText(language, "all"), onDestination)
    }
}

@Composable
private fun FlightFilterMenu(
    modifier: Modifier,
    title: String,
    selection: String?,
    values: List<String>,
    all: String,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IumrahPressable(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().height(58.dp), cornerRadius = 18.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title.uppercase(), fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Text(selection ?: all, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                CupertinoIcon(CupertinoSymbol.ChevronDown, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(all) }, onClick = { onSelect(null); expanded = false })
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value, fontWeight = if (selection == value) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = if (selection == value) ({ CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(15.dp)) }) else null,
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun StorefrontFlightCard(
    option: StorefrontFlightOption,
    packageSnapshot: StorefrontPackageSnapshot?,
    language: AppLanguage,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), shape).clickable(enabled = packageSnapshot != null) { onClick() },
    ) {
        Box(Modifier.fillMaxWidth().height(118.dp)) {
            val media = packageSnapshot?.imageUrl?.takeIf { it.isNotBlank() }
                ?: packageSnapshot?.hotelImages?.firstOrNull()
            if (media != null) {
                StorefrontImage(media, Modifier.fillMaxSize())
            } else {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.iumrah_flights_showcase_v2),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .58f)))))
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.Bottom) {
                StorefrontAirlineLogo(option.outbound.airlineCode, 40)
                Spacer(Modifier.weight(1f))
                packageSnapshot?.let {
                    Text(packageTypeText(it, language), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .36f)).padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(routeTitle(option), fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(airlineTitle(option), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 2)
                }
                PackagePrice(packageSnapshot, loading, language)
            }
            Row(Modifier.fillMaxWidth()) {
                FlightTime(option.outbound, language, Modifier.weight(1f))
                option.inbound?.let { inbound ->
                    Box(Modifier.width(1.dp).height(34.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .1f)))
                    FlightTime(inbound, language, Modifier.weight(1f).padding(start = 12.dp))
                }
            }
            packageSnapshot?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CupertinoIcon(CupertinoSymbol.Suitcase, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Text(packageRouteText(it, language), fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 2)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    CupertinoIcon(CupertinoSymbol.ShieldCheck, null, Modifier.size(12.dp), if (packageSnapshot == null) MaterialTheme.colorScheme.onSurface.copy(alpha=.5f) else Color(0xFF30B0C7))
                    Text(if (packageSnapshot == null) "iumrah Flights Scanner" else generatedText(language), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (packageSnapshot == null) MaterialTheme.colorScheme.onSurface.copy(alpha=.52f) else Color(0xFF30B0C7))
                }
                Spacer(Modifier.weight(1f))
                if (packageSnapshot != null) CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha=.27f))
            }
        }
    }
}

@Composable
private fun PackagePrice(snapshot: StorefrontPackageSnapshot?, loading: Boolean, language: AppLanguage) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(132.dp)) {
        when {
            snapshot?.pricePerPerson != null -> {
                Text(money(snapshot.pricePerPerson), fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Text(packagePerPerson(language), fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), maxLines = 2)
            }
            loading -> {
                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                Text(calculatingPackage(language), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
            else -> {
                Text("—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(packageUnavailable(language), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), maxLines = 2)
            }
        }
    }
}

@Composable
private fun FlightTime(leg: StorefrontFlightLeg, language: AppLanguage, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(dayText(leg.departureAt, language), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("${clockText(leg.departureAt)}  ${leg.origin} → ${leg.destination}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 1)
    }
}

@Composable
private fun StorefrontAirlineLogo(codeRaw: String?, size: Int) {
    val code = codeRaw?.uppercase(Locale.US)?.takeIf { it.length == 2 }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (code != null) {
            AsyncImage(
                model = "https://www.gstatic.com/flights/airline_logos/70px/$code.png",
                contentDescription = code,
                modifier = Modifier.size((size - 6).dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size((size / 2).dp), Color(0xFF007AFF))
        }
    }
}

@Composable
private fun SundayClubComingSoonCard(language: AppLanguage) {
    val orange = Color(0xFFFF9C2E)
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(9.dp, shape, clip = false, ambientColor = orange.copy(alpha = .14f), spotColor = orange.copy(alpha = .14f))
            .clip(shape)
            .background(orange)
            .border(.8.dp, Color.Black.copy(alpha = .06f), shape)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .92f)), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.CalendarClock, null, Modifier.size(20.dp), orange)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sundaySoonBadge(language).uppercase(language.locale), fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = .65.sp, color = Color.Black.copy(alpha = .58f))
                Text("Sunday Umrah Club", fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.3).sp, color = Color.Black)
            }
        }
        Text(sundaySoonTitle(language), fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp, color = Color.Black)
        Text(sundaySoonBody(language), fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = Color.Black.copy(alpha = .65f))
    }
}

private fun sundaySoonBadge(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Скоро"
    AppLanguage.ENGLISH -> "Coming soon"
    AppLanguage.UZBEK -> "Tez orada"
    AppLanguage.UZBEK_CYRILLIC -> "Тез орада"
}
private fun sundaySoonTitle(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Умра для выходных"
    AppLanguage.ENGLISH -> "Umrah for the weekend"
    AppLanguage.UZBEK -> "Dam olish kunlari uchun Umra"
    AppLanguage.UZBEK_CYRILLIC -> "Дам олиш кунлари учун Умра"
}
private fun sundaySoonBody(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Скоро здесь появятся готовые пакеты Sunday Umrah Club — короткие поездки на выходные с уже собранными датами и маршрутом."
    AppLanguage.ENGLISH -> "Ready Sunday Umrah Club packages are coming here soon — short weekend trips with dates and route already prepared."
    AppLanguage.UZBEK -> "Tez orada bu yerda Sunday Umrah Club tayyor paketlari paydo bo‘ladi — dam olish kunlariga mos, sanalari va yo‘nalishi oldindan tuzilgan qisqa safarlar."
    AppLanguage.UZBEK_CYRILLIC -> "Тез орада бу ерда Sunday Umrah Club тайёр пакетлари пайдо бўлади — дам олиш кунларига мос, саналари ва йўналиши олдиндан тузилган қисқа сафарлар."
}

@Composable
private fun NoFlightsCard(language: AppLanguage) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(28.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        Text(hotelText(language, "no_flights"), fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(hotelText(language, "change_filter"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun LoadingStorefront(language: AppLanguage) {
    Row(Modifier.fillMaxWidth().height(92.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(hotelText(language, "loading"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
    }
}

@Composable
private fun StorefrontInfoCard(message: String) {
    Text(message, Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(18.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))
}

@Composable
fun HotelCareShowcaseCard(language: AppLanguage, onClick: () -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), shape),
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.iumrah_care_showcase),
            contentDescription = "iumrah Care",
            modifier = Modifier.fillMaxWidth().background(Color(0xFF040917)),
            contentScale = ContentScale.FillWidth,
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("iumrah Care", fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
            Text(
                careText(language),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
            )
            IumrahPrimaryButton(title = careContactText(language), onClick = onClick)
        }
    }
}

@Composable
fun HotelCareContactSheet(language: AppLanguage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("iumrah Care", fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                    Text(
                        L10n.text("hotel_care_prebook_body", language),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f),
                    )
                }
                IumrahPressable(onClick = onDismiss, modifier = Modifier.size(42.dp), cornerRadius = 21.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(17.dp)) }
                }
            }
            CareContactRow(CupertinoSymbol.Send, "Telegram", "@saudiclub966") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/saudiclub966")))
            }
            CareContactRow(CupertinoSymbol.Phone, L10n.text("hotel_care_call", language), "+998 50 889 88 45") {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+998508898845")))
            }
        }
    }
}

@Composable
private fun CareContactRow(icon: CupertinoSymbol, title: String, value: String, onClick: () -> Unit) {
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, background = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFFF5C77).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                CupertinoIcon(icon, null, Modifier.size(18.dp), Color(0xFFFF5C77))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
            CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
        }
    }
}

private fun isPublicPackageId(value: String): Boolean = value.length == 10 && value.all(Char::isDigit)

/** Exact HotelStorefrontStore admission rule from iOS. The server registry is the
 * source of truth: only immutable ready snapshots with a public 10-digit Package ID
 * and positive package prices are allowed onto a hotel card. The first variant is
 * selected by the same variant-index/day/id ordering used on iOS (not by cheapest
 * price, which previously made Android show a different package). */
private fun bestHotelPackage(hotel: HotelSummary, packages: List<StorefrontPackageSnapshot>): StorefrontPackageSnapshot? =
    packages.asSequence()
        .filter { it.entryMode.equals("hotel-first", true) && it.status.equals("ready", true) }
        .filter { isPublicPackageId(it.id) }
        .filter { p ->
            val anchor = p.hotelFirstAnchorHotelId ?: p.makkahHotelId ?: p.madinahHotelId
            anchor == hotel.id
        }
        .filter { (it.pricePerPerson ?: 0.0) > 0.0 && (it.totalPackagePrice ?: 0.0) > 0.0 }
        .sortedWith(
            compareBy<StorefrontPackageSnapshot> { it.hotelFirstVariantIndex ?: 99 }
                .thenBy { it.totalDays ?: Int.MAX_VALUE }
                .thenBy { it.id },
        )
        .firstOrNull()

/** Flight First uses the same server-owned snapshot list as iOS. Preserve server
 * order and never synthesize a client-side price. */
private fun bestFlightPackage(option: StorefrontFlightOption, packages: List<StorefrontPackageSnapshot>): StorefrontPackageSnapshot? =
    packages.firstOrNull { item ->
        item.status.equals("ready", true) &&
            (item.outboundOfferId == option.id || item.inboundOfferId == option.id) &&
            (item.pricePerPerson ?: 0.0) > 0.0 &&
            (item.totalPackagePrice ?: 0.0) > 0.0
    }

private fun routeTitle(option: StorefrontFlightOption): String = option.inbound?.let { "${option.outbound.origin} → ${option.outbound.destination} · ${it.origin} → ${it.destination}" }
    ?: "${option.outbound.origin} → ${option.outbound.destination}"

private fun airlineTitle(option: StorefrontFlightOption): String {
    val first = "${option.outbound.airline} ${option.outbound.flightNumber}".trim()
    return option.inbound?.let { "$first · ${it.airline} ${it.flightNumber}".trim() } ?: first
}

private fun packageRouteText(snapshot: StorefrontPackageSnapshot, language: AppLanguage): String {
    val route = snapshot.routeSummary?.takeIf { it.isNotBlank() } ?: listOfNotNull(snapshot.originCode, snapshot.destinationCode).joinToString(" → ")
    val days = snapshot.totalDays ?: 0
    val suffix = when (language) {
        AppLanguage.RUSSIAN -> "$days дн."
        AppLanguage.ENGLISH -> "$days days"
        AppLanguage.UZBEK -> "$days kun"
        AppLanguage.UZBEK_CYRILLIC -> "$days кун"
    }
    return listOf(route, suffix).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun packageTypeText(snapshot: StorefrontPackageSnapshot, language: AppLanguage): String {
    val scope = when {
        (snapshot.madinahNights ?: 0) > 0 -> when (language) {
            AppLanguage.RUSSIAN -> "Мекка + Медина"
            AppLanguage.ENGLISH -> "Makkah + Madinah"
            AppLanguage.UZBEK -> "Makka + Madina"
            AppLanguage.UZBEK_CYRILLIC -> "Макка + Мадина"
        }
        else -> when (language) {
            AppLanguage.RUSSIAN -> "Только Мекка"
            AppLanguage.ENGLISH -> "Makkah only"
            AppLanguage.UZBEK -> "Faqat Makka"
            AppLanguage.UZBEK_CYRILLIC -> "Фақат Макка"
        }
    }
    val tier = snapshot.tier?.replaceFirstChar { it.uppercase() }.orEmpty()
    return listOf(scope, tier).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun money(value: Double): String = "$${String.format(Locale.US, "%.0f", value)}"

private fun clockText(raw: String): String = runCatching {
    OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrElse { raw.take(5) }

private fun dayText(raw: String, language: AppLanguage): String = runCatching {
    val locale = when (language) {
        AppLanguage.RUSSIAN -> Locale("ru")
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn")
        AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl")
    }
    OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("d MMM", locale))
}.getOrElse { raw.take(10) }

private fun shareHotel(context: Context, hotel: HotelSummary) {
    val token = Base64.encodeToString(hotel.id.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    val text = "${hotel.name}\nhttps://iumrah.app/h/$token"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, hotel.name)) }
}

private fun generatedText(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Сгенерировано iumrah Configurator"
    AppLanguage.ENGLISH -> "Generated by iumrah Configurator"
    AppLanguage.UZBEK -> "iumrah Configurator yaratdi"
    AppLanguage.UZBEK_CYRILLIC -> "iumrah Configurator яратди"
}

private fun packagePerPerson(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "пакет · 1 человек"
    AppLanguage.ENGLISH -> "package · 1 person"
    AppLanguage.UZBEK -> "paket · 1 kishi"
    AppLanguage.UZBEK_CYRILLIC -> "пакет · 1 киши"
}

private fun calculatingPackage(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Считаем пакет"
    AppLanguage.ENGLISH -> "Calculating package"
    AppLanguage.UZBEK -> "Paket hisoblanmoqda"
    AppLanguage.UZBEK_CYRILLIC -> "Пакет ҳисобланмоқда"
}

private fun packageUnavailable(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "нет пары 2–15 дней"
    AppLanguage.ENGLISH -> "no 2–15 day pair"
    AppLanguage.UZBEK -> "2–15 kunlik juftlik yo‘q"
    AppLanguage.UZBEK_CYRILLIC -> "2–15 кунлик жуфтлик йўқ"
}

private fun careText(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Есть вопросы об отеле, пакете или сезоне? До бронирования можно напрямую проконсультироваться с Абдулазизом, основателем iumrah."
    AppLanguage.ENGLISH -> "Questions about the hotel, package or season? Speak directly with Abdulaziz, iumrah founder, before booking."
    AppLanguage.UZBEK -> "Mehmonxona, paket yoki mavsum haqida savol bormi? Bron qilishdan oldin iumrah asoschisi Abdulaziz bilan bevosita maslahatlashishingiz mumkin."
    AppLanguage.UZBEK_CYRILLIC -> "Меҳмонхона, пакет ёки мавсум ҳақида савол борми? Брон қилишдан олдин iumrah асосчиси Абдулазиз билан бевосита маслаҳатлашишингиз мумкин."
}

private fun careContactText(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Связаться с iumrah Care"
    AppLanguage.ENGLISH -> "Contact iumrah Care"
    AppLanguage.UZBEK -> "iumrah Care bilan bog‘lanish"
    AppLanguage.UZBEK_CYRILLIC -> "iumrah Care билан боғланиш"
}

@SuppressLint("MissingPermission")
private suspend fun resolveNearestDepartureAirport(context: Context, service: AirportSearchService): Airport? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val location = withContext(Dispatchers.IO) {
        val known = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        known ?: withTimeoutOrNull(3500) { requestSingleLocation(manager) }
    } ?: return null

    val queries = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val place = Geocoder(context, Locale.ENGLISH).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            listOfNotNull(place?.locality, place?.subAdminArea, place?.adminArea, place?.countryName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.ENGLISH) }
        }.getOrDefault(emptyList())
    }
    val candidates = mutableMapOf<String, Airport>()
    for (query in queries) {
        runCatching { service.search(query, 12) }.getOrDefault(emptyList()).forEach { candidates[it.iata.uppercase()] = it }
    }
    return candidates.values.map { airport ->
        val result = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, airport.lat, airport.lon, result)
        airport to result[0].toDouble()
    }.filter { it.second <= 250_000.0 }.minByOrNull { it.second }?.first
}

@SuppressLint("MissingPermission")
private suspend fun requestSingleLocation(manager: LocationManager): Location? = suspendCancellableCoroutine { continuation ->
    val provider = when {
        runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
        runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
        else -> null
    }
    if (provider == null) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            manager.removeUpdates(this)
            if (continuation.isActive) continuation.resume(location)
        }
        @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }
    continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
    runCatching { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
        .onFailure { if (continuation.isActive) continuation.resume(null) }
}

private fun hotelText(language: AppLanguage, key: String): String {
    val values = when (language) {
        AppLanguage.ENGLISH -> mapOf(
            "flights" to "Flights", "weekend" to "Weekend",
            "hotel_body" to "Hotels curated by iumrah for a calmer Umrah — convenient locations, trusted service and a ready package price.",
            "hotel_note" to "Choose your departure airport once. iumrah Configurator uses the next compatible published return, falls back to Tashkent for the return when needed, and prices each hotel by category: up to 3★ Standard, 4★ Comfort, 5★ Luxury.",
            "flights_body" to "iumrah Flights Scanner finds current direct routes from Uzbekistan to Saudi Arabia and combines compatible legs into ready Umrah packages.",
            "flights_note" to "Jeddah routes can form 2–3 day Makkah-only Comfort packages. Trips of 4–15 days form Makkah + Madinah Standard packages, returning to the same city when possible or Tashkent as fallback.",
            "weekend_body" to "Umrah that fits into your weekend. Sunday Umrah Club will collect short ready-to-go journeys here.",
            "weekend_note" to "Weekend packages will appear here as published departures. No placeholder offers are shown.",
            "loading" to "Preparing hotels and package prices…", "published" to "Find us with iumrah Flights Scanner", "current" to "CURRENT", "per_pilgrim" to "per pilgrim",
            "package_total" to "%@ · package for two", "calculating" to "Preparing price…", "includes" to "Flight + hotel + iumrah Services",
            "from" to "From", "to" to "To", "all" to "All", "no_flights" to "iumrah Flights Scanner found no flights for this route", "change_filter" to "Change the departure or arrival airport.",
            "airport_search_hint" to "Search by city or IATA code", "share_unavailable" to "The package could not be prepared for sharing. Refresh pricing and try again.", "share_error_title" to "Could not share", "share_error_ok" to "OK", "load_error" to "Could not load the current hotel catalogue.",
        )
        AppLanguage.RUSSIAN -> mapOf(
            "flights" to "Авиабилеты", "weekend" to "Weekend",
            "hotel_body" to "Отели, отобранные iumrah для более спокойной Умры — удобное расположение, проверенный сервис и готовая цена пакета.",
            "hotel_note" to "Выберите аэропорт вылета один раз. iumrah Configurator подберёт следующий совместимый обратный рейс, при необходимости использует Ташкент как fallback для возврата и рассчитает каждый отель по категории: до 3★ Standard, 4★ Comfort, 5★ Luxury.",
            "flights_body" to "iumrah Flights Scanner находит актуальные прямые рейсы из городов Узбекистана в Саудовскую Аравию и собирает совместимые рейсы в готовые пакеты Умры.",
            "flights_note" to "Маршруты через Джидду могут стать Comfort-пакетом только Мекка на 2–3 дня. Поездки на 4–15 дней становятся Standard-пакетом Мекка + Медина с возвратом в исходный город или, если пары нет, в Ташкент.",
            "weekend_body" to "Умра, которая помещается в ваши выходные. Здесь Sunday Umrah Club соберёт короткие готовые поездки.",
            "weekend_note" to "Weekend-пакеты появятся здесь как опубликованные готовые вылеты. Тестовые предложения не показываются.",
            "loading" to "Готовим отели и цены пакетов…", "published" to "Найдите нас с помощью iumrah Flights Scanner", "current" to "АКТУАЛЬНО", "per_pilgrim" to "на паломника",
            "package_total" to "%@ · пакет для двоих", "calculating" to "Готовим цену…", "includes" to "Перелёт + отель + iumrah Services",
            "from" to "Откуда", "to" to "Куда", "all" to "Все", "no_flights" to "iumrah Flights Scanner не нашёл рейсов по этому маршруту", "change_filter" to "Измените аэропорт отправления или прибытия.",
            "airport_search_hint" to "Поиск по городу или IATA-коду", "share_unavailable" to "Не удалось подготовить пакет для отправки. Обновите цены и попробуйте ещё раз.", "share_error_title" to "Не удалось поделиться", "share_error_ok" to "Понятно", "load_error" to "Не удалось загрузить актуальный каталог отелей.",
        )
        AppLanguage.UZBEK -> mapOf(
            "flights" to "Aviachiptalar", "weekend" to "Weekend",
            "hotel_body" to "iumrah sokinroq Umra uchun tanlagan mehmonxonalar — qulay joylashuv, ishonchli xizmat va tayyor paket narxi.",
            "hotel_note" to "Jo‘nash aeroportini bir marta tanlang. iumrah Configurator keyingi mos qaytish reysini topadi, kerak bo‘lsa qaytishda Toshkent fallback sifatida ishlatiladi va har bir mehmonxonani toifasiga ko‘ra hisoblaydi: 3★ gacha Standard, 4★ Comfort, 5★ Luxury.",
            "flights_body" to "iumrah Flights Scanner O‘zbekiston shaharlaridan Saudiya Arabistoniga dolzarb to‘g‘ridan-to‘g‘ri reyslarni topadi va mos reyslarni tayyor Umra paketlariga birlashtiradi.",
            "flights_note" to "Jidda yo‘nalishlari 2–3 kunlik faqat Makka Comfort paketiga aylanishi mumkin. 4–15 kunlik safarlar Makka + Madina Standard paketiga aylanadi; qaytish imkon qadar boshlang‘ich shaharga, aks holda Toshkentga.",
            "weekend_body" to "Dam olish kunlariga sig‘adigan Umra. Sunday Umrah Club bu yerda qisqa va tayyor safarlarni jamlaydi.",
            "weekend_note" to "Weekend paketlar bu yerda e’lon qilingan tayyor jo‘nashlar sifatida paydo bo‘ladi. Sinov takliflari ko‘rsatilmaydi.",
            "loading" to "Mehmonxonalar va paket narxlari tayyorlanmoqda…", "published" to "iumrah Flights Scanner yordamida bizni toping", "current" to "DOLZARB", "per_pilgrim" to "bir ziyoratchiga",
            "package_total" to "%@ · ikki kishilik paket", "calculating" to "Narx tayyorlanmoqda…", "includes" to "Parvoz + mehmonxona + iumrah Services",
            "from" to "Qayerdan", "to" to "Qayerga", "all" to "Barchasi", "no_flights" to "iumrah Flights Scanner bu yo‘nalishda reys topmadi", "change_filter" to "Jo‘nash yoki yetib borish aeroportini o‘zgartiring.",
            "airport_search_hint" to "Shahar yoki IATA kodi bo‘yicha qidiring", "share_unavailable" to "Paketni ulashish uchun tayyorlab bo‘lmadi. Narxlarni yangilang va qayta urinib ko‘ring.", "share_error_title" to "Ulashib bo‘lmadi", "share_error_ok" to "Tushunarli", "load_error" to "Mehmonxonalar katalogini yuklab bo‘lmadi.",
        )
        AppLanguage.UZBEK_CYRILLIC -> mapOf(
            "flights" to "Авиачипталар", "weekend" to "Weekend",
            "hotel_body" to "iumrah сокинроқ Умра учун танлаган меҳмонхоналар — қулай жойлашув, ишончли хизмат ва тайёр пакет нархи.",
            "hotel_note" to "Жўнаш аэропортини бир марта танланг. iumrah Configurator кейинги мос қайтиш рейсини топади, керак бўлса қайтишда Тошкент fallback сифатида ишлатилади ва ҳар бир меҳмонхонани тоифасига кўра ҳисоблайди: 3★ гача Standard, 4★ Comfort, 5★ Luxury.",
            "flights_body" to "iumrah Flights Scanner Ўзбекистон шаҳарларидан Саудия Арабистонига долзарб тўғридан-тўғри рейсларни топади ва мос рейсларни тайёр Умра пакетларига бирлаштиради.",
            "flights_note" to "Жидда йўналишлари 2–3 кунлик фақат Макка Comfort пакетига айланиши мумкин. 4–15 кунлик сафарлар Макка + Мадина Standard пакетига айланади; қайтиш имкон қадар бошланғич шаҳарга, акс ҳолда Тошкентга.",
            "weekend_body" to "Дам олиш кунларига сиғадиган Умра. Sunday Umrah Club бу ерда қисқа ва тайёр сафарларни жамлайди.",
            "weekend_note" to "Weekend пакетлар бу ерда эълон қилинган тайёр жўнашлар сифатида пайдо бўлади. Синов таклифлари кўрсатилмайди.",
            "loading" to "Меҳмонхоналар ва пакет нархлари тайёрланмоқда…", "published" to "iumrah Flights Scanner ёрдамида бизни топинг", "current" to "ДОЛЗАРБ", "per_pilgrim" to "бир зиёратчига",
            "package_total" to "%@ · икки кишилик пакет", "calculating" to "Нарх тайёрланмоқда…", "includes" to "Парвоз + меҳмонхона + iumrah Services",
            "from" to "Қаердан", "to" to "Қаерга", "all" to "Барчаси", "no_flights" to "iumrah Flights Scanner бу йўналишда рейс топмади", "change_filter" to "Жўнаш ёки етиб бориш аэропортини ўзгартиринг.",
            "airport_search_hint" to "Шаҳар ёки IATA коди бўйича қидиринг", "share_unavailable" to "Пакетни улашиш учун тайёрлаб бўлмади. Нархларни янгиланг ва қайта уриниб кўринг.", "share_error_title" to "Улашиб бўлмади", "share_error_ok" to "Тушунарли", "load_error" to "Меҳмонхоналар каталогини юклаб бўлмади.",
        )
    }
    return values[key] ?: key
}
