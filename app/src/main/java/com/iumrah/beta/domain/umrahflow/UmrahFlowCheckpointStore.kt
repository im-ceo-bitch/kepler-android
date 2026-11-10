package com.iumrah.beta.domain.umrahflow

import android.content.Context

/** A local private checkpoint; contains no passport, booking or identity data. */
class UmrahFlowCheckpointStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("iumrah.flow.progress.v1", Context.MODE_PRIVATE)

    fun hasCheckpoint(): Boolean = preferences.getBoolean("exists", false)

    fun restore(): UmrahFlowProgress? = if (!hasCheckpoint()) null else runCatching {
        UmrahFlowProgress(
            phase = UmrahPhase.valueOf(preferences.getString("phase", "START") ?: "START"),
            tawafCompleted = preferences.getInt("tawaf", 0).coerceIn(0, 7),
            saiCompleted = preferences.getInt("sai", 0).coerceIn(0, 7),
        )
    }.getOrNull()

    fun save(progress: UmrahFlowProgress) {
        if (progress.phase == UmrahPhase.COMPLETED) { clear(); return }
        preferences.edit().putBoolean("exists", true)
            .putString("phase", progress.phase.name)
            .putInt("tawaf", progress.tawafCompleted)
            .putInt("sai", progress.saiCompleted).apply()
    }

    fun clear() { preferences.edit().clear().apply() }
}
