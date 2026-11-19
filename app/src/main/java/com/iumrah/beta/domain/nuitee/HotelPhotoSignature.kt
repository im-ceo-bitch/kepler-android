package com.iumrah.beta.domain.nuitee

/** iOS IumrahHotelPhotoSignature: mean RGB difference, ignoring alpha. */
object HotelPhotoSignature {
    fun similar(a: IntArray, b: IntArray): Boolean {
        if (a.isEmpty() || a.size != b.size) return false
        var delta = 0L
        for (i in a.indices) for (shift in intArrayOf(16,8,0))
            delta += kotlin.math.abs(((a[i] shr shift) and 255) - ((b[i] shr shift) and 255))
        return delta.toDouble() / (a.size * 3) <= 8.0
    }
}
