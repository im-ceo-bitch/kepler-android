package com.iumrah.beta.models.ziyarat

import com.iumrah.beta.core.settings.AppLanguage
import kotlinx.serialization.Serializable

@Serializable
data class ZiyaratImage(
    val id: String,
    val url: String,
    val position: Int = 0,
    val byteSize: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ZiyaratPlaceTranslation(
    val title: String,
    val shortDescription: String,
    val longDescription: String,
    val interestingFacts: List<String> = emptyList(),
    val visitNotes: String = "",
)

@Serializable
data class ZiyaratPlace(
    val id: String,
    val routeID: String,
    val slug: String,
    val city: String,
    val country: String,
    val title: String,
    val titleArabic: String = "",
    val category: String = "place",
    val shortDescription: String = "",
    val longDescription: String = "",
    val interestingFacts: List<String> = emptyList(),
    val visitNotes: String = "",
    val visitType: String = "stop",
    val durationMinutes: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val mapLabel: String = "",
    val routeOrder: Int = 0,
    val status: String = "published",
    val images: List<ZiyaratImage> = emptyList(),
    val translations: Map<String, ZiyaratPlaceTranslation>? = null,
) {
    fun localized(language: AppLanguage): ZiyaratPlaceTranslation {
        val locale = when (language) {
            AppLanguage.RUSSIAN -> "ru"
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "en"
            AppLanguage.UZBEK -> "uz"
            AppLanguage.UZBEK_CYRILLIC -> "uz-cyrl"
        }
        return translations?.get(locale)?.takeIf { it.title.isNotBlank() }
            ?: translations?.get("en")?.takeIf { it.title.isNotBlank() }
            ?: translations?.get("ru")?.takeIf { it.title.isNotBlank() }
            ?: translations?.get("uz")?.takeIf { it.title.isNotBlank() }
            ?: translations?.get("uz-cyrl")?.takeIf { it.title.isNotBlank() }
            ?: ZiyaratPlaceTranslation(title, shortDescription, longDescription, interestingFacts, visitNotes)
    }
}

@Serializable
data class ZiyaratRoute(
    val id: String,
    val slug: String,
    val city: String,
    val country: String,
    val title: String,
    val subtitle: String = "",
    val transportMode: String = "car",
    val status: String = "published",
    val estimatedMinutes: Int = 0,
    val stopCount: Int = 0,
    val places: List<ZiyaratPlace> = emptyList(),
)

@Serializable
data class ZiyaratCatalogResponse(val ok: Boolean, val route: ZiyaratRoute? = null)

object ZiyaratSeedData {
    private val translations = mapOf(
        "ru" to ZiyaratPlaceTranslation(
            title = "Мечеть Куба",
            shortDescription = "Первая мечеть, основанная в исламе, и одна из важнейших точек зиярата в Медине.",
            longDescription = "Мечеть Куба тесно связана с хиджрой и ранней мусульманской общиной Медины. Современная мечеть находится на историческом месте и остаётся одной из самых посещаемых точек города.",
            interestingFacts = listOf(
                "Мечеть связана с началом периода жизни Пророка ﷺ в Медине.",
                "Она традиционно считается первой мечетью, основанной в исламе.",
                "Современный комплекс сохраняет связь с историческим местом Куба и принимает большое количество молящихся.",
            ),
            visitNotes = "Основная остановка. Оставьте достаточно времени, чтобы спокойно войти, совершить намаз и собраться с группой перед продолжением маршрута.",
        ),
        "en" to ZiyaratPlaceTranslation(
            title = "Quba Mosque",
            shortDescription = "The first mosque established in Islam and one of Madinah’s most important ziyarat stops.",
            longDescription = "Quba Mosque is closely connected with the Hijrah and the earliest Muslim community in Madinah. The present mosque stands on the historic site and remains one of the city’s most visited places.",
            interestingFacts = listOf(
                "The mosque is connected with the beginning of the Prophet’s ﷺ life in Madinah.",
                "It is traditionally regarded as the first mosque established in Islam.",
                "The modern complex preserves the identity of the historic Quba site while serving large numbers of worshippers.",
            ),
            visitNotes = "Main stop. Allow enough time to enter calmly, pray and regroup with your guide before continuing the route.",
        ),
        "uz" to ZiyaratPlaceTranslation(
            title = "Qubo masjidi",
            shortDescription = "Islomda barpo etilgan ilk masjid va Madinadagi eng muhim ziyorat maskanlaridan biri.",
            longDescription = "Qubo masjidi hijrat va Madinadagi ilk musulmon jamoasi bilan chambarchas bog‘liq. Hozirgi masjid tarixiy joyda joylashgan bo‘lib, shaharning eng ko‘p ziyorat qilinadigan maskanlaridan biri bo‘lib qolmoqda.",
            interestingFacts = listOf(
                "Masjid Payg‘ambarimiz ﷺning Madinadagi hayotining boshlanish davri bilan bog‘liq.",
                "U an’anaviy ravishda Islomda barpo etilgan birinchi masjid deb qaraladi.",
                "Zamonaviy majmua tarixiy Qubo maskani bilan bog‘liqlikni saqlab, ko‘plab namozxonlarga xizmat qiladi.",
            ),
            visitNotes = "Asosiy to‘xtash joyi. Ichkariga xotirjam kirish, namoz o‘qish va yo‘nalishni davom ettirishdan oldin guruh bilan yig‘ilish uchun yetarli vaqt ajrating.",
        ),
        "uz-cyrl" to ZiyaratPlaceTranslation(
            title = "Қубо масжиди",
            shortDescription = "Исломда барпо этилган илк масжид ва Мадинадаги энг муҳим зиёрат масканларидан бири.",
            longDescription = "Қубо масжиди ҳижрат ва Мадинадаги илк мусулмон жамоаси билан чамбарчас боғлиқ. Ҳозирги масжид тарихий жойда жойлашган бўлиб, шаҳарнинг энг кўп зиёрат қилинадиган масканларидан бири бўлиб қолмоқда.",
            interestingFacts = listOf(
                "Масжид Пайғамбаримиз ﷺнинг Мадинадаги ҳаётининг бошланиш даври билан боғлиқ.",
                "У анъанавий равишда Исломда барпо этилган биринчи масжид деб қаралади.",
                "Замонавий мажмуа тарихий Қубо маскани билан боғлиқликни сақлаб, кўплаб намозхонларга хизмат қилади.",
            ),
            visitNotes = "Асосий тўхташ жойи. Ичкарига хотиржам кириш, намоз ўқиш ва йўналишни давом эттиришдан олдин гуруҳ билан йиғилиш учун етарли вақт ажратинг.",
        ),
    )

    val madinah = ZiyaratRoute(
        id = "medina-main",
        slug = "medina-ziyarat",
        city = "Madinah",
        country = "Saudi Arabia",
        title = "Medina Ziyarat",
        subtitle = "Sacred and historic places around Madinah",
        transportMode = "car",
        status = "published",
        estimatedMinutes = 40,
        stopCount = 1,
        places = listOf(
            ZiyaratPlace(
                id = "quba-mosque",
                routeID = "medina-main",
                slug = "quba-mosque",
                city = "Madinah",
                country = "Saudi Arabia",
                title = "Quba Mosque",
                titleArabic = "مسجد قباء",
                category = "mosque",
                shortDescription = translations.getValue("en").shortDescription,
                longDescription = translations.getValue("en").longDescription,
                interestingFacts = translations.getValue("en").interestingFacts,
                visitNotes = translations.getValue("en").visitNotes,
                visitType = "enter",
                durationMinutes = 40,
                latitude = 24.43917,
                longitude = 39.61722,
                address = "3493 Al Hijrah Rd, Al Khatim, Madinah 42318, Saudi Arabia",
                mapLabel = "Quba Mosque · exact point",
                routeOrder = 1,
                status = "published",
                images = (1..5).map { ZiyaratImage("quba-$it", "asset:ZiyaratQuba$it", it - 1, width = 1254, height = 1254) },
                translations = translations,
            )
        ),
    )

    fun fallback(city: String): ZiyaratRoute = if (city.trim().lowercase() in setOf("makkah", "mecca")) {
        ZiyaratRoute(
            id = "makkah-main", slug = "makkah-ziyarat", city = "Makkah", country = "Saudi Arabia",
            title = "Makkah Ziyarat", subtitle = "Sacred and historic places around Makkah",
        )
    } else madinah
}
