package dev.tireless.abun

import dev.tireless.abun.app.AgendaTaskItemView
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.AuthViewState
import dev.tireless.abun.app.BudgetEvaluationViewItem
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.FinanceAccountViewItem
import dev.tireless.abun.app.FinanceAmortizationPlanViewItem
import dev.tireless.abun.app.FinanceClearingGroupViewItem
import dev.tireless.abun.app.FinanceLedgerFilter
import dev.tireless.abun.app.FinanceLedgerViewMode
import dev.tireless.abun.app.FinanceSplitViewItem
import dev.tireless.abun.app.FinanceTransactionViewItem
import dev.tireless.abun.app.FinanceViewState
import dev.tireless.abun.app.LanguagePreference
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroSessionState
import dev.tireless.abun.app.PomodoroSessionView
import dev.tireless.abun.app.PreferencesViewState
import dev.tireless.abun.app.SyncStateView
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.app.TaskViewState
import dev.tireless.abun.app.TodayViewState
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
import dev.tireless.abun.finance.FinanceSplit
import dev.tireless.abun.finance.FinanceTransaction
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.sync.TaskStatus

internal const val ScreenshotNow: Long = 1_779_800_400_000L

internal fun screenshotState(
    selectedTab: AppTab = AppTab.TODAY,
    selectedTaskSubTab: TaskSubTab = TaskSubTab.TASKS,
    auth: AuthViewState = AuthViewState(showGuide = false, mode = AuthMode.GUEST),
    taskView: TaskViewState = populatedTaskView(),
    today: TodayViewState = populatedTodayView(),
    activePomodoroSession: PomodoroSessionView? = null,
    recentPomodoroSessions: List<PomodoroSessionView> = recentPomodoroSessions(),
    financeView: FinanceViewState = FinanceViewState(
        safeToSpendMinor = 1420000L,
        formattedSafeToSpend = "¥14,200.00",
        liquidAssetsMinor = 4050000L,
        formattedLiquidAssets = "¥40,500.00",
        netWorthMinor = 3570000L,
        formattedNetWorth = "¥35,700.00",
    ),
): AppUiState = AppUiState(
    selectedDate = "2026-05-30",
    selectedTab = selectedTab,
    selectedTaskSubTab = selectedTaskSubTab,
    today = today,
    taskView = taskView,
    financeView = financeView,
    activePomodoroSession = activePomodoroSession,
    recentPomodoroSessions = recentPomodoroSessions,
    preferences = PreferencesViewState(
        titlePrefix = "Deep work",
        defaultAlarmLeadMinutes = 20,
        focusMinutes = 30,
        shortBreakMinutes = 6,
        longBreakMinutes = 18,
        timezoneOverride = "Asia/Shanghai",
        dateFormat = DateFormatPreference.WEEKDAY_MONTH_DAY,
        themePreference = ThemePreference.SYSTEM,
        languagePreference = LanguagePreference.EN,
    ),
    syncState = SyncStateView(lastSyncedAt = "2026-05-30 14:20"),
    auth = auth,
)

internal fun populatedTaskView(): TaskViewState = TaskViewState(
    tasks = listOf(
        TaskListItemView("task-1", "Review launch checklist", TaskStatus.IN_PROGRESS),
        TaskListItemView("task-2", "Write compact sync notes", TaskStatus.PENDING),
        TaskListItemView("task-3", "Archive finished inbox items", TaskStatus.COMPLETED),
    ),
)

internal fun populatedTodayView(): TodayViewState = TodayViewState(
    currentTasks = listOf(
        AgendaTaskItemView("task-1", "Review launch checklist", TaskStatus.IN_PROGRESS, "09:30"),
        AgendaTaskItemView("task-2", "Write compact sync notes", TaskStatus.PENDING, "11:00"),
    ),
    upcomingTasks = listOf(
        AgendaTaskItemView("task-4", "Plan tomorrow priorities", TaskStatus.PENDING, "16:45"),
    ),
)

