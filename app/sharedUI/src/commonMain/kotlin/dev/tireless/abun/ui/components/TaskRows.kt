package dev.tireless.abun.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.editorialStatusColors
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
internal fun TaskStack(
    tasks: List<TaskListItemView>,
    empty: String,
    onOpenTask: (TaskListItemView) -> Unit,
    disabled: Boolean = false,
    compact: Boolean = false,
    cardCompact: Boolean = false,
) {
    if (tasks.isEmpty()) {
        Text(empty, style = ThemeTokens.type.body)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        tasks.forEach { task ->
            TaskRow(task = task, compact = compact, cardCompact = cardCompact, disabled = disabled, onOpenTask = onOpenTask)
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskListItemView,
    compact: Boolean,
    cardCompact: Boolean,
    disabled: Boolean,
    onOpenTask: (TaskListItemView) -> Unit,
) {
    EditorialCard(
        modifier = Modifier
            .testTag("task-row-${task.id}")
            .clickable(enabled = !disabled) { onOpenTask(task) },
        contentPadding = if (cardCompact) PaddingValues(ThemeTokens.spacing.mdDp) else PaddingValues(ThemeTokens.spacing.lgDp),
        contentSpacing = if (cardCompact) ThemeTokens.spacing.smDp else ThemeTokens.spacing.mdDp,
    ) {
        Text(task.title, style = ThemeTokens.type.cardTitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
        ) {
            TaskStateIndicator(task.status)
            task.routineId?.let { Text("Routine", style = ThemeTokens.type.bodyMuted) }
        }
        if (disabled) {
            Text("Pomodoro active", style = ThemeTokens.type.bodyMuted)
        }
    }
}

@Composable
private fun TaskStateIndicator(status: TaskStatus) {
    val colors = editorialStatusColors(status, ThemeTokens.colors)
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(colors.content, CircleShape),
    )
}

@Composable
internal fun StatusPill(status: TaskStatus) {
    TaskStateIndicator(status = status)
}
