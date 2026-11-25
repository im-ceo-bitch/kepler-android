package com.iumrah.beta.core.settings
import java.util.Locale
enum class AppLanguage(val code: String, val localeTag: String) {
    RUSSIAN("ru", "ru-RU"), ENGLISH("en", "en-US"), ARABIC("ar", "ar-SA"), TURKISH("tr", "tr-TR"), INDONESIAN("id", "id-ID"), MALAY("ms", "ms-MY"), UZBEK("uz", "uz-Latn-UZ"), UZBEK_CYRILLIC("uz-cyrl", "uz-Cyrl-UZ");
    val locale: Locale get() = Locale.forLanguageTag(localeTag)
}
