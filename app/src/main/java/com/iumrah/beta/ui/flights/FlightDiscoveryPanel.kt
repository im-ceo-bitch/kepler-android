@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.flights

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.setSelectedDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.flight.AviasalesFlightDiscoveryService
import com.iumrah.beta.data.flight.FlightDiscoveryCalendarDay
import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.data.flight.FlightFavoriteRecord
import com.iumrah.beta.data.flight.FlightFavoritesStore
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.data.flight.FlightDataParity
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.FlightTripType
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageFlightPath
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.models.flight.Airport
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.trip.IumrahAirportRouteMapDialog
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class DiscoveryTripType { ONE_WAY, ROUND_TRIP }
private enum class DiscoveryAirportRole { ORIGIN, DESTINATION }

@Composable
fun FlightDiscoveryPanel(
    language: AppLanguage,
    service: AviasalesFlightDiscoveryService,
    airports: AirportSearchService,
    journey: JourneyStore,
    chrome: AppChromeStore,
    showEmbeddedAssembly: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val journeyState by journey.state.collectAsState()
    val trip = journeyState.trip
    val favoritesStore = remember { FlightFavoritesStore(context) }

    var originAirport by remember(trip.originAirport?.iata, trip.originCode) { mutableStateOf(trip.originAirport) }
    var originCode by remember(trip.originCode) { mutableStateOf(trip.originCode.uppercase()) }
    var destinationAirport by remember { mutableStateOf<Airport?>(null) }
    var destinationCode by remember(trip.outboundDestinationCode) { mutableStateOf(trip.outboundDestinationCode.uppercase()) }
    var tripType by remember(trip.isRoundTripFlight) { mutableStateOf(if (trip.isRoundTripFlight) DiscoveryTripType.ROUND_TRIP else DiscoveryTripType.ONE_WAY) }
    var departureDate by remember(trip.departureDate) { mutableStateOf(maxOf(LocalDate.now(), trip.departureDate)) }
    var returnDate by remember(trip.returnDate, departureDate) { mutableStateOf(if (trip.returnDate.isAfter(departureDate)) trip.returnDate else departureDate.plusDays(7)) }
    var adults by remember(trip.adults) { mutableStateOf(maxOf(1, trip.adults)) }
    var children by remember(trip.children) { mutableStateOf(maxOf(0, trip.children)) }
    var infants by remember(trip.infants) { mutableStateOf(maxOf(0, trip.infants)) }
    var directOnly by rememberSaveable { mutableStateOf(false) }
    var mode by remember { mutableStateOf(FlightSearchMode.GLOBAL) }
    var selectedAirlines by remember { mutableStateOf<Set<String>>(emptySet()) }

    var offers by remember { mutableStateOf<List<FlightDiscoveryOffer>>(emptyList()) }
    var calendar by remember { mutableStateOf<List<FlightDiscoveryCalendarDay>>(emptyList()) }
    var currency by remember { mutableStateOf("usd") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(favoritesStore.records()) }
    var recommended by remember { mutableStateOf<List<CuratedFlightRecommendation>>(emptyList()) }
    var recommendedLoading by remember { mutableStateOf(false) }
    var recommendedError by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    var airportPicker by remember { mutableStateOf<DiscoveryAirportRole?>(null) }
    var routeMap by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var passengerPicker by remember { mutableStateOf(false) }
    var airlinePicker by remember { mutableStateOf(false) }
    var priceChart by remember { mutableStateOf(false) }
    var favoritesSheet by remember { mutableStateOf(false) }
    var selectedOffer by remember { mutableStateOf<FlightDiscoveryOffer?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    suspend fun refresh() {
        if (originCode.length != 3 || destinationCode.length != 3 || originCode.equals(destinationCode, true)) {
            offers = emptyList(); calendar = emptyList(); return
        }
        loading = true
        error = null
        val departure = departureDate.toString()
        val month = departure.take(7)
        val inbound = if (tripType == DiscoveryTripType.ROUND_TRIP) returnDate.toString() else null
        runCatching {
            coroutineScope {
                val cal = async { service.calendar(originCode, destinationCode, month, directOnly) }
                val exact = async { service.offers(originCode, destinationCode, departure, inbound, directOnly, 100) }
                cal.await() to exact.await()
            }
        }.onSuccess { (cal, exact) ->
            calendar = cal.days
            currency = exact.currency.ifBlank { cal.currency }
            val selectedDayRows = exact.offers.toMutableList()
            if (selectedDayRows.size < 6) {
                runCatching { service.offers(originCode, destinationCode, month, inbound, directOnly, 100) }
                    .getOrNull()?.offers?.filter { it.departureAt.take(10) == departure }?.let(selectedDayRows::addAll)
            }
            cal.days.firstOrNull { it.date == departure }?.offer?.let(selectedDayRows::add)
            offers = selectedDayRows.distinctBy { listOf(it.originAirport, it.destinationAirport, it.airlineCode, it.flightNumber, it.departureAt, it.price.toInt()).joinToString("|") }
            favorites = favoritesStore.reconcile(offers, currency, language)
        }.onFailure {
            if (it is CancellationException) throw it
            error = discoveryText(language, "load_error")
        }
        loading = false
    }

    LaunchedEffect(mode, originCode, destinationCode, departureDate, returnDate, tripType, directOnly, adults, children, infants, trip.scope) {
        if (mode == FlightSearchMode.GLOBAL) {
            delay(320)
            hasSearched = true
            refresh()
        }
    }

    LaunchedEffect(mode, originCode, destinationCode, departureDate, returnDate, trip.scope) {
        if (mode == FlightSearchMode.IUMRAH) {
            recommendedLoading = true
            recommendedError = null
            try {
                recommended = service.recommendations(trip.copy(origin = originCode, originAirport = originAirport))
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                recommended = emptyList()
                recommendedError = discoveryText(language, "load_error")
            } finally {
                recommendedLoading = false
            }
        }
    }

    val filtered = remember(offers, selectedAirlines) {
        offers.filter { selectedAirlines.isEmpty() || it.airlineCode.uppercase() in selectedAirlines }
    }
    val ranked = remember(filtered) {
        filtered.sortedWith(compareByDescending<FlightDiscoveryOffer> { it.isDirect }
            .thenBy { if (it.price > 0) it.price else Double.MAX_VALUE }
            .thenBy { if (it.durationMinutes > 0) it.durationMinutes else Int.MAX_VALUE }
            .thenBy { it.departureAt })
    }
    val cheapest = filtered.map { it.price }.filter { it > 0 }.minOrNull()
    val fastest = filtered.map { it.durationMinutes }.filter { it > 0 }.minOrNull()
    val selectedDay = departureDate.toString()
    val nearby = remember(calendar, selectedDay) {
        calendar.filter { it.date != selectedDay }.sortedWith(compareBy<FlightDiscoveryCalendarDay> {
            runCatching { kotlin.math.abs(LocalDate.parse(it.date).toEpochDay() - departureDate.toEpochDay()) }.getOrDefault(Long.MAX_VALUE)
        }.thenBy { it.price }).take(12)
    }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        FlightSearchModeBar(language, mode) { mode = it }
        FlightUnifiedConfigurationCard(
            language = language,
            originCode = originCode, originName = originAirport?.city ?: airportCity(originCode),
            destinationCode = destinationCode, destinationName = destinationAirport?.city ?: airportCity(destinationCode),
            routeScope = trip.scope,
            dateSummary = dateLabel(language, departureDate, if (mode == FlightSearchMode.IUMRAH || tripType == DiscoveryTripType.ROUND_TRIP) returnDate else null),
            travelerSummary = passengerLabel(language, adults + children + infants),
            onOrigin = { airportPicker = DiscoveryAirportRole.ORIGIN },
            onDestination = { airportPicker = DiscoveryAirportRole.DESTINATION },
            onSwap = {
                val oldAirport = originAirport; val oldCode = originCode
                originAirport = destinationAirport; originCode = destinationCode
                destinationAirport = oldAirport; destinationCode = oldCode
                // A user may swap the search to find the return flight. Preserve
                // an already staged outbound ticket until a new offer is selected.
                if (journeyState.stagedAviasalesOutbound == null && journeyState.stagedAviasalesRoundTrip == null)
                    journey.updateTrip(trip.copy(origin = originCode, originAirport = originAirport))
            },
            onScope = { selected ->
                val newTrip = trip.copy(scope = selected,
                    arrivalAirport = if (selected == JourneyScope.MAKKAH_ONLY) SaudiArrivalAirport.JEDDAH else trip.arrivalAirport)
                if (journeyState.stagedAviasalesOutbound != null || journeyState.stagedAviasalesRoundTrip != null)
                    journey.updateFlightScopePreservingSelection(selected)
                else journey.updateTrip(newTrip)
                if (selected == JourneyScope.MAKKAH_ONLY) { destinationCode = "JED"; destinationAirport = null }
            },
            onDates = { datePicker = true }, onTravelers = { passengerPicker = true }, onMap = { routeMap = true },
        )

        if (mode == FlightSearchMode.GLOBAL) {
            DiscoverySegmented(language, tripType) { type ->
                tripType = type
                if (type == DiscoveryTripType.ROUND_TRIP && !returnDate.isAfter(departureDate)) returnDate = departureDate.plusDays(7)
            }
            FlightDiscoveryActions(language, directOnly, selectedAirlines.isNotEmpty(),
                onChart = { priceChart = true }, onDirect = { directOnly = !directOnly }, onAirlines = { airlinePicker = true })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(discoveryText(language, "saved"), Modifier.weight(1f), fontSize=12.sp,
                    color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))
                DiscoveryChip(CupertinoSymbol.Heart, "${favorites.size}", favorites.isNotEmpty()) { favoritesSheet = true }
            }

        if (loading && ranked.isEmpty()) {
            DiscoveryLoadingCard(language)
        } else if (error != null && ranked.isEmpty()) {
            DiscoveryInfoCard(error!!, discoveryText(language, "retry")) { scope.launch { refresh() } }
        } else if (ranked.isEmpty()) {
            DiscoveryInfoCard(discoveryText(language, "empty"), discoveryText(language, "change")) { datePicker = true }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(discoveryText(language, "results"), fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
                    Text("${localizedDate(language, departureDate)} · ${ranked.size}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                }
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            ranked.forEach { offer ->
                FlightDiscoveryCard(
                    language = language,
                    offer = offer,
                    currency = currency,
                    cheapest = cheapest != null && kotlin.math.abs(offer.price - cheapest) < .5,
                    fastest = fastest != null && offer.durationMinutes == fastest,
                    favorite = favorites.any { it.offer.monitorKey == offer.monitorKey },
                    onFavorite = {
                        favorites = favoritesStore.toggle(offer, currency)
                        if (favorites.any { it.offer.monitorKey == offer.monitorKey } && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onClick = { selectedOffer = offer },
                )
            }
        }

        if (nearby.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(discoveryText(language, "nearby"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    nearby.forEach { day ->
                        IumrahPressable(onClick = {
                            departureDate = runCatching { LocalDate.parse(day.date) }.getOrDefault(departureDate)
                            if (tripType == DiscoveryTripType.ROUND_TRIP && !returnDate.isAfter(departureDate)) returnDate = departureDate.plusDays(7)
                        }, modifier = Modifier.width(130.dp).height(78.dp), cornerRadius = 20.dp, background = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(localizedDate(language, runCatching { LocalDate.parse(day.date) }.getOrDefault(departureDate)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), maxLines = 1)
                                Spacer(Modifier.height(5.dp))
                                Text(money(day.price, currency), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.Top) {
            Icon(CupertinoSymbol.InfoCircle, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            Spacer(Modifier.width(8.dp))
            Text(discoveryText(language, "source_note"), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        }
        if (hasSearched && !loading && ranked.size < 4) {
            FlightPartnerGatewayCard(language) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.aviasales.com/"))) }
            }
        }
        } else {
            val outbound = recommended.filter { item ->
                item.inbound == null && item.effectiveJourneyRole == "outbound" &&
                    item.outbound.origin.equals(originCode, true) && item.outbound.destination.equals(destinationCode, true)
            }.sortedBy { runCatching { kotlin.math.abs(LocalDate.parse(it.outboundDate).toEpochDay() - departureDate.toEpochDay()) }.getOrDefault(Long.MAX_VALUE) }
            val inbound = recommended.filter { item ->
                item.inbound == null && item.effectiveJourneyRole == "return" &&
                    item.outbound.origin.uppercase() in (if (trip.scope == JourneyScope.MAKKAH_ONLY) setOf("JED") else setOf("JED", "MED")) &&
                    item.outbound.destination.equals(originCode, true)
            }.sortedBy { runCatching { kotlin.math.abs(LocalDate.parse(it.outboundDate).toEpochDay() - returnDate.toEpochDay()) }.getOrDefault(Long.MAX_VALUE) }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(discoveryText(language, "recommended"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(when(language) {
                    AppLanguage.RUSSIAN -> "Только опубликованные прямые рейсы из базы iumrah. Выберите туда и обратно отдельно."
                    AppLanguage.ENGLISH -> "Published non-stop flights from iumrah. Select outbound and return separately."
                    AppLanguage.UZBEK -> "iumrah bazasidan e’lon qilingan to‘g‘ri reyslar. Borish va qaytishni alohida tanlang."
                    AppLanguage.UZBEK_CYRILLIC -> "iumrah базасидан эълон қилинган тўғри рейслар. Бориш ва қайтишни алоҳида танланг."
                }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
            }
            val publishedIDs = setOfNotNull(journeyState.selectedPublishedCompleteID,
                journeyState.selectedPublishedOutboundID, journeyState.selectedPublishedReturnID)
            FlightPublishedCarousel(language,
                when(language) { AppLanguage.RUSSIAN -> "Рейсы туда"; AppLanguage.ENGLISH -> "Outbound flights"; AppLanguage.UZBEK -> "Borish reyslari"; AppLanguage.UZBEK_CYRILLIC -> "Бориш рейслари" },
                "$originCode → $destinationCode · ${localizedDate(language, departureDate)}",
                outbound, recommendedLoading, publishedIDs) { selection ->
                val selectedTrip = trip.copy(origin = selection.outbound.origin.uppercase(),
                    arrivalAirport = if (selection.outbound.destination.equals("MED",true)) SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH,
                    departureDate = parseIsoDate(selection.outbound.departureAt) ?: departureDate,
                    adults=adults,children=children,infants=infants, flightTripType=FlightTripType.ROUND_TRIP)
                journey.commitTripBuilder(selectedTrip, PackageFlightPath.PUBLISHED_DIRECT,
                    outboundID = selection.id, returnID = journeyState.selectedPublishedReturnID)
            }
            FlightPublishedCarousel(language,
                when(language) { AppLanguage.RUSSIAN -> "Обратные рейсы"; AppLanguage.ENGLISH -> "Return flights"; AppLanguage.UZBEK -> "Qaytish reyslari"; AppLanguage.UZBEK_CYRILLIC -> "Қайтиш рейслари" },
                "${if (trip.scope == JourneyScope.MAKKAH_ONLY) "JED" else if (destinationCode == "MED") "JED" else "MED"} → $originCode · ${localizedDate(language, returnDate)}",
                inbound, recommendedLoading, publishedIDs) { selection ->
                val selectedTrip = trip.copy(returnDate = parseIsoDate(selection.outbound.departureAt) ?: returnDate,
                    adults=adults,children=children,infants=infants, flightTripType=FlightTripType.ROUND_TRIP)
                journey.commitTripBuilder(selectedTrip, PackageFlightPath.PUBLISHED_DIRECT,
                    outboundID = journeyState.selectedPublishedOutboundID, returnID = selection.id)
            }
            if (recommendedError != null) Text(recommendedError!!, fontSize=12.sp, color=MaterialTheme.colorScheme.error)
        }
        if (showEmbeddedAssembly) FlightAssemblyCard(language, journeyState,
            onContinue = { chrome.openHotelSelection() },
            onClear = { journey.clearStagedAviasalesFlights(); journey.clearPublishedFlightSelection() })
    }

    if (airportPicker != null) {
        FlightAirportPickerSheet(language, airports, airportPicker!!, onDismiss = { airportPicker = null }) { airport ->
            if (airportPicker == DiscoveryAirportRole.ORIGIN) {
                if (!airport.iata.equals(destinationCode, true)) {
                    originAirport = airport; originCode = airport.iata.uppercase()
                    if (journeyState.stagedAviasalesOutbound == null && journeyState.stagedAviasalesRoundTrip == null)
                        journey.updateTrip(trip.copy(origin = airport.iata.uppercase(), originAirport = airport))
                }
            } else if (!airport.iata.equals(originCode, true)) {
                destinationAirport = airport; destinationCode = airport.iata.uppercase()
            }
            airportPicker = null
        }
    }
    if (routeMap) {
        IumrahAirportRouteMapDialog(
            language = language,
            service = airports,
            origin = originAirport,
            originCode = originCode,
            destination = destinationAirport,
            destinationCode = destinationCode,
            onDismiss = { routeMap = false },
            onCommit = { from, to ->
                originAirport = from; originCode = from.iata.uppercase(); destinationAirport = to; destinationCode = to.iata.uppercase()
                if (journeyState.stagedAviasalesOutbound == null && journeyState.stagedAviasalesRoundTrip == null)
                    journey.updateTrip(trip.copy(origin = from.iata.uppercase(), originAirport = from))
                routeMap = false
            },
        )
    }
    if (datePicker) {
        FlightDiscoveryDateDialog(language, departureDate, returnDate, if (mode == FlightSearchMode.IUMRAH) DiscoveryTripType.ROUND_TRIP else tripType, onDismiss = { datePicker = false }) { out, inbound ->
            departureDate = out
            if (inbound != null) returnDate = inbound
            datePicker = false
        }
    }
    if (passengerPicker) {
        FlightPassengerSheet(language, adults, children, infants, onDismiss = { passengerPicker = false }) { a, c, i -> adults = a; children = c; infants = i; passengerPicker = false }
    }
    if (airlinePicker) {
        val rows = offers.map { it.airlineCode.uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
        AirlineFilterSheet(language, rows, selectedAirlines, onDismiss = { airlinePicker = false }) { selectedAirlines = it }
    }
    if (priceChart) {
        FlightPriceGraphV46(language, originCode, destinationCode, departureDate, returnDate,
            tripType == DiscoveryTripType.ROUND_TRIP, service,
            onDismiss = { priceChart = false }, onSelect = { out, inbound ->
                departureDate = out
                returnDate = inbound
            })
    }
    if (favoritesSheet) {
        FavoriteFlightsSheet(language, favorites, onDismiss = { favoritesSheet = false }, onSelect = { selectedOffer = it.offer; favoritesSheet = false }, onRemove = { favorites = favoritesStore.remove(it.id) })
    }
    selectedOffer?.let { offer ->
        FlightDiscoveryDetailSheetV46(
            language = language,
            initialOffer = offer,
            service = service,
            currency = currency,
            adults = adults,
            children = children,
            infants = infants,
            favorite = favorites.any { it.offer.monitorKey == offer.monitorKey },
            onDismiss = { selectedOffer = null },
            onFavorite = { current -> favorites = favoritesStore.toggle(current, currency) },
            onBuildUmrah = { current ->
                if (current.destination.uppercase() in setOf("JED", "MED") || current.origin.uppercase() in setOf("JED", "MED")) {
                    // Do not call updateTrip() here: it recreates JourneyState and
                    // deletes the previously selected outbound when adding a return.
                    journey.stageAviasalesFlight(current, adults = adults, children = children, infants = infants)
                    selectedOffer = null
                }
            },
        )
    }
}

@Composable
private fun DiscoveryRouteCard(language: AppLanguage, originCode: String, originName: String, destinationCode: String, destinationName: String, onOrigin: () -> Unit, onDestination: () -> Unit, onSwap: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), shape)) {
        Column {
            DiscoveryRouteRow(language, true, originName, originCode, onOrigin)
            Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)))
            DiscoveryRouteRow(language, false, destinationName, destinationCode, onDestination)
        }
        IumrahPressable(onClick = onSwap, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(46.dp), cornerRadius = 23.dp, background = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 0.dp) {
            Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.ArrowUp, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurface.copy(alpha=.65f)); Icon(CupertinoSymbol.ArrowDown, null, Modifier.padding(start=10.dp).size(16.dp), MaterialTheme.colorScheme.onSurface.copy(alpha=.65f)) }
        }
    }
}

@Composable
private fun DiscoveryRouteRow(language: AppLanguage, origin: Boolean, name: String, code: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp).heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(if (origin) CupertinoSymbol.AirplaneTakeoff else CupertinoSymbol.AirplaneLand, null, Modifier.size(17.dp), Color(0xFF007AFF)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text((if (origin) discoveryText(language, "from") else discoveryText(language, "to")).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            Text(name, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(code.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
        Spacer(Modifier.width(50.dp))
    }
}

@Composable
private fun DiscoverySegmented(language: AppLanguage, value: DiscoveryTripType, onChange: (DiscoveryTripType) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp)) {
        listOf(DiscoveryTripType.ONE_WAY to discoveryText(language, "one_way"), DiscoveryTripType.ROUND_TRIP to discoveryText(language, "round_trip")).forEach { (type, title) ->
            val active = type == value
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent).clickable { onChange(type) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(title, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 1f else .58f))
            }
        }
    }
}

@Composable
private fun DiscoveryChip(icon: CupertinoSymbol, title: String, active: Boolean, onClick: () -> Unit) {
    IumrahPressable(onClick = onClick, modifier = Modifier.height(42.dp), cornerRadius = 21.dp, background = if (active) Color(0xFF007AFF).copy(alpha = .12f) else MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
        Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp), if (active) Color(0xFF007AFF) else MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)); Spacer(Modifier.width(7.dp)); Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color(0xFF007AFF) else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun FlightDiscoveryCard(language: AppLanguage, offer: FlightDiscoveryOffer, currency: String, cheapest: Boolean, fastest: Boolean, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), shape).clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF007AFF).copy(alpha = .10f)), contentAlignment = Alignment.Center) { Text(offer.airlineCode.ifBlank { "✈" }, color = Color(0xFF007AFF), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(FlightReferenceCatalog.airlineName(offer.airlineCode, offer.airlineCode.ifBlank { discoveryText(language, "airline") }), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(offer.flightNumber.ifBlank { offer.routeTitle }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.52f))
            }
            IumrahPressable(onClick = onFavorite, modifier = Modifier.size(40.dp), cornerRadius = 20.dp, background = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 0.dp) { Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) { Icon(if (favorite) CupertinoSymbol.HeartFill else CupertinoSymbol.Heart, null, Modifier.size(16.dp), if (favorite) Color(0xFFFF375F) else MaterialTheme.colorScheme.onSurface.copy(alpha=.6f)) } }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column { Text(offer.originAirport.ifBlank { offer.origin }, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(FlightDataParity.localTime(offer.departureAt, offer.origin), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.52f)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Icon(CupertinoSymbol.Airplane, null, Modifier.size(17.dp), Color(0xFF007AFF)); Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.12f))); Spacer(Modifier.height(4.dp)); Text(if (offer.isDirect) discoveryText(language, "nonstop") else transfersText(language, offer.transfers), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.52f)) }
            Column(horizontalAlignment = Alignment.End) { Text(offer.destinationAirport.ifBlank { offer.destination }, fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(FlightDataParity.arrivalTime(offer.departureAt, offer.durationMinutes, offer.destination),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.52f)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (offer.isDirect) Badge(discoveryText(language, "recommended"), Color(0xFF34C759))
                if (cheapest) Badge(discoveryText(language, "cheapest"), Color(0xFFFF9500))
                if (fastest) Badge(discoveryText(language, "fastest"), Color(0xFFAF52DE))
            }
            Spacer(Modifier.width(10.dp)); Text(money(offer.price, currency), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (offer.isRoundTrip) Text(discoveryText(language, "round_trip_fare"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.5f))
    }
}

