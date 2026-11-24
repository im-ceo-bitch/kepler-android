@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.iumrah.beta.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahSignatureWorldBackground
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

private class BT(val ru: String,val en: String,val uz: String,val cy: String = uz) {
    fun text(l: AppLanguage): String = when(l){
        AppLanguage.RUSSIAN->ru; AppLanguage.UZBEK->uz; AppLanguage.UZBEK_CYRILLIC->cy; else -> en
    }
}
private class BrandReason(val label:BT,val title:BT,val detail:BT,val icon:CupertinoSymbol)
private val reasons=listOf(
    BrandReason(BT("Наша миссия","Our mission","Missiyamiz","Миссиямиз"),BT("Умра должна быть доступна каждой семье.","Umrah should be accessible to every family.","Umra har bir oila uchun imkonli bo‘lishi kerak.","Умра ҳар бир оила учун имконли бўлиши керак."),BT("Самостоятельно организуйте индивидуальную умру для своих близких. С понятной стоимостью и без необходимости обращаться в традиционную турфирму.","Organize an individual Umrah for your loved ones, with clear pricing and without relying on a traditional travel agency.","Yaqinlaringiz uchun individual umrani mustaqil tashkil qiling.","Яқинларингиз учун индивидуал умрани мустақил ташкил қилинг."),CupertinoSymbol.Heart),
    BrandReason(BT("Свобода путешествия","Freedom to travel","Safar erkinligi","Сафар эркинлиги"),BT("Ваше путешествие. Ваш темп.","Your journey. Your pace.","Safaringiz. O‘z sur’atingiz.","Сафарингиз. Ўз суръатингиз."),BT("Без ожидания большой группы и необходимости подстраиваться под чужое расписание.","No waiting for a large group or adapting to someone else’s schedule.","Katta guruhni kutmasdan va boshqalarning jadvaliga moslashmasdan.","Катта гуруҳни кутмасдан ва бошқаларнинг жадвалига мослашмасдан."),CupertinoSymbol.Clock),
    BrandReason(BT("Персональное сопровождение","Personal guidance","Shaxsiy hamrohlik","Шахсий ҳамроҳлик"),BT("Один гид. Для вашей семьи.","One guide. For your family.","Bir gid. Oilangiz uchun.","Бир гид. Оилангиз учун."),BT("Задавайте вопросы, получайте объяснения и совершайте умру без спешки.","Ask questions, receive explanations and perform Umrah without rushing.","Savollar bering, tushuntirishlar oling va umrani shoshilmasdan bajaring.","Саволлар беринг, тушунтиришлар олинг ва умрани шошилмасдан бажаринг."),CupertinoSymbol.Persons),
    BrandReason(BT("Индивидуальный транспорт","Private transport","Shaxsiy transport","Шахсий транспорт"),BT("Ваш личный автомобиль.","Your private car.","Shaxsiy avtomobilingiz.","Шахсий автомобилнгиз."),BT("Путешествуйте вместе с близкими, не ожидая остальных пассажиров.","Travel with your loved ones without waiting for other passengers.","Yaqinlaringiz bilan boshqa yo‘lovchilarni kutmasdan sayohat qiling.","Яқинларингиз билан бошқа йўловчиларни кутмасдан саёҳат қилинг."),CupertinoSymbol.Car),
    BrandReason(BT("Прозрачность","Transparency","Shaffoflik","Шаффофлик"),BT("Знайте, за что платите.","Know what you pay for.","Nima uchun to‘layotganingizni biling.","Нима учун тўлаётганингизни билинг."),BT("Стоимость каждой услуги вашего путешествия доступна ещё до бронирования.","See the price of each service before booking.","Har bir xizmat narxini bron qilishdan oldin ko‘ring.","Ҳар бир хизмат нархини брон қилишдан олдин кўринг."),CupertinoSymbol.CreditCard),
    BrandReason(BT("Свобода выбора","Freedom of choice","Tanlov erkinligi","Танлов эркинлиги"),BT("Создайте свою умру.","Create your own Umrah.","O‘z umrangizni tuzing.","Ўз умрангизни тузинг."),BT("Ваши даты, авиабилеты, отели, номера и дополнительные услуги.","Your dates, flights, hotels, rooms and additional services.","Sanalar, aviachiptalar, mehmonxonalar, xonalar va xizmatlar.","Саналар, авиачипталар, меҳмонхоналар, хоналар ва хизматлар."),CupertinoSymbol.Sliders),
    BrandReason(BT("Наша ответственность","Our responsibility","Mas’uliyatimiz","Масъулиятимиз"),BT("Вы поклоняетесь. Мы заботимся.","You worship. We take care.","Siz ibodat qiling. Biz g‘amxo‘rlik qilamiz.","Сиз ибодат қилинг. Биз ғамхўрлик қиламиз."),BT("Наша команда помогает вам на каждом этапе путешествия.","Our team supports you at every stage of your journey.","Jamoamiz har bir bosqichda sizga yordam beradi.","Жамоамиз ҳар бир босқичда сизга ёрдам беради."),CupertinoSymbol.ShieldCheck),
    BrandReason(BT("Почувствуйте разницу","Experience the difference","Farqni his qiling","Фарқни ҳис қилинг"),BT("Два формата. Совершенно разный опыт.","Two formats. A completely different experience.","Ikki format. Butunlay boshqa tajriba.","Икки формат. Бутунлай бошқа тажриба."),BT("Узнайте, чем индивидуальная умра с iumrah отличается от традиционного группового путешествия.","Discover how individual Umrah with iumrah differs from traditional group travel.","Individual umra guruhli safardan nimasi bilan farq qiladi?","Индивидуал умра гуруҳли сафардан нимаси билан фарқ қилади?"),CupertinoSymbol.ArrowLeftRight),
)
private fun brandGradient() = Brush.linearGradient(listOf(Color(0xFF1F1F1F),Color(0xFF060606)))

