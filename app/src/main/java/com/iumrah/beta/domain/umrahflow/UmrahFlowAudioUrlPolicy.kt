package com.iumrah.beta.domain.umrahflow

import com.iumrah.beta.core.config.AppConfig
import java.net.URI

/** Only the unified Worker may serve Advisor recordings. Legacy cached URLs are rejected. */
object UmrahFlowAudioUrlPolicy {
    fun isAllowed(raw: String): Boolean = try {
        val uri = URI(raw)
        raw.length <= 2048 && uri.scheme == "https" &&
            uri.host == URI(AppConfig.API_BASE_URL).host &&
            (uri.port == -1 || uri.port == 443) && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null &&
            uri.path.startsWith("/api/services/advisor/audio/") &&
            uri.path.split('/').none { it == "." || it == ".." } && !uri.path.contains('\\')
    } catch (_: Exception) { false }

    fun resolve(raw: String): String? = runCatching { AppConfig.apiUrl(raw) }.getOrNull()?.takeIf(::isAllowed)
}
