package dev.tireless.abun

import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.sheets.DurationPreset
import dev.tireless.abun.ui.sheets.TaskCreateContext
import dev.tireless.abun.ui.sheets.TaskCreateDraft
import dev.tireless.abun.ui.sheets.TaskCreateSource
import dev.tireless.abun.ui.sheets.defaultTaskCreateDraft
import dev.tireless.abun.ui.sheets.normalizeTaskCreateDraft
import dev.tireless.abun.ui.sheets.normalizeTaskSaveDraft
import dev.tireless.abun.ui.sheets.taskCreateContextFor
import dev.tireless.abun.ui.sheets.taskCreateStartOfDayIso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskSaveDraftTest {
    @Test
    fun `normalize task save draft trims title and converts blank planning fields to null`() {
        val draft = normalizeTaskSaveDraft(
            title = "  Review launch checklist  ",
            detail = "",
            parentId = "task-1",
            startNotBefore = "",
            endNotAfter = "2026-05-30T17:00:00Z",
            estimatedDuration = "",
        )

        assertEquals("Review launch checklist", draft.title)
        assertNull(draft.detail)
        assertEquals("task-1", draft.parentId)
        assertNull(draft.startNotBefore)
        assertEquals("2026-05-30T17:00:00Z", draft.endNotAfter)
        assertNull(draft.estimatedDuration)
    }

    @Test
    fun `task create draft defaults to backlog in tasks context`() {
        val draft = defaultTaskCreateDraft(
            TaskCreateContext(
                source = TaskCreateSource.TASKS,
                selectedDate = "2026-06-11",
            ),
        )

        assertFalse(draft.hasSchedule)
        assertNull(draft.startDate)
        assertEquals(DurationPreset.NONE, draft.durationPreset)
    }

    @Test
    fun `task create draft defaults to selected day schedule in day context`() {
        val draft = defaultTaskCreateDraft(
            TaskCreateContext(
                source = TaskCreateSource.DAY,
                selectedDate = "2026-06-11",
            ),
        )

        assertTrue(draft.hasSchedule)
        assertEquals("2026-06-11", draft.startDate)
        assertEquals(DurationPreset.NONE, draft.durationPreset)
    }

    @Test
    fun `task create normalization clears planning fields when schedule is off`() {
        val draft = normalizeTaskCreateDraft(
            TaskCreateDraft(
                title = "  Inbox task  ",
                detail = "  ",
                hasSchedule = false,
                startDate = "2026-06-11",
                durationPreset = DurationPreset.HOUR_1,
                customDurationMinutes = "",
            ),
        )

        assertEquals("Inbox task", draft.title)
        assertNull(draft.startNotBefore)
        assertNull(draft.endNotAfter)
        assertNull(draft.estimatedDuration)
    }

    @Test
    fun `task create normalization converts schedule and preset duration to task fields`() {
        val draft = normalizeTaskCreateDraft(
            TaskCreateDraft(
                title = "Plan day",
                detail = "Important",
                hasSchedule = true,
                startDate = "2026-06-11",
                durationPreset = DurationPreset.HOUR_2,
                customDurationMinutes = "",
            ),
        )

        assertEquals("Plan day", draft.title)
        assertEquals("Important", draft.detail)
        assertEquals(taskCreateStartOfDayIso("2026-06-11"), draft.startNotBefore)
        assertNull(draft.endNotAfter)
        assertEquals("PT2H", draft.estimatedDuration)
    }

    @Test
    fun `task create normalization converts custom duration minutes to iso duration`() {
        val draft = normalizeTaskCreateDraft(
            TaskCreateDraft(
                title = "Focus block",
                detail = "",
                hasSchedule = true,
                startDate = "2026-06-11",
                durationPreset = DurationPreset.CUSTOM,
                customDurationMinutes = "45",
            ),
        )

        assertEquals("PT45M", draft.estimatedDuration)
    }

    @Test
    fun `task create context is derived from selected tab`() {
        assertEquals(
            TaskCreateContext(TaskCreateSource.DAY, "2026-06-11"),
            taskCreateContextFor(AppTab.TODAY, "2026-06-11"),
        )
        assertEquals(
            TaskCreateContext(TaskCreateSource.TASKS, "2026-06-11"),
            taskCreateContextFor(AppTab.TASKS, "2026-06-11"),
        )
    }
}