@Composable private fun Badge(title: String, color: Color) { Text(title, Modifier.clip(CircleShape).background(color.copy(alpha=.13f)).padding(horizontal=9.dp, vertical=5.dp), color=color, fontSize=10.sp, fontWeight=FontWeight.Bold, maxLines=1) }

@Composable private fun DiscoveryLoadingCard(language: AppLanguage) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth=2.dp); Spacer(Modifier.width(10.dp)); Text(discoveryText(language,"loading"), fontWeight=FontWeight.SemiBold) } }
@Composable private fun DiscoveryInfoCard(text: String, action: String, onAction: () -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(text,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.62f)); IumrahPressable(onClick=onAction,modifier=Modifier.fillMaxWidth().height(44.dp),cornerRadius=16.dp,background=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=0.dp){Box(Modifier.fillMaxWidth().height(44.dp),contentAlignment=Alignment.Center){Text(action,fontWeight=FontWeight.Bold)}} } }

@Composable
private fun FlightAirportPickerSheet(language: AppLanguage, service: AirportSearchService, role: DiscoveryAirportRole, onDismiss: () -> Unit, onSelect: (Airport) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) { if (query.trim().length >= 2) { loading=true; results=runCatching{service.search(query.trim(),12)}.getOrDefault(emptyList()); loading=false } else results=emptyList() }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background,dragHandle=null,shape=RoundedCornerShape(topStart=34.dp,topEnd=34.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(top=20.dp,bottom=30.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(role==DiscoveryAirportRole.ORIGIN) discoveryText(language,"choose_origin") else discoveryText(language,"choose_destination"),fontSize=26.sp,fontWeight=FontWeight.Bold);Text(discoveryText(language,"airport_hint"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f))};TextButton(onClick=onDismiss){Text(discoveryText(language,"close"))}}
            Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){Icon(CupertinoSymbol.Menu,null,Modifier.size(16.dp),MaterialTheme.colorScheme.onSurface.copy(alpha=.45f));Spacer(Modifier.width(8.dp));BasicTextField(query,{query=it},Modifier.weight(1f),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=MaterialTheme.colorScheme.onSurface,fontSize=16.sp),decorationBox={inner->Box{if(query.isBlank())Text(discoveryText(language,"airport_hint"),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.4f));inner()}});if(loading)CircularProgressIndicator(Modifier.size(17.dp),strokeWidth=2.dp)}
            results.forEach{airport->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable{onSelect(airport)}.padding(horizontal=12.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha=.12f)),contentAlignment=Alignment.Center){Icon(CupertinoSymbol.Airplane,null,Modifier.size(16.dp),Color(0xFF007AFF))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(airport.compactTitle,fontWeight=FontWeight.Bold);Text(airport.subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f),maxLines=1,overflow=TextOverflow.Ellipsis)}}}
        }
    }
}

