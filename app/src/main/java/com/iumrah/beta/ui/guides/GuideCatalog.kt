package com.iumrah.beta.ui.guides

import android.app.DatePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
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
import coil3.compose.AsyncImage
import com.iumrah.beta.IumrahApplication
import com.iumrah.beta.R
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.chat.IumrahPublicProfile
import com.iumrah.beta.domain.trip.*
import com.iumrah.beta.ui.components.*
import com.iumrah.beta.ui.cupertino.*
import com.iumrah.beta.ui.hotels.HotelsBoard
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

internal fun guideCopy(l: AppLanguage, r: String, e: String, u: String, c: String) = when(l) {
    AppLanguage.RUSSIAN -> r; AppLanguage.UZBEK -> u; AppLanguage.UZBEK_CYRILLIC -> c; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> e
}

@Composable private fun GuideSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp), content=content)
}

@Composable internal fun GuidePreferenceCard(l: AppLanguage, value: IumrahGuidePreference?, open: () -> Unit) {
    GuideSurface {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){CupertinoIcon(CupertinoSymbol.PersonCircle,null,Modifier.size(20.dp));Text(guideCopy(l,"Персональный гид","Personal guide","Shaxsiy gid","Шахсий гид"), fontWeight=FontWeight.SemiBold)}
        Text(value?.displayName ?: guideCopy(l,"Подбор от iumrah","Selected by iumrah","iumrah tanlovi","iumrah танлови"), fontSize=22.sp,fontWeight=FontWeight.Bold)
        Text(guideCopy(l,"Сопровождение включено в расчёт пакета. Назначение подтверждается после проверки дат.","Accompaniment is included in the package quote. Assignment is confirmed after checking dates.","Hamrohlik paket hisobiga kiradi. Gid sanalar tekshirilgach tasdiqlanadi.","Ҳамроҳлик пакет ҳисобига киради. Гид саналар текширилгач тасдиқланади."),color=MaterialTheme.colorScheme.onSurfaceVariant)
        IumrahSecondaryButton(guideCopy(l,"Выбрать гида","Choose guide","Gid tanlash","Гид танлаш"),onClick=open)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GuidePickerSheet(l: AppLanguage, trip: TripDraft, price: String?, onDismiss: () -> Unit, onSelect: (IumrahGuidePreference?) -> Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true), containerColor=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=18.dp).navigationBarsPadding().padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(guideCopy(l,"Гиды","Guides","Gidlar","Гидлар"),Modifier.weight(1f),fontSize=24.sp,fontWeight=FontWeight.Bold); TextButton(onDismiss){Text(guideCopy(l,"Закрыть","Close","Yopish","Ёпиш"))} }
            GuideCatalog(l,trip,price,false,onSelect,{})
        }
    }
}

