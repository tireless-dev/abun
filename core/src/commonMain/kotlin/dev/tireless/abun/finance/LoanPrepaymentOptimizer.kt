package dev.tireless.abun.finance

import kotlin.math.roundToLong

data class LoanInput(
    val id: String,
    val name: String,
    val principalMinor: Long,
    val annualRateBps: Int,
    val minimumMonthlyPaymentMinor: Long,
)

data class StrategyResult(
    val totalInterestMinor: Long,
    val totalMonths: Int,
    val interestSavedMinor: Long,
    val monthsShortened: Int,
    val payoffOrder: List<String>,
)

data class PrepaymentComparison(
    val baseline: StrategyResult,
    val avalanche: StrategyResult,
    val snowball: StrategyResult,
)

object LoanPrepaymentOptimizer {

    fun simulate(
        loans: List<LoanInput>,
        extraMonthlyPaymentMinor: Long = 0L,
    ): PrepaymentComparison {
        val baseline = simulateStrategy(loans, extraMonthlyPaymentMinor = 0L, strategy = StrategyType.NONE)
        val avalanche = simulateStrategy(loans, extraMonthlyPaymentMinor, strategy = StrategyType.AVALANCHE, baseline = baseline)
        val snowball = simulateStrategy(loans, extraMonthlyPaymentMinor, strategy = StrategyType.SNOWBALL, baseline = baseline)

        return PrepaymentComparison(
            baseline = baseline,
            avalanche = avalanche,
            snowball = snowball,
        )
    }

    private enum class StrategyType { NONE, AVALANCHE, SNOWBALL }

    private class LoanState(
        val input: LoanInput,
        var remainingMinor: Long,
    ) {
        val monthlyRate: Double = (input.annualRateBps.toDouble() / 10_000.0) / 12.0
    }

    private fun simulateStrategy(
        loans: List<LoanInput>,
        extraMonthlyPaymentMinor: Long,
        strategy: StrategyType,
        baseline: StrategyResult? = null,
    ): StrategyResult {
        if (loans.isEmpty()) {
            return StrategyResult(0L, 0, 0L, 0, emptyList())
        }

        val states = loans.filter { it.principalMinor > 0 }.map { LoanState(it, it.principalMinor) }.toMutableList()
        var totalInterest = 0L
        var month = 0
        val payoffOrder = mutableListOf<String>()
        val maxMonths = 360 // 30-year safety bound

        val originalTotalCommitment = loans.sumOf { it.minimumMonthlyPaymentMinor } + extraMonthlyPaymentMinor

        while (states.any { it.remainingMinor > 0 } && month < maxMonths) {
            month++

            // 1. Accrue monthly interest on all active balances
            for (state in states) {
                if (state.remainingMinor > 0) {
                    val interest = (state.remainingMinor * state.monthlyRate).roundToLong()
                    state.remainingMinor += interest
                    totalInterest += interest
                }
            }

            // 2. Pay minimums across all active loans
            var budgetRemaining = originalTotalCommitment
            for (state in states) {
                if (state.remainingMinor > 0) {
                    val minPay = state.input.minimumMonthlyPaymentMinor.coerceAtMost(state.remainingMinor)
                    state.remainingMinor -= minPay
                    budgetRemaining -= minPay
                    if (state.remainingMinor == 0L && state.input.id !in payoffOrder) {
                        payoffOrder.add(state.input.id)
                    }
                }
            }

            // 3. Allocate freed-up snowball capacity & extra prepayment
            if (budgetRemaining > 0L && strategy != StrategyType.NONE) {
                val targetOrder = when (strategy) {
                    StrategyType.AVALANCHE -> states.filter { it.remainingMinor > 0 }
                        .sortedWith(compareByDescending<LoanState> { it.input.annualRateBps }.thenBy { it.remainingMinor })
                    StrategyType.SNOWBALL -> states.filter { it.remainingMinor > 0 }
                        .sortedWith(compareBy<LoanState> { it.remainingMinor }.thenByDescending { it.input.annualRateBps })
                    StrategyType.NONE -> emptyList()
                }

                for (target in targetOrder) {
                    if (budgetRemaining <= 0L) break
                    val pay = budgetRemaining.coerceAtMost(target.remainingMinor)
                    target.remainingMinor -= pay
                    budgetRemaining -= pay
                    if (target.remainingMinor == 0L && target.input.id !in payoffOrder) {
                        payoffOrder.add(target.input.id)
                    }
                }
            }
        }

        val baseInterest = baseline?.totalInterestMinor ?: totalInterest
        val baseMonths = baseline?.totalMonths ?: month

        return StrategyResult(
            totalInterestMinor = totalInterest,
            totalMonths = month,
            interestSavedMinor = (baseInterest - totalInterest).coerceAtLeast(0L),
            monthsShortened = (baseMonths - month).coerceAtLeast(0),
            payoffOrder = payoffOrder,
        )
    }
}
