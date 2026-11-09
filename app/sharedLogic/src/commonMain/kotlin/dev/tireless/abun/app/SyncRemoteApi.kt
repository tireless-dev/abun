package dev.tireless.abun.app

import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncPomodoroSession
import dev.tireless.abun.sync.SyncPreference
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.SyncFinanceAccount
import dev.tireless.abun.sync.SyncFinanceExchangeRate
import dev.tireless.abun.sync.SyncFinanceClearingGroup
import dev.tireless.abun.sync.SyncFinanceTransaction
import dev.tireless.abun.sync.SyncFinanceSplit
import dev.tireless.abun.sync.SyncFinanceSplitClearingGroup
import dev.tireless.abun.sync.SyncFinanceAmortizationPlan
import dev.tireless.abun.sync.SyncFinanceBudgetRule
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
data class ApiVersionResponse(
    val name: String? = null,
    val version: String,
    val commit: String? = null,
    val buildTime: String? = null,
)

fun ApiVersionResponse.formatForDisplay(): String {
    if (commit.isNullOrBlank() && buildTime.isNullOrBlank()) return version
    val timePart = if (!buildTime.isNullOrBlank()) "-$buildTime" else ""
    val suffix = if (commit == "wip" || commit.isNullOrBlank()) "wip" else commit
    return "$version$timePart-$suffix"
}

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
    suspend fun fetchApiVersion(): ApiVersionResponse =
        expectJson(client.get("$baseUrl/api/version"))

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

open class SyncRemoteApi(
    private val baseUrl: String,
    private val client: HttpClient,
    private val accessTokenProvider: AccessTokenProvider,
    private val logger: AppLogger = DefaultAppLogger,
) {
    open suspend fun pullPreferences(cursor: Long, limit: Int): PullResponse<SyncPreference> = pull("preferences", cursor, limit)
    open suspend fun pullRoutines(cursor: Long, limit: Int): PullResponse<SyncRoutine> = pull("routines", cursor, limit)
    open suspend fun pullTasks(cursor: Long, limit: Int): PullResponse<SyncTask> = pull("tasks", cursor, limit)
    open suspend fun pullAlarms(cursor: Long, limit: Int): PullResponse<SyncAlarm> = pull("alarms", cursor, limit)
    open suspend fun pullTaskEvents(cursor: Long, limit: Int): PullResponse<SyncTaskEvent> = pull("task-events", cursor, limit)
    open suspend fun pullPomodoroSessions(cursor: Long, limit: Int): PullResponse<SyncPomodoroSession> = pull("pomodoro-sessions", cursor, limit)
    open suspend fun pullFinanceAccounts(cursor: Long, limit: Int): PullResponse<SyncFinanceAccount> = pull("finance-accounts", cursor, limit)
    open suspend fun pullFinanceExchangeRates(cursor: Long, limit: Int): PullResponse<SyncFinanceExchangeRate> = pull("finance-exchange-rates", cursor, limit)
    open suspend fun pullFinanceClearingGroups(cursor: Long, limit: Int): PullResponse<SyncFinanceClearingGroup> = pull("finance-clearing-groups", cursor, limit)
    open suspend fun pullFinanceTransactions(cursor: Long, limit: Int): PullResponse<SyncFinanceTransaction> = pull("finance-transactions", cursor, limit)
    open suspend fun pullFinanceSplits(cursor: Long, limit: Int): PullResponse<SyncFinanceSplit> = pull("finance-splits", cursor, limit)
    open suspend fun pullFinanceSplitClearingGroups(cursor: Long, limit: Int): PullResponse<SyncFinanceSplitClearingGroup> = pull("finance-split-clearing-groups", cursor, limit)
    open suspend fun pullFinanceAmortizationPlans(cursor: Long, limit: Int): PullResponse<SyncFinanceAmortizationPlan> = pull("finance-amortization-plans", cursor, limit)
    open suspend fun pullFinanceBudgetRules(cursor: Long, limit: Int): PullResponse<SyncFinanceBudgetRule> = pull("finance-budget-rules", cursor, limit)

    open suspend fun pushPreferences(items: List<SyncPreference>): List<SyncPreference> = push("preferences", items)
    open suspend fun pushRoutines(items: List<SyncRoutine>): List<SyncRoutine> = push("routines", items)
    open suspend fun pushTasks(items: List<SyncTask>): List<SyncTask> = push("tasks", items)
    open suspend fun pushAlarms(items: List<SyncAlarm>): List<SyncAlarm> = push("alarms", items)
    open suspend fun pushTaskEvents(items: List<SyncTaskEvent>): List<SyncTaskEvent> = push("task-events", items)
    open suspend fun pushPomodoroSessions(items: List<SyncPomodoroSession>): List<SyncPomodoroSession> = push("pomodoro-sessions", items)
    open suspend fun pushFinanceAccounts(items: List<SyncFinanceAccount>): List<SyncFinanceAccount> = push("finance-accounts", items)
    open suspend fun pushFinanceExchangeRates(items: List<SyncFinanceExchangeRate>): List<SyncFinanceExchangeRate> = push("finance-exchange-rates", items)
    open suspend fun pushFinanceClearingGroups(items: List<SyncFinanceClearingGroup>): List<SyncFinanceClearingGroup> = push("finance-clearing-groups", items)
    open suspend fun pushFinanceTransactions(items: List<SyncFinanceTransaction>): List<SyncFinanceTransaction> = push("finance-transactions", items)
    open suspend fun pushFinanceSplits(items: List<SyncFinanceSplit>): List<SyncFinanceSplit> = push("finance-splits", items)
    open suspend fun pushFinanceSplitClearingGroups(items: List<SyncFinanceSplitClearingGroup>): List<SyncFinanceSplitClearingGroup> = push("finance-split-clearing-groups", items)
    open suspend fun pushFinanceAmortizationPlans(items: List<SyncFinanceAmortizationPlan>): List<SyncFinanceAmortizationPlan> = push("finance-amortization-plans", items)
    open suspend fun pushFinanceBudgetRules(items: List<SyncFinanceBudgetRule>): List<SyncFinanceBudgetRule> = push("finance-budget-rules", items)

    private suspend inline fun <reified T> pull(resource: String, cursor: Long, limit: Int): PullResponse<T> =
        executeAuthorizedRequest {
            logger.info(
                message = "sync.remote.pull.request",
                context = mapOf(
                    "resource" to resource,
                    "cursor" to cursor.toString(),
                    "limit" to limit.toString(),
                ),
            )
            expectJson(
                client.get("$baseUrl/api/sync/$resource?cursor=$cursor&limit=$limit") {
                    authorize(it)
                },
            )
        }

    private suspend inline fun <reified T> push(resource: String, items: List<T>): List<T> =
        executeAuthorizedRequest {
            logger.info(
                message = "sync.remote.push.request",
                context = mapOf(
                    "resource" to resource,
                    "itemCount" to items.size.toString(),
                ),
            )
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
                logger.error(
                    message = "sync.remote.request.failed",
                    context = mapOf("statusCode" to error.statusCode.toString()),
                    throwable = error,
                )
                throw error
            }
            logger.info(message = "sync.remote.request.retrying_after_unauthorized")
            block(accessTokenProvider.validAccessToken(forceRefresh = true))
        } catch (error: Throwable) {
            logger.error(
                message = "sync.remote.request.unexpected_error",
                throwable = error,
            )
            throw error
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