@Composable internal fun ManualGuideDirectory(l: AppLanguage, chrome: AppChromeStore, unread: Int, onBoard: (HotelsBoard)->Unit) {
    val manual=(LocalContext.current.applicationContext as IumrahApplication).container.manualJourney
    val state by manual.state.collectAsState(); val trip=state.trip ?: state.plan
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        IumrahRootPageHeader(guideCopy(l,"Гиды и команда","Guides & team","Gidlar va jamoa","Гидлар ва жамоа"),chrome,unreadCount=unread,usesBrandLogo=true,discovery=true,language=l,footer={ IumrahDiscoveryPills(l,4,includeGuides=true){i-> when(i){0->chrome.openDiscoveryBuild();1->onBoard(HotelsBoard.HOTELS);2->onBoard(HotelsBoard.FLIGHTS);3->chrome.openDiscoveryTransfer();5->chrome.openDiscoveryPackage();else->Unit} } })
        Column(Modifier.padding(horizontal=18.dp).padding(bottom=130.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            GuideSurface {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xff242426)).clickable { expanded=!expanded }.padding(16.dp)) { Text(guideCopy(l,"Ваш персональный гид","Your personal guide","Shaxsiy gidingiz","Шахсий гидингиз"),color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp); Text("${trip.hotelStayStartDate} — ${trip.returnDate}",color=Color.White.copy(alpha=.75f)) }
                if(expanded) {
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { JourneyScope.entries.forEach { v -> FilterChip(selected=trip.scope==v,onClick={manual.update(state){old->old.copy(plan=old.plan.copy(scope=v,arrivalAirport=if(v==JourneyScope.MAKKAH_ONLY) SaudiArrivalAirport.JEDDAH else old.plan.arrivalAirport,selectedReturnAirport=null),rooms=old.rooms.filterKeys{it!="madinah"||v==JourneyScope.MAKKAH_AND_MADINAH},outbound=old.outbound?.takeIf{v!=JourneyScope.MAKKAH_ONLY||it.destination=="JED"},inbound=old.inbound?.takeIf{v!=JourneyScope.MAKKAH_ONLY||it.origin=="JED"},train=old.train&&v==JourneyScope.MAKKAH_AND_MADINAH,routeConfigured=true)}},label={Text(if(v==JourneyScope.MAKKAH_ONLY)guideCopy(l,"Только Мекка","Makkah only","Faqat Makka","Фақат Макка") else guideCopy(l,"Мекка + Медина","Makkah + Madinah","Makka + Madina","Макка + Мадина"))}) } }
                    GuideDate(l,guideCopy(l,"Начало поездки","Trip start","Safar boshlanishi","Сафар бошланиши"),trip.departureDate,LocalDate.now(),state.outbound==null) { d -> manual.update(state){it.copy(plan=it.plan.copy(departureDate=d,saudiArrivalDate=null,returnDate=if(it.plan.returnDate<=d&&it.inbound==null)d.plusDays(7) else it.plan.returnDate),routeConfigured=true)} }
                    GuideDate(l,guideCopy(l,"Окончание поездки","Trip end","Safar tugashi","Сафар тугаши"),trip.returnDate,trip.departureDate.plusDays(1),state.inbound==null) { d -> manual.update(state){it.copy(plan=it.plan.copy(returnDate=d),routeConfigured=true)} }
                    Row(verticalAlignment=Alignment.CenterVertically){Text(guideCopy(l,"Взрослых","Adults","Kattalar","Катталар")+": ${trip.adults}",Modifier.weight(1f)); TextButton(enabled=trip.adults>1,onClick={manual.update(state){it.copy(plan=it.plan.copy(adults=trip.adults-1),routeConfigured=true)}}){Text("−")}; TextButton(enabled=trip.adults<6,onClick={manual.update(state){it.copy(plan=it.plan.copy(adults=trip.adults+1),routeConfigured=true)}}){Text("+")} }
                    IumrahPrimaryButton(guideCopy(l,"Применить","Apply","Qo‘llash","Қўллаш")){expanded=false}
                }
            }
            GuideCatalog(l,trip,null,true,{v->manual.update(state){it.copy(plan=it.plan.copy(guidePreference=v))};chrome.finishDiscoveryGuide()},{chrome.navigate(AppTab.CARE)})
        }
    }
}

