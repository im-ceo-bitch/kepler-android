package com.iumrah.beta.ui.trip

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.DateFlexibility
import com.iumrah.beta.domain.trip.FlightTripType
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageFlightPath
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.generator.GeneratorCard
import com.iumrah.beta.ui.generator.GeneratorGeometry
import com.iumrah.beta.ui.generator.GeneratorHeader
import com.iumrah.beta.ui.generator.GeneratorPrimaryButton
import com.iumrah.beta.ui.generator.GeneratorStage
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.generator.generatorRaisedColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class GeneratorFlightChoice { PUBLISHED_DIRECT, FLEXIBLE_DATES, WEEKEND }
private enum class CuratedLegSelection { OUTBOUND, RETURN }

@Composable
fun TripBuilderScreen(
    language: AppLanguage,
    journey: JourneyStore,
    airports: AirportSearchService,
    curatedFlights: CuratedFlightRecommendationService,
    chrome: AppChromeStore,
) {
    val journeyState by journey.state.collectAsState()
    val initial = journeyState.trip
    var draft by remember { mutableStateOf(initial.copy(flightTripType = FlightTripType.ROUND_TRIP)) }
    var mode by remember {
        mutableStateOf(
            when {
                initial.isWeekendUmrah || journeyState.packageFlightPath == PackageFlightPath.WEEKEND -> GeneratorFlightChoice.WEEKEND
                journeyState.packageFlightPath == PackageFlightPath.FLEXIBLE_DATES -> GeneratorFlightChoice.FLEXIBLE_DATES
                else -> GeneratorFlightChoice.PUBLISHED_DIRECT
            },
        )
    }
    var recommendations by remember { mutableStateOf<List<CuratedFlightRecommendation>>(emptyList()) }
    var loadingCurated by remember { mutableStateOf(false) }
    var curatedError by remember { mutableStateOf<String?>(null) }
    var selectedCompleteID by remember { mutableStateOf(journeyState.selectedPublishedCompleteID) }
    var selectedOutboundID by remember { mutableStateOf(journeyState.selectedPublishedOutboundID) }
    var selectedReturnID by remember { mutableStateOf(journeyState.selectedPublishedReturnID) }
    var showDateCalendar by remember { mutableStateOf(false) }
    var showWeekendCalendar by remember { mutableStateOf(false) }

    val routeKey = "${draft.originCode}|${draft.scope}|${draft.arrivalAirport}"
    var loadedRouteKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(routeKey) {
        val routeChanged = loadedRouteKey != null && loadedRouteKey != routeKey
        loadedRouteKey = routeKey
        if (routeChanged && mode != GeneratorFlightChoice.WEEKEND) {
            // Mirrors SwiftUI onChange(routeSelectionKey): a real route edit returns
            // the generator to Published Direct and clears only stale flight IDs.
            mode = GeneratorFlightChoice.PUBLISHED_DIRECT
            draft = draft.copy(flexibility = DateFlexibility.EXACT, flightTripType = FlightTripType.ROUND_TRIP)
            selectedCompleteID = null
            selectedOutboundID = null
            selectedReturnID = null
        }
    }
    LaunchedEffect(routeKey, mode) {
        if (mode != GeneratorFlightChoice.WEEKEND) {
            loadingCurated = true
            curatedError = null
            recommendations = runCatching { curatedFlights.load(draft) }
                .onFailure { curatedError = it.message }
                .getOrDefault(emptyList())
            loadingCurated = false
        }
    }

    val outboundFlights = recommendations.filter { recommendation ->
        recommendation.inbound == null && recommendation.nonstop &&
            recommendation.outbound.origin.equals(draft.originCode, true) &&
            recommendation.outbound.destination.equals(draft.outboundDestinationCode, true)
    }
    val returnFlights = recommendations.filter { recommendation ->
        recommendation.inbound == null && recommendation.nonstop &&
            recommendation.outbound.origin.equals(draft.returnOriginCode, true) &&
            recommendation.outbound.destination.equals(draft.originCode, true)
    }.filter { recommendation ->
        if (selectedOutboundID == null) true
        else runCatching { LocalDate.parse(recommendation.outboundDate) }.getOrNull()?.let { !it.isBefore(draft.departureDate) } == true
    }

    val canContinue = draft.canContinue && when (mode) {
        GeneratorFlightChoice.PUBLISHED_DIRECT -> selectedCompleteID != null || (selectedOutboundID != null && selectedReturnID != null)
        GeneratorFlightChoice.FLEXIBLE_DATES -> !draft.isWeekendUmrah
        GeneratorFlightChoice.WEEKEND -> draft.isWeekendUmrah
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(generatorPageColor()).statusBarsPadding(),
        contentPadding = PaddingValues(start = GeneratorGeometry.pagePadding, end = GeneratorGeometry.pagePadding, top = 12.dp, bottom = 42.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { GeneratorHeader(GeneratorStage.TRIP, language, chrome) }
        item { TripIntro(language) }
        item {
            GeneratorCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardTitle(CupertinoSymbol.AirplaneTakeoff, tr(language, "Откуда вылетаете", "Where are you flying from?", "Qayerdan uchasiz?", "Қаердан учасиз?"))
                    ConfiguratorAirportSelector(
                        language = language,
                        service = airports,
                        airport = draft.originAirport,
                        fallbackCode = draft.originCode,
                    ) { airport ->
                        draft = draft.copy(origin = airport.iata.uppercase(), originAirport = airport)
                    }
                    if (mode != GeneratorFlightChoice.WEEKEND) {
                        Text(tr(language, "Маршрут", "Route", "Yo‘nalish", "Йўналиш"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                        SegmentedChoice(
                            labels = listOf(
                                JourneyScope.MAKKAH_ONLY to tr(language, "Только Мекка", "Makkah only", "Faqat Makka", "Фақат Макка"),
                                JourneyScope.MAKKAH_AND_MADINAH to tr(language, "Мекка + Медина", "Makkah + Madinah", "Makka + Madina", "Макка + Мадина"),
                            ),
                            selected = draft.scope,
                            onSelect = { draft = draft.copy(scope = it) },
                        )
                        if (draft.scope == JourneyScope.MAKKAH_AND_MADINAH) {
                            Text(tr(language, "Аэропорт прибытия", "Arrival airport", "Yetib borish aeroporti", "Етиб бориш аэропорти"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                            SegmentedChoice(
                                labels = listOf(
                                    SaudiArrivalAirport.JEDDAH to "JED · Jeddah",
                                    SaudiArrivalAirport.MADINAH to "MED · Madinah",
                                ),
                                selected = draft.arrivalAirport,
                                onSelect = { draft = draft.copy(arrivalAirport = it) },
                            )
                            Text(
                                if (draft.arrivalAirport == SaudiArrivalAirport.MADINAH)
                                    tr(language, "Прилёт в Медину, возвращение через Джидду.", "Arrive in Madinah, return via Jeddah.", "Madinaga yetib boring, Jidda orqali qayting.", "Мадинага етиб боринг, Жидда орқали қайтинг.")
                                else tr(language, "Прилёт в Джидду, возвращение из Медины.", "Arrive in Jeddah, return from Madinah.", "Jiddaga yetib boring, Madinadan qayting.", "Жиддага етиб боринг, Мадинадан қайтинг."),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                            )
                        }
                    } else WeekendRouteSummary(draft.originCode)
                }
            }
        }
        item {
            GeneratorCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CardTitle(CupertinoSymbol.Airplane, flightChoiceTitle(language))
                    Text(flightChoiceBody(language), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(directFlightsModeTitle(language), CupertinoSymbol.Airplane, mode == GeneratorFlightChoice.PUBLISHED_DIRECT, Modifier.weight(1f)) {
                            if (mode != GeneratorFlightChoice.PUBLISHED_DIRECT || draft.isWeekendUmrah) {
                                mode = GeneratorFlightChoice.PUBLISHED_DIRECT
                                draft = draft.copy(flexibility = DateFlexibility.EXACT, flightTripType = FlightTripType.ROUND_TRIP)
                                selectedCompleteID = null; selectedOutboundID = null; selectedReturnID = null
                            }
                        }
                        ModeChip(flexibleDatesModeTitle(language), CupertinoSymbol.CalendarClock, mode == GeneratorFlightChoice.FLEXIBLE_DATES, Modifier.weight(1f)) {
                            if (mode != GeneratorFlightChoice.FLEXIBLE_DATES || draft.isWeekendUmrah) {
                                mode = GeneratorFlightChoice.FLEXIBLE_DATES
                                draft = draft.copy(flexibility = DateFlexibility.EXACT, flightTripType = FlightTripType.ROUND_TRIP)
                                selectedCompleteID = null; selectedOutboundID = null; selectedReturnID = null
                            }
                            showDateCalendar = true
                        }
                        ModeChip(weekendModeTitle(language), null, mode == GeneratorFlightChoice.WEEKEND, Modifier.weight(1f)) {
                            if (mode != GeneratorFlightChoice.WEEKEND || !draft.isWeekendUmrah) {
                                mode = GeneratorFlightChoice.WEEKEND
                                draft = draft.withFlexibility(DateFlexibility.WEEKEND).copy(flightTripType = FlightTripType.ROUND_TRIP)
                                selectedCompleteID = null; selectedOutboundID = null; selectedReturnID = null
                            }
                        }
                    }
                    when (mode) {
                        GeneratorFlightChoice.PUBLISHED_DIRECT -> PublishedDirectHint(language)
                        GeneratorFlightChoice.FLEXIBLE_DATES -> {
                            DateRangeRow(language, draft.departureDate, draft.returnDate, enabled = true) { showDateCalendar = true }
                            FlexibleDatesHint(language)
                        }
                        GeneratorFlightChoice.WEEKEND -> WeekendDatesSummary(
                            language = language,
                            departure = draft.departureDate,
                            returnDate = draft.returnDate,
                            onOpenCalendar = { showWeekendCalendar = true },
                        )
                    }
                }
            }
        }
        if (mode == GeneratorFlightChoice.PUBLISHED_DIRECT) {
            item {
                CuratedFlightsSection(
                    language = language,
                    loading = loadingCurated,
                    error = curatedError,
                    origin = draft.originCode,
                    outboundDestination = draft.outboundDestinationCode,
                    returnOrigin = draft.returnOriginCode,
                    outbound = outboundFlights,
                    returns = returnFlights,
                    selectedOutboundID = selectedOutboundID,
                    selectedReturnID = selectedReturnID,
                    onOutbound = { recommendation ->
                        val date = runCatching { LocalDate.parse(recommendation.outboundDate) }.getOrNull()
                        if (date != null) {
                            val needsReturnShift = draft.returnDate.isBefore(date)
                            val oldDuration = maxOf(1L, java.time.temporal.ChronoUnit.DAYS.between(draft.departureDate, draft.returnDate))
                            draft = if (needsReturnShift) draft.copy(departureDate = date, returnDate = date.plusDays(oldDuration), flexibility = DateFlexibility.EXACT)
                            else draft.copy(departureDate = date, flexibility = DateFlexibility.EXACT)
                            if (needsReturnShift) selectedReturnID = null
                            selectedCompleteID = null
                            selectedOutboundID = recommendation.id
                        }
                    },
                    onReturn = { recommendation ->
                        val date = runCatching { LocalDate.parse(recommendation.outboundDate) }.getOrNull()
                        if (date != null && !date.isBefore(draft.departureDate)) {
                            draft = draft.copy(returnDate = date, flexibility = DateFlexibility.EXACT)
                            selectedCompleteID = null
                            selectedReturnID = recommendation.id
                        }
                    },
                )
            }
        }
        item {
            GeneratorCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardTitle(CupertinoSymbol.Persons, tr(language, "Паломники", "Travelers", "Ziyoratchilar", "Зиёратчилар"))
                    Text(
                        tr(language, "Размещение и перелёты будут рассчитаны под вашу семью или компанию.", "Rooming and flights will be calculated for your family or group.", "Joylashuv va parvozlar oilangiz yoki guruhingiz uchun hisoblanadi.", "Жойлашув ва парвозлар оилангиз ёки гуруҳингиз учун ҳисобланади."),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    GroupSavingsSummaryCard(language, draft)
                    StepperRow(
                        title = tr(language, "Взрослые", "Adults", "Kattalar", "Катталар"),
                        subtitle = null,
                        value = draft.adults,
                        min = 1,
                        max = maxOf(1, 9 - draft.children - draft.infants),
                    ) { draft = draft.copy(adults = it) }
                    GeneratorDivider()
                    StepperRow(
                        title = tr(language, "Дети", "Children", "Bolalar", "Болалар"),
                        subtitle = tr(language, "2–11 лет", "2–11 years", "2–11 yosh", "2–11 ёш"),
                        value = draft.children,
                        min = 0,
                        max = maxOf(0, 9 - draft.adults - draft.infants),
                    ) { draft = draft.copy(children = it) }
                    GeneratorDivider()
                    StepperRow(
                        title = tr(language, "Младенцы", "Infants", "Chaqaloqlar", "Чақалоқлар"),
                        subtitle = tr(language, "до 2 лет", "under 2", "2 yoshgacha", "2 ёшгача"),
                        value = draft.infants,
                        min = 0,
                        max = minOf(4, maxOf(0, 9 - draft.adults - draft.children)),
                    ) { draft = draft.copy(infants = it) }
                    GeneratorDivider()
                    StepperRow(
                        title = tr(language, "Комнаты", "Rooms", "Xonalar", "Хоналар"),
                        subtitle = null,
                        value = draft.rooms,
                        min = 1,
                        max = 6,
                    ) { draft = draft.copy(rooms = it) }
                }
            }
        }
        if (mode != GeneratorFlightChoice.PUBLISHED_DIRECT) {
            item { FlightSearchFiltersCard(filters = draft.effectiveFlightFilters, infantCount = draft.infants, language = language, onChange = { draft = draft.copy(flightFilters = it) }) }
        }
        item {
            GeneratorCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardTitle(CupertinoSymbol.Grid, tr(language, "Формат поездки", "Trip format", "Safar formati", "Сафар формати"))
                    PackageTier.entries.forEach { tier ->
                        TierCard(language, tier, draft.packageTier == tier) {
                            draft = draft.copy(packageTier = tier, hotelStars = tierStars(tier))
                        }
                    }
                }
            }
        }
        item {
            GeneratorPrimaryButton(
                title = tr(language, "Продолжить к отелю", "Continue to hotel", "Mehmonxonaga davom etish", "Меҳмонхонага давом этиш"),
                enabled = canContinue,
            ) {
                val path = when (mode) {
                    GeneratorFlightChoice.PUBLISHED_DIRECT -> PackageFlightPath.PUBLISHED_DIRECT
                    GeneratorFlightChoice.FLEXIBLE_DATES -> PackageFlightPath.FLEXIBLE_DATES
                    GeneratorFlightChoice.WEEKEND -> PackageFlightPath.WEEKEND
                }
                journey.commitTripBuilder(draft, path, selectedCompleteID, selectedOutboundID, selectedReturnID)
                chrome.openHotelSelection()
            }
        }
    }

    if (showDateCalendar) {
        FlightDateCalendarDialog(
            language = language,
            trip = draft,
            initialDeparture = draft.departureDate,
            initialReturn = draft.returnDate,
            curatedFlights = curatedFlights,
            onDismiss = { showDateCalendar = false },
            onApply = { result ->
                draft = draft.copy(
                    departureDate = result.departure,
                    returnDate = result.returnDate,
                    flexibility = DateFlexibility.EXACT,
                    flightTripType = FlightTripType.ROUND_TRIP,
                )
                val published = result.publishedSelection
                if (published != null && published.isComplete) {
                    mode = GeneratorFlightChoice.PUBLISHED_DIRECT
                    selectedCompleteID = published.completeID
                    selectedOutboundID = published.outboundID
                    selectedReturnID = published.returnID
                } else {
                    mode = GeneratorFlightChoice.FLEXIBLE_DATES
                    selectedCompleteID = null
                    selectedOutboundID = null
                    selectedReturnID = null
                }
                showDateCalendar = false
            },
        )
    }

    if (showWeekendCalendar) {
        FlightDateCalendarDialog(
            language = language,
            trip = draft,
            initialDeparture = draft.departureDate,
            initialReturn = draft.returnDate,
            curatedFlights = curatedFlights,
            onDismiss = { showWeekendCalendar = false },
            onApply = { result ->
                draft = draft.copy(departureDate = result.departure).applyWeekendWindow()
                mode = GeneratorFlightChoice.WEEKEND
                selectedCompleteID = null
                selectedOutboundID = null
                selectedReturnID = null
                showWeekendCalendar = false
            },
        )
    }
}

