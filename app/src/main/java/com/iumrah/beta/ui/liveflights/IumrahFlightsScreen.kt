package com.iumrah.beta.ui.liveflights

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.models.booking.BookingGeneratorFlightSnapshot
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.homeflow.IosCard
import com.iumrah.beta.ui.homeflow.InternalNavBar
import com.iumrah.beta.ui.homeflow.tr
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable
fun IumrahFlightsScreen(language: AppLanguage, bookingStore: BookingStore, chrome: AppChromeStore, preferredBookingID: String? = null) {
    val bookingState by bookingStore.state.collectAsState()
    val sessions = bookingState.sessions.filter { bookingStore.canOpenBookingFromPush(it.id) && it.effectiveStatus in setOf("BOOKING_CONFIRMED", "READY_TO_TRAVEL", "IN_TRIP") }
    val accessSession = if (preferredBookingID != null) sessions.firstOrNull { it.id == preferredBookingID } else sessions.firstOrNull()
    LaunchedEffect(accessSession?.id) { accessSession?.id?.let { id ->
        try { bookingStore.refresh(id) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
    } }
    val flights = remember(accessSession) { listOfNotNull(accessSession?.booking?.generatorTrace?.outbound, accessSession?.booking?.generatorTrace?.inbound) }
    val primary = flights.firstOrNull()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    var scheduled by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) { sendFlightsDemoNotification(context, language); scheduled = true }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        InternalNavBar("iumrah Flights", chrome)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 50.dp)
        ) {
            FlightsGlobeHero(language, accessSession != null, primary)
            Column(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FlightIntelligenceCard(language, primary)
                NotificationCard(language, scheduled, permissionDenied) {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else { sendFlightsDemoNotification(context, language); scheduled = true; permissionDenied = false }
                }
                TripSearchCard(language, accessSession, flights, query, { query = it })
                FriendsCard(language, accessSession)
                AccessPrincipleCard(language, accessSession != null, chrome)
                androidx.compose.foundation.Image(
                    painterResource(R.drawable.iumrah_flights_wordmark), null,
                    Modifier.fillMaxWidth().padding(horizontal = 34.dp).heightIn(max = 86.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun FlightsGlobeHero(language: AppLanguage, hasAccess: Boolean, flight: BookingGeneratorFlightSnapshot?) {
    Box(Modifier.fillMaxWidth().height(590.dp).background(Color(0xFF05070B))) {
        AnimatedGlobe(Modifier.fillMaxWidth().height(520.dp).align(Alignment.TopCenter))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xFF05070B)), startY = 230f)))
        Text("iumrah Flights", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.9).sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 8.dp))
        Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                tr(language, "Ваш рейс. Живой статус.", "Your flight. Live status.", "Parvozingiz. Jonli holat.", "Парвозингиз. Жонли ҳолат."),
                color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = (-1.0f).sp, lineHeight = 38.sp,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                tr(language, "Изменения времени, задержки и важные обновления поездки — в одном месте.", "Schedule changes, delays and important trip updates — in one place.", "Vaqt o‘zgarishi, kechikish va safar yangiliklari — bir joyda.", "Вақт ўзгариши, кечикиш ва сафар янгиликлари — бир жойда."),
                color = Color.White.copy(alpha=.72f), fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.height(38.dp).clip(CircleShape).background(Color.White.copy(alpha=.12f)).border(.7.dp, Color.White.copy(alpha=.16f), CircleShape).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(if (hasAccess) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.Lock, null, Modifier.size(16.dp), tint = if (hasAccess) Color(0xFF34C759) else Color.White.copy(alpha=.7f))
                Spacer(Modifier.width(7.dp))
                Text(if (hasAccess) tr(language,"Доступ активен","Access active","Kirish faol","Кириш фаол") else tr(language,"Для активной поездки","For active trips","Faol safar uchun","Фаол сафар учун"), color=Color.White, fontSize=13.sp, fontWeight=FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AnimatedGlobe(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "flights-globe")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "phase")
    val pulse by transition.animateFloat(.45f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        val r = min(size.width * .58f, size.height * .46f)
        val c = Offset(size.width/2, size.height*.49f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF183A78).copy(alpha=.72f), Color(0xFF071321).copy(alpha=.88f), Color.Transparent), c, r*1.35f), r*1.35f, c)
        drawCircle(Color(0xFF0B1830), r, c)
        drawCircle(Color(0xFF6F9CD8).copy(alpha=.28f), r, c, style = Stroke(1.2f))
        for (i in -3..3) {
            val y = c.y + i * r/4
            val rx = sqrt(max(0f, r*r - (y-c.y)*(y-c.y)))
            drawOval(Color.White.copy(alpha=.10f), topLeft=Offset(c.x-rx,y-1), size=Size(rx*2,2f), style=Stroke(1f))
        }
        for (i in 0 until 8) {
            val xoff = sin((i/8f + phase)*PI*2).toFloat() * r*.84f
            val width = sqrt(max(0f, r*r-xoff*xoff))*2
            drawOval(Color.White.copy(alpha=.09f), topLeft=Offset(c.x+xoff-width*.12f,c.y-r), size=Size(width*.24f,r*2), style=Stroke(1f))
        }
        val start = Offset(c.x-r*.66f,c.y+r*.18f)
        val end = Offset(c.x+r*.57f,c.y-r*.30f)
        val path=Path().apply{ moveTo(start.x,start.y); quadraticBezierTo(c.x,c.y-r*.77f,end.x,end.y) }
        drawPath(path, Color(0xFF63A8FF).copy(alpha=.68f), style=Stroke(3f, cap=StrokeCap.Round))
        drawCircle(Color.White, 5f+3f*pulse, start); drawCircle(Color(0xFF76B6FF), 6f+4f*pulse, end)
        val t=phase; val omt=1-t; val p=Offset(omt*omt*start.x+2*omt*t*c.x+t*t*end.x, omt*omt*start.y+2*omt*t*(c.y-r*.77f)+t*t*end.y)
        drawCircle(Color.White.copy(alpha=.22f), 13f, p); drawCircle(Color.White, 4.5f, p)
    }
}

