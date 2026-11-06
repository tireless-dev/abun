package dev.tireless.abun.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BudgetEvaluatorTest {

    @Test
    fun `evaluates global discretionary budget with pacing delta and daily allowance`() {
        val rule = FinanceBudgetRule(
            id = "rule-global",
            name = "日常弹性总预算",
            scopeType = BudgetScopeType.GLOBAL_DISCRETIONARY,
            excludedPrefixesJson = "[\"Expenses:Finance:Interest\", \"Expenses:Housing:Rent\"]",
            monthlyLimitMinor = 600000L, // 6,000 CNY
            currency = "CNY",
        )

        val foodAccount = FinanceAccount(
            id = "acc-food",
            path = "Expenses:Food:Dinner",
            name = "晚餐",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
        )
        val interestAccount = FinanceAccount(
            id = "acc-interest",
            path = "Expenses:Finance:Interest",
            name = "利息",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
        )
        val accounts = listOf(foodAccount, interestAccount).associateBy { it.id }

        // 2 transactions in November 2026 (30 days total)
        // Tx 1: Food 2,500 CNY
        // Tx 2: Interest 500 CNY (should be excluded)
        val tx1 = FinanceTransaction(
            id = "tx-1",
            txDate = "2026-11-05",
            status = TransactionStatus.CLEARED,
        )
        val tx2 = FinanceTransaction(
            id = "tx-2",
            txDate = "2026-11-08",
            status = TransactionStatus.CLEARED,
        )
        val transactions = listOf(tx1, tx2).associateBy { it.id }

        val splits = listOf(
            FinanceSplit(id = "s-1", transactionId = "tx-1", accountId = "acc-food", amountMinor = 250000L),
            FinanceSplit(id = "s-2", transactionId = "tx-2", accountId = "acc-interest", amountMinor = 50000L),
        )

        // Evaluate on Day 10 of November (10/30 = 33.3% elapsed, expected spend = 2,000 CNY)
        val result = BudgetEvaluator.evaluate(
            rule = rule,
            targetYear = 2026,
            targetMonth = 11,
            currentDate = "2026-11-10",
            accounts = accounts,
            transactions = transactions,
            splits = splits,
        )

        assertEquals("rule-global", result.ruleId)
        assertEquals(250000L, result.spentMinor) // 2,500 CNY (interest excluded!)
        assertEquals(350000L, result.remainingMinor) // 3,500 CNY remaining
        assertEquals(10.0 / 30.0, result.timeElapsedRatio)
        assertEquals(250000.0 / 600000.0, result.spendRatio)
        // Expected on day 10 = 2,000.00; Actual = 2,500.00; Pacing Delta = +500.00
        assertEquals(50000L, result.pacingDeltaMinor)
        assertTrue(result.isAheadOfPacing)
        assertFalse(result.isOverBudget)
        // Remaining days = 30 - 10 + 1 = 21 days; Allowance = 3,500.00 / 21 = ~166.66 (16666 minor)
        assertEquals(350000L / 21L, result.recommendedDailyAllowanceMinor)
    }

    @Test
    fun `evaluates account subtree budget and pass-through account`() {
        val rule = FinanceBudgetRule(
            id = "rule-food",
            name = "餐饮专项预算",
            scopeType = BudgetScopeType.ACCOUNT_SUBTREE,
            accountPrefix = "Expenses:Food",
            monthlyLimitMinor = 200000L, // 2,000 CNY
            currency = "CNY",
        )

        val foodAccount = FinanceAccount(
            id = "acc-food",
            path = "Expenses:Food:Groceries",
            name = "超市买菜",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
        )
        val transportAccount = FinanceAccount(
            id = "acc-transport",
            path = "Expenses:Transport:Taxi",
            name = "打车",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
        )
        val canteenCard = FinanceAccount(
            id = "acc-canteen",
            path = "Assets:Prepaid:Canteen",
            name = "食堂饭卡",
            category = AccountCategory.ASSET,
            isPassThrough = true,
            passThroughExpenseAccountId = "acc-food",
            currency = "CNY",
        )
        val accounts = listOf(foodAccount, transportAccount, canteenCard).associateBy { it.id }

        val tx1 = FinanceTransaction(id = "tx-1", txDate = "2026-11-02", status = TransactionStatus.CLEARED)
        val tx2 = FinanceTransaction(id = "tx-2", txDate = "2026-11-04", status = TransactionStatus.CLEARED)
        val tx3 = FinanceTransaction(id = "tx-3", txDate = "2026-11-06", status = TransactionStatus.CLEARED)
        val transactions = listOf(tx1, tx2, tx3).associateBy { it.id }

        val splits = listOf(
            FinanceSplit(id = "s-1", transactionId = "tx-1", accountId = "acc-food", amountMinor = 40000L), // 400
            FinanceSplit(id = "s-2", transactionId = "tx-2", accountId = "acc-transport", amountMinor = 15000L), // 150 (not in subtree)
            FinanceSplit(id = "s-3", transactionId = "tx-3", accountId = "acc-canteen", amountMinor = 20000L), // 200 (pass-through to Food!)
        )

        val result = BudgetEvaluator.evaluate(
            rule = rule,
            targetYear = 2026,
            targetMonth = 11,
            currentDate = "2026-11-15",
            accounts = accounts,
            transactions = transactions,
            splits = splits,
        )

        // 400 + 200 = 600 CNY
        assertEquals(60000L, result.spentMinor)
        assertEquals(140000L, result.remainingMinor)
        assertFalse(result.isAheadOfPacing) // 15/30 = 50% elapsed (expected 1,000), only spent 600
        assertEquals(-40000L, result.pacingDeltaMinor)
    }
}
