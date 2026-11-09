package dev.tireless.abun.app

import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncConflictResolver
import dev.tireless.abun.sync.SyncPomodoroSession
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent

class TaskSyncStore(private val store: LocalStore) {
    fun dirtyRoutines(): List<SyncRoutine> =
        store.queries.selectDirtyRoutines(store.routineAlarmStore::mapRoutineRow).executeAsList().map { it.toSyncRoutine() }

    fun dirtyTasks(): List<SyncTask> =
        store.queries.selectDirtyTasks(store.taskStore::mapTaskRow).executeAsList().map { it.toSyncTask() }

    fun dirtyAlarms(): List<SyncAlarm> =
        store.queries.selectDirtyAlarms(store.routineAlarmStore::mapAlarmRow).executeAsList().map { it.toSyncAlarm() }

    fun dirtyTaskEvents(): List<SyncTaskEvent> =
        store.queries.selectDirtyTaskEvents(store.taskStore::mapTaskEventRow).executeAsList().map { it.entity.toSyncTaskEvent() }

    fun dirtyPomodoroSessions(): List<SyncPomodoroSession> =
        store.queries.selectDirtyPomodoroSessions(store.pomodoroStore::mapPomodoroSessionRow).executeAsList().map { it.toSyncPomodoroSession() }

    fun mergeRemoteRoutines(items: List<SyncRoutine>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { mergeRemoteRoutine(it, clearAccepted) }
    }

    fun mergeRemoteTasks(items: List<SyncTask>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { mergeRemoteTask(it, clearAccepted) }
    }

    fun mergeRemoteAlarms(items: List<SyncAlarm>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { mergeRemoteAlarm(it, clearAccepted) }
    }

    fun mergeRemoteTaskEvents(items: List<SyncTaskEvent>) = store.database.transaction {
        items.forEach { remote ->
            val existing = store.queries.selectTaskEventById(remote.id, store.taskStore::mapTaskEventRow).executeAsOneOrNull()
            val isDirty = if (existing == null) false else existing.entity.isDirty && !(remote.accepted ?: false)
            store.queries.upsertTaskEvent(
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
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: existing?.entity?.createdAt ?: store.timeProvider.nowEpochMillis(),
            )
        }
    }

    fun mergeRemotePomodoroSessions(items: List<SyncPomodoroSession>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemotePomodoroSession(remote, clearAccepted) }
    }

    private fun mergeRemoteTask(remote: SyncTask, clearAccepted: Boolean) {
        val existing = store.queries.selectTaskById(remote.id, store.taskStore::mapTaskRow).executeAsOneOrNull()
        if (existing == null) {
            store.taskStore.persistTask(
                remote.toLocalTask(),
                remote.hlcMap,
                emptyList(),
                false,
                remote.createdAt?.let(::isoStringToEpochMillis) ?: store.timeProvider.nowEpochMillis(),
                remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: store.timeProvider.nowEpochMillis(),
            )
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
        if (clearAccepted) {
            remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
            remote.rejectedFields.orEmpty().forEach(stillDirty::remove)
        }
        store.taskStore.persistTask(
            mergedTask.copy(serverVersion = remote.serverVersion),
            mergedHlc,
            stillDirty.toList(),
            stillDirty.isNotEmpty(),
            existing.entity.createdAt,
            remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: existing.entity.updatedAt,
        )
    }

    private fun mergeRemoteRoutine(remote: SyncRoutine, clearAccepted: Boolean) {
        val existing = store.queries.selectRoutineById(remote.id, store.routineAlarmStore::mapRoutineRow).executeAsOneOrNull()
        if (existing == null) {
            store.routineAlarmStore.persistRoutine(remote.toLocalRoutine(), remote.hlcMap, emptyList(), false)
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
        if (clearAccepted) {
            remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
            remote.rejectedFields.orEmpty().forEach(stillDirty::remove)
        }
        store.routineAlarmStore.persistRoutine(merged.copy(serverVersion = remote.serverVersion), mergedHlc, stillDirty.toList(), stillDirty.isNotEmpty())
    }

    private fun mergeRemoteAlarm(remote: SyncAlarm, clearAccepted: Boolean) {
        val existing = store.queries.selectAlarmById(remote.id, store.routineAlarmStore::mapAlarmRow).executeAsOneOrNull()
        if (existing == null) {
            store.routineAlarmStore.persistAlarm(remote.toLocalAlarm(), remote.hlcMap, emptyList(), false)
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
        if (clearAccepted) {
            remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
            remote.rejectedFields.orEmpty().forEach(stillDirty::remove)
        }
        store.routineAlarmStore.persistAlarm(merged.copy(serverVersion = remote.serverVersion), mergedHlc, stillDirty.toList(), stillDirty.isNotEmpty())
    }

    private fun mergeRemotePomodoroSession(remote: SyncPomodoroSession, clearAccepted: Boolean) {
        val existing = store.queries.selectPomodoroSessionById(remote.id, store.pomodoroStore::mapPomodoroSessionRow).executeAsOneOrNull()
        if (existing == null) {
            store.pomodoroStore.persistPomodoroSession(remote.toLocalPomodoroSession(), remote.hlcMap, emptyList(), false)
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
        if (clearAccepted) {
            remote.acceptedFields.orEmpty().forEach(stillDirty::remove)
            remote.rejectedFields.orEmpty().forEach(stillDirty::remove)
        }
        store.pomodoroStore.persistPomodoroSession(
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
}

private fun Boolean.toLong(): Long = if (this) 1 else 0

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

private fun SyncAlarm.toLocalAlarm(): LocalAlarm = LocalAlarm(
    id = id,
    taskId = taskId,
    triggerTime = isoStringToEpochMillis(triggerTime),
    isActive = isActive,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    createdAt = createdAt?.let(::isoStringToEpochMillis) ?: 0L,
    updatedAt = serverUpdatedAt?.let(::isoStringToEpochMillis) ?: 0L,
)

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
