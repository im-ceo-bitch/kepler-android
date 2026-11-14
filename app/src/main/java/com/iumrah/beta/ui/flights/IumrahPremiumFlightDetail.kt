package com.iumrah.beta.ui.flights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import com.iumrah.beta.models.flight.Airport
import com.iumrah.beta.core.network.APIClient
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.geometry.GlobeVector
import com.iumrah.beta.core.geometry.IumrahGlobeGeometry
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.*
import com.iumrah.beta.ui.components.IumrahFlightHemisphere
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

internal data class PremiumFlightLeg(val origin: String,val destination: String,val departure: String,val arrival: String?,val carrier: String,val number: String,val title: String,val stops: Int?,val duration: Int,val cabin: String?=null,val aircraft: String?=null,val operatingCarrier: String?=null)
private fun tr(l: AppLanguage,ru: String,en: String,uz: String,cy: String)=when(l) { AppLanguage.RUSSIAN -> ru; AppLanguage.UZBEK -> uz; AppLanguage.UZBEK_CYRILLIC -> cy; else -> en }
internal val FlightHeroColor=Color(0xFF111111)
private fun timeParts(raw: String?,airport: String): Pair<String,String>? {
    val value=raw?.takeIf { it.isNotBlank() } ?: return null
    val parsed=FlightDataParity.instant(value)
    if(parsed!=null) {
        val zone=FlightReferenceCatalog.timeZone(airport) ?: runCatching { OffsetDateTime.parse(value).offset }.getOrNull() ?: return null
        val local=parsed.atZone(zone); return local.toLocalDate().toString() to local.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val local=runCatching { LocalDateTime.parse(value.replace(' ','T')) }.getOrNull() ?: return null
    return local.toLocalDate().toString() to local.format(DateTimeFormatter.ofPattern("HH:mm"))
}
private fun dateText(day: String,l: AppLanguage)=runCatching { LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMM EEE",l.locale)) }.getOrDefault(day)
private fun durationText(m: Int,l: AppLanguage): String { val h=tr(l,"ч","h","soat","соат"); val min=tr(l,"мин","m","daq","дақ"); return if(m<60) "$m $min" else if(m%60==0) "${m/60} $h" else "${m/60} $h ${m%60} $min" }
private fun statusText(status: String?,l: AppLanguage): String?=when(status) {
    "Expected" -> tr(l,"По расписанию","Scheduled","Jadval bo‘yicha","Жадвал бўйича")
    "EnRoute" -> tr(l,"В полёте","In flight","Parvozda","Парвозда")
    "Departed" -> tr(l,"Вылетел","Departed","Jo‘nadi","Жўнади")
    "Approaching" -> tr(l,"На подлёте","Approaching","Yetib kelmoqda","Етиб келмоқда")
    "CheckIn" -> tr(l,"Регистрация","Check-in","Ro‘yxatdan o‘tish","Рўйхатдан ўтиш")
    "Boarding" -> tr(l,"Посадка","Boarding","Samolyotga chiqish","Самолётга чиқиш")
    "GateClosed" -> tr(l,"Посадка завершена","Gate closed","Chiqish yopildi","Чиқиш ёпилди")
    "Delayed" -> tr(l,"Задержан","Delayed","Kechikmoqda","Кечикмоқда")
    "Arrived" -> tr(l,"Прибыл","Arrived","Yetib keldi","Етиб келди")
    "Canceled" -> tr(l,"Отменён","Canceled","Bekor qilindi","Бекор қилинди")
    "Diverted" -> tr(l,"Маршрут изменён","Diverted","Yo‘nalish o‘zgardi","Йўналиш ўзгарди")
    "CanceledUncertain" -> tr(l,"Возможна отмена","Possible cancellation","Bekor qilinishi mumkin","Бекор қилиниши мумкин")
    else -> null
}
private fun detailMessage(reason: String?,l: AppLanguage)=when(reason) {
    "no_match" -> tr(l,"Дополнительное расписание ещё не опубликовано","Additional schedule not published yet","Qo‘shimcha jadval hali e’lon qilinmagan","Қўшимча жадвал ҳали эълон қилинмаган")
    "quota_exhausted","provider_quota" -> tr(l,"Лимит обновлений исчерпан","Flight update limit reached","Yangilash limiti tugadi","Янгилаш лимити тугади")
    "not_configured","http_404","http_503" -> tr(l,"Сервис данных рейса недоступен","Flight data service unavailable","Reys ma’lumotlari xizmati mavjud emas","Рейс маълумотлари хизмати мавжуд эмас")
    else -> tr(l,"Не удалось обновить данные рейса","Could not update flight data","Reys ma’lumotlarini yangilab bo‘lmadi","Рейс маълумотларини янгилаб бўлмади")
}

/** Shared search, guided selection, package and booked-leg view. Never poll the provider; render only actual telemetry. */
@Composable internal fun IumrahPremiumFlightDetail(leg: PremiumFlightLeg,language: AppLanguage,modifier: Modifier=Modifier,pageHeroInset: Dp=0.dp) {
    val number=FlightOperationalPolicy.normalize(leg.number,leg.carrier)
    val airportService=remember { AirportSearchService(APIClient()) }
    val bundledFrom=remember(leg.origin) { AirportMapBootstrapCatalog.airports.firstOrNull { it.iata.equals(leg.origin,true) } }
    val bundledTo=remember(leg.destination) { AirportMapBootstrapCatalog.airports.firstOrNull { it.iata.equals(leg.destination,true) } }
    var resolvedFrom by remember(leg.origin) { mutableStateOf(bundledFrom) }
    var resolvedTo by remember(leg.destination) { mutableStateOf(bundledTo) }
    LaunchedEffect(leg.origin) { if(bundledFrom==null && leg.origin.matches(Regex("[A-Z]{3}"))) resolvedFrom=airportService.search(leg.origin,4).firstOrNull { it.iata.equals(leg.origin,true) } }
    LaunchedEffect(leg.destination) { if(bundledTo==null && leg.destination.matches(Regex("[A-Z]{3}"))) resolvedTo=airportService.search(leg.destination,4).firstOrNull { it.iata.equals(leg.destination,true) } }
    val local=timeParts(leg.departure,leg.origin)
    val identity=if(leg.stops==0 && number!=null && local!=null) FlightOperationalPolicy.key(number,local.first,local.second,leg.origin,leg.destination) else null
    var details by remember(leg) { mutableStateOf<IumrahFlightOperationalDetail?>(null) }
    var busy by remember(leg) { mutableStateOf(identity!=null) }
    var refresh by remember(leg) { mutableIntStateOf(0) }
    var reason by remember(leg) { mutableStateOf<String?>(null) }
    LaunchedEffect(leg,refresh) {
        if(identity==null || number==null || local==null) return@LaunchedEffect
        busy=true
        try { val result=IumrahFlightDetailService.shared.detail(number,local.first,local.second,leg.origin,leg.destination,force=refresh>0)
            if(result.detail!=null) details=result.detail
            reason=result.reason
        } finally { busy=false }
    }
    var now by remember(leg) { mutableStateOf(Instant.now()) }
    val active=details?.status in setOf("EnRoute","Approaching","Departed")
    LaunchedEffect(active,leg) { now=Instant.now(); if(active) while(true) { delay(60_000); now=Instant.now() } }
    val position=details?.position?.takeIf { IumrahGlobeGeometry.isLivePosition(details?.status,it.reportedAt,it.latitude,it.longitude,now) }
    val live=position?.let { GlobeVector.geographic(it.latitude,it.longitude) }
    val departure=details?.departureScheduled?.takeIf { it.isNotBlank() } ?: leg.departure
    val scheduledArrival=details?.arrivalScheduled?.takeIf { it.isNotBlank() } ?: leg.arrival?.takeIf { it.isNotBlank() }
    val estimate=if(scheduledArrival==null && leg.stops==0 && leg.duration>0) FlightDataParity.instant(leg.departure)?.let { start -> FlightReferenceCatalog.timeZone(leg.destination)?.let { zone -> start.plusSeconds(leg.duration.toLong()*60).atZone(zone).toLocalDateTime().toString() } } else null
    val arrival=scheduledArrival ?: estimate
    val duration=run { val a=FlightDataParity.instant(details?.departureScheduled); val b=FlightDataParity.instant(details?.arrivalScheduled); if(a!=null && b!=null && b>a) Duration.between(a,b).toMinutes().toInt() else if(leg.duration>0) leg.duration else { val start=FlightDataParity.instant(leg.departure); val end=FlightDataParity.instant(leg.arrival); if(start!=null && end!=null && end>start) Duration.between(start,end).toMinutes().toInt() else 0 } }
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().layout { measurable, constraints ->
            val inset=pageHeroInset.roundToPx()
            if(inset>0 && constraints.hasBoundedWidth) {
                val w=constraints.maxWidth+inset*2
                val placeable=measurable.measure(constraints.copy(minWidth=w,maxWidth=w))
                layout(constraints.maxWidth,placeable.height) { placeable.placeRelative(-inset,0) }
            } else { val placeable=measurable.measure(constraints); layout(placeable.width,placeable.height) { placeable.placeRelative(0,0) } }
        }.clip(RoundedCornerShape(if(pageHeroInset>0.dp) 0.dp else 22.dp)).background(FlightHeroColor)) {
            Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=if(pageHeroInset>0.dp) 12.dp else 18.dp,bottom=10.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(if(leg.stops==0 && number!=null) number else leg.title,Modifier.weight(1f),fontSize=if(leg.stops==0 && number!=null) 29.sp else 20.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=2)
                    statusText(details?.status,language)?.let { status -> Text(status,fontSize=12.sp,fontWeight=FontWeight.Bold,color=when { active || details?.status=="Arrived" -> Color(0xFF34C759); details?.status in setOf("Delayed","Canceled","Diverted","CanceledUncertain") -> Color(0xFFFF9500); else -> Color.White.copy(alpha=.8f) },textAlign=TextAlign.End) }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Top) {
                    Endpoint(leg.origin,departure,language,false,false,Modifier.weight(1f))
                    Column(Modifier.weight(1f).padding(top=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Icon(CupertinoSymbol.Airplane,null,Modifier.size(24.dp),if(live==null) Color.White else Color(0xFF34C759))
                        if(duration>0) Text(durationText(duration,language),fontSize=12.sp,color=Color.White.copy(alpha=.82f),maxLines=1)
                        leg.stops?.let { Text(if(it==0) tr(language,"Прямой","Direct","To‘g‘ri","Тўғри") else tr(language,"Пересадок: $it","Stops: $it","Ulanish: $it","Уланиш: $it"),fontSize=11.sp,color=Color.White.copy(alpha=.76f),textAlign=TextAlign.Center) }
                    }
                    Endpoint(leg.destination,arrival,language,true,estimate!=null,Modifier.weight(1f))
                }
            }
            IumrahFlightHemisphere(leg.origin,leg.destination,leg.stops==0,live,resolvedFrom?.takeIf { it.lat.isFinite() && it.lon.isFinite() && kotlin.math.abs(it.lat)<=90 && kotlin.math.abs(it.lon)<=180 }?.let { GlobeVector.geographic(it.lat,it.lon) },resolvedTo?.takeIf { it.lat.isFinite() && it.lon.isFinite() && kotlin.math.abs(it.lat)<=90 && kotlin.math.abs(it.lon)<=180 }?.let { GlobeVector.geographic(it.lat,it.lon) })
        }
        if(position!=null && (position.altitudeFeet!=null || position.speedKmh!=null)) PremiumCard {
            Text(tr(language,"В полёте","In flight","Parvozda","Парвозда"),fontWeight=FontWeight.Bold,fontSize=19.sp)
            Fact(tr(language,"Высота","Altitude","Balandlik","Баландлик"),position.altitudeFeet?.takeIf { it.isFinite() }?.let { "${it.toInt()} ft" })
            Fact(tr(language,"Скорость","Speed","Tezlik","Тезлик"),position.speedKmh?.takeIf { it.isFinite() }?.let { "${it.toInt()} km/h" })
        }
        PremiumCard {
            Text(tr(language,"Аэропорты","Airports","Aeroportlar","Аэропортлар"),fontSize=19.sp,fontWeight=FontWeight.Bold)
            AirportRow(leg.origin,departure,language,false,resolvedFrom,details?.departureTerminal,details?.departureGate,details?.departureRevised,details?.departureActual)
            HorizontalDivider()
            AirportRow(leg.destination,arrival,language,true,resolvedTo,details?.arrivalTerminal,details?.arrivalGate,details?.arrivalRevised,details?.arrivalActual)
            details?.baggageBelt?.takeIf { it.isNotBlank() }?.let { HorizontalDivider(); Fact(tr(language,"Лента багажа","Baggage belt","Bagaj lentasi","Багаж лентаси"),it) }
        }
        if(listOf(details?.aircraft,leg.aircraft,details?.registration,leg.cabin,leg.operatingCarrier).any { !it.isNullOrBlank() }) PremiumCard {
            Text(tr(language,"Самолёт и класс","Aircraft & cabin","Samolyot va klass","Самолёт ва класс"),fontWeight=FontWeight.Bold,fontSize=19.sp)
            Fact(tr(language,"Самолёт","Aircraft","Samolyot","Самолёт"),details?.aircraft ?: leg.aircraft)
            Fact(tr(language,"Бортовой номер","Registration","Bort raqami","Борт рақами"),details?.registration)
            Fact(tr(language,"Класс","Cabin","Klass","Класс"),leg.cabin)
            Fact(tr(language,"Выполняет рейс","Operated by","Parvoz operatori","Парвоз оператори"),leg.operatingCarrier)
        }
        if(identity!=null) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            if(busy) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
            Text(if(busy) tr(language,"Обновляем расписание","Updating schedule","Jadval yangilanmoqda","Жадвал янгиланмоқда") else if(reason!=null || details==null) detailMessage(reason,language) else "AeroDataBox"+(details?.updatedAt?.let { " · ${it.take(16).replace('T',' ')}" } ?: ""),Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!busy && reason !in setOf("quota_exhausted","provider_quota")) IconButton(onClick={refresh++}) { Icon(CupertinoSymbol.Refresh,tr(language,"Обновить","Refresh","Yangilash","Янгилаш"),Modifier.size(18.dp)) }
        }
    }
}
@Composable private fun Endpoint(code: String,raw: String?,l: AppLanguage,end: Boolean,estimated: Boolean,modifier: Modifier) {
    Column(modifier,horizontalAlignment=if(end) Alignment.End else Alignment.Start,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(code.uppercase(),fontSize=38.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1,softWrap=false)
        timeParts(raw,code)?.let { Text(it.second,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=1,softWrap=false) }
        if(estimated) Text(tr(l,"Расчётное","Estimated","Hisoblangan","Ҳисобланган"),fontSize=10.sp,color=Color.White.copy(alpha=.78f))
    }
}
@Composable private fun PremiumCard(content: @Composable ColumnScope.()->Unit) {
    val dark=MaterialTheme.colorScheme.background.red<.5f; val shape=RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().background(if(dark) Color(0xFF1B1B1B) else Color.White,shape).border(.75.dp,MaterialTheme.colorScheme.onSurface.copy(alpha=.07f),shape).padding(17.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable private fun AirportRow(code: String,raw: String?,l: AppLanguage,end: Boolean,resolved: Airport?,terminal: String?,gate: String?,revised: String?,actual: String?) {
    val airport=FlightReferenceCatalog.airport(code)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.surfaceVariant,CircleShape),contentAlignment=Alignment.Center) { Icon(if(end) CupertinoSymbol.AirplaneLand else CupertinoSymbol.AirplaneTakeoff,null,Modifier.size(16.dp)) }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(if(end) tr(l,"Прилёт","Arrival","Yetib kelish","Етиб келиш") else tr(l,"Вылет","Departure","Jo‘nash","Жўнаш"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val day=timeParts(raw,code)?.first ?: raw?.takeIf { it.length==10 }; if(day!=null) Text(dateText(day,l),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(resolved?.city ?: airport?.city ?: code,fontSize=18.sp,fontWeight=FontWeight.Bold)
            (resolved?.name ?: airport?.name)?.let { Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Fact(tr(l,"Терминал","Terminal","Terminal","Терминал"),terminal); Fact(tr(l,"Выход","Gate","Chiqish","Чиқиш"),gate)
            val actualTime=timeParts(actual,code); val revisedTime=timeParts(revised,code)
            if(actualTime!=null) Fact(tr(l,"Фактическое время ВПП","Actual runway time","Haqiqiy uchish vaqti","Ҳақиқий учиш вақти"),dateText(actualTime.first,l)+" · "+actualTime.second)
            else if(revisedTime!=null) Fact(tr(l,"Обновлённое время","Revised time","Yangilangan vaqt","Янгиланган вақт"),dateText(revisedTime.first,l)+" · "+revisedTime.second)
        }
    }
}
@Composable private fun Fact(label: String,value: String?) { if(!value.isNullOrBlank()) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text(label,Modifier.weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant); Text(value,fontSize=13.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.End) } }
