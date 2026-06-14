package dev.tireless.abun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AuthSession
import dev.tireless.abun.app.AppDependencies
import dev.tireless.abun.app.DefaultTimeProvider
import dev.tireless.abun.app.DebugAuthPreset
import dev.tireless.abun.app.JvmDatabaseDriverFactory
import dev.tireless.abun.app.LoginPreferenceStore
import dev.tireless.abun.app.JvmNodeIdProvider
import dev.tireless.abun.app.DEFAULT_SERVER_BASE_URL
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
            loginPreferenceStore = JvmLoginPreferenceStore(),
            nodeIdProvider = JvmNodeIdProvider(),
            idGenerator = StableStringIdGenerator(),
            timeProvider = DefaultTimeProvider(),
            debugAuthPreset = debugAuthPreset(enabled = System.getProperty("abun.debug") == "true"),
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

    override fun authSession(): AuthSession? {
        val userId = preferences.get(AUTH_USER_ID_KEY, null) ?: return null
        val accessToken = preferences.get(AUTH_ACCESS_TOKEN_KEY, null) ?: return null
        val accessTokenExpiresAt = preferences.getLong(AUTH_ACCESS_EXPIRES_AT_KEY, 0L)
        val refreshToken = preferences.get(AUTH_REFRESH_TOKEN_KEY, null) ?: return null
        val refreshTokenExpiresAt = preferences.getLong(AUTH_REFRESH_EXPIRES_AT_KEY, 0L)
        return AuthSession(userId, accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt)
    }

    override fun setAuthSession(session: AuthSession) {
        preferences.put(AUTH_USER_ID_KEY, session.userId)
        preferences.put(AUTH_ACCESS_TOKEN_KEY, session.accessToken)
        preferences.putLong(AUTH_ACCESS_EXPIRES_AT_KEY, session.accessTokenExpiresAtEpochMillis)
        preferences.put(AUTH_REFRESH_TOKEN_KEY, session.refreshToken)
        preferences.putLong(AUTH_REFRESH_EXPIRES_AT_KEY, session.refreshTokenExpiresAtEpochMillis)
    }

    override fun clearAuthSession() {
        preferences.remove(AUTH_USER_ID_KEY)
        preferences.remove(AUTH_ACCESS_TOKEN_KEY)
        preferences.remove(AUTH_ACCESS_EXPIRES_AT_KEY)
        preferences.remove(AUTH_REFRESH_TOKEN_KEY)
        preferences.remove(AUTH_REFRESH_EXPIRES_AT_KEY)
    }
}

private const val LOGIN_OMITTED_KEY = "app.auth.login_omitted"
private const val THEME_PREFERENCE_KEY = "app.theme_preference"
private const val AUTH_USER_ID_KEY = "app.auth.user_id"
private const val AUTH_ACCESS_TOKEN_KEY = "app.auth.access_token"
private const val AUTH_ACCESS_EXPIRES_AT_KEY = "app.auth.access_token_expires_at"
private const val AUTH_REFRESH_TOKEN_KEY = "app.auth.refresh_token"
private const val AUTH_REFRESH_EXPIRES_AT_KEY = "app.auth.refresh_token_expires_at"
