package com.iumrah.beta.ui.trip

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iumrah.beta.R
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.models.flight.CuratedPublishedFlightSelection
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.generator.generatorCardColor
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.generator.generatorRaisedColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Android counterpart of iOS FlightDateCalendarView. */
data class FlightDateCalendarResult(
    val departure: LocalDate,
    val returnDate: LocalDate,
    val publishedSelection: CuratedPublishedFlightSelection?,
)

@Composable
fun FlightDateCalendarDialog(
    language: AppLanguage,
    trip: TripDraft,
    initialDeparture: LocalDate,
    initialReturn: LocalDate,
    curatedFlights: CuratedFlightRecommendationService,
    onDismiss: () -> Unit,
    onApply: (FlightDateCalendarResult) -> Unit,
) {
    var departure by remember { mutableStateOf<LocalDate?>(initialDeparture) }
    var returnDate by remember { mutableStateOf<LocalDate?>(initialReturn) }
    var publishedFlights by remember { mutableStateOf<List<CuratedFlightRecommendation>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var showFlexibleWarning by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableStateOf(0) }

    val today = remember { LocalDate.now() }
    val firstMonth = remember(today) { YearMonth.from(today) }
    val months = remember(firstMonth) { (0 until 6).map { firstMonth.plusMonths(it.toLong()) } }

    LaunchedEffect(trip.originCode, trip.scope, trip.arrivalAirport, reloadNonce) {
        loading = true
        loadError = false
        val lastDay = months.last().atEndOfMonth()
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, lastDay).toInt().coerceAtLeast(1)
        runCatching { curatedFlights.load(trip, today, (days + 2).coerceAtMost(365)) }
            .onSuccess { publishedFlights = it }
            .onFailure { publishedFlights = emptyList(); loadError = true }
        loading = false
    }

    fun completeRecommendations() = publishedFlights.filter { recommendation ->
        val inbound = recommendation.inbound ?: return@filter false
        recommendation.outbound.origin.equals(trip.originCode, true) &&
            recommendation.outbound.destination.equals(trip.outboundDestinationCode, true) &&
            inbound.origin.equals(trip.returnOriginCode, true) &&
            inbound.destination.equals(trip.originCode, true) &&
            recommendation.inboundDate != null
    }
    fun oneWayOutbound() = publishedFlights.filter { recommendation ->
        recommendation.inbound == null &&
            recommendation.outbound.origin.equals(trip.originCode, true) &&
            recommendation.outbound.destination.equals(trip.outboundDestinationCode, true)
    }
    fun oneWayReturn() = publishedFlights.filter { recommendation ->
        recommendation.inbound == null &&
            recommendation.outbound.origin.equals(trip.returnOriginCode, true) &&
            recommendation.outbound.destination.equals(trip.originCode, true)
    }
    fun publishedSelection(out: LocalDate, back: LocalDate): CuratedPublishedFlightSelection? {
        val outKey = out.toString()
        val backKey = back.toString()
        completeRecommendations().firstOrNull { it.outboundDate == outKey && it.inboundDate == backKey }?.let {
            return CuratedPublishedFlightSelection(completeID = it.id)
        }
        val outFlight = oneWayOutbound().firstOrNull { it.outboundDate == outKey }
        val returnFlight = oneWayReturn().firstOrNull { it.outboundDate == backKey }
        return if (outFlight != null && returnFlight != null) CuratedPublishedFlightSelection(outboundID = outFlight.id, returnID = returnFlight.id) else null
    }
    fun recommendedOutboundKeys(): Set<String> = buildSet {
        completeRecommendations().forEach { add(it.outboundDate) }
        oneWayOutbound().forEach { add(it.outboundDate) }
    }
    fun recommendedReturnKeys(out: LocalDate): Set<String> = buildSet {
        val outKey = out.toString()
        completeRecommendations().forEach { if (it.outboundDate == outKey) it.inboundDate?.let(::add) }
        if (oneWayOutbound().any { it.outboundDate == outKey }) {
            oneWayReturn().forEach { rec ->
                runCatching { LocalDate.parse(rec.outboundDate) }.getOrNull()?.takeIf { it.isAfter(out) }?.let { add(rec.outboundDate) }
            }
        }
    }
    fun isRecommended(day: LocalDate): Boolean {
        val out = departure
        return if (out != null && returnDate == null) recommendedReturnKeys(out).contains(day.toString())
        else recommendedOutboundKeys().contains(day.toString())
    }
    fun select(day: LocalDate) {
        val out = departure
        val back = returnDate
        when {
            out == null || back != null -> { departure = day; returnDate = null }
            !day.isAfter(out) -> { departure = day; returnDate = null }
            else -> returnDate = day
        }
    }

    val selectedPublished = departure?.let { out -> returnDate?.let { back -> publishedSelection(out, back) } }
    val selectionNeedsFlexible = departure?.let { out ->
        returnDate?.let { back -> publishedSelection(out, back) == null }
            ?: !recommendedOutboundKeys().contains(out.toString())
    } ?: false
    val hasValidRange = departure?.let { out -> returnDate?.isAfter(out) == true } == true
    val locale = calendarLocale(language)
    val page = generatorPageColor()
    val card = generatorCardColor()
    val raised = generatorRaisedColor()
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = primary.copy(alpha = .56f)
    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9500)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize()
                .background(page)
                .background(
                    Brush.linearGradient(
                        listOf(primary.copy(alpha = .018f), Color.Transparent, green.copy(alpha = .055f)),
                    ),
                ),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                CalendarTopBar(language, onDismiss)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 136.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(calendarCopy(language, CalendarCopy.TITLE), fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
                        Text(calendarCopy(language, CalendarCopy.SUBTITLE), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = secondary)
                    }

                    Row(
                        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(24.dp)).background(card.copy(alpha = .94f)).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RouteCode(trip.originCode, Modifier.weight(1f)); Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(13.dp), secondary)
                        RouteCode(trip.outboundDestinationCode, Modifier.weight(1f)); Icon(CupertinoSymbol.ArrowLeftRight, null, Modifier.size(13.dp), secondary)
                        RouteCode(trip.returnOriginCode, Modifier.weight(1f)); Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(13.dp), secondary)
                        RouteCode(trip.originCode, Modifier.weight(1f))
                    }

                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(green.copy(alpha = .075f)).border(1.dp, green.copy(alpha = .16f), RoundedCornerShape(22.dp)).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(green.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                            Icon(CupertinoSymbol.Sparkles, null, Modifier.size(16.dp), green)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(calendarCopy(language, CalendarCopy.RECOMMENDED), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(if (loading) calendarCopy(language, CalendarCopy.LOADING) else calendarCopy(language, CalendarCopy.DIRECT_HINT), fontSize = 12.sp, lineHeight = 17.sp, color = secondary)
                        }
                        if (loadError) {
                            Text(calendarCopy(language, CalendarCopy.RETRY), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(raised).clickable { reloadNonce += 1 }.padding(horizontal = 11.dp, vertical = 9.dp))
                        }
                    }

                    if (selectionNeedsFlexible) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(orange.copy(alpha = .10f)).border(1.dp, orange.copy(alpha = .24f), RoundedCornerShape(20.dp)).padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                        ) {
                            Icon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(18.dp), orange)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(calendarCopy(language, CalendarCopy.NO_DIRECT_TITLE), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(calendarCopy(language, CalendarCopy.NO_DIRECT_INLINE), fontSize = 12.sp, lineHeight = 17.sp, color = secondary)
                            }
                        }
                    }

                    months.forEach { month ->
                        CalendarMonth(
                            language = language,
                            locale = locale,
                            month = month,
                            today = today,
                            departure = departure,
                            returnDate = returnDate,
                            directPair = selectedPublished != null,
                            isRecommended = ::isRecommended,
                            onSelect = ::select,
                        )
                    }

                    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(CupertinoSymbol.CheckCircle, null, Modifier.size(16.dp), secondary)
                        Text(calendarCopy(language, CalendarCopy.INVENTORY_NOTE), fontSize = 12.sp, lineHeight = 17.sp, color = secondary, modifier = Modifier.weight(1f))
                    }
                }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(card).navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                IumrahPressable(onClick = { departure = null; returnDate = null }, modifier = Modifier.width(116.dp).height(60.dp), cornerRadius = 22.dp, background = raised) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(calendarCopy(language, CalendarCopy.RESET), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
                IumrahPressable(
                    onClick = {
                        val out = departure; val back = returnDate
                        if (out != null && back != null && back.isAfter(out)) {
                            val published = publishedSelection(out, back)
                            if (published != null) onApply(FlightDateCalendarResult(out, back, published)) else showFlexibleWarning = true
                        }
                    },
                    enabled = hasValidRange,
                    modifier = Modifier.weight(1f).height(60.dp),
                    cornerRadius = 22.dp,
                    background = primary.copy(alpha = if (hasValidRange) .92f else .38f),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(calendarCopy(language, CalendarCopy.CONTINUE), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = card) }
                }
            }

            if (showFlexibleWarning) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f)).clickable { showFlexibleWarning = false }, contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.padding(horizontal = 22.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(card).border(1.dp, primary.copy(alpha = .07f), RoundedCornerShape(30.dp)).clickable(enabled = false) {}.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(orange.copy(alpha = .13f)), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(22.dp), orange) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(calendarCopy(language, CalendarCopy.NO_DIRECT_TITLE), fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                            Text(calendarCopy(language, CalendarCopy.NO_DIRECT_POPUP), fontSize = 16.sp, lineHeight = 22.sp, color = secondary)
                        }
                        IumrahPressable(onClick = { showFlexibleWarning = false; departure = null; returnDate = null }, modifier = Modifier.fillMaxWidth().height(56.dp), cornerRadius = 19.dp, background = green) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(calendarCopy(language, CalendarCopy.CHOOSE_DIRECT), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = card) }
                        }
                        IumrahPressable(onClick = {
                            val out = departure; val back = returnDate
                            if (out != null && back != null) onApply(FlightDateCalendarResult(out, back, null))
                        }, modifier = Modifier.fillMaxWidth().height(56.dp), cornerRadius = 19.dp, background = raised) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(calendarCopy(language, CalendarCopy.CONTINUE_FLEXIBLE), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarTopBar(language: AppLanguage, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp)) {
        IumrahPressable(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart).size(42.dp), cornerRadius = 99.dp, background = generatorRaisedColor()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.Close, calendarCopy(language, CalendarCopy.CLOSE), Modifier.size(14.dp)) }
        }
        Image(painterResource(R.drawable.iumrah_flights_calendar_logo), null, Modifier.align(Alignment.Center).width(138.dp).height(30.dp), contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
    }
}

