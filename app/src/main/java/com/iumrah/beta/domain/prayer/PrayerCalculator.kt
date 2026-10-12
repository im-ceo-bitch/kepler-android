package com.iumrah.beta.domain.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import kotlin.math.*

/** iOS IumrahPrayerCalculator port. All returned times are absolute instants. */
data class PrayerPlace(val id: String, val name: String, val latitude: Double, val longitude: Double, val zoneId: String) {
    val zone: ZoneId get() = ZoneId.of(zoneId)
    val valid: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    companion object {
        val presets = listOf(
            PrayerPlace("makkah", "Makkah", 21.4225, 39.8262, "Asia/Riyadh"),
            PrayerPlace("madinah", "Madinah", 24.4672, 39.6111, "Asia/Riyadh"),
            PrayerPlace("tashkent", "Tashkent", 41.3111, 69.2797, "Asia/Tashkent"),
            PrayerPlace("samarkand", "Samarkand", 39.6542, 66.9597, "Asia/Tashkent"),
            PrayerPlace("istanbul", "Istanbul", 41.0082, 28.9784, "Europe/Istanbul"),
            PrayerPlace("dubai", "Dubai", 25.2048, 55.2708, "Asia/Dubai"),
            PrayerPlace("jakarta", "Jakarta", -6.2088, 106.8456, "Asia/Jakarta"),
            PrayerPlace("london", "London", 51.5074, -0.1278, "Europe/London"),
        )
    }
}

enum class PrayerMethod(val title: String, val fajrAngle: Double, val ishaAngle: Double?) {
    UMM_AL_QURA("Umm al-Qura", 18.5, null),
    MUSLIM_WORLD_LEAGUE("Muslim World League", 18.0, 17.0),
    EGYPT("Egyptian Authority", 19.5, 17.5),
    KARACHI("University of Karachi", 18.0, 18.0),
    NORTH_AMERICA("ISNA", 15.0, 15.0),
}

enum class PrayerKind { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA, DUHA, TAHAJJUD }

data class PrayerSchedule(val place: PrayerPlace, val date: LocalDate, val times: Map<PrayerKind, Instant>, val duhaEnd: Instant?) {
    fun at(kind: PrayerKind): Instant? = times[kind]
}

object PrayerCalculator {
    private fun rad(x: Double) = Math.toRadians(x)
    private fun deg(x: Double) = Math.toDegrees(x)
    private fun norm(x: Double) = ((x % 360.0) + 360.0) % 360.0
    private data class Sun(val decl: Double, val equationMinutes: Double)

