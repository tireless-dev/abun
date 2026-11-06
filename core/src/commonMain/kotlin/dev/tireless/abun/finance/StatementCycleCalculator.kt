package dev.tireless.abun.finance

data class StatementCycleInfo(
    val cycleTag: String,             // e.g. "2026-05"
    val statementClosingDate: String, // e.g. "2026-05-05"
    val dueDate: String,              // e.g. "2026-05-25"
    val deterministicGroupId: String, // e.g. "cg-stmt:acc-1:2026-05"
    val defaultGroupName: String,     // e.g. "招行卡 2026-05 账单"
)

object StatementCycleCalculator {

    fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    fun calculateCycle(
        accountId: String,
        accountName: String,
        txDate: String, // "YYYY-MM-DD"
        statementDay: Int, // 1..31
        dueDay: Int, // 1..31
    ): StatementCycleInfo {
        val parts = txDate.split("-")
        require(parts.size == 3) { "Invalid txDate format: $txDate, expected YYYY-MM-DD" }
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()

        // Effective statement day for this month
        val effectiveStmtDayThisMonth = statementDay.coerceAtMost(daysInMonth(year, month))

        val (cycleYear, cycleMonth) = if (day <= effectiveStmtDayThisMonth) {
            year to month
        } else {
            if (month == 12) (year + 1) to 1 else year to (month + 1)
        }

        val closingDay = statementDay.coerceAtMost(daysInMonth(cycleYear, cycleMonth))
        val closingMonthStr = cycleMonth.toString().padStart(2, '0')
        val closingDayStr = closingDay.toString().padStart(2, '0')
        val cycleTag = "$cycleYear-$closingMonthStr"
        val statementClosingDate = "$cycleYear-$closingMonthStr-$closingDayStr"

        // Determine Due Date:
        // If dueDay >= statementDay, due date is in the statement closing month
        // If dueDay < statementDay, due date is in the month following statement closing
        val (dueYear, dueMonth) = if (dueDay >= statementDay) {
            cycleYear to cycleMonth
        } else {
            if (cycleMonth == 12) (cycleYear + 1) to 1 else cycleYear to (cycleMonth + 1)
        }

        val effectiveDueDay = dueDay.coerceAtMost(daysInMonth(dueYear, dueMonth))
        val dueMonthStr = dueMonth.toString().padStart(2, '0')
        val dueDayStr = effectiveDueDay.toString().padStart(2, '0')
        val dueDate = "$dueYear-$dueMonthStr-$dueDayStr"

        val deterministicGroupId = "cg-stmt:$accountId:$cycleTag"
        val defaultGroupName = "$accountName $cycleTag 账单"

        return StatementCycleInfo(
            cycleTag = cycleTag,
            statementClosingDate = statementClosingDate,
            dueDate = dueDate,
            deterministicGroupId = deterministicGroupId,
            defaultGroupName = defaultGroupName,
        )
    }
}
