import com.iumrah.beta.domain.orbit.OrbitV5Geometry as G
import kotlin.math.PI
import kotlin.math.floor

// Uses production Android geometry, not a second implementation of the equations.
fun main() {
    fun emit(vararg values: Double) = println(values.joinToString(","))
    for (sample in 0 until 101) {
        val time = sample * 0.247 + 0.013
        val rotation = G.rotation(time, 25.0)
        val r = G.reveal(time, rotation, 3.2)
        val p = G.pinnedReveal(rotation, G.ease(time / 0.55))
        emit(rotation, r.slot.toDouble(), r.dot.toDouble(), r.expansion, r.symbolOpacity, r.center.x, r.center.y, r.radius)
        emit(p.dot.toDouble(), p.expansion, p.symbolOpacity, p.center.x, p.center.y, p.radius)
        for (row in G.radii.indices) for (dot in 0 until G.pointsPerRing) {
            val point = G.point(row, dot, rotation)
            val hue = (point.angle + PI / 2) / G.tau
            emit(point.x, point.y, point.angle, G.visibility(row, dot, point, r), hue - floor(hue))
        }
        for (index in 0 until 12) {
            val point = G.assemblyPoint(index, 12, rotation, G.ease(sample / 100.0))
            emit(point.x, point.y, point.angle)
        }
    }
}
