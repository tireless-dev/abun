package dev.tireless.abun.app

import dev.tireless.abun.db.AbunDatabase
import dev.tireless.abun.sync.PomodoroPhaseWire
import dev.tireless.abun.sync.PomodoroSessionStateWire
import dev.tireless.abun.sync.PomodoroTaskUpdateWire
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncConflictResolver
import dev.tireless.abun.sync.SyncPreference
import dev.tireless.abun.sync.SyncPomodoroSession
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskPostponedPayload
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.sync.PreferenceValueType
import kotlinx.datetime.*

internal data class MutableSyncRow<T>(
    val entity: T,
    val hlcMap: Map<String, String>,
    val dirtyFields: List<String>,
)

private const val PREF_TASK_TITLE_PREFIX = "task.title_prefix"
private const val PREF_TASK_DEFAULT_ALARM_LEAD_MINUTES = "task.default_alarm_lead_minutes"
private const val PREF_TASK_BLANK_TITLE_POLICY = "task.blank_title_policy"
private const val PREF_POMODORO_FOCUS_MINUTES = "pomodoro.focus_minutes"
private const val PREF_POMODORO_SHORT_BREAK_MINUTES = "pomodoro.short_break_minutes"
private const val PREF_POMODORO_LONG_BREAK_MINUTES = "pomodoro.long_break_minutes"
private const val PREF_APP_TIMEZONE_OVERRIDE = "app.timezone_override"
private const val PREF_APP_DATE_FORMAT = "app.date_format"
val PREF_APP_ROLLOVER_TIME = "app.rollover_time"

