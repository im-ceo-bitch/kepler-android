package com.iumrah.beta.ui.packageflow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahDesign
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.domain.nuitee.NuiteeBookingRules
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.components.IumrahSignatureWorldBackground
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.hotels.PackageInfoSheet
import com.iumrah.beta.ui.hotels.PackageInformationSheet
import com.iumrah.beta.ui.hotels.PurchaseTrustCard
import com.iumrah.beta.ui.hotels.nuiteeText
import com.iumrah.beta.ui.smart.smartMoney
import com.iumrah.beta.ui.generator.generatorCardColor
import com.iumrah.beta.ui.generator.generatorPageColor

internal fun reviewText(l: AppLanguage, ru: String, en: String, uz: String, cy: String) = nuiteeText(l,ru,en,uz,cy)

internal fun reviewTier(l: AppLanguage, tier: PackageTier) = when(tier) {
    PackageTier.ECONOMY -> reviewText(l,"Эконом","Economy","Ekonom","Эконом")
    PackageTier.STANDARD -> reviewText(l,"Стандарт","Standard","Standart","Стандарт")
    PackageTier.COMFORT -> reviewText(l,"Комфорт","Comfort","Komfort","Комфорт")
    PackageTier.LUXURY -> reviewText(l,"Люкс","Luxury","Lyuks","Люкс")
}

