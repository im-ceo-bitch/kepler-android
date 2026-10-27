package com.iumrah.beta.ui.booking

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Android parity for iOS JourneyTravelInfoView (BOOKING_TRAVEL_INFO_20261004). */
private enum class IumrahHolyCity(
    val raw: String,
    val latitude: Double,
    val longitude: Double,
) {
    MAKKAH("makkah", 21.4225, 39.8262),
    MADINAH("madinah", 24.4672, 39.6111);

    val zone: ZoneId get() = ZoneId.of("Asia/Riyadh")

    fun title(language: AppLanguage): String = when (this) {
        MAKKAH -> when (language) {
            AppLanguage.RUSSIAN -> "Мекка"
            AppLanguage.ENGLISH -> "Makkah"
            AppLanguage.UZBEK -> "Makka"
            AppLanguage.UZBEK_CYRILLIC -> "Макка"
        }
        MADINAH -> when (language) {
            AppLanguage.RUSSIAN -> "Медина"
            AppLanguage.ENGLISH -> "Madinah"
            AppLanguage.UZBEK -> "Madina"
            AppLanguage.UZBEK_CYRILLIC -> "Мадина"
        }
    }
}

private enum class TravelInfoPage { PRAYER, WEATHER, CLOCKS }

private data class PrayerMoment(
    val id: String,
    val name: String,
    val time: String,
    val epochMillis: Long,
) {
    fun title(language: AppLanguage): String = when (id) {
        "fajr" -> if (language == AppLanguage.RUSSIAN) "Фаджр" else "Fajr"
        "dhuhr" -> if (language == AppLanguage.RUSSIAN) "Зухр" else "Dhuhr"
        "asr" -> if (language == AppLanguage.RUSSIAN) "Аср" else "Asr"
        "maghrib" -> if (language == AppLanguage.RUSSIAN) "Магриб" else "Maghrib"
        "isha" -> if (language == AppLanguage.RUSSIAN) "Иша" else "Isha"
        else -> name
    }

    fun shortTitle(language: AppLanguage): String = when (id) {
        "maghrib" -> if (language == AppLanguage.RUSSIAN) "Магр." else "Magh."
        else -> title(language)
    }
}

private data class PrayerDay(
    val city: IumrahHolyCity,
    val dateKey: String,
    val prayers: List<PrayerMoment>,
    val sunrise: String?,
    val tomorrowFajr: PrayerMoment?,
    val cachedAtMillis: Long,
) {
    fun nextPrayer(nowMillis: Long): PrayerMoment? =
        prayers.firstOrNull { it.epochMillis > nowMillis }
            ?: tomorrowFajr?.takeIf { it.epochMillis > nowMillis }
}

private enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SHOWERS, THUNDER, SNOW, UNKNOWN;

    fun localized(language: AppLanguage): String = when (this) {
        CLEAR -> tr(language, "Ясно", "Clear", "Ochiq", "Очиқ")
        PARTLY_CLOUDY -> tr(language, "Переменная облачность", "Partly cloudy", "Qisman bulutli", "Қисман булутли")
        CLOUDY -> tr(language, "Облачно", "Cloudy", "Bulutli", "Булутли")
        FOG -> tr(language, "Туман", "Fog", "Tuman", "Туман")
        RAIN, SHOWERS -> tr(language, "Дождь", "Rain", "Yomg‘ir", "Ёмғир")
        THUNDER -> tr(language, "Гроза", "Thunderstorm", "Momaqaldiroq", "Момақалдироқ")
        SNOW -> tr(language, "Снег", "Snow", "Qor", "Қор")
        UNKNOWN -> tr(language, "Погода", "Weather", "Ob-havo", "Об-ҳаво")
    }

    companion object {
        fun fromMet(raw: String?): WeatherCondition {
            val value = raw?.lowercase(Locale.US).orEmpty()
            return when {
                "thunder" in value -> THUNDER
                "snow" in value || "sleet" in value -> SNOW
                "rainshowers" in value || "showers" in value -> SHOWERS
                "rain" in value -> RAIN
                "fog" in value -> FOG
                "partlycloudy" in value || "fair" in value -> PARTLY_CLOUDY
                "cloudy" in value -> CLOUDY
                "clearsky" in value -> CLEAR
                else -> UNKNOWN
            }
        }
    }
}

private data class WeatherDay(
    val date: LocalDate,
    val high: Double,
    val low: Double,
    val condition: WeatherCondition,
)

