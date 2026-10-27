package com.iumrah.beta.ui.packageflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.map.IumrahMapLibreView
import com.iumrah.beta.ui.map.IumrahMapStyle
import com.iumrah.beta.ui.map.rememberIumrahMapController
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Native Android map for the iOS TransferLiveSearchMap screen.
 *
 * Uses the existing MapLibre host, real vector streets, the same Makkah
 * coordinates and exactly the same nine animated candidate trajectories as
 * the SwiftUI MapKit version. Pins represent illustrative search candidates,
 * not live driver locations or guaranteed inventory.
 */
private data class Candidate(
    val id: String, val latitude: Double, val longitude: Double,
    val phase: Double, val appearSecond: Int, val emphasis: Boolean,
) {
    fun coordinate(second: Int): LatLng {
        val t = second * .22 + phase
        return LatLng(latitude + sin(t) * .00115 + cos(t * .55) * .00038,
            longitude + cos(t * .88) * .00135 + sin(t * .46) * .00042)
    }
}

private val transferCandidates = listOf(
    Candidate("A",21.4314,39.8347,.2,1,false),
    Candidate("B",21.4159,39.8382,1.0,3,true),
    Candidate("C",21.4378,39.8171,1.8,5,false),
    Candidate("D",21.4108,39.8164,2.5,8,false),
    Candidate("E",21.4475,39.8292,3.2,11,true),
    Candidate("F",21.4234,39.8505,4.1,14,false),
    Candidate("G",21.4019,39.8298,5.0,18,false),
    Candidate("H",21.4391,39.8460,5.9,21,true),
    Candidate("I",21.4201,39.8058,6.6,24,false),
)

private val makkahCenter = LatLng(21.4225, 39.8262)

@Composable
internal fun TransferSearchLiveMap(second: Int, duration: Int, language: AppLanguage) {
    val controller = rememberIumrahMapController()
    val map = controller.map
    val positions = remember { mutableStateMapOf<String, IntOffset>() }
    val secondNow by rememberUpdatedState(second)
    val density = LocalDensity.current
    val carRadiusRegular = with(density) { 20.dp.roundToPx() }
    val carRadiusBig = with(density) { 23.dp.roundToPx() }
    val centerRadius = with(density) { 24.dp.roundToPx() }
    val centerPosition = positions["makkah"]
    val radiusPosition = positions["radius"]

    fun updatePositions(active: MapLibreMap) {
        val projection = active.projection
        fun point(latLng: LatLng): IntOffset {
            val p = projection.toScreenLocation(latLng)
            return IntOffset(p.x.roundToInt(), p.y.roundToInt())
        }
        positions["makkah"] = point(makkahCenter)
        val radius = 650.0 + 5900.0 * (secondNow.toDouble() / duration.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        positions["radius"] = point(LatLng(makkahCenter.latitude + radius / 111320.0, makkahCenter.longitude))
        transferCandidates.forEach { candidate -> positions[candidate.id] = point(candidate.coordinate(secondNow)) }
    }

    LaunchedEffect(map) {
        controller.setStyle(IumrahMapStyle.STANDARD)
    }
    LaunchedEffect(map, controller.styleEpoch) {
        val active = map ?: return@LaunchedEffect
        if (controller.styleEpoch == 0) return@LaunchedEffect
        active.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(21.4257,39.8264)).zoom(13.35).tilt(34.0).build()
        ))
        updatePositions(active)
    }
    LaunchedEffect(map, second) {
        val active = map ?: return@LaunchedEffect
        if (controller.styleEpoch == 0) return@LaunchedEffect
        if (second > 0 && second % 5 == 0) {
            val zoom = (13.35 - second * .009).coerceAtLeast(12.85)
            active.animateCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(makkahCenter).zoom(zoom).tilt(34.0).build()
            ), 2100)
        }
        updatePositions(active)
    }
    DisposableEffect(map) {
        val active = map
        if (active == null) return@DisposableEffect onDispose { }
        val listener = MapLibreMap.OnCameraMoveListener { updatePositions(active) }
        val idle = MapLibreMap.OnCameraIdleListener { updatePositions(active) }
        active.addOnCameraMoveListener(listener)
        active.addOnCameraIdleListener(idle)
        updatePositions(active)
        onDispose {
            active.removeOnCameraMoveListener(listener)
            active.removeOnCameraIdleListener(idle)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) {
        IumrahMapLibreView(controller, Modifier.fillMaxSize())

        // The search radius is measured in real metres, not screen-relative dots.
        if (centerPosition != null && radiusPosition != null) {
            val c = centerPosition
            val r = radiusPosition
            if (c != null && r != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val radiusPx = kotlin.math.abs(c.y - r.y).toFloat().coerceAtLeast(1f)
                    drawCircle(Color(0xFF007AFF).copy(alpha = .075f), radius = radiusPx,
                        center = androidx.compose.ui.geometry.Offset(c.x.toFloat(), c.y.toFloat()))
                    drawCircle(Color(0xFF007AFF).copy(alpha = .44f), radius = radiusPx,
                        center = androidx.compose.ui.geometry.Offset(c.x.toFloat(), c.y.toFloat()),
                        style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        if (centerPosition != null) {
            val c = centerPosition
            if (c != null) {
                Box(Modifier.offset { IntOffset(c.x-centerRadius, c.y-centerRadius) }
                    .size(48.dp).clip(CircleShape)
                    .background(Color(0xFF007AFF).copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White)
                        .border(5.dp, Color(0xFF007AFF), CircleShape))
                }
            }
        }
        transferCandidates.forEach { candidate ->
            val position = positions[candidate.id]
            if (position != null) {
                val active = second >= candidate.appearSecond
                val radius = if (candidate.emphasis) carRadiusBig else carRadiusRegular
                Box(
                    Modifier.offset { IntOffset(position.x - radius, position.y - radius) }
                        .size(if (candidate.emphasis) 46.dp else 40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = if (active) 1f else .36f))
                        .border(2.dp, Color.White.copy(alpha = if (active) .92f else .45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CupertinoSymbol.Car, null,
                        Modifier.size(if (candidate.emphasis) 18.dp else 16.dp), tint = Color.White)
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 176.dp, end = 16.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = .94f))
            .padding(horizontal = 10.dp).height(34.dp), contentAlignment = Alignment.Center) {
            Text("Makkah", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}
