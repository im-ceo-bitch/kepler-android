package com.iumrah.beta.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedBackendConfigTest {
    @Test fun allFirstPartyOriginsResolveToUnifiedWorker() {
        val route = "/api/catalog/hotels/client/trips/1234?include=documents%2Cpayments"
        for (host in listOf("iumrah.app", "www.iumrah.app", "iumrah.uz", "iumrah.us", "iumrahproject.com", "iumrah-package-api.helloiumrah.workers.dev", "iumrah-flight-hub.helloiumrah.workers.dev", "iumrah-backend.helloiumrah.workers.dev")) {
            assertEquals(AppConfig.API_BASE_URL + route, AppConfig.apiUrl("https://$host$route"))
        }
        assertEquals(AppConfig.API_BASE_URL + route, AppConfig.apiUrl(route))
    }

    @Test fun externalOriginsAndUnsafePathsAreRejected() {
        for (url in listOf("https://example.org/api/bookings", "https://iumrah.app.evil.org/api/bookings", "https://evil.org@iumrah.app/api/bookings", "http://iumrah.app/api/bookings", "//evil.org/api/bookings", "/api/%2e%2e/home")) {
            assertTrue(url, runCatching { AppConfig.apiUrl(url) }.isFailure)
        }
    }

    @Test fun analyticsAndHealthUseIdenticalOrigin() {
        assertEquals(AppConfig.API_BASE_URL + AppConfig.PACKAGE_HEALTH_PATH, AppConfig.HEALTH_URL)
        assertEquals(AppConfig.API_BASE_URL + AppConfig.INTELLIGENCE_EVENTS_PATH, AppConfig.INTELLIGENCE_EVENTS_URL)
    }

    @Test fun publicMediaDoesNotGetRewrittenToTheApi() {
        val cdn = "https://example.org/public/photo.jpg"
        assertEquals(cdn, AppConfig.absoluteUrl(cdn))
    }
}
