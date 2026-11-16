package com.iumrah.beta.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.smart.HomeActivity
import com.iumrah.beta.data.smart.HomeActivityStore
import com.iumrah.beta.data.smart.HomePublicContentCache
import com.iumrah.beta.domain.smart.SmartDraft
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.hotels.nuiteeText
import com.iumrah.beta.ui.hotels.nuiteeUSD
import java.time.LocalDate
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.iumrah.beta.data.smart.HomeHotelVisit
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.nuitee.NuiteeHotelService
import com.iumrah.beta.data.nuitee.NuiteeStaySelectionStore
import com.iumrah.beta.ui.account.rememberRewardsDashboard
import com.iumrah.beta.ui.hotels.NuiteeHotelDetailSheet
import com.iumrah.beta.core.navigation.AppTab

@Composable private fun HomePersonalHeader(title: String) = Text(title, fontSize = 31.sp, lineHeight = 36.sp, letterSpacing = (-.8).sp, fontWeight = FontWeight.Bold)
@Composable private fun HomeSmallActivityCard(symbol: CupertinoSymbol, label: String, action: () -> Unit, accent: Color = Color(0xFF007AFF), content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.width(220.dp).height(184.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), RoundedCornerShape(22.dp)).clickable(onClick = action).padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)), contentAlignment = Alignment.Center) { CupertinoIcon(symbol, null, Modifier.size(20.dp), accent) }
            Spacer(Modifier.weight(1f)); CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        content()
    }
}
@Composable internal fun HomeRecentActivitySection(language: AppLanguage, activity: HomeActivity, booking: StoredBookingSession?, chrome: AppChromeStore, owner: String?,
                                                   resume: (SmartDraft) -> Unit, repeat: (TripDraft) -> Unit) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    if (activity.smart == null && activity.flight == null && booking == null && activity.hotels.isEmpty()) return
    var selectedHotel by remember(owner) { mutableStateOf<HomeHotelVisit?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePersonalHeader(t("Продолжите с того места, где остановились", "Pick up where you left off", "To‘xtagan joyingizdan davom eting", "Тўхтаган жойингиздан давом этинг"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            booking?.let { b -> item { HomeSmallActivityCard(CupertinoSymbol.SuitcaseFill, t("Ваша поездка", "Your journey", "Safaringiz", "Сафарингиз"), { chrome.openBookingDetail(b.id) }, accent = Color(0xFF34C759)) {
                Text("${b.booking.input.originCode} → ${b.booking.input.arrivalAirportCode}", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(L10n.status(b.effectiveStatus, language), fontSize = 13.sp, maxLines = 2)
                Text(b.displayBookingNumber, fontSize = 12.sp)
            } } }
            activity.smart?.let { draft -> item { HomeSmallActivityCard(if (draft.wasAssembled) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.Sparkles,
                if (draft.wasAssembled) t("Недавно собранный пакет", "Recently assembled package", "Yaqinda tuzilgan paket", "Яқинда тузилган пакет") else t("Незавершённая Умра", "Unfinished Umrah", "Tugallanmagan Umra", "Тугалланмаган Умра"), { resume(draft) }) {
                Text("iumrah Smart", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("${runCatching { java.time.YearMonth.parse(draft.preferences.month).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", language.locale)) }.getOrDefault(draft.preferences.month)} · ${draft.preferences.duration} " + t("дн.", "days", "kun", "кун"), fontSize = 12.sp)
                if (!draft.wasAssembled) LinearProgressIndicator(progress = { draft.step.coerceIn(0, 8) / 8f }, modifier = Modifier.fillMaxWidth())
                Text(if (draft.wasAssembled) t("Проверить пакет", "Recheck package", "Paketni tekshirish", "Пакетни текшириш") else t("Продолжить сборку", "Continue", "Davom etish", "Давом этиш"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } } }
            activity.flight?.trip()?.let { trip -> item { HomeSmallActivityCard(CupertinoSymbol.Airplane, t("Недавний поиск", "Recent search", "So‘nggi qidiruv", "Сўнгги қидирув"), { repeat(trip) }) {
                Text("${trip.originCode} → ${trip.outboundDestinationCode}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(trip.departureDate.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(language.locale)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t("Повторить поиск", "Search again", "Qayta qidirish", "Қайта қидириш"), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            } } }
            items(activity.hotels.take(4), key = { "recent-hotel-${it.hotel.id}" }) { visit ->
                HomeRecommendedHotelCard(visit, language, recent = true) { selectedHotel = visit }
            }
        }
    }
    selectedHotel?.let { visit -> HomeHotelVisitDetail(visit, language) { selectedHotel = null } }
}
@Composable internal fun HomeDestinationsSection(language: AppLanguage, onCity: (String) -> Unit) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePersonalHeader(t("Ваше следующее путешествие", "Your next journey", "Keyingi safaringiz", "Кейинги сафарингиз"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeDestinationCard(Modifier.weight(1f), R.drawable.iumrah_makkah_background, t("Мекка", "Makkah", "Makka", "Макка"), t("Совершите Умру", "Perform Umrah", "Umra ziyorati", "Умра зиёрати")) { onCity("makkah") }
            HomeDestinationCard(Modifier.weight(1f), R.drawable.ziyarat_quba_1, t("Медина", "Madinah", "Madina", "Мадина"), t("Город Пророка ﷺ", "City of the Prophet ﷺ", "Payg‘ambarimiz ﷺ shahri", "Пайғамбаримиз ﷺ шаҳри")) { onCity("madinah") }
        }
    }
}
@Composable private fun HomeDestinationCard(modifier: Modifier, image: Int, title: String, subtitle: String, action: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = action).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(image), null, Modifier.fillMaxWidth().height(138.dp).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}
@Composable internal fun HomeAvailablePackagesSection(language: AppLanguage, origin: String, chrome: AppChromeStore, startSmart: () -> Unit) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    var values by remember(origin) { mutableStateOf<List<StorefrontPackageSnapshot>>(emptyList()) }
    LaunchedEffect(origin) { values = HomePublicContentCache.packages(origin) }
    val points = values.filter { it.startDate?.take(10)?.let { day -> day >= LocalDate.now().toString() } == true }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePersonalHeader(t("Актуальные пакеты Умры", "Umrah packages", "Umra paketlari", "Умра пакетлари"))
        if (points.isEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = startSmart).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            CupertinoIcon(CupertinoSymbol.SuitcaseFill, null, Modifier.size(36.dp)); Column(Modifier.weight(1f)) {
                Text(t("Подобрать пакет по вашим датам", "Find a package for your dates", "Sanalaringizga mos paket", "Саналарингизга мос пакет"), fontWeight = FontWeight.Bold)
                Text(t("Рейсы, отели и услуги — единая стоимость", "Flights, hotels and services — one total", "Reys, mehmonxona va xizmatlar — bitta narx", "Рейс, меҳмонхона ва хизматлар — битта нарх"), fontSize = 12.sp)
            }; CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(15.dp))
        } else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(points, key = { it.id }) { point ->
            HomeSmallActivityCard(CupertinoSymbol.Calendar, point.startDate.orEmpty().take(10), { chrome.openFlightPackage(point.id) }) {
                Text(point.hotelName.orEmpty(), fontWeight = FontWeight.Bold, maxLines = 2)
                Text(point.pricePerPerson?.let(::nuiteeUSD) ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(t("На одного паломника · тариф требует проверки", "Per traveler · subject to recheck", "Bir ziyoratchiga · tarif tekshiriladi", "Бир зиёратчига · тариф текширилади"), fontSize = 11.sp, maxLines = 2)
            }
        } }
    }
}
@Composable internal fun HomeWhyAndPreferencesSection(language: AppLanguage, history: HomeActivityStore, owner: String?, revision: Int) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    var expanded by remember { mutableStateOf(false) }
    val enabled = remember(revision) { history.enabled }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePersonalHeader(t("Почему iumrah?", "Why iumrah?", "Nega iumrah?", "Нега iumrah?"))
        val features = listOf(
            Triple(CupertinoSymbol.PersonsFill,t("С семьёй, без группы", "With family, not a group", "Oila bilan, guruhsiz", "Оила билан, гуруҳсиз"), t("Путешествуйте в удобном темпе", "Travel at your own pace", "O‘zingizga qulay sur’atda", "Ўзингизга қулай суръатда")),
            Triple(CupertinoSymbol.PackageBox,t("Единый пакет", "One package", "Bitta paket", "Битта пакет"),t("Рейсы, отели и услуги вместе", "Flights, hotels and services together", "Reys, mehmonxona va xizmatlar birga", "Рейс, меҳмонхона ва хизматлар бирга")),
            Triple(CupertinoSymbol.HeartFill,t("Персональная поддержка", "Personal support", "Shaxsiy yordam", "Шахсий ёрдам"), t("Сопровождение вашей поездки", "Support throughout your journey", "Safaringiz davomida yordam", "Сафарингиз давомида ёрдам")),
            Triple(CupertinoSymbol.Sliders,t("Свобода выбора", "Freedom to choose", "Tanlash erkinligi", "Танлаш эркинлиги"), t("Уровень комфорта и маршрут на ваш выбор", "Your comfort level and route", "Qulaylik va yo‘nalishni tanlang", "Қулайлик ва йўналишни танланг")))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(features) { (symbol, title, desc) ->
            Column(Modifier.width(190.dp).height(168.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CupertinoIcon(symbol, null, Modifier.size(32.dp)); Text(title, fontWeight = FontWeight.Bold); Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text(t("Управление рекомендациями", "Manage recommendations", "Tavsiyalarni boshqarish", "Тавсияларни бошқариш"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                CupertinoIcon(if (expanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown, null, Modifier.size(16.dp))
            }
            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Персональные рекомендации", "Personal recommendations", "Shaxsiy tavsiyalar", "Шахсий тавсиялар"), Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = history::setEnabled) }
                Text(t("Недавние действия хранятся на этом устройстве. Отключение очищает историю рекомендаций, но не удаляет бронирования.", "Recent activity stays on this device. Turning it off clears recommendation history, not bookings.", "Tarix shu qurilmada saqlanadi. O‘chirish bronlarni emas, tavsiyalar tarixini tozalaydi.", "Тарих шу қурилмада сақланади. Ўчириш бронларни эмас, тавсиялар тарихини тозалайди."), fontSize = 12.sp)
                TextButton(onClick = { history.clear(owner) }) { Text(t("Очистить недавние действия", "Clear recent activity", "So‘nggi harakatlarni tozalash", "Сўнгги ҳаракатларни тозалаш")) }
            }
        }
    }
}

@Composable internal fun HomeRewardsOffersSection(language: AppLanguage, account: IumrahAccountStore, chrome: AppChromeStore) {
    val live = rememberRewardsDashboard(account, pollSeconds=0)
    val state by account.state.collectAsState()
    val tier=live?.tier?.lowercase()?.takeIf { it in listOf("member","plus","elite") }?.replaceFirstChar { it.uppercase() }
    fun open(page: String) { if(state.isAuthenticated) chrome.openRewards(page) else chrome.navigate(AppTab.ACCOUNT) }
    fun t(ru:String,en:String,uz:String,cy:String)=nuiteeText(language,ru,en,uz,cy)
    Column(verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            HomePersonalHeader(t("Путешествуйте с преимуществами","Travel with benefits","Imtiyozlar bilan sayohat qiling","Имтиёзлар билан саёҳат қилинг"))
            LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                item { Column(Modifier.width(220.dp).height(184.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black).clickable { open("rewards") }.padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Text("iumrah Rewards", color=Color.White,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));Box(Modifier.size(38.dp).background(Color.White.copy(alpha=.12f),CircleShape),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.StarFill,null,Modifier.size(20.dp),Color(0xFFFFCC00))}}
                    Spacer(Modifier.weight(1f));Text(tier?:"iumrah Rewards",color=Color.White,fontWeight=FontWeight.Bold,fontSize=22.sp);Text(if(tier!=null)t("Ваш текущий уровень","Your current level","Joriy darajangiz","Жорий даражангиз")else t("Откройте программу лояльности","Explore loyalty rewards","Sodiqlik dasturini oching","Содиқлик дастурини очинг"),color=Color.White.copy(alpha=.75f),fontSize=14.sp)
                } }
                item { HomeSmallActivityCard(CupertinoSymbol.Gift,"iumrah Friends",{open("friends")}){Text(if(live?.invitation!=null)"+$100" else t("Пригласите друга","Invite a friend","Do‘stingizni taklif qiling","Дўстингизни таклиф қилинг"),fontSize=21.sp,fontWeight=FontWeight.Bold);Text(t("Вознаграждение после подтверждения оплаты его первой поездки","Reward after confirmed payment for their first trip","Birinchi safar to‘lovi tasdiqlangach mukofot","Биринчи сафар тўлови тасдиқлангач мукофот"),fontSize=12.sp,maxLines=3,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
                if(tier!=null && tier!="Elite") item { HomeSmallActivityCard(CupertinoSymbol.Lock,t("Следующий уровень","Next level","Keyingi daraja","Кейинги даража"),{open("rewards")}){Text(if(tier=="Member")"iumrah Plus" else "iumrah Elite",fontWeight=FontWeight.Bold,fontSize=20.sp);Text(t("Условия и ваш прогресс","Requirements and your progress","Shartlar va natijangiz","Шартлар ва натижангиз"),fontSize=14.sp)} }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            HomePersonalHeader(t("Специальные предложения","Special offers","Maxsus takliflar","Махсус таклифлар"))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable{open(if(live?.invitation!=null)"friends" else "rewards")}.padding(17.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Text(if(live?.invitation!=null)t("Ваши рекомендации","Your referrals","Tavsiyalaringiz","Тавсияларингиз") else "iumrah Rewards",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(live?.invitation!=null)t("$100 за успешное приглашение","$100 per successful referral","Muvaffaqiyatli taklif uchun $100","Муваффақиятли таклиф учун $100") else t("Откройте свои преимущества","Unlock your benefits","Imtiyozlaringizni oching","Имтиёзларингизни очинг"),fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Text(if(live?.invitation!=null)t("После подтверждённой оплаты поездки другом","After your friend's confirmed trip payment","Do‘stingizning safari to‘lovi tasdiqlangach","Дўстингизнинг сафари тўлови тасдиқлангач") else t("Условия программы лояльности","Loyalty program details","Sodiqlik dasturi shartlari","Содиқлик дастури шартлари"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t("Подробнее","Learn more","Batafsil","Батафсил"),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                };Box(Modifier.size(66.dp).background(Color(0xFF34C759).copy(alpha=.12f),CircleShape),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.Gift,null,Modifier.size(32.dp),Color(0xFF34C759))}
            }
        }
    }
}

@Composable private fun HomeRecommendedHotelCard(visit:HomeHotelVisit,language:AppLanguage,recent:Boolean=false,onClick:()->Unit){
    val hotel=visit.hotel
    Column(Modifier.width(if (recent) 220.dp else 205.dp).height(if (recent) 184.dp else 202.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=onClick).padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        AsyncImage(hotel.coverImageURL,hotel.name,Modifier.fillMaxWidth().height(if (recent) 88.dp else 110.dp).clip(RoundedCornerShape(if (recent) 12.dp else 13.dp)),contentScale=ContentScale.Crop)
        if(recent) Text(nuiteeText(language,"Просмотренный отель","Viewed hotel","Ko‘rilgan mehmonxona","Кўрилган меҳмонхона"),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
        Text(hotel.name,fontWeight=FontWeight.Bold,fontSize=15.sp,maxLines=2)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){repeat(hotel.roundedStars){CupertinoIcon(CupertinoSymbol.StarFill,null,Modifier.size(11.dp),MaterialTheme.colorScheme.onSurfaceVariant)};if(!recent) hotel.rating?.let{Text(String.format(java.util.Locale.US,"%.1f",it),fontSize=12.sp,fontWeight=FontWeight.Bold)}}
    }
}
@Composable private fun HomeHotelVisitDetail(visit:HomeHotelVisit,language:AppLanguage,onDismiss:()->Unit){
    val context=LocalContext.current
    val service=remember(context){NuiteeHotelService(context)}
    val checkin=remember(visit){maxOf(runCatching{LocalDate.parse(visit.checkin)}.getOrDefault(LocalDate.now().plusDays(1)),LocalDate.now().plusDays(1))}
    val checkout=checkin.plusDays(visit.nights.coerceAtLeast(1).toLong())
    NuiteeHotelDetailSheet(language,service,visit.hotel,checkin,checkout,visit.adults.coerceIn(1,9),onDismiss=onDismiss,onRate={rate,_->NuiteeStaySelectionStore(context).save(visit.hotel,checkin.toString(),checkout.toString(),visit.adults.coerceIn(1,9),rate);onDismiss()})
}
@Composable internal fun HomeRecommendedHotelsSection(language:AppLanguage,activity:HomeActivity,owner:String?,chrome:AppChromeStore){
    val context=LocalContext.current
    val service=remember(context){NuiteeHotelService(context)}
    val recent=activity.hotels.firstOrNull()
    val city=if(recent?.hotel?.city?.lowercase()=="madinah")"madinah" else "makkah"
    val checkin=maxOf(recent?.checkin?.let{runCatching{LocalDate.parse(it)}.getOrNull()}?:LocalDate.now().plusDays(14),LocalDate.now().plusDays(1))
    val nights=recent?.nights?.coerceAtLeast(1)?:7;val adults=recent?.adults?.coerceIn(1,9)?:2
    var recommendations by remember(owner,recent?.hotel?.id,checkin,nights,adults){mutableStateOf<List<HomeHotelVisit>>(emptyList())}
    var selected by remember(owner){mutableStateOf<HomeHotelVisit?>(null)}
    LaunchedEffect(owner,city,recent?.hotel?.id,checkin,nights,adults){
        val source=HomePublicContentCache.hotels(service,city,checkin,nights,adults)
        recommendations=source?.takeIf { !it.sandbox }?.hotels.orEmpty().filter{it.id!=recent?.hotel?.id && !it.coverImageURL.isNullOrBlank()}.sortedByDescending{it.rating?:0.0}.take(4).map{HomeHotelVisit(it.copy(minimumRate=null,rateStatus=null),checkin.toString(),nights,adults)}
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        HomePersonalHeader(nuiteeText(language,"Рекомендуемые отели","Recommended hotels","Tavsiya etilgan mehmonxonalar","Тавсия этилган меҳмонхоналар"))
        if(recommendations.isNotEmpty())LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(recommendations,key={it.hotel.id}){visit->HomeRecommendedHotelCard(visit,language){selected=visit}}}
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).clickable{chrome.openHomeHotelSearch(city)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(nuiteeText(language,"Посмотреть все отели","View all hotels","Barcha mehmonxonalar","Барча меҳмонхоналар"),Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=15.sp);CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(15.dp))}
    }
    selected?.let{visit->HomeHotelVisitDetail(visit,language){selected=null}}
}