@Composable
private fun FlightDiscoveryDateDialog(language: AppLanguage, departure: LocalDate, returnDate: LocalDate, type: DiscoveryTripType, onDismiss: () -> Unit, onApply: (LocalDate, LocalDate?) -> Unit) {
    var selectingReturn by remember { mutableStateOf(false) }
    var pendingDeparture by remember { mutableStateOf(departure) }
    val state = rememberDatePickerState(initialSelectedDateMillis = departure.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val chosen = state.selectedDateMillis?.let(::millisToDate) ?: pendingDeparture
                if (type == DiscoveryTripType.ROUND_TRIP && !selectingReturn) {
                    pendingDeparture = maxOf(LocalDate.now(), chosen)
                    selectingReturn = true
                    state.setSelectedDate(maxOf(returnDate, pendingDeparture.plusDays(1)))
                } else {
                    val out = if (selectingReturn) pendingDeparture else maxOf(LocalDate.now(), chosen)
                    val inbound = if (type == DiscoveryTripType.ROUND_TRIP) maxOf(out.plusDays(1), chosen) else null
                    onApply(out, inbound)
                }
            }) { Text(if (type == DiscoveryTripType.ROUND_TRIP && !selectingReturn) discoveryText(language, "next") else discoveryText(language, "done")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(discoveryText(language, "cancel")) } },
    ) {
        Column {
            if (type == DiscoveryTripType.ROUND_TRIP) Text(if (selectingReturn) discoveryText(language, "return_date") else discoveryText(language, "departure_date"), Modifier.padding(start = 24.dp, top = 12.dp), fontWeight = FontWeight.Bold)
            DatePicker(state = state)
        }
    }
}

