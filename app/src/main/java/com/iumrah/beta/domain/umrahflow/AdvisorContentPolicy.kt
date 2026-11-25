package com.iumrah.beta.domain.umrahflow

/** Same precedence as Swift UmrahFlowStore. Fallback is presentation-only. */
object AdvisorContentPolicy {
    fun merge(bundled: Map<String, String>, cached: Map<String, String>, fresh: Map<String, String> = emptyMap()): Map<String, String> =
        cached.filterValues { it.isNotBlank() } + bundled.filterValues { it.isNotBlank() } + fresh.filterValues { it.isNotBlank() }
    fun display(selected: Map<String, String>, english: Map<String, String>): Map<String, String> = selected.ifEmpty { english }
    fun nextTawafText(mode: Int): Int = (mode.coerceIn(0, 2) + 1) % 3
}
