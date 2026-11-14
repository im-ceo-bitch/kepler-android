package com.iumrah.beta.data.advisor

import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.network.APIClient
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Offline HTTP contract tests for the unified Advisor API. No live network or Android Context. */
class UmrahFlowServiceParityTest {
    private data class Reply(val body: String, val code: Int = 200)
    private class Fixture(responder: (Request) -> Reply) {
        val requests = CopyOnWriteArrayList<Request>()
        private val transport = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val request = chain.request()
            requests.add(request)
            val reply = responder(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(reply.code).message("Test response").body(reply.body.toResponseBody()).build()
        }).build()
        val service = UmrahFlowService(api = APIClient(client = transport))
    }

    @Test fun usesRequestedLanguageAndFiltersUnknownKeys() = runBlocking {
        val fixture = Fixture { request ->
            when (request.url.encodedPath) {
                "/api/services/advisor/translations" -> Reply("""[{"key":"start_text","value":"Начало умры"},{"key":"admin_secret","value":"must not leak"}]""")
                "/api/services/advisor/audio" -> Reply("""[{"key":"tawaf_start","url":"/api/services/advisor/audio/ru/tawaf_start"}]""")
                else -> Reply("[]", 404)
            }
        }
        val guide = fixture.service.load("ru")
        assertEquals("Начало умры", guide.translations["start_text"])
        assertFalse(guide.translations.containsKey("admin_secret"))
        assertEquals(AppConfig.API_BASE_URL + "/api/services/advisor/audio/ru/tawaf_start", guide.audio["tawaf_start"])
        assertEquals(listOf("/api/services/advisor/translations", "/api/services/advisor/audio"), fixture.requests.map { it.url.encodedPath })
        assertTrue(fixture.requests.all { it.method == "GET" && it.url.scheme == "https" && it.url.host == "iumrah-backend.helloiumrah.workers.dev" && it.url.queryParameter("lang") == "ru" && it.header("apikey") == null })
    }

    @Test fun emptyRequestedLanguageWithoutOfflineCatalogDoesNotInventTranslations() = runBlocking {
        val fixture = Fixture { request ->
            if (request.url.encodedPath.endsWith("/translations") && request.url.queryParameter("lang") == "en")
                Reply("""[{"key":"start_text","value":"Begin Umrah"}]""")
            else Reply("[]")
        }
        // A context-free service has no bundled translations or persistent cache.
        // English fallback is local; a missing Turkish response must not trigger an extra network request.
        val guide = fixture.service.load("tr")
        assertTrue(guide.translations.isEmpty())
        assertEquals(listOf("tr", "tr"), fixture.requests.map { it.url.queryParameter("lang") })
        assertEquals("Begin Umrah", fixture.service.load("en").translations["start_text"])
    }

    @Test fun rejectsLegacyExternalInsecureAudioAndUnsupportedLanguage() = runBlocking {
        val fixture = Fixture { request ->
            if (request.url.encodedPath.endsWith("/audio")) Reply("""[
                {"key":"insecure","url":"http://iumrah-backend.helloiumrah.workers.dev/api/services/advisor/audio/en/start"},
                {"key":"external","url":"https://cdn.example.com/guide.mp3"},
                {"key":"legacy","url":"https://example.supabase.co/storage/v1/object/public/audio/start.mp3"},
                {"key":"wrong_path","url":"/api/bookings/1234"},
                {"key":"other","url":"/api/services/advisor/audio/en/start"}
            ]""") else Reply("""[{"key":"start_text","value":"Begin"}]""")
        }
        val guide = fixture.service.load("en")
        assertEquals(mapOf("other" to AppConfig.API_BASE_URL + "/api/services/advisor/audio/en/start"), guide.audio)
        val count = fixture.requests.size
        assertTrue(runCatching { fixture.service.load("<invalid>") }.isFailure)
        assertEquals(count, fixture.requests.size)
    }

    @Test fun translationsFailureDoesNotDiscardValidAudio() = runBlocking {
        val fixture = Fixture { request ->
            if (request.url.encodedPath.endsWith("/translations")) Reply("{}", 503)
            else Reply("""[{"key":"start","url":"/api/services/advisor/audio/en/start"}]""")
        }
        val guide = fixture.service.load("en")
        assertTrue(guide.translations.isEmpty())
        assertEquals(AppConfig.API_BASE_URL + "/api/services/advisor/audio/en/start", guide.audio["start"])
        assertEquals(2, fixture.requests.size)
    }
}
