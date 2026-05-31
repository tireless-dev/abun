package dev.tireless.abun

import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.PomodoroPhaseWire
import dev.tireless.abun.sync.PomodoroSessionStateWire
import dev.tireless.abun.sync.PomodoroTaskUpdateWire
import dev.tireless.abun.sync.PreferenceValueType
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.h2.jdbcx.JdbcDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `root responds with sync server banner`() = testApplication {
        application { module(testServices()) }

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("abun sync server", response.bodyAsText())
    }

    @Test
    fun `task sync accepts newer hlc and rejects older hlc`() = testApplication {
        application { module(testServices()) }
        val jsonClient = jsonClient()

        val initialResponse = jsonClient.post("/sync/tasks") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                BatchRequest(
                    items = listOf(
                        SyncTask(
                            id = "task-1",
                            title = "Write spec",
                            hlcMap = mapOf("title" to "1715959378000-0001-deviceA"),
                            dirtyFields = listOf("title"),
                        ),
                    ),
                ),
            )
        }
        val initialBody = initialResponse.body<BatchRequest<SyncTask>>()
        assertEquals(listOf("title"), initialBody.items.single().acceptedFields)

        val conflictResponse = jsonClient.post("/sync/tasks") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                BatchRequest(
                    items = listOf(
                        SyncTask(
                            id = "task-1",
                            title = "Old edit",
                            hlcMap = mapOf("title" to "1715959378000-0000-deviceB"),
                            dirtyFields = listOf("title"),
                        ),
                    ),
                ),
            )
        }

        val conflictBody = conflictResponse.body<BatchRequest<SyncTask>>()
        val item = conflictBody.items.single()
        assertEquals("Write spec", item.title)
        assertTrue(item.acceptedFields.orEmpty().isEmpty())
        assertEquals(listOf("title"), item.rejectedFields)
    }

    @Test
    fun `preferences are scoped per user and business writes appear in sync pulls`() = testApplication {
        application { module(testServices()) }
        val jsonClient = jsonClient()

        val updated = jsonClient.put("/api/preferences/task.default_alarm_lead_minutes") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(PreferencePutRequest(value = "20", valueType = PreferenceValueType.INT))
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals("20", updated.body<PreferenceResponse>().value)

        val userOnePreferences = jsonClient.get("/api/preferences") {
            auth("user-1")
        }.body<List<PreferenceResponse>>()
        assertEquals(1, userOnePreferences.size)
        assertEquals("task.default_alarm_lead_minutes", userOnePreferences.single().key)

        val userTwoPreferences = jsonClient.get("/api/preferences") {
            auth("user-2")
        }.body<List<PreferenceResponse>>()
        assertTrue(userTwoPreferences.isEmpty())

        val pulled = jsonClient.get("/sync/preferences?cursor=0&limit=10") {
            auth("user-1")
        }.body<PullResponse<dev.tireless.abun.sync.SyncPreference>>()
        assertEquals(1, pulled.items.size)
        assertEquals("20", pulled.items.single().value)
    }

    @Test
    fun `business apis support full mutable resource flow and sync visibility`() = testApplication {
        application { module(testServices()) }
        val jsonClient = jsonClient()

        val routine = jsonClient.post("/api/routines") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                RoutineUpsertRequest(
                    id = "routine-1",
                    templateTitle = "Morning plan",
                    cronSchedule = "0 9 * * *",
                    timezone = "UTC",
                ),
            )
        }.body<RoutineResponse>()
        assertEquals("routine-1", routine.id)

        val task = jsonClient.post("/api/tasks") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                TaskUpsertRequest(
                    id = "task-1",
                    title = "Plan day",
                    detail = "Work through the new planning window",
                    routineId = routine.id,
                    startNotBefore = "2026-05-25T09:00:00Z",
                    endNotAfter = "2026-05-25T17:00:00Z",
                    estimatedDuration = "PT2H",
                ),
            )
        }.body<TaskResponse>()
        assertEquals("task-1", task.id)
        assertEquals("Work through the new planning window", task.detail)
        assertEquals("2026-05-25T09:00:00Z", task.startNotBefore)
        assertEquals("2026-05-25T17:00:00Z", task.endNotAfter)
        assertEquals("PT2H", task.estimatedDuration)

        val alarm = jsonClient.post("/api/alarms") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                AlarmUpsertRequest(
                    id = "alarm-1",
                    taskId = task.id,
                    triggerTime = "2026-05-25T08:50:00Z",
                ),
            )
        }.body<AlarmResponse>()
        assertEquals("alarm-1", alarm.id)

        val session = jsonClient.post("/api/pomodoro-sessions") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                PomodoroSessionUpsertRequest(
                    id = "session-1",
                    taskId = task.id,
                    phase = PomodoroPhaseWire.FOCUS,
                    state = PomodoroSessionStateWire.ACTIVE,
                    startedAt = "2026-05-25T09:00:00Z",
                    endsAt = "2026-05-25T09:25:00Z",
                    durationMinutes = 25,
                ),
            )
        }.body<PomodoroSessionResponse>()
        assertEquals("session-1", session.id)

        val patchedRoutine = jsonClient.patch("/api/routines/routine-1") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(RoutinePatchRequest(isActive = false))
        }.body<RoutineResponse>()
        assertEquals(false, patchedRoutine.isActive)

        val patchedTask = jsonClient.patch("/api/tasks/task-1") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                TaskPatchRequest(
                    detail = "Updated plan",
                    startNotBefore = "2026-05-25T10:00:00Z",
                    endNotAfter = "2026-05-25T18:00:00Z",
                    estimatedDuration = "PT3H",
                ),
            )
        }.body<TaskResponse>()
        assertEquals("Updated plan", patchedTask.detail)
        assertEquals("2026-05-25T10:00:00Z", patchedTask.startNotBefore)
        assertEquals("2026-05-25T18:00:00Z", patchedTask.endNotAfter)
        assertEquals("PT3H", patchedTask.estimatedDuration)

        val patchedAlarm = jsonClient.patch("/api/alarms/alarm-1") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(AlarmPatchRequest(isActive = false))
        }.body<AlarmResponse>()
        assertEquals(false, patchedAlarm.isActive)

        val patchedSession = jsonClient.patch("/api/pomodoro-sessions/session-1") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                PomodoroSessionPatchRequest(
                    state = PomodoroSessionStateWire.COMPLETED,
                    completedAt = "2026-05-25T09:26:00Z",
                    note = "Finished focus block",
                    taskUpdate = PomodoroTaskUpdateWire.COMPLETE,
                ),
            )
        }.body<PomodoroSessionResponse>()
        assertEquals(PomodoroSessionStateWire.COMPLETED, patchedSession.state)
        assertEquals(PomodoroTaskUpdateWire.COMPLETE, patchedSession.taskUpdate)

        val deletedTask = jsonClient.delete("/api/tasks/task-1") {
            auth("user-1")
        }.body<TaskResponse>()
        assertEquals(true, deletedTask.isDeleted)

        val pulledTasks = jsonClient.get("/sync/tasks?cursor=0&limit=10") {
            auth("user-1")
        }.body<PullResponse<SyncTask>>()
        assertEquals(1, pulledTasks.items.size)
        assertEquals(true, pulledTasks.items.single().isDeleted)
        assertEquals("Updated plan", pulledTasks.items.single().detail)
        assertEquals("2026-05-25T10:00:00Z", pulledTasks.items.single().startNotBefore)
        assertEquals("2026-05-25T18:00:00Z", pulledTasks.items.single().endNotAfter)
        assertEquals("PT3H", pulledTasks.items.single().estimatedDuration)
    }

    @Test
    fun `task events remain append only and direct api exposes events journal and status`() = testApplication {
        application { module(testServices()) }
        val jsonClient = jsonClient()

        jsonClient.post("/api/tasks") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                TaskUpsertRequest(
                    id = "task-1",
                    title = "Ledger task",
                    journalDate = "2026-05-24",
                    eventTime = "2026-05-24T08:00:00Z",
                ),
            )
        }

        val initialEvents = jsonClient.get("/api/tasks/task-1/events") {
            auth("user-1")
        }.body<List<TaskEventResponse>>()
        assertEquals(1, initialEvents.size)
        assertEquals(TaskEventType.CREATED, initialEvents.single().eventType)

        val initialStatus = jsonClient.get("/api/tasks/task-1/status") {
            auth("user-1")
        }.body<TaskStatusResponse>()
        assertEquals(TaskStatus.PENDING, initialStatus.status)

        val initialJournal = jsonClient.get("/api/journals/2026-05-24") {
            auth("user-1")
        }.body<List<JournalEntry>>()
        assertEquals(1, initialJournal.size)
        assertEquals(TaskEventType.CREATED, initialJournal.single().eventType)

        jsonClient.post("/api/tasks") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                TaskUpsertRequest(
                    id = "task-1",
                    title = "Ledger task renamed",
                    journalDate = "2026-05-24",
                    eventTime = "2026-05-24T08:15:00Z",
                ),
            )
        }

        val eventsAfterUpsert = jsonClient.get("/api/tasks/task-1/events") {
            auth("user-1")
        }.body<List<TaskEventResponse>>()
        assertEquals(1, eventsAfterUpsert.size)

        val task = jsonClient.get("/api/tasks/task-1") {
            auth("user-1")
        }.body<TaskResponse>()
        assertEquals("Ledger task renamed", task.title)

        val progressed = jsonClient.post("/api/tasks/task-1/events") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                TaskEventCreateRequest(
                    id = "event-1",
                    taskId = "ignored-by-route",
                    journalDate = "2026-05-24",
                    eventType = TaskEventType.PROGRESSED,
                    eventTime = "2026-05-24T09:00:00Z",
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, progressed.status)

        val created = jsonClient.post("/api/tasks/task-1/events") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                TaskEventCreateRequest(
                    id = "event-extra",
                    taskId = "ignored-by-route",
                    journalDate = "2026-05-24",
                    eventType = TaskEventType.CREATED,
                    eventTime = "2026-05-24T09:10:00Z",
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)

        jsonClient.post("/sync/task-events") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                BatchRequest(
                    items = listOf(
                        SyncTaskEvent(
                            id = "event-2",
                            taskId = "task-1",
                            journalDate = "2026-05-28",
                            eventType = TaskEventType.COMPLETED,
                            eventTime = "2026-05-28T10:00:00Z",
                        ),
                    ),
                ),
            )
        }

        val duplicate = jsonClient.post("/sync/task-events") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                BatchRequest(
                    items = listOf(
                        SyncTaskEvent(
                            id = "event-1",
                            taskId = "task-1",
                            journalDate = "2026-05-24",
                            eventType = TaskEventType.PROGRESSED,
                            eventTime = "2026-05-24T09:00:00Z",
                        ),
                    ),
                ),
            )
        }.body<BatchRequest<SyncTaskEvent>>()
        assertEquals(false, duplicate.items.single().accepted)

        val events = jsonClient.get("/api/tasks/task-1/events") {
            auth("user-1")
        }.body<List<TaskEventResponse>>()
        assertEquals(4, events.size)

        val status = jsonClient.get("/api/tasks/task-1/status") {
            auth("user-1")
        }.body<TaskStatusResponse>()
        assertEquals(TaskStatus.COMPLETED, status.status)

        val journal = jsonClient.get("/api/journals/2026-05-24") {
            auth("user-1")
        }.body<List<JournalEntry>>()
        assertEquals(3, journal.size)
        assertEquals(listOf(TaskEventType.CREATED, TaskEventType.PROGRESSED, TaskEventType.CREATED), journal.map { it.eventType })
    }

    @Test
    fun `invalid ownership returns bad request and missing resources return not found`() = testApplication {
        application { module(testServices()) }
        val jsonClient = jsonClient()

        val badAlarm = jsonClient.post("/api/alarms") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                AlarmUpsertRequest(
                    id = "alarm-1",
                    taskId = "missing-task",
                    triggerTime = "2026-05-25T08:50:00Z",
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, badAlarm.status)
        assertEquals("task_id does not belong to current user", badAlarm.body<ErrorResponse>().message)

        val missingTask = jsonClient.get("/api/tasks/missing-task") {
            auth("user-1")
        }
        assertEquals(HttpStatusCode.NotFound, missingTask.status)

        val missingPreferenceDelete = jsonClient.delete("/api/preferences/unknown") {
            auth("user-1")
        }
        assertEquals(HttpStatusCode.NotFound, missingPreferenceDelete.status)
    }

    @Test
    fun `auth required mode rejects unauthenticated requests`() = testApplication {
        application { module(testServices(authRequired = true)) }
        val jsonClient = jsonClient()

        val response = jsonClient.get("/api/tasks")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Missing or invalid bearer token", response.body<ErrorResponse>().message)
    }

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }

    private fun testServices(authRequired: Boolean = false): AppServices {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        return AppServices.forDataSource(dataSource, authRequired = authRequired)
    }
}

private fun io.ktor.client.request.HttpRequestBuilder.auth(userId: String) {
    header(HttpHeaders.Authorization, "Bearer $userId")
}
