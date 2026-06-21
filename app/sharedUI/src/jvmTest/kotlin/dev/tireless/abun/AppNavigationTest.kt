package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.ui.navigation.AppNavHost
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {
    @Test
    fun `app nav host renders the selected top level route`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                AppNavHost(
                    navController = rememberNavController(),
                    state = screenshotState(selectedTab = AppTab.SETTINGS),
                    liveNow = ScreenshotNow,
                    isPomodoroActive = false,
                    onSelectTaskFilter = {},
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                    onUpdateThemePreference = {},
                    onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                    onReopenLogin = {},
                    onLogout = {},
                )
            }
        }

        onNodeWithText("Appearance").fetchSemanticsNode()
    }

    @Test
    fun `app content keeps tasks route selected after clicking tasks tab`() = runDesktopComposeUiTest {
        setContent {
            var state by mutableStateOf(screenshotState(selectedTab = AppTab.TODAY, selectedTaskSubTab = TaskSubTab.TASKS))
            AppTheme {
                AppContent(
                    state = state,
                    liveNow = ScreenshotNow,
                    selectedTaskHistory = emptyList(),
                    onSelectTab = { state = state.copy(selectedTab = it) },
                    onSelectTaskSubTab = { state = state.copy(selectedTaskSubTab = it, selectedTab = AppTab.TASKS) },
                    onSelectTaskFilter = { state = state.copy(selectedTaskFilter = it, selectedTab = AppTab.TASKS, selectedTaskSubTab = TaskSubTab.TASKS) },
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                    onUpdateThemePreference = {},
                    onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                    onReopenLogin = {},
                    onLogout = {},
                    onRequestEmailOtp = {},
                    onVerifyEmailOtp = {},
                    onSkipLogin = {},
                    onUpdateLoginEmail = {},
                    taskHistoryFor = { emptyList() },
                    onCreateTask = { _, _, _, _, _, _ -> },
                    onCreateRoutineConfirm = { _, _, _, _, _ -> },
                    onSaveTask = { _, _, _, _, _, _, _ -> },
                    onProgressTask = { _, _ -> },
                    onCompleteTask = { _, _ -> },
                    onSkipTask = { _, _ -> },
                    onPostponeTask = { _, _, _, _, _ -> },
                    onDeleteTask = {},
                    onSaveRoutine = { _, _, _, _, _, _ -> },
                    onToggleRoutine = {},
                    onDeleteRoutine = {},
                    onStartPomodoro = { _, _ -> },
                    onCompletePomodoro = { _, _ -> },
                    onStopPomodoro = {},
                )
            }
        }

        onNodeWithText("Tasks").performClick()
        waitForIdle()

        onNodeWithText("All active").assertIsDisplayed()
    }
}
