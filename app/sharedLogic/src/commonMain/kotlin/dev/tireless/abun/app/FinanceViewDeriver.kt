package dev.tireless.abun.app

import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.BudgetEvaluator
import dev.tireless.abun.finance.CashflowRunwayEngine
import dev.tireless.abun.finance.ClearingGroupEvaluator
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.finance.TransactionStatus

class FinanceViewDeriver(private val store: LocalStore) {
    fun deriveFinanceViewState(
        selectedFilter: FinanceLedgerFilter = FinanceLedgerFilter.ALL,
        filterAccountId: String? = null,
        filterClearingGroupId: String? = null,
        viewMode: FinanceLedgerViewMode = FinanceLedgerViewMode.STANDARD,
        runwayHorizonDays: Int = 90,
    ): FinanceViewState {
        val accounts = store.queries.selectAllFinanceAccounts(store.financeAccountStore::mapFinanceAccountRow).executeAsList()
        val accountsById = accounts.associateBy { it.id }

        val allSplits = store.queries.selectAllActiveFinanceSplits(store.financeLedgerStore::mapFinanceSplitRow).executeAsList()
        val splitsByAccount = allSplits.groupBy { it.accountId }
        val splitsByTx = allSplits.groupBy { it.transactionId }

        val allClearingGroups = store.queries.selectAllFinanceClearingGroups(store.financeLedgerStore::mapFinanceClearingGroupRow).executeAsList()
        val cgMap = allClearingGroups.associateBy { it.id }

        val allLinks = store.queries.selectAllActiveFinanceSplitClearingGroups(store.financeLedgerStore::mapFinanceSplitClearingGroupRow).executeAsList()
        val linksBySplitId = allLinks.groupBy { it.splitId }
        val cgsBySplitId = linksBySplitId.mapValues { (_, links) ->
            links.mapNotNull { cgMap[it.clearingGroupId] }
        }
        val splitsByCgId = allLinks.groupBy { it.clearingGroupId }.mapValues { (_, links) ->
            val splitIds = links.map { it.splitId }.toSet()
            allSplits.filter { it.id in splitIds }
        }

        val accountViews = accounts.map { acc ->
            val splits = splitsByAccount[acc.id].orEmpty()
            val balance = splits.sumOf { it.amountMinor }
            FinanceAccountViewItem(
                account = acc,
                balanceMinor = balance,
                formattedBalance = FinanceCurrencies.format(balance, acc.currency),
                splitCount = splits.size,
            )
        }

        val allTxs = store.queries.selectAllFinanceTransactions(store.financeLedgerStore::mapFinanceTransactionRow).executeAsList()
        val allTxsById = allTxs.associateBy { it.id }

        val clearingGroupViews = allClearingGroups.map { cg ->
            val splits = splitsByCgId[cg.id].orEmpty()
            val eval = ClearingGroupEvaluator.evaluate(cg, splits, allTxsById)
            FinanceClearingGroupViewItem(
                group = cg.copy(
                    status = eval.derivedStatus,
                ),
                evaluation = eval,
                formattedRealizedBalance = FinanceCurrencies.format(eval.realizedBalance, cg.anchorCurrency),
                formattedProjectedBalance = FinanceCurrencies.format(eval.projectedBalance, cg.anchorCurrency),
            )
        }

        val transactionViews = allTxs.map { tx ->
            val splits = splitsByTx[tx.id].orEmpty()
            val splitViews = splits.map { split ->
                val acc = accountsById[split.accountId]
                val hasWeight = split.currency != split.weightCurrency || split.amountMinor != split.weightAmountMinor
                FinanceSplitViewItem(
                    split = split,
                    accountPath = acc?.path ?: split.accountId,
                    accountName = acc?.name ?: split.accountId,
                    formattedAmount = FinanceCurrencies.format(split.amountMinor, split.currency),
                    formattedWeight = if (hasWeight) "@@ ${FinanceCurrencies.format(split.weightAmountMinor, split.weightCurrency)}" else null,
                    clearingGroups = cgsBySplitId[split.id].orEmpty(),
                )
            }

            val outflowSplit = splits.find { it.amountMinor < 0 }
            val inflowSplit = splits.find { it.amountMinor > 0 }
            val primaryAmountFormatted = when {
                outflowSplit != null -> FinanceCurrencies.format(outflowSplit.amountMinor, outflowSplit.currency)
                inflowSplit != null -> FinanceCurrencies.format(inflowSplit.amountMinor, inflowSplit.currency)
                splits.isNotEmpty() -> FinanceCurrencies.format(splits.first().amountMinor, splits.first().currency)
                else -> "0.00"
            }
            val fromPath = outflowSplit?.let { accountsById[it.accountId]?.name ?: accountsById[it.accountId]?.path }
            val toPath = inflowSplit?.let { accountsById[it.accountId]?.name ?: accountsById[it.accountId]?.path }
            val primaryAccountsFormatted = when {
                splits.size > 2 -> "${fromPath ?: "Multiple"} -> (${splits.size} splits)"
                fromPath != null && toPath != null -> "$toPath <- $fromPath"
                fromPath != null -> fromPath
                toPath != null -> toPath
                else -> "Empty"
            }

            val beancountHeader = buildString {
                append(tx.txDate)
                append(" *")
                if (!tx.payee.isNullOrBlank()) append(" \"${tx.payee}\"")
                if (tx.summary.isNotBlank()) append(" \"${tx.summary}\"")
            }
            val beancountBody = splits.joinToString("\n") { s ->
                val path = accountsById[s.accountId]?.path ?: s.accountId
                val amt = FinanceCurrencies.format(s.amountMinor, s.currency)
                val weight = if (s.currency != s.weightCurrency || s.amountMinor != s.weightAmountMinor) {
                    " @@ ${FinanceCurrencies.format(s.weightAmountMinor, s.weightCurrency)}"
                } else ""
                val comment = buildString {
                    if (s.narration.isNotBlank()) append(" ; ${s.narration}")
                    val cgs = cgsBySplitId[s.id].orEmpty()
                    if (cgs.isNotEmpty()) {
                        append(" [${cgs.joinToString { it.name }}]")
                    }
                }
                "  ${path.padEnd(32)} ${amt.padStart(12)}$weight$comment"
            }
            val beancountFull = "$beancountHeader\n$beancountBody"

            FinanceTransactionViewItem(
                transaction = tx,
                splits = splitViews,
                primaryAmountFormatted = primaryAmountFormatted,
                primaryAccountsFormatted = primaryAccountsFormatted,
                beancountText = beancountFull,
            )
        }

        val filteredTransactions = when (selectedFilter) {
            FinanceLedgerFilter.ALL -> transactionViews
            FinanceLedgerFilter.DRAFTS -> transactionViews.filter { it.transaction.status == TransactionStatus.DRAFT }
            FinanceLedgerFilter.SCHEDULED -> transactionViews.filter { it.transaction.status == TransactionStatus.SCHEDULED }
            FinanceLedgerFilter.BY_ACCOUNT -> {
                if (filterAccountId == null) transactionViews
                else transactionViews.filter { tx -> tx.splits.any { it.split.accountId == filterAccountId } }
            }
            FinanceLedgerFilter.BY_CLEARING_GROUP -> {
                if (filterClearingGroupId == null) transactionViews
                else transactionViews.filter { tx ->
                    tx.splits.any { splitView ->
                        splitView.clearingGroups.any { it.id == filterClearingGroupId }
                    }
                }
            }
        }

        val netWorth = accountViews.filter { it.account.includeInNetWorth }.sumOf { it.balanceMinor }
        val liquidAssets = accountViews.filter {
            it.account.category == AccountCategory.ASSET && it.account.liquidTier == LiquidTier.LIQUID
        }.sumOf { it.balanceMinor }

        val allPlans = store.queries.selectAllFinanceAmortizationPlans(store.financeLedgerStore::mapFinanceAmortizationPlanRow).executeAsList()
        val planTxsByPlanId = allTxs.filter { it.sourcePlanId != null }.groupBy { it.sourcePlanId!! }

        val amortizationPlanViews = allPlans.map { plan ->
            val liabilityAcc = accountsById[plan.liabilityAccountId]
            val fundingAcc = plan.fundingAccountId?.let { accountsById[it] }
            val planTxs = planTxsByPlanId[plan.id].orEmpty()
            val clearedTxs = planTxs.filter { it.status == TransactionStatus.CLEARED && (it.periodIndex ?: 0) > 0 }
            val scheduledTxs = planTxs.filter { it.status == TransactionStatus.SCHEDULED && (it.periodIndex ?: 0) > 0 }.sortedBy { it.txDate }

            val paidPrincipal = clearedTxs.sumOf { tx ->
                splitsByTx[tx.id].orEmpty().find { it.accountId == plan.liabilityAccountId }?.amountMinor ?: 0L
            }
            val remainingPrincipal = (plan.principalMinor - paidPrincipal).coerceAtLeast(0L)
            val progress = if (plan.principalMinor > 0) (paidPrincipal.toFloat() / plan.principalMinor.toFloat()).coerceIn(0f, 1f) else 1f
            val nextTx = scheduledTxs.firstOrNull()
            val nextPaymentFormatted = nextTx?.let { tx ->
                val splits = splitsByTx[tx.id].orEmpty()
                val fundingSplit = splits.find { it.amountMinor < 0 }
                if (fundingSplit != null) FinanceCurrencies.format(-fundingSplit.amountMinor, fundingSplit.currency)
                else {
                    val p = splits.find { it.accountId == plan.liabilityAccountId }?.amountMinor ?: 0L
                    val i = splits.find { it.accountId == plan.interestAccountId }?.amountMinor ?: 0L
                    FinanceCurrencies.format(p + i, plan.currency)
                }
            }

            FinanceAmortizationPlanViewItem(
                plan = plan,
                liabilityAccountName = liabilityAcc?.name ?: plan.liabilityAccountId,
                fundingAccountName = fundingAcc?.name,
                formattedPrincipal = FinanceCurrencies.format(plan.principalMinor, plan.currency),
                paidPrincipalMinor = paidPrincipal,
                formattedPaidPrincipal = FinanceCurrencies.format(paidPrincipal, plan.currency),
                remainingPrincipalMinor = remainingPrincipal,
                formattedRemainingPrincipal = FinanceCurrencies.format(remainingPrincipal, plan.currency),
                progressPercent = progress,
                clearedPeriods = clearedTxs.size,
                totalPeriods = plan.totalPeriods,
                nextDueDate = nextTx?.txDate,
                nextPaymentFormatted = nextPaymentFormatted,
                clearingGroup = cgMap[plan.lifecycleClearingGroupId],
            )
        }

        val allBudgetRules = store.queries.selectAllFinanceBudgetRules(store.financeAccountStore::mapFinanceBudgetRuleRow).executeAsList()
        val today = store.timeProvider.today()
        val todayStr = today.toString()
        val txMap = allTxs.associateBy { it.id }

        val budgetEvaluations = allBudgetRules.map { rule ->
            val eval = BudgetEvaluator.evaluate(
                rule = rule,
                targetYear = today.year,
                targetMonth = today.month.ordinal + 1,
                currentDate = todayStr,
                accounts = accountsById,
                transactions = txMap,
                splits = allSplits,
            )
            BudgetEvaluationViewItem(
                rule = rule,
                evaluation = eval,
                formattedSpent = FinanceCurrencies.format(eval.spentMinor, eval.currency),
                formattedLimit = FinanceCurrencies.format(eval.monthlyLimitMinor, eval.currency),
                formattedRemaining = FinanceCurrencies.format(eval.remainingMinor, eval.currency),
                formattedDailyAllowance = FinanceCurrencies.format(eval.recommendedDailyAllowanceMinor, eval.currency),
                formattedPacingDelta = FinanceCurrencies.format(eval.pacingDeltaMinor, eval.currency),
            )
        }

        val runwayRule = allBudgetRules.find { it.includeInRunway }
        val runwayDailyBurn = if (runwayRule != null) {
            val eval = budgetEvaluations.find { it.rule.id == runwayRule.id }?.evaluation
            eval?.recommendedDailyAllowanceMinor ?: (runwayRule.monthlyLimitMinor / 30L)
        } else 0L

        val liquidAccountIds = accountsById.values
            .filter { it.category == AccountCategory.ASSET && it.liquidTier == LiquidTier.LIQUID }
            .map { it.id }
            .toSet()

        val activeStatementCgs = allClearingGroups.filter {
            !it.isDeleted && it.groupType == ClearingGroupType.CREDIT_STATEMENT && it.status == ClearingGroupStatus.ACTIVE
        }
        val statementBalances = clearingGroupViews.associate { it.group.id to it.evaluation.realizedBalance }

        val runway30 = CashflowRunwayEngine.projectRunway(
            currentDate = todayStr,
            horizonDays = 30,
            currentLiquidCashMinor = liquidAssets,
            scheduledTransactions = allTxs,
            splitsByTx = splitsByTx,
            liquidAccountIds = liquidAccountIds,
            unsettledStatements = activeStatementCgs,
            statementBalancesByGroupId = statementBalances,
            dailyLivingBurnMinor = runwayDailyBurn,
        )

        val runway90 = CashflowRunwayEngine.projectRunway(
            currentDate = todayStr,
            horizonDays = 90,
            currentLiquidCashMinor = liquidAssets,
            scheduledTransactions = allTxs,
            splitsByTx = splitsByTx,
            liquidAccountIds = liquidAccountIds,
            unsettledStatements = activeStatementCgs,
            statementBalancesByGroupId = statementBalances,
            dailyLivingBurnMinor = runwayDailyBurn,
        )

        val activeRunway = if (runwayHorizonDays == 30) runway30 else runway90
        val safeToSpend = activeRunway.safeToSpendMinor
        val canLoadPredefined = store.financeAccountStore.canLoadPredefinedFinanceAccounts()

        return FinanceViewState(
            accounts = accountViews,
            transactions = filteredTransactions,
            clearingGroups = clearingGroupViews,
            amortizationPlans = amortizationPlanViews,
            budgetEvaluations = budgetEvaluations,
            runwayProjection30 = runway30,
            runwayProjection90 = runway90,
            runwayHorizonDays = runwayHorizonDays,
            safeToSpendMinor = safeToSpend,
            formattedSafeToSpend = FinanceCurrencies.format(safeToSpend, "CNY"),
            netWorthMinor = netWorth,
            formattedNetWorth = FinanceCurrencies.format(netWorth, "CNY"),
            liquidAssetsMinor = liquidAssets,
            formattedLiquidAssets = FinanceCurrencies.format(liquidAssets, "CNY"),
            selectedFilter = selectedFilter,
            filterAccountId = filterAccountId,
            filterClearingGroupId = filterClearingGroupId,
            viewMode = viewMode,
            canLoadPredefinedAccounts = canLoadPredefined,
            hasSubAccountsOrRecords = !canLoadPredefined,
        )
    }
}
