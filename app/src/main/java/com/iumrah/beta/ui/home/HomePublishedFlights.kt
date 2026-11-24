@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.iumrah.beta.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import com.iumrah.beta.ui.flights.IumrahPremiumFlightDetail
import com.iumrah.beta.ui.flights.PremiumFlightLeg
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.data.flight.FlightDataParity
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.DateFlexibility
import com.iumrah.beta.domain.trip.FlightTripType
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageFlightPath
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Unlike live Ignav discovery, this carousel reads staff-published Business/D1
 * flights only. A 5-minute origin-scoped cache deduplicates simultaneous loads.
 */
internal object PublishedHomeCatalog {
    private data class Snapshot(val timeMs: Long, val flights: List<CuratedFlightRecommendation>)
    private val lock = Mutex()
    private val records = mutableMapOf<String, Snapshot>()
    suspend fun get(origin: String, trip: TripDraft, service: CuratedFlightRecommendationService, force: Boolean = false): List<CuratedFlightRecommendation> = lock.withLock {
        val current = records[origin]
        if (!force && current != null && System.currentTimeMillis() - current.timeMs < 300_000L) return@withLock current.flights
        val result = service.load(trip)
        records[origin] = Snapshot(System.currentTimeMillis(), result)
        result
    }
}

internal data class PublishedHomeLeg(
    val rowID: String,
    val leg: CuratedFlightRecommendation.Leg,
    val isPair: Boolean,
    val priceUsd: Double,
) {
    val id: String get() = "$rowID:${leg.origin}:${leg.departureAt}"
}

internal object PublishedHomeRules {
    fun day(iso: String, airport: String): LocalDate? {
        val instant = FlightDataParity.instant(iso)
        val zone = FlightReferenceCatalog.timeZone(airport)
        return if (instant != null && zone != null) instant.atZone(zone).toLocalDate()
        else runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
    }

    fun filtered(rows: List<CuratedFlightRecommendation>, trip: TripDraft, direction: Int, outbound: PublishedHomeLeg?): List<PublishedHomeLeg> {
        val from = if (direction == 0) trip.originCode else trip.returnOriginCode
        val to = if (direction == 0) trip.outboundDestinationCode else trip.originCode
        val cabin = trip.effectiveFlightFilters.cabinClass.wireValue
        val result = buildList {
            for (row in rows) {
                val price = row.fareUsdPerTraveler?.takeIf { it.isFinite() && it > 0.0 } ?: continue
                if (!row.nonstop || row.cabinClass.lowercase().replace(' ', '_') != cabin) continue
                for (leg in listOfNotNull(row.outbound, row.inbound)) {
                    if (!leg.origin.equals(from,true) || !leg.destination.equals(to,true) || leg.stops != 0 ||
                        leg.cabinClass.lowercase().replace(' ', '_') != cabin) continue
                    val date = day(leg.departureAt, leg.origin) ?: continue
                    if (date.isBefore(LocalDate.now())) continue
                    val candidate = PublishedHomeLeg(row.id, leg, row.inbound != null, price)
                    if (direction == 1 && outbound != null && !compatible(outbound,candidate,trip.scope)) continue
                    add(candidate)
                }
            }
        }
        return result.sortedWith(compareBy<PublishedHomeLeg> { it.leg.departureAt }.thenBy { it.id }).distinctBy { it.id }
    }

    fun compatible(outbound: PublishedHomeLeg, inbound: PublishedHomeLeg, scope: JourneyScope): Boolean {
        if (!outbound.leg.origin.equals(inbound.leg.destination,true)) return false
        val arrival = day(outbound.leg.arrivalAt,outbound.leg.destination) ?: return false
        val returns = day(inbound.leg.departureAt,inbound.leg.origin) ?: return false
        if (!returns.isAfter(arrival)) return false
        if (scope == JourneyScope.MAKKAH_AND_MADINAH && java.time.temporal.ChronoUnit.DAYS.between(arrival,returns) < 2) return false
        if (scope == JourneyScope.MAKKAH_ONLY && (outbound.leg.destination != "JED" || inbound.leg.origin != "JED")) return false
        if (outbound.isPair || inbound.isPair) return outbound.isPair && inbound.isPair && outbound.rowID == inbound.rowID
        return outbound.rowID != inbound.rowID
    }
}

