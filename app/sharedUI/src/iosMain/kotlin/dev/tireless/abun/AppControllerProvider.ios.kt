package dev.tireless.abun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AuthSession
import dev.tireless.abun.app.AppDependencies
import dev.tireless.abun.app.DEFAULT_SERVER_BASE_URL
import dev.tireless.abun.app.DefaultTimeProvider
import dev.tireless.abun.app.DebugAuthPreset
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
        accessTokenExpiresAtEpochMillis = Long.MAX_VALUE,
        refreshToken = "debug-refresh-token",
        refreshTokenExpiresAtEpochMillis = Long.MAX_VALUE,
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

    override fun authSession(): AuthSession? {
        val userId = defaults.stringForKey(AUTH_USER_ID_KEY) ?: return null
        val accessToken = defaults.stringForKey(AUTH_ACCESS_TOKEN_KEY) ?: return null
        val refreshToken = defaults.stringForKey(AUTH_REFRESH_TOKEN_KEY) ?: return null
        val accessTokenExpiresAt = defaults.doubleForKey(AUTH_ACCESS_EXPIRES_AT_KEY).toLong()
        val refreshTokenExpiresAt = defaults.doubleForKey(AUTH_REFRESH_EXPIRES_AT_KEY).toLong()
        return AuthSession(userId, accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt)
    }

    override fun setAuthSession(session: AuthSession) {
        defaults.setObject(session.userId, forKey = AUTH_USER_ID_KEY)
        defaults.setObject(session.accessToken, forKey = AUTH_ACCESS_TOKEN_KEY)
        defaults.setDouble(session.accessTokenExpiresAtEpochMillis.toDouble(), forKey = AUTH_ACCESS_EXPIRES_AT_KEY)
        defaults.setObject(session.refreshToken, forKey = AUTH_REFRESH_TOKEN_KEY)
        defaults.setDouble(session.refreshTokenExpiresAtEpochMillis.toDouble(), forKey = AUTH_REFRESH_EXPIRES_AT_KEY)
    }

    override fun clearAuthSession() {
        defaults.removeObjectForKey(AUTH_USER_ID_KEY)
        defaults.removeObjectForKey(AUTH_ACCESS_TOKEN_KEY)
        defaults.removeObjectForKey(AUTH_ACCESS_EXPIRES_AT_KEY)
        defaults.removeObjectForKey(AUTH_REFRESH_TOKEN_KEY)
        defaults.removeObjectForKey(AUTH_REFRESH_EXPIRES_AT_KEY)
    }
}

private const val LOGIN_OMITTED_KEY = "app.auth.login_omitted"
private const val THEME_PREFERENCE_KEY = "app.theme_preference"
private const val AUTH_USER_ID_KEY = "app.auth.user_id"
private const val AUTH_ACCESS_TOKEN_KEY = "app.auth.access_token"
private const val AUTH_ACCESS_EXPIRES_AT_KEY = "app.auth.access_token_expires_at"
private const val AUTH_REFRESH_TOKEN_KEY = "app.auth.refresh_token"
private const val AUTH_REFRESH_EXPIRES_AT_KEY = "app.auth.refresh_token_expires_at"
