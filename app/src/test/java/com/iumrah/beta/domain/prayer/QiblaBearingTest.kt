package com.iumrah.beta.domain.prayer

import org.junit.Assert.*
import org.junit.Test

class QiblaBearingTest {
    @Test fun knownCoordinatesAndHeading() {
        assertEquals(180.0, QiblaBearing.degrees(30.0,39.8262)!!,0.001)
        assertEquals(0.0, QiblaBearing.degrees(10.0,39.8262)!!,0.001)
        assertEquals(350.0, QiblaBearing.relative(10.0,20.0),0.001)
        assertNull(QiblaBearing.degrees(21.4225,39.8262))
        assertNull(QiblaBearing.degrees(Double.NaN,39.8262))
    }
}
