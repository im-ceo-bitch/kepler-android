package com.iumrah.beta.domain.umrahflow

import android.content.Context

/** Private checkpoint. It is only restored after the pilgrim explicitly chooses Resume. */
class AdvisorCheckpointStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("iumrah.flow.progress.v2", Context.MODE_PRIVATE)
    private val legacy = context.applicationContext.getSharedPreferences("iumrah.flow.progress.v1", Context.MODE_PRIVATE)
    // v1 had no date or reading state. Preserve confirmed counters, explicitly opt in on resume.
    private fun legacyCheckpoint(): AdvisorCheckpoint? {
        if (!legacy.getBoolean("exists", false)) return null
        val phase = when (legacy.getString("phase", "")) {
            "START" -> "START"; "TAWAF" -> "TAWAF"; "POST_TAWAF" -> "POST_TAWAF"
            "SAI" -> "SAFA"; "END" -> "END"; else -> return null
        }
        return AdvisorCheckpoint(stage = phase, tawafCompleted = legacy.getInt("tawaf", 0), safaRound = (legacy.getInt("sai", 0).coerceIn(0, 7) + 1).coerceAtMost(7)).normalized()
    }
    fun restore(now: Long = System.currentTimeMillis()): AdvisorCheckpoint? = runCatching {
        if (!preferences.contains("savedAt")) return legacyCheckpoint()
        if (!AdvisorCheckpoint.isFresh(preferences.getLong("savedAt", 0), now)) return null
        AdvisorCheckpoint(
            stage = preferences.getString("stage", "") ?: "",
            startPhase = preferences.getInt("startPhase", 0), tawafCompleted = preferences.getInt("tawafCompleted", 0),
            tawafTextMode = preferences.getInt("tawafTextMode", 0), tawafMode = preferences.getString("tawafMode", "LISTEN") ?: "LISTEN",
            tawafReadingStep = preferences.getInt("tawafReadingStep", 0), tawafZikrCount = preferences.getInt("tawafZikrCount", 0),
            postTawafStep = preferences.getInt("postTawafStep", 0), safaRound = preferences.getInt("safaRound", 1),
            safaTextMode = preferences.getInt("safaTextMode", 0), safaMode = preferences.getString("safaMode", "LISTEN") ?: "LISTEN",
            safaReadingStep = preferences.getInt("safaReadingStep", 0), endPhase = preferences.getInt("endPhase", 0),
        ).normalized()
    }.getOrNull()
    fun save(value: AdvisorCheckpoint) {
        val s = value.normalized() ?: run { clear(); return }
        preferences.edit().putLong("savedAt", System.currentTimeMillis()).putString("stage", s.stage)
            .putInt("startPhase", s.startPhase).putInt("tawafCompleted", s.tawafCompleted)
            .putInt("tawafTextMode", s.tawafTextMode).putString("tawafMode", s.tawafMode)
            .putInt("tawafReadingStep", s.tawafReadingStep).putInt("tawafZikrCount", s.tawafZikrCount)
            .putInt("postTawafStep", s.postTawafStep).putInt("safaRound", s.safaRound)
            .putInt("safaTextMode", s.safaTextMode).putString("safaMode", s.safaMode)
            .putInt("safaReadingStep", s.safaReadingStep).putInt("endPhase", s.endPhase).apply()
        legacy.edit().clear().apply()
    }
    fun clear() { preferences.edit().clear().apply(); legacy.edit().clear().apply() }
}
