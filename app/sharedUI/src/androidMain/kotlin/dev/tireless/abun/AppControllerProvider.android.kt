package dev.tireless.abun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.content.pm.ApplicationInfo
import androidx.compose.ui.platform.LocalContext
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AndroidDatabaseDriverFactory
import dev.tireless.abun.app.AndroidNodeIdProvider
import dev.tireless.abun.app.AppDependencies
import dev.tireless.abun.app.DEFAULT_SERVER_BASE_URL
import dev.tireless.abun.app.DefaultTimeProvider
import dev.tireless.abun.app.DebugAuthPreset
import dev.tireless.abun.app.DemoAuthProvider
import dev.tireless.abun.app.LoginPreferenceStore
import dev.tireless.abun.app.StableStringIdGenerator
import dev.tireless.abun.app.ThemePreference
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

@Composable
actual fun rememberAbunAppController(): AbunAppController {
    val context = LocalContext.current
    return remember {
        AbunAppController.create(
            AppDependencies(
                databaseDriverFactory = AndroidDatabaseDriverFactory(context),
                httpClient = HttpClient(OkHttp) {
                    install(ContentNegotiation) { json(dev.tireless.abun.app.AppJson) }
                },
                authProvider = DemoAuthProvider(),
                loginPreferenceStore = AndroidLoginPreferenceStore(context),
                nodeIdProvider = AndroidNodeIdProvider(),
                idGenerator = StableStringIdGenerator(),
                timeProvider = DefaultTimeProvider(),
                debugAuthPreset = debugAuthPreset(
                    enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                ),
                serverBaseUrl = DEFAULT_SERVER_BASE_URL,
            ),
        )
    }
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

private class AndroidLoginPreferenceStore(context: android.content.Context) : LoginPreferenceStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCE_FILE, android.content.Context.MODE_PRIVATE)

    override fun isLoginOmitted(): Boolean = preferences.getBoolean(LOGIN_OMITTED_KEY, false)

    override fun setLoginOmitted(isOmitted: Boolean) {
        preferences.edit().apply {
            if (isOmitted) {
                putBoolean(LOGIN_OMITTED_KEY, true)
            } else {
                remove(LOGIN_OMITTED_KEY)
            }
        }.apply()
    }

    override fun themePreference(): ThemePreference =
        preferences.getString(THEME_PREFERENCE_KEY, ThemePreference.SYSTEM.name)
            ?.let(ThemePreference::valueOf)
            ?: ThemePreference.SYSTEM

    override fun setThemePreference(themePreference: ThemePreference) {
        preferences.edit().apply {
            if (themePreference == ThemePreference.SYSTEM) {
                remove(THEME_PREFERENCE_KEY)
            } else {
                putString(THEME_PREFERENCE_KEY, themePreference.name)
            }
        }.apply()
    }
}

private const val PREFERENCE_FILE = "abun.local.preferences"
private const val LOGIN_OMITTED_KEY = "app.auth.login_omitted"
private const val THEME_PREFERENCE_KEY = "app.theme_preference"
