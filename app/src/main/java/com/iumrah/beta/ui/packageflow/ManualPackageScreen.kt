package com.iumrah.beta.ui.packageflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iumrah.beta.IumrahApplication
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.*
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.journey.*
import com.iumrah.beta.domain.nuitee.*
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.models.flight.*
import com.iumrah.beta.models.nuitee.*
import com.iumrah.beta.ui.flights.GuidedFlightChoice
import com.iumrah.beta.ui.hotels.*
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The catalogue component basket is independent of the active guided journey. */
@Composable internal fun ManualPackageScreen(language:AppLanguage, journey:JourneyStore, chrome:AppChromeStore, curated:CuratedFlightRecommendationService,
        discovery:AviasalesFlightDiscoveryService, engine:RemotePackageEngineClient) {
    val context=LocalContext.current;val manual=(context.applicationContext as IumrahApplication).container.manualJourney
    val state by manual.state.collectAsState();val plan=state.trip?:state.plan
    var flight by remember { mutableStateOf<Boolean?>(null) };var city by remember { mutableStateOf<String?>(null) }
    var estimate by remember(state){mutableStateOf<NuiteeManualEstimate?>(null)};var priced by remember(state){mutableStateOf<JourneyStore?>(null)}
    val verifiedQuote = priced?.let { store -> store.state.collectAsState().value.quote }
    var error by remember(state){mutableStateOf<String?>(null)};var busy by remember(state){mutableStateOf(false)};var retry by remember{mutableIntStateOf(0)}
    fun t(r:String,e:String,u:String,c:String)=reviewText(language,r,e,u,c)
    fun change(f:(ManualJourneyState)->ManualJourneyState)=manual.update(state,f)
    LaunchedEffect(state,retry){
        if(!state.hasPriceComponent)return@LaunchedEffect
        busy=true;error=null;priced=null;estimate=null
        try{
            delay(350)
            val draft=state.makeQuoteJourney()
            if(draft!=null){val q=draft.prepareNuiteePackage(curated,engine);if(q.exceptionOrNull() is CancellationException)throw requireNotNull(q.exceptionOrNull());if(q.getOrNull()?.hasVerifiedBreakdown==true && manual.state.value==state){priced=draft;return@LaunchedEffect}}
            state.estimateRequest()?.let{val result=engine.manualEstimate(it);if(manual.state.value==state)estimate=result}
        }catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}finally{busy=false}
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {TextButton(onClick=chrome::back){Text("‹")};Text(t("Мой пакет","My package","Mening paketim","Менинг пакетим"),style=MaterialTheme.typography.headlineMedium);Text("${state.componentCount}/${state.cities.size+3}")}
        item {Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("${plan.originCode} → ${plan.outboundDestinationCode} · ${plan.returnOriginCode} → ${plan.originCode}")
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {JourneyScope.entries.forEach{s->FilterChip(selected=plan.scope==s,onClick={change{old->old.copy(plan=old.plan.copy(scope=s,arrivalAirport=if(s==JourneyScope.MAKKAH_ONLY)SaudiArrivalAirport.JEDDAH else old.plan.arrivalAirport,selectedReturnAirport=null),rooms=old.rooms.filterKeys{it!="madinah"||s==JourneyScope.MAKKAH_AND_MADINAH},outbound=old.outbound?.takeIf{s!=JourneyScope.MAKKAH_ONLY||it.destination=="JED"},inbound=old.inbound?.takeIf{s!=JourneyScope.MAKKAH_ONLY||it.origin=="JED"},train=old.train&&s==JourneyScope.MAKKAH_AND_MADINAH)}},label={Text(if(s==JourneyScope.MAKKAH_ONLY) "Makkah" else "Makkah + Madinah")})}}
            var origin by remember(plan.originCode){mutableStateOf(plan.originCode)}
            OutlinedTextField(origin,{origin=it.uppercase().filter(Char::isLetter).take(3)},label={Text(t("Аэропорт вылета","Departure airport","Jo‘nash aeroporti","Жўнаш аэропорти"))},enabled=state.outbound==null&&state.inbound==null,singleLine=true)
            TextButton(enabled=origin.length==3&&origin!=plan.originCode&&state.outbound==null&&state.inbound==null,onClick={change{it.copy(plan=it.plan.copy(origin=origin,originAirport=null))}}){Text(t("Применить","Apply","Qo‘llash","Қўллаш"))}
            if(plan.scope==JourneyScope.MAKKAH_AND_MADINAH && state.outbound==null){Row {SaudiArrivalAirport.entries.forEach{a->FilterChip(selected=plan.arrivalAirport==a,onClick={change{it.copy(plan=it.plan.copy(arrivalAirport=a,selectedReturnAirport=null))}},label={Text(a.iata)})}}}
            Text("${plan.departureDate} — ${plan.returnDate}")
            Row {TextButton(enabled=state.outbound==null&&plan.departureDate>LocalDate.now(),onClick={change{it.copy(plan=it.plan.copy(departureDate=it.plan.departureDate.minusDays(1)))}}){Text("−")};Text(t("Начало","Start","Boshlanish","Бошланиш"));TextButton(enabled=state.outbound==null&&plan.departureDate.plusDays(state.cities.size.toLong())<plan.returnDate,onClick={change{it.copy(plan=it.plan.copy(departureDate=it.plan.departureDate.plusDays(1)))}}){Text("+")}}
            Row {TextButton(enabled=state.inbound==null&&plan.returnDate.minusDays(state.cities.size.toLong())>plan.departureDate,onClick={change{it.copy(plan=it.plan.copy(returnDate=it.plan.returnDate.minusDays(1)))}}){Text("−")};Text(t("Окончание","End","Tugash","Тугаш"));TextButton(enabled=state.inbound==null,onClick={change{it.copy(plan=it.plan.copy(returnDate=it.plan.returnDate.plusDays(1)))}}){Text("+")}}
            Row {Text(t("Взрослые","Adults","Kattalar","Катталар"),Modifier.weight(1f));TextButton(enabled=plan.adults>1,onClick={change{it.copy(plan=it.plan.copy(adults=it.plan.adults-1),tickets=it.tickets.coerceAtMost(it.plan.adults-1))}}){Text("−")};Text("${plan.adults}");TextButton(enabled=plan.adults<6,onClick={change{it.copy(plan=it.plan.copy(adults=it.plan.adults+1))}}){Text("+")}}
            var cabin by remember {mutableStateOf(false)};Box{OutlinedButton(onClick={cabin=true}){Text(plan.effectiveFlightFilters.cabinClass.wireValue)};DropdownMenu(cabin,{cabin=false}){FlightCabinClass.entries.forEach{cl->DropdownMenuItem(text={Text(cl.wireValue)},onClick={change{it.copy(plan=it.plan.copy(flightFilters=it.plan.effectiveFlightFilters.copy(cabinClass=cl)))};cabin=false})}}}
        }}}
        items(listOf(false,true)){returning->val f=if(returning)state.inbound else state.outbound
            ManualComponent(if(returning)t("Рейс обратно","Return flight","Qaytish reysi","Қайтиш рейси") else t("Рейс туда","Outbound flight","Borish reysi","Бориш рейси"),f?.let{"${it.origin} → ${it.destination} · ${it.flightNumber}\n${it.departureAt}"},language,{flight=returning},{change{if(returning)it.copy(inbound=null)else it.copy(outbound=null,plan=it.plan.copy(saudiArrivalDate=null))}})
        }
        if(state.outbound!=null&&state.inbound!=null&&(state.trip==null||!state.sourceCompatible))item{Text(t("Проверьте совместимость рейсов и даты","Check flight compatibility and dates","Reys va sanalarni tekshiring","Рейс ва саналарни текширинг"),color=MaterialTheme.colorScheme.error)}
        items(state.cities){c->val r=state.rooms[c];ManualComponent(if(c=="makkah")"Makkah" else "Madinah",r?.let{"${it.hotel.name} · ${it.rate.roomName}\n${it.checkin} — ${it.checkout}"+(if(!state.validRoom(c))" · "+t("Проверьте номер","Check room","Xonani tekshiring","Хонани текширинг") else "")},language,{city=c},{change{it.copy(rooms=it.rooms-c)}})}
        item {Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(t("Трансфер","Transfer","Transfer","Трансфер"),style=MaterialTheme.typography.titleMedium)
            TransferVehicleKind.entries.forEach{v->FilterChip(state.vehicle==v,{change{it.copy(vehicle=v)}},label={Text("${v.modelName} · ${v.passengerCapacity}")},enabled=v.passengerCapacity>=plan.adults)}
            if(state.vehicle!=null)TextButton(onClick={change{it.copy(vehicle=null,train=false)}}){Text(t("Убрать","Remove","Olib tashlash","Олиб ташлаш"))}
            if(state.cities.size==2&&state.vehicle!=null){Row{Checkbox(state.train,{v->change{it.copy(train=v)}});Text("Haramain")};if(state.train){Row{HaramainFareClass.entries.forEach{f->FilterChip(state.fare==f,{change{it.copy(fare=f)}},label={Text(f.wireValue)})}};Row{TextButton(enabled=state.tickets>1,onClick={change{it.copy(tickets=it.tickets-1)}}){Text("−")};Text("${state.tickets}");TextButton(enabled=state.tickets<plan.adults,onClick={change{it.copy(tickets=it.tickets+1)}}){Text("+")}}}}
        }}}
        item {Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(t("Стоимость пакета","Package total","Paket narxi","Пакет нархи"),style=MaterialTheme.typography.titleMedium)
            val q=verifiedQuote;val e=estimate
            Text(when{q!=null->"${q.totalPackagePrice} USD";e!=null->"${e.totalPackagePrice} USD";!state.hasPriceComponent->"$0.00";else->"—"},style=MaterialTheme.typography.headlineMedium)
            if(e!=null){Text(t("Предварительный серверный расчёт выбранных компонентов. Не является подтверждением брони.","Server estimate of selected components; not a booking confirmation.","Tanlangan qismlarning taxminiy narxi.","Танланган қисмларнинг тахминий нархи."));if(e.pairedFareIncluded)Text(t("Включён связанный тариф туда и обратно","Paired round-trip fare included","Juft tarif kiritilgan","Жуфт тариф киритилган"));if(e.provisionalTier)Text("Standard")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)};if(!busy&&state.hasPriceComponent&&q==null)TextButton(onClick={retry++}){Text(t("Повторить расчёт","Retry quote","Qayta hisoblash","Қайта ҳисоблаш"))}
            Button(enabled=!busy&&state.complete&&q?.bookingAllowed==true&&q.hasVerifiedBreakdown,onClick={val p=priced;if(p!=null&&manual.state.value==state){journey.adoptSmartResult(p);chrome.openFinalPackage()}},modifier=Modifier.fillMaxWidth()){Text(t("Перейти к бронированию","Continue to booking","Bron qilishga o‘tish","Брон қилишга ўтиш"))}
        }}}
    }
    flight?.let{r->ManualFlightPicker(language,plan,r,curated,discovery,{flight=null}){f->try{change{it.chooseFlight(f,r)};flight=null}catch(e:Exception){error=e.message}}}
    city?.let{c->ManualRoomPicker(language,plan,c,{city=null}){r->change{it.copy(rooms=it.rooms+(c to r))};city=null}}
}