@Composable
internal fun HomeBrandExperience(language: AppLanguage, chrome: AppChromeStore) {
    var comparisonOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val current by remember { derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, reasons.lastIndex) } }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(BT("Почему iumrah?","Why iumrah?","Nega iumrah?","Нега iumrah?").text(language),fontSize=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            Text("${current+1} / 8",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(state=listState,flingBehavior=rememberSnapFlingBehavior(lazyListState=listState),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            items(reasons.indices.toList()) { index ->
                val item=reasons[index]
                Column(Modifier.width(284.dp).heightIn(min=290.dp).clip(RoundedCornerShape(24.dp))
                    .background(brandGradient()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(item.label.text(language),fontSize=12.sp,color=Color.White.copy(alpha=.65f),modifier=Modifier.weight(1f))
                        CupertinoIcon(item.icon,null,Modifier.size(22.dp),Color.White)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.title.text(language),fontSize=22.sp,lineHeight=27.sp,fontWeight=FontWeight.Bold,color=Color.White)
                    Text(item.detail.text(language),fontSize=15.sp,lineHeight=20.sp,color=Color.White.copy(alpha=.75f))
                    Spacer(Modifier.weight(1f,fill=false))
                    if(index==7) BrandWhiteCTA(BT("Сравнить с групповым туром","Compare with group travel","Guruhli safar bilan taqqoslash","Гуруҳли сафар билан таққослаш").text(language)) { comparisonOpen=true }
                    else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("iumrah",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=Color.White,modifier=Modifier.weight(1f))
                        Text(String.format(java.util.Locale.ROOT,"%02d / 08",index+1),fontSize=11.sp,color=Color.White.copy(alpha=.5f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp,Alignment.CenterHorizontally)) {
            reasons.indices.forEach { i -> Box(Modifier.width(if(i==current)20.dp else 5.dp).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha=if(i==current).65f else .18f))) }
        }
    }
    if(comparisonOpen) HomeBrandComparison(language,chrome){comparisonOpen=false}
}

@Composable private fun BrandWhiteCTA(title:String, action:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=44.dp).clip(CircleShape).background(Color.White).clickable(onClick=action).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp)); Text(title,modifier=Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Color.Black,textAlign=TextAlign.Center)
        CupertinoIcon(CupertinoSymbol.ArrowRight,null,Modifier.size(16.dp),Color.Black)
    }
}

