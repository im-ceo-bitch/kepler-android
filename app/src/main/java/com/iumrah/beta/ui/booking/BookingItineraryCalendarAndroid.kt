package com.iumrah.beta.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.models.booking.BookingItineraryItem
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class ScheduleTemporalState { NEUTRAL, COMPLETED, ACTIVE, UPCOMING }

@Composable
internal fun BookingItineraryCalendarAndroid(
    language: AppLanguage,
    session: StoredBookingSession,
    remoteItems: List<BookingItineraryItem>,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = true,
    onOpenFullSchedule: (() -> Unit)? = null,
) {
    val items = remember(session.booking.updatedAt, remoteItems, language) {
        itinerarySource(session, remoteItems, language)
    }
    val days = remember(session.booking.input.startDate, session.booking.input.endDate, items) {
        dayRange(session.booking.input.startDate, session.booking.input.endDate).ifEmpty {
            items.map { it.dateLocal }.distinct().sorted()
        }
    }
    val today = remember { LocalDate.now(ZoneId.of("Asia/Riyadh")).toString() }
    var selectedDay by remember(days, today) {
        mutableStateOf(days.firstOrNull { it == today } ?: days.firstOrNull { it >= today } ?: days.lastOrNull())
    }
    val dayItems = remember(items, selectedDay) {
        items.filter { it.dateLocal == selectedDay }
            .sortedWith(compareBy<BookingItineraryItem> { it.sortOrder }.thenBy { it.title })
    }
    val visibleDayItems = if (fullScreen) dayItems else dayItems.take(4)
    val detailed = selectedDay == today && visibleDayItems.any { !it.timeLocal.isNullOrBlank() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(bookingIosCard())
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .055f), RoundedCornerShape(30.dp))
            .padding(if (fullScreen) 20.dp else 19.dp),
        verticalArrangement = Arrangement.spacedBy(if (fullScreen) 18.dp else 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    scheduleText(language, "Расписание поездки", "Trip schedule", "Safar jadvali", "Сафар жадвали"),
                    fontSize = if (fullScreen) 27.sp else 25.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.4).sp,
                )
                Text(
                    scheduleText(
                        language,
                        "Все этапы поездки по дням и времени",
                        "Every stage of the journey by day and time",
                        "Safarning barcha bosqichlari kun va vaqt bo‘yicha",
                        "Сафарнинг барча босқичлари кун ва вақт бўйича",
                    ),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                )
            }
            if (!fullScreen && onOpenFullSchedule != null) {
                IumrahPressable(
                    onClick = onOpenFullSchedule,
                    cornerRadius = 20.dp,
                    background = bookingIosRaised(),
                    modifier = Modifier.height(40.dp),
                    pressedScale = .94f,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 10.dp).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            scheduleText(language, "Полностью", "Full", "To‘liq", "Тўлиқ"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .65f),
                        )
                        CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onBackground)
                    }
                }
            } else {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(bookingIosRaised()),
                    contentAlignment = Alignment.Center,
                ) {
                    CupertinoIcon(CupertinoSymbol.CalendarClock, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .68f))
                }
            }
        }

        if (fullScreen && selectedDay != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(bookingIosRaised().copy(alpha = .72f)).padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(fullDate(selectedDay!!, language), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (selectedDay == today) scheduleText(language, "Сегодняшний план", "Today’s plan", "Bugungi reja", "Бугунги режа")
                        else scheduleText(language, "План на выбранный день", "Plan for the selected day", "Tanlangan kun rejasi", "Танланган кун режаси"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                    )
                }
                if (selectedDay == today) {
                    Text(
                        scheduleText(language, "СЕЙЧАС", "NOW", "HOZIR", "ҲОЗИР"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .78f)).padding(horizontal = 11.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (days.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                days.forEach { day ->
                    val selected = day == selectedDay
                    val isToday = day == today
                    IumrahPressable(
                        onClick = { selectedDay = day },
                        modifier = Modifier.width(if (selected && isToday) 68.dp else 58.dp).height(if (selected && isToday) 70.dp else 64.dp),
                        cornerRadius = 19.dp,
                        background = if (selected) MaterialTheme.colorScheme.onBackground.copy(alpha = .78f) else bookingIosRaised(),
                        shadowElevation = 0.dp,
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(dayNumber(day), fontSize = if (selected && isToday) 20.sp else 18.sp, fontWeight = FontWeight.Bold, color = if (selected) bookingIosPrimaryText() else MaterialTheme.colorScheme.onBackground)
                            Text(weekday(day, language), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (selected) bookingIosPrimaryText().copy(alpha = .78f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                            if (isToday) {
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) Color.White.copy(alpha = .88f) else Color(0xFF007AFF)))
                            }
                        }
                    }
                }
            }
        }

        if (visibleDayItems.isEmpty()) {
            Text(
                scheduleText(language, "На этот день событий пока нет.", "There are no events for this day yet.", "Bu kun uchun hozircha voqealar yo‘q.", "Бу кун учун ҳозирча воқеалар йўқ."),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(if (detailed) 14.dp else 10.dp)) {
                visibleDayItems.forEachIndexed { index, item ->
                    if (detailed) TimedScheduleRow(language, item, selectedDay == today, index == visibleDayItems.lastIndex)
                    else CompactScheduleRow(language, item)
                }
            }
        }
    }
}

