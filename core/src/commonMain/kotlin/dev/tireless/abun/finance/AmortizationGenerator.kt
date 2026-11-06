package dev.tireless.abun.finance

import kotlin.math.pow
import kotlin.math.roundToLong

data class AmortizationScheduleItem(
    val periodIndex: Int,
    val dueDate: String,
    val principalMinor: Long,
    val interestMinor: Long,
    val feeMinor: Long,
    val totalPaymentMinor: Long,
    val remainingPrincipalMinor: Long,
)

object AmortizationGenerator {

    fun generateSchedule(
        principalMinor: Long,
        totalPeriods: Int,
        method: AmortizationMethod,
        annualRateBps: Int,
        feePerPeriodMinor: Long = 0L,
        repaymentDay: Int,
        firstRepaymentDate: String, // "YYYY-MM-DD"
    ): List<AmortizationScheduleItem> {
        require(principalMinor > 0) { "Principal must be greater than 0" }
        require(totalPeriods > 0) { "Total periods must be greater than 0" }

        val parts = firstRepaymentDate.split("-")
        require(parts.size == 3) { "Invalid firstRepaymentDate: $firstRepaymentDate" }
        val startYear = parts[0].toInt()
        val startMonth = parts[1].toInt()

        val monthlyRate = annualRateBps / 10000.0 / 12.0
        var remainingPrincipal = principalMinor
        val items = mutableListOf<AmortizationScheduleItem>()

        val pmtEqualInstallment: Long = if (method == AmortizationMethod.EQUAL_INSTALLMENT && monthlyRate > 0.0) {
            val factor = (1.0 + monthlyRate).pow(totalPeriods)
            ((principalMinor * monthlyRate * factor) / (factor - 1.0)).roundToLong()
        } else {
            0L
        }

        for (period in 1..totalPeriods) {
            // Calculate due date for this period
            val totalMonths = (startMonth - 1) + (period - 1)
            val currentYear = startYear + totalMonths / 12
            val currentMonth = (totalMonths % 12) + 1
            val maxDay = StatementCycleCalculator.daysInMonth(currentYear, currentMonth)
            val day = if (period == 1) {
                parts[2].toInt().coerceAtMost(maxDay)
            } else {
                repaymentDay.coerceAtMost(maxDay)
            }
            val dueDate = "$currentYear-${currentMonth.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

            val isLastPeriod = period == totalPeriods

            val (periodPrincipal, periodInterest) = when (method) {
                AmortizationMethod.EQUAL_PRINCIPAL -> {
                    val p = if (isLastPeriod) remainingPrincipal else principalMinor / totalPeriods
                    val i = (remainingPrincipal * monthlyRate).roundToLong()
                    p to i
                }
                AmortizationMethod.EQUAL_INSTALLMENT -> {
                    if (monthlyRate == 0.0) {
                        val p = if (isLastPeriod) remainingPrincipal else principalMinor / totalPeriods
                        p to 0L
                    } else {
                        val i = (remainingPrincipal * monthlyRate).roundToLong()
                        val p = if (isLastPeriod) remainingPrincipal else (pmtEqualInstallment - i).coerceAtMost(remainingPrincipal)
                        p to i
                    }
                }
                AmortizationMethod.FLAT_FEE -> {
                    val p = if (isLastPeriod) remainingPrincipal else principalMinor / totalPeriods
                    p to 0L
                }
                AmortizationMethod.INTEREST_FIRST -> {
                    val p = if (isLastPeriod) remainingPrincipal else 0L
                    val i = (principalMinor * monthlyRate).roundToLong()
                    p to i
                }
            }

            remainingPrincipal -= periodPrincipal
            val totalPayment = periodPrincipal + periodInterest + feePerPeriodMinor

            items.add(
                AmortizationScheduleItem(
                    periodIndex = period,
                    dueDate = dueDate,
                    principalMinor = periodPrincipal,
                    interestMinor = periodInterest,
                    feeMinor = feePerPeriodMinor,
                    totalPaymentMinor = totalPayment,
                    remainingPrincipalMinor = remainingPrincipal.coerceAtLeast(0L),
                ),
            )
        }

        return items
    }
}
