package com.iumrah.beta.ui.packageflow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.ui.hotels.rememberNuiteeHotelService
import com.iumrah.beta.ui.hotels.NuiteeHotelDetailSheet
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.nuitee.NuiteeRoomSelection
import com.iumrah.beta.domain.nuitee.NuiteeBookingRules
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.models.nuitee.NuiteeMealChoices
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.components.IumrahClientPackageMoment
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.components.selectedOrbitServices
import com.iumrah.beta.ui.smart.SmartFlightCard
import com.iumrah.beta.ui.smart.smartMoney
import com.iumrah.beta.ui.generator.generatorCardColor
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.temporal.ChronoUnit

/** SwiftUI FinalPackageView local-room branch; retain Android's signed-quote checkout boundary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuiteeJourneyReviewScreen(language: AppLanguage, journey: JourneyStore, account: IumrahAccountStore,
                              chrome: AppChromeStore, curated: com.iumrah.beta.data.flight.CuratedFlightRecommendationService,
                              packageEngine: com.iumrah.beta.data.hotel.RemotePackageEngineClient, hotelService: NuiteeHotelService = rememberNuiteeHotelService(), unreadCount: Int = 0) {
    val state by journey.state.collectAsState()
    val draft = state.nuitee
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var help by remember { mutableStateOf(false) }
    var transferSheet by remember { mutableStateOf(!state.transferSelectionConfirmed && draft != null) }
    var editing by remember { mutableStateOf<NuiteeRoomSelection?>(null) }
    var editKind by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = (context.applicationContext as com.iumrah.beta.IumrahApplication).container
    val editorService = remember(curated,hotelService,packageEngine) { com.iumrah.beta.data.smart.SmartConfiguratorService(curated,hotelService,packageEngine) }
    val roomsComplete = draft?.let { NuiteeBookingRules.complete(state.trip, it) && it.flights.size == 2 } == true
    val valid = roomsComplete && state.resolvedTransferVehicle.passengerCapacity >= state.trip.travelerCount
    val quote = state.quote?.takeIf { state.hasFinalGeneratorQuote && it.bookingAllowed == true }

    fun submit(confirm: Boolean = false) {
        if (confirm && !state.priceChangeAccepted) return
        if (!valid || sending || !journey.state.value.transferSelectionConfirmed || journey.state.value != state) return
        val accepted = if (confirm) quote?.takeIf { it.hasVerifiedBreakdown && it.allowsOccupancy(state.trip.adults,state.trip.rooms) } else null
        sending = true; error = null
        scope.launch {
            try {
                val result = journey.prepareNuiteePackage(curated, packageEngine)
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it else error = it.localizedMessage ?: "PACKAGE_QUOTE_FAILED" }
                val fresh = result.getOrNull()
                if (accepted != null && fresh != null) {
                    if (fresh.hasVerifiedBreakdown && fresh.allowsOccupancy(state.trip.adults,state.trip.rooms) && accepted.totalPackagePrice.compareTo(fresh.totalPackagePrice) == 0 && accepted.pricePerPerson.compareTo(fresh.pricePerPerson) == 0 && accepted.currency == fresh.currency && journey.state.value.quote == fresh && journey.state.value.nuitee == draft) chrome.openBookingCheckout()
                    else error = reviewText(language,"Стоимость изменилась. Проверьте новую цену.", "Price changed. Review the new total.", "Narx o‘zgardi. Yangi narxni tekshiring.", "Нарх ўзгарди. Янги нархни текширинг.")
                }
            } finally { sending = false }
        }
    }
    // Match the iOS automatic quote refresh when the selected components change.
    LaunchedEffect(draft,state.resolvedTransferVehicle,state.haramainTrainSelected,state.haramainTicketCount,state.transferSelectionConfirmed) {
        if (quote?.hasVerifiedBreakdown != true && valid && state.transferSelectionConfirmed && !sending) submit()
    }
    val reason = when {
        sending -> reviewText(language,"Обновляем стоимость…","Updating price…","Narx yangilanmoqda…","Нарх янгиланмоқда…")
        !roomsComplete -> reviewText(language,"Выберите номер","Choose room","Xona tanlash","Хона танлаш")
        !state.transferSelectionConfirmed || !valid -> reviewText(language,"Подтвердите выбранный трансфер.","Confirm your transfer.","Transferni tasdiqlang.","Трансферни тасдиқланг.")
        quote != null && !quote.allowsOccupancy(state.trip.adults,state.trip.rooms) -> reviewText(language,"Требуется согласование размещения.","Room occupancy approval required.","Joylashuvni kelishish kerak.","Жойлашувни келишиш керак.")
        quote?.hasVerifiedBreakdown != true -> reviewText(language,"Для продолжения обновите расчёт поездки.","Refresh the trip price to continue.","Davom etish uchun safar narxini yangilang.","Давом этиш учун сафар нархини янгиланг.")
        else -> null
    }
    Column(Modifier.fillMaxSize().background(generatorPageColor())) {
        PullToRefreshBox(isRefreshing=sending,onRefresh={ submit() },modifier=Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                item { ReviewHeader(language,state,quote,sending,chrome,unreadCount) { help=!help } }
                item { Box(Modifier.padding(horizontal=16.dp)) { ReviewPriceAndGuests(language,journey,chrome,!sending) } }
                item { Box(Modifier.padding(horizontal=16.dp)) { IumrahClientPackageMoment(language,selectedOrbitServices(state)) } }
                item { Box(Modifier.padding(horizontal=16.dp)) { ReviewHelp(language,quote!=null,help) { help=!help } } }
                if(quote==null) item { Box(Modifier.padding(horizontal=16.dp)) { ReviewPricingStatus(language,sending) } }
                item { Box(Modifier.padding(horizontal=16.dp)) { ReviewSection(reviewText(language,"Авиабилеты","Flights","Aviachiptalar","Авиачипталар")) } }
                item { TextButton(enabled=!sending,onClick={editKind="flights"},modifier=Modifier.padding(horizontal=16.dp)) { Text(reviewText(language,"Изменить рейсы","Change flights","Reyslarni o‘zgartirish","Рейсларни ўзгартириш")) } }
                items(draft?.flights.orEmpty(),key={it.id}) { flight ->
                    Box(Modifier.padding(horizontal=16.dp)) {
                        val outbound=draft?.flights?.firstOrNull()?.id==flight.id
                        val verified=(if(outbound) state.selectedJourney?.outbound else state.selectedJourney?.inbound)?.takeIf { it.origin==flight.origin && it.destination==flight.destination && it.flightNumber==flight.flightNumber }
                        if(verified!=null) ReviewFlight(language,state,verified,outbound) else SmartFlightCard(language,flight,outbound)
                    }
                }
                item { Box(Modifier.padding(horizontal=16.dp)) { ReviewSection(reviewText(language,"Отели и номера","Hotels and rooms","Mehmonxonalar va xonalar","Меҳмонхоналар ва хоналар")) } }
                item { Row(Modifier.padding(horizontal=16.dp)) {
                    TextButton(enabled=!sending,onClick={editKind="allHotels"}) { Text(reviewText(language,"Все отели","All hotels","Barcha mehmonxonalar","Барча меҳмонхоналар")) }
                    if(draft?.rooms?.containsKey("makkah:0")==true) TextButton(enabled=!sending,onClick={editKind="makkah"}) { Text(reviewText(language,"Отель Мекки","Makkah hotel","Makka mehmonxonasi","Макка меҳмонхонаси")) }
                    if(draft?.rooms?.containsKey("madinah:0")==true) TextButton(enabled=!sending,onClick={editKind="madinah"}) { Text(reviewText(language,"Отель Медины","Madinah hotel","Madina mehmonxonasi","Мадина меҳмонхонаси")) }
                } }
                val groups = draft?.rooms?.values?.sortedWith(compareBy<NuiteeRoomSelection>{it.checkin}.thenBy{it.key})
                    ?.groupBy { it.city }?.entries?.toList().orEmpty()
                items(groups,key={"hotel-group-${it.key}"}) { group ->
                    val component = if (group.key == "madinah") "madinah_hotel" else "makkah_hotel"
                    val confirmed = quote?.takeIf { it.hasVerifiedBreakdown }?.tripSummary?.components
                        ?.firstOrNull { it.code == component }?.amountUsd
                    Box(Modifier.padding(horizontal=16.dp)) {
                        LocalReviewHotelGroup(language,group.value,confirmed,!sending,
                            onHotel={ editKind=group.key },onRoom={ editing=it },
                            onMeals={ room, meals -> journey.updateNuiteeRoomMeals(room,meals) })
                    }
                }
                items(draft?.pending?.values?.toList().orEmpty(),key={"pending-${it.key}"}) { pending ->
                    Column(Modifier.padding(horizontal=16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(generatorCardColor()).padding(14.dp)) {
                        Text(pending.hotel.name,fontWeight=FontWeight.Bold)
                        Text(reviewText(language,"Без подтверждённой цены","Awaiting provider price","Narx tasdiqlanmagan","Нарх тасдиқланмаган"),fontSize=12.sp)
                    }
                }
                item { Box(Modifier.padding(horizontal=16.dp)) { ReviewServices(language,state,journey::setGuidePreference) { transferSheet=true } } }
                quote?.let { item { Box(Modifier.padding(horizontal=16.dp)) { ReviewVerifiedPrice(language,it) } } }
                quote?.let { item { Box(Modifier.padding(horizontal=16.dp)) { ReviewPriceDistribution(language,it) } } }
                item { Box(Modifier.padding(horizontal=16.dp)) { com.iumrah.beta.ui.home.BrandComparisonEntry(language,chrome) } }
                item { Box(Modifier.padding(horizontal=16.dp)) { SavedPackageActions(language,journey,chrome,enabled=!sending && valid && state.transferSelectionConfirmed) } }
                item { Box(Modifier.padding(horizontal=16.dp)) { ReviewItinerary(language,state,quote) } }
                error?.let { message -> item { Text(message,Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.error,fontSize=13.sp) } }
            }
        }
        ReviewBookingBar(language,quote,true,sending,reason,
            when { !roomsComplete -> reviewText(language,"Выбрать номер","Choose room","Xona tanlash","Хона танлаш")
                !state.transferSelectionConfirmed || !valid -> reviewText(language,"Подтвердить трансфер","Confirm transfer","Transferni tasdiqlash","Трансферни тасдиқлаш")
                quote?.hasVerifiedBreakdown == true && quote.allowsOccupancy(state.trip.adults,state.trip.rooms) -> reviewText(language,"Забронировать пакет","Book package","Paketni bron qilish","Пакетни брон қилиш")
                else -> reviewText(language,"Повторить расчёт","Retry pricing","Qayta hisoblash","Қайта ҳисоблаш") },!sending && (quote == null || (state.priceChangeAccepted && quote.allowsOccupancy(state.trip.adults,state.trip.rooms)))) {
            when { !roomsComplete -> editKind="allHotels"
                !state.transferSelectionConfirmed || !valid -> transferSheet=true
                else -> submit(confirm=quote!=null) }
        }
    }
    editKind?.let { kind ->
        val original = remember(kind,journey) { journey.state.value }
        val editor = remember(kind,journey) { journey.beginReviewEdit() }
        if(kind=="allHotels") {
            androidx.compose.ui.window.Dialog(onDismissRequest={editKind=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
                com.iumrah.beta.ui.trip.NuiteeJourneyHotelsScreen(language,editor,chrome,hotelService,reviewEditing=true,onBack={editKind=null},onComplete={
                    if(original.transferSelectionConfirmed) editor.confirmTransferSelection()
                    if(!journey.commitReviewEdit(original,editor)) error="PACKAGE_SELECTION_CHANGED"
                    editKind=null
                })
            }
        } else com.iumrah.beta.ui.smart.SmartPackageEditDialog(language,editorService,container.airportSearchService,journey,kind,
            onDismiss={editKind=null},onApply={candidate ->
                val committed = if(kind=="flights") journey.commitReviewFlightEdit(original,candidate) else {
                    if(original.transferSelectionConfirmed) candidate.confirmTransferSelection()
                    journey.commitReviewEdit(original,candidate)
                }
                if(!committed) error=reviewText(language,"Пакет изменился. Откройте редактор снова.","Package changed. Reopen the editor.","Paket o‘zgardi. Tahrirni qayta oching.","Пакет ўзгарди. Таҳрирни қайта очинг.")
                editKind=if(committed && kind=="flights" && journey.state.value.nuitee?.let { NuiteeBookingRules.complete(journey.state.value.trip,it) } != true) "allHotels" else null
            },reviewEditor=editor)
    }
    editing?.let { room ->
        val original = remember(room) { journey.state.value }
        val editor = remember(room) { journey.beginReviewEdit() }
        NuiteeHotelDetailSheet(language,hotelService,room.hotel,room.checkin,room.checkout,room.adults,room.childrenAges,
            onDismiss={editing=null},onRate={rate,meals ->
                editor.replaceNuiteeRoom(room,room.copy(rate=rate,meals=meals))
                if (!journey.commitReviewEdit(original,editor)) error = reviewText(language,"Пакет изменился. Откройте редактор снова.","Package changed. Reopen the editor.","Paket o‘zgardi. Tahrirni qayta oching.","Пакет ўзгарди. Таҳрирни қайта очинг.")
                editing=null
            },allowMealSelection=true,initialMeals=room.meals)
    }
    if (transferSheet) {
        val original = remember(journey) { journey.state.value }
        val editor = remember(journey) { journey.beginReviewEdit() }
        val edited by editor.state.collectAsState()
        ReviewTransferSheet(language,edited,editor,!sending,{ transferSheet=false }) {
            editor.confirmTransferSelection()
            if (!journey.commitReviewEdit(original,editor)) error = reviewText(language,"Пакет изменился. Откройте редактор снова.","Package changed. Reopen the editor.","Paket o‘zgardi. Tahrirni qayta oching.","Пакет ўзгарди. Таҳрирни қайта очинг.")
            transferSheet=false
        }
    }
}

/** TripSummaryHotelOverview / compactLocalHotelGroup in the SwiftUI reference.
 * A city is one card; the exact server amount wins over the supplier estimate. */
