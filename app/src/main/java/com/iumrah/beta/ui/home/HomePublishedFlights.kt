package com.iumrah.beta.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
        if (list.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(end=3.dp)) {
            items(list,key={it.id}) { flight ->
                val selected=(if(direction==0)outbound else inbound)?.id==flight.id
                val ink=MaterialTheme.colorScheme.onSurface
                val green=Color(0xFF34C759)
                val shape=RoundedCornerShape(24.dp)
                Column(Modifier.width(290.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp,if(selected)green else ink.copy(alpha=.08f),shape).clickable { opened=flight }.padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(flight.leg.airline,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        if(selected) CupertinoIcon(CupertinoSymbol.CheckCircleFill,null,Modifier.size(22.dp),green)
                    }
                    Text("${flight.leg.origin}  →  ${flight.leg.destination}",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Text("${flight.leg.flightNumber} · ${FlightDataParity.localDate(flight.leg.departureAt,flight.leg.origin, Locale.forLanguageTag(language.localeTag))} · ${FlightDataParity.localTime(flight.leg.departureAt,flight.leg.origin)}",
                        fontSize=12.sp,color=ink.copy(alpha=.65f))
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("$${String.format(Locale.US,"%.2f",flight.priceUsd)}",fontWeight=FontWeight.Bold,fontSize=17.sp)
                        Text(ft(language," / чел."," / person"," / kishi"," / киши"),fontSize=12.sp,color=ink.copy(alpha=.6f))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick={
                            val day=PublishedHomeRules.day(flight.leg.departureAt,flight.leg.origin)
                            if(day!=null) {
                                if(direction==0) {
                                    outbound=flight
                                    if(inbound!=null && !PublishedHomeRules.compatible(flight,inbound!!,trip.scope)) inbound=null
                                    journey.updateTrip(trip.copy(departureDate=day,returnDate=if(trip.returnDate.isAfter(day))trip.returnDate else day.plusDays(7)))
                                } else {
                                    if(outbound==null || PublishedHomeRules.compatible(outbound!!,flight,trip.scope)) {
                                        inbound=flight;journey.updateTrip(trip.copy(returnDate=day))
                                    }
                                }
                            }
                        }) { Text(if(selected)ft(language,"Выбрано","Selected","Tanlangan","Танланган") else ft(language,"Выбрать","Select","Tanlash","Танлаш"),color=green,fontWeight=FontWeight.Bold) }
                    }
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
        AlertDialog(onDismissRequest={opened=null},title={Text("${flight.leg.origin} → ${flight.leg.destination}")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${flight.leg.airline} · ${flight.leg.flightNumber}")
                Text(FlightDataParity.localDate(flight.leg.departureAt,flight.leg.origin,Locale.forLanguageTag(language.localeTag)))
                Text(ft(language,"Прямой рейс","Nonstop flight","To‘g‘ridan-to‘g‘ri reys","Тўғридан-тўғри рейс"))
                Text("$${String.format(Locale.US,"%.2f",flight.priceUsd)}")
            }}, confirmButton={TextButton(onClick={opened=null}){Text(ft(language,"Закрыть","Close","Yopish","Ёпиш"))}})
    }
}
