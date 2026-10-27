package com.iumrah.beta.core.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.iumrahPreferences by preferencesDataStore(name = "iumrah_settings")

enum class AppAppearance(val wireValue: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

enum class AppLanguage(val code: String, val localeTag: String) {
    RUSSIAN("ru", "ru-RU"),
    ENGLISH("en", "en-US"),
    ARABIC("ar", "ar-SA"),
    TURKISH("tr", "tr-TR"),
    INDONESIAN("id", "id-ID"),
    MALAY("ms", "ms-MY"),
    UZBEK("uz", "uz-Latn-UZ"),
    UZBEK_CYRILLIC("uz-cyrl", "uz-Cyrl-UZ");

    val locale: Locale get() = Locale.forLanguageTag(localeTag)
    val isRightToLeft: Boolean get() = this == ARABIC
    val nativeTitle: String get() = when (this) {
        RUSSIAN -> "Русский"
        ENGLISH -> "English"
        ARABIC -> "العربية"
        TURKISH -> "Türkçe"
        INDONESIAN -> "Bahasa Indonesia"
        MALAY -> "Bahasa Melayu"
        UZBEK -> "O‘zbekcha"
        UZBEK_CYRILLIC -> "Ўзбекча"
    }

    companion object {
        fun fromCode(value: String?): AppLanguage = entries.firstOrNull { it.code == value } ?: UZBEK
    }
}

data class AppSettingsState(
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val language: AppLanguage = AppLanguage.UZBEK,
    val firstName: String = "",
    val lastName: String = "",
    val telegram: String = "",
    val whatsapp: String = "",
    val phone: String = "",
    val email: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val nationality: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    val emergencyRelation: String = "",
    val launcherIcon: String = "standard",
    val hasCompletedOnboarding: Boolean = false,
    val isLoaded: Boolean = false,
) {
    val displayName: String
        get() = listOf(firstName.trim(), lastName.trim()).filter { it.isNotBlank() }.joinToString(" ")

    val hasBookingIdentity: Boolean
        get() = firstName.isNotBlank() && lastName.isNotBlank() && (telegram.isNotBlank() || whatsapp.isNotBlank())
}

class AppSettingsStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AppSettingsState())
    val state: StateFlow<AppSettingsState> = _state

    init {
        scope.launch { restore() }
    }

    suspend fun restore() {
        val values = context.iumrahPreferences.data.first()
        _state.value = AppSettingsState(
            appearance = values[Keys.APPEARANCE]?.let { raw -> AppAppearance.entries.firstOrNull { it.wireValue == raw } } ?: AppAppearance.SYSTEM,
            language = AppLanguage.fromCode(values[Keys.LANGUAGE]),
            firstName = values[Keys.FIRST_NAME].orEmpty(),
            lastName = values[Keys.LAST_NAME].orEmpty(),
            telegram = values[Keys.TELEGRAM].orEmpty(),
            whatsapp = values[Keys.WHATSAPP].orEmpty(),
            phone = values[Keys.PHONE].orEmpty(),
            email = values[Keys.EMAIL].orEmpty(),
            dateOfBirth = values[Keys.DATE_OF_BIRTH].orEmpty(),
            gender = values[Keys.GENDER].orEmpty(),
            nationality = values[Keys.NATIONALITY].orEmpty(),
            emergencyName = values[Keys.EMERGENCY_NAME].orEmpty(),
            emergencyPhone = values[Keys.EMERGENCY_PHONE].orEmpty(),
            emergencyRelation = values[Keys.EMERGENCY_RELATION].orEmpty(),
            launcherIcon = values[Keys.LAUNCHER_ICON] ?: "standard",
            hasCompletedOnboarding = values[Keys.ONBOARDING] == "true",
            isLoaded = true,
        )
    }

    fun setAppearance(value: AppAppearance) = persist(Keys.APPEARANCE, value.wireValue) { copy(appearance = value) }
    fun setLanguage(value: AppLanguage) = persist(Keys.LANGUAGE, value.code) { copy(language = value) }
    fun completeOnboarding() = persist(Keys.ONBOARDING, "true") { copy(hasCompletedOnboarding = true) }

    fun setLauncherIcon(value: String) {
        val normalized = value.lowercase().takeIf { it in LAUNCHER_ALIASES.keys } ?: "standard"
        val packageManager = context.packageManager
        LAUNCHER_ALIASES.forEach { (key, suffix) ->
            val component = ComponentName(context.packageName, "com.iumrah.beta.$suffix")
            val state = if (key == normalized) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching { packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
        }
        persist(Keys.LAUNCHER_ICON, normalized) { copy(launcherIcon = normalized) }
    }

    fun updateProfile(firstName: String, lastName: String, telegram: String, whatsapp: String) {
        _state.update { it.copy(firstName = firstName, lastName = lastName, telegram = telegram, whatsapp = whatsapp) }
        scope.launch {
            context.iumrahPreferences.edit {
                it[Keys.FIRST_NAME] = firstName
                it[Keys.LAST_NAME] = lastName
                it[Keys.TELEGRAM] = telegram
                it[Keys.WHATSAPP] = whatsapp
            }
        }
    }

    fun updateOwnerDetails(
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        telegram: String,
        whatsapp: String,
        dateOfBirth: String,
        gender: String,
        nationality: String,
        emergencyName: String,
        emergencyPhone: String,
        emergencyRelation: String,
    ) {
        _state.update {
            it.copy(
                firstName = firstName,
                lastName = lastName,
                phone = phone,
                email = email,
                telegram = telegram,
                whatsapp = whatsapp,
                dateOfBirth = dateOfBirth,
                gender = gender,
                nationality = nationality,
                emergencyName = emergencyName,
                emergencyPhone = emergencyPhone,
                emergencyRelation = emergencyRelation,
            )
        }
        scope.launch {
            context.iumrahPreferences.edit {
                it[Keys.FIRST_NAME] = firstName
                it[Keys.LAST_NAME] = lastName
                it[Keys.PHONE] = phone
                it[Keys.EMAIL] = email
                it[Keys.TELEGRAM] = telegram
                it[Keys.WHATSAPP] = whatsapp
                it[Keys.DATE_OF_BIRTH] = dateOfBirth
                it[Keys.GENDER] = gender
                it[Keys.NATIONALITY] = nationality
                it[Keys.EMERGENCY_NAME] = emergencyName
                it[Keys.EMERGENCY_PHONE] = emergencyPhone
                it[Keys.EMERGENCY_RELATION] = emergencyRelation
            }
        }
    }

    private fun persist(key: Preferences.Key<String>, value: String, reducer: AppSettingsState.() -> AppSettingsState) {
        _state.update { it.reducer() }
        scope.launch { context.iumrahPreferences.edit { it[key] = value } }
    }

    private object Keys {
        val APPEARANCE = stringPreferencesKey("iumrah.appearance")
        val LANGUAGE = stringPreferencesKey("iumrah.language")
        val FIRST_NAME = stringPreferencesKey("iumrah.profile.firstName")
        val LAST_NAME = stringPreferencesKey("iumrah.profile.lastName")
        val TELEGRAM = stringPreferencesKey("iumrah.profile.telegram")
        val WHATSAPP = stringPreferencesKey("iumrah.profile.whatsapp")
        val PHONE = stringPreferencesKey("iumrah.profile.phone")
        val EMAIL = stringPreferencesKey("iumrah.profile.email")
        val DATE_OF_BIRTH = stringPreferencesKey("iumrah.profile.dateOfBirth")
        val GENDER = stringPreferencesKey("iumrah.profile.gender")
        val NATIONALITY = stringPreferencesKey("iumrah.profile.nationality")
        val EMERGENCY_NAME = stringPreferencesKey("iumrah.profile.emergencyName")
        val EMERGENCY_PHONE = stringPreferencesKey("iumrah.profile.emergencyPhone")
        val EMERGENCY_RELATION = stringPreferencesKey("iumrah.profile.emergencyRelation")
        val LAUNCHER_ICON = stringPreferencesKey("iumrah.launcherIcon")
        val ONBOARDING = stringPreferencesKey("iumrah.hasCompletedOnboarding.cinematic.v4")
    }

    companion object {
        private val LAUNCHER_ALIASES = linkedMapOf(
            "standard" to "LauncherStandard",
            "blue" to "LauncherBlue",
            "cyan" to "LauncherCyan",
            "deep" to "LauncherDeep",
            "world" to "LauncherWorld",
            "makkah" to "LauncherMakkah",
        )
    }
}
