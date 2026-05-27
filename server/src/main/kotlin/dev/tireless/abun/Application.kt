package dev.tireless.abun

import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncPomodoroSession
import dev.tireless.abun.sync.SyncPreference
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.TaskStatus
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    val services = AppServices.fromEnvironment()
    io.ktor.server.engine.embeddedServer(
        io.ktor.server.netty.Netty,
        port = 8080,
        host = "0.0.0.0",
        module = { module(services) },
    ).start(wait = true)
}

fun Application.module(services: AppServices = AppServices.fromEnvironment()) {
    attributes.put(AppServicesKey, services)
    monitor.subscribe(ApplicationStopped) {
        services.close()
    }

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request"))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request"))
        }
    }

    routing {
        get("/") {
            call.respondText("abun sync server")
        }

        route("/sync") {
            syncRoutes(services)
        }
        route("/auth") {
            authRoutes(services)
        }
        route("/api") {
            apiRoutes(services)
        }
    }
}

private fun Route.authRoutes(services: AppServices) {
    post("/otp/request") {
        val request = call.receive<OtpRequestBody>()
        services.auth.requestOtp(request.email)
        call.respond(io.ktor.http.HttpStatusCode.NoContent)
    }
    post("/otp/verify") {
        val request = call.receive<OtpVerifyBody>()
        val (userId, token) = services.auth.verifyOtp(request.email, request.otp)
        call.respond(OtpVerifyResponse(accessToken = token, userId = userId))
    }
}

private fun Route.syncRoutes(services: AppServices) {
    get("/preferences") {
        call.respond(services.preferences.list(call.userId(), call.cursor(), call.limit()))
    }
    post("/preferences") {
        val request = call.receive<BatchRequest<SyncPreference>>()
        call.respond(BatchRequest(services.preferences.push(call.userId(), request.items)))
    }

    get("/routines") {
        call.respond(services.routines.list(call.userId(), call.cursor(), call.limit()))
    }
    post("/routines") {
        val request = call.receive<BatchRequest<SyncRoutine>>()
        call.respond(BatchRequest(services.routines.push(call.userId(), request.items)))
    }

    get("/tasks") {
        call.respond(services.tasks.list(call.userId(), call.cursor(), call.limit()))
    }
    post("/tasks") {
        val request = call.receive<BatchRequest<SyncTask>>()
        call.respond(BatchRequest(services.tasks.push(call.userId(), request.items)))
    }

    get("/alarms") {
        call.respond(services.alarms.list(call.userId(), call.cursor(), call.limit()))
    }
    post("/alarms") {
        val request = call.receive<BatchRequest<SyncAlarm>>()
        call.respond(BatchRequest(services.alarms.push(call.userId(), request.items)))
    }

    get("/task-events") {
        call.respond(services.taskEvents.list(call.userId(), call.cursor(), call.limit()))
    }
    post("/task-events") {
        val request = call.receive<BatchRequest<SyncTaskEvent>>()
        call.respond(BatchRequest(services.taskEvents.push(call.userId(), request.items)))
    }

    get("/pomodoro-sessions") {
        call.respond(services.pomodoroSessions.list(call.userId(), call.cursor(), call.limit()))
    }
    post("/pomodoro-sessions") {
        val request = call.receive<BatchRequest<SyncPomodoroSession>>()
        call.respond(BatchRequest(services.pomodoroSessions.push(call.userId(), request.items)))
    }
}

