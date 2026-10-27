package com.iumrah.beta.ui.booking

import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.models.booking.BookingItineraryItem

/**
 * Current iOS parity: operational itinerary rows can be authored by staff in any
 * supported language. Normalize known semantic event kinds at render time so the
 * booking schedule always follows the pilgrim's selected app locale.
 */
internal fun localizeServerItinerary(items: List<BookingItineraryItem>, language: AppLanguage): List<BookingItineraryItem> =
    items.map { localizeServerItineraryItem(it, language) }

private fun localizeServerItineraryItem(item: BookingItineraryItem, language: AppLanguage): BookingItineraryItem {
    val kind = item.kind.orEmpty().trim().lowercase()
    val title = item.title.trim()
    val normalized = "$kind ${title.lowercase()}"
    val localizedTitle = when {
        containsAny(normalized, "passport", "immigration", "baggage", "паспорт", "багаж", "nazorat", "назорат") ->
            scheduleTr(language, "Паспортный контроль и багаж", "Passport control and baggage", "Pasport nazorati va bagaj", "Паспорт назорати ва багаж")
        containsAny(normalized, "hotel_check", "checkin", "check-in", "засел", "joylash", "жойлаш") ->
            scheduleTr(language, "Заселение в отель", "Hotel check-in", "Mehmonxonaga joylashish", "Меҳмонхонага жойлашиш")
        containsAny(normalized, "hotel_transfer", "transfer_to_hotel", "трансфер в отель", "mehmonxonaga transfer", "меҳмонхонага трансфер") ->
            scheduleTr(language, "Трансфер в отель", "Transfer to hotel", "Mehmonxonaga transfer", "Меҳмонхонага трансфер")
        containsAny(normalized, "airport_transfer", "transfer_to_airport", "трансфер в аэропорт", "aeroportga transfer", "аэропортга трансфер") ->
            scheduleTr(language, "Трансфер в аэропорт", "Transfer to airport", "Aeroportga transfer", "Аэропортга трансфер")
        containsAny(normalized, "arrival", "прилёт", "прибыт", "saud", "yetib kel", "келиш") && !normalized.contains("departure") ->
            scheduleTr(language, "Прилёт в Саудовскую Аравию", "Arrival in Saudi Arabia", "Saudiya Arabistoniga yetib kelish", "Саудия Арабистонига етиб келиш")
        containsAny(normalized, "makkah_ziyar", "зияраты мек", "makka ziyor", "макка зиёрат") ->
            scheduleTr(language, "Зияраты Мекки", "Makkah ziyarat", "Makka ziyoratlari", "Макка зиёратлари")
        containsAny(normalized, "madinah_ziyar", "зияраты мед", "madina ziyor", "мадина зиёрат") ->
            scheduleTr(language, "Зияраты Медины", "Madinah ziyarat", "Madina ziyoratlari", "Мадина зиёратлари")
        containsAny(normalized, "to_makkah", "переезд в мек", "makkaga", "маккага") ->
            scheduleTr(language, "Переезд в Мекку", "Transfer to Makkah", "Makkaga yo‘l", "Маккага йўл")
        containsAny(normalized, "to_madinah", "переезд в мед", "madinaga", "мадинага") ->
            scheduleTr(language, "Переезд в Медину", "Transfer to Madinah", "Madinaga yo‘l", "Мадинага йўл")
        containsAny(normalized, "flight_home", "departure", "вылет домой", "уйга парвоз", "uyga parvoz") ->
            scheduleTr(language, "Вылет домой", "Flight home", "Uyga parvoz", "Уйга парвоз")
        containsAny(normalized, "free_day", "свободный день", "erkin kun", "эркин кун") ->
            scheduleTr(language, "Свободный день", "Free day", "Erkin kun", "Эркин кун")
        containsAny(normalized, "umrah", "умра", "umra") ->
            scheduleTr(language, "Умра", "Umrah", "Umra", "Умра")
        else -> title
    }
    return item.copy(
        title = localizedTitle,
        subtitle = localizeServerSubtitle(item.subtitle, language),
        location = localizeServerLocation(item.location, language),
    )
}

private fun localizeServerSubtitle(value: String, language: AppLanguage): String {
    val trimmed = value.trim()
    val normalized = trimmed.lowercase()
    val suffixes = listOf(" min", " мин", " daq", " дақ")
    for (suffix in suffixes) {
        if (normalized.endsWith(suffix)) {
            val minutes = normalized.removeSuffix(suffix).trim().toIntOrNull() ?: continue
            return when (language) {
                AppLanguage.RUSSIAN -> "$minutes мин"
                AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "$minutes min"
                AppLanguage.UZBEK -> "$minutes daq"
                AppLanguage.UZBEK_CYRILLIC -> "$minutes дақ"
            }
        }
    }
    return trimmed
}

private fun localizeServerLocation(value: String, language: AppLanguage): String = when (value.trim().lowercase()) {
    "медина", "madinah", "madina", "мадина" -> scheduleTr(language, "Медина", "Madinah", "Madina", "Мадина")
    "мекка", "makkah", "makka", "макка" -> scheduleTr(language, "Мекка", "Makkah", "Makka", "Макка")
    "джидда", "jeddah", "jidda", "жидда" -> scheduleTr(language, "Джидда", "Jeddah", "Jidda", "Жидда")
    else -> value
}

private fun containsAny(value: String, vararg needles: String): Boolean = needles.any(value::contains)

private fun scheduleTr(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
