package dev.tireless.abun.ui.screens

import androidx.compose.runtime.Composable
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.TaskSubTab

@Composable
internal fun TasksScreen(
    state: AppUiState,
    liveNow: Long,
    isPomodoroActive: Boolean,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
    onOpenStartPomodoro: () -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
    onStartPomodoro: ((String?, PomodoroPhase) -> Unit)? = null,
    onCompletePomodoro: ((String, PomodoroTaskUpdate) -> Unit)? = null,
    onStopPomodoro: ((String) -> Unit)? = null,
    onUpdateTaskPreferences: ((titlePrefix: String, defaultAlarmLeadMinutes: Int, focusMinutes: Int, shortBreakMinutes: Int, longBreakMinutes: Int) -> Unit)? = null,
) {
    when (state.selectedTaskSubTab) {
        TaskSubTab.TASKS -> TaskListScreen(
            state = state,
            isPomodoroActive = isPomodoroActive,
            onSelectTaskFilter = onSelectTaskFilter,
            onOpenTask = onOpenTask,
        )
        TaskSubTab.ROUTINES -> RoutineListScreen(
            state = state,
            onCreateRoutine = onCreateRoutine,
            onOpenRoutine = onOpenRoutine,
            onRunRoutine = onRunRoutine,
        )
        TaskSubTab.POMODORO -> PomodoroScreen(
            state = state,
            liveNow = liveNow,
            onOpenStart = onOpenStartPomodoro,
            onStartPomodoro = onStartPomodoro,
            onCompletePomodoro = onCompletePomodoro,
            onStopPomodoro = onStopPomodoro,
        )
        TaskSubTab.SETTINGS -> TaskSettingsScreen(
            state = state,
            onUpdateTaskPreferences = onUpdateTaskPreferences ?: { _, _, _, _, _ -> },
        )
    }
}
