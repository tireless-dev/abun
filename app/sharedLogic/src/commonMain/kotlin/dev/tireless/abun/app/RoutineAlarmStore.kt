package dev.tireless.abun.app

import dev.tireless.abun.sync.TaskEventType

class RoutineAlarmStore(private val store: LocalStore) {
    fun routines(): List<RoutineListItemView> = store.queries.selectActiveRoutines(::mapRoutineRow).executeAsList().map {
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

    fun alarms(preferences: PreferencesViewState = store.preferenceStore.preferences()): List<AlarmListItemView> {
        val taskTitles = store.taskStore.allTasks().associateBy(TaskListItemView::id)
        return store.queries.selectActiveAlarms(::mapAlarmRow).executeAsList().mapNotNull { row ->
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

    fun createRoutine(
        templateTitle: String,
        templateDetail: String?,
        recurrenceRule: String,
        defaultStartNotBefore: String?,
        defaultEstimatedDuration: String?,
    ): String = store.database.transactionWithResult {
        val now = store.timeProvider.nowEpochMillis()
        val routineId = store.idGenerator.randomId()
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
                "template_title" to store.clock.next(),
                "template_detail" to store.clock.next(),
                "recurrence_rule" to store.clock.next(),
                "default_start_not_before" to store.clock.next(),
                "default_estimated_duration" to store.clock.next(),
                "active" to store.clock.next(),
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
    ) = store.database.transaction {
        val existing = store.queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull() ?: return@transaction
        persistRoutine(
            routine = existing.entity.copy(
                templateTitle = templateTitle.trim(),
                templateDetail = templateDetail?.trim()?.takeIf(String::isNotEmpty),
                recurrenceRule = recurrenceRule.trim(),
                defaultStartNotBefore = defaultStartNotBefore?.trim()?.takeIf(String::isNotEmpty),
                defaultEstimatedDuration = defaultEstimatedDuration?.trim()?.takeIf(String::isNotEmpty),
                updatedAt = store.timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + mapOf(
                "template_title" to store.clock.next(existing.hlcMap["template_title"]),
                "template_detail" to store.clock.next(existing.hlcMap["template_detail"]),
                "recurrence_rule" to store.clock.next(existing.hlcMap["recurrence_rule"]),
                "default_start_not_before" to store.clock.next(existing.hlcMap["default_start_not_before"]),
                "default_estimated_duration" to store.clock.next(existing.hlcMap["default_estimated_duration"]),
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

    fun toggleRoutineActive(routineId: String) = store.database.transaction {
        val existing = store.queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull() ?: return@transaction
        persistRoutine(
            routine = existing.entity.copy(
                isActive = !existing.entity.isActive,
                updatedAt = store.timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("active" to store.clock.next(existing.hlcMap["active"])),
            dirtyFields = (existing.dirtyFields + "active").distinct(),
            isDirty = true,
        )
    }

    fun deleteRoutine(routineId: String) = store.database.transaction {
        val existing = store.queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull() ?: return@transaction
        persistRoutine(
            routine = existing.entity.copy(
                isDeleted = true,
                updatedAt = store.timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("delete" to store.clock.next(existing.hlcMap["delete"])),
            dirtyFields = (existing.dirtyFields + "delete").distinct(),
            isDirty = true,
        )
    }

    fun createAlarm(taskId: String, triggerTimeIso: String): String = store.database.transactionWithResult {
        val now = store.timeProvider.nowEpochMillis()
        val alarmId = store.idGenerator.randomId()
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
                "trigger" to store.clock.next(),
                "active" to store.clock.next(),
            ),
            dirtyFields = listOf("trigger", "active"),
            isDirty = true,
        )
        alarmId
    }

    fun updateAlarm(alarmId: String, triggerTimeIso: String) = store.database.transaction {
        val existing = store.queries.selectAlarmById(alarmId, ::mapAlarmRow).executeAsOneOrNull() ?: return@transaction
        persistAlarm(
            alarm = existing.entity.copy(
                triggerTime = isoStringToEpochMillis(triggerTimeIso),
                updatedAt = store.timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("trigger" to store.clock.next(existing.hlcMap["trigger"])),
            dirtyFields = (existing.dirtyFields + "trigger").distinct(),
            isDirty = true,
        )
    }

    fun toggleAlarmActive(alarmId: String) = store.database.transaction {
        val existing = store.queries.selectAlarmById(alarmId, ::mapAlarmRow).executeAsOneOrNull() ?: return@transaction
        persistAlarm(
            alarm = existing.entity.copy(
                isActive = !existing.entity.isActive,
                updatedAt = store.timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("active" to store.clock.next(existing.hlcMap["active"])),
            dirtyFields = (existing.dirtyFields + "active").distinct(),
            isDirty = true,
        )
    }

    fun deleteAlarm(alarmId: String) = store.database.transaction {
        val existing = store.queries.selectAlarmById(alarmId, ::mapAlarmRow).executeAsOneOrNull() ?: return@transaction
        persistAlarm(
            alarm = existing.entity.copy(
                isDeleted = true,
                updatedAt = store.timeProvider.nowEpochMillis(),
            ),
            hlcMap = existing.hlcMap + ("delete" to store.clock.next(existing.hlcMap["delete"])),
            dirtyFields = (existing.dirtyFields + "delete").distinct(),
            isDirty = true,
        )
    }

    fun runRoutine(routineId: String, journalDate: String) = store.database.transaction {
        val taskId = store.idGenerator.deterministicId("routine-task", "$routineId:$journalDate")
        if (store.queries.selectTaskById(taskId, store.taskStore::mapTaskRow).executeAsOneOrNull() == null) {
            val now = store.timeProvider.nowEpochMillis()
            val routine = store.queries.selectRoutineById(routineId, ::mapRoutineRow).executeAsOneOrNull()?.entity
            val title = routine?.templateTitle ?: "Routine Task"
            store.queries.upsertTask(
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
                        "title" to store.clock.next(),
                        "detail" to store.clock.next(),
                        "start_not_before" to store.clock.next(),
                        "estimated_duration" to store.clock.next(),
                        "routine" to store.clock.next(),
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
        val eventId = store.idGenerator.deterministicId("routine-created-event", "$routineId:$journalDate")
        if (store.queries.selectTaskEventById(eventId, store.taskStore::mapTaskEventRow).executeAsOneOrNull() == null) {
            val now = store.timeProvider.nowEpochMillis()
            store.queries.upsertTaskEvent(
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

    fun fireAlarm(alarmId: String, triggerTimeIso: String) = store.database.transaction {
        val existing = store.queries.selectAlarmById(alarmId, ::mapAlarmRow).executeAsOneOrNull() ?: return@transaction
        val updatedHlc = store.clock.next(existing.hlcMap["active"])
        val dirty = (existing.dirtyFields + "active").distinct()
        persistAlarm(
            alarm = existing.entity.copy(isActive = false),
            hlcMap = existing.hlcMap + ("active" to updatedHlc),
            dirtyFields = dirty,
            isDirty = true,
            createdAt = existing.entity.createdAt,
            updatedAt = store.timeProvider.nowEpochMillis(),
        )
        val eventId = store.idGenerator.deterministicId("alarm-fired", "$alarmId:$triggerTimeIso")
        if (store.queries.selectTaskEventById(eventId, store.taskStore::mapTaskEventRow).executeAsOneOrNull() == null) {
            store.queries.upsertTaskEvent(
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
                created_at = store.timeProvider.nowEpochMillis(),
            )
        }
    }

    internal fun persistRoutine(routine: LocalRoutine, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean) {
        store.queries.upsertRoutine(
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

    internal fun persistAlarm(
        alarm: LocalAlarm,
        hlcMap: Map<String, String>,
        dirtyFields: List<String>,
        isDirty: Boolean,
        createdAt: Long = alarm.createdAt,
        updatedAt: Long = alarm.updatedAt,
    ) {
        store.queries.upsertAlarm(
            alarm.id,
            alarm.taskId,
            alarm.triggerTime,
            alarm.isActive.toLong(),
            alarm.isDeleted.toLong(),
            JsonCodecs.encodeMap(hlcMap),
            JsonCodecs.encodeList(dirtyFields),
            isDirty.toLong(),
            alarm.serverVersion,
            createdAt,
            updatedAt,
        )
    }

    internal fun mapRoutineRow(
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

    internal fun mapAlarmRow(
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
        LocalAlarm(id, task_id, trigger_time, is_active != 0L, is_deleted != 0L, server_version, created_at, updated_at),
        JsonCodecs.decodeMap(hlc_map),
        if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
    )
}

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

private fun Boolean.toLong(): Long = if (this) 1 else 0
