package dev.tireless.abun

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.tireless.abun.app.DeviceNodeIdProvider
import dev.tireless.abun.app.FinanceSplitDraft
import dev.tireless.abun.app.IdGenerator
import dev.tireless.abun.app.LocalStore
import dev.tireless.abun.app.TimeProvider
import dev.tireless.abun.app.autoPadAccountBalance
import dev.tireless.abun.app.clearingGroupsForSplit
import dev.tireless.abun.app.createBudgetRule
import dev.tireless.abun.app.createFinanceAccount
import dev.tireless.abun.app.createFinanceAmortizationPlan
import dev.tireless.abun.app.createFinanceClearingGroup
import dev.tireless.abun.app.createFinanceTransaction
import dev.tireless.abun.app.createHybridClock
import dev.tireless.abun.app.deleteBudgetRule
import dev.tireless.abun.app.deleteFinanceAccount
import dev.tireless.abun.app.deleteFinanceAmortizationPlan
import dev.tireless.abun.app.deleteFinanceTransaction
import dev.tireless.abun.app.deriveFinanceViewState
import dev.tireless.abun.app.financeAccount
import dev.tireless.abun.app.financeAccounts
import dev.tireless.abun.app.financeAmortizationPlan
import dev.tireless.abun.app.financeAmortizationPlans
import dev.tireless.abun.app.financeBudgetRule
import dev.tireless.abun.app.financeBudgetRules
import dev.tireless.abun.app.financeClearingGroup
import dev.tireless.abun.app.financeSplitsForAccount
import dev.tireless.abun.app.financeSplitsForTransaction
import dev.tireless.abun.app.financeTransaction
import dev.tireless.abun.app.financeTransactions
import dev.tireless.abun.app.reassignSplitClearingGroup
import dev.tireless.abun.app.seedDefaultFinanceAccountsIfEmpty
import dev.tireless.abun.app.updateFinanceAccount
import dev.tireless.abun.db.AbunDatabase
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.AmortizationMethod
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.finance.TransactionStatus
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceStoreTest {

    private fun testTimeProvider(): TimeProvider = object : TimeProvider {
        private var now = 1_748_120_000_000L
        override fun nowEpochMillis(): Long = now++
        override fun today(): LocalDate = LocalDate.parse("2026-05-25")
    }

    private fun testIdGenerator(): IdGenerator = object : IdGenerator {
        private var index = 0
        override fun randomId(): String = "id-${++index}"
        override fun deterministicId(namespace: String, seed: String): String = "$namespace:$seed"
    }

    private fun createTestStore(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            AbunDatabase.Schema.create(it)
        }
        val database = AbunDatabase(driver)
        return LocalStore(
            database = database,
            timeProvider = testTimeProvider(),
            idGenerator = testIdGenerator(),
            clock = createHybridClock(object : DeviceNodeIdProvider {
                override fun nodeId(): String = "test-node"
            }, testTimeProvider()),
        )
    }

    @Test
    fun `default starter accounts are seeded on store initialization`() {
        val store = createTestStore()
        val accounts = store.financeAccounts()

        assertTrue(accounts.isNotEmpty())
        val paths = accounts.map { it.path }
        assertTrue(paths.contains("Assets:Bank:Default"))
        assertTrue(paths.contains("Liabilities:CreditCard:Default"))
        assertTrue(paths.contains("Liabilities:Loan:Default"))
        assertTrue(paths.contains("Expenses:Food"))
        assertTrue(paths.contains("Expenses:Housing"))
        assertTrue(paths.contains("Expenses:Finance:Interest"))
        assertTrue(paths.contains("Expenses:Adjustment:Unallocated"))
        assertTrue(paths.contains("Income:Salary"))
    }

    @Test
    fun `account lifecycle create update and delete`() {
        val store = createTestStore()

        val accId = store.createFinanceAccount(
            path = "Assets:Crypto:ColdWallet",
            name = "冷钱包",
            category = AccountCategory.ASSET,
            currency = "USD",
            includeInNetWorth = true,
            isPassThrough = false,
            liquidTier = LiquidTier.SEMI_LIQUID,
        )

        val created = store.financeAccount(accId)
        assertNotNull(created)
        assertEquals("冷钱包", created.name)
        assertEquals("USD", created.currency)
        assertEquals(LiquidTier.SEMI_LIQUID, created.liquidTier)

        store.updateFinanceAccount(
            id = accId,
            path = "Assets:Crypto:ColdWallet",
            name = "硬件冷钱包",
            category = AccountCategory.ASSET,
            currency = "USD",
            includeInNetWorth = true,
            isPassThrough = false,
            passThroughExpenseAccountId = null,
            liquidTier = LiquidTier.ILLIQUID,
            statementDay = null,
            dueDay = null,
            isArchived = false,
        )

        val updated = store.financeAccount(accId)
        assertNotNull(updated)
        assertEquals("硬件冷钱包", updated.name)
        assertEquals(LiquidTier.ILLIQUID, updated.liquidTier)

        store.deleteFinanceAccount(accId)
        assertNull(store.financeAccount(accId))
    }

    @Test
    fun `creating transaction validates zero sum balance and inserts splits atomically`() {
        val store = createTestStore()
        val accounts = store.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        // Unbalanced transaction should fail
        assertFailsWith<IllegalArgumentException> {
            store.createFinanceTransaction(
                txDate = "2026-11-05",
                summary = "Unbalanced",
                status = TransactionStatus.CLEARED,
                splits = listOf(
                    FinanceSplitDraft(accountId = bank.id, amountMinor = -5000L),
                    FinanceSplitDraft(accountId = food.id, amountMinor = 4000L),
                ),
            )
        }

        // Balanced transaction succeeds
        val txId = store.createFinanceTransaction(
            txDate = "2026-11-05",
            summary = "Dinner",
            payee = "Restaurant",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -5000L, narration = "Bank payment"),
                FinanceSplitDraft(accountId = food.id, amountMinor = 5000L, narration = "Food items"),
            ),
        )

        val tx = store.financeTransaction(txId)
        assertNotNull(tx)
        assertEquals("Dinner", tx.summary)
        assertEquals("Restaurant", tx.payee)

        val splits = store.financeSplitsForTransaction(txId)
        assertEquals(2, splits.size)
        assertEquals(-5000L, splits[0].amountMinor)
        assertEquals(5000L, splits[1].amountMinor)
        assertEquals("Bank payment", splits[0].narration)
        assertEquals("Food items", splits[1].narration)
    }

    @Test
    fun `clearing group auto updates status to settled upon full repayment`() {
        val store = createTestStore()
        val accounts = store.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val loan = accounts.first { it.path == "Liabilities:Loan:Default" }

        val groupId = store.createFinanceClearingGroup(
            name = "装修贷平账测试",
            groupType = ClearingGroupType.LOAN_LIFECYCLE,
            anchorCurrency = "CNY",
        )

        // 1. Initial borrowing: Bank +10,000, Loan -10,000
        store.createFinanceTransaction(
            txDate = "2026-01-01",
            summary = "Borrow loan",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = 1000000L),
                FinanceSplitDraft(accountId = loan.id, amountMinor = -1000000L, clearingGroupIds = listOf(groupId)),
            ),
        )

        val groupAfterBorrow = store.financeClearingGroup(groupId)
        assertNotNull(groupAfterBorrow)
        assertEquals(ClearingGroupStatus.ACTIVE, groupAfterBorrow.status)

        // 2. Full repayment: Bank -10,000, Loan +10,000
        store.createFinanceTransaction(
            txDate = "2026-02-01",
            summary = "Repay loan",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -1000000L),
                FinanceSplitDraft(accountId = loan.id, amountMinor = 1000000L, clearingGroupIds = listOf(groupId)),
            ),
        )

        val groupAfterRepay = store.financeClearingGroup(groupId)
        assertNotNull(groupAfterRepay)
        assertEquals(ClearingGroupStatus.SETTLED, groupAfterRepay.status)
        assertNotNull(groupAfterRepay.settledAt)
    }

    @Test
    fun `autoPadAccountBalance creates adjustment transaction when drift exists`() {
        val store = createTestStore()
        val accounts = store.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        // Initial deposit and spending
        store.createFinanceTransaction(
            txDate = "2026-11-01",
            summary = "Salary deposit",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = 1000000L), // +10,000
                FinanceSplitDraft(accountId = accounts.first { it.path == "Income:Salary" }.id, amountMinor = -1000000L),
            ),
        )
        store.createFinanceTransaction(
            txDate = "2026-11-02",
            summary = "Lunch",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -20000L), // -200 -> recorded = 9,800
                FinanceSplitDraft(accountId = food.id, amountMinor = 20000L),
            ),
        )

        // If actual balance is 9,500 (300 missing/unrecorded)
        val padTxId = store.autoPadAccountBalance(
            accountId = bank.id,
            actualBalanceMinor = 950000L,
            adjustmentDate = "2026-11-05",
        )
        assertNotNull(padTxId)

        // Verify account balance now matches exactly 9,500
        val splits = store.financeSplitsForAccount(bank.id)
        val newBalance = splits.sumOf { it.amountMinor }
        assertEquals(950000L, newBalance)

        // Re-running auto-pad when already balanced returns null
        val noOpPad = store.autoPadAccountBalance(
            accountId = bank.id,
            actualBalanceMinor = 950000L,
            adjustmentDate = "2026-11-05",
        )
        assertNull(noOpPad)
    }

    @Test
    fun `deriveFinanceViewState and deleteFinanceTransaction lifecycle`() {
        val store = createTestStore()
        val accounts = store.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        val txId = store.createFinanceTransaction(
            txDate = "2026-11-05",
            summary = "Lunch",
            payee = "Subway",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -3500L, narration = "Debit Card"),
                FinanceSplitDraft(accountId = food.id, amountMinor = 3500L, narration = "Sandwich"),
            ),
        )

        val viewState = store.deriveFinanceViewState()
        assertEquals(1, viewState.transactions.size)
        val txView = viewState.transactions.first()
        assertEquals("Subway", txView.transaction.payee)
        assertEquals(2, txView.splits.size)
        assertTrue(txView.beancountText.contains("Subway"))
        assertTrue(txView.beancountText.contains("Assets:Bank:Default"))
        assertTrue(txView.beancountText.contains("Expenses:Food"))

        // Delete transaction
        store.deleteFinanceTransaction(txId)
        assertNull(store.financeTransaction(txId))

        val updatedViewState = store.deriveFinanceViewState()
        assertEquals(0, updatedViewState.transactions.size)
    }

    @Test
    fun `credit card statement auto-binding refund reduction and repayment settling`() {
        val store = createTestStore()
        val accounts = store.financeAccounts()
        val card = accounts.first { it.path == "Liabilities:CreditCard:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }
        val bank = accounts.first { it.path == "Assets:Bank:Default" }

        // Card has statementDay = 5, dueDay = 25
        assertEquals(5, card.statementDay)
        assertEquals(25, card.dueDay)

        // Purchase 1 on May 4: -50.00 CNY (5000 minor units) on card, +50.00 CNY on food
        val tx1Id = store.createFinanceTransaction(
            txDate = "2026-05-04",
            summary = "Dinner",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = card.id, amountMinor = -5000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 5000L),
            ),
        )

        // Verify credit card split automatically bound to 2026-05 statement group
        val tx1Splits = store.financeSplitsForTransaction(tx1Id)
        val cardSplit1 = tx1Splits.first { it.accountId == card.id }
        val cgs1 = store.clearingGroupsForSplit(cardSplit1.id)
        assertEquals(1, cgs1.size)
        val stmtGroup = cgs1.first()
        assertEquals("cg-stmt:${card.id}:2026-05", stmtGroup.id)
        assertEquals("2026-05", stmtGroup.statementCycle)
        assertEquals("2026-05-25", stmtGroup.dueDate)

        // Purchase 2 on May 4: -30.00 CNY on card
        store.createFinanceTransaction(
            txDate = "2026-05-04",
            summary = "Groceries",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = card.id, amountMinor = -3000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 3000L),
            ),
        )

        // Refund on May 5: +20.00 CNY refund on card (reducing debt to -60.00 CNY)
        store.createFinanceTransaction(
            txDate = "2026-05-05",
            summary = "Grocery Refund",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = card.id, amountMinor = 2000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = -2000L),
            ),
        )

        // View state evaluates group balance to -60.00 CNY (-6000 minor)
        val midViewState = store.deriveFinanceViewState()
        val stmtViewItem = midViewState.clearingGroups.first { it.group.id == stmtGroup.id }
        assertEquals(-6000L, stmtViewItem.evaluation.realizedBalance)
        assertEquals(ClearingGroupStatus.ACTIVE, stmtViewItem.evaluation.derivedStatus)

        // Repay remaining 60.00 CNY from bank to card on May 20
        store.createFinanceTransaction(
            txDate = "2026-05-20",
            summary = "Statement Repayment",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -6000L),
                FinanceSplitDraft(accountId = card.id, amountMinor = 6000L, clearingGroupIds = listOf(stmtGroup.id)),
            ),
        )

        // Verify statement clearing group is now Settled with 0 balance!
        val settledViewState = store.deriveFinanceViewState()
        val settledStmt = settledViewState.clearingGroups.first { it.group.id == stmtGroup.id }
        assertEquals(0L, settledStmt.evaluation.realizedBalance)
        assertEquals(ClearingGroupStatus.SETTLED, settledStmt.evaluation.derivedStatus)
    }

    @Test
    fun `reassignSplitClearingGroup shifts split to next statement cycle`() {
        val store = createTestStore()
        val accounts = store.financeAccounts()
        val card = accounts.first { it.path == "Liabilities:CreditCard:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        // Transaction on May 5 automatically bound to 2026-05 cycle
        val txId = store.createFinanceTransaction(
            txDate = "2026-05-05",
            summary = "Late posted meal",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = card.id, amountMinor = -4000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 4000L),
            ),
        )

        val cardSplit = store.financeSplitsForTransaction(txId).first { it.accountId == card.id }
        val oldGroup = store.clearingGroupsForSplit(cardSplit.id).first()
        assertEquals("cg-stmt:${card.id}:2026-05", oldGroup.id)

        // Manually create / shift to June statement group
        val juneGroupId = store.createFinanceClearingGroup(
            name = "${card.name} 2026-06 账单",
            groupType = ClearingGroupType.CREDIT_STATEMENT,
            anchorCurrency = "CNY",
            targetAccountId = card.id,
            statementCycle = "2026-06",
            dueDate = "2026-06-25",
        )

        store.reassignSplitClearingGroup(
            splitId = cardSplit.id,
            oldGroupId = oldGroup.id,
            newGroupId = juneGroupId,
        )

        // Verify split is now bound to June statement group
        val newCgs = store.clearingGroupsForSplit(cardSplit.id)
        assertEquals(1, newCgs.size)
        assertEquals(juneGroupId, newCgs.first().id)

        // Verify old group balance is now 0 and June group has -4000L
        val viewState = store.deriveFinanceViewState()
        val oldGroupItem = viewState.clearingGroups.first { it.group.id == oldGroup.id }
        val juneGroupItem = viewState.clearingGroups.first { it.group.id == juneGroupId }
        assertEquals(0L, oldGroupItem.evaluation.realizedBalance)
        assertEquals(-4000L, juneGroupItem.evaluation.realizedBalance)
    }

    @Test
    fun `amortization plan generates schedule and balances to principal`() {
        val store = createTestStore()
        val accounts = store.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val interest = accounts.first { it.path == "Expenses:Finance:Interest" }

        val loanId = store.createFinanceAccount(
            path = "Liabilities:Loan:Consumer",
            name = "个人信用贷",
            category = AccountCategory.LIABILITY,
            currency = "CNY",
        )

        // Create 12-month equal principal plan for 12,000 CNY (1,200,000 minor)
        val planId = store.createFinanceAmortizationPlan(
            name = "装修消费贷",
            principalMinor = 1200000L,
            totalPeriods = 12,
            repaymentMethod = AmortizationMethod.EQUAL_PRINCIPAL,
            annualRateBps = 480, // 4.8%
            startDate = "2026-04-15",
            repaymentDay = 15,
            liabilityAccountId = loanId,
            fundingAccountId = bank.id,
            interestAccountId = interest.id,
            recordInitialDisbursement = true,
        )

        val plan = store.financeAmortizationPlan(planId)
        assertNotNull(plan)
        assertEquals("装修消费贷", plan.name)
        assertEquals(12, plan.totalPeriods)

        // Verify initial disbursement transaction created
        val txs = store.financeTransactions().filter { it.sourcePlanId == planId }
        assertEquals(13, txs.size) // 1 disbursement + 12 scheduled

        val disbursementTx = txs.first { it.periodIndex == 0 }
        assertEquals(TransactionStatus.CLEARED, disbursementTx.status)
        val disbSplits = store.financeSplitsForTransaction(disbursementTx.id)
        val bankDisb = disbSplits.first { it.accountId == bank.id }
        val loanDisb = disbSplits.first { it.accountId == loanId }
        assertEquals(1200000L, bankDisb.amountMinor)
        assertEquals(-1200000L, loanDisb.amountMinor)

        // Verify all 12 tranches sum up to exactly 1,200,000 minor
        val scheduledTxs = txs.filter { it.periodIndex != 0 }
        assertEquals(12, scheduledTxs.size)
        var totalPrincipal = 0L
        for (stx in scheduledTxs) {
            assertEquals(TransactionStatus.SCHEDULED, stx.status)
            val splits = store.financeSplitsForTransaction(stx.id)
            val pSplit = splits.first { it.accountId == loanId }
            totalPrincipal += pSplit.amountMinor
            // Verify each scheduled transaction balances to 0
            val sum = splits.sumOf { it.amountMinor }
            assertEquals(0L, sum)
        }
        assertEquals(1200000L, totalPrincipal)

        // Verify clearing group tracking
        val clearingGroup = store.financeClearingGroup(plan.lifecycleClearingGroupId)
        assertNotNull(clearingGroup)
        assertEquals(ClearingGroupType.LOAN_LIFECYCLE, clearingGroup.groupType)

        // Initial realized balance should be -12,000.00
        val viewStateInitial = store.deriveFinanceViewState()
        val cgItem = viewStateInitial.clearingGroups.first { it.group.id == clearingGroup.id }
        assertEquals(-1200000L, cgItem.evaluation.realizedBalance)
        assertEquals(0L, cgItem.evaluation.projectedBalance) // -12,000 + 12,000 scheduled = 0!

        // Delete plan cleans up scheduled tranches and marks plan deleted
        store.deleteFinanceAmortizationPlan(planId)
        assertNull(store.financeAmortizationPlan(planId))
        val remainingTxs = store.financeTransactions().filter { it.sourcePlanId == planId }
        // Disbursement tx is CLEARED so kept, SCHEDULED txs are deleted
        assertEquals(1, remainingTxs.size)
        assertEquals(0, remainingTxs.first().periodIndex)
    }

    @Test
    fun `budget rule lifecycle and runway derivation in view state`() {
        val store = createTestStore()
        store.seedDefaultFinanceAccountsIfEmpty()

        val rules = store.financeBudgetRules()
        assertEquals(1, rules.size)
        assertEquals("日常支出护栏", rules.first().name)
        assertEquals(dev.tireless.abun.finance.BudgetScopeType.GLOBAL_DISCRETIONARY, rules.first().scopeType)

        val foodRule = store.createBudgetRule(
            name = "餐饮专项",
            scopeType = dev.tireless.abun.finance.BudgetScopeType.ACCOUNT_SUBTREE,
            accountPrefix = "Expenses:Food",
            monthlyLimitMinor = 150000L,
            currency = "CNY",
            includeInRunway = false,
        )
        assertEquals(2, store.financeBudgetRules().size)

        // Initial view state
        val initialView = store.deriveFinanceViewState()
        assertEquals(2, initialView.budgetEvaluations.size)
        assertNotNull(initialView.runwayProjection30)
        assertNotNull(initialView.runwayProjection90)

        // Record an expense of 200 CNY in Expenses:Food
        val today = "2026-05-25"
        store.createFinanceTransaction(
            txDate = today,
            summary = "Lunch with team",
            splits = listOf(
                FinanceSplitDraft(accountId = "acc-expenses-food", amountMinor = 20000L, currency = "CNY"),
                FinanceSplitDraft(accountId = "acc-assets-bank-default", amountMinor = -20000L, currency = "CNY"),
            ),
        )

        val updatedView = store.deriveFinanceViewState()
        val globalEval = updatedView.budgetEvaluations.first { it.rule.scopeType == dev.tireless.abun.finance.BudgetScopeType.GLOBAL_DISCRETIONARY }
        val foodEval = updatedView.budgetEvaluations.first { it.rule.id == foodRule.id }

        assertEquals(20000L, globalEval.evaluation.spentMinor)
        assertEquals(20000L, foodEval.evaluation.spentMinor)
        assertEquals(280000L, globalEval.evaluation.remainingMinor)
        assertEquals(130000L, foodEval.evaluation.remainingMinor)

        // Delete custom budget rule
        store.deleteBudgetRule(foodRule.id)
        assertNull(store.financeBudgetRule(foodRule.id))
        assertEquals(1, store.financeBudgetRules().size)
    }
}
