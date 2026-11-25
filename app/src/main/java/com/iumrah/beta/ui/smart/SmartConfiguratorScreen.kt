package com.iumrah.beta.ui.smart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import kotlinx.coroutines.launch
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.ui.components.IumrahOrbitV5
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.smart.SmartConfiguratorService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.smart.*
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.hotels.nuiteeText
import com.iumrah.beta.ui.trip.ConfiguratorAirportPickerSheet
import kotlinx.coroutines.CancellationException

@Composable
internal fun SmartConfiguratorHomeCard(language: AppLanguage, onOpen: () -> Unit, active: Boolean = true) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.Black).clickable(onClick = onOpen).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("iumrah Smart", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .15f)), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(18.dp), Color.White) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(310.dp), contentAlignment = Alignment.Center) {
            IumrahOrbitV5(minOf(320.dp, maxWidth, maxHeight), lightOnDark = true, active = active)
        }
        Text(nuiteeText(language, "Расскажите о своих планах. Остальное сделаем мы.", "Tell us your plans. We'll do the rest.", "Rejalaringizni ayting. Qolganini biz qilamiz.", "Режаларингизни айтинг. Қолганини биз қиламиз."), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(nuiteeText(language, "Подберём рейсы, отели и услуги в один пакет.", "We'll match flights, hotels and services in one package.", "Reyslar, mehmonxonalar va xizmatlarni bitta paketga jamlaymiz.", "Рейслар, меҳмонхоналар ва хизматларни битта пакетга жамлаймиз."), color = Color.White.copy(alpha = .72f), fontSize = 15.sp)
        SmartAction(nuiteeText(language, "Собрать мою Умру", "Build my Umrah", "Umramni tayyorlash", "Умрамни тайёрлаш"), true, true, onOpen)
    }
}

