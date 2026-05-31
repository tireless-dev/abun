package dev.tireless.abun.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullResponse<T>(
    val items: List<T>,
    @SerialName("next_cursor") val nextCursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
data class BatchRequest<T>(
    val items: List<T>,
)

@Serializable
data class SyncTask(
    val id: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("routine_id") val routineId: String? = null,
    val title: String,
    val detail: String? = null,
    @SerialName("start_not_before") val startNotBefore: String? = null,
    @SerialName("end_not_after") val endNotAfter: String? = null,
    @SerialName("estimated_duration") val estimatedDuration: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncRoutine(
    val id: String,
    @SerialName("template_title") val templateTitle: String,
    @SerialName("cron_schedule") val cronSchedule: String,
    val timezone: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncAlarm(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("trigger_time") val triggerTime: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncTaskEvent(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("journal_date") val journalDate: String,
    @SerialName("event_type") val eventType: TaskEventType,
    val content: String? = null,
    val postponed: TaskPostponedPayload? = null,
    @SerialName("event_time") val eventTime: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("accepted") val accepted: Boolean? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TaskPostponedPayload(
    @SerialName("previous_start_not_before") val previousStartNotBefore: String? = null,
    @SerialName("new_start_not_before") val newStartNotBefore: String? = null,
    @SerialName("previous_end_not_after") val previousEndNotAfter: String? = null,
    @SerialName("new_end_not_after") val newEndNotAfter: String? = null,
)

@Serializable
data class SyncPomodoroSession(
    val id: String,
    @SerialName("task_id") val taskId: String? = null,
    val phase: PomodoroPhaseWire,
    val state: PomodoroSessionStateWire,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val note: String? = null,
    @SerialName("task_update") val taskUpdate: PomodoroTaskUpdateWire = PomodoroTaskUpdateWire.NONE,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SyncPreference(
    val key: String,
    val value: String? = null,
    @SerialName("value_type") val valueType: PreferenceValueType,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("hlc_map") val hlcMap: Map<String, String> = emptyMap(),
    @SerialName("dirty_fields") val dirtyFields: List<String> = emptyList(),
    @SerialName("accepted_fields") val acceptedFields: List<String>? = null,
    @SerialName("rejected_fields") val rejectedFields: List<String>? = null,
    @SerialName("server_version") val serverVersion: Long = 0,
    @SerialName("server_updated_at") val serverUpdatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
enum class TaskEventType {
    CREATED,
    POSTPONED,
    DELETED,
    MISSED,
    SKIPPED,
    MIGRATED,
    PROGRESSED,
    ALARM_FIRED,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class TaskStatus {
    UNKNOWN,
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class PomodoroPhaseWire {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
}

@Serializable
enum class PomodoroSessionStateWire {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class PomodoroTaskUpdateWire {
    NONE,
    PROGRESS,
    COMPLETE,
    CANCEL,
}

@Serializable
enum class PreferenceValueType {
    STRING,
    INT,
    ENUM,
}

object TaskStatusDeriver {
    fun fromEvents(events: List<SyncTaskEvent>): TaskStatus {
        val latest = events
            .filterNot(SyncTaskEvent::isDeleted)
            .maxWithOrNull(compareBy<SyncTaskEvent>({ it.eventTime }, { it.createdAt ?: "" }))
            ?: return TaskStatus.UNKNOWN
        return when (latest.eventType) {
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
        }
    }
}
