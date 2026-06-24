package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.EditorialStatusTag
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

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
        contentPadding = if (cardCompact) PaddingValues(ThemeTokens.spacing.mdDp) else PaddingValues(ThemeTokens.spacing.lgDp),
        contentSpacing = if (cardCompact) ThemeTokens.spacing.smDp else ThemeTokens.spacing.mdDp,
    ) {
        Text(task.title, style = ThemeTokens.type.cardTitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
        ) {
            StatusPill(task.status)
            task.routineId?.let { Text("Routine", style = ThemeTokens.type.bodyMuted) }
        }
        if (!compact) {
            Button(onClick = { onOpenTask(task) }, enabled = !disabled) {
                Text(if (disabled) "Pomodoro active" else "Manage", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        } else {
            Button(onClick = { onOpenTask(task) }, enabled = !disabled) {
                Text("Open", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    }
}

@Composable
internal fun StatusPill(status: TaskStatus) {
    EditorialStatusTag(status = status)
}
