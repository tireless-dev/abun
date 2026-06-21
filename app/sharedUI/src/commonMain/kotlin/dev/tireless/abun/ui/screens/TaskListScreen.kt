package dev.tireless.abun.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.filterTasksForSurface
import dev.tireless.abun.taskListFilterEmptyState
import dev.tireless.abun.taskListFilterFromLabel
import dev.tireless.abun.TaskStack
import dev.tireless.abun.label
import dev.tireless.abun.icon
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun TaskListScreen(
    state: AppUiState,
    isPomodoroActive: Boolean,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
) {
    val filteredTasks = filterTasksForSurface(state.taskView.tasks, state.selectedTaskFilter)
    Column(
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TaskListFilter.entries.forEachIndexed { index, filter ->
                val option = filter.label()
                val isSelected = filter == state.selectedTaskFilter
                OutlinedButton(
                    modifier = if (isSelected) Modifier.weight(1f) else Modifier,
                    onClick = { onSelectTaskFilter(taskListFilterFromLabel(option)) },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) ThemeTokens.colors.borderStrong else ThemeTokens.colors.border,
                    ),
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TaskListFilter.entries.size),
                    contentPadding = PaddingValues(
                        horizontal = if (isSelected) ThemeTokens.spacing.mdDp else ThemeTokens.spacing.smDp,
                        vertical = ThemeTokens.spacing.smDp,
                    ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = filter.icon(),
                            contentDescription = option,
                            modifier = Modifier.size(18.dp),
                        )
                        if (isSelected) {
                            Text(
                                option,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = ThemeTokens.type.body.withMaterialContentColor(),
                            )
                        }
                    }
                }
            }
        }
        TaskStack(
            tasks = filteredTasks,
            empty = taskListFilterEmptyState(state.selectedTaskFilter),
            onOpenTask = onOpenTask,
            disabled = isPomodoroActive,
        )
    }
}
