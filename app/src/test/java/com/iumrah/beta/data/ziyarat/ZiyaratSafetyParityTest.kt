package com.iumrah.beta.data.ziyarat

import com.iumrah.beta.models.ziyarat.ZiyaratImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZiyaratSafetyParityTest {
    @Test fun `iOS offline catalogue has six Makkah and five Madinah places`() {
        val makkah = ZiyaratFallbackCatalog.route("Makkah")
        val madinah = ZiyaratFallbackCatalog.route("Madinah")
        assertEquals(6, makkah.places.size)
        assertEquals(5, madinah.places.size)
        for (route in listOf(makkah, madinah)) {
            assertEquals("offline", route.status)
            assertEquals(route.places.size, route.places.map { it.id }.distinct().size)
            assertEquals(route.places.size, route.stopCount)
            assertTrue(route.places.all { ZiyaratService.validCoordinate(it.latitude, it.longitude) })
            assertTrue(route.places.all { it.translations?.keys?.containsAll(listOf("en", "ru", "uz", "uz-cyrl")) == true })
        }
    }

    @Test fun `remote catalogue rejects mixed city invalid coordinates and duplicate ids`() {
        val original = ZiyaratFallbackCatalog.route("Makkah")
        val base = original.places.first().copy(status = "published")
        val remote = original.copy(status = "published", places = listOf(
            base,
            base.copy(title = "duplicate"),
            base.copy(id = "bad-coordinate", latitude = Double.NaN),
            base.copy(id = "wrong-city", city = "Madinah"),
            base.copy(id = "outside-ksa", longitude = 120.0),
            base.copy(
                id = "safe", images = listOf(
                    ZiyaratImage("bad-file", "file:///etc/passwd"),
                    ZiyaratImage("bad-data", "data:image/png;base64,AAA"),
                    ZiyaratImage("good-https", "https://iumrah.app/ziyarat.jpg"),
                ),
            ),
        ))
        val clean = ZiyaratService.sanitize(remote, "Makkah")
        assertEquals(2, clean.places.size)
        assertEquals(2, clean.stopCount)
        assertEquals(1, clean.places.first { it.id == "safe" }.images.size)
        assertEquals("https://iumrah.app/ziyarat.jpg", clean.places.first { it.id == "safe" }.images.single().url)
    }

    @Test fun `sanitizer never uses catalogue from opposite city`() {
        val wrong = ZiyaratService.sanitize(ZiyaratFallbackCatalog.route("Makkah"), "Madinah")
        assertEquals("Madinah", wrong.city)
        assertEquals("offline", wrong.status)
        assertEquals(5, wrong.places.size)
    }

    @Test fun `non saudi coordinates and not a number cannot reach maps`() {
        assertFalse(ZiyaratService.validCoordinate(Double.POSITIVE_INFINITY, 39.8))
        assertFalse(ZiyaratService.validCoordinate(21.4, Double.NaN))
        assertFalse(ZiyaratService.validCoordinate(0.0, 0.0))
        assertTrue(ZiyaratService.validCoordinate(24.4679, 39.6161))
    }
}
