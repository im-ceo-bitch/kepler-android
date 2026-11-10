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
import com.iumrah.beta.data.care.IumrahCarePackageRequest
import com.iumrah.beta.data.care.IumrahCarePackageRequestResponse
import com.iumrah.beta.data.care.IumrahCareRequestService
import com.iumrah.beta.data.care.NuiteeCareConfiguration
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.nuitee.NuiteeJourneyPricing
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
                              chrome: AppChromeStore, hotelService: NuiteeHotelService = remember { NuiteeHotelService() }) {
    val state by journey.state.collectAsState()
    val accountState by account.state.collectAsState()
    val draft = state.nuitee
    val estimate = draft?.let {
        NuiteeJourneyPricing.estimate(state.trip, it, state.selectedTransferVehicle,
            state.haramainTrainSelected, state.haramainTicketCount, state.haramainFareClass)
    }
    val scope = rememberCoroutineScope()
    var firstName by remember(accountState.account) { mutableStateOf(accountState.account?.firstName.orEmpty()) }
    var lastName by remember(accountState.account) { mutableStateOf(accountState.account?.lastName.orEmpty()) }
    var phone by remember(accountState.account) { mutableStateOf(accountState.account?.phone.orEmpty()) }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<IumrahCarePackageRequestResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val care = remember { IumrahCareRequestService() }
    val valid = draft != null && draft.roomsComplete(state.trip) && draft.flights.size == 2

    fun submit() {
        if (!valid || sending || firstName.isBlank() || phone.filter(Char::isDigit).length < 7) return
        val snapshot = state
        scope.launch {
            sending = true; error = null
            try {
                val request = IumrahCarePackageRequest(
                    locale = language.code, firstName = firstName.trim(), lastName = lastName.trim(),
                    phone = phone.trim(), telegram = "", accountID = accountState.account?.iumrahID,
                    originCode = snapshot.trip.originCode, timingMode = "exact_dates",
                    exactStartDate = snapshot.trip.departureDate.toString(), exactEndDate = snapshot.trip.returnDate.toString(),
                    adults = snapshot.trip.adults, children = snapshot.trip.children, infants = snapshot.trip.infants,
                    rooms = snapshot.trip.rooms,
                    scope = if (snapshot.trip.scope == JourneyScope.MAKKAH_AND_MADINAH) "makkah_madinah" else "makkah",
                    firstSaudiCity = snapshot.trip.arrivalAirport.name.lowercase(), priority = "balanced",
                    hotelClass = snapshot.trip.hotelStars,
                    transferPreference = if (snapshot.resolvedTransferVehicle == TransferVehicleKind.YUKON) "vip" else "comfortable",
                    directFlightsPreferred = snapshot.nuitee!!.flights.all { it.stops == 0 },
                    checkedBaggagePreferred = true, includeZiyarat = true, includeESIM = false,
                    guidePreference = "live", notes = "Выбранные рейсы и комнаты Nuitee в configuration; оценка не является бронью. Требуется подтверждение доступности и тарифа.",
                    configuration = NuiteeCareConfiguration.from(snapshot),
                )
                val response = care.submit(request, account.bearerToken)
                if (response.ok) result = response else error = "Request was not accepted"
            } catch (cancel: CancellationException) { throw cancel
            } catch (ex: Exception) { error = ex.localizedMessage }
            sending = false
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
                Text(estimate?.let { nuiteeUSD(it.totalUsd.toDouble()) } ?: nuiteeText(language,
                    "Стоимость уточняется", "Price to be confirmed", "Narx aniqlanmoqda", "Нарх аниқланмоқда"),
                    fontWeight = FontWeight.Bold, fontSize = 25.sp)
                Text(nuiteeText(language,
                    "Предварительный расчёт, не подтверждённая бронь.",
                    "Preliminary estimate, not a confirmed booking.",
                    "Taxminiy narx. Bron tasdiqlanmagan.", "Тахминий нарх. Брон тасдиқланмаган."),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (draft?.pending?.isNotEmpty() == true) Text(nuiteeText(language,
                    "Отели на уточнении исключены из суммы.", "Pending hotels are excluded from the estimate.",
                    "Aniqlanayotgan mehmonxonalar kiritilmagan.", "Аниқланаётган меҳмонхоналар киритилмаган."),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                estimate?.lines?.forEach { line ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(line.title, Modifier.weight(1f), fontSize = 12.sp)
                        Text(nuiteeUSD(line.usd.toDouble()), fontSize = 12.sp)
                    }
                }
            }
        }
        item { Text(nuiteeText(language,"Авиабилеты", "Flights", "Reyslar", "Рейслар"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(draft?.flights.orEmpty(), key = { it.id }) { flight ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text("${flight.origin} → ${flight.destination}", fontWeight = FontWeight.Bold)
                Text("${flight.airline} · ${flight.flightNumber} · ${flight.departureAt}", fontSize = 12.sp)
                Text(flight.adultFareUSD?.let(::nuiteeUSD) ?: nuiteeText(language,
                    "Цена билета уточняется", "Airfare to be confirmed", "Chipta narxi tekshiriladi", "Чипта нархи текширилади"), fontSize = 12.sp)
            }
        }
        item { Text(nuiteeText(language,"Отели и номера", "Hotels & rooms", "Xonalar", "Хоналар"),
            fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(draft?.rooms?.toSortedMap()?.values?.toList().orEmpty(), key = { it.key }) { room ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
                Text(room.hotel.name, fontWeight = FontWeight.Bold)
                Text("${room.city} · ${room.rate.roomName} · ${room.checkin} — ${room.checkout}", fontSize = 12.sp)
                Text(nuiteeUSD(room.rate.price) + " · ${room.rate.mealPlan}", fontSize = 13.sp)
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
            OutlinedButton(enabled = !refreshing && draft?.rooms?.isNotEmpty() == true,
                onClick = {
                    scope.launch {
                        refreshing = true
                        // Failed recheck invalidates only the unchanged offer; no stale total is reused.
                        val snapshot = journey.state.value.nuitee?.rooms.orEmpty()
                        for ((key, room) in snapshot) {
                            val matched = try {
                                val response = hotelService.rates(room.hotel.id, room.checkin, room.checkout,
                                    room.adults, room.childrenAges, language.code)
                                if (response.cached) null else response.rates.firstOrNull {
                                    it.offerId == room.rate.offerId && it.roomId == room.rate.roomId
                                }
                            } catch (cancel: CancellationException) { throw cancel
                            } catch (_: Exception) { null }
                            journey.revalidateNuiteeRoom(key, room.rate.offerId, room.rate.roomId, matched)
                        }
                        refreshing = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                Text(nuiteeText(language,"Перепроверить цены номеров", "Revalidate room prices",
                    "Xona narxlarini tekshirish", "Хона нархларини текшириш"))
            }
        }
        item {
            if (result == null) {
                Text(nuiteeText(language,"Контактные данные", "Contact details", "Aloqa", "Алоқа"),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(firstName, { firstName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(nuiteeText(language,"Имя", "First name", "Ism", "Исм")) })
                OutlinedTextField(lastName, { lastName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(nuiteeText(language,"Фамилия", "Last name", "Familiya", "Фамилия")) })
                OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(nuiteeText(language,"Телефон", "Phone", "Telefon", "Телефон")) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                IumrahPrimaryButton(title = if (sending) nuiteeText(language,"Отправка…", "Sending…", "Yuborilmoqda…", "Юборилмоқда…")
                    else nuiteeText(language,"Отправить заявку", "Submit enquiry", "So‘rov yuborish", "Сўров юбориш"),
                    enabled = valid && !sending && firstName.isNotBlank() && phone.filter(Char::isDigit).length >= 7,
                    onClick = ::submit)
                Text(nuiteeText(language, "Оплата пока не требуется. Доступность подтвердит поддержка.",
                    "No payment required yet. Support will confirm availability.",
                    "Hozir to‘lov talab qilinmaydi.", "Ҳозир тўлов талаб қилинмайди."), fontSize = 12.sp)
            } else {
                Text(nuiteeText(language,"Заявка отправлена", "Enquiry submitted", "So‘rov yuborildi", "Сўров юборилди"), fontWeight = FontWeight.Bold)
                Text(result?.requestID.orEmpty())
            }
        }
    }
}
