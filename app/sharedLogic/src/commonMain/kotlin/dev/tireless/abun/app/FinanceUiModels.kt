package dev.tireless.abun.app

import dev.tireless.abun.finance.BudgetEvaluationResult
import dev.tireless.abun.finance.CashflowRunwayProjection
import dev.tireless.abun.finance.ClearingGroupEvaluation
import dev.tireless.abun.finance.FinanceAccount
import dev.tireless.abun.finance.FinanceAmortizationPlan
import dev.tireless.abun.finance.FinanceBudgetRule
import dev.tireless.abun.finance.FinanceClearingGroup
import dev.tireless.abun.finance.FinanceSplit
import dev.tireless.abun.finance.FinanceTransaction

enum class FinanceLedgerFilter {
    ALL,
    DRAFTS,
    SCHEDULED,
    BY_ACCOUNT,
    BY_CLEARING_GROUP,
}

enum class FinanceLedgerViewMode {
    STANDARD,
    BEANCOUNT,
}

data class FinanceSplitViewItem(
    val split: FinanceSplit,
    val accountPath: String,
    val accountName: String,
    val formattedAmount: String,
    val formattedWeight: String? = null,
    val clearingGroups: List<FinanceClearingGroup> = emptyList(),
)

data class FinanceTransactionViewItem(
    val transaction: FinanceTransaction,
    val splits: List<FinanceSplitViewItem>,
    val primaryAmountFormatted: String,
    val primaryAccountsFormatted: String,
    val beancountText: String,
)

data class FinanceAccountViewItem(
    val account: FinanceAccount,
    val balanceMinor: Long,
    val formattedBalance: String,
    val splitCount: Int,
)

data class FinanceClearingGroupViewItem(
    val group: FinanceClearingGroup,
    val evaluation: ClearingGroupEvaluation,
    val formattedRealizedBalance: String,
    val formattedProjectedBalance: String,
)

data class FinanceAmortizationPlanViewItem(
    val plan: FinanceAmortizationPlan,
    val liabilityAccountName: String,
    val fundingAccountName: String?,
    val formattedPrincipal: String,
    val paidPrincipalMinor: Long,
    val formattedPaidPrincipal: String,
    val remainingPrincipalMinor: Long,
    val formattedRemainingPrincipal: String,
    val progressPercent: Float,
    val clearedPeriods: Int,
    val totalPeriods: Int,
    val nextDueDate: String?,
    val nextPaymentFormatted: String?,
    val clearingGroup: FinanceClearingGroup?,
)

data class BudgetEvaluationViewItem(
    val rule: FinanceBudgetRule,
    val evaluation: BudgetEvaluationResult,
    val formattedSpent: String,
    val formattedLimit: String,
    val formattedRemaining: String,
    val formattedDailyAllowance: String,
    val formattedPacingDelta: String,
)

data class FinanceViewState(
    val accounts: List<FinanceAccountViewItem> = emptyList(),
    val transactions: List<FinanceTransactionViewItem> = emptyList(),
    val clearingGroups: List<FinanceClearingGroupViewItem> = emptyList(),
    val amortizationPlans: List<FinanceAmortizationPlanViewItem> = emptyList(),
    val budgetEvaluations: List<BudgetEvaluationViewItem> = emptyList(),
    val runwayProjection30: CashflowRunwayProjection? = null,
    val runwayProjection90: CashflowRunwayProjection? = null,
    val runwayHorizonDays: Int = 90,
    val safeToSpendMinor: Long = 0L,
    val formattedSafeToSpend: String = "¥0.00",
    val netWorthMinor: Long = 0L,
    val formattedNetWorth: String = "¥0.00",
    val liquidAssetsMinor: Long = 0L,
    val formattedLiquidAssets: String = "¥0.00",
    val selectedFilter: FinanceLedgerFilter = FinanceLedgerFilter.ALL,
    val filterAccountId: String? = null,
    val filterClearingGroupId: String? = null,
    val viewMode: FinanceLedgerViewMode = FinanceLedgerViewMode.STANDARD,
) {
    val currentRunwayProjection: CashflowRunwayProjection?
        get() = if (runwayHorizonDays == 30) runwayProjection30 else runwayProjection90
}