@Composable
internal fun HomeBrandMission(language: AppLanguage) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(brandGradient()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(reasons[0].label.text(language).uppercase(),color=Color.White.copy(alpha=.55f),fontSize=12.sp)
        Text(BT("Мы создали не просто туристическую платформу.","We built more than a travel platform.","Biz shunchaki sayohat platformasini yaratmadik.","Биз шунчаки саёҳат платформасини яратмадик.").text(language),
            color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold,lineHeight=27.sp)
        Text(BT("Мы хотели дать каждой семье возможность отправиться на умру вместе. Самостоятельно выбрать своё путешествие, понимать его стоимость и не зависеть от расписания больших групп.","We wanted every family to be able to go on Umrah together. To choose their own journey, understand its cost and travel without depending on the schedule of a large group.","Har bir oilaga birga umra safariga borish imkonini bermoqchi edik. Safarini mustaqil tanlash, narxini tushunish va katta guruhlar jadvaliga bog‘liq bo‘lmaslik uchun.","Ҳар бир оилага бирга умра сафарига бориш имконини бермоқчи эдик. Сафарини мустақил танлаш, нархини тушуниш ва катта гуруҳлар жадвалига боғлиқ бўлмаслик учун.").text(language),color=Color.White.copy(alpha=.75f),fontSize=14.sp,lineHeight=21.sp)
        HorizontalDivider(color=Color.White.copy(alpha=.15f))
        Text(BT("Потому что умра — это прежде всего поклонение, а не туристическая поездка.","Because Umrah is first and foremost an act of worship, not a tourist trip.","Chunki umra — avvalo ibodat, sayyohlik safari emas.","Чунки умра — аввало ибодат, сайёҳлик сафари эмас.").text(language),color=Color.White,fontWeight=FontWeight.Medium,fontSize=14.sp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text("iumrah",color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp,modifier=Modifier.weight(1f)); CupertinoIcon(CupertinoSymbol.Heart,null,Modifier.size(20.dp),Color.White.copy(alpha=.5f)) }
    }
}

@Composable
internal fun HomeBrandSlogan(language: AppLanguage) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
        IumrahSignatureWorldBackground(Modifier.matchParentSize(),mapOpacity=.25f)
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.05f),Color.Black.copy(alpha=.85f))))
            .padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("iumrah",fontWeight=FontWeight.ExtraBold,fontSize=34.sp,color=Color.White)
            Text(BT("Ближе к своим близким. Ближе к поклонению.","Closer to your loved ones. Closer to worship.","Yaqinlaringizga yaqinroq. Ibodatga yaqinroq.","Яқинларингизга яқинроқ. Ибодатга яқинроқ.").text(language),
                fontSize=22.sp,fontWeight=FontWeight.Bold,lineHeight=27.sp,color=Color.White)
            Text(BT("Индивидуальная умра. Прозрачная стоимость. Персональная забота.","Individual Umrah. Transparent pricing. Personal care.","Individual umra. Shaffof narx. Shaxsiy g‘amxo‘rlik.","Индивидуал умра. Шаффоф нарх. Шахсий ғамхўрлик.").text(language),
                fontSize=15.sp,color=Color.White.copy(alpha=.72f))
            HorizontalDivider(color=Color.White.copy(alpha=.14f))
            Text("iumrah.app",color=Color.White.copy(alpha=.6f),fontSize=12.sp)
        }
    }
}

