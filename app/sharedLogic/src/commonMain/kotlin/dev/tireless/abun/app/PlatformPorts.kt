package dev.tireless.abun.app

import app.cash.sqldelight.db.SqlDriver
import dev.tireless.abun.ota.PlatformOtaClient
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate

interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val accessTokenExpiresAtEpochMillis: Long,
    val refreshToken: String,
    val refreshTokenExpiresAtEpochMillis: Long,
)

interface AccessTokenProvider {
    suspend fun validAccessToken(forceRefresh: Boolean = false): String
}

interface DeviceNodeIdProvider {
    fun nodeId(): String
}

interface IdGenerator {
    fun randomId(): String
    fun deterministicId(namespace: String, seed: String): String
}

interface TimeProvider {
    fun nowEpochMillis(): Long
    fun today(): LocalDate
}

interface LoginPreferenceStore {
    fun isLoginOmitted(): Boolean
    fun setLoginOmitted(isOmitted: Boolean)
    fun themePreference(): ThemePreference
    fun setThemePreference(themePreference: ThemePreference)
    fun authSession(): AuthSession?
    fun setAuthSession(session: AuthSession)
    fun clearAuthSession()
    fun lastSyncedUserId(): String? = null
    fun setLastSyncedUserId(userId: String?) {}
    fun isAutoUpdateEnabled(): Boolean = true
    fun setAutoUpdateEnabled(enabled: Boolean) {}
}

data class AppDependencies(
    val databaseDriverFactory: DatabaseDriverFactory,
    val httpClient: HttpClient,
    val loginPreferenceStore: LoginPreferenceStore,
    val nodeIdProvider: DeviceNodeIdProvider,
    val idGenerator: IdGenerator,
    val timeProvider: TimeProvider,
    val serverBaseUrl: String,
    val logger: AppLogger = DefaultAppLogger,
    val otaClient: PlatformOtaClient = PlatformOtaClient(),
)
