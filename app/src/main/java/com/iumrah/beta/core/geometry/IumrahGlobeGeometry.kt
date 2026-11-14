package com.iumrah.beta.core.geometry

import kotlin.math.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

/** Port of IumrahHemisphereProjection, IumrahLaunchGeometry and IumrahFlightGlobeRoute. */
internal data class GlobeVector(val x: Double, val y: Double, val z: Double) {
    fun dot(b: GlobeVector) = x*b.x + y*b.y + z*b.z
    companion object {
        fun geographic(latitude: Double, longitude: Double): GlobeVector {
            val lat = Math.toRadians(latitude); val lon = Math.toRadians(longitude)
            return GlobeVector(cos(lat)*cos(lon), cos(lat)*sin(lon), sin(lat))
        }
    }
}
internal data class GlobePoint(val x: Double, val y: Double, val depth: Double) {
    val u get() = (x+1)/2
    val v get() = (1-y)/2
    fun intersection(b: GlobePoint): GlobePoint {
        val t = depth/(depth-b.depth)
        return GlobePoint(x+(b.x-x)*t, y+(b.y-y)*t, 0.0)
    }
}
internal data class GlobeProjection(val latitude: Double, val longitude: Double) {
    private val phi = Math.toRadians(latitude); private val lambda = Math.toRadians(longitude)
    private val forward = GlobeVector.geographic(latitude, longitude)
    private val east = GlobeVector(-sin(lambda), cos(lambda), 0.0)
    private val north = GlobeVector(-sin(phi)*cos(lambda), -sin(phi)*sin(lambda), cos(phi))
    fun project(v: GlobeVector) = GlobePoint(v.dot(east), v.dot(north), v.dot(forward))
    fun polygon(a: GlobeVector,b: GlobeVector,c: GlobeVector): List<GlobePoint> {
        val input = listOf(project(a),project(b),project(c)); val output = mutableListOf<GlobePoint>(); var prev = input.last()
        for (current in input) {
            if ((prev.depth >= 0) != (current.depth >= 0)) output.add(prev.intersection(current))
            if (current.depth >= 0) output.add(current)
            prev = current
        }
        if (output.size < 3) return emptyList()
        val area = output.indices.sumOf { i -> val p=output[i]; val q=output[(i+1)%output.size]; p.u*q.v-q.u*p.v }
        return if (abs(area)<1e-13) emptyList() else if (area>0) output else output.asReversed()
    }
}
internal object IumrahGlobeGeometry {
    const val duration = 1.2
    const val mountContent = 0.84
    const val fadeStart = 0.98
    fun stage(time: Double, start: Double, end: Double): Double { val x=((time-start)/(end-start)).coerceIn(0.0,1.0); return x*x*(3-2*x) }
    fun launchProjection(time: Double): GlobeProjection { val p=stage(time,.12,.86); return GlobeProjection(30-5*p,81-33*p) }
    fun route(a: GlobeVector,b: GlobeVector,steps: Int): List<GlobeVector> {
        val angle=acos(a.dot(b).coerceIn(-1.0,1.0)); if(angle<=.00001 || angle>=PI-.00001 || steps<1) return emptyList()
        val d=sin(angle)
        return (0..steps).map { i -> val t=i.toDouble()/steps; val l=sin((1-t)*angle)/d; val r=sin(t*angle)/d; GlobeVector(l*a.x+r*b.x,l*a.y+r*b.y,l*a.z+r*b.z) }
    }
    fun centered(route: List<GlobeVector>): GlobeProjection {
        val mid=route.getOrNull(route.size/2) ?: return GlobeProjection(25.0,48.0)
        return GlobeProjection(Math.toDegrees(asin(mid.z.coerceIn(-1.0,1.0))),Math.toDegrees(atan2(mid.y,mid.x)))
    }
    fun decode(bytes: ByteArray): List<GlobeVector> {
        require(bytes.size%4==0)
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(bytes.size/4) { GlobeVector.geographic(b.short.toDouble()/100,b.short.toDouble()/100) }
    }
    fun isLivePosition(status: String?, reportedAt: String?, lat: Double, lon: Double, now: Instant): Boolean {
        if(status !in setOf("EnRoute","Approaching","Departed") || !lat.isFinite() || !lon.isFinite() || abs(lat)>90 || abs(lon)>180) return false
        val date=runCatching { Instant.parse(reportedAt) }.getOrNull() ?: return false
        val age=java.time.Duration.between(date,now).toMillis()
        return age > -60_000 && age < 900_000
    }
}