@Composable private fun LocalReviewHotelGroup(l: AppLanguage, rooms: List<NuiteeRoomSelection>, confirmed: BigDecimal?, enabled:Boolean,
    onHotel:()->Unit,onRoom:(NuiteeRoomSelection)->Unit,onMeals:(NuiteeRoomSelection,NuiteeMealChoices)->Unit) {
    val first=rooms.firstOrNull() ?: return
    val muted=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)
    var expanded by remember(first.city) { mutableStateOf(false) }
    val nights=ChronoUnit.DAYS.between(first.checkin,first.checkout)
    val estimate=rooms.takeIf { selections -> selections.all { it.rate.currency.equals("USD",ignoreCase=true) && it.rate.price.isFinite() && it.rate.price>0 } }
        ?.fold(BigDecimal.ZERO) { total,room -> total+BigDecimal.valueOf(room.rate.price) }
    val amount=confirmed ?: estimate
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(generatorCardColor()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top) {
            Box(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.Building,null,Modifier.size(28.dp),muted)
                first.hotel.coverImageURL?.let { AsyncImage(model=it,contentDescription=first.hotel.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                val city=if(first.city=="madinah") reviewText(l,"Медина","Madinah","Madina","Мадина") else reviewText(l,"Мекка","Makkah","Makka","Макка")
                Text("$city · $nights "+reviewText(l,"ночей","nights","tun","тун"),fontSize=12.sp,color=muted)
                Text(first.hotel.name,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text(if(first.hotel.roundedStars>0) "★".repeat(first.hotel.roundedStars) else reviewText(l,"Категория не указана","Unrated","Toifasi ko‘rsatilmagan","Тоифаси кўрсатилмаган"),fontSize=12.sp,color=muted)
                Text("${rooms.size} "+reviewText(l,"ном. · ","rooms · ","xona · ","хона · ")+"${rooms.sumOf { it.adults+it.childrenAges.size }} "+reviewText(l,"гостей","guests","mehmon","меҳмон"),fontSize=12.sp,color=muted)
                amount?.let { Text(smartMoney(it,"USD",l),fontSize=20.sp,fontWeight=FontWeight.Bold) }
                if(amount!=null) Text(if(confirmed!=null) reviewText(l,"За все номера и ночи","All rooms and nights","Barcha xona va tunlar","Барча хона ва тунлар") else reviewText(l,"Предварительно · все номера и ночи","Estimated · all rooms and nights","Taxminiy · barcha xona va tunlar","Тахминий · барча хона ва тунлар"),fontSize=11.sp,color=muted)
            }
        }
        // Do not hide a different hotel when a supplier returned mixed selections.
        rooms.map { it.hotel.name }.distinct().filter { it!=first.hotel.name }.forEach { Text(it,fontSize=13.sp) }
        ReviewLabel(CupertinoSymbol.Calendar,"${first.checkin} – ${first.checkout}")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onHotel,enabled=enabled,modifier=Modifier.weight(1f),shape=RoundedCornerShape(22.dp)) { Text(reviewText(l,"Изменить отель","Change hotel","Mehmonxonani o‘zgartirish","Меҳмонхонани ўзгартириш"),fontSize=12.sp) }
            OutlinedButton(onClick={ if(rooms.size==1) onRoom(first) else expanded=!expanded },enabled=enabled,modifier=Modifier.weight(1f),shape=RoundedCornerShape(22.dp)) { Text(reviewText(l,"Номер и питание","Room and meals","Xona va ovqat","Хона ва овқат"),fontSize=12.sp) }
        }
        Text(when { rooms.all { it.rate.refundable } -> reviewText(l,"Возврат по условиям тарифа","Refundable under rate terms","Tarif bo‘yicha qaytarish","Тариф бўйича қайтариш")
            rooms.none { it.rate.refundable } -> reviewText(l,"Невозвратный тариф","Non-refundable rate","Qaytarilmaydigan tarif","Қайтарилмайдиган тариф")
            else -> reviewText(l,"Условия возврата различаются по номерам","Refund terms vary by room","Qaytarish shartlari xonaga bog‘liq","Қайтариш шартлари хонага боғлиқ") },fontSize=12.sp,color=muted)
        if(expanded && rooms.size>1) rooms.sortedBy { it.key }.forEach { room ->
            key(room.key) {
                HorizontalDivider()
                LocalReviewHotel(l,room,enabled,{onRoom(room)},{onMeals(room,it)})
            }
        }
    }
}