@Composable
private fun FlightPassengerSheet(language: AppLanguage, initialAdults:Int, initialChildren:Int, initialInfants:Int, onDismiss:()->Unit, onApply:(Int,Int,Int)->Unit){var adults by remember{mutableStateOf(initialAdults)};var children by remember{mutableStateOf(initialChildren)};var infants by remember{mutableStateOf(initialInfants)};ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background,dragHandle=null,shape=RoundedCornerShape(topStart=34.dp,topEnd=34.dp)){Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text(discoveryText(language,"passengers"),fontSize=26.sp,fontWeight=FontWeight.Bold);PassengerStepper(discoveryText(language,"adults"),adults,1,9){adults=it};PassengerStepper(discoveryText(language,"children"),children,0,9){children=it};PassengerStepper(discoveryText(language,"infants"),infants,0,4){infants=it};IumrahPressable(onClick={onApply(adults,children,infants)},modifier=Modifier.fillMaxWidth().height(54.dp),cornerRadius=18.dp,background=Color.Black){Box(Modifier.fillMaxWidth().height(54.dp),contentAlignment=Alignment.Center){Text(discoveryText(language,"done"),color=Color.White,fontWeight=FontWeight.Bold)}}}}
}
@Composable private fun PassengerStepper(title:String,value:Int,min:Int,max:Int,onChange:(Int)->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);IumrahPressable(onClick={if(value>min)onChange(value-1)},modifier=Modifier.size(38.dp),cornerRadius=19.dp,background=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=0.dp){Box(Modifier.fillMaxWidth().height(38.dp),contentAlignment=Alignment.Center){Icon(CupertinoSymbol.Minus,null,Modifier.size(14.dp))}};Text(value.toString(),Modifier.width(42.dp),fontWeight=FontWeight.Bold,fontSize=17.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center);IumrahPressable(onClick={if(value<max)onChange(value+1)},modifier=Modifier.size(38.dp),cornerRadius=19.dp,background=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=0.dp){Box(Modifier.fillMaxWidth().height(38.dp),contentAlignment=Alignment.Center){Icon(CupertinoSymbol.Plus,null,Modifier.size(14.dp))}}}}

