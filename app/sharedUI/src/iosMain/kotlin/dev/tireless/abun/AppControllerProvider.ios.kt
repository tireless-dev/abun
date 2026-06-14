package dev.tireless.abun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AppDependencies
import dev.tireless.abun.app.DEFAULT_SERVER_BASE_URL
import dev.tireless.abun.app.DefaultTimeProvider
import dev.tireless.abun.app.DebugAuthPreset
import dev.tireless.abun.app.DemoAuthProvider
import dev.tireless.abun.app.IosDatabaseDriverFactory
import dev.tireless.abun.app.LoginPreferenceStore
import dev.tireless.abun.app.IosNodeIdProvider
import dev.tireless.abun.app.StableStringIdGenerator
import dev.tireless.abun.app.ThemePreference
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSUserDefaults

@OptIn(ExperimentalNativeApi::class)
@Composable
actual fun rememberAbunAppController(): AbunAppController = remember {
    AbunAppController.create(
        AppDependencies(
            databaseDriverFactory = IosDatabaseDriverFactory(),
            httpClient = HttpClient(Darwin) {
                install(ContentNegotiation) { json(dev.tireless.abun.app.AppJson) }
            },
            authProvider = DemoAuthProvider(),
            loginPreferenceStore = IosLoginPreferenceStore(),
            nodeIdProvider = IosNodeIdProvider(),
            idGenerator = StableStringIdGenerator(),
            timeProvider = DefaultTimeProvider(),
            debugAuthPreset = debugAuthPreset(enabled = Platform.isDebugBinary),
            serverBaseUrl = DEFAULT_SERVER_BASE_URL,
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

private class IosLoginPreferenceStore : LoginPreferenceStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun isLoginOmitted(): Boolean = defaults.boolForKey(LOGIN_OMITTED_KEY)

    override fun setLoginOmitted(isOmitted: Boolean) {
        if (isOmitted) {
            defaults.setBool(true, forKey = LOGIN_OMITTED_KEY)
        } else {
            defaults.removeObjectForKey(LOGIN_OMITTED_KEY)
        }
    }

    override fun themePreference(): ThemePreference =
        (defaults.stringForKey(THEME_PREFERENCE_KEY)?.let(ThemePreference::valueOf)) ?: ThemePreference.SYSTEM

    override fun setThemePreference(themePreference: ThemePreference) {
        if (themePreference == ThemePreference.SYSTEM) {
            defaults.removeObjectForKey(THEME_PREFERENCE_KEY)
        } else {
            defaults.setObject(themePreference.name, forKey = THEME_PREFERENCE_KEY)
        }
    }
}

private const val LOGIN_OMITTED_KEY = "app.auth.login_omitted"
private const val THEME_PREFERENCE_KEY = "app.theme_preference"
