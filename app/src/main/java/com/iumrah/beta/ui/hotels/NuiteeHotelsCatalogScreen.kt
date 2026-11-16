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
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.data.nuitee.NuiteeStaySelectionStore
import com.iumrah.beta.domain.nuitee.NuiteeRules
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
            date.year, date.monthValue - 1, date.dayOfMonth).show()
    }, modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), maxLines = 1)
    }
}

@Composable
internal fun NuiteeCityChoice(language: AppLanguage, city: String, onCity: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("makkah", "madinah").forEach { code ->
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
) {
    val context = LocalContext.current
    val staySelections = remember { NuiteeStaySelectionStore(context) }
    var selectionRevision by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("iumrah_nuitee_favorites", 0) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("hotels", emptySet()).orEmpty().toSet()) }
    var city by remember(initialCity) { mutableStateOf(initialCity) }
    val savedStay = remember(city, selectionRevision) { staySelections.selected(city) }
    val pendingStay = remember(city, selectionRevision) { staySelections.pending(city) }
    var searchExpanded by remember { mutableStateOf(false) }
    var hotelQuery by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(0) }
    var checkin by remember { mutableStateOf(LocalDate.now().plusDays(14)) }
    var nights by remember { mutableIntStateOf(7) }
    var adults by remember { mutableIntStateOf(2) }
    var draftCheckin by remember { mutableStateOf(checkin) }
    var draftNights by remember { mutableIntStateOf(nights) }
    var draftAdults by remember { mutableIntStateOf(adults) }
    var refreshing by remember { mutableIntStateOf(0) }
    var handledRefresh by remember { mutableIntStateOf(0) }
    var loadedDay by remember { mutableStateOf<LocalDate?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && loadedDay != null && loadedDay != LocalDate.now()) refreshing++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var hotels by remember { mutableStateOf<List<NuiteeHotel>>(emptyList()) }
    var sandbox by remember { mutableStateOf(false) }
    var pricesStale by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var requestError by remember { mutableStateOf<String?>(null) }
    var rateError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<NuiteeHotel?>(null) }
    val checkout = checkin.plusDays(nights.toLong())
    val visible = remember(hotels, stars, hotelQuery, favorites, favoritesOnly) {
        NuiteeRules.filterHotels(if (favoritesOnly) hotels.filter { it.id in favorites } else hotels, stars, hotelQuery)
    }

    LaunchedEffect(city, checkin, nights, adults, refreshing) {
        val forceRefresh = refreshing != handledRefresh
        handledRefresh = refreshing
        loadedDay = LocalDate.now()
        hotels = emptyList() // no stale inventory from a previous date/city
        requestError = null; rateError = null; pricesStale = false; loading = true
        IumrahIntelligence.record("search_started", "hotels", "nuitee")
        try {
            val cached = if (!forceRefresh) service.cachedHotels(city, checkin, checkout, adults) else null
            if (cached != null) {
                hotels = cached.hotels
                sandbox = cached.sandbox
                pricesStale = cached.pricesStale || cached.hotelCatalogStale
                rateError = cached.rateError
                return@LaunchedEffect
            }
            val listing = service.hotels(city, checkin, checkout, adults, catalogOnly = true, forceRefresh = forceRefresh)
            hotels = listing.hotels
            sandbox = listing.sandbox
            pricesStale = true // catalog-only prices are not a verified supplier quote
            try {
                val result = service.hotels(city, checkin, checkout, adults, forceRefresh = forceRefresh)
                hotels = result.hotels
                sandbox = result.sandbox
                pricesStale = result.pricesStale || result.hotelCatalogStale
                rateError = result.rateError
            } catch (cancel: CancellationException) { throw cancel
            } catch (ex: Exception) { rateError = ex.localizedMessage }
            IumrahIntelligence.record("search_results", "hotels", if (hotels.isEmpty()) "empty" else "available")
        } catch (cancel: CancellationException) { throw cancel
        } catch (ex: Exception) {
            requestError = ex.localizedMessage
            IumrahIntelligence.record("error", "hotels", "nuitee")
        } finally { loading = false }
    }

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
                            if (!searchExpanded) { draftCheckin = checkin; draftNights = nights; draftAdults = adults }
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
                        NuiteeCityChoice(language, city) { city = it; stars = 0; hotelQuery = "" }
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
                            TextButton(enabled = draftAdults < 6, onClick = { draftAdults++ }) { Text("+") }
                        }
                        IumrahPrimaryButton(title = nuiteeText(language,"Найти отели", "Search hotels", "Mehmonxonalarni qidirish", "Меҳмонхоналарни қидириш"),
                            onClick = { checkin = draftCheckin; nights = draftNights; adults = draftAdults; searchExpanded = false })
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
        item { NuiteeCityChoice(language, city) { city = it; stars = 0; hotelQuery = "" } }
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
                favorite = hotel.id in favorites, nights = nights, adults = adults, sandbox = sandbox,
                onFavorite = {
                    favorites = if (hotel.id in favorites) favorites - hotel.id else favorites + hotel.id
                    prefs.edit().putStringSet("hotels", favorites).apply()
                }, onOpen = { selected = hotel; IumrahIntelligence.record("tap", "hotels", "nuitee_details") })
        }
    }
    selected?.let { hotel ->
        NuiteeHotelDetailSheet(language, service, hotel, checkin, checkout, adults,
            onDismiss = { selected = null },
            onRate = { rate, _ ->
                staySelections.save(hotel, checkin.toString(), checkout.toString(), adults, rate)
                selectionRevision++; selected = null
            })
    }
}

