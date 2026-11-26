@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.iumrah.beta.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.iumrah.beta.data.flight.FlightDataParity
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.data.flight.AirportMapBootstrapCatalog
import com.iumrah.beta.models.flight.FlightCabinClass
import java.time.Instant
import java.time.LocalDateTime
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.models.hotel.StorefrontFlightOption
import com.iumrah.beta.ui.components.IumrahBackButton
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.flights.IumrahPremiumFlightDetail
import com.iumrah.beta.ui.flights.PremiumFlightLeg
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.WeakHashMap
import java.util.Locale

internal fun homeText(l: AppLanguage, r: String, e: String, u: String, c: String) = when(l) {
    AppLanguage.RUSSIAN -> r; AppLanguage.UZBEK -> u; AppLanguage.UZBEK_CYRILLIC -> c; else -> e
}

/** Mirrors IumrahAllPublishedFlightStore.flatten, not the route-scoped search. */
internal data class GlobalPublishedLeg(val rowID: String, val leg: CuratedFlightRecommendation.Leg, val pair: Boolean, val price: Double?) {
    val id: String get() = "published:$rowID:${leg.origin}:${leg.departureAt}"
    fun selectable(): PublishedHomeLeg? = price?.takeIf { it.isFinite() && it > 0 }?.let { PublishedHomeLeg(rowID,leg,pair,it) }
}
internal fun flattenGlobalPublished(options: List<StorefrontFlightOption>): List<GlobalPublishedLeg> = options.flatMap { option ->
    val price = option.perTravelerFare.takeIf { option.currency.equals("USD", true) && it.isFinite() && it > 0 }
    listOfNotNull(option.outbound,option.inbound).filter { it.stops == 0 }.map { leg ->
        GlobalPublishedLeg(option.id, CuratedFlightRecommendation.Leg(airline=leg.airline,flightNumber=leg.flightNumber,airlineCode=leg.airlineCode,
            origin=leg.origin.uppercase(),destination=leg.destination.uppercase(),departureAt=leg.departureAt,arrivalAt=leg.arrivalAt,durationMinutes=leg.durationMinutes,stops=leg.stops,cabinClass=leg.cabinClass),option.inbound!=null,price)
    }
}.sortedWith(compareBy<GlobalPublishedLeg>{it.leg.departureAt}.thenBy{it.id}).distinctBy{it.id}

internal class HomeGlobalCatalogue(private val service: HotelCatalogService) {
    var flights by mutableStateOf<List<GlobalPublishedLeg>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var complete by mutableStateOf(false); private set
    private var fetched: Long? = null
    private var attempt = 0L
    private val lock = Mutex()
    suspend fun load(force: Boolean = false) {
        val requestedAttempt = attempt
        lock.withLock {
            if (requestedAttempt != attempt) return@withLock // request completed while waiting
            val now = android.os.SystemClock.elapsedRealtime()
            if (!force && fetched?.let { now-it < 300_000 } == true) return@withLock
            loading = true
            try {
                val board = service.storefrontFlightBoard("TAS", allPublished=true)
                check(board.ok)
                flights = flattenGlobalPublished(board.options)
                loaded = true; complete = board.catalogComplete == true; failed = false
                fetched = android.os.SystemClock.elapsedRealtime(); attempt++
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true; attempt++ }
            finally { loading = false }
        }
    }
}
private var catalogueService: HotelCatalogService? = null
private var catalogueState: HomeGlobalCatalogue? = null
internal fun homeGlobalCatalogue(service: HotelCatalogService): HomeGlobalCatalogue {
    if (catalogueService !== service || catalogueState == null) { catalogueService = service; catalogueState = HomeGlobalCatalogue(service) }
    return requireNotNull(catalogueState)
}

/** Selection belongs to the trip, survives Home → all flights → detail navigation.
 * Merely opening a card never changes the route, dates, or cabin. */
