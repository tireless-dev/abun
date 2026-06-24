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
import dev.tireless.abun.ui.components.JournalTimeline
import dev.tireless.abun.ui.components.Panel
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.components.TaskStack
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun HomeScreen(
    state: AppUiState,
    liveNow: Long,
    onOpenTask: (TaskListItemView) -> Unit,
    onStartPomodoro: () -> Unit,
) {
    val tasksById = state.taskView.tasks.associateBy(TaskListItemView::id)
    val openTasks = state.today.currentTasks.mapNotNull { agenda -> tasksById[agenda.taskId] }
    val active = state.activePomodoroSession

    Column(
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        Panel(testTag = "day-panel-summary", compact = true) {
            Text(state.selectedDate, style = ThemeTokens.type.title)
            TaskStack(
                tasks = openTasks,
                empty = "No open tasks for this date.",
                onOpenTask = onOpenTask,
                cardCompact = true,
            )
        }

        Panel(testTag = "day-panel-timeline", compact = true) {
            SectionHeader("Day timeline", "${state.today.journalEntries.size} events")
            JournalTimeline(state.today.journalEntries)
        }

        Panel(testTag = "day-panel-pomodoro", compact = true) {
            SectionHeader("Pomodoro", active?.let { formatRemaining(it.endsAtEpochMillis - liveNow) } ?: "Ready")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                    Text(active?.taskTitle ?: "No active timer", style = ThemeTokens.type.bodyMuted)
                    active?.let {
                        Text("${it.phase.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)} • ${it.durationMinutes}m", style = ThemeTokens.type.label)
                    }
                }
                if (active == null) {
                    Button(onClick = onStartPomodoro) {
                        Text("Start", style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                } else {
                    Button(onClick = onStartPomodoro) {
                        Text("Complete or stop", style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }
        }
    }
}
