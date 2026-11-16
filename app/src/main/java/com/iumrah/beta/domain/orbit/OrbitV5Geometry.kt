package com.iumrah.beta.domain.orbit

import kotlin.math.*

/** Direct port of IumrahOrbitV5Geometry.swift, in normalized 360x360 coordinates. */
object OrbitV5Geometry {
    val radii = listOf(140.0, 125.0, 111.0, 97.0)
    val dotRadii = listOf(7.3, 6.3, 5.5, 4.8)
    const val pointsPerRing = 24
    const val activeRow = 1
    const val expandedRadius = 27.5
    const val middleRadius = 119.75
    const val tau = 2 * PI
    data class Point(val x: Double, val y: Double, val angle: Double)
    data class Reveal(val slot: Int, val dot: Int, val expansion: Double, val symbolOpacity: Double, val center: Point, val radius: Double)
    fun ease(x: Double): Double { val t = x.coerceIn(0.0, 1.0); return t*t*t*(t*(t*6-15)+10) }
    fun rotation(time: Double, duration: Double = 25.0): Double { val period = max(1.0, duration); return time % period * tau / period }
    fun point(row: Int, dot: Int, rotation: Double, radius: Double = radii[row]): Point {
        val angle = -PI/2 + (if (row % 2 == 0) -PI/24 else 0.0) + dot*tau/24 + rotation
        return Point(180 + radius*cos(angle), 180 + radius*sin(angle), angle)
    }
    private fun revealAt(slot: Int, dot: Int, p: Double, rotation: Double): Reveal = Reveal(slot, dot, p, ease((p-.3)/.6),
        point(activeRow, dot, rotation, radii[activeRow]+(middleRadius-radii[activeRow])*p), dotRadii[activeRow]+(expandedRadius-dotRadii[activeRow])*p)
    fun reveal(time: Double, rotation: Double, duration: Double = 3.2, still: Boolean = false): Reveal {
        val cycles = max(0.0,time)/max(.5,duration); val slot = floor(cycles).toInt(); val f = cycles-slot
        return revealAt(slot, (slot%8)*3, if(still) 1.0 else ease(f/.28)*ease((1-f)/.28), rotation)
    }
    fun pinnedReveal(rotation: Double, expansion: Double): Reveal {
        val dot = (0 until pointsPerRing).minBy { abs(sin((point(activeRow,it,rotation).angle+PI/4)/2)) }
        return revealAt(0,dot,expansion.coerceIn(0.0,1.0),rotation)
    }
    fun visibility(row: Int,dot: Int,point: Point,reveal: Reveal?): Double {
        if(reveal==null) return 1.0
        if(row==activeRow && dot==reveal.dot) return 0.0
        val distance=hypot(point.x-reveal.center.x,point.y-reveal.center.y)
        return 1-reveal.expansion*(1-ease((distance-reveal.radius-dotRadii[row]-1)/13))
    }
    fun assemblyPoint(index: Int,count: Int,rotation: Double,progress: Double): Point {
        val angle=-PI/2+index*tau/max(1,count)+rotation
        val r=middleRadius*(1-progress.coerceIn(0.0,1.0))
        return Point(180+r*cos(angle),180+r*sin(angle),angle)
    }
    val palette = listOf(0x9bbdb3,0x63bdcf,0x30bce9,0x30afe8,0x419de9,0x5389e8,0x6674e7,0x8456e9,0xa43aea,0xbb25e5,0xd524d6,0xe329bb,0xe927a1,0xec388b,0xf24d78,0xfa656a,0xfa7d71,0xff997c,0xffb18a,0xf9bc88,0xdfba91,0xbdbaa5)
    fun color(angle: Double): Triple<Float,Float,Float> {
        val h=(angle+PI/2)/tau; val f=(h-floor(h))*palette.size; val i=f.toInt()%palette.size; val q=f-floor(f)
        val a=palette[i]; val b=palette[(i+1)%palette.size]
        fun channel(shift: Int): Float { val x=(a shr shift) and 255; val y=(b shr shift) and 255; return ((x+(y-x)*q)/255).toFloat() }
        return Triple(channel(16),channel(8),channel(0))
    }
}
