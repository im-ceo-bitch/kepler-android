package com.iumrah.beta.ui.cupertino

import androidx.compose.ui.graphics.Color
import com.iumrah.beta.core.design.IumrahColors

/**
 * Android mirror of iOS DesignSystem.swift::IumrahIconRole.
 * These are information-state colors (not global brand accent colors).
 * Keep role assignments identical across Account, Booking, Hotels and Care.
 */
enum class IumrahIconRole(val color: Color) {
    ACCENT(IumrahColors.SystemBlue),
    TRAVEL(IumrahColors.SystemBlue),
    HOTEL(IumrahColors.SystemIndigo),
    BOOKING(IumrahColors.SystemOrange),
    UMRAH(IumrahColors.SystemTeal),
    CARE(IumrahColors.SystemPink),
    PROFILE(IumrahColors.SystemCyan),
    PHONE(IumrahColors.SystemGreen),
    MAIL(IumrahColors.SystemBlue),
    MESSAGE(IumrahColors.SystemTeal),
    CONNECTIVITY(IumrahColors.SystemBlue),
    DEVICE(IumrahColors.SystemBlue),
    GIFT(IumrahColors.SystemPurple),
    SEARCH(IumrahColors.SystemBlue),
    RATING(IumrahColors.SystemYellow),
    CALENDAR(IumrahColors.SystemOrange),
    LOCATION(IumrahColors.SystemRed),
    SECURITY(IumrahColors.SystemGreen),
    NOTIFICATION(IumrahColors.SystemRed),
    APPEARANCE(IumrahColors.SystemPurple),
    LANGUAGE(IumrahColors.SystemBlue),
    SETTINGS(IumrahColors.SystemGray),
    DOCUMENT(IumrahColors.SystemCyan),
    PAYMENT(IumrahColors.SystemGreen),
    WAITING(IumrahColors.SystemYellow),
    PAYMENT_PENDING(IumrahColors.SystemOrange),
    CONFIRMED(IumrahColors.SystemGreen),
    READY(IumrahColors.SystemTeal),
    IN_TRIP(IumrahColors.SystemBlue),
    COMPLETED(IumrahColors.SystemIndigo),
    TELEGRAM(IumrahColors.SystemBlue),
    WHATSAPP(IumrahColors.SystemGreen),
    TRANSFER(IumrahColors.SystemCyan),
    SUCCESS(IumrahColors.SystemGreen),
    WARNING(IumrahColors.SystemYellow),
    DESTRUCTIVE(IumrahColors.SystemRed),
    NEUTRAL(IumrahColors.SystemGray);

    /** iOS uses black at 78% only for yellow status/rating badges. */
    val onColor: Color
        get() = if (this == WAITING || this == WARNING || this == RATING)
            Color.Black.copy(alpha = 0.78f) else Color.White

    companion object {
        // Matches the ordering of iOS IumrahIconRole.inferred(from:).
        fun inferred(systemName: String): IumrahIconRole {
            val name = systemName.lowercase()
            return when {
                name.contains("trash") || name.contains("delete") -> DESTRUCTIVE
                name.contains("exclamation") || name.contains("warning") -> WARNING
                name.contains("checkmark") || name.contains("seal") -> SUCCESS
                listOf("creditcard", "wallet", "dollarsign", "banknote").any { name.contains(it) } -> PAYMENT
                listOf("passport", "doc", "text.rectangle", "id").any { name.contains(it) } -> DOCUMENT
                listOf("lock", "shield", "key", "faceid", "touchid").any { name.contains(it) } -> SECURITY
                name.contains("bell") -> NOTIFICATION
                listOf("simcard", "wifi", "antenna", "network").any { name.contains(it) } -> CONNECTIVITY
                listOf("iphone", "ipad", "desktopcomputer", "laptopcomputer", "apps.iphone").any { name.contains(it) } -> DEVICE
                name.contains("gift") -> GIFT
                name.contains("magnifyingglass") || name.contains("scope") -> SEARCH
                name.contains("star") -> RATING
                name.contains("lefthalf") || name.contains("sun.") || name == "sun.max.fill" || name.contains("paintpalette") -> APPEARANCE
                name.contains("globe") || name.contains("character") -> LANGUAGE
                listOf("gear", "slider", "switch").any { name.contains(it) } -> SETTINGS
                listOf("mappin", "location", "map").any { name.contains(it) } -> LOCATION
                name.contains("calendar") -> CALENDAR
                name.contains("clock") || name.contains("timer") -> WAITING
                name.contains("phone") -> PHONE
                name.contains("envelope") -> MAIL
                name.contains("message") || name.contains("bubble") -> MESSAGE
                name.contains("building") || name.contains("bed") || name.contains("house") -> HOTEL
                name.contains("suitcase") || name.contains("bag") -> BOOKING
                listOf("airplane", "car", "bus", "tram", "ferry").any { name.contains(it) } -> TRAVEL
                name.contains("book") || name.contains("moon.stars") || name.contains("hands") || name.contains("sparkles") -> UMRAH
                name.contains("person") || name.contains("figure") -> PROFILE
                name.contains("plus") -> ACCENT
                else -> NEUTRAL
            }
        }
    }
}
