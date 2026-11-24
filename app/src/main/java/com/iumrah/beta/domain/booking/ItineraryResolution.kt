package com.iumrah.beta.domain.booking

import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.models.booking.BookingItineraryResponse
import kotlinx.coroutines.CancellationException

/** Manager-published schedule wins, including a deliberately empty managed schedule. */
object ItineraryResolution {
    suspend fun resolve(
        bookingID: String,
        operational: suspend (localized: Boolean) -> BookingItineraryResponse,
        packageSchedule: suspend () -> BookingItineraryResponse,
    ): BookingItineraryResponse {
        fun verified(value: BookingItineraryResponse): BookingItineraryResponse {
            check(value.ok && value.bookingID == bookingID && value.items.all { it.bookingID == bookingID }) { "Invalid itinerary response" }
            return value.copy(items = value.items.sortedWith(compareBy({ it.dateLocal }, { it.sortOrder })))
        }
        suspend fun optional(load: suspend () -> BookingItineraryResponse): BookingItineraryResponse? {
            val result = try { load() } catch (e: CancellationException) { throw e }
            catch (e: APIException) {
                if (BookingAuthFallbackPolicy.isAuthorizationFailure(e)) throw e
                null
            } catch (_: java.io.IOException) { null }
            return result?.let(::verified)
        }
        val current = optional { operational(false) }
        if (current?.managed == true) return current
        val generated = optional { packageSchedule() }
        if (generated != null && generated.items.isNotEmpty()) return generated
        return verified(operational(true))
    }
}
