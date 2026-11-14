package com.iumrah.beta.data.flight

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.network.APIException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable data class IumrahFlightOperationalDetail(
    val status: String? = null, val updatedAt: String? = null,
    val departureTerminal: String? = null, val departureGate: String? = null,
    val arrivalTerminal: String? = null, val arrivalGate: String? = null,
    val baggageBelt: String? = null, val departureScheduled: String? = null,
    val arrivalScheduled: String? = null, val departureRevised: String? = null,
    val arrivalRevised: String? = null, val departureActual: String? = null,
    val arrivalActual: String? = null, val arrivalEstimatedUTC: String? = null,
    val aircraft: String? = null, val registration: String? = null,
    val position: Position? = null,
) {
    @Serializable data class Position(val latitude: Double, val longitude: Double,
        val reportedAt: String? = null, val altitudeFeet: Double? = null, val speedKmh: Double? = null)
}
@Serializable data class IumrahFlightDetailResult(val detail: IumrahFlightOperationalDetail? = null, val reason: String? = null)

/** Same shared request identity, five-minute successful-result cache and no automatic polling as iOS. */
class IumrahFlightDetailService(private val api: APIClient = APIClient()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private data class Entry(val result: IumrahFlightDetailResult, val savedAt: Long)
    private val cache = mutableMapOf<String, Entry>()
    private val inFlight = mutableMapOf<String, Deferred<IumrahFlightDetailResult>>()

    suspend fun detail(number: String, day: String, time: String, origin: String, destination: String,
                       force: Boolean = false): IumrahFlightDetailResult {
        val key = FlightOperationalPolicy.key(number, day, time, origin, destination)
            ?: return IumrahFlightDetailResult(reason = "invalid_request")
        val request = mutex.withLock {
            inFlight[key]?.let { return@withLock it }
            val now = System.currentTimeMillis()
            cache.entries.removeAll { !FlightOperationalPolicy.fresh(it.value.savedAt, now) }
            if (!force) cache[key]?.let { return it.result }
            val parts = key.split('|')
            scope.async(start = CoroutineStart.LAZY) {
                val result = fetch(parts)
                mutex.withLock {
                    inFlight.remove(key)
                    if (result.detail != null) cache[key] = Entry(result, System.currentTimeMillis())
                }
                result
            }.also { inFlight[key] = it; it.start() }
        }
        return request.await()
    }
    private suspend fun fetch(parts: List<String>): IumrahFlightDetailResult = try {
        val result = api.get<IumrahFlightDetailResult>("/api/package/flights/detail", query =
            listOf("number", "date", "time", "origin", "destination").zip(parts).toMap(), timeoutSeconds = 15)
        if (result.detail == null && result.reason == null) result.copy(reason = "no_match") else result
    } catch (cancel: CancellationException) { throw cancel
    } catch (error: APIException.Status) { IumrahFlightDetailResult(reason = "http_${error.code}")
    } catch (error: APIException.Server) { IumrahFlightDetailResult(reason = "http_${error.code}")
    } catch (_: APIException.Decoding) { IumrahFlightDetailResult(reason = "invalid_response")
    } catch (_: APIException) { IumrahFlightDetailResult(reason = "request_failed")
    } catch (_: Exception) { IumrahFlightDetailResult(reason = "network_error") }

    companion object { val shared by lazy { IumrahFlightDetailService() } }
}
