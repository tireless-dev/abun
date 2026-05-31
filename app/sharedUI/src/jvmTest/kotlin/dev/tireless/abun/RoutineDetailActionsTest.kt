package dev.tireless.abun

import kotlin.test.Test
import kotlin.test.assertEquals

class RoutineDetailActionsTest {
    @Test
    fun `routine save draft trims optional blank fields to null`() {
        val draft = normalizeRoutineSaveDraft(
            id = "routine-1",
            title = "Morning plan",
            detail = "",
            recurrenceRule = "RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0",
            defaultStartNotBefore = "",
            defaultEstimatedDuration = "",
        )

        assertEquals(
            RoutineSaveDraft(
                id = "routine-1",
                title = "Morning plan",
                detail = null,
                recurrenceRule = "RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0",
                defaultStartNotBefore = null,
                defaultEstimatedDuration = null,
            ),
            draft,
        )
    }

    @Test
    fun `daily recurrence rule is described in human readable form`() {
        assertEquals(
            "Every day at 09:00",
            describeRecurrenceRule("RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0"),
        )
    }

    @Test
    fun `weekly recurrence rule lists weekday summary`() {
        assertEquals(
            "Every Mon, Wed, Fri at 08:30",
            describeRecurrenceRule("RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;BYHOUR=8;BYMINUTE=30"),
        )
    }
}