@Composable private fun LocalReviewHotel(l: AppLanguage, room: NuiteeRoomSelection, enabled:Boolean, onChange:()->Unit,onMeals:(NuiteeMealChoices)->Unit) {
    val included=NuiteeBookingRules.supplierMeals(room.rate)
    val makkah=room.city=="makkah"
    val muted=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(room.rate.roomName,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
        val nights=ChronoUnit.DAYS.between(room.checkin,room.checkout)
        ReviewLabel(CupertinoSymbol.Calendar,"${room.checkin} – ${room.checkout} · $nights "+reviewText(l,"ночей","nights","tun","тун"))
        Text("${room.adults} "+reviewText(l,"взрослых","adults","kattalar","катталар")+" · ${room.childrenAges.size} "+reviewText(l,"детей","children","bolalar","болалар"),fontSize=12.sp,color=muted)
        if(room.rate.mealPlan.isNotBlank()) Text(room.rate.mealPlan,fontSize=12.sp,color=muted)
        Text(if(room.rate.refundable) reviewText(l,"Возврат по условиям тарифа","Refundable under rate terms","Tarif bo‘yicha qaytarish","Тариф бўйича қайтариш") else reviewText(l,"Невозвратный тариф","Non-refundable rate","Qaytarilmaydigan tarif","Қайтарилмайдиган тариф"),fontSize=12.sp,color=muted)
        if(room.rate.cancellationDescription.isNotBlank()) Text(room.rate.cancellationDescription,fontSize=12.sp,color=muted)
        if(nights>0 && room.rate.price.isFinite() && room.rate.price>0 && room.rate.currency.isNotBlank()) ReviewUnitPrice(l,BigDecimal.valueOf(room.rate.price).divide(BigDecimal.valueOf(nights),java.math.MathContext.DECIMAL128),room.rate.currency,reviewText(l,"Средняя цена за ночь","Average per night","Tun uchun o‘rtacha","Тун учун ўртача"))
        OutlinedButton(onClick=onChange,enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text(reviewText(l,"Изменить номер и питание","Change room and meals","Xona va ovqatni o‘zgartirish","Хона ва овқатни ўзгартириш")) }
        Text(reviewText(l,"Питание","Meals","Ovqatlanish","Овқатланиш"),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        if(room.hotel.roundedStars<=3) {
            val fullyIncluded=included.breakfast && included.dinner && (!makkah || included.lunch)
            ReviewMealToggle(if(makkah) reviewText(l,"Трёхразовое питание","Three meals daily","Kuniga uch mahal","Кунига уч маҳал") else reviewText(l,"Двухразовое питание","Two meals daily","Kuniga ikki mahal","Кунига икки маҳал"),fullyIncluded||room.meals.breakfast||room.meals.lunch||room.meals.dinner,enabled&&!fullyIncluded) { onMeals(NuiteeMealChoices(it,it&&makkah,it)) }
        } else {
            ReviewMealToggle(reviewText(l,"Завтрак включён","Breakfast included","Nonushta kiritilgan","Нонушта киритилган"),true,false) {}
            if(makkah) ReviewMealToggle(reviewText(l,"Обед","Lunch","Tushlik","Тушлик"),included.lunch||room.meals.lunch,enabled&&!included.lunch) { onMeals(room.meals.copy(lunch=it)) }
            ReviewMealToggle(reviewText(l,"Ужин","Dinner","Kechki ovqat","Кечки овқат"),included.dinner||room.meals.dinner,enabled&&!included.dinner) { onMeals(room.meals.copy(dinner=it)) }
        }
    }
}
@Composable private fun ReviewMealToggle(title:String,checked:Boolean,enabled:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f),fontSize=15.sp); Switch(checked=checked,onCheckedChange=onChange,enabled=enabled) }
}
