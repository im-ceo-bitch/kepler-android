@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.flights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.analytics.IumrahIntelligence
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.flight.AviasalesFlightDiscoveryService
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.nuitee.NuiteeFlightSnapshot
import com.iumrah.beta.domain.trip.FlightTripType
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageFlightPath
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.models.flight.FlightCabinClass
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.trip.ConfiguratorAirportSelector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Flight-first search counterpart of the current SwiftUI IumrahGuidedFlightSelectionView.
 * It shows real published and Data API observations together but NEVER mixes them
 * in one booking selection. No guessed fares, fabricated segments, or new API endpoints.
 */
@Composable
fun IumrahGuidedFlightsScreen(
    language: AppLanguage,
    journey: JourneyStore,
    curated: CuratedFlightRecommendationService,
    discovery: AviasalesFlightDiscoveryService,
    airports: AirportSearchService,
    chrome: AppChromeStore,
) {
    val state by journey.state.collectAsState()
    val trip = state.trip
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var stepReturn by remember { mutableStateOf(false) }
    var outbound by remember { mutableStateOf<GuidedFlightChoice?>(null) }
    var inbound by remember { mutableStateOf<GuidedFlightChoice?>(null) }
    var published by remember { mutableStateOf<List<CuratedFlightRecommendation>>(emptyList()) }
    var dataOutbound by remember { mutableStateOf<List<FlightDiscoveryOffer>>(emptyList()) }
    var dataReturn by remember { mutableStateOf<List<FlightDiscoveryOffer>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<GuidedFlightChoice?>(null) }
    var dateField by remember { mutableStateOf<Boolean?>(null) } // false departure, true return
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(trip.originCode, trip.outboundDestinationCode, trip.returnOriginCode,
        trip.departureDate, trip.returnDate, trip.effectiveFlightFilters.cabinClass, refreshKey) {
        outbound = null
        inbound = null
        stepReturn = false
        editing = false
        loadingError = null
        published = emptyList()
        dataOutbound = emptyList()
        dataReturn = emptyList()
        busy = true
        IumrahIntelligence.record("search_started", "flight_builder", "published_and_data")
        var firstError: Throwable? = null
        try {
            published = try {
                curated.load(trip)
            } catch (ex: CancellationException) { throw ex
            } catch (ex: Exception) { firstError = ex; emptyList() }
            if (trip.effectiveFlightFilters.cabinClass == FlightCabinClass.ECONOMY) {
                // Retain the same exact-day and surrounding-month fallback as SwiftUI.
                val legs = listOf(
                    Triple(trip.originCode, trip.outboundDestinationCode, trip.departureDate),
                    Triple(trip.returnOriginCode, trip.originCode, trip.returnDate),
                )
                for ((index, leg) in legs.withIndex()) {
                    val (origin, destination, target) = leg
                    val months = (-3..3).map { target.plusDays(it.toLong()).toString().take(7) }.distinct()
                    val rows = mutableListOf<FlightDiscoveryOffer>()
                    for (dayOrMonth in months + target.toString()) {
                        try {
                            rows += discovery.offers(origin, destination, dayOrMonth, direct = false, limit = 100).offers
                        } catch (ex: CancellationException) { throw ex
                        } catch (ex: Exception) { if (firstError == null) firstError = ex }
                    }
                    val filtered = rows.filter { it.origin.equals(origin, true) &&
                        it.destination.equals(destination, true) && !it.isRoundTrip }
                        .distinctBy { "${it.id}|${it.departureAt}|${it.flightNumber}" }
                    if (index == 0) dataOutbound = filtered else dataReturn = filtered
                }
            }
            if (published.isEmpty() && dataOutbound.isEmpty() && dataReturn.isEmpty() && firstError != null) {
                loadingError = guidedTr(language,
                    "Не удалось загрузить рейсы. Проверьте подключение и повторите поиск.",
                    "Could not load flights. Check your connection and try again.",
                    "Reyslarni yuklab bo‘lmadi. Internetni tekshirib, qayta urinib ko‘ring.",
                    "Рейсларни юклаб бўлмади. Интернетни текшириб, қайта уриниб кўринг.")
            }
            IumrahIntelligence.record("search_results", "flight_builder",
                if (published.isEmpty() && dataOutbound.isEmpty()) "empty" else "available")
        } finally { busy = false }
    }

    val results = remember(trip, stepReturn, outbound, published, dataOutbound, dataReturn) {
        GuidedFlightRules.options(trip, published, dataOutbound, dataReturn, stepReturn, outbound)
    }
    val target = if (stepReturn) trip.returnDate else trip.departureDate
    val groupExact = results.filter { GuidedFlightRules.departureDay(it.departureAt) == target && it.stops == 0 }
    val groupNearby = results.filter { GuidedFlightRules.departureDay(it.departureAt) != target }
    val groupConnecting = results.filter { GuidedFlightRules.departureDay(it.departureAt) == target && it.stops > 0 }
    val chosen = if (stepReturn) inbound else outbound

    fun commit() {
        if (!GuidedFlightRules.compatible(outbound, inbound)) {
            selectionError = guidedTr(language,
                "Выберите совместимые рейсы одного источника.", "Choose compatible flights from one source.",
                "Bitta manbadan mos reyslarni tanlang.", "Битта манбадан мос рейсларни танланг.")
            return
        }
        val first = requireNotNull(outbound)
        val back = requireNotNull(inbound)
        val outDay = requireNotNull(GuidedFlightRules.departureDay(first.departureAt))
        val backDay = requireNotNull(GuidedFlightRules.departureDay(back.departureAt))
        val nextTrip = trip.copy(
            departureDate = outDay, returnDate = backDay,
            arrivalAirport = if (first.destination == "MED") SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH,
            flightTripType = FlightTripType.ROUND_TRIP,
        )
        when {
            first is GuidedFlightChoice.Published && back is GuidedFlightChoice.Published -> {
                journey.clearStagedAviasalesFlights()
                if (first.publishedPair) {
                    journey.commitTripBuilder(nextTrip, PackageFlightPath.PUBLISHED_DIRECT,
                        completeID = back.recommendation.id)
                } else {
                    journey.commitTripBuilder(nextTrip, PackageFlightPath.PUBLISHED_DIRECT,
                        outboundID = first.recommendation.id, returnID = back.recommendation.id)
                }
            }
            first is GuidedFlightChoice.Data && back is GuidedFlightChoice.Data -> {
                journey.clearStagedAviasalesFlights()
                journey.stageAviasalesFlight(first.offer)
                journey.stageAviasalesFlight(back.offer, returnLeg = true)
                if (!journey.state.value.hasCompleteStagedFlightSelection) {
                    selectionError = guidedTr(language, "Маршрут обратного рейса не совпадает.",
                        "Return flight route is not compatible.", "Qaytish yo‘nalishi mos emas.",
                        "Қайтиш йўналиши мос эмас.")
                    return
                }
            }
            else -> return
        }
        journey.startNuiteeJourney(listOf(first, back).map { chosen -> NuiteeFlightSnapshot(
            id = chosen.id, origin = chosen.origin, destination = chosen.destination,
            airline = chosen.airlineName, flightNumber = chosen.flightNumber,
            airlineCode = chosen.airlineCode, durationMinutes = chosen.durationMinutes,
            stops = chosen.stops, departureAt = chosen.departureAt,
            arrivalAt = chosen.arrivalAt, adultFareUSD = chosen.priceUsd,
        ) })
        IumrahIntelligence.record("tap", "flight_builder", "continue_to_nuitee_hotels")
        chrome.openNuiteeJourneyHotels()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (outbound != null) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 9.dp)) {
                    IumrahPrimaryButton(
                        title = if (!stepReturn)
                            guidedTr(language, "Выбрать обратный перелёт", "Choose return flight",
                                "Qaytish reysini tanlash", "Қайтиш рейсини танлаш")
                        else guidedTr(language, "Продолжить к отелям", "Continue to hotels",
                            "Mehmonxonalarga davom etish", "Меҳмонхоналарга давом этиш"),
                        enabled = !stepReturn || GuidedFlightRules.compatible(outbound, inbound),
                        onClick = { if (!stepReturn) stepReturn = true else commit() },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 25.dp)) {
            item {
                GuidedFlightHero(language, trip.originCode, trip.outboundDestinationCode,
                    trip.returnOriginCode, trip.travelerCount, trip.departureDate, trip.returnDate,
                    editing, { chrome.back() }, onEdit = { editing = !editing },
                    onRefresh = { refreshKey += 1 })
            }
            item {
                AnimatedVisibility(visible = editing) {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(guidedTr(language, "Изменить поиск", "Edit search", "Qidiruvni o‘zgartirish",
                            "Қидирувни ўзгартириш"), fontWeight = FontWeight.Bold)
                        ConfiguratorAirportSelector(language = language, service = airports,
                            airport = trip.originAirport, fallbackCode = trip.originCode,
                            onSelect = { journey.updateTrip(trip.copy(origin = it.iata, originAirport = it)) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(JourneyScope.MAKKAH_ONLY, JourneyScope.MAKKAH_AND_MADINAH).forEach { scopeValue ->
                                val selected = trip.scope == scopeValue
                                Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(22.dp))
                                    .background(if (selected) Color.Black else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { journey.updateTrip(trip.copy(scope = scopeValue,
                                        arrivalAirport = if (scopeValue == JourneyScope.MAKKAH_ONLY)
                                            SaudiArrivalAirport.JEDDAH else trip.arrivalAirport)) },
                                    contentAlignment = Alignment.Center) {
                                    Text(if (scopeValue == JourneyScope.MAKKAH_ONLY)
                                        guidedTr(language, "Мекка", "Makkah", "Makka", "Макка")
                                    else guidedTr(language, "Мекка + Медина", "Makkah + Madinah",
                                        "Makka + Madina", "Макка + Мадина"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GuidedEditorButton("${trip.departureDate}", Modifier.weight(1f)) { dateField = false }
                            GuidedEditorButton("${trip.returnDate}", Modifier.weight(1f)) { dateField = true }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(guidedTr(language, "Взрослые", "Adults", "Kattalar", "Катталар"),
                                modifier = Modifier.weight(1f))
                            TextButton(enabled = trip.adults > 1, onClick = {
                                journey.updateTrip(trip.copy(adults = trip.adults - 1,
                                    infants = minOf(trip.infants, trip.adults - 1)))
                            }) { Text("−", fontSize = 22.sp) }
                            Text("${trip.adults}")
                            TextButton(enabled = trip.travelerCount < 9, onClick = {
                                journey.updateTrip(trip.copy(adults = trip.adults + 1))
                            }) { Text("+", fontSize = 22.sp) }
                        }
                        Text(guidedTr(language,
                            "Дети и младенцы сохраняются из параметров поездки на главной.",
                            "Children and infants are preserved from your Home trip settings.",
                            "Bolalar soni asosiy sahifadagi tanlovdan saqlanadi.",
                            "Болалар сони асосий саҳифадаги танловдан сақланади."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                        TextButton(onClick = { editing = false; refreshKey += 1 }) {
                            Text(guidedTr(language, "Найти рейсы", "Search flights", "Reyslarni qidirish",
                                "Рейсларни қидириш"))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GuidedStepChip(
                        guidedTr(language, "Туда", "Outbound", "Borish", "Бориш") + " · ${trip.departureDate}",
                        selected = !stepReturn, enabled = true, modifier = Modifier.weight(1f)) { stepReturn = false }
                    GuidedStepChip(
                        guidedTr(language, "Обратно", "Return", "Qaytish", "Қайтиш") + " · ${trip.returnDate}",
                        selected = stepReturn, enabled = outbound != null, modifier = Modifier.weight(1f)) { stepReturn = true }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (!stepReturn) guidedTr(language, "Рейсы туда", "Outbound flights", "Borish reyslari", "Бориш рейслари")
                            else guidedTr(language, "Обратные рейсы", "Return flights", "Qaytish reyslari", "Қайтиш рейслари"),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${if (stepReturn) trip.returnOriginCode else trip.originCode} → ${if (stepReturn) trip.originCode else trip.outboundDestinationCode} · $target",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            if (selectionError != null) item {
                GuidedNotice(selectionError.orEmpty(), language, onRetry = { selectionError = null })
            }
            if (loadingError != null && results.isEmpty()) item {
                GuidedNotice(loadingError.orEmpty(), language, onRetry = { refreshKey += 1 })
            }
            if (stepReturn && !busy && results.isEmpty() && outbound != null) item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(guidedTr(language,
                        "Нет совместимых обратных рейсов. Измените рейс туда или даты.",
                        "No compatible return flights. Change outbound flight or dates.",
                        "Mos qaytish reysi yo‘q. Borish reysini yoki sanalarni o‘zgartiring.",
                        "Мос қайтиш рейси йўқ. Бориш рейсини ёки саналарни ўзгартиринг."),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { inbound = null; outbound = null; stepReturn = false }) {
                        Text(guidedTr(language, "Изменить рейс туда", "Change outbound flight",
                            "Borish reysini o‘zgartirish", "Бориш рейсини ўзгартириш"))
                    }
                }
            } else if (!busy && results.isEmpty() && loadingError == null) item {
                GuidedNotice(guidedTr(language, "Рейсов на эти даты пока нет.",
                    "No flights available for these dates yet.", "Bu sanalarda hozircha reys yo‘q.",
                    "Бу саналарда ҳозирча рейс йўқ."), language, onRetry = { refreshKey += 1 })
            }
            if (groupExact.isNotEmpty()) item { GuidedSectionTitle(guidedTr(language,
                "Прямые на выбранную дату", "Non-stop on your date", "Tanlangan kundagi to‘g‘ri reyslar",
                "Танланган кундаги тўғри рейслар")) }
            items(groupExact, key = { it.id }) { choice ->
                GuidedFlightRow(choice, selected = chosen?.id == choice.id, language = language) { detail = choice }
            }
            if (groupNearby.isNotEmpty()) item { GuidedSectionTitle(guidedTr(language,
                "Ближайшие даты", "Nearby dates", "Yaqin sanalar", "Яқин саналар")) }
            items(groupNearby, key = { it.id }) { choice ->
                GuidedFlightRow(choice, selected = chosen?.id == choice.id, language = language) { detail = choice }
            }
            if (groupConnecting.isNotEmpty()) item { GuidedSectionTitle(guidedTr(language,
                "С пересадкой", "Connecting", "Transferli", "Трансферли")) }
            items(groupConnecting, key = { it.id }) { choice ->
                GuidedFlightRow(choice, selected = chosen?.id == choice.id, language = language) { detail = choice }
            }
        }
    }
    detail?.let { item ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            GuidedFlightDetail(item, language) {
                if (stepReturn) {
                    if (outbound?.source != item.source) {
                        selectionError = guidedTr(language, "Источники рейсов должны совпадать.",
                            "Flight sources must match.", "Reys manbalari bir xil bo‘lishi kerak.",
                            "Рейс манбалари бир хил бўлиши керак.")
                    } else {
                        inbound = item
                        IumrahIntelligence.record("flight_selected", "flight_builder", "inbound")
                    }
                } else {
                    outbound = item
                    inbound = null
                    IumrahIntelligence.record("flight_selected", "flight_builder", "outbound")
                }
                detail = null
            }
        }
    }
    dateField?.let { isReturn ->
        val dateValue = if (isReturn) trip.returnDate else trip.departureDate
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateValue.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton(onClick = {
                    val picked = pickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    if (picked != null && !picked.isBefore(LocalDate.now())) {
                        val next = if (isReturn) {
                            trip.copy(returnDate = maxOf(picked, trip.departureDate.plusDays(1)))
                        } else {
                            trip.copy(departureDate = picked,
                                returnDate = maxOf(trip.returnDate, picked.plusDays(1)))
                        }
                        journey.updateTrip(next)
                    }
                    dateField = null
                }) { Text(guidedTr(language, "Готово", "Done", "Tayyor", "Тайёр")) }
            },
            dismissButton = { TextButton(onClick = { dateField = null }) {
                Text(guidedTr(language, "Отмена", "Cancel", "Bekor", "Бекор"))
            } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun GuidedFlightHero(language: AppLanguage, origin: String, destination: String,
    returnOrigin: String, count: Int, outboundDay: LocalDate, returnDay: LocalDate,
    editing: Boolean, onBack: () -> Unit, onEdit: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF090909))
        .padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f))
                .clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.ChevronLeft, "Back", Modifier.size(20.dp), Color.White)
            }
            Text(guidedTr(language, "Выбор авиабилета", "Choose your flights", "Reys tanlash",
                "Рейс танлаш"), color = Color.White, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f))
                .clickable(onClick = onRefresh), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.Refresh, "Refresh flights", Modifier.size(19.dp), Color.White)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
            .background(Color.White).border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(23.dp))
            .clickable(onClick = onEdit).padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$origin → $destination  ·  $returnOrigin → $origin",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = Color(0xFF181818), modifier = Modifier.weight(1f))
                Icon(if (editing) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown,
                    null, Modifier.size(16.dp), Color(0xFF181818))
            }
            Text("$count ${guidedTr(language, "чел.", "travelers", "kishi", "киши")} · $outboundDay – $returnDay",
                style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
        }
    }
}