@Composable
private fun AirlineFilterSheet(language:AppLanguage,codes:List<String>,initial:Set<String>,onDismiss:()->Unit,onChange:(Set<String>)->Unit){var selected by remember(initial){mutableStateOf(initial)};ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background,dragHandle=null,shape=RoundedCornerShape(topStart=34.dp,topEnd=34.dp)){Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom=26.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(discoveryText(language,"airlines"),Modifier.weight(1f),fontSize=26.sp,fontWeight=FontWeight.Bold);TextButton(onClick={selected=emptySet();onChange(emptySet())}){Text(discoveryText(language,"clear"))}};codes.forEach{code->val active=code in selected;Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable{selected=if(active)selected-code else selected+code;onChange(selected)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF007AFF).copy(alpha=.10f)),contentAlignment=Alignment.Center){Text(code,color=Color(0xFF007AFF),fontWeight=FontWeight.Bold,fontSize=12.sp)};Spacer(Modifier.width(10.dp));Text(FlightReferenceCatalog.airlineName(code,code),Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Icon(if(active)CupertinoSymbol.CheckCircleFill else CupertinoSymbol.CheckCircle,null,Modifier.size(19.dp),if(active)Color(0xFF007AFF)else MaterialTheme.colorScheme.onSurface.copy(alpha=.3f))}}}}
}

