package com.iumrah.beta.ui.flights

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.IgnavFlightInventoryProvider
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.models.flight.LiveFlightCandidate
import com.iumrah.beta.models.flight.LiveFlightJourneyCandidate
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.generator.GeneratorCard
import com.iumrah.beta.ui.generator.GeneratorGeometry
import com.iumrah.beta.ui.generator.GeneratorHeader
import com.iumrah.beta.ui.generator.GeneratorPrimaryButton
import com.iumrah.beta.ui.generator.GeneratorStage
import com.iumrah.beta.ui.generator.generatorCardColor
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.generator.generatorRaisedColor
import com.iumrah.beta.ui.media.LoopingRawVideo
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class FlightSort { RECOMMENDED, CHEAPEST, FASTEST }
private enum class StopsFilter { ALL, NONSTOP, ONE, MULTIPLE }

/** SwiftUI OutboundFlightView parity. */
@Composable
fun FlightSearchScreen(
    language: AppLanguage,
    journey: JourneyStore,
    provider: IgnavFlightInventoryProvider,
    packageEngine: RemotePackageEngineClient,
    chrome: AppChromeStore,
) {
    val state by journey.state.collectAsState()
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(FlightSort.RECOMMENDED) }
    var stops by remember { mutableStateOf(StopsFilter.ALL) }
    var airline by remember { mutableStateOf<String?>(null) }
    var packagePrices by remember { mutableStateOf<Map<String, BigDecimal>>(emptyMap()) }

    LaunchedEffect(state.trip, state.makkahHotel?.id, state.madinahHotel?.id) {
        if (state.flightResults.isEmpty() && !state.isSearchingFlights && state.flightError == null) {
            journey.searchFlights(provider)
        }
    }

    val offers = state.flightResults
    val airlineCodes = remember(offers) {
        offers.mapNotNull { it.outbound.airlineCode?.uppercase()?.takeIf { code -> code.length == 2 } }.distinct().sorted()
    }
    LaunchedEffect(offers.map { it.id }) {
        packagePrices = if (offers.isEmpty()) emptyMap() else journey.packagePricePreviews(packageEngine, offers.map { it.id })
    }

    val recommended = remember(offers, packagePrices) {
        offers.minWithOrNull(compareBy<LiveFlightJourneyCandidate>(
            { packagePrices[it.id] ?: it.totalFare },
            { it.outbound.stops },
            { it.outbound.durationMinutes },
            { it.outbound.departureAt },
        ))
    }
    LaunchedEffect(recommended?.id) {
        if (state.selectedOutboundJourneyId == null) recommended?.let { journey.selectOutboundJourney(it.id) }
    }

    if (offers.isEmpty() && state.isSearchingFlights) {
        FlightSearchImmersive(language, chrome)
        return
    }
    if (offers.isEmpty() && state.flightError != null) {
        FlightSearchGate(
            language = language,
            outbound = true,
            dateText = formatDate(state.trip.departureDate.toString(), language),
            message = searchGateFallback(language),
            chrome = chrome,
            onRetry = { scope.launch { journey.clearFlights(); journey.searchFlights(provider) } },
        )
        return
    }

    val filtered = remember(offers, packagePrices, sort, stops, airline, recommended?.id) {
        offers.filter { row ->
            val stopPass = when (stops) {
                StopsFilter.ALL -> true
                StopsFilter.NONSTOP -> row.outbound.stops == 0
                StopsFilter.ONE -> row.outbound.stops == 1
                StopsFilter.MULTIPLE -> row.outbound.stops >= 2
            }
            val airlinePass = airline == null || row.outbound.airlineCode?.uppercase() == airline
            stopPass && airlinePass
        }.sortedWith { a, b ->
            if (sort == FlightSort.RECOMMENDED) {
                if (a.id == recommended?.id && b.id != recommended?.id) return@sortedWith -1
                if (b.id == recommended?.id && a.id != recommended?.id) return@sortedWith 1
            }
            when (sort) {
                FlightSort.RECOMMENDED, FlightSort.CHEAPEST -> (packagePrices[a.id] ?: a.totalFare).compareTo(packagePrices[b.id] ?: b.totalFare)
                FlightSort.FASTEST -> a.outbound.durationMinutes.compareTo(b.outbound.durationMinutes)
            }.takeIf { it != 0 } ?: a.outbound.departureAt.compareTo(b.outbound.departureAt)
        }
    }
    val selectedId = state.selectedOutboundJourneyId
    val baseline = selectedId?.let(packagePrices::get) ?: recommended?.id?.let(packagePrices::get)

    FlightResultsScaffold(
        language = language,
        chrome = chrome,
        title = tr(language, "Выберите перелёт", "Choose your outbound flight", "Jo‘nash parvozini tanlang", "Жўнаш парвозини танланг"),
        eyebrow = tr(language, "Туда", "Outbound", "Borish", "Бориш"),
        subtitle = tr(language,
            "Разница в цене рассчитана по полной стоимости маршрута туда и обратно.",
            "Price differences are based on the complete outbound + return or open-jaw itinerary.",
            "Narx farqi to‘liq borish-qaytish yo‘nalishi narxi asosida hisoblangan.",
            "Нарх фарқи тўлиқ бориш-қайтиш йўналиши нархи асосида ҳисобланган."),
        countLabel = tr(language,
            "Показано ${filtered.size} из ${offers.size} найденных билетов",
            "Showing ${filtered.size} of ${offers.size} tickets found",
            "Topilgan ${offers.size} chiptadan ${filtered.size} tasi ko‘rsatilmoqda",
            "Топилган ${offers.size} чиптадан ${filtered.size} таси кўрсатилмоқда"),
        isSearching = state.isSearchingFlights,
        hasResults = offers.isNotEmpty(),
        onContinueSearch = { scope.launch { journey.searchFlights(provider) } },
        continueVisible = selectedId != null,
        continueTitle = if (state.trip.isRoundTripFlight)
            tr(language, "Выбрать билет и продолжить", "Select ticket and continue", "Chiptani tanlash va davom etish", "Чиптани танлаш ва давом этиш")
        else tr(language, "Выбрать билет и продолжить к трансферу", "Select ticket and continue to transfer", "Chiptani tanlash va transferga o‘tish", "Чиптани танлаш ва трансферга ўтиш"),
        onContinue = {
            if (state.trip.isRoundTripFlight) chrome.openReturnFlights()
            else {
                selectedId?.let(journey::selectReturnJourney)
                chrome.openTransferSelection()
            }
        },
        filters = {
            FlightFilters(
                language = language,
                isRoundTrip = state.trip.isRoundTripFlight,
                sort = sort,
                stops = stops,
                airline = airline,
                airlineCodes = airlineCodes,
                onSort = { sort = it },
                onStops = { stops = it },
                onAirline = { airline = it },
            )
        },
    ) {
        if (filtered.isEmpty()) {
            GeneratorCard {
                Text(tr(language, "Нет рейсов для выбранных фильтров.", "No flights match these filters.", "Tanlangan filtrlarga mos reys yo‘q.", "Танланган фильтрларга мос рейс йўқ."), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            }
        } else {
            filtered.forEach { offer ->
                FlightResultBlock(
                    language = language,
                    pair = offer,
                    leg = offer.outbound,
                    selected = selectedId == offer.id,
                    recommended = recommended?.id == offer.id,
                    packagePrice = packagePrices[offer.id],
                    baselinePrice = baseline,
                    onSelect = { journey.selectOutboundJourney(offer.id) },
                    onDetails = { chrome.openFlightDetails(offer.id, "outbound") },
                )
            }
        }
    }
}

