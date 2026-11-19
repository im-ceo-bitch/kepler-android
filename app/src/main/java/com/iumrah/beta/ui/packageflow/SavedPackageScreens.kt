package com.iumrah.beta.ui.packageflow

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.packageflow.*
import com.iumrah.beta.domain.journey.JourneyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable private fun savedStore(): SavedPackageStore = (LocalContext.current.applicationContext as IumrahApplication).container.savedPackages
private fun saveText(l:AppLanguage,r:String,e:String,u:String,c:String)=reviewText(l,r,e,u,c)

/** Same save/share/support contract for all summary entry points. */
@Composable internal fun SavedPackageActions(language:AppLanguage,journey:JourneyStore,chrome:AppChromeStore,enabled:Boolean) {
    val store=savedStore(); val entries by store.entries.collectAsState(); val state by journey.state.collectAsState(); val context=LocalContext.current
    val entry=entries.firstOrNull { state.savedPackageID != null && it.receipt?.packageID==state.savedPackageID }
    val quote=state.quote; val scope=rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var threadID by remember { mutableStateOf<String?>(null) }; var requestRooms by remember { mutableStateOf<Int?>(null) }
    val canSave=enabled && !saving && state.priceChangeAccepted && state.hasFinalGeneratorQuote && quote?.hasVerifiedBreakdown==true && quote.bookingAllowed!=false && quote.allowsOccupancy(state.trip.adults,state.trip.rooms) && entry?.pendingBookingID==null && entry?.receipt?.status!="converted"
    val saved=entry != null && entry.savedQuoteID == quote?.quoteId && entry.draft == SavedJourneyDraft.from(state) && entry.receipt?.status!="expired"
    fun save(then:(SavedPackageEntry)->Unit={}) {
        if (!canSave || journey.state.value!=state) return
        saving=true;error=null
        scope.launch {
            try {
                val result=store.save(state,language)
                val id=requireNotNull(result.receipt).packageID
                check(journey.attachSavedPackage(state,id)) { "PACKAGE_SELECTION_CHANGED" }
                then(result)
            } catch(c:CancellationException){throw c} catch(e:Exception){error=e.message ?: "PACKAGE_SAVE_FAILED"} finally{saving=false}
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            entry?.receipt?.let { Text("Package ID · ${it.packageID} · v${it.revision}",style=MaterialTheme.typography.labelMedium) }
            OutlinedButton(onClick={save()},enabled=canSave,modifier=Modifier.fillMaxWidth()) { Text(if(saved) saveText(language,"Пакет сохранён","Package saved","Paket saqlandi","Пакет сақланди") else saveText(language,"Сохранить пакет","Save package","Paketni saqlash","Пакетни сақлаш")) }
            OutlinedButton(onClick={save { shareSaved(context,it,language) }},enabled=canSave,modifier=Modifier.fillMaxWidth()) { Text(saveText(language,"Сохранить и поделиться","Save and share","Saqlash va ulashish","Сақлаш ва улашиш")) }
            OutlinedButton(onClick={requestRooms=null;save { threadID=it.receipt?.packageID }},enabled=canSave,modifier=Modifier.fillMaxWidth()) { Text(saveText(language,"Задать вопрос по пакету","Ask about package","Paket haqida savol","Пакет ҳақида савол")) }
            if(state.nuitee!=null && state.trip.rooms>1 && state.trip.adults>(state.trip.rooms-1)*5) {
                Text(saveText(language,"Меньше номеров — только после согласования с отелем.","Fewer rooms require hotel approval.","Kamroq xona uchun kelishuv kerak.","Камроқ хона учун келишув керак."))
                OutlinedButton(onClick={requestRooms=state.trip.rooms-1;save { threadID=it.receipt?.packageID }},enabled=canSave,modifier=Modifier.fillMaxWidth()) { Text(saveText(language,"Согласовать размещение","Request room approval","Joylashuvni kelishish","Жойлашувни келишиш")) }
            }
            if(saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    }
    threadID?.let { id ->
        PackageThreadDialog(language,store,id,requestRooms,{threadID=null}) { approval ->
            try {
                val owner=requireNotNull(store.entry(id)).owner
                journey.applyApprovedRooms(id,owner,approval.adults,approval.rooms)
                threadID=null; chrome.openNuiteeJourneyHotels()
            } catch(e:Exception) { error=e.message ?: "ROOM_APPROVAL_SCOPE_CHANGED";threadID=null }
        }
    }
}

@Composable private fun PackageThreadDialog(language:AppLanguage,store:SavedPackageStore,id:String,rooms:Int?,onDismiss:()->Unit,onApprove:((PackageThread.RoomRequest)->Unit)?=null) {
    var thread by remember(id) { mutableStateOf<PackageThread?>(null) }
    var draft by remember(id,rooms) { mutableStateOf(if(rooms==null) "" else saveText(language,"Здравствуйте! Можно ли разместить нас в $rooms номере(ах)?","Hello! Can our group stay in $rooms room(s)?","$rooms xonada joylashish mumkinmi?","$rooms хонада жойлашиш мумкинми?")) }
    var requestID by remember(id) { mutableStateOf(UUID.randomUUID().toString()) }
    var error by remember(id) { mutableStateOf<String?>(null) };var busy by remember(id) { mutableStateOf(false) };val scope=rememberCoroutineScope()
    suspend fun load(){try{thread=store.thread(id);error=null}catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}}
    LaunchedEffect(id){load()}
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("iumrah Care · $id")},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            thread?.messages?.forEach { Text("${it.sender} · ${it.createdAt.take(16)}",style=MaterialTheme.typography.labelSmall);Text(it.body) }
            thread?.roomRequest?.let { r ->
                Text("${r.state} · ${r.adults} / ${r.rooms}\n${r.note}")
                if(r.state=="approved" && onApprove!=null) OutlinedButton(onClick={onApprove(r)},enabled=!busy) { Text(saveText(language,"Выбрать согласованное размещение","Select approved occupancy","Kelishilgan joylashuvni tanlash","Келишилган жойлашувни танлаш")) }
            }
            OutlinedTextField(draft,{draft=it},label={Text(saveText(language,"Сообщение","Message","Xabar","Хабар"))},enabled=!busy,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={scope.launch {busy=true;try{load()}finally{busy=false}}},enabled=!busy){Text(saveText(language,"Обновить","Refresh","Yangilash","Янгилаш"))}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    },confirmButton={TextButton(enabled=!busy && thread!=null && draft.isNotBlank(),onClick={
        val current=thread ?: return@TextButton;busy=true;scope.launch {
            try{thread=store.send(id,current.revision,draft,requestID,rooms.takeIf { current.roomRequest==null });draft="";requestID=UUID.randomUUID().toString();error=null}
            catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}finally{busy=false}
        }
    }){Text(saveText(language,"Отправить","Send","Yuborish","Юбориш"))}},dismissButton={TextButton(onClick=onDismiss,enabled=!busy){Text(saveText(language,"Закрыть","Close","Yopish","Ёпиш"))}})
}