@Composable
private fun FlightIntelligenceCard(language: AppLanguage, flight: BookingGeneratorFlightSnapshot?) {
    IosCard(radius=32) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(tr(language,"Рейс под контролем","Flight intelligence","Parvoz nazoratda","Парвоз назоратда"), tr(language,"iumrah следит за ключевыми данными Вашего маршрута и показывает их в контексте поездки.","iumrah keeps key flight data connected to your trip.","iumrah asosiy parvoz ma’lumotlarini safaringiz bilan bog‘laydi.","iumrah асосий парвоз маълумотларини сафарингиз билан боғлайди."))
            Spacer(Modifier.height(16.dp))
            if (flight != null) FlightStatusPreview(language, flight) else EmptyFlightPreview(language)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeaturePill(CupertinoSymbol.BellSignal, tr(language,"Изменения","Changes","O‘zgarishlar","Ўзгаришлар"), Modifier.weight(1f))
                FeaturePill(CupertinoSymbol.Airplane, tr(language,"Статус","Status","Holat","Ҳолат"), Modifier.weight(1f))
                FeaturePill(CupertinoSymbol.CalendarClock, tr(language,"Время","Timing","Vaqt","Вақт"), Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun SectionTitle(title:String, body:String){ Text(title,fontSize=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.65).sp); Spacer(Modifier.height(7.dp)); Text(body,fontSize=16.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.58f),lineHeight=21.sp) }

