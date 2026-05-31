package dev.tireless.abun

import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.sync.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskSurfaceGroupingTest {
    @Test
    fun `task surface groups backlog scheduled and completed tasks`() {
        val tasks = listOf(
            TaskListItemView(id = "task-1", title = "Backlog task", status = TaskStatus.PENDING),
            TaskListItemView(
                id = "task-2",
                title = "Scheduled task",
                status = TaskStatus.IN_PROGRESS,
                startNotBefore = "2026-05-26T09:00:00Z",
            ),
            TaskListItemView(
                id = "task-3",
                title = "Completed scheduled task",
                status = TaskStatus.COMPLETED,
                endNotAfter = "2026-05-26T17:00:00Z",
            ),
        )

        val grouped = groupTasksForSurface(tasks)

        assertEquals(listOf("task-1"), grouped.backlog.map { it.id })
        assertEquals(listOf("task-2"), grouped.scheduled.map { it.id })
        assertEquals(listOf("task-3"), grouped.completed.map { it.id })
    }

    @Test
    fun `task surface filters active backlog scheduled routine and completed views`() {
        val tasks = listOf(
            TaskListItemView(id = "task-1", title = "Backlog task", status = TaskStatus.PENDING),
            TaskListItemView(
                id = "task-2",
                title = "Scheduled task",
                status = TaskStatus.IN_PROGRESS,
                startNotBefore = "2026-05-26T09:00:00Z",
            ),
            TaskListItemView(
                id = "task-3",
                title = "Routine occurrence",
                status = TaskStatus.PENDING,
                startNotBefore = "2026-05-27T09:00:00Z",
                routineId = "routine-1",
            ),
            TaskListItemView(
                id = "task-4",
                title = "Completed task",
                status = TaskStatus.COMPLETED,
            ),
        )

        assertEquals(listOf("task-1", "task-2", "task-3"), filterTasksForSurface(tasks, TaskListFilter.ALL_ACTIVE).map { it.id })
        assertEquals(listOf("task-1"), filterTasksForSurface(tasks, TaskListFilter.BACKLOG).map { it.id })
        assertEquals(listOf("task-2", "task-3"), filterTasksForSurface(tasks, TaskListFilter.SCHEDULED).map { it.id })
        assertEquals(listOf("task-3"), filterTasksForSurface(tasks, TaskListFilter.ROUTINE_DERIVED).map { it.id })
        assertEquals(listOf("task-4"), filterTasksForSurface(tasks, TaskListFilter.COMPLETED).map { it.id })
    }
}