@Composable private fun GuideDate(l: AppLanguage, label: String, date: LocalDate, min: LocalDate, enabled: Boolean, select: (LocalDate)->Unit) {
    val context=LocalContext.current
    OutlinedButton(onClick={DatePickerDialog(context,{_,y,m,d-> val selected=LocalDate.of(y,m+1,d);if(selected>=min)select(selected)},date.year,date.monthValue-1,date.dayOfMonth).apply { datePicker.minDate=min.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.show()},enabled=enabled,modifier=Modifier.fillMaxWidth()) { Text("$label · $date") }
}

@Composable private fun GuideCatalog(l: AppLanguage, trip: TripDraft, price: String?, team: Boolean, onSelect: (IumrahGuidePreference?)->Unit, care: ()->Unit) {
    val service=(LocalContext.current.applicationContext as IumrahApplication).container.chatService
    val start=trip.hotelStayStartDate; val end=trip.returnDate
    var revision by remember { mutableIntStateOf(0) }
    var profiles by remember(start,end,revision) { mutableStateOf<List<IumrahPublicProfile>>(emptyList()) }
    var loading by remember(start,end,revision) { mutableStateOf(true) }; var failed by remember(start,end,revision) { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }; var details by remember(start,end,revision) { mutableStateOf<String?>(null) }
    LaunchedEffect(start,end,revision) {
        try { profiles=service.loadGuideProfiles(start,end,team) }
        catch(c: CancellationException){throw c} catch(e:Exception){failed=true} finally {loading=false}
    }
    val guides=profiles.filter { !it.isOwner && it.roleKind.equals("guide",true) }.sortedByDescending{it.isHeadGuide==true}
    fun choose(p:IumrahPublicProfile){ if(!loading&&!failed&&guides.any{it.id==p.id&&it.guideAvailable==true})onSelect(IumrahGuidePreference(p.id,p.displayName)) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color(0xff131313))) {
        Image(painterResource(R.drawable.about_iumrah_kaaba_corner),null,Modifier.fillMaxWidth().height(210.dp),contentScale=ContentScale.Crop)
        Column(Modifier.padding(23.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
            Text("IUMRAH  ·  PERSONAL GUIDE",color=Color.White.copy(alpha=.64f),fontSize=11.sp,letterSpacing=2.sp)
            Text(guideCopy(l,"Ваша умра.\nВаш персональный гид.","Your Umrah.\nYour personal guide.","Umrangiz.\nShaxsiy gidingiz.","Умрангиз.\nШахсий гидингиз."),color=Color.White,fontSize=32.sp,fontWeight=FontWeight.Bold)
            Text(guideCopy(l,"От встречи в аэропорту до завершения путешествия. Больше времени для поклонения и близких.","From the airport welcome to the end of your journey. More time for worship and loved ones.","Aeroportdagi uchrashuvdan safar yakunigacha. Ibodat va yaqinlaringiz uchun ko‘proq vaqt.","Аэропортдаги учрашувдан сафар якунигача. Ибодат ва яқинларингиз учун кўпроқ вақт."),color=Color.White.copy(alpha=.76f))
            HorizontalDivider(color=Color.White.copy(alpha=.2f))
            listOf(guideCopy(l,"Один гид на семью","One guide per family","Oilaga bitta gid","Оилага битта гид"),guideCopy(l,"Встреча в аэропорту","Airport welcome","Aeroportda kutib olish","Аэропортда кутиб олиш"),guideCopy(l,"Обучение умре","Umrah preparation","Umraga tayyorgarlik","Умрага тайёргарлик"),guideCopy(l,"Личная забота","Personal care","Shaxsiy g‘amxo‘rlik","Шахсий ғамхўрлик")).chunked(2).forEach { row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{Text(it,Modifier.weight(1f),color=Color.White,fontWeight=FontWeight.Medium)}} }
        }
    }
    Text(guideCopy(l,"Выберите своего гида","Choose your guide","Gidingizni tanlang","Гидингизни танланг"),fontSize=28.sp,fontWeight=FontWeight.Bold)
    Text(guideCopy(l,"Познакомьтесь с командой до вылета. Один гид для вашей семьи.","Meet the team before departure. One guide for your family.","Parvozdan oldin jamoa bilan tanishing. Oilangiz uchun bitta gid.","Парвоздан олдин жамоа билан танишинг. Оилангиз учун битта гид."),color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(loading) Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}
    else if(failed) GuideSurface {Text(guideCopy(l,"Не удалось загрузить гидов","Could not load guides","Gidlar yuklanmadi","Гидлар юкланмади"));OutlinedButton({revision++}){Text(guideCopy(l,"Повторить","Retry","Qayta urinish","Қайта уриниш"))}}
    else {
        if(guides.size>3) OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),label={Text(guideCopy(l,"Имя или описание","Name or description","Ism yoki tavsif","Исм ёки тавсиф"))})
        if(guides.isEmpty())Text(guideCopy(l,"Нет опубликованных гидов","No published guides","E’lon qilingan gidlar yo‘q","Эълон қилинган гидлар йўқ"))
        val visible = guides.filter { listOf(it.displayName,it.localizedBio(l),it.roleTitle).any { s->s.contains(search.trim(),true) } }
        if (guides.isNotEmpty() && visible.isEmpty()) Text(guideCopy(l,"Ничего не найдено","No matches","Hech narsa topilmadi","Ҳеч нарса топилмади"),color=MaterialTheme.colorScheme.onSurfaceVariant)
        visible.forEach { p-> GuideProfile(l,p,trip,price, {details=p.id},{choose(p)}) }
    }
    GuideSurface {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.Persons,null,Modifier.size(22.dp))}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(guideCopy(l,"Доверить выбор iumrah","Let iumrah choose","Tanlovni iumrahga topshiring","Танловни iumrahга топширинг"),fontWeight=FontWeight.SemiBold);Text(guideCopy(l,"Подберём гида с учётом вашей поездки.","We will match a guide to your trip.","Safaringizga mos gid tanlaymiz.","Сафарингизга мос гид танлаймиз."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(trip.guidePreference==null)CupertinoIcon(CupertinoSymbol.CheckCircleFill,null,Modifier.size(20.dp))
        }
        IumrahSecondaryButton(guideCopy(l,"Доверить подбор","Let iumrah select","Tanlovni topshirish","Танловни топшириш")){onSelect(null)}
    }
    if(team) profiles.filter { it.isOwner || !it.roleKind.equals("guide",true) }.sortedByDescending{it.isOwner}.forEachIndexed { index,p ->
        if (p.isOwner || index==0 || profiles.filter { it.isOwner || !it.roleKind.equals("guide",true) }.sortedByDescending{it.isOwner}.getOrNull(index-1)?.isOwner==true) Text(if(p.isOwner) guideCopy(l,"Основатель проекта","Founder","Loyiha asoschisi","Лойиҳа асосчиси") else guideCopy(l,"Команда iumrah","iumrah team","iumrah jamoasi","iumrah жамоаси"),fontSize=24.sp,fontWeight=FontWeight.Bold)
        GuideSurface { Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){GuideAvatar(p,68);Text(p.displayName,fontSize=20.sp,fontWeight=FontWeight.Bold)};Text((p.publicRoles ?: listOf(if(p.isOwner)"founder" else p.roleKind)).joinToString(" · "){publicRole(l,it)},fontWeight=FontWeight.SemiBold);Text(p.localizedBio(l));OutlinedButton(care,Modifier.fillMaxWidth()){Text(guideCopy(l,"Проконсультироваться через iumrah Care","Consult through iumrah Care","iumrah Care orqali maslahat olish","iumrah Care орқали маслаҳат олиш"))} } }
    if(team && profiles.any{it.isOwner||!it.roleKind.equals("guide",true)})GuideSurface{Text(guideCopy(l,"Личные контакты назначенного гида доступны после подтверждения оплаты.","Assigned guide contacts are available after payment confirmation.","Biriktirilgan gid kontaktlari to‘lov tasdiqlangach ochiladi.","Бириктирилган гид контактлари тўлов тасдиқлангач очилади."))}
    GuideProgram(l,trip)
    GuideSurface {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){CupertinoIcon(CupertinoSymbol.ShieldCheck,null,Modifier.size(22.dp));Text(guideCopy(l,"Прозрачная стоимость","Transparent pricing","Shaffof narx","Шаффоф нарх"),fontSize=20.sp,fontWeight=FontWeight.Bold)}
        price?.takeIf{it!="—"}?.let{Text(it,fontSize=34.sp,fontWeight=FontWeight.Bold)}
        Text(guideCopy(l,"Стоимость персонального сопровождения учитывается в общей цене пакета. Выбор конкретного гида не добавляет вторую оплату.","Personal accompaniment is included in the package total. Selecting a particular guide does not add a second charge.","Shaxsiy hamrohlik paket narxiga kiradi. Muayyan gidni tanlash ikki marta to‘lov qilmaydi.","Шахсий ҳамроҳлик пакет нархига киради. Муайян гидни танлаш икки марта тўлов қилмайди."),fontSize=15.sp)
        HorizontalDivider();Text(guideCopy(l,"Дополнительные поездки, транспорт и расходы вне программы согласовываются отдельно. Доступность гида подтверждает команда iumrah.","Extra trips, transport and out-of-program expenses are arranged separately. The iumrah team confirms guide availability.","Qo‘shimcha safarlar, transport va dasturdan tashqari xarajatlar alohida kelishiladi. Gid mavjudligini iumrah tasdiqlaydi.","Қўшимча сафарлар, транспорт ва дастурдан ташқари харажатлар алоҳида келишилади. Гид мавжудлигини iumrah тасдиқлайди."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.Black).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("THE IUMRAH EXPERIENCE",fontSize=11.sp,letterSpacing=2.sp,color=Color.White.copy(alpha=.6f));Text(guideCopy(l,"Одна семья. Один персональный гид.","One family. One personal guide.","Bir oila. Bir shaxsiy gid.","Бир оила. Бир шахсий гид."),fontSize=27.sp,fontWeight=FontWeight.Bold,color=Color.White);Text(guideCopy(l,"Путешествуйте самостоятельно, но не в одиночку.","Travel independently, but not alone.","Mustaqil sayohat qiling, ammo yolg‘iz emas.","Мустақил саёҳат қилинг, аммо ёлғиз эмас."),color=Color.White.copy(alpha=.75f));HorizontalDivider(color=Color.White.copy(alpha=.2f));Text(guideCopy(l,"Ближе к своим близким. Ближе к поклонению.","Closer to loved ones. Closer to worship.","Yaqinlaringizga yaqinroq. Ibodatga yaqinroq.","Яқинларингизга яқинроқ. Ибодатга яқинроқ."),color=Color.White,fontWeight=FontWeight.SemiBold)
    }
    details?.let { id->guides.firstOrNull{it.id==id}?.let { p -> GuideDetail(l,p,trip,price,{details=null},{details=null;choose(p)}) } }
}