@Composable
private fun FlightStatusPreview(language: AppLanguage, f: BookingGeneratorFlightSnapshot) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f), RoundedCornerShape(26.dp)).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=.06f), RoundedCornerShape(26.dp)).padding(18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(42.dp).background(Color.White,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){ CupertinoIcon(CupertinoSymbol.Airplane,null,Modifier.size(22.dp),tint=Color.Black) }
            Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)){ Text(f.airline,fontWeight=FontWeight.SemiBold,fontSize=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis); Text(f.flightNumbers,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.52f)) }
            Text(tr(language,"ПЛАН","SCHEDULED","REJA","РЕЖА"),fontSize=11.sp,fontWeight=FontWeight.Bold,color=Color(0xFF30A14E),modifier=Modifier.background(Color(0xFF30A14E).copy(alpha=.1f),CircleShape).padding(horizontal=9.dp,vertical=6.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment=Alignment.CenterVertically){
            AirportCode(f.origin, Modifier.weight(1f)); Column(horizontalAlignment=Alignment.CenterHorizontally){ CupertinoIcon(CupertinoSymbol.Airplane,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f)); Text("•••",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.35f)) }; AirportCode(f.destination, Modifier.weight(1f), true)
        }
        Spacer(Modifier.height(16.dp)); Row{ TimeMetric("Departure", formatTime(f.departureAt),Modifier.weight(1f)); TimeMetric("Arrival",formatTime(f.arrivalAt),Modifier.weight(1f),true) }
    }
}
@Composable private fun AirportCode(code:String, modifier:Modifier=Modifier, right:Boolean=false){ Text(code.uppercase(),fontSize=28.sp,fontWeight=FontWeight.Bold,textAlign=if(right)TextAlign.End else TextAlign.Start,modifier=modifier) }
@Composable private fun TimeMetric(title:String,value:String,modifier:Modifier,right:Boolean=false){ Column(modifier,horizontalAlignment=if(right)Alignment.End else Alignment.Start){ Text(title,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f)); Text(value,fontSize=20.sp,fontWeight=FontWeight.Bold) } }
@Composable private fun EmptyFlightPreview(language:AppLanguage){ Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f),RoundedCornerShape(26.dp)).padding(18.dp)){ CupertinoIcon(CupertinoSymbol.Airplane,null,Modifier.size(27.dp)); Spacer(Modifier.height(10.dp)); Text(tr(language,"Рейс появится после подтверждения поездки","Your flight appears after trip confirmation","Parvoz safar tasdiqlangach paydo bo‘ladi","Парвоз сафар тасдиқлангач пайдо бўлади"),fontWeight=FontWeight.SemiBold); Text(tr(language,"Данные привязываются к активному iumrah Booking.","Flight data is tied to your active iumrah Booking.","Ma’lumot faol iumrah Booking bilan bog‘lanadi.","Маълумот фаол iumrah Booking билан боғланади."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)) } }
@Composable private fun FeaturePill(icon:CupertinoSymbol,title:String,modifier:Modifier){ Row(modifier.height(64.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f),RoundedCornerShape(18.dp)).padding(horizontal=11.dp),verticalAlignment=Alignment.CenterVertically){ CupertinoIcon(icon,null,Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(title,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=2) } }