@Composable private fun PublishedDirectHint(language: AppLanguage) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF34C759).copy(alpha = .08f)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
    ) {
        Icon(CupertinoSymbol.CheckCircle, null, Modifier.size(18.dp), tint = Color(0xFF34C759))
        Text(publishedDirectHint(language), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), modifier = Modifier.weight(1f))
    }
}

@Composable private fun FlexibleDatesHint(language: AppLanguage) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(CupertinoSymbol.CalendarClock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        Text(flexibleDatesHint(language), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WeekendDatesSummary(
    language: AppLanguage,
    departure: LocalDate,
    returnDate: LocalDate,
    onOpenCalendar: () -> Unit,
) {
    val locale = when (language) {
        AppLanguage.RUSSIAN -> Locale("ru", "RU")
        AppLanguage.ENGLISH -> Locale.US
        AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn-UZ")
        AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl-UZ")
    }
    val dates = (0L..3L).map { departure.plusDays(it) }
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)
    val card = MaterialTheme.colorScheme.surface

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IumrahPressable(
            onClick = onOpenCalendar,
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            background = generatorRaisedColor(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr(language, "Дата поездки", "Trip date", "Safar sanasi", "Сафар санаси"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = secondary)
                    Text(departure.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Icon(CupertinoSymbol.CalendarClock, null, Modifier.size(18.dp), tint = secondary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(language, "Ваши выходные", "Your weekend", "Dam olish kunlaringiz", "Дам олиш кунларингиз"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dates.forEach { date ->
                    Column(
                        modifier = Modifier.weight(1f).height(78.dp).clip(RoundedCornerShape(18.dp)).background(generatorRaisedColor()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale).uppercase(locale), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = secondary)
                        Text(date.dayOfMonth.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(date.month.getDisplayName(java.time.format.TextStyle.SHORT, locale), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = secondary)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF34C759).copy(alpha = .18f), card)))
                .border(1.dp, Color(0xFF34C759).copy(alpha = .18f), RoundedCornerShape(22.dp))
                .padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF34C759).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.Moon, null, Modifier.size(18.dp), tint = Color(0xFF34C759))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Weekend Umrah", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    tr(language, "Короткая умра с пятницы по понедельник — для тех, кто хочет вписать поездку в выходные.", "A short Friday-to-Monday Umrah for pilgrims who want the journey to fit into a weekend.", "Jumadan dushanbagacha qisqa Umra — safarni dam olish kunlariga sig‘dirmoqchi bo‘lganlar uchun.", "Жумадан душанбагача қисқа Умра — сафарни дам олиш кунларига сиғдирмоқчи бўлганлар учун."),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = secondary,
                )
            }
        }
    }
}

