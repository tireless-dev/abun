package dev.tireless.abun

import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.AuthViewState
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.app.TaskViewState
import kotlin.test.Test

class AppScreenScreenshotTest {
    @Test
    fun guide() = captureScreenshot("screens/guide") {
        GuideScreenContent(
            state = screenshotState(
                auth = AuthViewState(
                    showGuide = true,
                    mode = AuthMode.GUEST,
                    email = "jerry@example.com",
                    otpRequested = true,
                ),
            ),
            onUpdateLoginEmail = {},
            onRequestEmailOtp = {},
            onVerifyEmailOtp = {},
            onSkipLogin = {},
        )
    }

    @Test
    fun today() = captureScreenshot("screens/today") {
        val state = screenshotState(selectedTab = AppTab.TODAY)
        ScreenshotScreenFrame("Day", AppTab.TODAY) {
            TodayScreen(state, liveNow = ScreenshotNow, onOpenTask = {}, onStartPomodoro = {})
        }
    }

    @Test
    fun tasksEmpty() = captureScreenshot("screens/tasks_empty") {
        val state = screenshotState(
            selectedTab = AppTab.TASKS,
            taskView = TaskViewState(),
        )
        ScreenshotScreenFrame("Tasks", AppTab.TASKS) {
            TasksScreen(
                state = state,
                liveNow = ScreenshotNow,
                isPomodoroActive = false,
                onSelectPanel = {},
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
            )
        }
    }

    @Test
    fun tasksPopulated() = captureScreenshot("screens/tasks_populated") {
        val state = screenshotState(selectedTab = AppTab.TASKS)
        ScreenshotScreenFrame("Tasks", AppTab.TASKS) {
            TasksScreen(
                state = state,
                liveNow = ScreenshotNow,
                isPomodoroActive = false,
                onSelectPanel = {},
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
            )
        }
    }

    @Test
    fun pomodoroInactive() = captureScreenshot("screens/pomodoro_inactive") {
        val state = screenshotState(selectedTab = AppTab.TASKS, selectedTaskSubTab = TaskSubTab.POMODORO, activePomodoroSession = null)
        ScreenshotScreenFrame("Tasks", AppTab.TASKS) {
            TasksScreen(
                state = state,
                liveNow = ScreenshotNow,
                isPomodoroActive = false,
                onSelectPanel = {},
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
            )
        }
    }

    @Test
    fun pomodoroActive() = captureScreenshot("screens/pomodoro_active") {
        val state = screenshotState(
            selectedTab = AppTab.TASKS,
            selectedTaskSubTab = TaskSubTab.POMODORO,
            activePomodoroSession = activePomodoroSession(),
        )
        ScreenshotScreenFrame("Tasks", AppTab.TASKS) {
            TasksScreen(
                state = state,
                liveNow = ScreenshotNow,
                isPomodoroActive = true,
                onSelectPanel = {},
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
            )
        }
    }

    @Test
    fun settings() = captureScreenshot("screens/settings") {
        val state = screenshotState(selectedTab = AppTab.SETTINGS)
        ScreenshotScreenFrame("Settings", AppTab.SETTINGS) {
            SettingsScreenContent(
                state = state,
                onUpdatePreferences = { _, _, _, _, _, _, _ -> },
            )
        }
    }

    @Test
    fun createTaskSheet() = captureScreenshot("sheets/create_task") {
        CreateTaskSheet(onDismiss = {}, onCreate = {})
    }

    @Test
    fun taskActionsSheet() = captureScreenshot("sheets/task_actions") {
        TaskActionsSheet(
            task = populatedTaskView().tasks.first(),
            history = populatedTodayView().journalEntries,
            availableParents = populatedTaskView().tasks.drop(1),
            isPomodoroActive = false,
            onDismiss = {},
            onSaveTask = { _, _, _, _, _, _, _ -> },
            onProgress = {},
            onComplete = {},
            onDelete = {},
            onStartPomodoro = {},
        )
    }

    @Test
    fun createRoutineSheet() = captureScreenshot("sheets/create_routine") {
        CreateRoutineSheet(onDismiss = {}, onCreate = { _: String, _: String, _: String -> })
    }

    @Test
    fun startPomodoroSheet() = captureScreenshot("sheets/start_pomodoro") {
        StartPomodoroSheet(
            state = screenshotState(selectedTab = AppTab.TASKS, selectedTaskSubTab = TaskSubTab.POMODORO),
            hasActive = false,
            onDismiss = {},
            onStart = { _: String?, _: PomodoroPhase -> },
        )
    }

    @Test
    fun completePomodoroSheet() = captureScreenshot("sheets/complete_pomodoro") {
        CompletePomodoroSheet(
            state = screenshotState(
                selectedTab = AppTab.TASKS,
                selectedTaskSubTab = TaskSubTab.POMODORO,
                activePomodoroSession = activePomodoroSession(remainingMinutes = -1),
            ),
            liveNow = ScreenshotNow,
            onDismiss = {},
            onSave = { _: String, _: PomodoroTaskUpdate -> },
            onStop = {},
        )
    }
}