@Composable
private fun NotificationCard(language:AppLanguage, scheduled:Boolean, denied:Boolean, onNotify:()->Unit){
    IosCard(radius=32){ Column(Modifier.padding(20.dp)){ SectionTitle(tr(language,"Получайте важные изменения","Get important updates","Muhim o‘zgarishlarni oling","Муҳим ўзгаришларни олинг"),tr(language,"Покажем, как выглядит уведомление при изменении рейса.","See how a flight-change notification looks.","Parvoz o‘zgarishi bildirishnomasi qanday ko‘rinishini ko‘ring.","Парвоз ўзгариши билдиришномаси қандай кўринишини кўринг.")); Spacer(Modifier.height(15.dp)); Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f),RoundedCornerShape(24.dp)).padding(14.dp)){ Box(Modifier.size(42.dp).background(Color.Black,RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){ CupertinoIcon(CupertinoSymbol.Airplane,null,Modifier.size(21.dp),tint=Color.White)}; Spacer(Modifier.width(11.dp)); Column{ Row{ Text("iumrah Flights",fontSize=13.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.weight(1f)); Text(tr(language,"сейчас","now","hozir","ҳозир"),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.46f))}; Text(tr(language,"Тестовое уведомление iumrah Flights","iumrah Flights test notification","iumrah Flights sinov xabari","iumrah Flights синов хабари"),fontSize=14.sp,fontWeight=FontWeight.SemiBold); Text(tr(language,"Это проверка уведомлений, не изменение рейса.","This is a notification test, not a flight change.","Bu sinov xabari, reys o‘zgarmadi.","Бу синов хабари, рейс ўзгармади."),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f)) } }; Spacer(Modifier.height(15.dp)); BlackButton(if(scheduled)tr(language,"Уведомление отправлено","Notification sent","Bildirishnoma yuborildi","Билдиришнома юборилди") else tr(language,"Показать тестовое уведомление","Show test notification","Sinov bildirishnomasini ko‘rsatish","Синов билдиришномасини кўрсатиш"), onNotify); if(denied){ Spacer(Modifier.height(8.dp)); Text(tr(language,"Разрешите уведомления в настройках Android.","Allow notifications in Android settings.","Android sozlamalarida bildirishnomalarga ruxsat bering.","Android созламаларида билдиришномаларга рухсат беринг."),fontSize=12.sp,color=Color(0xFFFF3B30)) } } }
}