@Composable private fun CuratedFlightsSection(
    language: AppLanguage,
    loading: Boolean,
    error: String?,
    origin: String,
    outboundDestination: String,
    returnOrigin: String,
    outbound: List<CuratedFlightRecommendation>,
    returns: List<CuratedFlightRecommendation>,
    selectedOutboundID: String?,
    selectedReturnID: String?,
    onOutbound: (CuratedFlightRecommendation) -> Unit,
    onReturn: (CuratedFlightRecommendation) -> Unit,
) {
    val orange = Color(0xFFFF9500)
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(orange.copy(alpha = .085f)).border(1.dp, orange.copy(alpha = .18f), shape).padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(orange), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.AirplaneTakeoff, null, Modifier.size(19.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(curatedFlightsTitle(language), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(curatedFlightsSubtitle(language), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            }
            Box(Modifier.height(26.dp).clip(CircleShape).background(orange.copy(alpha = .12f)).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                Text("iumrah", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = orange)
            }
        }
        if (loading && outbound.isEmpty() && returns.isEmpty()) {
            Row(Modifier.fillMaxWidth().height(92.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(curatedLoadingLabel(language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            }
        } else {
            CuratedOneWayRow(language, curatedOutboundRowTitle(language), "$origin → $outboundDestination", outbound, selectedOutboundID, CuratedLegSelection.OUTBOUND, onOutbound)
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)))
            CuratedOneWayRow(language, curatedReturnRowTitle(language), "$returnOrigin → $origin", returns, selectedReturnID, CuratedLegSelection.RETURN, onReturn)
            if (error != null && outbound.isEmpty() && returns.isEmpty()) {
                Text(error, fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = .85f))
            }
        }
    }
}

