package com.iumrah.beta.data.hotel

import android.content.Context
import android.util.AtomicFile
import com.iumrah.beta.models.hotel.HotelSummary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Public metadata cache matching the offline hotel-list behavior on iOS.
 * Do not store supplier fares, package quotes, payment/KYC or user data here.
 * Write atomically so a terminated app cannot leave a partially written JSON.
 */
internal class StorefrontCatalogCache(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.cacheDir, "iumrah-storefront-hotels-v1.json"))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun restore(): CatalogSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val disk = json.decodeFromString<DiskSnapshot>(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            if (disk.version != 1 || !catalogCacheIsFresh(disk.savedAtMillis, System.currentTimeMillis())) null
            else CatalogSnapshot(disk.makkah, disk.madinah)
        }.getOrNull()
    }

    suspend fun save(makkah: List<HotelSummary>, madinah: List<HotelSummary>) = withContext(Dispatchers.IO) {
        if (makkah.isEmpty() && madinah.isEmpty()) return@withContext
        val bytes = json.encodeToString(DiskSnapshot(
            savedAtMillis = System.currentTimeMillis(), makkah = makkah, madinah = madinah,
        )).toByteArray(Charsets.UTF_8)
        if (bytes.size > 4 * 1024 * 1024) return@withContext
        runCatching {
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (failure: Throwable) {
                file.failWrite(stream)
                throw failure
            }
        }
    }
}

internal data class CatalogSnapshot(val makkah: List<HotelSummary>, val madinah: List<HotelSummary>)

@Serializable
private data class DiskSnapshot(
    val version: Int = 1,
    val savedAtMillis: Long,
    val makkah: List<HotelSummary>,
    val madinah: List<HotelSummary>,
)
