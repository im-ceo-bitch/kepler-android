package com.iumrah.beta.domain.umrahflow

/** The counts are entered by the pilgrim; no physical movement is inferred. */
enum class UmrahPhase { START, TAWAF, POST_TAWAF, SAI, END, COMPLETED }

data class UmrahFlowProgress(
    val phase: UmrahPhase = UmrahPhase.START,
    val tawafCompleted: Int = 0,
    val saiCompleted: Int = 0,
) {
    init {
        require(tawafCompleted in 0..7 && saiCompleted in 0..7) { "Invalid ritual count" }
    }

    val canAdvance: Boolean get() = when (phase) {
        UmrahPhase.TAWAF -> tawafCompleted == 7
        UmrahPhase.SAI -> saiCompleted == 7
        UmrahPhase.COMPLETED -> false
        else -> true
    }

    fun completeTawafTurn(): UmrahFlowProgress =
        if (phase == UmrahPhase.TAWAF) copy(tawafCompleted = (tawafCompleted + 1).coerceAtMost(7)) else this

    fun completeSaiLeg(): UmrahFlowProgress =
        if (phase == UmrahPhase.SAI) copy(saiCompleted = (saiCompleted + 1).coerceAtMost(7)) else this

    /** Deliberately disallows skipping incomplete seven-turn rites. */
    fun advance(): UmrahFlowProgress = if (!canAdvance) this else when (phase) {
        UmrahPhase.START -> copy(phase = UmrahPhase.TAWAF)
        UmrahPhase.TAWAF -> copy(phase = UmrahPhase.POST_TAWAF)
        UmrahPhase.POST_TAWAF -> copy(phase = UmrahPhase.SAI)
        UmrahPhase.SAI -> copy(phase = UmrahPhase.END)
        UmrahPhase.END -> copy(phase = UmrahPhase.COMPLETED)
        UmrahPhase.COMPLETED -> this
    }

    val milestone: Int get() = when (phase) {
        UmrahPhase.START -> 1
        UmrahPhase.TAWAF -> 2
        UmrahPhase.POST_TAWAF -> 3
        UmrahPhase.SAI -> 4
        UmrahPhase.END, UmrahPhase.COMPLETED -> 5
    }

    val progressFraction: Float get() = when (phase) {
        UmrahPhase.START -> .05f
        UmrahPhase.TAWAF -> .20f + tawafCompleted / 7f * .20f
        UmrahPhase.POST_TAWAF -> .42f
        UmrahPhase.SAI -> .47f + saiCompleted / 7f * .32f
        UmrahPhase.END -> .88f
        UmrahPhase.COMPLETED -> 1f
    }
}
