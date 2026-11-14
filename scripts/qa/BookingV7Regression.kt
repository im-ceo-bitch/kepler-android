package com.iumrah.beta.domain.booking

import com.iumrah.beta.core.network.APIException

fun main() {
    var count = 0
    fun expect(ok: Boolean, label: String) { check(ok) { label }; count++ }
    val policy = BookingAuthFallbackPolicy
    for (code in listOf(401, 403)) {
        expect(policy.isAuthorizationFailure(APIException.Status(code)), "auth $code")
        expect(policy.isAuthorizationFailure(APIException.Server(code, "denied")), "auth server $code")
    }
    for (code in listOf(404, 410, 408, 429, 500, 503)) {
        expect(!policy.isAuthorizationFailure(APIException.Status(code)), "not auth $code")
    }
    for (message in listOf("TOKEN_EXPIRED", "BOOKING_PROOF_INVALID", "unauthorized")) {
        expect(policy.isAuthorizationFailure(APIException.Server(400, message)), "auth marker $message")
    }
    expect(!policy.isAuthorizationFailure(IllegalStateException("offline")), "transport failure")
    expect(policy.isConfirmedMissing(APIException.Status(404)), "404 missing")
    expect(policy.isConfirmedMissing(APIException.Server(410, "gone")), "410 gone")
    expect(!policy.isConfirmedMissing(APIException.Status(401)), "expired proof is not missing")
    expect(!policy.isConfirmedMissing(APIException.Server(500, "NOT_FOUND")), "5xx must retain cache")
    expect(policy.mayRetryWithAccount(APIException.Status(404)), "checkout retains historical 404 fallback")
    expect(policy.mayRetryWithAccount(APIException.Server(400, "TOKEN_EXPIRED")), "chat/checkout expired proof")
    expect(BookingSessionPolicy.visible("guest-proof", null, null), "guest persists after sign out")
    expect(BookingSessionPolicy.visible("", "account-A", "account-A"), "own trip")
    expect(!BookingSessionPolicy.visible("", "account-A", "account-B"), "no other-account trip")
    expect(!BookingSessionPolicy.visible("", "account-A", null), "no signed-out account cache")
    expect(!BookingSessionPolicy.visible("", null, "account-A"), "unscoped legacy entry requires restore")
    expect(BookingSessionPolicy.pilgrimDisplay("123") == "00000123", "eight digit display")
    expect(BookingSessionPolicy.pilgrimDisplay("00000123") == "00000123", "already formatted")
    for (raw in listOf(null, "", "0", "00000000", "abc123", "123456789", "-123", "１２３")) {
        expect(BookingSessionPolicy.pilgrimDisplay(raw) == null, "invalid identity $raw")
    }
    expect(BookingSessionPolicy.preserveRoom("hotel1", true, "hotel1", false, null), "retain full local room")
    expect(!BookingSessionPolicy.preserveRoom("hotel1", true, "hotel2", false, null), "new hotel")
    expect(!BookingSessionPolicy.preserveRoom("hotel1", true, "hotel1", true, null), "new category")
    expect(!BookingSessionPolicy.preserveRoom("hotel1", true, "hotel1", false, "room2"), "new room")
    println("V7 booking/identity policy: $count checks passed")
}