@Composable private fun ManualComponent(title:String,value:String?,language:AppLanguage,onChoose:()->Unit,onRemove:()->Unit){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(value?:reviewText(language,"Не выбрано","Not selected","Tanlanmagan","Танланмаган"));Row{TextButton(onClick=onChoose){Text(reviewText(language,"Выбрать / изменить","Choose / change","Tanlash / o‘zgartirish","Танлаш / ўзгартириш"))};if(value!=null)TextButton(onClick=onRemove){Text(reviewText(language,"Убрать","Remove","Olib tashlash","Олиб ташлаш"))}}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ManualFlightPicker(language:AppLanguage,plan:TripDraft,returning:Boolean,curated:CuratedFlightRecommendationService,discovery:AviasalesFlightDiscoveryService,onDismiss:()->Unit,onSelect:(GuidedFlightChoice)->Unit){
    var rows by remember{mutableStateOf<List<GuidedFlightChoice>>(emptyList())};var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(true)};var retry by remember{mutableIntStateOf(0)}
    val origin=if(returning)plan.returnOriginCode else plan.originCode;val destination=if(returning)plan.originCode else plan.outboundDestinationCode;val day=if(returning)plan.returnDate else plan.departureDate
    LaunchedEffect(plan,returning,retry){busy=true;error=null;val choices=mutableListOf<GuidedFlightChoice>();try{
        try{curated.load(plan).filter{it.nonstop&&it.cabinClass.replace(' ','_').equals(plan.effectiveFlightFilters.cabinClass.wireValue,true)}.forEach{p->if(!returning)choices+=GuidedFlightChoice.Published(p,false) else if(p.inbound!=null||p.effectiveJourneyRole=="return")choices+=GuidedFlightChoice.Published(p,true)}}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}
        if(plan.effectiveFlightFilters.cabinClass==FlightCabinClass.ECONOMY){try{for(date in listOf(day.toString().take(7),day.toString()))discovery.offers(origin,destination,date).offers.filter{!it.isRoundTrip&&it.price.isFinite()&&it.price>0&&it.durationMinutes>0}.forEach{choices+=GuidedFlightChoice.Data(it)}}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}}
        rows=choices.filter{it.origin==origin&&it.destination==destination&&runCatching{val d=LocalDate.parse(it.departureAt.take(10));d>=LocalDate.now()&&kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(d,day))<=3}.getOrDefault(false)}.distinctBy{it.id}.sortedBy{it.departureAt}
    }finally{busy=false}}
    ModalBottomSheet(onDismissRequest=onDismiss){LazyColumn(Modifier.fillMaxWidth().heightIn(max=600.dp),contentPadding=PaddingValues(16.dp)){
        item{Text("$origin → $destination · $day");if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(error!=null)Text(error.orEmpty());if(!busy)TextButton(onClick={retry++}){Text(reviewText(language,"Повторить","Retry","Qayta urinish","Қайта уриниш"))}}
        items(rows,key={it.id}){f->TextButton(onClick={onSelect(f)},modifier=Modifier.fillMaxWidth()){Column{Text("${f.airlineName} · ${f.flightNumber}");Text("${f.departureAt} · ${f.source.name}")}};HorizontalDivider()}
    }}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ManualRoomPicker(language:AppLanguage,plan:TripDraft,city:String,onDismiss:()->Unit,onSelect:(NuiteeRoomSelection)->Unit){
    val service=rememberNuiteeHotelService();val window=TripStayPlanner.windows(plan).let{if(city=="makkah")it.makkah else it.madinah}?:return
    var query by remember{mutableStateOf("")};val page=rememberNuiteePagedCatalog(service,city,window.checkIn,window.checkOut,plan.adults,query=query);var selected by remember{mutableStateOf<NuiteeHotel?>(null)}
    ModalBottomSheet(onDismissRequest=onDismiss){LazyColumn(Modifier.fillMaxWidth().heightIn(max=600.dp),contentPadding=PaddingValues(16.dp)){
        item{OutlinedTextField(query,{query=it},label={Text(reviewText(language,"Отель","Hotel","Mehmonxona","Меҳмонхона"))});Text("${window.checkIn} — ${window.checkOut}");if(page.loading)LinearProgressIndicator(Modifier.fillMaxWidth());page.error?.let{Text(it)};if(page.error!=null)TextButton(onClick=page::retry){Text(reviewText(language,"Повторить","Retry","Qayta urinish","Қайта уриниш"))}}
        items(page.hotels,key={it.id}){h->TextButton(onClick={selected=h},enabled=!page.sandbox,modifier=Modifier.fillMaxWidth()){Text("${h.name} · ${h.roundedStars}★")};HorizontalDivider()}
    }}
    selected?.let{h->NuiteeHotelDetailSheet(language,service,h,window.checkIn,window.checkOut,plan.adults,onDismiss={selected=null},onRate={rate,meals->onSelect(NuiteeRoomSelection("$city:0",city,0,h,rate,window.checkIn,window.checkOut,plan.adults,emptyList(),meals));selected=null},allowMealSelection=true)}
}
