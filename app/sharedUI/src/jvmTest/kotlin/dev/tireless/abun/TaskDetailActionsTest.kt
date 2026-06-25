package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.sheets.TaskActionsSheet
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskDetailActionsTest {
    @Test
    fun `open task detail actions use progress complete pomodoro and delete`() {
        val labels = taskDetailActionLabels(
            TaskListItemView(id = "task-1", title = "Open task", status = TaskStatus.IN_PROGRESS),
        )

        assertEquals(listOf("Progress", "Complete", "Postpone", "Pomodoro", "Delete task"), labels)
    }

    @Test
    fun `closed task detail actions collapse to delete only`() {
        val labels = taskDetailActionLabels(
            TaskListItemView(id = "task-2", title = "Closed task", status = TaskStatus.COMPLETED),
        )

        assertEquals(listOf("Delete task"), labels)
    }

    @Test
    fun `routine derived open task uses occurrence action set`() {
        val labels = taskDetailActionLabels(
            TaskListItemView(
                id = "task-3",
                title = "Routine occurrence",
                routineId = "routine-1",
                status = TaskStatus.PENDING,
            ),
        )

        assertEquals(listOf("Complete", "Postpone", "Skip", "Pomodoro"), labels)
    }

    @Test
    fun `routine derived task hides execution actions after rollover`() {
        val labels = taskDetailActionLabels(
            TaskListItemView(
                id = "task-4",
                title = "Expired occurrence",
                routineId = "routine-1",
                routineCanExecute = false,
                routineCanSkip = false,
                status = TaskStatus.PENDING,
            ),
        )

        assertEquals(emptyList(), labels)
    }

    @Test
    fun `routine derived task hides postpone at next occurrence boundary`() {
        val labels = taskDetailActionLabels(
            TaskListItemView(
                id = "task-5",
                title = "Boundary occurrence",
                routineId = "routine-1",
                routineCanPostpone = false,
                status = TaskStatus.PENDING,
            ),
        )

        assertEquals(listOf("Complete", "Skip", "Pomodoro"), labels)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `task detail opens read only and enters edit mode in place`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                TaskActionsSheet(
                    task = sampleTask(),
                    history = sampleHistory(),
                    availableParents = emptyList(),
                    availableRoutines = emptyList(),
                    isPomodoroActive = false,
                    onDismiss = {},
                    onSaveTask = { _, _, _, _, _, _, _ -> },
                    onProgress = {},
                    onComplete = {},
                    onSkip = {},
                    onPostpone = { _, _, _, _, _ -> },
                    onDelete = {},
                    onStartPomodoro = {},
                )
            }
        }

        onAllNodesWithText("Title").assertCountEquals(0)
        onNodeWithContentDescription("Edit task").assertExists()
        onAllNodesWithText("Save").assertCountEquals(0)
        onAllNodesWithText("Progress").assertCountEquals(0)
        onAllNodesWithText("Complete").assertCountEquals(0)
        onAllNodesWithText("Postpone").assertCountEquals(0)
        onAllNodesWithText("Pomodoro").assertCountEquals(0)
        onAllNodesWithText("Delete").assertCountEquals(0)

        onNodeWithContentDescription("Edit task").performClick()

        onNodeWithText("Title").assertExists()
        onNodeWithText("Save").assertExists()
        onNodeWithText("Cancel").assertExists()
        onNodeWithText("Delete").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `delete requires confirmation before callback`() = runDesktopComposeUiTest {
        var deleteCount = 0

        setContent {
            AppTheme {
                TaskActionsSheet(
                    task = sampleTask(),
                    history = sampleHistory(),
                    availableParents = emptyList(),
                    availableRoutines = emptyList(),
                    isPomodoroActive = false,
                    onDismiss = {},
                    onSaveTask = { _, _, _, _, _, _, _ -> },
                    onProgress = {},
                    onComplete = {},
                    onSkip = {},
                    onPostpone = { _, _, _, _, _ -> },
                    onDelete = { deleteCount += 1 },
                    onStartPomodoro = {},
                )
            }
        }

        onNodeWithContentDescription("Edit task").performClick()
        onNodeWithText("Delete").performClick()
        onNodeWithText("Confirm delete").assertExists()
        assertEquals(0, deleteCount)

        onNodeWithText("Delete task").performClick()
        assertEquals(1, deleteCount)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `task detail sheet uses eighty percent of the available height`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                TaskActionsSheet(
                    task = sampleTask(),
                    history = List(6) { sampleHistory().first().copy(eventId = "event-$it") },
                    availableParents = emptyList(),
                    availableRoutines = emptyList(),
                    isPomodoroActive = false,
                    onDismiss = {},
                    onSaveTask = { _, _, _, _, _, _, _ -> },
                    onProgress = {},
                    onComplete = {},
                    onSkip = {},
                    onPostpone = { _, _, _, _, _ -> },
                    onDelete = {},
                    onStartPomodoro = {},
                )
            }
        }

        waitForIdle()
        val rootHeight = onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.height }
        val sheetBounds = onNodeWithTag("task-detail-sheet").fetchSemanticsNode().boundsInRoot
        val sheetHeight = sheetBounds.height

        assertTrue(sheetHeight >= rootHeight * 0.78f, "sheetHeight=$sheetHeight rootHeight=$rootHeight")
        assertTrue(sheetHeight <= rootHeight * 0.82f, "sheetHeight=$sheetHeight rootHeight=$rootHeight")
        assertTrue(sheetBounds.bottom >= rootHeight - 1f, "sheetBottom=${sheetBounds.bottom} rootHeight=$rootHeight")
    }
}

private fun sampleTask() = TaskListItemView(
    id = "task-1",
    title = "Open task",
    detail = "Read only detail",
    status = TaskStatus.IN_PROGRESS,
    startNotBefore = "2026-06-24T09:00:00Z",
    estimatedDuration = "PT30M",
)

private fun sampleHistory() = listOf(
    JournalEntryView(
        taskId = "task-1",
        title = "Open task",
        eventId = "event-1",
        eventType = TaskEventType.CREATED,
        content = "Created",
        eventTimeLabel = "09:00",
    ),
)
