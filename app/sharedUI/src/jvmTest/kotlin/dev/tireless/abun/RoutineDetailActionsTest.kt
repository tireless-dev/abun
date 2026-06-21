package dev.tireless.abun

import dev.tireless.abun.ui.sheets.RoutineRecurrenceEditorState
import dev.tireless.abun.ui.sheets.RoutineRecurrencePreset
import dev.tireless.abun.ui.sheets.RoutineSaveDraft
import dev.tireless.abun.ui.sheets.buildRecurrenceRule
import dev.tireless.abun.ui.sheets.describeRecurrenceRule
import dev.tireless.abun.ui.sheets.normalizeRoutineSaveDraft
import dev.tireless.abun.ui.sheets.recurrenceEditorStateFor
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

    @Test
    fun `daily recurrence rule maps to daily preset editor state`() {
        assertEquals(
            RoutineRecurrenceEditorState(
                preset = RoutineRecurrencePreset.DAILY,
                time = "09:00",
                customRule = "RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0",
            ),
            recurrenceEditorStateFor("RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0"),
        )
    }

    @Test
    fun `weekday recurrence rule maps to weekdays preset editor state`() {
        assertEquals(
            RoutineRecurrenceEditorState(
                preset = RoutineRecurrencePreset.WEEKDAYS,
                time = "08:30",
                customRule = "RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;BYHOUR=8;BYMINUTE=30",
            ),
            recurrenceEditorStateFor("RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;BYHOUR=8;BYMINUTE=30"),
        )
    }

    @Test
    fun `unrecognized recurrence rule falls back to custom preset`() {
        assertEquals(
            RoutineRecurrenceEditorState(
                preset = RoutineRecurrencePreset.CUSTOM,
                time = "",
                customRule = "RRULE:FREQ=MONTHLY;BYMONTHDAY=1;BYHOUR=7;BYMINUTE=15",
            ),
            recurrenceEditorStateFor("RRULE:FREQ=MONTHLY;BYMONTHDAY=1;BYHOUR=7;BYMINUTE=15"),
        )
    }

    @Test
    fun `daily preset builds deterministic rrule`() {
        assertEquals(
            "RRULE:FREQ=DAILY;BYHOUR=7;BYMINUTE=15",
            buildRecurrenceRule(
                RoutineRecurrenceEditorState(
                    preset = RoutineRecurrencePreset.DAILY,
                    time = "07:15",
                    customRule = "",
                ),
            ),
        )
    }

    @Test
    fun `weekdays preset builds deterministic rrule`() {
        assertEquals(
            "RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;BYHOUR=18;BYMINUTE=05",
            buildRecurrenceRule(
                RoutineRecurrenceEditorState(
                    preset = RoutineRecurrencePreset.WEEKDAYS,
                    time = "18:05",
                    customRule = "",
                ),
            ),
        )
    }
}
