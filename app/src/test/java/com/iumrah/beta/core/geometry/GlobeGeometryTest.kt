package com.iumrah.beta.core.geometry
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import kotlin.math.abs
class GlobeGeometryTest {
    @Test fun routeIsCenteredAndVisible() {
        val a=GlobeVector.geographic(41.2579,69.2812); val b=GlobeVector.geographic(21.6796,39.1565)
        val route=IumrahGlobeGeometry.route(a,b,64); val p=IumrahGlobeGeometry.centered(route)
        assertEquals(65,route.size); assertTrue(abs(p.project(route[32]).u-.5)<1e-10); assertTrue(route.all{p.project(it).depth>0})
        assertTrue(IumrahGlobeGeometry.route(a,a,64).isEmpty())
    }
    @Test fun staleAndInvalidAircraftPositionsAreNotDisplayed() {
        val now=Instant.parse("2026-11-14T12:00:00Z")
        assertTrue(IumrahGlobeGeometry.isLivePosition("EnRoute",now.minusSeconds(899).toString(),40.0,50.0,now))
        assertFalse(IumrahGlobeGeometry.isLivePosition("EnRoute",now.minusSeconds(900).toString(),40.0,50.0,now))
        assertFalse(IumrahGlobeGeometry.isLivePosition("Arrived",now.toString(),40.0,50.0,now))
        assertFalse(IumrahGlobeGeometry.isLivePosition("EnRoute",now.toString(),Double.NaN,50.0,now))
    }
    @Test fun coldLaunchTimingMatchesSwift() {
        assertEquals(1.2,IumrahGlobeGeometry.duration,0.0); assertEquals(.84,IumrahGlobeGeometry.mountContent,0.0)
        assertEquals(GlobeProjection(30.0,81.0),IumrahGlobeGeometry.launchProjection(0.0)); assertEquals(GlobeProjection(25.0,48.0),IumrahGlobeGeometry.launchProjection(1.2))
    }
}
