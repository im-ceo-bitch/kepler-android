package com.iumrah.beta.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.models.account.IumrahAccountProfile
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.util.Locale

private fun t(l:AppLanguage,r:String,e:String,u:String,c:String)=when(l){AppLanguage.RUSSIAN->r;AppLanguage.UZBEK->u;AppLanguage.UZBEK_CYRILLIC->c;AppLanguage.ENGLISH,AppLanguage.ARABIC,AppLanguage.TURKISH,AppLanguage.INDONESIAN,AppLanguage.MALAY->e}
private fun money(value:Double?)=value?.takeIf{it.isFinite()}?.let{String.format(Locale.US,"$%,.2f",it)}?:"—"
private fun tier(raw:String?)=raw?.lowercase(Locale.ROOT)?.takeIf{it in listOf("member","plus","elite")}?.replaceFirstChar{it.titlecase(Locale.ROOT)}
@Composable internal fun DashboardProfileFooter(l:AppLanguage,p:IumrahAccountProfile,level:String?,open:()->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha=.075f)).border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(22.dp)).clickable(onClick=open).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){
        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha=.10f)),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.PersonCircleFill,null,Modifier.size(43.dp),Color.White)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(p.displayName.ifBlank{listOf(p.firstName,p.lastName).joinToString(" ").trim()},fontSize=20.sp,fontWeight=FontWeight.Bold,color=Color.White,maxLines=2);tier(level)?.let{Text("iumrah $it",color=Color.White.copy(alpha=.78f),fontSize=14.sp)};Text(t(l,"Управление аккаунтом","Manage your account","Akkauntni boshqarish","Аккаунтни бошқариш"),color=Color.White.copy(alpha=.72f),fontSize=12.sp)}
        CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(15.dp),Color.White.copy(alpha=.85f))
    }
}
@Composable private fun DashboardTile(modifier:Modifier=Modifier,colors:List<Color>,ink:Color,open:()->Unit,content:@Composable ColumnScope.()->Unit){Column(modifier.clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(colors)).clickable(onClick=open).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){CompositionLocalProvider(LocalContentColor provides ink){content()}}}
@Composable internal fun DashboardBalance(l:AppLanguage,account:IumrahAccountStore,chrome:AppChromeStore,revision:Int){
    val snapshot by account.dashboardRewards.collectAsState();val live=snapshot.data
    DashboardTile(Modifier.fillMaxWidth(),listOf(Color(0xff215c4a),Color(0xff092921)),Color.White,{chrome.openRewards("balance")}){
        Row(verticalAlignment=Alignment.CenterVertically){CupertinoIcon(CupertinoSymbol.Wallet,null,Modifier.size(22.dp),Color.White);Spacer(Modifier.width(8.dp));Text("iumrah Balance",fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(20.dp),Color.White)}
        Text(money(live?.availableCreditUsd),fontSize=42.sp,fontWeight=FontWeight.SemiBold)
        Text(when{snapshot.stale->t(l,"Последние проверенные данные · обновление недоступно","Last verified data · refresh unavailable","Oxirgi tasdiqlangan ma’lumot","Охирги тасдиқланган маълумот");snapshot.loading&&live==null->t(l,"Загрузка…","Loading…","Yuklanmoqda…","Юкланмоқда…");live==null->t(l,"Данные недоступны","Data unavailable","Ma’lumot mavjud emas","Маълумот мавжуд эмас");else->t(l,"Доступно для следующей поездки","Available for your next trip","Keyingi safar uchun mavjud","Кейинги сафар учун мавжуд")},color=Color.White.copy(alpha=.78f))
        HorizontalDivider(color=Color.White.copy(alpha=.19f));Row{Text(t(l,"История операций","Transaction history","Amallar tarixi","Амаллар тарихи"),Modifier.weight(1f));CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(16.dp),Color.White)}
    }
}
@Composable private fun FriendsTile(l:AppLanguage,modifier:Modifier,chrome:AppChromeStore){val ink=Color(0xff1f408a);DashboardTile(modifier,listOf(Color(0xffe8f0ff),Color(0xffc9d9fc)),ink,{chrome.openRewards("friends")}){Row{CupertinoIcon(CupertinoSymbol.Gift,null,Modifier.size(24.dp),ink);Spacer(Modifier.weight(1f));CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(16.dp),ink)};Text("+\$100",fontSize=32.sp,fontWeight=FontWeight.Bold);Text(t(l,"Пригласите друга","Invite a friend","Do‘stingizni taklif qiling","Дўстингизни таклиф қилинг"),fontWeight=FontWeight.Bold);Text(t(l,"За каждую первую подходящую оплаченную поездку друга","For each friend's first eligible paid trip","Do‘stning birinchi mos to‘langan safari uchun","Дўстнинг биринчи мос тўланган сафари учун"),fontSize=13.sp,color=ink.copy(alpha=.82f))}}
@Composable private fun RewardsTile(l:AppLanguage,modifier:Modifier,account:IumrahAccountStore,chrome:AppChromeStore,compact:Boolean=true){val snapshot by account.dashboardRewards.collectAsState();val level=tier(snapshot.data?.tier);val ink=Color(0xff5e3d17);DashboardTile(modifier,listOf(Color(0xfffcf2d6),Color(0xffe8ca8c)),ink,{chrome.openRewards()}){Row{CupertinoIcon(CupertinoSymbol.StarFill,null,Modifier.size(24.dp),ink);Spacer(Modifier.weight(1f));CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(16.dp),ink)};Text(level?:"Rewards",fontSize=if(compact)27.sp else 35.sp,fontWeight=FontWeight.Bold);Text("iumrah Rewards",fontWeight=FontWeight.Bold);Text(if(snapshot.stale)t(l,"Последние проверенные данные","Last verified data","Oxirgi tasdiqlangan ma’lumot","Охирги тасдиқланган маълумот")else t(l,"Ваши персональные преимущества","Your personal benefits","Shaxsiy imtiyozlaringiz","Шахсий имтиёзларингиз"),fontSize=13.sp);if(!compact)Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("Member","Plus","Elite").forEachIndexed{i,name->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(if(level!=null&&i<=listOf("Member","Plus","Elite").indexOf(level))ink else ink.copy(alpha=.15f)));Text(name,fontSize=10.sp)}}}}}
@Composable internal fun DashboardBenefits(l:AppLanguage,account:IumrahAccountStore,chrome:AppChromeStore){if(LocalDensity.current.fontScale>=1.3f){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){FriendsTile(l,Modifier.fillMaxWidth(),chrome);RewardsTile(l,Modifier.fillMaxWidth(),account,chrome,compact=false)}}else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){FriendsTile(l,Modifier.weight(1f).heightIn(min=184.dp),chrome);RewardsTile(l,Modifier.weight(1f).heightIn(min=184.dp),account,chrome)}}
@Composable internal fun DashboardIdentity(l:AppLanguage,open:()->Unit){
    DashboardTile(Modifier.fillMaxWidth(),listOf(Color(0xff30364d),Color(0xff13141f)),Color.White,open){
        Row(verticalAlignment=Alignment.CenterVertically){Text("iumrah ID",fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));CupertinoIcon(CupertinoSymbol.QrCode,null,Modifier.size(26.dp),Color.White)}
        Image(painterResource(R.drawable.iumrah_world_dots),null,Modifier.fillMaxWidth().height(110.dp),alpha=.55f,contentScale=ContentScale.Fit)
        Row(verticalAlignment=Alignment.CenterVertically){Text(t(l,"Ваша персональная карта паломника","Your personal pilgrim card","Shaxsiy ziyoratchi kartangiz","Шахсий зиёратчи картангиз"),Modifier.weight(1f),color=Color.White.copy(alpha=.78f));CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(16.dp),Color.White)}
    }
}
@Composable internal fun DashboardServices(l:AppLanguage,chrome:AppChromeStore){
    val green=Color(0xff1f5c3d);val violet=Color(0xff573885)
    val mobile:@Composable (Modifier)->Unit={m->DashboardTile(m,listOf(Color(0xffe8f7ed),Color(0xffc7e3d4)),green,chrome::openAccountESIM){CupertinoIcon(CupertinoSymbol.SimCard,null,Modifier.size(26.dp),green);Text("iumrah Mobile",fontWeight=FontWeight.Bold);Text(t(l,"Ваша eSIM","Your eSIM","Sizning eSIM","Сизнинг eSIM"))}}
    val care:@Composable (Modifier)->Unit={m->DashboardTile(m,listOf(Color(0xfff5edfc),Color(0xffdecff2)),violet,{chrome.navigate(AppTab.CARE)}){CupertinoIcon(CupertinoSymbol.ChatBubble,null,Modifier.size(26.dp),violet);Text("iumrah Care",fontWeight=FontWeight.Bold);Text(t(l,"Поддержка 24/7","Support 24/7","Yordam 24/7","Ёрдам 24/7"))}}
    if(LocalDensity.current.fontScale>=1.3f){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){mobile(Modifier.fillMaxWidth());care(Modifier.fillMaxWidth())}}else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){mobile(Modifier.weight(1f).heightIn(min=130.dp));care(Modifier.weight(1f).heightIn(min=130.dp))}
}

