package dev.tireless.abun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AppDependencies
import dev.tireless.abun.app.DefaultTimeProvider
import dev.tireless.abun.app.DebugAuthPreset
import dev.tireless.abun.app.DemoAuthProvider
import dev.tireless.abun.app.JvmDatabaseDriverFactory
import dev.tireless.abun.app.LoginPreferenceStore
import dev.tireless.abun.app.JvmNodeIdProvider
import dev.tireless.abun.app.StableStringIdGenerator
import dev.tireless.abun.app.ThemePreference
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.util.prefs.Preferences

@Composable
actual fun rememberAbunAppController(): AbunAppController = remember {
    AbunAppController.create(
        AppDependencies(
            databaseDriverFactory = JvmDatabaseDriverFactory(),
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) { json(dev.tireless.abun.app.AppJson) }
            },
            authProvider = DemoAuthProvider(),
            loginPreferenceStore = JvmLoginPreferenceStore(),
            nodeIdProvider = JvmNodeIdProvider(),
            idGenerator = StableStringIdGenerator(),
            timeProvider = DefaultTimeProvider(),
            serverBaseUrl = "http://127.0.0.1:8080",
            debugAuthPreset = debugAuthPreset(enabled = System.getProperty("abun.debug") == "true"),
        ),
    )
}

private fun debugAuthPreset(enabled: Boolean): DebugAuthPreset? = if (enabled) {
    DebugAuthPreset(
        email = "abun@tireless.dev",
        otp = "424242",
        accessToken = "debug-access-token",
        userId = "debug-user",
    )
} else {
    null
}

private class JvmLoginPreferenceStore : LoginPreferenceStore {
    private val preferences = Preferences.userRoot().node("dev/tireless/abun")

    override fun isLoginOmitted(): Boolean = preferences.getBoolean(LOGIN_OMITTED_KEY, false)

    override fun setLoginOmitted(isOmitted: Boolean) {
        if (isOmitted) {
            preferences.putBoolean(LOGIN_OMITTED_KEY, true)
        } else {
            preferences.remove(LOGIN_OMITTED_KEY)
        }
    }

    override fun themePreference(): ThemePreference =
        preferences.get(THEME_PREFERENCE_KEY, ThemePreference.SYSTEM.name)
            ?.let(ThemePreference::valueOf)
            ?: ThemePreference.SYSTEM

    override fun setThemePreference(themePreference: ThemePreference) {
        if (themePreference == ThemePreference.SYSTEM) {
            preferences.remove(THEME_PREFERENCE_KEY)
        } else {
            preferences.put(THEME_PREFERENCE_KEY, themePreference.name)
        }
    }
}

private const val LOGIN_OMITTED_KEY = "app.auth.login_omitted"
private const val THEME_PREFERENCE_KEY = "app.theme_preference"
