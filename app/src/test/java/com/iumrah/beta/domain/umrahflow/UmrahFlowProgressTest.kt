package com.iumrah.beta.domain.umrahflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UmrahFlowProgressTest {
    @Test fun sevenManualTawafTurnsAreRequired() {
        var state = UmrahFlowProgress().advance()
        assertEquals(UmrahPhase.TAWAF, state.phase)
        assertFalse(state.canAdvance)
        repeat(6) { state = state.completeTawafTurn() }
        assertEquals(state, state.advance())
        state = state.completeTawafTurn().completeTawafTurn()
        assertEquals(7, state.tawafCompleted)
        assertEquals(UmrahPhase.POST_TAWAF, state.advance().phase)
    }
    @Test fun saiRequiresSevenManualLegs() {
        var state = UmrahFlowProgress(UmrahPhase.SAI)
        repeat(6) { state = state.completeSaiLeg() }
        assertFalse(state.canAdvance)
        assertEquals(UmrahPhase.SAI, state.advance().phase)
        state = state.completeSaiLeg()
        assertEquals(7, state.saiCompleted)
        assertEquals(UmrahPhase.END, state.advance().phase)
        assertEquals(UmrahPhase.COMPLETED, state.advance().advance().phase)
    }
    @Test fun cannotCountRitualsInWrongStage() {
        val initial = UmrahFlowProgress()
        assertEquals(initial, initial.completeSaiLeg())
        assertEquals(initial, initial.completeTawafTurn())
        assertTrue(initial.progressFraction < .2f)
    }
    @Test fun onlyProjectPublicAudioIsAccepted() {
        assertTrue(UmrahFlowAudioUrlPolicy.isAllowed("https://iumrah-backend.helloiumrah.workers.dev/api/services/advisor/audio/ru/tawaf_1"))
        assertFalse(UmrahFlowAudioUrlPolicy.isAllowed("https://evil.example/storage/v1/object/public/audio.mp3"))
        assertFalse(UmrahFlowAudioUrlPolicy.isAllowed("http://iumrah-backend.helloiumrah.workers.dev/api/services/advisor/audio/ru/tawaf_1"))
        assertFalse(UmrahFlowAudioUrlPolicy.isAllowed("https://iumrah-backend.helloiumrah.workers.dev:8000/api/services/advisor/audio/ru/tawaf_1"))
    }
}
