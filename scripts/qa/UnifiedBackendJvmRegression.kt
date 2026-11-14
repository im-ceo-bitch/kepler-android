package com.iumrah.beta.core.config

object UnifiedBackendJvmRegression {
    @JvmStatic fun main(args: Array<String>) {
        val base = "https://iumrah-backend.helloiumrah.workers.dev"
        val routes = listOf(
            "/api/package/health", "/api/package/flights/calendar", "/api/package/flights/search",
            "/api/package/nuitee/hotels", "/api/package/nuitee/rates", "/api/package/quote",
            "/api/bookings", "/api/bookings/1234", "/api/catalog/hotels/client/trips/1234",
            "/api/package/client/account/login", "/api/catalog/hotels/client/account/session",
            "/api/catalog/hotels/client/chats/1234/attachments", AppConfig.INTELLIGENCE_EVENTS_PATH,
        )
        var checked = 0
        for (route in routes) {
            check(AppConfig.apiUrl(route) == base + route); checked++
            check(AppConfig.apiUrl(route.drop(1)) == base + route); checked++
        }
        for (host in listOf("iumrah.app", "www.iumrah.app", "iumrah.uz", "iumrah.us", "iumrahproject.com", "iumrah-package-api.helloiumrah.workers.dev", "iumrah-flight-hub.helloiumrah.workers.dev", "iumrah-backend.helloiumrah.workers.dev")) {
            check(AppConfig.apiUrl("https://$host/api/bookings/1234?x=a%2Fb&y=1") == "$base/api/bookings/1234?x=a%2Fb&y=1"); checked++
        }
        for (bad in listOf("https://example.org/api/bookings", "https://iumrah.app.evil.org/api/bookings", "https://iumrah.app@evil.org/api/bookings", "https://evil.org@iumrah.app/api/bookings", "http://iumrah.app/api/bookings", "//evil.org/api/bookings", "https://iumrah.app:9999/api/bookings", "/api/../home", "/api/%2e%2e/home", "/api/bookings#fragment", "/api/%5cfoo", "/home", "", "https://coaqrsapnpyutsxflsru.supabase.co/rest/v1/audio")) {
            check(runCatching { AppConfig.apiUrl(bad) }.isFailure) { "Unexpected acceptance: $bad" }; checked++
        }
        check(AppConfig.HEALTH_URL == "$base/api/package/health"); checked++
        check(AppConfig.INTELLIGENCE_EVENTS_URL == "$base/api/catalog/hotels/intelligence/events"); checked++
        check(AppConfig.absoluteUrl("https://cdn.example.org/photo.jpg") == "https://cdn.example.org/photo.jpg"); checked++
        check(AppConfig.absoluteUrl("/api/catalog/hotels/photo.jpg") == "$base/api/catalog/hotels/photo.jpg"); checked++
        check(AppConfig.absoluteUrl("https://iumrah.uz/api/catalog/hotels/photo.jpg") == "$base/api/catalog/hotels/photo.jpg"); checked++
        println("Unified backend URL policy: $checked checks passed")
    }
}
