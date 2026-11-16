package com.iumrah.beta.ui.account

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.account.ReferralPolicy
import com.iumrah.beta.data.account.ReferralStore
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.models.account.IumrahFriendsDashboard
import com.iumrah.beta.ui.components.IumrahOrbitV5
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.hotels.nuiteeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val blue = Color(0xFF007AFF)
private val green = Color(0xFF34C759)
private val rewardsServices = listOf(CupertinoSymbol.StarFill, CupertinoSymbol.Gift, CupertinoSymbol.PersonsFill, CupertinoSymbol.Airplane, CupertinoSymbol.CrownFill, CupertinoSymbol.Wallet)
private fun AppLanguage.t(en: String, ru: String, uz: String, cy: String) = nuiteeText(this, ru, en, uz, cy)
private fun money(value: Double?) = value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "$%,.2f", it) } ?: "—"
private fun unavailable(l: AppLanguage) = l.t("Rewards data is unavailable. Pull to refresh.", "Данные бонусов недоступны. Обновите страницу.", "Bonus ma’lumotlari mavjud emas. Yangilang.", "Бонус маълумотлари мавжуд эмас. Янгиланг.")

/** Never render another session's data; absent dashboard is not a zero balance. */
@Composable
internal fun rememberRewardsDashboard(account: IumrahAccountStore, revision: Int = 0, pollSeconds: Long = 60): IumrahFriendsDashboard? {
    val state by account.state.collectAsState()
    val owner = account.bearerToken.takeIf { state.isAuthenticated }
    var dashboard by remember(owner) { mutableStateOf<IumrahFriendsDashboard?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(owner, revision, pollSeconds, active) {
        if (owner == null || !active) return@LaunchedEffect
        do {
            try {
                val value = account.friendsDashboard()
                if (account.bearerToken == owner) dashboard = value.takeIf { it.ok }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                val status = when(error) { is APIException.Status -> error.code; is APIException.Server -> error.code; else -> null }
                if (status in listOf(401,403)) dashboard = null
            }
            if (pollSeconds <= 0) break
            delay(pollSeconds * 1000)
        } while (account.bearerToken == owner)
    }
    return dashboard.takeIf { owner != null && account.bearerToken == owner }
}

@Composable private fun Badge(symbol: CupertinoSymbol, tint: Color, size: Int = 44) {
    Box(Modifier.size(size.dp).background(tint.copy(alpha=.12f), CircleShape), contentAlignment=Alignment.Center) {
        CupertinoIcon(symbol, null, Modifier.size((size*.43f).dp), tint)
    }
}
@Composable private fun Card(modifier: Modifier = Modifier, radius: Int = 28, padding: Int = 18, background: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(radius.dp)).background(background).padding(padding.dp), verticalArrangement=Arrangement.spacedBy(12.dp), content=content)
}
@Composable private fun CTA(label: String, onClick: () -> Unit, dark: Boolean = false) {
    Button(onClick=onClick, modifier=Modifier.fillMaxWidth().heightIn(min=52.dp), shape=RoundedCornerShape(16.dp), colors=ButtonDefaults.buttonColors(containerColor=if(dark) Color.White else MaterialTheme.colorScheme.onBackground, contentColor=if(dark) Color.Black else MaterialTheme.colorScheme.background)) { Text(label, fontSize=17.sp, fontWeight=FontWeight.SemiBold, textAlign=TextAlign.Center) }
}
@Composable internal fun ReferralConsentForm(language: AppLanguage, store: ReferralStore, enabled: Boolean = true) {
    val state by store.state.collectAsState()
    var draft by remember { mutableStateOf(state.code.orEmpty()) }
    LaunchedEffect(state.code) { state.code?.let { draft = it } }
    OutlinedTextField(draft, { raw ->
        draft=raw.uppercase(Locale.ROOT).take(29)
        if (ReferralPolicy.normalize(draft) != null) store.setCode(draft) else store.clear()
    }, modifier=Modifier.fillMaxWidth(), singleLine=true, enabled=enabled, shape=RoundedCornerShape(16.dp), label={Text(language.t("Invitation code (optional)", "Код приглашения (необязательно)", "Taklif kodi (ixtiyoriy)", "Таклиф коди (ихтиёрий)"))})
    if (state.code != null) Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Text(language.t("Share my name, ID and invitation status with my inviter", "Разрешаю показать пригласившему моё имя, ID и статус приглашения", "Ismim, ID va taklif holatini ulashishga roziman", "Исмим, ID ва таклиф ҳолатини улашишга розиман"), Modifier.weight(1f), fontSize=12.sp)
        Switch(state.consent, store::consent, enabled=enabled)
    }
}