@Suppress("UNUSED_PARAMETER") // legacy Home callback retained for source compatibility; Smart never adopts into it
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
    var preview by remember { mutableStateOf<JourneyState?>(null) }
    var replacingCity by remember { mutableStateOf<String?>(null) }
    var replacementError by remember { mutableStateOf(false) }
    var editKind by remember { mutableStateOf<String?>(null) }
    var hotelsChanged by remember { mutableStateOf(false) }
    var excludedHotels by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val chrome = (context.applicationContext as com.iumrah.beta.IumrahApplication).container.chromeStore
    var openingReview by remember { mutableStateOf(false) }
    fun openPrivateReview() {
        val selected = result ?: return
        val snapshot = selected.state.value
        if (openingReview || !snapshot.hasFinalGeneratorQuote || snapshot.quote?.bookingAllowed != true ||
            snapshot.quote?.hasVerifiedBreakdown != true || busy || replacingCity != null) return
        val navigation = chrome.state.value
        if (navigation.currentTab != com.iumrah.beta.core.navigation.AppTab.HOME ||
            (navigation.route != com.iumrah.beta.core.navigation.AppRoute.Root && navigation.route !is com.iumrah.beta.core.navigation.AppRoute.SmartFlow)) return
        openingReview = true
        val previous = SmartRules.steps(preferences.both).let { it[(it.indexOf(8) - 1).coerceAtLeast(0)] }
        val fromRoot = chrome.state.value.route == com.iumrah.beta.core.navigation.AppRoute.Root
        chrome.openSmartReview(selected, SmartDraft(preferences, previous, false))
        // Close the original Home modal only. The resumed SmartFlow route is
        // removed by navigation and must not pop its newly opened review.
        if (fromRoot) onClose()
    }
    var languageMenu by remember { mutableStateOf(false) }
    var airportPicker by remember { mutableStateOf(false) }
    val steps = SmartRules.steps(preferences.both)
    val index = steps.indexOf(step).coerceAtLeast(0)
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    val stepMotion = remember(step) { Animatable(if (step == 0) 1f else 0f) }
    LaunchedEffect(step) { stepMotion.animateTo(1f, tween(250)) }
    LaunchedEffect(step, preferences, result != null) { if(step != 0) onSave(SmartDraft(preferences, step, result != null)) }
    LaunchedEffect(step, attempt) {
        if (step != 8 || attempt == 0) return@LaunchedEffect
        busy = true; failure = null; result = null; preview = null; excludedHotels = emptyMap(); replacementError = false; hotelsChanged = false
        progress = SmartConfiguratorService.Progress.FLIGHTS; city = null
        try { result = service.assemble(preferences, preview = { preview = it }) { stage, location -> progress = stage; city = location } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure = e.message ?: "SMART_QUOTE_UNAVAILABLE" }
        finally { busy = false }
    }
    fun publishEdit(candidate: JourneyStore, kind: String) {
        val previous = result ?: return
        if (replacingCity != null || busy) return
        editKind = null; replacingCity = kind; replacementError = false
        scope.launch {
            try {
                val edited = service.applyEdit(preferences, previous, candidate, flightsChanged = kind == "flights")
                if (result === previous) {
                    result = edited.journey; preview = edited.journey.state.value; hotelsChanged = edited.hotelsChanged
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { replacementError = true }
            finally { replacingCity = null }
        }
    }
    // A single reactive quote snapshot drives both the UI and CTA.
    // Key the subscription to the store so a replaced package cannot reuse old state.
    val assembled = result?.let { journey ->
        key(journey) { journey.state.collectAsState().value }
    }
    LaunchedEffect(step, assembled?.quote, busy, replacingCity) {
        if (step == 8 && assembled?.hasFinalGeneratorQuote == true && assembled.quote?.bookingAllowed == true) openPrivateReview()
    }
    val canContinue = when (step) {
        1 -> preferences.origin.matches(Regex("[A-Z]{3}")) && preferences.origin !in setOf("JED", "MED")
        6 -> preferences.valid()
        7 -> preferences.valid()
        8 -> assembled?.let { it.hasFinalGeneratorQuote && it.quote?.bookingAllowed == true } ?: (failure != null)
        else -> true
    }
    val title = when (step) {
        0 -> t("Ваша Умра. Ваш выбор.", "Your Umrah. Your choice.", "Sizning Umrangiz. Sizning tanlovingiz.", "Сизнинг Умрангиз. Сизнинг танловингиз.")
        1 -> t("Город вылета", "Departure city", "Uchish shahri", "Учиш шаҳри")
        2 -> t("Когда поедем?", "When shall we travel?", "Qachon boramiz?", "Қачон борамиз?")
        3 -> t("Длительность поездки", "Trip duration", "Safar davomiyligi", "Сафар давомийлиги")
        4 -> t("Маршрут поездки", "Journey route", "Safar yo‘nalishi", "Сафар йўналиши")
        5 -> t("Первый город", "First city", "Birinchi shahar", "Биринчи шаҳар")
        6 -> t("Кто едет на Умру?", "Who is traveling with you?", "Umraga necha kishi boradi?", "Умрага неча киши боради?")
        7 -> t("Уровень комфорта", "Comfort level", "Qulaylik darajasi", "Қулайлик даражаси")
        else -> if (result != null) t("Ваша Умра собрана", "Your Umrah is ready", "Umrangiz tayyor", "Умрангиз тайёр") else t("Собираем вашу Умру", "Building your Umrah", "Umrangizni tayyorlayapmiz", "Умрангизни тайёрлаяпмиз")
    }
    val subtitle = when (step) {
        0 -> t("Расскажите о своих планах. Мы подберём рейсы, отели и услуги в едином пакете.", "Tell us your plans. We'll find flights, hotels and services in one package.", "Rejalaringizni ayting. Reyslar, mehmonxonalar va xizmatlarni bitta paketda topamiz.", "Режаларингизни айтинг. Рейслар, меҳмонхоналар ва хизматларни битта пакетда топамиз.")
        1 -> t("Подберём вылет и возвращение в этот город.", "We will match departure and return to this city.", "Shu shahardan borish va qaytish reyslarini topamiz.", "Шу шаҳардан бориш ва қайтиш рейсларини топамиз.")
        2 -> t("Точные даты подберём автоматически. Вылет — в выбранном месяце.", "We'll find the dates automatically. Departure stays within your chosen month.", "Aniq sanalarni avtomatik topamiz. Uchish tanlangan oyda bo‘ladi.", "Аниқ саналарни автоматик топамиз. Учиш танланган ойда бўлади.")
        4 -> t("Выберите маршрут вашей поездки.", "Choose your journey route.", "Safaringiz yo‘nalishini tanlang.", "Сафарингиз йўналишини танланг.")
        5 -> t("В Мекку — через Джидду. В Медину — через аэропорт Медины.", "Makkah via Jeddah, Madinah via Madinah airport.", "Makkaga Jidda, Madinaga Madina aeroporti orqali.", "Маккага Жидда, Мадинага Мадина аэропорти орқали.")
        7 -> t("Выберите категорию отелей.", "Choose a hotel category.", "Mehmonxona toifasini tanlang.", "Меҳмонхона тоифасини танланг.")
        3 -> t("При необходимости расширим поиск на 3–4 дня.", "We may adjust the duration by 3–4 days to find flights.", "Zarur bo‘lsa, qidiruvni 3–4 kunga kengaytiramiz.", "Зарур бўлса, қидирувни 3–4 кунга кенгайтирамиз.")
        6 -> t("Возраст детей нужен для корректного тарифа.", "Children's ages help determine the right fare.", "To‘g‘ri tarif uchun bolalarning yoshi kerak.", "Тўғри тариф учун болаларнинг ёши керак.")
        8 -> if (result != null) t("Проверьте все детали перед бронированием.", "Review every detail before booking.", "Bron qilishdan oldin barcha tafsilotlarni tekshiring.", "Брон қилишдан олдин барча тафсилотларни текширинг.") else t("Проверяем компоненты и рассчитываем стоимость на сервере.", "Checking components and calculating the server price.", "Qismlarni tekshirib, serverda narxni hisoblayapmiz.", "Қисмларни текшириб, серверда нархни ҳисоблаяпмиз.")
        else -> ""
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 5.dp, bottom = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        SmartHeaderIcon(if (step == 0) CupertinoSymbol.Globe else CupertinoSymbol.ChevronLeft, !busy && replacingCity == null) {
                            if (step == 0) languageMenu = true else {
                                result = null; preview = null; failure = null; step = steps[(index - 1).coerceAtLeast(0)]
                            }
                        }
                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                            listOf(AppLanguage.RUSSIAN, AppLanguage.UZBEK, AppLanguage.UZBEK_CYRILLIC, AppLanguage.ENGLISH).forEach { value ->
                                DropdownMenuItem(text = { Text(value.nativeTitle) }, onClick = { languageMenu = false; onLanguage(value) })
                            }
                        }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("iumrah Smart", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(t("Персональная поездка", "Personal journey", "Shaxsiy safar", "Шахсий сафар"), color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                    }
                    SmartHeaderIcon(CupertinoSymbol.Close, true, onClose)
                }

            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
              Column(Modifier.widthIn(max = 570.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val symbol = when (step) { 1 -> CupertinoSymbol.AirplaneTakeoff; 2 -> CupertinoSymbol.Calendar; 3 -> CupertinoSymbol.Clock; 4 -> CupertinoSymbol.MapFill; 5 -> CupertinoSymbol.AirplaneLand; 6 -> CupertinoSymbol.PersonsFill; 7 -> CupertinoSymbol.StarFill; 8 -> if(result != null) null else when(progress) { SmartConfiguratorService.Progress.FLIGHTS -> CupertinoSymbol.Airplane; SmartConfiguratorService.Progress.HOTELS -> CupertinoSymbol.BuildingFill; SmartConfiguratorService.Progress.TRANSFER -> CupertinoSymbol.Car; SmartConfiguratorService.Progress.PRICE -> CupertinoSymbol.CheckCircle; SmartConfiguratorService.Progress.READY -> null }; else -> null }
                SmartOrbitHeader(step, title, subtitle, symbol, assembled)
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    alpha = stepMotion.value
                    translationY = (1f - stepMotion.value) * 10.dp.toPx()
                }) {
                when (step) {
                    0 -> Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SmartFeature(CupertinoSymbol.Airplane, t("Рейсы по вашим датам", "Flights for your dates", "Sanangizga mos reyslar", "Санангизга мос рейслар"))
                        SmartFeature(CupertinoSymbol.BuildingFill, t("Отели с доступными номерами", "Hotels with available rooms", "Bo‘sh xonali mehmonxonalar", "Бўш хонали меҳмонхоналар"))
                        SmartFeature(CupertinoSymbol.CheckCircle, t("Один пакет и единая стоимость", "One package, one total", "Bitta paket va umumiy narx", "Битта пакет ва умумий нарх"))
                    }
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmartAirportCard(preferences.airport?.city ?: FlightReferenceCatalog.airport(preferences.origin)?.city ?: preferences.origin, preferences.origin) { airportPicker = true }
                        if(airportPicker) ConfiguratorAirportPickerSheet(language, airports, preferences.airport, preferences.origin, onDismiss={ airportPicker=false }, onSelect={ airport -> preferences=preferences.copy(origin=airport.iata,airport=airport); airportPicker=false })
                        listOf("TAS", "SKD", "FEG", "NMA", "BHK", "UGC", "KSQ", "TMJ").chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { code -> SmartChoice(FlightReferenceCatalog.airport(code)?.city ?: code, code, preferences.origin == code, Modifier.weight(1f)) { preferences = preferences.copy(origin = code, airport = null) } } }
                        }
                    }
                    2 -> SmartMonthRow(language, preferences.month) { preferences = preferences.copy(month = it) }
                    3 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { listOf(3, 7, 10, 14, 20, 25).chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { n -> SmartChoice("$n", t(if(n == 3) "дня" else "дней", "days", "kun", "кун"), n == preferences.duration, Modifier.weight(1f)) { preferences = preferences.copy(duration = n) } } } } }
                    4 -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(smartSecondary()).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            listOf(false,true).forEach { both -> val label = if(both) t("Мекка и Медина", "Makkah & Madinah", "Makka va Madina", "Макка ва Мадина") else t("Мекка", "Makkah", "Makka", "Макка"); Box(Modifier.weight(1f).heightIn(min=32.dp).clip(RoundedCornerShape(7.dp)).background(if(preferences.both==both) MaterialTheme.colorScheme.surface else Color.Transparent).selectable(preferences.both==both, role=Role.Tab) { preferences=preferences.copy(both=both) }.padding(5.dp), contentAlignment=Alignment.Center) { Text(label,fontSize=13.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center) } }
                        }
                    }
                    5 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmartChoice(t("Мекка", "Makkah", "Makka", "Макка"), "JED", preferences.firstCity == "JED", Modifier.weight(1f)) { preferences = preferences.copy(firstCity = "JED") }
                        SmartChoice(t("Медина", "Madinah", "Madina", "Мадина"), "MED", preferences.firstCity == "MED", Modifier.weight(1f)) { preferences = preferences.copy(firstCity = "MED") }
                    }
                    6 -> Column(Modifier.fillMaxWidth().background(smartSecondary(), RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        SmartCounter(t("Взрослые", "Adults", "Kattalar", "Катталар"), preferences.adults, preferences.adults > 1, preferences.adults + preferences.childAges.size < 6) { delta -> preferences = preferences.copy(adults = preferences.adults + delta) }
                        HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.12f))
                        SmartCounter(t("Дети", "Children", "Bolalar", "Болалар"), preferences.childAges.size, preferences.childAges.isNotEmpty(), preferences.adults + preferences.childAges.size < 6) { delta -> preferences = preferences.copy(childAges = if (delta > 0) preferences.childAges + (-1) else preferences.childAges.dropLast(1)) }
                        preferences.childAges.forEachIndexed { i, age ->
                            key(i) { SmartChildAgePicker(t("Возраст ребёнка", "Child age", "Bolaning yoshi", "Боланинг ёши") + " ${i+1}", age) { selected ->
                                preferences = preferences.copy(childAges = preferences.childAges.mapIndexed { j, a -> if (i == j) selected else a })
                            } }
                        }
                        if (preferences.infants > preferences.adults) Text(t("На каждого младенца нужен взрослый.", "Each infant needs an adult.", "Har bir chaqaloqqa katta hamroh kerak.", "Ҳар бир чақалоққа катта ҳамроҳ керак."), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                    7 -> SmartCategoryRow(language, preferences.category) { preferences = preferences.copy(tier = it.wireValue) }
                    8 -> {
                        SmartAssemblyContent(language, progress, city, busy, assembled ?: preview, assembled != null, failure, replacingCity, replacementError,
                            onEdit = { result = null; preview = null; failure = null; step = 2 },
                            onReplace = { selectedCity -> if (result != null && replacingCity == null && !busy) editKind = selectedCity })
                        if (assembled != null) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (hotelsChanged) Text(t("После изменения рейсов некоторые отели заменены на аналогичные по категории.", "Some hotels were replaced in the same comfort category after the flight change.", "Reyslar o‘zgargach, ayrim mehmonxonalar shu toifadagilariga almashtirildi.", "Рейслар ўзгаргач, айрим меҳмонхоналар шу тоифадагиларига алмаштирилди."), fontSize = 13.sp)
                            OutlinedButton(enabled = replacingCity == null && !busy, onClick = { editKind = "flights" }, modifier = Modifier.fillMaxWidth()) { Text(t("Изменить рейсы", "Change flights", "Reyslarni o‘zgartirish", "Рейсларни ўзгартириш")) }
                            OutlinedButton(enabled = replacingCity == null && !busy, onClick = { editKind = "transfer" }, modifier = Modifier.fillMaxWidth()) { Text(t("Изменить трансфер", "Change transfer", "Transferni o‘zgartirish", "Трансферни ўзгартириш")) }
                        }
                    }
                }
                }
              }
            }
            Column(Modifier.fillMaxWidth().background(if(MaterialTheme.colorScheme.background == Color.Black) Color.Black else Color.White).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Column(Modifier.widthIn(max=570.dp).fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                assembled?.takeIf { step == 8 && it.hasFinalGeneratorQuote && it.quote?.bookingAllowed == true }?.quote?.let { quote ->
                    SmartPriceLine(t("Предварительно за всех", "Estimated total", "Taxminiy jami", "Тахминий жами"), smartMoney(quote.totalPackagePrice,quote.currency,language), true)
                    SmartPriceLine(t("На одного паломника", "Per pilgrim", "Bir ziyoratchi uchun", "Бир зиёратчи учун"),smartMoney(quote.pricePerPerson,quote.currency,language),false)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEach { value -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(smartPrimary().copy(alpha = if (value <= step) 1f else .23f))) }
                    Text("${index + 1}/${steps.size}", color = smartPrimary(), fontSize = 11.sp, modifier=Modifier.padding(start=5.dp))
                }
                val action = when { step == 8 && result != null -> t("Посмотреть пакет", "Review package", "Paketni ko‘rish", "Пакетни кўриш"); step == 8 -> t("Повторить поиск", "Retry search", "Qayta qidirish", "Қайта қидириш"); step == 7 -> t("Собрать мою Умру", "Build my Umrah", "Umramni tayyorlash", "Умрамни тайёрлаш"); step == 0 -> t("Начать", "Get started", "Boshlash", "Бошлаш"); else -> t("Продолжить", "Continue", "Davom etish", "Давом этиш") }
                SmartAction(action, false, canContinue && !busy && replacingCity == null) {
                    if (step == 8) { if (result != null) openPrivateReview() else attempt++ }
                    else { step = steps[(index + 1).coerceAtMost(steps.lastIndex)]; if (step == 8) attempt++ }
                }
              }
            }
        }
    }
    val current = result
    val kind = editKind
    if (current != null && kind != null) SmartPackageEditDialog(language, service, airports, current, kind,
        onDismiss = { editKind = null }, onApply = { candidate -> publishEdit(candidate, kind) })
}