@Composable private fun CuratedOneWayRow(
    language: AppLanguage,
    title: String,
    subtitle: String,
    values: List<CuratedFlightRecommendation>,
    selectedID: String?,
    selection: CuratedLegSelection,
    onSelect: (CuratedFlightRecommendation) -> Unit,
) {
    val orange = Color(0xFFFF9500)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
            Text(values.size.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
        if (values.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(generatorRaisedColor().copy(alpha = .82f)).padding(13.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CupertinoSymbol.CalendarClock, null, Modifier.size(18.dp), tint = orange)
                Text(curatedEmptyRowLabel(language), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), modifier = Modifier.weight(1f))
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                items(values, key = { "${selection.name}-${it.id}" }) { recommendation ->
                    CuratedFlightCard(language, recommendation, selectedID == recommendation.id, selection) { onSelect(recommendation) }
                }
            }
        }
    }
}

@Composable private fun CuratedFlightCard(
    language: AppLanguage,
    recommendation: CuratedFlightRecommendation,
    selected: Boolean,
    selection: CuratedLegSelection,
    onClick: () -> Unit,
) {
    val orange = Color(0xFFFF9500)
    val leg = if (selection == CuratedLegSelection.RETURN && recommendation.effectiveOfferType != "one_way") recommendation.inbound ?: recommendation.outbound else recommendation.outbound
    val date = if (selection == CuratedLegSelection.RETURN && recommendation.effectiveOfferType != "one_way") recommendation.inboundDate ?: recommendation.outboundDate else recommendation.outboundDate
    val shape = RoundedCornerShape(21.dp)
    IumrahPressable(onClick = onClick, modifier = Modifier.width(274.dp).height(154.dp), cornerRadius = 21.dp, background = Color.Transparent) {
        Column(
            Modifier.fillMaxSize().clip(shape)
                .background(if (selected) orange.copy(alpha = .075f) else generatorRaisedColor())
                .border(1.dp, if (selected) orange.copy(alpha = .35f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AirlineLogo(leg.airlineCode)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(leg.airline.ifBlank { curatedAirlineFallback(language) }, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(leg.flightNumber, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), maxLines = 1)
                }
                Box(Modifier.height(24.dp).clip(CircleShape).background(orange.copy(alpha = .10f)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(curatedDirectLabel(language), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = orange)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (selection == CuratedLegSelection.OUTBOUND) CupertinoSymbol.AirplaneTakeoff else CupertinoSymbol.AirplaneLand, null, Modifier.size(15.dp))
                Text(leg.origin, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
                Text(leg.destination, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text(shortDate(language, date), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(curatedChooseFlightLabel(language), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) orange else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Icon(if (selected) CupertinoSymbol.CheckCircle else CupertinoSymbol.ChevronRight, null, Modifier.size(18.dp), tint = if (selected) orange else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable private fun AirlineLogo(codeRaw: String?) {
    val code = codeRaw?.uppercase()?.trim()?.takeIf { it.matches(Regex("^[A-Z0-9]{2}$")) }
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(42.dp).clip(shape).background(Color.White).border(.7.dp, Color.Black.copy(alpha = .06f), shape), contentAlignment = Alignment.Center) {
        if (code == "C6") {
            Image(painterResource(R.drawable.centrum_air_logo), null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
        } else if (code != null) {
            AsyncImage(
                model = "https://www.gstatic.com/flights/airline_logos/70px/$code.png",
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(CupertinoSymbol.Airplane, null, Modifier.size(14.dp), tint = Color.Black.copy(alpha = .55f))
        }
    }
}

private fun shortDate(language: AppLanguage, value: String): String {
    val date = runCatching { LocalDate.parse(value.take(10)) }.getOrNull() ?: return value
    val locale = Locale.forLanguageTag(language.localeTag)
    return DateTimeFormatter.ofPattern("d MMM", locale).format(date)
}

@Composable private fun TripIntro(language: AppLanguage) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr(language, "ВАША УМРА", "YOUR UMRAH", "SIZNING UMRANGIZ", "СИЗНИНГ УМРАНГИЗ"), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        Text(tr(language, "Соберите свою поездку", "Build your Umrah", "Umra safaringizni tuzing", "Умра сафарингизни тузинг"), fontSize = 33.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
        Text(tr(language, "Маршрут, даты, паломники и уровень пакета — iumrah соберёт всё остальное.", "Route, dates, travelers and package level — iumrah builds the rest.", "Yo‘nalish, sanalar, ziyoratchilar va paket darajasi — qolganini iumrah yig‘adi.", "Йўналиш, саналар, зиёратчилар ва пакет даражаси — қолганини iumrah йиғади."), fontSize = 16.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
}

@Composable private fun CardTitle(icon: CupertinoSymbol, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(icon, null, Modifier.size(21.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun <T> SegmentedChoice(labels: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val page = generatorPageColor(); val fg = if (page == Color.White) Color.Black else Color.White
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(generatorRaisedColor()).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEach { (value, label) ->
            val active = selected == value
            IumrahPressable(onClick = { onSelect(value) }, modifier = Modifier.weight(1f).height(34.dp), cornerRadius = 9.dp, background = if (active) generatorPageColor() else Color.Transparent) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = fg, maxLines = 1) }
            }
        }
    }
}

@Composable private fun ModeChip(title: String, icon: CupertinoSymbol?, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val page = generatorPageColor(); val fg = if (page == Color.White) Color.Black else Color.White
    IumrahPressable(onClick = onClick, modifier = modifier.height(42.dp), cornerRadius = 99.dp, background = if (selected) fg else generatorRaisedColor()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, null, Modifier.size(14.dp), tint = if (selected) page else fg); Spacer(Modifier.width(4.dp)) }
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) page else fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun DateRangeRow(language: AppLanguage, departure: LocalDate, returnDate: LocalDate, enabled: Boolean, onClick: () -> Unit) {
    val formatter = remember(language) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language.localeTag)) }
    IumrahPressable(onClick = { if (enabled) onClick() }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(76.dp), cornerRadius = 20.dp, background = generatorRaisedColor()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            DateColumn(tr(language, "Вылет", "Departure", "Jo‘nash", "Жўнаш"), formatter.format(departure), Modifier.weight(1f))
            Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            DateColumn(tr(language, "Обратно", "Return", "Qaytish", "Қайтиш"), formatter.format(returnDate), Modifier.weight(1f))
            Icon(CupertinoSymbol.ChevronRight, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .5f else .25f))
        }
    }
}

@Composable private fun DateColumn(title: String, value: String, modifier: Modifier = Modifier) { Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)); Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1) } }

