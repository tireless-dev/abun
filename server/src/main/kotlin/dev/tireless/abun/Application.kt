package dev.tireless.abun

import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.TaskStatus
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

    routing {
        get("/") {
            call.respondText("abun sync server")
        }

        route("/sync") {
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
        }

        route("/api/tasks") {
            get {
                call.respond(services.tasks.all(call.userId()))
            }
            get("/{id}") {
                val task = services.tasks.get(call.userId(), call.requireId())
                if (task == null) {
                    call.respond(io.ktor.http.HttpStatusCode.NotFound)
                } else {
                    call.respond(task)
                }
            }
            post {
                val request = call.receive<TaskUpsertRequest>()
                call.respond(
                    io.ktor.http.HttpStatusCode.Created,
                    services.tasks.createFromBusinessApi(call.userId(), request),
                )
            }
            patch("/{id}") {
                val task = services.tasks.patchFromBusinessApi(call.userId(), call.requireId(), call.receive<TaskPatchRequest>())
                if (task == null) {
                    call.respond(io.ktor.http.HttpStatusCode.NotFound)
                } else {
                    call.respond(task)
                }
            }
            delete("/{id}") {
                val task = services.tasks.softDeleteFromBusinessApi(call.userId(), call.requireId())
                if (task == null) {
                    call.respond(io.ktor.http.HttpStatusCode.NotFound)
                } else {
                    call.respond(task)
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
        }

        route("/api/task-events") {
            post {
                val request = call.receive<TaskEventCreateRequest>()
                call.respond(
                    io.ktor.http.HttpStatusCode.Created,
                    services.taskEvents.createFromBusinessApi(call.userId(), request),
                )
            }
        }

        get("/api/journals/{date}") {
            call.respond(services.taskEvents.journal(call.userId(), call.parameters["date"].orEmpty()))
        }
    }
}

private fun ApplicationCall.userId(): String {
    val authorization = request.headers["Authorization"]
    val bearerUserId = authorization
        ?.takeIf { it.startsWith("Bearer ") }
        ?.removePrefix("Bearer ")
        ?.trim()
    return bearerUserId ?: request.headers["X-User-Id"] ?: "demo-user"
}

private fun ApplicationCall.cursor(): Long = request.queryParameters["cursor"]?.toLongOrNull() ?: 0L

private fun ApplicationCall.limit(): Int = request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 500

private fun ApplicationCall.requireId(): String = parameters["id"] ?: error("missing id parameter")

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
