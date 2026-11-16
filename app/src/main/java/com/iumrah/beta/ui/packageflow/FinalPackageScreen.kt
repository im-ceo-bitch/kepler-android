package com.iumrah.beta.ui.packageflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.domain.trip.TripStayPlanner
import com.iumrah.beta.domain.trip.TripStayWindow
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.flight.LiveFlightCandidate
import com.iumrah.beta.models.hotel.StorefrontFlightLeg
import com.iumrah.beta.ui.components.IumrahClientPackageMoment
import com.iumrah.beta.ui.components.selectedOrbitServices
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.hotels.PackageFlightLegCard
import com.iumrah.beta.ui.hotels.PackageHotelCard
import com.iumrah.beta.ui.smart.smartMoney
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** iOS FinalPackageView legacy review; tier comparison no longer appears on this page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinalPackageScreen(language: AppLanguage, journey: JourneyStore, hotelCatalog: HotelCatalogService,
                       packageEngine: RemotePackageEngineClient, chrome: AppChromeStore, unreadCount: Int = 0) {
    val state by journey.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val quote = state.quote?.takeIf { state.hasFinalGeneratorQuote && it.bookingAllowed != false }
    val needsMadinah = state.trip.scope == JourneyScope.MAKKAH_AND_MADINAH
    val roomsComplete = state.hasMakkahRoomSelection && (!needsMadinah || state.hasMadinahRoomSelection)
    val flightsComplete = state.selectedJourney?.let { !state.trip.isRoundTripFlight || it.inbound!=null } == true
    val hotelsComplete = state.hasRequiredHotels
    val canBook = quote!=null && roomsComplete && flightsComplete && hotelsComplete && state.transferSelectionConfirmed && state.resolvedTransferVehicle.passengerCapacity>=state.trip.travelerCount
    fun reprice() {
        if(busy || !roomsComplete || !flightsComplete || !hotelsComplete || !state.transferSelectionConfirmed || journey.state.value!=state) return
        busy=true; error=null
        scope.launch {
            try { journey.refreshTransferQuote(packageEngine::packageQuote) }
            catch(cancel: CancellationException) { throw cancel }
            catch(e: Exception) { error=reviewText(language,"Не удалось обновить стоимость поездки. Повторите расчёт.","Could not refresh the trip price. Retry pricing.","Safar narxini yangilab bo‘lmadi. Qayta hisoblang.","Сафар нархини янгилаб бўлмади. Қайта ҳисобланг.") }
            finally { busy=false }
        }
    }
    LaunchedEffect(state.trip,state.makkahHotel,state.makkahRoom,state.makkahRoomCategory,state.madinahHotel,state.madinahRoom,state.madinahRoomCategory,state.selectedJourney,state.selectedTransferVehicle,state.haramainTrainSelected,state.haramainTicketCount,state.transferSelectionConfirmed) {
        if(quote==null) reprice()
    }
    val reason=when {
        busy -> reviewText(language,"Обновляем стоимость…","Updating price…","Narx yangilanmoqda…","Нарх янгиланмоқда…")
        !roomsComplete -> reviewText(language,"Выберите номер","Choose room","Xona tanlash","Хона танлаш")
        !flightsComplete || !hotelsComplete -> reviewText(language,"Проверьте выбранные рейсы и отели перед бронированием.","Review your flights and hotels before booking.","Bron qilishdan oldin reyslar va mehmonxonalarni tekshiring.","Брон қилишдан олдин рейслар ва меҳмонхоналарни текширинг.")
        !state.transferSelectionConfirmed -> reviewText(language,"Подтвердите выбранный трансфер.","Confirm your transfer.","Transferni tasdiqlang.","Трансферни тасдиқланг.")
        quote==null -> reviewText(language,"Для продолжения обновите расчёт поездки.","Refresh the trip price to continue.","Davom etish uchun safar narxini yangilang.","Давом этиш учун сафар нархини янгиланг.")
        else -> null
    }
    Column(Modifier.fillMaxSize().background(generatorPageColor())) {
        PullToRefreshBox(isRefreshing=busy,onRefresh={reprice()},modifier=Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                item { ReviewHeader(language,state,quote,busy,chrome,unreadCount) {help=!help} }
                item { Box(Modifier.padding(horizontal=18.dp)) { IumrahClientPackageMoment(language,selectedOrbitServices(state)) } }
                item { Box(Modifier.padding(horizontal=18.dp)) { ReviewHelp(language,quote!=null,help) {help=!help} } }
                if(quote==null) item { Box(Modifier.padding(horizontal=18.dp)) { ReviewPricingStatus(language,busy) } }
                item { Box(Modifier.padding(horizontal=18.dp)) { ReviewSection(reviewText(language,"Авиабилеты","Flights","Aviachiptalar","Авиачипталар")) } }
                state.selectedJourney?.let { flight ->
                    item { Box(Modifier.padding(horizontal=18.dp)) { ReviewFlight(language,state,flight.outbound,true) } }
                    if(state.trip.isRoundTripFlight) flight.inbound?.let { leg -> item { Box(Modifier.padding(horizontal=18.dp)) { ReviewFlight(language,state,leg,false) } } }
                }
                item { Box(Modifier.padding(horizontal=18.dp)) { ReviewSection(reviewText(language,"Отели и номера","Hotels and rooms","Mehmonxonalar va xonalar","Меҳмонхоналар ва хоналар")) } }
                val windows=TripStayPlanner.windows(state.trip)
                state.makkahHotel?.let { hotel -> item { Box(Modifier.padding(horizontal=18.dp)) { ReviewCatalogHotel(language,hotel,state.makkahRoom?.name ?: state.makkahRoomCategory?.displayName,windows.makkah) {chrome.openConfiguratorHotelDetail(hotel.id,"makkah")} } } }
                if(needsMadinah) state.madinahHotel?.let { hotel -> item { Box(Modifier.padding(horizontal=18.dp)) { ReviewCatalogHotel(language,hotel,state.madinahRoom?.name ?: state.madinahRoomCategory?.displayName,windows.madinah ?: windows.makkah) {chrome.openConfiguratorHotelDetail(hotel.id,"madinah")} } } }
                item { Box(Modifier.padding(horizontal=18.dp)) { ReviewServices(language,state) {showTransfer=true} } }
                error?.let { message -> item { Text(message,Modifier.padding(horizontal=18.dp),color=MaterialTheme.colorScheme.error,fontSize=13.sp) } }
            }
        }
        ReviewBookingBar(language,quote,false,busy,reason,
            when { !hotelsComplete || !roomsComplete -> reviewText(language,"Выбрать номер","Choose room","Xona tanlash","Хона танлаш")
                !flightsComplete -> reviewText(language,"Выбрать рейс","Choose flight","Reys tanlash","Рейс танлаш")
                !state.transferSelectionConfirmed -> reviewText(language,"Подтвердить трансфер","Confirm transfer","Transferni tasdiqlash","Трансферни тасдиқлаш")
                quote!=null -> reviewText(language,"Забронировать пакет","Book package","Paketni bron qilish","Пакетни брон қилиш")
                else -> reviewText(language,"Повторить расчёт","Retry pricing","Qayta hisoblash","Қайта ҳисоблаш") },!busy && (quote==null || canBook || !roomsComplete || !hotelsComplete || !flightsComplete || !state.transferSelectionConfirmed)) {
            if(journey.state.value==state) when {
                !state.hasMakkahRoomSelection || state.makkahHotel==null -> state.makkahHotel?.let {chrome.openConfiguratorHotelDetail(it.id,"makkah")} ?: chrome.openConfiguratorHotelSelection("makkah")
                needsMadinah && (!state.hasMadinahRoomSelection || state.madinahHotel==null) -> state.madinahHotel?.let {chrome.openConfiguratorHotelDetail(it.id,"madinah")} ?: chrome.openConfiguratorHotelSelection("madinah")
                !flightsComplete -> chrome.openFlights()
                !state.transferSelectionConfirmed -> chrome.openTransferSelection()
                canBook -> chrome.openBookingCheckout()
                else -> reprice()
            }
        }
    }
    if(showTransfer) ReviewTransferSheet(language,state,journey,!busy,{showTransfer=false},editable=false) { showTransfer=false; chrome.openTransferSelection() }
}

