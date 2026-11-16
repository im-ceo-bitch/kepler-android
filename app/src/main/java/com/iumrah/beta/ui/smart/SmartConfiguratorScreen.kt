package com.iumrah.beta.ui.smart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iumrah.beta.R
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.smart.SmartConfiguratorService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.smart.*
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.ui.components.IumrahSignatureWorldBackground
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.hotels.nuiteeText
import com.iumrah.beta.ui.trip.ConfiguratorAirportSelector
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException

@Composable
internal fun SmartConfiguratorHomeCard(language: AppLanguage, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.Black).clickable(onClick = onOpen).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("iumrah Smart", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .15f)), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(18.dp), Color.White) }
        }
        SmartOrbitPreview(Modifier.fillMaxWidth().height(310.dp), true, CupertinoSymbol.Sparkles)
        Text(nuiteeText(language, "Расскажите о своих планах. Остальное сделаем мы.", "Tell us your plans. We'll do the rest.", "Rejalaringizni ayting. Qolganini biz qilamiz.", "Режаларингизни айтинг. Қолганини биз қиламиз."), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(nuiteeText(language, "Подберём рейсы, отели и услуги в один пакет.", "We'll match flights, hotels and services in one package.", "Reyslar, mehmonxonalar va xizmatlarni bitta paketga jamlaymiz.", "Рейслар, меҳмонхоналар ва хизматларни битта пакетга жамлаймиз."), color = Color.White.copy(alpha = .72f), fontSize = 15.sp)
        SmartAction(nuiteeText(language, "Собрать мою Умру", "Build my Umrah", "Umramni tayyorlash", "Умрамни тайёрлаш"), true, true, onOpen)
    }
}

/** Temporary shared motion surface; the SwiftUI Orbit V5 renderer is a separate parity task. */
@Composable private fun SmartOrbitPreview(modifier: Modifier, dark: Boolean, symbol: CupertinoSymbol) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        IumrahSignatureWorldBackground(Modifier.fillMaxSize(), mapOpacity = .58f)
        if (dark) Image(painterResource(R.drawable.iumrah_header_wordmark_dark), "iumrah", Modifier.width(125.dp))
        else Box(Modifier.size(84.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            CupertinoIcon(symbol, null, Modifier.size(40.dp), Color.White)
        }
    }
}

