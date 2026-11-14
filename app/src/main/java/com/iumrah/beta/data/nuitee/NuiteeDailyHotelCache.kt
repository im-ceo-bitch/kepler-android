package com.iumrah.beta.data.nuitee

import android.content.Context
import android.util.AtomicFile
import com.iumrah.beta.models.nuitee.NuiteeHotelListEnvelope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Public catalogue only: no account, booking, final quote or room-selection responses. */
internal class NuiteeDailyHotelCache private constructor(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "iumrah-nuitee-daily-v1")
    private val hot = NuiteeHotCache<Entry>()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var lastCleanup = Long.MIN_VALUE

    @Serializable private data class Entry(val savedAt: Long, val response: NuiteeHotelListEnvelope)

    private fun file(key: String): AtomicFile = AtomicFile(File(directory,
        key.map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("") + ".json"))

    suspend fun read(key: String, now: Long = System.currentTimeMillis()): NuiteeHotelListEnvelope? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                hot.get(key)?.takeIf { NuiteeDailyCachePolicy.fresh(it.savedAt, now) }?.let { return@withLock it.response }
                hot.remove(key)
                val disk = file(key)
                val entry = runCatching {
                    json.decodeFromString<Entry>(disk.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
                }.getOrNull()
                if (entry == null || !NuiteeDailyCachePolicy.fresh(entry.savedAt, now)) {
                    runCatching { disk.delete() }
                    return@withLock null
                }
                hot.put(key, entry)
                entry.response
            }
        }

    suspend fun write(key: String, response: NuiteeHotelListEnvelope, now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                // Listing rates are indicative only. Remove supplier selection identifiers from disk and memory.
                val publicResponse = response.copy(hotels = response.hotels.map { hotel ->
                    hotel.copy(minimumRate = hotel.minimumRate?.copy(offerId = null))
                })
                val entry = Entry(now, publicResponse)
                hot.put(key, entry)
                runCatching {
                    directory.mkdirs()
                    val disk = file(key)
                    val stream = disk.startWrite()
                    try {
                        stream.write(json.encodeToString(Entry.serializer(), entry).toByteArray(Charsets.UTF_8))
                        disk.finishWrite(stream)
                    } catch (error: Throwable) {
                        disk.failWrite(stream)
                        throw error
                    }
                    if (lastCleanup == Long.MIN_VALUE || now - lastCleanup >= 3_600_000L) {
                        lastCleanup = now
                        directory.listFiles()?.filter { it.extension == "json" }?.forEach { old ->
                            val modified = old.lastModified()
                            if (modified < now && !NuiteeDailyCachePolicy.fresh(modified, now)) AtomicFile(old).delete()
                        }
                    }
                }
                Unit // disk failures must not block search/booking
            }
        }

    companion object {
        @Volatile private var instance: NuiteeDailyHotelCache? = null
        fun shared(context: Context): NuiteeDailyHotelCache = instance ?: synchronized(this) {
            instance ?: NuiteeDailyHotelCache(context).also { instance = it }
        }
    }
}
