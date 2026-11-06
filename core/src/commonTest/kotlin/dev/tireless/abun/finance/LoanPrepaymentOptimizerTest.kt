package dev.tireless.abun.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoanPrepaymentOptimizerTest {

    @Test
    fun `avalanche targets highest APR while snowball targets smallest principal`() {
        val loanHighApr = LoanInput(
            id = "loan-credit-card",
            name = "信用卡账单分期",
            principalMinor = 1_000_000L, // 10,000 CNY
            annualRateBps = 1500, // 15.00%
            minimumMonthlyPaymentMinor = 50_000L, // 500 CNY / mo
        )
        val loanLowAprSmallBalance = LoanInput(
            id = "loan-device",
            name = "数码分期",
            principalMinor = 300_000L, // 3,000 CNY
            annualRateBps = 450, // 4.50%
            minimumMonthlyPaymentMinor = 25_000L, // 250 CNY / mo
        )

        val comparison = LoanPrepaymentOptimizer.simulate(
            loans = listOf(loanHighApr, loanLowAprSmallBalance),
            extraMonthlyPaymentMinor = 30_000L, // Extra 300 CNY / mo
        )

        // Baseline:
        assertTrue(comparison.baseline.totalMonths > 0)
        assertTrue(comparison.baseline.totalInterestMinor > 0)
        assertEquals(0L, comparison.baseline.interestSavedMinor)
        assertEquals(0, comparison.baseline.monthsShortened)

        // Avalanche saves at least as much or more interest than Snowball
        assertTrue(comparison.avalanche.interestSavedMinor >= comparison.snowball.interestSavedMinor)
        assertTrue(comparison.avalanche.totalInterestMinor <= comparison.snowball.totalInterestMinor)
        assertTrue(comparison.avalanche.monthsShortened > 0)

        // Snowball: targets smallest balance (loan-device) first
        assertEquals("loan-device", comparison.snowball.payoffOrder.first())
        assertTrue(comparison.snowball.monthsShortened > 0)
        assertTrue(comparison.snowball.interestSavedMinor > 0)
    }

    @Test
    fun `handles empty or zero balance loans gracefully`() {
        val emptyResult = LoanPrepaymentOptimizer.simulate(emptyList(), extraMonthlyPaymentMinor = 50000L)
        assertEquals(0L, emptyResult.baseline.totalInterestMinor)
        assertEquals(0, emptyResult.baseline.totalMonths)
        assertEquals(0L, emptyResult.avalanche.interestSavedMinor)
        assertEquals(0L, emptyResult.snowball.interestSavedMinor)
    }
}