/** SwiftUI ReturnFlightView parity. */
@Composable
fun ReturnFlightScreen(
    language: AppLanguage,
    journey: JourneyStore,
    provider: IgnavFlightInventoryProvider,
    packageEngine: RemotePackageEngineClient,
    chrome: AppChromeStore,
) {
    val state by journey.state.collectAsState()
    val scope = rememberCoroutineScope()
    val outbound = state.selectedOutboundJourney
    val candidates = remember(state.flightResults, outbound?.id) {
        if (outbound == null) emptyList() else state.flightResults.filter { sameFlight(it.outbound, outbound.outbound) && it.inbound != null }
    }
    var packagePrices by remember { mutableStateOf<Map<String, BigDecimal>>(emptyMap()) }
    LaunchedEffect(candidates.map { it.id }) {
        packagePrices = if (candidates.isEmpty()) emptyMap() else journey.packagePricePreviews(packageEngine, candidates.map { it.id })
    }
    val recommended = remember(candidates, packagePrices) {
        candidates.minWithOrNull(compareBy<LiveFlightJourneyCandidate>(
            { packagePrices[it.id] ?: it.totalFare },
            { it.inbound?.stops ?: Int.MAX_VALUE },
            { it.inbound?.durationMinutes ?: Int.MAX_VALUE },
        ))
    }
    LaunchedEffect(recommended?.id) {
        if (state.selectedJourneyId == null) recommended?.let { journey.selectReturnJourney(it.id) }
    }
    val selectedId = state.selectedJourneyId
    val baseline = selectedId?.let(packagePrices::get) ?: recommended?.id?.let(packagePrices::get)

    if (candidates.isEmpty()) {
        FlightSearchGate(
            language = language,
            outbound = false,
            dateText = formatDate(state.trip.returnDate.toString(), language),
            message = searchGateFallback(language),
            chrome = chrome,
            onRetry = { scope.launch { journey.continueSearchFlights(provider) } },
        )
        return
    }

    FlightResultsScaffold(
        language = language,
        chrome = chrome,
        title = tr(language, "Выберите обратный перелёт", "Choose your return flight", "Qaytish parvozini tanlang", "Қайтиш парвозини танланг"),
        eyebrow = tr(language, "Обратно", "Return", "Qaytish", "Қайтиш"),
        subtitle = tr(language,
            "Каждый обратный рейс совместим с выбранным рейсом туда и рассчитан как один полный маршрут.",
            "Each return option matches your selected outbound and is priced as one complete journey.",
            "Har bir qaytish reysi tanlangan borish reysiga mos va bitta to‘liq yo‘nalish sifatida narxlangan.",
            "Ҳар бир қайтиш рейси танланган бориш рейсига мос ва битта тўлиқ йўналиш сифатида нархланган."),
        countLabel = tr(language,
            "Найдено билетов обратно: ${candidates.size}",
            "Return tickets found: ${candidates.size}",
            "Qaytish chiptalari topildi: ${candidates.size}",
            "Қайтиш чипталари топилди: ${candidates.size}"),
        isSearching = state.isSearchingFlights,
        hasResults = true,
        onContinueSearch = { scope.launch { journey.continueSearchFlights(provider) } },
        continueVisible = selectedId != null,
        continueTitle = tr(language, "Выбрать билет и продолжить к трансферу", "Select ticket and continue to transfer", "Chiptani tanlash va transferga o‘tish", "Чиптани танлаш ва трансферга ўтиш"),
        onContinue = chrome::openTransferSelection,
        filters = null,
    ) {
        candidates.sortedWith(compareBy({ if (it.id == recommended?.id) 0 else 1 }, { packagePrices[it.id] ?: it.totalFare })).forEach { offer ->
            val leg = offer.inbound ?: return@forEach
            FlightResultBlock(
                language = language,
                pair = offer,
                leg = leg,
                selected = selectedId == offer.id,
                recommended = recommended?.id == offer.id,
                packagePrice = packagePrices[offer.id],
                baselinePrice = baseline,
                onSelect = { journey.selectReturnJourney(offer.id) },
                onDetails = { chrome.openFlightDetails(offer.id, "inbound") },
            )
        }
    }
}

