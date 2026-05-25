package dev.tireless.abun.app

import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus

data class TaskListItemView(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val parentId: String? = null,
    val routineId: String? = null,
)

data class JournalEntryView(
    val taskId: String,
    val title: String,
    val eventId: String,
    val eventType: TaskEventType,
    val content: String?,
    val eventTimeLabel: String,
)

data class SyncStateView(
    val isSyncing: Boolean = false,
    val lastSyncedAt: String? = null,
    val errorMessage: String? = null,
)

data class AppUiState(
    val selectedDate: String,
    val tasks: List<TaskListItemView> = emptyList(),
    val journalEntries: List<JournalEntryView> = emptyList(),
    val syncState: SyncStateView = SyncStateView(),
)

enum class SyncScope(val wireName: String) {
    ROUTINES("routines"),
    TASKS("tasks"),
    ALARMS("alarms"),
    TASK_EVENTS("task_events"),
}