private data class WeatherSnapshot(
    val city: IumrahHolyCity,
    val currentTemperature: Double,
    val currentCondition: WeatherCondition,
    val days: List<WeatherDay>,
    val fetchedAtMillis: Long,
)

private data class TravelInfoState(
    val prayers: PrayerDay? = null,
    val weather: WeatherSnapshot? = null,
    val refreshing: Boolean = false,
    val unavailable: Boolean = false,
)

private class JourneyTravelInfoService(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("iumrah_travel_info", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .callTimeout(14, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun load(city: IumrahHolyCity, force: Boolean): TravelInfoState = coroutineScope {
        val prayer = async { runCatching { loadPrayers(city, force) }.getOrNull() }
        val weather = async { runCatching { loadWeather(city, force) }.getOrNull() }
        val p = prayer.await()
        val w = weather.await()
        TravelInfoState(prayers = p, weather = w, unavailable = p == null && w == null)
    }

    private suspend fun loadPrayers(city: IumrahHolyCity, force: Boolean): PrayerDay {
        val today = LocalDate.now(city.zone)
        val key = "prayers_${city.raw}_${today}"
        val cached = prefs.getString(key, null)
        if (!force && cached != null) {
            runCatching { return decodePrayerCache(city, cached) }
        }

        return try {
            val todayRaw = fetchPrayerJson(city, today)
            val tomorrowRaw = fetchPrayerJson(city, today.plusDays(1))
            val payload = JSONObject()
                .put("date", today.toString())
                .put("today", todayRaw)
                .put("tomorrow", tomorrowRaw)
                .put("cachedAt", System.currentTimeMillis())
                .toString()
            prefs.edit().putString(key, payload).apply()
            decodePrayerCache(city, payload)
        } catch (error: Throwable) {
            if (cached != null) decodePrayerCache(city, cached) else throw error
        }
    }

    private suspend fun fetchPrayerJson(city: IumrahHolyCity, date: LocalDate): JSONObject = withContext(Dispatchers.IO) {
        val dateString = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US))
        val url = buildString {
            append("https://api.aladhan.com/v1/timings/")
            append(dateString)
            append("?latitude=")
            append(String.format(Locale.US, "%.5f", city.latitude))
            append("&longitude=")
            append(String.format(Locale.US, "%.5f", city.longitude))
            append("&method=4&school=0")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "iumrah-Android/2.0 (https://iumrah.app)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Prayer API ${response.code}")
            JSONObject(response.body.string())
        }
    }

    private fun decodePrayerCache(city: IumrahHolyCity, raw: String): PrayerDay {
        val root = JSONObject(raw)
        val today = LocalDate.parse(root.getString("date"))
        val tomorrow = today.plusDays(1)
        val todayTimings = root.getJSONObject("today").getJSONObject("data").getJSONObject("timings")
        val tomorrowTimings = root.getJSONObject("tomorrow").getJSONObject("data").getJSONObject("timings")
        fun clean(value: String): String = value.substringBefore(" ").trim()
        fun moment(id: String, name: String, time: String, date: LocalDate): PrayerMoment? {
            val parsed = runCatching { LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm", Locale.US)) }.getOrNull() ?: return null
            val epoch = ZonedDateTime.of(date, parsed, city.zone).toInstant().toEpochMilli()
            return PrayerMoment(id, name, time, epoch)
        }
        val entries = listOf(
            Triple("fajr", "Fajr", clean(todayTimings.getString("Fajr"))),
            Triple("dhuhr", "Dhuhr", clean(todayTimings.getString("Dhuhr"))),
            Triple("asr", "Asr", clean(todayTimings.getString("Asr"))),
            Triple("maghrib", "Maghrib", clean(todayTimings.getString("Maghrib"))),
            Triple("isha", "Isha", clean(todayTimings.getString("Isha"))),
        )
        val prayers = entries.mapNotNull { (id, name, time) -> moment(id, name, time, today) }
        val tomorrowFajrTime = clean(tomorrowTimings.getString("Fajr"))
        return PrayerDay(
            city = city,
            dateKey = today.toString(),
            prayers = prayers,
            sunrise = clean(todayTimings.optString("Sunrise")).takeIf { it.isNotBlank() },
            tomorrowFajr = moment("fajr", "Fajr", tomorrowFajrTime, tomorrow),
            cachedAtMillis = root.optLong("cachedAt", System.currentTimeMillis()),
        )
    }

    private suspend fun loadWeather(city: IumrahHolyCity, force: Boolean): WeatherSnapshot {
        val key = "weather_${city.raw}"
        val raw = prefs.getString(key, null)
        val cachedAt = prefs.getLong("${key}_at", 0L)
        if (!force && raw != null && System.currentTimeMillis() - cachedAt < 30 * 60 * 1000L) {
            runCatching { return decodeWeather(city, raw, cachedAt) }
        }

        return try {
            val fresh = fetchWeatherJson(city)
            val now = System.currentTimeMillis()
            prefs.edit().putString(key, fresh.toString()).putLong("${key}_at", now).apply()
            decodeWeather(city, fresh.toString(), now)
        } catch (error: Throwable) {
            if (raw != null) decodeWeather(city, raw, cachedAt) else throw error
        }
    }

    private suspend fun fetchWeatherJson(city: IumrahHolyCity): JSONObject = withContext(Dispatchers.IO) {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" +
            String.format(Locale.US, "%.4f", city.latitude) + "&lon=" + String.format(Locale.US, "%.4f", city.longitude)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "iumrah-Android/2.0 (https://iumrah.app)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("MET API ${response.code}")
            JSONObject(response.body.string())
        }
    }

    private fun decodeWeather(city: IumrahHolyCity, raw: String, fetchedAt: Long): WeatherSnapshot {
        val series = JSONObject(raw).getJSONObject("properties").getJSONArray("timeseries")
        if (series.length() == 0) throw IOException("Empty weather payload")
        val first = series.getJSONObject(0)
        val currentData = first.getJSONObject("data")
        val currentTemp = currentData.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
        val currentCondition = WeatherCondition.fromMet(periodSymbol(currentData))

        data class Sample(val instant: Instant, val temp: Double, val symbol: String?)
        val samples = buildList {
            for (index in 0 until series.length()) {
                val item = series.getJSONObject(index)
                val instant = runCatching { Instant.parse(item.getString("time")) }.getOrNull() ?: continue
                val data = item.getJSONObject("data")
                val temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
                add(Sample(instant, temp, periodSymbol(data)))
            }
        }
        val grouped = samples.groupBy { it.instant.atZone(city.zone).toLocalDate() }
        val days = grouped.keys.sorted().take(7).mapNotNull { date ->
            val values = grouped[date].orEmpty()
            if (values.isEmpty()) return@mapNotNull null
            val high = values.maxOf { it.temp }
            val low = values.minOf { it.temp }
            val representative = values.minByOrNull { abs(it.instant.atZone(city.zone).hour - 12) }
            WeatherDay(date, high, low, WeatherCondition.fromMet(representative?.symbol))
        }
        return WeatherSnapshot(city, currentTemp, currentCondition, days, fetchedAt)
    }

    private fun periodSymbol(data: JSONObject): String? {
        val one = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
        if (!one.isNullOrBlank()) return one
        return data.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code")
    }
}

