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
    val roomsComplete = draft?.let { NuiteeBookingRules.complete(state.trip, it) && it.flights.size == 2 } == true
    val valid = roomsComplete && state.resolvedTransferVehicle.passengerCapacity >= state.trip.travelerCount
    val quote = state.quote?.takeIf { state.hasFinalGeneratorQuote && it.bookingAllowed == true }

    fun submit(confirm: Boolean = false) {
        if (!valid || sending || !journey.state.value.transferSelectionConfirmed || journey.state.value != state) return
        val accepted = if (confirm) quote else null
        sending = true; error = null
        scope.launch {
            try {
                val result = journey.prepareNuiteePackage(curated, packageEngine)
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it else error = it.localizedMessage ?: "PACKAGE_QUOTE_FAILED" }
                val fresh = result.getOrNull()
                if (accepted != null && fresh != null) {
                    if (accepted.totalPackagePrice.compareTo(fresh.totalPackagePrice) == 0 && accepted.pricePerPerson.compareTo(fresh.pricePerPerson) == 0 && accepted.currency == fresh.currency && journey.state.value.quote == fresh && journey.state.value.nuitee == draft) chrome.openBookingCheckout()
                    else error = reviewText(language,"Стоимость изменилась. Проверьте новую цену.", "Price changed. Review the new total.", "Narx o‘zgardi. Yangi narxni tekshiring.", "Нарх ўзгарди. Янги нархни текширинг.")
                }
            } finally { sending = false }
        }
    }
    // Match the iOS automatic quote refresh when the selected components change.
    LaunchedEffect(draft,state.resolvedTransferVehicle,state.haramainTrainSelected,state.haramainTicketCount,state.transferSelectionConfirmed) {
        if (quote == null && valid && state.transferSelectionConfirmed && !sending) submit()
    }
    val reason = when {
        sending -> reviewText(language,"Обновляем стоимость…","Updating price…","Narx yangilanmoqda…","Нарх янгиланмоқда…")
        !roomsComplete -> reviewText(language,"Выберите номер","Choose room","Xona tanlash","Хона танлаш")
        !state.transferSelectionConfirmed || !valid -> reviewText(language,"Подтвердите выбранный трансфер.","Confirm your transfer.","Transferni tasdiqlang.","Трансферни тасдиқланг.")
        quote == null -> reviewText(language,"Для продолжения обновите расчёт поездки.","Refresh the trip price to continue.","Davom etish uchun safar narxini yangilang.","Давом этиш учун сафар нархини янгиланг.")
        else -> null
    }
    Column(Modifier.fillMaxSize().background(generatorPageColor())) {
        PullToRefreshBox(isRefreshing=sending,onRefresh={ submit() },modifier=Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(bottom=20.dp)) {
                item { ReviewHeader(language,state,quote,sending,chrome,unreadCount) { help=!help } }
                item { Box(Modifier.padding(horizontal=18.dp)) { IumrahClientPackageMoment(language,selectedOrbitServices(state)) } }
                item { Box(Modifier.padding(horizontal=18.dp)) { ReviewHelp(language,quote!=null,help) { help=!help } } }
                if(quote==null) item { Box(Modifier.padding(horizontal=18.dp)) { ReviewPricingStatus(language,sending) } }
                item { Box(Modifier.padding(horizontal=18.dp)) { ReviewSection(reviewText(language,"Авиабилеты","Flights","Aviachiptalar","Авиачипталар")) } }
                items(draft?.flights.orEmpty(),key={it.id}) { flight ->
                    Box(Modifier.padding(horizontal=18.dp)) {
                        val outbound=draft?.flights?.firstOrNull()?.id==flight.id
                        val verified=(if(outbound) state.selectedJourney?.outbound else state.selectedJourney?.inbound)?.takeIf { it.origin==flight.origin && it.destination==flight.destination && it.flightNumber==flight.flightNumber }
                        if(verified!=null) ReviewFlight(language,state,verified,outbound) else SmartFlightCard(language,flight,outbound)
                    }
                }
                item { Box(Modifier.padding(horizontal=18.dp)) { ReviewSection(reviewText(language,"Отели и номера","Hotels and rooms","Mehmonxonalar va xonalar","Меҳмонхоналар ва хоналар")) } }
                items(draft?.rooms?.values?.sortedWith(compareBy<NuiteeRoomSelection>{it.checkin}.thenBy{it.key}).orEmpty(),key={it.key}) { room ->
                    Box(Modifier.padding(horizontal=18.dp)) { LocalReviewHotel(language,room,!sending,{ editing=room },{ meals -> journey.updateNuiteeRoomMeals(room,meals) }) }
                }
                items(draft?.pending?.values?.toList().orEmpty(),key={"pending-${it.key}"}) { pending ->
                    Column(Modifier.padding(horizontal=18.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(generatorCardColor()).padding(14.dp)) {
                        Text(pending.hotel.name,fontWeight=FontWeight.Bold)
                        Text(reviewText(language,"Без подтверждённой цены","Awaiting provider price","Narx tasdiqlanmagan","Нарх тасдиқланмаган"),fontSize=12.sp)
                    }
                }
                item { Box(Modifier.padding(horizontal=18.dp)) { ReviewServices(language,state) { transferSheet=true } } }
                error?.let { message -> item { Text(message,Modifier.padding(horizontal=18.dp),color=MaterialTheme.colorScheme.error,fontSize=13.sp) } }
            }
        }
        ReviewBookingBar(language,quote,true,sending,reason,
            when { !roomsComplete -> reviewText(language,"Выбрать номер","Choose room","Xona tanlash","Хона танлаш")
                !state.transferSelectionConfirmed || !valid -> reviewText(language,"Подтвердить трансфер","Confirm transfer","Transferni tasdiqlash","Трансферни тасдиқлаш")
                quote!=null -> reviewText(language,"Забронировать пакет","Book package","Paketni bron qilish","Пакетни брон қилиш")
                else -> reviewText(language,"Повторить расчёт","Retry pricing","Qayta hisoblash","Қайта ҳисоблаш") },!sending) {
            when { !roomsComplete -> chrome.openNuiteeJourneyHotels()
                !state.transferSelectionConfirmed || !valid -> transferSheet=true
                else -> submit(confirm=quote!=null) }
        }
    }
    editing?.let { room ->
        NuiteeHotelDetailSheet(language,hotelService,room.hotel,room.checkin,room.checkout,room.adults,room.childrenAges,
            onDismiss={editing=null},onRate={rate,meals ->
                journey.replaceNuiteeRoom(room,room.copy(rate=rate,meals=meals))
                editing=null
            },allowMealSelection=true,initialMeals=room.meals)
    }
    if(transferSheet) ReviewTransferSheet(language,state,journey,!sending,{ transferSheet=false }) {
        journey.confirmTransferSelection(); transferSheet=false
    }
}

