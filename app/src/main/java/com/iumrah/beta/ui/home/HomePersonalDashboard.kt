package com.iumrah.beta.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.util.Locale

private fun AppLanguage.t(r:String,e:String,u:String,c:String)=homeText(this,r,e,u,c)
@Composable private fun DashboardTitle(title:String){Text(title,fontSize=31.sp,lineHeight=36.sp,letterSpacing=(-.8).sp,fontWeight=FontWeight.Bold)}
private fun amount(value:Double?)=value?.takeIf{it.isFinite()}?.let{String.format(Locale.US,"$%,.2f",it)}?:"—"

@Composable internal fun HomeDashboardActiveBooking(l:AppLanguage,s:StoredBookingSession,chrome:AppChromeStore){
    val ink=Color.Black;val secondary=ink.copy(alpha=.58f);val b=s.booking
    val badgeColor=com.iumrah.beta.core.design.IumrahBookingStatusVisual.color(s.effectiveStatus)
    val badgeIcon=when(s.effectiveStatus.uppercase()) {
        "NEW", "AVAILABILITY_CHECK" -> CupertinoSymbol.Hourglass
        "PAYMENT_PENDING" -> CupertinoSymbol.CreditCard
        "PAID", "BOOKING_CONFIRMED", "COMPLETED" -> CupertinoSymbol.CheckCircle
        "DOCUMENTS_READY", "READY_TO_TRAVEL" -> CupertinoSymbol.ShieldCheck
        "IN_TRIP" -> CupertinoSymbol.Location
        "CANCELLED", "CANCELED" -> CupertinoSymbol.ExclamationCircle
        else -> CupertinoSymbol.Suitcase
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(alpha=.96f)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(17.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){CupertinoIcon(CupertinoSymbol.MoonStarsFill,null,Modifier.size(16.dp),secondary);Spacer(Modifier.width(6.dp));Text(L10n.text("home_hero_kicker",l),Modifier.weight(1f),color=secondary,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=.7.sp);Box(Modifier.size(36.dp).background(badgeColor.copy(alpha=.16f),RoundedCornerShape(50)),contentAlignment=Alignment.Center){CupertinoIcon(badgeIcon,null,Modifier.size(15.dp),badgeColor)}}
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text(L10n.status(s.effectiveStatus,l),fontSize=30.sp,fontWeight=FontWeight.Bold,color=ink);s.travelerName?.takeIf{it.isNotBlank()}?.let{Text(it,fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=secondary)}}
        HorizontalDivider(color=ink.copy(alpha=.08f))
        DashboardBookingFact(CupertinoSymbol.Airplane,L10n.text("route_label",l),"${b.route.originCode} → ${b.route.outboundDestination}")
        DashboardBookingFact(CupertinoSymbol.Calendar,L10n.text("detail_dates",l),"${L10n.date(b.input.startDate,l)} – ${L10n.date(b.input.endDate,l)}")
        if(b.hotelNames.makkah.isNotBlank())DashboardBookingFact(CupertinoSymbol.BuildingFill,L10n.text("detail_hotel",l),b.hotelNames.makkah)
        Row(verticalAlignment=Alignment.Bottom){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(L10n.text("final_price",l),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=secondary);Text(amount(b.perPilgrimUsd),fontSize=27.sp,fontWeight=FontWeight.Bold,color=ink)};s.displayPilgrimID?.let{Text("ID $it",fontSize=11.sp,color=ink.copy(alpha=.42f))}}
        IumrahPressable(onClick={chrome.openBookingDetail(s.id)},modifier=Modifier.fillMaxWidth().height(54.dp),cornerRadius=19.dp,background=Color.Black){Row(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){Text(L10n.text("open_booking",l),Modifier.weight(1f),color=Color.White,fontWeight=FontWeight.SemiBold);CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(16.dp),Color.White)}}
    }
}
@Composable private fun DashboardBookingFact(icon:CupertinoSymbol,title:String,value:String){Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(34.dp).background(Color(0xffF2F2F7),RoundedCornerShape(50)),contentAlignment=Alignment.Center){CupertinoIcon(icon,null,Modifier.size(14.dp),Color.Black)};Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,fontSize=12.sp,color=Color.Black.copy(alpha=.55f));Text(value,fontSize=15.sp,color=Color.Black,fontWeight=FontWeight.SemiBold)}}}

