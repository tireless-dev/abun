package dev.tireless.abun.app

import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskPostponedPayload
import dev.tireless.abun.sync.TaskStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class TaskStore(private val store: LocalStore) {
    fun allTasks(selectedDate: String? = null): List<TaskListItemView> =
        store.queries.selectTasks(::mapTaskRow).executeAsList().map { toTaskListItemView(it, selectedDate) }

    fun backlogTasks(): List<TaskListItemView> =
        store.queries.selectTasks(::mapTaskRow).executeAsList()
            .map(::toTaskListItemView)
            .filter(::isBacklogTask)

    fun openTasksForDate(selectedDate: String): List<TaskListItemView> =
        store.queries.selectAllTasks(::mapTaskRow).executeAsList()
            .map { row -> row to toTaskListItemView(row, selectedDate) }
            .filter { (_, task) -> task.status != TaskStatus.COMPLETED && task.status != TaskStatus.CANCELLED }
            .filterNot { (_, task) -> isBacklogTask(task) }
            .filter { (row, task) -> isTaskVisibleOnSelectedDate(row, LocalDate.parse(selectedDate), task) }
            .map { (_, task) -> task }

    fun journal(date: String, preferences: PreferencesViewState = store.preferenceStore.preferences()): List<JournalEntryView> =
        store.queries.selectJournalEntries(
            date,
            ::mapJournalRow,
        ).executeAsList().mapNotNull { it.toJournalEntry(preferences) }

    fun taskHistory(taskId: String, preferences: PreferencesViewState = store.preferenceStore.preferences()): List<JournalEntryView> {
        val task = store.queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull()?.entity ?: return emptyList()
        return store.queries.selectTaskEventsForTask(taskId, ::mapTaskEventRow).executeAsList().mapNotNull { row ->
            row.entity.toJournalEntry(task.id, task.title, preferences)
        }
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
        store.database.transactionWithResult {
            val now = store.timeProvider.nowEpochMillis()
            val taskId = store.idGenerator.randomId()
            val titleHlc = store.clock.next()
            val parentHlc = parentId?.let { store.clock.next(titleHlc) }
            val routineHlc = routineId?.let { store.clock.next(titleHlc) }
            val detailHlc = detail?.let { store.clock.next(routineHlc ?: parentHlc ?: titleHlc) }
            val startHlc = startNotBefore?.let { store.clock.next(detailHlc ?: routineHlc ?: parentHlc ?: titleHlc) }
            val endHlc = endNotAfter?.let { store.clock.next(startHlc ?: detailHlc ?: routineHlc ?: parentHlc ?: titleHlc) }
            val durationHlc = estimatedDuration?.let { store.clock.next(endHlc ?: startHlc ?: detailHlc ?: routineHlc ?: parentHlc ?: titleHlc) }
            val dirty = buildList {
                add("title")
                if (parentId != null) add("parent")
                if (routineId != null) add("routine")
                if (detail != null) add("detail")
                if (startNotBefore != null) add("start_not_before")
                if (endNotAfter != null) add("end_not_after")
                if (estimatedDuration != null) add("estimated_duration")
            }

            store.queries.upsertTask(
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

            store.queries.upsertTaskEvent(
                id = store.idGenerator.randomId(),
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
    ) = store.database.transaction {
        val existing = store.queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() ?: return@transaction
        val normalizedStart = startNotBefore?.trim().orEmpty().ifBlank { null }
        val normalizedEnd = endNotAfter?.trim().orEmpty().ifBlank { null }
        val normalizedDuration = estimatedDuration?.trim().orEmpty().ifBlank { null }
        val now = store.timeProvider.nowEpochMillis()

        if (!canApplyRoutineOccurrenceEvent(existing.entity, TaskEventType.POSTPONED, now)) return@transaction

        if (existing.entity.routineId != null) {
            val routine = store.queries.selectRoutineById(existing.entity.routineId, store.routineAlarmStore::mapRoutineRow).executeAsOneOrNull()?.entity
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
            updatedHlc["start_not_before"] = store.clock.next(existing.hlcMap["start_not_before"])
            dirty += "start_not_before"
        }
        if (existing.entity.endNotAfter != normalizedEnd) {
            updatedHlc["end_not_after"] = store.clock.next(existing.hlcMap["end_not_after"])
            dirty += "end_not_after"
        }
        if (existing.entity.estimatedDuration != normalizedDuration) {
            updatedHlc["estimated_duration"] = store.clock.next(existing.hlcMap["estimated_duration"])
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
        store.queries.upsertTaskEvent(
            id = store.idGenerator.randomId(),
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
    ) = store.database.transaction {
        val existing = store.queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() ?: return@transaction
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
            updatedHlc["title"] = store.clock.next(existing.hlcMap["title"])
            dirty += "title"
        }
        if (existing.entity.detail != normalizedDetail) {
            updatedHlc["detail"] = store.clock.next(existing.hlcMap["detail"])
            dirty += "detail"
        }
        if (existing.entity.parentId != normalizedParentId) {
            updatedHlc["parent"] = store.clock.next(existing.hlcMap["parent"])
            dirty += "parent"
        }
        if (existing.entity.startNotBefore != normalizedStart) {
            updatedHlc["start_not_before"] = store.clock.next(existing.hlcMap["start_not_before"])
            dirty += "start_not_before"
        }
        if (existing.entity.endNotAfter != normalizedEnd) {
            updatedHlc["end_not_after"] = store.clock.next(existing.hlcMap["end_not_after"])
            dirty += "end_not_after"
        }
        if (existing.entity.estimatedDuration != normalizedDuration) {
            updatedHlc["estimated_duration"] = store.clock.next(existing.hlcMap["estimated_duration"])
            dirty += "estimated_duration"
        }

        persistTask(
            task = updatedTask,
            hlcMap = updatedHlc,
            dirtyFields = dirty.toList(),
            isDirty = true,
            createdAt = existing.entity.createdAt,
            updatedAt = store.timeProvider.nowEpochMillis(),
        )
    }

    fun cancelTask(taskId: String, journalDate: String, note: String? = null) {
        appendTaskEvent(taskId, journalDate, TaskEventType.CANCELLED, note)
    }

    fun skipTask(taskId: String, journalDate: String, note: String? = null) {
        appendTaskEvent(taskId, journalDate, TaskEventType.SKIPPED, note)
    }

    fun deleteTask(taskId: String, journalDate: String) = store.database.transaction {
        val existing = store.queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() ?: return@transaction
        val now = store.timeProvider.nowEpochMillis()
        val deleteHlc = store.clock.next(existing.hlcMap["delete"])
        val dirty = (existing.dirtyFields + "delete").distinct()
        persistTask(
            task = existing.entity.copy(isDeleted = true),
            hlcMap = existing.hlcMap + ("delete" to deleteHlc),
            dirtyFields = dirty,
            isDirty = true,
            createdAt = existing.entity.createdAt,
            updatedAt = now,
        )
        store.queries.upsertTaskEvent(
            id = store.idGenerator.randomId(),
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

    fun autoMarkMissedTasks() {
        val prefs = store.preferenceStore.preferences()
        val now = store.timeProvider.nowEpochMillis()
        val zoneId = if (prefs.timezoneOverride == "SYSTEM") TimeZone.currentSystemDefault().id else prefs.timezoneOverride
        val tz = TimeZone.of(zoneId)
        val currentJournalDate = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date.toString()

        val allRoutineTasks = store.queries.selectTasks(::mapTaskRow).executeAsList()
            .filter { it.entity.routineId != null }
            .map { toTaskListItemView(it) }
            .filter { it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS }

        for (task in allRoutineTasks) {
            val events = store.queries.selectTaskEventsForTask(task.id, ::mapTaskEventRow).executeAsList()
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

    internal fun appendTaskEvent(
        taskId: String,
        journalDate: String,
        type: TaskEventType,
        note: String?,
        postponed: TaskPostponedPayload? = null,
    ) = store.database.transaction {
        val now = store.timeProvider.nowEpochMillis()
        val task = store.queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull()?.entity ?: return@transaction
        if (!canApplyRoutineOccurrenceEvent(task, type, now)) return@transaction
        store.queries.upsertTaskEvent(
            id = store.idGenerator.randomId(),
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

    internal fun persistTask(
        task: LocalTask,
        hlcMap: Map<String, String>,
        dirtyFields: List<String>,
        isDirty: Boolean,
        createdAt: Long,
        updatedAt: Long,
    ) {
        store.queries.upsertTask(
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

    internal fun toTaskListItemView(row: MutableSyncRow<LocalTask>): TaskListItemView =
        toTaskListItemView(row, selectedDate = null)

    internal fun toTaskListItemView(row: MutableSyncRow<LocalTask>, selectedDate: String?): TaskListItemView {
        val latestEvent = store.queries.selectTaskEventsForTask(row.entity.id, ::mapTaskEventRow).executeAsList()
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

    internal fun buildRoutineOccurrenceContext(
        task: LocalTask,
        routineId: String,
        selectedDate: String?,
    ): RoutineOccurrenceContext? {
        if (selectedDate == null) return null
        val routine = store.queries.selectRoutineById(routineId, store.routineAlarmStore::mapRoutineRow).executeAsOneOrNull()?.entity ?: return null
        val structured = StructuredRecurrence.fromRRule(routine.recurrenceRule)
        val selected = LocalDate.parse(selectedDate)
        val rolloverInstant = rolloverInstantFor(selected)
        val canExecute = Instant.fromEpochMilliseconds(store.timeProvider.nowEpochMillis()) <= rolloverInstant
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

    internal fun rolloverInstantFor(date: LocalDate): Instant {
        val rolloverDate = date.plus(1, DateTimeUnit.DAY)
        val prefs = store.preferenceStore.preferences()
        val rolloverParts = prefs.rolloverTime.split(":")
        val hour = rolloverParts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = rolloverParts.getOrNull(1)?.toIntOrNull() ?: 0
        val rolloverDateTime =
            LocalDateTime(rolloverDate.year, rolloverDate.month, rolloverDate.day, hour, minute)
        return rolloverDateTime.toInstant(TimeZone.currentSystemDefault())
    }

    internal fun canApplyRoutineOccurrenceEvent(task: LocalTask, type: TaskEventType, nowEpochMillis: Long): Boolean {
        if (task.routineId == null) return true
        val lifecycle = routineOccurrenceLifecycle(task.id) ?: return true
        if (type == TaskEventType.MISSED) return true
        if (lifecycle.isExpired(nowEpochMillis)) return false
        return true
    }

    internal fun isTaskVisibleOnSelectedDate(
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

    internal fun isValidPomodoroTask(taskId: String, currentDate: String): Boolean =
        openTasksForDate(currentDate).any { it.id == taskId }

    internal fun routineOccurrenceLifecycle(taskId: String): RoutineOccurrenceLifecycle? {
        val createdEvent = store.queries.selectTaskEventsForTask(taskId, ::mapTaskEventRow)
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

    internal fun mapTaskRow(
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

    internal fun mapTaskEventRow(
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

    internal fun mapJournalRow(
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
}

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

private fun Boolean.toLong(): Long = if (this) 1 else 0