@Composable
private fun TripSearchCard(
    language: AppLanguage,
    session: StoredBookingSession?,
    flights: List<BookingGeneratorFlightSnapshot>,
    query: String,
    onQuery: (String) -> Unit,
) {
    IosCard(radius = 32) {
        Column(Modifier.padding(20.dp)) {
            SectionTitle(
                tr(language, "Найдите рейс своей поездки", "Find a trip flight", "Safaringiz parvozini toping", "Сафарингиз парвозини топинг"),
                tr(language, "Поиск работает внутри Ваших активных бронирований iumrah.", "Search works inside your active iumrah trips.", "Qidiruv faol iumrah bronlaringiz ichida ishlaydi.", "Қидирув фаол iumrah бронларингиз ичида ишлайди."),
            )
            Spacer(Modifier.height(15.dp))
            if (session == null) {
                Row(
                    Modifier.fillMaxWidth().height(58.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CupertinoIcon(CupertinoSymbol.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        tr(language, "Только для активной поездки", "Active trips only", "Faqat faol safar uchun", "Фақат фаол сафар учун"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    )
                }
            } else {
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth().height(58.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 15.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                    decorationBox = { inner ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
                            Spacer(Modifier.width(8.dp))
                            Box {
                                if (query.isBlank()) Text(
                                    tr(language, "Номер рейса или аэропорт", "Flight number or airport", "Reys raqami yoki aeroport", "Рейс рақами ёки аэропорт"),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f),
                                )
                                inner()
                            }
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                val filtered = flights.filter { flight ->
                    query.isBlank() || listOf(flight.flightNumbers, flight.origin, flight.destination, flight.airline).any { it.contains(query, true) }
                }
                if (filtered.isEmpty()) {
                    Text(tr(language, "Совпадений нет", "No matches", "Moslik topilmadi", "Мослик топилмади"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                } else {
                    filtered.forEach { flight ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(flight.flightNumbers, fontWeight = FontWeight.SemiBold)
                                Text("${flight.origin} → ${flight.destination} · ${flight.airline}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                            }
                            Text(formatTime(flight.departureAt), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun FriendsCard(language:AppLanguage, session:StoredBookingSession?){ IosCard(radius=32){Column(Modifier.padding(20.dp)){SectionTitle(tr(language,"Летите вместе","Travel together","Birga parvoz qiling","Бирга парвоз қилинг"),tr(language,"Участники одной поездки видят один связанный маршрут.","People on one trip see one connected route.","Bir safardagi hamrohlar yagona marshrutni ko‘radi.","Бир сафардаги ҳамроҳлар ягона маршрутни кўради."));Spacer(Modifier.height(16.dp)); Row(verticalAlignment=Alignment.CenterVertically){repeat(3){i->Box(Modifier.offset(x=(-i*7).dp).size(34.dp).background(listOf(Color(0xFF5E5CE6),Color(0xFF0A84FF),Color(0xFFFF9F0A))[i],CircleShape),contentAlignment=Alignment.Center){Text(listOf("A","M","S")[i],color=Color.White,fontWeight=FontWeight.Bold,fontSize=12.sp)}};Spacer(Modifier.width(4.dp));Column{Text(session?.travelerName?.takeIf{it.isNotBlank()}?:tr(language,"Ваша поездка","Your trip","Safaringiz","Сафарингиз"),fontWeight=FontWeight.SemiBold);Text(if(session!=null)session.displayBookingNumber else tr(language,"После подтверждения бронирования","After booking confirmation","Bron tasdiqlangach","Брон тасдиқлангач"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f))}}} } }

@Composable private fun AccessPrincipleCard(language:AppLanguage,hasAccess:Boolean,chrome:AppChromeStore){ val shape=RoundedCornerShape(32.dp); Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFEEF4FF),Color(0xFFF1EEFF),MaterialTheme.colorScheme.surface)),shape).border(.7.dp,Color(0xFF5E5CE6).copy(alpha=.12f),shape).padding(22.dp)){CupertinoIcon(CupertinoSymbol.ShieldCheck,null,Modifier.size(28.dp),tint=Color(0xFF5E5CE6));Spacer(Modifier.height(14.dp));Text(tr(language,"Flights — часть поездки, а не отдельный сервис","Flights are part of your trip, not a separate product","Flights — safarning bir qismi","Flights — сафарнинг бир қисми"),fontSize=27.sp,fontWeight=FontWeight.Bold,lineHeight=30.sp);Spacer(Modifier.height(8.dp));Text(tr(language,"Доступ включается для подтверждённой iumrah-поездки и связывает рейс с остальными компонентами.","Access activates for a confirmed iumrah trip and keeps your flight connected to the rest of the journey.","Kirish tasdiqlangan iumrah safari uchun faollashadi.","Кириш тасдиқланган iumrah сафари учун фаоллашади."),fontSize=14.sp,color=Color.Black.copy(alpha=.6f));if(!hasAccess){Spacer(Modifier.height(16.dp));BlackButton(tr(language,"Собрать поездку","Build a trip","Safar tuzish","Сафар тузиш")){chrome.startNewTrip()}}} }

@Composable private fun BlackButton(title:String,onClick:()->Unit){IumrahPressable(onClick,Modifier.fillMaxWidth().height(56.dp),cornerRadius=20.dp,background=Color.Black){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(title,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=16.sp)}}}
private fun formatTime(raw:String):String=runCatching{OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("HH:mm"))}.getOrElse{raw.takeLast(5)}
private fun sendFlightsDemoNotification(context: Context, language: AppLanguage){ val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager; if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel("iumrah_flights","iumrah Flights",NotificationManager.IMPORTANCE_DEFAULT)); val n=NotificationCompat.Builder(context,"iumrah_flights").setSmallIcon(R.mipmap.ic_launcher).setContentTitle(tr(language,"Тестовое уведомление iumrah Flights","iumrah Flights test notification","iumrah Flights sinov xabari","iumrah Flights синов хабари")).setContentText(tr(language,"Это проверка уведомлений, не изменение рейса.","This is a notification test, not a flight change.","Bu sinov xabari, reys o‘zgarmadi.","Бу синов хабари, рейс ўзгармади.")).setAutoCancel(true).build(); manager.notify(781,n) }
