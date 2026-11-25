import com.iumrah.beta.data.notification.NotificationScopeGate
import com.iumrah.beta.domain.booking.BookingIdentityPolicy as P
import com.iumrah.beta.data.booking.PublicTripLinkPolicy
fun main() {
    var n=0
    fun verify(ok:Boolean) { check(ok) { "case ${n+1}" }; n++ }
    val a=NotificationScopeGate.fingerprint("Account-A",listOf("b","a"))
    val b=NotificationScopeGate.fingerprint("Account-B",listOf("a","b"))
    verify(a==NotificationScopeGate.fingerprint("Account-A",listOf("a","b","a")))
    verify(a!=b); verify(a!=NotificationScopeGate.fingerprint("Account-A",listOf("a")))
    verify(a.length==64 && !a.contains("Account-A"))
    val gate=NotificationScopeGate(a)
    val reg1=gate.register(); val feed1=gate.refresh()
    verify(gate.registrationCurrent(reg1,a)); verify(gate.feedCurrent(feed1,a))
    val reg2=gate.register(); verify(!gate.registrationCurrent(reg1,a)); verify(gate.registrationCurrent(reg2,a))
    val feed2=gate.refresh(); verify(!gate.feedCurrent(feed1,a)); verify(gate.feedCurrent(feed2,a))
    verify(!gate.feedCurrent(feed2,b)); verify(gate.switch(b)); verify(!gate.registrationCurrent(reg2,b))
    verify(gate.switch(a)); verify(!gate.feedCurrent(feed2,a)); verify(!gate.switch(a))
    verify(gate.feedCurrent(gate.refresh(),a))
    verify(P.canRequestCode("Ali","Karim","sms","+998901234567"))
    verify(!P.canRequestCode("A","Karim","sms","+998901234567"))
    verify(!P.canRequestCode("Ali"," ","sms","+998901234567"))
    verify(!P.contactValid("sms","+966501234567")); verify(!P.contactValid("sms","+99890123456"))
    verify(!P.contactValid("sms","+9989012345670")); verify(P.contactValid("email","ali@example.com"))
    verify(!P.contactValid("email","ali@example")); verify(!P.contactValid("email","ali @example.com"))
    verify(!P.contactValid("email","a@example..com")); verify(!P.contactValid("telegram","@ali"))
    verify(P.initialChannel("","a@example.com")=="email"); verify(P.initialChannel(""," ".trim())=="sms")
    verify(P.initialChannel("+998901234567","a@example.com")=="sms")
    val link="https://iumrah.app/s/"+"a".repeat(43)
    verify(PublicTripLinkPolicy.isPublicShare(link)); verify(!PublicTripLinkPolicy.isPublicShare(link+"?token=private")); verify(!PublicTripLinkPolicy.isPublicShare(link+"#private"))
    verify(!PublicTripLinkPolicy.isPublicShare(link.replace("https:","http:"))); verify(!PublicTripLinkPolicy.isPublicShare(link.replace("iumrah.app","iumrah.app.evil.test")))
    println("Block03: $n actual policy assertions passed (scope, checkout, public URL)")
}
