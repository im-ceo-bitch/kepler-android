package com.iumrah.beta.ui.home

import com.iumrah.beta.core.settings.AppLanguage

object HomeEmotionalCopy {
    fun prompt(language: AppLanguage) = when (language) {
        AppLanguage.RUSSIAN -> "Почувствуйте перед поездкой"
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Feel it before your journey"
        AppLanguage.UZBEK -> "Safardan oldin his eting"
        AppLanguage.UZBEK_CYRILLIC -> "Сафардан олдин ҳис этинг"
    }
    fun action(language: AppLanguage) = when (language) {
        AppLanguage.RUSSIAN -> "Попробовать"
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Experience"
        AppLanguage.UZBEK -> "His etish"
        AppLanguage.UZBEK_CYRILLIC -> "Ҳис этиш"
    }
    fun title(index: Int, language: AppLanguage): String {
        val values = when (language) {
            AppLanguage.RUSSIAN -> listOf(
                "Однажды это будет не на экране.",
                "Есть места, к которым сердце приходит раньше нас.",
                "У каждого здесь своя история.",
                "Здесь становится тише внутри.",
                "То, о чём вы просили в тишине…",
                "А потом — Медина.",
            )
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> listOf(
                "One day, this will not be on a screen.",
                "Some places are reached by the heart before we arrive.",
                "Everyone here carries a story.",
                "Something inside becomes quieter here.",
                "What you asked for in silence…",
                "And then — Madinah.",
            )
            AppLanguage.UZBEK -> listOf(
                "Bir kuni bu faqat ekranda bo‘lmaydi.",
                "Shunday joylar borki, qalbimiz bizdan oldin yetib boradi.",
                "Bu yerda har kimning o‘z hikoyasi bor.",
                "Bu yerda ichingiz sokinlashadi.",
                "Sukunatda so‘ragan narsalaringiz…",
                "Keyin esa — Madina.",
            )
            AppLanguage.UZBEK_CYRILLIC -> listOf(
                "Бир куни бу фақат экранда бўлмайди.",
                "Шундай жойлар борки, қалбимиз биздан олдин етиб боради.",
                "Бу ерда ҳар кимнинг ўз ҳикояси бор.",
                "Бу ерда ичингиз сокинлашади.",
                "Сукунатда сўраган нарсаларингиз…",
                "Кейин эса — Мадина.",
            )
        }
        return values.getOrElse(index) { "" }
    }
    fun subtitle(index: Int, language: AppLanguage): String? {
        val values: List<String?> = when (language) {
            AppLanguage.RUSSIAN -> listOf("Вы будете здесь.", null, "И своя молитва.", null, "…однажды может привести вас сюда.", "Город, из которого сердце уезжает не сразу.")
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> listOf("You will be here.", null, "And a prayer of their own.", null, "…may one day bring you here.", "A city the heart does not leave all at once.")
            AppLanguage.UZBEK -> listOf("Siz shu yerda bo‘lasiz.", null, "Va o‘z duosi.", null, "…bir kuni sizni shu yerga olib kelishi mumkin.", "Yurak darrov tark eta olmaydigan shahar.")
            AppLanguage.UZBEK_CYRILLIC -> listOf("Сиз шу ерда бўласиз.", null, "Ва ўз дуоси.", null, "…бир куни сизни шу ерга олиб келиши мумкин.", "Юрак дарров тарк эта олмайдиган шаҳар.")
        }
        return values.getOrNull(index)
    }
}