@Composable private fun GuideProfile(l:AppLanguage,p:IumrahPublicProfile,trip:TripDraft,price:String?,detail:()->Unit,choose:()->Unit){GuideSurface{
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){GuideAvatar(p,68);Column(Modifier.weight(1f)){ProfileIdentity(l,p)}}
    Text(p.localizedBio(l),maxLines=3,color=MaterialTheme.colorScheme.onSurfaceVariant);GuideAvailability(l,p)
    price?.takeIf{it!="—"}?.let{Text(it,fontWeight=FontWeight.Bold)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick=detail,modifier=Modifier.weight(1f)){Text(guideCopy(l,"Подробнее","View profile","Batafsil","Батафсил"))}
        if(p.guideAvailable==true) Button(onClick=choose,modifier=Modifier.weight(1f)){Text(if(trip.guidePreference?.memberID==p.id)guideCopy(l,"Выбран","Selected","Tanlangan","Танланган")else guideCopy(l,"Выбрать гида","Choose guide","Gid tanlash","Гид танлаш"))}
    }
}}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GuideDetail(l:AppLanguage,p:IumrahPublicProfile,trip:TripDraft,price:String?,dismiss:()->Unit,choose:()->Unit){
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max=780.dp)) {
            Row(Modifier.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) { Text(guideCopy(l,"Профиль гида","Guide profile","Gid profili","Гид профили"),Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=20.sp);TextButton(dismiss){Text(guideCopy(l,"Закрыть","Close","Yopish","Ёпиш"))} }
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                GuideSurface {Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically){GuideAvatar(p,80);Column(Modifier.weight(1f)){ProfileIdentity(l,p)}};GuideAvailability(l,p);if(p.localizedBio(l).isNotBlank()){HorizontalDivider();Text(guideCopy(l,"О гиде","About the guide","Gid haqida","Гид ҳақида"),fontWeight=FontWeight.Bold);Text(p.localizedBio(l))};AppConfig.absoluteUrl(p.introVideoURL)?.takeIf{it.startsWith("https://")}?.let{url->HorizontalDivider();Text(guideCopy(l,"Знакомство с гидом","Meet your guide","Gid bilan tanishuv","Гид билан танишув"),fontWeight=FontWeight.Bold);GuideIntroVideo(url)} }
                GuideProgram(l,trip)
                if(!p.guideReviews.isNullOrEmpty())GuideSurface{Text(guideCopy(l,"Отзывы паломников","Pilgrim reviews","Ziyoratchilar fikri","Зиёратчилар фикри"),fontWeight=FontWeight.Bold);p.guideReviews.forEach{r->Text(r.author,fontWeight=FontWeight.Bold);Text(r.text);HorizontalDivider()}}
                Text(guideCopy(l,"Конкретное назначение подтвердит команда iumrah после проверки дат. Дополнительные расходы согласовываются заранее.","The iumrah team confirms assignment after checking dates. Additional expenses are agreed in advance.","Gid sanalar tekshirilgach tasdiqlanadi. Qo‘shimcha xarajatlar oldindan kelishiladi.","Гид саналар текширилгач тасдиқланади. Қўшимча харажатлар олдиндан келишилади."),color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.fillMaxWidth().padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){price?.takeIf{it!="—"}?.let{Text(it+" · "+guideCopy(l,"в пакете","in package","paketda","пакетда"),fontWeight=FontWeight.SemiBold)};IumrahPrimaryButton(if(trip.guidePreference?.memberID==p.id)guideCopy(l,"Выбран","Selected","Tanlangan","Танланган") else guideCopy(l,"Добавить в мой пакет","Add to my package","Paketimga qo‘shish","Пакетимга қўшиш"),enabled=p.guideAvailable==true,onClick=choose)}
        }
    }
}