@Composable internal fun ReviewHeader(l: AppLanguage, state: JourneyState, quote: PackageQuote?, busy: Boolean, chrome: AppChromeStore, unreadCount: Int = 0, onHelp: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val trip = state.trip
    val caption = if (state.nuitee != null) reviewText(l,"Предварительно за всех","Estimated total","Taxminiy jami","Тахминий жами") else reviewText(l,"Итого за всех","Total for everyone","Hamma uchun jami","Ҳамма учун жами")
    val total = if (busy) reviewText(l,"Расчёт…","Calculating…","Hisoblanmoqda…","Ҳисобланмоқда…") else quote?.let { smartMoney(it.totalPackagePrice,it.currency,l) } ?: "—"
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy((-46).dp)) {
        Box(Modifier.fillMaxWidth()) {
            IumrahSignatureWorldBackground(Modifier.matchParentSize(),mapOpacity=.30f,isAnimating=false)
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top=IumrahDesign.SignatureHeaderTop,bottom=IumrahDesign.SignatureHeaderBottom).padding(horizontal=20.dp), verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(88.dp),contentAlignment=Alignment.CenterStart) {
                    ReviewHeaderAction(CupertinoSymbol.ChevronLeft,reviewText(l,"Назад","Back","Orqaga","Орқага"),chrome::back)
                }
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    Image(painterResource(R.drawable.iumrah_header_wordmark_dark),"iumrah",Modifier.width(IumrahDesign.SignatureLogoWidth).height(IumrahDesign.SignatureLogoHeight),contentScale=ContentScale.Fit)
                }
                Row(Modifier.width(88.dp).height(44.dp).clip(CircleShape).background(Color.White.copy(alpha=.12f)),verticalAlignment=Alignment.CenterVertically) {
                    ReviewHeaderAction(if(unreadCount>0) CupertinoSymbol.BellBadge else CupertinoSymbol.Bell,reviewText(l,"Уведомления","Notifications","Bildirishnomalar","Билдиришномалар"),chrome::openNotifications, grouped=true)
                    ReviewHeaderAction(CupertinoSymbol.QuestionMark,reviewText(l,"Что делать здесь?","What can I do here?","Bu yerda nima qilish kerak?","Бу ерда нима қилиш керак?"),onHelp, grouped=true)
                }
            }
        }
        Column(Modifier.padding(horizontal=18.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(generatorCardColor()).animateContentSize(tween(240)).padding(12.dp)) {
            Column(Modifier.fillMaxWidth().clickable { expanded=!expanded },verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text(reviewText(l,"Ваша умра","Your Umrah","Sizning Umrangiz","Сизнинг Умрангиз"),Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.SemiBold); CupertinoIcon(if(expanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown,null,Modifier.size(13.dp)) }
                Row(verticalAlignment=Alignment.CenterVertically) { Text(total,Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold); Text(caption,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)) }
                Text(reviewText(l,"${trip.adults} взр. · ${trip.children} дет. · ${trip.infants} млад. · ${trip.rooms} ном.","${trip.adults} adults · ${trip.children} children · ${trip.infants} infants · ${trip.rooms} rooms","${trip.adults} katta · ${trip.children} bola · ${trip.infants} chaqaloq · ${trip.rooms} xona","${trip.adults} катта · ${trip.children} бола · ${trip.infants} чақалоқ · ${trip.rooms} хона"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(Modifier.padding(top=7.dp))
                    Text(reviewTier(l,trip.packageTier),fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                    ReviewLabel(CupertinoSymbol.Map,if(trip.scope==JourneyScope.MAKKAH_AND_MADINAH) reviewText(l,"Мекка и Медина","Makkah and Madinah","Makka va Madina","Макка ва Мадина") else reviewText(l,"Только Мекка","Makkah only","Faqat Makka","Фақат Макка"))
                    ReviewLabel(CupertinoSymbol.Calendar,"${trip.departureDate} – ${trip.returnDate}")
                    val draft=state.nuitee
                    if(draft!=null) {
                        draft.flights.forEachIndexed { index,f -> ReviewLabel(if(index==0) CupertinoSymbol.AirplaneTakeoff else CupertinoSymbol.AirplaneLand,"${f.origin} → ${f.destination} · ${f.flightNumber}") }
                        draft.rooms.values.sortedWith(compareBy<com.iumrah.beta.domain.nuitee.NuiteeRoomSelection>{it.checkin}.thenBy{it.key}).forEach { ReviewLabel(CupertinoSymbol.Bed,"${it.hotel.name} · ${it.rate.roomName}") }
                    } else {
                        state.selectedJourney?.outbound?.let { ReviewLabel(CupertinoSymbol.AirplaneTakeoff,"${it.origin} → ${it.destination} · ${it.flightNumber}") }
                        if(trip.isRoundTripFlight) state.selectedJourney?.inbound?.let { ReviewLabel(CupertinoSymbol.AirplaneLand,"${it.origin} → ${it.destination} · ${it.flightNumber}") }
                        state.makkahHotel?.let { ReviewLabel(CupertinoSymbol.Bed,"${it.name} · ${state.makkahRoom?.name ?: state.makkahRoomCategory?.displayName.orEmpty()}") }
                        state.madinahHotel?.takeIf { trip.scope==JourneyScope.MAKKAH_AND_MADINAH }?.let { ReviewLabel(CupertinoSymbol.Bed,"${it.name} · ${state.madinahRoom?.name ?: state.madinahRoomCategory?.displayName.orEmpty()}") }
                    }
                    state.selectedTransferVehicle?.let { ReviewLabel(CupertinoSymbol.Car,it.modelName) }
                    if(state.haramainTrainSelected) ReviewLabel(CupertinoSymbol.Route,"Haramain · ${state.haramainTicketCount}")
                    if(quote!=null && !busy) Text(smartMoney(quote.pricePerPerson,quote.currency,l)+" · "+reviewText(l,"на одного паломника","per pilgrim","bir ziyoratchi uchun","бир зиёратчи учун"),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun ReviewHeaderAction(icon: CupertinoSymbol, label: String, action: () -> Unit, grouped: Boolean=false) {
    IumrahPressable(onClick=action,modifier=Modifier.size(if(grouped) 44.dp else 48.dp),cornerRadius=99.dp,background=if(grouped) Color.Transparent else Color.White.copy(alpha=.12f)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CupertinoIcon(icon,label,Modifier.size(19.dp),Color.White) }
    }
}

@Composable internal fun ReviewLabel(icon: CupertinoSymbol, title: String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { CupertinoIcon(icon,null,Modifier.size(19.dp),Color(0xFF24844C)); Text(title,fontSize=15.sp) }
}
@Composable internal fun ReviewSection(title: String) { Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold) }

@Composable internal fun ReviewHelp(l: AppLanguage, ready: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(generatorCardColor()).clickable(onClick=onToggle).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text(reviewText(l,"Что делать на этой странице","What to do on this page","Bu sahifada nima qilish kerak","Бу саҳифада нима қилиш керак"),Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold); CupertinoIcon(if(expanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown,null,Modifier.size(14.dp)) }
        AnimatedVisibility(expanded) { Text(if(ready) reviewText(l,"Проверьте рейсы, отели и услуги, затем забронируйте пакет.","Review your flights, hotels and services, then book the package.","Reyslar, mehmonxonalar va xizmatlarni tekshirib, paketni bron qiling.","Рейслар, меҳмонхоналар ва хизматларни текшириб, пакетни брон қилинг.") else reviewText(l,"Проверьте выбранные компоненты и обновите расчёт.","Review your selection and refresh the price.","Tanlangan qismlarni tekshiring va narxni yangilang.","Танланган қисмларни текширинг ва нархни янгиланг."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f)) }
    }
}

@Composable internal fun ReviewBookingBar(l: AppLanguage, quote: PackageQuote?, estimated: Boolean, busy: Boolean, reason: String?, title: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(generatorCardColor()).navigationBarsPadding().padding(horizontal=18.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text(if(estimated) reviewText(l,"Предварительно за всех","Estimated total","Taxminiy jami","Тахминий жами") else reviewText(l,"Итого за всех","Total for everyone","Hamma uchun jami","Ҳамма учун жами"),Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)); Text(if(busy) reviewText(l,"Расчёт…","Calculating…","Hisoblanmoqda…","Ҳисобланмоқда…") else quote?.let { smartMoney(it.totalPackagePrice,it.currency,l) } ?: "—",fontSize=20.sp,fontWeight=FontWeight.Bold) }
        if(quote!=null && !busy) Row(verticalAlignment=Alignment.CenterVertically) { Text(reviewText(l,"На одного паломника","Per pilgrim","Bir ziyoratchi uchun","Бир зиёратчи учун"),Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)); Text(smartMoney(quote.pricePerPerson,quote.currency,l),fontSize=15.sp,fontWeight=FontWeight.SemiBold) }
        if(estimated) Text(reviewText(l,"Наличие и окончательная стоимость подтверждаются перед оплатой.","Availability and final price will be confirmed before payment.","Mavjudlik va yakuniy narx to‘lovdan oldin tasdiqlanadi.","Мавжудлик ва якуний нарх тўловдан олдин тасдиқланади."),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))
        reason?.let { Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f)) }
        IumrahPrimaryButton(title=title,enabled=enabled && !busy,onClick=onClick)
    }
}

