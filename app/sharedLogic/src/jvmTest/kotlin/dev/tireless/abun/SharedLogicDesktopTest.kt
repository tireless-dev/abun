package dev.tireless.abun

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.tireless.abun.app.AppJson
import dev.tireless.abun.app.AuthProvider
import dev.tireless.abun.app.DatabaseDriverFactory
import dev.tireless.abun.app.DeviceNodeIdProvider
import dev.tireless.abun.app.IdGenerator
import dev.tireless.abun.app.LocalStore
import dev.tireless.abun.app.SyncEngine
import dev.tireless.abun.app.SyncRemoteApi
import dev.tireless.abun.app.SyncScope
import dev.tireless.abun.app.TimeProvider
import dev.tireless.abun.app.createDatabase
import dev.tireless.abun.app.createHybridClock
import dev.tireless.abun.db.AbunDatabase
import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedLogicDesktopTest {
    @Test
    fun `create and complete task keeps append-only journal`() {
        val store = testStore()
        val date = "2026-05-25"

        val taskId = store.createTask("Plan sync client", date)
        store.progressTask(taskId, date, "Started wiring persistence")
        store.completeTask(taskId, date, "Finished first pass")

        val tasks = store.allTasks()
        val journal = store.journal(date)

        assertEquals(1, tasks.size)
        assertEquals(TaskStatus.COMPLETED, tasks.single().status)
        assertEquals(listOf(TaskEventType.CREATED, TaskEventType.PROGRESSED, TaskEventType.COMPLETED), journal.map { it.eventType })
    }

    @Test
    fun `sync engine pulls before push and clears accepted dirty fields`() = runTest {
        val store = testStore()
        val date = "2026-05-25"
        store.createTask("Offline task", date)

        val taskPushResponse = store.dirtyTasks().map {
            it.copy(
                acceptedFields = it.dirtyFields,
                rejectedFields = emptyList(),
                serverVersion = 1,
                serverUpdatedAt = "2026-05-25T10:00:00Z",
                createdAt = "2026-05-25T09:00:00Z",
            )
        }
        val taskEventPushResponse = store.dirtyTaskEvents().map {
            it.copy(
                accepted = true,
                serverVersion = 2,
                serverUpdatedAt = "2026-05-25T10:00:00Z",
                createdAt = "2026-05-25T09:00:00Z",
            )
        }
        val requests = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                requests += "${request.method.value} ${request.url.encodedPath}"
                val body = when (request.url.encodedPath) {
                    "/sync/routines" -> if (request.method.value == "GET") {
                        AppJson.encodeToString(PullResponse(items = emptyList<kotlin.String>(), nextCursor = 0, hasMore = false))
                    } else {
                        AppJson.encodeToString(BatchRequest(items = emptyList<String>()))
                    }
                    "/sync/tasks" -> if (request.method.value == "GET") {
                        AppJson.encodeToString(PullResponse(items = emptyList<dev.tireless.abun.sync.SyncTask>(), nextCursor = 0, hasMore = false))
                    } else {
                        AppJson.encodeToString(BatchRequest(taskPushResponse))
                    }
                    "/sync/alarms" -> if (request.method.value == "GET") {
                        AppJson.encodeToString(PullResponse(items = emptyList<dev.tireless.abun.sync.SyncAlarm>(), nextCursor = 0, hasMore = false))
                    } else {
                        AppJson.encodeToString(BatchRequest(items = emptyList<dev.tireless.abun.sync.SyncAlarm>()))
                    }
                    "/sync/task-events" -> if (request.method.value == "GET") {
                        AppJson.encodeToString(PullResponse(items = emptyList<dev.tireless.abun.sync.SyncTaskEvent>(), nextCursor = 0, hasMore = false))
                    } else {
                        AppJson.encodeToString(BatchRequest(taskEventPushResponse))
                    }
                    else -> error("Unexpected path ${request.url.encodedPath}")
                }
                respond(
                    content = body,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) { json(AppJson) }
        }

        SyncEngine(
            localStore = store,
            remoteApi = SyncRemoteApi("http://localhost:8080", client, object : AuthProvider {
                override suspend fun bearerToken(): String = "demo-user"
            }),
        ).syncNow()

        assertTrue(store.dirtyTasks().isEmpty())
        assertTrue(store.dirtyTaskEvents().isEmpty())
        assertEquals(
            listOf(
                "GET /sync/routines",
                "GET /sync/tasks",
                "GET /sync/alarms",
                "GET /sync/task-events",
                "POST /sync/tasks",
                "POST /sync/task-events",
            ),
            requests,
        )
        assertEquals(0L, store.syncCursor(SyncScope.TASKS))
    }

    private fun testStore(): LocalStore {
        val driverFactory = object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
                AbunDatabase.Schema.create(it)
            }
        }
        val timeProvider = object : TimeProvider {
            private var now = 1_748_120_000_000L

            override fun nowEpochMillis(): Long = now++
            override fun today(): LocalDate = LocalDate.parse("2026-05-25")
        }
        val idGenerator = object : IdGenerator {
            private var index = 0

            override fun randomId(): String {
                index += 1
                return "id-$index"
            }

            override fun deterministicId(namespace: String, seed: String): String = "$namespace:$seed"
        }
        return LocalStore(
            database = createDatabase(driverFactory),
            timeProvider = timeProvider,
            idGenerator = idGenerator,
            clock = createHybridClock(object : DeviceNodeIdProvider {
                override fun nodeId(): String = "test-device"
            }, timeProvider),
        )
    }
}
