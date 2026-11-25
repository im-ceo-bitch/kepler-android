@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.prayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import java.time.chrono.HijrahDate
import kotlin.math.abs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.prayer.PrayerAlert
import com.iumrah.beta.data.prayer.PrayerAlarmScheduler
import com.iumrah.beta.data.prayer.PrayerOptions
import com.iumrah.beta.data.prayer.PrayerPreferences
import com.iumrah.beta.domain.prayer.*
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val wallpaperIds = listOf(
    R.drawable.prayer_wallpaper_sea, R.drawable.prayer_wallpaper_rows,
    R.drawable.prayer_wallpaper_crowd, R.drawable.prayer_wallpaper_motion,
    R.drawable.prayer_wallpaper_kaaba_corner, R.drawable.prayer_wallpaper_touch,
    R.drawable.prayer_wallpaper_sujud,
)
private val wallpaperNames = listOf("Sea Prayer", "Prayer Rows", "Pilgrim Light", "Red Silence", "Kaaba Corner", "Kaaba Touch", "Sujud")
private val fivePrayers = listOf(PrayerKind.FAJR, PrayerKind.DHUHR, PrayerKind.ASR, PrayerKind.MAGHRIB, PrayerKind.ISHA)
private val sixRows = listOf(PrayerKind.FAJR, PrayerKind.SUNRISE, PrayerKind.DHUHR, PrayerKind.ASR, PrayerKind.MAGHRIB, PrayerKind.ISHA)
private enum class PrayerDisplayMode { COUNTDOWN, SCHEDULE, QIBLA }

internal fun tr(lang: AppLanguage, ru: String, en: String, uz: String, cy: String) = when (lang) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, lang)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
private fun prayerName(kind: PrayerKind, lang: AppLanguage): String = when (kind) {
    PrayerKind.FAJR -> tr(lang, "Фаджр", "Fajr", "Bomdod", "Бомдод")
    PrayerKind.SUNRISE -> tr(lang, "Восход", "Sunrise", "Quyosh chiqishi", "Қуёш чиқиши")
    PrayerKind.DHUHR -> tr(lang, "Зухр", "Dhuhr", "Peshin", "Пешин")
    PrayerKind.ASR -> tr(lang, "Аср", "Asr", "Asr", "Аср")
    PrayerKind.MAGHRIB -> tr(lang, "Магриб", "Maghrib", "Shom", "Шом")
    PrayerKind.ISHA -> tr(lang, "Иша", "Isha", "Xufton", "Хуфтон")
    PrayerKind.DUHA -> tr(lang, "Духа", "Duha", "Zuho", "Зуҳо")
    PrayerKind.TAHAJJUD -> tr(lang, "Тахаджуд", "Tahajjud", "Tahajjud", "Таҳажжуд")
}
private fun symbol(kind: PrayerKind) = when (kind) {
    PrayerKind.FAJR, PrayerKind.TAHAJJUD -> CupertinoSymbol.MoonStarsFill
    PrayerKind.SUNRISE, PrayerKind.DUHA, PrayerKind.DHUHR -> CupertinoSymbol.SunMaxFill
    PrayerKind.ASR -> CupertinoSymbol.Sun
    PrayerKind.MAGHRIB -> CupertinoSymbol.Moon
    PrayerKind.ISHA -> CupertinoSymbol.Moon
}
private fun tint(kind: PrayerKind) = when (kind) {
    PrayerKind.FAJR, PrayerKind.TAHAJJUD -> Color(0xFF6863C8)
    PrayerKind.SUNRISE, PrayerKind.DUHA -> Color(0xFFF49A36)
    PrayerKind.DHUHR -> Color(0xFFD6A21A)
    PrayerKind.ASR -> Color(0xFF45A7A1)
    PrayerKind.MAGHRIB -> Color(0xFFD46693)
    PrayerKind.ISHA -> Color(0xFF9A6ED4)
}
private fun clockText(instant: Instant?, place: PrayerPlace): String =
    instant?.atZone(place.zone)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"

