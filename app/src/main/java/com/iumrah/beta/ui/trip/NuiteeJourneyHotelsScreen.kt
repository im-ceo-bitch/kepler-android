@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.iumrah.beta.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.iumrah.beta.ui.hotels.rememberNuiteePagedCatalog
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.nuitee.NuiteeRoomSelection
import com.iumrah.beta.domain.nuitee.NuiteePendingHotel
import com.iumrah.beta.domain.nuitee.NuiteeRules
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.TripStayPlanner
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
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
                               service: NuiteeHotelService = rememberNuiteeHotelService(),
                               reviewEditing: Boolean = false,
                               onBack: () -> Unit = { chrome.back() },
                               onComplete: () -> Unit = { chrome.openNuiteeJourneyReview() }) {
    val context = LocalContext.current
    val favoritePrefs = remember { context.getSharedPreferences("iumrah_nuitee_favorites", 0) }
    var favorites by remember { mutableStateOf(favoritePrefs.getStringSet("hotels", emptySet()).orEmpty().toSet()) }
    val state by journey.state.collectAsState()
    val trip = state.trip
    val draft = state.nuitee
    var city by remember { mutableStateOf("makkah") }
    var roomIndex by remember { mutableIntStateOf(0) }
    var stars by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val childrenAges = state.childAges.takeIf { it.size==trip.children+trip.infants }
        ?: draft?.rooms?.values?.filter { it.city=="makkah" }?.sortedBy { it.roomIndex }?.let { rooms ->
            List(trip.children+trip.infants){i->rooms.firstOrNull{it.roomIndex==i%trip.rooms}?.childrenAges?.getOrNull(i/trip.rooms) ?: -1}
        } ?: List(trip.children+trip.infants){-1}
    val windows = TripStayPlanner.windows(trip)
    val stay = if (city == "madinah") windows.madinah ?: windows.makkah else windows.makkah
    val roomAdults = trip.adults / trip.rooms + if (roomIndex < trip.adults % trip.rooms) 1 else 0
    val roomChildren = childrenAges.withIndex().filter { it.index % trip.rooms == roomIndex }.map { it.value }
    val ready = childrenAges.size == trip.children + trip.infants && childrenAges.all { it in 0..17 } && roomAdults > 0
    val roomKey = "$city:$roomIndex"
    var selectedHotel by remember(roomKey, stay.checkIn, stay.checkOut, roomAdults, roomChildren) { mutableStateOf<NuiteeHotel?>(null) }
    var partyExpanded by remember { mutableStateOf(!ready) }
    val listState = rememberLazyListState()
    LaunchedEffect(roomKey) { listState.scrollToItem(0) }
    val allComplete = draft?.let { com.iumrah.beta.domain.nuitee.NuiteeBookingRules.complete(trip, it) } == true
    val page = rememberNuiteePagedCatalog(service, city, stay.checkIn, stay.checkOut, roomAdults.coerceAtLeast(1), stars, refresh = refresh, childrenAges = roomChildren)
    val hotels = page.hotels
    val rateError = page.error ?: page.rateError
    val loading = page.loading
    val sandbox = page.sandbox
    val pricesStale = page.stale

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        if (allComplete) Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
            IumrahPrimaryButton(title = if (reviewEditing) nuiteeText(language,"Применить", "Apply", "Qo‘llash", "Қўллаш") else nuiteeText(language,"Выбрать трансфер", "Choose transfer",
                "Transferni tanlash", "Трансферни танлаш"), onClick = onComplete)
        }
    }) { scaffoldPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(scaffoldPadding), state = listState, contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF111111)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { CupertinoIcon(CupertinoSymbol.ChevronLeft, null, Modifier.size(22.dp), Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(nuiteeText(language,"Отели и номера", "Hotels & rooms", "Mehmonxona va xonalar", "Меҳмонхона ва хоналар"),
                            fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
                        Text("${trip.travelerCount} · ${trip.rooms} " + nuiteeText(language,"номер(ов)", "room(s)", "xona", "хона"), fontSize = 13.sp, color = Color.White.copy(alpha = .75f))
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { partyExpanded = !partyExpanded }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(nuiteeText(language,"Состав поездки", "Travel party", "Sayohatchilar", "Саёҳатчилар"), fontWeight = FontWeight.Bold)
                            Text("${stay.checkIn} — ${stay.checkOut} · ${trip.travelerCount}", fontSize = 12.sp)
                        }
                        CupertinoIcon(if (partyExpanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown, null, Modifier.size(22.dp))
                    }
                    if (partyExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nuiteeText(language,"Номеров", "Rooms", "Xonalar", "Хоналар"), Modifier.weight(1f))
                        TextButton(enabled = !reviewEditing && trip.rooms > (trip.adults+4)/5, onClick = { journey.changeNuiteeRoomCount(trip.rooms - 1); roomIndex = 0 }) { CupertinoIcon(CupertinoSymbol.Minus, null, Modifier.size(18.dp)) }
                        Text("${trip.rooms}")
                        TextButton(enabled = !reviewEditing && trip.rooms < trip.adults, onClick = { journey.changeNuiteeRoomCount(trip.rooms + 1); roomIndex = 0 }) { CupertinoIcon(CupertinoSymbol.Plus, null, Modifier.size(18.dp)) }
                    }
                    childrenAges.forEachIndexed { idx, age ->
                        var open by remember(idx) { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nuiteeText(language,"Возраст ребёнка", "Child's age", "Bola yoshi", "Бола ёши") + " ${idx + 1}", Modifier.weight(1f))
                            Box {
                                OutlinedButton(onClick = { open = true }) { Text(if (age < 0) "—" else "$age") }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    (0..17).forEach { value -> DropdownMenuItem(text = { Text("$value") }, onClick = {
                                        if (age != value) { journey.updateGuestAges(childrenAges.toMutableList().also { it[idx] = value }) }; open = false
                                    }) }
                                }
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
                    "Выберите актуальный номер", "Select a current room offer",
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
            items(hotels, key = { "journey-${it.id}" }) { hotel ->
                NuiteeListingCard(hotel, language, pricesStale, favorite = hotel.id in favorites,
                    nights = java.time.temporal.ChronoUnit.DAYS.between(stay.checkIn, stay.checkOut).toInt(), adults = roomAdults, sandbox = sandbox,
                    onFavorite = {
                        favorites = if (hotel.id in favorites) favorites - hotel.id else favorites + hotel.id
                        favoritePrefs.edit().putStringSet("hotels", favorites).apply()
                    }, onOpen = { if (ready) selectedHotel = hotel })
            }
            if (page.error != null && hotels.isNotEmpty()) item {
                OutlinedButton(enabled = !loading, onClick = page::retry, modifier = Modifier.fillMaxWidth()) { Text(nuiteeText(language, "Повторить", "Retry", "Qayta urinish", "Қайта уриниш")) }
            }
            if (page.error == null && page.nextOffset != null && hotels.isNotEmpty()) item { OutlinedButton(enabled = !loading, onClick = page::loadMore, modifier = Modifier.fillMaxWidth()) { Text(nuiteeText(language,"Показать ещё", "Load more", "Yana ko‘rsatish", "Яна кўрсатиш")) } }
        }
    }
    selectedHotel?.let { hotel ->
        NuiteeHotelDetailSheet(language, service, hotel, stay.checkIn, stay.checkOut,
            roomAdults, roomChildren, onDismiss = { selectedHotel = null },
            onRate = { rate, meals ->
                if (NuiteeRules.validRoomRate(rate)) {
                    journey.chooseNuiteeRoom(NuiteeRoomSelection(
                        roomKey, city, roomIndex, hotel, rate, stay.checkIn, stay.checkOut, roomAdults, roomChildren, meals))
                    val updated = journey.state.value
                    if (updated.nuitee?.rooms?.get(roomKey)?.rate == rate) {
                        val next = updated.nuitee?.let { draft -> (listOf(city) + draft.requiredCities(trip).filter { it != city }).flatMap { c -> (0 until trip.rooms).map { c to it } }.firstOrNull { (c, i) -> "$c:$i" !in draft.rooms } }
                        if (next != null) { city = next.first; roomIndex = next.second; stars = 0 }
                        else if (!reviewEditing && updated.nuitee?.let { com.iumrah.beta.domain.nuitee.NuiteeBookingRules.complete(trip, it) } == true) onComplete()
                    }
                }
                selectedHotel = null
            }, allowMealSelection = true)
    }
}
