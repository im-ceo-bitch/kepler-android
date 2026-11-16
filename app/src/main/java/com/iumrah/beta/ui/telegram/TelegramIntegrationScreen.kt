package com.iumrah.beta.ui.telegram

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.telegram.TelegramBookingIntegrationService
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.homeflow.InternalNavBar
import com.iumrah.beta.ui.homeflow.tr
import kotlinx.coroutines.launch

private val TelegramBlue=Color(0xFF269ED9)
private val TelegramGreen=Color(0xFF2EAD5C)

@Composable
fun TelegramIntegrationScreen(language:AppLanguage, bookingStore:BookingStore, accountStore:IumrahAccountStore, chrome:AppChromeStore){
    val bookings by bookingStore.state.collectAsState()
    val account by accountStore.state.collectAsState()
    val sessions=bookings.sessions
    var selectedID by remember(sessions){mutableStateOf(sessions.firstOrNull{it.effectiveStatus !in setOf("COMPLETED","CANCELLED")}?.id?:sessions.firstOrNull()?.id)}
    val selected=sessions.firstOrNull{it.id==selectedID}?:sessions.firstOrNull()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        InternalNavBar("Telegram",chrome)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            Image(painterResource(R.drawable.telegram_integration_hero),null,Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Color.White).border(.8.dp,MaterialTheme.colorScheme.onSurface.copy(alpha=.05f),RoundedCornerShape(32.dp)),contentScale=ContentScale.FillWidth)
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)){Text(tr(language,"Ваша бронь теперь и в Telegram","Your booking, now in Telegram","Broningiz endi Telegram’da ham","Бронингиз энди Telegram’да ҳам"),fontSize=31.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.7).sp,lineHeight=34.sp);Text(tr(language,"Привяжите бронь один раз — бот iumrah будет держать актуальный статус под рукой. Бот использует то же состояние бронирования, что и приложение.","Link a booking once and the iumrah bot will keep its live status close at hand. The bot uses the same booking state as the app.","Bronni bir marta ulang — iumrah boti uning joriy holatini doim qo‘l ostida saqlaydi. Bot ilovadagi ayni bron holatidan foydalanadi.","Бронни бир марта уланг — iumrah боти унинг жорий ҳолатини доим қўл остида сақлайди. Бот иловадаги айни брон ҳолатидан фойдаланади."),fontSize=16.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.56f),lineHeight=21.sp)}
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Benefit(CupertinoSymbol.Refresh,tr(language,"Живой статус бронирования","Live booking status","Jonli bron holati","Жонли брон ҳолати"),tr(language,"Проверка наличия, подтверждение, готовность к поездке и этапы путешествия.","Availability, confirmation, travel-ready and trip stages.","Mavjudlik, tasdiq, safarga tayyorlik va safar bosqichlari.","Мавжудлик, тасдиқ, сафарга тайёрлик ва сафар босқичлари."))
                Benefit(CupertinoSymbol.CreditCard,tr(language,"Изменения по оплате","Payment updates","To‘lov yangilanishlari","Тўлов янгиланишлари"),tr(language,"Узнавайте, когда ожидается, получена или подтверждена оплата.","See when payment is expected, received or confirmed.","To‘lov qachon kutilayotgani, qabul qilingani yoki tasdiqlanganini ko‘ring.","Тўлов қачон кутилаётгани, қабул қилингани ёки тасдиқланганини кўринг."))
                Benefit(CupertinoSymbol.Document,tr(language,"Документы и готовность","Documents and readiness","Hujjatlar va tayyorgarlik","Ҳужжатлар ва тайёргарлик"),tr(language,"Получайте уведомление, когда подтверждения и документы к поездке готовы.","Get notified when confirmations and travel documents are ready.","Tasdiqlar va safar hujjatlari tayyor bo‘lganda xabar oling.","Тасдиқлар ва сафар ҳужжатлари тайёр бўлганда хабар олинг."))
            }
            if(sessions.isEmpty()) NoBookingCard(language) else {
                if(sessions.size>1) BookingPicker(language,sessions,selectedID){selectedID=it}
                selected?.let{TelegramConnectCard(language,it,accountStore.bearerToken,bookingStore)}
            }
            Text(tr(language,"Ссылка подключения одноразовая и действует 10 минут. Бот получает доступ только к той брони, которую Вы привязываете.","The connection link is one-time and expires after 10 minutes. The bot receives access only to the booking you link.","Ulash havolasi bir martalik va 10 daqiqa amal qiladi. Bot faqat siz ulagan bronga kirish oladi.","Улаш ҳаволаси бир марталик ва 10 дақиқа амал қилади. Бот фақат сиз улаган бронга кириш олади."),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f),modifier=Modifier.padding(horizontal=4.dp))
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable private fun Benefit(icon:CupertinoSymbol,title:String,body:String){Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(21.dp)).padding(15.dp),verticalAlignment=Alignment.Top){Box(Modifier.size(42.dp).background(TelegramBlue.copy(alpha=.11f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){CupertinoIcon(icon,null,Modifier.size(18.dp),tint=TelegramBlue)};Spacer(Modifier.width(12.dp));Column{Text(title,fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text(body,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.53f),lineHeight=16.sp)}}}
@Composable private fun NoBookingCard(language:AppLanguage){Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(22.dp)).padding(16.dp),verticalAlignment=Alignment.Top){Box(Modifier.size(44.dp).background(Color(0xFF5E5CE6).copy(alpha=.1f),RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.SuitcaseFill,null,Modifier.size(18.dp),tint=Color(0xFF5E5CE6))};Spacer(Modifier.width(12.dp));Column{Text(tr(language,"Сначала создайте бронирование","Create a booking first","Avval bron yarating","Аввал брон яратинг"),fontWeight=FontWeight.Bold);Text(tr(language,"Подключение Telegram появится сразу после создания брони через Hotel First, Flight First или Configurator.","Telegram linking becomes available as soon as Hotel First, Flight First or Configurator creates a booking.","Telegram ulanishi bron yaratilishi bilan paydo bo‘ladi.","Telegram уланиши брон яратилиши билан пайдо бўлади."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.52f))}}}
@Composable private fun BookingPicker(language:AppLanguage,sessions:List<StoredBookingSession>,selected:String?,onSelect:(String)->Unit){var expanded by remember{mutableStateOf(false)};Column{Text(tr(language,"Бронь для подключения","Booking to connect","Ulanadigan bron","Уланадиган брон"),fontSize=12.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f));Spacer(Modifier.height(9.dp));Box{Row(Modifier.fillMaxWidth().height(52.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(18.dp)).clickable{expanded=!expanded}.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){val s=sessions.firstOrNull{it.id==selected}?:sessions.first();Text("${s.displayBookingNumber} · ${s.booking.route.originCode} → ${s.booking.route.outboundDestination}",modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold);CupertinoIcon(CupertinoSymbol.ChevronDown,null,Modifier.size(17.dp))};if(expanded){Column(Modifier.fillMaxWidth().offset(y=54.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(18.dp)).border(.8.dp,MaterialTheme.colorScheme.onSurface.copy(alpha=.08f),RoundedCornerShape(18.dp)).padding(6.dp)){sessions.forEach{s->Text("${s.displayBookingNumber} · ${s.booking.route.originCode} → ${s.booking.route.outboundDestination}",Modifier.fillMaxWidth().clickable{onSelect(s.id);expanded=false}.padding(12.dp),fontSize=14.sp)}}}}}}

