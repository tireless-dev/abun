package dev.tireless.abun

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.ui.navigation.AppNavHost
import dev.tireless.abun.ui.navigation.AppRoute
import dev.tireless.abun.ui.navigation.appTabForRoute
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
                    taskHistoryFor = { emptyList() },
                    onSelectTaskFilter = {},
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateTask = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                    onUpdateThemePreference = {},
                    onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                    onReopenLogin = {},
                    onLogout = {},
                    onCreateTaskConfirm = { _, _, _, _, _, _ -> },
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

        onNodeWithTag("settings-panel-theme").fetchSemanticsNode()
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

    @Test
    fun `task detail sheet route renders from nav host`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                val navController = rememberNavController()
                LaunchedEffect(Unit) { navController.navigate("task-detail/task-1") }
                AppNavHost(
                    navController = navController,
                    state = screenshotState(selectedTab = AppTab.TASKS),
                    liveNow = ScreenshotNow,
                    isPomodoroActive = false,
                    taskHistoryFor = { emptyList() },
                    onSelectTaskFilter = {},
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateTask = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                    onUpdateThemePreference = {},
                    onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                    onReopenLogin = {},
                    onLogout = {},
                    onCreateTaskConfirm = { _, _, _, _, _, _ -> },
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

        onNodeWithTag("task-detail-sheet").fetchSemanticsNode()
    }

    @Test
    fun `app content seamlessly navigates through all bottom tabs and subtabs without layout constraint exceptions`() = runDesktopComposeUiTest {
        var state by mutableStateOf(
            screenshotState(selectedTab = AppTab.TODAY)
        )
        setContent {
            AppTheme {
                AppContent(
                    state = state,
                    liveNow = ScreenshotNow,
                    selectedTaskHistory = emptyList(),
                    onSelectTab = { state = state.copy(selectedTab = it) },
                    onSelectTaskSubTab = { state = state.copy(selectedTaskSubTab = it, selectedTab = AppTab.TASKS) },
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

        // Initially on Day / Dashboard tab
        onNodeWithTag("day-panel-summary").assertIsDisplayed()

        // 1. Navigate to Tasks tab
        onNodeWithText("Tasks").performClick()
        waitForIdle()
        onNodeWithText("All active").assertIsDisplayed()

        // Switch Task subtabs
        state = state.copy(selectedTaskSubTab = TaskSubTab.ROUTINES)
        waitForIdle()
        onNodeWithText("Routine support").assertExists()

        state = state.copy(selectedTaskSubTab = TaskSubTab.POMODORO)
        waitForIdle()
        onNodeWithText("Timer").assertExists()

        // 2. Navigate to Finance tab (renders FinanceCockpitScreen with verticalScroll)
        onNodeWithText("Finance").performClick()
        waitForIdle()
        onNodeWithTag("finance-cockpit-screen").assertIsDisplayed()

        // Navigate within Finance to Clearing Hub (also has verticalScroll)
        onNodeWithTag("nav-clearing-hub-btn").performClick()
        waitForIdle()
        onNodeWithTag("clearing-hub-screen").assertIsDisplayed()

        // Navigate within Finance to Ledger
        onNodeWithTag("nav-finance-ledger-btn").performClick()
        waitForIdle()
        onNodeWithTag("finance-ledger-screen").assertIsDisplayed()

        // 3. Navigate to Settings tab (renders SettingsScreen with verticalScroll)
        onNodeWithTag("app-tab-settings").performClick()
        waitForIdle()
        onNodeWithTag("settings-panel-cloud").assertIsDisplayed()

        // 4. Navigate back to Dashboard tab
        onNodeWithTag("app-tab-today").performClick()
        waitForIdle()
        onNodeWithTag("day-panel-summary").assertIsDisplayed()
    }

    @Test
    fun `all scrollable finance subroutes and sheets in AppNavHost measure and render without infinite height constraint crash`() = runDesktopComposeUiTest {
        val routesToTest = listOf(
            AppRoute.FinanceCockpit.route to "finance-cockpit-screen",
            AppRoute.DebtClearingHub.route to "clearing-hub-screen",
            AppRoute.Finance.route to "finance-ledger-screen",
            AppRoute.CreateBudgetRule.route to "budget-rule-sheet",
            AppRoute.CombinedRepayment.route to "combined-repayment-sheet",
            AppRoute.AmortizationWizard.route to "amortization-wizard-sheet",
            AppRoute.FinanceAccounts.route to "finance-accounts-sheet-content",
            AppRoute.FinanceSettings.route to "finance-settings-screen",
        )

        routesToTest.forEach { (route, testTag) ->
            setContent {
                AppTheme {
                    val navController = rememberNavController()
                    LaunchedEffect(route) { navController.navigate(route) }
                    AppNavHost(
                        navController = navController,
                        state = screenshotState(selectedTab = appTabForRoute(route)),
                        liveNow = ScreenshotNow,
                        isPomodoroActive = false,
                        taskHistoryFor = { emptyList() },
                        onSelectTaskFilter = {},
                        onOpenTask = {},
                        onOpenStartPomodoro = {},
                        onCreateTask = {},
                        onCreateRoutine = {},
                        onOpenRoutine = {},
                        onRunRoutine = {},
                        onUpdateThemePreference = {},
                        onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                        onReopenLogin = {},
                        onLogout = {},
                        onCreateTaskConfirm = { _, _, _, _, _, _ -> },
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

            waitForIdle()
            onNodeWithTag(testTag).assertExists()
        }
    }

    @Test
    fun `task detail route still belongs to tasks tab`() {
        kotlin.test.assertEquals(AppTab.TASKS, dev.tireless.abun.ui.navigation.appTabForRoute("task-detail/task-1"))
    }

    @Test
    fun `settings module toggle dispatches through AppNavHost to update enabledModules`() = runDesktopComposeUiTest {
        var state by mutableStateOf(
            screenshotState(selectedTab = AppTab.SETTINGS).copy(
                preferences = screenshotState().preferences.copy(
                    enabledModules = setOf(dev.tireless.abun.app.AppModule.TASKS),
                ),
            ),
        )

        setContent {
            AppTheme {
                AppContent(
                    state = state,
                    liveNow = ScreenshotNow,
                    selectedTaskHistory = emptyList(),
                    onSelectTab = { state = state.copy(selectedTab = it) },
                    onSelectTaskSubTab = { state = state.copy(selectedTaskSubTab = it) },
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
                    onToggleModule = { module, enabled ->
                        val updated = if (enabled) {
                            state.preferences.enabledModules + module
                        } else {
                            state.preferences.enabledModules - module
                        }
                        state = state.copy(preferences = state.preferences.copy(enabledModules = updated))
                    },
                )
            }
        }

        waitForIdle()
        onNodeWithTag("module-toggle-finance").performClick()
        waitForIdle()

        kotlin.test.assertTrue(dev.tireless.abun.app.AppModule.FINANCE in state.preferences.enabledModules)
    }

    @Test
    fun `finance settings screen renders finance presets card and triggers load and reset callbacks`() = runDesktopComposeUiTest {
        var loadTriggered = false
        var resetTriggered = false
        var state by mutableStateOf(
            screenshotState(selectedTab = AppTab.FINANCE).copy(
                preferences = screenshotState().preferences.copy(
                    enabledModules = setOf(dev.tireless.abun.app.AppModule.TASKS, dev.tireless.abun.app.AppModule.FINANCE),
                ),
                financeView = screenshotState().financeView.copy(
                    canLoadPredefinedAccounts = true,
                ),
            ),
        )

        setContent {
            AppTheme {
                val navController = rememberNavController()
                LaunchedEffect(Unit) { navController.navigate(AppRoute.FinanceSettings.route) }
                AppNavHost(
                    navController = navController,
                    state = state,
                    liveNow = ScreenshotNow,
                    isPomodoroActive = false,
                    taskHistoryFor = { emptyList() },
                    onSelectTaskFilter = {},
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateTask = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                    onUpdateThemePreference = {},
                    onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                    onReopenLogin = {},
                    onLogout = {},
                    onCreateTaskConfirm = { _, _, _, _, _, _ -> },
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
                    onLoadPredefinedFinanceAccounts = { loadTriggered = true },
                    onResetFinanceToDefaultAccounts = { resetTriggered = true },
                )
            }
        }

        waitForIdle()
        onNodeWithTag("settings-panel-finance-presets").assertExists()
        onNodeWithTag("finance-load-predefined-button").assertExists()
        onNodeWithTag("finance-reset-default-five-button").assertExists()

        onNodeWithTag("finance-load-predefined-button").performScrollTo().performClick()
        waitForIdle()
        kotlin.test.assertTrue(loadTriggered)

        state = state.copy(
            financeView = state.financeView.copy(
                canLoadPredefinedAccounts = false,
            ),
        )
        waitForIdle()
        onNodeWithTag("finance-reset-default-five-button").performScrollTo().performClick()
        waitForIdle()
        kotlin.test.assertTrue(resetTriggered)
    }
}
