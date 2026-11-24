package com.iumrah.beta.domain.umrahflow

import org.junit.Assert.*
import org.junit.Test

class AdvisorCheckpointTest {
    @Test fun sevenExplicitConfirmationsAndUndo() {
        var counter = TawafCounter()
        repeat(6) { counter = counter.confirm(); assertFalse(counter.complete) }
        assertEquals(7, counter.round)
        counter = counter.confirm()
        assertTrue(counter.complete)
        assertEquals(counter, counter.confirm())
        assertEquals(6, counter.undo().completed)
        assertEquals(0, TawafCounter().undo().completed)
    }
    @Test fun readingDoesNotChangeCounter() {
        val start = AdvisorCheckpoint(stage = "TAWAF", tawafCompleted = 3)
        val read = start.copy(tawafMode = "READ", tawafReadingStep = 6, tawafZikrCount = 20)
        assertEquals(start.progressFraction, read.progressFraction, 0.0001f)
        assertEquals(3, read.tawafCompleted)
    }
    @Test fun expiredAndCompletedCannotResume() {
        val now = 10_000_000_000L
        assertTrue(AdvisorCheckpoint.isFresh(now - 1, now))
        assertFalse(AdvisorCheckpoint.isFresh(now - 60L*24*60*60*1000, now))
        assertFalse(AdvisorCheckpoint.isFresh(now + 60_000, now))
        assertNull(AdvisorCheckpoint(stage = "AFTER").normalized())
        assertNull(AdvisorCheckpoint(stage = "INVALID").normalized())
        assertEquals(7, AdvisorCheckpoint(tawafCompleted = 99).normalized()!!.tawafCompleted)
    }
}
