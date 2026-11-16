import com.iumrah.beta.domain.orbit.OrbitV5Geometry as G
import kotlin.math.*
fun main() {
    var n=0
    fun checkValue(value:Boolean) { check(value) { "Orbit check ${n+1} failed" }; n++ }
    fun near(a:Double,b:Double)=abs(a-b)<1e-8
    checkValue(G.radii.size*G.pointsPerRing==96)
    checkValue(G.dotRadii==listOf(7.3,6.3,5.5,4.8)); checkValue(G.palette.size==22)
    checkValue(near(G.rotation(25.0),0.0)); checkValue(near(G.rotation(12.5),PI))
    for(row in G.radii.indices) for(dot in 0 until G.pointsPerRing) {
        val p=G.point(row,dot,0.7); checkValue(near(hypot(p.x-180,p.y-180),G.radii[row]))
    }
    for(i in 0..32) {
        val time=i*.1; val rot=G.rotation(time); val r=G.reveal(time,rot)
        checkValue(r.expansion in 0.0..1.0 && r.symbolOpacity in 0.0..1.0)
        checkValue(G.visibility(G.activeRow,r.dot,r.center,r)==0.0)
        val pinned=G.pinnedReveal(rot,1.0); checkValue(near(pinned.radius,27.5)); checkValue(near(hypot(pinned.center.x-180,pinned.center.y-180),119.75))
        checkValue(abs(atan2(sin(pinned.center.angle+PI/4),cos(pinned.center.angle+PI/4)))<=PI/24+1e-8)
    }
    checkValue(G.reveal(1.6,0.0).expansion==1.0); checkValue(G.reveal(0.0,0.0).expansion==0.0)
    checkValue(G.reveal(3.2,0.0).dot==3); checkValue(G.reveal(0.0,0.0,still=true).radius==27.5)
    for(i in 0..7) { val p=G.assemblyPoint(i,8,.3,1.0); checkValue(near(p.x,180.0)&&near(p.y,180.0)) }
    val (r,g,b)=G.color(-PI/2);checkValue(abs(r-155f/255)<1e-6 && abs(g-189f/255)<1e-6 && abs(b-179f/255)<1e-6)
    checkValue(G.color(-PI/2)==G.color(3*PI/2))
    println("Orbit V5: $n JVM geometry assertions passed")
}
