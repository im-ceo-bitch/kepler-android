@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.iumrah.beta.ui.hotels

import android.app.DatePickerDialog as NativeDatePicker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.core.analytics.IumrahIntelligence
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.HotelTabSearchRequest
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.data.nuitee.NuiteeStaySelectionStore
import com.iumrah.beta.domain.nuitee.NuiteeRules
import com.iumrah.beta.domain.nuitee.NuiteeRoomSelection
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.journey.FamilyHotelJourneyFactory
import com.iumrah.beta.IumrahApplication
import com.iumrah.beta.models.nuitee.*
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.components.IumrahRootPageHeader
import com.iumrah.beta.ui.components.IumrahDiscoveryPills
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.ui.components.IumrahOverlappingMasthead
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner


internal fun nuiteeText(language: AppLanguage, ru: String, en: String, uz: String = en, uzCyrl: String = ru): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> uzCyrl
    else -> en
}

internal fun nuiteeUSD(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)

@Composable
internal fun rememberNuiteeHotelService(): NuiteeHotelService {
    val context = LocalContext.current.applicationContext
    return remember(context) { NuiteeHotelService(context) }
}

@Composable
internal fun NuiteeDateButton(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        NativeDatePicker(context, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d)) },
            date.year, date.monthValue - 1, date.dayOfMonth).apply {
            datePicker.minDate = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }, modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), maxLines = 1)
    }
}

