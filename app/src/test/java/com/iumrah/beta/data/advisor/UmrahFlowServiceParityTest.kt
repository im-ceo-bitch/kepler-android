package com.iumrah.beta.data.advisor

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic offline tests of the public iOS UmrahFlowStore/Supabase parity contract. */
class UmrahFlowServiceParityTest {
    private fun service(
        responder: (String, String) -> String,
    ): UmrahFlowService = UmrahFlowService(client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val request = chain.request()
        val table = request.url.pathSegments.last()
        val lang = request.url.queryParameter("lang")?.removePrefix("eq.").orEmpty()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(responder(table, lang).toResponseBody()).build()
    }).build())

    @Test fun usesRequestedLanguageAndFiltersUnknownKeys() = runBlocking {
        val guide = service { table, language ->
            when {
                table == "audio" -> """[{"key":"tawaf_start","url":"https://cdn.example.com/guide.mp3"}]"""
                language == "ru" -> """[{"key":"start_text","value":"Начало умры"},{"key":"admin_secret","value":"must not leak"}]"""
                else -> "[]"
            }
        }.load("ru")
        assertEquals("Начало умры", guide.translations["start_text"])
        assertFalse(guide.translations.containsKey("admin_secret"))
        assertEquals("https://cdn.example.com/guide.mp3", guide.audio["tawaf_start"])
    }

    @Test fun fallsBackToEnglishOnlyWhenRequestedLanguageHasNoRows() = runBlocking {
        val guide = service { table, language ->
            when {
                table == "audio" -> "[]"
                language == "en" -> """[{"key":"start_text","value":"Begin Umrah"}]"""
                else -> "[]"
            }
        }.load("tr")
        assertEquals("Begin Umrah", guide.translations["start_text"])
    }

    @Test fun rejectsInsecureAudioAndUnsupportedLanguage() = runBlocking {
        val guide = service { table, _ ->
            if (table == "audio") """[{"key":"start","url":"http://unsafe.test/a.mp3"},{"key":"other","url":"https://cdn.example.com/a.mp3"}]"""
            else """[{"key":"start_text","value":"Begin"}]"""
        }.load("en")
        assertFalse(guide.audio.containsKey("start"))
        assertTrue(guide.audio.containsKey("other"))
        assertTrue(runCatching { service { _, _ -> "[]" }.load("<invalid>") }.isFailure)
    }
}
