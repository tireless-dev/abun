package dev.tireless.abun.finance

import kotlin.test.Test
import kotlin.test.assertEquals

class StatementCycleTest {

    @Test
    fun `transaction before or on statement day falls into current month cycle`() {
        val infoBefore = StatementCycleCalculator.calculateCycle(
            accountId = "acc-cc-cmb",
            accountName = "招行信用卡",
            txDate = "2026-05-04",
            statementDay = 5,
            dueDay = 25,
        )

        assertEquals("2026-05", infoBefore.cycleTag)
        assertEquals("2026-05-05", infoBefore.statementClosingDate)
        assertEquals("2026-05-25", infoBefore.dueDate)
        assertEquals("cg-stmt:acc-cc-cmb:2026-05", infoBefore.deterministicGroupId)
        assertEquals("招行信用卡 2026-05 账单", infoBefore.defaultGroupName)

        val infoOn = StatementCycleCalculator.calculateCycle(
            accountId = "acc-cc-cmb",
            accountName = "招行信用卡",
            txDate = "2026-05-05",
            statementDay = 5,
            dueDay = 25,
        )
        assertEquals("2026-05", infoOn.cycleTag)
        assertEquals("2026-05-05", infoOn.statementClosingDate)
    }

    @Test
    fun `transaction after statement day rolls to next month cycle`() {
        val infoAfter = StatementCycleCalculator.calculateCycle(
            accountId = "acc-cc-cmb",
            accountName = "招行信用卡",
            txDate = "2026-05-06",
            statementDay = 5,
            dueDay = 25,
        )

        assertEquals("2026-06", infoAfter.cycleTag)
        assertEquals("2026-06-05", infoAfter.statementClosingDate)
        assertEquals("2026-06-25", infoAfter.dueDate)
        assertEquals("cg-stmt:acc-cc-cmb:2026-06", infoAfter.deterministicGroupId)
    }

    @Test
    fun `year boundary rollover across december and january`() {
        val infoYearEnd = StatementCycleCalculator.calculateCycle(
            accountId = "acc-cc-cmb",
            accountName = "招行信用卡",
            txDate = "2026-12-10",
            statementDay = 5,
            dueDay = 25,
        )

        assertEquals("2027-01", infoYearEnd.cycleTag)
        assertEquals("2027-01-05", infoYearEnd.statementClosingDate)
        assertEquals("2027-01-25", infoYearEnd.dueDate)
        assertEquals("cg-stmt:acc-cc-cmb:2027-01", infoYearEnd.deterministicGroupId)
    }

    @Test
    fun `due day before statement day rolls due date into following month`() {
        // Statement day 20, Due day 10 (repay by 10th of next month)
        val info = StatementCycleCalculator.calculateCycle(
            accountId = "acc-cc-icbc",
            accountName = "工行信用卡",
            txDate = "2026-05-15",
            statementDay = 20,
            dueDay = 10,
        )

        assertEquals("2026-05", info.cycleTag)
        assertEquals("2026-05-20", info.statementClosingDate)
        assertEquals("2026-06-10", info.dueDate)
    }

    @Test
    fun `month end day clamping for shorter months like february`() {
        // Statement on 31st, February 2026 (non-leap: 28 days)
        val infoFeb = StatementCycleCalculator.calculateCycle(
            accountId = "acc-cc-boc",
            accountName = "中行信用卡",
            txDate = "2026-02-10",
            statementDay = 31,
            dueDay = 20,
        )

        assertEquals("2026-02", infoFeb.cycleTag)
        assertEquals("2026-02-28", infoFeb.statementClosingDate)
    }
}
