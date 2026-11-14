package com.iumrah.beta.ui.packageflow

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.ui.hotels.rememberNuiteeHotelService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.hotels.nuiteeText
import com.iumrah.beta.ui.hotels.nuiteeUSD
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Same review/request boundary as SwiftUI LocalJourneyReview; never creates an unpaid booking. */
@Composable
fun NuiteeJourneyReviewScreen(language: AppLanguage, journey: JourneyStore, account: IumrahAccountStore,
                              chrome: AppChromeStore, curated: com.iumrah.beta.data.flight.CuratedFlightRecommendationService,
                              packageEngine: com.iumrah.beta.data.hotel.RemotePackageEngineClient, hotelService: NuiteeHotelService = rememberNuiteeHotelService()) {
    val state by journey.state.collectAsState()
    val draft = state.nuitee
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val valid = draft?.let { com.iumrah.beta.domain.nuitee.NuiteeBookingRules.complete(state.trip, it) && it.flights.size == 2 } == true &&
        state.resolvedTransferVehicle.passengerCapacity >= state.trip.travelerCount
    val quote = state.quote?.takeIf { state.hasFinalGeneratorQuote && it.bookingAllowed == true }

    fun submit(confirm: Boolean = false) {
        if (!valid || sending || refreshing) return
        val accepted = if (confirm) quote else null
        scope.launch {
            sending = true; error = null
            try {
                val result = journey.prepareNuiteePackage(curated, packageEngine)
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it else error = it.localizedMessage ?: "PACKAGE_QUOTE_FAILED" }
                val fresh = result.getOrNull()
                if (accepted != null && fresh != null) {
                    if (accepted.totalPackagePrice.compareTo(fresh.totalPackagePrice) == 0 && accepted.pricePerPerson.compareTo(fresh.pricePerPerson) == 0 && accepted.currency == fresh.currency && journey.state.value.quote == fresh) chrome.openBookingCheckout()
                    else error = nuiteeText(language,"Стоимость изменилась. Проверьте новую цену.", "Price changed. Review the new total.", "Narx o‘zgardi. Yangi narxni tekshiring.", "Нарх ўзгарди. Янги нархни текширинг.")
                }
            } finally { sending = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = { chrome.back() }) { Text("‹") }
            Text(nuiteeText(language,"Ваша поездка", "Your trip", "Safaringiz", "Сафарингиз"),
                fontWeight = FontWeight.Bold, fontSize = 27.sp)
            Text("${state.trip.travelerCount} · ${state.trip.rooms} " +
                nuiteeText(language,"номеров", "room(s)", "xona", "хона"), fontSize = 13.sp)
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(quote?.let { nuiteeUSD(it.totalPackagePrice.toDouble()) } ?: nuiteeText(language,
                    "Итоговая стоимость", "Total package price", "Jami narx", "Жами нарх"), fontWeight = FontWeight.Bold, fontSize = 25.sp)
                Text(if (quote != null) nuiteeText(language,"Цена подтверждена сервером", "Server-verified price", "Server tasdiqlagan narx", "Сервер тасдиқлаган нарх") else
                    nuiteeText(language,"Рассчитайте пакет по выбранным компонентам", "Calculate the package for your selection", "Paket narxini hisoblang", "Пакет нархини ҳисобланг"), fontSize = 12.sp)

            }
        }
        item { Text(nuiteeText(language,"Авиабилеты", "Flights", "Reyslar", "Рейслар"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(draft?.flights.orEmpty(), key = { it.id }) { flight ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text("${flight.origin} → ${flight.destination}", fontWeight = FontWeight.Bold)
                Text("${flight.airline} · ${flight.flightNumber} · ${flight.departureAt}", fontSize = 12.sp)

            }
        }
        item { Text(nuiteeText(language,"Отели и номера", "Hotels & rooms", "Xonalar", "Хоналар"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(draft?.rooms?.toSortedMap()?.values?.toList().orEmpty(), key = { it.key }) { room ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text(room.hotel.name, fontWeight = FontWeight.Bold)
                Text("${room.city} · ${room.rate.roomName} · ${room.checkin} — ${room.checkout}", fontSize = 12.sp)
                Text(room.rate.mealPlan, fontSize = 13.sp)
                Text(room.rate.cancellationDescription.ifBlank { room.rate.cancellationTag }, fontSize = 12.sp)
            }
        }
        items(draft?.pending?.toSortedMap()?.values?.toList().orEmpty(), key = { "pending-${it.key}" }) { pending ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text(pending.hotel.name, fontWeight = FontWeight.Bold)
                Text(nuiteeText(language,"Без подтверждённой цены", "Awaiting provider price",
                    "Narx tasdiqlanmagan", "Нарх тасдиқланмаган"), fontSize = 12.sp)
            }
        }
        item {
            Text(nuiteeText(language,"Трансфер", "Transfer", "Transfer", "Трансфер"),
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf(TransferVehicleKind.CARNIVAL, TransferVehicleKind.YUKON).forEach { kind ->
                    OutlinedButton(onClick = { journey.chooseTransferVehicle(kind) },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(containerColor =
                            if (state.resolvedTransferVehicle == kind) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)) {
                        Text(kind.modelName, maxLines = 1)
                    }
                }
            }
            if (state.trip.scope == JourneyScope.MAKKAH_AND_MADINAH) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Haramain", Modifier.weight(1f))
                Switch(checked = state.haramainTrainSelected,
                    onCheckedChange = { journey.setHaramainTrainSelected(it) })
            }
        }
        item {
            if (refreshing) CircularProgressIndicator()
            OutlinedButton(enabled = !sending && !refreshing && draft?.rooms?.isNotEmpty() == true,
                onClick = {
                    scope.launch {
                        refreshing = true
                        // Failed recheck invalidates only the unchanged offer; no stale total is reused.
                        val snapshot = journey.state.value.nuitee?.rooms.orEmpty()
                        try { for ((key, room) in snapshot) {
                            val matched = try {
                                val response = hotelService.rates(room.hotel.id, room.checkin, room.checkout,
                                    room.adults, room.childrenAges, language.code)
                                if (response.cached) null else response.rates.firstOrNull {
                                    it.offerId == room.rate.offerId && it.roomId == room.rate.roomId
                                }
                            } catch (cancel: CancellationException) { throw cancel
                            } catch (_: Exception) { null }
                            journey.revalidateNuiteeRoom(key, room.rate.offerId, room.rate.roomId, matched, expected = room)
                        } } finally { refreshing = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                Text(nuiteeText(language,"Перепроверить цены номеров", "Revalidate room prices",
                    "Xona narxlarini tekshirish", "Хона нархларини текшириш"))
            }
        }
        item {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            if (quote != null) IumrahPrimaryButton(title = nuiteeText(language,"Забронировать", "Book", "Bron qilish", "Брон қилиш"),
                enabled = valid && !sending && !refreshing, onClick = { submit(confirm = true) })
            else IumrahPrimaryButton(title = if (sending) nuiteeText(language,"Рассчитываем…", "Calculating…", "Hisoblanmoqda…", "Ҳисобланмоқда…") else
                nuiteeText(language,"Рассчитать стоимость", "Calculate price", "Narxni hisoblash", "Нархни ҳисоблаш"),
                enabled = valid && !sending && !refreshing, onClick = { submit() })
            if (!valid) TextButton(onClick = { chrome.back() }) { Text(nuiteeText(language,"Проверить выбранные номера", "Check selected rooms", "Xonalarni tekshirish", "Хоналарни текшириш")) }
        }
    }
}