@Composable internal fun ReviewFlight(l:AppLanguage,state:JourneyState,flight:LiveFlightCandidate,outbound:Boolean) {
    val leg=StorefrontFlightLeg(flight.airline,flight.flightNumber,flight.airlineCode.orEmpty(),flight.origin,flight.destination,flight.departureAt.toString(),flight.arrivalAt.toString(),flight.durationMinutes,flight.stops,flight.cabinClass ?: "economy")
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        PackageFlightLegCard(leg,if(outbound) reviewText(l,"Туда","Outbound","Borish","Бориш") else reviewText(l,"Обратно","Return","Qaytish","Қайтиш"),l)
        val selected=state.selectedJourney
        val fare=com.iumrah.beta.domain.pricing.ReviewFareDisplay.flight(flight,selected?.outbound,selected?.inbound?.takeIf { state.trip.isRoundTripFlight },state.trip.travelerCount)
        fare?.let {
            val unit=if(it.average) reviewText(l,"в среднем на паломника","average per pilgrim","ziyoratchiga o‘rtacha","зиёратчига ўртача") else reviewText(l,"на человека","per person","kishi uchun","киши учун")
            ReviewUnitPrice(l,it.amount,it.currency,(if(it.roundTrip) reviewText(l,"Туда и обратно","Round trip","Borish va qaytish","Бориш ва қайтиш")+" · " else "")+unit)
        }
    }
}

