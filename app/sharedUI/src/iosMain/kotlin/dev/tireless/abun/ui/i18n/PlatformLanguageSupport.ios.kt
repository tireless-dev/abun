package dev.tireless.abun.ui.i18n

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages

actual fun platformSystemLanguage(): AppLanguage {
    val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String)?.lowercase()
        ?: NSLocale.currentLocale.languageCode.lowercase()
    if (preferred.startsWith("zh")) {
        return if (preferred.contains("hant") || preferred.contains("tw") || preferred.contains("hk") || preferred.contains("mo")) {
            AppLanguage.TC
        } else {
            AppLanguage.SC
        }
    }
    return AppLanguage.EN
}
