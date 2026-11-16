package com.iumrah.beta.data.smart

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.models.hotel.StorefrontPackagesEnvelope
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Public discovery reads only. Same five-minute bounded cache as iOS, no live generation. */
object HomePublicContentCache {
    private data class Entry(val time: Long, val values: List<StorefrontPackageSnapshot>)
    private val lock = Mutex()
    private val entries = linkedMapOf<String, Entry>()
    private val api = APIClient()
    suspend fun packages(origin: String): List<StorefrontPackageSnapshot> = lock.withLock {
        val key = origin.uppercase()
        val now = android.os.SystemClock.elapsedRealtime()
        entries[key]?.takeIf { now - it.time < 300_000 }?.let { return@withLock it.values }
        val result = try {
            val response = api.get<StorefrontPackagesEnvelope>("/api/storefront/packages", mapOf("mode" to "hotel-first", "origins" to key, "limit" to "24"), timeoutSeconds = 15)
            if (!response.ok) emptyList() else response.items.filter {
                it.status.equals("ready", true) && it.currency.equals("USD", true) &&
                    it.pricePerPerson?.let { price -> price.isFinite() && price > 0 } == true &&
                    it.originCode.equals(key, true) && it.id.matches(Regex("[0-9]{10}")) &&
                    it.startDate?.take(10)?.let { day -> runCatching { LocalDate.parse(day) >= LocalDate.now() }.getOrDefault(false) } == true
            }.sortedBy { it.startDate }.take(4)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        if (entries.size >= 8) entries.clear()
        entries[key] = Entry(android.os.SystemClock.elapsedRealtime(), result)
        result
    }
}