@Composable private fun GuidePortrait(p:IumrahPublicProfile,height:Int,open:()->Unit){
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick=open)){
        val photo=AppConfig.absoluteUrl(p.photoURL)?.takeIf{it.startsWith("https://")}
        if(photo!=null)AsyncImage(photo,p.displayName,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Text(p.displayName.take(1).uppercase(),Modifier.align(Alignment.Center),fontSize=100.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.15f))
        Text(if(p.roleKind.equals("guide",true)&&!p.isOwner)"iumrah · GUIDE" else "iumrah · TEAM",Modifier.align(Alignment.BottomStart).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha=.72f)).padding(horizontal=12.dp,vertical=7.dp),fontSize=11.sp,fontWeight=FontWeight.Bold,color=Color.White,letterSpacing=1.2.sp)
    }
}
@Composable private fun ProfileIdentity(l:AppLanguage,p:IumrahPublicProfile){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){Text(p.displayName,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f,fill=false));CupertinoIcon(CupertinoSymbol.CheckCircleFill,null,Modifier.size(20.dp),Color(0xff007aff))}
    Text(if(p.isHeadGuide==true)guideCopy(l,"Главный гид iumrah","Head guide","iumrah bosh gidi","iumrah бош гиди")else guideCopy(l,"Персональный гид iumrah","Personal guide","iumrah shaxsiy gidi","iumrah шахсий гиди"),color=MaterialTheme.colorScheme.onSurfaceVariant)
    p.experienceYears?.takeIf{it>0}?.let{y->Text(guideCopy(l,"Опыт: $y г.","Experience: $y years","Tajriba: $y yil","Тажриба: $y йил"),fontSize=13.sp)}
    p.languages?.takeIf{it.isNotEmpty()}?.let{Text(it.joinToString(" · "),fontSize=13.sp)}
}}
private fun publicRole(l:AppLanguage,role:String)=when(role){
    "founder","owner"->guideCopy(l,"Основатель iumrah","Founder","iumrah asoschisi","iumrah асосчиси")
    "admin"->guideCopy(l,"Администратор","Administrator","Administrator","Администратор")
    "support"->guideCopy(l,"Специалист поддержки","Care specialist","Yordam mutaxassisi","Ёрдам мутахассиси")
    "manager"->guideCopy(l,"Менеджер путешествий","Travel manager","Safar menejeri","Сафар менежери")
    "transfer"->guideCopy(l,"Координатор трансфера","Transfer coordinator","Transfer koordinatori","Трансфер координатори")
    "guide"->guideCopy(l,"Персональный гид","Personal guide","Shaxsiy gid","Шахсий гид")
    else->guideCopy(l,"Организация поездок","Operations","Safarni tashkil etish","Сафарни ташкил этиш")
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun GuideIntroVideo(url:String){
    val context=LocalContext.current
    val player=remember(url){androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply{setMediaItem(androidx.media3.common.MediaItem.fromUri(url));prepare();playWhenReady=false}}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player,lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,_->if(!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))player.pause()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);player.release()}}
    androidx.compose.ui.viewinterop.AndroidView(factory={ctx->androidx.media3.ui.PlayerView(ctx).apply{this.player=player;useController=true}},update={it.player=player},modifier=Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(16.dp)))
}

