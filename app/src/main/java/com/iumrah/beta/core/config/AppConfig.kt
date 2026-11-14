package com.iumrah.beta.core.config

import java.net.URI
import java.util.Locale

/**
 * Match the current iOS AppConfig: the Worker is the sole API origin.
 * Public website and share links remain on iumrah.app.
 */
object AppConfig {
    const val APP_NAME = "iumrah Beta"
    const val API_BASE_URL = "https://iumrah-backend.helloiumrah.workers.dev"
    const val PUBLIC_WEB_BASE_URL = "https://iumrah.app"

    const val BACKEND_SERVICE_NAME = "iumrah-backend"
    const val INTELLIGENCE_EVENTS_PATH = "/api/catalog/hotels/intelligence/events"
    const val INTELLIGENCE_EVENTS_URL = API_BASE_URL + INTELLIGENCE_EVENTS_PATH
    const val PACKAGE_HEALTH_PATH = "/api/package/health"
    const val HEALTH_URL = API_BASE_URL + PACKAGE_HEALTH_PATH
    const val PACKAGE_BOOKING_PATH = "/api/bookings"

    const val FLIGHT_INVENTORY_CONFIGURED = true
    const val USES_SERVER_PRIMARY_HOTEL_RESOLVER = true
    const val USES_LOCAL_PRIMARY_HOTEL_FALLBACK = true

    const val HOTEL_PRICE_PROVIDER_TIMEOUT_SECONDS = 17L
    const val HOTEL_PRICE_SEARCH_HARD_TIMEOUT_SECONDS = 46L

    private val firstPartyHosts = setOf(
        "iumrah.app", "www.iumrah.app", "iumrah.uz", "www.iumrah.uz",
        "iumrah.us", "www.iumrah.us", "iumrahproject.com", "www.iumrahproject.com",
        "iumrah-package-api.helloiumrah.workers.dev", "iumrah-flight-hub.helloiumrah.workers.dev",
        "iumrah-backend.helloiumrah.workers.dev",
    )

    /** First-party API and private-media URLs only; never forward credentials to another host. */
    fun apiUrl(rawValue: String): String {
        val value = rawValue.trim()
        require(value.isNotEmpty() && !value.startsWith("//") && !value.contains('\\')) { "Invalid API path" }
        val uri = URI(value)
        require(uri.rawFragment == null && uri.rawUserInfo == null) { "Invalid API URL" }
        if (uri.isAbsolute) {
            require(uri.scheme.equals("https", ignoreCase = true) &&
                uri.host?.lowercase(Locale.ROOT) in firstPartyHosts &&
                (uri.port == -1 || uri.port == 443)) { "Unexpected API origin" }
        } else {
            require(uri.rawAuthority == null) { "Unexpected API authority" }
        }
        val path = "/" + uri.rawPath.orEmpty().trimStart('/')
        require(path.startsWith("/api/")) { "Unexpected API path" }
        require(uri.path.orEmpty().split('/').none { it == "." || it == ".." } &&
            !uri.path.orEmpty().contains('\\')) { "Invalid API path" }
        return API_BASE_URL + path + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    /** Public images and external links may still use their original CDN. */
    fun absoluteUrl(rawValue: String?): String? {
        val value = rawValue?.trim().orEmpty()
        if (value.isEmpty()) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return value.takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
        if (uri.isAbsolute) {
            if (uri.scheme.equals("https", ignoreCase = true) &&
                uri.host?.lowercase(Locale.ROOT) in firstPartyHosts &&
                uri.rawPath.orEmpty().startsWith("/api/")) {
                return runCatching { apiUrl(value) }.getOrNull()
            }
            return value.takeIf { uri.scheme.equals("https", true) || uri.scheme.equals("http", true) }
        }
        if (uri.rawAuthority != null || value.startsWith("//")) return null
        return if (value.trimStart('/').startsWith("api/")) runCatching { apiUrl(value) }.getOrNull()
        else PUBLIC_WEB_BASE_URL + "/" + value.trimStart('/')
    }
}
