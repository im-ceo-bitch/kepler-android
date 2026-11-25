package com.iumrah.beta.data.advisor

import android.content.Context
import com.iumrah.beta.core.network.APIClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.iumrah.beta.domain.umrahflow.AdvisorContentPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.iumrah.beta.domain.umrahflow.UmrahFlowAudioUrlPolicy

data class UmrahFlowContent(
    val translations: Map<String, String> = emptyMap(),
    val audio: Map<String, String> = emptyMap(),
    val selectedTranslations: Map<String, String> = translations,
)

/** Reviewed bundled content plus the unified Advisor API. No legacy database or media host. */
@Serializable private data class TranslationRow(val key: String, val value: String)
@Serializable private data class AudioRow(val key: String, val url: String)
class UmrahFlowService(context: Context? = null, private val api: APIClient = APIClient()) {
    private val json = Json { ignoreUnknownKeys = true }
    private val applicationContext = context?.applicationContext
    private val cache = applicationContext?.getSharedPreferences("iumrah.umrahFlow.unified.v3", Context.MODE_PRIVATE)
    private val offlineCatalog by lazy {
        runCatching {
            applicationContext?.assets?.open("umrah_flow_catalog.json")?.bufferedReader()?.use {
                json.parseToJsonElement(it.readText()).jsonObject
            }
        }.getOrNull()
    }

    private fun bundled(type: String, language: String): Map<String, String> = runCatching {
        offlineCatalog?.get(type)?.jsonObject?.get(language)?.jsonObject
            ?.mapValues { (_, item) -> item.jsonPrimitive.content } ?: emptyMap()
    }.getOrDefault(emptyMap())
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    fun cached(language: String): UmrahFlowContent {
        if (language !in SUPPORTED_LANGUAGES) return UmrahFlowContent()
        fun read(type: String): Map<String, String> {
            val raw = cache?.getString("$type.$language", null) ?: return emptyMap()
            return try { json.decodeFromString(mapSerializer, raw) }
            catch (_: Exception) { emptyMap() }
        }
        // v3 intentionally excludes v2 entries that may contain mislabeled English fallback.
        return UmrahFlowContent(
            translations = AdvisorContentPolicy.merge(bundled("translations", language), read("translations")).filterKeys { it in UmrahFlowKeys.all },
            audio = (read("audio") + bundled("audio", language)).mapNotNull { (key, value) -> UmrahFlowAudioUrlPolicy.resolve(value)?.let { key to it } }.toMap().filterValues(UmrahFlowAudioUrlPolicy::isAllowed),
        )
    }

    suspend fun load(language: String): UmrahFlowContent {
        require(language in SUPPORTED_LANGUAGES) { "Unsupported Advisor language" }
        val snapshot = cached(language)
        suspend fun <T> attempt(block: suspend () -> T): T? = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        val (translations, audio) = coroutineScope {
            val texts = async { attempt {
                api.get<List<TranslationRow>>("/api/services/advisor/translations", query = mapOf("lang" to language), timeoutSeconds = 12)
                    .filter { it.key in UmrahFlowKeys.all && it.value.isNotBlank() }.associate { it.key to it.value }
            }.orEmpty() }
            val recordings = async { attempt {
                api.get<List<AudioRow>>("/api/services/advisor/audio", query = mapOf("lang" to language), timeoutSeconds = 12)
                    .mapNotNull { row -> UmrahFlowAudioUrlPolicy.resolve(row.url)?.let { row.key to it } }.toMap()
            }.orEmpty() }
            texts.await() to recordings.await()
        }
        currentCoroutineContext().ensureActive()
        val selected = AdvisorContentPolicy.merge(snapshot.translations, emptyMap(), translations)
        val recordings = snapshot.audio + audio
        cache?.edit()?.putString("translations.$language", json.encodeToString(mapSerializer, selected))
            ?.putString("audio.$language", json.encodeToString(mapSerializer, recordings))?.apply()
        return UmrahFlowContent(AdvisorContentPolicy.display(selected, bundled("translations", "en")), recordings, selected)
    }

    private companion object {
        val SUPPORTED_LANGUAGES = setOf("ru", "en", "uz", "kk", "id", "tr", "ar", "ms", "bn", "fr")
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