@Composable
private fun FlightPriceChartSheet(language:AppLanguage,days:List<FlightDiscoveryCalendarDay>,currency:String,directOnly:Boolean,onDismiss:()->Unit,onSelect:(FlightDiscoveryCalendarDay)->Unit){val rows=days.sortedBy{it.date}.take(31);val max=rows.maxOfOrNull{it.price}?.coerceAtLeast(1.0)?:1.0;ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background,dragHandle=null,shape=RoundedCornerShape(topStart=34.dp,topEnd=34.dp)){Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom=26.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text(discoveryText(language,"price_chart"),fontSize=26.sp,fontWeight=FontWeight.Bold);Text(if(directOnly)discoveryText(language,"direct_chart")else discoveryText(language,"all_chart"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f));if(rows.isEmpty())Text(discoveryText(language,"empty"),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))else Row(Modifier.fillMaxWidth().height(190.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom){rows.forEach{day->val h=(42+118*(day.price/max)).dp;Column(Modifier.width(48.dp).clickable{onSelect(day)},horizontalAlignment=Alignment.CenterHorizontally){Text(money(day.price,currency),fontSize=9.sp,fontWeight=FontWeight.SemiBold,maxLines=1);Spacer(Modifier.height(5.dp));Box(Modifier.width(28.dp).height(h).clip(RoundedCornerShape(topStart=8.dp,topEnd=8.dp)).background(Color(0xFF007AFF).copy(alpha=.22f)));Spacer(Modifier.height(5.dp));Text(runCatching{LocalDate.parse(day.date).dayOfMonth.toString()}.getOrDefault(""),fontSize=10.sp)}}}}}
}

