package com.iumrah.beta.domain.booking

import com.iumrah.beta.core.network.APIException

/** Only authorization/not-found errors may trigger an account-token retry.
 * Network, 5xx and decoding failures must propagate without a second request.
 */
object BookingAuthFallbackPolicy {
    fun mayRetryWithAccount(error: Throwable): Boolean = when (error) {
        is APIException.Status -> error.code in setOf(401, 403, 404)
        is APIException.Server -> error.code in setOf(401, 403, 404) ||
            listOf("UNAUTHORIZED", "BOOKING_NOT_FOUND", "BOOKING_PROOF_INVALID")
                .any { it in error.serverMessage.trim().uppercase() }
        else -> false
    }
}
