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
import dev.tireless.abun.app.JvmDatabaseDriverFactory
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
import java.io.File
import java.sql.DriverManager
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
    fun `create task persists planning window fields and detail in sync payload`() {
        val store = testStore()

        val taskId = store.createTask(
            title = "Plan sync client",
            journalDate = "2026-05-25",
            detail = "Break the migration into thin slices",
            startNotBefore = "2026-05-26T09:00:00Z",
            endNotAfter = "2026-05-27T17:00:00Z",
            estimatedDuration = "PT2H",
        )

        val dirtyTask = store.dirtyTasks().single { it.id == taskId }

        assertEquals("Break the migration into thin slices", dirtyTask.detail)
        assertEquals("2026-05-26T09:00:00Z", dirtyTask.startNotBefore)
        assertEquals("2026-05-27T17:00:00Z", dirtyTask.endNotAfter)
        assertEquals("PT2H", dirtyTask.estimatedDuration)
        assertEquals(
            setOf("title", "detail", "start_not_before", "end_not_after", "estimated_duration"),
            dirtyTask.dirtyFields.toSet(),
        )
    }

    @Test
    fun `backlog tasks stay out of day open tasks until scheduled`() {
        val store = testStore()

        val backlogId = store.createTask(
            title = "Unscheduled idea",
            journalDate = "2026-05-25",
        )
        val scheduledId = store.createTask(
            title = "Windowed work",
            journalDate = "2026-05-25",
            startNotBefore = "2026-05-26T09:00:00Z",
            endNotAfter = "2026-05-26T17:00:00Z",
            estimatedDuration = "PT4H",
        )

        val backlog = store.backlogTasks()
        val may25OpenTasks = store.openTasksForDate("2026-05-25")
        val may26OpenTasks = store.openTasksForDate("2026-05-26")

        assertEquals(listOf(backlogId), backlog.map { it.id })
        assertTrue(may25OpenTasks.none { it.id == backlogId })
        assertTrue(may26OpenTasks.none { it.id == backlogId })
        assertTrue(may25OpenTasks.none { it.id == scheduledId })
        assertEquals(listOf(scheduledId), may26OpenTasks.map { it.id })
    }

    @Test
    fun `deleted task stays visible before deletion date but not on or after deletion date`() {
        val store = testStore(
            timeProvider = object : TimeProvider {
                private var now = isoStringToEpochMillis("2026-05-25T10:00:00Z")

                override fun nowEpochMillis(): Long = now++
                override fun today(): LocalDate = LocalDate.parse("2026-05-25")
            },
        )

        val taskId = store.createTask(
            title = "Soon deleted task",
            journalDate = "2026-05-24",
            startNotBefore = "2026-05-24T09:00:00Z",
        )

        store.deleteTask(taskId)

        val beforeDeletion = store.openTasksForDate("2026-05-24")
        val onDeletionDate = store.openTasksForDate("2026-05-25")
        val afterDeletion = store.openTasksForDate("2026-05-26")

        assertEquals(listOf(taskId), beforeDeletion.map { it.id })
        assertTrue(onDeletionDate.none { it.id == taskId })
        assertTrue(afterDeletion.none { it.id == taskId })
    }

    @Test
    fun `desktop database factory migrates legacy task table before queries run`() {
        val dbFile = File.createTempFile("abun-legacy", ".db")
        dbFile.deleteOnExit()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE task (
                        id TEXT NOT NULL PRIMARY KEY,
                        parent_id TEXT,
                        routine_id TEXT,
                        title TEXT NOT NULL,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        hlc_map TEXT NOT NULL DEFAULT '{}',
                        dirty_fields TEXT NOT NULL DEFAULT '[]',
                        is_dirty INTEGER NOT NULL DEFAULT 0,
                        server_version INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE task_event (
                        id TEXT NOT NULL PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        journal_date TEXT NOT NULL,
                        event_type TEXT NOT NULL,
                        content TEXT,
                        event_time INTEGER NOT NULL,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        server_version INTEGER NOT NULL DEFAULT 0,
                        is_dirty INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE routine (
                        id TEXT NOT NULL PRIMARY KEY,
                        template_title TEXT NOT NULL,
                        cron_schedule TEXT NOT NULL,
                        timezone TEXT NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        hlc_map TEXT NOT NULL DEFAULT '{}',
                        dirty_fields TEXT NOT NULL DEFAULT '[]',
                        is_dirty INTEGER NOT NULL DEFAULT 0,
                        server_version INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE alarm (
                        id TEXT NOT NULL PRIMARY KEY,
                        task_id TEXT NOT NULL,
                        trigger_time INTEGER NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        hlc_map TEXT NOT NULL DEFAULT '{}',
                        dirty_fields TEXT NOT NULL DEFAULT '[]',
                        is_dirty INTEGER NOT NULL DEFAULT 0,
                        server_version INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE sync_state (
                        scope TEXT NOT NULL PRIMARY KEY,
                        last_server_version INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE app_preferences (
                        id INTEGER NOT NULL PRIMARY KEY,
                        title_prefix TEXT,
                        default_alarm_lead_minutes INTEGER NOT NULL,
                        default_focus_minutes INTEGER NOT NULL,
                        default_short_break_minutes INTEGER NOT NULL,
                        default_long_break_minutes INTEGER NOT NULL,
                        timezone_override TEXT NOT NULL,
                        date_format TEXT NOT NULL,
                        blank_title_policy TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE preference (
                        pref_key TEXT NOT NULL PRIMARY KEY,
                        pref_value TEXT,
                        value_type TEXT NOT NULL,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        hlc_map TEXT NOT NULL DEFAULT '{}',
                        dirty_fields TEXT NOT NULL DEFAULT '[]',
                        is_dirty INTEGER NOT NULL DEFAULT 0,
                        server_version INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE pomodoro_session (
                        id TEXT NOT NULL PRIMARY KEY,
                        task_id TEXT,
                        phase TEXT NOT NULL,
                        state TEXT NOT NULL,
                        started_at INTEGER NOT NULL,
                        ends_at INTEGER NOT NULL,
                        completed_at INTEGER,
                        duration_minutes INTEGER NOT NULL,
                        note TEXT,
                        task_update TEXT NOT NULL DEFAULT 'NONE',
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        hlc_map TEXT NOT NULL DEFAULT '{}',
                        dirty_fields TEXT NOT NULL DEFAULT '[]',
                        is_dirty INTEGER NOT NULL DEFAULT 0,
                        server_version INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    INSERT INTO task (
                        id, parent_id, routine_id, title, is_deleted, hlc_map, dirty_fields, is_dirty, server_version, created_at, updated_at
                    ) VALUES (
                        'task-1', NULL, NULL, 'Legacy task', 0, '{}', '[]', 0, 0, 1, 1
                    )
                    """.trimIndent(),
                )
                statement.executeUpdate(
                    """
                    INSERT INTO task_event (
                        id, task_id, journal_date, event_type, content, event_time, is_deleted, server_version, is_dirty, created_at
                    ) VALUES (
                        'event-1', 'task-1', '2026-05-25', 'CREATED', NULL, 1, 0, 0, 0, 1
                    )
                    """.trimIndent(),
                )
            }
        }

        val store = LocalStore(
            database = createDatabase(object : DatabaseDriverFactory {
                override fun createDriver(): SqlDriver = JvmDatabaseDriverFactory("jdbc:sqlite:${dbFile.absolutePath}").createDriver()
            }),
            timeProvider = testTimeProvider(),
            idGenerator = testIdGenerator(),
            clock = createHybridClock(object : DeviceNodeIdProvider {
                override fun nodeId(): String = "test-device"
            }, testTimeProvider()),
        )

        val tasks = store.allTasks()

        assertEquals(listOf("Legacy task"), tasks.map { it.title })
    }

    @Test
    fun `day view includes every open task for selected date`() {
        val today = deriveTodayViewState(
            openTasks = listOf(
                TaskListItemView(id = "task-1", title = "Overdue", status = TaskStatus.PENDING),
                TaskListItemView(id = "task-2", title = "Ready now", status = TaskStatus.IN_PROGRESS),
            ),
            journalEntries = emptyList(),
        )

        assertEquals(listOf("task-1", "task-2"), today.currentTasks.map { it.taskId })
        assertTrue(today.upcomingTasks.isEmpty())
    }

    @Test
    fun `day view preserves journal entries`() {
        val journal = listOf(
            JournalEntryView(
                taskId = "task-1",
                title = "Deep work",
                eventId = "event-1",
                eventType = TaskEventType.CREATED,
                content = null,
                eventTimeLabel = "May 25 09:00",
            ),
        )

        val today = deriveTodayViewState(
            openTasks = emptyList(),
            journalEntries = journal,
        )

        assertEquals(journal, today.journalEntries)
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

    private fun testStore(
        timeProvider: TimeProvider = testTimeProvider(),
        idGenerator: IdGenerator = testIdGenerator(),
    ): LocalStore {
        val driverFactory = object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
                AbunDatabase.Schema.create(it)
            }
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

    private fun testTimeProvider(): TimeProvider = object : TimeProvider {
        private var now = 1_748_120_000_000L

        override fun nowEpochMillis(): Long = now++
        override fun today(): LocalDate = LocalDate.parse("2026-05-25")
    }

    private fun testIdGenerator(): IdGenerator = object : IdGenerator {
        private var index = 0

        override fun randomId(): String {
            index += 1
            return "id-$index"
        }

        override fun deterministicId(namespace: String, seed: String): String = "$namespace:$seed"
    }
}
