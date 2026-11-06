package dev.tireless.abun.finance

data class DailyCashflowPoint(
    val date: String,
    val dayIndex: Int,
    val projectedLiquidCashMinor: Long,
    val scheduledInflowsMinor: Long,
    val scheduledOutflowsMinor: Long,
    val statementObligationsMinor: Long,
    val dailyDiscretionaryBurnMinor: Long,
    val isDeficit: Boolean,
)

data class CashflowRunwayProjection(
    val initialLiquidCashMinor: Long,
    val safeToSpendMinor: Long,
    val deficitDate: String?,
    val daysOfRunway: Int,
    val minimumTroughMinor: Long,
    val minimumTroughDate: String,
    val dailyPoints: List<DailyCashflowPoint>,
)

object CashflowRunwayEngine {

    fun projectRunway(
        currentDate: String, // "YYYY-MM-DD"
        horizonDays: Int = 90,
        currentLiquidCashMinor: Long,
        scheduledTransactions: List<FinanceTransaction>,
        splitsByTx: Map<String, List<FinanceSplit>>,
        liquidAccountIds: Set<String>,
        unsettledStatements: List<FinanceClearingGroup>,
        statementBalancesByGroupId: Map<String, Long>,
        dailyLivingBurnMinor: Long,
    ): CashflowRunwayProjection {
        require(horizonDays > 0) { "horizonDays must be > 0" }

        val scheduledByDate = scheduledTransactions
            .filter { !it.isDeleted && it.status == TransactionStatus.SCHEDULED }
            .groupBy { it.txDate }

        // Statements with due date and debt (< 0 realized balance)
        val statementsByDueDate = unsettledStatements
            .filter { !it.isDeleted && it.status == ClearingGroupStatus.ACTIVE && it.dueDate != null }
            .groupBy { it.dueDate!! }

        val points = mutableListOf<DailyCashflowPoint>()
        var runningCash = currentLiquidCashMinor
        var deficitDate: String? = null
        var minTroughMinor = currentLiquidCashMinor
        var minTroughDate = currentDate

        var dateCursor = currentDate

        for (d in 0..horizonDays) {
            val scheduledTxs = scheduledByDate[dateCursor].orEmpty()
            var inflows = 0L
            var outflows = 0L

            for (tx in scheduledTxs) {
                val splits = splitsByTx[tx.id].orEmpty()
                for (split in splits) {
                    if (split.accountId in liquidAccountIds) {
                        if (split.amountMinor > 0L) {
                            inflows += split.amountMinor
                        } else if (split.amountMinor < 0L) {
                            outflows += (-split.amountMinor)
                        }
                    }
                }
            }

            val dueStatements = statementsByDueDate[dateCursor].orEmpty()
            var statementObligations = 0L
            for (stmt in dueStatements) {
                val balance = statementBalancesByGroupId[stmt.id] ?: 0L
                if (balance < 0L) {
                    statementObligations += (-balance)
                }
            }

            val burn = if (d > 0) dailyLivingBurnMinor else 0L

            runningCash = runningCash + inflows - outflows - statementObligations - burn

            val isDeficit = runningCash < 0L
            if (isDeficit && deficitDate == null) {
                deficitDate = dateCursor
            }

            if (runningCash < minTroughMinor) {
                minTroughMinor = runningCash
                minTroughDate = dateCursor
            }

            points.add(
                DailyCashflowPoint(
                    date = dateCursor,
                    dayIndex = d,
                    projectedLiquidCashMinor = runningCash,
                    scheduledInflowsMinor = inflows,
                    scheduledOutflowsMinor = outflows,
                    statementObligationsMinor = statementObligations,
                    dailyDiscretionaryBurnMinor = burn,
                    isDeficit = isDeficit,
                ),
            )

            dateCursor = nextDay(dateCursor)
        }

        val daysOfRunway = if (deficitDate != null) {
            points.indexOfFirst { it.date == deficitDate }.coerceAtLeast(0)
        } else {
            horizonDays
        }

        val safeToSpend = minTroughMinor.coerceAtLeast(0L)

        return CashflowRunwayProjection(
            initialLiquidCashMinor = currentLiquidCashMinor,
            safeToSpendMinor = safeToSpend,
            deficitDate = deficitDate,
            daysOfRunway = daysOfRunway,
            minimumTroughMinor = minTroughMinor,
            minimumTroughDate = minTroughDate,
            dailyPoints = points,
        )
    }

    fun nextDay(dateStr: String): String {
        val parts = dateStr.split("-")
        var year = parts[0].toInt()
        var month = parts[1].toInt()
        var day = parts[2].toInt()

        val daysInCurMonth = StatementCycleCalculator.daysInMonth(year, month)
        if (day < daysInCurMonth) {
            day += 1
        } else {
            day = 1
            if (month < 12) {
                month += 1
            } else {
                month = 1
                year += 1
            }
        }
        return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }
}