/** iOS Home uses compact green-on-light Balance; Account uses dark variant. */
@Composable internal fun HomeDashboardBalance(l:AppLanguage,account:IumrahAccountStore,chrome:AppChromeStore){
    val snapshot by account.dashboardRewards.collectAsState();val live=snapshot.data?.availableCreditUsd?.takeIf{it.isFinite()};val ink=Color(0xff12402e);val subdued=ink.copy(alpha=.78f)
    val detail=when{live==null&&snapshot.loading->l.t("Обновляем баланс…","Updating balance…","Balans yangilanmoqda…","Баланс янгиланмоқда…");live==null->l.t("Баланс недоступен · откройте для обновления","Balance unavailable · open to refresh","Balans mavjud emas · yangilash uchun oching","Баланс мавжуд эмас · янгилаш учун очинг");snapshot.stale->l.t("Последний подтверждённый баланс","Last verified balance","Oxirgi tasdiqlangan balans","Охирги тасдиқланган баланс");live==0.0->l.t("Получайте бонусы за приглашения","Earn rewards through referrals","Takliflar orqali bonus oling","Таклифлар орқали бонус олинг");else->l.t("Доступно для бронирования","Available for booking","Bron qilish uchun mavjud","Брон қилиш учун мавжуд")}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xffE3F5EB),Color(0xffC2E0D1)))).clickable{chrome.openRewards("balance")}.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text("iumrah Balance",Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=subdued);CupertinoIcon(CupertinoSymbol.Wallet,null,Modifier.size(24.dp),ink)}
        Text(amount(live),fontSize=32.sp,fontWeight=FontWeight.SemiBold,color=ink,maxLines=1);Text(detail,fontSize=15.sp,color=subdued);HorizontalDivider(color=ink.copy(alpha=.19f))
        Row(verticalAlignment=Alignment.CenterVertically){Text(l.t("История операций","Transaction history","Amallar tarixi","Амаллар тарихи"),Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.Medium,color=subdued);CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(16.dp),subdued)}
    }
}
@Composable internal fun HomeDashboardLink(symbol:CupertinoSymbol,title:String,subtitle:String,open:()->Unit,compact:Boolean=false){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape((if(compact)18 else 22).dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=open).padding((if(compact)12 else 16).dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy((if(compact)10 else 14).dp)){
    CupertinoIcon(symbol,null,Modifier.size((if(compact)18 else 26).dp),if(compact)MaterialTheme.colorScheme.onSurface else Color(0xff3D61A3));Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy((if(compact)2 else 4).dp)){Text(title,fontWeight=FontWeight.SemiBold,fontSize=(if(compact)15 else 17).sp);Text(subtitle,fontSize=(if(compact)12 else 15).sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(12.dp),MaterialTheme.colorScheme.onSurfaceVariant)
}}
@Composable internal fun HomeDashboardSaved(l:AppLanguage,chrome:AppChromeStore)=HomeDashboardLink(CupertinoSymbol.Bookmark,l.t("Сохранённые путешествия","Saved journeys","Saqlangan safarlar","Сақланган сафарлар"),l.t("Продолжите сборку и проверьте актуальную цену","Resume planning and check the latest price","Tuzishni davom ettiring va narxni tekshiring","Тузишни давом эттиринг ва нархни текширинг"),chrome::openHomeSavedJourneys)

