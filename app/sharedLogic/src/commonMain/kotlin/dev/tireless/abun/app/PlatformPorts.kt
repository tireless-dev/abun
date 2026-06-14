package dev.tireless.abun.app

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate

interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

interface AuthProvider {
    suspend fun bearerToken(): String
}

interface MutableAuthProvider : AuthProvider {
    fun updateToken(token: String?)
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
}

data class AppDependencies(
    val databaseDriverFactory: DatabaseDriverFactory,
    val httpClient: HttpClient,
    val authProvider: AuthProvider,
    val loginPreferenceStore: LoginPreferenceStore,
    val nodeIdProvider: DeviceNodeIdProvider,
    val idGenerator: IdGenerator,
    val timeProvider: TimeProvider,
    val serverBaseUrl: String,
    val debugAuthPreset: DebugAuthPreset? = null,
)

data class DebugAuthPreset(
    val email: String,
    val otp: String,
    val accessToken: String,
    val userId: String,
)
