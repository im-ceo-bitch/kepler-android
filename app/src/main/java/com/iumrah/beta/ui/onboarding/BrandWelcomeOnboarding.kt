package com.iumrah.beta.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalView
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.rememberIumrahMotionAllowed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahSignatureWorldBackground
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import kotlinx.coroutines.launch

/** First-install six-step Brand Experience 15. The existing first-install
 * completion flag is kept; there is no account requirement or reset for
 * returning customers. Content is local and never invents booking prices.
 */
private fun brandT(l: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when(l) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
    else -> en
}

private data class BrandIntroSlide(val title: List<String>, val details: List<String>, val picture: Int?) {
    fun title(l: AppLanguage) = brandT(l,title[0],title[1],title[2],title[3])
    fun details(l: AppLanguage) = brandT(l,details[0],details[1],details[2],details[3])
}

private val introSlides = listOf(
    BrandIntroSlide(listOf("Ближе к своим близким. Ближе к поклонению.","Closer to your loved ones. Closer to worship.","Yaqinlaringizga yaqinroq. Ibodatga yaqinroq.","Яқинларингизга яқинроқ. Ибодатга яқинроқ."),
        listOf("Индивидуальная умра нового поколения.","A new generation of individual Umrah.","Yangi avlod individual umrasi.","Янги авлод индивидуал умраси."),R.drawable.about_iumrah_kaaba_corner),
    BrandIntroSlide(listOf("Умра должна быть доступна каждой семье.","Umrah should be accessible to every family.","Umra har bir oila uchun imkonli bo‘lishi kerak.","Умра ҳар бир оила учун имконли бўлиши керак."),
        listOf("Создайте собственное путешествие для себя и своих близких.","Create your own journey for your loved ones.","O‘zingiz va yaqinlaringiz uchun safar tuzing.","Ўзингиз ва яқинларингиз учун сафар тузинг."),R.drawable.about_iumrah_pilgrims),
    BrandIntroSlide(listOf("Только вы и ваша семья.","Only you and your family.","Faqat siz va oilangiz.","Фақат сиз ва оилангиз."),
        listOf("Ваш персональный гид. Ваш автомобиль. Ваше расписание.","Your private guide. Your car. Your schedule.","Shaxsiy gidingiz. Avtomobilingiz. Jadvalingiz.","Шахсий гидингиз. Автомобилингиз. Жадвалингиз."),R.drawable.iumrah_care_team_hero),
    BrandIntroSlide(listOf("Знайте, за что платите.","Know what you pay for.","Nima uchun to‘layotganingizni biling.","Нима учун тўлаётганингизни билинг."),
        listOf("Стоимость каждой услуги вашего путешествия доступна ещё до бронирования.","See the price of each service before booking.","Safaringizdagi har bir xizmat narxini bron qilishdan oldin ko‘ring.","Сафарингиздаги ҳар бир хизмат нархини брон қилишдан олдин кўринг."),null),
    BrandIntroSlide(listOf("Мы рядом на каждом этапе.","We are here at every stage.","Har bir bosqichda yoningizdamiz.","Ҳар бир босқичда ёнингиздамиз."),
        listOf("Наша команда помогает вам на каждом этапе путешествия.","Our team supports you at every stage of your journey.","Jamoamiz safaringizning har bir bosqichida sizga yordam beradi.","Жамоамиз сафарингизнинг ҳар бир босқичида сизга ёрдам беради."),R.drawable.iumrah_makkah_background),
    BrandIntroSlide(listOf("Создайте свою умру.","Create your own Umrah.","O‘z umrangizni tuzing.","Ўз умрангизни тузинг."),
        listOf("Ваши даты, авиабилеты, отели, номера и дополнительные услуги.","Your dates, flights, hotels, rooms and additional services.","Sizning sanalaringiz, aviachiptalaringiz, mehmonxonalaringiz, xonalaringiz va qo‘shimcha xizmatlaringiz.","Сизнинг саналарингиз, авиачипталарингиз, меҳмонхоналарингиз, хоналарингиз ва қўшимча хизматларингиз."),R.drawable.about_iumrah_kaaba_touch),
)

