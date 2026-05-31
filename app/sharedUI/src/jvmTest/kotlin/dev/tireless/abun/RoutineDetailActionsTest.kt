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
}
