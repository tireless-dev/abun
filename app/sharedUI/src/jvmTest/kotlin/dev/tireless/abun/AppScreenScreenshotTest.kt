package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.AuthViewState
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.app.TaskViewState
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.ui.screens.HomeScreen
import dev.tireless.abun.ui.screens.GuideScreenContent
import dev.tireless.abun.ui.screens.SettingsScreenContent
import dev.tireless.abun.ui.screens.TasksScreen
import dev.tireless.abun.ui.sheets.CompletePomodoroSheet
import dev.tireless.abun.ui.sheets.CreateRoutineSheet
import dev.tireless.abun.ui.sheets.CreateTaskSheetContent
import dev.tireless.abun.ui.sheets.StartPomodoroSheet
import dev.tireless.abun.ui.sheets.TaskActionsSheet
import dev.tireless.abun.ui.sheets.TaskCreateContext
import dev.tireless.abun.ui.sheets.TaskCreateSource
import dev.tireless.abun.ui.theme.AppTheme
import dev.tireless.abun.ui.theme.ThemeTokens
import io.github.takahirom.roborazzi.captureRoboImage
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
        ScreenshotScreenFrame("Dashboard", AppTab.TODAY) {
            HomeScreen(state, liveNow = ScreenshotNow, onOpenTask = {}, onStartPomodoro = {})
        }
    }

    @Test
    fun tasksEmpty() = captureScreenshot("screens/tasks_empty") {
        val state = screenshotState(
            selectedTab = AppTab.TASKS,
            taskView = TaskViewState(),
        )
        ScreenshotScreenFrame("Tasks", AppTab.TASKS, selectedTaskSubTab = state.selectedTaskSubTab) {
            TasksScreen(
                state = state,
                liveNow = ScreenshotNow,
                isPomodoroActive = false,
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
                onRunRoutine = {},
            )
        }
    }

    @Test
    fun tasksPopulated() = captureScreenshot("screens/tasks_populated") {
        val state = screenshotState(selectedTab = AppTab.TASKS)
        ScreenshotScreenFrame("Tasks", AppTab.TASKS, selectedTaskSubTab = state.selectedTaskSubTab) {
            TasksScreen(
                state = state,
                liveNow = ScreenshotNow,
                isPomodoroActive = false,
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
                onRunRoutine = {},
            )
        }
    }

    @Test
    fun pomodoroInactive() = captureScreenshot("screens/pomodoro_inactive") {
        val state = screenshotState(selectedTab = AppTab.TASKS, selectedTaskSubTab = TaskSubTab.POMODORO, activePomodoroSession = null)
        ScreenshotScreenFrame("Tasks", AppTab.TASKS, selectedTaskSubTab = state.selectedTaskSubTab) {
            TasksScreen(
                state = state,
                liveNow = ScreenshotNow,
                isPomodoroActive = false,
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
                onRunRoutine = {},
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
        ScreenshotScreenFrame("Tasks", AppTab.TASKS, selectedTaskSubTab = state.selectedTaskSubTab) {
            TasksScreen(
                state = state,
                liveNow = ScreenshotNow,
                isPomodoroActive = true,
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
                onRunRoutine = {},
            )
        }
    }

    @Test
    fun settings() = captureScreenshot("screens/settings") {
        val state = screenshotState(selectedTab = AppTab.SETTINGS)
        ScreenshotScreenFrame("Settings", AppTab.SETTINGS) {
            SettingsScreenContent(
                state = state,
                onUpdateThemePreference = {},
                onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                onReopenLogin = {},
                onLogout = {},
            )
        }
    }

    @Test
    fun createTaskSheet() = captureScreenshot("sheets/create_task") {
        Column(
            modifier = Modifier
                .background(ThemeTokens.colors.background)
                .padding(ThemeTokens.spacing.screenPaddingDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                CreateTaskSheetContent(
                    context = TaskCreateContext(
                        source = TaskCreateSource.DAY,
                        selectedDate = "2026-06-11",
                    ),
                    onDismiss = {},
                    onCreate = {},
                )
            }
        }
    }

    @Test
    fun createTaskSheetFull() = captureScreenshot("sheets/create_task_full") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            CreateTaskSheetContent(
                context = TaskCreateContext(
                    source = TaskCreateSource.DAY,
                    selectedDate = "2026-06-11",
                ),
                onDismiss = {},
                onCreate = {},
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun createTaskSheetNodeCapture() = runDesktopComposeUiTest {
        setContent {
            AppTheme(themePreference = ThemePreference.LIGHT) {
                Column(
                    modifier = Modifier
                        .testTag("create-task-sheet-content")
                        .fillMaxWidth()
                        .padding(ThemeTokens.spacing.lgDp),
                    verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                ) {
                    CreateTaskSheetContent(
                        context = TaskCreateContext(
                            source = TaskCreateSource.DAY,
                            selectedDate = "2026-06-11",
                        ),
                        onDismiss = {},
                        onCreate = {},
                    )
                }
            }
        }

        onNodeWithTag("create-task-sheet-content").captureRoboImage(
            filePath = "src/jvmTest/screenshots/sheets/create_task_node.png",
        )
    }

    @Test
    fun taskActionsSheet() = captureScreenshot("sheets/task_actions") {
        TaskActionsSheet(
            task = populatedTaskView().tasks.first(),
            history = populatedTodayView().journalEntries,
            availableParents = populatedTaskView().tasks.drop(1),
            availableRoutines = emptyList(),
            isPomodoroActive = false,
            onDismiss = {},
            onSaveTask = { _, _, _, _, _, _, _ -> },
            onProgress = {},
            onComplete = {},
            onSkip = {},
            onPostpone = { _, _, _, _, _ -> },
            onDelete = {},
            onStartPomodoro = {},
        )
    }

    @Test
    fun createRoutineSheet() = captureScreenshot("sheets/create_routine") {
        CreateRoutineSheet(onDismiss = {}, onCreate = { _: String, _: String?, _: String, _: String?, _: String? -> })
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
