package com.iumrah.beta.ui.planning

import android.app.*
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.content.*
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.iumrah.beta.R
import com.iumrah.beta.core.widget.IumrahWidgetSync
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.homeflow.InternalNavBar
import com.iumrah.beta.ui.homeflow.IosCard
import com.iumrah.beta.ui.homeflow.tr
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

private data class PlannedTrip(
    val title:String="Umrah", val start:LocalDate, val end:LocalDate, val background:String="gradient-sunset",
    val reminderDays:Set<Int> = setOf(60,30,20,15,10,7,5,3,2,1), val hour:Int=19, val minute:Int=0,
    val notifications:Boolean=true, val language:String="ru"
)

private class PlanStore(private val context:Context){
    private val prefs=context.getSharedPreferences("iumrah.planned-trip.v1",Context.MODE_PRIVATE)
    var trip by mutableStateOf(load()); private set
    fun reload() { trip = load() }
    fun save(t:PlannedTrip){ trip=t; prefs.edit().putString("title",t.title).putString("start",t.start.toString()).putString("end",t.end.toString()).putString("bg",t.background).putString("days",t.reminderDays.sortedDescending().joinToString(",")).putInt("hour",t.hour).putInt("minute",t.minute).putBoolean("notifications",t.notifications).putString("language",t.language).apply(); PlanScheduler.reschedule(context,t); IumrahWidgetSync.refreshAll(context) }
    fun delete(){ trip=null; prefs.edit().clear().apply(); PlanScheduler.cancel(context); IumrahWidgetSync.refreshAll(context) }
    private fun load():PlannedTrip?{ val s=prefs.getString("start",null)?:return null; return runCatching{PlannedTrip(prefs.getString("title","Umrah")?:"Umrah",LocalDate.parse(s),LocalDate.parse(prefs.getString("end",s)),prefs.getString("bg","gradient-sunset")?:"gradient-sunset",prefs.getString("days","")!!.split(',').mapNotNull{it.toIntOrNull()}.toSet().ifEmpty{setOf(60,30,20,15,10,7,5,3,2,1)},prefs.getInt("hour",19),prefs.getInt("minute",0),prefs.getBoolean("notifications",true),prefs.getString("language","ru")?:"ru")}.getOrNull() }
}

