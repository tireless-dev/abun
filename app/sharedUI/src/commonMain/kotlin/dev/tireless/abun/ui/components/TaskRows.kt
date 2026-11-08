package dev.tireless.abun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.SwissHairlineDivider
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
        Text(empty, style = ThemeTokens.type.bodyMuted)
        return
    }
    TaskHierarchyList(
        tasks = tasks,
        allTasks = tasks,
        onOpenTask = onOpenTask,
        disabled = disabled,
    )
}

@Composable
internal fun TaskHierarchyList(
    tasks: List<TaskListItemView>,
    allTasks: List<TaskListItemView> = tasks,
    onOpenTask: (TaskListItemView) -> Unit,
    disabled: Boolean = false,
    expandedTaskIds: Set<String>? = null,
    onToggleExpand: ((String) -> Unit)? = null,
) {
    val tasksById = remember(allTasks) { allTasks.associateBy { it.id } }
    val childrenByParentId = remember(allTasks) {
        allTasks.filter { it.parentId != null }.groupBy { it.parentId!! }
    }
    val defaultExpanded = remember(allTasks) {
        allTasks.mapNotNull { it.parentId }.toSet()
    }
    var localExpandedIds by remember(allTasks) { mutableStateOf(defaultExpanded) }

    val effectiveExpanded = expandedTaskIds ?: localExpandedIds
    val effectiveToggle: (String) -> Unit = onToggleExpand ?: { id ->
        localExpandedIds = if (id in localExpandedIds) localExpandedIds - id else localExpandedIds + id
    }

    val visibleTaskIds = remember(tasks) { tasks.map { it.id }.toSet() }
    val rootTasks = remember(tasks, allTasks) {
        tasks.filter { it.parentId == null || it.parentId !in visibleTaskIds }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        rootTasks.forEachIndexed { index, task ->
            if (index > 0) {
                SwissHairlineDivider()
            }
            TaskTreeNode(
                task = task,
                depth = 0,
                childrenByParentId = childrenByParentId,
                expandedTaskIds = effectiveExpanded,
                onToggleExpand = effectiveToggle,
                onOpenTask = onOpenTask,
                disabled = disabled,
            )
        }
    }
}

@Composable
private fun TaskTreeNode(
    task: TaskListItemView,
    depth: Int,
    childrenByParentId: Map<String, List<TaskListItemView>>,
    expandedTaskIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
    disabled: Boolean,
) {
    val children = childrenByParentId[task.id].orEmpty()
    val hasChildren = children.isNotEmpty()
    val isExpanded = task.id in expandedTaskIds

    TaskRow(
        task = task,
        depth = depth,
        hasChildren = hasChildren,
        isExpanded = isExpanded,
        onToggleExpand = { onToggleExpand(task.id) },
        onOpenTask = onOpenTask,
        disabled = disabled,
    )

    if (hasChildren && isExpanded) {
        children.forEach { child ->
            SwissHairlineDivider()
            TaskTreeNode(
                task = child,
                depth = depth + 1,
                childrenByParentId = childrenByParentId,
                expandedTaskIds = expandedTaskIds,
                onToggleExpand = onToggleExpand,
                onOpenTask = onOpenTask,
                disabled = disabled,
            )
        }
    }
}

@Composable
internal fun TaskRow(
    task: TaskListItemView,
    depth: Int = 0,
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onOpenTask: (TaskListItemView) -> Unit,
    disabled: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val timeblockBadge = formatTimeblockBadge(task.startNotBefore, task.endNotAfter, task.estimatedDuration)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-row-${task.id}")
            .clickable(enabled = !disabled) { onOpenTask(task) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (depth > 0) {
            Spacer(modifier = Modifier.width((depth * 14).dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ThemeTokens.colors.tasksAccent),
            )
        }

        if (hasChildren) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .testTag("task-row-expand-${task.id}")
                    .clip(CircleShape)
                    .clickable { onToggleExpand() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isExpanded) Lucide.ChevronDown else Lucide.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse subtasks" else "Expand subtasks",
                    tint = ThemeTokens.colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            TaskStateIndicator(task.status)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = task.title,
                style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Medium),
                color = ThemeTokens.colors.textPrimary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hasChildren) {
                    TaskStateIndicator(task.status)
                }

                if (timeblockBadge != null) {
                    val isActive = task.status == TaskStatus.IN_PROGRESS
                    Surface(
                        color = if (isActive) ThemeTokens.colors.tasksSoft else ThemeTokens.colors.surfaceMuted,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = timeblockBadge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isActive) ThemeTokens.colors.tasksAccent else ThemeTokens.colors.textSecondary,
                            ),
                        )
                    }
                }

                if (task.routineId != null) {
                    Surface(
                        color = ThemeTokens.colors.surfaceMuted,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = "ROUTINE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = ThemeTokens.colors.textSecondary,
                            ),
                        )
                    }
                }

                if (disabled) {
                    Text(
                        text = "Pomodoro active",
                        style = ThemeTokens.type.label,
                        color = ThemeTokens.colors.textSecondary,
                    )
                }
            }
        }

        Box {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .testTag("task-row-overflow-${task.id}")
                    .clip(CircleShape)
                    .clickable { menuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = "Actions",
                    tint = ThemeTokens.colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                val actions = listOf("Start", "Complete", "Postpone", "Cancel", "Add Subtask", "Edit", "Delete", "History")
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action, style = ThemeTokens.type.body) },
                        onClick = {
                            menuExpanded = false
                            onOpenTask(task)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskStateIndicator(status: TaskStatus) {
    val colors = editorialStatusColors(status, ThemeTokens.colors)
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(colors.content),
    )
}

@Composable
internal fun StatusPill(status: TaskStatus) {
    TaskStateIndicator(status = status)
}

internal fun formatTimeblockBadge(start: String?, end: String?, duration: String?): String? {
    fun cleanTime(iso: String): String {
        return if (iso.contains("T")) {
            iso.substringAfter("T").take(5)
        } else if (iso.contains(" ")) {
            iso.substringAfter(" ").take(5)
        } else {
            iso
        }
    }
    val s = start?.takeIf { it.isNotBlank() }?.let(::cleanTime)
    val e = end?.takeIf { it.isNotBlank() }?.let(::cleanTime)
    val dur = duration?.takeIf { it.isNotBlank() }

    val range = when {
        s != null && e != null -> "$s-$e"
        s != null -> s
        e != null -> e
        else -> null
    }

    return when {
        range != null && dur != null -> "$range · $dur"
        range != null -> range
        dur != null -> dur
        else -> null
    }
}
