@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.iumrah.beta.ui.hotels

import android.app.DatePickerDialog as NativeDatePicker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
) {
    val context = LocalContext.current
    val staySelections = remember { NuiteeStaySelectionStore(context) }
    var selectionRevision by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("iumrah_nuitee_favorites", 0) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("hotels", emptySet()).orEmpty().toSet()) }
    var city by remember { mutableStateOf("makkah") }
    val savedStay = remember(city, selectionRevision) { staySelections.selected(city) }
    val pendingStay = remember(city, selectionRevision) { staySelections.pending(city) }
    var searchExpanded by remember { mutableStateOf(false) }
    var hotelQuery by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(0) }
    var checkin by remember { mutableStateOf(LocalDate.now().plusDays(10)) }
    var nights by remember { mutableIntStateOf(1) }
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
    val visible = remember(hotels, stars, hotelQuery) { NuiteeRules.filterHotels(hotels, stars, hotelQuery) }

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
        item {
            Column(Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .padding(bottom = 20.dp)) {
                IumrahRootPageHeader(nuiteeText(language,"Отели", "Hotels", "Mehmonxonalar", "Меҳмонхоналар"),
                    chrome, unreadCount = unreadCount, drawsSignatureBackground = false)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(HotelsBoard.HOTELS, HotelsBoard.FLIGHTS, HotelsBoard.SUNDAY).forEach { board ->
                        Box(Modifier.weight(1f).height(39.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (board == HotelsBoard.HOTELS) Color.White else Color.White.copy(alpha = .13f))
                            .clickable { onBoard(board) }, contentAlignment = Alignment.Center) {
                            Text(when (board) {
                                HotelsBoard.HOTELS -> nuiteeText(language, "Отели", "Hotels", "Mehmonxonalar", "Меҳмонхоналар")
                                HotelsBoard.FLIGHTS -> nuiteeText(language, "Авиабилеты", "Flights", "Aviachiptalar", "Авиачипталар")
                                HotelsBoard.SUNDAY -> "Sunday"
                            }, color = if (board == HotelsBoard.HOTELS) Color.Black else Color.White,
                                fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(nuiteeText(language, "Найти отель", "Find a hotel", "Mehmonxona topish", "Меҳмонхона топиш"),
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${if (city == "makkah") "Makkah" else "Madinah"} · $checkin · $nights " +
                            nuiteeText(language,"ночь", "night(s)", "tun", "тун") + " · $adults", fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        if (!searchExpanded) { draftCheckin = checkin; draftNights = nights; draftAdults = adults }
                        searchExpanded = !searchExpanded
                    }) {
                        Text(if (searchExpanded) nuiteeText(language,"Свернуть","Close","Yopish","Ёпиш")
                             else nuiteeText(language,"Изменить","Edit","O‘zgartirish","Ўзгартириш"))
                    }
                }
                AnimatedVisibility(searchExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        }
        if (savedStay != null || pendingStay != null) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text(nuiteeText(language,"Ваш выбор", "Your selection", "Tanlovingiz", "Танловингиз"), fontWeight = FontWeight.Bold)
                if (savedStay != null) Text("${savedStay.hotelName} · ${savedStay.roomName} · ${nuiteeUSD(savedStay.totalUSD)}", fontSize = 12.sp)
                if (pendingStay != null) Text("${pendingStay.hotelName} · " + nuiteeText(language,
                    "Цена уточняется", "Rate to be confirmed", "Narx aniqlanadi", "Нарх аниқланади"), fontSize = 12.sp)
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
        if (sandbox) item { Text(nuiteeText(language,"Тестовый каталог поставщика", "Supplier sandbox catalog", "Sinov katalogi", "Синов каталоги"),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        if (!loading && hotels.isEmpty() && requestError == null) item { Text(nuiteeText(language,
            "Отели не найдены на выбранные даты", "No hotels for the selected dates", "Mehmonxona topilmadi", "Меҳмонхона топилмади")) }
        items(visible, key = { "nuitee-${it.id}" }) { hotel ->
            NuiteeListingCard(hotel, language, pricesStale,
                favorite = hotel.id in favorites,
                onFavorite = {
                    favorites = if (hotel.id in favorites) favorites - hotel.id else favorites + hotel.id
                    prefs.edit().putStringSet("hotels", favorites).apply()
                }, onOpen = { selected = hotel; IumrahIntelligence.record("tap", "hotels", "nuitee_details") })
        }
    }
    selected?.let { hotel ->
        NuiteeHotelDetailSheet(language, service, hotel, checkin, checkout, adults,
            onDismiss = { selected = null },
            onRate = { rate ->
                staySelections.save(hotel, checkin.toString(), checkout.toString(), adults, rate)
                selectionRevision++; selected = null
            },
            onPending = {
                staySelections.savePending(hotel, checkin.toString(), checkout.toString(), adults)
                selectionRevision++; selected = null
            })
    }
}

@Composable
internal fun NuiteeListingCard(
    hotel: NuiteeHotel, language: AppLanguage, stale: Boolean,
    favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surface).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().height(172.dp)) {
            val image = hotel.coverImageURL ?: hotel.previewImages?.firstOrNull()
            AsyncImage(model = image, contentDescription = hotel.name,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            TextButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                CupertinoIcon(if (favorite) CupertinoSymbol.HeartFill else CupertinoSymbol.Heart, null,
                    modifier = Modifier.size(19.dp), tint = if (favorite) Color(0xFFFF3B30) else Color.White)
            }
        }
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(hotel.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text((if (hotel.roundedStars > 0) "★".repeat(hotel.roundedStars) + " · " else "") +
                (hotel.address?.take(74) ?: hotel.city), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hotel.rating != null) Text("${hotel.rating}" + (hotel.reviewCount?.let { " · $it" } ?: ""), fontSize = 12.sp)
            val rate = NuiteeRules.listedRate(hotel, stale)
            Text(if (rate != null) "${nuiteeText(language,"От", "From", "Dan", "Дан")} ${nuiteeUSD(rate)} · " +
                nuiteeText(language,"за проживание", "room stay", "turar joy", "яшаш учун")
            else nuiteeText(language,"Цена уточняется", "Rate being confirmed", "Narx aniqlanmoqda", "Нарх аниқланмоқда"),
                fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Supplier terms are visible before choice. No automatic confirmation of an unpriced hotel. */
@Composable
internal fun NuiteeHotelDetailSheet(
    language: AppLanguage, service: NuiteeHotelService, hotel: NuiteeHotel,
    checkin: LocalDate, checkout: LocalDate, adults: Int, childrenAges: List<Int> = emptyList(),
    onDismiss: () -> Unit, onRate: (NuiteeRoomRate) -> Unit, onPending: () -> Unit,
) {
    var detail by remember(hotel.id) { mutableStateOf<NuiteeHotelDetail?>(null) }
    var rates by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf<List<NuiteeRoomRate>>(emptyList()) }
    var busy by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf(true) }
    var failure by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf<String?>(null) }
    var freshRates by remember(hotel.id, checkin, checkout, adults, childrenAges) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(hotel.id, checkin, checkout, adults, childrenAges, retry) {
        busy = true; rates = emptyList(); failure = null; freshRates = false
        try {
            detail = service.detail(hotel.id, language.code)
        } catch (cancel: CancellationException) { throw cancel
        } catch (_: Exception) { /* show catalogue basics without inventing hotel detail */ }
        try {
            val response = service.rates(hotel.id, checkin, checkout, adults, childrenAges, language.code)
            rates = response.rates.filter(NuiteeRules::validRoomRate)
            freshRates = !response.cached
            if (response.cached) failure = nuiteeText(language,
                "Кэш тарифа. Перед заявкой требуется обновление.", "Cached rates need revalidation.",
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
                Text("${detail?.address ?: hotel.address.orEmpty()} · $checkin — $checkout", fontSize = 13.sp)
            }
            val photos = detail?.images.orEmpty().ifEmpty { hotel.previewImages.orEmpty().ifEmpty {
                listOfNotNull(hotel.coverImageURL) } }
            if (photos.isNotEmpty()) item {
                AsyncImage(model = photos.first(), contentDescription = hotel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp)))
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
            items(rates, key = { it.id }) { rate ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(rate.roomName, fontWeight = FontWeight.Bold)
                    Text("${nuiteeUSD(rate.price)} · ${rate.mealPlan}", fontWeight = FontWeight.SemiBold)
                    Text(rate.cancellationDescription.ifBlank { rate.cancellationTag }, fontSize = 12.sp)
                    rate.cancellationDeadlines.forEach { Text("${it.until} · ${it.amount?.let(::nuiteeUSD).orEmpty()}", fontSize = 12.sp) }
                    rate.chargeOnArrival.forEach { charge -> Text(
                        nuiteeText(language,"Оплата в отеле", "Pay at hotel", "Mehmonxonada to‘lov", "Меҳмонхонада тўлов") +
                        ": ${charge.amount?.let(::nuiteeUSD) ?: "—"} ${charge.currency}", fontSize = 12.sp) }
                    IumrahPrimaryButton(title = if (freshRates) nuiteeText(language,
                        "Выбрать номер", "Choose room", "Xonani tanlash", "Хонани танлаш") else
                        nuiteeText(language,"Обновите цену", "Recheck price", "Narxni tekshiring", "Нархни текширинг"),
                        enabled = freshRates, onClick = { if (freshRates) onRate(rate) })
                }
            }
            if (!busy && (!freshRates || rates.isEmpty())) item {
                OutlinedButton(onClick = onPending, modifier = Modifier.fillMaxWidth()) {
                    Text(nuiteeText(language, "Добавить на уточнение без цены", "Request hotel without a confirmed price",
                        "Narxni aniqlash uchun so‘rash", "Нархни аниқлаш учун сўраш"))
                }
            }
        }
    }
}
