package com.iumrah.beta.ui.smart

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.iumrah.beta.ui.flights.IumrahPremiumFlightDetail
import com.iumrah.beta.ui.flights.PremiumFlightLeg
import com.iumrah.beta.ui.cupertino.IumrahIconRole
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.data.smart.SmartConfiguratorService.Progress
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.nuitee.NuiteeFlightSnapshot
import com.iumrah.beta.domain.nuitee.NuiteeRoomSelection
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.ui.components.IumrahOrbitV5
import com.iumrah.beta.ui.components.orbitShowcase
import com.iumrah.beta.ui.components.selectedOrbitServices
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.hotels.nuiteeText
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

@Composable internal fun SmartOrbitHeader(step:Int,title:String,subtitle:String,symbol:CupertinoSymbol?,assembled:JourneyState?) {
    val dark=MaterialTheme.colorScheme.background==Color.Black
    val services=if(assembled==null) orbitShowcase else selectedOrbitServices(assembled)
    val viewportHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val diameter = minOf(maxWidth * .34f, viewportHeight * .19f, 148.dp).coerceAtLeast(76.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IumrahOrbitV5(diameter, lightOnDark = dark, pinnedSymbol = symbol, services = services)
            SmartOrbitTitle(title, subtitle, false, false, Modifier.weight(1f))
        }
    }
}
@Composable private fun SmartOrbitTitle(title:String,subtitle:String,large:Boolean,center:Boolean,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text(title,Modifier.fillMaxWidth(),fontSize=if(large) 26.sp else 21.sp,fontWeight=FontWeight.Bold,textAlign=if(center) TextAlign.Center else TextAlign.Start)
        Text(subtitle,Modifier.fillMaxWidth(),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f),textAlign=if(center) TextAlign.Center else TextAlign.Start)
    }
}
@Composable private fun SmartIcon(symbol:CupertinoSymbol,size:Dp=44.dp,ink:Color=MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.size(size).background(ink.copy(alpha=.08f),CircleShape).border(1.dp,ink.copy(alpha=.08f),CircleShape),contentAlignment=Alignment.Center) {
        CupertinoIcon(symbol,null,Modifier.size(size*.4f),ink)
    }
}
@Composable internal fun SmartControl(symbol:CupertinoSymbol,size:Dp,enabled:Boolean,action:()->Unit) {
    val ink=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled) 1f else .35f)
    Box(Modifier.size(size).clip(CircleShape).background(ink.copy(alpha=.08f)).clickable(enabled=enabled,role=Role.Button,onClick=action),contentAlignment=Alignment.Center) { CupertinoIcon(symbol,symbol.name,Modifier.size(size*.4f),ink) }
}
@Composable internal fun SmartFeature(symbol:CupertinoSymbol,title:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) { SmartIcon(symbol); Text(title,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.Medium) }
}
@Composable internal fun SmartMonthRow(language:AppLanguage,selected:String,onSelect:(String)->Unit) {
    val months=remember { (0..11).map { YearMonth.now().plusMonths(it.toLong()) } }
    val state=rememberLazyListState(initialFirstVisibleItemIndex=months.indexOfFirst { it.toString()==selected }.coerceAtLeast(0))
    LazyRow(state=state,flingBehavior=rememberSnapFlingBehavior(lazyListState=state),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        items(months,key={it.toString()}) { month ->
            val chosen=selected==month.toString(); val ink=if(chosen) smartOnPrimary() else MaterialTheme.colorScheme.onSurface
            Column(Modifier.size(146.dp).clip(RoundedCornerShape(23.dp)).background(if(chosen) smartPrimary() else smartSecondary()).selectable(chosen,role=Role.RadioButton) { onSelect(month.toString()) }.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { SmartIcon(CupertinoSymbol.Calendar,42.dp,ink); Spacer(Modifier.weight(1f)); if(chosen) CupertinoIcon(CupertinoSymbol.CheckCircleFill,null,Modifier.size(18.dp),ink) }
                Spacer(Modifier.weight(1f)); Text(month.format(DateTimeFormatter.ofPattern("MMMM",Locale.forLanguageTag(language.localeTag))).replaceFirstChar { it.titlecase(Locale.forLanguageTag(language.localeTag)) },fontSize=17.sp,fontWeight=FontWeight.Bold,color=ink,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(month.year.toString(),fontSize=15.sp,color=ink.copy(alpha=.7f))
            }
        }
    }
}
private fun tierSubtitle(language:AppLanguage,tier:PackageTier):String = when(tier) {
    PackageTier.ECONOMY -> nuiteeText(language,"Практичные отели 2★ для более доступной цены пакета. Отели 1★ доступны как Super Economy.","Practical 2★ hotels for a lower package price. 1★ stays are also available as Super Economy.","Paket narxini qulayroq qilish uchun amaliy 2★ mehmonxonalar. 1★ variantlar Super Economy sifatida ham mavjud.","Пакет нархини қулайроқ қилиш учун амалий 2★ меҳмонхоналар. 1★ вариантлар Super Economy сифатида ҳам мавжуд.")
    PackageTier.STANDARD -> nuiteeText(language,"Отобранные отели 3★ — сбалансированный уровень комфорта и стоимости для Вашей Умры.","Selected 3★ hotels — a balanced level of comfort and value for your Umrah.","Tanlangan 3★ mehmonxonalar — Umrangiz uchun qulaylik va narxning muvozanatli darajasi.","Танланган 3★ меҳмонхоналар — Умрангиз учун қулайлик ва нархнинг мувозанатли даражаси.")
    PackageTier.COMFORT -> nuiteeText(language,"Отели 4★ с более высоким уровнем номеров, сервиса и ежедневного комфорта.","4★ hotels with a higher level of rooms, service and everyday comfort.","4★ mehmonxonalar — xonalar, xizmat va kundalik qulaylikning yuqoriroq darajasi.","4★ меҳмонхоналар — хоналар, хизмат ва кундалик қулайликнинг юқорироқ даражаси.")
    PackageTier.LUXURY -> nuiteeText(language,"Отели 5★ для премиального проживания и максимально комфортной Умры.","5★ hotels for a premium stay and the most comfortable Umrah experience.","5★ mehmonxonalar — premium yashash va eng qulay Umra tajribasi uchun.","5★ меҳмонхоналар — премиум яшаш ва энг қулай Умра тажрибаси учун.")
}
@Composable internal fun SmartCategoryRow(language:AppLanguage,selected:PackageTier,onSelect:(PackageTier)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        PackageTier.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach { tier ->
                    val chosen = tier == selected
                    val ink = if (chosen) smartOnPrimary() else MaterialTheme.colorScheme.onSurface
                    Column(Modifier.weight(1f).heightIn(min=78.dp).clip(RoundedCornerShape(18.dp))
                        .background(if(chosen) smartPrimary() else smartSecondary())
                        .selectable(chosen, role=Role.RadioButton) { onSelect(tier) }.padding(12.dp),
                        verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(tierTitle(language,tier), fontSize=16.sp, fontWeight=FontWeight.Bold, color=ink)
                        Text(tier.selectableHotelStars.sorted().joinToString("–")+" ★", fontSize=13.sp, color=ink)
                    }
                }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(tierTitle(language,selected),fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text(tierSubtitle(language,selected),fontSize=15.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
            SmartLabel(CupertinoSymbol.BuildingFill,nuiteeText(language,"Категория отелей","Hotel category","Mehmonxona toifasi","Меҳмонхона тоифаси")+" · "+selected.selectableHotelStars.joinToString(" / ")+" ★")
            SmartLabel(CupertinoSymbol.CheckCircle,nuiteeText(language,"Рейсы и услуги по правилам iumrah","Flights and services under existing iumrah rules","Reyslar va xizmatlar iumrah qoidalari bo‘yicha","Рейслар ва хизматлар iumrah қоидалари бўйича"))
        }
    }
}
@Composable private fun SmartLabel(symbol:CupertinoSymbol,label:String) { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { CupertinoIcon(symbol,null,Modifier.size(17.dp)); Text(label,fontSize=15.sp) } }
internal fun smartMoney(amount:BigDecimal,currency:String,language:AppLanguage):String = runCatching { NumberFormat.getCurrencyInstance(Locale.forLanguageTag(language.localeTag)).apply { this.currency=Currency.getInstance(currency); maximumFractionDigits=2; minimumFractionDigits=0 }.format(amount) }.getOrElse { "${amount.toPlainString()} $currency" }
@Composable internal fun SmartPriceLine(label:String,price:String,large:Boolean) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f)); Text(price,fontSize=if(large) 20.sp else 15.sp,fontWeight=if(large) FontWeight.Bold else FontWeight.SemiBold) } }
private fun localTime(raw:String?,airport:String,language:AppLanguage):String = runCatching { OffsetDateTime.parse(raw!!).atZoneSameInstant(FlightReferenceCatalog.timeZone(airport) ?: java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("d MMM HH:mm",Locale.forLanguageTag(language.localeTag))) }.getOrDefault("—")
@Composable internal fun SmartFlightCard(language:AppLanguage,f:NuiteeFlightSnapshot,out:Boolean) {
    fun t(ru:String,en:String,uz:String,cy:String)=nuiteeText(language,ru,en,uz,cy)
    var detail by remember(f.id) { mutableStateOf(false) }
    val context=LocalContext.current
    val muted=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f)
    val direction=if(out) t("Туда","Outbound","Borish","Бориш") else t("Обратно","Return","Qaytish","Қайтиш")
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).border(.75.dp,MaterialTheme.colorScheme.onSurface.copy(alpha=.095f),RoundedCornerShape(28.dp)).clickable { detail=true }.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            val code=f.airlineCode.uppercase(Locale.US).takeIf { it.matches(Regex("[A-Z0-9]{2}")) }
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),contentAlignment=Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.Airplane,null,Modifier.size(21.dp),IumrahIconRole.TRAVEL.color)
                if(code!=null) AsyncImage(model="https://www.gstatic.com/flights/airline_logos/70px/$code.png",contentDescription=null,modifier=Modifier.size(42.dp).background(Color.White),contentScale=ContentScale.Fit)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(direction.uppercase(Locale.forLanguageTag(language.localeTag)),fontSize=11.sp,fontWeight=FontWeight.Bold,color=muted)
                Text("${f.origin} → ${f.destination}",fontSize=17.sp,fontWeight=FontWeight.Bold)
                Text("${f.airline} ${f.flightNumber}",fontSize=12.sp,color=muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(localTime(f.departureAt,f.origin,language),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            }
            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                CupertinoIcon(CupertinoSymbol.Airplane,null,Modifier.size(17.dp),IumrahIconRole.TRAVEL.color)
                Text(if(f.stops==0) t("прямой","direct","to‘g‘ridan-to‘g‘ri","тўғридан-тўғри") else "${f.stops} "+t("пересад.","stops","ulanish","уланиш"),fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=muted)
                CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(11.dp),muted.copy(alpha=.35f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(t("Вылет","Departure","Uchish","Учиш"),fontSize=12.sp,color=muted); Text(localTime(f.departureAt,f.origin,language),fontSize=12.sp,fontWeight=FontWeight.SemiBold) }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(t("Прилёт","Arrival","Kelish","Келиш"),fontSize=12.sp,color=muted); Text(localTime(f.arrivalAt,f.destination,language),fontSize=12.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.End) }
        }
    }
    if(detail) Dialog(onDismissRequest={ detail=false },properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(if(MaterialTheme.colorScheme.background==Color.Black) Color(0xFF0B0B0B) else Color(0xFFF2F2F2))) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF111111)).statusBarsPadding().height(48.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={ detail=false }) { CupertinoIcon(CupertinoSymbol.ChevronLeft,t("Назад","Back","Orqaga","Орқага"),Modifier.size(20.dp),Color.White) }
                Text(f.airline,Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                IconButton(onClick={ runCatching { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply { type="text/plain"; putExtra(android.content.Intent.EXTRA_TEXT,"${f.airline} ${f.flightNumber}\n${f.origin} → ${f.destination}\n${f.departureAt}") },null)) } }) { CupertinoIcon(CupertinoSymbol.Share,t("Поделиться","Share","Ulashish","Улашиш"),Modifier.size(20.dp),Color.White) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start=18.dp,end=18.dp,bottom=18.dp)) {
                IumrahPremiumFlightDetail(PremiumFlightLeg(f.origin,f.destination,f.departureAt,f.arrivalAt,f.airlineCode,f.flightNumber,f.airline,f.stops,f.durationMinutes),language,pageHeroInset=18.dp)
            }
        }
    }
}
@Composable internal fun SmartHotelCard(language:AppLanguage,room:NuiteeRoomSelection,canReplace:Boolean,replacing:String?,onReplace:(String)->Unit) {
    fun t(ru:String,en:String,uz:String,cy:String)=nuiteeText(language,ru,en,uz,cy)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(24.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(108.dp).height(118.dp).clip(RoundedCornerShape(17.dp)).background(smartSecondary()),contentAlignment=Alignment.Center) { SmartIcon(CupertinoSymbol.Building); room.hotel.coverImageURL?.takeIf { it.isNotBlank() }?.let { AsyncImage(model=it,contentDescription=room.hotel.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop) } }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(if(room.city=="madinah") t("Медина","Madinah","Madina","Мадина") else t("Мекка","Makkah","Makka","Макка"),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
                Text(room.hotel.name,fontSize=17.sp,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) { repeat(room.hotel.roundedStars) { CupertinoIcon(CupertinoSymbol.StarFill,null,Modifier.size(12.dp)) } }
                Text(room.rate.roomName,fontSize=15.sp); SmartLabel(CupertinoSymbol.Persons,"${room.adults+room.childrenAges.size}"); if(room.rate.mealPlan.isNotBlank()) Text(room.rate.mealPlan,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
            }
        }
        SmartLabel(CupertinoSymbol.Calendar,"${room.checkin} – ${room.checkout}")
        val nights=ChronoUnit.DAYS.between(room.checkin,room.checkout)
        if(nights>0 && room.rate.price.isFinite() && room.rate.price>0) SmartPriceLine(t("В среднем за номер / ночь","Average per room / night","Xona / tun uchun o‘rtacha","Хона / тун учун ўртача"),smartMoney(BigDecimal.valueOf(room.rate.price/nights),room.rate.currency,language),true)
        if(canReplace && room.roomIndex==0) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(smartSecondary()).clickable(enabled=replacing==null) { onReplace(room.city) }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(t("Изменить отель","Change hotel","Mehmonxonani o‘zgartirish","Меҳмонхонани ўзгартириш"),Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold); if(replacing==room.city) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp) else SmartIcon(CupertinoSymbol.Refresh,30.dp) }
    }
}
@Composable internal fun SmartAssemblyContent(language:AppLanguage,progress:Progress,city:String?,busy:Boolean,preview:JourneyState?,ready:Boolean,failure:String?,replacing:String?,replacementError:Boolean,onEdit:()->Unit,onReplace:(String)->Unit) {
    fun t(ru:String,en:String,uz:String,cy:String)=nuiteeText(language,ru,en,uz,cy)
    Column(Modifier.fillMaxWidth().animateContentSize(tween(350)), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SmartProgressRow(progress,Progress.FLIGHTS,t("Авиабилеты","Flights","Aviachiptalar","Авиа чипталар"),CupertinoSymbol.Airplane,busy)
        preview?.nuitee?.flights.orEmpty().forEachIndexed { i,f -> SmartFlightCard(language,f,i==0) }
        SmartProgressRow(progress,Progress.HOTELS,t("Отели и номера","Hotels and rooms","Mehmonxonalar va xonalar","Меҳмонхоналар ва хоналар"),CupertinoSymbol.BuildingFill,busy)
        preview?.nuitee?.rooms.orEmpty().values.sortedWith(compareBy<NuiteeRoomSelection>{it.checkin}.thenBy{it.key}).forEach { SmartHotelCard(language,it,ready,replacing,onReplace) }
        if(city!=null && busy) SmartLabel(CupertinoSymbol.Search,t("Подбираем варианты проживания","Finding stay options","Yashash variantlarini tanlaymiz","Яшаш вариантларини танлаймиз")+" · "+if(city=="madinah") t("Медина","Madinah","Madina","Мадина") else t("Мекка","Makkah","Makka","Макка"))
        if(replacementError) Text(t("Не удалось пересчитать изменения. Текущий пакет сохранён.","Could not reprice changes. Your current package is unchanged.","O‘zgarishlar hisoblanmadi. Joriy paket saqlandi.","Ўзгаришлар ҳисобланмади. Жорий пакет сақланди."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
        SmartProgressRow(progress,Progress.TRANSFER,t("Маршрут и трансфер","Route and transfer","Yo‘nalish va transfer","Йўналиш ва трансфер"),CupertinoSymbol.Car,busy)
        if(preview!=null && preview.transferSelectionConfirmed) Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(20.dp)).padding(14.dp)) { SmartFeature(CupertinoSymbol.Car,preview.resolvedTransferVehicle.modelName) }
        SmartProgressRow(progress,Progress.PRICE,t("Стоимость пакета","Package price","Paket narxi","Пакет нархи"),CupertinoSymbol.CheckCircle,busy)
        if(ready && preview!=null) preview.quote?.let { quote -> Column(Modifier.fillMaxWidth().background(smartSecondary(),RoundedCornerShape(22.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${preview.trip.origin} → ${preview.nuitee?.flights?.firstOrNull()?.destination.orEmpty()} · ${preview.nuitee?.flights?.lastOrNull()?.origin.orEmpty()} → ${preview.trip.origin}",fontSize=17.sp,fontWeight=FontWeight.Bold)
            Text("${preview.trip.departureDate} — ${preview.trip.returnDate}",fontSize=15.sp)
            Text(if(preview.nuitee?.flights.orEmpty().any { it.stops>0 }) t("Есть пересадка","Includes a connection","Ulanish reysi bor","Уланиш рейси бор") else t("Прямые рейсы","Nonstop flights","To‘g‘ridan-to‘g‘ri reyslar","Тўғридан-тўғри рейслар"),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text(smartMoney(quote.totalPackagePrice,quote.currency,language),fontSize=34.sp,fontWeight=FontWeight.Bold); Text(t("Предварительно за всех путешественников","Estimated total for all travelers","Barcha sayohatchilar uchun taxminiy jami","Барча саёҳатчилар учун тахминий жами"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f)); SmartPriceLine(t("На одного паломника","Per pilgrim","Bir ziyoratchi uchun","Бир зиёратчи учун"),smartMoney(quote.pricePerPerson,quote.currency,language),false)
            Text(t("Наличие и окончательную стоимость подтвердим вручную перед оплатой.","Availability and final price will be confirmed manually before payment.","Mavjudlik va yakuniy narx to‘lovdan oldin tasdiqlanadi.","Мавжудлик ва якуний нарх тўловдан олдин тасдиқланади."),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
        } }
        if(failure!=null) Column(Modifier.fillMaxWidth().background(smartSecondary(),RoundedCornerShape(20.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(t("Не удалось собрать пакет","We couldn't build a package","Paketni tayyorlab bo‘lmadi","Пакетни тайёрлаб бўлмади"),fontSize=17.sp,fontWeight=FontWeight.Bold)
            Text(when { failure.contains("CORE_CUTOVER_PENDING") -> t("Сервер бронирований ещё не готов. Попробуйте позже.","The booking server is not ready yet. Try again later.","Bronlash serveri hali tayyor emas. Keyinroq urinib ko‘ring.","Бронлаш сервери ҳали тайёр эмас. Кейинроқ уриниб кўринг."); failure.contains("SMART_AVIASALES_PARTY_FARE_UNAVAILABLE") -> t("Для поездки с детьми пока нет подходящих опубликованных рейсов. Выберите другой месяц или длительность.","No suitable published flights for your family yet. Try another month or duration.","Oilaviy safar uchun mos e’lon qilingan reyslar topilmadi. Boshqa oy yoki muddatni tanlang.","Оилавий сафар учун мос эълон қилинган рейслар топилмади. Бошқа ой ёки муддатни танланг."); failure.contains("SMART_HOTEL_NETWORK") -> t("Сервис тарифов временно не отвечает. Повторите поиск.","Hotel rates are temporarily unavailable. Retry the search.","Tarif xizmati vaqtincha ishlamayapti. Qayta urinib ko‘ring.","Тариф хизмати вақтинча ишламаяпти. Қайта уриниб кўринг."); failure.contains("SMART_ROOM") || failure.contains("SMART_HOTEL") -> t("Не найден подходящий номер с доступным тарифом. Измените месяц или категорию.","No suitable room with an available rate was found. Change the month or category.","Mos tarifli xona topilmadi. Oy yoki toifani o‘zgartiring.","Мос тарифли хона топилмади. Ой ёки тоифани ўзгартиринг."); else -> t("Попробуйте другой месяц, длительность или категорию. Можно повторить поиск.","Try another month, duration or category, or retry the search.","Boshqa oy, muddat yoki toifani tanlang yoki qayta qidiring.","Бошқа ой, муддат ёки тоифани танланг ёки қайта қидиринг.") },fontSize=15.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
            OutlinedButton(onClick=onEdit) { Text(t("Изменить параметры","Edit preferences","Parametrlarni o‘zgartirish","Параметрларни ўзгартириш")) }
        }
    }
}
@Composable private fun SmartProgressRow(current:Progress,stage:Progress,title:String,symbol:CupertinoSymbol,busy:Boolean) {
    if(current.ordinal<stage.ordinal) return
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        SmartIcon(symbol,46.dp); Text(title,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.Medium)
        if(current.ordinal>stage.ordinal) CupertinoIcon(CupertinoSymbol.CheckCircleFill,null,Modifier.size(20.dp)) else if(busy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else CupertinoIcon(CupertinoSymbol.CircleOutline,null,Modifier.size(20.dp),MaterialTheme.colorScheme.onSurface.copy(alpha=.35f))
    }
}

@Composable internal fun SmartAirportCard(city:String,code:String,onOpen:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(smartSecondary()).clickable(onClick=onOpen).padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        SmartIcon(CupertinoSymbol.AirplaneTakeoff,48.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) { Text(city,fontSize=22.sp,fontWeight=FontWeight.Bold); Text(code,fontSize=15.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f)) }
        CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(18.dp),MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
    }
}