internal class HomePublishedSelection {
    var outbound by mutableStateOf<GlobalPublishedLeg?>(null); private set
    var inbound by mutableStateOf<GlobalPublishedLeg?>(null); private set
    val complete get() = outbound != null && inbound != null
    fun clear() { outbound = null; inbound = null }
    private fun cabin(f: GlobalPublishedLeg, trip: TripDraft) = f.leg.cabinClass.lowercase().replace(' ','_') == trip.effectiveFlightFilters.cabinClass.wireValue
    private fun outgoing(f: GlobalPublishedLeg, trip: TripDraft) = f.leg.origin == trip.originCode && f.leg.destination == trip.outboundDestinationCode
    private fun returning(f: GlobalPublishedLeg, trip: TripDraft) = f.leg.destination == trip.originCode && f.leg.origin == trip.returnOriginCode
    private fun day(f: GlobalPublishedLeg) = PublishedHomeRules.day(f.leg.departureAt,f.leg.origin)
    private fun compatible(a: GlobalPublishedLeg,b: GlobalPublishedLeg,trip: TripDraft): Boolean {
        val aa=a.selectable() ?: return false; val bb=b.selectable() ?: return false
        return PublishedHomeRules.compatible(aa,bb,trip.scope)
    }
    fun reconcile(trip: TripDraft) {
        outbound?.let { if (!outgoing(it,trip)||!cabin(it,trip)||day(it)!=trip.departureDate) outbound=null }
        inbound?.let { if (!returning(it,trip)||!cabin(it,trip)||day(it)!=trip.returnDate) inbound=null }
    }
    fun canContinue(trip: TripDraft): Boolean {
        val a=outbound ?: return false; val b=inbound ?: return false
        return trip.canContinue && a.future() && b.future() && outgoing(a,trip) && returning(b,trip) && cabin(a,trip) && cabin(b,trip) && day(a)==trip.departureDate && day(b)==trip.returnDate && compatible(a,b,trip)
    }
    private fun proposed(f: GlobalPublishedLeg,trip: TripDraft): TripDraft? {
        val direction=f.direction() ?: return null
        val date=day(f) ?: return null
        val arrival=PublishedHomeRules.day(f.leg.arrivalAt,f.leg.destination) ?: return null
        val cabin=FlightCabinClass.entries.firstOrNull{it.wireValue==f.leg.cabinClass.lowercase().replace(' ','_')} ?: return null
        if(f.rowID.isBlank()||f.leg.stops!=0||f.selectable()==null||!f.future())return null
        val other=if(direction==0)inbound else outbound
        if(other!=null && (!other.future()||other.leg.cabinClass.lowercase().replace(' ','_')!=cabin.wireValue))return null
        val origin=if(direction==0)f.leg.origin else f.leg.destination
        val saudi=if(direction==0)f.leg.destination else f.leg.origin
        val airport=SaudiArrivalAirport.entries.firstOrNull{it.iata==saudi} ?: return null
        var next=trip.copy(origin=origin,originAirport=if(trip.originCode==origin)trip.originAirport else AirportMapBootstrapCatalog.airport(origin),flightFilters=trip.effectiveFlightFilters.copy(cabinClass=cabin),flightTripType=FlightTripType.ROUND_TRIP,flexibility=DateFlexibility.EXACT,saudiArrivalDate=null,scope=if(airport==SaudiArrivalAirport.MADINAH)JourneyScope.MAKKAH_AND_MADINAH else trip.scope)
        if(direction==0){
            next=next.copy(arrivalAirport=airport,departureDate=date)
            if(other!=null){if(!compatible(f,other,next))return null;next=next.copy(returnDate=day(other)?:return null,selectedReturnAirport=SaudiArrivalAirport.entries.firstOrNull{it.iata==other.leg.origin})}
            else {val minimum=arrival.plusDays(if(next.scope==JourneyScope.MAKKAH_AND_MADINAH)2 else 1);next=next.copy(selectedReturnAirport=null,returnDate=if(next.returnDate<minimum)arrival.plusDays(7)else next.returnDate)}
        }else{
            next=next.copy(selectedReturnAirport=airport,returnDate=date)
            if(other!=null){if(!compatible(other,f,next))return null;next=next.copy(departureDate=day(other)?:return null,arrivalAirport=SaudiArrivalAirport.entries.firstOrNull{it.iata==other.leg.destination}?:SaudiArrivalAirport.JEDDAH)}
            else {if(next.scope==JourneyScope.MAKKAH_ONLY)next=next.copy(arrivalAirport=SaudiArrivalAirport.JEDDAH);if(next.departureDate>=date){val today=LocalDate.now();if(date<=today)return null;next=next.copy(departureDate=maxOf(today,date.minusDays(7)))}}
        }
        return next.takeIf{it.canContinue}
    }
    fun canChoose(f: GlobalPublishedLeg,trip: TripDraft): Boolean = selected(f)||proposed(f,trip)!=null
    fun choose(f: GlobalPublishedLeg,journey: JourneyStore): Boolean {
        if(outbound?.id==f.id){outbound=null;return true};if(inbound?.id==f.id){inbound=null;return true}
        val next=proposed(f,journey.state.value.trip)?:return false
        if(f.direction()==0)outbound=f else inbound=f
        journey.updateTrip(next)
        return true
    }
    fun commit(journey: JourneyStore): Boolean {
        val trip=journey.state.value.trip; reconcile(trip)
        val a=outbound ?: return false; val b=inbound ?: return false
        if(!canContinue(trip))return false
        val dep=day(a) ?: return false; val back=day(b) ?: return false
        val arrival=PublishedHomeRules.day(a.leg.arrivalAt,a.leg.destination) ?: return false
        val airport=SaudiArrivalAirport.entries.firstOrNull{it.iata==b.leg.origin} ?: return false
        val next=trip.copy(departureDate=dep,returnDate=back,saudiArrivalDate=arrival,selectedReturnAirport=airport,flightTripType=FlightTripType.ROUND_TRIP,flexibility=DateFlexibility.EXACT)
        if(!next.canContinue)return false
        val pair=a.pair&&b.pair&&a.rowID==b.rowID
        val state=journey.state.value
        if(state.trip==next && state.packageFlightPath==PackageFlightPath.PUBLISHED_DIRECT && state.selectedPublishedCompleteID==(if(pair)a.rowID else null) && state.selectedPublishedOutboundID==(if(pair)null else a.rowID) && state.selectedPublishedReturnID==(if(pair)null else b.rowID))return true
        journey.commitTripBuilder(next,PackageFlightPath.PUBLISHED_DIRECT,completeID=if(pair)a.rowID else null,outboundID=if(pair)null else a.rowID,returnID=if(pair)null else b.rowID)
        return true
    }
    fun selected(f: GlobalPublishedLeg) = outbound?.id==f.id || inbound?.id==f.id
}
private val selections=WeakHashMap<JourneyStore,HomePublishedSelection>()
internal fun homePublishedSelection(journey: JourneyStore)=selections.getOrPut(journey){HomePublishedSelection()}

