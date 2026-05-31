package dev.tireless.abun.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncDomainTest {
    @Test
    fun `hlc tokens compare lexicographically`() {
        assertTrue(
            HlcToken.parse("1715959378000-0001-deviceA") >
                HlcToken.parse("1715959378000-0000-deviceB"),
        )
    }

    @Test
    fun `hlc generator advances logical clock when time stalls`() {
        val clock = HybridLogicalClock(nodeId = "deviceA") { 1715959378000L }

        val first = clock.next()
        val second = clock.next()

        assertEquals("1715959378000-0000-deviceA", first)
        assertEquals("1715959378000-0001-deviceA", second)
    }

    @Test
    fun `field resolver rejects older updates`() {
        assertFalse(
            SyncConflictResolver.shouldAcceptIncoming(
                incomingHlc = "1715959378000-0000-deviceA",
                existingHlc = "1715959378000-0001-deviceB",
            ),
        )
    }

    @Test
    fun `task status is derived from latest event`() {
        val status = TaskStatusDeriver.fromEvents(
            listOf(
                SyncTaskEvent(
                    id = "1",
                    taskId = "task-1",
                    journalDate = "2026-05-24",
                    eventType = TaskEventType.CREATED,
                    eventTime = "2026-05-24T10:00:00Z",
                ),
                SyncTaskEvent(
                    id = "2",
                    taskId = "task-1",
                    journalDate = "2026-05-28",
                    eventType = TaskEventType.COMPLETED,
                    eventTime = "2026-05-28T10:00:00Z",
                ),
            ),
        )

        assertEquals(TaskStatus.COMPLETED, status)
    }

    @Test
    fun `postponed payload keeps both previous and next planning windows`() {
        val event = SyncTaskEvent(
            id = "event-1",
            taskId = "task-1",
            journalDate = "2026-05-24",
            eventType = TaskEventType.POSTPONED,
            postponed = TaskPostponedPayload(
                previousStartNotBefore = "2026-05-24T09:00:00Z",
                newStartNotBefore = "2026-05-25T09:00:00Z",
                previousEndNotAfter = "2026-05-24T17:00:00Z",
                newEndNotAfter = "2026-05-25T17:00:00Z",
            ),
            eventTime = "2026-05-24T08:00:00Z",
        )

        assertEquals("2026-05-24T09:00:00Z", event.postponed?.previousStartNotBefore)
        assertEquals("2026-05-25T09:00:00Z", event.postponed?.newStartNotBefore)
        assertEquals("2026-05-24T17:00:00Z", event.postponed?.previousEndNotAfter)
        assertEquals("2026-05-25T17:00:00Z", event.postponed?.newEndNotAfter)
    }
}
