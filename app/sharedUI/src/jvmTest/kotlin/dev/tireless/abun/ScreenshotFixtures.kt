package dev.tireless.abun

import dev.tireless.abun.app.AgendaTaskItemView
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.AuthViewState
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroSessionState
import dev.tireless.abun.app.PomodoroSessionView
import dev.tireless.abun.app.PreferencesViewState
import dev.tireless.abun.app.SyncStateView
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.TaskViewState
import dev.tireless.abun.app.TodayViewState
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus

internal const val ScreenshotNow: Long = 1_779_800_400_000L

internal fun screenshotState(
    selectedTab: AppTab = AppTab.TODAY,
    auth: AuthViewState = AuthViewState(showGuide = false, mode = AuthMode.GUEST),
    taskView: TaskViewState = populatedTaskView(),
    today: TodayViewState = populatedTodayView(),
    activePomodoroSession: PomodoroSessionView? = null,
    recentPomodoroSessions: List<PomodoroSessionView> = recentPomodoroSessions(),
): AppUiState = AppUiState(
    selectedDate = "2026-05-30",
    selectedTab = selectedTab,
    today = today,
    taskView = taskView,
    activePomodoroSession = activePomodoroSession,
    recentPomodoroSessions = recentPomodoroSessions,
    preferences = PreferencesViewState(
        titlePrefix = "Deep work",
        defaultAlarmLeadMinutes = 20,
        focusMinutes = 30,
        shortBreakMinutes = 6,
        longBreakMinutes = 18,
        timezoneOverride = "Asia/Shanghai",
        dateFormat = DateFormatPreference.WEEKDAY_MONTH_DAY,
    ),
    syncState = SyncStateView(lastSyncedAt = "2026-05-30 14:20"),
    auth = auth,
)

internal fun populatedTaskView(): TaskViewState = TaskViewState(
    tasks = listOf(
        TaskListItemView("task-1", "Review launch checklist", TaskStatus.IN_PROGRESS),
        TaskListItemView("task-2", "Write compact sync notes", TaskStatus.PENDING),
        TaskListItemView("task-3", "Archive finished inbox items", TaskStatus.COMPLETED),
    ),
)

internal fun populatedTodayView(): TodayViewState = TodayViewState(
    currentTasks = listOf(
        AgendaTaskItemView("task-1", "Review launch checklist", TaskStatus.IN_PROGRESS, "09:30"),
        AgendaTaskItemView("task-2", "Write compact sync notes", TaskStatus.PENDING, "11:00"),
    ),
    upcomingTasks = listOf(
        AgendaTaskItemView("task-4", "Plan tomorrow priorities", TaskStatus.PENDING, "16:45"),
    ),
    journalEntries = listOf(
        JournalEntryView("task-1", "Review launch checklist", "event-1", TaskEventType.PROGRESSED, "Moved the risky item up.", "09:42"),
        JournalEntryView("task-3", "Archive finished inbox items", "event-2", TaskEventType.COMPLETED, null, "10:05"),
    ),
)

internal fun activePomodoroSession(remainingMinutes: Int = 18): PomodoroSessionView = PomodoroSessionView(
    id = "session-active",
    taskId = "task-1",
    taskTitle = "Review launch checklist",
    phase = PomodoroPhase.FOCUS,
    state = PomodoroSessionState.ACTIVE,
    startedAtEpochMillis = ScreenshotNow - 7 * 60_000L,
    endsAtEpochMillis = ScreenshotNow + remainingMinutes * 60_000L,
    durationMinutes = 25,
    note = "Keep the scope tight.",
)

internal fun recentPomodoroSessions(): List<PomodoroSessionView> = listOf(
    PomodoroSessionView(
        id = "session-1",
        taskId = "task-2",
        taskTitle = "Write compact sync notes",
        phase = PomodoroPhase.FOCUS,
        state = PomodoroSessionState.COMPLETED,
        startedAtEpochMillis = ScreenshotNow - 90 * 60_000L,
        endsAtEpochMillis = ScreenshotNow - 65 * 60_000L,
        completedAtEpochMillis = ScreenshotNow - 65 * 60_000L,
        durationMinutes = 25,
        note = "Captured the open decisions.",
    ),
    PomodoroSessionView(
        id = "session-2",
        phase = PomodoroPhase.SHORT_BREAK,
        state = PomodoroSessionState.COMPLETED,
        startedAtEpochMillis = ScreenshotNow - 55 * 60_000L,
        endsAtEpochMillis = ScreenshotNow - 50 * 60_000L,
        completedAtEpochMillis = ScreenshotNow - 50 * 60_000L,
        durationMinutes = 5,
    ),
)
