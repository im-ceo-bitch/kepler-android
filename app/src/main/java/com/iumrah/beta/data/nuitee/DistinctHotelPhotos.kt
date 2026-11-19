package com.iumrah.beta.data.nuitee

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.domain.nuitee.HotelPhotoSignature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Same 15-candidate, 3-photo, 30-minute selection policy as iOS. Public images only. */
object DistinctHotelPhotos {
    private data class Entry(val time: Long, val photos: List<String>)
    private val cache = LinkedHashMap<List<String>, Entry>()
    private val slots = Semaphore(4)
    private val fallbackCache = LinkedHashMap<Pair<String, List<String>>, Entry>()
    private val locks = Array(32) { Mutex() }
    suspend fun selectWithFallback(hotelId: String, candidates: List<String>, loadMore: suspend () -> List<String>): List<String> {
        val key = hotelId to candidates.take(15)
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            synchronized(fallbackCache) { fallbackCache[key]?.takeIf { System.currentTimeMillis()-it.time < 1_800_000 } }?.let { return@withLock it.photos }
            val initial = select(candidates)
            if (initial.size == 3) return@withLock initial
            val extra = try { loadMore() } catch (e: CancellationException) { throw e } catch (_: Exception) { return@withLock initial }
            val result = select((initial + extra + candidates).distinct()).ifEmpty { initial }
            if (result.isNotEmpty()) synchronized(fallbackCache) {
                if (fallbackCache.size >= 160) fallbackCache.clear()
                fallbackCache[key] = Entry(System.currentTimeMillis(), result)
            }
            result
        }
    }
    suspend fun select(candidates: List<String>): List<String> {
        val key = candidates.take(15)
        synchronized(cache) { cache[key]?.takeIf { System.currentTimeMillis()-it.time < 1_800_000 }?.let { return it.photos } }
        val result = slots.withPermit { withContext(Dispatchers.IO) {
            val selected = mutableListOf<String>(); val signatures = mutableListOf<IntArray>()
            for (raw in key) {
                coroutineContext.ensureActive()
                val signature = try { signature(raw) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: continue
                if (signatures.any { HotelPhotoSignature.similar(it,signature) }) continue
                selected.add(raw); signatures.add(signature)
                if (selected.size == 3) break
            }
            selected.toList()
        } }
        if (result.isNotEmpty()) synchronized(cache) {
            if (cache.size >= 160) cache.clear()
            cache[key] = Entry(System.currentTimeMillis(), result)
        }
        return result
    }
    private fun signature(raw: String): IntArray? {
        val url = AppConfig.absoluteUrl(raw) ?: return null
        val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
        try {
            connection.connectTimeout=6000; connection.readTimeout=6000
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16384)
                while (true) { val n=input.read(buffer); if(n<0) break; if(output.size()+n>8_388_608) return null; output.write(buffer,0,n) }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if (bounds.outWidth<=0 || bounds.outHeight<=0) return null
            var sample=1
            while (maxOf(bounds.outWidth,bounds.outHeight)/sample>512) sample*=2
            val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample; inPreferredConfig=Bitmap.Config.ARGB_8888 }) ?: return null
            val thumb=Bitmap.createScaledBitmap(image,16,16,true)
            return IntArray(256).also { thumb.getPixels(it,0,16,0,0,16,16); if(thumb!==image) thumb.recycle(); image.recycle() }
        } finally { connection.disconnect() }
    }
}
