package com.iumrah.beta.core.localization
import com.iumrah.beta.core.settings.AppLanguage
import java.io.File
fun main(args: Array<String>) {
    var count = 0
    File(args.single()).forEachLine { row ->
        val (code, key, raw) = row.split('\t', limit = 3)
        val expected = raw.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
        val language = AppLanguage.entries.single { it.code == code }
        check(L10n.text(key, language) == expected) { "Mismatch $code $key" }
        count++
    }
    for (language in AppLanguage.entries) {
        val value = L10n.format("hotel_detail_package_total_for_fmt", language, "$500", 2)
        check(value.contains("$500") && !value.contains("%@") && !value.contains("%d")) { "Unformatted $language: $value" }
        check(L10n.text("hotel_care_call", language) != "hotel_care_call")
        check(L10n.text("hotel_gallery_all", language) != "hotel_gallery_all")
    }
    println("$count source-derived keyed values; 24 cross-locale format/fallback assertions passed")
}