internal fun activePomodoroSession(remainingMinutes: Int = 18): PomodoroSessionView = PomodoroSessionView(
    id = "session-active",
    taskId = "task-1",
    taskTitle = "Review launch checklist",
    phase = PomodoroPhase.FOCUS,
    state = PomodoroSessionState.ACTIVE,
    startedAtEpochMillis = ScreenshotNow - 7 * 60_000L,
    endsAtEpochMillis = ScreenshotNow + remainingMinutes * 60_000L,
    durationMinutes = 25,
    note = "Keep the scope tight.",
)

internal fun recentPomodoroSessions(): List<PomodoroSessionView> = listOf(
    PomodoroSessionView(
        id = "session-1",
        taskId = "task-2",
        taskTitle = "Write compact sync notes",
        phase = PomodoroPhase.FOCUS,
        state = PomodoroSessionState.COMPLETED,
        startedAtEpochMillis = ScreenshotNow - 90 * 60_000L,
        endsAtEpochMillis = ScreenshotNow - 65 * 60_000L,
        completedAtEpochMillis = ScreenshotNow - 65 * 60_000L,
        durationMinutes = 25,
        note = "Captured the open decisions.",
    ),
    PomodoroSessionView(
        id = "session-2",
        phase = PomodoroPhase.SHORT_BREAK,
        state = PomodoroSessionState.COMPLETED,
        startedAtEpochMillis = ScreenshotNow - 55 * 60_000L,
        endsAtEpochMillis = ScreenshotNow - 50 * 60_000L,
        completedAtEpochMillis = ScreenshotNow - 50 * 60_000L,
        durationMinutes = 5,
    ),
)

