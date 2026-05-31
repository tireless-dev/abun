package dev.tireless.abun.app

import kotlinx.datetime.*

enum class RecurrenceFrequency {
    DAILY, WEEKLY
}

data class StructuredRecurrence(
    val frequency: RecurrenceFrequency,
    val byDay: List<String> = emptyList(), // MO, TU, WE, TH, FR, SA, SU
    val byHour: Int? = null,
    val byMinute: Int? = null
) {
    fun toRRule(): String {
        val parts = mutableListOf<String>()
        parts.add("FREQ=${frequency.name}")
        if (frequency == RecurrenceFrequency.WEEKLY && byDay.isNotEmpty()) {
            parts.add("BYDAY=${byDay.joinToString(",")}")
        }
        if (byHour != null) {
            parts.add("BYHOUR=$byHour")
        }
        if (byMinute != null) {
            parts.add("BYMINUTE=$byMinute")
        }
        return "RRULE:${parts.joinToString(";")}"
    }

    fun nextOccurrence(after: LocalDateTime): LocalDateTime {
        val tz = TimeZone.currentSystemDefault()
        var currentInstant = after.toInstant(tz).plus(1, DateTimeUnit.MINUTE)

        // Simple search for next matching minute, optimized slightly
        repeat(10000) { // Safety limit
            val dt = currentInstant.toLocalDateTime(tz)
            if (matches(dt)) return dt

            val nextInstant = when {
                byMinute != null && dt.minute != byMinute -> {
                    val diff = (byMinute - dt.minute + 60) % 60
                    currentInstant.plus(if (diff == 0) 60 else diff, DateTimeUnit.MINUTE)
                }
                byHour != null && dt.hour != byHour -> {
                    val diff = (byHour - dt.hour + 24) % 24
                    currentInstant.plus(if (diff == 0) 24 else diff, DateTimeUnit.HOUR)
                }
                frequency == RecurrenceFrequency.WEEKLY && byDay.isNotEmpty() -> {
                    currentInstant.plus(1, DateTimeUnit.DAY, tz)
                }
                else -> currentInstant.plus(1, DateTimeUnit.MINUTE)
            }
            currentInstant = nextInstant
        }
        return currentInstant.toLocalDateTime(tz)
    }

    private fun matches(dt: LocalDateTime): Boolean {
        if (byHour != null && dt.hour != byHour) return false
        if (byMinute != null && dt.minute != byMinute) return false
        if (frequency == RecurrenceFrequency.WEEKLY && byDay.isNotEmpty()) {
            val day = when (dt.dayOfWeek) {
                DayOfWeek.MONDAY -> "MO"
                DayOfWeek.TUESDAY -> "TU"
                DayOfWeek.WEDNESDAY -> "WE"
                DayOfWeek.THURSDAY -> "TH"
                DayOfWeek.FRIDAY -> "FR"
                DayOfWeek.SATURDAY -> "SA"
                DayOfWeek.SUNDAY -> "SU"
            }
            if (!byDay.contains(day)) return false
        }
        return true
    }

    companion object {
        fun fromRRule(rule: String): StructuredRecurrence {
            val normalized = rule.removePrefix("RRULE:")
            val parts = normalized.split(";")
                .mapNotNull { entry ->
                    val separator = entry.indexOf('=')
                    if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
                }
                .toMap()

            val freq = when (parts["FREQ"]) {
                "WEEKLY" -> RecurrenceFrequency.WEEKLY
                else -> RecurrenceFrequency.DAILY
            }

            val byDay = parts["BYDAY"]?.split(",") ?: emptyList()
            val byHour = parts["BYHOUR"]?.toIntOrNull()
            val byMinute = parts["BYMINUTE"]?.toIntOrNull()

            return StructuredRecurrence(
                frequency = freq,
                byDay = byDay,
                byHour = byHour,
                byMinute = byMinute
            )
        }
    }
}
