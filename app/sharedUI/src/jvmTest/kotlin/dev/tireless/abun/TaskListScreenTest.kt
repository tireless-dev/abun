package dev.tireless.abun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.app.TaskViewState
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.screens.TasksScreen
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TaskListScreenTest {
    @Test
    fun `task list screen keeps only selected filter label visible`() = runDesktopComposeUiTest {
        setContent {
            var selectedFilter by mutableStateOf(TaskListFilter.ALL_ACTIVE)
            AppTheme {
                TasksScreen(
                    state = screenshotState(
                        selectedTab = AppTab.TASKS,
                        selectedTaskSubTab = TaskSubTab.TASKS,
                        taskView = TaskViewState(
                            tasks = listOf(
                                TaskListItemView(
                                    id = "backlog-task",
                                    title = "Inbox cleanup",
                                    status = TaskStatus.PENDING,
                                ),
                                TaskListItemView(
                                    id = "scheduled-task",
                                    title = "Plan next sprint",
                                    status = TaskStatus.IN_PROGRESS,
                                    startNotBefore = "2026-06-21T09:00:00Z",
                                ),
                            ),
                        ),
                    ).copy(selectedTaskFilter = selectedFilter),
                    liveNow = ScreenshotNow,
                    isPomodoroActive = false,
                    onSelectTaskFilter = { selectedFilter = it },
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                )
            }
        }

        onAllNodesWithText("Task list").assertCountEquals(0)
        onAllNodesWithText("All active").assertCountEquals(1)
        onAllNodesWithText("Backlog").assertCountEquals(0)

        onNodeWithContentDescription("Backlog").performClick()

        onAllNodesWithText("Backlog").assertCountEquals(1)
        onNodeWithText("Inbox cleanup").fetchSemanticsNode()
    }

    @Test
    fun `task list opens detail from clickable card without status text or cta copy`() = runDesktopComposeUiTest {
        var openedTaskId: String? = null

        setContent {
            AppTheme {
                TasksScreen(
                    state = screenshotState(
                        selectedTab = AppTab.TASKS,
                        selectedTaskSubTab = TaskSubTab.TASKS,
                        taskView = TaskViewState(
                            tasks = listOf(
                                TaskListItemView(
                                    id = "task-1",
                                    title = "Inbox cleanup",
                                    status = TaskStatus.PENDING,
                                ),
                            ),
                        ),
                    ),
                    liveNow = ScreenshotNow,
                    isPomodoroActive = false,
                    onSelectTaskFilter = {},
                    onOpenTask = { openedTaskId = it.id },
                    onOpenStartPomodoro = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                )
            }
        }

        onNodeWithTag("task-row-task-1").performClick()
        onAllNodesWithText("Click to open detail").assertCountEquals(0)
        onAllNodesWithText("Manage").assertCountEquals(0)
        onAllNodesWithText("Pending").assertCountEquals(0)
        kotlin.test.assertEquals("task-1", openedTaskId)
    }
}
