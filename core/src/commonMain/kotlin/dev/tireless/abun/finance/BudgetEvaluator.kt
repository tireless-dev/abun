package dev.tireless.abun.finance

import kotlin.math.roundToLong

data class BudgetEvaluationResult(
    val ruleId: String,
    val ruleName: String,
    val currency: String,
    val monthlyLimitMinor: Long,
    val spentMinor: Long,
    val remainingMinor: Long,
    val timeElapsedRatio: Double,
    val spendRatio: Double,
    val pacingDeltaMinor: Long, // positive = overspent relative to time elapsed; negative = underspent / safe
    val recommendedDailyAllowanceMinor: Long,
    val isOverBudget: Boolean,
    val isAheadOfPacing: Boolean,
)

object BudgetEvaluator {

    fun evaluate(
        rule: FinanceBudgetRule,
        targetYear: Int,
        targetMonth: Int,
        currentDate: String, // "YYYY-MM-DD"
        accounts: Map<String, FinanceAccount>,
        transactions: Map<String, FinanceTransaction>,
        splits: List<FinanceSplit>,
    ): BudgetEvaluationResult {
        val daysInMonth = StatementCycleCalculator.daysInMonth(targetYear, targetMonth)
        val parts = currentDate.split("-")
        val currentYear = parts.getOrNull(0)?.toIntOrNull() ?: targetYear
        val currentMonth = parts.getOrNull(1)?.toIntOrNull() ?: targetMonth
        val currentDay = parts.getOrNull(2)?.toIntOrNull() ?: 1

        val clampedDay = if (currentYear == targetYear && currentMonth == targetMonth) {
            currentDay.coerceIn(1, daysInMonth)
        } else if (currentYear > targetYear || (currentYear == targetYear && currentMonth > targetMonth)) {
            daysInMonth
        } else {
            1
        }

        val timeElapsedRatio = clampedDay.toDouble() / daysInMonth.toDouble()
        val remainingDays = (daysInMonth - clampedDay + 1).coerceAtLeast(1)

        val excludedPrefixes = parseExcludedPrefixes(rule.excludedPrefixesJson)

        var totalSpentMinor = 0L

        for (split in splits) {
            if (split.isDeleted) continue
            val tx = transactions[split.transactionId] ?: continue
            if (tx.isDeleted || tx.status != TransactionStatus.CLEARED) continue

            // Verify txDate is in the target month
            val txParts = tx.txDate.split("-")
            if (txParts.size != 3) continue
            val txYear = txParts[0].toIntOrNull() ?: continue
            val txMonth = txParts[1].toIntOrNull() ?: continue
            if (txYear != targetYear || txMonth != targetMonth) continue

            val account = accounts[split.accountId] ?: continue

            // Determine if account is matched by rule scope
            val effectivePath = when {
                account.category == AccountCategory.EXPENSE -> account.path
                account.isPassThrough && account.passThroughExpenseAccountId != null -> {
                    accounts[account.passThroughExpenseAccountId]?.path
                }
                else -> null
            } ?: continue

            // Excluded check
            if (excludedPrefixes.any { effectivePath.startsWith(it) }) continue

            val matched = when (rule.scopeType) {
                BudgetScopeType.GLOBAL_DISCRETIONARY -> true
                BudgetScopeType.ACCOUNT_SUBTREE -> effectivePath.startsWith(rule.accountPrefix)
            }

            if (!matched) continue

            // Expense in double-entry is positive (+amount is debit/expense).
            // Normalizing currency:
            val splitExpenseMinor = if (split.currency.equals(rule.currency, ignoreCase = true)) {
                split.amountMinor
            } else if (split.weightCurrency.equals(rule.currency, ignoreCase = true)) {
                split.weightAmountMinor
            } else {
                split.amountMinor
            }

            if (splitExpenseMinor > 0L) {
                totalSpentMinor += splitExpenseMinor
            }
        }

        val remainingMinor = (rule.monthlyLimitMinor - totalSpentMinor).coerceAtLeast(0L)
        val spendRatio = if (rule.monthlyLimitMinor > 0L) {
            totalSpentMinor.toDouble() / rule.monthlyLimitMinor.toDouble()
        } else {
            1.0
        }

        val expectedSpendToDate = (rule.monthlyLimitMinor * timeElapsedRatio).roundToLong()
        val pacingDeltaMinor = totalSpentMinor - expectedSpendToDate
        val recommendedDailyAllowanceMinor = remainingMinor / remainingDays
        val isOverBudget = totalSpentMinor > rule.monthlyLimitMinor
        val isAheadOfPacing = pacingDeltaMinor > 0L

        return BudgetEvaluationResult(
            ruleId = rule.id,
            ruleName = rule.name,
            currency = rule.currency,
            monthlyLimitMinor = rule.monthlyLimitMinor,
            spentMinor = totalSpentMinor,
            remainingMinor = remainingMinor,
            timeElapsedRatio = timeElapsedRatio,
            spendRatio = spendRatio,
            pacingDeltaMinor = pacingDeltaMinor,
            recommendedDailyAllowanceMinor = recommendedDailyAllowanceMinor,
            isOverBudget = isOverBudget,
            isAheadOfPacing = isAheadOfPacing,
        )
    }

    private fun parseExcludedPrefixes(json: String): List<String> {
        val trimmed = json.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
        return trimmed
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotBlank() }
    }
}