/** Promotional artwork only: not a wallet balance or issued coupon. */
@Composable internal fun DashboardFriendsPromotion(l:AppLanguage,chrome:AppChromeStore){
    DashboardTile(Modifier.fillMaxWidth(),listOf(Color(0xff1f1f1f),Color(0xff090909),Color.Black),Color.White,{chrome.openRewards("friends")}){
        Row(verticalAlignment=Alignment.CenterVertically){Text("iumrah FRIENDS",fontSize=12.sp,letterSpacing=1.6.sp,color=Color.White.copy(alpha=.65f),fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(20.dp),Color.White)}
        FriendsCouponArtwork(chrome)
        Text(t(l,"Приглашайте друзей. Получайте по \$100.","Invite friends. Earn \$100 each.","Har bir do‘st uchun \$100","Ҳар бир дўст учун \$100"),fontSize=28.sp,fontWeight=FontWeight.Bold)
        Text(t(l,"Получайте бонус на баланс после подтверждённой оплаты первой подходящей поездки каждого приглашённого друга.","Earn a balance reward after confirmed payment for each invited friend's first eligible trip.","Har bir taklif qilingan do‘stning birinchi mos safari to‘lovi tasdiqlangach, balansingizga bonus oling.","Ҳар бир таклиф қилинган дўстнинг биринчи мос сафари тўлови тасдиқлангач, балансингизга бонус олинг."),color=Color.White.copy(alpha=.72f))
        Row(Modifier.fillMaxWidth().heightIn(min=50.dp).clip(CircleShape).background(Color.White).padding(14.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){Text(t(l,"Пригласить друга","Invite a friend","Do‘stni taklif qilish","Дўстни таклиф қилиш"),color=Color.Black,fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(8.dp));CupertinoIcon(CupertinoSymbol.ArrowRight,null,Modifier.size(16.dp),Color.Black)}
    }
}
@Composable internal fun DashboardGuestRewards(l:AppLanguage,account:IumrahAccountStore,chrome:AppChromeStore){RewardsTile(l,Modifier.fillMaxWidth(),account,chrome,compact=false)}
@Composable private fun FriendsCouponArtwork(chrome:AppChromeStore){
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val navigation by chrome.state.collectAsState()
    var foreground by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,_->foreground=lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
    val context=androidx.compose.ui.platform.LocalContext.current
    val reduce=remember(foreground){!android.animation.ValueAnimator.areAnimatorsEnabled() || (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice==true}
    val active=foreground&&(navigation.currentTab==AppTab.ACCOUNT||navigation.currentTab==AppTab.HOME)&&navigation.route==com.iumrah.beta.core.navigation.AppRoute.Root
    var phase by remember{mutableFloatStateOf(4f)}
    LaunchedEffect(active,reduce){if(!active||reduce){phase=4f;return@LaunchedEffect};val start=System.nanoTime();while(true){phase=((System.nanoTime()-start)/1_000_000_000.0%10).toFloat();kotlinx.coroutines.delay(34)}}
    fun smooth(x:Float):Float {val a=x.coerceIn(0f,1f);return a*a*(3f-2*a)}
    BoxWithConstraints(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center){
        val scale=minOf(1f,maxWidth.value/292f)
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()){
            val span=maxOf(size.width,size.height)*.78f
            repeat(7){i->val p=(phase/7f+i/7f)%1f;val r=18.dp.toPx()+p*span;val alpha=(.14*kotlin.math.sin(p*Math.PI).coerceAtLeast(0.0)).toFloat();val path=androidx.compose.ui.graphics.Path();repeat(97){j->val a=j/96.0*2*Math.PI;val ripple=(kotlin.math.sin(a*3+phase*.6)*3.5*kotlin.math.sin(p*Math.PI)).toFloat()*density;val x=size.width*.5f+kotlin.math.cos(a).toFloat()*(r+ripple);val y=size.height*.48f+kotlin.math.sin(a).toFloat()*(r+ripple)*.73f;if(j==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close();drawPath(path,Color(0xffadc9ff).copy(alpha=alpha),style=androidx.compose.ui.graphics.drawscope.Stroke(.8.dp.toPx()))}
        }
        repeat(3){index->
            val presence=smooth((phase-index*.72f)/1f)*(1-smooth((phase-(9f-index*.52f))/.9f))
            val shades=when(index){0->listOf(Color(0xff4d6394),Color(0xff1f3357));1->listOf(Color(0xff87a6d9),Color(0xff3d5c94));else->listOf(Color(0xfff5faff),Color(0xffb3ccee),Color(0xffebf2ff))};val ink=if(index==2)Color(0xff1a335e)else Color.White
            val x=listOf(-26f,27f,0f)[index]+(1-presence)*24f;val y=listOf(-37f,-7f,27f)[index]+(1-presence)*27f+(kotlin.math.sin(phase*Math.PI/5+index*.65)*2.5).toFloat()
            Row(Modifier.offset((x*scale).dp,(y*scale).dp).size((168*scale).dp,(83*scale).dp).graphicsLayer{alpha=presence;rotationZ=listOf(-15f,13f,-5f)[index]+(1-presence)*7;rotationY=(1-presence)*13;scaleX=.88f+.12f*presence;scaleY=scaleX}.clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(shades)).border(1.dp,Color.White.copy(alpha=.34f),RoundedCornerShape(15.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("\$100",fontSize=(30*scale).sp,fontWeight=FontWeight.Bold,color=ink);Text("iumrah FRIENDS",fontSize=(8*scale).sp,fontWeight=FontWeight.SemiBold,color=ink.copy(alpha=.72f),maxLines=1)};Box(Modifier.width(1.dp).fillMaxHeight().background(ink.copy(alpha=.18f)));Box(Modifier.width((38*scale).dp),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.Gift,null,Modifier.size(19.dp),ink)}}
        }
    }
}

@Composable internal fun DashboardGroup(title:String,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text(title,Modifier.padding(horizontal=3.dp),fontSize=18.sp,fontWeight=FontWeight.Bold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal=14.dp,vertical=5.dp),content=content)
    }
}
@Composable internal fun DashboardRow(symbol:CupertinoSymbol,title:String,detail:String?=null,open:()->Unit){
    Row(Modifier.fillMaxWidth().clickable(onClick=open).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.size(width=34.dp,height=38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){CupertinoIcon(symbol,null,Modifier.size(17.dp))}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,fontSize=15.sp,fontWeight=FontWeight.SemiBold);detail?.let{Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(12.dp),MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun DashboardDivider(){HorizontalDivider(Modifier.padding(start=46.dp),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.10f))}
