package dev.tireless.abun.finance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccountCategory {
    @SerialName("Asset") ASSET,
    @SerialName("Liability") LIABILITY,
    @SerialName("Income") INCOME,
    @SerialName("Expense") EXPENSE,
    @SerialName("Equity") EQUITY,
}

@Serializable
enum class LiquidTier {
    @SerialName("Liquid") LIQUID,
    @SerialName("SemiLiquid") SEMI_LIQUID,
    @SerialName("Illiquid") ILLIQUID,
    @SerialName("None") NONE,
}

@Serializable
enum class TransactionStatus {
    @SerialName("Draft") DRAFT,
    @SerialName("Scheduled") SCHEDULED,
    @SerialName("Cleared") CLEARED,
    @SerialName("Void") VOID,
}

@Serializable
enum class ClearingGroupType {
    @SerialName("InstantBatch") INSTANT_BATCH,
    @SerialName("CreditStatement") CREDIT_STATEMENT,
    @SerialName("LoanLifecycle") LOAN_LIFECYCLE,
    @SerialName("Reimbursement") REIMBURSEMENT,
}

@Serializable
enum class ClearingGroupStatus {
    @SerialName("Active") ACTIVE,
    @SerialName("Settled") SETTLED,
    @SerialName("Overdrawn") OVERDRAWN,
}

@Serializable
enum class ExchangeRateSource {
    @SerialName("Manual") MANUAL,
    @SerialName("ImplicitFromSplit") IMPLICIT_FROM_SPLIT,
    @SerialName("RemoteFeed") REMOTE_FEED,
}

@Serializable
enum class AmortizationMethod {
    @SerialName("EqualInstallment") EQUAL_INSTALLMENT,
    @SerialName("EqualPrincipal") EQUAL_PRINCIPAL,
    @SerialName("InterestFirst") INTEREST_FIRST,
    @SerialName("FlatFee") FLAT_FEE,
}

@Serializable
enum class BudgetScopeType {
    @SerialName("GlobalDiscretionary") GLOBAL_DISCRETIONARY,
    @SerialName("AccountSubtree") ACCOUNT_SUBTREE,
}

@Serializable
data class FinanceAccount(
    val id: String,
    val path: String,
    val name: String,
    val category: AccountCategory,
    val currency: String = "CNY",
    @SerialName("include_in_net_worth") val includeInNetWorth: Boolean = true,
    @SerialName("is_pass_through") val isPassThrough: Boolean = false,
    @SerialName("pass_through_expense_account_id") val passThroughExpenseAccountId: String? = null,
    @SerialName("liquid_tier") val liquidTier: LiquidTier = LiquidTier.LIQUID,
    @SerialName("statement_day") val statementDay: Int? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)

@Serializable
data class FinanceTransaction(
    val id: String,
    @SerialName("tx_date") val txDate: String,
    @SerialName("tx_time") val txTime: String? = null,
    val payee: String? = null,
    val summary: String = "",
    val status: TransactionStatus = TransactionStatus.CLEARED,
    @SerialName("source_plan_id") val sourcePlanId: String? = null,
    @SerialName("period_index") val periodIndex: Int? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)

@Serializable
data class FinanceSplit(
    val id: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String = "CNY",
    @SerialName("weight_amount_minor") val weightAmountMinor: Long = amountMinor,
    @SerialName("weight_currency") val weightCurrency: String = currency,
    val narration: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)

@Serializable
data class FinanceClearingGroup(
    val id: String,
    val name: String,
    @SerialName("group_type") val groupType: ClearingGroupType,
    @SerialName("anchor_currency") val anchorCurrency: String = "CNY",
    @SerialName("target_account_id") val targetAccountId: String? = null,
    @SerialName("statement_cycle") val statementCycle: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val status: ClearingGroupStatus = ClearingGroupStatus.ACTIVE,
    @SerialName("settled_at") val settledAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)

@Serializable
data class FinanceSplitClearingGroup(
    val id: String,
    @SerialName("split_id") val splitId: String,
    @SerialName("clearing_group_id") val clearingGroupId: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)

@Serializable
data class FinanceExchangeRate(
    val id: String,
    @SerialName("base_currency") val baseCurrency: String,
    @SerialName("quote_currency") val quoteCurrency: String,
    @SerialName("rate_date") val rateDate: String,
    @SerialName("rate_micros") val rateMicros: Long,
    val source: ExchangeRateSource = ExchangeRateSource.MANUAL,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)

@Serializable
data class FinanceAmortizationPlan(
    val id: String,
    val name: String,
    @SerialName("principal_minor") val principalMinor: Long,
    val currency: String = "CNY",
    @SerialName("total_periods") val totalPeriods: Int,
    @SerialName("repayment_method") val repaymentMethod: AmortizationMethod,
    @SerialName("annual_rate_bps") val annualRateBps: Int = 0,
    @SerialName("fee_per_period_minor") val feePerPeriodMinor: Long = 0L,
    @SerialName("start_date") val startDate: String,
    @SerialName("repayment_day") val repaymentDay: Int,
    @SerialName("liability_account_id") val liabilityAccountId: String,
    @SerialName("funding_account_id") val fundingAccountId: String? = null,
    @SerialName("interest_account_id") val interestAccountId: String,
    @SerialName("lifecycle_clearing_group_id") val lifecycleClearingGroupId: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)

@Serializable
data class FinanceBudgetRule(
    val id: String,
    val name: String,
    @SerialName("scope_type") val scopeType: BudgetScopeType,
    @SerialName("account_prefix") val accountPrefix: String = "Expenses:",
    @SerialName("excluded_prefixes_json") val excludedPrefixesJson: String = "[]",
    @SerialName("monthly_limit_minor") val monthlyLimitMinor: Long,
    val currency: String = "CNY",
    @SerialName("include_in_runway") val includeInRunway: Boolean = true,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)