private fun ft(l: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when(l) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
    else -> en
}

@Composable
internal fun HomePublishedFlightCarousel(language: AppLanguage, trip: TripDraft, journey: JourneyStore,
                                         service: CuratedFlightRecommendationService, chrome: AppChromeStore) {
    val origin = trip.originCode
    val routeKey = "${origin}:${trip.outboundDestinationCode}:${trip.returnOriginCode}:${trip.effectiveFlightFilters.cabinClass.wireValue}:${trip.scope}"
    var direction by remember { mutableIntStateOf(0) }
    var outbound by remember(routeKey) { mutableStateOf<PublishedHomeLeg?>(null) }
    var inbound by remember(routeKey) { mutableStateOf<PublishedHomeLeg?>(null) }
    var opened by remember { mutableStateOf<PublishedHomeLeg?>(null) }
    var rows by remember(origin) { mutableStateOf<List<CuratedFlightRecommendation>?>(null) }
    var error by remember(origin) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(origin, reload) {
        try {
            rows = PublishedHomeCatalog.get(origin, trip, service, force = reload != 0)
            error = false
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    // Other controls on Home may change dates. Never keep a silently stale pair.
    LaunchedEffect(trip.departureDate,trip.returnDate,routeKey) {
        if (outbound != null && PublishedHomeRules.day(outbound!!.leg.departureAt,outbound!!.leg.origin) != trip.departureDate) {
            outbound = null; inbound = null
        }
        if (inbound != null && PublishedHomeRules.day(inbound!!.leg.departureAt,inbound!!.leg.origin) != trip.returnDate) inbound = null
    }
    val list = remember(rows,trip,direction,outbound) { PublishedHomeRules.filtered(rows.orEmpty(),trip,direction,outbound) }
    val complete = outbound != null && inbound != null && PublishedHomeRules.compatible(outbound!!,inbound!!,trip.scope)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(ft(language,"Прямые рейсы","Direct flights","To‘g‘ri reyslar","Тўғри рейслар"),fontSize=22.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.045f)).padding(3.dp)) {
            listOf(ft(language,"Туда","Outbound","Borish","Бориш"),ft(language,"Обратно","Return","Qaytish","Қайтиш")).forEachIndexed { idx,title ->
                val selected=direction==idx
                IumrahPressable(onClick={direction=idx},modifier=Modifier.weight(1f).height(36.dp),cornerRadius=10.dp,
                    background=if(selected) MaterialTheme.colorScheme.surface else Color.Transparent,shadowElevation=0.dp) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text(title,fontWeight=if(selected)FontWeight.Bold else FontWeight.Medium,fontSize=13.sp) }
                }
            }
        }
        if (list.isNotEmpty()) {
            val carousel=rememberLazyListState()
            LazyRow(state=carousel,flingBehavior=rememberSnapFlingBehavior(lazyListState=carousel),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                items(list,key={it.id}) { flight ->
                    val selected=(if(direction==0)outbound else inbound)?.id==flight.id
                    PublishedResultCard(language,flight,selected,onOpen={opened=flight},onSelect={
                        val day=PublishedHomeRules.day(flight.leg.departureAt,flight.leg.origin)
                        if(day!=null) {
                            if(direction==0) {
                                outbound=flight
                                if(inbound!=null && !PublishedHomeRules.compatible(flight,inbound!!,trip.scope)) inbound=null
                                journey.updateTrip(trip.copy(departureDate=day,returnDate=if(trip.returnDate.isAfter(day))trip.returnDate else day.plusDays(7)))
                            } else if(day.isAfter(trip.departureDate) && (outbound==null || PublishedHomeRules.compatible(outbound!!,flight,trip.scope))) {
                                inbound=flight;journey.updateTrip(trip.copy(returnDate=day))
                            }
                        }
                    })
                }
            }
        } else {
            Text(if(error) ft(language,"Не удалось загрузить рейсы","Could not load flights","Reyslar yuklanmadi","Рейслар юкланмади")
                else if(rows==null) ft(language,"Получаем расписание рейсов","Getting flight schedules","Reyslar jadvali olinmoqda","Рейслар жадвали олинмоқда")
                else ft(language,"Нет опубликованных рейсов по этому направлению","No published flights for this route","Bu yo‘nalishda e’lon qilingan reyslar yo‘q","Бу йўналишда эълон қилинган рейслар йўқ"),
                fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.65f))
            if(error) TextButton(onClick={reload++}) { Text(ft(language,"Обновить рейсы","Refresh flights","Reyslarni yangilash","Рейсларни янгилаш")) }
        }
        if (complete) IumrahPressable(onClick={
            val a=outbound;val b=inbound
            if(a!=null && b!=null && PublishedHomeRules.compatible(a,b,trip.scope)) {
                val dep=PublishedHomeRules.day(a.leg.departureAt,a.leg.origin)
                val arrival=PublishedHomeRules.day(a.leg.arrivalAt,a.leg.destination)
                val back=PublishedHomeRules.day(b.leg.departureAt,b.leg.origin)
                val returnAirport=SaudiArrivalAirport.entries.firstOrNull{it.iata==b.leg.origin}
                if(dep!=null && arrival!=null && back!=null && returnAirport!=null) {
                    val newTrip=trip.copy(departureDate=dep,returnDate=back,saudiArrivalDate=arrival,
                        selectedReturnAirport=returnAirport,flightTripType=FlightTripType.ROUND_TRIP,flexibility=DateFlexibility.EXACT)
                    if(newTrip.canContinue) {
                        val pair=a.isPair && b.isPair && a.rowID==b.rowID
                        journey.commitTripBuilder(newTrip,PackageFlightPath.PUBLISHED_DIRECT,
                            completeID=if(pair)a.rowID else null,
                            outboundID=if(!pair)a.rowID else null,
                            returnID=if(!pair)b.rowID else null)
                        chrome.openHotelSelection()
                    }
                }
            }
        },modifier=Modifier.fillMaxWidth().height(52.dp),cornerRadius=26.dp,background=Color.Black) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Text(ft(language,"Продолжить и выбрать отель","Continue and choose hotel","Davom etish va mehmonxona tanlash","Давом этиш ва меҳмонхона танлаш"),color=Color.White,fontWeight=FontWeight.SemiBold)
            }
        }
    }
    opened?.let { flight ->
        val leg=flight.leg
        Dialog(onDismissRequest={opened=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { IconButton(onClick={opened=null}) { CupertinoIcon(CupertinoSymbol.ChevronLeft,ft(language,"Назад","Back","Orqaga","Орқага"),Modifier.size(20.dp)) };Text("${leg.origin} → ${leg.destination}",fontWeight=FontWeight.SemiBold) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    IumrahPremiumFlightDetail(PremiumFlightLeg(leg.origin,leg.destination,leg.departureAt,leg.arrivalAt,leg.airlineCode,leg.flightNumber,leg.airline,leg.stops,leg.durationMinutes,leg.cabinClass),language,pageHeroInset=18.dp)
                    Text("$${String.format(Locale.US,"%.2f",flight.priceUsd)}",fontSize=28.sp,fontWeight=FontWeight.Bold)
                    Text(if(flight.isPair)ft(language,"Туда и обратно · на человека","Round trip · per person","Borish va qaytish · bir kishi uchun","Бориш ва қайтиш · бир киши учун") else ft(language,"Стоимость на человека","Fare per person","Bir kishi uchun narx","Бир киши учун нарх"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(flight.isPair) Text(ft(language,"Этот рейс опубликован в паре. Выберите второй рейс из того же маршрута.","This flight belongs to a published pair. Select its matching leg.","Bu reys juft yo‘nalishda. Ikkinchi reysni shu yo‘nalishdan tanlang.","Бу рейс жуфт йўналишда. Иккинчи рейсни шу йўналишдан танланг."),fontSize=13.sp)
                    IumrahPrimaryButton(ft(language,"Добавить в мой пакет","Add to my package","Paketimga qo‘shish","Пакетимга қўшиш")) {
                        val day=PublishedHomeRules.day(leg.departureAt,leg.origin)
                        if(day!=null) {
                            if(direction==0) {
                                outbound=flight
                                if(inbound!=null && !PublishedHomeRules.compatible(flight,inbound!!,trip.scope)) inbound=null
                                journey.updateTrip(trip.copy(departureDate=day,returnDate=if(trip.returnDate.isAfter(day))trip.returnDate else day.plusDays(7)))
                                opened=null
                            } else if(day.isAfter(trip.departureDate) && (outbound==null || PublishedHomeRules.compatible(outbound!!,flight,trip.scope))) {
                                inbound=flight;journey.updateTrip(trip.copy(returnDate=day));opened=null
                            }
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

/** Native Compose mirror of active IumrahFlightResultCard.swift for published fares. */
@Composable private fun PublishedResultCard(l:AppLanguage,flight:PublishedHomeLeg,selected:Boolean,onOpen:()->Unit,onSelect:()->Unit) {
    val leg=flight.leg
    val ink=MaterialTheme.colorScheme.onSurface
    val secondary=MaterialTheme.colorScheme.onSurfaceVariant
    val green=Color(0xFF34C759)
    val shape=RoundedCornerShape(22.dp)
    Box(Modifier.width(290.dp).clip(shape).background(MaterialTheme.colorScheme.surface).border(if(selected)1.5.dp else .75.dp,if(selected)green else ink.copy(alpha=.07f),shape)) {
        Column(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(16.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            Row(Modifier.padding(end=44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),contentAlignment=Alignment.Center) {
                    val code=leg.airlineCode.uppercase()
                    if(code.matches(Regex("[A-Z0-9]{2}"))) AsyncImage(model="https://www.gstatic.com/flights/airline_logos/70px/$code.png",contentDescription=null,modifier=Modifier.size(30.dp),contentScale=ContentScale.Fit)
                    else CupertinoIcon(CupertinoSymbol.Airplane,null,Modifier.size(18.dp),Color.Black)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(leg.airline,fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { Text(leg.flightNumber,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1);Text("iumrah Direct",fontSize=10.sp,color=secondary,maxLines=1) }
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                PublishedEndpoint(l,leg.origin,leg.departureAt,Modifier.weight(1f),Alignment.Start)
                Column(Modifier.weight(.95f).padding(top=2.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    if(leg.durationMinutes>0) Text("${leg.durationMinutes/60} "+ft(l,"ч","h","soat","соат")+" ${leg.durationMinutes%60} "+ft(l,"мин","m","daq","дақ"),fontSize=11.sp,color=secondary,textAlign=TextAlign.Center)
                    CupertinoIcon(CupertinoSymbol.Airplane,null,Modifier.size(22.dp),ink)
                    Text(ft(l,"Прямой","Direct","To‘g‘ri","Тўғри"),fontSize=11.sp,color=secondary,maxLines=1)
                }
                PublishedEndpoint(l,leg.destination,leg.arrivalAt,Modifier.weight(1f),Alignment.End)
            }
            HorizontalDivider(color=ink.copy(alpha=.08f))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(flight.isPair)ft(l,"Туда и обратно · 1 чел.","Round trip · per person","Borish va qaytish · 1 kishi","Бориш ва қайтиш · 1 киши") else ft(l,"За 1 человека","Per person","1 kishi uchun","1 киши учун"),Modifier.weight(1f),fontSize=12.sp,color=secondary)
                Text("$"+java.text.NumberFormat.getNumberInstance(l.locale).apply{maximumFractionDigits=0}.format(flight.priceUsd),fontWeight=FontWeight.Bold,fontSize=25.sp,maxLines=1)
            }
        }
        IconButton(onClick=onSelect,modifier=Modifier.align(Alignment.TopEnd).padding(9.dp).size(44.dp)) { CupertinoIcon(if(selected)CupertinoSymbol.CheckCircleFill else CupertinoSymbol.CircleOutline,ft(l,"Выбрать рейс","Select flight","Reysni tanlash","Рейсни танлаш"),Modifier.size(24.dp),if(selected)green else secondary) }
    }
}
@Composable private fun PublishedEndpoint(l:AppLanguage,code:String,raw:String,modifier:Modifier,alignment:Alignment.Horizontal) {
    val time=FlightDataParity.localTime(raw,code)
    Column(modifier,horizontalAlignment=alignment,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(time,fontSize=28.sp,fontWeight=FontWeight.Bold,maxLines=1)
        Text(code.uppercase(),fontSize=19.sp,fontWeight=FontWeight.Bold,maxLines=1)
        Text(FlightDataParity.localDate(raw,code,l.locale),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
    }
}