@Composable fun SavedJourneysScreen(language:AppLanguage,journey:JourneyStore,bookings:BookingStore,chrome:AppChromeStore) {
    val store=savedStore(); val entries by store.entries.collectAsState();val scope=rememberCoroutineScope();val context=LocalContext.current
    var threadID by remember { mutableStateOf<String?>(null) };var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    suspend fun refresh(){
        busy=true;error=null
        try{ for(e in store.entries.value){val id=e.receipt?.packageID ?: continue
            try{e.pendingBookingID?.let { bookingID -> bookings.booking(bookingID)?.let { store.link(id,it) } };store.read(id)}
            catch(c:CancellationException){throw c}catch(e:Exception){error=e.message}
        }}finally{busy=false}
    }
    LaunchedEffect(Unit){refresh()}
    val visible=entries.filter { it.receipt!=null && it.receipt.status!="converted" }.sortedByDescending { it.receipt?.expiresAt }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row {TextButton(onClick=chrome::back){Text("‹")}; Text(saveText(language,"Сохранённые путешествия","Saved journeys","Saqlangan safarlar","Сақланган сафарлар"),style=MaterialTheme.typography.titleLarge)} }
        item {TextButton(onClick={scope.launch{refresh()}},enabled=!busy){Text(saveText(language,"Обновить","Refresh","Yangilash","Янгилаш"))};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(visible.isEmpty())item{Text(saveText(language,"Нет сохранённых путешествий","No saved journeys","Saqlangan safarlar yo‘q","Сақланган сафарлар йўқ"))}
        items(visible,key={requireNotNull(it.receipt).packageID}){e->
            val receipt=requireNotNull(e.receipt)
            Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Package ID · ${receipt.packageID} · v${receipt.revision}")
                receipt.snapshot?.let {p->Text(p.hotelNames.makkah,style=MaterialTheme.typography.titleMedium);Text("${p.input.startDate} — ${p.input.endDate}");Text("${p.totalUsd} USD",style=MaterialTheme.typography.headlineSmall)}
                if(receipt.status=="expired")Text(saveText(language,"Требуется новый расчёт","New quote required","Yangi hisob kerak","Янги ҳисоб керак"))
                if(e.pendingBookingID!=null) Text(saveText(language,"Бронирование создано. Синхронизация пакета…","Booking created. Synchronizing package…","Bron yaratildi. Sinxronlanmoqda…","Брон яратилди. Синхронланмоқда…"))
                if(e.draft!=null && e.pendingBookingID==null) Button(onClick={try{store.restore(receipt.packageID,journey);chrome.openFinalPackage()}catch(ex:Exception){error=ex.message}},enabled=!busy){Text(saveText(language,"Открыть и проверить цену","Open and reprice","Ochish va narxni tekshirish","Очиш ва нархни текшириш"))}
                if(receipt.shareURL!=null) OutlinedButton(onClick={try{shareSaved(context,e,language)}catch(ex:Exception){error=ex.message}}){Text(saveText(language,"Поделиться","Share","Ulashish","Улашиш"))}
                OutlinedButton(onClick={threadID=receipt.packageID}){Text(saveText(language,"Задать вопрос","Ask a question","Savol berish","Савол бериш"))}
            }}
        }
        error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
    }
    threadID?.let {PackageThreadDialog(language,store,it,null,{threadID=null})}
}