@Composable
fun JourneyTravelInfoPanel(language: AppLanguage, initialPage: Int = 0) {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("iumrah_travel_info", Context.MODE_PRIVATE) }
    val service = remember { JourneyTravelInfoService(context) }
    val scope = rememberCoroutineScope()
    var city by remember {
        mutableStateOf(
            runCatching { IumrahHolyCity.valueOf(prefs.getString("selected_city", IumrahHolyCity.MAKKAH.name)!!) }
                .getOrDefault(IumrahHolyCity.MAKKAH),
        )
    }
    var state by remember { mutableStateOf(TravelInfoState(refreshing = true)) }
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, TravelInfoPage.entries.lastIndex), pageCount = { TravelInfoPage.entries.size })

    suspend fun refresh(force: Boolean) {
        state = state.copy(refreshing = true, unavailable = false)
        val loaded = service.load(city, force)
        state = loaded.copy(refreshing = false)
    }

    LaunchedEffect(city) { refresh(force = false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HolyCityPicker(
            language = language,
            selected = city,
            onSelect = { selected ->
                if (selected != city) {
                    city = selected
                    prefs.edit().putString("selected_city", selected.name).apply()
                }
            },
            refreshing = state.refreshing,
            onRefresh = { scope.launch { refresh(force = true) } },
        )

        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            pageSpacing = 10.dp,
        ) { page ->
            when (TravelInfoPage.entries[page]) {
                TravelInfoPage.PRAYER -> PrayerTimesCard(language, city, state.prayers, state.refreshing)
                TravelInfoPage.WEATHER -> WeatherForecastCard(language, city, state.weather, state.refreshing)
                TravelInfoPage.CLOCKS -> DualWorldClockCard(language, city)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TravelInfoPage.entries.forEachIndexed { index, _ ->
                val active = pager.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(if (active) 22.dp else 7.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)),
                )
            }
        }
    }
}

