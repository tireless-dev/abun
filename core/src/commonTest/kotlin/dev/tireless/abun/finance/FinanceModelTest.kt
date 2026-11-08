package dev.tireless.abun.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FinanceModelTest {

    @Test
    fun `currency scale and minor unit conversions handle crypto and fiat correctly`() {
        assertEquals(2, FinanceCurrencies.minorUnitScale("CNY"))
        assertEquals(2, FinanceCurrencies.minorUnitScale("USD"))
        assertEquals(0, FinanceCurrencies.minorUnitScale("JPY"))
        assertEquals(3, FinanceCurrencies.minorUnitScale("KWD"))
        assertEquals(6, FinanceCurrencies.minorUnitScale("USDT"))
        assertEquals(8, FinanceCurrencies.minorUnitScale("ETH"))

        // Decimal string conversion tests
        assertEquals(10050L, FinanceCurrencies.toMinorUnitsFromDecimalString("100.50", "CNY"))
        assertEquals(-337885L, FinanceCurrencies.toMinorUnitsFromDecimalString("-3,378.85", "CNY"))
        assertEquals(612530L, FinanceCurrencies.toMinorUnitsFromDecimalString("0.61253", "USDT"))
        assertEquals(160999095L, FinanceCurrencies.toMinorUnitsFromDecimalString("1.60999095", "ETH"))
        assertEquals(-495100L, FinanceCurrencies.toMinorUnitsFromDecimalString("-0.004951", "ETH"))
    }

    @Test
    fun `valid 2-split single currency transaction passes validation`() {
        val tx = FinanceTransaction(
            id = "tx-1",
            txDate = "2026-11-05",
            summary = "Supermarket groceries",
            status = TransactionStatus.CLEARED,
        )
        val splits = listOf(
            FinanceSplit(
                id = "s-1",
                transactionId = "tx-1",
                accountId = "acc-bank",
                amountMinor = -10050L, // -100.50 CNY
                currency = "CNY",
                narration = "Debit card payment",
            ),
            FinanceSplit(
                id = "s-2",
                transactionId = "tx-1",
                accountId = "acc-expense",
                amountMinor = 10050L, // +100.50 CNY
                currency = "CNY",
                narration = "Food and groceries",
            ),
        )

        val result = FinanceValidators.validateTransactionSplits(tx, splits)
        assertIs<TransactionValidationResult.Valid>(result)
    }

    @Test
    fun `unbalanced committed transaction fails validation`() {
        val tx = FinanceTransaction(
            id = "tx-2",
            txDate = "2026-11-05",
            status = TransactionStatus.CLEARED,
        )
        val splits = listOf(
            FinanceSplit(
                id = "s-1",
                transactionId = "tx-2",
                accountId = "acc-bank",
                amountMinor = -10000L,
                currency = "CNY",
            ),
            FinanceSplit(
                id = "s-2",
                transactionId = "tx-2",
                accountId = "acc-expense",
                amountMinor = 9000L,
                currency = "CNY",
            ),
        )

        val result = FinanceValidators.validateTransactionSplits(tx, splits)
        assertIs<TransactionValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("balance to zero"))
    }

    @Test
    fun `draft transactions allow zero or one split without failing`() {
        val draftTx = FinanceTransaction(
            id = "tx-draft",
            txDate = "2026-11-05",
            summary = "Quick draft",
            status = TransactionStatus.DRAFT,
        )

        // 0 splits
        assertEquals(TransactionValidationResult.Valid, FinanceValidators.validateTransactionSplits(draftTx, emptyList()))

        // 1 split
        val singleSplit = listOf(
            FinanceSplit(
                id = "s-1",
                transactionId = "tx-draft",
                accountId = "acc-expense",
                amountMinor = 5000L,
                currency = "CNY",
            ),
        )
        assertEquals(TransactionValidationResult.Valid, FinanceValidators.validateTransactionSplits(draftTx, singleSplit))
    }

    @Test
    fun `cross-currency transaction balances by weight currency`() {
        // e.g. Expenses:SaaS 20.00 USD @@ 145.20 CNY, Liabilities:CC -145.20 CNY
        val tx = FinanceTransaction(
            id = "tx-fx",
            txDate = "2026-11-05",
            summary = "SaaS Subscription",
            status = TransactionStatus.CLEARED,
        )
        val splits = listOf(
            FinanceSplit(
                id = "s-fx-1",
                transactionId = "tx-fx",
                accountId = "acc-saas",
                amountMinor = 2000L, // 20.00 USD
                currency = "USD",
                weightAmountMinor = 14520L, // 145.20 CNY
                weightCurrency = "CNY",
                narration = "ChatGPT Plus $20",
            ),
            FinanceSplit(
                id = "s-fx-2",
                transactionId = "tx-fx",
                accountId = "acc-credit-card",
                amountMinor = -14520L, // -145.20 CNY
                currency = "CNY",
                weightAmountMinor = -14520L,
                weightCurrency = "CNY",
                narration = "Card posted in CNY",
            ),
        )

        val result = FinanceValidators.validateTransactionSplits(tx, splits)
        assertIs<TransactionValidationResult.Valid>(result)
    }

    @Test
    fun `cross-currency transaction with conflicting signs fails validation`() {
        val tx = FinanceTransaction(
            id = "tx-bad-sign",
            txDate = "2026-11-05",
            status = TransactionStatus.CLEARED,
        )
        val splits = listOf(
            FinanceSplit(
                id = "s-1",
                transactionId = "tx-bad-sign",
                accountId = "acc-saas",
                amountMinor = 2000L, // Positive USD
                currency = "USD",
                weightAmountMinor = -14520L, // Contradictory Negative CNY weight!
                weightCurrency = "CNY",
            ),
            FinanceSplit(
                id = "s-2",
                transactionId = "tx-bad-sign",
                accountId = "acc-credit-card",
                amountMinor = 14520L,
                currency = "CNY",
            ),
        )

        val result = FinanceValidators.validateTransactionSplits(tx, splits)
        assertIs<TransactionValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("amount sign"))
    }

    @Test
    fun `clearing group tracks debt accumulation and reaches settled status on exact zero balance`() {
        val group = FinanceClearingGroup(
            id = "cg-loan-1",
            name = "装修贷款本金清偿",
            groupType = ClearingGroupType.LOAN_LIFECYCLE,
            anchorCurrency = "CNY",
        )

        val txBorrow = FinanceTransaction(id = "tx-borrow", txDate = "2026-01-01", status = TransactionStatus.CLEARED)
        val txRepay1 = FinanceTransaction(id = "tx-repay-1", txDate = "2026-02-01", status = TransactionStatus.CLEARED)
        val txRepay2 = FinanceTransaction(id = "tx-repay-2", txDate = "2026-03-01", status = TransactionStatus.CLEARED)
        val txMap = mapOf(
            txBorrow.id to txBorrow,
            txRepay1.id to txRepay1,
            txRepay2.id to txRepay2,
        )

        val splitBorrow = FinanceSplit(id = "s-b", transactionId = txBorrow.id, accountId = "acc-loan", amountMinor = -1000000L) // -10,000.00 CNY
        val splitRepay1 = FinanceSplit(id = "s-r1", transactionId = txRepay1.id, accountId = "acc-loan", amountMinor = 600000L)   // +6,000.00 CNY
        val splitRepay2 = FinanceSplit(id = "s-r2", transactionId = txRepay2.id, accountId = "acc-loan", amountMinor = 400000L)   // +4,000.00 CNY

        // Step 1: After initial borrowing
        val eval1 = ClearingGroupEvaluator.evaluate(group, listOf(splitBorrow), txMap)
        assertEquals(-1000000L, eval1.realizedBalance)
        assertEquals(ClearingGroupStatus.ACTIVE, eval1.derivedStatus)

        // Step 2: After first repayment
        val eval2 = ClearingGroupEvaluator.evaluate(group, listOf(splitBorrow, splitRepay1), txMap)
        assertEquals(-400000L, eval2.realizedBalance)
        assertEquals(ClearingGroupStatus.ACTIVE, eval2.derivedStatus)

        // Step 3: After full repayment
        val eval3 = ClearingGroupEvaluator.evaluate(group, listOf(splitBorrow, splitRepay1, splitRepay2), txMap)
        assertEquals(0L, eval3.realizedBalance)
        assertEquals(ClearingGroupStatus.SETTLED, eval3.derivedStatus)
        assertEquals(3, eval3.clearedSplitCount)
    }

    @Test
    fun `clearing group detects overdrawn loan repayment`() {
        val group = FinanceClearingGroup(
            id = "cg-loan-2",
            name = "贷款超还测试",
            groupType = ClearingGroupType.LOAN_LIFECYCLE,
            anchorCurrency = "CNY",
        )
        val tx = FinanceTransaction(id = "tx-1", txDate = "2026-01-01", status = TransactionStatus.CLEARED)
        val txMap = mapOf(tx.id to tx)

        val splitBorrow = FinanceSplit(id = "s-1", transactionId = tx.id, accountId = "acc-loan", amountMinor = -100000L) // -1,000
        val splitRepay = FinanceSplit(id = "s-2", transactionId = tx.id, accountId = "acc-loan", amountMinor = 120000L)   // +1,200

        val eval = ClearingGroupEvaluator.evaluate(group, listOf(splitBorrow, splitRepay), txMap)
        assertEquals(20000L, eval.realizedBalance)
        assertEquals(ClearingGroupStatus.OVERDRAWN, eval.derivedStatus)
    }

    @Test
    fun `clearing group supports cross-currency matching via weight currency`() {
        // Statement group anchored in CNY
        val statementGroup = FinanceClearingGroup(
            id = "cg-stmt-1",
            name = "2026-05 信用卡账单",
            groupType = ClearingGroupType.CREDIT_STATEMENT,
            anchorCurrency = "CNY",
        )
        val txSpend = FinanceTransaction(id = "tx-spend", txDate = "2026-05-02", status = TransactionStatus.CLEARED)
        val txRepay = FinanceTransaction(id = "tx-repay", txDate = "2026-05-20", status = TransactionStatus.CLEARED)
        val txMap = mapOf(txSpend.id to txSpend, txRepay.id to txRepay)

        // Foreign currency spend: -20 USD with weight -145.20 CNY
        val splitSpend = FinanceSplit(
            id = "s-spend",
            transactionId = txSpend.id,
            accountId = "acc-cc",
            amountMinor = -2000L, // -20.00 USD
            currency = "USD",
            weightAmountMinor = -14520L, // -145.20 CNY
            weightCurrency = "CNY",
        )
        // Repayment in CNY: +145.20 CNY
        val splitRepay = FinanceSplit(
            id = "s-repay",
            transactionId = txRepay.id,
            accountId = "acc-cc",
            amountMinor = 14520L, // +145.20 CNY
            currency = "CNY",
        )

        val eval = ClearingGroupEvaluator.evaluate(statementGroup, listOf(splitSpend, splitRepay), txMap)
        assertEquals(0L, eval.realizedBalance)
        assertEquals(ClearingGroupStatus.SETTLED, eval.derivedStatus)
    }
}
