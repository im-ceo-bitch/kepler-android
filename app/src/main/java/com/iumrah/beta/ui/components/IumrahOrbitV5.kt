package com.iumrah.beta.ui.components

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.hotels.nuiteeText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.iumrah.beta.R
import com.iumrah.beta.domain.orbit.OrbitV5Geometry as G
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

internal val orbitShowcase = listOf(CupertinoSymbol.Airplane, CupertinoSymbol.BuildingFill, CupertinoSymbol.Car,
    CupertinoSymbol.CheckCircleFill, CupertinoSymbol.SuitcaseFill, CupertinoSymbol.MapFill, CupertinoSymbol.PersonsFill, CupertinoSymbol.SignalWave)
private fun orbitColor(angle: Double): Color { val (r,g,b)=G.color(angle); return Color(r,g,b) }

/** Shared native Orbit V5. The logo remains stationary; no animated bitmap or network media. */
@Composable
internal fun IumrahOrbitV5(diameter: Dp, modifier: Modifier = Modifier, lightOnDark: Boolean = false,
    pinnedSymbol: CupertinoSymbol? = null, mergeToCenter: Boolean = false,
    services: List<CupertinoSymbol> = orbitShowcase, active: Boolean = true, accessibilityTitle: String = "iumrah Smart") {
    val owner=LocalLifecycleOwner.current
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _, _ -> foreground=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val context=LocalContext.current
    // API 26+ (the application's minSdk). Re-evaluate on resume.
    val reduceMotion=remember(context,foreground) { !ValueAnimator.areAnimatorsEnabled() }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var transition by remember(pinnedSymbol,mergeToCenter) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(active,foreground,reduceMotion,pinnedSymbol,mergeToCenter,services.isEmpty()) {
        if (!active || !foreground || reduceMotion || services.isEmpty()) return@LaunchedEffect
        var last=withFrameNanos { it }
        while (true) {
            if (mergeToCenter && transition>=1.8 || !mergeToCenter && pinnedSymbol!=null && transition>=.55) break
            val now=withFrameNanos { it }; val dt=((now-last)/1e9).coerceIn(0.0,.1); last=now
            if(pinnedSymbol==null) elapsed+=dt
            if(mergeToCenter || pinnedSymbol!=null) transition+=dt
        }
    }
    val rotation=G.rotation(if(reduceMotion) 0.0 else elapsed)
    val assembly=if(mergeToCenter) (if(reduceMotion) 1.0 else (transition/1.8).coerceIn(0.0,1.0)) else 0.0
    val merge=G.ease((assembly-.32)/.68)
    val reveal=when { mergeToCenter -> null; pinnedSymbol!=null -> G.pinnedReveal(rotation,if(reduceMotion) 1.0 else G.ease(transition/.55)); services.isEmpty() -> null; else -> G.reveal(elapsed,rotation,still=reduceMotion) }
    Box(modifier.size(diameter).semantics { contentDescription=accessibilityTitle },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val scale=size.minDimension/360f
            for(row in G.radii.indices) for(dot in 0 until G.pointsPerRing) {
                val p=G.point(row,dot,rotation); val v=G.visibility(row,dot,p,reveal)*(1-.65*G.ease(assembly/.28))
                if(v>.001 && merge<1) drawCircle(orbitColor(p.angle).copy(alpha=(v*(1-merge)).toFloat()),
                    radius=(G.dotRadii[row]*v*(1-.8*merge)*scale).toFloat(), center=Offset(((180+(p.x-180)*(1-merge))*scale).toFloat(),((180+(p.y-180)*(1-merge))*scale).toFloat()))
            }
        }
        if(reveal!=null) {
            val symbol=pinnedSymbol ?: services.takeIf { it.isNotEmpty() }?.let { it[reveal.slot % it.size] }
            if(symbol!=null) {
                val diameterFraction=(2*reveal.radius/360).toFloat()
                Box(Modifier.align(Alignment.TopStart).offset(diameter*((reveal.center.x-reveal.radius)/360).toFloat(),diameter*((reveal.center.y-reveal.radius)/360).toFloat())
                    .size(diameter*diameterFraction).background(orbitColor(reveal.center.angle),CircleShape),contentAlignment=Alignment.Center) {
                    CupertinoIcon(symbol,null,Modifier.size(diameter*(32/360.0*(.55+.45*reveal.expansion)).toFloat()).alpha(reveal.symbolOpacity.toFloat()),Color.White)
                }
            }
        }
        if(mergeToCenter && !reduceMotion && assembly<1) {
            val appear=G.ease(assembly/.28); val disappear=1-G.ease((merge-.58)/.42); val bubble=36*appear*(1-.55*merge)
            services.take(12).forEachIndexed { i,symbol ->
                val p=G.assemblyPoint(i,minOf(12,services.size),rotation,merge)
                Box(Modifier.align(Alignment.TopStart).offset(diameter*((p.x-bubble/2)/360).toFloat(),diameter*((p.y-bubble/2)/360).toFloat())
                    .size(diameter*(bubble/360).toFloat()).alpha((appear*disappear).toFloat()).background(orbitColor(p.angle),CircleShape),contentAlignment=Alignment.Center) {
                    CupertinoIcon(symbol,null,Modifier.size(diameter*(21/360.0*(1-.55*merge)).toFloat()).alpha(appear.toFloat()),Color.White)
                }
            }
        }
        Image(painterResource(R.drawable.iumrah_header_wordmark_light),null,Modifier.width(diameter*.38f),
            colorFilter=ColorFilter.tint(if(lightOnDark) Color.White else Color.Black))
    }
}

/** The same selected services used by iOS IumrahOrbitService.selectedForJourney. */
internal fun selectedOrbitServices(state: JourneyState): List<CupertinoSymbol> = buildList {
    if(state.selectedJourney != null || state.selectedOutboundJourney != null || state.nuitee?.flights.orEmpty().isNotEmpty() || state.hasCompleteStagedFlightSelection) add(CupertinoSymbol.Airplane)
    if(state.makkahHotel != null || state.madinahHotel != null || state.nuitee?.rooms.orEmpty().isNotEmpty()) add(CupertinoSymbol.BuildingFill)
    if(state.transferSelectionConfirmed) add(CupertinoSymbol.Car)
    if(state.haramainTrainSelected) add(CupertinoSymbol.Route)
}

/** One-shot visual moment only. Never creates a booking or initiates a quote. */
@Composable internal fun IumrahClientPackageMoment(language: AppLanguage, services: List<CupertinoSymbol>) {
    if(services.isEmpty()) return
    var merging by remember(services) { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    LaunchedEffect(services) { if(!merging) { delay(700); merging=true } }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(28.dp)).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp),contentAlignment=Alignment.Center) {
            IumrahOrbitV5(minOf(220.dp,maxWidth),lightOnDark=MaterialTheme.colorScheme.background==Color.Black,services=services,mergeToCenter=merging,accessibilityTitle="iumrah")
        }
        Text(nuiteeText(language,"Ваши услуги в одном пакете","Your services in one package","Xizmatlaringiz bitta paketda","Хизматларингиз битта пакетда"),fontSize=19.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
        Text(nuiteeText(language,"Бронирование ещё не создано","Booking has not been created yet","Bron hali yaratilmagan","Брон ҳали яратилмаган"),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f),textAlign=TextAlign.Center)
    }
}