@Composable internal fun ReviewServices(l: AppLanguage, state: JourneyState, onTransfer: () -> Unit) {
    var info by remember { mutableStateOf<PackageInfoSheet?>(null) }
    val included=reviewText(l,"Включено","Included","Kiritilgan","Киритилган")
    val both=state.trip.scope==JourneyScope.MAKKAH_AND_MADINAH
    Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
        ReviewSection(reviewText(l,"В вашем пакете","Your package includes","Paketingizga kiradi","Пакетингизга киради"))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(generatorCardColor())) {
            ReviewServiceRow(CupertinoSymbol.PersonCircle,"iumrah Guide",null,{info=PackageInfoSheet.GUIDE},true)
            ReviewServiceRow(CupertinoSymbol.Heart,"iumrah Care",null,{info=PackageInfoSheet.CARE},true)
            ReviewServiceRow(CupertinoSymbol.Car,"iumrah Transfer",state.selectedTransferVehicle?.modelName,onTransfer,true)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(generatorCardColor()).padding(horizontal=16.dp)) {
            ReviewServiceRow(CupertinoSymbol.Document,reviewText(l,"Виза","Visa","Viza","Виза"),reviewText(l,"Подробнее","Details","Batafsil","Батафсил"),{info=PackageInfoSheet.VISA})
            HorizontalDivider()
            val meals=state.nuitee?.rooms?.values?.sortedWith(compareBy<com.iumrah.beta.domain.nuitee.NuiteeRoomSelection>{it.checkin}.thenBy{it.key})?.joinToString("\n") { room ->
                val m=NuiteeBookingRules.bookingSelection(room).meals
                val count=listOf(m.breakfast,m.lunch && room.city=="makkah",m.dinner).count { it }
                val city=if(room.city=="madinah") reviewText(l,"Медина","Madinah","Madina","Мадина") else reviewText(l,"Мекка","Makkah","Makka","Макка")
                "$city · $count "+reviewText(l,"приёмов пищи/день","meals/day","mahal/kun","маҳал/кун")
            } ?: mealsSummary(l,state)
            ReviewServiceRow(CupertinoSymbol.ForkKnife,reviewText(l,"Питание","Meals","Ovqatlanish","Овқатланиш"),meals)
            HorizontalDivider(); ReviewServiceRow(CupertinoSymbol.Location,reviewText(l,"Зияраты в Мекке","Makkah ziyarat","Makka ziyoratlari","Макка зиёратлари"),included)
            if(both) { HorizontalDivider(); ReviewServiceRow(CupertinoSymbol.Location,reviewText(l,"Зияраты в Медине","Madinah ziyarat","Madina ziyoratlari","Мадина зиёратлари"),included) }
            if(both && state.haramainTrainSelected) { HorizontalDivider(); ReviewServiceRow(CupertinoSymbol.Route,"Haramain","${state.haramainTicketCount} · "+reviewText(l,"Стандарт","Standard","Standart","Стандарт")) }
            HorizontalDivider(); ReviewServiceRow(CupertinoSymbol.SimCard,"iumrah Mobile eSIM",included)
        }
        RefundPolicyCard(l)
        PurchaseTrustCard(l) { info=PackageInfoSheet.TRUST }
        ManualPaymentCard(l)
    }
    info?.let { PackageInformationSheet(it,l) { info=null } }
}