@Composable
fun BrandWelcomeOnboarding(language: AppLanguage, onLanguageChange: (AppLanguage)->Unit, onFinished: ()->Unit) {
    val pager = rememberPagerState(pageCount = { introSlides.size })
    val scope = rememberCoroutineScope()
    var languageOpen by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val motion = rememberIumrahMotionAllowed()
    val view = LocalView.current
    fun move(to: Int) {
        if (!leaving && !pager.isScrollInProgress && to in introSlides.indices) scope.launch {
            IumrahHaptics.selection(view)
            if (motion) pager.animateScrollToPage(to, animationSpec = tween(250)) else pager.scrollToPage(to)
        }
    }
    val advance: () -> Unit = {
        if (!leaving && !pager.isScrollInProgress) {
            if (pager.currentPage == introSlides.lastIndex) { leaving = true; onFinished() }
            else move(pager.currentPage + 1)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        IumrahSignatureWorldBackground(Modifier.fillMaxSize(),mapOpacity=.15f,waveIntensity=0f,isAnimating=false)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("iumrah",modifier=Modifier.weight(1f),fontSize=26.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Box {
                    TextButton(onClick={languageOpen=true}) {
                        CupertinoIcon(CupertinoSymbol.Globe,null,Modifier.size(21.dp),Color.White)
                    }
                    DropdownMenu(expanded=languageOpen,onDismissRequest={languageOpen=false}) {
                        AppLanguage.entries.forEach { lang -> DropdownMenuItem(text={Text(lang.nativeTitle)},onClick={languageOpen=false;onLanguageChange(lang)}) }
                    }
                }
                TextButton(onClick={if(!leaving){leaving=true;onFinished()}}) {
                    Text(brandT(language,"Пропустить","Skip","O‘tkazib yuborish","Ўтказиб юбориш"),fontSize=13.sp,color=Color.White)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                repeat(6) { index -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha=if(index<=pager.currentPage)1f else .22f))) }
            }
            HorizontalPager(state=pager,modifier=Modifier.weight(1f),userScrollEnabled=!leaving,verticalAlignment=Alignment.Top) { index ->
                val item=introSlides[index]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(item.picture!=null) {
                        Image(painterResource(item.picture),null,Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(22.dp)),
                            contentScale=ContentScale.Crop)
                    } else {
                        Column(Modifier.fillMaxWidth().heightIn(min=200.dp),verticalArrangement=Arrangement.spacedBy(20.dp,Alignment.CenterVertically),horizontalAlignment=Alignment.CenterHorizontally) {
                            listOf(CupertinoSymbol.Airplane to brandT(language,"Авиабилеты","Flights","Aviachiptalar","Авиачипталар"),
                                CupertinoSymbol.Bed to brandT(language,"Отели","Hotels","Mehmonxonalar","Меҳмонхоналар"),
                                CupertinoSymbol.Car to brandT(language,"Трансфер","Transfer","Transfer","Трансфер"),
                                CupertinoSymbol.Persons to brandT(language,"Персональный гид","Private guide","Shaxsiy gid","Шахсий гид")
                            ).forEach { (icon,label) -> Row(horizontalArrangement=Arrangement.spacedBy(13.dp),verticalAlignment=Alignment.CenterVertically) {
                                CupertinoIcon(icon,null,Modifier.size(22.dp),Color.White)
                                Text(label,fontSize=18.sp,color=Color.White)
                            } }
                        }
                    }
                    Text(item.title(language),fontSize=34.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold,color=Color.White)
                    Text(item.details(language),fontSize=16.sp,lineHeight=23.sp,color=Color.White.copy(alpha=.75f))
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal=20.dp).heightIn(min=44.dp).clip(CircleShape)
                .background(Color.White).clickable(enabled=!leaving && !pager.isScrollInProgress,onClick=advance).padding(12.dp),contentAlignment=Alignment.Center) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(.12f))
                    Text(if(pager.currentPage==5)brandT(language,"Начать пользоваться iumrah","Start using iumrah","iumrah’dan foydalanishni boshlash","iumrah’дан фойдаланишни бошлаш")
                        else brandT(language,"Продолжить","Continue","Davom etish","Давом этиш"),fontWeight=FontWeight.SemiBold,color=Color.Black,modifier=Modifier.weight(1f),textAlign=TextAlign.Center,fontSize=15.sp)
                    CupertinoIcon(CupertinoSymbol.ArrowRight,null,Modifier.size(16.dp),Color.Black)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(enabled=pager.currentPage>0 && !leaving && !pager.isScrollInProgress,onClick={move(pager.currentPage-1)}) {
                    CupertinoIcon(CupertinoSymbol.ChevronLeft,null,Modifier.size(20.dp),Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage+1} / 6",color=Color.White.copy(alpha=.7f),fontSize=13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(enabled=pager.currentPage<5 && !leaving && !pager.isScrollInProgress,onClick={move(pager.currentPage+1)}) {
                    CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(20.dp),Color.White)
                }
            }
        }
    }
}