@Composable internal fun TelegramConnectCard(language:AppLanguage,session:StoredBookingSession,accountToken:String?,bookingStore:BookingStore){val context=LocalContext.current;val scope=rememberCoroutineScope();val service=remember{TelegramBookingIntegrationService()};var connecting by remember{mutableStateOf(false)};var linked by remember{mutableStateOf(false)};var opened by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};val headers=remember(session,accountToken){buildMap{session.accessToken.trim().takeIf{it.isNotBlank()}?.let{put("x-booking-token",it)};accountToken?.trim()?.takeIf{it.isNotBlank()}?.let{put("Authorization","Bearer $it")}}};LaunchedEffect(session.id){if(headers.isNotEmpty())linked=runCatching{service.isLinked(session.id,headers)}.getOrDefault(false)};Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(26.dp)).border(.8.dp,MaterialTheme.colorScheme.onSurface.copy(alpha=.06f),RoundedCornerShape(26.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){Row(verticalAlignment=Alignment.Top){Box(Modifier.size(48.dp).background(TelegramBlue.copy(alpha=.12f),CircleShape),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.Send,null,Modifier.size(21.dp),tint=TelegramBlue)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(tr(language,"Статус бронирования в Telegram","Booking status in Telegram","Bron holati Telegram’da","Брон ҳолати Telegram’да"),fontSize=18.sp,fontWeight=FontWeight.Bold);Text(tr(language,"Подключите эту бронь один раз. Бот iumrah будет автоматически присылать изменения статуса, оплаты, подтверждения и документов.","Connect this booking once. The iumrah bot will send status, payment, confirmation and document updates automatically.","Bu bronni bir marta ulang. iumrah boti yangilanishlarni avtomatik yuboradi.","Бу бронни бир марта уланг. iumrah боти янгиланишларни автоматик юборади."),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))}};if(opened)Text(tr(language,"Telegram открыт. Нажмите «Запустить» в боте, чтобы завершить привязку этой брони.","Telegram opened. Tap Start in the bot to finish linking this booking.","Telegram ochildi. Botda Start tugmasini bosing.","Telegram очилди. Ботда Start тугмасини босинг."),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=TelegramGreen);error?.let{Text(it,fontSize=12.sp,color=Color(0xFFFF3B30))};IumrahPressable(onClick={if(!connecting&&!linked){scope.launch{connecting=true;error=null;runCatching{service.createLink(session.id,headers,language.code)}.onSuccess{url->opened=true;context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}.onFailure{error=tr(language,"Не удалось создать ссылку Telegram.","Could not create the Telegram link.","Telegram havolasini yaratib bo‘lmadi.","Telegram ҳаволасини яратиб бўлмади.")};connecting=false}}},modifier=Modifier.fillMaxWidth().height(52.dp),enabled=!connecting&&!linked,cornerRadius=18.dp,background=if(linked)TelegramGreen else TelegramBlue){Row(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){CupertinoIcon(if(linked)CupertinoSymbol.CheckCircleFill else CupertinoSymbol.Send,null,Modifier.size(18.dp),tint=Color.White);Spacer(Modifier.width(9.dp));Text(if(linked)tr(language,"Telegram подключен","Telegram connected","Telegram ulangan","Telegram уланган") else if(connecting)tr(language,"Создаём безопасную ссылку…","Creating secure link…","Xavfsiz havola yaratilmoqda…","Хавфсиз ҳавола яратилмоқда…") else tr(language,"Подключить Telegram","Connect Telegram","Telegram’ni ulash","Telegram’ни улаш"),color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));if(!linked&&!connecting)CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(15.dp),tint=Color.White)}}} }