private object PlanScheduler {
    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        (0..200).forEach { id ->
            am.cancel(PendingIntent.getBroadcast(context, 8600 + id, Intent(context, UmrahPlanReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        am.cancel(PendingIntent.getBroadcast(context, 8999, Intent(context, UmrahPlanReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    fun reschedule(context: Context, trip: PlannedTrip) {
        cancel(context)
        if (!trip.notifications) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        trip.reminderDays.filter { it > 0 }.toSet().sortedDescending().forEachIndexed { index, day ->
            val zdt = trip.start.minusDays(day.toLong()).atTime(trip.hour, trip.minute).atZone(ZoneId.systemDefault())
            if (zdt.toInstant().toEpochMilli() > System.currentTimeMillis()) {
                val intent = Intent(context, UmrahPlanReminderReceiver::class.java)
                    .putExtra("days", day)
                    .putExtra("language", trip.language)
                    .putExtra("start", trip.start.toString())
                val pi = PendingIntent.getBroadcast(context, 8600 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zdt.toInstant().toEpochMilli(), pi)
            }
        }
    }

    fun schedulePreview(context: Context, trip: PlannedTrip): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, UmrahPlanReminderReceiver::class.java)
            .putExtra("days", 20)
            .putExtra("language", trip.language)
            .putExtra("start", trip.start.toString())
            .putExtra("preview", true)
        val pi = PendingIntent.getBroadcast(context, 8999, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 4_000L, pi)
        true
    }.getOrDefault(false)
}

class UmrahPlanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val languageRaw = intent.getStringExtra("language") ?: "ru"
        val days = intent.getIntExtra("days", 7)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("umrah_plan", "Umrah Plan", NotificationManager.IMPORTANCE_DEFAULT))
        }
        manager.notify(
            if (intent.getBooleanExtra("preview", false)) 8799 else 8700 + days,
            NotificationCompat.Builder(context, "umrah_plan")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(notificationTitle(languageRaw, days))
                .setContentText(notificationBodyRaw(languageRaw, days))
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** Mirrors iOS UmrahPlanReminderCenterView. This is Umrah Plan, not the excluded Umrah Flow. */
@Composable
fun UmrahPlanReminderCenter(language: AppLanguage, onOpenPlan: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PlanStore(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { store.reload() }
    val trip = store.trip
    var editing by remember { mutableStateOf(false) }

    if (editing && trip != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { editing = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ReminderEditor(language, trip, { editing = false }) {
                    store.save(it)
                    editing = false
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (trip == null) {
            val shape = RoundedCornerShape(32.dp)
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFFFFFAF5), Color(0xFFFFE8D9))), shape)
                    .border(.8.dp, Color.Black.copy(alpha = .08f), shape).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                StackedPlanIcon()
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(language, "Запланировать Umrah", "Plan your Umrah", "Umrani rejalashtiring", "Умрани режалаштиринг"),
                        fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Color.Black)
                    Text(tr(language,
                        "Выберите будущие даты и iumrah будет спокойно напоминать о подготовке к поездке.",
                        "Choose your future dates and iumrah will calmly remind you as your trip gets closer.",
                        "Kelajakdagi sanalarni tanlang — iumrah safar yaqinlashgani sari tayyorgarlikni eslatib turadi.",
                        "Келажакдаги саналарни танланг — iumrah сафар яқинлашгани сари тайёргарликни эслатиб туради."),
                        fontSize = 16.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, color = Color.Black.copy(alpha = .55f))
                }
                IumrahPressable(onOpenPlan, Modifier.fillMaxWidth().height(58.dp), cornerRadius = 20.dp, background = Color(0xFFFF520F)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "Создать план поездки", "Create trip plan", "Safar rejasini yaratish", "Сафар режасини яратиш"),
                            modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(17.dp), tint = Color.White)
                    }
                }
            }
            Text(
                tr(language,
                    "Сначала запланируйте следующую Umrah — после этого здесь появится расписание напоминаний.",
                    "Plan your next Umrah first; your reminder schedule will then appear here.",
                    "Avval keyingi Umrani rejalashtiring — so‘ng bu yerda eslatmalar jadvali paydo bo‘ladi.",
                    "Аввал кейинги Умрани режалаштиринг — сўнг бу ерда эслатмалар жадвали пайдо бўлади."),
                modifier = Modifier.padding(horizontal = 4.dp),
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val foreground = if (backgroundPrefersLight(trip.background)) Color.White else Color.Black
            Box(
                Modifier.fillMaxWidth().height(286.dp).clip(RoundedCornerShape(32.dp))
                    .clickable(onClick = onOpenPlan),
            ) {
                PlanBackground(trip.background)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .16f)))))
                Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "ПРЕДСТОЯЩАЯ", "UPCOMING", "KELGUSI", "КЕЛГУСИ"),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                            color = foreground.copy(alpha = .86f), modifier = Modifier.weight(1f))
                        CupertinoIcon(if (trip.notifications) CupertinoSymbol.BellFill else CupertinoSymbol.BellSlashFill,
                            null, Modifier.size(16.dp), tint = foreground.copy(alpha = .86f))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(trip.title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = foreground)
                    Text(heroCountdown(language, trip), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        color = foreground.copy(alpha = .78f))
                    Text(formatRange(trip.start, trip.end), fontSize = 14.sp,
                        color = foreground.copy(alpha = .72f))
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.fillMaxWidth().height(48.dp)
                            .background(Color.White.copy(alpha = .92f), RoundedCornerShape(17.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CupertinoIcon(CupertinoSymbol.BellBadge, null, Modifier.size(16.dp), tint = Color.Black)
                        Text(tr(language, "Напоминания настроены", "Reminders ready", "Eslatmalar tayyor", "Эслатмалар тайёр"),
                            fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                            modifier = Modifier.weight(1f))
                        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(16.dp), tint = Color.Black)
                    }
                }
            }
        }
        if (trip != null) {
            IosCard(radius = 28) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(48.dp).background(Color(0xFF1677FF).copy(alpha = .10f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CupertinoIcon(CupertinoSymbol.BellBadge, null, Modifier.size(19.dp), tint = Color(0xFF1677FF))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr(language,
                                "Напоминания о следующей поездке", "Next-trip reminders",
                                "Keyingi safar eslatmalari", "Кейинги сафар эслатмалари"),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                            Text(tr(language,
                                "iumrah напомнит о подготовке заранее и будет делать это чаще по мере приближения поездки.",
                                "iumrah reminds you early, then increases the cadence as your journey gets closer.",
                                "iumrah safar yaqinlashgani sari eslatmalarni oldindan va tez-tez yuboradi.",
                                "iumrah сафар яқинлашгани сари эслатмаларни олдиндан ва тез-тез юборади."),
                                fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        trip.reminderDays.sortedDescending().take(4).forEach { day ->
                            Text(
                                reminderCenterDayLabel(language, day),
                                modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), CircleShape)
                                    .height(30.dp).wrapContentHeight(Alignment.CenterVertically)
                                    .padding(horizontal = 9.dp),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("%02d:%02d".format(trip.hour, trip.minute), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IumrahPressable(
                        { editing = true }, Modifier.fillMaxWidth().height(50.dp),
                        cornerRadius = 17.dp, background = Color.Black,
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr(language, "Настроить напоминания", "Edit reminders", "Eslatmalarni sozlash", "Эслатмаларни созлаш"),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                modifier = Modifier.weight(1f))
                            CupertinoIcon(CupertinoSymbol.Sliders, null, Modifier.size(18.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun reminderCenterDayLabel(language: AppLanguage, day: Int): String = when (language) {
    AppLanguage.RUSSIAN -> if (day >= 30) "${day / 30} мес." else "$day дн."
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> if (day >= 30) "${day / 30} mo" else "${day}d"
    AppLanguage.UZBEK -> if (day >= 30) "${day / 30} oy" else "$day kun"
    AppLanguage.UZBEK_CYRILLIC -> if (day >= 30) "${day / 30} ой" else "$day кун"
}

@Composable
fun UmrahPlanScreen(language:AppLanguage, chrome:AppChromeStore){
    val context=LocalContext.current
    val store=remember{PlanStore(context)}
    var editor by remember{mutableStateOf(false)}
    var editReminders by remember{mutableStateOf(false)}
    if(editor){ PlanEditor(language,store.trip,{editor=false},{store.save(it);editor=false}); return }
    if(editReminders && store.trip!=null){ ReminderEditor(language,store.trip!!,{editReminders=false}){store.save(it);editReminders=false}; return }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)){
        InternalNavBar(tr(language,"План Umrah","Umrah Plan","Umrah rejasi","Umrah режаси"),chrome)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
            PlanHeader(language,store.trip,{if(store.trip!=null)editReminders=true})
            val trip=store.trip
            if(trip==null){ UpcomingLabel(language); CreationCard(language,{editor=true},{chrome.navigate(com.iumrah.beta.core.navigation.AppTab.BOOKING)}) }
            else { PlannedHero(language,trip,{editReminders=true}); RouteCard(language,trip); ReminderSection(language,trip,{editReminders=true}); PackageFutureCard(language); TextButton(onClick={store.delete()},modifier=Modifier.fillMaxWidth().height(54.dp)){Text(tr(language,"Удалить план поездки","Delete trip plan","Safar rejasini o‘chirish","Сафар режасини ўчириш"),color=Color(0xFFFF3B30),fontWeight=FontWeight.SemiBold)} }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PlanHeader(language: AppLanguage, trip: PlannedTrip?, settings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(tr(language, "Мои поездки", "My trips", "Safarlarim", "Сафарларим"), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
            Text(
                (trip?.start ?: LocalDate.now()).year.toString(),
                color = Color(0xFFFF520F), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color(0xFFFF520F).copy(alpha = .10f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        if (trip != null) IumrahPressable(settings, Modifier.size(44.dp), cornerRadius = 22.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Sliders, null, Modifier.size(17.dp)) }
        }
    }
}
@Composable
private fun UpcomingLabel(language: AppLanguage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr(language, "Предстоящий", "Upcoming", "Kelgusi", "Келгуси"), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .18f))
    }
}
@Composable
private fun CreationCard(language: AppLanguage, onCreate: () -> Unit, onBookings: () -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFFBF7), Color(0xFFFFE6D6))), shape)
            .border(.8.dp, Color.Black.copy(alpha = .08f), shape).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StackedPlanIcon()
        Spacer(Modifier.height(20.dp))
        Text(tr(language, "Организовать\nновую Umrah", "Plan a new\nUmrah", "Yangi Umrani\nrejalashtirish", "Янги Умрани\nрежалаштириш"), fontSize = 31.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 34.sp)
        Spacer(Modifier.height(9.dp))
        Text(
            tr(language,
                "Создайте будущую поездку, выберите даты и настройте напоминания о подготовке.",
                "Create your future trip, choose dates and set preparation reminders.",
                "Kelajakdagi safarni yarating, sanalarni tanlang va tayyorgarlik eslatmalarini sozlang.",
                "Келажакдаги сафарни яратинг, саналарни танланг ва тайёргарлик эслатмаларини созланг."),
            fontSize = 16.sp, color = Color.Black.copy(alpha = .55f), textAlign = TextAlign.Center, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(20.dp))
        OrangeButton(tr(language, "Создать новую поездку", "Create a new trip", "Yangi safar yaratish", "Янги сафар яратиш"), onCreate)
        Spacer(Modifier.height(10.dp))
        IumrahPressable(onBookings, Modifier.fillMaxWidth().height(58.dp), cornerRadius = 20.dp, background = Color.Black.copy(alpha = .06f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr(language, "Открыть мои бронирования", "Open my bookings", "Bronlarimni ochish", "Бронларимни очиш"), color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}
@Composable private fun StackedPlanIcon(){Box(Modifier.height(66.dp).width(100.dp),contentAlignment=Alignment.Center){Box(Modifier.offset(x=(-18).dp).size(44.dp,54.dp).background(Color.White,RoundedCornerShape(10.dp)));Box(Modifier.size(44.dp,54.dp).background(Color(0xFFEBC09A),RoundedCornerShape(10.dp)));Box(Modifier.offset(x=18.dp).size(44.dp,54.dp).background(Color(0xFF8DBAE9),RoundedCornerShape(10.dp)));CupertinoIcon(CupertinoSymbol.MoonStarsFill,null,Modifier.size(19.dp),tint=Color.Black.copy(alpha=.72f))}}

@Composable private fun PlannedHero(language:AppLanguage,t:PlannedTrip,onReminder:()->Unit){ val fg=if(backgroundPrefersLight(t.background))Color.White else Color.Black; Box(Modifier.fillMaxWidth().height(410.dp).clip(RoundedCornerShape(34.dp))){PlanBackground(t.background);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.12f),Color.Transparent,Color.Black.copy(alpha=.36f)))));Column(Modifier.fillMaxSize().padding(20.dp)){Row{Box(Modifier.size(42.dp).background(Color.Black.copy(alpha=.24f),CircleShape),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.MoonStarsFill,null,Modifier.size(19.dp),tint=Color.White)};Spacer(Modifier.weight(1f));Box(Modifier.size(42.dp).background(Color.Black.copy(alpha=.24f),CircleShape),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.CalendarClock,null,Modifier.size(19.dp),tint=Color.White)}};Spacer(Modifier.weight(1f));Text(t.title,fontSize=31.sp,fontWeight=FontWeight.Bold,color=fg,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth());Text(heroCountdown(language,t),fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=fg.copy(alpha=.82f),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth());Text(formatRange(t.start,t.end),fontSize=13.5.sp,color=fg.copy(alpha=.78f),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth());Spacer(Modifier.weight(1f));IumrahPressable(onReminder,Modifier.fillMaxWidth().height(50.dp),cornerRadius=18.dp,background=Color.White.copy(alpha=.18f)){Row(Modifier.fillMaxSize().padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically){CupertinoIcon(CupertinoSymbol.BellBadge,null,Modifier.size(18.dp),tint=Color.White);Spacer(Modifier.width(9.dp));Text(tr(language,"Настроить напоминания","Manage reminders","Eslatmalarni sozlash","Эслатмаларни созлаш"),color=Color.White,fontWeight=FontWeight.SemiBold)}}} } }
@Composable private fun RouteCard(language:AppLanguage,t:PlannedTrip){IosCard(radius=28){Column(Modifier.padding(18.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(Color(0xFFFF520F).copy(alpha=.11f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.Route,null,Modifier.size(21.dp),tint=Color(0xFFFF520F))};Spacer(Modifier.width(12.dp));Column{Text(tr(language,"Ваша будущая поездка","Your future trip","Kelgusi safaringiz","Келгуси сафарингиз"),fontWeight=FontWeight.Bold,fontSize=18.sp);Text(formatRange(t.start,t.end),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f))}};Spacer(Modifier.height(17.dp));Row{PlanMetric(tr(language,"Начало","Starts","Boshlanish","Бошланиш"),shortDate(t.start),Modifier.weight(1f));PlanMetric(tr(language,"Дней","Days","Kun","Кун"),(ChronoUnit.DAYS.between(t.start,t.end)+1).toString(),Modifier.weight(1f));PlanMetric(tr(language,"Маршрут","Route","Yo‘nalish","Йўналиш"),tr(language,"Позже","Later","Keyin","Кейин"),Modifier.weight(1f))}}}}
@Composable private fun PlanMetric(label:String,value:String,modifier:Modifier){Column(modifier){Text(label,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.48f));Text(value,fontSize=16.sp,fontWeight=FontWeight.Bold)}}
@Composable private fun ReminderSection(language:AppLanguage,t:PlannedTrip,onEdit:()->Unit){Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text(tr(language,"Какие уведомления вы получите","What notifications you'll get","Qanday bildirishnomalar olasiz","Қандай билдиришномалар оласиз"),fontSize=22.sp,fontWeight=FontWeight.Bold);t.reminderDays.sortedDescending().take(4).forEach{day->Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(20.dp)).border(.7.dp,MaterialTheme.colorScheme.onSurface.copy(alpha=.055f),RoundedCornerShape(20.dp)).padding(15.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Color(0xFFFF520F).copy(alpha=.11f),RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.Bell,null,Modifier.size(19.dp),tint=Color(0xFFFF520F))};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(daysBefore(language,day),fontWeight=FontWeight.SemiBold);Text(notificationBody(language,day),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f),maxLines=2)}}};IumrahPressable(onEdit,Modifier.fillMaxWidth().height(54.dp),cornerRadius=18.dp,background=MaterialTheme.colorScheme.surfaceVariant){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(tr(language,"Настроить расписание","Edit schedule","Jadvalni sozlash","Жадвални созлаш"),fontWeight=FontWeight.SemiBold)}}}}
@Composable private fun PackageFutureCard(language:AppLanguage){IosCard(radius=24){Column(Modifier.padding(18.dp)){Row{Text(tr(language,"Пакеты для вашей даты","Packages for your dates","Sanangiz uchun paketlar","Санангиз учун пакетлар"),fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text(tr(language,"Скоро","Soon","Tez orada","Тез орада"),fontSize=11.sp,fontWeight=FontWeight.Bold,color=Color(0xFFFF520F),modifier=Modifier.background(Color(0xFFFF520F).copy(alpha=.09f),CircleShape).padding(horizontal=10.dp,vertical=6.dp))};Spacer(Modifier.height(8.dp));Text(tr(language,"Следующим этапом сюда подключатся Hotel First и Flight First — iumrah покажет подходящий пакет прямо в плане.","Next, Hotel First and Flight First will connect here so iumrah can surface a matching package directly in this plan.","Keyinroq Hotel First va Flight First ulanadi va mos paket shu yerda ko‘rinadi.","Кейинроқ Hotel First ва Flight First уланади ва мос пакет шу ерда кўринади."),fontSize=14.5.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))}}}

