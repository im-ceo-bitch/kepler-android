@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.iumrah.beta.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.ui.hotels.rememberNuiteeHotelService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.nuitee.NuiteeRoomSelection
import com.iumrah.beta.domain.nuitee.NuiteePendingHotel
import com.iumrah.beta.domain.nuitee.NuiteeRules
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.TripStayPlanner
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.hotels.NuiteeCityChoice
import com.iumrah.beta.ui.hotels.NuiteeHotelDetailSheet
import com.iumrah.beta.ui.hotels.NuiteeListingCard
import com.iumrah.beta.ui.hotels.nuiteeText
import com.iumrah.beta.ui.hotels.nuiteeUSD
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

/** Source parity: room-index × city, real supplier rates and explicitly unpriced pending requests. */
@Composable
fun NuiteeJourneyHotelsScreen(language: AppLanguage, journey: JourneyStore, chrome: AppChromeStore,
                               service: NuiteeHotelService = rememberNuiteeHotelService()) {
    val context = LocalContext.current
    val favoritePrefs = remember { context.getSharedPreferences("iumrah_nuitee_favorites", 0) }
    var favorites by remember { mutableStateOf(favoritePrefs.getStringSet("hotels", emptySet()).orEmpty().toSet()) }
    val state by journey.state.collectAsState()
    val trip = state.trip
    val draft = state.nuitee
    var city by remember { mutableStateOf("makkah") }
    var roomIndex by remember { mutableIntStateOf(0) }
    var stars by remember { mutableIntStateOf(0) }
    var hotels by remember { mutableStateOf<List<NuiteeHotel>>(emptyList()) }
    var rateError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sandbox by remember { mutableStateOf(false) }
    var pricesStale by remember { mutableStateOf(false) }
    var selectedHotel by remember { mutableStateOf<NuiteeHotel?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var handledRefresh by remember { mutableIntStateOf(0) }
    var childrenAges by remember { mutableStateOf(List(trip.children + trip.infants) { -1 }) }
    val windows = TripStayPlanner.windows(trip)
    val stay = if (city == "madinah") windows.madinah ?: windows.makkah else windows.makkah
    val roomAdults = trip.adults / trip.rooms + if (roomIndex < trip.adults % trip.rooms) 1 else 0
    val roomChildren = childrenAges.withIndex().filter { it.index % trip.rooms == roomIndex }.map { it.value }
    val ready = childrenAges.size == trip.children + trip.infants && childrenAges.all { it in 0..17 } && roomAdults > 0
    val roomKey = "$city:$roomIndex"
    val allComplete = draft?.roomsComplete(trip) == true

    LaunchedEffect(city, roomIndex, stay.checkIn, stay.checkOut, roomAdults, refresh) {
        val forceRefresh = refresh != handledRefresh
        handledRefresh = refresh
        hotels = emptyList(); rateError = null; loading = true; pricesStale = false
        try {
            val response = service.hotels(city, stay.checkIn, stay.checkOut, roomAdults, forceRefresh = forceRefresh)
            hotels = response.hotels; sandbox = response.sandbox
            rateError = response.rateError
            pricesStale = response.hotelCatalogStale || response.pricesStale
        } catch (cancel: CancellationException) { throw cancel
        } catch (ex: Exception) { rateError = ex.localizedMessage
        } finally { loading = false }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        if (allComplete) Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
            IumrahPrimaryButton(title = nuiteeText(language,"Продолжить к поездке", "Review trip",
                "Safarni ko‘rish", "Сафарни кўриш"), onClick = { chrome.openNuiteeJourneyReview() })
        }
    }) { scaffoldPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(scaffoldPadding), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { chrome.back() }) { Text("‹") }
                    Column(Modifier.weight(1f)) {
                        Text(nuiteeText(language,"Отели и номера", "Hotels & rooms", "Mehmonxona va xonalar", "Меҳмонхона ва хоналар"),
                            fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("${trip.travelerCount} · ${trip.rooms} " + nuiteeText(language,"номер(ов)", "room(s)", "xona", "хона"), fontSize = 13.sp)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(nuiteeText(language,"Состав поездки", "Travel party", "Sayohatchilar", "Саёҳатчилар"), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nuiteeText(language,"Номеров", "Rooms", "Xonalar", "Хоналар"), Modifier.weight(1f))
                        TextButton(enabled = trip.rooms > 1, onClick = { journey.changeNuiteeRoomCount(trip.rooms - 1); roomIndex = 0 }) { Text("−") }
                        Text("${trip.rooms}")
                        TextButton(enabled = trip.rooms < trip.adults, onClick = { journey.changeNuiteeRoomCount(trip.rooms + 1); roomIndex = 0 }) { Text("+") }
                    }
                    childrenAges.forEachIndexed { idx, age ->
                        var open by remember(idx) { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nuiteeText(language,"Возраст ребёнка", "Child's age", "Bola yoshi", "Бола ёши") + " ${idx + 1}", Modifier.weight(1f))
                            Box {
                                OutlinedButton(onClick = { open = true }) { Text(if (age < 0) "—" else "$age") }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    (0..17).forEach { value -> DropdownMenuItem(text = { Text("$value") }, onClick = {
                                        childrenAges = childrenAges.toMutableList().also { it[idx] = value }
                                        journey.clearNuiteeRooms(); open = false
                                    }) }
                                }
                            }
                        }
                    }
                    if (!ready) Text(nuiteeText(language,
                        "Укажите возраст всех детей, чтобы поставщик рассчитал тариф.",
                        "Enter each child's age to request supplier room rates.",
                        "Bolalar yoshini kiriting.", "Болалар ёшини киритинг."),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            if (trip.scope == JourneyScope.MAKKAH_AND_MADINAH) item {
                NuiteeCityChoice(language, city) { city = it; roomIndex = 0; stars = 0 }
            }
            if (trip.rooms > 1) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until trip.rooms).forEach { i ->
                        OutlinedButton(onClick = { roomIndex = i }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor =
                                if (roomIndex == i) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)) {
                            Text("${i + 1}")
                        }
                    }
                }
            }
            item {
                Text("${stay.checkIn} — ${stay.checkOut} · $roomAdults " +
                    nuiteeText(language,"взрослых", "adults", "katta", "катта") + " · ${roomChildren.size} " +
                    nuiteeText(language,"детей", "children", "bola", "бола"), fontSize = 13.sp)
                val chosen = draft?.rooms?.get(roomKey)
                val pending = draft?.pending?.get(roomKey)
                chosen?.let { Text("${it.hotel.name} · ${it.rate.roomName} · ${nuiteeUSD(it.rate.price)}",
                    color = Color(0xFF258654), fontSize = 12.sp) }
                pending?.let { Text("${it.hotel.name} · " + nuiteeText(language,
                    "Цена уточняется — не входит в расчёт", "Price unconfirmed — excluded from estimate",
                    "Narx tekshirilmoqda", "Нарх текширилмоқда"), fontSize = 12.sp) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    (0..5).forEach { level ->
                        Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (stars == level) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                            .clickable { stars = level }, contentAlignment = Alignment.Center) {
                            Text(if (level == 0) nuiteeText(language,"Все", "All", "Barcha", "Барча") else "$level★",
                                fontSize = 11.sp, color = if (stars == level) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (sandbox) item { Text("Nuitee sandbox", fontSize = 12.sp) }
            if (pricesStale || rateError != null) item { Text(nuiteeText(language,
                "Для точной цены откройте номер и обновите тариф.", "Open a room to verify its current price.",
                "Xona narxini tekshiring.", "Хона нархини текширинг."), fontSize = 12.sp) }
            if (loading) item { CircularProgressIndicator(Modifier.padding(10.dp)) }
            if (!loading && hotels.isEmpty()) item {
                Column {
                    Text(rateError ?: nuiteeText(language,"Отелей нет на эти даты", "No stays for these dates", "Mehmonxona yo‘q", "Меҳмонхона йўқ"))
                    TextButton(onClick = { refresh++ }) { Text(nuiteeText(language,"Повторить", "Retry", "Yana", "Яна")) }
                }
            }
            items(hotels.filter { stars == 0 || it.roundedStars == stars }, key = { "journey-${it.id}" }) { hotel ->
                NuiteeListingCard(hotel, language, pricesStale, favorite = hotel.id in favorites,
                    onFavorite = {
                        favorites = if (hotel.id in favorites) favorites - hotel.id else favorites + hotel.id
                        favoritePrefs.edit().putStringSet("hotels", favorites).apply()
                    }, onOpen = { if (ready) selectedHotel = hotel })
            }
        }
    }
    selectedHotel?.let { hotel ->
        NuiteeHotelDetailSheet(language, service, hotel, stay.checkIn, stay.checkOut,
            roomAdults, roomChildren, onDismiss = { selectedHotel = null },
            onRate = { rate ->
                if (NuiteeRules.validRoomRate(rate)) journey.chooseNuiteeRoom(NuiteeRoomSelection(
                    roomKey, city, roomIndex, hotel, rate, stay.checkIn, stay.checkOut, roomAdults, roomChildren))
                selectedHotel = null
            }, onPending = {
                journey.chooseNuiteePending(NuiteePendingHotel(
                    roomKey, city, roomIndex, hotel, stay.checkIn, stay.checkOut, roomAdults, roomChildren))
                selectedHotel = null
            })
    }
}