/** Standalone Android version of iOS `IumrahPrayerTimesView` with offline calculations. */
@Composable
fun PrayerTimesScreen(language: AppLanguage, chrome: AppChromeStore) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val window = (context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val light = controller?.isAppearanceLightStatusBars
        if (controller != null) controller.isAppearanceLightStatusBars = false
        onDispose { if (light != null) controller?.isAppearanceLightStatusBars = light }
    }
    val storage = remember(context) { PrayerPreferences(context) }
    var options by remember(storage) { mutableStateOf(storage.read()) }
    var chosen by remember { mutableStateOf(LocalDate.now(options.place.zone)) }
    var now by remember { mutableStateOf(Instant.now()) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(PrayerDisplayMode.COUNTDOWN) }
    var selectedKind by remember { mutableStateOf(PrayerKind.FAJR) }
    var locationError by remember { mutableStateOf<String?>(null) }
    val requestNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) PrayerAlarmScheduler.sync(context, options)
    }
    var locateRequest by remember { mutableIntStateOf(0) }
    fun locate() { locateRequest++ }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else locationError = tr(language, "Геопозиция недоступна", "Location permission denied", "Joylashuvga ruxsat yo‘q", "Жойлашувга рухсат йўқ")
    }
    LaunchedEffect(locateRequest) {
        if (locateRequest == 0) return@LaunchedEffect
        locationError = null
        val fix = currentPrayerLocation(context)
        if (fix == null) {
            locationError = tr(language, "Не удалось получить геопозицию", "Location unavailable", "Joylashuv aniqlanmadi", "Жойлашув аниқланмади")
        } else {
            val city = PrayerPlace("custom", tr(language, "Моё местоположение", "Current location", "Joylashuvim", "Жойлашувим"), fix.latitude, fix.longitude, ZoneId.systemDefault().id)
            options = options.copy(place = city)
            chosen = LocalDate.now(city.zone)
            sheet = null
        }
    }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(1_000) } }
    LaunchedEffect(options) {
        storage.write(options)
        delay(350) // Avoid rescheduling 128 slots for each intermediate slider value.
        withContext(Dispatchers.Default) { PrayerAlarmScheduler.sync(context, options) }
    }
    val today = now.atZone(options.place.zone).toLocalDate()
    val schedule = remember(chosen, options.place, options.method, options.hanafi) { PrayerCalculator.calculate(chosen, options.place, options.method, options.hanafi) }
    val next = remember(now.epochSecond, options.place, options.method, options.hanafi) { PrayerCalculator.upcoming(now, options.place, options.method, options.hanafi) }
    val surface = MaterialTheme.colorScheme.surface
    val foreground = MaterialTheme.colorScheme.onSurface

    val todaySchedule = remember(today, options.place, options.method, options.hanafi) {
        PrayerCalculator.calculate(today, options.place, options.method, options.hanafi)
    }
    val active = todaySchedule.times.filter { it.key in fivePrayers && it.value <= now }.maxByOrNull { it.value }?.key
    val remaining = next?.let { Duration.between(now, it.second).seconds.coerceAtLeast(0) }
    val countdown = remaining?.let { "%02d:%02d:%02d".format(it / 3600, it / 60 % 60, it % 60) } ?: "--:--:--"
    Box(Modifier.fillMaxSize().background(Color(0xFF0A2924))) {
        Image(painterResource(wallpaperIds[options.wallpaper]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .51f), Color.Black.copy(alpha = .20f), Color.Black.copy(alpha = .40f)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .pointerInput(mode) {
                var dx = 0f; var dy = 0f
                detectDragGestures(onDragStart = { dx = 0f; dy = 0f }, onDragEnd = {
                    if (abs(dx) > 100.dp.toPx() && abs(dx) > abs(dy)) {
                        mode = if (mode == PrayerDisplayMode.QIBLA) PrayerDisplayMode.COUNTDOWN else PrayerDisplayMode.QIBLA
                    } else if (dy < -90.dp.toPx() && mode != PrayerDisplayMode.QIBLA) mode = PrayerDisplayMode.SCHEDULE
                    else if (dy > 90.dp.toPx() && mode == PrayerDisplayMode.SCHEDULE) mode = PrayerDisplayMode.COUNTDOWN
                }) { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
            }, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PrayerCircle(CupertinoSymbol.Close, tr(language, "Закрыть", "Close", "Yopish", "Ёпиш")) { chrome.back() }
                Column(Modifier.weight(1f).clickable { sheet = "city" }.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CupertinoIcon(CupertinoSymbol.Location, null, Modifier.size(11.dp), tint = Color.White)
                        Text(options.place.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        CupertinoIcon(CupertinoSymbol.ChevronDown, null, Modifier.size(9.dp), tint = Color.White)
                    }
                    val hijri = remember(today) { runCatching { HijrahDate.from(today).format(DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale)) }.getOrNull() }
                    hijri?.let { Text(it, color = Color.White.copy(alpha = .72f), fontSize = 10.sp) }
                }
                PrayerCircle(CupertinoSymbol.Sliders, tr(language, "Настройки", "Settings", "Sozlamalar", "Созламалар")) { sheet = "options" }
            }
            if (options.place.id in listOf("makkah", "madinah")) {
                Spacer(Modifier.height(13.dp))
                Row(Modifier.widthIn(max = 250.dp).clip(CircleShape).background(Color.Black.copy(alpha = .20f)).padding(4.dp)) {
                    listOf("makkah", "madinah").forEach { id ->
                        val preset = PrayerPlace.presets.first { it.id == id }
                        Box(Modifier.weight(1f).clip(CircleShape).background(if (id == options.place.id) Color.White.copy(alpha = .20f) else Color.Transparent)
                            .clickable { options = options.copy(place = preset); chosen = LocalDate.now(preset.zone) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(if (id == "makkah") tr(language, "Мекка", "Makkah", "Makka", "Макка") else tr(language, "Медина", "Madinah", "Madina", "Мадина"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (mode) {
                    PrayerDisplayMode.COUNTDOWN -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr(language, "Следующий намаз", "Next prayer", "Keyingi namoz", "Кейинги намоз") + " · " + (next?.let { prayerName(it.first, language) + " " + clockText(it.second, options.place) } ?: "—"), color = Color.White.copy(alpha = .9f), fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center)
                        BoxWithConstraints { Text(countdown, color = Color.White, fontSize = (maxWidth.value / 5.2f).coerceIn(30f, 60f).sp, fontWeight = FontWeight.Light, maxLines = 1, fontFamily = FontFamily.Monospace) }
                        Text(tr(language, "До следующего намаза", "Until the next prayer", "Keyingi namozgacha", "Кейинги намозгача"), color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                    }
                    PrayerDisplayMode.SCHEDULE -> Column(Modifier.widthIn(max = 350.dp).fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color.Black.copy(alpha = .43f)).border(.8.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(17.dp))) {
                        sixRows.forEach { kind ->
                            todaySchedule.at(kind)?.let { at ->
                                Row(Modifier.fillMaxWidth().height(42.dp).background(if (kind == active) Color(0xFF0A9669).copy(alpha = .72f) else Color.Transparent).clickable { selectedKind = kind; sheet = "alert" }.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(prayerName(kind, language), color = Color.White, fontSize = 14.sp, fontWeight = if (kind == active) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                                    if (options.alerts[kind]?.enabled == true) CupertinoIcon(CupertinoSymbol.BellFill, null, Modifier.padding(end = 8.dp).size(12.dp), tint = Color.White.copy(alpha = .7f))
                                    Text(clockText(at, options.place), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        next?.let { (kind, _) -> Row(Modifier.fillMaxWidth().height(36.dp).background(Color(0xFF058F63)).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) { Text(prayerName(kind, language), Modifier.weight(1f), color = Color.White, fontSize = 12.sp); Text(countdown, color = Color.White, fontSize = 13.sp) } }
                    }
                    PrayerDisplayMode.QIBLA -> PrayerQiblaCompass(language)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                PrayerCircle(CupertinoSymbol.Location, tr(language, "Кибла", "Qibla", "Qibla", "Қибла")) { mode = if (mode == PrayerDisplayMode.QIBLA) PrayerDisplayMode.COUNTDOWN else PrayerDisplayMode.QIBLA }
                if (mode != PrayerDisplayMode.QIBLA) {
                    Box(Modifier.height(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = .20f)).clickable {
                        if (mode == PrayerDisplayMode.SCHEDULE) { chosen = today; sheet = "schedule" } else mode = PrayerDisplayMode.SCHEDULE
                    }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                        Text(if (mode == PrayerDisplayMode.SCHEDULE) tr(language, "Календарь", "Full schedule", "Taqvim", "Тақвим") else tr(language, "Расписание", "Prayer times", "Jadval", "Жадвал"), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                } else Spacer(Modifier.width(1.dp))
                PrayerCircle(CupertinoSymbol.Ellipsis, tr(language, "Ещё", "More", "Yana", "Яна")) { sheet = "options" }
            }
        }
    }

    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            when (sheet) {
                "options" -> {
                    listOf("alert" to tr(language, "Уведомления о намазе", "Prayer notifications", "Namoz eslatmalari", "Намоз эслатмалари"),
                        "city" to tr(language, "Город и местоположение", "City and location", "Shahar va joylashuv", "Шаҳар ва жойлашув"),
                        "method" to tr(language, "Метод расчёта", "Calculation method", "Hisoblash usuli", "Ҳисоблаш усули"),
                        "wallpaper" to tr(language, "Обои", "Wallpaper", "Fon rasmi", "Фон расми")).forEach { (key, title) ->
                        TextButton(onClick = { if (key == "alert") selectedKind = PrayerKind.FAJR; sheet = key }, modifier = Modifier.fillMaxWidth()) { Text(title) }
                    }
                }
                "schedule" -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(chosen.format(DateTimeFormatter.ofPattern("d MMM yyyy", language.locale)), color = foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    PrayerDateButton("‹") { chosen = chosen.minusDays(1) }
                    Spacer(Modifier.width(6.dp))
                    PrayerDateButton(tr(language, "Сегодня", "Today", "Bugun", "Бугун"), enabled = chosen != today) { chosen = today }
                    Spacer(Modifier.width(6.dp))
                    PrayerDateButton("›") { chosen = chosen.plusDays(1) }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(surface.copy(alpha = .97f))) {
                    sixRows.forEachIndexed { index, kind ->
                        val at = schedule.at(kind)
                        if (at != null) {
                            PrayerEntry(kind, language, clockText(at, options.place), options.alerts[kind]?.enabled == true) {
                                selectedKind = kind; sheet = "alert"
                            }
                            if (index != sixRows.lastIndex) HorizontalDivider(Modifier.padding(start = 78.dp), color = foreground.copy(alpha = .09f))
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr(language, "Дополнительные молитвы", "Additional prayers", "Qo‘shimcha namozlar", "Қўшимча намозлар"), color = foreground, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    listOf(PrayerKind.DUHA, PrayerKind.TAHAJJUD).forEach { kind ->
                        val at = schedule.at(kind)
                        if (at != null) {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(surface.copy(alpha = .97f)).clickable { selectedKind = kind; sheet = "alert" }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CupertinoIcon(symbol(kind), null, Modifier.size(27.dp), tint = tint(kind))
                                Spacer(Modifier.width(15.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(prayerName(kind, language), color = foreground, fontWeight = FontWeight.SemiBold)
                                    Text(if (kind == PrayerKind.DUHA) tr(language, "После восхода", "After sunrise", "Quyosh chiqqandan keyin", "Қуёш чиққандан кейин") else tr(language, "Последняя треть ночи", "Last third of the night", "Tunning oxirgi uchdan biri", "Туннинг охирги учдан бири"), color = foreground.copy(alpha = .62f), fontSize = 12.sp)
                                }
                                Text(clockText(at, options.place), color = foreground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                Text("${options.method.title} · ${if (options.hanafi) "Hanafi Asr" else "Standard Asr"}", color = foreground.copy(alpha = .65f), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(tr(language, "Офлайн-расчёт по координатам и часовому поясу. Расписание местной мечети может отличаться; проверяйте во время поездки. Напоминания зависят от разрешений Android.", "Calculated offline using coordinates and time zone. Local mosque timetables may differ. Android notification permission is required for reminders.", "Koordinata va vaqt mintaqasi bo‘yicha oflayn hisoblanadi. Mahalliy masjid jadvali farq qilishi mumkin. Eslatmalar uchun Android ruxsati kerak.", "Координата ва вақт минтақаси бўйича офлайн ҳисобланади. Маҳаллий масжид жадвали фарқ қилиши мумкин. Эслатмалар учун Android рухсати керак."), color = foreground.copy(alpha = .58f), fontSize = 12.sp, lineHeight = 18.sp)
            }
                }
                "city" -> {
                    var filter by remember { mutableStateOf("") }
                    Text(tr(language, "Выберите город", "Choose a city", "Shahar tanlash", "Шаҳар танлаш"), Modifier.padding(horizontal = 22.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth().padding(18.dp), label = { Text(tr(language, "Поиск города", "Search city", "Shahar qidirish", "Шаҳар қидириш")) }, singleLine = true, shape = RoundedCornerShape(18.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), contentPadding = PaddingValues(horizontal = 18.dp)) {
                        items(PrayerPlace.presets.filter { it.name.contains(filter, ignoreCase = true) }) { city ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { options = options.copy(place = city); chosen = LocalDate.now(city.zone); sheet = null }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(city.name, Modifier.weight(1f), fontSize = 17.sp)
                                if (city.id == options.place.id) CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(20.dp), tint = Color(0xFFE58931))
                            }
                        }
                    }
                    Text(tr(language, "Другой город: выберите из списка или используйте геопозицию. Для точного времени необходим правильный часовой пояс.", "Choose a city above or use current location. Its time zone must be correct for accurate prayer times.", "Ro‘yxatdan shaharni tanlang yoki joylashuvdan foydalaning. Vaqt mintaqasi to‘g‘ri bo‘lishi kerak.", "Рўйхатдан шаҳарни танланг ёки жойлашувдан фойдаланинг. Вақт минтақаси тўғри бўлиши керак."), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 12.sp)
                    TextButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            // Android LocationManager does not supply a location time zone.
                            // Current-zone fallback is explicit; preset cities retain authoritative zone IDs.
                            locationError = tr(language, "Для другого часового пояса выберите город из списка.", "For a different time zone, choose a city from the list.", "Boshqa vaqt mintaqasi uchun ro‘yxatdan shahar tanlang.", "Бошқа вақт минтақаси учун рўйхатдан шаҳар танланг.")
                            locate()
                        }
                    }, modifier = Modifier.padding(horizontal = 22.dp)) {
                        Text(tr(language, "Использовать геопозицию", "Use current location", "Joylashuvim", "Жойлашувим"))
                    }
                    locationError?.let { Text(it, Modifier.padding(horizontal = 22.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                }
                "method" -> {
                    Text(tr(language, "Метод расчёта", "Calculation method", "Hisoblash usuli", "Ҳисоблаш усули"), Modifier.padding(horizontal = 22.dp, vertical = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    PrayerMethod.entries.forEach { method ->
                        Row(Modifier.fillMaxWidth().clickable { options = options.copy(method = method) }.padding(horizontal = 22.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(method.title, Modifier.weight(1f)); RadioButton(options.method == method, onClick = { options = options.copy(method = method) })
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Hanafi Asr", Modifier.weight(1f)); Switch(options.hanafi, onCheckedChange = { options = options.copy(hanafi = it) })
                    }
                }
                "wallpaper" -> {
                    Text(tr(language, "Изменить обои", "Change wallpaper", "Fon rasmini o‘zgartirish", "Фон расмини ўзгартириш"), Modifier.padding(horizontal = 22.dp, vertical = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp), contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(wallpaperIds.size) { index ->
                            Column(Modifier.clip(RoundedCornerShape(19.dp)).clickable { options = options.copy(wallpaper = index); sheet = null }) {
                                Box(Modifier.fillMaxWidth().height(135.dp)) {
                                    Image(painterResource(wallpaperIds[index]), wallpaperNames[index], Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    if (index == options.wallpaper) Box(Modifier.fillMaxSize().border(3.dp, Color(0xFFFFA13A), RoundedCornerShape(19.dp)))
                                }
                                Text(wallpaperNames[index], Modifier.padding(6.dp), fontSize = 13.sp)
                            }
                        }
                    }
                }
                "alert" -> {
                    LazyRowPrayerSelector(selectedKind, language) { selectedKind = it }
                    var draft by remember { mutableStateOf(options.alerts) }
                    var previewMessage by remember { mutableStateOf("") }
                    val current = draft[selectedKind] ?: PrayerAlert()
                    fun modify(value: PrayerAlert) { draft = draft + (selectedKind to value) }
                    Text(prayerName(selectedKind, language), Modifier.padding(horizontal = 22.dp, vertical = 14.dp), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "Уведомление", "Notification", "Bildirishnoma", "Билдиришнома"), Modifier.weight(1f))
                        Switch(current.enabled, onCheckedChange = { enabled ->
                            modify(current.copy(enabled = enabled))
                        })
                    }
                    if (current.enabled) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr(language, "Звук", "Sound", "Ovoz", "Овоз"), Modifier.weight(1f)); Switch(current.sound, onCheckedChange = { modify(current.copy(sound = it)) })
                        }
                        Text(tr(language, "Сдвиг, минут", "Offset, minutes", "Vaqt farqi, daqiqa", "Вақт фарқи, дақиқа") + ": ${current.offset}", Modifier.padding(horizontal = 22.dp), fontSize = 14.sp)
                        Slider(current.offset.toFloat(), { modify(current.copy(offset = it.toInt())) }, Modifier.padding(horizontal = 22.dp), valueRange = -30f..30f, steps = 59)
                        Text(tr(language, "Заранее напомнить", "Early reminder", "Oldindan eslatish", "Олдиндан эслатиш"), Modifier.padding(horizontal = 22.dp), fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0, 5, 10, 15, 30).forEach { lead ->
                                FilterChip(selected = current.earlyMinutes == lead, onClick = { modify(current.copy(earlyMinutes = lead)) }, label = { Text(if (lead == 0) "Off" else "$lead m", fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                            }
                        }
                        Text(tr(language, "Android может задерживать уведомления ради экономии энергии.", "Android may delay alerts to save battery.", "Android batareya tejash uchun eslatmani kechiktirishi mumkin.", "Android батарея тежаш учун эслатмани кечиктириши мумкин."), Modifier.padding(horizontal = 22.dp), fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        val ok = PrayerAlarmScheduler.preview(context, selectedKind, current.sound)
                        previewMessage = if (ok) tr(language, "Тест запланирован", "Test scheduled", "Sinov rejalashtirildi", "Синов режалаштирилди") else tr(language, "Разрешите уведомления в настройках", "Enable notifications in settings", "Bildirishnomalarga ruxsat bering", "Билдиришномаларга рухсат беринг")
                    }, modifier = Modifier.fillMaxWidth()) { Text(tr(language,"Тестовое уведомление","Test notification","Sinov bildirishnomasi","Синов билдиришномаси")) }
                    if (previewMessage.isNotBlank()) Text(previewMessage, Modifier.padding(horizontal = 22.dp), fontSize = 12.sp)
                    Button(onClick = {
                        options = options.copy(alerts = draft)
                        storage.write(options)
                        if (draft.values.any { it.enabled } && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                        sheet = null
                    }, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp)) { Text(tr(language,"Сохранить","Save","Saqlash","Сақлаш")) }
                }
            }
            Spacer(Modifier.height(28.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun PrayerCircle(icon: CupertinoSymbol, label: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        CupertinoIcon(icon, label, Modifier.size(21.dp), tint = Color.White)
    }
}

@Composable
private fun PrayerDateButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.height(38.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
        Text(title, fontSize = 14.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .4f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrayerEntry(kind: PrayerKind, lang: AppLanguage, time: String, enabled: Boolean, onClick: () -> Unit) {
    val foreground = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 77.dp).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(tint(kind).copy(alpha = .09f)), contentAlignment = Alignment.Center) {
            CupertinoIcon(symbol(kind), null, Modifier.size(21.dp), tint = tint(kind))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(prayerName(kind, lang), color = foreground, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(if (kind == PrayerKind.SUNRISE) tr(lang, "Не намаз · время восхода", "Not a prayer · sunrise time", "Namoz emas · quyosh chiqishi", "Намоз эмас · қуёш чиқиши") else tr(lang, "Напоминание", "Configure reminder", "Eslatmani sozlash", "Эслатмани созлаш"), color = foreground.copy(alpha = .55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            CupertinoIcon(if (enabled) CupertinoSymbol.Bell else CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), tint = if (enabled) Color(0xFFEC9338) else foreground.copy(alpha = .45f))
        }
    }
}

@Composable
private fun LazyRowPrayerSelector(selected: PrayerKind, language: AppLanguage, onSelect: (PrayerKind) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(PrayerKind.entries) { kind -> FilterChip(selected = kind == selected, onClick = { onSelect(kind) }, label = { Text(prayerName(kind, language)) }) }
    }
}
