package dev.tireless.abun.ui.i18n

import java.util.Locale

actual fun platformSystemLanguage(): AppLanguage {
    val locale = Locale.getDefault()
    val language = locale.language.lowercase()
    if (language == "zh") {
        val script = locale.script.lowercase()
        val country = locale.country.uppercase()
        return if (script == "hant" || country in setOf("TW", "HK", "MO")) {
            AppLanguage.TC
        } else {
            AppLanguage.SC
        }
    }
    return AppLanguage.EN
}
