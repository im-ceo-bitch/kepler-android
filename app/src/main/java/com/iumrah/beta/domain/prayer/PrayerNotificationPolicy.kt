package com.iumrah.beta.domain.prayer
import java.time.Instant

data class PrayerReminderRule(val enabled: Boolean, val offset: Int, val sound: Boolean, val earlyMinutes: Int)
data class PrayerReminderSlot(val slot: Int, val kind: PrayerKind, val at: Instant, val lead: Int, val sound: Boolean)
object PrayerNotificationPolicy {
    val order = listOf(PrayerKind.FAJR, PrayerKind.SUNRISE, PrayerKind.DUHA, PrayerKind.DHUHR, PrayerKind.ASR, PrayerKind.MAGHRIB, PrayerKind.ISHA, PrayerKind.TAHAJJUD)
    fun build(now: Instant, place: PrayerPlace, method: PrayerMethod, hanafi: Boolean, rules: Map<PrayerKind, PrayerReminderRule>): List<PrayerReminderSlot> {
        val cutoff = now.plusSeconds(10)
        val today = now.atZone(place.zone).toLocalDate()
        val result = mutableListOf<PrayerReminderSlot>()
        for (day in 0..7) {
            val times = PrayerCalculator.calculate(today.plusDays(day.toLong()), place, method, hanafi)
            for (kind in order) {
                val rule = rules[kind]?.takeIf { it.enabled } ?: continue
                val fire = times.at(kind) ?: continue
                val regular = fire.plusSeconds(rule.offset.coerceIn(-30, 30)*60L)
                if (regular <= cutoff) continue
                val slot = (day * 8 + kind.ordinal)*2
                if (result.size < 56) result.add(PrayerReminderSlot(slot, kind, regular, 0, rule.sound))
                val lead = rule.earlyMinutes
                if (lead in setOf(5,10,15,30) && result.size < 56) {
                    val early = fire.minusSeconds(lead*60L)
                    if (early > cutoff) result.add(PrayerReminderSlot(slot+1, kind, early, lead, rule.sound))
                }
                if (result.size == 56) return result
            }
        }
        return result
    }
}
