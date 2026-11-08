package dev.tireless.abun.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.filterTasksForSurface
import dev.tireless.abun.icon
import dev.tireless.abun.label
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.taskListFilterEmptyState
import dev.tireless.abun.taskListFilterFromLabel
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.SwissHairlineDivider
import dev.tireless.abun.ui.components.TaskHierarchyList
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

private fun TaskStatus.isOpen(): Boolean = this == TaskStatus.PENDING || this == TaskStatus.IN_PROGRESS || this == TaskStatus.UNKNOWN

@Composable
internal fun TaskListScreen(
    state: AppUiState,
    isPomodoroActive: Boolean,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
) {
    val allTasks = state.taskView.tasks
    val openTasks = remember(allTasks) { allTasks.filter { it.status.isOpen() } }
    val rootTasks = remember(openTasks) { openTasks.filter { it.parentId == null } }
    val backlogTasks = remember(allTasks) {
        allTasks.filter { it.status.isOpen() && it.startNotBefore == null && it.endNotAfter == null }
    }
    val filteredTasks = filterTasksForSurface(allTasks, state.selectedTaskFilter)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        // Monospace filter & summary strip directly on the canvas
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ALL · ${allTasks.size}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = ThemeTokens.colors.textPrimary,
                    ),
                )
                Text(
                    text = "OPEN · ${rootTasks.size}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = ThemeTokens.colors.tasksAccent,
                    ),
                )
                Text(
                    text = "BACKLOG · ${backlogTasks.size}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = ThemeTokens.colors.textSecondary,
                    ),
                )
            }
            SwissHairlineDivider()
        }

        // Segmented filter buttons row
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

        // Multi-level hierarchy panel in SwissBentoCard
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tasks-panel-list"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "TASKS · MULTI-LEVEL HIERARCHY",
                accentDotColor = ThemeTokens.colors.tasksAccent,
                trailingText = "${allTasks.size} ITEMS",
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (filteredTasks.isEmpty()) {
                Text(
                    text = taskListFilterEmptyState(state.selectedTaskFilter),
                    style = ThemeTokens.type.bodyMuted,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                TaskHierarchyList(
                    tasks = filteredTasks,
                    allTasks = allTasks,
                    disabled = isPomodoroActive,
                    onOpenTask = onOpenTask,
                )
            }
        }
    }
}
