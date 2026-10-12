package com.iumrah.beta.domain.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerCalculatorTest {
    private fun time(schedule: PrayerSchedule, kind: PrayerKind): String =
        schedule.at(kind)!!.atZone(schedule.place.zone).format(DateTimeFormatter.ofPattern("HH:mm"))

    @Test fun matchesIOSSwiftOctober12EightCities() {
        // Golden output generated from the uploaded iOS PrayerCalculator.swift, not an external web API.
        val expected = mapOf(
            "makkah" to listOf("04:59", "06:15", "12:07", "15:28", "17:59", "19:29", "06:35", "01:19"),
            "madinah" to listOf("05:00", "06:18", "12:08", "15:28", "17:58", "19:28", "06:38", "01:19"),
            "tashkent" to listOf("04:56", "06:31", "12:09", "15:17", "17:47", "19:17", "06:51", "01:14"),
            "samarkand" to listOf("05:07", "06:39", "12:18", "15:28", "17:58", "19:28", "06:59", "01:24"),
            "istanbul" to listOf("05:38", "07:12", "12:50", "15:58", "18:28", "19:58", "07:32", "01:55"),
            "dubai" to listOf("04:57", "06:15", "12:05", "15:25", "17:55", "19:25", "06:35", "01:16"),
            "jakarta" to listOf("04:20", "05:32", "11:39", "14:42", "17:45", "19:15", "05:52", "00:48"),
            "london" to listOf("05:25", "07:19", "12:46", "15:37", "18:14", "19:44", "07:39", "01:42"),
        )
        val order = listOf(PrayerKind.FAJR, PrayerKind.SUNRISE, PrayerKind.DHUHR, PrayerKind.ASR, PrayerKind.MAGHRIB, PrayerKind.ISHA, PrayerKind.DUHA, PrayerKind.TAHAJJUD)
        for (place in PrayerPlace.presets) {
            val schedule = PrayerCalculator.calculate(LocalDate.of(2026, 10, 12), place)
            assertEquals(place.id, expected[place.id], order.map { time(schedule, it) })
        }
    }

    @Test fun methodsAndAsrVariantsAreRespected() {
        val place = PrayerPlace.presets.first()
        val date = LocalDate.of(2026, 10, 12)
        val base = PrayerCalculator.calculate(date, place)
        val hanafi = PrayerCalculator.calculate(date, place, hanafi = true)
        val northAmerica = PrayerCalculator.calculate(date, place, method = PrayerMethod.NORTH_AMERICA)
        assertTrue(hanafi.at(PrayerKind.ASR)!! > base.at(PrayerKind.ASR)!!)
        assertTrue(northAmerica.at(PrayerKind.FAJR)!! > base.at(PrayerKind.FAJR)!!)
        assertTrue(base.at(PrayerKind.DUHA)!! > base.at(PrayerKind.SUNRISE)!!)
        assertTrue(base.at(PrayerKind.TAHAJJUD)!! > base.at(PrayerKind.MAGHRIB)!!)
    }

    @Test fun nextPrayerExcludesSunriseDuhaTahajjud() {
        val place = PrayerPlace.presets[0]
        val date = LocalDate.of(2026, 10, 12)
        val schedule = PrayerCalculator.calculate(date, place)
        val now = schedule.at(PrayerKind.FAJR)!!.plusSeconds(1)
        val next = PrayerCalculator.upcoming(now, place, PrayerMethod.UMM_AL_QURA, false)
        assertEquals(PrayerKind.DHUHR, next?.first)
    }

    @Test fun invalidCoordinatesCannotCreateSchedule() {
        val invalid = PrayerPlace("bad", "Bad", Double.NaN, 200.0, "Asia/Riyadh")
        assertFalse(invalid.valid)
        assertTrue(PrayerCalculator.calculate(LocalDate.of(2026,10,12), invalid).times.isEmpty())
    }
}