@Composable
private fun GuidedEditorButton(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clip(RoundedCornerShape(15.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(title, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun GuidedStepChip(title: String, selected: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(53.dp).clip(RoundedCornerShape(28.dp))
        .background(if (selected) Color.Black else MaterialTheme.colorScheme.surface)
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center) {
        Text(title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .35f),
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GuidedSectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 9.dp),
        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun GuidedFlightRow(choice: GuidedFlightChoice, selected: Boolean,
    language: AppLanguage, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
        .clip(shape).background(MaterialTheme.colorScheme.surface)
        .border(if (selected) 1.5.dp else .75.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) .7f else .09f), shape)
        .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(CupertinoSymbol.Airplane, null, Modifier.size(20.dp))
            Text(choice.airlineName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Icon(CupertinoSymbol.CheckCircleFill, "Selected", Modifier.size(19.dp), Color(0xFF2B8A69))
            Text(choice.flightNumber, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GuidedClock(choice.departureAt.substringAfter('T', "").take(5).ifBlank { "—" },
                choice.origin, Modifier.weight(1f), Alignment.Start)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(55.dp).height(2.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f)))
                Text(if (choice.stops == 0) guidedTr(language, "Прямой", "Non-stop", "To‘g‘ri", "Тўғри")
                else guidedTr(language, "Пересадка", "Transfer", "Transfer", "Трансфер"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GuidedClock(choice.arrivalAt?.substringAfter('T', "")?.take(5)?.ifBlank { "—" }
                ?: "—", choice.destination, Modifier.weight(1f), Alignment.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(choice.departureAt.take(10), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(if (choice.priceUsd != null) "${"%,.0f".format(Locale.US, choice.priceUsd)} $ / ${guidedTr(language, "чел.", "person", "kishi", "киши")}" else
                guidedTr(language, "Цена при расчёте", "Price on quote", "Narx hisobda", "Нарх ҳисобда"),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GuidedClock(time: String, airport: String, modifier: Modifier = Modifier, align: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(airport, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuidedNotice(text: String, language: AppLanguage, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text(guidedTr(language, "Повторить", "Retry", "Qayta", "Қайта")) }
    }
}

@Composable
private fun GuidedFlightDetail(choice: GuidedFlightChoice, language: AppLanguage, onSelect: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Text(guidedTr(language, "Детали рейса", "Flight details", "Reys tafsilotlari",
            "Рейс тафсилотлари"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${choice.origin} → ${choice.destination}", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("${choice.airlineName}  ·  ${choice.flightNumber}")
        Text(choice.departureAt)
        choice.arrivalAt?.let { Text(it) }
        Text(if (choice.stops == 0) guidedTr(language, "Прямой рейс", "Non-stop flight",
            "To‘g‘ri reys", "Тўғри рейс") else "${choice.stops} ${guidedTr(language, "пересадок",
            "stop(s)", "transfer", "трансфер")}")
        if (choice.durationMinutes > 0) Text("${choice.durationMinutes / 60}h ${choice.durationMinutes % 60}m")
        Text(if (choice.source == GuidedFlightChoice.Source.PUBLISHED)
            guidedTr(language, "Опубликованный рейс iumrah · цена подтверждается при расчёте",
                "iumrah published flight · price verified at quote", "iumrah reysi · narx hisobda tekshiriladi",
                "iumrah рейси · нарх ҳисобда текширилади")
        else guidedTr(language, "Наблюдаемая цена авиа-API; доступность проверяется при расчёте",
            "Observed API fare; availability is rechecked at quote",
            "API narxi; mavjudlik hisobda tekshiriladi", "API нархи; мавжудлик ҳисобда текширилади"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        choice.priceUsd?.let {
            Text("${"%,.0f".format(Locale.US, it)} $", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
        IumrahPrimaryButton(title = guidedTr(language, "Выбрать этот рейс", "Select this flight",
            "Shu reysni tanlash", "Шу рейсни танлаш"), onClick = onSelect)
    }
}

private fun guidedTr(language: AppLanguage, ru: String, en: String, uz: String, cy: String) = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
    else -> en
}