@Composable
private fun FavoriteFlightsSheet(language:AppLanguage,records:List<FlightFavoriteRecord>,onDismiss:()->Unit,onSelect:(FlightFavoriteRecord)->Unit,onRemove:(FlightFavoriteRecord)->Unit){ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background,dragHandle=null,shape=RoundedCornerShape(topStart=34.dp,topEnd=34.dp)){Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom=26.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(discoveryText(language,"saved"),fontSize=26.sp,fontWeight=FontWeight.Bold);if(records.isEmpty())Text(discoveryText(language,"no_saved"),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f));records.forEach{r->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable{onSelect(r)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF007AFF).copy(alpha=.1f)),contentAlignment=Alignment.Center){Text(r.offer.airlineCode.ifBlank{"✈"},fontWeight=FontWeight.Bold,color=Color(0xFF007AFF),fontSize=12.sp)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(r.offer.routeTitle,fontWeight=FontWeight.Bold);Text("${r.offer.airlineCode} ${r.offer.flightNumber} · ${money(r.lastKnownPrice,r.currency)}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f))};IumrahPressable(onClick={onRemove(r)},modifier=Modifier.size(38.dp),cornerRadius=19.dp,background=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=0.dp){Box(Modifier.fillMaxWidth().height(38.dp),contentAlignment=Alignment.Center){Icon(CupertinoSymbol.Trash,null,Modifier.size(14.dp),Color(0xFFFF3B30))}}}}}}
}

}

private fun airportCity(code:String):String=FlightReferenceCatalog.airport(code)?.city?:code.uppercase()
private fun money(value:Double,currency:String):String=if(currency.equals("usd",true))"$${value.toInt()}" else "${currency.uppercase()} ${value.toInt()}"
private fun millisToDate(ms:Long):LocalDate=Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()
private fun parseIsoDate(raw:String):LocalDate?=runCatching{OffsetDateTime.parse(raw).toLocalDate()}.getOrNull()?:runCatching{LocalDate.parse(raw.take(10))}.getOrNull()
private fun timeText(raw:String):String=runCatching{OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("HH:mm"))}.getOrElse{raw.dropWhile{it!='T'}.drop(1).take(5)}
private fun durationText(minutes:Int):String=if(minutes<=0)"—" else "${minutes/60}h ${minutes%60}m"
private fun transfersText(language:AppLanguage,count:Int):String=when(language){AppLanguage.RUSSIAN->if(count==1)"1 пересадка" else "$count пересадки";AppLanguage.ENGLISH->if(count==1)"1 stop" else "$count stops";AppLanguage.UZBEK->"$count ta transfer";AppLanguage.UZBEK_CYRILLIC->"$count та трансфер"}
private fun localizedDate(language:AppLanguage,date:LocalDate):String=date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale(language)))
private fun localizedIsoDate(language:AppLanguage,raw:String):String=parseIsoDate(raw)?.let{localizedDate(language,it)}?:raw.take(10)
private fun dateLabel(language:AppLanguage,out:LocalDate,inbound:LocalDate?):String {
    if (inbound == null) return localizedDate(language, out)
    val formatter = DateTimeFormatter.ofPattern("d MMM", locale(language))
    return "${out.format(formatter)} – ${inbound.format(formatter)}"
}
private fun passengerLabel(language:AppLanguage,count:Int):String=when(language){AppLanguage.RUSSIAN->"$count, эконом";AppLanguage.ENGLISH->"$count, economy";AppLanguage.UZBEK->"$count, ekonom";AppLanguage.UZBEK_CYRILLIC->"$count, эконом"}
private fun locale(language:AppLanguage)=when(language){AppLanguage.RUSSIAN->Locale("ru","RU");AppLanguage.ENGLISH->Locale.US;AppLanguage.UZBEK->Locale.forLanguageTag("uz-Latn-UZ");AppLanguage.UZBEK_CYRILLIC->Locale.forLanguageTag("uz-Cyrl-UZ")}

private fun openAviasales(context:Context,offer:FlightDiscoveryOffer,adults:Int,children:Int,infants:Int,fallbackReturn:LocalDate?){val url=offer.bookingUrl?.takeIf{it.startsWith("http")}?:run{val out=parseIsoDate(offer.departureAt)?:LocalDate.now();val token="%02d%02d".format(out.dayOfMonth,out.monthValue);val pax=if(children==0&&infants==0)adults.toString() else "$adults$children$infants";var params="${offer.origin.uppercase()}$token${offer.destination.uppercase()}";if(offer.isRoundTrip){val r=offer.returnAt?.let(::parseIsoDate)?:fallbackReturn;if(r!=null)params+="%02d%02d".format(r.dayOfMonth,r.monthValue)};params+=pax;"https://www.aviasales.com/search/$params"};runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))} }
private fun shareFlight(context:Context,offer:FlightDiscoveryOffer,currency:String){val text="${offer.routeTitle}\n${FlightReferenceCatalog.airlineName(offer.airlineCode,offer.airlineCode)} ${offer.flightNumber}\n${localizedIsoDate(AppLanguage.ENGLISH,offer.departureAt)} · ${money(offer.price,currency)}";runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},null))}}