@Composable
private fun CompactScheduleRow(language: AppLanguage, item: BookingItineraryItem) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bookingIosRaised().copy(alpha = .72f)).padding(13.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item.timeLocal?.takeIf { it.isNotBlank() }?.let { start ->
            Column(Modifier.width(43.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(start, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                item.endTimeLocal?.takeIf { it.isNotBlank() && it != start }?.let { end ->
                    Text(end, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                }
            }
        }
        ScheduleIcon(item.icon, Color(0xFF007AFF), 40)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.location.isNotBlank()) Text("• ${item.location}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                durationLabel(item, language)?.let { Text(it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f)) }
            }
        }
    }
}

@Composable
private fun TimedScheduleRow(language: AppLanguage, item: BookingItineraryItem, today: Boolean, isLast: Boolean) {
    val state = temporalState(item, today)
    val tint = when (state) {
        ScheduleTemporalState.ACTIVE -> Color(0xFF007AFF)
        ScheduleTemporalState.UPCOMING -> Color(0xFFFF9500)
        ScheduleTemporalState.COMPLETED -> Color(0xFF8E8E93)
        ScheduleTemporalState.NEUTRAL -> Color(0xFF007AFF)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(66.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.timeLocal ?: "—", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            item.endTimeLocal?.takeIf { it.isNotBlank() && it != item.timeLocal }?.let { Text(it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f)) }
            durationLabel(item, language)?.let { Text(it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f)) }
        }
        Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 7.dp).size(if (state == ScheduleTemporalState.ACTIVE) 14.dp else 11.dp).clip(CircleShape).background(tint))
            if (!isLast) Box(Modifier.padding(top = 6.dp).width(2.dp).height(94.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .10f)))
        }
        val bg = when (state) {
            ScheduleTemporalState.ACTIVE -> tint.copy(alpha = .08f)
            else -> bookingIosRaised().copy(alpha = .78f)
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(bg).border(if (state == ScheduleTemporalState.ACTIVE) 1.dp else .7.dp, if (state == ScheduleTemporalState.ACTIVE) tint.copy(alpha = .22f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .04f), RoundedCornerShape(22.dp)).padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScheduleIcon(item.icon, tint, 42)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                if (item.location.isNotBlank()) Text("• ${item.location}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.notes.isNotBlank()) Text(item.notes, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
            if (state != ScheduleTemporalState.NEUTRAL) {
                val label = when (state) {
                    ScheduleTemporalState.ACTIVE -> scheduleText(language, "Сейчас", "Now", "Hozir", "Ҳозир")
                    ScheduleTemporalState.UPCOMING -> scheduleText(language, "Далее", "Next", "Keyin", "Кейин")
                    ScheduleTemporalState.COMPLETED -> scheduleText(language, "Завершено", "Done", "Tugadi", "Тугади")
                    ScheduleTemporalState.NEUTRAL -> ""
                }
                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (state == ScheduleTemporalState.COMPLETED) MaterialTheme.colorScheme.onBackground.copy(alpha = .52f) else MaterialTheme.colorScheme.onBackground, modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = .13f)).padding(horizontal = 9.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun ScheduleIcon(raw: String, tint: Color, size: Int) {
    val symbol = when {
        raw.contains("airplane") -> if (raw.contains("arrival")) CupertinoSymbol.AirplaneLand else CupertinoSymbol.AirplaneTakeoff
        raw.contains("building") -> CupertinoSymbol.Building
        raw.contains("tram") -> CupertinoSymbol.Route
        raw.contains("car") -> CupertinoSymbol.Car
        raw.contains("person") -> CupertinoSymbol.Persons
        raw.contains("moon") -> CupertinoSymbol.MoonStarsFill
        raw.contains("spark") -> CupertinoSymbol.Sparkles
        else -> CupertinoSymbol.CalendarClock
    }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
        CupertinoIcon(symbol, null, Modifier.size(16.dp), tint)
    }
}

private fun temporalState(item: BookingItineraryItem, today: Boolean): ScheduleTemporalState {
    if (!today || item.timeLocal.isNullOrBlank()) return ScheduleTemporalState.NEUTRAL
    val day = runCatching { LocalDate.parse(item.dateLocal) }.getOrNull() ?: return ScheduleTemporalState.NEUTRAL
    val start = runCatching { LocalDateTime.of(day, LocalTime.parse(item.timeLocal)) }.getOrNull() ?: return ScheduleTemporalState.NEUTRAL
    val end = item.endTimeLocal?.let { runCatching { LocalDateTime.of(day, LocalTime.parse(it)) }.getOrNull() } ?: start.plusMinutes(50)
    val now = LocalDateTime.now(ZoneId.of("Asia/Riyadh"))
    return when {
        now.isBefore(start) -> ScheduleTemporalState.UPCOMING
        !now.isAfter(end) -> ScheduleTemporalState.ACTIVE
        else -> ScheduleTemporalState.COMPLETED
    }
}

private fun durationLabel(item: BookingItineraryItem, language: AppLanguage): String? {
    val start = item.timeLocal?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    val end = item.endTimeLocal?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
    val minutes = Duration.between(start, end).toMinutes().let { if (it <= 0) it + 24 * 60 else it }.toInt().coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    return when (language) {
        AppLanguage.RUSSIAN -> if (h > 0 && m > 0) "≈ $h ч $m мин" else if (h > 0) "≈ $h ч" else "≈ $m мин"
        AppLanguage.ENGLISH -> if (h > 0 && m > 0) "~ ${h}h ${m}m" else if (h > 0) "~ ${h}h" else "~ ${m}m"
        AppLanguage.UZBEK -> if (h > 0 && m > 0) "≈ $h soat $m daq" else if (h > 0) "≈ $h soat" else "≈ $m daq"
        AppLanguage.UZBEK_CYRILLIC -> if (h > 0 && m > 0) "≈ $h соат $m дақ" else if (h > 0) "≈ $h соат" else "≈ $m дақ"
    }
}

private fun itinerarySource(session: StoredBookingSession, remote: List<BookingItineraryItem>, language: AppLanguage): List<BookingItineraryItem> {
    if (remote.any { !it.timeLocal.isNullOrBlank() }) return remote.sortedWith(compareBy<BookingItineraryItem> { it.dateLocal }.thenBy { it.sortOrder })
    val days = dayRange(session.booking.input.startDate, session.booking.input.endDate)
    val distinct = remote.map { it.dateLocal }.toSet().size
    val minimumUseful = minOf(3, maxOf(2, days.size - 1))
    if (distinct >= minimumUseful) return remote.sortedWith(compareBy<BookingItineraryItem> { it.dateLocal }.thenBy { it.sortOrder })
    return generatedItinerary(session, language)
}

private fun generatedItinerary(session: StoredBookingSession, language: AppLanguage): List<BookingItineraryItem> {
    val booking = session.booking
    val days = dayRange(booking.input.startDate, booking.input.endDate)
    if (days.isEmpty()) return emptyList()
    val first = days.first(); val last = days.last()
    val madinahFirst = booking.stay.madinahCheckIn == first || booking.input.arrivalAirportCode.uppercase() == "MED"
    val out = mutableListOf<BookingItineraryItem>()
    fun add(day: String, order: Int, title: String, subtitle: String, icon: String, location: String) {
        if (day !in days) return
        out += BookingItineraryItem("local-${session.id}-$day-$order-${out.size}", session.id, day, order, title, subtitle, icon, location, "", "", "")
    }
    val cityMakkah = scheduleText(language, "Мекка", "Makkah", "Makka", "Макка")
    val cityMadinah = scheduleText(language, "Медина", "Madinah", "Madina", "Мадина")
    val arrivalCity = if (madinahFirst) cityMadinah else cityMakkah
    add(first, 10, scheduleText(language, "Прилёт и встреча", "Arrival and welcome", "Kelish va kutib olish", "Келиш ва кутиб олиш"), booking.flight, "airplane.arrival", arrivalCity)
    add(first, 20, scheduleText(language, "Заселение в отель", "Hotel check-in", "Mehmonxonaga joylashish", "Меҳмонхонага жойлашиш"), if (madinahFirst) booking.hotelNames.madinah else booking.hotelNames.makkah, "building.2.fill", arrivalCity)
    val makkahIn = booking.stay.makkahCheckIn
    val umrahDay = days.firstOrNull { it > makkahIn && it < last } ?: makkahIn.takeIf { it < last }
    if (umrahDay != null) add(umrahDay, 30, "Umrah", scheduleText(language, "Таваф, Са’и и завершение Умры", "Tawaf, Sa’i and completing Umrah", "Tavof, Sa’y va Umrani yakunlash", "Тавоф, Са’й ва Умрани якунлаш"), "person.2.fill", cityMakkah)
    booking.stay.madinahCheckIn?.takeIf { booking.input.includeMadinah && it in days && it != first }?.let { day ->
        add(day, 10, scheduleText(language, "Переезд в Медину", "Transfer to Madinah", "Madinaga yo‘l", "Мадинага йўл"), scheduleText(language, "Междугородний трансфер", "Intercity transfer", "Shaharlararo transfer", "Шаҳарлараро трансфер"), "car.fill", cityMadinah)
        add(day, 20, scheduleText(language, "Заселение в отель", "Hotel check-in", "Mehmonxonaga joylashish", "Меҳмонхонага жойлашиш"), booking.hotelNames.madinah, "building.2.fill", cityMadinah)
    }
    add(last, 80, scheduleText(language, "Трансфер в аэропорт", "Airport transfer", "Aeroportga transfer", "Аэропортга трансфер"), scheduleText(language, "Подготовка к вылету домой", "Preparing for the flight home", "Uyga parvozga tayyorgarlik", "Уйга парвозга тайёргарлик"), "car.fill", booking.route.returnOrigin)
    add(last, 90, scheduleText(language, "Вылет домой", "Flight home", "Uyga parvoz", "Уйга парвоз"), booking.flight, "airplane.departure", booking.route.returnOrigin)
    return out.sortedWith(compareBy<BookingItineraryItem> { it.dateLocal }.thenBy { it.sortOrder })
}

private fun dayRange(start: String, end: String): List<String> {
    val a = runCatching { LocalDate.parse(start) }.getOrNull() ?: return emptyList()
    val b = runCatching { LocalDate.parse(end) }.getOrNull() ?: return emptyList()
    if (b.isBefore(a)) return emptyList()
    val out = mutableListOf<String>()
    var cursor = a
    while (!cursor.isAfter(b) && out.size < 40) { out += cursor.toString(); cursor = cursor.plusDays(1) }
    return out
}

private fun dayNumber(raw: String): String = runCatching { LocalDate.parse(raw).dayOfMonth.toString() }.getOrDefault(raw.takeLast(2))
private fun weekday(raw: String, language: AppLanguage): String {
    val d = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return ""
    return d.dayOfWeek.getDisplayName(TextStyle.SHORT, language.locale).replace(".", "")
}
private fun fullDate(raw: String, language: AppLanguage): String {
    val d = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return raw
    return d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale))
}
private fun scheduleText(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