@Composable
internal fun NuiteeListingCard(
    hotel: NuiteeHotel, language: AppLanguage, stale: Boolean,
    favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit,
    nights: Int = 0, adults: Int = 0, sandbox: Boolean = false,
) {
    val context = LocalContext.current
    val photos = remember(hotel.previewImages, hotel.coverImageURL) {
        (hotel.previewImages.orEmpty() + listOfNotNull(hotel.coverImageURL)).filter { it.isNotBlank() }.distinct().take(3)
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
                hotel.rating?.let { Text(String.format(Locale.US, "%.1f", it), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                if (sandbox) Text(nuiteeText(language, "Ориентировочная цена", "Indicative price", "Taxminiy narx", "Тахминий нарх"), fontSize = 10.sp)
                Text(nuiteeUSD(rate), fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nights > 0 && adults > 0) Text("$nights " + nuiteeText(language, "ночей · 1 номер ·", "nights · 1 room ·", "tun · 1 xona ·", "тун · 1 хона ·") + " $adults " + nuiteeText(language, "гостей", "guests", "mehmon", "меҳмон"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            } else {
                val message = when (hotel.rateStatus) {
                    "provider_error" -> nuiteeText(language, "Не удалось загрузить цену", "Could not load price", "Narxni yuklab bo‘lmadi", "Нархни юклаб бўлмади")
                    "loading", "not_requested" -> nuiteeText(language, "Загружаем цену…", "Loading price…", "Narx yuklanmoqda…", "Нарх юкланмоқда…")
                    else -> nuiteeText(language, "Посмотреть номера", "View rooms", "Xonalarni ko‘rish", "Хоналарни кўриш")
                }
                Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NuiteeListingPhoto(url: String?, name: String, modifier: Modifier) {
    AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop,
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
    var reviewBusy by remember(hotel.id, contentLanguage) { mutableStateOf(false) }
    var reviewError by remember(hotel.id, contentLanguage) { mutableStateOf(false) }
    LaunchedEffect(hotel.id, contentLanguage, reviewRequest, showReviews) {
        if (!showReviews) return@LaunchedEffect
        val offset = nextOffset ?: return@LaunchedEffect
        reviewBusy = true; reviewError = false
        try {
            val page = service.reviews(hotel.id, contentLanguage, offset)
            reviews = (reviews + page.reviews).distinctBy { it.id }
            nextOffset = page.nextOffset?.takeIf { it > offset }
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
    LaunchedEffect(hotel.id, checkin, checkout, adults, childrenAges, contentLanguage, retry) {
        busy = true; rates = emptyList(); failure = null; freshRates = false; indicativeRates = false
        try {
            detail = service.detail(hotel.id, contentLanguage)
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) { /* show catalogue basics without inventing hotel detail */ }
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)) {
            item {
                Text(detail?.name ?: hotel.name, fontWeight = FontWeight.Bold, fontSize = 23.sp)
                Text(detail?.address ?: hotel.address.orEmpty(), fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(nuiteeText(language,"Заезд", "Check-in", "Kirish", "Кириш"), fontSize = 12.sp); Text(checkin.toString()); detail?.checkin?.let { Text(it, fontSize = 12.sp) } }
                    Column { Text(nuiteeText(language,"Выезд", "Check-out", "Chiqish", "Чиқиш"), fontSize = 12.sp); Text(checkout.toString()); detail?.checkout?.let { Text(it, fontSize = 12.sp) } }
                }
                (detail?.rating ?: hotel.rating)?.let { Text("★ $it · ${detail?.reviewCount ?: hotel.reviewCount ?: 0}", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { showReviews = !showReviews }) { Text(nuiteeText(language,"Отзывы гостей", "Guest reviews", "Mehmonlar fikri", "Меҳмонлар фикри")) }
            }
            val photos = detail?.images.orEmpty().ifEmpty { hotel.previewImages.orEmpty().ifEmpty {
                listOfNotNull(hotel.coverImageURL) } }
            if (photos.isNotEmpty()) item {
                AsyncImage(model = photos.first(), contentDescription = hotel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp)))
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
            detail?.amenities?.take(8)?.takeIf { it.isNotEmpty() }?.let { amenities -> item { Text(amenities.joinToString(" · "), fontSize = 12.sp) } }
            detail?.policies?.take(4)?.forEach { policy -> item {
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
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(rate.roomName, fontWeight = FontWeight.Bold)
                    Text("${nuiteeUSD(rate.price)} · ${rate.mealPlan}", fontWeight = FontWeight.SemiBold)
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