@Composable
private fun RouteCode(code: String, modifier: Modifier = Modifier) {
    Text(code, modifier = modifier, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
}

@Composable
private fun CalendarMonth(
    language: AppLanguage,
    locale: Locale,
    month: YearMonth,
    today: LocalDate,
    departure: LocalDate?,
    returnDate: LocalDate?,
    directPair: Boolean,
    isRecommended: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = primary.copy(alpha = .56f)
    val green = Color(0xFF34C759)
    val card = generatorCardColor()
    val weekdays = remember(locale) { (1..7).map { DayOfWeek.of(it).getDisplayName(TextStyle.NARROW_STANDALONE, locale) } }
    val leading = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val cells: List<LocalDate?> = List(leading) { null } + (1..days).map { month.atDay(it) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(card.copy(alpha = .94f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val title = month.atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        Text(title, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) { weekdays.forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = secondary, textAlign = TextAlign.Center) } }
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                (row + List(7 - row.size) { null }).forEach { date ->
                    if (date == null) Spacer(Modifier.weight(1f).height(58.dp))
                    else CalendarDay(date, today, departure, returnDate, directPair, isRecommended(date), onSelect, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    day: LocalDate,
    today: LocalDate,
    departure: LocalDate?,
    returnDate: LocalDate?,
    directPair: Boolean,
    directDay: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val disabled = day.isBefore(today)
    val isDeparture = day == departure
    val isReturn = day == returnDate
    val inRange = departure != null && returnDate != null && day.isAfter(departure) && day.isBefore(returnDate)
    val primary = MaterialTheme.colorScheme.onSurface
    val card = generatorCardColor()
    val green = Color(0xFF34C759)
    val selectedColor = if (directPair) green else primary
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        isDeparture || isReturn -> selectedColor
        inRange -> selectedColor.copy(alpha = if (directPair) .15f else .055f)
        directDay -> green.copy(alpha = .14f)
        else -> Color.Transparent
    }
    val fg = when {
        disabled -> primary.copy(alpha = .22f)
        isDeparture || isReturn -> card
        directDay -> green
        else -> primary
    }
    Box(
        modifier.height(58.dp).padding(2.dp).clip(shape).background(bg)
            .then(if (directDay && !isDeparture && !isReturn) Modifier.border(.8.dp, green.copy(alpha = .24f), shape) else Modifier)
            .clickable(enabled = !disabled) { onSelect(day) },
        contentAlignment = Alignment.Center,
    ) {
        Text(day.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = if (isDeparture || isReturn || directDay) FontWeight.Bold else FontWeight.Medium, color = fg)
    }
}

private fun calendarLocale(language: AppLanguage) = when (language) {
    AppLanguage.ENGLISH -> Locale.forLanguageTag("en-US")
    AppLanguage.RUSSIAN -> Locale.forLanguageTag("ru-RU")
    AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn-UZ")
    AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl-UZ")
}

private enum class CalendarCopy { TITLE, SUBTITLE, RECOMMENDED, LOADING, DIRECT_HINT, RETRY, NO_DIRECT_TITLE, NO_DIRECT_INLINE, NO_DIRECT_POPUP, INVENTORY_NOTE, RESET, CONTINUE, CHOOSE_DIRECT, CONTINUE_FLEXIBLE, CLOSE }
private fun calendarCopy(l: AppLanguage, key: CalendarCopy): String = when (l) {
    AppLanguage.RUSSIAN -> when (key) {
        CalendarCopy.TITLE -> "Выберите даты"; CalendarCopy.SUBTITLE -> "Зелёные дни — опубликованные прямые рейсы iumrah"; CalendarCopy.RECOMMENDED -> "Рекомендует iumrah AI"; CalendarCopy.LOADING -> "Проверяем опубликованные прямые рейсы…"; CalendarCopy.DIRECT_HINT -> "Зелёные дни доступны в базе iumrah: это прямые рейсы, которые обычно удобнее и выгоднее вариантов с пересадками."; CalendarCopy.RETRY -> "Обновить"; CalendarCopy.NO_DIRECT_TITLE -> "На эти даты нет прямых рейсов"; CalendarCopy.NO_DIRECT_INLINE -> "Можно продолжить с гибкими датами, но система может предложить рейсы с одной или двумя пересадками и более высокой стоимостью. Это повлияет на итоговую цену пакета."; CalendarCopy.NO_DIRECT_POPUP -> "В iumrah сейчас нет опубликованной пары прямых рейсов на выбранные даты. При гибком поиске могут появиться варианты с 1–2 пересадками и более высокой ценой, которая увеличит итоговую стоимость вашего пакета."; CalendarCopy.INVENTORY_NOTE -> "Зелёные дни берутся только из опубликованных рейсов в базе iumrah. Другие дни не помечаются как прямые автоматически."; CalendarCopy.RESET -> "Сбросить"; CalendarCopy.CONTINUE -> "Продолжить"; CalendarCopy.CHOOSE_DIRECT -> "Выбрать даты с прямым рейсом"; CalendarCopy.CONTINUE_FLEXIBLE -> "Продолжить с гибкими датами"; CalendarCopy.CLOSE -> "Закрыть"
    }
    AppLanguage.ENGLISH -> when (key) {
        CalendarCopy.TITLE -> "Choose your dates"; CalendarCopy.SUBTITLE -> "Green days are iumrah-published direct flights"; CalendarCopy.RECOMMENDED -> "Recommended by iumrah AI"; CalendarCopy.LOADING -> "Checking published direct flights…"; CalendarCopy.DIRECT_HINT -> "Green days are available in iumrah inventory: direct flights that are usually more convenient and better value than connecting options."; CalendarCopy.RETRY -> "Refresh"; CalendarCopy.NO_DIRECT_TITLE -> "No direct flights on these dates"; CalendarCopy.NO_DIRECT_INLINE -> "You can continue with flexible dates, but the system may offer one- or two-stop flights at a higher fare. This affects your final package total."; CalendarCopy.NO_DIRECT_POPUP -> "iumrah currently has no published direct-flight pair for these dates. Flexible search may return one- or two-stop options at a higher fare, increasing your final package total."; CalendarCopy.INVENTORY_NOTE -> "Green days come only from flights published in the iumrah database. Other dates are never marked direct automatically."; CalendarCopy.RESET -> "Reset"; CalendarCopy.CONTINUE -> "Continue"; CalendarCopy.CHOOSE_DIRECT -> "Choose direct-flight dates"; CalendarCopy.CONTINUE_FLEXIBLE -> "Continue with flexible dates"; CalendarCopy.CLOSE -> "Close"
    }
    AppLanguage.UZBEK -> when (key) {
        CalendarCopy.TITLE -> "Sanalarni tanlang"; CalendarCopy.SUBTITLE -> "Yashil kunlar — iumrah e’lon qilgan to‘g‘ridan-to‘g‘ri reyslar"; CalendarCopy.RECOMMENDED -> "iumrah AI tavsiya qiladi"; CalendarCopy.LOADING -> "E’lon qilingan to‘g‘ri reyslar tekshirilmoqda…"; CalendarCopy.DIRECT_HINT -> "Yashil kunlar iumrah bazasida mavjud: ular odatda transferli variantlardan qulayroq va foydaliroq bo‘lgan to‘g‘ridan-to‘g‘ri reyslardir."; CalendarCopy.RETRY -> "Yangilash"; CalendarCopy.NO_DIRECT_TITLE -> "Bu sanalarda to‘g‘ridan-to‘g‘ri reys yo‘q"; CalendarCopy.NO_DIRECT_INLINE -> "Moslashuvchan sanalar bilan davom etishingiz mumkin, ammo tizim 1–2 ta transferli va qimmatroq reyslarni taklif qilishi mumkin. Bu paketning yakuniy narxiga ta’sir qiladi."; CalendarCopy.NO_DIRECT_POPUP -> "Hozir iumrah bazasida tanlangan sanalar uchun e’lon qilingan to‘g‘ridan-to‘g‘ri reys juftligi yo‘q. Moslashuvchan qidiruv 1–2 ta transferli va qimmatroq variantlarni topishi mumkin; bu yakuniy paket narxini oshiradi."; CalendarCopy.INVENTORY_NOTE -> "Yashil kunlar faqat iumrah bazasida e’lon qilingan reyslardan olinadi. Boshqa kunlar avtomatik ravishda to‘g‘ri reys deb belgilanmaydi."; CalendarCopy.RESET -> "Tozalash"; CalendarCopy.CONTINUE -> "Davom etish"; CalendarCopy.CHOOSE_DIRECT -> "To‘g‘ri reysli sanalarni tanlash"; CalendarCopy.CONTINUE_FLEXIBLE -> "Moslashuvchan sanalar bilan davom etish"; CalendarCopy.CLOSE -> "Yopish"
    }
    AppLanguage.UZBEK_CYRILLIC -> when (key) {
        CalendarCopy.TITLE -> "Саналарни танланг"; CalendarCopy.SUBTITLE -> "Яшил кунлар — iumrah эълон қилган тўғридан-тўғри рейслар"; CalendarCopy.RECOMMENDED -> "iumrah AI тавсия қилади"; CalendarCopy.LOADING -> "Эълон қилинган тўғри рейслар текширилмоқда…"; CalendarCopy.DIRECT_HINT -> "Яшил кунлар iumrah базасида мавжуд: улар одатда трансферли вариантлардан қулайроқ ва фойдалироқ бўлган тўғридан-тўғри рейслардир."; CalendarCopy.RETRY -> "Янгилаш"; CalendarCopy.NO_DIRECT_TITLE -> "Бу саналарда тўғридан-тўғри рейс йўқ"; CalendarCopy.NO_DIRECT_INLINE -> "Мослашувчан саналар билан давом этишингиз мумкин, аммо тизим 1–2 та трансферли ва қимматроқ рейсларни таклиф қилиши мумкин. Бу пакетнинг якуний нархига таъсир қилади."; CalendarCopy.NO_DIRECT_POPUP -> "Ҳозир iumrah базасида танланган саналар учун эълон қилинган тўғридан-тўғри рейс жуфтлиги йўқ. Мослашувчан қидирув 1–2 та трансферли ва қимматроқ вариантларни топиши мумкин; бу якуний пакет нархини оширади."; CalendarCopy.INVENTORY_NOTE -> "Яшил кунлар фақат iumrah базасида эълон қилинган рейслардан олинади. Бошқа кунлар автоматик равишда тўғри рейс деб белгиланмайди."; CalendarCopy.RESET -> "Тозалаш"; CalendarCopy.CONTINUE -> "Давом этиш"; CalendarCopy.CHOOSE_DIRECT -> "Тўғри рейсли саналарни танлаш"; CalendarCopy.CONTINUE_FLEXIBLE -> "Мослашувчан саналар билан давом этиш"; CalendarCopy.CLOSE -> "Ёпиш"
    }
}