@Composable private fun PlanEditor(language:AppLanguage,existing:PlannedTrip?,onCancel:()->Unit,onSave:(PlannedTrip)->Unit){ val context=LocalContext.current; val base=existing?:PlannedTrip(start=LocalDate.now().plusMonths(2),end=LocalDate.now().plusMonths(2).plusDays(7),language=language.code); var title by remember{mutableStateOf(base.title)};var start by remember{mutableStateOf(base.start)};var end by remember{mutableStateOf(base.end)};var bg by remember{mutableStateOf(base.background)};var showBackgrounds by remember{mutableStateOf(false)};var showReminders by remember{mutableStateOf(false)};var draft by remember{mutableStateOf(base)};if(showBackgrounds){BackgroundPicker(language,bg,{showBackgrounds=false}){bg=it;showBackgrounds=false};return};if(showReminders){ReminderEditor(language,draft.copy(start=start,end=end,background=bg,title=title),{showReminders=false}){draft=it;showReminders=false};return};Box(Modifier.fillMaxSize()){PlanBackground(bg);Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.22f),Color.Black.copy(alpha=.04f),Color.Black.copy(alpha=.34f)))));Column(Modifier.fillMaxSize().statusBarsPadding().padding(18.dp)){Row{GlassCapsule(tr(language,"Отмена","Cancel","Bekor qilish","Бекор қилиш"),onCancel);Spacer(Modifier.weight(1f));GlassCapsule(if(existing==null)tr(language,"Создать поездку","Create trip","Safar yaratish","Сафар яратиш") else tr(language,"Сохранить","Save","Saqlash","Сақлаш")){onSave(draft.copy(title=title,start=start,end=maxOf(end,start),background=bg,language=language.code))}};Spacer(Modifier.weight(1f));androidx.compose.foundation.text.BasicTextField(title,{title=it},Modifier.fillMaxWidth(),textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=32.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center),singleLine=true);Spacer(Modifier.height(8.dp));Text(formatRange(start,end),color=Color.White.copy(alpha=.78f),textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth());Spacer(Modifier.weight(1f));Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){EditorAction(CupertinoSymbol.CalendarClock,tr(language,"Даты","Dates","Sanalar","Саналар"),Modifier.weight(1f)){pickDate(context,start){start=it;if(end<it)end=it.plusDays(7)};pickDate(context,end){end=maxOf(it,start)}};EditorAction(CupertinoSymbol.Grid,tr(language,"Фон","Background","Fon","Фон"),Modifier.weight(1f)){showBackgrounds=true};EditorAction(CupertinoSymbol.BellBadge,tr(language,"Напоминания","Reminders","Eslatmalar","Эслатмалар"),Modifier.weight(1f)){showReminders=true}};Spacer(Modifier.height(12.dp))}}}
@Composable private fun GlassCapsule(title:String,onClick:()->Unit){IumrahPressable(onClick,Modifier.height(42.dp),cornerRadius=21.dp,background=Color.Black.copy(alpha=.28f)){Box(Modifier.padding(horizontal=14.dp).fillMaxHeight(),contentAlignment=Alignment.Center){Text(title,color=Color.White,fontSize=14.5.sp,fontWeight=FontWeight.Bold)}}}
@Composable private fun EditorAction(icon:CupertinoSymbol,title:String,modifier:Modifier,onClick:()->Unit){IumrahPressable(onClick,modifier.height(68.dp),cornerRadius=20.dp,background=Color.Black.copy(alpha=.27f)){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){CupertinoIcon(icon,null,Modifier.size(20.dp),tint=Color.White);Spacer(Modifier.height(7.dp));Text(title,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}}}