@Composable
private fun GeneratorDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)))
}

@Composable
private fun StepperRow(
    title: String,
    subtitle: String?,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .50f))
            }
        }
        RoundStep(CupertinoSymbol.Minus, value > min) { onChange(value - 1) }
        Text(value.toString(), modifier = Modifier.width(42.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        RoundStep(CupertinoSymbol.Plus, value < max) { onChange(value + 1) }
    }
}

@Composable
private fun RoundStep(icon: CupertinoSymbol, enabled: Boolean, onClick: () -> Unit) {
    IumrahPressable(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
        cornerRadius = 99.dp,
        background = generatorRaisedColor().copy(alpha = if (enabled) 1f else .4f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .35f))
        }
    }
}

private data class EstimatedGroupSavings(val percent: Int, val total: Double, val perPerson: Double)

private fun estimatedGroupSavings(trip: TripDraft, people: Int): EstimatedGroupSavings? {
    val count = maxOf(1, people)
    if (count <= 1) return null
    val sharedWeight = .24
    val occupancyFactor = (count - 1).toDouble() / count.toDouble()
    val percent = maxOf(6, kotlin.math.round(sharedWeight * occupancyFactor * 100.0).toInt())
    val nights = maxOf(3L, ChronoUnit.DAYS.between(trip.hotelStayStartDate, trip.returnDate)).toInt()
    val base = when (trip.packageTier) {
        PackageTier.ECONOMY -> if (trip.scope == JourneyScope.MAKKAH_AND_MADINAH) 980.0 else 860.0
        PackageTier.STANDARD -> if (trip.scope == JourneyScope.MAKKAH_AND_MADINAH) 1210.0 else 1080.0
        PackageTier.COMFORT -> if (trip.scope == JourneyScope.MAKKAH_AND_MADINAH) 1480.0 else 1320.0
        PackageTier.LUXURY -> if (trip.scope == JourneyScope.MAKKAH_AND_MADINAH) 1880.0 else 1690.0
    }
    val soloPerPilgrim = base + maxOf(0, nights - 5) * 42.0
    val total = maxOf(0.0, soloPerPilgrim * count * percent / 100.0)
    return EstimatedGroupSavings(percent, total, total / count)
}

private fun groupMoney(value: Double): String = "$${kotlin.math.round(value).toInt()}"