@Composable private fun BrandStoryCard(title:String, detail:String, photo:Int, dark:Boolean=false) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if(dark)Color(0xFF151515) else MaterialTheme.colorScheme.surface).padding(if(dark)18.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Image(painterResource(photo),null,Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)),contentScale=ContentScale.Crop)
        Text(title,fontSize=if(dark)34.sp else 22.sp,fontWeight=FontWeight.Bold,color=if(dark)Color.White else MaterialTheme.colorScheme.onSurface)
        Text(detail,fontSize=17.sp,color=if(dark)Color.White.copy(alpha=.75f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun BrandContentCard(content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}

@Composable
internal fun HomeBrandComparison(language: AppLanguage, chrome: AppChromeStore, onDismiss:()->Unit) {
    fun t(r:String,e:String,u:String,c:String)=BT(r,e,u,c).text(language)
    val pairs = listOf(
        t("Общее расписание","Shared schedule","Umumiy jadval","Умумий жадвал") to t("Ваше расписание","Your schedule","Jadvalingiz","Жадвалингиз"),
        t("Гид на группу","Group guide","Guruh gidi","Гуруҳ гиди") to t("Персональный гид","Private guide","Shaxsiy gid","Шахсий гид"),
        t("Общий транспорт","Shared transport","Umumiy transport","Умумий транспорт") to t("Личный автомобиль","Private car","Shaxsiy avtomobil","Шахсий автомобил"),
        t("Готовый маршрут","Fixed route","Tayyor yo‘nalish","Тайёр йўналиш") to t("Ваш выбор","Your choice","Tanlovingiz","Танловингиз"),
        t("Цена всего тура","Tour price","Tur narxi","Тур нархи") to t("Стоимость каждой услуги","Itemized pricing","Har bir xizmat narxi","Ҳар бир хизмат нархи"),
        t("Общий темп","Group pace","Umumiy sur’at","Умумий суръат") to t("Ваш темп","Your pace","O‘z sur’atingiz","Ўз суръатингиз")
    )
    val priceRows = listOf(
        t("Авиабилеты","Flights","Aviachiptalar","Авиачипталар") to "1 098 $",
        t("Проживание","Accommodation","Turar joy","Турар жой") to "1 289.22 $",
        t("Визы","Visas","Vizalar","Визалар") to "240 $",
        t("Трансфер","Transfer","Transfer","Трансфер") to "473.72 $",
        t("Персональный гид","Private guide","Shaxsiy gid","Шахсий гид") to "400 $",
        t("Зияраты","Ziyarats","Ziyoratlar","Зиёратлар") to "200 $",
        t("Поддержка и комиссия · 9%","Support and fee · 9%","Yordam va komissiya · 9%","Ёрдам ва комиссия · 9%") to "333.08 $",
        t("Комиссия оплаты · 2%","Payment fee · 2%","To‘lov komissiyasi · 2%","Тўлов комиссияси · 2%") to "82.40 $",
        t("Округление","Rounding","Yaxlitlash","Яхлитлаш") to "3.58 $"
    )
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.96f).clip(RoundedCornerShape(topStart=24.dp,topEnd=24.dp)).background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Text(t("Почему iumrah?","Why iumrah?","Nega iumrah?","Нега iumrah?"),modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=17.sp,textAlign=TextAlign.Center)
                IconButton(onClick=onDismiss){CupertinoIcon(CupertinoSymbol.Close,null,Modifier.size(18.dp))}
            }
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                item { BrandStoryCard(t("Возможно, именно так вы всегда хотели совершить умру.","Perhaps this is how you always wanted to perform Umrah.","Balki umrani aynan shunday ado etishni istagandirsiz.","Балки умрани айнан шундай адо этишни истагандирсиз."),t("Вместе со своими близкими. Без спешки. Без необходимости подстраиваться под других.","With your loved ones. Without rushing or adapting to others.","Yaqinlaringiz bilan. Shoshilmasdan. Boshqalarga moslashmasdan.","Яқинларингиз билан. Шошилмасдан. Бошқаларга мослашмасдан."),R.drawable.about_iumrah_kaaba_corner,true) }
                item { BrandStoryCard(t("Это ваше путешествие.","This is your journey.","Bu sizning safaringiz.","Бу сизнинг сафарингиз."),t("Не нужно ждать, пока соберутся десятки других паломников. Ваше путешествие строится вокруг вас и вашей семьи.","No waiting for dozens of other pilgrims. Your journey is built around you and your family.","O‘nlab boshqa ziyoratchilarni kutish shart emas. Safar siz va oilangizga mos tashkil qilinadi.","Ўнлаб бошқа зиёратчиларни кутиш шарт эмас. Сафар сиз ва оилангизга мос ташкил қилинади."),R.drawable.about_iumrah_pilgrims) }
                item { BrandStoryCard(t("Каждый ваш вопрос важен.","Every question matters.","Har bir savolingiz muhim.","Ҳар бир саволингиз муҳим."),t("В большой группе индивидуального внимания может не хватать всем. Ваш персональный гид помогает именно вашей семье.","In a large group, personal attention may be limited. Your private guide helps your family.","Katta guruhda har bir kishiga e’tibor yetmasligi mumkin. Shaxsiy gidingiz oilangizga yordam beradi.","Катта гуруҳда ҳар бир кишига эътибор етмаслиги мумкин. Шахсий гидингиз оилангизга ёрдам беради."),R.drawable.iumrah_care_team_hero) }
                item { BrandStoryCard(t("Не торопитесь.","Take your time.","Shoshilmang.","Шошилманг."),t("Совершайте поклонение в удобном для вас темпе, не потому, что нужно успеть за группой.","Worship at your own pace, without having to keep up with a group.","Guruhga yetib olish uchun emas, o‘zingizga qulay sur’atda ibodat qiling.","Гуруҳга етиб олиш учун эмас, ўзингизга қулай суръатда ибодат қилинг."),R.drawable.about_iumrah_kaaba_touch) }
                item { BrandContentCard {
                    Text(t("Ваш автомобиль. Ваш маршрут.","Your car. Your route.","Avtomobilingiz. Yo‘nalishingiz.","Автомобилингиз. Йўналишингиз."),fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Image(painterResource(R.drawable.transfer_carnival),null,Modifier.fillMaxWidth().height(145.dp),contentScale=ContentScale.Fit)
                    listOf(t("Встреча в аэропорту","Airport pickup","Aeroportda kutib olish","Аэропортда кутиб олиш"),t("Индивидуальные трансферы","Private transfers","Shaxsiy transferlar","Шахсий трансферлар"),t("Переезды между городами","Intercity travel","Shaharlar orasida safar","Шаҳарлар орасида сафар"),t("Поездки по программе зияратов","Ziyarat trips","Ziyorat dasturi bo‘yicha safarlar","Зиёрат дастури бўйича сафарлар")).forEach { label -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {CupertinoIcon(CupertinoSymbol.CheckCircle,null,Modifier.size(17.dp));Text(label,fontSize=17.sp)} }
                } }
                item { BrandContentCard {
                    Text(t("Никаких загадок в стоимости.","No mystery in pricing.","Narxda yashirin jumboqlar yo‘q.","Нархда яширин жумбоқлар йўқ."),fontSize=22.sp,fontWeight=FontWeight.Bold)
                    priceRows.forEach { (key,value)-> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {Text(key,Modifier.weight(1f),fontSize=15.sp);Text(value,fontSize=15.sp,fontWeight=FontWeight.SemiBold)} }
                    HorizontalDivider();Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Text(t("Стоимость путешествия","Total journey price","Safar narxi","Сафар нархи"),Modifier.weight(1f),fontWeight=FontWeight.SemiBold,fontSize=17.sp);Text("4 120 $",fontSize=22.sp,fontWeight=FontWeight.Bold)}
                    Text(t("Пример пакета на двоих. Стоимость зависит от выбранных услуг.","Example for two travelers. Price depends on selected services.","Ikki kishilik paket misoli. Narx tanlangan xizmatlarga bog‘liq.","Икки кишилик пакет мисоли. Нарх танланган хизматларга боғлиқ."),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                item { BrandContentCard {
                    Text(t("Почувствуйте разницу","Experience the difference","Farqni his qiling","Фарқни ҳис қилинг"),fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)){Image(painterResource(R.drawable.about_iumrah_pilgrims),null,Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop);Text(t("Групповая умра","Group Umrah","Guruhli umra","Гуруҳли умра"),fontSize=17.sp,fontWeight=FontWeight.SemiBold)}
                        Column(Modifier.weight(1f)){Image(painterResource(R.drawable.iumrah_care_team_hero),null,Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop);Text("iumrah",fontSize=17.sp,fontWeight=FontWeight.SemiBold)}
                    }
                    pairs.forEach { (group,individual)-> HorizontalDivider();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {Text(group,Modifier.weight(1f),fontSize=15.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(5.dp)){CupertinoIcon(CupertinoSymbol.Checkmark,null,Modifier.size(15.dp));Text(individual,fontSize=15.sp,fontWeight=FontWeight.Medium)}} }
                    Text(t("Сравнение типичных форматов путешествия. Условия отдельных туроператоров могут отличаться. Фотографии иллюстративные.","Comparison of typical travel formats. Individual operators may differ. Images are illustrative.","Odatdagi safar formatlari taqqoslandi. Ayrim turoperatorlar shartlari farq qilishi mumkin. Rasmlar namuna sifatida berilgan.","Одатдаги сафар форматлари таққосланди. Айрим туроператорлар шартлари фарқ қилиши мумкин. Расмлар намуна сифатида берилган."),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                item { BrandContentCard {
                    Text(t("Вы не останетесь одни.","You are not alone.","Siz yolg‘iz qolmaysiz.","Сиз ёлғиз қолмайсиз."),fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Text("iumrah "+t("Поддержка","Support","Yordam","Ёрдам"),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(t("Создаёте путешествие","Create your journey","Safaringizni tuzasiz","Сафарингизни тузасиз"),t("Проверяем наличие и стоимость","We check availability and price","Mavjudlik va narxni tekshiramiz","Мавжудлик ва нархни текширамиз"),t("Оформляем документы","Prepare documents","Hujjatlarni tayyorlaymiz","Ҳужжатларни тайёрлаймиз"),t("Встречаем в Саудовской Аравии","Meet you in Saudi Arabia","Saudiya Arabistonida kutib olamiz","Саудия Арабистонида кутиб оламиз"),t("Сопровождаем до возвращения домой","Support you until you return home","Uyga qaytguningizcha hamrohlik qilamiz","Уйга қайтгунингизча ҳамроҳлик қиламиз")).forEachIndexed { i,label->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.12f),CircleShape),contentAlignment=Alignment.Center){Text("${i+1}",fontWeight=FontWeight.SemiBold)};Text(label,fontSize=15.sp)} }
                } }
                item { HomeBrandMission(language) }
                item { HomeBrandSlogan(language) }
                item { com.iumrah.beta.ui.components.IumrahPrimaryButton(t("Создать своё путешествие","Create your journey","O‘z safaringizni tuzing","Ўз сафарингизни тузинг")) { onDismiss();chrome.startNewTrip() } }
            }
        }
    }
}


/** Brand Experience 15: contextual CTA reused by Trip Summary, proposals and saved packages. */
@Composable
internal fun BrandComparisonEntry(language: AppLanguage, chrome: AppChromeStore) {
    var comparisonOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF121214))
        .clickable { comparisonOpen=true }.padding(18.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(BT("Почувствуйте разницу","Experience the difference","Farqni his qiling","Фарқни ҳис қилинг").text(language),
                fontWeight=FontWeight.SemiBold,color=Color.White,fontSize=17.sp)
            Text(BT("Сравнить индивидуальную умру с групповым форматом","Compare personal and group Umrah","Individual va guruhli umrani solishtirish","Индивидуал ва гуруҳли умрани солиштириш").text(language),
                color=Color.White.copy(alpha=.72f),fontSize=13.sp)
        }
        CupertinoIcon(CupertinoSymbol.ArrowRight,null,Modifier.size(20.dp),Color.White)
    }
    if (comparisonOpen) HomeBrandComparison(language,chrome){comparisonOpen=false}
}
