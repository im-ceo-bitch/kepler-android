import com.iumrah.beta.domain.umrahflow.*
import com.iumrah.beta.domain.prayer.*
fun main() {
 var count=0; fun checkThat(ok:Boolean) { check(ok); count++ }
 var c=TawafCounter(); repeat(6){c=c.confirm();checkThat(!c.complete)}; checkThat(c.round==7); c=c.confirm();checkThat(c.complete);checkThat(c.confirm()==c);checkThat(c.undo().completed==6);checkThat(TawafCounter().undo().completed==0)
 val p=AdvisorCheckpoint(stage="TAWAF",tawafCompleted=3);checkThat(p.copy(tawafReadingStep=6,tawafMode="READ").progressFraction==p.progressFraction)
 checkThat(AdvisorCheckpoint(stage="AFTER").normalized()==null);checkThat(AdvisorCheckpoint(stage="x").normalized()==null);checkThat(AdvisorCheckpoint(tawafCompleted=99).normalized()!!.tawafCompleted==7)
 val now=10_000_000_000L;checkThat(AdvisorCheckpoint.isFresh(now-1,now));checkThat(!AdvisorCheckpoint.isFresh(now-60L*24*60*60*1000,now));checkThat(!AdvisorCheckpoint.isFresh(now+60_000,now));checkThat(!AdvisorCheckpoint.isFresh(0,now))
 checkThat(kotlin.math.abs(QiblaBearing.degrees(30.0,39.8262)!!-180)<.001);checkThat(kotlin.math.abs(QiblaBearing.degrees(10.0,39.8262)!!)<.001);checkThat(QiblaBearing.relative(10.0,20.0)==350.0);checkThat(QiblaBearing.degrees(21.4225,39.8262)==null);checkThat(QiblaBearing.degrees(Double.NaN,39.8262)==null)
 println("P11 JVM: $count assertions passed")
}