@Composable
private fun GroupSavingsSummaryCard(language: AppLanguage, trip: TripDraft) {
    val count = maxOf(1, trip.travelerCount)
    if (count == 1) {
        val comparison = estimatedGroupSavings(trip, 2)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFF9500).copy(alpha = .11f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CupertinoSymbol.Persons, null, Modifier.size(17.dp), tint = Color(0xFFFF9500))
                Text(tr(language, "Для одного человека пакет дороже", "Solo travel costs more", "Bir kishi uchun paket qimmatroq", "Бир киши учун пакет қимматроқ"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
            }
            Text(
                if (comparison != null) tr(
                    language,
                    "Если ехать вдвоём, цена на человека сейчас ниже примерно на ${comparison.percent}%. Вместе двое экономят ${groupMoney(comparison.total)} по сравнению с двумя отдельными такими поездками.",
                    "For two pilgrims, the current per-person price is about ${comparison.percent}% lower. Together, two save ${groupMoney(comparison.total)} versus two separate solo packages.",
                    "Ikki kishi bo‘lib borsangiz, kishi boshiga narx hozir taxminan ${comparison.percent}% arzon. Ikki alohida yakka paketga nisbatan jami ${groupMoney(comparison.total)} tejaysiz.",
                    "Икки киши бўлиб борсангиз, киши бошига нарх ҳозир тахминан ${comparison.percent}% арзон. Икки алоҳида якка пакетга нисбатан жами ${groupMoney(comparison.total)} тежайсиз.",
                ) else tr(
                    language,
                    "Совместная поездка обычно снижает цену на человека, потому что номер, трансфер, сопровождение и часть сервисов распределяются на группу.",
                    "Travelling together usually lowers the per-person price because rooms, transfers, assistance and group services are shared.",
                    "Birga safar qilish odatda kishi boshiga narxni pasaytiradi: xona, transfer, hamrohlik va guruh xizmatlari bo‘linadi.",
                    "Бирга сафар қилиш одатда киши бошига нархни пасайтиради: хона, трансфер, ҳамроҳлик ва гуруҳ хизматлари бўлинади.",
                ),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = if (comparison != null) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
            )
        }
    } else {
        val savings = estimatedGroupSavings(trip, count) ?: return
        val tint = if (count == 2) Color(0xFFAF52DE) else Color(0xFF30B0C7)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(tint.copy(alpha = .10f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (count == 2) "iumrah Family Package" else "iumrah Friends Package", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(tr(language, "Сравнение с таким же пакетом для 1 паломника", "Compared with the same package for 1 pilgrim", "Xuddi shu 1 kishilik paket bilan solishtirganda", "Худди шу 1 кишилик пакет билан солиштирганда"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
                Text("−${savings.percent}%", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(tr(language, "Экономия группы ${groupMoney(savings.total)}", "Group saves ${groupMoney(savings.total)}", "Guruh tejaydi: ${groupMoney(savings.total)}", "Гуруҳ тежайди: ${groupMoney(savings.total)}"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(tr(language, "${groupMoney(savings.perPerson)} на человека", "${groupMoney(savings.perPerson)} per person", "kishi boshiga ${groupMoney(savings.perPerson)}", "киши бошига ${groupMoney(savings.perPerson)}"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
        }
    }
}

@Composable
private fun TierCard(language: AppLanguage, tier: PackageTier, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val colors = when (tier) {
        PackageTier.ECONOMY -> listOf(Color(red = .32f, green = .56f, blue = .34f), Color(red = .13f, green = .25f, blue = .16f))
        PackageTier.STANDARD -> listOf(Color(red = .31f, green = .37f, blue = .83f), Color(red = .13f, green = .18f, blue = .43f))
        PackageTier.COMFORT -> listOf(Color(red = .11f, green = .60f, blue = .62f), Color(red = .03f, green = .18f, blue = .24f))
        PackageTier.LUXURY -> listOf(Color(red = .81f, green = .63f, blue = .24f), Color(red = .29f, green = .18f, blue = .05f))
    }
    val accent = when (tier) {
        PackageTier.ECONOMY -> Color(red = .62f, green = .91f, blue = .69f)
        PackageTier.STANDARD -> Color(red = .76f, green = .80f, blue = 1f)
        PackageTier.COMFORT -> Color(red = .69f, green = .95f, blue = .94f)
        PackageTier.LUXURY -> Color(red = 1f, green = .91f, blue = .68f)
    }
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, background = Color.Transparent) {
        Column(
            Modifier.fillMaxWidth()
                .shadow(if (selected) 18.dp else 12.dp, shape)
                .clip(shape)
                .background(Brush.linearGradient(colors))
                .border(1.dp, Color.White.copy(alpha = if (selected) .22f else .10f), shape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (selected) .20f else .10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (selected) CupertinoSymbol.CheckCircle else tierIcon(tier), null, Modifier.size(18.dp), tint = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tierTitle(language, tier), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        if (tier == PackageTier.STANDARD) {
                            Box(Modifier.height(24.dp).clip(CircleShape).background(Color.White.copy(alpha = .92f)).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                                Text(tr(language, "Популярный", "Popular", "Mashhur", "Машҳур"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accent)
                            }
                        }
                    }
                    Text(tierHeadline(language, tier), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .86f))
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(26.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            val labelTint = Color.White.copy(alpha = .92f)
                            if (tier == PackageTier.ECONOMY) {
                                Text("2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = labelTint)
                                Icon(CupertinoSymbol.StarFill, null, Modifier.size(10.dp), labelTint)
                                Text("· 1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = labelTint)
                            } else Text("${tierStars(tier)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = labelTint)
                            Icon(CupertinoSymbol.StarFill, null, Modifier.size(10.dp), labelTint)
                        }
                    }
                    if (selected) Text(tr(language, "Текущий", "Current", "Joriy", "Жорий"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Text(tierBody(language, tier), fontSize = 14.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = .84f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tierBullets(language, tier).forEach { benefit ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                        Icon(CupertinoSymbol.CheckCircle, null, Modifier.size(13.dp).padding(top = 1.dp), tint = Color.White)
                        Text(benefit, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = .86f), modifier = Modifier.weight(1f))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (selected) .18f else .12f)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selected) tr(language, "Этот уровень уже выбран", "This level is already selected", "Bu daraja allaqachon tanlangan", "Бу даража аллақачон танланган")
                    else tr(language, "Выбрать этот уровень", "Choose this level", "Shu darajani tanlash", "Шу даражани танлаш"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                Icon(if (selected) CupertinoSymbol.Checkmark else CupertinoSymbol.ArrowRight, null, Modifier.size(13.dp), tint = Color.White)
            }
        }
    }
}

private fun tierStars(t: PackageTier) = when (t) { PackageTier.ECONOMY -> 2; PackageTier.STANDARD -> 3; PackageTier.COMFORT -> 4; PackageTier.LUXURY -> 5 }
private fun tierIcon(t: PackageTier) = when (t) { PackageTier.ECONOMY -> CupertinoSymbol.LeafFill; PackageTier.STANDARD -> CupertinoSymbol.Checklist; PackageTier.COMFORT -> CupertinoSymbol.Sparkles; PackageTier.LUXURY -> CupertinoSymbol.CrownFill }
private fun tierTitle(l: AppLanguage, t: PackageTier) = when (t) { PackageTier.ECONOMY -> tr(l, "Economy", "Economy", "Economy", "Economy"); PackageTier.STANDARD -> tr(l, "Standard", "Standard", "Standard", "Standard"); PackageTier.COMFORT -> tr(l, "Comfort", "Comfort", "Comfort", "Comfort"); PackageTier.LUXURY -> tr(l, "Luxury", "Luxury", "Luxury", "Luxury") }
private fun tierHeadline(l: AppLanguage, t: PackageTier) = when (t) {
    PackageTier.ECONOMY -> tr(l, "Практичный пакет", "Practical package", "Amaliy paket", "Амалий пакет")
    PackageTier.STANDARD -> tr(l, "Сбалансированная база", "Balanced essentials", "Muvozanatli asos", "Мувозанатли асос")
    PackageTier.COMFORT -> tr(l, "Больше ежедневного удобства", "More daily comfort", "Har kuni ko‘proq qulaylik", "Ҳар куни кўпроқ қулайлик")
    PackageTier.LUXURY -> tr(l, "Максимально близко к Хараму", "Closest to the Haram", "Haromga maksimal yaqin", "Ҳаромга максимал яқин")
}
private fun tierBody(l: AppLanguage, t: PackageTier) = when (t) {
    PackageTier.ECONOMY -> tr(l, "Для тех, кто хочет сохранить бюджет и собрать полноценную поездку без лишнего. Основная цель — выгодная личная умра с базовым комфортом.", "For pilgrims who want to keep the budget under control and still assemble a full trip. The goal is a smart personal Umrah with essential comfort.", "Budjetni nazoratda ushlab, to‘liq safar yig‘moqchi bo‘lganlar uchun. Asosiy maqsad — zarur qulayliklar bilan foydali shaxsiy umra.", "Бюджетни назоратда ушлаб, тўлиқ сафар йиғмоқчи бўлганлар учун. Асосий мақсад — зарур қулайликлар билан фойдали шахсий умра.")
    PackageTier.STANDARD -> tr(l, "Оптимальный старт для большинства поездок: аккуратный баланс цены, расположения и привычных удобств.", "The best starting point for most trips: a clean balance of price, location and familiar convenience.", "Ko‘pchilik safarlar uchun eng maqbul boshlanish: narx, joylashuv va odatiy qulayliklarning muvozanati.", "Кўпчилик сафарлар учун энг мақбул бошланиш: нарх, жойлашув ва одатий қулайликларнинг мувозанати.")
    PackageTier.COMFORT -> tr(l, "Комфортный вариант для тех, кто хочет меньше бытовой нагрузки и более приятный ежедневный ритм поездки.", "A comfortable option for pilgrims who want less daily friction and a smoother travel rhythm.", "Kundalik tashvishlarni kamaytirib, safarni yengilroq qilishni istaganlar uchun qulay variant.", "Кундалик ташвишларни камайтириб, сафарни енгилроқ қилишни истаганлар учун қулай вариант.")
    PackageTier.LUXURY -> tr(l, "Премиальный формат для тех, кто хочет самый высокий уровень сервиса и максимально сократить дорогу до Харама.", "A premium format for pilgrims who want the highest level of service and the shortest possible walk to the Haram.", "Eng yuqori xizmat va Haromgacha yo‘lni maksimal qisqartirishni istaganlar uchun premium format.", "Энг юқори хизмат ва Ҳаромгача йўлни максимал қисқартиришни истаганлар учун премиум формат.")
}
private fun tierBullets(l: AppLanguage, t: PackageTier): List<String> = when (t) {
    PackageTier.ECONOMY -> listOf(tr(l, "Базовый уровень отеля 2★ / 1★", "2★ / 1★ hotel level", "2★ / 1★ mehmonxona darajasi", "2★ / 1★ меҳмонхона даражаси"), tr(l, "Лучше всего, если приоритет — цена", "Best when price is the main priority", "Asosiy ustuvorlik narx bo‘lsa mos", "Асосий устуворлик нарх бўлса мос"), tr(l, "Все основные этапы уже внутри одного пакета", "All core trip parts stay inside one package", "Safarning barcha asosiy qismlari bir paketda", "Сафарнинг барча асосий қисмлари бир пакетда"))
    PackageTier.STANDARD -> listOf(tr(l, "3★ отель как основной уровень", "3★ hotel as the main level", "Asosiy daraja — 3★ mehmonxona", "Асосий даража — 3★ меҳмонхона"), tr(l, "Хороший баланс цены и повседневного удобства", "A strong balance of price and comfort", "Narx va qulaylikning yaxshi muvozanati", "Нарх ва қулайликнинг яхши мувозанати"), tr(l, "Подходит для большинства индивидуальных поездок", "Suitable for most private trips", "Ko‘pchilik individual safarlar uchun mos", "Кўпчилик индивидуал сафарлар учун мос"))
    PackageTier.COMFORT -> listOf(tr(l, "4★ уровень с более удобным проживанием", "4★ level with more comfortable stays", "4★ daraja va qulayroq yashash", "4★ даража ва қулайроқ яшаш"), tr(l, "Лучше ежедневный ритм и меньше бытовых компромиссов", "A smoother daily rhythm with fewer compromises", "Har kuni qulayroq ritm va kamroq murosa", "Ҳар куни қулайроқ ритм ва камроқ муроса"), tr(l, "Хороший выбор для семей и спокойной поездки", "A strong choice for families and calmer trips", "Oilalar va sokin safar uchun yaxshi tanlov", "Оилалар ва сокин сафар учун яхши танлов"))
    PackageTier.LUXURY -> listOf(tr(l, "5★ уровень и премиальная подача", "5★ level and a premium feel", "5★ daraja va premium tajriba", "5★ даража ва премиум тажриба"), tr(l, "Максимальная близость к Хараму", "Maximum closeness to the Haram", "Haromga maksimal yaqinlik", "Ҳаромга максимал яқинлик"), tr(l, "Для тех, кто хочет сократить нагрузку в поездке", "For pilgrims who want the lightest trip burden", "Safardagi yuklamani kamaytirishni istaganlar uchun", "Сафардаги юкламани камайтиришни истаганлар учун"))
}

@Composable private fun WeekendRouteSummary(origin: String) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(generatorRaisedColor()).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { RouteCode(origin, Modifier.weight(1f)); Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(14.dp)); RouteCode("JED", Modifier.weight(1f)); Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(14.dp)); RouteCode(origin, Modifier.weight(1f)) } }
@Composable private fun RouteCode(code: String, modifier: Modifier = Modifier) { Box(modifier.height(42.dp).clip(RoundedCornerShape(14.dp)).background(generatorPageColor()), contentAlignment = Alignment.Center) { Text(code, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) } }

private fun flightChoiceTitle(l: AppLanguage) = tr(l, "Сначала выберите перелёт", "Choose your flight first", "Avval parvozni tanlang", "Аввал парвозни танланг")
private fun flightChoiceBody(l: AppLanguage) = tr(l, "Выберите прямой рейс с подходящими датами. Если даты важнее рейса, используйте гибкий календарь.", "Choose a direct flight with suitable dates. If your dates matter more than the flight, use the flexible calendar.", "Mos sanali to‘g‘ridan-to‘g‘ri reysni tanlang. Agar sana muhimroq bo‘lsa, moslashuvchan kalendardan foydalaning.", "Мос санали тўғридан-тўғри рейсни танланг. Агар сана муҳимроқ бўлса, мослашувчан календардан фойдаланинг.")
private fun directFlightsModeTitle(l: AppLanguage) = tr(l, "Прямые", "Direct", "To‘g‘ri", "Тўғри")
private fun flexibleDatesModeTitle(l: AppLanguage) = tr(l, "Гибкие даты", "Flexible", "Moslashuvchan", "Мослашувчан")
private fun weekendModeTitle(l: AppLanguage) = tr(l, "Выходные", "Weekend", "Dam olish", "Дам олиш")
private fun publishedDirectHint(l: AppLanguage) = tr(l, "Рейсы ниже найдены iumrah Scanner. Выберите билет туда и обратно — цену авиабилета отдельно не показываем; она войдёт в итоговую цену вашего личного пакета.", "The flights below were found by iumrah Scanner. Choose your outbound and return; the airfare is not shown separately and will be included in your personal package total.", "Quyidagi reyslar iumrah Scanner yordamida topilgan. Borish va qaytish reysini tanlang — aviachipta narxi alohida ko‘rsatilmaydi, u shaxsiy paketingiz yakuniy narxiga kiradi.", "Қуйидаги рейслар iumrah Scanner ёрдамида топилган. Бориш ва қайтиш рейсини танланг — авиачипта нархи алоҳида кўрсатилмайди, у шахсий пакетингиз якуний нархига киради.")
private fun flexibleDatesHint(l: AppLanguage) = tr(l, "Зелёные дни в календаре — даты прямых рейсов, найденных iumrah AI. Выбор других дат запустит гибкий поиск после выбора отеля.", "Green calendar days are direct-flight dates found by iumrah AI. Choosing other dates starts flexible flight search after your hotel is selected.", "Kalendardagi yashil kunlar — iumrah AI topgan to‘g‘ridan-to‘g‘ri reys sanalari. Boshqa sanalar mehmonxona tanlangach moslashuvchan qidiruvni ishga tushiradi.", "Календардаги яшил кунлар — iumrah AI топган тўғридан-тўғри рейс саналари. Бошқа саналар меҳмонхона танлангач мослашувчан қидирувни ишга туширади.")
private fun curatedFlightsTitle(l: AppLanguage) = tr(l, "Найденные прямые рейсы", "Found direct flights", "Topilgan to‘g‘ridan-to‘g‘ri reyslar", "Топилган тўғридан-тўғри рейслар")
private fun curatedFlightsSubtitle(l: AppLanguage) = tr(l, "Найдено с помощью iumrah Scanner", "Found with iumrah Scanner", "iumrah Scanner yordamida topildi", "iumrah Scanner ёрдамида топилди")
private fun curatedOutboundRowTitle(l: AppLanguage) = tr(l, "Туда", "Outbound", "Borish", "Бориш")
private fun curatedReturnRowTitle(l: AppLanguage) = tr(l, "Обратно", "Return", "Qaytish", "Қайтиш")
private fun curatedDirectLabel(l: AppLanguage) = tr(l, "ПРЯМОЙ", "DIRECT", "TO‘G‘RI", "ТЎҒРИ")
private fun curatedChooseFlightLabel(l: AppLanguage) = tr(l, "Выбрать рейс", "Choose flight", "Reysni tanlash", "Рейсни танлаш")
private fun curatedLoadingLabel(l: AppLanguage) = tr(l, "Ищем рейсы через iumrah Scanner…", "Searching flights with iumrah Scanner…", "iumrah Scanner orqali reyslar qidirilmoqda…", "iumrah Scanner орқали рейслар қидирилмоқда…")
private fun curatedEmptyRowLabel(l: AppLanguage) = tr(l, "Для этого направления пока не найдено подходящих прямых рейсов.", "No suitable direct flights found for this route yet.", "Bu yo‘nalish uchun hozircha mos to‘g‘ridan-to‘g‘ri reys topilmadi.", "Бу йўналиш учун ҳозирча мос тўғридан-тўғри рейс топилмади.")
private fun curatedAirlineFallback(l: AppLanguage) = tr(l, "Авиакомпания", "Airline", "Aviakompaniya", "Авиакомпания")

private fun tr(l: AppLanguage, ru: String, en: String, uz: String, uzCy: String) = when (l) { AppLanguage.RUSSIAN -> ru; AppLanguage.ENGLISH -> en; AppLanguage.UZBEK -> uz; AppLanguage.UZBEK_CYRILLIC -> uzCy }
