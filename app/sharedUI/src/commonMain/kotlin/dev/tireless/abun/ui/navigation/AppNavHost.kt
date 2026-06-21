package dev.tireless.abun.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.tireless.abun.SettingsScreenContent
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.ui.screens.DayScreen
import dev.tireless.abun.ui.screens.TasksScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    state: AppUiState,
    liveNow: Long,
    isPomodoroActive: Boolean,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
    onOpenStartPomodoro: () -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
    onUpdateThemePreference: (ThemePreference) -> Unit,
    onUpdatePreferences: (
        titlePrefix: String,
        defaultAlarmLeadMinutes: Int,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        timezoneOverride: String,
        dateFormat: DateFormatPreference,
        themePreference: ThemePreference,
        rolloverTime: String,
    ) -> Unit,
    onReopenLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = routeForTab(state.selectedTab),
    ) {
        composable(AppRoute.Day.route) {
            DayScreen(
                state = state,
                liveNow = liveNow,
                onOpenTask = onOpenTask,
                onStartPomodoro = onOpenStartPomodoro,
            )
        }
        composable(AppRoute.Tasks.route) {
            TasksScreen(
                state = state,
                liveNow = liveNow,
                isPomodoroActive = isPomodoroActive,
                onSelectTaskFilter = onSelectTaskFilter,
                onOpenTask = onOpenTask,
                onOpenStartPomodoro = onOpenStartPomodoro,
                onCreateRoutine = onCreateRoutine,
                onOpenRoutine = onOpenRoutine,
                onRunRoutine = onRunRoutine,
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreenContent(
                state = state,
                onUpdateThemePreference = onUpdateThemePreference,
                onUpdatePreferences = onUpdatePreferences,
                onReopenLogin = onReopenLogin,
                onLogout = onLogout,
            )
        }
    }
}
