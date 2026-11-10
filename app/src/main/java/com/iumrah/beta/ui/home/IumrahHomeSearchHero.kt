package com.iumrah.beta.ui.home

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.analytics.IumrahIntelligence
import com.iumrah.beta.core.design.IumrahDesign
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.DateFlexibility
import com.iumrah.beta.domain.trip.FlightTripType
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageFlightPath
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.FlightCabinClass
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.trip.ConfiguratorAirportSelector
import com.iumrah.beta.ui.trip.FlightDateCalendarDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Stage 1–2 mirror of HomeDashboardView.IumrahHomeJourneySearchHero +
 * IumrahGraphiteSearchSurface, referencing kepler-ios-main 7.
 *
 * Unlike the previous preview-only card, controls here edit the *actual*
 * JourneyStore/TripDraft. No duplicate flight results or invented fares.
 *
 * Screens have platform-native mechanisms (Android ModalBottomSheet / Compose),
 * but source copy, search geometry and routes match the iOS product.
 */
@Composable
fun IumrahHomeSearchHero(
    language: AppLanguage,
    trip: TripDraft,
    journey: JourneyStore,
    airports: AirportSearchService,
    curatedFlights: CuratedFlightRecommendationService,
    chrome: AppChromeStore,
) {
    var expanded by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    var travelersOpen by remember { mutableStateOf(false) }
    var headlineIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val motionAllowed = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
    val headlines = remember(language) {
        listOf(
            homeTr(language, "Ваша умра. Ваш путь.", "Your Umrah. Your way.", "Umrangiz. O‘z yo‘lingiz.", "Умрангиз. Ўз йўлингиз."),
            homeTr(language, "Вместе с близкими.", "Together with those you love.", "Yaqinlaringiz bilan birga.", "Яқинларингиз билан бирга."),
            homeTr(language, "Всё путешествие — в одном месте.", "Your entire journey. One place.", "Butun safar bir joyda.", "Бутун сафар бир жойда."),
        )
    }
    LaunchedEffect(language, motionAllowed) {
        headlineIndex = 0
        if (motionAllowed) {
            while (true) {
                delay(5_000)
                headlineIndex = (headlineIndex + 1) % headlines.size
            }
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(IumrahDesign.HeroRadius)
    val ok = trip.canContinue && !trip.departureDate.isBefore(LocalDate.now()) && trip.infants <= trip.adults
    val dateFormatter = remember(language) { DateTimeFormatter.ofPattern("d MMM", homeLocale(language)) }
    // The monochrome animated highlight is intentionally independent of the
    // material content card, matching SwiftUI IumrahAnimatedSearchBorder.
    val glowTransition = rememberInfiniteTransition(label = "home-search-border")
    val highlight by glowTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(2500)), label = "home-search-highlight",
    )
    val stroke = if (motionAllowed) highlight else .23f
    Column(
        Modifier.fillMaxWidth()
            .animateContentSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(3.dp, Brush.sweepGradient(listOf(
                ink.copy(alpha = stroke), ink.copy(alpha = .10f),
                ink.copy(alpha = .30f), ink.copy(alpha = .08f),
            ))), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable {
                expanded = !expanded
                IumrahIntelligence.record("tap", "home", if (expanded) "search_expand" else "search_collapse")
            }.padding(15.dp).height(50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CupertinoIcon(CupertinoSymbol.Search, null, Modifier.size(23.dp), ink)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Crossfade(targetState = headlines[headlineIndex], animationSpec = tween(250), label = "home-title") { title ->
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                }
                Text("${trip.originCode} · ${trip.departureDate.format(dateFormatter)}",
                    fontSize = 12.sp, color = ink.copy(alpha = .58f), maxLines = 1)
            }
            CupertinoIcon(if (expanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown,
                null, Modifier.size(14.dp), ink.copy(alpha = .65f))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(260)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(210)) + fadeOut(tween(160)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 1.dp, color = ink.copy(alpha = .12f))
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(homeTr(language, "Аэропорт вылета", "Departure airport", "Jo‘nash aeroporti", "Жўнаш аэропорти"),
                        fontSize = 12.sp, color = ink.copy(alpha = .58f))
                    ConfiguratorAirportSelector(
                        language = language, service = airports,
                        airport = trip.originAirport, fallbackCode = trip.originCode,
                        onSelect = { selected ->
                            journey.updateTrip(trip.copy(origin = selected.iata, originAirport = selected))
                            IumrahIntelligence.record("tap", "home", "change_departure_airport")
                        },
                    )
                    HorizontalDivider(color = ink.copy(alpha = .12f))
                    Text(homeTr(language, "Маршрут умры", "Your Umrah route", "Umra yo‘nalishi", "Умра йўналиши"),
                        fontSize = 12.sp, color = ink.copy(alpha = .58f))
                    HomeSearchSegmented(
                        options = listOf(
                            JourneyScope.MAKKAH_ONLY to homeTr(language, "Только Мекка", "Makkah only", "Faqat Makka", "Фақат Макка"),
                            JourneyScope.MAKKAH_AND_MADINAH to homeTr(language, "Мекка + Медина", "Makkah + Madinah", "Makka + Madina", "Макка + Мадина"),
                        ), selected = trip.scope,
                        onSelect = { scope ->
                            if (scope != trip.scope) {
                                journey.updateTrip(trip.copy(scope = scope,
                                    arrivalAirport = if (scope == JourneyScope.MAKKAH_ONLY) SaudiArrivalAirport.JEDDAH else trip.arrivalAirport))
                                IumrahIntelligence.record("tap", "home", "change_journey_scope")
                            }
                        },
                    )
                    if (trip.scope == JourneyScope.MAKKAH_AND_MADINAH) {
                        Text(homeTr(language, "Первый аэропорт прилёта", "First arrival airport", "Birinchi kelish aeroporti", "Биринчи келиш аэропорти"),
                            fontSize = 12.sp, color = ink.copy(alpha = .58f))
                        HomeSearchSegmented(
                            options = listOf(
                                SaudiArrivalAirport.JEDDAH to homeTr(language, "Джидда", "Jeddah", "Jidda", "Жидда"),
                                SaudiArrivalAirport.MADINAH to homeTr(language, "Медина", "Madinah", "Madina", "Мадина"),
                            ), selected = trip.arrivalAirport,
                            onSelect = { arrival ->
                                if (arrival != trip.arrivalAirport) {
                                    journey.updateTrip(trip.copy(arrivalAirport = arrival))
                                    IumrahIntelligence.record("tap", "home", "change_saudi_arrival")
                                }
                            },
                        )
                    }
                    HorizontalDivider(color = ink.copy(alpha = .12f))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable { calendarOpen = true }
                            .padding(vertical = 9.dp, horizontal = 3.dp)
                            .semantics { contentDescription = homeTr(language, "Выбрать даты поездки", "Choose travel dates", "Safar sanalarini tanlash", "Сафар саналарини танлаш") },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HomeDateColumn(homeTr(language, "Вылет", "Departure", "Uchish", "Учиш"), trip.departureDate.format(dateFormatter), Modifier.weight(1f))
                        CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(15.dp), ink.copy(alpha = .45f))
                        HomeDateColumn(homeTr(language, "Обратно", "Return", "Qaytish", "Қайтиш"), trip.returnDate.format(dateFormatter), Modifier.weight(1f))
                        CupertinoIcon(CupertinoSymbol.Calendar, null, Modifier.size(17.dp), ink)
                    }
                    HorizontalDivider(color = ink.copy(alpha = .12f))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable { travelersOpen = true }.padding(vertical = 10.dp, horizontal = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CupertinoIcon(CupertinoSymbol.Persons, null, Modifier.size(20.dp), ink)
                        Column(Modifier.weight(1f)) {
                            Text(homeTr(language, "Паломники", "Travelers", "Ziyoratchilar", "Зиёратчилар"), fontSize = 12.sp, color = ink.copy(alpha = .58f))
                            Text(homeTr(language, "${trip.travelerCount} чел.", "${trip.travelerCount} travelers", "${trip.travelerCount} kishi", "${trip.travelerCount} киши") + " · " + homeCabin(language, trip.effectiveFlightFilters.cabinClass),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), ink.copy(alpha = .45f))
                    }
                    Box(
                        Modifier.fillMaxWidth().height(IumrahDesign.ControlHeight)
                            .clip(CircleShape)
                            .background(Color(0xFF090909).copy(alpha = if (ok) 1f else .5f))
                            .clickable(enabled = ok) {
                                // Exactly the same Store used by TripBuilder / flight results.
                                journey.commitTripBuilder(
                                    trip.copy(flightTripType = FlightTripType.ROUND_TRIP,
                                        flexibility = DateFlexibility.EXACT),
                                    PackageFlightPath.PUBLISHED_DIRECT,
                                    completeID = journey.state.value.selectedPublishedCompleteID,
                                    outboundID = journey.state.value.selectedPublishedOutboundID,
                                    returnID = journey.state.value.selectedPublishedReturnID,
                                )
                                expanded = false
                                IumrahIntelligence.record("tap", "home", "start_trip_search")
                                chrome.openFlights()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(homeTr(language, "Найти авиабилеты", "Find flights", "Reyslarni topish", "Рейсларни топиш"),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(homeTr(language, "Рейс → Отель → Трансфер → Итог", "Flight → Hotel → Transfer → Review",
                        "Reys → Mehmonxona → Transfer → Yakun", "Рейс → Меҳмонхона → Трансфер → Якун"),
                        fontSize = 12.sp, color = ink.copy(alpha = .60f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (calendarOpen) {
        FlightDateCalendarDialog(
            language = language, trip = trip,
            initialDeparture = trip.departureDate, initialReturn = trip.returnDate,
            curatedFlights = curatedFlights, publishedOnly = true,
            onDismiss = { calendarOpen = false },
            onApply = { result ->
                val newTrip = trip.copy(
                    departureDate = result.departure,
                    returnDate = result.returnDate,
                    flightTripType = FlightTripType.ROUND_TRIP,
                    flexibility = DateFlexibility.EXACT,
                )
                journey.updateTrip(newTrip)
                // The published calendar selects feasible dates; it does NOT
                // auto-select or reserve airline fares. Flight choice is explicit.
                journey.setPublishedSelection()
                IumrahIntelligence.record("tap", "home", "change_trip_dates")
                calendarOpen = false
            },
        )
    }
    if (travelersOpen) {
        HomeTravelersSheet(language, trip,
            onDismiss = { travelersOpen = false },
            onSave = { adults, children, infants, cabin ->
                journey.updateTrip(trip.copy(adults = adults, children = children, infants = infants,
                    flightFilters = trip.effectiveFlightFilters.copy(cabinClass = cabin)))
                IumrahIntelligence.record("tap", "home", "change_travelers")
                travelersOpen = false
            },
        )
    }
}

@Composable
private fun HomeDateColumn(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun <T> HomeSearchSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (key, caption) ->
            val active = key == selected
            Box(
                Modifier.weight(1f).height(42.dp).clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(caption, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTravelersSheet(
    language: AppLanguage, trip: TripDraft,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, FlightCabinClass) -> Unit,
) {
    var adults by remember(trip) { mutableIntStateOf(trip.adults) }
    var children by remember(trip) { mutableIntStateOf(trip.children) }
    var infants by remember(trip) { mutableIntStateOf(trip.infants) }
    var cabin by remember(trip) { mutableStateOf(trip.effectiveFlightFilters.cabinClass) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Text(homeTr(language, "Паломники", "Travelers", "Ziyoratchilar", "Зиёратчилар"),
                fontSize = 25.sp, fontWeight = FontWeight.Bold)
            HomeTravelerCounter(homeTr(language, "Взрослые", "Adults", "Kattalar", "Катталар"), adults, 1, 9) {
                adults = it
                infants = infants.coerceAtMost(adults)
                children = children.coerceAtMost((9 - adults - infants).coerceAtLeast(0))
            }
            HomeTravelerCounter(homeTr(language, "Дети", "Children", "Bolalar", "Болалар"), children, 0, 9 - adults - infants) { children = it }
            HomeTravelerCounter(homeTr(language, "Младенцы", "Infants", "Chaqaloqlar", "Чақалоқлар"), infants,
                0, minOf(adults, 9 - adults - children)) { infants = it }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
            Text(homeTr(language, "Класс перелёта", "Cabin class", "Parvoz klassi", "Парвоз класси"),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlightCabinClass.entries.chunked(2).forEach { pair ->
                    HomeSearchSegmented(pair.map { it to homeCabin(language, it) }, cabin) { cabin = it }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(Color(0xFF090909))
                    .clickable { onSave(adults, children, infants, cabin) },
                contentAlignment = Alignment.Center,
            ) {
                Text(homeTr(language, "Готово", "Done", "Tayyor", "Тайёр"),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeTravelerCounter(label: String, value: Int, minimum: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        HomeRoundControl("−", value > minimum) { onChange((value - 1).coerceAtLeast(minimum)) }
        Text("$value", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        HomeRoundControl("+", value < maximum) { onChange((value + 1).coerceAtMost(maximum)) }
    }
}

@Composable
private fun HomeRoundControl(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .10f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .25f))
    }
}

private fun homeTr(language: AppLanguage, ru: String, en: String, uz: String, cy: String) = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
    else -> en
}

private fun homeLocale(language: AppLanguage): Locale = when (language) {
    AppLanguage.RUSSIAN -> Locale("ru")
    AppLanguage.UZBEK, AppLanguage.UZBEK_CYRILLIC -> Locale("uz")
    else -> Locale.ENGLISH
}

private fun homeCabin(language: AppLanguage, cabin: FlightCabinClass): String = when (cabin) {
    FlightCabinClass.ECONOMY -> homeTr(language, "Эконом", "Economy", "Ekonom", "Эконом")
    FlightCabinClass.PREMIUM_ECONOMY -> homeTr(language, "Премиум-эконом", "Premium economy", "Premium ekonom", "Премиум эконом")
    FlightCabinClass.BUSINESS -> homeTr(language, "Бизнес", "Business", "Biznes", "Бизнес")
    FlightCabinClass.FIRST -> homeTr(language, "Первый", "First", "Birinchi klass", "Биринчи класс")
}
