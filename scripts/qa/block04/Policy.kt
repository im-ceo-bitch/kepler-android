import com.iumrah.beta.domain.prayer.*
import com.iumrah.beta.domain.umrahflow.*
import java.time.*
fun main(){
 var checks=0
 fun verify(value:Boolean){check(value); checks++}
 verify(AdvisorContentPolicy.merge(mapOf("a" to "UZ"), mapOf("a" to "old", "b" to " "), mapOf("c" to "new")) == mapOf("a" to "UZ", "c" to "new"))
 verify(AdvisorContentPolicy.merge(mapOf("a" to "UZ"), emptyMap(), mapOf("a" to " "))["a"]=="UZ")
 verify(AdvisorContentPolicy.display(emptyMap(),mapOf("a" to "EN"))["a"]=="EN")
 verify(AdvisorContentPolicy.display(mapOf("a" to "UZ"),mapOf("a" to "EN"))["a"]=="UZ")
 verify((0..2).map(AdvisorContentPolicy::nextTawafText)==listOf(1,2,0))
 verify(AdvisorCheckpoint(safaReadingStep=2,tawafTextMode=2).normalized()?.safaReadingStep==1)
 verify(AdvisorCheckpoint(tawafTextMode=2).normalized()?.tawafTextMode==2)
 verify(TawafCounter(6).confirm().complete)
 verify(TawafCounter(7).confirm().completed==7)
 verify(TawafCounter(0).undo().completed==0)
 val p=PrayerPlace.presets.first();val now=Instant.parse("2026-11-25T00:00:00Z")
 val rules=PrayerKind.entries.associateWith{PrayerReminderRule(true,0,false,10)}
 val alarms=PrayerNotificationPolicy.build(now,p,PrayerMethod.UMM_AL_QURA,false,rules)
 verify(alarms.size==56);verify(alarms.map{it.slot}.distinct().size==56);verify(alarms.all{it.at>now.plusSeconds(10)});verify(alarms.all{!it.sound});verify(alarms.all{it.lead in listOf(0,10)})
 verify(PrayerNotificationPolicy.build(now,p,PrayerMethod.UMM_AL_QURA,false,emptyMap()).isEmpty())
 val schedule=PrayerCalculator.calculate(now.atZone(p.zone).toLocalDate(),p)
 val fajr=schedule.at(PrayerKind.FAJR)!!
 val early=PrayerNotificationPolicy.build(fajr.minusSeconds(20*60),p,PrayerMethod.UMM_AL_QURA,false,mapOf(PrayerKind.FAJR to PrayerReminderRule(true,-30,true,5)))
 verify(early.none{it.at<fajr});verify(early.all{it.kind==PrayerKind.FAJR})
 val regular=PrayerNotificationPolicy.build(fajr.minusSeconds(60*60),p,PrayerMethod.UMM_AL_QURA,false,mapOf(PrayerKind.FAJR to PrayerReminderRule(true,5,true,10)))
 verify(regular.first().at==fajr.plusSeconds(300));verify(regular[1].at==fajr.minusSeconds(600));verify(regular.size<=16)
 println("PASS $checks real Kotlin policy assertions")
}
