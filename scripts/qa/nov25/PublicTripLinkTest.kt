import com.iumrah.beta.data.booking.PublicTripLinkPolicy
fun main() {
    val token = "a".repeat(43)
    val good = "https://iumrah.app/s/$token"
    check(PublicTripLinkPolicy.isPublicShare(good))
    listOf("http://iumrah.app/s/$token", "$good?token=private", "$good#private", "https://iumrah.app.evil.test/s/$token", "https://secret@iumrah.app/s/$token", "https://iumrah.app:8443/s/$token", "https://iumrah.app/s/short", "$good/", "https://iumrah.app/api/bookings/$token", "https://iumrah.app/s/%61${token.drop(1)}").forEach { check(!PublicTripLinkPolicy.isPublicShare(it)) { it } }
    println("Public viewer URL policy: 11 checks passed")
}