@Composable private fun LocalReviewHotel(l: AppLanguage, room: NuiteeRoomSelection, enabled:Boolean, onChange:()->Unit,onMeals:(NuiteeMealChoices)->Unit) {
    val included=NuiteeBookingRules.supplierMeals(room.rate)
    val makkah=room.city=="makkah"
    val muted=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(generatorCardColor()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(86.dp).height(100.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.Building,null,Modifier.size(28.dp),muted)
                room.hotel.coverImageURL?.let { AsyncImage(model=it,contentDescription=room.hotel.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(room.hotel.name,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text("★".repeat(room.hotel.roundedStars)+" · "+if(makkah) reviewText(l,"Мекка","Makkah","Makka","Макка") else reviewText(l,"Медина","Madinah","Madina","Мадина"),fontSize=12.sp,color=muted)
                Text(room.rate.roomName,fontSize=15.sp)
            }
        }
        val nights=ChronoUnit.DAYS.between(room.checkin,room.checkout)
        ReviewLabel(CupertinoSymbol.Calendar,"${room.checkin} – ${room.checkout} · $nights "+reviewText(l,"ночей","nights","tun","тун"))
        if(nights>0 && room.rate.price.isFinite() && room.rate.price>0 && room.rate.currency.isNotBlank()) ReviewUnitPrice(l,BigDecimal.valueOf(room.rate.price).divide(BigDecimal.valueOf(nights),java.math.MathContext.DECIMAL128),room.rate.currency,reviewText(l,"Ориентировочно за номер / ночь","Estimated per room / night","Xona / tun uchun taxminan","Хона / тун учун тахминан"))
        IumrahPressable(onClick=onChange,enabled=enabled,modifier=Modifier.fillMaxWidth().height(48.dp),cornerRadius=16.dp,background=MaterialTheme.colorScheme.surfaceVariant) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text(reviewText(l,"Изменить номер","Change room","Xonani o‘zgartirish","Хонани ўзгартириш"),fontWeight=FontWeight.SemiBold,fontSize=15.sp) } }
        HorizontalDivider()
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