@Composable
internal fun SmartConfiguratorScreen(language: AppLanguage, airports: AirportSearchService, service: SmartConfiguratorService,
                                     resume: SmartDraft?, onSave: (SmartDraft) -> Unit, onClose: () -> Unit,
                                     onReview: (JourneyStore) -> Unit, onLanguage: (AppLanguage) -> Unit) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    var preferences by remember { mutableStateOf(resume?.preferences ?: SmartPreferences()) }
    var step by remember { mutableIntStateOf(resume?.step ?: 0) }
    var attempt by remember { mutableIntStateOf(if (resume?.step == 8) 1 else 0) }
    var progress by remember { mutableStateOf(SmartConfiguratorService.Progress.FLIGHTS) }
    var city by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<JourneyStore?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    val steps = SmartRules.steps(preferences.both)
    val index = steps.indexOf(step).coerceAtLeast(0)
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    LaunchedEffect(step, preferences, result != null) { onSave(SmartDraft(preferences, step, result != null)) }
    LaunchedEffect(step, attempt) {
        if (step != 8 || attempt == 0) return@LaunchedEffect
        busy = true; failure = null; result = null
        try { result = service.assemble(preferences) { stage, location -> progress = stage; city = location } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure = e.message ?: "SMART_QUOTE_UNAVAILABLE" }
        finally { busy = false }
    }
    val canContinue = when (step) {
        1 -> preferences.origin.matches(Regex("[A-Z]{3}")) && preferences.origin !in setOf("JED", "MED")
        6 -> preferences.valid()
        7 -> preferences.valid()
        8 -> result != null || failure != null
        else -> true
    }
    val title = when (step) {
        0 -> t("Ваша Умра. Ваш выбор.", "Your Umrah. Your choice.", "Sizning Umrangiz. Sizning tanlovingiz.", "Сизнинг Умрангиз. Сизнинг танловингиз.")
        1 -> t("Из какого города вы хотите вылететь?", "Where will you depart from?", "Qaysi shahardan uchmoqchisiz?", "Қайси шаҳардан учмоқчисиз?")
        2 -> t("В каком месяце вы планируете совершить Умру?", "Which month would you like to travel?", "Qaysi oyda Umra safarini rejalashtiryapsiz?", "Қайси ойда Умра сафарини режалаштиряпсиз?")
        3 -> t("На сколько дней вы планируете поездку?", "How long would you like to travel?", "Safarni necha kunga rejalashtiryapsiz?", "Сафарни неча кунга режалаштиряпсиз?")
        4 -> t("Какие города вы хотите посетить?", "Which cities would you like to visit?", "Qaysi shaharlarga bormoqchisiz?", "Қайси шаҳарларга бормоқчисиз?")
        5 -> t("С какого города начнём путешествие?", "Which city shall we visit first?", "Safarni qaysi shahardan boshlaymiz?", "Сафарни қайси шаҳардан бошлаймиз?")
        6 -> t("Сколько человек отправляется на Умру?", "Who is traveling with you?", "Umraga necha kishi boradi?", "Умрага неча киши боради?")
        7 -> t("Какой уровень комфорта вы предпочитаете?", "Choose your comfort level", "Qaysi qulaylik darajasini tanlaysiz?", "Қайси қулайлик даражасини танлайсиз?")
        else -> if (result != null) t("Ваша Умра собрана", "Your Umrah is ready", "Umrangiz tayyor", "Умрангиз тайёр") else t("Собираем вашу Умру", "Building your Umrah", "Umrangizni tayyorlayapmiz", "Умрангизни тайёрлаяпмиз")
    }
    val subtitle = when (step) {
        0 -> t("Расскажите о своих планах. Мы подберём рейсы, отели и услуги в едином пакете.", "Tell us your plans. We'll find flights, hotels and services in one package.", "Rejalaringizni ayting. Reyslar, mehmonxonalar va xizmatlarni bitta paketda topamiz.", "Режаларингизни айтинг. Рейслар, меҳмонхоналар ва хизматларни битта пакетда топамиз.")
        2 -> t("Точные даты подберём автоматически.", "We'll find the dates automatically.", "Aniq sanalarni avtomatik topamiz.", "Аниқ саналарни автоматик топамиз.")
        3 -> t("При необходимости расширим поиск на 3–4 дня.", "We may adjust the duration by 3–4 days to find flights.", "Zarur bo‘lsa, qidiruvni 3–4 kunga kengaytiramiz.", "Зарур бўлса, қидирувни 3–4 кунга кенгайтирамиз.")
        6 -> t("Возраст детей нужен для корректного тарифа.", "Children's ages help determine the right fare.", "To‘g‘ri tarif uchun bolalarning yoshi kerak.", "Тўғри тариф учун болаларнинг ёши керак.")
        8 -> t("Проверяем компоненты и рассчитываем стоимость на сервере.", "Checking components and calculating the server price.", "Qismlarni tekshirib, serverda narxni hisoblayapmiz.", "Қисмларни текшириб, серверда нархни ҳисоблаяпмиз.")
        else -> ""
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        SmartHeaderIcon(if (step == 0) CupertinoSymbol.Globe else CupertinoSymbol.ChevronLeft, !busy) {
                            if (step == 0) languageMenu = true else {
                                result = null; failure = null; step = steps[(index - 1).coerceAtLeast(0)]
                            }
                        }
                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                            listOf(AppLanguage.RUSSIAN, AppLanguage.UZBEK, AppLanguage.UZBEK_CYRILLIC, AppLanguage.ENGLISH).forEach { value ->
                                DropdownMenuItem(text = { Text(value.nativeTitle) }, onClick = { languageMenu = false; onLanguage(value) })
                            }
                        }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("iumrah Smart", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(t("Персональная поездка", "Personal journey", "Shaxsiy safar", "Шахсий сафар"), color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                    }
                    SmartHeaderIcon(CupertinoSymbol.Close, true, onClose)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEach { value -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = if (value <= step) 1f else .23f))) }
                    Text("${index + 1}/${steps.size}", color = Color.White, fontSize = 11.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val symbol = when (step) { 1 -> CupertinoSymbol.AirplaneTakeoff; 2 -> CupertinoSymbol.Calendar; 3 -> CupertinoSymbol.Clock; 4 -> CupertinoSymbol.MapFill; 5 -> CupertinoSymbol.AirplaneLand; 6 -> CupertinoSymbol.PersonsFill; 7 -> CupertinoSymbol.StarFill; else -> CupertinoSymbol.Sparkles }
                SmartOrbitPreview(Modifier.fillMaxWidth().height(if (step == 0 || step == 8) 300.dp else 210.dp), step == 0, symbol)
                Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                when (step) {
                    1 -> ConfiguratorAirportSelector(language, airports, preferences.airport, preferences.origin) { airport -> preferences = preferences.copy(origin = airport.iata, airport = airport) }
                    2 -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        (0..11).forEach { n -> val month = YearMonth.now().plusMonths(n.toLong()); SmartChoice(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag(language.localeTag))), month.toString() == preferences.month, Modifier.width(146.dp).height(100.dp)) { preferences = preferences.copy(month = month.toString()) } }
                    }
                    3 -> listOf(3, 7, 10, 14, 20, 25).chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { n -> SmartChoice("$n " + t("дн.", "days", "kun", "кун"), n == preferences.duration, Modifier.weight(1f)) { preferences = preferences.copy(duration = n) } } } }
                    4 -> { SmartChoice(t("Мекка", "Makkah", "Makka", "Макка") + " · JED → JED", !preferences.both) { preferences = preferences.copy(both = false) }; SmartChoice(t("Мекка и Медина", "Makkah & Madinah", "Makka va Madina", "Макка ва Мадина") + " · JED ↔ MED", preferences.both) { preferences = preferences.copy(both = true) } }
                    5 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmartChoice(t("Мекка", "Makkah", "Makka", "Макка") + " · JED", preferences.firstCity == "JED", Modifier.weight(1f)) { preferences = preferences.copy(firstCity = "JED") }
                        SmartChoice(t("Медина", "Madinah", "Madina", "Мадина") + " · MED", preferences.firstCity == "MED", Modifier.weight(1f)) { preferences = preferences.copy(firstCity = "MED") }
                    }
                    6 -> {
                        SmartCounter(t("Взрослые", "Adults", "Kattalar", "Катталар"), preferences.adults, preferences.adults > 1, preferences.adults + preferences.childAges.size < 6) { delta -> preferences = preferences.copy(adults = preferences.adults + delta) }
                        SmartCounter(t("Дети", "Children", "Bolalar", "Болалар"), preferences.childAges.size, preferences.childAges.isNotEmpty(), preferences.adults + preferences.childAges.size < 6) { delta -> preferences = preferences.copy(childAges = if (delta > 0) preferences.childAges + 6 else preferences.childAges.dropLast(1)) }
                        preferences.childAges.forEachIndexed { i, age -> SmartCounter(t("Возраст ребёнка", "Child age", "Bolaning yoshi", "Боланинг ёши") + " ${i+1}", age, age > 0, age < 11) { delta -> preferences = preferences.copy(childAges = preferences.childAges.mapIndexed { j, a -> if (i == j) a + delta else a }) } }
                    }
                    7 -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) { PackageTier.entries.forEach { tier -> SmartChoice(tierTitle(language, tier) + "\n" + tier.selectableHotelStars.sorted().joinToString("–") + " ★", preferences.tier == tier.wireValue, Modifier.width(222.dp).height(180.dp)) { preferences = preferences.copy(tier = tier.wireValue) } } }
                    8 -> {
                        val assembled = result?.state?.value
                        if (assembled != null) {
                            assembled.nuitee?.flights.orEmpty().forEach { f -> Text("${f.origin} → ${f.destination} · ${f.flightNumber}\n${f.departureAt}", fontWeight = FontWeight.Medium) }
                            assembled.nuitee?.rooms?.values?.forEach { room -> Text("${room.hotel.name} · ${room.rate.roomName}\n${room.checkin} — ${room.checkout}", fontWeight = FontWeight.Medium) }
                            Text(assembled.resolvedTransferVehicle.modelName)
                            assembled.quote?.let { Text("${it.totalPackagePrice.toPlainString()} ${it.currency}", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
                        } else if (failure != null) {
                            Text(if (failure == "SMART_AVIASALES_PARTY_FARE_UNAVAILABLE") t("Для семьи нет подходящих опубликованных рейсов. Выберите другой месяц или длительность.", "No suitable published flights for your family. Try another month or duration.", "Oilaviy safar uchun mos reyslar topilmadi.", "Оилавий сафар учун мос рейслар топилмади.") else t("Попробуйте другой месяц, длительность или категорию. Можно повторить поиск.", "Try another month, duration or category, or retry the search.", "Boshqa oy, muddat yoki toifani tanlang yoki qayta qidiring.", "Бошқа ой, муддат ёки тоифани танланг ёки қайта қидиринг."), color = MaterialTheme.colorScheme.error)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(when (progress) { SmartConfiguratorService.Progress.FLIGHTS -> t("Авиабилеты", "Flights", "Reyslar", "Рейслар"); SmartConfiguratorService.Progress.HOTELS -> t("Отели", "Hotels", "Mehmonxonalar", "Меҳмонхоналар") + (city?.let { " · $it" } ?: ""); SmartConfiguratorService.Progress.TRANSFER -> t("Трансфер", "Transfer", "Transfer", "Трансфер"); SmartConfiguratorService.Progress.PRICE -> t("Стоимость", "Price", "Narx", "Нарх"); SmartConfiguratorService.Progress.READY -> t("Готово", "Ready", "Tayyor", "Тайёр") })
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                val action = when { step == 8 && result != null -> t("Проверить пакет", "Review package", "Paketni tekshirish", "Пакетни текшириш"); step == 8 && failure != null -> t("Повторить поиск", "Try again", "Qayta qidirish", "Қайта қидириш"); step == 7 -> t("Собрать мою Умру", "Build my Umrah", "Umramni tayyorlash", "Умрамни тайёрлаш"); else -> t("Продолжить", "Continue", "Davom etish", "Давом этиш") }
                SmartAction(action, false, canContinue && !busy) {
                    if (step == 8) { result?.let(onReview) ?: run { attempt++ } }
                    else { step = steps[(index + 1).coerceAtMost(steps.lastIndex)]; if (step == 8) attempt++ }
                }
            }
        }
    }
}