@Composable private fun SmartHeaderIcon(symbol: CupertinoSymbol, enabled: Boolean, action: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(enabled = enabled, onClick = action), contentAlignment = Alignment.Center) { CupertinoIcon(symbol, null, Modifier.size(18.dp), Color.White.copy(alpha = if (enabled) 1f else .5f)) }
}
@Composable internal fun smartPrimary(): Color = if(MaterialTheme.colorScheme.background == Color.Black) Color.White else Color.Black
@Composable internal fun smartOnPrimary(): Color = if(MaterialTheme.colorScheme.background == Color.Black) Color.Black else Color.White
@Composable internal fun smartSecondary(): Color = if(MaterialTheme.colorScheme.background == Color.Black) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
@Composable private fun SmartChoice(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), action: () -> Unit) {
    val ink = if(selected) smartOnPrimary() else MaterialTheme.colorScheme.onSurface
    Column(modifier.heightIn(min=68.dp).clip(RoundedCornerShape(20.dp)).background(if(selected) smartPrimary() else smartSecondary()).selectable(selected, role=Role.RadioButton, onClick=action).padding(10.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { if(selected) CupertinoIcon(CupertinoSymbol.CheckCircleFill,null,Modifier.size(18.dp),ink) else Box(Modifier.size(18.dp).border(1.5.dp,ink.copy(alpha=.35f),CircleShape)) }
        Text(title,color=ink,fontWeight=FontWeight.Bold,fontSize=17.sp)
        Text(subtitle,color=ink.copy(alpha=.7f),fontSize=15.sp)
    }
}
@Composable private fun SmartCounter(label: String, value: Int, minus: Boolean, plus: Boolean, change: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("$label · $value", Modifier.weight(1f), fontWeight = FontWeight.SemiBold,fontSize=17.sp)
        SmartControl(CupertinoSymbol.Minus,44.dp,minus) { change(-1) }; SmartControl(CupertinoSymbol.Plus,44.dp,plus) { change(1) }
    }
}
@Composable private fun SmartChildAgePicker(label: String, age: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 17.sp)
        Box {
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (age < 0) "—" else age.toString(), fontSize = 17.sp)
                CupertinoIcon(CupertinoSymbol.ChevronDown, null, Modifier.size(14.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                (0..11).forEach { value -> DropdownMenuItem(text = { Text(value.toString()) }, onClick = { expanded = false; onSelect(value) },
                    trailingIcon = { if (value == age) CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(16.dp)) }) }
            }
        }
    }
}
@Composable private fun SmartAction(label: String, dark: Boolean, enabled: Boolean, action: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min=if(dark) 48.dp else 54.dp).clip(RoundedCornerShape(if(dark) 16.dp else 18.dp)).background((if (dark) Color.White else smartPrimary()).copy(alpha = if (enabled) 1f else .45f)).clickable(enabled = enabled, onClick = action).padding(vertical=if(dark) 14.dp else 8.dp), contentAlignment = Alignment.Center) { Text(label, color = if (dark) Color.Black else smartOnPrimary(), fontWeight = FontWeight.Bold,fontSize=17.sp,textAlign=TextAlign.Center) }
}
internal fun tierTitle(language: AppLanguage, tier: PackageTier) = when (tier) {
    PackageTier.ECONOMY -> nuiteeText(language, "Эконом", "Economy", "Ekonom", "Эконом")
    PackageTier.STANDARD -> nuiteeText(language, "Стандарт", "Standard", "Standart", "Стандарт")
    PackageTier.COMFORT -> nuiteeText(language, "Комфорт", "Comfort", "Komfort", "Комфорт")
    PackageTier.LUXURY -> nuiteeText(language, "Люкс", "Luxury", "Lyuks", "Люкс")
}
