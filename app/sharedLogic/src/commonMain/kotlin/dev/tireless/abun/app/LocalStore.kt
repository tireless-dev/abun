package dev.tireless.abun.app

import dev.tireless.abun.db.AbunDatabase
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncConflictResolver
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus

internal data class MutableSyncRow<T>(
    val entity: T,
    val hlcMap: Map<String, String>,
    val dirtyFields: List<String>,
)

class LocalStore(
    private val database: AbunDatabase,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    private val clock: dev.tireless.abun.sync.HybridLogicalClock,
) {
    private val queries = database.abunDatabaseQueries

    init {
        SyncScope.entries.forEach { queries.upsertSyncState(it.wireName, 0) }
    }

    fun allTasks(): List<TaskListItemView> = queries.selectTasks(::mapTaskRow).executeAsList().map { row ->
        val latestEvent = queries.selectTaskLatestEvent(row.entity.id, ::mapTaskEventRow).executeAsOneOrNull()?.entity?.eventType
        TaskListItemView(
            id = row.entity.id,
            title = row.entity.title,
            status = when (latestEvent) {
                null -> TaskStatus.UNKNOWN
                TaskEventType.CREATED, TaskEventType.MIGRATED, TaskEventType.ALARM_FIRED -> TaskStatus.PENDING
                TaskEventType.PROGRESSED -> TaskStatus.IN_PROGRESS
                TaskEventType.COMPLETED -> TaskStatus.COMPLETED
                TaskEventType.CANCELLED -> TaskStatus.CANCELLED
            },
            parentId = row.entity.parentId,
            routineId = row.entity.routineId,
        )
    }

    fun journal(date: String): List<JournalEntryView> = queries.selectJournalEntries(
        date,
        ::mapJournalRow,
    ).executeAsList().map {
        JournalEntryView(
            taskId = it.taskId,
            title = it.title,
            eventId = it.eventId,
            eventType = enumValueOf(it.eventType),
            content = it.content,
            eventTimeLabel = epochMillisToIsoString(it.eventTime),
        )
    }

    fun createTask(title: String, journalDate: String, parentId: String? = null, routineId: String? = null): String =
        database.transactionWithResult {
            val now = timeProvider.nowEpochMillis()
            val taskId = idGenerator.randomId()
            val titleHlc = clock.next()
            val parentHlc = parentId?.let { clock.next(titleHlc) }
            val routineHlc = routineId?.let { clock.next(titleHlc) }
            val dirty = buildList {
                add("title")
                if (parentId != null) add("parent")
                if (routineId != null) add("routine")
            }

            queries.upsertTask(
                id = taskId,
                parent_id = parentId,
                routine_id = routineId,
                title = title,
                is_deleted = 0,
                hlc_map = JsonCodecs.encodeMap(
                    buildMap {
                        put("title", titleHlc)
                        parentHlc?.let { put("parent", it) }
                        routineHlc?.let { put("routine", it) }
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

    fun deleteTask(taskId: String) = database.transaction {
        val existing = queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() ?: return@transaction
        val deleteHlc = clock.next(existing.hlcMap["delete"])
        val dirty = (existing.dirtyFields + "delete").distinct()
        persistTask(
            task = existing.entity.copy(isDeleted = true),
            hlcMap = existing.hlcMap + ("delete" to deleteHlc),
            dirtyFields = dirty,
            isDirty = true,
            createdAt = existing.entity.createdAt,
            updatedAt = timeProvider.nowEpochMillis(),
        )
    }

    fun runRoutine(routineId: String, journalDate: String) = database.transaction {
        val taskId = idGenerator.deterministicId("routine-task", "$routineId:$journalDate")
        if (queries.selectTaskById(taskId, ::mapTaskRow).executeAsOneOrNull() == null) {
            val now = timeProvider.nowEpochMillis()
            val title = queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull()?.entity?.templateTitle
                ?: "Routine Task"
            queries.upsertTask(
                id = taskId,
                parent_id = null,
                routine_id = routineId,
                title = title,
                is_deleted = 0,
                hlc_map = JsonCodecs.encodeMap(
                    mapOf(
                        "title" to clock.next(),
                        "routine" to clock.next(),
                    ),
                ),
                dirty_fields = JsonCodecs.encodeList(listOf("title", "routine")),
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
                event_time = isoStringToEpochMillis(triggerTimeIso),
                is_deleted = 0,
                server_version = 0,
                is_dirty = 1,
                created_at = timeProvider.nowEpochMillis(),
            )
        }
    }

    fun dirtyRoutines(): List<SyncRoutine> = queries.selectDirtyRoutines(::mapRoutineRow).executeAsList().map { it.toSyncRoutine() }
    fun dirtyTasks(): List<SyncTask> = queries.selectDirtyTasks(::mapTaskRow).executeAsList().map { it.toSyncTask() }
    fun dirtyAlarms(): List<SyncAlarm> = queries.selectDirtyAlarms(::mapAlarmRow).executeAsList().map { it.toSyncAlarm() }
    fun dirtyTaskEvents(): List<SyncTaskEvent> = queries.selectDirtyTaskEvents(::mapTaskEventRow).executeAsList().map { it.entity.toSyncTaskEvent() }

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
                event_time = isoStringToEpochMillis(remote.eventTime),
                is_deleted = remote.isDeleted.toLong(),
                server_version = remote.serverVersion,
                is_dirty = isDirty.toLong(),
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: existing?.entity?.createdAt ?: timeProvider.nowEpochMillis(),
            )
        }
    }

    fun syncCursor(scope: SyncScope): Long = queries.selectSyncState(scope.wireName).executeAsOne().last_server_version

    fun updateSyncCursor(scope: SyncScope, version: Long) {
        queries.upsertSyncState(scope.wireName, version)
    }

    private fun appendTaskEvent(taskId: String, journalDate: String, type: TaskEventType, note: String?) = database.transaction {
        val now = timeProvider.nowEpochMillis()
        queries.upsertTaskEvent(
            id = idGenerator.randomId(),
            task_id = taskId,
            journal_date = journalDate,
            event_type = type.name,
            content = note,
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
            persistTask(
                task = remote.toLocalTask(false),
                hlcMap = remote.hlcMap,
                dirtyFields = emptyList(),
                isDirty = false,
                createdAt = remote.createdAt?.let(::isoStringToEpochMillis) ?: timeProvider.nowEpochMillis(),
                updatedAt = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: timeProvider.nowEpochMillis(),
            )
            return
        }
        var mergedTask = existing.entity
        var mergedHlc = existing.hlcMap.toMutableMap()
        val stillDirty = existing.dirtyFields.toMutableSet()
        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "title" -> mergedTask = mergedTask.copy(title = remote.title)
                "parent" -> mergedTask = mergedTask.copy(parentId = remote.parentId)
                "routine" -> mergedTask = mergedTask.copy(routineId = remote.routineId)
                "delete" -> mergedTask = mergedTask.copy(isDeleted = remote.isDeleted)
            }
            mergedHlc[field] = incomingHlc
        }
        if (clearAccepted) {
            remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
        }
        persistTask(
            task = mergedTask.copy(serverVersion = remote.serverVersion),
            hlcMap = mergedHlc,
            dirtyFields = stillDirty.toList(),
            isDirty = stillDirty.isNotEmpty(),
            createdAt = existing.entity.createdAt,
            updatedAt = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: existing.entity.updatedAt,
        )
    }

    private fun mergeRemoteRoutine(remote: SyncRoutine, clearAccepted: Boolean) {
        val existing = queries.selectRoutineById(remote.id, ::mapRoutineRow).executeAsOneOrNull()
        if (existing == null) {
            persistRoutine(remote.toLocalRoutine(false), remote.hlcMap, emptyList(), false)
            return
        }
        var merged = existing.entity
        val mergedHlc = existing.hlcMap.toMutableMap()
        val stillDirty = existing.dirtyFields.toMutableSet()
        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "template" -> merged = merged.copy(templateTitle = remote.templateTitle)
                "schedule" -> merged = merged.copy(cronSchedule = remote.cronSchedule, timezone = remote.timezone)
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
            persistAlarm(remote.toLocalAlarm(false), remote.hlcMap, emptyList(), false)
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

    private fun persistTask(task: LocalTask, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean, createdAt: Long, updatedAt: Long) {
        queries.upsertTask(
            id = task.id,
            parent_id = task.parentId,
            routine_id = task.routineId,
            title = task.title,
            is_deleted = task.isDeleted.toLong(),
            hlc_map = JsonCodecs.encodeMap(hlcMap),
            dirty_fields = JsonCodecs.encodeList(dirtyFields),
            is_dirty = isDirty.toLong(),
            server_version = task.serverVersion,
            created_at = createdAt,
            updated_at = updatedAt,
        )
    }

    private fun persistRoutine(routine: LocalRoutine, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean) {
        queries.upsertRoutine(
            id = routine.id,
            template_title = routine.templateTitle,
            cron_schedule = routine.cronSchedule,
            timezone = routine.timezone,
            is_active = routine.isActive.toLong(),
            is_deleted = routine.isDeleted.toLong(),
            hlc_map = JsonCodecs.encodeMap(hlcMap),
            dirty_fields = JsonCodecs.encodeList(dirtyFields),
            is_dirty = isDirty.toLong(),
            server_version = routine.serverVersion,
            created_at = routine.createdAt,
            updated_at = routine.updatedAt,
        )
    }

    private fun persistAlarm(alarm: LocalAlarm, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean, createdAt: Long = alarm.createdAt, updatedAt: Long = alarm.updatedAt) {
        queries.upsertAlarm(
            id = alarm.id,
            task_id = alarm.taskId,
            trigger_time = alarm.triggerTime,
            is_active = alarm.isActive.toLong(),
            is_deleted = alarm.isDeleted.toLong(),
            hlc_map = JsonCodecs.encodeMap(hlcMap),
            dirty_fields = JsonCodecs.encodeList(dirtyFields),
            is_dirty = isDirty.toLong(),
            server_version = alarm.serverVersion,
            created_at = createdAt,
            updated_at = updatedAt,
        )
    }
}