@Composable
private fun BackgroundPicker(language: AppLanguage, selected: String, onBack: () -> Unit, onSelect: (String) -> Unit) {
    val photos = listOf("photo-makkah-window", "photo-kaaba-arch")
    val colors = listOf("gradient-sunset", "gradient-dawn", "gradient-sky", "gradient-mint", "gradient-gold", "gradient-ocean", "gradient-lime", "gradient-night", "gradient-sand")
    var tab by remember { mutableIntStateOf(if (selected.startsWith("photo-")) 0 else 1) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(language, "Выберите фон", "Choose background", "Fon tanlang", "Фон танланг"), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(tr(language, "Готово", "Done", "Tayyor", "Тайёр"), fontWeight = FontWeight.SemiBold, color = Color(0xFF007AFF), modifier = Modifier.clickable { onBack() })
        }
        Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(3.dp)) {
            listOf(tr(language, "Фото", "Photos", "Rasmlar", "Расмлар"), tr(language, "Цвета", "Colors", "Ranglar", "Ранглар")).forEachIndexed { index, title ->
                IumrahPressable({ tab = index }, Modifier.weight(1f).height(36.dp), cornerRadius = 9.dp, background = if (tab == index) MaterialTheme.colorScheme.surface else Color.Transparent) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        val items = if (tab == 0) photos else colors
        Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(if (tab == 0) 2 else 3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { id ->
                        Box(Modifier.weight(1f).height(160.dp).clip(RoundedCornerShape(16.dp)).clickable { onSelect(id) }) {
                            PlanBackground(id)
                            if (id == selected) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(16.dp), tint = Color.Black)
                            }
                        }
                    }
                    repeat((if (tab == 0) 2 else 3) - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun reminderPermissionMessage(language: AppLanguage, success: Boolean): String = if (success)
    tr(language,
        "Тестовое уведомление запланировано через несколько секунд.",
        "The test notification is scheduled in a few seconds.",
        "Sinov bildirishnomasi bir necha soniyadan so‘ng rejalashtirildi.",
        "Синов билдиришномаси бир неча сониядан сўнг режалаштирилди.")
else tr(language,
        "Для напоминаний разрешите уведомления iumrah в настройках Android.",
        "Allow iumrah notifications in Android Settings to receive reminders.",
        "Eslatmalar uchun Android sozlamalarida iumrah bildirishnomalariga ruxsat bering.",
        "Эслатмалар учун Android созламаларида iumrah билдиришномаларига рухсат беринг.")

@Composable
private fun ReminderEditor(language: AppLanguage, trip: PlannedTrip, onBack: () -> Unit, onSave: (PlannedTrip) -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(trip.notifications) }
    var days by remember { mutableStateOf(trip.reminderDays) }
    var hour by remember { mutableIntStateOf(trip.hour) }
    var minute by remember { mutableIntStateOf(trip.minute) }
    var customDays by remember { mutableIntStateOf(12) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    var pendingSave by remember { mutableStateOf<PlannedTrip?>(null) }
    var pendingPreview by remember { mutableStateOf<PlannedTrip?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val nextSave = pendingSave
        val nextPreview = pendingPreview
        pendingSave = null
        pendingPreview = null
        if (granted) {
            if (nextSave != null) onSave(nextSave)
            if (nextPreview != null) {
                previewMessage = if (PlanScheduler.schedulePreview(context, nextPreview))
                    reminderPermissionMessage(language, true) else reminderPermissionMessage(language, false)
            }
        } else {
            previewMessage = reminderPermissionMessage(language, false)
        }
    }
    fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun configuredTrip(): PlannedTrip = trip.copy(reminderDays = days, hour = hour, minute = minute,
        notifications = enabled, language = language.code)
    val allDays = (setOf(60, 30, 20, 15, 10, 7, 5, 3, 2, 1) + days).sortedDescending()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.statusBarsPadding().height(56.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(language, "Напоминания", "Reminders", "Eslatmalar", "Эслатмалар"), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(tr(language, "Сохранить", "Save", "Saqlash", "Сақлаш"), fontWeight = FontWeight.Bold, color = Color(0xFF007AFF), modifier = Modifier.clickable {
                val result = configuredTrip()
                if (result.notifications && !permissionGranted()) {
                    pendingSave = result
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else onSave(result)
            })
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(language, "Напоминать о поездке", "Trip reminders", "Safar eslatmalari", "Сафар эслатмалари"), fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(enabled, { enabled = it })
            }
            if (enabled) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp)).padding(16.dp)) {
                    Text(tr(language, "Время уведомлений", "Reminder time", "Eslatma vaqti", "Эслатма вақти"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(tr(language, "Все выбранные напоминания приходят в это время.", "All selected reminders arrive at this time.", "Tanlangan eslatmalar shu vaqtda keladi.", "Танланган эслатмалар шу вақтда келади."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberControl(hour, { hour = (it + 24) % 24 }, 0..23, Modifier.weight(1f))
                        NumberControl(minute, { minute = (it + 60) % 60 }, 0..59, Modifier.weight(1f))
                    }
                }
                Text(tr(language, "Расписание", "Schedule", "Jadval", "Жадвал"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                allDays.forEach { day ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(daysBefore(language, day), fontWeight = FontWeight.SemiBold)
                            if (day == 20 || day == 15) Text(tr(language, "С этого этапа напоминания становятся чаще", "Reminders become more frequent from here", "Shu bosqichdan eslatmalar tezlashadi", "Шу босқичдан эслатмалар тезлашади"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                        Switch(day in days, { checked -> days = if (checked) days + day else days - day })
                    }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberControl(customDays, { customDays = it.coerceIn(1, 180) }, 1..180, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    IumrahPressable({ days = days + customDays }, Modifier.size(42.dp), cornerRadius = 21.dp, background = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Plus, null, Modifier.size(20.dp)) }
                    }
                }
                IumrahPressable({
                    val previewTrip = configuredTrip()
                    if (!permissionGranted()) {
                        pendingPreview = previewTrip
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        previewMessage = reminderPermissionMessage(language, PlanScheduler.schedulePreview(context, previewTrip))
                    }
                }, Modifier.fillMaxWidth().height(56.dp), cornerRadius = 19.dp, background = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                        CupertinoIcon(CupertinoSymbol.BellBadge, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(tr(language, "Отправить тестовое уведомление", "Send test notification", "Sinov bildirishnomasini yuborish", "Синов билдиришномасини юбориш"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                previewMessage?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
            }
        }
    }
}
@Composable private fun NumberControl(value:Int,set:(Int)->Unit,range:IntRange,modifier:Modifier){Row(modifier.height(54.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(18.dp)),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly){CupertinoIcon(CupertinoSymbol.Minus,null,Modifier.size(20.dp).clickable{set(if(value==range.first)range.last else value-1)});Text("%02d".format(value),fontWeight=FontWeight.Bold,fontSize=18.sp);CupertinoIcon(CupertinoSymbol.Plus,null,Modifier.size(20.dp).clickable{set(if(value==range.last)range.first else value+1)})}}

@Composable private fun PlanBackground(id:String){when(id){"photo-makkah-window"->Image(painterResource(R.drawable.iumrah_makkah_background),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);"photo-kaaba-arch"->Image(painterResource(R.drawable.umrah_plan_kaaba),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);else->Box(Modifier.fillMaxSize().background(Brush.linearGradient(backgroundColors(id))))}}
private fun backgroundColors(id:String)=when(id){"gradient-dawn"->listOf(Color(0xFFFFA630),Color(0xFFF24F3B));"gradient-sky"->listOf(Color(0xFF2EA3F7),Color(0xFF3857E0));"gradient-mint"->listOf(Color(0xFF52D18C),Color(0xFF1A9687));"gradient-gold"->listOf(Color(0xFFFAD42E),Color(0xFFF77A26));"gradient-ocean"->listOf(Color(0xFF00C9E3),Color(0xFF0359BF));"gradient-lime"->listOf(Color(0xFFCCE619),Color(0xFF4DBA38));"gradient-night"->listOf(Color(0xFF424057),Color(0xFF0D1421));"gradient-sand"->listOf(Color(0xFFE0A870),Color(0xFF8C6147));else->listOf(Color(0xFFBA5E38),Color(0xFF597AA3))}
private fun backgroundPrefersLight(id:String)=id !in setOf("gradient-gold","gradient-lime")
@Composable private fun OrangeButton(title:String,onClick:()->Unit){IumrahPressable(onClick,Modifier.fillMaxWidth().height(58.dp),cornerRadius=20.dp,background=Color(0xFFFF520F)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=16.sp)}}}
private fun formatRange(a:LocalDate,b:LocalDate)="${a.format(DateTimeFormatter.ofPattern("d MMM"))} — ${b.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
private fun shortDate(d:LocalDate)=d.format(DateTimeFormatter.ofPattern("d MMM"))
private fun heroCountdown(language:AppLanguage,t:PlannedTrip):String{val days=max(0,ChronoUnit.DAYS.between(LocalDate.now(),t.start).toInt());val duration=ChronoUnit.DAYS.between(t.start,t.end).toInt()+1;return when(language){AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY ->if(days>0)"Starts in $days days · $duration-day trip" else "Starts today · $duration-day trip";AppLanguage.RUSSIAN->if(days>0)"Начнётся через $days дней · $duration дней поездки" else "Начинается сегодня · $duration дней поездки";AppLanguage.UZBEK->if(days>0)"$days kundan keyin boshlanadi · $duration kun" else "Bugun boshlanadi · $duration kun";AppLanguage.UZBEK_CYRILLIC->if(days>0)"$days кундан кейин бошланади · $duration кун" else "Бугун бошланади · $duration кун"}}
private fun daysBefore(l:AppLanguage,d:Int)=tr(l,"За $d дней","$d days before","$d kun oldin","$d кун олдин")
private fun notificationTitle(languageRaw: String, days: Int): String {
    val l = when {
        languageRaw == "en" -> AppLanguage.ENGLISH
        languageRaw == "uz" -> AppLanguage.UZBEK
        languageRaw.contains("cyrl", ignoreCase = true) || languageRaw.contains("cyr", ignoreCase = true) -> AppLanguage.UZBEK_CYRILLIC
        else -> AppLanguage.RUSSIAN
    }
    return when (l) {
        AppLanguage.RUSSIAN -> if (days == 1) "Umrah уже завтра" else "До Umrah осталось $days дней"
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> if (days == 1) "Your Umrah starts tomorrow" else "$days days until your Umrah"
        AppLanguage.UZBEK -> if (days == 1) "Umrangiz ertaga boshlanadi" else "Umragacha $days kun qoldi"
        AppLanguage.UZBEK_CYRILLIC -> if (days == 1) "Умрангиз эртага бошланади" else "Умрагача $days кун қолди"
    }
}

private fun notificationBodyRaw(languageRaw: String, days: Int): String {
    val l = when {
        languageRaw == "en" -> AppLanguage.ENGLISH
        languageRaw == "uz" -> AppLanguage.UZBEK
        languageRaw.contains("cyrl", ignoreCase = true) || languageRaw.contains("cyr", ignoreCase = true) -> AppLanguage.UZBEK_CYRILLIC
        else -> AppLanguage.RUSSIAN
    }
    return when (l) {
        AppLanguage.RUSSIAN -> when { days >= 60 -> "План уже создан. Начните спокойно готовить документы, даты и бюджет поездки."; days >= 30 -> "До поездки месяц. Проверьте паспорт, перелёт и отель — всё важное будет в одном плане iumrah."; days >= 15 -> "Поездка становится ближе. Проверьте документы, трансфер и список подготовки."; days >= 7 -> "Umrah уже скоро. Проверьте финальные детали поездки и всё необходимое в дороге."; else -> "Финальная подготовка к Umrah. Откройте план и проверьте даты, документы и маршрут." }
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> when { days >= 60 -> "Your plan is saved. Start preparing documents, dates and budget at your own pace."; days >= 30 -> "One month to go. Check your passport, flight and hotel in your iumrah plan."; days >= 15 -> "Your trip is getting close. Review documents, transfers and preparation."; days >= 7 -> "Your Umrah is close. Review the final trip details and what you need to take."; else -> "Final Umrah preparation. Open your plan and check dates, documents and route." }
        AppLanguage.UZBEK -> when { days >= 60 -> "Rejangiz saqlandi. Hujjatlar, sanalar va safar byudjetini xotirjam tayyorlashni boshlang."; days >= 30 -> "Safargacha bir oy. Pasport, parvoz va mehmonxonani iumrah rejangizda tekshiring."; days >= 15 -> "Safar yaqinlashmoqda. Hujjatlar, transfer va tayyorgarlik ro‘yxatini tekshiring."; days >= 7 -> "Umra yaqin. Safarning yakuniy tafsilotlarini va kerakli narsalarni tekshiring."; else -> "Umraga yakuniy tayyorgarlik. Rejangizni ochib sana, hujjat va yo‘nalishni tekshiring." }
        AppLanguage.UZBEK_CYRILLIC -> when { days >= 60 -> "Режангиз сақланди. Ҳужжатлар, саналар ва сафар бюджетини хотиржам тайёрлашни бошланг."; days >= 30 -> "Сафаргача бир ой. Паспорт, парвоз ва меҳмонхонани iumrah режангизда текширинг."; days >= 15 -> "Сафар яқинлашмоқда. Ҳужжатлар, трансфер ва тайёргарлик рўйхатини текширинг."; days >= 7 -> "Умра яқин. Сафарнинг якуний тафсилотларини ва керакли нарсаларни текширинг."; else -> "Умрага якуний тайёргарлик. Режангизни очиб сана, ҳужжат ва йўналишни текширинг." }
    }
}

private fun notificationBody(l: AppLanguage, d: Int) = notificationBodyRaw(l.code, d)
private fun pickDate(context:Context,current:LocalDate,onSet:(LocalDate)->Unit){DatePickerDialog(context,{_,y,m,d->onSet(LocalDate.of(y,m+1,d))},current.year,current.monthValue-1,current.dayOfMonth).show()}