    private fun sun(at: Instant): Sun {
        val jd = at.epochSecond / 86400.0 + at.nano / 86400000000000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l0 = norm(280.46646 + t * (36000.76983 + 0.0003032 * t))
        val m = norm(357.52911 + t * (35999.05029 - 0.0001537 * t))
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
        val obliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60 + 0.00256 * cos(rad(omega))
        val epsilon = rad(obliquity)
        val declination = asin(sin(epsilon) * sin(rad(lambda)))
        val y = tan(epsilon / 2).pow(2)
        val eq = 4 * deg(y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) - .5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)))
        return Sun(declination, eq)
    }

    private fun hourAngle(lat: Double, decl: Double, altitude: Double): Double? {
        val phi = rad(lat)
        val den = cos(phi) * cos(decl)
        if (abs(den) < 1e-7) return null
        val cosine = (sin(rad(altitude)) - sin(phi) * sin(decl)) / den
        if (!cosine.isFinite() || cosine !in -1.0..1.0) return null
        return deg(acos(cosine)) * 4.0
    }

    private fun instantPlusMinutes(instant: Instant, minutes: Double): Instant =
        instant.plusMillis((minutes * 60_000.0).roundToLong())

    private fun baseDay(date: LocalDate, place: PrayerPlace, method: PrayerMethod, hanafi: Boolean): Map<PrayerKind, Instant> {
        val zone = place.zone
        val midnight = date.atStartOfDay(zone)
        val noonGuess = date.atTime(12, 0).atZone(zone).toInstant()
        var solar = sun(noonGuess)
        var transit = noonGuess
        var noonMinutes = 0.0
        repeat(3) {
            noonMinutes = 720.0 - 4 * place.longitude - solar.equationMinutes + zone.rules.getOffset(transit).totalSeconds / 60.0
            val whole = floor(noonMinutes).toLong()
            // Form local wall time before converting to an instant: DST may make a day != 24h.
            val local = midnight.toLocalDate().atStartOfDay().plusMinutes(whole).atZone(zone).toInstant()
            transit = instantPlusMinutes(local, noonMinutes - whole)
            solar = sun(transit)
        }
        val result = mutableMapOf<PrayerKind, Instant>(PrayerKind.DHUHR to transit)
        fun before(angle: Double) = hourAngle(place.latitude, solar.decl, -angle)?.let { instantPlusMinutes(transit, -it) }
        fun after(alt: Double) = hourAngle(place.latitude, solar.decl, alt)?.let { instantPlusMinutes(transit, it) }
        val rise = before(.833)
        val set = after(-.833)
        if (rise != null) result[PrayerKind.SUNRISE] = rise
        if (set != null) result[PrayerKind.MAGHRIB] = set
        before(method.fajrAngle)?.let { result[PrayerKind.FAJR] = it }
        val shadow = if (hanafi) 2.0 else 1.0
        val asrAngle = deg(atan(1.0 / (shadow + tan(abs(rad(place.latitude) - solar.decl)))))
        after(asrAngle)?.let { result[PrayerKind.ASR] = it }
        if (method.ishaAngle != null) {
            after(-method.ishaAngle)?.let { result[PrayerKind.ISHA] = it }
        } else if (set != null) {
            // Umm al-Qura uses 120 min after Maghrib during Ramadan, 90 otherwise.
            val ramadan = runCatching { HijrahDate.from(transit.atZone(zone)).get(ChronoField.MONTH_OF_YEAR) == 9 }.getOrDefault(false)
            result[PrayerKind.ISHA] = instantPlusMinutes(set, if (ramadan) 120.0 else 90.0)
        }
        if (result[PrayerKind.FAJR] == null && rise != null && set != null) {
            val night = rise.epochSecond - set.minusSeconds(86400).epochSecond
            if (night > 0) result[PrayerKind.FAJR] = instantPlusMinutes(rise, -night / 60.0 * method.fajrAngle / 60.0)
        }
        if (result[PrayerKind.ISHA] == null && set != null && rise != null) {
            val night = rise.plusSeconds(86400).epochSecond - set.epochSecond
            if (night > 0) result[PrayerKind.ISHA] = instantPlusMinutes(set, night / 60.0 * (method.ishaAngle ?: 17.0) / 60.0)
        }
        if (rise != null) result[PrayerKind.DUHA] = instantPlusMinutes(rise, 20.0)
        return result
    }

    fun calculate(date: LocalDate, place: PrayerPlace, method: PrayerMethod = PrayerMethod.UMM_AL_QURA, hanafi: Boolean = false): PrayerSchedule {
        if (!place.valid) return PrayerSchedule(place, date, emptyMap(), null)
        val today = baseDay(date, place, method, hanafi).toMutableMap()
        val nextFajr = baseDay(date.plusDays(1), place, method, hanafi)[PrayerKind.FAJR]
        val sunset = today[PrayerKind.MAGHRIB]
        if (sunset != null && nextFajr != null && nextFajr > sunset) {
            today[PrayerKind.TAHAJJUD] = sunset.plusMillis((nextFajr.toEpochMilli() - sunset.toEpochMilli()) * 2 / 3)
        }
        return PrayerSchedule(place, date, today, today[PrayerKind.DHUHR]?.minusSeconds(900))
    }

    fun upcoming(now: Instant, place: PrayerPlace, method: PrayerMethod, hanafi: Boolean): Pair<PrayerKind, Instant>? {
        val local = now.atZone(place.zone).toLocalDate()
        val ritual = setOf(PrayerKind.FAJR, PrayerKind.DHUHR, PrayerKind.ASR, PrayerKind.MAGHRIB, PrayerKind.ISHA)
        return (0L..2L).asSequence().flatMap { shift -> calculate(local.plusDays(shift), place, method, hanafi).times.asSequence() }
            .filter { it.key in ritual && it.value > now }.minByOrNull { it.value }?.let { it.key to it.value }
    }
}