private fun discoveryText(language:AppLanguage,key:String):String{val en=mapOf("load_error" to "Couldn’t load current flight data.","map" to "Map","direct" to "Direct flights","filters" to "Direct flights","price_chart" to "Price chart","airlines" to "Airlines","saved" to "Saved","results" to "Flights","retry" to "Try again","empty" to "No flights found for these dates.","change" to "Change dates","nearby" to "Nearby dates","source_note" to "Prices are based on recently found Aviasales fares. Refresh before purchase to confirm the current fare and availability.","from" to "From","to" to "To","one_way" to "One way","round_trip" to "Round trip","airline" to "Airline","nonstop" to "Non-stop","recommended" to "iumrah recommends","cheapest" to "Cheapest","fastest" to "Fastest","round_trip_fare" to "Round-trip fare","loading" to "Finding current fares…","choose_origin" to "Choose departure airport","choose_destination" to "Choose arrival airport","airport_hint" to "Search city or airport code","close" to "Close","next" to "Next","done" to "Done","cancel" to "Cancel","return_date" to "Return","departure_date" to "Departure","passengers" to "Passengers","adults" to "Adults","children" to "Children","infants" to "Infants","clear" to "Clear","direct_chart" to "Direct-flight prices","all_chart" to "Outbound date prices","no_saved" to "No saved flights yet.","fare" to "Fare","fare_conditions" to "Airfares change quickly. The current price and availability are confirmed when you open the booking source.","buy_aviasales" to "Check current price on Aviasales","share" to "Share","build_umrah" to "Use for Umrah")
val ru=mapOf("load_error" to "Не удалось загрузить актуальные данные рейсов.","map" to "Карта","direct" to "Прямые рейсы","filters" to "Прямые рейсы","price_chart" to "График цен","airlines" to "Авиакомпании","saved" to "Сохранённые","results" to "Авиабилеты","retry" to "Повторить","empty" to "На эти даты рейсы не найдены.","change" to "Изменить даты","nearby" to "Соседние даты","source_note" to "Цены основаны на недавно найденных предложениях Aviasales. Перед покупкой обновите данные, чтобы подтвердить актуальную цену и наличие.","from" to "Откуда","to" to "Куда","one_way" to "В одну сторону","round_trip" to "Туда-обратно","airline" to "Авиакомпания","nonstop" to "Без пересадок","recommended" to "Рекомендует iumrah","cheapest" to "Самый дешёвый","fastest" to "Самый быстрый","round_trip_fare" to "Цена туда-обратно","loading" to "Ищем актуальные тарифы…","choose_origin" to "Аэропорт вылета","choose_destination" to "Аэропорт прилёта","airport_hint" to "Город или код аэропорта","close" to "Закрыть","next" to "Далее","done" to "Готово","cancel" to "Отмена","return_date" to "Обратно","departure_date" to "Туда","passengers" to "Пассажиры","adults" to "Взрослые","children" to "Дети","infants" to "Младенцы","clear" to "Сбросить","direct_chart" to "Цены прямых рейсов","all_chart" to "Цены по датам вылета","no_saved" to "Сохранённых рейсов пока нет.","fare" to "Тариф","fare_conditions" to "Цены на авиабилеты быстро меняются. Актуальная цена и наличие подтверждаются при открытии источника бронирования.","buy_aviasales" to "Проверить цену на Aviasales","share" to "Поделиться","build_umrah" to "Использовать для умры")
val uz=mapOf("load_error" to "Joriy reys ma’lumotlarini yuklab bo‘lmadi.","map" to "Xarita","direct" to "To‘g‘ridan-to‘g‘ri","filters" to "To‘g‘ridan-to‘g‘ri","price_chart" to "Narx grafigi","airlines" to "Aviakompaniyalar","saved" to "Saqlangan","results" to "Aviachiptalar","retry" to "Qayta urinish","empty" to "Bu sanalarda reys topilmadi.","change" to "Sanalarni o‘zgartirish","nearby" to "Yaqin sanalar","source_note" to "Narxlar Aviasales’da yaqinda topilgan takliflarga asoslanadi. Xarid qilishdan oldin joriy narx va mavjudlikni tekshiring.","from" to "Qayerdan","to" to "Qayerga","one_way" to "Bir tomonga","round_trip" to "Borib-kelish","airline" to "Aviakompaniya","nonstop" to "To‘g‘ridan-to‘g‘ri","recommended" to "iumrah tavsiya qiladi","cheapest" to "Eng arzon","fastest" to "Eng tez","round_trip_fare" to "Borib-kelish narxi","loading" to "Joriy tariflar qidirilmoqda…","choose_origin" to "Jo‘nash aeroporti","choose_destination" to "Kelish aeroporti","airport_hint" to "Shahar yoki aeroport kodi","close" to "Yopish","next" to "Keyingi","done" to "Tayyor","cancel" to "Bekor qilish","return_date" to "Qaytish","departure_date" to "Borish","passengers" to "Yo‘lovchilar","adults" to "Kattalar","children" to "Bolalar","infants" to "Chaqaloqlar","clear" to "Tozalash","direct_chart" to "To‘g‘ridan-to‘g‘ri reys narxlari","all_chart" to "Jo‘nash sanalari narxi","no_saved" to "Saqlangan reyslar yo‘q.","fare" to "Tarif","fare_conditions" to "Aviachipta narxlari tez o‘zgaradi. Joriy narx va mavjudlik bron manbasi ochilganda tasdiqlanadi.","buy_aviasales" to "Aviasales’da narxni tekshirish","share" to "Ulashish","build_umrah" to "Umra uchun ishlatish")
val cy=uz.mapValues{it.value}.toMutableMap().apply{put("map","Харита");put("direct","Тўғридан-тўғри");put("price_chart","Нарх графиги");put("airlines","Авиакомпаниялар");put("saved","Сақланган");put("results","Авиачипталар");put("from","Қаердан");put("to","Қаерга");put("one_way","Бир томонга");put("round_trip","Бориб-келиш");put("done","Тайёр");put("cancel","Бекор қилиш")}
return when(language){AppLanguage.RUSSIAN->ru[key];AppLanguage.ENGLISH->en[key];AppLanguage.UZBEK->uz[key];AppLanguage.UZBEK_CYRILLIC->cy[key]}?:en[key]?:key}
