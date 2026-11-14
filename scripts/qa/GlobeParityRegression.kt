package com.iumrah.beta.core.geometry
import java.io.File
import java.time.Instant
import kotlin.math.abs
fun main() {
    var checks=0
    fun test(condition:Boolean, label:String) { check(condition) { label }; checks++ }
    val a=GlobeVector.geographic(41.2579,69.2812); val b=GlobeVector.geographic(21.6796,39.1565)
    val route=IumrahGlobeGeometry.route(a,b,64)
    test(route.size==65,"route sample count")
    test(route.first().dot(a)>.999999,"origin"); test(route.last().dot(b)>.999999,"destination")
    test(route.all { abs(it.dot(it)-1)<1e-9 },"unit vectors")
    val projection=IumrahGlobeGeometry.centered(route); val mid=projection.project(route[32]); test(abs(mid.u-.5)<1e-10 && abs(mid.v-.5)<1e-10,"route centered")
    test(route.all { projection.project(it).depth>0 },"visible route")
    val proj=GlobeProjection(0.0,0.0); val front=GlobeVector.geographic(0.0,0.0); val back=GlobeVector.geographic(0.0,180.0)
    test(proj.project(front).depth>.999,"front"); test(proj.project(back).depth<-.999,"back")
    val poly=proj.polygon(front,GlobeVector.geographic(35.0,120.0),GlobeVector.geographic(-35.0,120.0))
    test(poly.size==3 && poly.all { it.depth>=0 },"horizon clipping")
    test(proj.polygon(back,GlobeVector.geographic(35.0,140.0),GlobeVector.geographic(-35.0,140.0)).isEmpty(),"back face")
    test(IumrahGlobeGeometry.route(a,a,64).isEmpty(),"identical endpoints")
    test(IumrahGlobeGeometry.stage(.1,.2,.8)==0.0,"before transition"); test(IumrahGlobeGeometry.stage(.5,.2,.8) in .499999.. .500001,"mid transition"); test(IumrahGlobeGeometry.stage(1.0,.2,.8)==1.0,"after transition")
    test(IumrahGlobeGeometry.launchProjection(0.0)==GlobeProjection(30.0,81.0),"launch start projection")
    test(IumrahGlobeGeometry.launchProjection(1.2)==GlobeProjection(25.0,48.0),"launch end projection")
    val root=File("app/src/main/assets"); val dots=IumrahGlobeGeometry.decode(File(root,"iumrah_launch_land.bin").readBytes()); val land=IumrahGlobeGeometry.decode(File(root,"iumrah_hemisphere_land.bin").readBytes())
    test(dots.size==5195,"original iOS dots"); test(land.size%3==0 && land.size/3==16905,"original iOS land triangles")
    test(dots.all { abs(it.dot(it)-1)<1e-9 },"decoded unit dots"); test(land.all { abs(it.dot(it)-1)<1e-9 },"decoded unit triangles")
    val airports=File(root,"iumrah_globe_airports.csv").readLines(); test(airports.size==54 && airports.map{it.substringBefore(',')}.distinct().size==54,"iOS airports")
    val now=Instant.parse("2026-11-14T10:00:00Z")
    fun live(status:String?,age:Long,lat:Double=40.0,lon:Double=50.0)=IumrahGlobeGeometry.isLivePosition(status,now.minusSeconds(age).toString(),lat,lon,now)
    test(live("EnRoute",0),"fresh position"); test(live("Approaching",899),"last fresh second"); test(!live("Departed",900),"stale position"); test(!live("Arrived",10),"arrived not live"); test(!live("EnRoute",-60),"future threshold"); test(!live("EnRoute",1,Double.NaN),"nonfinite coordinate"); test(!live("EnRoute",1,91.0),"invalid latitude"); test(!live("EnRoute",1,40.0,181.0),"invalid longitude"); test(!IumrahGlobeGeometry.isLivePosition("EnRoute",null,40.0,50.0,now),"missing timestamp")
    println("V5 globe geometry JVM: $checks checks passed")
}