@Composable private fun SmartHeaderIcon(symbol: CupertinoSymbol, enabled: Boolean, action: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(enabled = enabled, onClick = action), contentAlignment = Alignment.Center) { CupertinoIcon(symbol, null, Modifier.size(18.dp), Color.White.copy(alpha = if (enabled) 1f else .5f)) }
}
@Composable private fun SmartChoice(title: String, selected: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), action: () -> Unit) {
    val ink = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Box(modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(22.dp)).background(if (selected) Color.Black else MaterialTheme.colorScheme.surface).clickable(onClick = action).padding(15.dp), contentAlignment = Alignment.Center) { Text(title, color = ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}
@Composable private fun SmartCounter(label: String, value: Int, minus: Boolean, plus: Boolean, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label · $value", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { change(-1) }, enabled = minus) { Text("−") }
        TextButton(onClick = { change(1) }, enabled = plus) { Text("+") }
    }
}
@Composable private fun SmartAction(label: String, dark: Boolean, enabled: Boolean, action: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background((if (dark) Color.White else Color.Black).copy(alpha = if (enabled) 1f else .45f)).clickable(enabled = enabled, onClick = action), contentAlignment = Alignment.Center) { Text(label, color = if (dark) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
}
private fun tierTitle(language: AppLanguage, tier: PackageTier) = when (tier) {
    PackageTier.ECONOMY -> nuiteeText(language, "Эконом", "Economy", "Ekonom", "Эконом")
    PackageTier.STANDARD -> nuiteeText(language, "Стандарт", "Standard", "Standart", "Стандарт")
    PackageTier.COMFORT -> nuiteeText(language, "Комфорт", "Comfort", "Komfort", "Комфорт")
    PackageTier.LUXURY -> nuiteeText(language, "Люкс", "Luxury", "Lyuks", "Люкс")
}
