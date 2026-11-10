package com.iumrah.beta.core.analytics

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID

/** Android mirror of iOS IumrahIntelligence. Only fixed event codes; no personal content. */
object IumrahIntelligence {
    const val ENDPOINT = "https://iumrah-backend.helloiumrah.workers.dev/api/catalog/hotels/intelligence/events"
    private const val PREFS = "iumrah_intelligence_v1"
    private const val MAX_AGE_MS = 6L * 86_400_000L
    private val mutex = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var app: Context? = null
    private var enabled = true
    private var visitor: String? = null
    private var session = uid()
    private var pending = mutableListOf<JSONObject>()
    private var started = false
    private var backgrounded = false
    private var activeScreen: String? = null
    private var activeSince: Long? = null
    private val details = mutableMapOf<String, Long>()
    private var scheduled = false
    private var flushing = false
    private var privacyEpoch = 0L

    private fun uid() = UUID.randomUUID().toString().lowercase()
    fun code(value: String): String = value.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(64)
    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(): Boolean = synchronized(mutex) { enabled }

    fun start(context: Context, screen: String, onboardingCompleted: Boolean) {
        synchronized(mutex) {
            if (app == null) {
                app = context.applicationContext
                enabled = prefs()?.getBoolean("enabled", true) ?: true
                visitor = prefs()?.getString("visitor", null)
                val raw = prefs()?.getString("pending", "[]") ?: "[]"
                pending = runCatching {
                    val arr = JSONArray(raw)
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.toMutableList()
                }.getOrDefault(mutableListOf())
                trimLocked()
            }
            if (started || !enabled) return
            val firstInstall = visitor == null
            if (firstInstall) { visitor = uid(); prefs()?.edit()?.putString("visitor", visitor)?.apply() }
            started = true
            if (firstInstall && !onboardingCompleted) addLocked("app_first_open")
            addLocked("app_open")
            openScreenLocked(screen)
        }
    }
    fun record(name: String, screen: String = "", element: String = "") {
        synchronized(mutex) { if (started && enabled && !backgrounded) addLocked(name, screen.ifEmpty { activeScreen.orEmpty() }, element) }
    }
    fun screenChanged(screen: String) {
        synchronized(mutex) {
            if (!started || !enabled || backgrounded || code(screen) == activeScreen) return
            closeScreenLocked(); openScreenLocked(screen)
        }
    }
    fun detailAppeared(raw: String) {
        synchronized(mutex) {
            if (!started || !enabled || backgrounded) return
            val screen = code(raw)
            if (details.putIfAbsent(screen, System.currentTimeMillis()) == null) addLocked("screen_view", screen)
        }
    }
    fun detailDisappeared(raw: String) {
        synchronized(mutex) {
            if (!started || !enabled) return
            val screen = code(raw)
            val since = details.remove(screen) ?: return
            addLocked("screen_exit", screen, durationMs = duration(since))
        }
    }
    fun enterBackground() {
        synchronized(mutex) {
            if (!started || !enabled || backgrounded) return
            closeScreenLocked()
            details.keys.toList().forEach { detailDisappeared(it) }
            addLocked("app_background")
            backgrounded = true
        }
        scope.launch { flush() }
    }
    fun resume(screen: String) {
        synchronized(mutex) {
            if (!started || !enabled || !backgrounded) return
            backgrounded = false; session = uid()
            addLocked("app_open"); openScreenLocked(screen)
        }
    }
    fun setEnabled(value: Boolean) {
        synchronized(mutex) {
            if (enabled == value) return
            enabled = value; privacyEpoch++
            prefs()?.edit()?.putBoolean("enabled", value)?.apply()
            if (!value) {
                pending.clear(); visitor = null; started = false; backgrounded = false
                activeScreen = null; activeSince = null; details.clear()
                prefs()?.edit()?.remove("visitor")?.remove("pending")?.apply()
            } else {
                visitor = uid(); session = uid(); started = true; backgrounded = false
                prefs()?.edit()?.putString("visitor", visitor)?.apply()
                addLocked("app_open"); openScreenLocked("account")
            }
        }
    }
    private fun duration(start: Long) = (System.currentTimeMillis() - start).coerceIn(0L, 3_600_000L).toInt()
    private fun openScreenLocked(raw: String) {
        activeScreen = code(raw); activeSince = System.currentTimeMillis()
        addLocked("screen_view", activeScreen.orEmpty())
    }
    private fun closeScreenLocked() {
        val screen = activeScreen ?: return
        val began = activeSince ?: return
        addLocked("screen_exit", screen, durationMs = duration(began))
        activeScreen = null; activeSince = null
    }
    private fun trimLocked() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        pending = pending.filter { it.optLong("createdMs") >= cutoff }.takeLast(150).toMutableList()
        prefs()?.edit()?.putString("pending", JSONArray(pending).toString())?.apply()
    }
    private fun addLocked(name: String, screen: String = "", element: String = "", durationMs: Int? = null) {
        val at = System.currentTimeMillis()
        val event = JSONObject().put("id", uid()).put("name", code(name))
            .put("screen", code(screen)).put("elementId", code(element))
            .put("occurredAt", Instant.ofEpochMilli(at).toString())
        if (durationMs != null) event.put("durationMs", durationMs)
        pending.add(JSONObject().put("sessionId", session).put("createdMs", at).put("event", event))
        trimLocked(); scheduleLocked()
    }
    private fun scheduleLocked() {
        if (scheduled || !enabled || pending.isEmpty()) return
        scheduled = true
        scope.launch { delay(3_000); flush(); synchronized(mutex) { scheduled = false } }
    }
    suspend fun flush() {
        val snapshot = synchronized(mutex) {
            if (!enabled || flushing || visitor == null || pending.isEmpty()) return
            flushing = true
            val firstSession = pending.first().getString("sessionId")
            val batch = pending.takeWhile { it.optString("sessionId") == firstSession }.take(20)
            val version = runCatching { val c = requireNotNull(app); c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() }.getOrDefault("")
            val request = JSONObject().put("visitorId", visitor).put("sessionId", firstSession)
                .put("platform", "android").put("appVersion", code(version))
                .put("events", JSONArray(batch.map { it.getJSONObject("event") }))
            Triple(request.toString(), batch.map { it.getJSONObject("event").getString("id") }.toSet(), privacyEpoch)
        }
        var accepted = false
        try {
            val payload = snapshot.first.toByteArray(Charsets.UTF_8)
            if (payload.size <= 16_384) {
                val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.connectTimeout = 12_000; connection.readTimeout = 18_000
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.outputStream.use { it.write(payload) }
                    accepted = connection.responseCode == 202
                } finally { connection.disconnect() }
            }
        } catch (_: Exception) { /* Network failures never block the UI. */ }
        synchronized(mutex) {
            if (accepted && enabled && privacyEpoch == snapshot.third) {
                pending.removeAll { it.optJSONObject("event")?.optString("id") in snapshot.second }
                trimLocked()
            }
            flushing = false
            // Only advance on success; rejected batches are retried on later app activity.
            if (accepted && enabled && pending.isNotEmpty()) scope.launch { delay(3_000); flush() }
        }
    }
}
