import com.iumrah.beta.domain.prayer.*
import java.time.*
fun main() {
 val dates=listOf("2026-02-18","2026-03-19","2026-03-29","2026-06-21","2026-10-25","2026-12-21","2027-02-09")
 for(p in PrayerPlace.presets) for(d in dates) for(m in PrayerMethod.entries) for(h in listOf(false,true)) {
 val s=PrayerCalculator.calculate(LocalDate.parse(d),p,m,h)
 for(k in PrayerKind.entries) s.at(k)?.let{ println("${p.id}|$d|${m.ordinal}|$h|${k.name.lowercase()}|${it.toEpochMilli()/1000.0}") }
 }
}
