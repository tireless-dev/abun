package dev.tireless.abun.app

import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncPreference
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncPomodoroSession
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OtpRequest(val email: String)

@Serializable
data class OtpVerifyRequest(val email: String, val otp: String)

@Serializable
data class OtpVerifyResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("user_id") val userId: String,
)

class SyncRemoteApi(
    private val baseUrl: String,
    private val client: HttpClient,
    private val authProvider: AuthProvider,
) {
    suspend fun requestOtp(email: String) {
        client.post("$baseUrl/api/auth/otp/request") {
            contentType(ContentType.Application.Json)
            setBody(OtpRequest(email.trim()))
        }
    }

    suspend fun verifyOtp(email: String, otp: String): OtpVerifyResponse =
        client.post("$baseUrl/api/auth/otp/verify") {
            contentType(ContentType.Application.Json)
            setBody(OtpVerifyRequest(email.trim(), otp.trim()))
        }.body()

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
        client.get("$baseUrl/api/sync/$resource?cursor=$cursor&limit=$limit") {
            authorize()
        }.body()

    private suspend inline fun <reified T> push(resource: String, items: List<T>): List<T> =
        client.post("$baseUrl/api/sync/$resource") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(BatchRequest(items))
        }.body<BatchRequest<T>>().items

    private suspend fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        val token = authProvider.bearerToken().trim()
        if (token.isNotEmpty()) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
