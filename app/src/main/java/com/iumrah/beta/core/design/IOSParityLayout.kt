package com.iumrah.beta.core.design

/** Window-width policy ported from iOS Core/AdaptiveLayout.swift. Android has no
 * Catalyst equivalent. Widths refer to the actual window/column, not device name. */
internal enum class IOSLayoutClass { COMPACT, REGULAR, WIDE }
internal data class IOSLayoutProfile(val layoutClass: IOSLayoutClass) {
    val usesSidebar get() = layoutClass != IOSLayoutClass.COMPACT
    val rootMaxWidth: Int? get() = when (layoutClass) {
        IOSLayoutClass.COMPACT -> null
        IOSLayoutClass.REGULAR -> 760
        IOSLayoutClass.WIDE -> 1040
    }
    val detailMaxWidth: Int? get() = when (layoutClass) {
        IOSLayoutClass.COMPACT -> null
        IOSLayoutClass.REGULAR -> 760
        IOSLayoutClass.WIDE -> 920
    }
    val pagePadding get() = when (layoutClass) {
        IOSLayoutClass.COMPACT -> 18
        IOSLayoutClass.REGULAR -> 24
        IOSLayoutClass.WIDE -> 30
    }
    val gridColumns get() = when (layoutClass) {
        IOSLayoutClass.COMPACT -> 1
        IOSLayoutClass.REGULAR -> 2
        IOSLayoutClass.WIDE -> 3
    }
    companion object {
        fun resolve(width: Float, compact: Boolean = false) = IOSLayoutProfile(when {
            compact || width < 700 -> IOSLayoutClass.COMPACT
            width < 1000 -> IOSLayoutClass.REGULAR
            else -> IOSLayoutClass.WIDE
        })
    }
}
