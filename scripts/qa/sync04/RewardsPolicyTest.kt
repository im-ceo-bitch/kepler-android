import com.iumrah.beta.data.account.ReferralPolicy as R
import com.iumrah.beta.domain.booking.BookingRewardsPolicy as B
import com.iumrah.beta.models.account.*

fun main() {
    var n=0
    fun test(value:Boolean){check(value){"Check ${n+1} failed"};n++}
    val code="IUMR-0123456789ABCDEF01234567"
    test(R.normalize(" $code ")==code)
    test(R.normalize("abcd0123456789ef")=="ABCD0123456789EF")
    test(R.normalize("IUMR-123")==null)
    test(R.normalize("$code\nmore")==null)
    for (host in listOf("iumrah.app","iumrah.uz","go.iumrah.app","go.iumrah.uz","iumrah-backend.helloiumrah.workers.dev")) {
        test(R.capture("https://$host/ref/$code")==code)
        test(R.capture("https://$host/r/$code")==code)
    }
    test(R.capture("iumrahapp://referral/$code")==code)
    for (url in listOf("http://iumrah.app/ref/$code","https://iumrah.app.evil.test/ref/$code","https://evil.test/ref/$code","https://evil.test@iumrah.app/ref/$code","https://iumrah.app:443/ref/$code","https://iumrah.app/ref/$code/extra","https://iumrah.app/hotel/$code","iumrahapp://hotel/$code","https://iumrah.app/ref/invalid")) test(R.capture(url)==null)
    test(R.headers(code,false).isEmpty());test(R.headers(null,true).isEmpty());test(R.headers("bad",true).isEmpty());test(R.headers(code,true)==mapOf("x-iumrah-referral-code" to code))
    val rewards=IumrahBookingRewardsAmounts(1000.0,0.0,0.0,0.0,1000.0)
    val summary=IumrahFriendsBookingSummary(true,"b1",true,1000.0,200.0,0.0,0.0,0.0,200.0,1000.0,250.0,emptyList())
    test(B.confirmed(rewards,summary));test(B.confirmed(rewards,null));test(!B.confirmed(null,summary));test(!B.confirmed(rewards.copy(payableUsd=999.0),summary));test(!B.confirmed(rewards.copy(totalUsd=Double.NaN),summary));test(!B.confirmed(rewards,summary.copy(ok=false)))
    fun amounts(s:IumrahFriendsBookingSummary=summary, status:String="payment_pending", receiptsEmpty:Boolean=true, r:IumrahBookingRewardsAmounts?=rewards, id:String="b1")=B.amounts(status,receiptsEmpty,r,s,id)
    test(amounts()==listOf(100,200));test(amounts(summary.copy(availableCreditUsd=100.0))==listOf(100));test(amounts(summary.copy(remainingAllowanceUsd=100.0))==listOf(100));test(amounts(summary.copy(availableCreditUsd=99.99)).isEmpty());test(amounts(summary.copy(identityConfirmed=false)).isEmpty());test(amounts(summary.copy(availableCreditUsd=Double.POSITIVE_INFINITY)).isEmpty());test(amounts(status="paid").isEmpty());test(amounts(receiptsEmpty=false).isEmpty());test(amounts(id="b2").isEmpty());test(amounts(r=null).isEmpty());test(amounts(summary.copy(maxDiscountUsd=100.0))==listOf(100));test(amounts(summary.copy(totalDiscountUsd=100.0)).isEmpty())
    val applied=rewards.copy(creditAppliedUsd=100.0,totalDiscountUsd=100.0,payableUsd=900.0)
    val appliedSummary=summary.copy(creditAppliedUsd=100.0,totalDiscountUsd=100.0,payableUsd=900.0,remainingAllowanceUsd=100.0,availableCreditUsd=150.0)
    test(B.confirmed(applied,appliedSummary));test(amounts(appliedSummary,r=applied).isEmpty());test(!B.confirmed(applied,summary));test(!B.confirmed(applied.copy(totalDiscountUsd=99.0),appliedSummary))
    println("Sync04: $n referral and booking rewards policy checks passed")
}