@Composable
internal fun NuiteeCityChoice(language: AppLanguage, city: String, onCity: (String) -> Unit,
                                  availableCities: List<String> = listOf("makkah", "madinah")) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        availableCities.forEach { code ->
            val selected = city == code
            Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(22.dp))
                .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onCity(code) }, contentAlignment = Alignment.Center) {
                Text(if (code == "makkah") nuiteeText(language, "Мекка", "Makkah", "Makka", "Макка")
                else nuiteeText(language, "Медина", "Madinah", "Madina", "Мадина"),
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Catalog loads immediately, then enriches rates. A new search cannot reuse the prior stay's prices. */
@Composable
fun NuiteeHotelsCatalogScreen(
    language: AppLanguage, chrome: AppChromeStore, onBoard: (HotelsBoard) -> Unit,
    service: NuiteeHotelService = rememberNuiteeHotelService(),
    unreadCount: Int = 0,
    initialCity: String = "makkah",
    favoritesOnly: Boolean = false,
    searchRequest: HotelTabSearchRequest? = null,
) {
    val context = LocalContext.current
    val staySelections = remember { NuiteeStaySelectionStore(context) }
    val manual = remember(context) { (context.applicationContext as IumrahApplication).container.manualJourney }
    val manualState by manual.state.collectAsState()
    val availableCities = if (manualState.routeConfigured && manualState.plan.scope == JourneyScope.MAKKAH_ONLY)
        listOf("makkah") else listOf("makkah", "madinah")
    var selectionRevision by remember { mutableIntStateOf(0) }
    var selectionMessage by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("iumrah_nuitee_favorites", 0) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("hotels", emptySet()).orEmpty().toSet()) }
    // Only the initial mounting uses initialCity. After a one-shot handoff the
    // city must remain selected when Chrome acknowledges/clears the request.
    var city by remember { mutableStateOf(searchRequest?.city ?: initialCity) }
    val savedStay = remember(city, selectionRevision) { staySelections.selected(city) }
    val pendingStay = remember(city, selectionRevision) { staySelections.pending(city) }
    var searchExpanded by remember { mutableStateOf(false) }
    var hotelQuery by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(0) }
    var checkin by remember { mutableStateOf(searchRequest?.checkin ?: LocalDate.now().plusDays(14)) }
    var nights by remember { mutableIntStateOf(searchRequest?.nights ?: 7) }
    var adults by remember { mutableIntStateOf(searchRequest?.adults ?: 2) }
    var childrenAges by remember { mutableStateOf(searchRequest?.childrenAges ?: emptyList()) }
    var roomCount by remember { mutableIntStateOf(1) }
    val firstRoom = remember(adults, childrenAges, roomCount) {
        FamilyHotelJourneyFactory.firstRoom(adults, childrenAges, roomCount)
    }
    val familyQuoteRequired = roomCount != 1 || childrenAges.isNotEmpty() || adults > 5
    var draftRoomCount by remember { mutableIntStateOf(1) }
    var draftChildrenAges by remember { mutableStateOf(childrenAges) }
    var draftCheckin by remember { mutableStateOf(checkin) }
    var draftNights by remember { mutableIntStateOf(nights) }
    var draftAdults by remember { mutableIntStateOf(adults) }
    var refreshing by remember { mutableIntStateOf(0) }
    // Initial request already owns the initial catalogue load; do not launch
    // an identical second rates request during the first composition.
    var appliedSearchRevision by remember { mutableStateOf(searchRequest?.revision) }
    // Mirror HotelsHomeView.applyRequestedHotelSearch: apply all supplied
    // fields, invalidate old filters/rates and acknowledge exactly once.
    LaunchedEffect(searchRequest?.revision) {
        val request = searchRequest ?: return@LaunchedEffect
        if (favoritesOnly) return@LaunchedEffect
        val identicalQuery = city == request.city && checkin == request.checkin &&
            nights == request.nights && adults == request.adults &&
            childrenAges == request.childrenAges && stars == 0 && hotelQuery.isEmpty()
        val replay = appliedSearchRevision != request.revision
        city = request.city.takeIf { it in availableCities } ?: "makkah"
        roomCount = 1
        draftRoomCount = 1
        draftChildrenAges = request.childrenAges
        checkin = request.checkin
        nights = request.nights
        adults = request.adults
        childrenAges = request.childrenAges
        draftCheckin = request.checkin
        draftNights = request.nights
        draftAdults = request.adults
        stars = 0
        hotelQuery = ""
        searchExpanded = false
        // Distinct parameters invalidate the rate-state keys automatically.
        // An exact repeat must refresh once, but the first mount must not
        // perform two requests against the same rate-limited provider.
        if (replay && identicalQuery) refreshing++
        appliedSearchRevision = request.revision
        chrome.consumeHotelSearch(request.revision)
    }
    var loadedDay by remember { mutableStateOf<LocalDate?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && loadedDay != null && loadedDay != LocalDate.now()) refreshing++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(availableCities) {
        if (city !in availableCities) { city = "makkah"; stars = 0; hotelQuery = "" }
    }
    var selected by remember(city, checkin, nights, adults, childrenAges, roomCount) { mutableStateOf<NuiteeHotel?>(null) }
    val checkout = checkin.plusDays(nights.toLong())
    // Supplier prices a single room, NOT the whole party. Keep the true party
    // separately for the isolated guided family journey.
    val page = rememberNuiteePagedCatalog(service, city, checkin, checkout, firstRoom.adults, stars, hotelQuery,
        favorites = favorites.takeIf { favoritesOnly }, refresh = refreshing, childrenAges = firstRoom.childrenAges)
    val hotels = page.hotels
    val visible = hotels
    val sandbox = page.sandbox
    val pricesStale = page.stale
    val loading = page.loading
    val requestError = page.error
    val rateError = page.rateError
    LaunchedEffect(city, checkin, nights, adults, refreshing) { loadedDay = LocalDate.now() }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (favoritesOnly) item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.iumrah.beta.ui.components.IumrahBackButton(onClick = { chrome.back() })
                Text(nuiteeText(language, "Сохранённые отели", "Saved hotels", "Saqlangan mehmonxonalar", "Сақланган меҳмонхоналар"), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            Text(nuiteeText(language, "Отели, сохранённые на этом устройстве", "Hotels saved on this device", "Bu qurilmada saqlangan mehmonxonalar", "Бу қурилмада сақланган меҳмонхоналар"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        }
        item {
            IumrahOverlappingMasthead(header = {
                if (!favoritesOnly) IumrahRootPageHeader(nuiteeText(language,"Отели", "Hotels", "Mehmonxonalar", "Меҳмонхоналар"),
                    chrome, unreadCount = unreadCount, usesBrandLogo = true, discovery = true, language = language,
                    footer = {
                        IumrahDiscoveryPills(language, 1) { index ->
                            when (index) {
                                0 -> chrome.openDiscoveryBuild()
                                1 -> onBoard(HotelsBoard.HOTELS)
                                2 -> onBoard(HotelsBoard.FLIGHTS)
                                3 -> chrome.openDiscoveryTransfer()
                                4 -> chrome.openDiscoveryPackage()
                            }
                        }
                    })
            }, search = {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(3.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .20f), RoundedCornerShape(34.dp))) {
                        Row(Modifier.fillMaxWidth().clickable {
                            if (!searchExpanded) { draftCheckin = checkin; draftNights = nights; draftAdults = adults;
                                draftRoomCount = roomCount; draftChildrenAges = childrenAges }
                            searchExpanded = !searchExpanded
                        }.padding(horizontal = 14.dp, vertical = 10.dp).heightIn(min = 42.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CupertinoIcon(CupertinoSymbol.Search, null, Modifier.size(23.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(nuiteeText(language, "Поиск отелей", "Hotel search", "Mehmonxona qidirish", "Меҳмонхона қидириш"),
                                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2)
                                Text("${checkin.format(DateTimeFormatter.ofPattern("d MMM"))} · $nights " +
                                    nuiteeText(language,"ночей", "nights", "tun", "тун"), fontSize = 12.sp, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            CupertinoIcon(if (searchExpanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown,
                                null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                AnimatedVisibility(searchExpanded) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NuiteeCityChoice(language, city, { city = it; stars = 0; hotelQuery = "" }, availableCities)
                        OutlinedTextField(value = hotelQuery, onValueChange = { hotelQuery = it }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            label = { Text(nuiteeText(language, "Название отеля", "Hotel name", "Mehmonxona nomi", "Меҳмонхона номи")) })
                        Text(nuiteeText(language,"Заезд", "Check-in", "Kirish", "Кириш"), fontSize = 13.sp)
                        NuiteeDateButton(draftCheckin, { draftCheckin = it })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nuiteeText(language,"Ночей", "Nights", "Tunlar", "Тунлар"), Modifier.weight(1f))
                            TextButton(enabled = draftNights > 1, onClick = { draftNights-- }) { Text("−") }
                            Text("$draftNights")
                            TextButton(enabled = draftNights < 28, onClick = { draftNights++ }) { Text("+") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nuiteeText(language,"Взрослых", "Adults", "Kattalar", "Катталар"), Modifier.weight(1f))
                            TextButton(enabled = draftAdults > 1, onClick = { draftAdults-- }) { Text("−") }
                            Text("$draftAdults")
                            TextButton(enabled = draftAdults < 12, onClick = { draftAdults++ }) { Text("+") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nuiteeText(language,"Номеров", "Rooms", "Xonalar", "Хоналар"), Modifier.weight(1f))
                            TextButton(enabled = draftRoomCount > 1, onClick = { draftRoomCount-- }) { Text("−") }
                            Text("$draftRoomCount")
                            TextButton(enabled = draftRoomCount < minOf(4, draftAdults), onClick = { draftRoomCount++ }) { Text("+") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nuiteeText(language,"Детей", "Children", "Bolalar", "Болалар"), Modifier.weight(1f))
                            TextButton(enabled = draftChildrenAges.isNotEmpty(), onClick = { draftChildrenAges = draftChildrenAges.dropLast(1) }) { Text("−") }
                            Text("${draftChildrenAges.size}")
                            TextButton(enabled = draftChildrenAges.size < 4, onClick = { draftChildrenAges = draftChildrenAges + 5 }) { Text("+") }
                        }
                        draftChildrenAges.forEachIndexed { index, age ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(nuiteeText(language,"Возраст ребёнка", "Child's age", "Bola yoshi", "Бола ёши") + " ${index + 1}", Modifier.weight(1f))
                                TextButton(enabled = age > 0, onClick = { draftChildrenAges = draftChildrenAges.toMutableList().also { it[index] = age - 1 } }) { Text("−") }
                                Text("$age")
                                TextButton(enabled = age < 17, onClick = { draftChildrenAges = draftChildrenAges.toMutableList().also { it[index] = age + 1 } }) { Text("+") }
                            }
                        }
                        IumrahPrimaryButton(title = nuiteeText(language,"Найти отели", "Search hotels", "Mehmonxonalarni qidirish", "Меҳмонхоналарни қидириш"),
                            onClick = { checkin = draftCheckin; nights = draftNights; adults = draftAdults;
                                roomCount = draftRoomCount.coerceIn(1, minOf(4, draftAdults));
                                childrenAges = draftChildrenAges; searchExpanded = false })
                    }
                }
                    }
            })
        }
        if (!favoritesOnly && (savedStay != null || pendingStay != null)) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text(nuiteeText(language,"Ваш выбор", "Your selection", "Tanlovingiz", "Танловингиз"), fontWeight = FontWeight.Bold)
                if (savedStay != null) Text("${savedStay.hotelName} · ${savedStay.roomName} · ${nuiteeUSD(savedStay.totalUSD)}", fontSize = 12.sp)
                if (pendingStay != null) Text("${pendingStay.hotelName} · " + nuiteeText(language,
                    "Выберите номер заново", "Select a room again", "Xonani qayta tanlang", "Хонани қайта танланг"), fontSize = 12.sp)
            }
        }
        if (familyQuoteRequired && !favoritesOnly) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(nuiteeText(language,"Цена только за первый номер", "Price for the first room only",
                    "Faqat birinchi xona narxi", "Фақат биринчи хона нархи"), fontWeight = FontWeight.Bold)
                Text(nuiteeText(language,
                    "Для всей семьи выберите номера в общем конфигураторе. Показанная цена не является стоимостью всей поездки.",
                    "Build the whole family's trip to select all rooms. The displayed rate is not the total package price.",
                    "Butun oila uchun barcha xonalarni safar konstruktorida tanlang.",
                    "Бутун оила учун барча хоналарни сафар конструкторида танланг."), fontSize = 13.sp)
                IumrahPrimaryButton(title = nuiteeText(language,"Настроить путешествие", "Build your trip",
                    "Safarni sozlash", "Сафарни созлаш"), onClick = {
                    val current = manual.state.value
                    val standalone = FamilyHotelJourneyFactory.create(current.trip ?: current.plan,
                        adults, childrenAges, roomCount, checkin, nights, current.routeConfigured)
                    chrome.beginFamilyJourney(standalone)
                })
                if (adults + childrenAges.size > 9) Text(nuiteeText(language,
                    "Больше 9 паломников: оформление потребует дополнительного согласования.",
                    "More than 9 travelers requires additional review before checkout.",
                    "9 nafardan ortiq yo‘lovchi qo‘shimcha tekshiruvni talab qiladi.",
                    "9 нафардан ортиқ йўловчи қўшимча текширувни талаб қилади."), fontSize = 12.sp)
            }
        }
        selectionMessage?.let { message -> item { Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item { NuiteeCityChoice(language, city, { city = it; stars = 0; hotelQuery = "" }, availableCities) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..5).forEach { selectedStars ->
                    Box(Modifier.weight(1f).height(35.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (stars == selectedStars) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                        .clickable { stars = selectedStars }, contentAlignment = Alignment.Center) {
                        Text(if (selectedStars == 0) nuiteeText(language,"Все", "All", "Barchasi", "Барчаси") else "$selectedStars★",
                            fontSize = 11.sp, color = if (stars == selectedStars) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if (pricesStale || rateError != null) item {
            Text(nuiteeText(language, "Актуальность цен не подтверждена: тариф уточняется при открытии номера.",
                "Prices may be outdated. Recheck the room offer before selecting.", "Narxlar qayta tekshiriladi.", "Нархлар қайта текширилади."),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading) item { CircularProgressIndicator(Modifier.padding(20.dp)) }
        if (!loading && requestError != null && hotels.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(requestError.orEmpty(), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { refreshing++ }) { Text(nuiteeText(language,"Повторить", "Retry", "Qayta urinish", "Қайта уриниш")) }
            }
        }
        if (favoritesOnly && !loading && requestError == null && hotels.none { it.id in favorites }) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CupertinoIcon(CupertinoSymbol.Heart, null, Modifier.size(38.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Text(nuiteeText(language, "Нет сохранённых отелей в этом городе", "No saved hotels in this city", "Bu shaharda saqlangan mehmonxonalar yo‘q", "Бу шаҳарда сақланган меҳмонхоналар йўқ"), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(nuiteeText(language, "Отмечайте понравившиеся отели сердечком в каталоге.", "Tap the heart on hotels you like in the catalog.", "Katalogda yoqqan mehmonxonalarni yurakcha bilan belgilang.", "Каталогда ёққан меҳмонхоналарни юракча билан белгиланг."), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if (!favoritesOnly && !loading && hotels.isEmpty() && requestError == null) item { Text(nuiteeText(language,
            "Отели не найдены на выбранные даты", "No hotels for the selected dates", "Mehmonxona topilmadi", "Меҳмонхона топилмади")) }
        items(visible, key = { "nuitee-${it.id}" }) { hotel ->
            NuiteeListingCard(hotel, language, pricesStale,
                favorite = hotel.id in favorites, nights = nights, adults = firstRoom.adults, childrenCount = firstRoom.childrenAges.size, sandbox = sandbox,
                onFavorite = {
                    favorites = if (hotel.id in favorites) favorites - hotel.id else favorites + hotel.id
                    prefs.edit().putStringSet("hotels", favorites).apply()
                }, onOpen = { selected = hotel; IumrahIntelligence.record("tap", "hotels", "nuitee_details") })
        }
        if (page.error != null && hotels.isNotEmpty()) item {
            OutlinedButton(enabled = !loading, onClick = page::retry, modifier = Modifier.fillMaxWidth()) { Text(nuiteeText(language, "Повторить", "Retry", "Qayta urinish", "Қайта уриниш")) }
        }
        if (page.error == null && page.nextOffset != null && hotels.isNotEmpty()) item {
            OutlinedButton(enabled = !loading, onClick = page::loadMore, modifier = Modifier.fillMaxWidth()) {
                Text(nuiteeText(language, "Показать ещё", "Load more", "Yana ko‘rsatish", "Яна кўрсатиш"))
            }
        }
    }
    selected?.let { hotel ->
        NuiteeHotelDetailSheet(language, service, hotel, checkin, checkout, firstRoom.adults,
            childrenAges = firstRoom.childrenAges, onDismiss = { selected = null },
            onRate = { rate, meals ->
                if (familyQuoteRequired) {
                    selectionMessage = nuiteeText(language,
                        "Это стоимость первого номера. Создайте семейную поездку для выбора всех номеров.",
                        "This is a first-room rate. Build the family trip to select every room.",
                        "Bu faqat birinchi xona narxi. Oila safarini yarating.",
                        "Бу фақат биринчи хона нархи. Оила сафарини яратинг.")
                } else {
                    val manualSnapshot = manual.state.value
                    val choice = NuiteeRoomSelection("$city:0", city, 0, hotel, rate, checkin, checkout,
                        firstRoom.adults, emptyList(), meals)
                    val next = manualSnapshot.selectRoom(choice)
                    if (next != null) {
                        manual.update(manualSnapshot) { next }
                        staySelections.save(hotel, checkin.toString(), checkout.toString(), firstRoom.adults, rate)
                        selectionRevision++
                        selectionMessage = nuiteeText(language,"Номер добавлен в ваш пакет", "Room added to your package",
                            "Xona paketingizga qo‘shildi", "Хона пакетингизга қўшилди")
                    } else {
                        selectionMessage = nuiteeText(language,
                            "Номер не совпадает с настроенным маршрутом. Измените даты или состав пакета.",
                            "This room conflicts with your existing trip. Review dates and guests.",
                            "Xona mavjud marshrutga mos kelmaydi.",
                            "Хона мавжуд маршрутга мос келмайди.")
                    }
                }
                selected = null
            })
    }
}

@Composable
internal fun NuiteeListingCard(
    hotel: NuiteeHotel, language: AppLanguage, stale: Boolean,
    favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit,
    nights: Int = 0, adults: Int = 0, childrenCount: Int = 0, sandbox: Boolean = false,
) {
    val context = LocalContext.current
    val candidates = remember(hotel.previewImages, hotel.coverImageURL) {
        (hotel.previewImages.orEmpty() + listOfNotNull(hotel.coverImageURL)).filter { it.isNotBlank() }.distinctBy(::hotelPhotoIdentity).take(15)
    }
    val photoService = rememberNuiteeHotelService()
    val photos by produceState(candidates.take(3), hotel.id, candidates) {
        value = candidates.take(3)
        value = com.iumrah.beta.data.nuitee.DistinctHotelPhotos.selectWithFallback(hotel.id, candidates) {
            val detail = photoService.detail(hotel.id, "en")
            (detail.images + listOfNotNull(detail.coverImageURL) + detail.rooms.flatMap { it.images }).filter { it.isNotBlank() }.distinctBy(::hotelPhotoIdentity)
        }.ifEmpty { candidates.take(1) }
    }
    Row(Modifier.fillMaxWidth().height(232.dp).clip(RoundedCornerShape(30.dp))
        .background(MaterialTheme.colorScheme.surface).clickable(onClick = onOpen)) {
        Box(Modifier.weight(.43f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (photos.size < 2) {
                    NuiteeListingPhoto(photos.firstOrNull(), hotel.name, Modifier.fillMaxSize())
                } else {
                    NuiteeListingPhoto(photos[0], hotel.name, Modifier.fillMaxWidth().weight(.64f))
                    Row(Modifier.fillMaxWidth().weight(.36f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        NuiteeListingPhoto(photos[1], hotel.name, Modifier.weight(1f).fillMaxHeight())
                        if (photos.size > 2) NuiteeListingPhoto(photos[2], hotel.name, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val bubble = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = .35f))
                Box(bubble.clickable(onClick = onFavorite), contentAlignment = Alignment.Center) {
                    CupertinoIcon(if (favorite) CupertinoSymbol.HeartFill else CupertinoSymbol.Heart, null, Modifier.size(15.dp), Color.White)
                }
                Box(bubble.clickable {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, hotel.name + " — " + (hotel.address ?: hotel.city))
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, null))
                }, contentAlignment = Alignment.Center) {
                    CupertinoIcon(CupertinoSymbol.Share, null, Modifier.size(14.dp), Color.White)
                }
            }
        }
        Column(Modifier.weight(.57f).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(hotel.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hotel.roundedStars > 0) Text("★".repeat(hotel.roundedStars), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                else Text(nuiteeText(language,"Без категории","Unrated","Toifasi ko‘rsatilmagan","Тоифаси кўрсатилмаган"),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                hotel.rating?.takeIf { it.isFinite() && it > 0 }?.let { Text(String.format(Locale.US, "%.1f", it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CupertinoIcon(CupertinoSymbol.Location, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when (hotel.city.lowercase()) {
                    "makkah", "mecca", "makka" -> nuiteeText(language, "Мекка", "Makkah", "Makka", "Макка")
                    "madinah", "medina", "madina" -> nuiteeText(language, "Медина", "Madinah", "Madina", "Мадина")
                    else -> hotel.city
                }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            val rate = NuiteeRules.listedRate(hotel, stale)
            if (rate != null) {
                Text(nuiteeText(language, "От", "From", "Dan", "Дан"), fontSize = 11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(nuiteeUSD(rate), fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nights > 0 && adults > 0) Text("$nights " + nuiteeText(language, "ночей · 1 номер ·", "nights · 1 room ·", "tun · 1 xona ·", "тун · 1 хона ·") + " ${adults+childrenCount} " + nuiteeText(language, "гостей", "guests", "mehmon", "меҳмон"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            } else {
                if (!stale && (hotel.rateStatus == null || hotel.rateStatus in setOf("loading","not_requested"))) {
                    val unavailable=nuiteeText(language,"Цена ещё не получена","Price not yet received","Narx hali olinmagan","Нарх ҳали олинмаган")
                    Box(Modifier.size(width=80.dp,height=16.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.12f)).semantics { contentDescription=unavailable })
                } else {
                    val message=when(if(stale) "cached" else hotel.rateStatus) {
                        "cached" -> nuiteeText(language,"Цена требует проверки","Price needs verification","Narxni tekshirish kerak","Нархни текшириш керак")
                        "not_returned" -> nuiteeText(language,"Тариф не получен · повторите поиск","Rate not returned · retry search","Tarif olinmadi · qayta qidiring","Тариф олинмади · қайта қидиринг")
                        "no_availability" -> if(sandbox) nuiteeText(language,"Проверить тарифы в отеле","Check room rates","Mehmonxona tariflarini tekshirish","Меҳмонхона тарифларини текшириш") else nuiteeText(language,"На эти даты распродано","Sold out for these dates","Bu sanalarga barcha xonalar band","Бу саналарга барча хоналар банд")
                        else -> nuiteeText(language,"Не удалось проверить наличие","Could not check availability","Mavjudlikni tekshirib bo‘lmadi","Мавжудликни текшириб бўлмади")
                    }
                    Text(message,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NuiteeListingPhoto(url: String?, name: String, modifier: Modifier) {
    AsyncImage(model = com.iumrah.beta.core.config.AppConfig.absoluteUrl(url), contentDescription = name, contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).clip(RoundedCornerShape(0.dp)))
}

/** Supplier terms are visible before choice. No automatic confirmation of an unpriced hotel. */
@Composable
internal fun NuiteeHotelDetailSheet(
    language: AppLanguage, service: NuiteeHotelService, hotel: NuiteeHotel,
    checkin: LocalDate, checkout: LocalDate, adults: Int, childrenAges: List<Int> = emptyList(),
    onDismiss: () -> Unit, onRate: (NuiteeRoomRate, NuiteeMealChoices) -> Unit,
    allowMealSelection: Boolean = false,
    initialMeals: NuiteeMealChoices = NuiteeMealChoices(),
) {
    val context = LocalContext.current
    val account = (context.applicationContext as com.iumrah.beta.IumrahApplication).container.accountStore
    val accountState by account.state.collectAsState()
    val owner = if (accountState.isRestoring) null else com.iumrah.beta.domain.smart.SmartRules.owner(accountState.isAuthenticated, accountState.iumrahID)
    val visitOwner = remember(hotel.id, checkin, checkout, adults) { owner }
    LaunchedEffect(hotel.id, checkin, checkout, adults, visitOwner) {
        if (owner == visitOwner) com.iumrah.beta.data.smart.HomeActivityStore(context).saveHotel(visitOwner, com.iumrah.beta.data.smart.HomeHotelVisit(hotel, checkin.toString(), java.time.temporal.ChronoUnit.DAYS.between(checkin, checkout).toInt().coerceAtLeast(1), adults))
    }
    val contentLanguage = if (language == AppLanguage.UZBEK_CYRILLIC) "uz-Cyrl" else language.code
    var detail by remember(hotel.id, contentLanguage) { mutableStateOf<NuiteeHotelDetail?>(null) }
    var meals by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf(initialMeals) }
    var reviews by remember(hotel.id, contentLanguage) { mutableStateOf<List<NuiteeGuestReview>>(emptyList()) }
    var showReviews by remember(hotel.id, contentLanguage) { mutableStateOf(false) }
    var nextOffset by remember(hotel.id, contentLanguage) { mutableStateOf<Int?>(0) }
    var reviewRequest by remember(hotel.id, contentLanguage) { mutableIntStateOf(0) }
    var completedReviewRequest by remember(hotel.id, contentLanguage) { mutableIntStateOf(-1) }
    var reviewBusy by remember(hotel.id, contentLanguage) { mutableStateOf(false) }
    var reviewError by remember(hotel.id, contentLanguage) { mutableStateOf(false) }
    LaunchedEffect(hotel.id, contentLanguage, reviewRequest, showReviews) {
        if (!showReviews || completedReviewRequest == reviewRequest) return@LaunchedEffect
        val offset = nextOffset ?: return@LaunchedEffect
        reviewBusy = true; reviewError = false
        try {
            val page = service.reviews(hotel.id, contentLanguage, offset)
            reviews = (reviews + page.reviews).distinctBy { it.id }
            nextOffset = page.nextOffset?.takeIf { it > offset }
            completedReviewRequest = reviewRequest
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { reviewError = true }
        finally { reviewBusy = false }
    }
    var rates by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf<List<NuiteeRoomRate>>(emptyList()) }
    var busy by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf(true) }
    var failure by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf<String?>(null) }
    var indicativeRates by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf(false) }
    var freshRates by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(hotel.id, contentLanguage, retry) {
        try { detail = service.detail(hotel.id, contentLanguage) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* catalogue basics remain usable */ }
    }
    LaunchedEffect(hotel.id, checkin, checkout, adults, childrenAges, contentLanguage, retry) {
        busy = true; rates = emptyList(); failure = null; freshRates = false; indicativeRates = false
        try {
            val response = service.rates(hotel.id, checkin, checkout, adults, childrenAges, contentLanguage)
            rates = response.rates.filter(NuiteeRules::validRoomRate)
            freshRates = !response.cached
            indicativeRates = response.sandbox
            if (response.cached) failure = nuiteeText(language,
                "Проверьте актуальную стоимость перед бронированием.", "Check the current price before booking.",
                "Narxni qayta tekshiring.", "Нархни қайта текширинг.")
        } catch (cancel: CancellationException) { throw cancel
        } catch (ex: Exception) { failure = ex.localizedMessage }
        busy = false
    }
    val photos = remember(detail,hotel.id) { (detail?.images.orEmpty()+hotel.previewImages.orEmpty()+listOfNotNull(hotel.coverImageURL)).filter { it.isNotBlank() }.distinctBy(::hotelPhotoIdentity) }
    var photoGallery by remember(hotel.id) { mutableStateOf<List<String>?>(null) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onDismiss) { CupertinoIcon(CupertinoSymbol.ChevronLeft,nuiteeText(language,"Назад","Back","Orqaga","Орқага"),Modifier.size(20.dp)) }
            Text(nuiteeText(language,"Отель","Hotel","Mehmonxona","Меҳмонхона"),Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=17.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top=14.dp,bottom = 70.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text(detail?.name ?: hotel.name, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Text(detail?.address ?: hotel.address.orEmpty(), fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(nuiteeText(language,"Заезд", "Check-in", "Kirish", "Кириш"), fontSize = 12.sp); Text(checkin.toString()); detail?.checkin?.let { Text(it, fontSize = 12.sp) } }
                    Column { Text(nuiteeText(language,"Выезд", "Check-out", "Chiqish", "Чиқиш"), fontSize = 12.sp); Text(checkout.toString()); detail?.checkout?.let { Text(it, fontSize = 12.sp) } }
                }
                (detail?.rating ?: hotel.rating)?.let { Text("★ $it · ${detail?.reviewCount ?: hotel.reviewCount ?: 0}", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { showReviews = !showReviews }) { Text(nuiteeText(language,"Отзывы гостей", "Guest reviews", "Mehmonlar fikri", "Меҳмонлар фикри")) }
            }
            if (photos.isNotEmpty()) item {
                HotelPhotoMosaic(photos,hotel.name,156.dp) { photoGallery=photos }
            }
            if (showReviews) {
                items(reviews, key = { "review-${it.id}" }) { review ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(listOf(review.name, review.country, review.date).filter { it.isNotBlank() }.joinToString(" · "), fontWeight = FontWeight.SemiBold)
                        review.rating?.let { Text("★ $it", fontSize = 12.sp) }
                        listOf(review.headline, review.pros, review.cons).filter { it.isNotBlank() }.forEach { Text(it, fontSize = 13.sp) }
                    }
                }
                item {
                    if (reviewBusy) CircularProgressIndicator()
                    else if (reviewError || nextOffset != null) OutlinedButton(onClick = { reviewRequest++ }) {
                        Text(if (reviewError) nuiteeText(language,"Повторить", "Retry", "Qayta urinish", "Қайта уриниш") else nuiteeText(language,"Ещё отзывы", "More reviews", "Yana fikrlar", "Яна фикрлар"))
                    } else if (reviews.isEmpty()) Text(nuiteeText(language,"Отзывов пока нет", "No reviews yet", "Fikrlar yo‘q", "Фикрлар йўқ"))
                }
            }
            if (!detail?.description.isNullOrBlank()) item { Text(detail?.description.orEmpty(), fontSize = 13.sp) }
            if (!detail?.importantInformation.isNullOrBlank()) item { Text(detail?.importantInformation.orEmpty(), fontSize = 12.sp) }
            detail?.amenities?.takeIf { it.isNotEmpty() }?.let { amenities -> item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { amenities.chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { pair.forEach { amenity->Text(amenity,Modifier.weight(1f),fontSize=13.sp) } } } } } }
            detail?.policies?.forEach { policy -> item {
                Text(policy.title, fontWeight = FontWeight.SemiBold)
                Text(policy.description, fontSize = 12.sp)
            } }
            item {
                Text(nuiteeText(language,"Доступные номера", "Room offers", "Xona takliflari", "Хона таклифлари"),
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (busy) CircularProgressIndicator(Modifier.padding(10.dp))
                failure?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                if (!busy && (!freshRates || rates.isEmpty())) OutlinedButton(onClick = { retry++ }) {
                    Text(nuiteeText(language,"Обновить тарифы", "Refresh room rates", "Narxni yangilash", "Нархни янгилаш"))
                }
            }
            if (allowMealSelection) item {
                Text(nuiteeText(language,"Питание", "Meals", "Ovqatlanish", "Овқатланиш"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (hotel.roundedStars <= 3) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val madinah = hotel.city.lowercase().let { it.contains("madin") || it.contains("medin") || it.contains("медин") || it.contains("мадин") }
                        Text(if (madinah) nuiteeText(language,"Двухразовое питание", "Two meals daily", "Kuniga ikki mahal", "Кунига икки маҳал") else nuiteeText(language,"Трёхразовое питание", "Three meals daily", "Kuniga uch mahal", "Кунига уч маҳал"), Modifier.weight(1f))
                        Switch(checked = meals.breakfast || meals.lunch || meals.dinner, onCheckedChange = { meals = NuiteeMealChoices(it, it && !madinah, it) })
                    }
                } else {
                    Text(nuiteeText(language,"Завтрак включён", "Breakfast included", "Nonushta kiritilgan", "Нонушта киритилган"), fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nuiteeText(language,"Обед", "Lunch", "Tushlik", "Тушлик"), Modifier.weight(1f)); Switch(meals.lunch, { meals = meals.copy(lunch = it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nuiteeText(language,"Ужин", "Dinner", "Kechki ovqat", "Кечки овқат"), Modifier.weight(1f)); Switch(meals.dinner, { meals = meals.copy(dinner = it) })
                    }
                }
            }
            items(rates, key = { it.id }) { rate ->
                val room=detail?.rooms?.firstOrNull { it.id==rate.roomId }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val roomPhotos=room?.images.orEmpty().filter { it.isNotBlank() }.distinctBy(::hotelPhotoIdentity)
                    if(roomPhotos.isNotEmpty()) HotelPhotoMosaic(roomPhotos,rate.roomName,176.dp) { photoGallery=roomPhotos }
                    Text(room?.name?.takeIf { it.isNotBlank() } ?: rate.roomName.ifBlank { nuiteeText(language,"Номер","Room","Xona","Хона") }, fontWeight = FontWeight.SemiBold,fontSize=17.sp)
                    if(room?.name?.isNotBlank()==true && room.name!=rate.roomName) Text(rate.roomName,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    room?.maxOccupancy?.let { Text("${it} "+nuiteeText(language,"гостей","guests","mehmon","меҳмон"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    if(rate.mealPlan.isNotBlank()) Text(rate.mealPlan,fontSize=13.sp)
                    Text(nuiteeUSD(rate.price), fontWeight = FontWeight.Bold,fontSize=22.sp)
                    Text(nuiteeText(language,"За весь период проживания","For the entire stay","Butun yashash davri uchun","Бутун яшаш даври учун"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if (indicativeRates) Text(nuiteeText(language, "Стоимость, наличие и условия уточним перед оплатой.", "Price, availability and terms will be confirmed before payment.", "Narx, mavjudlik va shartlar to‘lovdan oldin tasdiqlanadi.", "Нарх, мавжудлик ва шартлар тўловдан олдин тасдиқланади."), fontSize = 12.sp)
                    else {
                    Text(rate.cancellationDescription.ifBlank { rate.cancellationTag }, fontSize = 12.sp)
                    rate.cancellationDeadlines.forEach { Text("${it.until} · ${it.amount?.let(::nuiteeUSD).orEmpty()}", fontSize = 12.sp) }
                    rate.chargeOnArrival.forEach { charge -> Text(
                        nuiteeText(language,"Оплата в отеле", "Pay at hotel", "Mehmonxonada to‘lov", "Меҳмонхонада тўлов") +
                        ": ${charge.amount?.let(::nuiteeUSD) ?: "—"} ${charge.currency}", fontSize = 12.sp) }
                    }
                    IumrahPrimaryButton(title = if (freshRates) nuiteeText(language,
                        "Выбрать номер", "Choose room", "Xonani tanlash", "Хонани танлаш") else
                        nuiteeText(language,"Обновите цену", "Recheck price", "Narxni tekshiring", "Нархни текширинг"),
                        enabled = freshRates, onClick = { if (freshRates) onRate(rate, meals) })
                }
            }
        }
        }
    }
    photoGallery?.let { HotelPhotoViewer(it,language) { photoGallery=null } }
}

@Composable private fun HotelPhotoMosaic(photos:List<String>,name:String,height:androidx.compose.ui.unit.Dp,onOpen:()->Unit) {
    Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(17.dp)).clickable(onClick=onOpen),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
        NuiteeListingPhoto(photos.firstOrNull(),name,Modifier.weight(if(photos.size>1).64f else 1f).fillMaxHeight())
        if(photos.size>1) Column(Modifier.weight(.36f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            NuiteeListingPhoto(photos[1],name,Modifier.fillMaxWidth().weight(1f))
            if(photos.size>2) Box(Modifier.fillMaxWidth().weight(1f)) {
                NuiteeListingPhoto(photos[2],name,Modifier.fillMaxSize())
                if(photos.size>3) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.45f)),contentAlignment=Alignment.Center) { Text("+${photos.size-3}",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.White) }
            }
        }
    }
}

@Composable private fun HotelPhotoViewer(photos:List<String>,language:AppLanguage,onDismiss:()->Unit) {
    val pager=rememberPagerState { photos.size }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            HorizontalPager(state=pager,modifier=Modifier.fillMaxSize()) { page -> AsyncImage(model=com.iumrah.beta.core.config.AppConfig.absoluteUrl(photos[page]),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit) }
            IconButton(onClick=onDismiss,modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)) { CupertinoIcon(CupertinoSymbol.Close,nuiteeText(language,"Закрыть","Close","Yopish","Ёпиш"),Modifier.size(22.dp),Color.White) }
            Text("${pager.currentPage+1} / ${photos.size}",Modifier.align(Alignment.BottomCenter).padding(16.dp),color=Color.White)
        }
    }
}

/** Collapse provider resize variants without stripping identity-bearing query parameters. */
internal fun hotelPhotoIdentity(raw: String): String = runCatching {
    val uri = java.net.URI(raw)
    val resize = setOf("w", "h", "width", "height", "size", "quality", "q", "fit", "crop", "dpr", "format", "resize", "auto", "imwidth", "impolicy")
    val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() && it.substringBefore('=').lowercase() !in resize }.sorted().joinToString("&")
    "${uri.scheme?.lowercase()}://${uri.rawAuthority?.lowercase()}${uri.rawPath.orEmpty()}" + if (query.isBlank()) "" else "?$query"
}.getOrDefault(raw)
