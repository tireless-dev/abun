package dev.tireless.abun

import dev.tireless.abun.sync.TaskEventType
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskEventPresentationTest {
    @Test
    fun `task timeline labels use explicit product wording`() {
        assertEquals("Created", taskEventLabel(TaskEventType.CREATED))
        assertEquals("Progressed", taskEventLabel(TaskEventType.PROGRESSED))
        assertEquals("Completed", taskEventLabel(TaskEventType.COMPLETED))
        assertEquals("Postponed", taskEventLabel(TaskEventType.POSTPONED))
        assertEquals("Deleted", taskEventLabel(TaskEventType.DELETED))
        assertEquals("Missed", taskEventLabel(TaskEventType.MISSED))
        assertEquals("Skipped", taskEventLabel(TaskEventType.SKIPPED))
    }
}
