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
import dev.tireless.abun.sync.TaskPostponedPayload
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.h2.jdbcx.JdbcDataSource
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
    fun `__any_long__ placeholder rejects quoted numbers`() {
        assertFailsWith<AssertionError> {
            assertJsonMatchesFixture(
                JsonPrimitive("__any_long__"),
                JsonPrimitive("123"),
            )
        }
    }

    @Test
    fun `server routes stay aligned with frozen auth sync and business contracts`() = testApplication {
        application { module(testServices()) }
        val jsonClient = jsonClient()
        val authFixture = readFixture<AuthContractFixture>("docs/contracts/server-fixtures/auth.json")
        val syncFixture = readFixture<SyncTaskContractFixture>("docs/contracts/server-fixtures/sync-tasks.json")
        val businessFixture = readFixture<BusinessApiContractFixture>("docs/contracts/server-fixtures/business-api.json")

        val otpRequestResponse = jsonClient.post("/auth/otp/request") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(fixtureJson.encodeToString(JsonElement.serializer(), authFixture.otpRequest.request))
        }
        assertEquals(HttpStatusCode.fromValue(authFixture.otpRequest.status), otpRequestResponse.status)

        val otpVerifyResponse = jsonClient.post("/auth/otp/verify") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(fixtureJson.encodeToString(JsonElement.serializer(), authFixture.otpVerify.request))
        }
        assertEquals(HttpStatusCode.fromValue(authFixture.otpVerify.status), otpVerifyResponse.status)
        val otpVerifyJson = fixtureJson.parseToJsonElement(otpVerifyResponse.bodyAsText())
        assertJsonMatchesFixture(authFixture.otpVerify.response, otpVerifyJson)

        val accessToken = otpVerifyJson.jsonObject["access_token"]?.jsonPrimitive?.content
        assertNotNull(accessToken)

        val syncResponse = jsonClient.post("/sync/tasks") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(fixtureJson.encodeToString(JsonElement.serializer(), syncFixture.syncTasks.request))
        }
        assertEquals(HttpStatusCode.fromValue(syncFixture.syncTasks.status), syncResponse.status)
        val syncJson = fixtureJson.parseToJsonElement(syncResponse.bodyAsText())
        assertJsonMatchesFixture(syncFixture.syncTasks.response, syncJson)
        assertEquals(
            syncFixture.syncTasks.acceptedFields,
            syncJson.jsonObject.getValue("items").jsonArray.single().jsonObject.getValue("accepted_fields").jsonArray.map { it.jsonPrimitive.content },
        )

        val createTaskResponse = jsonClient.post("/api/tasks") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(fixtureJson.encodeToString(JsonElement.serializer(), businessFixture.createTask.request))
        }
        assertEquals(HttpStatusCode.fromValue(businessFixture.createTask.status), createTaskResponse.status)

        val getTaskResponse = jsonClient.get("/api/tasks/fixture-task") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.fromValue(businessFixture.getTask.status), getTaskResponse.status)
        assertJsonMatchesFixture(
            businessFixture.getTask.response,
            fixtureJson.parseToJsonElement(getTaskResponse.bodyAsText()),
        )
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
    fun `shared test account request stores deterministic otp and verify returns shared user`() {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        val auth = AuthService(ServerDatabase(dataSource))

        auth.requestOtp(TestSharedAccount.EMAIL)

        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT code FROM otp_code WHERE email = ?").use { statement ->
                statement.setString(1, TestSharedAccount.EMAIL)
                statement.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    assertEquals(TestSharedAccount.OTP, rs.getString("code"))
                }
            }
        }

        val (userId, token) = auth.verifyOtp(TestSharedAccount.EMAIL, TestSharedAccount.OTP)

        assertEquals(TestSharedAccount.USER_ID, userId)
        assertEquals("uid:${TestSharedAccount.USER_ID}", token)
    }

    @Test
    fun `non shared account still creates user on successful verify`() {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        val auth = AuthService(ServerDatabase(dataSource))
        val email = "person@example.com"

        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO otp_code(email, code, expires_at, created_at) VALUES (?, ?, ?, ?)").use { statement ->
                statement.setString(1, email)
                statement.setString(2, "111111")
                statement.setString(3, Instant.now().plusSeconds(600).toString())
                statement.setString(4, Instant.now().toString())
                statement.executeUpdate()
            }
        }

        val (userId, token) = auth.verifyOtp(email, "111111")

        assertTrue(userId.isNotBlank())
        assertTrue(userId != TestSharedAccount.USER_ID)
        assertEquals("uid:$userId", token)
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
                    templateDetail = "Review backlog and choose the day target",
                    recurrenceRule = "RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0",
                    defaultStartNotBefore = "2026-05-25T09:00:00Z",
                    defaultEstimatedDuration = "PT30M",
                ),
            )
        }.body<RoutineResponse>()
        assertEquals("routine-1", routine.id)
        assertEquals("Review backlog and choose the day target", routine.templateDetail)
        assertEquals("RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0", routine.recurrenceRule)
        assertEquals("2026-05-25T09:00:00Z", routine.defaultStartNotBefore)
        assertEquals("PT30M", routine.defaultEstimatedDuration)

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
            setBody(
                RoutinePatchRequest(
                    templateDetail = "Start with the most important project",
                    recurrenceRule = "RRULE:FREQ=DAILY;BYHOUR=10;BYMINUTE=30",
                    defaultStartNotBefore = "2026-05-25T10:30:00Z",
                    defaultEstimatedDuration = "PT45M",
                    isActive = false,
                ),
            )
        }.body<RoutineResponse>()
        assertEquals(false, patchedRoutine.isActive)
        assertEquals("Start with the most important project", patchedRoutine.templateDetail)
        assertEquals("RRULE:FREQ=DAILY;BYHOUR=10;BYMINUTE=30", patchedRoutine.recurrenceRule)
        assertEquals("2026-05-25T10:30:00Z", patchedRoutine.defaultStartNotBefore)
        assertEquals("PT45M", patchedRoutine.defaultEstimatedDuration)

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

        val deletedTaskEvents = jsonClient.get("/api/tasks/task-1/events") {
            auth("user-1")
        }.body<List<TaskEventResponse>>()
        assertTrue(deletedTaskEvents.any { it.eventType == TaskEventType.DELETED })

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

        val postponed = jsonClient.post("/api/tasks/task-1/events") {
            auth("user-1")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                TaskEventCreateRequest(
                    id = "event-postponed",
                    taskId = "ignored-by-route",
                    journalDate = "2026-05-24",
                    eventType = TaskEventType.POSTPONED,
                    postponed = TaskPostponedPayload(
                        previousStartNotBefore = "2026-05-24T09:00:00Z",
                        newStartNotBefore = "2026-05-25T09:00:00Z",
                        previousEndNotAfter = "2026-05-24T17:00:00Z",
                        newEndNotAfter = "2026-05-25T17:00:00Z",
                    ),
                    eventTime = "2026-05-24T09:05:00Z",
                ),
            )
        }
        assertEquals(HttpStatusCode.Created, postponed.status)

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
        assertEquals(
            "2026-05-25T09:00:00Z",
            events.single { it.id == "event-postponed" }.postponed?.newStartNotBefore,
        )
        assertEquals(5, events.size)

        val status = jsonClient.get("/api/tasks/task-1/status") {
            auth("user-1")
        }.body<TaskStatusResponse>()
        assertEquals(TaskStatus.COMPLETED, status.status)

        val journal = jsonClient.get("/api/journals/2026-05-24") {
            auth("user-1")
        }.body<List<JournalEntry>>()
        assertEquals(4, journal.size)
        assertEquals(
            listOf(TaskEventType.CREATED, TaskEventType.PROGRESSED, TaskEventType.POSTPONED, TaskEventType.CREATED),
            journal.map { it.eventType },
        )
        assertEquals("2026-05-25T09:00:00Z", journal.single { it.eventType == TaskEventType.POSTPONED }.postponed?.newStartNotBefore)
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

    private inline fun <reified T> readFixture(relativePath: String): T =
        fixtureJson.decodeFromString(resolveFixturePath(relativePath).readText())

    private fun resolveFixturePath(relativePath: String): Path =
        sequenceOf(
            Path.of(relativePath),
            Path.of("..", relativePath),
        ).firstOrNull(Path::exists)
            ?: throw IllegalArgumentException("Missing fixture file: $relativePath")

    private fun assertJsonMatchesFixture(expected: JsonElement, actual: JsonElement, path: String = "$") {
        when {
            expected is JsonObject && actual is JsonObject -> {
                assertEquals(expected.keys, actual.keys, "JSON keys differ at $path")
                expected.forEach { (key, expectedValue) ->
                    val actualValue = actual[key]
                    assertNotNull(actualValue, "Missing key $path.$key")
                    assertJsonMatchesFixture(expectedValue, actualValue, "$path.$key")
                }
            }

            expected is JsonArray && actual is JsonArray -> {
                assertEquals(expected.size, actual.size, "JSON array size differs at $path")
                expected.zip(actual).forEachIndexed { index, (expectedItem, actualItem) ->
                    assertJsonMatchesFixture(expectedItem, actualItem, "$path[$index]")
                }
            }

            expected is JsonPrimitive && actual is JsonPrimitive -> {
                when (expected.content) {
                    "__any_string__" -> assertTrue(actual.isString && actual.content.isNotBlank(), "Expected non-blank string at $path")
                    "__any_long__" -> assertTrue(!actual.isString && actual.content.toLongOrNull() != null, "Expected numeric long at $path")
                    else -> assertEquals(expected, actual, "JSON value differs at $path")
                }
            }

            else -> assertEquals(expected, actual, "JSON shape differs at $path")
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

    @Serializable
    private data class AuthContractFixture(
        @SerialName("otp_request") val otpRequest: RequestContractFixture,
        @SerialName("otp_verify") val otpVerify: ResponseContractFixture,
    )

    @Serializable
    private data class SyncTaskContractFixture(
        @SerialName("sync_tasks") val syncTasks: ResponseContractFixture,
    )

    @Serializable
    private data class BusinessApiContractFixture(
        @SerialName("create_task") val createTask: RequestContractFixture,
        @SerialName("get_task") val getTask: ResponseContractFixture,
    )

    @Serializable
    private data class RequestContractFixture(
        val request: JsonElement,
        val status: Int,
    )

    @Serializable
    private data class ResponseContractFixture(
        val request: JsonElement,
        val status: Int,
        val response: JsonElement = buildJsonObject { },
        @SerialName("accepted_fields") val acceptedFields: List<String> = emptyList(),
    )

    private companion object {
        val fixtureJson = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }
    }
}

private fun io.ktor.client.request.HttpRequestBuilder.auth(userId: String) {
    header(HttpHeaders.Authorization, "Bearer $userId")
}
