package com.iumrah.beta.ui.components

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.iumrah.beta.R
import com.iumrah.beta.core.geometry.*
import com.iumrah.beta.core.design.IumrahDesign
import kotlin.math.*

private object LaunchGate { private var claimed=false; @Synchronized fun claim(): Boolean { if(claimed) return false; claimed=true; return true } }
private val launchBackground=Color(0xFF080808)
private val launchRoute=IumrahGlobeGeometry.route(GlobeVector.geographic(41.2579,69.2812),GlobeVector.geographic(21.6796,39.1565),48)

/** Cold launch only; mount the real root at 840ms, finish at 1200ms; never wait for network. */
@Composable internal fun IumrahSignatureLaunchHost(onboarded: Boolean, skip: Boolean, content: @Composable () -> Unit) {
    val context=LocalContext.current
    val view=LocalView.current
    val reduce=remember(context) { runCatching { Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }.getOrDefault(false) }
    var visible by remember { mutableStateOf(LaunchGate.claim() && !skip) }
    var time by remember { mutableDoubleStateOf(0.0) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { visible=false }
    val window=(context as? android.app.Activity)?.window
    DisposableEffect(visible,window) {
        val controller=window?.let { androidx.core.view.WindowCompat.getInsetsController(it,view) }
        val previous=controller?.isAppearanceLightStatusBars
        if(visible) controller?.isAppearanceLightStatusBars=false
        onDispose { if(visible && previous!=null) controller?.isAppearanceLightStatusBars=previous }
    }
    LaunchedEffect(visible,skip) {
        if(skip) { visible=false; return@LaunchedEffect }
        if(!visible) return@LaunchedEffect
        val duration=if(reduce) .18 else IumrahGlobeGeometry.duration
        val start=withFrameNanos { it }; var first=false; var second=false
        while(time<duration) {
            val elapsed=withFrameNanos { ((it-start)/1e9).coerceAtMost(duration) }
            if(elapsed-time>=1.0/30 || elapsed>=duration) time=elapsed
            if(!reduce && !first && elapsed>=.08) { first=true; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            if(!reduce && !second && elapsed>=.82) { second=true; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        }
        visible=false
    }
    Box(Modifier.fillMaxSize().background(launchBackground)) {
        if(!visible || reduce || time>=IumrahGlobeGeometry.mountContent) content()
        if(visible) {
            // Consume touches until handoff; accessibility remains on root once mounted.
            IumrahLaunchLayer(time,reduce,onboarded)
        }
    }
}
@Composable private fun IumrahLaunchLayer(time: Double,reduce: Boolean,onboarded: Boolean) {
    val context=LocalContext.current
    val dots=remember(context) { IumrahGlobeAssets.dots(context) }
    val planeFont=remember(context) { androidx.core.content.res.ResourcesCompat.getFont(context,R.font.cupertino_icons) ?: error("Cupertino icon font unavailable") }
    fun stage(a: Double,b: Double)=IumrahGlobeGeometry.stage(time,a,b).toFloat()
    val fade=if(reduce) stage(.04,.18) else stage(.98,1.2)
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics {}.background(launchBackground.copy(alpha=1-fade))) {
        val w=maxWidth; val h=maxHeight
        val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val handoff=if(onboarded && !reduce) stage(.87,1.12) else 0f
        val reveal=if(reduce) 1f else stage(.74,.88)
        val targetY=top+IumrahDesign.SignatureHeaderTop+IumrahDesign.SignatureLogoHeight/2
        val cy=h*.48f+(targetY-h*.48f)*handoff
        val logoWidth=if(reduce) minOf(220.dp,w*.62f) else minOf(w*.66f,248.dp)
        val targetWidth=minOf(IumrahDesign.SignatureLogoWidth,maxOf(90.dp,w-220.dp))
        val lw=logoWidth+(targetWidth-logoWidth)*handoff
        val lh=82.dp+(IumrahDesign.SignatureLogoHeight-82.dp)*handoff
        if(!reduce) {
            Canvas(Modifier.fillMaxSize()) {
                val pulse=stage(.01,.25); val alpha=1-stage(.12,.30); val center=Offset(size.width/2,size.height*.48f)
                repeat(2) { i -> drawCircle(Color.White.copy(alpha=alpha*(if(i==0) .24f else .12f)),(5+pulse*(20+i*14)).dp.toPx(),center,style=Stroke(.8.dp.toPx())) }
                drawCircle(Color.White.copy(alpha=alpha),2.5.dp.toPx(),center)
                val emergence=stage(.06,.43); val d=minOf(size.width*.8f,size.height*.49f,390.dp.toPx())*(.06f+.94f*emergence)
                val opacity=emergence*(1-.82f*reveal)*(1-handoff)*(1-fade)
                if(opacity>0 && d>0) {
                    val p=IumrahGlobeGeometry.launchProjection(time); val wave=stage(.13,.83); val dim=stage(.73,.89)
                    translate((size.width-d)/2,cy.toPx()-d/2) {
                        drawCircle(Brush.radialGradient(listOf(Color(.09f,.09f,.09f),Color(.02f,.02f,.02f)),Offset(d/2,d/2),d/2),d/2,Offset(d/2,d/2),alpha=opacity)
                        val paths=List(6) { Path() }
                        for(v in dots) { val q=p.project(v); if(q.depth<=0) continue
                            val light=exp(-((q.x-(-1.1+2.2*wave))*2.6).pow(2)); val band=min(5,((.22+q.depth*.42+light*.35).coerceIn(0.0,1.0)*6).toInt())
                            val r=max(.38.dp.toPx(),d*.0032f*(.58+q.depth*.42).toFloat()); val x=(q.u*d).toFloat(); val y=(q.v*d).toFloat()
                            paths[band].addOval(Rect(x-r,y-r,x+r,y+r))
                        }
                        paths.forEachIndexed { i,path -> drawPath(path,Color.White.copy(alpha=((.15f+i*.15f)*(1-.42f*dim)*opacity).coerceIn(0f,1f))) }
                        val progress=stage(.25,.73); val routeAlpha=(1-stage(.73,.85))*opacity
                        if(progress>0 && routeAlpha>0) {
                            val points=launchRoute.map { p.project(it).let { q -> Offset((q.u*d).toFloat(),(q.v*d).toFloat()) } }
                            val position=progress*(points.size-1); val index=min(points.size-2,position.toInt()); val a=points[index]; val b=points[index+1]; val tip=a+(b-a)*(position-index)
                            val path=Path().apply { moveTo(points.first().x,points.first().y); points.drop(1).take(index).forEach { lineTo(it.x,it.y) }; lineTo(tip.x,tip.y) }
                            drawPath(path,Color.White.copy(alpha=routeAlpha*.9f),style=Stroke(1.2.dp.toPx(),cap=StrokeCap.Round,pathEffect=PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),3.dp.toPx()))))
                            listOf(points.first(),points.last()).forEach { drawCircle(Color.White.copy(alpha=routeAlpha),2.8.dp.toPx(),it) }
                            rotate(Math.toDegrees(atan2((b.y-a.y).toDouble(),(b.x-a.x).toDouble())).toFloat(),tip) {
                                drawGlobePlane(planeFont,tip,15.dp.toPx(),Color.White.copy(alpha=routeAlpha))
                            }
                        }
                    }
                }
            }
            if(handoff>0) IumrahSignatureWorldBackground(Modifier.fillMaxWidth().height(220.dp).alpha(handoff*(1-fade)),mapOpacity=.33f,waveIntensity=0f)
        }
        Image(painterResource(R.drawable.iumrah_header_wordmark_light),null,Modifier.offset(x=(w-lw)/2,y=cy-lh/2).width(lw).height(lh).alpha(reveal*(1-fade)),contentScale=ContentScale.Fit,colorFilter=ColorFilter.tint(Color.White))
        // No click is passed through to a CTA while launch artwork overlays it.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while(true) { awaitPointerEvent().changes.forEach { it.consume() } } } })
    }
}
