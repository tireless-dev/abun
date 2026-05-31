package dev.tireless.abun.app

import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus

enum class AppTab {
    TODAY,
    TASKS,
    FOCUS,
    SETTINGS,
}

enum class TaskSubTab {
    TASKS,
    ALARMS,
}

enum class DateFormatPreference {
    ISO,
    MONTH_DAY,
    WEEKDAY_MONTH_DAY,
}

enum class BlankTitlePolicy {
    REJECT_BLANK,
}

enum class PomodoroPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
}

enum class PomodoroSessionState {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

enum class PomodoroTaskUpdate {
    NONE,
    PROGRESS,
    COMPLETE,
    CANCEL,
}

data class TaskListItemView(
    val id: String,
    val title: String,
    val status: TaskStatus,
    val detail: String? = null,
    val startNotBefore: String? = null,
    val endNotAfter: String? = null,
    val estimatedDuration: String? = null,
    val parentId: String? = null,
    val routineId: String? = null,
)

data class AgendaTaskItemView(
    val taskId: String,
    val title: String,
    val status: TaskStatus,
    val triggerTimeLabel: String? = null,
    val triggerTimeEpochMillis: Long? = null,
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
    val syncReady: Boolean = true,
)

enum class AuthMode {
    GUEST,
    AUTHENTICATED,
}

data class AuthViewState(
    val showGuide: Boolean = true,
    val mode: AuthMode = AuthMode.GUEST,
    val email: String = "",
    val otpRequested: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

data class RoutineListItemView(
    val id: String,
    val templateTitle: String,
    val cronSchedule: String,
    val timezone: String,
    val isActive: Boolean,
)

data class AlarmListItemView(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val triggerTimeLabel: String,
    val triggerTimeIso: String,
    val isActive: Boolean,
)

data class PomodoroSessionView(
    val id: String,
    val taskId: String? = null,
    val taskTitle: String? = null,
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val state: PomodoroSessionState = PomodoroSessionState.ACTIVE,
    val startedAtEpochMillis: Long,
    val endsAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val durationMinutes: Int,
    val note: String? = null,
    val taskUpdate: PomodoroTaskUpdate = PomodoroTaskUpdate.NONE,
    val isOverdue: Boolean = false,
)

data class PreferencesViewState(
    val titlePrefix: String = "",
    val defaultAlarmLeadMinutes: Int = 15,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val timezoneOverride: String = "SYSTEM",
    val dateFormat: DateFormatPreference = DateFormatPreference.ISO,
    val blankTitlePolicy: BlankTitlePolicy = BlankTitlePolicy.REJECT_BLANK,
)

data class TodayViewState(
    val currentTasks: List<AgendaTaskItemView> = emptyList(),
    val upcomingTasks: List<AgendaTaskItemView> = emptyList(),
    val journalEntries: List<JournalEntryView> = emptyList(),
)

data class TaskViewState(
    val tasks: List<TaskListItemView> = emptyList(),
    val routines: List<RoutineListItemView> = emptyList(),
    val alarms: List<AlarmListItemView> = emptyList(),
)

data class AppUiState(
    val selectedDate: String,
    val selectedTab: AppTab = AppTab.TODAY,
    val selectedTaskSubTab: TaskSubTab = TaskSubTab.TASKS,
    val isPreferencesOpen: Boolean = false,
    val isPomodoroDialogOpen: Boolean = false,
    val today: TodayViewState = TodayViewState(),
    val taskView: TaskViewState = TaskViewState(),
    val activePomodoroSession: PomodoroSessionView? = null,
    val recentPomodoroSessions: List<PomodoroSessionView> = emptyList(),
    val preferences: PreferencesViewState = PreferencesViewState(),
    val syncState: SyncStateView = SyncStateView(),
    val auth: AuthViewState = AuthViewState(),
)

enum class SyncScope(val wireName: String) {
    PREFERENCES("preferences"),
    ROUTINES("routines"),
    TASKS("tasks"),
    ALARMS("alarms"),
    TASK_EVENTS("task_events"),
    POMODORO_SESSIONS("pomodoro_sessions"),
}
