package com.iumrah.beta.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.geometry.*
import com.iumrah.beta.R
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.CupertinoGlyphCodepoints
import androidx.core.content.res.ResourcesCompat
import kotlin.math.*

/** Identical Natural Earth coordinates, 112-row hemisphere lattice and orthographic horizon clipping as iOS. */
internal object IumrahGlobeAssets {
    private var land: List<GlobeVector>? = null
    private var dots: List<GlobeVector>? = null
    private var airports: Map<String, GlobeVector>? = null
    @Synchronized fun land(context: Context) = land ?: IumrahGlobeGeometry.decode(context.assets.open("iumrah_hemisphere_land.bin").use { it.readBytes() }).also { land=it }
    @Synchronized fun dots(context: Context) = dots ?: IumrahGlobeGeometry.decode(context.assets.open("iumrah_launch_land.bin").use { it.readBytes() }).also { dots=it }
    @Synchronized fun airport(context: Context, code: String): GlobeVector? {
        val catalogue=airports ?: context.assets.open("iumrah_globe_airports.csv").bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.associate { line -> val p=line.split(','); p[0] to GlobeVector.geographic(p[1].toDouble(),p[2].toDouble()) } }.also { airports=it }
        return catalogue[code.uppercase()]
    }
}
internal fun DrawScope.drawGlobePlane(font: android.graphics.Typeface,center: Offset,fontSize: Float,tint: Color) {
    val glyph=String(Character.toChars(CupertinoGlyphCodepoints.bySymbol.getValue(CupertinoSymbol.Airplane)))
    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG).apply { typeface=font; color=tint.toArgb(); textSize=fontSize; textAlign=android.graphics.Paint.Align.CENTER }
    val bounds=android.graphics.Rect(); paint.getTextBounds(glyph,0,glyph.length,bounds)
    drawContext.canvas.nativeCanvas.drawText(glyph,center.x-(bounds.left+bounds.right)/2f,center.y-(bounds.top+bounds.bottom)/2f,paint)
}
private fun gray(value: Double) = Color(value.toFloat(),value.toFloat(),value.toFloat())
private fun GlobePoint.offset(d: Float) = Offset((u*d).toFloat(),(v*d).toFloat())
private fun Path.addPolygon(points: List<GlobePoint>) {
    if(points.isEmpty()) return
    moveTo(points[0].u.toFloat(),points[0].v.toFloat()); points.drop(1).forEach { lineTo(it.u.toFloat(),it.v.toFloat()) }; close()
}
private val FlightPlaneBackdrop=Color(0xFF111111)
private val pathCache = object : LinkedHashMap<GlobeProjection, Path>(16,.75f,true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GlobeProjection,Path>?) = size>12
}
@Synchronized private fun landPath(projection: GlobeProjection, vectors: List<GlobeVector>): Path = pathCache[projection] ?: Path().apply {
    for (i in vectors.indices step 3) addPolygon(projection.polygon(vectors[i],vectors[i+1],vectors[i+2]))
}.also { pathCache[projection]=it }
private val dotGrid by lazy {
    Path().apply { val step=1f/112; for(row in 0 until 112) for(col in 0 until 112) {
        val x=(col+.5f+if(row%2==0) 0f else .5f)*step; val y=(row+.5f)*step
        val r2=(x-.5f)*(x-.5f)+(y-.5f)*(y-.5f)
        if(r2<.248f) { val r=step*(.15f+.15f*sqrt(max(0f,1-r2*4))); addOval(Rect(x-r,y-r,x+r,y+r)) }
    } }
}
@Composable internal fun IumrahFlightHemisphere(from: String,to: String,direct: Boolean,live: GlobeVector? = null, origin: GlobeVector? = null, destination: GlobeVector? = null) {
    val context=LocalContext.current
    val font=remember(context) { ResourcesCompat.getFont(context,R.font.cupertino_icons) ?: error("Cupertino icon font unavailable") }
    val route=remember(from,to,direct,origin,destination) { val a=origin ?: IumrahGlobeAssets.airport(context,from); val b=destination ?: IumrahGlobeAssets.airport(context,to); if(direct && a!=null && b!=null) IumrahGlobeGeometry.route(a,b,64) else emptyList() }
    val projection=remember(route) { IumrahGlobeGeometry.centered(route) }
    val mesh=remember(projection) { landPath(projection,IumrahGlobeAssets.land(context)) }
    val livePoint=live?.let { projection.project(it) }?.takeIf { it.depth>=0 && route.isNotEmpty() }
    Canvas(Modifier.fillMaxWidth().height(142.dp).clipToBounds()) {
        val d=min(size.width-32.dp.toPx(),324.dp.toPx()).coerceAtLeast(1f)
        translate((size.width-d)/2,if(livePoint==null) 0f else -d*.22f) {
            scale(d,d,pivot=Offset.Zero) {
                val center=Offset(.5f,.5f); val light=Offset(.5f,.37f)
                drawCircle(Brush.radialGradient(listOf(gray(.079),gray(.065)),light,.84f),.5f,center)
                clipPath(mesh) { drawPath(dotGrid,Brush.radialGradient(listOf(gray(.832),gray(.526),gray(.22)),light,.78f)) }
                drawCircle(Brush.radialGradient(0f to Color.Transparent,.55f to Color.Transparent,1f to Color.Black.copy(alpha=.24f),center=center,radius=.5f),.5f,center)
                drawCircle(Color.White.copy(alpha=.06f),.499f,center,style=Stroke(.5.dp.toPx()/d))
                if(livePoint!=null) {
                    val points=route.map { projection.project(it).offset(1f) }
                    val path=Path().apply { moveTo(points.first().x,points.first().y); points.drop(1).forEach { lineTo(it.x,it.y) } }
                    drawPath(path,Color.White.copy(alpha=.85f),style=Stroke(1.6.dp.toPx()/d,cap=StrokeCap.Round,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx()/d,4.dp.toPx()/d))))
                    val index=route.indices.maxByOrNull { route[it].dot(live!!) } ?: 0
                    val completed=Path().apply { moveTo(points.first().x,points.first().y); points.take(index+1).drop(1).forEach { lineTo(it.x,it.y) } }
                    drawPath(completed,Color(0xFF34C759),style=Stroke(2.3.dp.toPx()/d,cap=StrokeCap.Round))
                    listOf(points.first() to from,points.last() to to).forEachIndexed { index, (point,label) ->
                        drawCircle(Color.White,3.5.dp.toPx()/d,point)
                        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE; textSize=10.dp.toPx()/d; textAlign=android.graphics.Paint.Align.CENTER; typeface=android.graphics.Typeface.DEFAULT_BOLD }
                        drawContext.canvas.nativeCanvas.drawText(label,point.x,point.y+(if(index==0) -12.dp.toPx() else 17.dp.toPx())/d,paint)
                    }
                    drawCircle(FlightPlaneBackdrop,8.5.dp.toPx()/d,livePoint.offset(1f))
                    drawGlobePlane(font,livePoint.offset(1f),17.dp.toPx()/d,Color(0xFF34C759))
                }
            }
        }
    }
}
