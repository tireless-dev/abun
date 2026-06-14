package dev.tireless.abun.app

import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncPomodoroSession
import dev.tireless.abun.sync.SyncPreference
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val OTP_EMAIL_METHOD = "otp_email"

@Serializable
data class AuthRequest(
    val method: String,
    val email: String,
)

@Serializable
data class AuthVerifyRequest(
    val method: String,
    val email: String,
    val otp: String,
)

@Serializable
data class AuthRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class AuthLogoutRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class AuthSessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("access_token_expires_at") val accessTokenExpiresAt: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("refresh_token_expires_at") val refreshTokenExpiresAt: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class ErrorResponse(
    val message: String,
)

class RemoteApiException(
    val statusCode: Int,
    override val message: String,
) : Exception(message)

class AuthRemoteApi(
    private val baseUrl: String,
    private val client: HttpClient,
) {
    suspend fun requestOtp(email: String) {
        expectNoContent(
            client.post("$baseUrl/auth/request") {
                contentType(ContentType.Application.Json)
                setBody(AuthRequest(method = OTP_EMAIL_METHOD, email = email.trim()))
            },
        )
    }

    suspend fun verifyOtp(email: String, otp: String): AuthSessionResponse =
        expectJson(
            client.post("$baseUrl/auth/verify") {
                contentType(ContentType.Application.Json)
                setBody(
                    AuthVerifyRequest(
                        method = OTP_EMAIL_METHOD,
                        email = email.trim(),
                        otp = otp.trim(),
                    ),
                )
            },
        )

    suspend fun refreshSession(refreshToken: String): AuthSessionResponse =
        expectJson(
            client.post("$baseUrl/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(AuthRefreshRequest(refreshToken = refreshToken))
            },
        )

    suspend fun logout(
        refreshToken: String,
        accessToken: String?,
    ) {
        expectNoContent(
            client.post("$baseUrl/auth/logout") {
                contentType(ContentType.Application.Json)
                if (!accessToken.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                setBody(AuthLogoutRequest(refreshToken = refreshToken))
            },
        )
    }
}

class SyncRemoteApi(
    private val baseUrl: String,
    private val client: HttpClient,
    private val accessTokenProvider: AccessTokenProvider,
) {
    suspend fun pullPreferences(cursor: Long, limit: Int): PullResponse<SyncPreference> = pull("preferences", cursor, limit)
    suspend fun pullRoutines(cursor: Long, limit: Int): PullResponse<SyncRoutine> = pull("routines", cursor, limit)
    suspend fun pullTasks(cursor: Long, limit: Int): PullResponse<SyncTask> = pull("tasks", cursor, limit)
    suspend fun pullAlarms(cursor: Long, limit: Int): PullResponse<SyncAlarm> = pull("alarms", cursor, limit)
    suspend fun pullTaskEvents(cursor: Long, limit: Int): PullResponse<SyncTaskEvent> = pull("task-events", cursor, limit)
    suspend fun pullPomodoroSessions(cursor: Long, limit: Int): PullResponse<SyncPomodoroSession> = pull("pomodoro-sessions", cursor, limit)

    suspend fun pushPreferences(items: List<SyncPreference>): List<SyncPreference> = push("preferences", items)
    suspend fun pushRoutines(items: List<SyncRoutine>): List<SyncRoutine> = push("routines", items)
    suspend fun pushTasks(items: List<SyncTask>): List<SyncTask> = push("tasks", items)
    suspend fun pushAlarms(items: List<SyncAlarm>): List<SyncAlarm> = push("alarms", items)
    suspend fun pushTaskEvents(items: List<SyncTaskEvent>): List<SyncTaskEvent> = push("task-events", items)
    suspend fun pushPomodoroSessions(items: List<SyncPomodoroSession>): List<SyncPomodoroSession> = push("pomodoro-sessions", items)

    private suspend inline fun <reified T> pull(resource: String, cursor: Long, limit: Int): PullResponse<T> =
        executeAuthorizedRequest {
            expectJson(
                client.get("$baseUrl/api/sync/$resource?cursor=$cursor&limit=$limit") {
                    authorize(it)
                },
            )
        }

    private suspend inline fun <reified T> push(resource: String, items: List<T>): List<T> =
        executeAuthorizedRequest {
            expectJson<BatchRequest<T>>(
                client.post("$baseUrl/api/sync/$resource") {
                    authorize(it)
                    contentType(ContentType.Application.Json)
                    setBody(BatchRequest(items))
                },
            ).items
        }

    private suspend inline fun <T> executeAuthorizedRequest(
        crossinline block: suspend (accessToken: String) -> T,
    ): T {
        return try {
            block(accessTokenProvider.validAccessToken())
        } catch (error: RemoteApiException) {
            if (error.statusCode != HttpStatusCode.Unauthorized.value) {
                throw error
            }
            block(accessTokenProvider.validAccessToken(forceRefresh = true))
        }
    }
}

fun AuthSessionResponse.toAuthSession(): AuthSession = AuthSession(
    userId = userId,
    accessToken = accessToken,
    accessTokenExpiresAtEpochMillis = isoStringToEpochMillis(accessTokenExpiresAt),
    refreshToken = refreshToken,
    refreshTokenExpiresAtEpochMillis = isoStringToEpochMillis(refreshTokenExpiresAt),
)

private suspend inline fun <reified T> expectJson(response: io.ktor.client.statement.HttpResponse): T {
    if (response.status.isSuccess()) {
        return response.body()
    }
    throw RemoteApiException(
        statusCode = response.status.value,
        message = response.readErrorMessage(),
    )
}

private suspend fun expectNoContent(response: io.ktor.client.statement.HttpResponse) {
    if (response.status.isSuccess()) {
        return
    }
    throw RemoteApiException(
        statusCode = response.status.value,
        message = response.readErrorMessage(),
    )
}

private suspend fun io.ktor.client.statement.HttpResponse.readErrorMessage(): String =
    runCatching { body<ErrorResponse>().message }
        .getOrNull()
        ?: "Request failed with status ${status.value}"

private fun io.ktor.client.request.HttpRequestBuilder.authorize(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
}