internal data class LocalTask(
    val id: String,
    val parentId: String?,
    val routineId: String?,
    val title: String,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

internal data class LocalRoutine(
    val id: String,
    val templateTitle: String,
    val cronSchedule: String,
    val timezone: String,
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
    val eventTime: Long,
    val createdAt: Long,
)

private fun mapTaskRow(
    id: String,
    parent_id: String?,
    routine_id: String?,
    title: String,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): MutableSyncRow<LocalTask> = MutableSyncRow(
    entity = LocalTask(id, parent_id, routine_id, title, is_deleted != 0L, server_version, created_at, updated_at),
    hlcMap = JsonCodecs.decodeMap(hlc_map),
    dirtyFields = if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
)

private fun mapRoutineRow(
    id: String,
    template_title: String,
    cron_schedule: String,
    timezone: String,
    is_active: Long,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): MutableSyncRow<LocalRoutine> = MutableSyncRow(
    entity = LocalRoutine(id, template_title, cron_schedule, timezone, is_active != 0L, is_deleted != 0L, server_version, created_at, updated_at),
    hlcMap = JsonCodecs.decodeMap(hlc_map),
    dirtyFields = if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
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
): MutableSyncRow<LocalAlarm> = MutableSyncRow(
    entity = LocalAlarm(id, task_id, trigger_time, is_active != 0L, is_deleted != 0L, server_version, created_at, updated_at),
    hlcMap = JsonCodecs.decodeMap(hlc_map),
    dirtyFields = if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
)

private fun mapTaskEventRow(
    id: String,
    task_id: String,
    journal_date: String,
    event_type: String,
    content: String?,
    event_time: Long,
    is_deleted: Long,
    server_version: Long,
    is_dirty: Long,
    created_at: Long,
): MutableSyncRow<LocalTaskEvent> = MutableSyncRow(
    entity = LocalTaskEvent(
        id = id,
        taskId = task_id,
        journalDate = journal_date,
        eventType = enumValueOf(event_type),
        content = content,
        eventTime = event_time,
        isDeleted = is_deleted != 0L,
        serverVersion = server_version,
        isDirty = is_dirty != 0L,
        createdAt = created_at,
    ),
    hlcMap = emptyMap(),
    dirtyFields = emptyList(),
)

private fun mapJournalRow(
    task_id: String,
    title: String,
    event_id: String,
    event_type: String,
    content: String?,
    event_time: Long,
    created_at: Long,
): JournalRow = JournalRow(task_id, title, event_id, event_type, content, event_time, created_at)

private fun Boolean.toLong(): Long = if (this) 1 else 0

private fun MutableSyncRow<LocalTask>.toSyncTask(): SyncTask = SyncTask(
    id = entity.id,
    parentId = entity.parentId,
    routineId = entity.routineId,
    title = entity.title,
    isDeleted = entity.isDeleted,
    hlcMap = hlcMap,
    dirtyFields = dirtyFields,
    serverVersion = entity.serverVersion,
    createdAt = epochMillisToIsoString(entity.createdAt),
)

private fun MutableSyncRow<LocalRoutine>.toSyncRoutine(): SyncRoutine = SyncRoutine(
    id = entity.id,
    templateTitle = entity.templateTitle,
    cronSchedule = entity.cronSchedule,
    timezone = entity.timezone,
    isActive = entity.isActive,
    isDeleted = entity.isDeleted,
    hlcMap = hlcMap,
    dirtyFields = dirtyFields,
    serverVersion = entity.serverVersion,
    createdAt = epochMillisToIsoString(entity.createdAt),
)

private fun MutableSyncRow<LocalAlarm>.toSyncAlarm(): SyncAlarm = SyncAlarm(
    id = entity.id,
    taskId = entity.taskId,
    triggerTime = epochMillisToIsoString(entity.triggerTime),
    isActive = entity.isActive,
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
    eventTime = epochMillisToIsoString(eventTime),
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = epochMillisToIsoString(createdAt),
)

private fun SyncTask.toLocalTask(isDirty: Boolean): LocalTask = LocalTask(
    id = id,
    parentId = parentId,
    routineId = routineId,
    title = title,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)

private fun SyncRoutine.toLocalRoutine(isDirty: Boolean): LocalRoutine = LocalRoutine(
    id = id,
    templateTitle = templateTitle,
    cronSchedule = cronSchedule,
    timezone = timezone,
    isActive = isActive,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)

private fun SyncAlarm.toLocalAlarm(isDirty: Boolean): LocalAlarm = LocalAlarm(
    id = id,
    taskId = taskId,
    triggerTime = isoStringToEpochMillis(triggerTime),
    isActive = isActive,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)
