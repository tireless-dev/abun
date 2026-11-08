package dev.tireless.abun.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CashflowRunwayTest {

    @Test
    fun `nextDay handles month ends and leap year correctly`() {
        assertEquals("2026-02-28", CashflowRunwayEngine.nextDay("2026-02-27"))
        assertEquals("2026-03-01", CashflowRunwayEngine.nextDay("2026-02-28")) // Non-leap year
        assertEquals("2024-02-29", CashflowRunwayEngine.nextDay("2024-02-28")) // Leap year
        assertEquals("2024-03-01", CashflowRunwayEngine.nextDay("2024-02-29"))
        assertEquals("2026-12-31", CashflowRunwayEngine.nextDay("2026-12-30"))
        assertEquals("2027-01-01", CashflowRunwayEngine.nextDay("2026-12-31"))
    }

    @Test
    fun `identifies deficit date when credit statement obligation exceeds cash before payday`() {
        // Initial liquid cash: 5,000 CNY (500,000 minor)
        val initialCash = 500000L
        val liquidAccountIds = setOf("acc-bank")

        // Statement due on Nov 10 for 6,000 CNY (-600,000 minor)
        val stmt = FinanceClearingGroup(
            id = "cg-stmt-cc",
            name = "信用卡11月账单",
            groupType = ClearingGroupType.CREDIT_STATEMENT,
            dueDate = "2026-11-10",
            status = ClearingGroupStatus.ACTIVE,
        )

        // Salary payday scheduled on Nov 15: +15,000 CNY (+1,500,000 minor)
        val salaryTx = FinanceTransaction(
            id = "tx-salary",
            txDate = "2026-11-15",
            status = TransactionStatus.SCHEDULED,
        )
        val salarySplit = FinanceSplit(
            id = "s-sal",
            transactionId = "tx-salary",
            accountId = "acc-bank",
            amountMinor = 1500000L,
        )

        val projection = CashflowRunwayEngine.projectRunway(
            currentDate = "2026-11-05",
            horizonDays = 30,
            currentLiquidCashMinor = initialCash,
            scheduledTransactions = listOf(salaryTx),
            splitsByTx = mapOf("tx-salary" to listOf(salarySplit)),
            liquidAccountIds = liquidAccountIds,
            unsettledStatements = listOf(stmt),
            statementBalancesByGroupId = mapOf("cg-stmt-cc" to -600000L),
            dailyLivingBurnMinor = 5000L, // 50 CNY per day
        )

        // Between Nov 5 and Nov 10 (5 days):
        // Cash on Nov 9: 500,000 - 4*5,000 = 480,000.
        // On Nov 10: 480,000 - 600,000 (statement) - 5,000 (burn) = -125,000 CNY -> DEFICIT!
        assertEquals("2026-11-10", projection.deficitDate)
        assertEquals(5, projection.daysOfRunway)
        assertEquals(0L, projection.safeToSpendMinor) // Can't spend anything!
        assertTrue(projection.minimumTroughMinor < 0L)
        // Lowest trough is on Nov 14 right before Nov 15 payday:
        assertEquals("2026-11-14", projection.minimumTroughDate)
    }

    @Test
    fun `calculates positive safe-to-spend when cashflow remains solvent`() {
        // Initial cash: 20,000 CNY (2,000,000 minor)
        val initialCash = 2000000L
        val liquidAccountIds = setOf("acc-bank")

        // Statement due on Nov 10 for 5,000 CNY (-500,000 minor)
        val stmt = FinanceClearingGroup(
            id = "cg-stmt-cc",
            name = "信用卡11月账单",
            groupType = ClearingGroupType.CREDIT_STATEMENT,
            dueDate = "2026-11-10",
            status = ClearingGroupStatus.ACTIVE,
        )

        // Loan installment due on Nov 20: -2,000 CNY (-200,000 minor)
        val loanTx = FinanceTransaction(
            id = "tx-loan",
            txDate = "2026-11-20",
            status = TransactionStatus.SCHEDULED,
        )
        val loanSplit = FinanceSplit(
            id = "s-loan",
            transactionId = "tx-loan",
            accountId = "acc-bank",
            amountMinor = -200000L,
        )

        // Daily living burn: 100 CNY per day (10,000 minor)
        val projection = CashflowRunwayEngine.projectRunway(
            currentDate = "2026-11-01",
            horizonDays = 30,
            currentLiquidCashMinor = initialCash,
            scheduledTransactions = listOf(loanTx),
            splitsByTx = mapOf("tx-loan" to listOf(loanSplit)),
            liquidAccountIds = liquidAccountIds,
            unsettledStatements = listOf(stmt),
            statementBalancesByGroupId = mapOf("cg-stmt-cc" to -500000L),
            dailyLivingBurnMinor = 10000L,
        )

        assertNull(projection.deficitDate) // Fully solvent!
        assertEquals(30, projection.daysOfRunway)
        assertTrue(projection.safeToSpendMinor > 0L)
        // At day 30, cash = 2,000,000 - 500,000 (stmt) - 200,000 (loan) - 30 * 10,000 (burn) = 1,000,000 minor (10,000 CNY)
        assertEquals(1000000L, projection.minimumTroughMinor)
        assertEquals(1000000L, projection.safeToSpendMinor)
    }

    @Test
    fun `internal transfers between liquid accounts do not create phantom inflows or outflows`() {
        val initialCash = 1000000L
        val liquidAccountIds = setOf("acc-cmb", "acc-icbc")

        // Intra-wallet transfer: CMB -200, ICBC +200
        val transferTx = FinanceTransaction(
            id = "tx-transfer",
            txDate = "2026-12-01",
            status = TransactionStatus.SCHEDULED,
        )
        val splits = listOf(
            FinanceSplit(id = "s-1", transactionId = "tx-transfer", accountId = "acc-cmb", amountMinor = -20000L),
            FinanceSplit(id = "s-2", transactionId = "tx-transfer", accountId = "acc-icbc", amountMinor = 20000L),
        )

        val projection = CashflowRunwayEngine.projectRunway(
            currentDate = "2026-11-30",
            horizonDays = 5,
            currentLiquidCashMinor = initialCash,
            scheduledTransactions = listOf(transferTx),
            splitsByTx = mapOf("tx-transfer" to splits),
            liquidAccountIds = liquidAccountIds,
            unsettledStatements = emptyList(),
            statementBalancesByGroupId = emptyMap(),
            dailyLivingBurnMinor = 0L,
        )

        val dec1Point = projection.dailyPoints.first { it.date == "2026-12-01" }
        assertEquals(0L, dec1Point.scheduledInflowsMinor)
        assertEquals(0L, dec1Point.scheduledOutflowsMinor)
        assertEquals(initialCash, dec1Point.projectedLiquidCashMinor)
    }
}
