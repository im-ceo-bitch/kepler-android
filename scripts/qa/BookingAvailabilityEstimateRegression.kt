import com.iumrah.beta.domain.booking.BookingAvailabilityEstimate as E
import java.time.Instant
fun main() {
    val start = "2026-11-14T11:00:00.000Z"
    val created = "2026-11-14T10:58:00Z"
    val t0 = Instant.parse(start).toEpochMilli()
    val end = E.deadline(start, created, "2026-11-14T17:00:00Z")!!
    check(end == t0 + 1800000)
    check(E.remainingSeconds(end,t0)==1800L)
    check(E.remainingSeconds(end,t0+61000)==1739L)
    check(E.remainingSeconds(end,t0+1799900)==1L)
    check(E.remainingSeconds(end,t0+1800000)==0L)
    check(E.remainingSeconds(end,t0+7200000)==0L)
    check(E.remainingSeconds(end,t0-60000)==1800L)
    check(E.deadline(null,created,null)==t0+1680000)
    check(E.deadline("invalid",created,null)==t0+1680000)
    check(E.deadline(start,created,"2026-11-14T11:15:00Z")==t0+900000)
    check(E.deadline(null,"invalid",null)==null)
    println("11/11 availability estimate checks passed")
}