@Composable internal fun RewardsAccountCarousel(language: AppLanguage, chrome: AppChromeStore, identity: @Composable () -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement=Arrangement.spacedBy(10.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        androidx.compose.foundation.pager.HorizontalPager(state=pager, modifier=Modifier.fillMaxWidth().aspectRatio(1.48f)) { page ->
            if (page==0) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { identity() }
            else Column(Modifier.fillMaxSize().padding(1.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF0F0F14)).clickable { chrome.openRewards() }.padding(16.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Text("iumrah Rewards", color=Color.White, fontWeight=FontWeight.SemiBold); Spacer(Modifier.weight(1f)); Badge(CupertinoSymbol.ArrowUpRight, Color.White, 32) }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment=Alignment.Center) { IumrahOrbitV5(112.dp, lightOnDark=true, services=rewardsServices, active=pager.currentPage==1, centerSymbol=CupertinoSymbol.StarFill, accessibilityTitle="iumrah Rewards") }
                Text(language.t("Your privileges","Ваши преимущества","Imtiyozlaringiz","Имтиёзларингиз"), color=Color.White, fontSize=20.sp, fontWeight=FontWeight.Bold)
                Text(language.t("Travel and unlock new benefits","Путешествуйте и открывайте новые возможности","Sayohat qiling va yangi imtiyozlarni oching","Саёҳат қилинг ва янги имтиёзларни очинг"), color=Color.White.copy(alpha=.72f), fontSize=12.sp, maxLines=2)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { repeat(2) { index -> Box(Modifier.size(30.dp).clickable { scope.launch { pager.animateScrollToPage(index) } }, contentAlignment=Alignment.Center) { Box(Modifier.size(width=if(pager.currentPage==index) 20.dp else 7.dp,height=7.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=if(pager.currentPage==index) 1f else .25f), CircleShape)) } } }
    }
}

