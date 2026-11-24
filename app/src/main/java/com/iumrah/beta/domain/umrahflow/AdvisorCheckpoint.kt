package com.iumrah.beta.domain.umrahflow

/** The seven circuits are independent of reading pages, playback and navigation. */
data class TawafCounter(val completed: Int = 0) {
    init { require(completed in 0..7) }
    val round: Int get() = (completed + 1).coerceAtMost(7)
    val complete: Boolean get() = completed == 7
    fun confirm() = copy(completed = (completed + 1).coerceAtMost(7))
    fun undo() = copy(completed = (completed - 1).coerceAtLeast(0))
}

/** Mirrors the iOS v2 checkpoint fields, using confirmed circuits instead of round+complete. */
data class AdvisorCheckpoint(
    val stage: String = "START", val startPhase: Int = 0, val tawafCompleted: Int = 0,
    val tawafTextMode: Int = 0, val tawafMode: String = "LISTEN", val tawafReadingStep: Int = 0,
    val tawafZikrCount: Int = 0, val postTawafStep: Int = 0, val safaRound: Int = 1,
    val safaTextMode: Int = 0, val safaMode: String = "LISTEN", val safaReadingStep: Int = 0,
    val endPhase: Int = 0,
) {
    fun normalized(): AdvisorCheckpoint? {
        if (stage !in setOf("START", "TAWAF", "POST_TAWAF", "SAFA", "END")) return null
        return copy(startPhase = startPhase.coerceIn(0, 3), tawafCompleted = tawafCompleted.coerceIn(0, 7),
            tawafTextMode = tawafTextMode.coerceIn(0, 1), tawafMode = if (tawafMode == "READ") "READ" else "LISTEN",
            tawafReadingStep = tawafReadingStep.coerceIn(0, 6), tawafZikrCount = tawafZikrCount.coerceIn(0, 20),
            postTawafStep = postTawafStep.coerceIn(0, 3), safaRound = safaRound.coerceIn(1, 7),
            safaTextMode = safaTextMode.coerceIn(0, 1), safaMode = if (safaMode == "READ") "READ" else "LISTEN",
            safaReadingStep = safaReadingStep.coerceIn(0, 2), endPhase = endPhase.coerceIn(0, 2))
    }
    val progressFraction: Float get() = when (stage) {
        "START" -> startPhase / 4f * .20f
        "TAWAF" -> .20f + tawafCompleted / 7f * .20f
        "POST_TAWAF" -> .40f + postTawafStep / 4f * .20f
        "SAFA" -> .60f + (safaRound - 1) / 7f * .20f
        "END" -> .80f + endPhase / 3f * .20f
        "AFTER" -> 1f
        else -> 0f
    }
    companion object {
        fun isFresh(savedAt: Long, now: Long): Boolean = savedAt > 0 && savedAt > now - 60L * 24 * 60 * 60 * 1000 && savedAt < now + 60_000
    }
}
