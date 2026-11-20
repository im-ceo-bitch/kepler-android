package com.iumrah.beta.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
private fun brandGradient() = Brush.linearGradient(listOf(Color(0xFF202024),Color(0xFF070708)))

@Composable
internal fun HomeBrandExperience(language: AppLanguage, chrome: AppChromeStore) {
    var comparisonOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(BT("Почему iumrah?","Why iumrah?","Nega iumrah?","Нега iumrah?").text(language),fontSize=23.sp,fontWeight=FontWeight.Bold)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            items(reasons.indices.toList()) { index ->
                val item=reasons[index]
                Column(Modifier.width(285.dp).height(250.dp).clip(RoundedCornerShape(24.dp))
                    .background(brandGradient()).clickable { if(index==7)comparisonOpen=true else chrome.startNewTrip() }
                    .padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(item.label.text(language).uppercase(),fontSize=11.sp,color=Color.White.copy(alpha=.60f),modifier=Modifier.weight(1f))
                        CupertinoIcon(item.icon,null,Modifier.size(19.dp),Color.White.copy(alpha=.8f))
                    }
                    Text(item.title.text(language),fontSize=21.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold,color=Color.White)
                    Text(item.detail.text(language),fontSize=13.sp,lineHeight=19.sp,color=Color.White.copy(alpha=.75f),maxLines=4)
                    Spacer(Modifier.weight(1f))
                    Text(if(index==7)BT("Сравнить форматы","Compare formats","Formatlarni taqqoslash","Форматларни таққослаш").text(language)
                        else BT("Создать путешествие","Create a trip","Safar yaratish","Сафар яратиш").text(language),
                        fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=Color.White)
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color.Black)
            .clickable { comparisonOpen=true }.padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(BT("Сравнить с групповым туром","Compare with group travel","Guruhli safar bilan taqqoslash","Гуруҳли сафар билан таққослаш").text(language),
                modifier=Modifier.weight(1f),fontWeight=FontWeight.SemiBold,color=Color.White,fontSize=14.sp)
            CupertinoIcon(CupertinoSymbol.ArrowRight,null,Modifier.size(16.dp),Color.White)
        }
    }
    if(comparisonOpen) HomeBrandComparison(language,chrome){comparisonOpen=false}
}

@Composable
internal fun HomeBrandMission(language: AppLanguage) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(brandGradient()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Text(reasons[0].label.text(language).uppercase(),color=Color.White.copy(alpha=.6f),fontSize=11.sp)
        Text(BT("Мы создали не просто туристическую платформу.","We built more than a travel platform.","Biz shunchaki sayohat platformasini yaratmadik.","Биз шунчаки саёҳат платформасини яратмадик.").text(language),
            color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold,lineHeight=29.sp)
        Text(BT("Мы хотели дать каждой семье возможность отправиться на умру вместе. Самостоятельно выбрать своё путешествие, понимать его стоимость и не зависеть от расписания больших групп.","We wanted every family to be able to go on Umrah together. To choose their own journey, understand its cost and travel without depending on the schedule of a large group.","Har bir oilaga birga umra safariga borish imkonini bermoqchi edik. Safarini mustaqil tanlash, narxini tushunish va katta guruhlar jadvaliga bog‘liq bo‘lmaslik uchun.","Ҳар бир оилага бирга умра сафарига бориш имконини бермоқчи эдик. Сафарини мустақил танлаш, нархини тушуниш ва катта гуруҳлар жадвалига боғлиқ бўлмаслик учун.").text(language),color=Color.White.copy(alpha=.75f),fontSize=14.sp,lineHeight=21.sp)
        HorizontalDivider(color=Color.White.copy(alpha=.15f))
        Text(BT("Потому что умра — это прежде всего поклонение, а не туристическая поездка.","Because Umrah is first and foremost an act of worship, not a tourist trip.","Chunki umra — avvalo ibodat, sayyohlik safari emas.","Чунки умра — аввало ибодат, сайёҳлик сафари эмас.").text(language),color=Color.White,fontWeight=FontWeight.Medium,fontSize=14.sp)
        Text("iumrah",color=Color.White,fontWeight=FontWeight.Bold,fontSize=21.sp)
    }
}

@Composable
internal fun HomeBrandSlogan(language: AppLanguage) {
    Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp)).background(Color.Black)) {
        IumrahSignatureWorldBackground(Modifier.fillMaxSize(),mapOpacity=.25f)
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.03f),Color.Black.copy(alpha=.87f))))
            .padding(24.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            Text("iumrah",fontWeight=FontWeight.Bold,fontSize=31.sp,color=Color.White)
            Text(BT("Ближе к своим близким. Ближе к поклонению.","Closer to your loved ones. Closer to worship.","Yaqinlaringizga yaqinroq. Ibodatga yaqinroq.","Яқинларингизга яқинроқ. Ибодатга яқинроқ.").text(language),
                fontSize=21.sp,fontWeight=FontWeight.Bold,lineHeight=27.sp,color=Color.White)
            Text(BT("Индивидуальная умра. Прозрачная стоимость. Персональная забота.","Individual Umrah. Transparent pricing. Personal care.","Individual umra. Shaffof narx. Shaxsiy g‘amxo‘rlik.","Индивидуал умра. Шаффоф нарх. Шахсий ғамхўрлик.").text(language),
                fontSize=13.sp,color=Color.White.copy(alpha=.75f))
            Spacer(Modifier.weight(1f))
            Text("iumrah.app",color=Color.White.copy(alpha=.6f),fontSize=11.sp)
        }
    }
}