@Composable internal fun RewardsBalanceEntry(language: AppLanguage, account: IumrahAccountStore, chrome: AppChromeStore, revision: Int = 0) {
    val live = rememberRewardsDashboard(account, revision)
    Card(Modifier.clickable { chrome.openRewards("balance") }) {
        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) { Badge(CupertinoSymbol.Wallet, blue); Text("iumrah Balance", fontSize=20.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.weight(1f)); Badge(CupertinoSymbol.ArrowUpRight, MaterialTheme.colorScheme.onSurface,34) }
        Text(money(live?.availableCreditUsd), fontSize=38.sp,fontWeight=FontWeight.Bold)
        Text(language.t("Available for your next trip","Доступно для следующей поездки","Keyingi safar uchun mavjud","Кейинги сафар учун мавжуд"), color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=15.sp)
        HorizontalDivider()
        Row { Text(language.t("Pending rewards","Ожидаемые бонусы","Kutilayotgan bonuslar","Кутилаётган бонуслар"), Modifier.weight(1f)); Text(money(live?.pendingRewardsUsd),fontWeight=FontWeight.SemiBold) }
        if(live==null) Text(unavailable(language),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun IumrahRewardsScreen(page: String, language: AppLanguage, account: IumrahAccountStore, chrome: AppChromeStore) {
    val state by account.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    val live = rememberRewardsDashboard(account,revision,if(page=="friends")25 else 0)
    val context = LocalContext.current
    val title=when(page){"friends"->"iumrah Friends";"balance"->"iumrah Balance";"coupon"->language.t("Add coupon","Добавить купон","Kupon qo‘shish","Купон қўшиш");else->"iumrah Rewards"}
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal=18.dp), verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=chrome::back){ CupertinoIcon(CupertinoSymbol.ChevronLeft,"Back",Modifier.size(20.dp)) }
            Text(title,Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=18.sp,textAlign=TextAlign.Center)
            IconButton(onClick={revision++}){ CupertinoIcon(CupertinoSymbol.Refresh,"Refresh",Modifier.size(20.dp)) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=16.dp).padding(bottom=20.dp), verticalArrangement=Arrangement.spacedBy(20.dp)) {
            when(page){
                "friends" -> {
                    Card(radius=26,padding=20,background=Color.Black) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) { IumrahOrbitV5(minOf(245.dp,maxWidth), lightOnDark=true,services=rewardsServices,centerSymbol=CupertinoSymbol.Gift,accessibilityTitle=title) }
                            Text(language.t("A good journey starts with an invitation.","Доброе путешествие начинается с приглашения.","Yaxshi safar taklifdan boshlanadi.","Яхши сафар таклифдан бошланади."),color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                            Text(language.t("Invite loved ones and earn rewards after confirmed payment.","Приглашайте близких и получайте вознаграждения после подтверждения оплаты.","Yaqinlaringizni taklif qiling va to‘lov tasdiqlangach mukofot oling.","Яқинларингизни таклиф қилинг ва тўлов тасдиқлангач мукофот олинг."),color=Color.White.copy(alpha=.72f),fontSize=15.sp,textAlign=TextAlign.Center)
                            val invitation=live?.invitation?.takeIf { ReferralPolicy.capture(it.url)==ReferralPolicy.normalize(it.code) && ReferralPolicy.normalize(it.code)!=null }
                            if(invitation!=null){ CTA(language.t("Invite a friend","Пригласить друга","Do‘stni taklif qilish","Дўстни таклиф қилиш"),{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,invitation.url),null))},true); Text(invitation.code,color=Color.White,fontFamily=FontFamily.Monospace,fontSize=12.sp) }
                            else Text(unavailable(language),color=Color.White.copy(alpha=.75f),fontSize=12.sp)
                            HorizontalDivider(color=Color.White.copy(alpha=.15f))
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                listOf(language.t("Invited","Приглашено","Takliflar","Таклифлар") to (live?.referrals?.size?.toString()?:"—"),language.t("Paid","Оплачено","To‘langan","Тўланган") to (live?.paidReferrals?.toString()?:"—"),language.t("Earned","Получено","Mukofot","Мукофот") to money(live?.earnedRewardsUsd)).forEach{ (label,value)->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(label,color=Color.White.copy(alpha=.72f),fontSize=11.sp,textAlign=TextAlign.Center)} }
                            }
                        }
                    }
                    live?.referrals?.let { friends ->
                        Text(language.t("Your invitations","Ваши приглашения","Takliflaringiz","Таклифларингиз"),fontSize=22.sp,fontWeight=FontWeight.Bold)
                        if(friends.isEmpty()) Card { Text(language.t("No invited friends yet","Пока нет приглашённых друзей","Hali takliflar yo‘q","Ҳали таклифлар йўқ")) }
                        friends.forEach { f -> Card {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){ Badge(CupertinoSymbol.PersonCircleFill,blue); Column(Modifier.weight(1f)){Text(f.name.ifBlank { language.t("Name not provided","Имя ещё не указано","Ism ko‘rsatilmagan","Исм кўрсатилмаган") },fontWeight=FontWeight.Bold);Text("iumrah ID · ${f.id}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(f.status=="earned")CupertinoIcon(CupertinoSymbol.CheckCircleFill,null,Modifier.size(20.dp),green) }
                            Text(when(f.status){"pending"->language.t("Awaiting payment","Ожидается оплата","To‘lov kutilmoqda","Тўлов кутилмоқда");"earned"->money(f.rewardUsd)+" · "+language.t("Credited to your balance","Начислено на баланс","Balansga qo‘shildi","Балансга қўшилди");"cancelled","ineligible"->language.t("Reward unavailable","Вознаграждение недоступно","Mukofot mavjud emas","Мукофот мавжуд эмас");else->language.t("Registered · no booking yet","Зарегистрировался · ещё нет бронирования","Ro‘yxatdan o‘tdi · hali bron yo‘q","Рўйхатдан ўтди · ҳали брон йўқ")},color=if(f.status=="earned")green else MaterialTheme.colorScheme.onSurfaceVariant)
                        } }
                    }
                    Text(language.t("How it will work","Как это будет работать","Qanday ishlaydi","Қандай ишлайди"),fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Card { listOf(CupertinoSymbol.Share to language.t("Share your personal invitation","Поделитесь персональным приглашением","Shaxsiy taklifingizni ulashing","Шахсий таклифингизни улашинг"),CupertinoSymbol.Airplane to language.t("Your friend pays for an eligible trip","Друг оплачивает подходящую поездку","Do‘stingiz safar uchun to‘lov qiladi","Дўстингиз сафар учун тўлов қилади"),CupertinoSymbol.Wallet to language.t("Rewards appear in your balance","Вознаграждение поступает на баланс","Mukofot balansingizga tushadi","Мукофот балансингизга тушади")).forEach{(icon,label)->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Badge(icon,blue);Text(label,Modifier.weight(1f),fontSize=15.sp)}} }
                }
                "balance" -> {
                    Card(radius=26,padding=20,background=Color.Black){ Row(verticalAlignment=Alignment.CenterVertically){Text(language.t("Available balance","Доступный баланс","Mavjud balans","Мавжуд баланс"),color=Color.White.copy(alpha=.75f),modifier=Modifier.weight(1f));Badge(CupertinoSymbol.Wallet,Color.White)};Text(money(live?.availableCreditUsd),color=Color.White,fontSize=42.sp,fontWeight=FontWeight.Bold);HorizontalDivider(color=Color.White.copy(alpha=.16f));Row{Text(language.t("Pending rewards","Ожидаемые бонусы","Kutilayotgan bonuslar","Кутилаётган бонуслар"),Modifier.weight(1f),color=Color.White.copy(alpha=.72f));Text(money(live?.pendingRewardsUsd),color=Color.White)} }
                    if(live==null)Text(unavailable(language),fontSize=12.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Card(Modifier.weight(1f).clickable { chrome.openRewards("coupon") },radius=24){Badge(CupertinoSymbol.Gift,Color(0xFFAF52DE),48);Text(language.t("Add coupon","Добавить купон","Kupon qo‘shish","Купон қўшиш"),fontWeight=FontWeight.SemiBold)}
                        Card(Modifier.weight(1f).clickable { chrome.openRewards("friends") },radius=24){Badge(CupertinoSymbol.PersonsFill,blue,48);Text(language.t("Invite a friend","Пригласить друга","Do‘stni taklif qilish","Дўстни таклиф қилиш"),fontWeight=FontWeight.SemiBold)}
                    }
                    Text(language.t("Transaction history","История операций","Amallar tarixi","Амаллар тарихи"),fontSize=22.sp,fontWeight=FontWeight.Bold)
                    live?.transactions?.let { txs -> if(txs.isEmpty())Card{Text(language.t("No transactions yet","Пока нет операций","Hali amallar yo‘q","Ҳали амаллар йўқ"))};txs.forEach { tx -> Card { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Badge(if(tx.amountUsd>=0)CupertinoSymbol.Plus else CupertinoSymbol.Minus,if(tx.amountUsd>=0)green else MaterialTheme.colorScheme.onSurfaceVariant);Column(Modifier.weight(1f)){Text(if(tx.amountUsd>=0)language.t("Reward","Начисление","Bonus","Бонус")else language.t("Debit / reversal","Списание / возврат","Hisobdan chiqarish","Ҳисобдан чиқариш"),fontWeight=FontWeight.Bold);Text(tx.createdAt.take(10),fontSize=12.sp)};Text(money(tx.amountUsd),fontWeight=FontWeight.Bold)} } } }
                }
                "coupon" -> {
                    var code by remember { mutableStateOf("") }
                    Card { Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Badge(CupertinoSymbol.Gift,Color(0xFFAF52DE),72)
                        Text(language.t("Enter your coupon code","Введите код купона","Kupon kodini kiriting","Купон кодини киритинг"),fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                        OutlinedTextField(code,{code=it.uppercase(Locale.ROOT).take(64)},Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp),label={Text(language.t("Coupon code","Код купона","Kupon kodi","Купон коди"))})
                        Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp)){Text(language.t("Apply coupon","Применить купон","Kuponni qo‘llash","Купонни қўллаш"))}
                        Text(language.t("Previously issued codes can be used at booking payment. New coupon redemption is not yet available.","Ранее выданные коды можно использовать при оплате бронирования. Новые купоны пока недоступны.","Avval berilgan kodlardan bron to‘lovida foydalanish mumkin. Yangi kuponlar hali mavjud emas.","Аввал берилган кодлардан брон тўловида фойдаланиш мумкин. Янги купонлар ҳали мавжуд эмас."),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
                else -> {
                    Card(radius=26,padding=18,background=Color.Black) { Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){IumrahOrbitV5(minOf(230.dp,maxWidth),lightOnDark=true,services=rewardsServices,centerSymbol=CupertinoSymbol.StarFill,accessibilityTitle=title)};Text(language.t("Travel more. Unlock more.","Путешествуйте. Открывайте больше.","Ko‘proq sayohat, ko‘proq imtiyoz.","Кўпроқ саёҳат, кўпроқ имтиёз."),color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center);Text(language.t("Benefits for journeys and recommendations","Преимущества за поездки и рекомендации","Safarlar va tavsiyalar uchun imtiyozlar","Сафарлар ва тавсиялар учун имтиёзлар"),color=Color.White.copy(alpha=.72f),fontSize=15.sp,textAlign=TextAlign.Center)} }
                    val tier=live?.tier?.lowercase()?.takeIf { it in listOf("member","plus","elite") }
                    if(tier!=null){Card{Text("iumrah "+tier.replaceFirstChar { it.uppercase() },fontSize=26.sp,fontWeight=FontWeight.Bold)};Text(language.t("Completed trips / paid referrals","Завершённые поездки / оплаченные приглашения","Yakunlangan safarlar / to‘langan takliflar","Якунланган сафарлар / тўланган таклифлар")+": ${live?.completedTrips?:"—"} / ${live?.paidReferrals?:"—"}",fontSize=15.sp)}else Text(unavailable(language),fontSize=12.sp)
                    val conditions=listOf(language.t("After registration","После регистрации","Ro‘yxatdan o‘tgach","Рўйхатдан ўтгач"),language.t("1 completed trip or 3 successful referrals","1 завершённая поездка или 3 успешных приглашения","1 yakunlangan safar yoki 3 muvaffaqiyatli taklif","1 якунланган сафар ёки 3 муваффақиятли таклиф"),language.t("3 completed trips or 7 successful referrals","3 завершённые поездки или 7 успешных приглашений","3 yakunlangan safar yoki 7 muvaffaqiyatli taklif","3 якунланган сафар ёки 7 муваффақиятли таклиф"))
                    listOf("Member","Plus","Elite").forEachIndexed { i,name -> val tint=listOf(Color.Gray,blue,Color(0xFFFF9500))[i]; val unlocked=listOf("member","plus","elite").indexOf(tier.orEmpty())>=i; Card(Modifier.border(1.dp,tint.copy(alpha=.19f),RoundedCornerShape(24.dp)),radius=24,background=tint.copy(alpha=.10f)){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Badge(listOf(CupertinoSymbol.PersonCircleFill,CupertinoSymbol.StarFill,CupertinoSymbol.CrownFill)[i],tint,48);Column(Modifier.weight(1f)){Text("iumrah $name",fontSize=20.sp,fontWeight=FontWeight.Bold);Text(language.t("Level","Уровень","Daraja","Даража")+" 0${i+1}",fontSize=12.sp)};CupertinoIcon(if(unlocked)CupertinoSymbol.CheckCircleFill else CupertinoSymbol.Lock,null,Modifier.size(18.dp),if(unlocked)green else MaterialTheme.colorScheme.onSurfaceVariant)};Text(conditions[i],fontSize=15.sp)} }
                    Text(language.t("Levels track your progress. Additional discounts will be announced separately.","Уровни показывают ваш прогресс. Дополнительные скидки будут объявлены отдельно.","Darajalar rivojlanishingizni ko‘rsatadi. Qo‘shimcha chegirmalar alohida e’lon qilinadi.","Даражалар ривожланишингизни кўрсатади. Қўшимча чегирмалар алоҳида эълон қилинади."),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    CTA(language.t("Invite a friend","Пригласить друга","Do‘stni taklif qilish","Дўстни таклиф қилиш"),{chrome.openRewards("friends")})
                }
            }
            if(!state.isAuthenticated) CTA(language.t("Sign in","Войти","Kirish","Кириш"),{chrome.navigate(AppTab.ACCOUNT)})
        }
    }
}
