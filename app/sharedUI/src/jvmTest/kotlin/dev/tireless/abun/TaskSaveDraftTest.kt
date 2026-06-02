package dev.tireless.abun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