@Composable
private fun HolyCityPicker(
    language: AppLanguage,
    selected: IumrahHolyCity,
    onSelect: (IumrahHolyCity) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IumrahHolyCity.entries.forEach { city ->
            val active = city == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(city) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    city.title(language),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 3.dp)
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !refreshing) { onRefresh() },
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.8.dp)
            } else {
                CupertinoIcon(CupertinoSymbol.Refresh, null, Modifier.size(17.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TravelInfoCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        accent.copy(alpha = 0.055f),
                    ),
                ),
            )
            .border(0.75.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.085f), shape)
            .padding(19.dp),
    ) { content() }
}

@Composable
private fun PrayerTimesCard(
    language: AppLanguage,
    city: IumrahHolyCity,
    day: PrayerDay?,
    refreshing: Boolean,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val next = day?.nextPrayer(now)
    TravelInfoCard(accent = Color(0xFF34C759)) {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CupertinoIcon(CupertinoSymbol.MoonStarsFill, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            tr(language, "ВРЕМЕНА МОЛИТВ", "PRAYER TIMES", "NAMOZ VAQTLARI", "НАМОЗ ВАҚТЛАРИ"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(city.title(language), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                }
                if (refreshing && day == null) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.8.dp)
                } else if (next != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(next.title(language), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                        Text(next.time, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (day != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    day.prayers.forEach { prayer ->
                        PrayerCell(language, prayer, prayer.id == next?.id, Modifier.weight(1f))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(94.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)),
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr(language, "Umm al-Qura · время Саудии", "Umm al-Qura · Saudi time", "Umm al-Qura · Saudiya vaqti", "Umm al-Qura · Саудия вақти"),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                day?.sunrise?.let { sunrise ->
                    CupertinoIcon(CupertinoSymbol.SunMaxFill, null, Modifier.size(12.dp), Color(0xFFFF9500))
                    Spacer(Modifier.width(4.dp))
                    Text(sunrise, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (next != null) {
                val seconds = max(0L, (next.epochMillis - now) / 1000L)
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr(language, "СЛЕДУЮЩАЯ МОЛИТВА", "NEXT PRAYER", "KEYINGI NAMOZ", "КЕЙИНГИ НАМОЗ"),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(next.title(language), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "%02d:%02d:%02d".format(Locale.US, hours, minutes, secs),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (day == null && !refreshing) {
                Text(
                    tr(language, "Времена появятся после подключения к сети", "Prayer times appear when online", "Namoz vaqtlari internet bo‘lganda chiqadi", "Намоз вақтлари интернет бўлганда чиқади"),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PrayerCell(language: AppLanguage, prayer: PrayerMoment, active: Boolean, modifier: Modifier = Modifier) {
    val background = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
    val foreground = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .height(94.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(background)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        CupertinoIcon(prayerSymbol(prayer.id), null, Modifier.size(18.dp), if (active) foreground else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            prayer.shortTitle(language),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) foreground.copy(alpha = 0.90f) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(prayer.time, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = foreground, maxLines = 1)
    }
}

private fun prayerSymbol(id: String): CupertinoSymbol = when (id) {
    "fajr" -> CupertinoSymbol.Sun
    "dhuhr" -> CupertinoSymbol.SunMaxFill
    "asr" -> CupertinoSymbol.Sun
    "maghrib" -> CupertinoSymbol.HalfCircle
    else -> CupertinoSymbol.MoonStarsFill
}

@Composable
private fun WeatherForecastCard(
    language: AppLanguage,
    city: IumrahHolyCity,
    forecast: WeatherSnapshot?,
    refreshing: Boolean,
) {
    TravelInfoCard(accent = Color(0xFF007AFF)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherGlyph(WeatherCondition.PARTLY_CLOUDY, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            tr(language, "ПОГОДА · 7 ДНЕЙ", "WEATHER · 7 DAYS", "OB-HAVO · 7 KUN", "ОБ-ҲАВО · 7 КУН"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(city.title(language), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                if (forecast != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherGlyph(forecast.currentCondition, Modifier.size(36.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${forecast.currentTemperature.roundToInt()}°", fontSize = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                            Text(forecast.currentCondition.localized(language), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                } else if (refreshing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.8.dp)
                }
            }

            if (forecast != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    forecast.days.take(7).forEach { day ->
                        WeatherDayCell(language, city, day, Modifier.weight(1f))
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(105.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(CupertinoSymbol.Refresh, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(5.dp))
                Text(
                    if (forecast == null) {
                        tr(language, "Прогноз появится при подключении к сети", "Forecast appears when online", "Prognoz internet bo‘lganda chiqadi", "Прогноз интернет бўлганда чиқади")
                    } else "Weather data · MET Norway",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (forecast != null) {
                    val time = Instant.ofEpochMilli(forecast.fetchedAtMillis).atZone(city.zone).format(DateTimeFormatter.ofPattern("HH:mm", language.locale))
                    Text(
                        tr(language, "обновлено $time", "updated $time", "yangilandi $time", "янгиланди $time"),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherDayCell(language: AppLanguage, city: IumrahHolyCity, day: WeatherDay, modifier: Modifier = Modifier) {
    val today = LocalDate.now(city.zone)
    val label = if (day.date == today) {
        tr(language, "Сег.", "Today", "Bugun", "Бугун")
    } else {
        val locale = language.locale
        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }
    }
    Column(
        modifier = modifier
            .height(105.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        WeatherGlyph(day.condition, Modifier.size(20.dp))
        Text("${day.high.roundToInt()}°", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("${day.low.roundToInt()}°", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeatherGlyph(condition: WeatherCondition, modifier: Modifier = Modifier) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = when (condition) {
        WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.THUNDER -> Color(0xFFFFCC00)
        WeatherCondition.RAIN, WeatherCondition.SHOWERS -> Color(0xFF007AFF)
        WeatherCondition.SNOW -> Color(0xFF32ADE6)
        else -> onSurfaceVariant
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = (minOf(w, h) * 0.075f).coerceAtLeast(1.2f)
        when (condition) {
            WeatherCondition.CLEAR -> {
                drawCircle(primary, radius = minOf(w, h) * 0.20f, center = Offset(w * 0.5f, h * 0.5f))
                repeat(8) { index ->
                    val a = 2 * PI * index / 8
                    drawLine(
                        primary,
                        Offset(w * 0.5f + cos(a).toFloat() * w * 0.30f, h * 0.5f + sin(a).toFloat() * h * 0.30f),
                        Offset(w * 0.5f + cos(a).toFloat() * w * 0.42f, h * 0.5f + sin(a).toFloat() * h * 0.42f),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }
            else -> {
                if (condition == WeatherCondition.PARTLY_CLOUDY) {
                    drawCircle(Color(0xFFFFCC00), radius = minOf(w, h) * 0.18f, center = Offset(w * 0.35f, h * 0.35f))
                }
                val cloud = if (condition == WeatherCondition.THUNDER) Color(0xFF5856D6) else onSurfaceVariant
                drawCircle(cloud, radius = w * 0.18f, center = Offset(w * 0.37f, h * 0.56f))
                drawCircle(cloud, radius = w * 0.23f, center = Offset(w * 0.54f, h * 0.48f))
                drawCircle(cloud, radius = w * 0.17f, center = Offset(w * 0.70f, h * 0.58f))
                drawLine(cloud, Offset(w * 0.27f, h * 0.66f), Offset(w * 0.80f, h * 0.66f), stroke * 1.8f, StrokeCap.Round)
                if (condition == WeatherCondition.RAIN || condition == WeatherCondition.SHOWERS) {
                    repeat(3) { i ->
                        val x = w * (0.36f + i * 0.16f)
                        drawLine(primary, Offset(x, h * 0.76f), Offset(x - w * 0.04f, h * 0.90f), stroke, StrokeCap.Round)
                    }
                }
                if (condition == WeatherCondition.THUNDER) {
                    drawLine(primary, Offset(w * 0.55f, h * 0.72f), Offset(w * 0.47f, h * 0.86f), stroke * 1.4f, StrokeCap.Square)
                    drawLine(primary, Offset(w * 0.47f, h * 0.86f), Offset(w * 0.59f, h * 0.82f), stroke * 1.4f, StrokeCap.Square)
                }
                if (condition == WeatherCondition.SNOW) {
                    repeat(3) { i ->
                        drawCircle(primary, radius = stroke * 0.55f, center = Offset(w * (0.38f + i * 0.14f), h * 0.84f))
                    }
                }
                if (condition == WeatherCondition.FOG) {
                    repeat(2) { i ->
                        drawLine(primary, Offset(w * 0.28f, h * (0.78f + i * 0.10f)), Offset(w * 0.76f, h * (0.78f + i * 0.10f)), stroke, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun DualWorldClockCard(language: AppLanguage, city: IumrahHolyCity) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val instant = Instant.ofEpochMilli(now)
    val localZone = ZoneId.systemDefault()
    val localCity = remember {
        TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ').ifBlank {
            tr(language, "Ваш город", "Your city", "Shahringiz", "Шаҳрингиз")
        }
    }
    TravelInfoCard(accent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(CupertinoSymbol.Clock, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(5.dp))
                Text(
                    tr(language, "МИРОВОЕ ВРЕМЯ", "WORLD CLOCK", "DUNYO VAQTI", "ДУНЁ ВАҚТИ"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ClockColumn(city.title(language), instant, city.zone, Color(0xFF34C759), Modifier.weight(1f))
                ClockColumn(localCity, instant, localZone, Color(0xFF007AFF), Modifier.weight(1f))
            }
            Column {
                Row {
                    Text("${city.title(language)} · ${gmtLabel(city.zone, instant)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("$localCity · ${gmtLabel(localZone, instant)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    offsetText(language, city.zone, localZone, instant),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ClockColumn(
    title: String,
    instant: Instant,
    zone: ZoneId,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val time = instant.atZone(zone)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnalogTravelClock(time, accent, Modifier.size(112.dp))
        Spacer(Modifier.height(7.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(time.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnalogTravelClock(time: ZonedDateTime, accent: Color, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f
        drawCircle(onSurface.copy(alpha = 0.035f), radius - 1f, center)
        drawCircle(onSurface.copy(alpha = 0.10f), radius - 1f, center, style = Stroke(0.8.dp.toPx()))
        repeat(60) { index ->
            val angle = index / 60.0 * 2.0 * PI - PI / 2.0
            val major = index % 5 == 0
            val outer = radius - 7.dp.toPx()
            val inner = outer - if (major) 8.dp.toPx() else 3.dp.toPx()
            drawLine(
                onSurface.copy(alpha = if (major) 0.58f else 0.18f),
                Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                if (major) 1.5.dp.toPx() else 0.7.dp.toPx(),
                StrokeCap.Round,
            )
        }
        val hour = time.hour + time.minute / 60.0
        val minute = time.minute + time.second / 60.0
        val second = time.second.toDouble()
        fun hand(fraction: Double, length: Float, width: Float, color: Color) {
            val angle = fraction * 2 * PI - PI / 2
            drawLine(
                color,
                center,
                Offset(center.x + cos(angle).toFloat() * radius * length, center.y + sin(angle).toFloat() * radius * length),
                width.dp.toPx(),
                StrokeCap.Round,
            )
        }
        hand(hour / 12.0, 0.48f, 4.2f, onSurface)
        hand(minute / 60.0, 0.68f, 2.7f, onSurface)
        hand(second / 60.0, 0.72f, 1.2f, accent)
        drawCircle(accent, 3.5.dp.toPx(), center)
    }
}

private fun gmtLabel(zone: ZoneId, instant: Instant): String {
    val seconds = zone.rules.getOffset(instant).totalSeconds
    val minutes = seconds / 60
    val sign = if (minutes >= 0) "+" else "−"
    val absolute = abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return if (remainder == 0) "UTC$sign$hours" else "UTC$sign$hours:${"%02d".format(Locale.US, remainder)}"
}

private fun offsetText(language: AppLanguage, holyZone: ZoneId, localZone: ZoneId, instant: Instant): String {
    val holyMinutes = holyZone.rules.getOffset(instant).totalSeconds / 60
    val localMinutes = localZone.rules.getOffset(instant).totalSeconds / 60
    val delta = localMinutes - holyMinutes
    if (delta == 0) {
        return tr(language, "Одинаковое время", "Same time", "Vaqt bir xil", "Вақт бир хил")
    }
    val sign = if (delta > 0) "+" else "−"
    val absolute = abs(delta)
    val hours = absolute / 60
    val minutes = absolute % 60
    val formatted = if (minutes == 0) "$sign$hours h" else "$sign$hours:${"%02d".format(Locale.US, minutes)}"
    return tr(
        language,
        "Ваш город $formatted относительно Саудии",
        "Your city $formatted vs Saudi Arabia",
        "Shahringiz Saudiya vaqtiga nisbatan $formatted",
        "Шаҳрингиз Саудия вақтига нисбатан $formatted",
    )
}

private fun tr(language: AppLanguage, ru: String, en: String, uz: String, cyrl: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cyrl
}
