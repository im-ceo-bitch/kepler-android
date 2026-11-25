@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.iumrah.beta.ui.smart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.data.smart.SmartConfiguratorService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.nuitee.NuiteeRules
import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.models.nuitee.NuiteeHotel
import com.iumrah.beta.ui.flights.IumrahGuidedFlightsScreen
import com.iumrah.beta.ui.hotels.*
import com.iumrah.beta.ui.components.IumrahPrimaryButton

/** An independent draft, discarded on cancel. Only a successfully re-quoted edit is adopted. */
@Composable internal fun SmartPackageEditDialog(language: AppLanguage, service: SmartConfiguratorService,
    airports: AirportSearchService, previous: JourneyStore, kind: String, onDismiss: () -> Unit, onApply: (JourneyStore) -> Unit,
    reviewEditor: JourneyStore? = null) {
    val candidate = remember(previous, kind, reviewEditor) { reviewEditor ?: previous.smartEditCopy() }
    val state by candidate.state.collectAsState()
    val original by previous.state.collectAsState()
    val context = LocalContext.current
    val favoritePrefs = remember(context) { context.getSharedPreferences("iumrah_nuitee_favorites", 0) }
    var favorites by remember(favoritePrefs) { mutableStateOf(favoritePrefs.getStringSet("hotels", emptySet()).orEmpty().toSet()) }
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (kind == "flights") {
            val chrome = remember { AppChromeStore() }
            val discovery = remember(service) { service.editDiscovery }
            IumrahGuidedFlightsScreen(language, candidate, service.editCurated, discovery, airports, chrome,
                lockParty = true, onBack = onDismiss, onComplete = { onApply(candidate) })
        } else if (kind == "transfer") {
            Scaffold(bottomBar = { Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp)) {
                IumrahPrimaryButton(title = t("Пересчитать пакет", "Reprice package", "Paketni qayta hisoblash", "Пакетни қайта ҳисоблаш"), onClick = { onApply(candidate) })
            } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { TextButton(onClick = onDismiss) { Text(t("Отмена", "Cancel", "Bekor qilish", "Бекор қилиш")) }; Text(t("Изменить трансфер", "Change transfer", "Transferni o‘zgartirish", "Трансферни ўзгартириш"), fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                items(TransferVehicleKind.entries.filter { it.passengerCapacity >= original.trip.travelerCount }) { vehicle ->
                    val art = when (vehicle) { TransferVehicleKind.MALIBU -> R.drawable.transfer_malibu; TransferVehicleKind.CARNIVAL -> R.drawable.transfer_carnival; TransferVehicleKind.YUKON -> R.drawable.transfer_yukon }
                    Card(onClick = { candidate.chooseTransferVehicle(vehicle) }, modifier = Modifier.fillMaxWidth()) {
                        Image(painterResource(art), vehicle.modelName, Modifier.fillMaxWidth().height(130.dp), contentScale = ContentScale.Fit)
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioButton(selected = state.resolvedTransferVehicle == vehicle, onClick = { candidate.chooseTransferVehicle(vehicle) })
                            Column { Text(vehicle.modelName, fontWeight = FontWeight.Bold); Text("${vehicle.passengerCapacity} · ${vehicle.luggageCapacity}") }
                        }
                    }
                }
            } }
        } else {
            val prior = original.nuitee?.rooms.orEmpty().values.filter { it.city == kind }.sortedBy { it.roomIndex }
            var roomIndex by remember { mutableIntStateOf(0) }
            val room = prior.getOrNull(roomIndex)
            if (room == null) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) { TextButton(onClick=onDismiss) { Text(t("Закрыть", "Close", "Yopish", "Ёпиш")) } }
            } else {
                var stars by remember { mutableIntStateOf(0) }
                var query by remember(roomIndex) { mutableStateOf("") }
                var hotel by remember(roomIndex) { mutableStateOf<NuiteeHotel?>(null) }
                var chosenKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
                val selectedHotelId = state.nuitee?.rooms?.get("$kind:0")?.hotel?.id
                val page = rememberNuiteePagedCatalog(service.editHotels, kind, room.checkin, room.checkout, room.adults, stars, childrenAges = room.childrenAges)
                val allSelected = prior.all { it.key in chosenKeys && state.nuitee?.rooms?.get(it.key) != null }
                Scaffold(bottomBar = { if (allSelected) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp)) {
                    IumrahPrimaryButton(title = t("Пересчитать пакет", "Reprice package", "Paketni qayta hisoblash", "Пакетни қайта ҳисоблаш"), onClick = { onApply(candidate) })
                } }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { TextButton(onClick=onDismiss) { Text(t("Отмена", "Cancel", "Bekor qilish", "Бекор қилиш")) }
                        Text(if(kind=="makkah") t("Отель в Мекке", "Makkah hotel", "Makkadagi mehmonxona", "Маккадаги меҳмонхона") else t("Отель в Медине", "Madinah hotel", "Madinadagi mehmonxona", "Мадинадаги меҳмонхона"), fontSize=25.sp,fontWeight=FontWeight.Bold)
                        Text("${room.checkin} — ${room.checkout} · ${roomIndex + 1}/${prior.size}")
                        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(t("Название отеля", "Hotel name", "Mehmonxona nomi", "Меҳмонхона номи")) })
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (0..5).forEach { value -> FilterChip(selected = stars == value, onClick = { stars = value }, label = { Text(if(value==0) "★" else "$value") }) } }
                    }
                    if (page.stale) item { Text(t("Цены требуют проверки", "Prices need verification", "Narxlar tekshirilishi kerak", "Нархлар текширилиши керак")) }
                    if (page.loading) item { CircularProgressIndicator() }
                    if (page.error != null) item { TextButton(onClick = { page.retry() }) { Text(t("Повторить", "Retry", "Qayta urinish", "Қайта уриниш")) } }
                    val rows = if (roomIndex == 0) page.hotels else page.hotels.filter { it.id == selectedHotelId }.ifEmpty { listOfNotNull(state.nuitee?.rooms?.get("$kind:0")?.hotel) }
                    items(rows.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, key = { it.id }) { option -> NuiteeListingCard(option, language, page.stale, option.id in favorites, {
                        favorites = if (option.id in favorites) favorites - option.id else favorites + option.id
                        favoritePrefs.edit().putStringSet("hotels", favorites).apply()
                    }, { hotel = option }, nights = java.time.temporal.ChronoUnit.DAYS.between(room.checkin, room.checkout).toInt(), adults = room.adults, childrenCount = room.childrenAges.size, sandbox = page.sandbox) }
                    if (page.error == null && page.nextOffset != null && roomIndex == 0) item { OutlinedButton(enabled = !page.loading, onClick = page::loadMore) { Text(t("Показать ещё", "Load more", "Yana ko‘rsatish", "Яна кўрсатиш")) } }
                } }
                hotel?.let { option -> NuiteeHotelDetailSheet(language, service.editHotels, option, room.checkin, room.checkout, room.adults, room.childrenAges,
                    onDismiss = { hotel = null }, onRate = { rate, meals ->
                        if (NuiteeRules.validRoomRate(rate)) {
                            candidate.chooseNuiteeRoom(room.copy(hotel = option, rate = rate, meals = meals))
                            if (candidate.state.value.nuitee?.rooms?.get(room.key)?.rate == rate) {
                                chosenKeys = chosenKeys + room.key
                                if (roomIndex + 1 < prior.size) roomIndex++
                            }
                        }; hotel = null
                    }, allowMealSelection = true, initialMeals = room.meals) }
            }
        }
    }
}