@Composable
internal fun HomeBrandComparison(language: AppLanguage, chrome: AppChromeStore, onDismiss:()->Unit) {
    val pairs = listOf(
        BT("Общее расписание","Shared schedule","Umumiy jadval","Умумий жадвал") to BT("Ваше расписание","Your schedule","Jadvalingiz","Жадвалингиз"),
        BT("Гид на группу","Group guide","Guruh gidi","Гуруҳ гиди") to BT("Персональный гид","Private guide","Shaxsiy gid","Шахсий гид"),
        BT("Общий транспорт","Shared transport","Umumiy transport","Умумий транспорт") to BT("Личный автомобиль","Private car","Shaxsiy avtomobil","Шахсий автомобил"),
        BT("Готовый маршрут","Fixed route","Tayyor yo‘nalish","Тайёр йўналиш") to BT("Ваш выбор","Your choice","Tanlovingiz","Танловингиз"),
        BT("Цена всего тура","Tour price","Tur narxi","Тур нархи") to BT("Стоимость каждой услуги","Itemized pricing","Har bir xizmat narxi","Ҳар бир хизмат нархи"),
        BT("Общий темп","Group pace","Umumiy sur’at","Умумий суръат") to BT("Ваш темп","Your pace","O‘z sur’atingiz","Ўз суръатингиз"),
    )
    Dialog(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(BT("Почувствуйте разницу","Experience the difference","Farqni his qiling","Фарқни ҳис қилинг").text(language),
                    modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=19.sp)
                TextButton(onClick=onDismiss){CupertinoIcon(CupertinoSymbol.Close,null,Modifier.size(18.dp))}
            }
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=17.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Image(painterResource(R.drawable.about_iumrah_pilgrims),null,Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
                        Text(BT("Групповая умра","Group Umrah","Guruhli umra","Гуруҳли умра").text(language),fontWeight=FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Image(painterResource(R.drawable.iumrah_care_team_hero),null,Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
                        Text("iumrah",fontWeight=FontWeight.Bold)
                    }
                } }
                items(pairs){(group,individual)->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(group.text(language),modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f),fontSize=13.sp)
                        Row(Modifier.weight(1f),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            CupertinoIcon(CupertinoSymbol.Checkmark,null,Modifier.size(15.dp))
                            Text(individual.text(language),fontWeight=FontWeight.Medium,fontSize=13.sp)
                        }
                    }
                }
                item { Text(BT("Сравнение типичных форматов путешествия. Условия отдельных туроператоров могут отличаться. Фотографии иллюстративные.","Comparison of typical travel formats. Individual operators may differ. Images are illustrative.","Odatdagi safar formatlari taqqoslandi. Ayrim turoperatorlar shartlari farq qilishi mumkin. Rasmlar namuna sifatida berilgan.","Одатдаги сафар форматлари таққосланди. Айрим туроператорлар шартлари фарқ қилиши мумкин. Расмлар намуна сифатида берилган.").text(language),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f)) }
                item { Text(BT("Никаких загадок в стоимости.","No mystery in pricing.","Narxda yashirin jumboqlar yo‘q.","Нархда яширин жумбоқлар йўқ.").text(language),fontWeight=FontWeight.Bold,fontSize=20.sp) }
                items(listOf("Авиабилеты" to "1 098 $","Проживание" to "1 289.22 $","Визы" to "240 $","Трансфер" to "473.72 $","Персональный гид" to "400 $","Зияраты" to "200 $","Поддержка и комиссия · 9%" to "333.08 $","Комиссия оплаты · 2%" to "82.40 $","Округление" to "3.58 $")) { (key,value) ->
                    Row(Modifier.fillMaxWidth()) { Text(key,modifier=Modifier.weight(1f),fontSize=13.sp);Text(value,fontSize=13.sp,fontWeight=FontWeight.SemiBold) }
                }
                item { HorizontalDivider();Row(Modifier.fillMaxWidth()) {Text("Итого",modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold);Text("4 120 $",fontWeight=FontWeight.Bold)}
                    Text(BT("Пример пакета на двоих. Стоимость зависит от выбранных услуг.","Example for two travelers. Price depends on selected services.","Ikki kishilik paket misoli. Narx tanlangan xizmatlarga bog‘liq.","Икки кишилик пакет мисоли. Нарх танланган хизматларга боғлиқ.").text(language),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.5f)) }
            }
            Box(Modifier.fillMaxWidth().padding(16.dp).height(50.dp).clip(CircleShape).background(Color.Black).clickable { onDismiss();chrome.startNewTrip() },contentAlignment=Alignment.Center) {
                Text(BT("Создать своё путешествие","Create your own journey","Safaringizni yarating","Сафарингизни яратинг").text(language),fontWeight=FontWeight.SemiBold,color=Color.White)
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
