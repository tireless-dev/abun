package dev.tireless.abun.app

import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncAlarm
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

class SyncRemoteApi(
    private val baseUrl: String,
    private val client: HttpClient,
    private val authProvider: AuthProvider,
) {
    suspend fun pullRoutines(cursor: Long, limit: Int): PullResponse<SyncRoutine> = pull("routines", cursor, limit)
    suspend fun pullTasks(cursor: Long, limit: Int): PullResponse<SyncTask> = pull("tasks", cursor, limit)
    suspend fun pullAlarms(cursor: Long, limit: Int): PullResponse<SyncAlarm> = pull("alarms", cursor, limit)
    suspend fun pullTaskEvents(cursor: Long, limit: Int): PullResponse<SyncTaskEvent> = pull("task-events", cursor, limit)

    suspend fun pushRoutines(items: List<SyncRoutine>): List<SyncRoutine> = push("routines", items)
    suspend fun pushTasks(items: List<SyncTask>): List<SyncTask> = push("tasks", items)
    suspend fun pushAlarms(items: List<SyncAlarm>): List<SyncAlarm> = push("alarms", items)
    suspend fun pushTaskEvents(items: List<SyncTaskEvent>): List<SyncTaskEvent> = push("task-events", items)

    private suspend inline fun <reified T> pull(resource: String, cursor: Long, limit: Int): PullResponse<T> =
        client.get("$baseUrl/sync/$resource?cursor=$cursor&limit=$limit") {
            authorize()
        }.body()

    private suspend inline fun <reified T> push(resource: String, items: List<T>): List<T> =
        client.post("$baseUrl/sync/$resource") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(BatchRequest(items))
        }.body<BatchRequest<T>>().items

    private suspend fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        header(HttpHeaders.Authorization, "Bearer ${authProvider.bearerToken()}")
    }
}