private fun Route.apiRoutes(services: AppServices) {
    route("/preferences") {
        get {
            call.respond(services.preferences.all(call.userId()).map(SyncPreference::toResponse))
        }
        get("/{key}") {
            val preference = services.preferences.get(call.userId(), call.requirePath("key"))
            if (preference == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(preference.toResponse())
            }
        }
        put("/{key}") {
            call.respond(services.preferences.putFromBusinessApi(call.userId(), call.requirePath("key"), call.receive<PreferencePutRequest>()).toResponse())
        }
        delete("/{key}") {
            val preference = services.preferences.softDeleteFromBusinessApi(call.userId(), call.requirePath("key"))
            if (preference == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(preference.toResponse())
            }
        }
    }

    route("/routines") {
        get {
            call.respond(services.routines.all(call.userId()).map(SyncRoutine::toResponse))
        }
        get("/{id}") {
            val routine = services.routines.get(call.userId(), call.requireId())
            if (routine == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(routine.toResponse())
            }
        }
        post {
            call.respond(
                io.ktor.http.HttpStatusCode.Created,
                services.routines.createFromBusinessApi(call.userId(), call.receive<RoutineUpsertRequest>()).toResponse(),
            )
        }
        patch("/{id}") {
            val routine = services.routines.patchFromBusinessApi(call.userId(), call.requireId(), call.receive<RoutinePatchRequest>())
            if (routine == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(routine.toResponse())
            }
        }
        delete("/{id}") {
            val routine = services.routines.softDeleteFromBusinessApi(call.userId(), call.requireId())
            if (routine == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(routine.toResponse())
            }
        }
    }

    route("/tasks") {
        get {
            call.respond(services.tasks.all(call.userId()).map(SyncTask::toResponse))
        }
        get("/{id}") {
            val task = services.tasks.get(call.userId(), call.requireId())
            if (task == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(task.toResponse())
            }
        }
        post {
            call.respond(
                io.ktor.http.HttpStatusCode.Created,
                services.tasks.createFromBusinessApi(call.userId(), call.receive<TaskUpsertRequest>()).toResponse(),
            )
        }
        patch("/{id}") {
            val task = services.tasks.patchFromBusinessApi(call.userId(), call.requireId(), call.receive<TaskPatchRequest>())
            if (task == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(task.toResponse())
            }
        }
        delete("/{id}") {
            val task = services.tasks.softDeleteFromBusinessApi(call.userId(), call.requireId())
            if (task == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(task.toResponse())
            }
        }
        get("/{id}/status") {
            val status = services.taskEvents.deriveStatus(call.userId(), call.requireId())
            if (status == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(TaskStatusResponse(status))
            }
        }
        get("/{id}/events") {
            val taskId = call.requireId()
            val task = services.tasks.get(call.userId(), taskId)
            if (task == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(services.taskEvents.allForTask(call.userId(), taskId).map(SyncTaskEvent::toResponse))
            }
        }
        post("/{id}/events") {
            val taskId = call.requireId()
            val created = services.taskEvents.createFromBusinessApi(
                call.userId(),
                call.receive<TaskEventCreateRequest>().copy(taskId = taskId),
            )
            call.respond(io.ktor.http.HttpStatusCode.Created, created.toResponse())
        }
    }

    route("/alarms") {
        get {
            call.respond(services.alarms.all(call.userId()).map(SyncAlarm::toResponse))
        }
        get("/{id}") {
            val alarm = services.alarms.get(call.userId(), call.requireId())
            if (alarm == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(alarm.toResponse())
            }
        }
        post {
            call.respond(
                io.ktor.http.HttpStatusCode.Created,
                services.alarms.createFromBusinessApi(call.userId(), call.receive<AlarmUpsertRequest>()).toResponse(),
            )
        }
        patch("/{id}") {
            val alarm = services.alarms.patchFromBusinessApi(call.userId(), call.requireId(), call.receive<AlarmPatchRequest>())
            if (alarm == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(alarm.toResponse())
            }
        }
        delete("/{id}") {
            val alarm = services.alarms.softDeleteFromBusinessApi(call.userId(), call.requireId())
            if (alarm == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(alarm.toResponse())
            }
        }
    }

    route("/pomodoro-sessions") {
        get {
            call.respond(services.pomodoroSessions.all(call.userId()).map(SyncPomodoroSession::toResponse))
        }
        get("/{id}") {
            val session = services.pomodoroSessions.get(call.userId(), call.requireId())
            if (session == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(session.toResponse())
            }
        }
        post {
            call.respond(
                io.ktor.http.HttpStatusCode.Created,
                services.pomodoroSessions.createFromBusinessApi(call.userId(), call.receive<PomodoroSessionUpsertRequest>()).toResponse(),
            )
        }
        patch("/{id}") {
            val session = services.pomodoroSessions.patchFromBusinessApi(
                call.userId(),
                call.requireId(),
                call.receive<PomodoroSessionPatchRequest>(),
            )
            if (session == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(session.toResponse())
            }
        }
        delete("/{id}") {
            val session = services.pomodoroSessions.softDeleteFromBusinessApi(call.userId(), call.requireId())
            if (session == null) {
                call.respond(io.ktor.http.HttpStatusCode.NotFound)
            } else {
                call.respond(session.toResponse())
            }
        }
    }

    get("/journals/{date}") {
        call.respond(services.taskEvents.journal(call.userId(), call.requirePath("date")))
    }
}

private fun ApplicationCall.userId(): String {
    val authorization = request.headers["Authorization"]
    val bearerToken = authorization
        ?.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.trim()
    val userId = appServices().auth.parseUserIdFromToken(bearerToken)
    if (userId != null) return userId
    return request.headers["X-User-Id"] ?: "demo-user"
}

private fun ApplicationCall.appServices(): AppServices =
    application.attributes[AppServicesKey]

private fun ApplicationCall.cursor(): Long = request.queryParameters["cursor"]?.toLongOrNull() ?: 0L

private fun ApplicationCall.limit(): Int = request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 500

private fun ApplicationCall.requireId(): String = requirePath("id")

private fun ApplicationCall.requirePath(name: String): String =
    parameters[name] ?: throw IllegalArgumentException("missing $name parameter")

@Serializable
data class TaskUpsertRequest(
    val id: String? = null,
    val title: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
)

@Serializable
data class TaskPatchRequest(
    val title: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
)

@Serializable
data class TaskEventCreateRequest(
    val id: String? = null,
    @SerialName("task_id") val taskId: String,
    @SerialName("journal_date") val journalDate: String,
    @SerialName("event_type") val eventType: dev.tireless.abun.sync.TaskEventType,
    val content: String? = null,
    @SerialName("event_time") val eventTime: String,
)

@Serializable
data class TaskStatusResponse(
    val status: TaskStatus,
)

@Serializable
data class ErrorResponse(
    val message: String,
)

@Serializable
data class OtpRequestBody(val email: String)

@Serializable
data class OtpVerifyBody(val email: String, val otp: String)

@Serializable
data class OtpVerifyResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("user_id") val userId: String,
)

private val AppServicesKey = io.ktor.util.AttributeKey<AppServices>("appServices")
