package com.iumrah.beta.data.ziyarat

import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.models.ziyarat.ZiyaratCatalogResponse
import com.iumrah.beta.models.ziyarat.ZiyaratPlace
import com.iumrah.beta.models.ziyarat.ZiyaratPlaceTranslation
import com.iumrah.beta.models.ziyarat.ZiyaratRoute
import kotlinx.coroutines.CancellationException

/** Mirrors iOS ZiyaratService 2026-10-09: no persisted catalogue, no eager images. */
class ZiyaratService(private val api: APIClient = APIClient()) {
    suspend fun route(city: String = "Madinah"): ZiyaratRoute {
        val name = ZiyaratFallbackCatalog.normalized(city)
        val remote = try {
            api.get<ZiyaratCatalogResponse>(
                "/api/catalog/ziyarats", query = mapOf("city" to name), timeoutSeconds = 10L,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled // Switching cities must cancel obsolete requests.
        } catch (_: Exception) {
            null // An offline API must not block the bundled catalogue.
        }
        if (remote?.ok != true) return ZiyaratFallbackCatalog.route(name)
        return remote.route?.let { sanitize(it, name) }
            ?.takeIf { it.places.isNotEmpty() } ?: ZiyaratFallbackCatalog.route(name)
    }

    companion object {
        /** No fabricated coordinates, duplicate places, stale city entries or unsafe photo URLs. */
        internal fun sanitize(route: ZiyaratRoute, requestedCity: String): ZiyaratRoute {
            val name = ZiyaratFallbackCatalog.normalized(requestedCity)
            if (ZiyaratFallbackCatalog.normalized(route.city) != name) {
                return ZiyaratFallbackCatalog.route(name)
            }
            val seen = mutableSetOf<String>()
            val places = route.places.take(120).mapNotNull { place ->
                val id = place.id.trim()
                if (id.isEmpty() || id.length > 120 ||
                    !validCoordinate(place.latitude, place.longitude) ||
                    ZiyaratFallbackCatalog.normalized(place.city) != name || !seen.add(id)
                ) return@mapNotNull null
                place.copy(
                    id = id, city = name,
                    title = place.title.take(180), titleArabic = place.titleArabic.take(180),
                    category = place.category.take(64),
                    shortDescription = place.shortDescription.take(800),
                    longDescription = place.longDescription.take(8000),
                    interestingFacts = place.interestingFacts.take(12).map { it.take(1000) },
                    visitNotes = place.visitNotes.take(2000),
                    durationMinutes = place.durationMinutes.coerceIn(0, 360),
                    address = place.address.take(600), mapLabel = place.mapLabel.take(180),
                    images = place.images.take(8).filter { photo ->
                        val safeSize = photo.byteSize == null || photo.byteSize in 0..(8 * 1024 * 1024)
                        safeSize && ((photo.url.startsWith("asset:") && photo.url.length < 100) ||
                            photo.url.startsWith("https://", ignoreCase = true))
                    },
                    translations = sanitizeTranslations(place.translations),
                )
            }.sortedWith(compareBy<ZiyaratPlace> { it.routeOrder }.thenBy { it.id })
            return route.copy(
                city = name, estimatedMinutes = route.estimatedMinutes.coerceIn(0, 1440),
                stopCount = places.size, places = places,
            )
        }

        fun validCoordinate(latitude: Double, longitude: Double) =
            latitude.isFinite() && longitude.isFinite() &&
                latitude in 18.0..33.0 && longitude in 34.0..56.0

        private fun sanitizeTranslations(source: Map<String, ZiyaratPlaceTranslation>?): Map<String, ZiyaratPlaceTranslation>? =
            source?.filterKeys { it in setOf("ru", "en", "uz", "uz-cyrl") }
                ?.mapValues { (_, t) ->
                    t.copy(
                        title = t.title.take(180), shortDescription = t.shortDescription.take(800),
                        longDescription = t.longDescription.take(8000),
                        interestingFacts = t.interestingFacts.take(12).map { it.take(1000) },
                        visitNotes = t.visitNotes.take(2000),
                    )
                }
    }
}
