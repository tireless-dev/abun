package dev.tireless.abun

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.tireless.abun.app.AlarmListItemView
import dev.tireless.abun.app.AppJson
import dev.tireless.abun.app.AuthProvider
import dev.tireless.abun.app.DatabaseDriverFactory
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.DeviceNodeIdProvider
import dev.tireless.abun.app.IdGenerator
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.LocalStore
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.SyncEngine
import dev.tireless.abun.app.SyncRemoteApi
import dev.tireless.abun.app.SyncScope
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.TimeProvider
import dev.tireless.abun.app.createDatabase
import dev.tireless.abun.app.createHybridClock
import dev.tireless.abun.app.deriveTodayViewState
import dev.tireless.abun.app.isoStringToEpochMillis
import dev.tireless.abun.db.AbunDatabase
import dev.tireless.abun.sync.BatchRequest
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncPreference
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
import kotlin.test.assertFalse
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
    fun `today agenda buckets overdue and upcoming alarms and excludes completed tasks`() {
        val tasks = listOf(
            TaskListItemView(id = "task-1", title = "Overdue", status = TaskStatus.PENDING),
            TaskListItemView(id = "task-2", title = "Upcoming", status = TaskStatus.IN_PROGRESS),
            TaskListItemView(id = "task-3", title = "Done", status = TaskStatus.COMPLETED),
        )
        val alarms = listOf(
            AlarmListItemView(
                id = "alarm-1",
                taskId = "task-1",
                taskTitle = "Overdue",
                triggerTimeLabel = "2026-05-25 09:00",
                triggerTimeIso = "2026-05-25T09:00:00Z",
                isActive = true,
            ),
            AlarmListItemView(
                id = "alarm-2",
                taskId = "task-2",
                taskTitle = "Upcoming",
                triggerTimeLabel = "2026-05-25 18:00",
                triggerTimeIso = "2026-05-25T18:00:00Z",
                isActive = true,
            ),
            AlarmListItemView(
                id = "alarm-3",
                taskId = "task-3",
                taskTitle = "Done",
                triggerTimeLabel = "2026-05-25 08:00",
                triggerTimeIso = "2026-05-25T08:00:00Z",
                isActive = true,
            ),
        )

        val today = deriveTodayViewState(
            tasks = tasks,
            alarms = alarms,
            journalEntries = emptyList(),
            nowEpochMillis = isoStringToEpochMillis("2026-05-25T12:00:00Z"),
        )

        assertEquals(listOf("task-1"), today.currentTasks.map { it.taskId })
        assertEquals(listOf("task-2"), today.upcomingTasks.map { it.taskId })
    }

    @Test
    fun `today agenda falls back to in progress tasks without alarms`() {
        val today = deriveTodayViewState(
            tasks = listOf(
                TaskListItemView(id = "task-1", title = "Deep work", status = TaskStatus.IN_PROGRESS),
                TaskListItemView(id = "task-2", title = "Later", status = TaskStatus.PENDING),
            ),
            alarms = emptyList(),
            journalEntries = emptyList(),
            nowEpochMillis = isoStringToEpochMillis("2026-05-25T12:00:00Z"),
        )

        assertEquals(listOf("task-1"), today.currentTasks.map { it.taskId })
        assertTrue(today.upcomingTasks.isEmpty())
    }

    @Test
    fun `routine and alarm CRUD persist and hide deleted rows`() {
        val store = testStore()
        val date = "2026-05-25"
        val taskId = store.createTask("Prepare notes", date)

        val routineId = store.createRoutine("Stretch", "0 9 * * *", "UTC")
        store.updateRoutine(routineId, "Stretch break", "0 10 * * *", "Asia/Shanghai")
        store.toggleRoutineActive(routineId)

        val alarmId = store.createAlarm(taskId, "2026-05-25T10:00:00Z")
        store.updateAlarm(alarmId, "2026-05-25T11:00:00Z")
        store.toggleAlarmActive(alarmId)

        val routines = store.routines()
        val alarms = store.alarms()

        assertEquals(1, routines.size)
        assertEquals("Stretch break", routines.single().templateTitle)
        assertFalse(routines.single().isActive)
        assertEquals(1, alarms.size)
        assertEquals("2026-05-25T11:00:00Z", alarms.single().triggerTimeIso)
        assertFalse(alarms.single().isActive)

        store.deleteRoutine(routineId)
        store.deleteAlarm(alarmId)

        assertTrue(store.routines().isEmpty())
        assertTrue(store.alarms().isEmpty())
    }

    @Test
    fun `preferences persist and pomodoro session uses updated defaults`() {
        val store = testStore()

        store.updatePreferences(
            titlePrefix = "[focus]",
            defaultAlarmLeadMinutes = 20,
            focusMinutes = 30,
            shortBreakMinutes = 7,
            longBreakMinutes = 18,
            timezoneOverride = "UTC",
            dateFormat = DateFormatPreference.MONTH_DAY,
        )

        val preferences = store.preferences()
        val session = store.startPomodoroSession(taskId = null, phase = PomodoroPhase.FOCUS, preferences = preferences)

        assertEquals("[focus]", preferences.titlePrefix)
        assertEquals(20, preferences.defaultAlarmLeadMinutes)
        assertEquals(DateFormatPreference.MONTH_DAY, preferences.dateFormat)
        assertEquals(30, session.durationMinutes)
        assertTrue(store.activePomodoroSession(preferences) != null)
        assertEquals(
            setOf(
                "task.title_prefix",
                "task.default_alarm_lead_minutes",
                "pomodoro.focus_minutes",
                "pomodoro.short_break_minutes",
                "pomodoro.long_break_minutes",
                "app.timezone_override",
                "app.date_format",
                "task.blank_title_policy",
            ),
            store.dirtyPreferences().map { it.key }.toSet(),
        )
    }

    @Test
    fun `pomodoro completion stores note and task update`() {
        val store = testStore()
        val taskId = store.createTask("Focus task", "2026-05-25")
        val session = store.startPomodoroSession(taskId, PomodoroPhase.FOCUS, store.preferences())

        store.completePomodoroSession(session.id, "Deep work finished", PomodoroTaskUpdate.PROGRESS, "2026-05-25")

        val active = store.activePomodoroSession()
        val recent = store.recentPomodoroSessions(limit = 1)
        val journal = store.journal("2026-05-25")

        assertEquals(null, active)
        assertEquals("Deep work finished", recent.single().note)
        assertEquals(PomodoroTaskUpdate.PROGRESS, recent.single().taskUpdate)
        assertTrue(journal.any { it.eventType == TaskEventType.PROGRESSED && it.content == "Deep work finished" })
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
                    "/sync/preferences" -> if (request.method.value == "GET") {
                        AppJson.encodeToString(PullResponse(items = emptyList<SyncPreference>(), nextCursor = 0, hasMore = false))
                    } else {
                        AppJson.encodeToString(BatchRequest(items = emptyList<SyncPreference>()))
                    }
                    "/sync/routines" -> if (request.method.value == "GET") {
                        AppJson.encodeToString(PullResponse(items = emptyList<String>(), nextCursor = 0, hasMore = false))
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
                    "/sync/pomodoro-sessions" -> if (request.method.value == "GET") {
                        AppJson.encodeToString(PullResponse(items = emptyList<dev.tireless.abun.sync.SyncPomodoroSession>(), nextCursor = 0, hasMore = false))
                    } else {
                        AppJson.encodeToString(BatchRequest(items = emptyList<dev.tireless.abun.sync.SyncPomodoroSession>()))
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
                "GET /sync/preferences",
                "GET /sync/routines",
                "GET /sync/tasks",
                "GET /sync/alarms",
                "GET /sync/task-events",
                "GET /sync/pomodoro-sessions",
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
