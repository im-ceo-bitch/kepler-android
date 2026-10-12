package com.iumrah.beta.data.ziyarat

import com.iumrah.beta.models.ziyarat.ZiyaratImage
import com.iumrah.beta.models.ziyarat.ZiyaratPlace
import com.iumrah.beta.models.ziyarat.ZiyaratPlaceTranslation
import com.iumrah.beta.models.ziyarat.ZiyaratRoute

/**
 * Direct port of the iOS ZiyaratFallbackCatalog (2026-10-09).
 * Loaded synchronously; it is never dependent on a database or a network request.
 * The live iumrah Business catalogue still supersedes this when validated.
 */
object ZiyaratFallbackCatalog {
    fun normalized(city: String): String = when (city.trim().lowercase()) {
        "makkah", "mecca", "makka" -> "Makkah"
        else -> "Madinah"
    }

    private data class Site(
        val id: String, val english: String, val russian: String,
        val uzbek: String, val cyrillic: String,
        val latitude: Double, val longitude: Double, val category: String,
        val englishSummary: String, val russianSummary: String,
    )

    private val makkah = listOf(
        Site("haram", "Masjid al-Haram", "Масджид аль-Харам", "Masjidul Harom", "Масжидул Ҳаром", 21.42252, 39.82618, "mosque", "The Grand Mosque and the sacred Kaaba.", "Заповедная мечеть и священная Кааба."),
        Site("thawr", "Jabal Thawr", "Гора Саур", "Savr tog‘i", "Савр тоғи", 21.3771, 39.8356, "history", "Mountain associated with the Hijrah.", "Гора, связанная с историей хиджры."),
        Site("arafat", "Arafat", "Арафат", "Arafot", "Арафот", 21.3554, 39.9841, "history", "A principal site of the Hajj pilgrimage.", "Одно из главных мест паломничества во время хаджа."),
        Site("muzdalifah", "Muzdalifah", "Муздалифа", "Muzdalifa", "Муздалифа", 21.3842, 39.9291, "history", "The sacred area between Arafat and Mina.", "Священная местность между Арафатом и Миной."),
        Site("mina", "Mina", "Мина", "Mino", "Мино", 21.4134, 39.8943, "history", "The valley connected with the rites of Hajj.", "Долина, связанная с обрядами хаджа."),
        Site("jamarat", "Jamarat", "Джамарат", "Jamarot", "Жамарот", 21.4219, 39.8714, "history", "The stoning site visited during Hajj.", "Место обряда бросания камней во время хаджа."),
    )
    private val madinah = listOf(
        Site("quba", "Quba Mosque", "Мечеть Куба", "Qubo masjidi", "Қубо масжиди", 24.43917, 39.61722, "mosque", "The first mosque established in Islam.", "Первая мечеть, основанная в исламе."),
        Site("qiblatain", "Masjid al-Qiblatayn", "Мечеть двух кибл", "Qiblatayn masjidi", "Қиблатайн масжиди", 24.4831, 39.5786, "mosque", "The mosque associated with the change of qibla.", "Мечеть, связанная с изменением направления киблы."),
        Site("uhud", "Mount Uhud", "Гора Ухуд", "Uhud tog‘i", "Уҳуд тоғи", 24.5091, 39.6138, "history", "Historic mountain near Madinah.", "Историческая гора неподалёку от Медины."),
        Site("baqi", "Al-Baqi Cemetery", "Кладбище аль-Баки", "Baqi qabristoni", "Бақи қабристони", 24.4679, 39.6161, "history", "Historic cemetery near the Prophet’s Mosque.", "Историческое кладбище рядом с мечетью Пророка ﷺ."),
        Site("shajara", "Masjid Dhul Hulaifah", "Мечеть Шаджара", "Shajara masjidi", "Шажара масжиди", 24.4124, 39.5428, "mosque", "A miqat point for pilgrims departing Madinah.", "Микат для паломников, отправляющихся из Медины."),
    )

    fun route(city: String): ZiyaratRoute {
        val name = normalized(city)
        val sites = if (name == "Makkah") makkah else madinah
        val routeId = "${name.lowercase()}-offline"
        val places = sites.mapIndexed { index, site ->
            val translations = mapOf(
                "en" to translation(site.english, site.englishSummary),
                "ru" to translation(site.russian, site.russianSummary),
                "uz" to translation(site.uzbek, site.englishSummary),
                "uz-cyrl" to translation(site.cyrillic, site.englishSummary),
            )
            ZiyaratPlace(
                id = "offline-${name.lowercase()}-${site.id}", routeID = routeId,
                slug = site.id, city = name, country = "Saudi Arabia",
                title = site.english, category = site.category,
                shortDescription = site.englishSummary, longDescription = site.englishSummary,
                visitType = "outside", latitude = site.latitude, longitude = site.longitude,
                mapLabel = site.english, routeOrder = index + 1, status = "offline",
                images = if (site.id == "quba") listOf(ZiyaratImage("quba-offline", "asset:ZiyaratQuba1")) else emptyList(),
                translations = translations,
            )
        }
        return ZiyaratRoute(
            id = routeId, slug = "${name.lowercase()}-ziyarat", city = name,
            country = "Saudi Arabia", title = "$name Ziyarats",
            subtitle = "Places and history", status = "offline",
            stopCount = places.size, places = places,
        )
    }

    private fun translation(title: String, body: String) =
        ZiyaratPlaceTranslation(title = title, shortDescription = body, longDescription = body)
}
