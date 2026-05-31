package dev.tireless.abun

import dev.tireless.abun.sync.PomodoroPhaseWire
import dev.tireless.abun.sync.PomodoroSessionStateWire
import dev.tireless.abun.sync.PomodoroTaskUpdateWire
import dev.tireless.abun.sync.PreferenceValueType
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncPomodoroSession
import dev.tireless.abun.sync.SyncPreference
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.TaskEventType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreferencePutRequest(
    val value: String? = null,
    @SerialName("value_type") val valueType: PreferenceValueType,
)

@Serializable
data class RoutineUpsertRequest(
    val id: String? = null,
    @SerialName("template_title") val templateTitle: String,
    @SerialName("template_detail") val templateDetail: String? = null,
    @SerialName("recurrence_rule") val recurrenceRule: String,
    @SerialName("default_start_not_before") val defaultStartNotBefore: String? = null,
    @SerialName("default_estimated_duration") val defaultEstimatedDuration: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class RoutinePatchRequest(
    @SerialName("template_title") val templateTitle: String? = null,
    @SerialName("template_detail") val templateDetail: String? = null,
    @SerialName("recurrence_rule") val recurrenceRule: String? = null,
    @SerialName("default_start_not_before") val defaultStartNotBefore: String? = null,
    @SerialName("default_estimated_duration") val defaultEstimatedDuration: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class AlarmUpsertRequest(
    val id: String? = null,
    @SerialName("task_id") val taskId: String,
    @SerialName("trigger_time") val triggerTime: String,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class AlarmPatchRequest(
    @SerialName("trigger_time") val triggerTime: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class PomodoroSessionUpsertRequest(
    val id: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    val phase: PomodoroPhaseWire,
    val state: PomodoroSessionStateWire,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val note: String? = null,
    @SerialName("task_update") val taskUpdate: PomodoroTaskUpdateWire = PomodoroTaskUpdateWire.NONE,
)

@Serializable
data class PomodoroSessionPatchRequest(
    @SerialName("task_id") val taskId: String? = null,
    val phase: PomodoroPhaseWire? = null,
    val state: PomodoroSessionStateWire? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val note: String? = null,
    @SerialName("task_update") val taskUpdate: PomodoroTaskUpdateWire? = null,
)

@Serializable
data class TaskResponse(
    val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
    val title: String,
    val detail: String? = null,
    @SerialName("start_not_before") val startNotBefore: String? = null,
    @SerialName("end_not_after") val endNotAfter: String? = null,
    @SerialName("estimated_duration") val estimatedDuration: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_version") val serverVersion: Long,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class RoutineResponse(
    val id: String,
    @SerialName("template_title") val templateTitle: String,
    @SerialName("template_detail") val templateDetail: String? = null,
    @SerialName("recurrence_rule") val recurrenceRule: String,
    @SerialName("default_start_not_before") val defaultStartNotBefore: String? = null,
    @SerialName("default_estimated_duration") val defaultEstimatedDuration: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_version") val serverVersion: Long,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AlarmResponse(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("trigger_time") val triggerTime: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_version") val serverVersion: Long,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PomodoroSessionResponse(
    val id: String,
    @SerialName("task_id") val taskId: String? = null,
    val phase: PomodoroPhaseWire,
    val state: PomodoroSessionStateWire,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val note: String? = null,
    @SerialName("task_update") val taskUpdate: PomodoroTaskUpdateWire,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_version") val serverVersion: Long,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PreferenceResponse(
    val key: String,
    val value: String? = null,
    @SerialName("value_type") val valueType: PreferenceValueType,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_version") val serverVersion: Long,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TaskEventResponse(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("journal_date") val journalDate: String,
    @SerialName("event_type") val eventType: TaskEventType,
    val content: String? = null,
    val postponed: dev.tireless.abun.sync.TaskPostponedPayload? = null,
    @SerialName("event_time") val eventTime: String,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("server_version") val serverVersion: Long,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

internal fun SyncTask.toResponse(): TaskResponse = TaskResponse(
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
    serverUpdatedAt = serverUpdatedAt,
    createdAt = createdAt,
)

internal fun SyncRoutine.toResponse(): RoutineResponse = RoutineResponse(
    id = id,
    templateTitle = templateTitle,
    templateDetail = templateDetail,
    recurrenceRule = recurrenceRule,
    defaultStartNotBefore = defaultStartNotBefore,
    defaultEstimatedDuration = defaultEstimatedDuration,
    isActive = isActive,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    serverUpdatedAt = serverUpdatedAt,
    createdAt = createdAt,
)

internal fun SyncAlarm.toResponse(): AlarmResponse = AlarmResponse(
    id = id,
    taskId = taskId,
    triggerTime = triggerTime,
    isActive = isActive,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    serverUpdatedAt = serverUpdatedAt,
    createdAt = createdAt,
)

internal fun SyncPomodoroSession.toResponse(): PomodoroSessionResponse = PomodoroSessionResponse(
    id = id,
    taskId = taskId,
    phase = phase,
    state = state,
    startedAt = startedAt,
    endsAt = endsAt,
    completedAt = completedAt,
    durationMinutes = durationMinutes,
    note = note,
    taskUpdate = taskUpdate,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    serverUpdatedAt = serverUpdatedAt,
    createdAt = createdAt,
)

internal fun SyncPreference.toResponse(): PreferenceResponse = PreferenceResponse(
    key = key,
    value = value,
    valueType = valueType,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    serverUpdatedAt = serverUpdatedAt,
    createdAt = createdAt,
)

internal fun SyncTaskEvent.toResponse(): TaskEventResponse = TaskEventResponse(
    id = id,
    taskId = taskId,
    journalDate = journalDate,
    eventType = eventType,
    content = content,
    postponed = postponed,
    eventTime = eventTime,
    isDeleted = isDeleted,
    serverVersion = serverVersion,
    serverUpdatedAt = serverUpdatedAt,
    createdAt = createdAt,
)