private enum class Tone(val first:Long,val second:Long,val ink:Long){BLUE(0xffE6F0FF,0xffCCDEFA,0xff244D8C),GOLD(0xffFCF2E0,0xffF0DEC0,0xff734D1F),SLATE(0xffF0F2F7,0xffD4DEEB,0xff405470),GREEN(0xffE8F7ED,0xffC7E3D4,0xff1F5C3D),VIOLET(0xffF5EDFC,0xffDECFF2,0xff573885)}
private data class Tile(val symbol:CupertinoSymbol,val title:String,val subtitle:String,val tone:Tone,val open:()->Unit)
@Composable private fun ServiceTile(tile:Tile,modifier:Modifier=Modifier){val ink=Color(tile.tone.ink);Column(modifier.heightIn(min=130.dp).clip(RoundedCornerShape(23.dp)).background(Brush.linearGradient(listOf(Color(tile.tone.first),Color(tile.tone.second)))).clickable(onClick=tile.open).padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){CupertinoIcon(tile.symbol,null,Modifier.size(26.dp),ink);Spacer(Modifier.weight(1f));CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(12.dp),ink)};Spacer(Modifier.height(3.dp));Text(tile.title,fontSize=17.sp,fontWeight=FontWeight.Bold,color=ink);Text(tile.subtitle,fontSize=15.sp,color=ink.copy(alpha=.83f))
}}
@Composable private fun DashboardGrid(tiles:List<Tile>){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){if(LocalDensity.current.fontScale>=1.3f){tiles.forEach{ServiceTile(it,Modifier.fillMaxWidth())}}else tiles.chunked(2).forEach{pair->Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)){pair.forEach{ServiceTile(it,Modifier.weight(1f).fillMaxHeight())};if(pair.size==1)Spacer(Modifier.weight(1f))}}}}
@Composable private fun DashboardPhoto(image:Int,title:String,subtitle:String,action:String,open:()->Unit){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=open)){
    Image(painterResource(image),null,Modifier.fillMaxWidth().height(172.dp),contentScale=ContentScale.Crop);Column(Modifier.fillMaxWidth().padding(19.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold);Text(subtitle,fontSize=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Row(Modifier.padding(top=4.dp),verticalAlignment=Alignment.CenterVertically){Text(action,Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.SemiBold);CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(16.dp))}}
}}
@Composable internal fun HomeDashboardPlanning(l:AppLanguage,chrome:AppChromeStore,focus:()->Unit,smart:()->Unit){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
    DashboardTitle(l.t("Организуйте свою умру","Plan your Umrah","Umrangizni rejalashtiring","Умрангизни режалаштиринг"));DashboardPhoto(R.drawable.iumrah_makkah_background,l.t("Ваша умра. Ваш выбор.","Your Umrah. Your choice.","Umrangiz. Tanlovingiz.","Умрангиз. Танловингиз."),l.t("Соберите индивидуальное путешествие для своей семьи.","Build a private journey for your family.","Oilangiz uchun individual safar tuzing.","Оилангиз учун индивидуал сафар тузинг."),l.t("Собрать умру","Build your Umrah","Umra tuzish","Умра тузиш"),focus)
    DashboardGrid(listOf(Tile(CupertinoSymbol.Sliders,l.t("Ручной подбор","Manual planner","Qo‘lda tanlash","Қўлда танлаш"),l.t("Полная свобода выбора","Full control of your choices","To‘liq tanlov erkinligi","Тўлиқ танлов эркинлиги"),Tone.BLUE,chrome::openDiscoveryFlights),Tile(CupertinoSymbol.Sparkles,l.t("Smart подбор","Smart planner","Smart tanlov","Smart танлов"),l.t("Персональные варианты","Personalized options","Shaxsiy variantlar","Шахсий вариантлар"),Tone.VIOLET,smart)))
}}
@Composable internal fun HomeDashboardServices(l:AppLanguage,chrome:AppChromeStore){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
    DashboardTitle(l.t("Всё для вашего путешествия","Everything for your journey","Safaringiz uchun hamma narsa","Сафарингиз учун ҳамма нарса"));DashboardGrid(listOf(
        Tile(CupertinoSymbol.Airplane,l.t("Авиабилеты","Flights","Aviachiptalar","Авиачипталар"),l.t("Прямые рейсы","Direct flights","To‘g‘ridan-to‘g‘ri reyslar","Тўғридан-тўғри рейслар"),Tone.BLUE,chrome::openDiscoveryFlights),
        Tile(CupertinoSymbol.Bed,l.t("Отели","Hotels","Mehmonxonalar","Меҳмонхоналар"),l.t("Мекка и Медина","Makkah and Madinah","Makka va Madina","Макка ва Мадина"),Tone.GOLD,{chrome.navigate(AppTab.HOTELS)}),
        Tile(CupertinoSymbol.Car,l.t("Трансфер","Transfer","Transfer","Трансфер"),l.t("Личный автомобиль","Private vehicle","Shaxsiy avtomobil","Шахсий автомобиль"),Tone.SLATE,chrome::openDiscoveryTransfer),
        Tile(CupertinoSymbol.Persons,l.t("Персональный гид","Personal guide","Shaxsiy gid","Шахсий гид"),l.t("Один гид на семью","One guide per family","Har oilaga bitta gid","Ҳар оилага битта гид"),Tone.GREEN,chrome::openDiscoveryGuides),
        Tile(CupertinoSymbol.Map,l.t("Зияраты","Ziyarats","Ziyoratlar","Зиёратлар"),l.t("Исторические места","Historic places","Tarixiy joylar","Тарихий жойлар"),Tone.GOLD,chrome::openZiyarats),
        Tile(CupertinoSymbol.SimCard,"iumrah Mobile",l.t("Оставайтесь на связи","Stay connected","Aloqada qoling","Алоқада қолинг"),Tone.VIOLET,chrome::openESIM)))
}}
@Composable internal fun HomeDashboardGuides(l:AppLanguage,chrome:AppChromeStore){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
    DashboardTitle(l.t("Персональное сопровождение","Personal accompaniment","Shaxsiy hamrohlik","Шахсий ҳамроҳлик"));DashboardPhoto(R.drawable.ziyarat_quba_1,l.t("Путешествуйте самостоятельно, но не в одиночку.","Travel independently, but not alone.","Mustaqil sayohat qiling, ammo yolg‘iz emas.","Мустақил саёҳат қилинг, аммо ёлғиз эмас."),l.t("Персональный гид: встреча, подготовка и сопровождение по программе поездки.","Personal guide: arrival, preparation and accompaniment according to your itinerary.","Shaxsiy gid: kutib olish, tayyorgarlik va safar dasturida hamrohlik.","Шахсий гид: кутиб олиш, тайёргарлик ва сафар дастурида ҳамроҳлик."),l.t("Познакомиться с гидами","Meet the guides","Gidlar bilan tanishing","Гидлар билан танишинг"),chrome::openDiscoveryGuides)
}}
@Composable internal fun HomeDashboardCare(l:AppLanguage,chrome:AppChromeStore)=ServiceTile(Tile(CupertinoSymbol.Phone,"iumrah Care",l.t("Мы рядом на протяжении вашего путешествия. Связаться с поддержкой","We're with you throughout your journey. Contact support","Safaringiz davomida yoningizdamiz. Yordamga murojaat qiling","Сафарингиз давомида ёнингиздамиз. Ёрдамга мурожаат қилинг"),Tone.GREEN,{chrome.navigate(AppTab.CARE)}),Modifier.fillMaxWidth())
@Composable internal fun HomeDashboardIncluded(l:AppLanguage,chrome:AppChromeStore){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
    DashboardTitle(l.t("Что входит в пакет?","What's in your package?","Paketda nimalar bor?","Пакетда нималар бор?"));Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(17.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        Text(l.t("Выбирайте услуги самостоятельно — итоговый состав и стоимость видны перед бронированием.","Choose your services. See the final inclusions and prices before booking.","Xizmatlarni o‘zingiz tanlang. Tarkib va narx bron qilishdan oldin ko‘rsatiladi.","Хизматларни ўзингиз танланг. Таркиб ва нарх брон қилишдан олдин кўрсатилади."),fontSize=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val entries=listOf(Triple("visa",CupertinoSymbol.Passport,l.t("Виза","Visa","Viza","Виза")),Triple("flights",CupertinoSymbol.Airplane,l.t("Авиабилеты","Flights","Aviachiptalar","Авиачипталар")),Triple("hotels",CupertinoSymbol.Bed,l.t("Отели","Hotels","Mehmonxonalar","Меҳмонхоналар")),Triple("transfer",CupertinoSymbol.Car,l.t("Трансфер","Transfer","Transfer","Трансфер")),Triple("guide",CupertinoSymbol.Persons,l.t("Личный гид","Private guide","Shaxsiy gid","Шахсий гид")),Triple("ziyarat",CupertinoSymbol.Map,l.t("Зияраты","Ziyarats","Ziyoratlar","Зиёратлар")))
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){entries.chunked(2).forEach{pair->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){pair.forEach{(id,symbol,label)->Row(Modifier.weight(1f).heightIn(min=54.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable{chrome.openIumrahServices(id)}.padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){CupertinoIcon(symbol,null,Modifier.size(25.dp));Text(label,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2)}}}}}
        Row(Modifier.fillMaxWidth().heightIn(min=44.dp).clickable{chrome.openIumrahServices()},verticalAlignment=Alignment.CenterVertically){Text(l.t("Все услуги","All services","Barcha xizmatlar","Барча хизматлар"),Modifier.weight(1f),fontWeight=FontWeight.SemiBold);CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(16.dp))}
    }
}}
@Composable internal fun HomeDashboardBenefits(l:AppLanguage,chrome:AppChromeStore){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
    DashboardTitle(l.t("Бонусы iumrah","iumrah benefits","iumrah bonuslari","iumrah бонуслари"));Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal=15.dp)){
        BenefitRow(CupertinoSymbol.Wallet,"iumrah Balance",l.t("Баланс и история начислений","Balance and reward history","Balans va bonuslar tarixi","Баланс ва бонуслар тарихи")){chrome.openRewards("balance")};HorizontalDivider(Modifier.padding(start=49.dp));BenefitRow(CupertinoSymbol.Star,"iumrah Rewards",l.t("Преимущества и уровни программы","Rewards and membership levels","Afzalliklar va dastur darajalari","Афзалликлар ва дастур даражалари")){chrome.openRewards()};HorizontalDivider(Modifier.padding(start=49.dp));BenefitRow(CupertinoSymbol.Gift,l.t("Пригласите друга","Invite a friend","Do‘stingizni taklif qiling","Дўстингизни таклиф қилинг"),l.t("Бонусы по условиям программы","Rewards subject to program terms","Dastur shartlariga muvofiq bonuslar","Дастур шартларига мувофиқ бонуслар")){chrome.openRewards("friends")}
    }
}}
@Composable private fun BenefitRow(symbol:CupertinoSymbol,title:String,subtitle:String,open:()->Unit){Row(Modifier.fillMaxWidth().heightIn(min=66.dp).clickable(onClick=open),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.size(34.dp,38.dp),contentAlignment=Alignment.Center){CupertinoIcon(symbol,null,Modifier.size(18.dp))};Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,fontSize=15.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)};CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(12.dp),MaterialTheme.colorScheme.onSurfaceVariant)}}