/** Local artwork uses only the public snapshot, never owner credentials or supplier fares. */
private fun shareSaved(context:Context,entry:SavedPackageEntry,language:AppLanguage) {
    val receipt=requireNotNull(entry.receipt);val url=requireNotNull(receipt.shareURL) { "PUBLIC_SHARE_UNAVAILABLE" }
    require(!url.contains(entry.owner,ignoreCase=true))
    val p=receipt.snapshot;val trip=entry.draft?.trip
    val date=p?.let { "${it.input.startDate} — ${it.input.endDate}" } ?: trip?.let { "${it.departureDate} — ${it.returnDate}" }.orEmpty()
    val people=p?.input?.travelers?.let { it.adults+it.children+it.infants } ?: trip?.travelerCount
    val price=p?.totalUsd ?: p?.tripSummary?.totalUsd?.toDouble()
    val title=saveText(language,"Индивидуальная умра","Individual Umrah","Individual umra","Индивидуал умра")
    val bitmap=Bitmap.createBitmap(1080,1080,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(android.graphics.Color.BLACK)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.WHITE;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
    fun line(s:String,y:Float,size:Float){paint.textSize=size;while(paint.measureText(s)>940 && paint.textSize>18)paint.textSize-=2;canvas.drawText(s,70f,y,paint)}
    line("iumrah",150f,86f);line(title,340f,65f);line(date,470f,42f);people?.let{line("$it · "+saveText(language,"паломников","pilgrims","ziyoratchi","зиёратчи"),550f,38f)};price?.let{line(String.format(java.util.Locale.US,"$%,.2f",it),780f,90f)};line("iumrah.app",990f,40f)
    val file=File(File(context.cacheDir,"iumrah-share").apply{mkdirs()},"saved-${receipt.packageID}-${receipt.revision}.png")
    file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    val intent=Intent(Intent.ACTION_SEND).apply{type="image/png";putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_TEXT,"$title\n$date\n$url");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
    context.startActivity(Intent.createChooser(intent,"iumrah"))
}
