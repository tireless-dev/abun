package dev.tireless.abun

import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.sync.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `routine derived task hides skip after rollover`() {
        val labels = taskDetailActionLabels(
            TaskListItemView(
                id = "task-4",
                title = "Expired occurrence",
                routineId = "routine-1",
                routineCanSkip = false,
                status = TaskStatus.PENDING,
            ),
        )

        assertEquals(listOf("Complete", "Postpone", "Pomodoro"), labels)
    }
}