@Composable private fun ReviewServiceRow(icon: CupertinoSymbol,title: String,detail: String?,onClick: (() -> Unit)?=null,tile:Boolean=false) {
    Row(Modifier.fillMaxWidth().then(if(onClick!=null) Modifier.clickable(onClick=onClick) else Modifier).padding(horizontal=if(tile) 16.dp else 0.dp,vertical=if(tile) 10.dp else 14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(tile) Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF24844C).copy(alpha=.12f)),contentAlignment=Alignment.Center) { CupertinoIcon(icon,null,Modifier.size(16.dp),Color(0xFF24844C)) } else CupertinoIcon(icon,null,Modifier.size(24.dp),Color(0xFF24844C))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(title,fontSize=15.sp,fontWeight=FontWeight.SemiBold); detail?.let { Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)) } }
        if(onClick!=null) CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(12.dp),MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))
    }
}

@Composable internal fun ReviewPricingStatus(l: AppLanguage,busy:Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(generatorCardColor()).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(busy) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
        Text(if(busy) reviewText(l,"Обновляем стоимость выбранной поездки…","Updating your trip price…","Safar narxi yangilanmoqda…","Сафар нархи янгиланмоқда…") else reviewText(l,"Стоимость пока не подтверждена. Повторите расчёт.","The price is not confirmed yet. Retry pricing.","Narx hali tasdiqlanmagan. Qayta hisoblang.","Нарх ҳали тасдиқланмаган. Қайта ҳисобланг."),fontSize=15.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
    }
}
@Composable internal fun ReviewUnitPrice(l:AppLanguage,amount:java.math.BigDecimal,currency:String,caption:String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(generatorCardColor()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(smartMoney(amount,currency,l),fontSize=20.sp,fontWeight=FontWeight.Bold)
        Text(caption,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReviewTransferSheet(l:AppLanguage,state:JourneyState,journey:com.iumrah.beta.domain.journey.JourneyStore,enabled:Boolean,onDismiss:()->Unit,editable:Boolean=true,onConfirm:()->Unit) {
    val trip=state.trip
    val both=trip.scope==JourneyScope.MAKKAH_AND_MADINAH
    val madinahFirst=both && trip.arrivalAirport==com.iumrah.beta.domain.trip.SaudiArrivalAirport.MADINAH
    val makkah=state.nuitee?.rooms?.get("makkah:0")?.hotel?.name ?: state.makkahHotel?.name ?: reviewText(l,"Мекка","Makkah","Makka","Макка")
    val madinah=state.nuitee?.rooms?.get("madinah:0")?.hotel?.name ?: state.madinahHotel?.name ?: reviewText(l,"Медина","Madinah","Madina","Мадина")
    val first=if(madinahFirst) madinah else makkah
    val last=if(both&&!madinahFirst) madinah else makkah
    val vehicle=state.resolvedTransferVehicle
    val drawable=when(vehicle) {
        com.iumrah.beta.domain.trip.TransferVehicleKind.MALIBU -> R.drawable.transfer_malibu
        com.iumrah.beta.domain.trip.TransferVehicleKind.CARNIVAL -> R.drawable.transfer_carnival
        com.iumrah.beta.domain.trip.TransferVehicleKind.YUKON -> R.drawable.transfer_yukon
    }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=generatorPageColor()) {
        Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).navigationBarsPadding().padding(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(reviewText(l,"Трансфер","Transfer","Transfer","Трансфер"),Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold); TextButton(onClick=onDismiss) { Text(reviewText(l,"Готово","Done","Tayyor","Тайёр")) } }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item { Image(painterResource(drawable),vehicle.modelName,Modifier.fillMaxWidth().height(180.dp),contentScale=ContentScale.Fit); Text(vehicle.modelName,fontSize=22.sp,fontWeight=FontWeight.Bold) }
                item { ReviewLabel(CupertinoSymbol.AirplaneLand,"${trip.outboundDestinationCode} → $first") }
                item { ReviewLabel(CupertinoSymbol.Location,reviewText(l,"Отель → зияраты → отель","Hotel → ziyarat → hotel","Mehmonxona → ziyorat → mehmonxona","Меҳмонхона → зиёрат → меҳмонхона")) }
                if(both) {
                    item { ReviewLabel(if(state.haramainTrainSelected) CupertinoSymbol.Route else CupertinoSymbol.Car, "$first → $last"+(if(state.haramainTrainSelected) " · Haramain" else "")) }
                    item { ReviewLabel(CupertinoSymbol.Location,reviewText(l,"Отель → зияраты → отель","Hotel → ziyarat → hotel","Mehmonxona → ziyorat → mehmonxona","Меҳмонхона → зиёрат → меҳмонхона")) }
                }
                item { ReviewLabel(CupertinoSymbol.AirplaneTakeoff,"$last → ${trip.returnOriginCode}") }
                if(editable) {
                    item {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(com.iumrah.beta.domain.trip.TransferVehicleKind.CARNIVAL,com.iumrah.beta.domain.trip.TransferVehicleKind.YUKON).forEach { kind ->
                                FilterChip(selected=vehicle==kind,onClick={journey.chooseTransferVehicle(kind)},enabled=enabled&&kind.passengerCapacity>=trip.travelerCount,label={Text(kind.modelName,fontSize=12.sp)},modifier=Modifier.weight(1f))
                            }
                        }
                    }
                    if(both) item { Row(verticalAlignment=Alignment.CenterVertically) { Text("Haramain",Modifier.weight(1f)); Switch(checked=state.haramainTrainSelected,onCheckedChange=journey::setHaramainTrainSelected,enabled=enabled) } }
                }
                item { RefundPolicyCard(l) }
            }
            IumrahPrimaryButton(title=if(editable) reviewText(l,"Подтвердить трансфер","Confirm transfer","Transferni tasdiqlash","Трансферни тасдиқлаш") else reviewText(l,"Изменить трансфер","Change transfer","Transferni o‘zgartirish","Трансферни ўзгартириш"),enabled=enabled&&(!editable||vehicle.passengerCapacity>=trip.travelerCount),onClick=onConfirm)
        }
    }
}
