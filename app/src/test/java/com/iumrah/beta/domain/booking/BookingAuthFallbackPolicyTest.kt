package com.iumrah.beta.domain.booking

import com.iumrah.beta.core.network.APIException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingAuthFallbackPolicyTest {
    @Test fun authAndNotFoundErrorsAllowAccountProof() {
        listOf(401, 403, 404).forEach { code ->
            assertTrue(BookingAuthFallbackPolicy.mayRetryWithAccount(APIException.Status(code)))
            assertTrue(BookingAuthFallbackPolicy.mayRetryWithAccount(APIException.Server(code, "denied")))
        }
        assertTrue(BookingAuthFallbackPolicy.mayRetryWithAccount(APIException.Server(400, "BOOKING_PROOF_INVALID")))
        assertTrue(BookingAuthFallbackPolicy.mayRetryWithAccount(APIException.Server(400, "BOOKING_NOT_FOUND")))
        assertTrue(BookingAuthFallbackPolicy.mayRetryWithAccount(APIException.Server(400, "unauthorized")))
    }

    @Test fun transportAndBackendFailuresNeverRetryWithAccount() {
        listOf(400, 408, 429, 500, 502, 503).forEach { code ->
            assertFalse(BookingAuthFallbackPolicy.mayRetryWithAccount(APIException.Status(code)))
            assertFalse(BookingAuthFallbackPolicy.mayRetryWithAccount(APIException.Server(code, "temporary unavailable")))
        }
        assertFalse(BookingAuthFallbackPolicy.mayRetryWithAccount(APIException.InvalidResponse))
        assertFalse(BookingAuthFallbackPolicy.mayRetryWithAccount(IllegalStateException("offline")))
    }
}