internal fun populatedFinanceScreenshotState(
    selectedTab: AppTab = AppTab.FINANCE,
): AppUiState {
    val bankAccount = FinanceAccount(
        id = "acc-bank-1",
        path = "Assets:Bank:CMB",
        name = "招商银行储蓄卡",
        category = AccountCategory.ASSET,
        currency = "CNY",
        liquidTier = LiquidTier.LIQUID,
        includeInNetWorth = true,
    )
    val alipayAccount = FinanceAccount(
        id = "acc-alipay-1",
        path = "Assets:Alipay:Balance",
        name = "支付宝余额宝",
        category = AccountCategory.ASSET,
        currency = "CNY",
        liquidTier = LiquidTier.LIQUID,
        includeInNetWorth = true,
    )
    val creditAccount = FinanceAccount(
        id = "acc-cc-1",
        path = "Liabilities:CreditCard:CMB",
        name = "招商银行信用卡",
        category = AccountCategory.LIABILITY,
        currency = "CNY",
        includeInNetWorth = true,
    )
    val foodAccount = FinanceAccount(
        id = "acc-food-1",
        path = "Expenses:Food:Lunch",
        name = "餐饮美食",
        category = AccountCategory.EXPENSE,
        currency = "CNY",
        includeInNetWorth = false,
    )
    val techAccount = FinanceAccount(
        id = "acc-tech-1",
        path = "Expenses:Electronics",
        name = "数码装备",
        category = AccountCategory.EXPENSE,
        currency = "CNY",
        includeInNetWorth = false,
    )

    val accounts = listOf(
        FinanceAccountViewItem(bankAccount, 2850000L, "¥28,500.00", 12),
        FinanceAccountViewItem(alipayAccount, 1200000L, "¥12,000.00", 8),
        FinanceAccountViewItem(creditAccount, -480000L, "-¥4,800.00", 5),
        FinanceAccountViewItem(foodAccount, 185000L, "¥1,850.00", 18),
        FinanceAccountViewItem(techAccount, 32900L, "¥329.00", 1),
    )

    // Transactions for ledger
    val tx1 = FinanceTransaction(
        id = "tx-1",
        txDate = "2026-05-29",
        payee = "Apple Store",
        summary = "MagSafe 双项充电器",
        status = TransactionStatus.CLEARED,
    )
    val tx1Splits = listOf(
        FinanceSplitViewItem(
            split = FinanceSplit(id = "s-1-1", transactionId = "tx-1", accountId = "acc-bank-1", amountMinor = -32900L, currency = "CNY"),
            accountPath = "Assets:Bank:CMB",
            accountName = "招商银行储蓄卡",
            formattedAmount = "-¥329.00",
        ),
        FinanceSplitViewItem(
            split = FinanceSplit(id = "s-1-2", transactionId = "tx-1", accountId = "acc-tech-1", amountMinor = 32900L, currency = "CNY"),
            accountPath = "Expenses:Electronics",
            accountName = "数码装备",
            formattedAmount = "¥329.00",
        ),
    )
    val tx1Item = FinanceTransactionViewItem(
        transaction = tx1,
        splits = tx1Splits,
        primaryAmountFormatted = "-¥329.00",
        primaryAccountsFormatted = "招商银行储蓄卡 → 数码装备",
        beancountText = "2026-05-29 * \"Apple Store\" \"MagSafe 双项充电器\"\n  Assets:Bank:CMB  -329.00 CNY\n  Expenses:Electronics  329.00 CNY",
    )

    val tx2 = FinanceTransaction(
        id = "tx-2",
        txDate = "2026-05-28",
        payee = "盒马鲜生",
        summary = "生鲜果蔬及半成品晚餐",
        status = TransactionStatus.CLEARED,
    )
    val tx2Splits = listOf(
        FinanceSplitViewItem(
            split = FinanceSplit(id = "s-2-1", transactionId = "tx-2", accountId = "acc-alipay-1", amountMinor = -14250L, currency = "CNY"),
            accountPath = "Assets:Alipay:Balance",
            accountName = "支付宝余额宝",
            formattedAmount = "-¥142.50",
        ),
        FinanceSplitViewItem(
            split = FinanceSplit(id = "s-2-2", transactionId = "tx-2", accountId = "acc-food-1", amountMinor = 14250L, currency = "CNY"),
            accountPath = "Expenses:Food:Lunch",
            accountName = "餐饮美食",
            formattedAmount = "¥142.50",
        ),
    )
    val tx2Item = FinanceTransactionViewItem(
        transaction = tx2,
        splits = tx2Splits,
        primaryAmountFormatted = "-¥142.50",
        primaryAccountsFormatted = "支付宝余额宝 → 餐饮美食",
        beancountText = "2026-05-28 * \"盒马鲜生\" \"生鲜果蔬及半成品晚餐\"\n  Assets:Alipay:Balance  -142.50 CNY\n  Expenses:Food:Lunch  142.50 CNY",
    )

    val tx3 = FinanceTransaction(
        id = "tx-3",
        txDate = "2026-05-27",
        payee = "星巴克臻选",
        summary = "冷萃浮乐朵",
        status = TransactionStatus.CLEARED,
    )
    val tx3Splits = listOf(
        FinanceSplitViewItem(
            split = FinanceSplit(id = "s-3-1", transactionId = "tx-3", accountId = "acc-bank-1", amountMinor = -4800L, currency = "CNY"),
            accountPath = "Assets:Bank:CMB",
            accountName = "招商银行储蓄卡",
            formattedAmount = "-¥48.00",
        ),
        FinanceSplitViewItem(
            split = FinanceSplit(id = "s-3-2", transactionId = "tx-3", accountId = "acc-food-1", amountMinor = 4800L, currency = "CNY"),
            accountPath = "Expenses:Food:Lunch",
            accountName = "餐饮美食",
            formattedAmount = "¥48.00",
        ),
    )
    val tx3Item = FinanceTransactionViewItem(
        transaction = tx3,
        splits = tx3Splits,
        primaryAmountFormatted = "-¥48.00",
        primaryAccountsFormatted = "招商银行储蓄卡 → 餐饮美食",
        beancountText = "2026-05-27 * \"星巴克臻选\" \"冷萃浮乐朵\"\n  Assets:Bank:CMB  -48.00 CNY\n  Expenses:Food:Lunch  48.00 CNY",
    )

    // Budget Rules & Evaluations for Cockpit
    val rule1 = FinanceBudgetRule(
        id = "rule-dining",
        name = "日常支出护栏",
        scopeType = BudgetScopeType.GLOBAL_DISCRETIONARY,
        monthlyLimitMinor = 400000L,
        currency = "CNY",
    )
    val eval1 = BudgetEvaluationResult(
        ruleId = "rule-dining",
        ruleName = "日常支出护栏",
        currency = "CNY",
        monthlyLimitMinor = 400000L,
        spentMinor = 185000L,
        remainingMinor = 215000L,
        timeElapsedRatio = 0.40,
        spendRatio = 0.46,
        pacingDeltaMinor = 25000L,
        recommendedDailyAllowanceMinor = 11940L,
        isOverBudget = false,
        isAheadOfPacing = true,
    )
    val budgetItem1 = BudgetEvaluationViewItem(
        rule = rule1,
        evaluation = eval1,
        formattedSpent = "¥1,850.00",
        formattedLimit = "¥4,000.00",
        formattedRemaining = "¥2,150.00",
        formattedDailyAllowance = "¥119.40",
        formattedPacingDelta = "+¥250.00",
    )

    val rule2 = FinanceBudgetRule(
        id = "rule-entertain",
        name = "数码与娱乐",
        scopeType = BudgetScopeType.ACCOUNT_SUBTREE,
        accountPrefix = "Expenses:Electronics",
        monthlyLimitMinor = 200000L,
        currency = "CNY",
    )
    val eval2 = BudgetEvaluationResult(
        ruleId = "rule-entertain",
        ruleName = "数码与娱乐",
        currency = "CNY",
        monthlyLimitMinor = 200000L,
        spentMinor = 32900L,
        remainingMinor = 167100L,
        timeElapsedRatio = 0.40,
        spendRatio = 0.16,
        pacingDeltaMinor = -47100L,
        recommendedDailyAllowanceMinor = 9280L,
        isOverBudget = false,
        isAheadOfPacing = false,
    )
    val budgetItem2 = BudgetEvaluationViewItem(
        rule = rule2,
        evaluation = eval2,
        formattedSpent = "¥329.00",
        formattedLimit = "¥2,000.00",
        formattedRemaining = "¥1,671.00",
        formattedDailyAllowance = "¥92.80",
        formattedPacingDelta = "-¥471.00",
    )

    // Runway projection for Cockpit
    val runwayPoints = listOf(
        DailyCashflowPoint(
            date = "2026-05-30",
            dayIndex = 0,
            projectedLiquidCashMinor = 4050000L,
            scheduledInflowsMinor = 0L,
            scheduledOutflowsMinor = 0L,
            statementObligationsMinor = 0L,
            dailyDiscretionaryBurnMinor = 11940L,
            isDeficit = false,
        ),
        DailyCashflowPoint(
            date = "2026-06-10",
            dayIndex = 11,
            projectedLiquidCashMinor = 3438660L,
            scheduledInflowsMinor = 0L,
            scheduledOutflowsMinor = 0L,
            statementObligationsMinor = 480000L,
            dailyDiscretionaryBurnMinor = 11940L,
            isDeficit = false,
        ),
        DailyCashflowPoint(
            date = "2026-06-15",
            dayIndex = 16,
            projectedLiquidCashMinor = 3278960L,
            scheduledInflowsMinor = 0L,
            scheduledOutflowsMinor = 103600L,
            statementObligationsMinor = 0L,
            dailyDiscretionaryBurnMinor = 11940L,
            isDeficit = false,
        ),
        DailyCashflowPoint(
            date = "2026-06-25",
            dayIndex = 26,
            projectedLiquidCashMinor = 2179560L,
            scheduledInflowsMinor = 0L,
            scheduledOutflowsMinor = 1000000L,
            statementObligationsMinor = 0L,
            dailyDiscretionaryBurnMinor = 11940L,
            isDeficit = false,
        ),
        DailyCashflowPoint(
            date = "2026-07-01",
            dayIndex = 32,
            projectedLiquidCashMinor = 4607920L,
            scheduledInflowsMinor = 2500000L,
            scheduledOutflowsMinor = 0L,
            statementObligationsMinor = 0L,
            dailyDiscretionaryBurnMinor = 11940L,
            isDeficit = false,
        ),
    )
    val runwayProjection = CashflowRunwayProjection(
        initialLiquidCashMinor = 4050000L,
        safeToSpendMinor = 1420000L,
        deficitDate = null,
        daysOfRunway = 90,
        minimumTroughMinor = 2179560L,
        minimumTroughDate = "2026-06-25",
        dailyPoints = runwayPoints,
    )

    // Clearing Groups & Amortization Plans for Clearing Hub
    val stmtGroup = FinanceClearingGroup(
        id = "cg-stmt-cmb-05",
        name = "招商信用卡 2026-05 账单",
        groupType = ClearingGroupType.CREDIT_STATEMENT,
        dueDate = "2026-06-10",
        status = ClearingGroupStatus.ACTIVE,
    )
    val stmtViewItem = FinanceClearingGroupViewItem(
        group = stmtGroup,
        evaluation = ClearingGroupEvaluation(
            realizedBalance = -480000L,
            projectedBalance = -480000L,
            derivedStatus = ClearingGroupStatus.ACTIVE,
            clearedSplitCount = 12,
        ),
        formattedRealizedBalance = "-¥4,800.00",
        formattedProjectedBalance = "-¥4,800.00",
    )

    val loanPlan = FinanceAmortizationPlan(
        id = "plan-macbook",
        name = "MacBook Pro 16 M3 Max 12期分期",
        principalMinor = 1200000L,
        totalPeriods = 12,
        repaymentMethod = AmortizationMethod.EQUAL_PRINCIPAL,
        startDate = "2026-02-15",
        repaymentDay = 15,
        liabilityAccountId = "acc-cc-1",
        interestAccountId = "acc-int-1",
        lifecycleClearingGroupId = "cg-loan-macbook",
    )
    val planViewItem = FinanceAmortizationPlanViewItem(
        plan = loanPlan,
        liabilityAccountName = "招商银行信用卡",
        fundingAccountName = "招商银行储蓄卡",
        formattedPrincipal = "¥12,000.00",
        paidPrincipalMinor = 400000L,
        formattedPaidPrincipal = "¥4,000.00",
        remainingPrincipalMinor = 800000L,
        formattedRemainingPrincipal = "¥8,000.00",
        progressPercent = 0.33f,
        clearedPeriods = 4,
        totalPeriods = 12,
        nextDueDate = "2026-06-15",
        nextPaymentFormatted = "¥1,036.00",
        clearingGroup = null,
    )

    val scheduledTrancheTx = FinanceTransaction(
        id = "tx-tranche-5",
        txDate = "2026-06-15",
        summary = "MacBook Pro (第 5/12 期)",
        status = TransactionStatus.SCHEDULED,
        sourcePlanId = "plan-macbook",
        periodIndex = 5,
    )
    val trancheView = FinanceTransactionViewItem(
        transaction = scheduledTrancheTx,
        splits = emptyList(),
        primaryAmountFormatted = "-¥1,036.00",
        primaryAccountsFormatted = "招商银行储蓄卡 → 招商银行信用卡",
        beancountText = "",
    )

    val allTransactions = listOf(tx1Item, tx2Item, tx3Item, trancheView)

    return screenshotState(selectedTab = selectedTab).copy(
        financeView = FinanceViewState(
            accounts = accounts,
            transactions = allTransactions,
            clearingGroups = listOf(stmtViewItem),
            amortizationPlans = listOf(planViewItem),
            budgetEvaluations = listOf(budgetItem1, budgetItem2),
            runwayProjection30 = runwayProjection,
            runwayProjection90 = runwayProjection,
            runwayHorizonDays = 90,
            safeToSpendMinor = 1420000L,
            formattedSafeToSpend = "¥14,200.00",
            netWorthMinor = 3570000L,
            formattedNetWorth = "¥35,700.00",
            liquidAssetsMinor = 4050000L,
            formattedLiquidAssets = "¥40,500.00",
            viewMode = FinanceLedgerViewMode.STANDARD,
        ),
    )
}
