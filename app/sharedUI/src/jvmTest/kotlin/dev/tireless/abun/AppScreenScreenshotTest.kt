package dev.tireless.abun

import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.AuthViewState
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
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
        ScreenshotScreenFrame("Today", AppTab.TODAY) {
            TodayScreen(state, isFocusModeActive = false, onStartFocus = {})
        }
    }

    @Test
    fun tasksEmpty() = captureScreenshot("screens/tasks_empty") {
        val state = screenshotState(
            selectedTab = AppTab.TASKS,
            taskView = TaskViewState(),
        )
        ScreenshotScreenFrame("Tasks", AppTab.TASKS) {
            TasksScreen(state, isFocusModeActive = false, onOpenActions = {})
        }
    }

    @Test
    fun tasksPopulated() = captureScreenshot("screens/tasks_populated") {
        val state = screenshotState(selectedTab = AppTab.TASKS)
        ScreenshotScreenFrame("Tasks", AppTab.TASKS) {
            TasksScreen(state, isFocusModeActive = false, onOpenActions = {})
        }
    }

    @Test
    fun focusInactive() = captureScreenshot("screens/focus_inactive") {
        val state = screenshotState(selectedTab = AppTab.FOCUS, activePomodoroSession = null)
        ScreenshotScreenFrame("Focus", AppTab.FOCUS) {
            FocusScreen(state, liveNow = ScreenshotNow, onOpenStart = {})
        }
    }

    @Test
    fun focusActive() = captureScreenshot("screens/focus_active") {
        val state = screenshotState(
            selectedTab = AppTab.FOCUS,
            activePomodoroSession = activePomodoroSession(),
        )
        ScreenshotScreenFrame("Focus", AppTab.FOCUS) {
            FocusScreen(state, liveNow = ScreenshotNow, onOpenStart = {})
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
            isFocusModeActive = false,
            onDismiss = {},
            onProgress = {},
            onComplete = {},
            onDelete = {},
            onStartFocus = {},
        )
    }

    @Test
    fun startFocusSheet() = captureScreenshot("sheets/start_focus") {
        StartFocusSheet(
            state = screenshotState(selectedTab = AppTab.FOCUS),
            hasActive = false,
            onDismiss = {},
            onStart = { _: String?, _: PomodoroPhase -> },
        )
    }

    @Test
    fun completeFocusSheet() = captureScreenshot("sheets/complete_focus") {
        CompleteFocusSheet(
            state = screenshotState(
                selectedTab = AppTab.FOCUS,
                activePomodoroSession = activePomodoroSession(remainingMinutes = -1),
            ),
            liveNow = ScreenshotNow,
            onDismiss = {},
            onSave = { _: String, _: PomodoroTaskUpdate -> },
            onStop = {},
        )
    }
}