@Composable
private fun FlightResultsScaffold(
    language: AppLanguage,
    chrome: AppChromeStore,
    title: String,
    eyebrow: String,
    subtitle: String,
    countLabel: String,
    isSearching: Boolean,
    hasResults: Boolean,
    onContinueSearch: () -> Unit,
    continueVisible: Boolean,
    continueTitle: String,
    onContinue: () -> Unit,
    filters: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(generatorPageColor()).statusBarsPadding()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = GeneratorGeometry.pagePadding, end = GeneratorGeometry.pagePadding, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { GeneratorHeader(GeneratorStage.FLIGHT, language, chrome) }
            item { FlightSectionHeader(title, eyebrow, subtitle) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(CupertinoSymbol.Checklist, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
                    Text(countLabel, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                }
            }
            item { RefundPolicyCard(language, "flight") { chrome.openBookingPolicy("refund") } }
            filters?.let { item { it() } }
            item { content() }
            item { FlightSearchProgressCard(language, isSearching, hasResults, onContinueSearch) }
        }
        if (continueVisible) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(generatorCardColor()).navigationBarsPadding(),
            ) {
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                Box(Modifier.fillMaxWidth().padding(horizontal = GeneratorGeometry.pagePadding).padding(top = 10.dp, bottom = 8.dp)) {
                    GeneratorPrimaryButton(
                        title = continueTitle,
                        enabled = true,
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlightSectionHeader(title: String, eyebrow: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(eyebrow.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
        Text(title, fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
        Text(subtitle, fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .56f))
    }
}

@Composable
private fun FlightFilters(
    language: AppLanguage,
    isRoundTrip: Boolean,
    sort: FlightSort,
    stops: StopsFilter,
    airline: String?,
    airlineCodes: List<String>,
    onSort: (FlightSort) -> Unit,
    onStops: (StopsFilter) -> Unit,
    onAirline: (String?) -> Unit,
) {
    GeneratorCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(CupertinoSymbol.AirplaneTakeoff, null, Modifier.size(16.dp))
                Text(if (isRoundTrip) tr(language, "Выберите перелёт туда", "Choose your outbound flight", "Borish reysini tanlang", "Бориш рейсини танланг") else tr(language, "Выберите перелёт", "Choose your flight", "Parvozni tanlang", "Парвозни танланг"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            FilterSection(tr(language, "Сортировка", "Sort", "Saralash", "Саралаш")) {
                FlightSort.entries.forEach { value ->
                    FilterChip(
                        when (value) {
                            FlightSort.RECOMMENDED -> tr(language, "Рекомендуем", "Recommended", "Tavsiya", "Тавсия")
                            FlightSort.CHEAPEST -> tr(language, "Дешевле", "Cheapest", "Arzonroq", "Арзонроқ")
                            FlightSort.FASTEST -> tr(language, "Быстрее", "Fastest", "Tezroq", "Тезроқ")
                        }, sort == value
                    ) { onSort(value) }
                }
            }
            FilterSection(tr(language, "Пересадки", "Stops", "To‘xtashlar", "Тўхташлар")) {
                StopsFilter.entries.forEach { value ->
                    FilterChip(
                        when (value) {
                            StopsFilter.ALL -> tr(language, "Все", "All", "Barchasi", "Барчаси")
                            StopsFilter.NONSTOP -> tr(language, "Без пересадок", "Nonstop", "To‘g‘ridan-to‘g‘ri", "Тўғридан-тўғри")
                            StopsFilter.ONE -> tr(language, "1 пересадка", "1 stop", "1 to‘xtash", "1 тўхташ")
                            StopsFilter.MULTIPLE -> tr(language, "2+ пересадки", "2+ stops", "2+ to‘xtash", "2+ тўхташ")
                        }, stops == value
                    ) { onStops(value) }
                }
            }
            if (airlineCodes.isNotEmpty()) {
                FilterSection(tr(language, "Авиакомпании", "Airlines", "Aviakompaniyalar", "Авиакомпаниялар")) {
                    FilterChip(tr(language, "Все", "All", "Barchasi", "Барчаси"), airline == null) { onAirline(null) }
                    airlineCodes.forEach { code -> FilterChip(code, airline == code) { onAirline(code) } }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}


@Composable
private fun FilterChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    IumrahPressable(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        cornerRadius = 99.dp,
        background = if (selected) fg else generatorRaisedColor(),
    ) {
        Box(Modifier.height(36.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) generatorCardColor() else fg, maxLines = 1)
        }
    }
}

@Composable
private fun FlightResultBlock(
    language: AppLanguage,
    pair: LiveFlightJourneyCandidate,
    leg: LiveFlightCandidate,
    selected: Boolean,
    recommended: Boolean,
    packagePrice: BigDecimal?,
    baselinePrice: BigDecimal?,
    onSelect: () -> Unit,
    onDetails: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlightParityCard(language, pair, leg, selected, recommended, packagePrice, baselinePrice, onSelect)
        IumrahPressable(onClick = onDetails, modifier = Modifier.fillMaxWidth().height(34.dp), cornerRadius = 12.dp, background = Color.Transparent) {
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(tr(language, "Подробнее о перелёте", "Flight details", "Parvoz tafsilotlari", "Парвоз тафсилотлари"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun FlightParityCard(
    language: AppLanguage,
    pair: LiveFlightJourneyCandidate,
    leg: LiveFlightCandidate,
    selected: Boolean,
    recommended: Boolean,
    packagePrice: BigDecimal?,
    baselinePrice: BigDecimal?,
    onClick: () -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(28.dp)
    val delta = if (packagePrice != null && baselinePrice != null) packagePrice.subtract(baselinePrice) else null
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp, background = Color.Transparent) {
        Column(
            Modifier.fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = fg.copy(alpha = .045f), spotColor = fg.copy(alpha = .045f))
                .clip(shape)
                .background(generatorCardColor())
                .border(if (selected) 1.7.dp else .8.dp, fg.copy(alpha = if (selected) 1f else .08f), shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AirlineLogo(leg.airlineCode)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(leg.airline, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(leg.flightNumber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = .55f), maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatDelta(delta), fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(tr(language, "к пакету / 1 человек", "package difference / traveler", "paket farqi / 1 kishi", "пакет фарқи / 1 киши"), modifier = Modifier.width(108.dp), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = fg.copy(alpha = .52f), textAlign = TextAlign.End, maxLines = 2)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FlightTimeBlock(leg, true, Modifier.width(82.dp), Alignment.Start)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f).height(1.5.dp).background(fg.copy(alpha = .18f)))
                        Icon(CupertinoSymbol.Airplane, null, Modifier.size(12.dp), tint = fg.copy(alpha = .58f))
                        Box(Modifier.weight(1f).height(1.5.dp).background(fg.copy(alpha = .18f)))
                    }
                    Text(durationText(language, leg.durationMinutes), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = .54f), maxLines = 1)
                }
                FlightTimeBlock(leg, false, Modifier.width(82.dp), Alignment.End)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stopRouteLabel(language, leg), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (recommended) {
                        Box(Modifier.clip(CircleShape).background(fg).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(tr(language, "Рекомендуем", "Recommended", "Tavsiya", "Тавсия"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = generatorCardColor())
                        }
                    }
                    if (selected) Icon(CupertinoSymbol.CheckCircle, null, Modifier.size(15.dp), tint = Color(0xFF34C759))
                }
                leg.connectionAirports?.firstOrNull()?.let { airport ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(CupertinoSymbol.Refresh, null, Modifier.size(13.dp), tint = fg.copy(alpha = .5f))
                        Text(tr(language, "Пересадка · ${airport.displayCity} (${airport.code})", "Connection · ${airport.displayCity} (${airport.code})", "Ulanish · ${airport.displayCity} (${airport.code})", "Уланиш · ${airport.displayCity} (${airport.code})"), fontSize = 12.sp, color = fg.copy(alpha = .55f), maxLines = 2)
                    }
                }
            }

            Divider(color = fg.copy(alpha = .12f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Meta(CupertinoSymbol.Suitcase, cabinLabel(language, leg.cabinClass))
                pair.baggage?.carryOn?.takeIf { it > 0 }?.let { Meta(CupertinoSymbol.Suitcase, "×$it") }
                pair.baggage?.checked?.takeIf { it > 0 }?.let { Meta(CupertinoSymbol.Suitcase, "×$it") }
            }
        }
    }
}

@Composable
private fun Meta(symbol: CupertinoSymbol, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(symbol, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
    }
}

@Composable
private fun AirlineLogo(codeRaw: String?) {
    val code = codeRaw?.uppercase()?.takeIf { it.length == 2 }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(38.dp).clip(shape).background(Color.White), contentAlignment = Alignment.Center) {
        if (code != null) {
            AsyncImage(
                model = "https://www.gstatic.com/flights/airline_logos/70px/$code.png",
                contentDescription = code,
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit,
            )
        } else Icon(CupertinoSymbol.Airplane, null, Modifier.size(20.dp), tint = Color(0xFF007AFF))
    }
}

@Composable
private fun FlightTimeBlock(leg: LiveFlightCandidate, departure: Boolean, modifier: Modifier, alignment: Alignment.Horizontal) {
    val airport = if (departure) leg.segments?.firstOrNull()?.origin else leg.segments?.lastOrNull()?.destination
    val code = if (departure) leg.origin else leg.destination
    val city = airport?.displayCity ?: code
    Column(modifier, horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatTime(leg, departure), fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (!departure && arrivesNextDay(leg)) Text("+1", fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = Color.Red)
        }
        Text(code, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(city, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RefundPolicyCard(language: AppLanguage, component: String, onClick: () -> Unit) {
    val title = if (component == "hotel") tr(language, "Отель", "Hotel", "Mehmonxona", "Меҳмонхона") else tr(language, "Авиабилет", "Flight", "Aviachipta", "Авиачипта")
    val badge = tr(language, "По умолчанию без возврата", "Non-refundable by default", "Odatda qaytarilmaydi", "Одатда қайтарилмайди")
    val shape = RoundedCornerShape(20.dp)
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, background = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(generatorCardColor()).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .075f), shape).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF007AFF).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(if (component == "hotel") CupertinoSymbol.Hotel else CupertinoSymbol.Airplane, null, Modifier.size(16.dp), tint = if (component == "hotel") Color(0xFFAF52DE) else Color(0xFF007AFF))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(badge, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), maxLines = 2)
            }
            Icon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
        }
    }
}

