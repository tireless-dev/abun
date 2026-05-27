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

data class AppDependencies(
    val databaseDriverFactory: DatabaseDriverFactory,
    val httpClient: HttpClient,
    val authProvider: AuthProvider,
    val nodeIdProvider: DeviceNodeIdProvider,
    val idGenerator: IdGenerator,
    val timeProvider: TimeProvider,
    val serverBaseUrl: String,
)
