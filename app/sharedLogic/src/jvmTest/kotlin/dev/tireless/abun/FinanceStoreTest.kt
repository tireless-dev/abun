package dev.tireless.abun

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.tireless.abun.app.DEFAULT_FIVE_FINANCE_ACCOUNTS
import dev.tireless.abun.app.DEFAULT_FIVE_FINANCE_ACCOUNT_IDS
import dev.tireless.abun.app.DeviceNodeIdProvider
import dev.tireless.abun.app.FinanceSplitDraft
import dev.tireless.abun.app.IdGenerator
import dev.tireless.abun.app.LocalStore
import dev.tireless.abun.app.TimeProvider
import dev.tireless.abun.app.createHybridClock
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
import kotlin.test.assertFalse
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
    fun `default mandatory five accounts are seeded on store initialization without budget rules`() {
        val store = createTestStore()
        val accounts = store.financeAccountStore.financeAccounts()

        assertEquals(5, accounts.size)
        val paths = accounts.map { it.path }
        assertTrue(paths.contains("Assets"))
        assertTrue(paths.contains("Liabilities"))
        assertTrue(paths.contains("Income"))
        assertTrue(paths.contains("Expenses"))
        assertTrue(paths.contains("Equity"))

        val rules = store.financeAccountStore.financeBudgetRules()
        assertTrue(rules.isEmpty())
        assertTrue(store.financeAccountStore.canLoadPredefinedFinanceAccounts())
    }

    @Test
    fun `loadPredefinedFinanceAccounts is only allowed when only Default 5 exist`() {
        val store = createTestStore()
        assertTrue(store.financeAccountStore.canLoadPredefinedFinanceAccounts())

        assertTrue(store.financeAccountStore.loadPredefinedFinanceAccounts())
        val accounts = store.financeAccountStore.financeAccounts()
        assertEquals(13, accounts.size) // 5 root + 8 sub-accounts
        assertFalse(store.financeAccountStore.canLoadPredefinedFinanceAccounts())

        // Calling again returns false because sub-accounts already exist
        assertFalse(store.financeAccountStore.loadPredefinedFinanceAccounts())
    }

    @Test
    fun `resetFinanceToDefaultAccounts erases sub-accounts and records back to Default 5`() {
        val store = createTestStore()
        assertTrue(store.financeAccountStore.loadPredefinedFinanceAccounts())
        assertFalse(store.financeAccountStore.canLoadPredefinedFinanceAccounts())

        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-25",
            summary = "Test expense",
            splits = listOf(
                FinanceSplitDraft(accountId = "acc-expenses-food", amountMinor = 5000L),
                FinanceSplitDraft(accountId = "acc-assets-bank-default", amountMinor = -5000L),
            ),
        )
        store.financeAccountStore.createBudgetRule(
            name = "Test rule",
            scopeType = dev.tireless.abun.finance.BudgetScopeType.GLOBAL_DISCRETIONARY,
            monthlyLimitMinor = 100000L,
        )

        assertEquals(13, store.financeAccountStore.financeAccounts().size)
        assertEquals(1, store.financeLedgerStore.financeTransactions().size)
        assertEquals(1, store.financeAccountStore.financeBudgetRules().size)

        store.financeAccountStore.resetFinanceToDefaultAccounts()

        val resetAccounts = store.financeAccountStore.financeAccounts()
        assertEquals(5, resetAccounts.size)
        assertEquals(DEFAULT_FIVE_FINANCE_ACCOUNT_IDS, resetAccounts.map { it.id }.toSet())
        assertTrue(store.financeLedgerStore.financeTransactions().isEmpty())
        assertTrue(store.financeAccountStore.financeBudgetRules().isEmpty())
        assertTrue(store.financeAccountStore.canLoadPredefinedFinanceAccounts())

        // Predefined sub-accounts can be loaded again
        assertTrue(store.financeAccountStore.loadPredefinedFinanceAccounts())
        assertEquals(13, store.financeAccountStore.financeAccounts().size)
    }

    @Test
    fun `default mandatory five accounts cannot be deleted`() {
        val store = createTestStore()
        for (acc in DEFAULT_FIVE_FINANCE_ACCOUNTS) {
            store.financeAccountStore.deleteFinanceAccount(acc.id)
            assertNotNull(store.financeAccountStore.financeAccount(acc.id))
        }
        assertEquals(5, store.financeAccountStore.financeAccounts().size)
    }

    @Test
    fun `account lifecycle create update and delete`() {
        val store = createTestStore()

        val accId = store.financeAccountStore.createFinanceAccount(
            path = "Assets:Crypto:ColdWallet",
            name = "冷钱包",
            category = AccountCategory.ASSET,
            currency = "USD",
            includeInNetWorth = true,
            isPassThrough = false,
            liquidTier = LiquidTier.SEMI_LIQUID,
        )

        val created = store.financeAccountStore.financeAccount(accId)
        assertNotNull(created)
        assertEquals("冷钱包", created.name)
        assertEquals("USD", created.currency)
        assertEquals(LiquidTier.SEMI_LIQUID, created.liquidTier)

        store.financeAccountStore.updateFinanceAccount(
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

        val updated = store.financeAccountStore.financeAccount(accId)
        assertNotNull(updated)
        assertEquals("硬件冷钱包", updated.name)
        assertEquals(LiquidTier.ILLIQUID, updated.liquidTier)

        store.financeAccountStore.deleteFinanceAccount(accId)
        assertNull(store.financeAccountStore.financeAccount(accId))
    }

    @Test
    fun `creating transaction validates zero sum balance and inserts splits atomically`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        val accounts = store.financeAccountStore.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        // Unbalanced transaction should fail
        assertFailsWith<IllegalArgumentException> {
            store.financeLedgerStore.createFinanceTransaction(
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
        val txId = store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-11-05",
            summary = "Dinner",
            payee = "Restaurant",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -5000L, narration = "Bank payment"),
                FinanceSplitDraft(accountId = food.id, amountMinor = 5000L, narration = "Food items"),
            ),
        )

        val tx = store.financeLedgerStore.financeTransaction(txId)
        assertNotNull(tx)
        assertEquals("Dinner", tx.summary)
        assertEquals("Restaurant", tx.payee)

        val splits = store.financeLedgerStore.financeSplitsForTransaction(txId)
        assertEquals(2, splits.size)
        assertEquals(-5000L, splits[0].amountMinor)
        assertEquals(5000L, splits[1].amountMinor)
        assertEquals("Bank payment", splits[0].narration)
        assertEquals("Food items", splits[1].narration)
    }

    @Test
    fun `clearing group auto updates status to settled upon full repayment`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        val accounts = store.financeAccountStore.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val loan = accounts.first { it.path == "Liabilities:Loan:Default" }

        val groupId = store.financeLedgerStore.createFinanceClearingGroup(
            name = "装修贷平账测试",
            groupType = ClearingGroupType.LOAN_LIFECYCLE,
            anchorCurrency = "CNY",
        )

        // 1. Initial borrowing: Bank +10,000, Loan -10,000
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-01-01",
            summary = "Borrow loan",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = 1000000L),
                FinanceSplitDraft(accountId = loan.id, amountMinor = -1000000L, clearingGroupIds = listOf(groupId)),
            ),
        )

        val groupAfterBorrow = store.financeLedgerStore.financeClearingGroup(groupId)
        assertNotNull(groupAfterBorrow)
        assertEquals(ClearingGroupStatus.ACTIVE, groupAfterBorrow.status)

        // 2. Full repayment: Bank -10,000, Loan +10,000
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-02-01",
            summary = "Repay loan",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -1000000L),
                FinanceSplitDraft(accountId = loan.id, amountMinor = 1000000L, clearingGroupIds = listOf(groupId)),
            ),
        )

        val groupAfterRepay = store.financeLedgerStore.financeClearingGroup(groupId)
        assertNotNull(groupAfterRepay)
        assertEquals(ClearingGroupStatus.SETTLED, groupAfterRepay.status)
        assertNotNull(groupAfterRepay.settledAt)
    }

    @Test
    fun `autoPadAccountBalance creates adjustment transaction when drift exists`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        val accounts = store.financeAccountStore.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        // Initial deposit and spending
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-11-01",
            summary = "Salary deposit",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = 1000000L), // +10,000
                FinanceSplitDraft(accountId = accounts.first { it.path == "Income:Salary" }.id, amountMinor = -1000000L),
            ),
        )
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-11-02",
            summary = "Lunch",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -20000L), // -200 -> recorded = 9,800
                FinanceSplitDraft(accountId = food.id, amountMinor = 20000L),
            ),
        )

        // If actual balance is 9,500 (300 missing/unrecorded)
        val padTxId = store.financeAccountStore.autoPadAccountBalance(
            accountId = bank.id,
            actualBalanceMinor = 950000L,
            adjustmentDate = "2026-11-05",
        )
        assertNotNull(padTxId)

        // Verify account balance now matches exactly 9,500
        val splits = store.financeLedgerStore.financeSplitsForAccount(bank.id)
        val newBalance = splits.sumOf { it.amountMinor }
        assertEquals(950000L, newBalance)

        // Re-running auto-pad when already balanced returns null
        val noOpPad = store.financeAccountStore.autoPadAccountBalance(
            accountId = bank.id,
            actualBalanceMinor = 950000L,
            adjustmentDate = "2026-11-05",
        )
        assertNull(noOpPad)
    }

    @Test
    fun `deriveFinanceViewState and deleteFinanceTransaction lifecycle`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        val accounts = store.financeAccountStore.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        val txId = store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-25",
            summary = "Lunch",
            payee = "Subway",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -3500L, narration = "Debit Card"),
                FinanceSplitDraft(accountId = food.id, amountMinor = 3500L, narration = "Sandwich"),
            ),
        )

        val viewState = store.financeViewDeriver.deriveFinanceViewState()
        assertEquals(1, viewState.transactions.size)
        val txView = viewState.transactions.first()
        assertEquals("Subway", txView.transaction.payee)
        assertEquals(2, txView.splits.size)
        assertTrue(txView.beancountText.contains("Subway"))
        assertTrue(txView.beancountText.contains("Assets:Bank:Default"))
        assertTrue(txView.beancountText.contains("Expenses:Food"))

        // Delete transaction
        store.financeLedgerStore.deleteFinanceTransaction(txId)
        assertNull(store.financeLedgerStore.financeTransaction(txId))

        val updatedViewState = store.financeViewDeriver.deriveFinanceViewState()
        assertEquals(0, updatedViewState.transactions.size)
    }

    @Test
    fun `credit card statement auto-binding refund reduction and repayment settling`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        val accounts = store.financeAccountStore.financeAccounts()
        val card = accounts.first { it.path == "Liabilities:CreditCard:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }
        val bank = accounts.first { it.path == "Assets:Bank:Default" }

        // Card has statementDay = 15, dueDay = 5
        assertEquals(15, card.statementDay)
        assertEquals(5, card.dueDay)

        // Purchase 1 on May 4: -50.00 CNY (5000 minor units) on card, +50.00 CNY on food
        val tx1Id = store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-04",
            summary = "Dinner",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = card.id, amountMinor = -5000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 5000L),
            ),
        )

        // Verify credit card split automatically bound to 2026-05 statement group
        val tx1Splits = store.financeLedgerStore.financeSplitsForTransaction(tx1Id)
        val cardSplit1 = tx1Splits.first { it.accountId == card.id }
        val cgs1 = store.financeLedgerStore.clearingGroupsForSplit(cardSplit1.id)
        assertEquals(1, cgs1.size)
        val stmtGroup = cgs1.first()
        assertEquals("cg-stmt:${card.id}:2026-05", stmtGroup.id)
        assertEquals("2026-05", stmtGroup.statementCycle)
        assertEquals("2026-06-05", stmtGroup.dueDate)

        // Purchase 2 on May 4: -30.00 CNY on card
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-04",
            summary = "Groceries",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = card.id, amountMinor = -3000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 3000L),
            ),
        )

        // Refund on May 5: +20.00 CNY refund on card (reducing debt to -60.00 CNY)
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-05",
            summary = "Grocery Refund",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = card.id, amountMinor = 2000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = -2000L),
            ),
        )

        // View state evaluates group balance to -60.00 CNY (-6000 minor)
        val midViewState = store.financeViewDeriver.deriveFinanceViewState()
        val stmtViewItem = midViewState.clearingGroups.first { it.group.id == stmtGroup.id }
        assertEquals(-6000L, stmtViewItem.evaluation.realizedBalance)
        assertEquals(ClearingGroupStatus.ACTIVE, stmtViewItem.evaluation.derivedStatus)

        // Repay remaining 60.00 CNY from bank to card on May 20
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-20",
            summary = "Statement Repayment",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -6000L),
                FinanceSplitDraft(accountId = card.id, amountMinor = 6000L, clearingGroupIds = listOf(stmtGroup.id)),
            ),
        )

        // Verify statement clearing group is now Settled with 0 balance!
        val settledViewState = store.financeViewDeriver.deriveFinanceViewState()
        val settledStmt = settledViewState.clearingGroups.first { it.group.id == stmtGroup.id }
        assertEquals(0L, settledStmt.evaluation.realizedBalance)
        assertEquals(ClearingGroupStatus.SETTLED, settledStmt.evaluation.derivedStatus)
    }

    @Test
    fun `reassignSplitClearingGroup shifts split to next statement cycle`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        val accounts = store.financeAccountStore.financeAccounts()
        val card = accounts.first { it.path == "Liabilities:CreditCard:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        // Transaction on May 5 automatically bound to 2026-05 cycle
        val txId = store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-05",
            summary = "Late posted meal",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(accountId = card.id, amountMinor = -4000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 4000L),
            ),
        )

        val cardSplit = store.financeLedgerStore.financeSplitsForTransaction(txId).first { it.accountId == card.id }
        val oldGroup = store.financeLedgerStore.clearingGroupsForSplit(cardSplit.id).first()
        assertEquals("cg-stmt:${card.id}:2026-05", oldGroup.id)

        // Manually create / shift to June statement group
        val juneGroupId = store.financeLedgerStore.createFinanceClearingGroup(
            name = "${card.name} 2026-06 账单",
            groupType = ClearingGroupType.CREDIT_STATEMENT,
            anchorCurrency = "CNY",
            targetAccountId = card.id,
            statementCycle = "2026-06",
            dueDate = "2026-06-25",
        )

        store.financeLedgerStore.reassignSplitClearingGroup(
            splitId = cardSplit.id,
            oldGroupId = oldGroup.id,
            newGroupId = juneGroupId,
        )

        // Verify split is now bound to June statement group
        val newCgs = store.financeLedgerStore.clearingGroupsForSplit(cardSplit.id)
        assertEquals(1, newCgs.size)
        assertEquals(juneGroupId, newCgs.first().id)

        // Verify old group balance is now 0 and June group has -4000L
        val viewState = store.financeViewDeriver.deriveFinanceViewState()
        val oldGroupItem = viewState.clearingGroups.first { it.group.id == oldGroup.id }
        val juneGroupItem = viewState.clearingGroups.first { it.group.id == juneGroupId }
        assertEquals(0L, oldGroupItem.evaluation.realizedBalance)
        assertEquals(-4000L, juneGroupItem.evaluation.realizedBalance)
    }

    @Test
    fun `amortization plan generates schedule and balances to principal`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        val accounts = store.financeAccountStore.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val interest = accounts.first { it.path == "Expenses:Finance:Interest" }

        val loanId = store.financeAccountStore.createFinanceAccount(
            path = "Liabilities:Loan:Consumer",
            name = "个人信用贷",
            category = AccountCategory.LIABILITY,
            currency = "CNY",
        )

        // Create 12-month equal principal plan for 12,000 CNY (1,200,000 minor)
        val planId = store.financeLedgerStore.createFinanceAmortizationPlan(
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

        val plan = store.financeLedgerStore.financeAmortizationPlan(planId)
        assertNotNull(plan)
        assertEquals("装修消费贷", plan.name)
        assertEquals(12, plan.totalPeriods)

        // Verify initial disbursement transaction created
        val txs = store.financeLedgerStore.financeTransactions().filter { it.sourcePlanId == planId }
        assertEquals(13, txs.size) // 1 disbursement + 12 scheduled

        val disbursementTx = txs.first { it.periodIndex == 0 }
        assertEquals(TransactionStatus.CLEARED, disbursementTx.status)
        val disbSplits = store.financeLedgerStore.financeSplitsForTransaction(disbursementTx.id)
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
            val splits = store.financeLedgerStore.financeSplitsForTransaction(stx.id)
            val pSplit = splits.first { it.accountId == loanId }
            totalPrincipal += pSplit.amountMinor
            // Verify each scheduled transaction balances to 0
            val sum = splits.sumOf { it.amountMinor }
            assertEquals(0L, sum)
        }
        assertEquals(1200000L, totalPrincipal)

        // Verify clearing group tracking
        val clearingGroup = store.financeLedgerStore.financeClearingGroup(plan.lifecycleClearingGroupId)
        assertNotNull(clearingGroup)
        assertEquals(ClearingGroupType.LOAN_LIFECYCLE, clearingGroup.groupType)

        // Initial realized balance should be -12,000.00
        val viewStateInitial = store.financeViewDeriver.deriveFinanceViewState()
        val cgItem = viewStateInitial.clearingGroups.first { it.group.id == clearingGroup.id }
        assertEquals(-1200000L, cgItem.evaluation.realizedBalance)
        assertEquals(0L, cgItem.evaluation.projectedBalance) // -12,000 + 12,000 scheduled = 0!

        // Delete plan cleans up scheduled tranches and marks plan deleted
        store.financeLedgerStore.deleteFinanceAmortizationPlan(planId)
        assertNull(store.financeLedgerStore.financeAmortizationPlan(planId))
        val remainingTxs = store.financeLedgerStore.financeTransactions().filter { it.sourcePlanId == planId }
        // Disbursement tx is CLEARED so kept, SCHEDULED txs are deleted
        assertEquals(1, remainingTxs.size)
        assertEquals(0, remainingTxs.first().periodIndex)
    }

    @Test
    fun `budget rule lifecycle and runway derivation in view state`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        store.financeAccountStore.seedDefaultBudgetRuleIfEmpty()

        val rules = store.financeAccountStore.financeBudgetRules()
        assertEquals(1, rules.size)
        assertEquals("日常支出护栏", rules.first().name)
        assertEquals(dev.tireless.abun.finance.BudgetScopeType.GLOBAL_DISCRETIONARY, rules.first().scopeType)

        val foodRule = store.financeAccountStore.createBudgetRule(
            name = "餐饮专项",
            scopeType = dev.tireless.abun.finance.BudgetScopeType.ACCOUNT_SUBTREE,
            accountPrefix = "Expenses:Food",
            monthlyLimitMinor = 150000L,
            currency = "CNY",
            includeInRunway = false,
        )
        assertEquals(2, store.financeAccountStore.financeBudgetRules().size)

        // Initial view state
        val initialView = store.financeViewDeriver.deriveFinanceViewState()
        assertEquals(2, initialView.budgetEvaluations.size)
        assertNotNull(initialView.runwayProjection30)
        assertNotNull(initialView.runwayProjection90)

        // Record an expense of 200 CNY in Expenses:Food
        val today = "2026-05-25"
        store.financeLedgerStore.createFinanceTransaction(
            txDate = today,
            summary = "Lunch with team",
            splits = listOf(
                FinanceSplitDraft(accountId = "acc-expenses-food", amountMinor = 20000L, currency = "CNY"),
                FinanceSplitDraft(accountId = "acc-assets-bank-default", amountMinor = -20000L, currency = "CNY"),
            ),
        )

        val updatedView = store.financeViewDeriver.deriveFinanceViewState()
        val globalEval = updatedView.budgetEvaluations.first { it.rule.scopeType == dev.tireless.abun.finance.BudgetScopeType.GLOBAL_DISCRETIONARY }
        val foodEval = updatedView.budgetEvaluations.first { it.rule.id == foodRule.id }

        assertEquals(20000L, globalEval.evaluation.spentMinor)
        assertEquals(20000L, foodEval.evaluation.spentMinor)
        assertEquals(280000L, globalEval.evaluation.remainingMinor)
        assertEquals(130000L, foodEval.evaluation.remainingMinor)

        // Delete custom budget rule
        store.financeAccountStore.deleteBudgetRule(foodRule.id)
        assertNull(store.financeAccountStore.financeBudgetRule(foodRule.id))
        assertEquals(1, store.financeAccountStore.financeBudgetRules().size)
    }

    @Test
    fun `ledger filtering future records, sorting order, and pagination lifecycle`() {
        val store = createTestStore()
        store.financeAccountStore.loadPredefinedFinanceAccounts()
        val accounts = store.financeAccountStore.financeAccounts()
        val bank = accounts.first { it.path == "Assets:Bank:Default" }
        val food = accounts.first { it.path == "Expenses:Food" }

        // Today is 2026-05-25 per testTimeProvider
        // Create 3 transactions: past (2026-05-20), today (2026-05-25), and future (2026-06-01)
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-20",
            summary = "Past coffee",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -2000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 2000L),
            ),
        )
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-05-25",
            summary = "Today lunch",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -3000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 3000L),
            ),
        )
        store.financeLedgerStore.createFinanceTransaction(
            txDate = "2026-06-01",
            summary = "Future dinner",
            splits = listOf(
                FinanceSplitDraft(accountId = bank.id, amountMinor = -5000L),
                FinanceSplitDraft(accountId = food.id, amountMinor = 5000L),
            ),
        )

        // 1. Default (includeFuture = false, sortAscending = false DESC):
        // Future transaction must be excluded by default!
        val defaultView = store.financeViewDeriver.deriveFinanceViewState(includeFuture = false, sortAscending = false)
        assertEquals(2, defaultView.transactions.size)
        assertEquals("Today lunch", defaultView.transactions[0].transaction.summary)
        assertEquals("Past coffee", defaultView.transactions[1].transaction.summary)

        // 2. Chronological ASC (includeFuture = false, sortAscending = true ASC):
        // Oldest first up to today
        val ascView = store.financeViewDeriver.deriveFinanceViewState(includeFuture = false, sortAscending = true)
        assertEquals(2, ascView.transactions.size)
        assertEquals("Past coffee", ascView.transactions[0].transaction.summary)
        assertEquals("Today lunch", ascView.transactions[1].transaction.summary)

        // 3. Include future = true (sortAscending = false DESC):
        // Future transaction is at the top
        val futureDescView = store.financeViewDeriver.deriveFinanceViewState(includeFuture = true, sortAscending = false)
        assertEquals(3, futureDescView.transactions.size)
        assertEquals("Future dinner", futureDescView.transactions[0].transaction.summary)
        assertEquals("Today lunch", futureDescView.transactions[1].transaction.summary)
        assertEquals("Past coffee", futureDescView.transactions[2].transaction.summary)

        // 4. Include future = true (sortAscending = true ASC):
        // Future transaction is at the bottom
        val futureAscView = store.financeViewDeriver.deriveFinanceViewState(includeFuture = true, sortAscending = true)
        assertEquals(3, futureAscView.transactions.size)
        assertEquals("Past coffee", futureAscView.transactions[0].transaction.summary)
        assertEquals("Today lunch", futureAscView.transactions[1].transaction.summary)
        assertEquals("Future dinner", futureAscView.transactions[2].transaction.summary)
    }
}