private data class GuideStep(val icon:CupertinoSymbol,val title:String,val description:String,val asset:Int?)
@Composable private fun GuideProgram(l:AppLanguage,trip:TripDraft){
    val steps=listOf(
        GuideStep(CupertinoSymbol.AirplaneLand,guideCopy(l,"Встреча в аэропорту", "Airport welcome", "Aeroportda kutib olish", "Аэропортда кутиб олиш"),guideCopy(l,"Гид встречает семью, помогает сориентироваться и координирует трансфер до отеля.", "Your guide meets the family, helps you find your way and coordinates the transfer to the hotel.", "Gid oilani kutib oladi va mehmonxonaga transferni muvofiqlashtiradi.", "Гид оилани кутиб олади ва меҳмонхонага трансферни мувофиқлаштиради."),null),
        GuideStep(CupertinoSymbol.Bed,guideCopy(l,"Размещение в отеле", "Hotel check-in", "Mehmonxonaga joylashish", "Меҳмонхонага жойлашиш"),guideCopy(l,"Помощь с заселением, общением с персоналом и организационными вопросами.", "Help with check-in, communication with hotel staff and practical arrangements.", "Joylashish, xodimlar bilan muloqot va tashkiliy masalalarda yordam.", "Жойлашиш, ходимлар билан мулоқот ва ташкилий масалаларда ёрдам."),R.drawable.iumrah_friends_hotels_cover),
        GuideStep(CupertinoSymbol.Book,guideCopy(l,"Подготовка к умре", "Umrah preparation", "Umraga tayyorgarlik", "Умрага тайёргарлик"),guideCopy(l,"Объясняет правила ихрама, намерение, последовательность обрядов и необходимые знания до их совершения.", "Explains ihram, intention, the sequence of rituals and essential knowledge before performing them.", "Ehrom, niyat, amallar ketma-ketligi va zarur bilimlarni tushuntiradi.", "Эҳром, ният, амаллар кетма-кетлиги ва зарур билимларни тушунтиради."),null),
        GuideStep(CupertinoSymbol.Persons,guideCopy(l,"Совершение умры", "Performing Umrah", "Umra amallari", "Умра амаллари"),guideCopy(l,"Сопровождает при тавафе, са‘и и завершении обрядов в темпе вашей семьи.", "Accompanies you through tawaf, sa‘i and completion of rituals at your family’s pace.", "Tavof, sa’y va amallar yakunida oilangizga mos sur’atda hamrohlik qiladi.", "Тавоф, саъй ва амаллар якунида оилангизга мос суръатда ҳамроҳлик қилади."),R.drawable.about_iumrah_pilgrims),
        GuideStep(CupertinoSymbol.Map,guideCopy(l,"Зияраты и святые места", "Ziyarats and sacred sites", "Ziyoratlar va muqaddas joylar", "Зиёратлар ва муқаддас жойлар"),if(trip.scope==JourneyScope.MAKKAH_AND_MADINAH)guideCopy(l,"Знакомство с историческими местами Мекки и Медины по согласованной программе.", "Discover historic sites in Makkah and Madinah according to the agreed program.", "Kelishilgan dastur bo‘yicha Makka va Madina tarixiy joylari.", "Келишилган дастур бўйича Макка ва Мадина тарихий жойлари.") else guideCopy(l,"Исторические места Мекки по согласованной программе.", "Historic sites in Makkah according to the agreed program.", "Kelishilgan dastur bo‘yicha Makka tarixiy joylari.", "Келишилган дастур бўйича Макка тарихий жойлари."),if(trip.scope==JourneyScope.MAKKAH_AND_MADINAH)R.drawable.ziyarat_quba_1 else R.drawable.about_iumrah_kaaba_corner),
        GuideStep(CupertinoSymbol.Heart,guideCopy(l,"Забота до отъезда", "Care until departure", "Jo‘nashgacha g‘amxo‘rlik", "Жўнашгача ғамхўрлик"),guideCopy(l,"Помощь с бытовыми вопросами, рекомендациями, дополнительными поездками и координацией отъезда.", "Help with everyday needs, recommendations, extra trips and departure coordination.", "Kundalik masalalar, tavsiyalar, qo‘shimcha safarlar va jo‘nashda yordam.", "Кундалик масалалар, тавсиялар, қўшимча сафарлар ва жўнашда ёрдам."),null)
    )
    GuideSurface {
        Text(guideCopy(l,"Что входит в сопровождение","What accompaniment includes","Hamrohlikka nimalar kiradi","Ҳамроҳликка нималар киради"),fontSize=24.sp,fontWeight=FontWeight.Bold)
        Text(guideCopy(l,"Ваша поездка — шаг за шагом","Your journey, step by step","Safaringiz qadamma-qadam","Сафарингиз қадамма-қадам"),color=MaterialTheme.colorScheme.onSurfaceVariant)
        steps.forEachIndexed{i,step->Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
                Box(Modifier.size(width=52.dp,height=52.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){CupertinoIcon(step.icon,null,Modifier.size(28.dp))}
                if(i<steps.lastIndex)Box(Modifier.width(1.dp).height(18.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.18f)))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(guideCopy(l,"ЭТАП","STEP","BOSQICH","БОСҚИЧ")+" "+(i+1).toString().padStart(2,'0'),fontSize=11.sp,letterSpacing=1.3.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(step.title,fontWeight=FontWeight.Bold);Text(step.description,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        };if(i<steps.lastIndex)HorizontalDivider()}
    }
}

@Composable private fun GuideAvailability(l:AppLanguage,p:IumrahPublicProfile){
    val value=when {
        p.availabilityStatus=="unavailable" -> guideCopy(l,"Временно недоступен","Temporarily unavailable","Vaqtincha mavjud emas","Вақтинча мавжуд эмас")
        p.availabilityStatus=="busy" -> guideCopy(l,"Занят","Busy","Band","Банд")
        p.guideAvailable==false -> guideCopy(l,"Занят на выбранные даты","Busy on selected dates","Tanlangan sanalarda band","Танланган саналарда банд")
        p.guideAvailable==true -> guideCopy(l,"Доступен для заявок · требуется подтверждение","Available for requests · confirmation required","So‘rov uchun ochiq · tasdiqlash kerak","Сўров учун очиқ · тасдиқлаш керак")
        else -> guideCopy(l,"Доступность уточняется","Availability to be confirmed","Mavjudlik aniqlanadi","Мавжудлик аниқланади")
    }
    val ink=if(p.guideAvailable==true)Color(0xff268347)else MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){CupertinoIcon(CupertinoSymbol.CalendarClock,null,Modifier.size(16.dp),ink);Text(value,fontSize=13.sp,fontWeight=FontWeight.Medium,color=ink)}
}

@Composable internal fun GuideAvatar(p:IumrahPublicProfile,size:Int){
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){
        val photo=AppConfig.absoluteUrl(p.photoURL)?.takeIf{it.startsWith("https://")}
        if(photo!=null)AsyncImage(photo,p.displayName,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else CupertinoIcon(CupertinoSymbol.Person,null,Modifier.size((size*.55f).dp))
    }
}
