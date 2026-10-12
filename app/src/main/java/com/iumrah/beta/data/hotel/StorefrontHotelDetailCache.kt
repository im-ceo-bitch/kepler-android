package com.iumrah.beta.data.hotel

import android.content.Context
import android.util.AtomicFile
import com.iumrah.beta.models.hotel.HotelDetail
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Cached public description/photos only; never contains prices for packages or booking/KYC data. */
internal class StorefrontHotelDetailCache(context: Context) {
    private val folder = File(context.applicationContext.cacheDir, "iumrah-hotel-details-v1")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun atomicFile(id: String): AtomicFile {
        // Hash the untrusted hotel ID rather than allowing path separators in a filename.
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return AtomicFile(File(folder, "$digest.json"))
    }

    suspend fun restore(id: String): HotelDetail? = withContext(Dispatchers.IO) {
        if (id.isBlank() || id.length > 512) return@withContext null
        runCatching {
            val raw = atomicFile(id).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val cached = json.decodeFromString<DetailSnapshot>(raw)
            if (cached.version != 1 || !hotelDetailCacheIsFresh(cached.savedAtMillis, System.currentTimeMillis())) null
            else cached.detail.takeIf { it.id == id }
        }.getOrNull()
    }

    suspend fun save(detail: HotelDetail) = withContext(Dispatchers.IO) {
        if (detail.id.isBlank() || detail.id.length > 512) return@withContext
        val bytes = json.encodeToString(DetailSnapshot(
            savedAtMillis = System.currentTimeMillis(), detail = detail,
        )).toByteArray(Charsets.UTF_8)
        if (bytes.size > 2 * 1024 * 1024) return@withContext
        runCatching {
            val file = atomicFile(detail.id)
            folder.mkdirs()
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (error: Throwable) {
                file.failWrite(stream)
                throw error
            }
        }
    }
}

@Serializable
private data class DetailSnapshot(val version: Int = 1, val savedAtMillis: Long, val detail: HotelDetail)
