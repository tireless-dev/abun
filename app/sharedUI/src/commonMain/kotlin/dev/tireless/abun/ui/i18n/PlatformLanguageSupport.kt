package dev.tireless.abun.ui.i18n

enum class AppLanguage {
    EN,
    SC,
    TC;

    val code: String
        get() = when (this) {
            EN -> "en"
            SC -> "zh-CN"
            TC -> "zh-TW"
        }
}

expect fun platformSystemLanguage(): AppLanguage
