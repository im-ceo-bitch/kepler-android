#!/usr/bin/env bash
# Compile exact pure Kotlin functions from UI files with real translation tables.
# Android/Compose compilation is separately required in GitHub CI.
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
python3 - "$TMP" <<'PY'
from pathlib import Path
import sys
out = Path(sys.argv[1])
src = Path('app/src/main/java/com/iumrah/beta')
settings = (src/'core/settings/AppSettingsStore.kt').read_text()
(out/'AppLanguage.kt').write_text('package com.iumrah.beta.core.settings\nimport java.util.Locale\n' + settings[settings.index('enum class AppLanguage'):settings.index('data class AppSettingsState')])
esim = (src/'ui/esim/ESIMScreen.kt').read_text()
trip = (src/'ui/trip/CuratedRoundTripFlights.kt').read_text()
# These functions are the final declarations in their files.
(out/'Regression.kt').write_text('''import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.localization.L10n
''' + esim[esim.index('private fun esimText('):] + '\n' + trip[trip.index('private fun curatedTripText('):] + '''
fun main() {
    var count = 0
    fun expect(condition: Boolean) { check(condition); count++ }
    val key = "header_subtitle"
    expect(esimText(AppLanguage.ENGLISH, key) == "Connectivity in Saudi Arabia")
    expect(esimText(AppLanguage.RUSSIAN, key) == "Связь в Саудовской Аравии")
    for (language in AppLanguage.entries) {
        expect(esimText(language, key).isNotBlank())
        expect(esimText(language, "__unknown__") == "__unknown__")
        expect(curatedRoundTripRowTitle(language).isNotBlank())
        expect(curatedTripText(language, "RU", "EN", "UZ", "CY") == when(language) {
            AppLanguage.RUSSIAN -> "RU"
            AppLanguage.UZBEK -> "UZ"
            AppLanguage.UZBEK_CYRILLIC -> "CY"
            else -> L10n.phrase("EN", language)
        })
    }
    for (language in listOf(AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY)) {
        expect(esimText(language, key) == L10n.phrase(esimText(AppLanguage.ENGLISH, key), language))
        expect(curatedRoundTripRowTitle(language) == L10n.phrase("Round-trip tickets", language))
    }
    println("V10 hotfix localization: $count checks passed")
}
''')
PY
kotlinc "$TMP/AppLanguage.kt" "$TMP/Regression.kt" app/src/main/java/com/iumrah/beta/core/localization/{L10n,IumrahInternationalTranslations}.kt -d "$TMP/test.jar"
kotlin -cp "$TMP/test.jar" RegressionKt
