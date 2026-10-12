package com.iumrah.beta.data.advisor

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Serializable
private data class TranslationRow(val key: String, val value: String)

@Serializable
private data class AudioRow(val key: String, val url: String)

data class UmrahFlowContent(
    val translations: Map<String, String> = emptyMap(),
    val audio: Map<String, String> = emptyMap(),
)

/** Mirrors the iOS UmrahFlowStore: cached guide text is immediately readable offline.
 * Per-language caches only contain the public translation and audio URL mappings.
 * Never cache booking, account or other private data here.
 */
class UmrahFlowService(
    context: Context? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = context?.applicationContext?.getSharedPreferences("iumrah.umrahFlow", Context.MODE_PRIVATE)
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    fun cached(language: String): UmrahFlowContent {
        if (language !in SUPPORTED_LANGUAGES) return UmrahFlowContent()
        fun read(type: String): Map<String, String> {
            val raw = cache?.getString("$type.$language", null) ?: return emptyMap()
            return try { json.decodeFromString(mapSerializer, raw) }
            catch (_: Exception) { emptyMap() }
        }
        return UmrahFlowContent(
            translations = read("translations").filterKeys { it in UmrahFlowKeys.all },
            audio = read("audio").filterValues(::validAudioUrl),
        )
    }

    suspend fun load(language: String): UmrahFlowContent = coroutineScope {
        require(language in SUPPORTED_LANGUAGES) { "Unsupported Advisor language" }
        val snapshot = cached(language)
        val freshText = async(Dispatchers.IO) { safeFetch { loadTranslations(language) } }
        val freshAudio = async(Dispatchers.IO) { safeFetch { loadAudio(language) } }
        val requestedText = freshText.await().orEmpty()
        val requestedAudio = freshAudio.await().orEmpty().filterValues(::validAudioUrl)

        var translations = requestedText.ifEmpty { snapshot.translations }
        val audio = requestedAudio.ifEmpty { snapshot.audio }
        // As on iOS, use English only if no text rows exist for the selected language.
        if (translations.isEmpty() && language != "en") {
            val englishSnapshot = cached("en").translations
            translations = englishSnapshot.ifEmpty {
                withContext(Dispatchers.IO) { safeFetch { loadTranslations("en") }.orEmpty() }
            }
        }
        if (requestedText.isNotEmpty()) cacheWrite("translations", language, requestedText)
        if (requestedAudio.isNotEmpty()) cacheWrite("audio", language, requestedAudio)
        UmrahFlowContent(translations = translations, audio = audio)
    }

    private inline fun <T> safeFetch(block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun cacheWrite(type: String, language: String, values: Map<String, String>) {
        if (values.isEmpty()) return
        cache?.edit()?.putString("$type.$language", json.encodeToString(mapSerializer, values))?.apply()
    }

    private fun validAudioUrl(url: String): Boolean =
        runCatching {
            val parsed = java.net.URI(url)
            parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank() &&
                parsed.userInfo == null && url.length <= 2048
        }.getOrDefault(false)

    private fun loadTranslations(language: String): Map<String, String> {
        for (table in listOf("translations2", "translations")) {
            val result = safeFetch {
                val body = request(table, "key,value", language)
                json.decodeFromString(ListSerializer(TranslationRow.serializer()), body)
                    .associate { it.key to it.value }
                    .filterKeys { it in UmrahFlowKeys.all }
            }.orEmpty()
            if (result.isNotEmpty()) return result
        }
        return emptyMap()
    }

    private fun loadAudio(language: String): Map<String, String> {
        val body = request("audio", "key,url", language)
        return json.decodeFromString(ListSerializer(AudioRow.serializer()), body)
            .associate { it.key to it.url }
    }

    private fun request(table: String, select: String, language: String): String {
        val encodedLang = URLEncoder.encode("eq.$language", StandardCharsets.UTF_8.toString())
        val url = "$BASE_URL/rest/v1/$table?select=$select&lang=$encodedLang"
        val request = Request.Builder()
            .url(url)
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer $ANON_KEY")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Umrah Flow HTTP ${response.code}")
            return response.body.string()
        }
    }

    private companion object {
        val SUPPORTED_LANGUAGES = setOf("ru", "en", "uz", "kk", "id", "tr", "ar", "ms", "bn", "fr")
        const val BASE_URL = "https://coaqrsapnpyutsxflsru.supabase.co"
        const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImNvYXFyc2FwbnB5dXRzeGZsc3J1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjQzNjE2OTcsImV4cCI6MjA3OTkzNzY5N30.iycnHay3nX__40VTKzvkyX3NKbSo8wWqBhKGKGl2yIo"
    }
}

object UmrahFlowKeys {
    val all: Set<String> = buildSet {
        addAll(
            listOf(
                "complete_btn", "continue_btn", "tap_btn", "sunna_dua_btn",
                "start_text", "start_text1", "start_text2", "start_text3",
                "end_text", "end_text1", "end_text3",
                "home1_title", "home1_sub", "home1_btn", "home1_btn3_sub",
                "home2_3title", "home2_3_subtitle", "home_3_btn", "home_3_btn_sub",
                "home_3_btn2", "home_3_btn2_sub", "home_3_btn3",
                "tawaf_title", "tawaf_break_title", "tawafpray_title1", "tawafpray_text1",
                "tawaf_common_text1", "tawaf_common_text2", "tawaf_common_text3", "tawaf_common_text4",
                "zamzam_title", "zamzam_title1", "zamzam_text",
                "safago_title", "safago_title1", "safago_text", "safadua_title",
                "overlay_safa_title", "overlay_safa_text1",
                "umrah_start_title", "sai_title", "tahallul_title",
                "navigate_button", "close_button", "cancel_omra"
            )
        )
        for (round in 1..7) {
            add("tawaf${round}_text1")
            add("tawaf${round}_text2")
            add("tawaf${round}_tarab1")
            add("tawaf${round}_zikr_text")
            add("tawaf${round}_zikr_repeat")
            for (index in 1..3) add("tawaf${round}_reading_arab$index")
            for (index in 1..6) add("tawaf${round}_reading_text$index")
            add("safa${round}_title1")
            add("safa${round}_text1")
            add("safa${round}_text2")
            add("safa${round}_sarab1")
        }
    }
}
