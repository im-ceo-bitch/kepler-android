package com.iumrah.beta.domain.booking

import com.iumrah.beta.core.network.APIException
import org.junit.Assert.*
import org.junit.Test

class BookingSessionPolicyTest {
    @Test fun refreshFallbackOnlyForAuthorization() {
        assertTrue(BookingAuthFallbackPolicy.isAuthorizationFailure(APIException.Status(401)))
        assertTrue(BookingAuthFallbackPolicy.isAuthorizationFailure(APIException.Server(400, "TOKEN_EXPIRED")))
        assertFalse(BookingAuthFallbackPolicy.isAuthorizationFailure(APIException.Status(404)))
        assertFalse(BookingAuthFallbackPolicy.isAuthorizationFailure(APIException.Status(503)))
        assertTrue(BookingAuthFallbackPolicy.isConfirmedMissing(APIException.Status(410)))
        assertFalse(BookingAuthFallbackPolicy.isConfirmedMissing(APIException.Server(500, "NOT_FOUND")))
    }
    @Test fun accountCacheIsScopedAndGuestProofSurvivesSignOut() {
        assertTrue(BookingSessionPolicy.visible("proof", null, null))
        assertTrue(BookingSessionPolicy.visible("", "A", "A"))
        assertFalse(BookingSessionPolicy.visible("", "A", "B"))
        assertFalse(BookingSessionPolicy.visible("", "A", null))
        assertFalse(BookingSessionPolicy.visible("", null, "A"))
    }
    @Test fun invalidServerIdentityIsNeverMadeToLookValid() {
        assertEquals("00000123", BookingSessionPolicy.pilgrimDisplay("123"))
        assertNull(BookingSessionPolicy.pilgrimDisplay("abc123"))
        assertNull(BookingSessionPolicy.pilgrimDisplay("0"))
    }
    @Test fun partialRemoteSnapshotDoesNotDiscardLocalRoom() {
        assertTrue(BookingSessionPolicy.preserveRoom("h", true, "h", false, null))
        assertFalse(BookingSessionPolicy.preserveRoom("h", true, "other", false, null))
        assertFalse(BookingSessionPolicy.preserveRoom("h", true, "h", false, "room"))
    }
}