@Composable private fun GlobalCatalogueStatus(l:AppLanguage,catalogue:HomeGlobalCatalogue){
    val scope=rememberCoroutineScope()
    if(catalogue.loading&&!catalogue.loaded) Box(Modifier.fillMaxWidth().padding(18.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(24.dp))}
    if(catalogue.failed || (catalogue.loaded&&!catalogue.complete)){
        Text(if(catalogue.failed)homeText(l,"Не удалось обновить рейсы","Could not refresh flights","Reyslar yangilanmadi","Рейслар янгиланмади")else homeText(l,"Полный каталог пока недоступен","Complete catalogue not available yet","To‘liq katalog hozircha mavjud emas","Тўлиқ каталог ҳозирча мавжуд эмас"),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={scope.launch{catalogue.load(force=true)}}){Text(homeText(l,"Обновить рейсы","Refresh flights","Reyslarni yangilash","Рейсларни янгилаш"))}
    }
    if(catalogue.loaded&&catalogue.complete&&!catalogue.failed&&catalogue.flights.isEmpty())Text(homeText(l,"Нет опубликованных рейсов","No published flights","E’lon qilingan reyslar yo‘q","Эълон қилинган рейслар йўқ"),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
internal fun GlobalPublishedLeg.direction(): Int? = when {
    leg.destination in setOf("JED","MED") && leg.origin !in setOf("JED","MED") -> 0
    leg.origin in setOf("JED","MED") && leg.destination !in setOf("JED","MED") -> 1
    else -> null
}
internal fun GlobalPublishedLeg.departureInstant(): Instant? = FlightDataParity.instant(leg.departureAt) ?: runCatching {
    LocalDateTime.parse(leg.departureAt).atZone(FlightReferenceCatalog.timeZone(leg.origin) ?: java.time.ZoneOffset.UTC).toInstant()
}.getOrNull()
internal fun GlobalPublishedLeg.future(now: Instant=Instant.now()) = departureInstant()?.let{it>=now}==true
internal fun prioritizePublished(flights:List<GlobalPublishedLeg>,trip:TripDraft,direction:Int,other:GlobalPublishedLeg?,now:Instant=Instant.now()):List<GlobalPublishedLeg>{
    val origin=if(direction==0)trip.originCode else trip.returnOriginCode
    val destination=if(direction==0)trip.outboundDestinationCode else trip.originCode
    val date=if(direction==0)trip.departureDate else trip.returnDate
    val cabin=trip.effectiveFlightFilters.cabinClass.wireValue
    return flights.filter{it.direction()==direction&&it.future(now)}.map{f->
        val route=f.leg.origin==origin&&f.leg.destination==destination&&f.leg.cabinClass.lowercase().replace(' ','_')==cabin
        val compatible=other==null||run{val a=if(direction==0)f else other;val b=if(direction==0)other else f;PublishedHomeRules.compatible(PublishedHomeLeg(a.rowID,a.leg,a.pair,a.price?:0.0),PublishedHomeLeg(b.rowID,b.leg,b.pair,b.price?:0.0),trip.scope)}
        f to if(!route||!compatible)2 else if(PublishedHomeRules.day(f.leg.departureAt,f.leg.origin)==date)0 else 1
    }.sortedWith(compareBy<Pair<GlobalPublishedLeg,Int>>{it.second}.thenBy{it.first.departureInstant()}.thenBy{it.first.id}).map{it.first}
}
@Composable private fun GlobalFlightCard(l:AppLanguage,f:GlobalPublishedLeg,trip:TripDraft,journey:JourneyStore,selection:HomePublishedSelection,chrome:AppChromeStore,horizontal:Boolean,width:androidx.compose.ui.unit.Dp?=if(horizontal)290.dp else null,onError:()->Unit={}){
    PublishedResultCard(l,PublishedHomeLeg(f.rowID,f.leg,f.pair,f.price?:0.0),selection.selected(f),onOpen={chrome.openHomePublishedFlightDetail(f.id)},onSelect={if(!selection.choose(f,journey))onError()},width=width,hasPrice=f.price!=null)
}
@Composable internal fun HomeGlobalPublishedSection(l:AppLanguage,journey:JourneyStore,catalogue:HomeGlobalCatalogue,selection:HomePublishedSelection,chrome:AppChromeStore,openOfficial:()->Unit,onContinue:()->Unit,continuing:Boolean,continueError:Boolean){
    val state by journey.state.collectAsState()
    LaunchedEffect(catalogue){catalogue.load()}
    var error by remember{mutableStateOf(false)}
    val now=remember(catalogue.flights,state.trip){Instant.now()}
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)){
        for(direction in 0..1){
            key(direction){
                val other=if(direction==0)selection.inbound else selection.outbound
                val flights=remember(catalogue.flights,state.trip,other,now){prioritizePublished(catalogue.flights,state.trip,direction,other,now)}
                val scroll=rememberLazyListState()
                LaunchedEffect(state.trip.originCode,state.trip.outboundDestinationCode,state.trip.returnOriginCode,state.trip.departureDate,state.trip.returnDate,state.trip.effectiveFlightFilters.cabinClass,other?.id,flights.map{it.id}){
                    val selected=if(direction==0)selection.outbound else selection.inbound
                    scroll.scrollToItem(flights.indexOfFirst{it.id==selected?.id}.coerceAtLeast(0))
                }
                Row(verticalAlignment=Alignment.CenterVertically){Text(if(direction==0)homeText(l,"Прямые рейсы туда","Direct outbound flights","Borish uchun to‘g‘ri reyslar","Бориш учун тўғри рейслар")else homeText(l,"Прямые рейсы обратно","Direct return flights","Qaytish uchun to‘g‘ri reyslar","Қайтиш учун тўғри рейслар"),Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold);IconButton(onClick=openOfficial){CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(16.dp))}}
                if(direction==0)GlobalCatalogueStatus(l,catalogue)
                BoxWithConstraints(Modifier.fillMaxWidth()){
                    val width=minOf(330.dp,maxWidth*.86f)
                    LazyRow(state=scroll,flingBehavior=rememberSnapFlingBehavior(lazyListState=scroll),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        items(flights,key={it.id}){f->GlobalFlightCard(l,f,state.trip,journey,selection,chrome,true,width){error=true}}
                        item(key="all-tickets-$direction"){
                            Column(Modifier.width(width).height((224*LocalDensity.current.fontScale.coerceAtLeast(1f)).dp).clip(RoundedCornerShape(22.dp)).background(Color(0xff202020)).clickable(onClick=openOfficial).padding(16.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){CupertinoIcon(CupertinoSymbol.ArrowRight,null,Modifier.size(36.dp),Color.White);Spacer(Modifier.height(12.dp));Text(homeText(l,"Все авиабилеты","All flight tickets","Barcha aviachiptalar","Барча авиачипталар"),fontSize=22.sp,fontWeight=FontWeight.Bold,color=Color.White,textAlign=TextAlign.Center);Text(homeText(l,"Перейти к выбору рейсов","Go to flight selection","Reyslarni tanlashga o‘tish","Рейсларни танлашга ўтиш"),fontSize=13.sp,color=Color.White.copy(alpha=.7f),textAlign=TextAlign.Center)}
                        }
                    }
                }
            }
        }
        if(selection.canContinue(state.trip))IumrahPrimaryButton(homeText(l,"Продолжить к отелям","Continue to hotels","Mehmonxonalarga o‘tish","Меҳмонхоналарга ўтиш"),enabled=!continuing,onClick=onContinue)
        if(continuing) CircularProgressIndicator(Modifier.size(24.dp))
        if(continueError) Text(homeText(l,"Не удалось подтвердить рейсы. Повторите попытку.","Could not confirm flights. Try again.","Reyslar tasdiqlanmadi. Qayta urinib ko‘ring.","Рейслар тасдиқланмади. Қайта уриниб кўринг."),color=MaterialTheme.colorScheme.error,fontSize=13.sp)
    }
    if(error)AlertDialog(onDismissRequest={error=false},title={Text(homeText(l,"Проверьте совместимость рейсов","Check flight compatibility","Reyslar mosligini tekshiring","Рейслар мослигини текширинг"))},text={Text(homeText(l,"Выберите совместимые даты, аэропорты, класс и тариф. Сначала снимите выбор несовместимого рейса.","Choose compatible dates, airports, cabin and fare. Deselect an incompatible leg first.","Mos sana, aeroport, klass va tarifni tanlang.","Мос сана, аэропорт, класс ва тарифни танланг."))},confirmButton={TextButton(onClick={error=false}){Text("OK")}})
}
@Composable internal fun HomeAllPublishedFlightsPage(l:AppLanguage,journey:JourneyStore,service:HotelCatalogService,chrome:AppChromeStore){
    val catalogue=remember(service){homeGlobalCatalogue(service)};val selection=remember(journey){homePublishedSelection(journey)};val state by journey.state.collectAsState();val scope=rememberCoroutineScope()
    LaunchedEffect(catalogue){catalogue.load()};LaunchedEffect(state.trip){selection.reconcile(state.trip)}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()){
        IumrahBackButton(chrome::back,Modifier.padding(horizontal=18.dp))
        PullToRefreshBox(isRefreshing=catalogue.loading,onRefresh={scope.launch{catalogue.load(force=true)}},modifier=Modifier.weight(1f)){
            LazyColumn(contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=40.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                item{Text(homeText(l,"Прямые рейсы","Direct flights","To‘g‘ridan-to‘g‘ri reyslar","Тўғридан-тўғри рейслар"),fontSize=31.sp,fontWeight=FontWeight.Bold)}
                item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){GlobalCatalogueStatus(l,catalogue)}}
                items(catalogue.flights.filter{it.direction()!=null&&it.future()},key={it.id}){f->GlobalFlightCard(l,f,state.trip,journey,selection,chrome,false)}
            }
        }
    }
}
@Composable internal fun HomePublishedFlightDetailPage(l:AppLanguage,journey:JourneyStore,service:HotelCatalogService,chrome:AppChromeStore,id:String){
    val catalogue=remember(service){homeGlobalCatalogue(service)};val selection=remember(journey){homePublishedSelection(journey)};val state by journey.state.collectAsState()
    LaunchedEffect(catalogue){catalogue.load()};LaunchedEffect(state.trip){selection.reconcile(state.trip)}
    val flight=catalogue.flights.firstOrNull{it.id==id}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()){
        IumrahBackButton(chrome::back,Modifier.padding(horizontal=18.dp))
        if(flight==null){Column(Modifier.padding(18.dp)){GlobalCatalogueStatus(l,catalogue);if(catalogue.loaded)Text(homeText(l,"Рейс больше недоступен","Flight no longer available","Reys endi mavjud emas","Рейс энди мавжуд эмас"))}}
        else{val leg=flight.leg;Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            IumrahPremiumFlightDetail(PremiumFlightLeg(leg.origin,leg.destination,leg.departureAt,leg.arrivalAt,leg.airlineCode,leg.flightNumber,leg.airline,leg.stops,leg.durationMinutes,leg.cabinClass),l,pageHeroInset=18.dp)
            Text(flight.price?.let{String.format(Locale.US,"$%,.2f",it)}?:"—",fontSize=28.sp,fontWeight=FontWeight.Bold)
            Text(if(flight.pair)homeText(l,"Туда и обратно · на человека","Round trip · per person","Borish va qaytish · bir kishi uchun","Бориш ва қайтиш · бир киши учун")else homeText(l,"Стоимость на человека","Fare per person","Bir kishi uchun narx","Бир киши учун нарх"))
            if(!selection.canChoose(flight,state.trip))Text(homeText(l,"Для этого рейса настройте подходящий маршрут, даты и класс в конфигураторе.","Configure a matching route, dates and cabin for this flight.","Bu reys uchun mos yo‘nalish, sana va klassni tanlang.","Бу рейс учун мос йўналиш, сана ва классни танланг."),color=MaterialTheme.colorScheme.onSurfaceVariant)
            else IumrahPrimaryButton(if(selection.selected(flight))homeText(l,"Выбрано","Selected","Tanlangan","Танланган")else homeText(l,"Добавить в мой пакет","Add to my package","Paketimga qo‘shish","Пакетимга қўшиш")){if(selection.choose(flight,journey))chrome.back()}
        }}
    }
}