@Composable
private fun FlightSearchProgressCard(language: AppLanguage, isSearching: Boolean, hasResults: Boolean, onContinue: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(generatorCardColor()).border(.7.dp, fg.copy(alpha = .055f), RoundedCornerShape(26.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF007AFF).copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                Icon(if (isSearching) CupertinoSymbol.SignalWave else CupertinoSymbol.Refresh, null, Modifier.size(18.dp), tint = Color(0xFF007AFF))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (isSearching) tr(language, "Продолжаем поиск", "Searching for more", "Qidiruv davom etmoqda", "Қидирув давом этмоқда") else tr(language, "Ищем ещё варианты", "Find more options", "Yana variant izlash", "Яна вариант излаш"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (hasResults) tr(language, "Найденные рейсы уже можно сравнивать. Новые варианты появятся здесь автоматически.", "You can already compare verified flights. New options will appear automatically.", "Topilgan reyslarni hozirdanoq solishtiring. Yangi variantlar avtomatik qo‘shiladi.", "Топилган рейсларни ҳозирданоқ солиштиринг. Янги вариантлар автоматик қўшилади.")
                    else tr(language, "Получаем актуальные рейсы. Первый подтверждённый вариант появится сразу.", "Fetching current flights. The first verified option will appear immediately.", "Dolzarb reyslarni olyapmiz. Birinchi tasdiqlangan variant darhol chiqadi.", "Долзарб рейсларни оляпмиз. Биринчи тасдиқланган вариант дарҳол чиқади."),
                    fontSize = 12.sp, lineHeight = 16.sp, color = fg.copy(alpha = .55f),
                )
            }
        }
        if (isSearching) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(5) { index -> Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(fg.copy(alpha = .18f + index * .035f))) }
            }
        } else {
            IumrahPressable(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp), cornerRadius = 18.dp, background = generatorRaisedColor()) {
                Row(Modifier.fillMaxSize().padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(language, "Продолжить поиск", "Continue search", "Qidiruvni davom ettirish", "Қидирувни давом эттириш"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f)); Icon(CupertinoSymbol.Refresh, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun FlightSearchImmersive(language: AppLanguage, chrome: AppChromeStore) {
    var step by remember { mutableIntStateOf(0) }
    val messages = searchMessages(language)
    DisposableEffect(Unit) {
        chrome.setImmersive(true)
        onDispose { chrome.setImmersive(false) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(7_000)
            step = (step + 1).coerceAtMost(messages.lastIndex)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            LoopingRawVideo("flight_search", Modifier.fillMaxWidth().height(380.dp))
            Image(painterResource(R.drawable.iumrah_header_wordmark_dark), null, Modifier.width(188.dp).padding(top = 0.dp, bottom = 22.dp), contentScale = ContentScale.Fit)
            Text(messages.getOrElse(step) { messages.last() }, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(10.dp))
            Text(tr(language, "Обычно поиск занимает 1–2 минуты", "Search usually takes 1–2 minutes", "Odatda qidiruv 1–2 daqiqa davom etadi", "Одатда қидирув 1–2 дақиқа давом этади"), fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = .62f), textAlign = TextAlign.Center, maxLines = 3, modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(70.dp))
        }
    }
}

@Composable
private fun FlightSearchGate(
    language: AppLanguage,
    outbound: Boolean,
    dateText: String,
    message: String,
    chrome: AppChromeStore,
    onRetry: () -> Unit,
) {
    LaunchedEffect(Unit) { chrome.setImmersive(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(generatorPageColor()).statusBarsPadding(),
        contentPadding = PaddingValues(start = GeneratorGeometry.pagePadding, end = GeneratorGeometry.pagePadding, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { GeneratorHeader(GeneratorStage.FLIGHT, language, chrome) }
        item {
            FlightSectionHeader(
                tr(language, "Поиск перелёта", "Flight search", "Parvoz qidiruvi", "Парвоз қидируви"),
                if (outbound) tr(language, "ТУДА", "OUTBOUND", "BORISH", "БОРИШ") else tr(language, "ОБРАТНО", "RETURN", "QAYTISH", "ҚАЙТИШ"),
                tr(language, "Экран выбора откроется только после получения хотя бы одного подтверждённого варианта.", "Flight selection opens only after at least one verified option is available.", "Kamida bitta tasdiqlangan variant kelgandan keyin tanlov ekrani ochiladi.", "Камида битта тасдиқланган вариант келгандан кейин танлов экрани очилади."),
            )
        }
        item {
            GeneratorCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(CupertinoSymbol.Airplane, null, Modifier.size(30.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(tr(language, "Рейсы пока не загружены", "Flights are not loaded yet", "Reyslar hali yuklanmadi", "Рейслар ҳали юкланмади"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(dateText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                        }
                    }
                    Text(message, fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
                    GeneratorPrimaryButton(tr(language, "Повторить поиск", "Retry search", "Qidiruvni takrorlash", "Қидирувни такрорлаш"), true, onClick = onRetry)
                }
            }
        }
    }
}

@Composable
fun FlightDetailsScreen(journeyId: String, direction: String, language: AppLanguage, journey: JourneyStore, chrome: AppChromeStore) {
    val state by journey.state.collectAsState()
    val pair = state.flightResults.firstOrNull { it.id == journeyId }
    val leg = if (direction == "inbound") pair?.inbound else pair?.outbound
    LazyColumn(
        Modifier.fillMaxSize().background(generatorPageColor()).statusBarsPadding(),
        contentPadding = PaddingValues(start = GeneratorGeometry.pagePadding, end = GeneratorGeometry.pagePadding, top = 12.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { GeneratorHeader(GeneratorStage.FLIGHT, language, chrome) }
        item { FlightSectionHeader(tr(language, "Детали перелёта", "Flight details", "Parvoz tafsilotlari", "Парвоз тафсилотлари"), tr(language, "РЕЙС", "FLIGHT", "PARVOZ", "ПАРВОЗ"), tr(language, "Маршрут, сегменты, багаж и условия выбранного рейса.", "Route, segments, baggage and conditions for the selected flight.", "Tanlangan reys yo‘nalishi, segmentlari, bagaji va shartlari.", "Танланган рейс йўналиши, сегментлари, багажи ва шартлари.")) }
        item { RefundPolicyCard(language, "flight") { chrome.openBookingPolicy("refund") } }
        if (pair == null || leg == null) {
            item { GeneratorCard { Text(tr(language, "Рейс больше недоступен.", "This flight is no longer available.", "Bu reys endi mavjud emas.", "Бу рейс энди мавжуд эмас."), fontSize = 15.sp) } }
        } else {
            item { FlightDetailHero(language, pair, leg) }
            items(leg.segments.orEmpty(), key = { it.id }) { segment ->
                GeneratorCard {
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(CupertinoSymbol.Airplane, null, Modifier.size(20.dp), tint = Color(0xFF007AFF)); Spacer(Modifier.width(9.dp)); Text("${segment.airline} ${segment.flightNumber}", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        TimelineRow(segment.origin.code, formatInstant(segment.departureAt, segment.origin.timeZoneIdentifier), segment.origin.displayAirport)
                        Row(Modifier.padding(start = 11.dp).height(32.dp)) { Box(Modifier.width(2.dp).fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(alpha = .13f))) }
                        TimelineRow(segment.destination.code, formatInstant(segment.arrivalAt, segment.destination.timeZoneIdentifier), segment.destination.displayAirport)
                        Text(durationText(language, segment.durationMinutes), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                    }
                }
            }
            item {
                GeneratorCard {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(tr(language, "Багаж и тариф", "Baggage & fare", "Bagaj va tarif", "Багаж ва тариф"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        DetailLine(tr(language, "Класс", "Cabin", "Klass", "Класс"), cabinLabel(language, leg.cabinClass))
                        DetailLine(tr(language, "Ручная кладь", "Carry-on", "Qo‘l yuki", "Қўл юки"), pair.baggage?.carryOn?.toString() ?: "—")
                        DetailLine(tr(language, "Багаж", "Checked baggage", "Bagaj", "Багаж"), pair.baggage?.checked?.toString() ?: "—")
                        DetailLine(tr(language, "Источник", "Source", "Manba", "Манба"), pair.sourceName)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightDetailHero(language: AppLanguage, pair: LiveFlightJourneyCandidate, leg: LiveFlightCandidate) {
    val dark = (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue) < 1.5f
    val colors = if (dark) listOf(Color(0xFF17191D), Color(0xFF252A33)) else listOf(Color(0xFFEEF4FF), Color(0xFFE1EAFA))
    val fg = if (dark) Color.White else Color.Black
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(colors)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AirlineLogo(leg.airlineCode); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(leg.airline, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg); Text("${leg.flightNumber} · ${pair.sourceName}", fontSize = 12.sp, color = fg.copy(alpha = .55f)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlightTimeBlock(leg, true, Modifier.weight(1f), Alignment.Start); Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(21.dp), tint = fg.copy(alpha = .5f)); FlightTimeBlock(leg, false, Modifier.weight(1f), Alignment.End)
        }
    }
}

@Composable
private fun TimelineRow(code: String, time: String, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, Color(0xFF007AFF), CircleShape)); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text("$time · $code", fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun DetailLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)); Spacer(Modifier.weight(1f)); Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

private fun sameFlight(a: LiveFlightCandidate, b: LiveFlightCandidate) = a.flightNumber == b.flightNumber && a.departureAt == b.departureAt && a.origin == b.origin && a.destination == b.destination
private fun formatDelta(delta: BigDecimal?): String {
    if (delta == null) return "—"
    val rounded = delta.abs().setScale(0, RoundingMode.HALF_UP).toPlainString()
    return when { delta.signum() > 0 -> "+$$rounded"; delta.signum() < 0 -> "−$$rounded"; else -> "$0" }
}
private fun formatTime(leg: LiveFlightCandidate, departure: Boolean): String {
    val segment = if (departure) leg.segments?.firstOrNull()?.origin else leg.segments?.lastOrNull()?.destination
    val zone = segment?.timeZoneIdentifier?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
    val instant = if (departure) leg.departureAt else leg.arrivalAt
    return DateTimeFormatter.ofPattern("HH:mm").format(instant.atZone(zone))
}
private fun formatInstant(instant: java.time.Instant, zoneId: String?): String {
    val zone = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
    return DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.ENGLISH).format(instant.atZone(zone))
}
private fun formatDate(value: String, language: AppLanguage): String = runCatching {
    val date = java.time.LocalDate.parse(value)
    val locale = when (language) { AppLanguage.RUSSIAN -> Locale("ru"); AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> language.locale; else -> Locale("uz") }
    DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date)
}.getOrDefault(value)
private fun arrivesNextDay(leg: LiveFlightCandidate): Boolean {
    val zone = leg.segments?.firstOrNull()?.origin?.timeZoneIdentifier?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
    return leg.departureAt.atZone(zone).toLocalDate() != leg.arrivalAt.atZone(zone).toLocalDate()
}
private fun durationText(language: AppLanguage, minutes: Int): String {
    val h = minutes.coerceAtLeast(0) / 60; val m = minutes.coerceAtLeast(0) % 60
    return when (language) {
        AppLanguage.RUSSIAN -> if (m == 0) "${h}ч" else "${h}ч ${m}м"
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> if (m == 0) "${h}h" else "${h}h ${m}m"
        AppLanguage.UZBEK -> if (m == 0) "${h}soat" else "${h}soat ${m}d"
        AppLanguage.UZBEK_CYRILLIC -> if (m == 0) "${h}соат" else "${h}соат ${m}д"
    }
}
private fun stopRouteLabel(language: AppLanguage, leg: LiveFlightCandidate): String {
    val stop = when (language) {
        AppLanguage.RUSSIAN -> if (leg.stops == 0) "прямой" else if (leg.stops == 1) "1 пересадка" else "${leg.stops} пересадки"
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> if (leg.stops == 0) "nonstop" else if (leg.stops == 1) "1 stop" else "${leg.stops} stops"
        AppLanguage.UZBEK -> if (leg.stops == 0) "to‘g‘ridan-to‘g‘ri" else "${leg.stops} ta to‘xtash"
        AppLanguage.UZBEK_CYRILLIC -> if (leg.stops == 0) "тўғридан-тўғри" else "${leg.stops} та тўхташ"
    }
    return "$stop · ${leg.origin} → ${leg.destination}"
}
private fun cabinLabel(language: AppLanguage, value: String?): String {
    return when (value?.lowercase()) {
        "business" -> tr(language, "Бизнес", "Business", "Business", "Business")
        "first" -> tr(language, "Первый", "First", "First", "First")
        "premium_economy" -> tr(language, "Премиум эконом", "Premium economy", "Premium economy", "Premium economy")
        else -> tr(language, "Эконом", "Economy", "Economy", "Economy")
    }
}
private fun searchGateFallback(language: AppLanguage) = tr(language, "Поиск завершён без рейса, который прошёл все проверки iumrah.", "Search completed without a flight that passed all iumrah checks.", "Qidiruv iumrah tekshiruvlaridan o‘tgan reyssiz yakunlandi.", "Қидирув iumrah текширувларидан ўтган рейссиз якунланди.")
private fun searchMessages(language: AppLanguage): List<String> = when (language) {
    AppLanguage.RUSSIAN -> listOf("Ищем лучшие варианты для вашей поездки", "Проверяем доступные рейсы", "Сравниваем комбинации перелёта туда и обратно", "Сравниваем расположение, условия и стоимость", "Проверяем маршруты на выбранные даты", "Подбираем оптимальные варианты трансфера", "Собираем всё в одну поездку", "Сравниваем доступные комбинации", "Сравниваем цены маршрутов туда и обратно", "Выбираем наиболее подходящие варианты", "Почти готово — завершаем подбор", "Готовим вашу поездку…", "Мы продолжаем поиск — это может занять немного времени", "Проверяем больше вариантов, чтобы не торопиться с выбором", "Ещё немного — завершаем сравнение предложений", "Ваши варианты почти готовы")
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> listOf("Searching for the best options for your trip", "Checking available flights", "Comparing return and open-jaw fare combinations", "Comparing location, conditions and cost", "Checking matching itineraries for your dates", "Selecting suitable transfer options", "Bringing everything into one trip", "Comparing available combinations", "Comparing complete journey fares", "Choosing the most suitable options", "Almost ready — finishing the search", "Preparing your trip…", "We are still searching — this can take a little longer", "Checking more options so we do not rush your choice", "Just a little more — finishing the comparison", "Your options are almost ready")
    AppLanguage.UZBEK -> listOf("Safaringiz uchun eng yaxshi variantlarni izlayapmiz", "Mavjud reyslarni tekshiryapmiz", "Borish-qaytish kombinatsiyalarini solishtiryapmiz", "Joylashuv, shartlar va narxni solishtiryapmiz", "Tanlangan sanalarga mos yo‘nalishlarni tekshiryapmiz", "Mos transfer variantlarini tanlayapmiz", "Barchasini bitta safarga birlashtiryapmiz", "Mavjud kombinatsiyalarni solishtiryapmiz", "Borish-qaytish yo‘nalishlari narxlarini solishtiryapmiz", "Eng mos variantlarni tanlayapmiz", "Deyarli tayyor — tanlovni yakunlayapmiz", "Safaringizni tayyorlayapmiz…", "Qidiruv davom etmoqda — biroz ko‘proq vaqt ketishi mumkin", "Tanlovga shoshilmaslik uchun ko‘proq variantlarni tekshiryapmiz", "Yana ozgina — takliflarni solishtirishni yakunlayapmiz", "Variantlaringiz deyarli tayyor")
    AppLanguage.UZBEK_CYRILLIC -> listOf("Сафарингиз учун энг яхши вариантларни излаяпмиз", "Мавжуд рейсларни текширяпмиз", "Бориш-қайтиш комбинацияларини солиштиряпмиз", "Жойлашув, шартлар ва нархни солиштиряпмиз", "Танланган саналарга мос йўналишларни текширяпмиз", "Мос трансфер вариантларини танлаяпмиз", "Барчасини битта сафарга бирлаштиряпмиз", "Мавжуд комбинацияларни солиштиряпмиз", "Бориш-қайтиш йўналишлари нархларини солиштиряпмиз", "Энг мос вариантларни танлаяпмиз", "Деярли тайёр — танловни якунлаяпмиз", "Сафарингизни тайёрлаяпмиз…", "Қидирув давом этмоқда — бироз кўпроқ вақт кетиши мумкин", "Танловга шошилмаслик учун кўпроқ вариантларни текширяпмиз", "Яна озгина — таклифларни солиштиришни якунлаяпмиз", "Вариантларингиз деярли тайёр")
}
private fun tr(language: AppLanguage, ru: String, en: String, uz: String, uzCy: String): String = when (language) { AppLanguage.RUSSIAN -> ru; AppLanguage.ENGLISH -> en
    AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language); AppLanguage.UZBEK -> uz; AppLanguage.UZBEK_CYRILLIC -> uzCy }
