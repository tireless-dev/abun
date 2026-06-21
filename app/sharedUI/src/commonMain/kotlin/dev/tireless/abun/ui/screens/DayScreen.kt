package dev.tireless.abun.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.formatRemaining
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.components.JournalTimeline
import dev.tireless.abun.ui.components.MetricRow
import dev.tireless.abun.ui.components.Panel
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.components.TaskStack
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun DayScreen(
    state: AppUiState,
    liveNow: Long,
    onOpenTask: (TaskListItemView) -> Unit,
    onStartPomodoro: () -> Unit,
) {
    val tasksById = state.taskView.tasks.associateBy(TaskListItemView::id)
    val openTasks = state.today.currentTasks.mapNotNull { agenda -> tasksById[agenda.taskId] }
    val runningTasks = openTasks.count { it.status == TaskStatus.IN_PROGRESS }
    val active = state.activePomodoroSession

    Column(
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.lgDp),
    ) {
        Panel(testTag = "day-panel-summary") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text("Daily desk", style = ThemeTokens.type.label)
                    Text("Day", style = ThemeTokens.type.title)
                    Text(state.selectedDate, style = ThemeTokens.type.bodyMuted)
                }
                Button(onClick = onStartPomodoro) {
                    Text(if (active == null) "Start" else formatRemaining(active.endsAtEpochMillis - liveNow), style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
            MetricRow(
                listOf(
                    "Open" to openTasks.size.toString(),
                    "Running" to runningTasks.toString(),
                    "Routines" to state.taskView.routines.size.toString(),
                ),
            )
            TaskStack(
                tasks = openTasks,
                empty = "No open tasks for this date.",
                onOpenTask = onOpenTask,
            )
        }

        Panel(testTag = "day-panel-timeline") {
            SectionHeader("Day timeline", "${state.today.journalEntries.size} events")
            JournalTimeline(state.today.journalEntries)
        }

        Panel(testTag = "day-panel-pomodoro") {
            SectionHeader("Pomodoro", active?.let { formatRemaining(it.endsAtEpochMillis - liveNow) } ?: "Ready")
            Text(active?.taskTitle ?: "No active timer", style = ThemeTokens.type.bodyMuted)
        }
    }
}
