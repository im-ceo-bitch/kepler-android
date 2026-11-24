package com.iumrah.beta.domain.prayer

import kotlin.math.*

/** True-north great-circle bearing toward the Kaaba; undefined at the destination. */
object QiblaBearing {
    fun degrees(latitude: Double, longitude: Double): Double? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val lat = Math.toRadians(latitude)
        val kaaba = Math.toRadians(21.4225)
        val delta = Math.toRadians(39.8262 - longitude)
        val east = sin(delta) * cos(kaaba)
        val north = cos(lat) * sin(kaaba) - sin(lat) * cos(kaaba) * cos(delta)
        if (hypot(east, north) < 1e-10) return null
        return normalize(Math.toDegrees(atan2(east, north)))
    }
    fun normalize(angle: Double) = ((angle % 360.0) + 360.0) % 360.0
    fun relative(bearing: Double, trueHeading: Double) = normalize(bearing - trueHeading)
}
