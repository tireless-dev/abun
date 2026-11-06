package dev.tireless.abun.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmortizationTest {

    @Test
    fun `equal principal schedule sums exactly to total principal`() {
        val principal = 1200000L // 12,000.00 CNY
        val periods = 12
        val schedule = AmortizationGenerator.generateSchedule(
            principalMinor = principal,
            totalPeriods = periods,
            method = AmortizationMethod.EQUAL_PRINCIPAL,
            annualRateBps = 600, // 6.00%
            feePerPeriodMinor = 0L,
            repaymentDay = 15,
            firstRepaymentDate = "2026-06-15",
        )

        assertEquals(periods, schedule.size)
        val sumPrincipal = schedule.sumOf { it.principalMinor }
        assertEquals(principal, sumPrincipal)

        // Remaining principal reaches exactly 0
        assertEquals(0L, schedule.last().remainingPrincipalMinor)

        // Interest should monotonically decrease as principal is repaid
        for (i in 0 until schedule.size - 1) {
            assertTrue(schedule[i].interestMinor >= schedule[i + 1].interestMinor)
        }
    }

    @Test
    fun `equal installment schedule sums exactly to total principal`() {
        val principal = 1000000L // 10,000.00 CNY
        val periods = 6
        val schedule = AmortizationGenerator.generateSchedule(
            principalMinor = principal,
            totalPeriods = periods,
            method = AmortizationMethod.EQUAL_INSTALLMENT,
            annualRateBps = 480, // 4.80%
            feePerPeriodMinor = 0L,
            repaymentDay = 10,
            firstRepaymentDate = "2026-06-10",
        )

        assertEquals(periods, schedule.size)
        val sumPrincipal = schedule.sumOf { it.principalMinor }
        assertEquals(principal, sumPrincipal)
        assertEquals(0L, schedule.last().remainingPrincipalMinor)
    }

    @Test
    fun `flat fee schedule includes fee each period and reconciles principal`() {
        val principal = 600000L // 6,000.00 CNY
        val feePerPeriod = 3000L // 30.00 CNY
        val periods = 6
        val schedule = AmortizationGenerator.generateSchedule(
            principalMinor = principal,
            totalPeriods = periods,
            method = AmortizationMethod.FLAT_FEE,
            annualRateBps = 0,
            feePerPeriodMinor = feePerPeriod,
            repaymentDay = 20,
            firstRepaymentDate = "2026-07-20",
        )

        assertEquals(periods, schedule.size)
        assertEquals(principal, schedule.sumOf { it.principalMinor })
        schedule.forEach {
            assertEquals(feePerPeriod, it.feeMinor)
        }
    }

    @Test
    fun `interest first schedule pays principal only on final period`() {
        val principal = 500000L // 5,000.00 CNY
        val periods = 3
        val schedule = AmortizationGenerator.generateSchedule(
            principalMinor = principal,
            totalPeriods = periods,
            method = AmortizationMethod.INTEREST_FIRST,
            annualRateBps = 1200, // 12.00%
            repaymentDay = 5,
            firstRepaymentDate = "2026-08-05",
        )

        assertEquals(periods, schedule.size)
        assertEquals(0L, schedule[0].principalMinor)
        assertEquals(0L, schedule[1].principalMinor)
        assertEquals(principal, schedule[2].principalMinor)
        assertEquals(principal, schedule.sumOf { it.principalMinor })
    }
}