class LocalStore(
    private val database: AbunDatabase,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val clock: dev.tireless.abun.sync.HybridLogicalClock,
) {
    private val queries = database.abunDatabaseQueries

    init {
        SyncScope.entries.forEach { queries.upsertSyncState(it.wireName, 0) }
        if (queries.selectAllPreferences(::mapPreferenceRow).executeAsList().isEmpty()) {
            migrateLegacyPreferences()
        }
    }

    fun allTasks(selectedDate: String? = null): List<TaskListItemView> =
        queries.selectTasks(::mapTaskRow).executeAsList().map { toTaskListItemView(it, selectedDate) }

    fun backlogTasks(): List<TaskListItemView> =
        queries.selectTasks(::mapTaskRow).executeAsList()
            .map(::toTaskListItemView)
            .filter(::isBacklogTask)

    fun openTasksForDate(selectedDate: String): List<TaskListItemView> =
        queries.selectAllTasks(::mapTaskRow).executeAsList()
            .map { row -> row to toTaskListItemView(row, selectedDate) }
            .filter { (_, task) -> task.status != TaskStatus.COMPLETED && task.status != TaskStatus.CANCELLED }
            .filterNot { (_, task) -> isBacklogTask(task) }
            .filter { (row, task) -> isTaskVisibleOnSelectedDate(row, LocalDate.parse(selectedDate), task) }
            .map { (_, task) -> task }

    fun routines(): List<RoutineListItemView> = queries.selectActiveRoutines(::mapRoutineRow).executeAsList().map {
        RoutineListItemView(
            id = it.entity.id,
            templateTitle = it.entity.templateTitle,
            templateDetail = it.entity.templateDetail,
            recurrenceRule = it.entity.recurrenceRule,
            defaultStartNotBefore = it.entity.defaultStartNotBefore,
            defaultEstimatedDuration = it.entity.defaultEstimatedDuration,
            isActive = it.entity.isActive,
        )
    }

    fun alarms(preferences: PreferencesViewState = preferences()): List<AlarmListItemView> {
        val taskTitles = allTasks().associateBy(TaskListItemView::id)
        return queries.selectActiveAlarms(::mapAlarmRow).executeAsList().mapNotNull { row ->
            val task = taskTitles[row.entity.taskId] ?: return@mapNotNull null
            AlarmListItemView(
                id = row.entity.id,
                taskId = row.entity.taskId,
                taskTitle = task.title,
                triggerTimeLabel = formatDateTimeLabel(row.entity.triggerTime, preferences.timezoneOverride, preferences.dateFormat),
                triggerTimeIso = epochMillisToIsoString(row.entity.triggerTime),
                isActive = row.entity.isActive,
            )
        }
    }

    fun journal(date: String, preferences: PreferencesViewState = preferences()): List<JournalEntryView> = queries.selectJournalEntries(
        date,
        ::mapJournalRow,
    ).executeAsList().mapNotNull { it.toJournalEntry(preferences) }

    fun taskHistory(taskId: String, preferences: PreferencesViewState = preferences()): List<JournalEntryView> {
        val task = queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull()?.entity ?: return emptyList()
        return queries.selectTaskEventsForTask(taskId, ::mapTaskEventRow).executeAsList().mapNotNull { row ->
            row.entity.toJournalEntry(task.id, task.title, preferences)
        }
    }

    fun preferences(): PreferencesViewState =
        queries.selectAllPreferences(::mapPreferenceRow).executeAsList().toPreferencesViewState()

    fun updatePreferences(
        titlePrefix: String,
        defaultAlarmLeadMinutes: Int,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        timezoneOverride: String,
        dateFormat: DateFormatPreference,
        rolloverTime: String,
    ) {
        persistPreferences(
            PreferencesViewState(
                titlePrefix = titlePrefix.trim(),
                defaultAlarmLeadMinutes = defaultAlarmLeadMinutes.coerceIn(0, 24 * 60),
                focusMinutes = focusMinutes.coerceIn(1, 180),
                shortBreakMinutes = shortBreakMinutes.coerceIn(1, 60),
                longBreakMinutes = longBreakMinutes.coerceIn(1, 120),
                timezoneOverride = timezoneOverride.ifBlank { "SYSTEM" },
                dateFormat = dateFormat,
                blankTitlePolicy = BlankTitlePolicy.REJECT_BLANK,
                rolloverTime = rolloverTime,
            ),
        )
    }

    fun activePomodoroSession(
        preferences: PreferencesViewState = preferences(),
        nowEpochMillis: Long = timeProvider.nowEpochMillis(),
    ): PomodoroSessionView? {
        val tasksById = allTasks().associateBy(TaskListItemView::id)
        return queries.selectActivePomodoroSession(::mapPomodoroSessionRow).executeAsOneOrNull()?.let {
            toPomodoroSessionView(it, tasksById, nowEpochMillis)
        }
    }

    fun recentPomodoroSessions(
        limit: Int = 8,
        preferences: PreferencesViewState = preferences(),
        nowEpochMillis: Long = timeProvider.nowEpochMillis(),
    ): List<PomodoroSessionView> {
        val tasksById = allTasks().associateBy(TaskListItemView::id)
        return queries.selectRecentPomodoroSessions(limit.toLong(), ::mapPomodoroSessionRow).executeAsList().map {
            toPomodoroSessionView(it, tasksById, nowEpochMillis)
        }
    }

    fun pomodoroStartableTasks(currentDate: String = timeProvider.today().toString()): List<TaskListItemView> =
        openTasksForDate(currentDate)

    fun startPomodoroSession(
        taskId: String?,
        phase: PomodoroPhase,
        preferences: PreferencesViewState = preferences(),
    ): PomodoroSessionView? {
        val existing = activePomodoroSession(preferences, timeProvider.nowEpochMillis())
        if (existing != null) return existing
        val currentDate = timeProvider.today().toString()
        if (taskId != null && !isValidPomodoroTask(taskId, currentDate)) return null
        val now = timeProvider.nowEpochMillis()
        val durationMinutes = durationForPhase(phase, preferences)
        val row = MutableSyncRow(
            entity = LocalPomodoroSession(
                id = idGenerator.randomId(),
                taskId = taskId,
                phase = phase,
                state = PomodoroSessionState.ACTIVE,
                startedAt = now,
                endsAt = now + durationMinutes * MILLIS_PER_MINUTE,
                completedAt = null,
                durationMinutes = durationMinutes,
                note = null,
                taskUpdate = PomodoroTaskUpdate.NONE,
                isDeleted = false,
                serverVersion = 0,
                createdAt = now,
                updatedAt = now,
            ),
            hlcMap = mapOf(
                "task" to clock.next(),
                "timing" to clock.next(),
                "state" to clock.next(),
            ),
            dirtyFields = listOf("task", "timing", "state"),
        )
        persistPomodoroSession(row.entity, row.hlcMap, row.dirtyFields, isDirty = true)
        val tasksById = allTasks().associateBy(TaskListItemView::id)
        return toPomodoroSessionView(row, tasksById, now)
    }

    fun completePomodoroSession(
        sessionId: String,
        note: String?,
        taskUpdate: PomodoroTaskUpdate,
        journalDate: String,
    ) = database.transaction {
        val existing = queries.selectPomodoroSessionById(sessionId, ::mapPomodoroSessionRow).executeAsOneOrNull() ?: return@transaction
        val now = timeProvider.nowEpochMillis()
        val updated = existing.entity.copy(
            state = PomodoroSessionState.COMPLETED,
            completedAt = now,
            note = note?.trim()?.ifBlank { null },
            taskUpdate = taskUpdate,
            updatedAt = now,
        )
        val updatedHlc = existing.hlcMap + mapOf(
            "state" to clock.next(existing.hlcMap["state"]),
            "note" to clock.next(existing.hlcMap["note"]),
            "outcome" to clock.next(existing.hlcMap["outcome"]),
        )
        val dirty = (existing.dirtyFields + listOf("state", "note", "outcome")).distinct()
        persistPomodoroSession(updated, updatedHlc, dirty, isDirty = true)
        existing.entity.taskId?.let { taskId ->
            if (isValidPomodoroTask(taskId, timeProvider.today().toString())) {
                when (taskUpdate) {
                    PomodoroTaskUpdate.NONE -> Unit
                    PomodoroTaskUpdate.PROGRESS -> appendTaskEvent(taskId, journalDate, TaskEventType.PROGRESSED, note)
                    PomodoroTaskUpdate.COMPLETE -> appendTaskEvent(taskId, journalDate, TaskEventType.COMPLETED, note)
                    PomodoroTaskUpdate.CANCEL -> appendTaskEvent(taskId, journalDate, TaskEventType.CANCELLED, note)
                }
            }
        }
    }

    fun cancelPomodoroSession(sessionId: String, note: String?) = database.transaction {
        val existing = queries.selectPomodoroSessionById(sessionId, ::mapPomodoroSessionRow).executeAsOneOrNull() ?: return@transaction
        val now = timeProvider.nowEpochMillis()
        val updated = existing.entity.copy(
            state = PomodoroSessionState.CANCELLED,
            completedAt = now,
            note = note?.trim()?.ifBlank { null },
            updatedAt = now,
        )
        val updatedHlc = existing.hlcMap + mapOf(
            "state" to clock.next(existing.hlcMap["state"]),
            "note" to clock.next(existing.hlcMap["note"]),
        )
        val dirty = (existing.dirtyFields + listOf("state", "note")).distinct()
        persistPomodoroSession(updated, updatedHlc, dirty, isDirty = true)
    }

    fun createTask(
        title: String,
        journalDate: String,
        parentId: String? = null,
        routineId: String? = null,
        detail: String? = null,
        startNotBefore: String? = null,
        endNotAfter: String? = null,
        estimatedDuration: String? = null,
    ): String =
        database.transactionWithResult {
            val now = timeProvider.nowEpochMillis()
            val taskId = idGenerator.randomId()
            val titleHlc = clock.next()
            val parentHlc = parentId?.let { clock.next(titleHlc) }
            val routineHlc = routineId?.let { clock.next(titleHlc) }
            val detailHlc = detail?.let { clock.next(routineHlc ?: parentHlc ?: titleHlc) }
            val startHlc = startNotBefore?.let { clock.next(detailHlc ?: routineHlc ?: parentHlc ?: titleHlc) }
            val endHlc = endNotAfter?.let { clock.next(startHlc ?: detailHlc ?: routineHlc ?: parentHlc ?: titleHlc) }
            val durationHlc = estimatedDuration?.let { clock.next(endHlc ?: startHlc ?: detailHlc ?: routineHlc ?: parentHlc ?: titleHlc) }
            val dirty = buildList {
                add("title")
                if (parentId != null) add("parent")
                if (routineId != null) add("routine")
                if (detail != null) add("detail")
                if (startNotBefore != null) add("start_not_before")
                if (endNotAfter != null) add("end_not_after")
                if (estimatedDuration != null) add("estimated_duration")
            }

            queries.upsertTask(
                id = taskId,
                parent_id = parentId,
                routine_id = routineId,
                title = title,
                detail = detail,
                start_not_before = startNotBefore,
                end_not_after = endNotAfter,
                estimated_duration = estimatedDuration,
                is_deleted = 0,
                hlc_map = JsonCodecs.encodeMap(
                    buildMap {
                        put("title", titleHlc)
                        parentHlc?.let { put("parent", it) }
                        routineHlc?.let { put("routine", it) }
                        detailHlc?.let { put("detail", it) }
                        startHlc?.let { put("start_not_before", it) }
                        endHlc?.let { put("end_not_after", it) }
                        durationHlc?.let { put("estimated_duration", it) }
                    },
                ),
                dirty_fields = JsonCodecs.encodeList(dirty),
                is_dirty = 1,
                server_version = 0,
                created_at = now,
                updated_at = now,
            )

            queries.upsertTaskEvent(
                id = idGenerator.randomId(),
                task_id = taskId,
                journal_date = journalDate,
                event_type = TaskEventType.CREATED.name,
                content = null,
                postponed_json = null,
                event_time = now,
                is_deleted = 0,
                server_version = 0,
                is_dirty = 1,
                created_at = now,
            )

            taskId
        }

    fun progressTask(taskId: String, journalDate: String, note: String? = null) {
        appendTaskEvent(taskId, journalDate, TaskEventType.PROGRESSED, note)
    }

    fun completeTask(taskId: String, journalDate: String, note: String? = null) {
        appendTaskEvent(taskId, journalDate, TaskEventType.COMPLETED, note)
    }

    fun postponeTask(
        taskId: String,
        journalDate: String,
        startNotBefore: String? = null,
        endNotAfter: String? = null,
        estimatedDuration: String? = null,
        note: String? = null,
    ) = database.transaction {
        val existing = queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() ?: return@transaction
        val normalizedStart = startNotBefore?.trim().orEmpty().ifBlank { null }
        val normalizedEnd = endNotAfter?.trim().orEmpty().ifBlank { null }
        val normalizedDuration = estimatedDuration?.trim().orEmpty().ifBlank { null }
        val now = timeProvider.nowEpochMillis()

        if (!canApplyRoutineOccurrenceEvent(existing.entity, TaskEventType.POSTPONED, now)) return@transaction

        if (existing.entity.routineId != null) {
            val routine = queries.selectRoutineById(existing.entity.routineId, ::mapRoutineRow).executeAsOneOrNull()?.entity
            if (routine != null) {
                val structured = StructuredRecurrence.fromRRule(routine.recurrenceRule)
                val baseTime = if (existing.entity.startNotBefore != null) {
                    try { Instant.parse(existing.entity.startNotBefore).toLocalDateTime(TimeZone.currentSystemDefault()) }
                    catch (e: Exception) { Instant.fromEpochMilliseconds(existing.entity.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()) }
                } else {
                    Instant.fromEpochMilliseconds(existing.entity.createdAt).toLocalDateTime(TimeZone.currentSystemDefault())
                }
                val nextOccurrence = structured.nextOccurrence(baseTime)
                val nextOccurrenceInstant = nextOccurrence.toInstant(TimeZone.currentSystemDefault())

                if (normalizedStart != null) {
                    val newStartInstant = Instant.parse(normalizedStart)
                    if (newStartInstant >= nextOccurrenceInstant) return@transaction
                }
            }
        }

        if (
            existing.entity.startNotBefore == normalizedStart &&
            existing.entity.endNotAfter == normalizedEnd &&
            existing.entity.estimatedDuration == normalizedDuration
        ) return@transaction

        val updatedTask = existing.entity.copy(
            startNotBefore = normalizedStart,
            endNotAfter = normalizedEnd,
            estimatedDuration = normalizedDuration,
        )
        val updatedHlc = existing.hlcMap.toMutableMap()
        val dirty = existing.dirtyFields.toMutableSet()
        if (existing.entity.startNotBefore != normalizedStart) {
            updatedHlc["start_not_before"] = clock.next(existing.hlcMap["start_not_before"])
            dirty += "start_not_before"
        }
        if (existing.entity.endNotAfter != normalizedEnd) {
            updatedHlc["end_not_after"] = clock.next(existing.hlcMap["end_not_after"])
            dirty += "end_not_after"
        }
        if (existing.entity.estimatedDuration != normalizedDuration) {
            updatedHlc["estimated_duration"] = clock.next(existing.hlcMap["estimated_duration"])
            dirty += "estimated_duration"
        }
        persistTask(
            task = updatedTask,
            hlcMap = updatedHlc,
            dirtyFields = dirty.toList(),
            isDirty = true,
            createdAt = existing.entity.createdAt,
            updatedAt = now,
        )
        queries.upsertTaskEvent(
            id = idGenerator.randomId(),
            task_id = taskId,
            journal_date = journalDate,
            event_type = TaskEventType.POSTPONED.name,
            content = note,
            postponed_json = JsonCodecs.encodePostponedPayload(
                TaskPostponedPayload(
                    previousStartNotBefore = existing.entity.startNotBefore,
                    newStartNotBefore = normalizedStart,
                    previousEndNotAfter = existing.entity.endNotAfter,
                    newEndNotAfter = normalizedEnd,
                ),
            ),
            event_time = now,
            is_deleted = 0,
            server_version = 0,
            is_dirty = 1,
            created_at = now,
        )
    }

    fun updateTask(
        taskId: String,
        title: String,
        detail: String? = null,
        parentId: String? = null,
        startNotBefore: String? = null,
        endNotAfter: String? = null,
        estimatedDuration: String? = null,
    ) = database.transaction {
        val existing = queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() ?: return@transaction
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) return@transaction
        val normalizedDetail = detail?.trim().orEmpty().ifBlank { null }
        val normalizedParentId = parentId?.trim().orEmpty().ifBlank { null }?.takeUnless { it == taskId }
        val normalizedStart = startNotBefore?.trim().orEmpty().ifBlank { null }
        val normalizedEnd = endNotAfter?.trim().orEmpty().ifBlank { null }
        val normalizedDuration = estimatedDuration?.trim().orEmpty().ifBlank { null }
        val updatedTask = existing.entity.copy(
            title = normalizedTitle,
            detail = normalizedDetail,
            parentId = normalizedParentId,
            startNotBefore = normalizedStart,
            endNotAfter = normalizedEnd,
            estimatedDuration = normalizedDuration,
        )
        if (updatedTask == existing.entity) return@transaction

        val updatedHlc = existing.hlcMap.toMutableMap()
        val dirty = existing.dirtyFields.toMutableSet()
        if (existing.entity.title != normalizedTitle) {
            updatedHlc["title"] = clock.next(existing.hlcMap["title"])
            dirty += "title"
        }
        if (existing.entity.detail != normalizedDetail) {
            updatedHlc["detail"] = clock.next(existing.hlcMap["detail"])
            dirty += "detail"
        }
        if (existing.entity.parentId != normalizedParentId) {
            updatedHlc["parent"] = clock.next(existing.hlcMap["parent"])
            dirty += "parent"
        }
        if (existing.entity.startNotBefore != normalizedStart) {
            updatedHlc["start_not_before"] = clock.next(existing.hlcMap["start_not_before"])
            dirty += "start_not_before"
        }
        if (existing.entity.endNotAfter != normalizedEnd) {
            updatedHlc["end_not_after"] = clock.next(existing.hlcMap["end_not_after"])
            dirty += "end_not_after"
        }
        if (existing.entity.estimatedDuration != normalizedDuration) {
            updatedHlc["estimated_duration"] = clock.next(existing.hlcMap["estimated_duration"])
            dirty += "estimated_duration"
        }

        persistTask(
            task = updatedTask,
            hlcMap = updatedHlc,
            dirtyFields = dirty.toList(),
            isDirty = true,
            createdAt = existing.entity.createdAt,
            updatedAt = timeProvider.nowEpochMillis(),
        )
    }

    fun cancelTask(taskId: String, journalDate: String, note: String? = null) {
        appendTaskEvent(taskId, journalDate, TaskEventType.CANCELLED, note)
    }

    fun skipTask(taskId: String, journalDate: String, note: String? = null) {
        appendTaskEvent(taskId, journalDate, TaskEventType.SKIPPED, note)
    }

    fun deleteTask(taskId: String, journalDate: String) = database.transaction {
        val existing = queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() ?: return@transaction
        val now = timeProvider.nowEpochMillis()
        val deleteHlc = clock.next(existing.hlcMap["delete"])
        val dirty = (existing.dirtyFields + "delete").distinct()
        persistTask(
            task = existing.entity.copy(isDeleted = true),
            hlcMap = existing.hlcMap + ("delete" to deleteHlc),
            dirtyFields = dirty,
            isDirty = true,
            createdAt = existing.entity.createdAt,
            updatedAt = now,
        )
        queries.upsertTaskEvent(
            id = idGenerator.randomId(),
            task_id = taskId,
            journal_date = journalDate,
            event_type = TaskEventType.DELETED.name,
            content = null,
            postponed_json = null,
            event_time = now,
            is_deleted = 0,
            server_version = 0,
            is_dirty = 1,
            created_at = now,
        )
    }

    fun createRoutine(
        templateTitle: String,
        templateDetail: String?,
        recurrenceRule: String,
        defaultStartNotBefore: String?,
        defaultEstimatedDuration: String?,
    ): String = database.transactionWithResult {
        val now = timeProvider.nowEpochMillis()
        val routineId = idGenerator.randomId()
        persistRoutine(
            routine = LocalRoutine(
                id = routineId,
                templateTitle = templateTitle.trim(),
                templateDetail = templateDetail?.trim()?.takeIf(String::isNotEmpty),
                recurrenceRule = recurrenceRule.trim(),
                defaultStartNotBefore = defaultStartNotBefore?.trim()?.takeIf(String::isNotEmpty),
                defaultEstimatedDuration = defaultEstimatedDuration?.trim()?.takeIf(String::isNotEmpty),
                isActive = true,
                isDeleted = false,
                serverVersion = 0,
                createdAt = now,
                updatedAt = now,
            ),
            hlcMap = mapOf(
                "template_title" to clock.next(),
                "template_detail" to clock.next(),
                "recurrence_rule" to clock.next(),
                "default_start_not_before" to clock.next(),
                "default_estimated_duration" to clock.next(),
                "active" to clock.next(),
            ),
            dirtyFields = listOf(
                "template_title",
                "template_detail",
                "recurrence_rule",
                "default_start_not_before",
                "default_estimated_duration",
                "active",
            ),
            isDirty = true,
        )
        routineId
    }

    fun updateRoutine(
        routineId: String,
        templateTitle: String,
        templateDetail: String?,
        recurrenceRule: String,
        defaultStartNotBefore: String?,
        defaultEstimatedDuration: String?,
    ) = database.transaction {
        val existing = queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull() ?: return@transaction
        persistRoutine(
            routine = existing.entity.copy(
                templateTitle = templateTitle.trim(),
                templateDetail = templateDetail?.trim()?.takeIf(String::isNotEmpty),
                recurrenceRule = recurrenceRule.trim(),
                defaultStartNotBefore = defaultStartNotBefore?.trim()?.takeIf(String::isNotEmpty),
                defaultEstimatedDuration = defaultEstimatedDuration?.trim()?.takeIf(String::isNotEmpty),
                updatedAt = timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + mapOf(
                "template_title" to clock.next(existing.hlcMap["template_title"]),
                "template_detail" to clock.next(existing.hlcMap["template_detail"]),
                "recurrence_rule" to clock.next(existing.hlcMap["recurrence_rule"]),
                "default_start_not_before" to clock.next(existing.hlcMap["default_start_not_before"]),
                "default_estimated_duration" to clock.next(existing.hlcMap["default_estimated_duration"]),
            ),
            dirtyFields = (
                existing.dirtyFields + listOf(
                    "template_title",
                    "template_detail",
                    "recurrence_rule",
                    "default_start_not_before",
                    "default_estimated_duration",
                )
            ).distinct(),
            isDirty = true,
        )
    }

    fun toggleRoutineActive(routineId: String) = database.transaction {
        val existing = queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull() ?: return@transaction
        persistRoutine(
            routine = existing.entity.copy(
                isActive = !existing.entity.isActive,
                updatedAt = timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("active" to clock.next(existing.hlcMap["active"])),
            dirtyFields = (existing.dirtyFields + "active").distinct(),
            isDirty = true,
        )
    }

    fun deleteRoutine(routineId: String) = database.transaction {
        val existing = queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull() ?: return@transaction
        persistRoutine(
            routine = existing.entity.copy(
                isDeleted = true,
                updatedAt = timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("delete" to clock.next(existing.hlcMap["delete"])),
            dirtyFields = (existing.dirtyFields + "delete").distinct(),
            isDirty = true,
        )
    }

    fun createAlarm(taskId: String, triggerTimeIso: String): String = database.transactionWithResult {
        val now = timeProvider.nowEpochMillis()
        val alarmId = idGenerator.randomId()
        persistAlarm(
            alarm = LocalAlarm(
                id = alarmId,
                taskId = taskId,
                triggerTime = isoStringToEpochMillis(triggerTimeIso),
                isActive = true,
                isDeleted = false,
                serverVersion = 0,
                createdAt = now,
                updatedAt = now,
            ),
            hlcMap = mapOf(
                "trigger" to clock.next(),
                "active" to clock.next(),
            ),
            dirtyFields = listOf("trigger", "active"),
            isDirty = true,
        )
        alarmId
    }

    fun updateAlarm(alarmId: String, triggerTimeIso: String) = database.transaction {
        val existing = queries.selectAlarmById(alarmId, ::mapAlarmRow).executeAsOneOrNull() ?: return@transaction
        persistAlarm(
            alarm = existing.entity.copy(
                triggerTime = isoStringToEpochMillis(triggerTimeIso),
                updatedAt = timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("trigger" to clock.next(existing.hlcMap["trigger"])),
            dirtyFields = (existing.dirtyFields + "trigger").distinct(),
            isDirty = true,
        )
    }

    fun toggleAlarmActive(alarmId: String) = database.transaction {
        val existing = queries.selectAlarmById(alarmId, ::mapAlarmRow).executeAsOneOrNull() ?: return@transaction
        persistAlarm(
            alarm = existing.entity.copy(
                isActive = !existing.entity.isActive,
                updatedAt = timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("active" to clock.next(existing.hlcMap["active"])),
            dirtyFields = (existing.dirtyFields + "active").distinct(),
            isDirty = true,
        )
    }

    fun deleteAlarm(alarmId: String) = database.transaction {
        val existing = queries.selectAlarmById(alarmId, ::mapAlarmRow).executeAsOneOrNull() ?: return@transaction
        persistAlarm(
            alarm = existing.entity.copy(
                isDeleted = true,
                updatedAt = timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("delete" to clock.next(existing.hlcMap["delete"])),
            dirtyFields = (existing.dirtyFields + "delete").distinct(),
            isDirty = true,
        )
    }

    fun runRoutine(routineId: String, journalDate: String) = database.transaction {
        val taskId = idGenerator.deterministicId("routine-task", "$routineId:$journalDate")
        if (queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() == null) {
            val now = timeProvider.nowEpochMillis()
            val routine = queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull()?.entity
            val title = routine?.templateTitle ?: "Routine Task"
            queries.upsertTask(
                id = taskId,
                parent_id = null,
                routine_id = routineId,
                title = title,
                detail = routine?.templateDetail,
                start_not_before = routine?.defaultStartNotBefore,
                end_not_after = null,
                estimated_duration = routine?.defaultEstimatedDuration,
                is_deleted = 0,
                hlc_map = JsonCodecs.encodeMap(
                    mapOf(
                        "title" to clock.next(),
                        "detail" to clock.next(),
                        "start_not_before" to clock.next(),
                        "estimated_duration" to clock.next(),
                        "routine" to clock.next(),
                    ),
                ),
                dirty_fields = JsonCodecs.encodeList(
                    buildList {
                        add("title")
                        if (routine?.templateDetail != null) add("detail")
                        if (routine?.defaultStartNotBefore != null) add("start_not_before")
                        if (routine?.defaultEstimatedDuration != null) add("estimated_duration")
                        add("routine")
                    },
                ),
                is_dirty = 1,
                server_version = 0,
                created_at = now,
                updated_at = now,
            )
        }
        val eventId = idGenerator.deterministicId("routine-created-event", "$routineId:$journalDate")
        if (queries.selectTaskEventById(eventId, ::mapTaskEventRow).executeAsOneOrNull() == null) {
            val now = timeProvider.nowEpochMillis()
            queries.upsertTaskEvent(
                id = eventId,
                task_id = taskId,
                journal_date = journalDate,
                event_type = TaskEventType.CREATED.name,
                content = null,
                postponed_json = null,
                event_time = now,
                is_deleted = 0,
                server_version = 0,
                is_dirty = 1,
                created_at = now,
            )
        }
    }

    fun fireAlarm(alarmId: String, triggerTimeIso: String) = database.transaction {
        val existing = queries.selectAlarmById(alarmId, ::mapAlarmRow).executeAsOneOrNull() ?: return@transaction
        val updatedHlc = clock.next(existing.hlcMap["active"])
        val dirty = (existing.dirtyFields + "active").distinct()
        persistAlarm(
            alarm = existing.entity.copy(isActive = false),
            hlcMap = existing.hlcMap + ("active" to updatedHlc),
            dirtyFields = dirty,
            isDirty = true,
            createdAt = existing.entity.createdAt,
            updatedAt = timeProvider.nowEpochMillis(),
        )
        val eventId = idGenerator.deterministicId("alarm-fired", "$alarmId:$triggerTimeIso")
        if (queries.selectTaskEventById(eventId, ::mapTaskEventRow).executeAsOneOrNull() == null) {
            queries.upsertTaskEvent(
                id = eventId,
                task_id = existing.entity.taskId,
                journal_date = triggerTimeIso.substringBefore('T'),
                event_type = TaskEventType.ALARM_FIRED.name,
                content = null,
                postponed_json = null,
                event_time = isoStringToEpochMillis(triggerTimeIso),
                is_deleted = 0,
                server_version = 0,
                is_dirty = 1,
                created_at = timeProvider.nowEpochMillis(),
            )
        }
    }

    fun autoMarkMissedTasks() {
        val prefs = preferences()
        val now = timeProvider.nowEpochMillis()
        val zoneId = if (prefs.timezoneOverride == "SYSTEM") TimeZone.currentSystemDefault().id else prefs.timezoneOverride
        val tz = TimeZone.of(zoneId)
        val currentJournalDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date.toString()

        val allRoutineTasks = queries.selectTasks(::mapTaskRow).executeAsList()
            .filter { it.entity.routineId != null }
            .map { toTaskListItemView(it) }
            .filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS }

        for (task in allRoutineTasks) {
            val events = queries.selectTaskEventsForTask(task.id, ::mapTaskEventRow).executeAsList()
            val createdEvent = events.find { it.entity.eventType == TaskEventType.CREATED } ?: continue
            val journalDate = createdEvent.entity.journalDate

            val rolloverDate = LocalDate.parse(journalDate).plus(1, DateTimeUnit.DAY)
            val rolloverParts = prefs.rolloverTime.split(":")
            val hour = rolloverParts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = rolloverParts.getOrNull(1)?.toIntOrNull() ?: 0

            val rolloverDateTime = LocalDateTime(rolloverDate.year, rolloverDate.month, rolloverDate.day, hour, minute)
            val rolloverInstant = rolloverDateTime.toInstant(tz)

            if (Instant.fromEpochMilliseconds(now) > rolloverInstant) {
                appendTaskEvent(task.id, currentJournalDate, TaskEventType.MISSED, null)
            }
        }
    }

    fun dirtyRoutines(): List<SyncRoutine> = queries.selectDirtyRoutines(::mapRoutineRow).executeAsList().map { it.toSyncRoutine() }
    fun dirtyPreferences(): List<SyncPreference> = queries.selectDirtyPreferences(::mapPreferenceRow).executeAsList().map { it.toSyncPreference() }
    fun dirtyTasks(): List<SyncTask> = queries.selectDirtyTasks(::mapTaskRow).executeAsList().map { it.toSyncTask() }
    fun dirtyAlarms(): List<SyncAlarm> = queries.selectDirtyAlarms(::mapAlarmRow).executeAsList().map { it.toSyncAlarm() }
    fun dirtyTaskEvents(): List<SyncTaskEvent> = queries.selectDirtyTaskEvents(::mapTaskEventRow).executeAsList().map { it.entity.toSyncTaskEvent() }
    fun dirtyPomodoroSessions(): List<SyncPomodoroSession> = queries.selectDirtyPomodoroSessions(::mapPomodoroSessionRow).executeAsList().map { it.toSyncPomodoroSession() }

    fun mergeRemotePreferences(items: List<SyncPreference>, clearAccepted: Boolean = false) = database.transaction {
        items.forEach { mergeRemotePreference(it, clearAccepted) }
    }

    fun mergeRemoteRoutines(items: List<SyncRoutine>, clearAccepted: Boolean = false) = database.transaction {
        items.forEach { mergeRemoteRoutine(it, clearAccepted) }
    }

    fun mergeRemoteTasks(items: List<SyncTask>, clearAccepted: Boolean = false) = database.transaction {
        items.forEach { mergeRemoteTask(it, clearAccepted) }
    }

    fun mergeRemoteAlarms(items: List<SyncAlarm>, clearAccepted: Boolean = false) = database.transaction {
        items.forEach { mergeRemoteAlarm(it, clearAccepted) }
    }

    fun mergeRemoteTaskEvents(items: List<SyncTaskEvent>) = database.transaction {
        items.forEach { remote ->
            val existing = queries.selectTaskEventById(remote.id, ::mapTaskEventRow).executeAsOneOrNull()
            val isDirty = if (existing == null) false else existing.entity.isDirty && !(remote.accepted ?: false)
            queries.upsertTaskEvent(
                id = remote.id,
                task_id = remote.taskId,
                journal_date = remote.journalDate,
                event_type = remote.eventType.name,
                content = remote.content,
                postponed_json = remote.postponed?.let(JsonCodecs::encodePostponedPayload),
                event_time = isoStringToEpochMillis(remote.eventTime),
                is_deleted = remote.isDeleted.toLong(),
                server_version = remote.serverVersion,
                is_dirty = isDirty.toLong(),
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: existing?.entity?.createdAt ?: timeProvider.nowEpochMillis(),
            )
        }
    }

    fun mergeRemotePomodoroSessions(items: List<SyncPomodoroSession>, clearAccepted: Boolean = false) = database.transaction {
        items.forEach { remote -> mergeRemotePomodoroSession(remote, clearAccepted) }
    }

    fun syncCursor(scope: SyncScope): Long = queries.selectSyncState(scope.wireName).executeAsOne().last_server_version

    fun updateSyncCursor(scope: SyncScope, version: Long) {
        queries.upsertSyncState(scope.wireName, version)
    }

    private fun appendTaskEvent(
        taskId: String,
        journalDate: String,
        type: TaskEventType,
        note: String?,
        postponed: TaskPostponedPayload? = null,
    ) = database.transaction {
        val now = timeProvider.nowEpochMillis()
        val task = queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull()?.entity ?: return@transaction
        if (!canApplyRoutineOccurrenceEvent(task, type, now)) return@transaction
        queries.upsertTaskEvent(
            id = idGenerator.randomId(),
            task_id = taskId,
            journal_date = journalDate,
            event_type = type.name,
            content = note,
            postponed_json = postponed?.let(JsonCodecs::encodePostponedPayload),
            event_time = now,
            is_deleted = 0,
            server_version = 0,
            is_dirty = 1,
            created_at = now,
        )
    }

    private fun mergeRemoteTask(remote: SyncTask, clearAccepted: Boolean) {
        val existing = queries.selectTaskById(remote.id, ::mapTaskRow).executeAsOneOrNull()
        if (existing == null) {
            persistTask(remote.toLocalTask(), remote.hlcMap, emptyList(), false, remote.createdAt?.let(::isoStringToEpochMillis) ?: timeProvider.nowEpochMillis(), remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: timeProvider.nowEpochMillis())
            return
        }
        var mergedTask = existing.entity
        val mergedHlc = existing.hlcMap.toMutableMap()
        val stillDirty = existing.dirtyFields.toMutableSet()
        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "title" -> mergedTask = mergedTask.copy(title = remote.title)
                "parent" -> mergedTask = mergedTask.copy(parentId = remote.parentId)
                "routine" -> mergedTask = mergedTask.copy(routineId = remote.routineId)
                "detail" -> mergedTask = mergedTask.copy(detail = remote.detail)
                "start_not_before" -> mergedTask = mergedTask.copy(startNotBefore = remote.startNotBefore)
                "end_not_after" -> mergedTask = mergedTask.copy(endNotAfter = remote.endNotAfter)
                "estimated_duration" -> mergedTask = mergedTask.copy(estimatedDuration = remote.estimatedDuration)
                "delete" -> mergedTask = mergedTask.copy(isDeleted = remote.isDeleted)
            }
            mergedHlc[field] = incomingHlc
        }
        if (clearAccepted) remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
        persistTask(mergedTask.copy(serverVersion = remote.serverVersion), mergedHlc, stillDirty.toList(), stillDirty.isNotEmpty(), existing.entity.createdAt, remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: existing.entity.updatedAt)
    }

    private fun mergeRemotePreference(remote: SyncPreference, clearAccepted: Boolean) {
        val existing = queries.selectPreferenceByKey(remote.key, ::mapPreferenceRow).executeAsOneOrNull()
        if (existing == null) {
            persistPreference(
                preference = remote.toLocalPreference(),
                hlcMap = remote.hlcMap,
                dirtyFields = emptyList(),
                isDirty = false,
            )
            return
        }
        var merged = existing.entity
        val mergedHlc = existing.hlcMap.toMutableMap()
        val stillDirty = existing.dirtyFields.toMutableSet()
        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "value" -> merged = merged.copy(value = remote.value, valueType = remote.valueType)
                "delete" -> merged = merged.copy(isDeleted = remote.isDeleted)
            }
            mergedHlc[field] = incomingHlc
        }
        if (clearAccepted) remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
        persistPreference(
            preference = merged.copy(
                serverVersion = remote.serverVersion,
                createdAt = existing.entity.createdAt,
                updatedAt = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: existing.entity.updatedAt,
            ),
            hlcMap = mergedHlc,
            dirtyFields = stillDirty.toList(),
            isDirty = stillDirty.isNotEmpty(),
        )
    }

    private fun mergeRemoteRoutine(remote: SyncRoutine, clearAccepted: Boolean) {
        val existing = queries.selectRoutineById(remote.id, ::mapRoutineRow).executeAsOneOrNull()
        if (existing == null) {
            persistRoutine(remote.toLocalRoutine(), remote.hlcMap, emptyList(), false)
            return
        }
        var merged = existing.entity
        val mergedHlc = existing.hlcMap.toMutableMap()
        val stillDirty = existing.dirtyFields.toMutableSet()
        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "template_title" -> merged = merged.copy(templateTitle = remote.templateTitle)
                "template_detail" -> merged = merged.copy(templateDetail = remote.templateDetail)
                "recurrence_rule" -> merged = merged.copy(recurrenceRule = remote.recurrenceRule)
                "default_start_not_before" -> merged = merged.copy(defaultStartNotBefore = remote.defaultStartNotBefore)
                "default_estimated_duration" -> merged = merged.copy(defaultEstimatedDuration = remote.defaultEstimatedDuration)
                "active" -> merged = merged.copy(isActive = remote.isActive)
                "delete" -> merged = merged.copy(isDeleted = remote.isDeleted)
            }
            mergedHlc[field] = incomingHlc
        }
        if (clearAccepted) remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
        persistRoutine(merged.copy(serverVersion = remote.serverVersion), mergedHlc, stillDirty.toList(), stillDirty.isNotEmpty())
    }

    private fun mergeRemoteAlarm(remote: SyncAlarm, clearAccepted: Boolean) {
        val existing = queries.selectAlarmById(remote.id, ::mapAlarmRow).executeAsOneOrNull()
        if (existing == null) {
            persistAlarm(remote.toLocalAlarm(), remote.hlcMap, emptyList(), false)
            return
        }
        var merged = existing.entity
        val mergedHlc = existing.hlcMap.toMutableMap()
        val stillDirty = existing.dirtyFields.toMutableSet()
        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "trigger" -> merged = merged.copy(triggerTime = isoStringToEpochMillis(remote.triggerTime))
                "active" -> merged = merged.copy(isActive = remote.isActive)
                "delete" -> merged = merged.copy(isDeleted = remote.isDeleted)
            }
            mergedHlc[field] = incomingHlc
        }
        if (clearAccepted) remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
        persistAlarm(merged.copy(serverVersion = remote.serverVersion), mergedHlc, stillDirty.toList(), stillDirty.isNotEmpty())
    }

    private fun mergeRemotePomodoroSession(remote: SyncPomodoroSession, clearAccepted: Boolean) {
        val existing = queries.selectPomodoroSessionById(remote.id, ::mapPomodoroSessionRow).executeAsOneOrNull()
        if (existing == null) {
            persistPomodoroSession(remote.toLocalPomodoroSession(), remote.hlcMap, emptyList(), false)
            return
        }
        var merged = existing.entity
        val mergedHlc = existing.hlcMap.toMutableMap()
        val stillDirty = existing.dirtyFields.toMutableSet()
        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "task" -> merged = merged.copy(taskId = remote.taskId)
                "timing" -> merged = merged.copy(
                    startedAt = isoStringToEpochMillis(remote.startedAt),
                    endsAt = isoStringToEpochMillis(remote.endsAt),
                    durationMinutes = remote.durationMinutes,
                )
                "state" -> merged = merged.copy(
                    state = remote.state.toApp(),
                    completedAt = remote.completedAt?.let(::isoStringToEpochMillis),
                )
                "note" -> merged = merged.copy(note = remote.note)
                "outcome" -> merged = merged.copy(taskUpdate = remote.taskUpdate.toApp())
                "delete" -> merged = merged.copy(isDeleted = remote.isDeleted)
            }
            mergedHlc[field] = incomingHlc
        }
        if (clearAccepted) remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
        persistPomodoroSession(
            merged.copy(
                serverVersion = remote.serverVersion,
                createdAt = existing.entity.createdAt,
                updatedAt = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: existing.entity.updatedAt,
            ),
            mergedHlc,
            stillDirty.toList(),
            stillDirty.isNotEmpty(),
        )
    }

    private fun persistTask(task: LocalTask, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean, createdAt: Long, updatedAt: Long) {
        queries.upsertTask(
            task.id,
            task.parentId,
            task.routineId,
            task.title,
            task.detail,
            task.startNotBefore,
            task.endNotAfter,
            task.estimatedDuration,
            task.isDeleted.toLong(),
            JsonCodecs.encodeMap(hlcMap),
            JsonCodecs.encodeList(dirtyFields),
            isDirty.toLong(),
            task.serverVersion,
            createdAt,
            updatedAt,
        )
    }

    private fun persistRoutine(routine: LocalRoutine, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean) {
        queries.upsertRoutine(
            routine.id,
            routine.templateTitle,
            routine.templateDetail,
            routine.recurrenceRule,
            routine.defaultStartNotBefore,
            routine.defaultEstimatedDuration,
            routine.isActive.toLong(),
            routine.isDeleted.toLong(),
            JsonCodecs.encodeMap(hlcMap),
            JsonCodecs.encodeList(dirtyFields),
            isDirty.toLong(),
            routine.serverVersion,
            routine.createdAt,
            routine.updatedAt,
        )
    }

    private fun persistAlarm(alarm: LocalAlarm, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean, createdAt: Long = alarm.createdAt, updatedAt: Long = alarm.updatedAt) {
        queries.upsertAlarm(alarm.id, alarm.taskId, alarm.triggerTime, alarm.isActive.toLong(), alarm.isDeleted.toLong(), JsonCodecs.encodeMap(hlcMap), JsonCodecs.encodeList(dirtyFields), isDirty.toLong(), alarm.serverVersion, createdAt, updatedAt)
    }

    private fun persistPomodoroSession(session: LocalPomodoroSession, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean) {
        queries.upsertPomodoroSession(
            session.id,
            session.taskId,
            session.phase.name,
            session.state.name,
            session.startedAt,
            session.endsAt,
            session.completedAt,
            session.durationMinutes.toLong(),
            session.note,
            session.taskUpdate.name,
            session.isDeleted.toLong(),
            JsonCodecs.encodeMap(hlcMap),
            JsonCodecs.encodeList(dirtyFields),
            isDirty.toLong(),
            session.serverVersion,
            session.createdAt,
            session.updatedAt,
        )
    }

    private fun persistPreferences(preferences: PreferencesViewState) {
        persistPreferenceEntry(PREF_TASK_TITLE_PREFIX, preferences.titlePrefix.ifBlank { null }, PreferenceValueType.STRING)
        persistPreferenceEntry(PREF_TASK_DEFAULT_ALARM_LEAD_MINUTES, preferences.defaultAlarmLeadMinutes.toString(), PreferenceValueType.INT)
        persistPreferenceEntry(PREF_POMODORO_FOCUS_MINUTES, preferences.focusMinutes.toString(), PreferenceValueType.INT)
        persistPreferenceEntry(PREF_POMODORO_SHORT_BREAK_MINUTES, preferences.shortBreakMinutes.toString(), PreferenceValueType.INT)
        persistPreferenceEntry(PREF_POMODORO_LONG_BREAK_MINUTES, preferences.longBreakMinutes.toString(), PreferenceValueType.INT)
        persistPreferenceEntry(PREF_APP_TIMEZONE_OVERRIDE, preferences.timezoneOverride, PreferenceValueType.STRING)
        persistPreferenceEntry(PREF_APP_DATE_FORMAT, preferences.dateFormat.name, PreferenceValueType.ENUM)
        persistPreferenceEntry(PREF_APP_ROLLOVER_TIME, preferences.rolloverTime, PreferenceValueType.STRING)
        persistPreferenceEntry(PREF_TASK_BLANK_TITLE_POLICY, preferences.blankTitlePolicy.name, PreferenceValueType.ENUM)
    }

    private fun persistPreferenceEntry(key: String, value: String?, valueType: PreferenceValueType) {
        val existing = queries.selectPreferenceByKey(key, ::mapPreferenceRow).executeAsOneOrNull()
        val now = timeProvider.nowEpochMillis()
        val valueChanged = existing?.entity?.value != value || existing?.entity?.valueType != valueType || existing?.entity?.isDeleted != false
        val currentHlc = existing?.hlcMap?.get("value")
        val dirtyFields = if (valueChanged) (existing?.dirtyFields.orEmpty() + "value").distinct() else existing?.dirtyFields.orEmpty()
        persistPreference(
            preference = LocalPreference(
                key = key,
                value = value,
                valueType = valueType,
                isDeleted = false,
                serverVersion = existing?.entity?.serverVersion ?: 0L,
                createdAt = existing?.entity?.createdAt ?: now,
                updatedAt = now,
            ),
            hlcMap = if (valueChanged) (existing?.hlcMap.orEmpty() + ("value" to clock.next(currentHlc))) else existing?.hlcMap.orEmpty(),
            dirtyFields = dirtyFields,
            isDirty = valueChanged || (existing?.dirtyFields?.isNotEmpty() == true),
        )
    }

    private fun persistPreference(preference: LocalPreference, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean) {
        queries.upsertPreference(
            pref_key = preference.key,
            pref_value = preference.value,
            value_type = preference.valueType.name,
            is_deleted = preference.isDeleted.toLong(),
            hlc_map = JsonCodecs.encodeMap(hlcMap),
            dirty_fields = JsonCodecs.encodeList(dirtyFields),
            is_dirty = isDirty.toLong(),
            server_version = preference.serverVersion,
            created_at = preference.createdAt,
            updated_at = preference.updatedAt,
        )
    }

    private fun migrateLegacyPreferences() {
        val legacy = queries.selectAppPreferences(::mapAppPreferencesRow).executeAsOneOrNull()?.toViewState() ?: return
        persistPreferences(legacy)
    }

    private fun toTaskListItemView(row: MutableSyncRow<LocalTask>): TaskListItemView {
        return toTaskListItemView(row, selectedDate = null)
    }

    private fun toTaskListItemView(row: MutableSyncRow<LocalTask>, selectedDate: String?): TaskListItemView {
        val latestEvent = queries.selectTaskEventsForTask(row.entity.id, ::mapTaskEventRow).executeAsList()
            .map { it.entity }
            .filter { selectedDate == null || it.journalDate <= selectedDate }
            .maxWithOrNull(compareBy<LocalTaskEvent>({ it.eventTime }, { it.createdAt }))
            ?.eventType
        val routineContext = row.entity.routineId?.let { routineId ->
            buildRoutineOccurrenceContext(
                task = row.entity,
                routineId = routineId,
                selectedDate = selectedDate,
            )
        }
        return TaskListItemView(
            id = row.entity.id,
            title = row.entity.title,
            status = when (latestEvent) {
                null -> TaskStatus.UNKNOWN
                TaskEventType.CREATED,
                TaskEventType.POSTPONED,
                TaskEventType.MIGRATED,
                TaskEventType.ALARM_FIRED,
                -> TaskStatus.PENDING
                TaskEventType.PROGRESSED -> TaskStatus.IN_PROGRESS
                TaskEventType.COMPLETED -> TaskStatus.COMPLETED
                TaskEventType.DELETED,
                TaskEventType.MISSED,
                TaskEventType.SKIPPED,
                TaskEventType.CANCELLED -> TaskStatus.CANCELLED
            },
            detail = row.entity.detail,
            startNotBefore = row.entity.startNotBefore,
            endNotAfter = row.entity.endNotAfter,
            estimatedDuration = row.entity.estimatedDuration,
            parentId = row.entity.parentId,
            routineId = row.entity.routineId,
            routineCanExecute = routineContext?.canExecute,
            routineCanPostpone = routineContext?.canPostpone,
            routineCanSkip = routineContext?.canSkip,
            routineNextOccurrenceBoundary = routineContext?.nextOccurrenceBoundary,
        )
    }

    private fun buildRoutineOccurrenceContext(
        task: LocalTask,
        routineId: String,
        selectedDate: String?,
    ): RoutineOccurrenceContext? {
        if (selectedDate == null) return null
        val routine = queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull()?.entity ?: return null
        val structured = StructuredRecurrence.fromRRule(routine.recurrenceRule)
        val selected = LocalDate.parse(selectedDate)
        val rolloverInstant = rolloverInstantFor(selected)
        val canExecute = Instant.fromEpochMilliseconds(timeProvider.nowEpochMillis()) <= rolloverInstant
        val baseTime = task.startNotBefore?.let {
            runCatching { Instant.parse(it).toLocalDateTime(TimeZone.currentSystemDefault()) }.getOrNull()
        } ?: Instant.fromEpochMilliseconds(task.createdAt).toLocalDateTime(TimeZone.currentSystemDefault())
        val nextOccurrence = structured.nextOccurrence(baseTime)
        val nextOccurrenceInstant = nextOccurrence.toInstant(TimeZone.currentSystemDefault())
        val currentAnchor = task.startNotBefore?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        } ?: Instant.fromEpochMilliseconds(task.createdAt)
        return RoutineOccurrenceContext(
            canExecute = canExecute,
            canPostpone = canExecute && currentAnchor.plus(1, DateTimeUnit.MINUTE, TimeZone.currentSystemDefault()) < nextOccurrenceInstant,
            canSkip = canExecute,
            nextOccurrenceBoundary = nextOccurrenceInstant.toString(),
        )
    }

    private fun rolloverInstantFor(date: LocalDate): Instant {
        val rolloverDate = date.plus(1, DateTimeUnit.DAY)
        val prefs = preferences()
        val rolloverParts = prefs.rolloverTime.split(":")
        val hour = rolloverParts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = rolloverParts.getOrNull(1)?.toIntOrNull() ?: 0
        val rolloverDateTime = LocalDateTime(rolloverDate.year, rolloverDate.month, rolloverDate.day, hour, minute)
        return rolloverDateTime.toInstant(TimeZone.currentSystemDefault())
    }

    private fun canApplyRoutineOccurrenceEvent(task: LocalTask, type: TaskEventType, nowEpochMillis: Long): Boolean {
        if (task.routineId == null) return true
        val lifecycle = routineOccurrenceLifecycle(task.id) ?: return true
        if (type == TaskEventType.MISSED) return true
        if (lifecycle.isExpired(nowEpochMillis)) return false
        return true
    }

    private fun isTaskVisibleOnSelectedDate(
        row: MutableSyncRow<LocalTask>,
        selectedDate: LocalDate,
        task: TaskListItemView,
    ): Boolean {
        if (row.entity.isDeleted) {
            val deletionDate = epochMillisToIsoString(row.entity.updatedAt).let(::isoDatePart)
            if (selectedDate >= deletionDate) return false
        }
        if (row.entity.routineId != null) {
            val occurrenceDate = routineOccurrenceLifecycle(row.entity.id)?.occurrenceDate ?: return false
            return selectedDate == occurrenceDate
        }
        return task.isVisibleOn(selectedDate)
    }

    private fun isValidPomodoroTask(taskId: String, currentDate: String): Boolean =
        openTasksForDate(currentDate).any { it.id == taskId }

    private fun routineOccurrenceLifecycle(taskId: String): RoutineOccurrenceLifecycle? {
        val createdEvent = queries.selectTaskEventsForTask(taskId, ::mapTaskEventRow)
            .executeAsList()
            .asSequence()
            .map { it.entity }
            .filter { it.eventType == TaskEventType.CREATED }
            .minWithOrNull(compareBy<LocalTaskEvent>({ it.eventTime }, { it.createdAt }))
            ?: return null
        val occurrenceDate = LocalDate.parse(createdEvent.journalDate)
        return RoutineOccurrenceLifecycle(
            occurrenceDate = occurrenceDate,
            rolloverInstant = rolloverInstantFor(occurrenceDate),
        )
    }

    private fun toPomodoroSessionView(
        row: MutableSyncRow<LocalPomodoroSession>,
        tasksById: Map<String, TaskListItemView>,
        nowEpochMillis: Long,
    ): PomodoroSessionView = PomodoroSessionView(
        id = row.entity.id,
        taskId = row.entity.taskId,
        taskTitle = row.entity.taskId?.let(tasksById::get)?.title,
        phase = row.entity.phase,
        state = row.entity.state,
        startedAtEpochMillis = row.entity.startedAt,
        endsAtEpochMillis = row.entity.endsAt,
        completedAtEpochMillis = row.entity.completedAt,
        durationMinutes = row.entity.durationMinutes,
        note = row.entity.note,
        taskUpdate = row.entity.taskUpdate,
        isOverdue = row.entity.state == PomodoroSessionState.ACTIVE && row.entity.endsAt <= nowEpochMillis,
    )

    private fun durationForPhase(phase: PomodoroPhase, preferences: PreferencesViewState): Int = when (phase) {
        PomodoroPhase.FOCUS -> preferences.focusMinutes
        PomodoroPhase.SHORT_BREAK -> preferences.shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> preferences.longBreakMinutes
    }

    companion object {
        private const val SINGLETON_ROW_ID = 1L
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

private fun isBacklogTask(task: TaskListItemView): Boolean =
    task.startNotBefore == null && task.endNotAfter == null

private fun TaskListItemView.isVisibleOn(selectedDate: LocalDate): Boolean {
    val visibleStart = effectiveVisibilityStartDate() ?: return false
    return selectedDate >= visibleStart
}

private fun TaskListItemView.effectiveVisibilityStartDate(): LocalDate? {
    val startDate = startNotBefore?.let(::isoDatePart)
    val endStartDate = endNotAfter?.let { end ->
        val durationMillis = estimatedDuration?.let(::parseIsoDurationMillis) ?: 0L
        isoStringToEpochMillis(end).minus(durationMillis).let(::epochMillisToIsoString).let(::isoDatePart)
    }
    return when {
        startDate != null && endStartDate != null -> maxOf(startDate, endStartDate)
        startDate != null -> startDate
        endStartDate != null -> endStartDate
        else -> null
    }
}

private fun MutableSyncRow<LocalTask>.isVisibleOn(selectedDate: LocalDate, task: TaskListItemView): Boolean {
    if (entity.isDeleted) {
        val deletionDate = epochMillisToIsoString(entity.updatedAt).let(::isoDatePart)
        if (selectedDate >= deletionDate) return false
    }
    return task.isVisibleOn(selectedDate)
}

private fun isoDatePart(isoTimestamp: String): LocalDate = LocalDate.parse(isoTimestamp.substringBefore('T'))

private fun parseIsoDurationMillis(value: String): Long {
    val match = ISO_DURATION_PATTERN.matchEntire(value)
        ?: error("Unsupported ISO-8601 duration: $value")
    val days = match.groups[1]?.value?.toLongOrNull() ?: 0L
    val hours = match.groups[2]?.value?.toLongOrNull() ?: 0L
    val minutes = match.groups[3]?.value?.toLongOrNull() ?: 0L
    val seconds = match.groups[4]?.value?.toLongOrNull() ?: 0L
    return (((days * 24 + hours) * 60 + minutes) * 60 + seconds).secondsToMillis()
}

private fun Long.secondsToMillis(): Long = this * 1_000L

private val ISO_DURATION_PATTERN = Regex("""P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")

internal data class LocalTask(
    val id: String,
    val parentId: String?,
    val routineId: String?,
    val title: String,
    val detail: String?,
    val startNotBefore: String?,
    val endNotAfter: String?,
    val estimatedDuration: String?,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class LocalRoutine(
    val id: String,
    val templateTitle: String,
    val templateDetail: String?,
    val recurrenceRule: String,
    val defaultStartNotBefore: String?,
    val defaultEstimatedDuration: String?,
    val isActive: Boolean,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class LocalAlarm(
    val id: String,
    val taskId: String,
    val triggerTime: Long,
    val isActive: Boolean,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class LocalTaskEvent(
    val id: String,
    val taskId: String,
    val journalDate: String,
    val eventType: TaskEventType,
    val content: String?,
    val postponed: TaskPostponedPayload?,
    val eventTime: Long,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val isDirty: Boolean,
    val createdAt: Long,
)

internal data class LocalAppPreferences(
    val titlePrefix: String?,
    val defaultAlarmLeadMinutes: Int,
    val defaultFocusMinutes: Int,
    val defaultShortBreakMinutes: Int,
    val defaultLongBreakMinutes: Int,
    val timezoneOverride: String,
    val dateFormat: DateFormatPreference,
    val blankTitlePolicy: BlankTitlePolicy,
)

internal data class LocalPreference(
    val key: String,
    val value: String?,
    val valueType: PreferenceValueType,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class LocalPomodoroSession(
    val id: String,
    val taskId: String?,
    val phase: PomodoroPhase,
    val state: PomodoroSessionState,
    val startedAt: Long,
    val endsAt: Long,
    val completedAt: Long?,
    val durationMinutes: Int,
    val note: String?,
    val taskUpdate: PomodoroTaskUpdate,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class JournalRow(
    val taskId: String,
    val title: String,
    val eventId: String,
    val eventType: String,
    val content: String?,
    val postponed: TaskPostponedPayload?,
    val eventTime: Long,
    val createdAt: Long,
)

internal data class RoutineOccurrenceContext(
    val canExecute: Boolean,
    val canPostpone: Boolean,
    val canSkip: Boolean,
    val nextOccurrenceBoundary: String,
)

internal data class RoutineOccurrenceLifecycle(
    val occurrenceDate: LocalDate,
    val rolloverInstant: Instant,
) {
    fun isExpired(nowEpochMillis: Long): Boolean = Instant.fromEpochMilliseconds(nowEpochMillis) > rolloverInstant
}

private fun mapTaskRow(
    id: String,
    parent_id: String?,
    routine_id: String?,
    title: String,
    detail: String?,
    start_not_before: String?,
    end_not_after: String?,
    estimated_duration: String?,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): MutableSyncRow<LocalTask> = MutableSyncRow(
    LocalTask(id, parent_id, routine_id, title, detail, start_not_before, end_not_after, estimated_duration, is_deleted != 0L, server_version, created_at, updated_at),
    JsonCodecs.decodeMap(hlc_map),
    if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
)

private fun mapRoutineRow(
    id: String,
    template_title: String,
    template_detail: String?,
    recurrence_rule: String,
    default_start_not_before: String?,
    default_estimated_duration: String?,
    is_active: Long,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): MutableSyncRow<LocalRoutine> = MutableSyncRow(
    LocalRoutine(
        id = id,
        templateTitle = template_title,
        templateDetail = template_detail,
        recurrenceRule = recurrence_rule,
        defaultStartNotBefore = default_start_not_before,
        defaultEstimatedDuration = default_estimated_duration,
        isActive = is_active != 0L,
        isDeleted = is_deleted != 0L,
        serverVersion = server_version,
        createdAt = created_at,
        updatedAt = updated_at,
    ),
    JsonCodecs.decodeMap(hlc_map),
    if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
)

private fun mapAlarmRow(
    id: String,
    task_id: String,
    trigger_time: Long,
    is_active: Long,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): MutableSyncRow<LocalAlarm> = MutableSyncRow(LocalAlarm(id, task_id, trigger_time, is_active != 0L, is_deleted != 0L, server_version, created_at, updated_at), JsonCodecs.decodeMap(hlc_map), if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList())

private fun mapTaskEventRow(
    id: String,
    task_id: String,
    journal_date: String,
    event_type: String,
    content: String?,
    postponed_json: String?,
    event_time: Long,
    is_deleted: Long,
    server_version: Long,
    is_dirty: Long,
    created_at: Long,
): MutableSyncRow<LocalTaskEvent> = MutableSyncRow(
    LocalTaskEvent(
        id,
        task_id,
        journal_date,
        enumValueOf(event_type),
        content,
        JsonCodecs.decodePostponedPayload(postponed_json),
        event_time,
        is_deleted != 0L,
        server_version,
        is_dirty != 0L,
        created_at,
    ),
    emptyMap(),
    emptyList(),
)

private fun mapPomodoroSessionRow(
    id: String,
    task_id: String?,
    phase: String,
    state: String,
    started_at: Long,
    ends_at: Long,
    completed_at: Long?,
    duration_minutes: Long,
    note: String?,
    task_update: String,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): MutableSyncRow<LocalPomodoroSession> = MutableSyncRow(
    LocalPomodoroSession(id, task_id, enumValueOf(phase), enumValueOf(state), started_at, ends_at, completed_at, duration_minutes.toInt(), note, enumValueOf(task_update), is_deleted != 0L, server_version, created_at, updated_at),
    JsonCodecs.decodeMap(hlc_map),
    if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
)

private fun mapJournalRow(
    task_id: String,
    title: String,
    event_id: String,
    event_type: String,
    content: String?,
    postponed_json: String?,
    event_time: Long,
    created_at: Long,
): JournalRow =
    JournalRow(task_id, title, event_id, event_type, content, JsonCodecs.decodePostponedPayload(postponed_json), event_time, created_at)

private fun mapAppPreferencesRow(
    id: Long,
    title_prefix: String?,
    default_alarm_lead_minutes: Long,
    default_focus_minutes: Long,
    default_short_break_minutes: Long,
    default_long_break_minutes: Long,
    timezone_override: String,
    date_format: String,
    blank_title_policy: String,
): LocalAppPreferences = LocalAppPreferences(title_prefix, default_alarm_lead_minutes.toInt(), default_focus_minutes.toInt(), default_short_break_minutes.toInt(), default_long_break_minutes.toInt(), timezone_override, enumValueOf(date_format), enumValueOf(blank_title_policy))

private fun mapPreferenceRow(
    pref_key: String,
    pref_value: String?,
    value_type: String,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): MutableSyncRow<LocalPreference> = MutableSyncRow(
    entity = LocalPreference(pref_key, pref_value, enumValueOf(value_type), is_deleted != 0L, server_version, created_at, updated_at),
    hlcMap = JsonCodecs.decodeMap(hlc_map),
    dirtyFields = if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
)

private fun LocalAppPreferences.toViewState(): PreferencesViewState = PreferencesViewState(
    titlePrefix = titlePrefix.orEmpty(),
    defaultAlarmLeadMinutes = defaultAlarmLeadMinutes,
    focusMinutes = defaultFocusMinutes,
    shortBreakMinutes = defaultShortBreakMinutes,
    longBreakMinutes = defaultLongBreakMinutes,
    timezoneOverride = timezoneOverride,
    dateFormat = dateFormat,
    blankTitlePolicy = blankTitlePolicy,
)

private fun List<MutableSyncRow<LocalPreference>>.toPreferencesViewState(): PreferencesViewState {
    val byKey = associateBy { it.entity.key }
    return PreferencesViewState(
        titlePrefix = byKey[PREF_TASK_TITLE_PREFIX]?.entity?.value.orEmpty(),
        defaultAlarmLeadMinutes = byKey[PREF_TASK_DEFAULT_ALARM_LEAD_MINUTES]?.entity?.value?.toIntOrNull() ?: 15,
        focusMinutes = byKey[PREF_POMODORO_FOCUS_MINUTES]?.entity?.value?.toIntOrNull() ?: 25,
        shortBreakMinutes = byKey[PREF_POMODORO_SHORT_BREAK_MINUTES]?.entity?.value?.toIntOrNull() ?: 5,
        longBreakMinutes = byKey[PREF_POMODORO_LONG_BREAK_MINUTES]?.entity?.value?.toIntOrNull() ?: 15,
        timezoneOverride = byKey[PREF_APP_TIMEZONE_OVERRIDE]?.entity?.value ?: "SYSTEM",
        dateFormat = byKey[PREF_APP_DATE_FORMAT]?.entity?.value?.let(DateFormatPreference::valueOf) ?: DateFormatPreference.ISO,
        blankTitlePolicy = byKey[PREF_TASK_BLANK_TITLE_POLICY]?.entity?.value?.let(BlankTitlePolicy::valueOf) ?: BlankTitlePolicy.REJECT_BLANK,
        rolloverTime = byKey[PREF_APP_ROLLOVER_TIME]?.entity?.value ?: "02:00",
    )
}

private fun Boolean.toLong(): Long = if (this) 1 else 0

private fun MutableSyncRow<LocalPreference>.toSyncPreference(): SyncPreference = SyncPreference(
    key = entity.key,
    value = entity.value,
    valueType = entity.valueType,
    isDeleted = entity.isDeleted,
    hlcMap = hlcMap,
    dirtyFields = dirtyFields,
    serverVersion = entity.serverVersion,
    createdAt = epochMillisToIsoString(entity.createdAt),
)

private fun MutableSyncRow<LocalTask>.toSyncTask(): SyncTask = SyncTask(
    id = entity.id,
    parentId = entity.parentId,
    routineId = entity.routineId,
    title = entity.title,
    detail = entity.detail,
    startNotBefore = entity.startNotBefore,
    endNotAfter = entity.endNotAfter,
    estimatedDuration = entity.estimatedDuration,
    isDeleted = entity.isDeleted,
    hlcMap = hlcMap,
    dirtyFields = dirtyFields,
    serverVersion = entity.serverVersion,
    createdAt = epochMillisToIsoString(entity.createdAt),
)

private fun MutableSyncRow<LocalRoutine>.toSyncRoutine(): SyncRoutine = SyncRoutine(
    id = entity.id,
    templateTitle = entity.templateTitle,
    templateDetail = entity.templateDetail,
    recurrenceRule = entity.recurrenceRule,
    defaultStartNotBefore = entity.defaultStartNotBefore,
    defaultEstimatedDuration = entity.defaultEstimatedDuration,
    isActive = entity.isActive,
    isDeleted = entity.isDeleted,
    hlcMap = hlcMap,
    dirtyFields = dirtyFields,
    serverVersion = entity.serverVersion,
    createdAt = epochMillisToIsoString(entity.createdAt),
)

private fun MutableSyncRow<LocalAlarm>.toSyncAlarm(): SyncAlarm = SyncAlarm(entity.id, entity.taskId, epochMillisToIsoString(entity.triggerTime), entity.isActive, entity.isDeleted, hlcMap, dirtyFields, serverVersion = entity.serverVersion, createdAt = epochMillisToIsoString(entity.createdAt))

private fun MutableSyncRow<LocalPomodoroSession>.toSyncPomodoroSession(): SyncPomodoroSession = SyncPomodoroSession(
    id = entity.id,
    taskId = entity.taskId,
    phase = entity.phase.toWire(),
    state = entity.state.toWire(),
    startedAt = epochMillisToIsoString(entity.startedAt),
    endsAt = epochMillisToIsoString(entity.endsAt),
    completedAt = entity.completedAt?.let(::epochMillisToIsoString),
    durationMinutes = entity.durationMinutes,
    note = entity.note,
    taskUpdate = entity.taskUpdate.toWire(),
    isDeleted = entity.isDeleted,
    hlcMap = hlcMap,
    dirtyFields = dirtyFields,
    serverVersion = entity.serverVersion,
    createdAt = epochMillisToIsoString(entity.createdAt),
)

private fun LocalTaskEvent.toSyncTaskEvent(): SyncTaskEvent = SyncTaskEvent(
    id = id,
    taskId = taskId,
    journalDate = journalDate,
    eventType = eventType,
    content = content,
    postponed = postponed,
    eventTime = epochMillisToIsoString(eventTime),
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = epochMillisToIsoString(createdAt),
)

private fun JournalRow.toJournalEntry(preferences: PreferencesViewState): JournalEntryView? =
    LocalTaskEvent(
        id = eventId,
        taskId = taskId,
        journalDate = "",
        eventType = enumValueOf(eventType),
        content = content,
        postponed = postponed,
        eventTime = eventTime,
        isDeleted = false,
        serverVersion = 0,
        isDirty = false,
        createdAt = createdAt,
    ).toJournalEntry(taskId, title, preferences)

private fun LocalTaskEvent.toJournalEntry(taskId: String, title: String, preferences: PreferencesViewState): JournalEntryView? {
    if (!eventType.isVisibleInDayTimeline()) return null
    return JournalEntryView(
        taskId = taskId,
        title = title,
        eventId = id,
        eventType = eventType,
        content = presentationContent(),
        eventTimeLabel = formatDateTimeLabel(eventTime, preferences.timezoneOverride, preferences.dateFormat),
    )
}

private fun LocalTaskEvent.presentationContent(): String? {
    val note = content?.takeIf(String::isNotBlank)
    val postponedSummary = postponed?.let { payload ->
        buildList {
            if (payload.previousStartNotBefore != payload.newStartNotBefore) {
                add("Start: ${payload.previousStartNotBefore ?: "unset"} -> ${payload.newStartNotBefore ?: "unset"}")
            }
            if (payload.previousEndNotAfter != payload.newEndNotAfter) {
                add("Deadline: ${payload.previousEndNotAfter ?: "unset"} -> ${payload.newEndNotAfter ?: "unset"}")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
    return listOfNotNull(note, postponedSummary).takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun TaskEventType.isVisibleInDayTimeline(): Boolean = when (this) {
    TaskEventType.CREATED,
    TaskEventType.PROGRESSED,
    TaskEventType.COMPLETED,
    TaskEventType.POSTPONED,
    TaskEventType.DELETED,
    TaskEventType.MISSED,
    TaskEventType.SKIPPED -> true
    TaskEventType.MIGRATED,
    TaskEventType.ALARM_FIRED,
    TaskEventType.CANCELLED -> false
}

private fun SyncTask.toLocalTask(): LocalTask = LocalTask(
    id = id,
    parentId = parentId,
    routineId = routineId,
    title = title,
    detail = detail,
    startNotBefore = startNotBefore,
    endNotAfter = endNotAfter,
    estimatedDuration = estimatedDuration,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)

private fun SyncRoutine.toLocalRoutine(): LocalRoutine = LocalRoutine(
    id = id,
    templateTitle = templateTitle,
    templateDetail = templateDetail,
    recurrenceRule = recurrenceRule,
    defaultStartNotBefore = defaultStartNotBefore,
    defaultEstimatedDuration = defaultEstimatedDuration,
    isActive = isActive,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)

private fun SyncAlarm.toLocalAlarm(): LocalAlarm = LocalAlarm(id, taskId, isoStringToEpochMillis(triggerTime), isActive, isDeleted, serverVersion, createdAt?.let(::isoStringToEpochMillis) ?: 0L, serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L)

private fun SyncPomodoroSession.toLocalPomodoroSession(): LocalPomodoroSession = LocalPomodoroSession(
    id = id,
    taskId = taskId,
    phase = phase.toApp(),
    state = state.toApp(),
    startedAt = isoStringToEpochMillis(startedAt),
    endsAt = isoStringToEpochMillis(endsAt),
    completedAt = completedAt?.let(::isoStringToEpochMillis),
    durationMinutes = durationMinutes,
    note = note,
    taskUpdate = taskUpdate.toApp(),
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)

private fun SyncPreference.toLocalPreference(): LocalPreference = LocalPreference(
    key = key,
    value = value,
    valueType = valueType,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)

private fun PomodoroPhase.toWire(): PomodoroPhaseWire = PomodoroPhaseWire.valueOf(name)
private fun PomodoroPhaseWire.toApp(): PomodoroPhase = PomodoroPhase.valueOf(name)
private fun PomodoroSessionState.toWire(): PomodoroSessionStateWire = PomodoroSessionStateWire.valueOf(name)
private fun PomodoroSessionStateWire.toApp(): PomodoroSessionState = PomodoroSessionState.valueOf(name)
private fun PomodoroTaskUpdate.toWire(): PomodoroTaskUpdateWire = PomodoroTaskUpdateWire.valueOf(name)
private fun PomodoroTaskUpdateWire.toApp(): PomodoroTaskUpdate = PomodoroTaskUpdate.valueOf(name)
