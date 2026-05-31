package dev.tireless.abun.app

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RecurrenceTest {
    @Test
    fun testDailyRRule() {
        val rrule = "RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0"
        val structured = StructuredRecurrence.fromRRule(rrule)
        assertEquals(RecurrenceFrequency.DAILY, structured.frequency)
        assertEquals(9, structured.byHour)
        assertEquals(0, structured.byMinute)
        assertEquals(rrule, structured.toRRule())

        val after = LocalDateTime(2026, 5, 25, 8, 0)
        val next = structured.nextOccurrence(after)
        assertEquals(LocalDateTime(2026, 5, 25, 9, 0), next)

        val afterLate = LocalDateTime(2026, 5, 25, 10, 0)
        val nextDay = structured.nextOccurrence(afterLate)
        assertEquals(LocalDateTime(2026, 5, 26, 9, 0), nextDay)
    }

    @Test
    fun testWeeklyRRule() {
        val rrule = "RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;BYHOUR=10;BYMINUTE=30"
        val structured = StructuredRecurrence.fromRRule(rrule)
        assertEquals(RecurrenceFrequency.WEEKLY, structured.frequency)
        assertEquals(listOf("MO", "WE", "FR"), structured.byDay)
        assertEquals(10, structured.byHour)
        assertEquals(30, structured.byMinute)
        assertEquals(rrule, structured.toRRule())

        // 2026-05-25 is Monday
        val after = LocalDateTime(2026, 5, 25, 8, 0)
        val next = structured.nextOccurrence(after)
        assertEquals(LocalDateTime(2026, 5, 25, 10, 30), next)

        val afterMondayLate = LocalDateTime(2026, 5, 25, 11, 0)
        val nextWed = structured.nextOccurrence(afterMondayLate)
        assertEquals(LocalDateTime(2026, 5, 27, 10, 30), nextWed)
    }

    @Test
    fun testPartialRRule() {
        val rrule = "RRULE:FREQ=DAILY"
        val structured = StructuredRecurrence.fromRRule(rrule)
        assertEquals(RecurrenceFrequency.DAILY, structured.frequency)
        assertEquals(null, structured.byHour)
        assertEquals(null, structured.byMinute)
        assertEquals(rrule, structured.toRRule())
    }
}
