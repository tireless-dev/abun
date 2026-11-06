package dev.tireless.abun

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.BudgetEvaluationViewItem
import dev.tireless.abun.app.FinanceAccountViewItem
import dev.tireless.abun.app.FinanceAmortizationPlanViewItem
import dev.tireless.abun.app.FinanceClearingGroupViewItem
import dev.tireless.abun.app.FinanceLedgerFilter
import dev.tireless.abun.app.FinanceLedgerViewMode
import dev.tireless.abun.app.FinanceSplitDraft
import dev.tireless.abun.app.FinanceSplitViewItem
import dev.tireless.abun.app.FinanceTransactionViewItem
import dev.tireless.abun.app.FinanceViewState
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.AmortizationMethod
import dev.tireless.abun.finance.BudgetEvaluationResult
import dev.tireless.abun.finance.BudgetScopeType
import dev.tireless.abun.finance.CashflowRunwayProjection
import dev.tireless.abun.finance.ClearingGroupEvaluation
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.finance.DailyCashflowPoint
import dev.tireless.abun.finance.FinanceAccount
import dev.tireless.abun.finance.FinanceAmortizationPlan
import dev.tireless.abun.finance.FinanceBudgetRule
import dev.tireless.abun.finance.FinanceClearingGroup
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.FinanceSplit
import dev.tireless.abun.finance.FinanceTransaction
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.ui.screens.ClearingHubScreen
import dev.tireless.abun.ui.screens.FinanceCockpitScreen
import dev.tireless.abun.ui.screens.FinanceLedgerScreen
import dev.tireless.abun.ui.sheets.AmortizationWizardSheetContent
import dev.tireless.abun.ui.sheets.BudgetRuleSheetContent
import dev.tireless.abun.ui.sheets.CombinedRepaymentSheetContent
import dev.tireless.abun.ui.sheets.CreateFinanceTransactionContent
import dev.tireless.abun.ui.sheets.FinanceAccountsSheetContent
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FinanceUiTest {

    private fun testFinanceState(
        transactions: List<FinanceTransactionViewItem> = emptyList(),
        viewMode: FinanceLedgerViewMode = FinanceLedgerViewMode.STANDARD,
    ): AppUiState {
        val bankAccount = FinanceAccount(
            id = "acc-bank-1",
            path = "Assets:Bank:CMB",
            name = "招商银行",
            category = AccountCategory.ASSET,
            currency = "CNY",
            liquidTier = LiquidTier.LIQUID,
            includeInNetWorth = true,
        )
        val foodAccount = FinanceAccount(
            id = "acc-food-1",
            path = "Expenses:Food:Lunch",
            name = "工作午餐",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
            includeInNetWorth = false,
        )

        val accountViews = listOf(
            FinanceAccountViewItem(bankAccount, 55500L, FinanceCurrencies.format(55500L, "CNY"), 1),
            FinanceAccountViewItem(foodAccount, 33300L, FinanceCurrencies.format(33300L, "CNY"), 1),
        )

        return screenshotState(selectedTab = AppTab.FINANCE).copy(
            financeView = FinanceViewState(
                accounts = accountViews,
                transactions = transactions,
                netWorthMinor = 123400L,
                formattedNetWorth = "¥1,234.00",
                liquidAssetsMinor = 88800L,
                formattedLiquidAssets = "¥888.00",
                viewMode = viewMode,
            ),
        )
    }

    @Test
    fun `finance ledger screen renders metrics and toggles view mode`() = runDesktopComposeUiTest {
        val tx = FinanceTransaction(
            id = "tx-1",
            txDate = "2026-11-05",
            payee = "Starbucks",
            summary = "Latte",
            status = TransactionStatus.CLEARED,
        )
        val splits = listOf(
            FinanceSplitViewItem(
                split = FinanceSplit(id = "s-1", transactionId = "tx-1", accountId = "acc-bank-1", amountMinor = -3500L, currency = "CNY"),
                accountPath = "Assets:Bank:CMB",
                accountName = "招商银行",
                formattedAmount = "-¥35.00",
            ),
            FinanceSplitViewItem(
                split = FinanceSplit(id = "s-2", transactionId = "tx-1", accountId = "acc-food-1", amountMinor = 3500L, currency = "CNY"),
                accountPath = "Expenses:Food:Lunch",
                accountName = "工作午餐",
                formattedAmount = "¥35.00",
            ),
        )
        val txItem = FinanceTransactionViewItem(
            transaction = tx,
            splits = splits,
            primaryAmountFormatted = "-¥35.00",
            primaryAccountsFormatted = "工作午餐 <- 招商银行",
            beancountText = "2026-11-05 * \"Starbucks\" \"Latte\"\n  Assets:Bank:CMB  -35.00 CNY\n  Expenses:Food:Lunch  35.00 CNY",
        )

        var currentMode by mutableStateOf(FinanceLedgerViewMode.STANDARD)

        setContent {
            AppTheme {
                FinanceLedgerScreen(
                    state = testFinanceState(transactions = listOf(txItem), viewMode = currentMode),
                    onSelectFilter = { _, _, _ -> },
                    onToggleViewMode = {
                        currentMode = if (currentMode == FinanceLedgerViewMode.STANDARD) {
                            FinanceLedgerViewMode.BEANCOUNT
                        } else {
                            FinanceLedgerViewMode.STANDARD
                        }
                    },
                    onOpenCreateTransaction = {},
                    onOpenAccounts = {},
                    onDeleteTransaction = {},
                )
            }
        }

        // Verify Net Worth metric displayed
        onNodeWithText("Net Worth").assertIsDisplayed()
        onNodeWithText("¥1,234.00").assertIsDisplayed()
        onNodeWithText("Liquid Runway").assertIsDisplayed()
        onNodeWithText("¥888.00").assertIsDisplayed()

        // Verify transaction card in Standard view
        onNodeWithText("Starbucks").assertIsDisplayed()
        onNodeWithText("-¥35.00").assertIsDisplayed()

        // Toggle to Beancount View
        onNodeWithTag("finance-view-mode-toggle").performClick()
        waitForIdle()
        onNodeWithTag("beancount-ledger-panel").assertIsDisplayed()
        onNodeWithText("Plain-Text Double-Entry Ledger").assertIsDisplayed()
    }

    @Test
    fun `finance accounts sheet triggers auto-pad reconciliation`() = runDesktopComposeUiTest {
        var paddedAccountId: String? = null
        var paddedActualMinor: Long? = null

        setContent {
            AppTheme {
                FinanceAccountsSheetContent(
                    state = testFinanceState(),
                    onDismiss = {},
                    onAutoPad = { id, actualMinor ->
                        paddedAccountId = id
                        paddedActualMinor = actualMinor
                    },
                    onCreateAccount = { _, _, _, _, _, _, _ -> },
                    onDeleteAccount = {},
                )
            }
        }

        // Open auto-pad drawer for bank account
        onNodeWithTag("finance-pad-btn-acc-bank-1").performClick()
        waitForIdle()

        // Input statement balance 1050.00 CNY (105,000 minor)
        onNodeWithTag("finance-pad-input-acc-bank-1").performTextInput("1050.00")

        // Confirm Pad
        onNodeWithTag("finance-pad-confirm-btn-acc-bank-1").performClick()
        waitForIdle()

        assertEquals("acc-bank-1", paddedAccountId)
        assertEquals(105000L, paddedActualMinor)
    }

    @Test
    fun `create transaction sheet submits 2-split balanced transaction`() = runDesktopComposeUiTest {
        var createdDate: String? = null
        var createdSummary: String? = null
        var createdPayee: String? = null
        var createdStatus: TransactionStatus? = null
        var createdSplits: List<FinanceSplitDraft>? = null

        setContent {
            AppTheme {
                CreateFinanceTransactionContent(
                    state = testFinanceState(),
                    onDismiss = {},
                    onCreate = { txDate, summary, payee, status, splits ->
                        createdDate = txDate
                        createdSummary = summary
                        createdPayee = payee
                        createdStatus = status
                        createdSplits = splits
                    },
                )
            }
        }

        // Input Payee, Summary, Amount
        onNodeWithTag("finance-tx-payee-input").performTextInput("Apple Store")
        onNodeWithTag("finance-tx-summary-input").performTextInput("Accessories")
        onNodeWithTag("finance-fast-amount-input").performTextInput("120.00")

        // Submit Entry
        onNodeWithTag("finance-tx-submit-btn").performClick()

        assertEquals("Apple Store", createdPayee)
        assertEquals("Accessories", createdSummary)
        assertEquals(TransactionStatus.CLEARED, createdStatus)
        assertNotNull(createdSplits)
        assertEquals(2, createdSplits!!.size)
        // Verify double-entry balancing (-12000 and +12000)
        assertEquals(-12000L, createdSplits!![0].amountMinor)
        assertEquals(12000L, createdSplits!![1].amountMinor)
        assertEquals(0L, createdSplits!!.sumOf { it.amountMinor })
    }

    @Test
    fun `clearing hub screen renders statement cards and amortization plans`() = runDesktopComposeUiTest {
        val stmtGroup = FinanceClearingGroup(
            id = "cg-stmt-1",
            name = "招商信用卡 2026-05 账单",
            groupType = ClearingGroupType.CREDIT_STATEMENT,
            dueDate = "2026-05-25",
            status = ClearingGroupStatus.ACTIVE,
        )
        val stmtViewItem = FinanceClearingGroupViewItem(
            group = stmtGroup,
            evaluation = ClearingGroupEvaluation(realizedBalance = -345000L, projectedBalance = -345000L, derivedStatus = ClearingGroupStatus.ACTIVE, clearedSplitCount = 2),
            formattedRealizedBalance = "-¥3,450.00",
            formattedProjectedBalance = "-¥3,450.00",
        )

        val plan = FinanceAmortizationPlan(
            id = "plan-1",
            name = "MacBook Pro 12期分期",
            principalMinor = 1200000L,
            totalPeriods = 12,
            repaymentMethod = AmortizationMethod.EQUAL_PRINCIPAL,
            startDate = "2026-04-15",
            repaymentDay = 15,
            liabilityAccountId = "acc-loan-1",
            interestAccountId = "acc-int-1",
            lifecycleClearingGroupId = "cg-loan-1",
        )
        val planViewItem = FinanceAmortizationPlanViewItem(
            plan = plan,
            liabilityAccountName = "分期负债",
            fundingAccountName = "招商银行",
            formattedPrincipal = "¥12,000.00",
            paidPrincipalMinor = 300000L,
            formattedPaidPrincipal = "¥3,000.00",
            remainingPrincipalMinor = 900000L,
            formattedRemainingPrincipal = "¥9,000.00",
            progressPercent = 0.25f,
            clearedPeriods = 3,
            totalPeriods = 12,
            nextDueDate = "2026-07-15",
            nextPaymentFormatted = "¥1,036.00",
            clearingGroup = null,
        )

        val scheduledTrancheTx = FinanceTransaction(
            id = "tx-tranche-4",
            txDate = "2026-07-15",
            summary = "MacBook Pro (第 4/12 期)",
            status = TransactionStatus.SCHEDULED,
            sourcePlanId = "plan-1",
            periodIndex = 4,
        )
        val trancheView = FinanceTransactionViewItem(
            transaction = scheduledTrancheTx,
            splits = emptyList(),
            primaryAmountFormatted = "-¥1,036.00",
            primaryAccountsFormatted = "招商银行 -> 分期负债",
            beancountText = "",
        )

        val baseState = testFinanceState(transactions = listOf(trancheView))
        val state = baseState.copy(
            financeView = baseState.financeView.copy(
                clearingGroups = listOf(stmtViewItem),
                amortizationPlans = listOf(planViewItem),
            ),
        )

        var repaidStmtId: String? = null
        var repaidTxId: String? = null

        setContent {
            AppTheme {
                ClearingHubScreen(
                    state = state,
                    onOpenLedger = {},
                    onOpenCombinedRepayment = {},
                    onOpenAmortizationWizard = {},
                    onRepayStatement = { repaidStmtId = it },
                    onRepayLoanTranche = { repaidTxId = it },
                    onDeleteAmortizationPlan = {},
                )
            }
        }

        // Verify statement card rendered
        onNodeWithTag("statement-card-cg-stmt-1").assertIsDisplayed()
        onNodeWithText("招商信用卡 2026-05 账单").assertIsDisplayed()
        onNodeWithText("Due: 2026-05-25").assertIsDisplayed()

        // Verify amortization plan card rendered
        onNodeWithTag("amortization-plan-card-plan-1").assertIsDisplayed()
        onNodeWithText("MacBook Pro 12期分期").assertIsDisplayed()
        onNodeWithText("3/12 periods").assertIsDisplayed()

        // Click repay statement
        onNodeWithTag("repay-statement-btn-cg-stmt-1").performClick()
        assertEquals("cg-stmt-1", repaidStmtId)

        // Click repay tranche
        onNodeWithTag("repay-tranche-btn-plan-1").performClick()
        assertEquals("tx-tranche-4", repaidTxId)
    }

    @Test
    fun `combined repayment sheet confirms batch repayment`() = runDesktopComposeUiTest {
        val stmtGroup = FinanceClearingGroup(
            id = "cg-stmt-1",
            name = "招商信用卡 2026-05 账单",
            groupType = ClearingGroupType.CREDIT_STATEMENT,
            dueDate = "2026-05-25",
            status = ClearingGroupStatus.ACTIVE,
        )
        val stmtViewItem = FinanceClearingGroupViewItem(
            group = stmtGroup,
            evaluation = ClearingGroupEvaluation(realizedBalance = -200000L, projectedBalance = -200000L, derivedStatus = ClearingGroupStatus.ACTIVE, clearedSplitCount = 2),
            formattedRealizedBalance = "-¥2,000.00",
            formattedProjectedBalance = "-¥2,000.00",
        )

        val scheduledTx = FinanceTransaction(
            id = "tx-plan-p1",
            txDate = "2026-05-15",
            summary = "消费贷第1期",
            status = TransactionStatus.SCHEDULED,
            sourcePlanId = "plan-loan",
            periodIndex = 1,
        )
        val splitOutflow = FinanceSplitViewItem(
            split = FinanceSplit(id = "s-out", transactionId = "tx-plan-p1", accountId = "acc-bank-1", amountMinor = -100000L),
            accountPath = "Assets:Bank:CMB",
            accountName = "招商银行",
            formattedAmount = "-¥1,000.00",
        )
        val trancheView = FinanceTransactionViewItem(
            transaction = scheduledTx,
            splits = listOf(splitOutflow),
            primaryAmountFormatted = "-¥1,000.00",
            primaryAccountsFormatted = "Bank",
            beancountText = "",
        )

        val baseState = testFinanceState(transactions = listOf(trancheView))
        val state = baseState.copy(
            financeView = baseState.financeView.copy(
                clearingGroups = listOf(stmtViewItem),
            ),
        )

        var repaidStmtId: String? = null
        var repaidFundingAcc: String? = null
        var repaidTrancheTxId: String? = null

        setContent {
            AppTheme {
                CombinedRepaymentSheetContent(
                    state = state,
                    initialClearingGroupId = "cg-stmt-1",
                    onDismiss = {},
                    onRepayStatement = { id, funding, _ ->
                        repaidStmtId = id
                        repaidFundingAcc = funding
                    },
                    onRepayLoanTranche = { id ->
                        repaidTrancheTxId = id
                    },
                )
            }
        }

        onNodeWithTag("combined-repayment-sheet").assertIsDisplayed()
        onNodeWithTag("repay-item-stmt-cg-stmt-1").assertIsDisplayed()

        // Select tranche item as well
        onNodeWithTag("repay-item-tranche-tx-plan-p1").performClick()

        // Click confirm repayment
        onNodeWithTag("repay-confirm-btn").performClick()

        assertEquals("cg-stmt-1", repaidStmtId)
        assertEquals("acc-bank-1", repaidFundingAcc)
        assertEquals("tx-plan-p1", repaidTrancheTxId)
    }

    @Test
    fun `amortization wizard sheet submits new loan plan`() = runDesktopComposeUiTest {
        var createdName: String? = null
        var createdPrincipalMinor: Long? = null
        var createdPeriods: Int? = null
        var createdMethod: AmortizationMethod? = null
        var createdLiabilityId: String? = null

        val liabilityAccount = FinanceAccount(
            id = "acc-loan-auto",
            path = "Liabilities:Loan:Car",
            name = "车贷",
            category = AccountCategory.LIABILITY,
            currency = "CNY",
        )
        val state = testFinanceState().copy(
            financeView = testFinanceState().financeView.copy(
                accounts = testFinanceState().financeView.accounts + FinanceAccountViewItem(
                    account = liabilityAccount,
                    balanceMinor = 0L,
                    formattedBalance = "¥0.00",
                    splitCount = 0,
                ),
            ),
        )

        setContent {
            AppTheme {
                AmortizationWizardSheetContent(
                    state = state,
                    onDismiss = {},
                    onCreate = { name, principalMinor, _, totalPeriods, repaymentMethod, _, _, _, _, liabilityAccountId, _, _, _ ->
                        createdName = name
                        createdPrincipalMinor = principalMinor
                        createdPeriods = totalPeriods
                        createdMethod = repaymentMethod
                        createdLiabilityId = liabilityAccountId
                    },
                )
            }
        }

        onNodeWithTag("amortization-wizard-sheet").assertIsDisplayed()

        // Input plan details
        onNodeWithTag("plan-name-input").performTextInput("新能源车分期")
        onNodeWithTag("plan-principal-input").performTextInput("150000.00")

        // Submit plan
        onNodeWithTag("plan-create-btn").performScrollTo().performClick()

        assertEquals("新能源车分期", createdName)
        assertEquals(15000000L, createdPrincipalMinor)
        assertEquals(12, createdPeriods)
        assertEquals(AmortizationMethod.EQUAL_INSTALLMENT, createdMethod)
        assertEquals("acc-loan-auto", createdLiabilityId)
    }

    @Test
    fun `finance cockpit screen renders hero metrics runway radar and dual needle pacing bar`() = runDesktopComposeUiTest {
        val rule = FinanceBudgetRule(
            id = "budget-rule-1",
            name = "日常支出护栏",
            scopeType = BudgetScopeType.GLOBAL_DISCRETIONARY,
            monthlyLimitMinor = 300000L,
            currency = "CNY",
        )
        val evalResult = BudgetEvaluationResult(
            ruleId = "budget-rule-1",
            ruleName = "日常支出护栏",
            currency = "CNY",
            monthlyLimitMinor = 300000L,
            spentMinor = 120000L,
            remainingMinor = 180000L,
            timeElapsedRatio = 0.33,
            spendRatio = 0.40,
            pacingDeltaMinor = 21000L,
            recommendedDailyAllowanceMinor = 9000L,
            isOverBudget = false,
            isAheadOfPacing = true,
        )
        val budgetItem = BudgetEvaluationViewItem(
            rule = rule,
            evaluation = evalResult,
            formattedSpent = "¥1,200.00",
            formattedLimit = "¥3,000.00",
            formattedRemaining = "¥1,800.00",
            formattedDailyAllowance = "¥90.00",
            formattedPacingDelta = "¥210.00",
        )

        val runwayPoint = DailyCashflowPoint(
            date = "2026-11-20",
            dayIndex = 15,
            projectedLiquidCashMinor = -50000L,
            scheduledInflowsMinor = 0L,
            scheduledOutflowsMinor = 80000L,
            statementObligationsMinor = 0L,
            dailyDiscretionaryBurnMinor = 9000L,
            isDeficit = true,
        )
        val runwayProjection = CashflowRunwayProjection(
            initialLiquidCashMinor = 500000L,
            safeToSpendMinor = 0L,
            deficitDate = "2026-11-20",
            daysOfRunway = 15,
            minimumTroughMinor = -50000L,
            minimumTroughDate = "2026-11-20",
            dailyPoints = listOf(runwayPoint),
        )

        val state = testFinanceState().copy(
            financeView = testFinanceState().financeView.copy(
                budgetEvaluations = listOf(budgetItem),
                runwayProjection90 = runwayProjection,
                runwayHorizonDays = 90,
                safeToSpendMinor = 0L,
                formattedSafeToSpend = "¥0.00",
            ),
        )

        var selectedHorizon: Int? = null

        setContent {
            AppTheme {
                FinanceCockpitScreen(
                    state = state,
                    onOpenLedger = {},
                    onOpenClearingHub = {},
                    onOpenCreateTransaction = {},
                    onOpenCreateBudgetRule = {},
                    onSelectHorizon = { selectedHorizon = it },
                    onDeleteBudgetRule = {},
                )
            }
        }

        onNodeWithTag("finance-cockpit-screen").assertIsDisplayed()
        onNodeWithTag("safe-to-spend-card").assertIsDisplayed()
        onNodeWithTag("safe-to-spend-amount").assertIsDisplayed()
        onNodeWithTag("runway-radar-banner").assertIsDisplayed()
        onNodeWithTag("pacing-bar-budget-rule-1").assertIsDisplayed()
        onNodeWithTag("time-needle-budget-rule-1").assertIsDisplayed()

        // Toggle horizon
        onNodeWithTag("runway-horizon-30").performScrollTo().performClick()
        assertEquals(30, selectedHorizon)
    }

    @Test
    fun `budget rule sheet allows creating a new budget rule`() = runDesktopComposeUiTest {
        var createdName = ""
        var createdScope: BudgetScopeType? = null
        var createdPrefix = ""
        var createdLimitMinor = 0L
        var createdCurrency = ""
        var createdIncludeInRunway = false

        setContent {
            AppTheme {
                BudgetRuleSheetContent(
                    state = testFinanceState(),
                    onDismiss = {},
                    onCreate = { name, scopeType, accountPrefix, monthlyLimitMinor, currency, includeInRunway ->
                        createdName = name
                        createdScope = scopeType
                        createdPrefix = accountPrefix
                        createdLimitMinor = monthlyLimitMinor
                        createdCurrency = currency
                        createdIncludeInRunway = includeInRunway
                    },
                )
            }
        }

        onNodeWithTag("budget-rule-sheet").assertIsDisplayed()

        // Input details
        onNodeWithTag("budget-name-input").performTextInput("餐饮专项")
        onNodeWithTag("scope-subtree-btn").performClick()
        onNodeWithTag("budget-prefix-input").performTextInput("Expenses:Food")
        onNodeWithTag("budget-limit-input").performTextInput("1500")

        onNodeWithTag("save-budget-rule-btn").performClick()

        assertTrue(createdName.contains("餐饮专项"))
        assertEquals(BudgetScopeType.ACCOUNT_SUBTREE, createdScope)
        assertTrue(createdPrefix.contains("Expenses:Food"))
        assertTrue(createdLimitMinor > 0L)
        assertEquals("CNY", createdCurrency)
    }
}