@Composable private fun ReviewCatalogHotel(l:AppLanguage,hotel:HotelSummary,room:String?,window:TripStayWindow,onOpen:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        ReviewLabel(CupertinoSymbol.Calendar,"${window.checkIn} – ${window.checkOut}")
        PackageHotelCard(hotel,window.nights,l,onOpen)
        IumrahPressable(onClick=onOpen,modifier=Modifier.fillMaxWidth().height(48.dp),cornerRadius=16.dp,background=MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize().padding(horizontal=12.dp),contentAlignment=Alignment.Center) { Text(room ?: reviewText(l,"Выбрать номер","Choose room","Xona tanlash","Хона танлаш"),fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=2) }
        }
        hotel.price?.takeIf { it.isFresh() }?.nightlyUSD?.takeIf { it.isFinite() && it>0 }?.let {
            ReviewUnitPrice(l,java.math.BigDecimal.valueOf(it),"USD",reviewText(l,"Тариф каталога · за номер / ночь","Catalog rate · per room / night","Katalog tarifi · xona / tun","Каталог тарифи · хона / тун"))
        }
    }
}

@Composable
internal fun RefundPolicyCard(language: AppLanguage) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFFFF4E8)), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.ShieldCheck, null, modifier = Modifier.size(18.dp), tint = Color(0xFF9A5B10))
            }
            Text(finalText(language, "Условия возврата пакета", "Package refund policy", "Paketni qaytarish shartlari", "Пакетни қайтариш шартлари"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            finalText(language, "Авиабилеты и подтверждённые тарифы отелей могут быть невозвратными. Возврат по дополнительным услугам рассчитывается отдельно по фактически неоказанной части.", "Flights and confirmed hotel rates may be non-refundable. Refunds for additional services are calculated separately for the unused portion.", "Aviabiletlar va tasdiqlangan mehmonxona tariflari qaytarilmasligi mumkin. Qo‘shimcha xizmatlar bo‘yicha qaytarish foydalanilmagan qism uchun alohida hisoblanadi.", "Авиабилетлар ва тасдиқланган меҳмонхона тарифлари қайтарилмаслиги мумкин. Қўшимча хизматлар бўйича қайтариш фойдаланилмаган қисм учун алоҳида ҳисобланади."),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .57f),
        )
    }
}

@Composable
internal fun ManualPaymentCard(language: AppLanguage) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFEFF4FF)), contentAlignment = Alignment.Center) {
            Icon(CupertinoSymbol.ShieldCheck, null, modifier = Modifier.size(19.dp), tint = Color(0xFF3559A8))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(finalText(language, "Оплата после подтверждения", "Payment after confirmation", "Tasdiqlangandan keyin to‘lov", "Тасдиқлангандан кейин тўлов"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                finalText(language, "После проверки наличия iumrah подтвердит пакет и отправит инструкции для оплаты. До подтверждения списания не будет.", "After availability is checked, iumrah confirms the package and sends payment instructions. Nothing is charged before confirmation.", "Mavjudlik tekshirilgach, iumrah paketni tasdiqlaydi va to‘lov bo‘yicha ko‘rsatmalarni yuboradi. Tasdiqlashgacha mablag‘ yechilmaydi.", "Мавжудлик текширилгач, iumrah пакетни тасдиқлайди ва тўлов бўйича кўрсатмаларни юборади. Тасдиқлашгача маблағ ечилмайди."),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
            )
        }
    }
}

internal fun mealsSummary(language: AppLanguage, state: JourneyState): String {
    val selectable = state.trip.packageTier == PackageTier.COMFORT || state.trip.packageTier == PackageTier.LUXURY
    val selection = state.trip.effectiveMealSelection
    val makkahCount = if (selectable) 1 + (if (selection.makkahLunch) 1 else 0) + (if (selection.makkahDinner) 1 else 0) else 3
    val madinahCount = if (selectable) 1 + (if (selection.madinahDinner) 1 else 0) else 2
    val makkah = mealsPerDay(language, makkahCount)
    return if (state.trip.scope == JourneyScope.MAKKAH_AND_MADINAH) {
        finalText(
            language,
            "Мекка · $makkah · Медина · ${mealsPerDay(language, madinahCount)}",
            "Makkah · $makkah · Madinah · ${mealsPerDay(language, madinahCount)}",
            "Makka · $makkah · Madina · ${mealsPerDay(language, madinahCount)}",
            "Макка · $makkah · Мадина · ${mealsPerDay(language, madinahCount)}",
        )
    } else {
        finalText(language, "Мекка · $makkah", "Makkah · $makkah", "Makka · $makkah", "Макка · $makkah")
    }
}

private fun mealsPerDay(language: AppLanguage, count: Int): String = when (language) {
    AppLanguage.RUSSIAN -> if (count == 1) "1 раз в день" else "$count раза в день"
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "$count meals/day"
    AppLanguage.UZBEK -> "kuniga $count mahal"
    AppLanguage.UZBEK_CYRILLIC -> "кунига $count маҳал"
}

private fun finalText(language: AppLanguage, ru: String, en: String, uz: String, uzCyrl: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> uzCyrl
}
