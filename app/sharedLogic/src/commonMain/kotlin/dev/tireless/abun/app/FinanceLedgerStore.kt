package dev.tireless.abun.app

import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.AmortizationGenerator
import dev.tireless.abun.finance.AmortizationMethod
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.finance.ClearingGroupEvaluator
import dev.tireless.abun.finance.ExchangeRateSource
import dev.tireless.abun.finance.FinanceAmortizationPlan
import dev.tireless.abun.finance.FinanceClearingGroup
import dev.tireless.abun.finance.FinanceExchangeRate
import dev.tireless.abun.finance.FinanceSplit
import dev.tireless.abun.finance.FinanceSplitClearingGroup
import dev.tireless.abun.finance.FinanceTransaction
import dev.tireless.abun.finance.FinanceValidators
import dev.tireless.abun.finance.StatementCycleCalculator
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.finance.TransactionValidationResult

data class FinanceSplitDraft(
    val id: String? = null,
    val accountId: String,
    val amountMinor: Long,
    val currency: String = "CNY",
    val weightAmountMinor: Long? = null,
    val weightCurrency: String? = null,
    val narration: String = "",
    val clearingGroupIds: List<String> = emptyList(),
)

class FinanceLedgerStore(private val store: LocalStore) {
    fun financeTransactions(): List<FinanceTransaction> =
        store.queries.selectAllFinanceTransactions(::mapFinanceTransactionRow).executeAsList()

    fun financeTransaction(id: String): FinanceTransaction? =
        store.queries.selectFinanceTransactionById(id, ::mapFinanceTransactionRow).executeAsOneOrNull()
            ?.takeUnless { it.isDeleted }

    fun financeSplitsForTransaction(transactionId: String): List<FinanceSplit> =
        store.queries.selectFinanceSplitsForTransaction(transactionId, ::mapFinanceSplitRow).executeAsList()

    fun financeSplitsForAccount(accountId: String): List<FinanceSplit> =
        store.queries.selectFinanceSplitsForAccount(accountId, ::mapFinanceSplitRow).executeAsList()

    fun financeClearingGroups(): List<FinanceClearingGroup> =
        store.queries.selectAllFinanceClearingGroups(::mapFinanceClearingGroupRow).executeAsList()

    fun financeClearingGroup(id: String): FinanceClearingGroup? =
        store.queries.selectFinanceClearingGroupById(id, ::mapFinanceClearingGroupRow).executeAsOneOrNull()
            ?.takeUnless { it.isDeleted }

    fun clearingGroupsForSplit(splitId: String): List<FinanceClearingGroup> =
        store.queries.selectClearingGroupsForSplit(splitId, ::mapFinanceClearingGroupRow).executeAsList()

    fun financeAmortizationPlans(): List<FinanceAmortizationPlan> =
        store.queries.selectAllFinanceAmortizationPlans(::mapFinanceAmortizationPlanRow).executeAsList()

    fun financeAmortizationPlan(id: String): FinanceAmortizationPlan? =
        store.queries.selectFinanceAmortizationPlanById(id, ::mapFinanceAmortizationPlanRow).executeAsOneOrNull()
            ?.takeUnless { it.isDeleted }

    fun createFinanceClearingGroup(
        name: String,
        groupType: ClearingGroupType,
        anchorCurrency: String = "CNY",
        targetAccountId: String? = null,
        statementCycle: String? = null,
        dueDate: String? = null,
    ): String = store.database.transactionWithResult {
        val now = store.timeProvider.nowEpochMillis()
        val groupId = store.idGenerator.randomId()
        val hlc = store.clock.next()
        store.queries.upsertFinanceClearingGroup(
            id = groupId,
            name = name,
            group_type = groupType.name,
            anchor_currency = anchorCurrency,
            target_account_id = targetAccountId,
            statement_cycle = statementCycle,
            due_date = dueDate,
            status = ClearingGroupStatus.ACTIVE.name,
            settled_at = null,
            is_deleted = 0L,
            hlc_map = "{\"core\":\"$hlc\"}",
            dirty_fields = "[\"core\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )
        groupId
    }

    fun createFinanceTransaction(
        txDate: String,
        summary: String = "",
        payee: String? = null,
        txTime: String? = null,
        status: TransactionStatus = TransactionStatus.CLEARED,
        sourcePlanId: String? = null,
        periodIndex: Int? = null,
        splits: List<FinanceSplitDraft>,
    ): String = store.database.transactionWithResult {
        val now = store.timeProvider.nowEpochMillis()
        val txId = store.idGenerator.randomId()
        val txHlc = store.clock.next()

        val fullTx = FinanceTransaction(
            id = txId,
            txDate = txDate,
            txTime = txTime,
            payee = payee,
            summary = summary,
            status = status,
            sourcePlanId = sourcePlanId,
            periodIndex = periodIndex,
            isDeleted = false,
        )

        val fullSplits = splits.mapIndexed { index, draft ->
            FinanceSplit(
                id = draft.id ?: store.idGenerator.randomId(),
                transactionId = txId,
                accountId = draft.accountId,
                amountMinor = draft.amountMinor,
                currency = draft.currency,
                weightAmountMinor = draft.weightAmountMinor ?: draft.amountMinor,
                weightCurrency = draft.weightCurrency ?: draft.currency,
                narration = draft.narration,
                sortOrder = index,
                isDeleted = false,
            )
        }

        when (val validation = FinanceValidators.validateTransactionSplits(fullTx, fullSplits)) {
            is TransactionValidationResult.Invalid -> throw IllegalArgumentException(validation.reason)
            is TransactionValidationResult.Valid -> {}
        }

        store.queries.upsertFinanceTransaction(
            id = txId,
            tx_date = txDate,
            tx_time = txTime,
            payee = payee,
            summary = summary,
            status = status.name,
            source_plan_id = sourcePlanId,
            period_index = periodIndex?.toLong(),
            is_deleted = 0L,
            hlc_map = "{\"header\":\"$txHlc\"}",
            dirty_fields = "[\"header\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )

        val touchedClearingGroupIds = mutableSetOf<String>()

        fullSplits.forEachIndexed { index, split ->
            val splitHlc = store.clock.next()
            store.queries.upsertFinanceSplit(
                id = split.id,
                transaction_id = split.transactionId,
                account_id = split.accountId,
                amount_minor = split.amountMinor,
                currency = split.currency,
                weight_amount_minor = split.weightAmountMinor,
                weight_currency = split.weightCurrency,
                narration = split.narration,
                sort_order = split.sortOrder.toLong(),
                is_deleted = 0L,
                hlc_map = "{\"content\":\"$splitHlc\"}",
                dirty_fields = "[\"content\"]",
                is_dirty = 1L,
                server_version = 0L,
                created_at = now,
                updated_at = now,
            )

            val effectiveClearingGroupIds = splits[index].clearingGroupIds.toMutableList()

            if (effectiveClearingGroupIds.isEmpty()) {
                val account = store.queries.selectFinanceAccountById(split.accountId, store.financeAccountStore::mapFinanceAccountRow).executeAsOneOrNull()
                val statementDay = account?.statementDay
                if (account != null && account.category == AccountCategory.LIABILITY && statementDay != null) {
                    val dueDay = account.dueDay ?: statementDay
                    val cycleInfo = StatementCycleCalculator.calculateCycle(
                        accountId = account.id,
                        accountName = account.name,
                        txDate = txDate,
                        statementDay = statementDay,
                        dueDay = dueDay,
                    )
                    val existingGroup = store.queries.selectFinanceClearingGroupById(cycleInfo.deterministicGroupId, ::mapFinanceClearingGroupRow).executeAsOneOrNull()
                    if (existingGroup == null) {
                        val groupHlc = store.clock.next()
                        store.queries.upsertFinanceClearingGroup(
                            id = cycleInfo.deterministicGroupId,
                            name = cycleInfo.defaultGroupName,
                            group_type = ClearingGroupType.CREDIT_STATEMENT.name,
                            anchor_currency = account.currency,
                            target_account_id = account.id,
                            statement_cycle = cycleInfo.cycleTag,
                            due_date = cycleInfo.dueDate,
                            status = ClearingGroupStatus.ACTIVE.name,
                            settled_at = null,
                            is_deleted = 0L,
                            hlc_map = "{\"core\":\"$groupHlc\"}",
                            dirty_fields = "[\"core\"]",
                            is_dirty = 1L,
                            server_version = 0L,
                            created_at = now,
                            updated_at = now,
                        )
                    }
                    effectiveClearingGroupIds.add(cycleInfo.deterministicGroupId)
                }
            }

            for (cgId in effectiveClearingGroupIds) {
                touchedClearingGroupIds.add(cgId)
                val linkId = "scg:${split.id}:$cgId"
                val linkHlc = store.clock.next()
                store.queries.upsertFinanceSplitClearingGroup(
                    id = linkId,
                    split_id = split.id,
                    clearing_group_id = cgId,
                    is_deleted = 0L,
                    hlc_map = "{\"link\":\"$linkHlc\"}",
                    dirty_fields = "[\"link\"]",
                    is_dirty = 1L,
                    server_version = 0L,
                    created_at = now,
                    updated_at = now,
                )
            }

            if (split.currency.uppercase() != split.weightCurrency.uppercase() && split.amountMinor != 0L) {
                val fxId = "fx:${split.currency.uppercase()}:${split.weightCurrency.uppercase()}:$txDate"
                val rateMicros = (split.weightAmountMinor.toDouble() / split.amountMinor * 1_000_000.0).toLong()
                val fxHlc = store.clock.next()
                store.queries.upsertFinanceExchangeRate(
                    id = fxId,
                    base_currency = split.currency.uppercase(),
                    quote_currency = split.weightCurrency.uppercase(),
                    rate_date = txDate,
                    rate_micros = rateMicros,
                    source = ExchangeRateSource.IMPLICIT_FROM_SPLIT.name,
                    is_deleted = 0L,
                    hlc_map = "{\"core\":\"$fxHlc\"}",
                    dirty_fields = "[\"core\"]",
                    is_dirty = 1L,
                    server_version = 0L,
                    created_at = now,
                    updated_at = now,
                )
            }
        }

        for (cgId in touchedClearingGroupIds) {
            refreshClearingGroupStatus(cgId, now)
        }

        txId
    }

    fun refreshClearingGroupStatus(cgId: String, now: Long = store.timeProvider.nowEpochMillis()): Unit = store.database.transaction {
        val group = store.queries.selectFinanceClearingGroupById(cgId, ::mapFinanceClearingGroupRow).executeAsOneOrNull()
            ?: return@transaction
        val groupSplits = store.queries.selectFinanceSplitsForClearingGroup(cgId, ::mapFinanceSplitRow).executeAsList()
        val txIds = groupSplits.map { it.transactionId }.toSet()
        val txMap = txIds.mapNotNull { id -> store.queries.selectFinanceTransactionById(id, ::mapFinanceTransactionRow).executeAsOneOrNull() }
            .associateBy { it.id }

        val eval = ClearingGroupEvaluator.evaluate(group, groupSplits, txMap)
        if (eval.derivedStatus != group.status) {
            val cgHlc = store.clock.next()
            store.queries.upsertFinanceClearingGroup(
                id = group.id,
                name = group.name,
                group_type = group.groupType.name,
                anchor_currency = group.anchorCurrency,
                target_account_id = group.targetAccountId,
                statement_cycle = group.statementCycle,
                due_date = group.dueDate,
                status = eval.derivedStatus.name,
                settled_at = if (eval.derivedStatus == ClearingGroupStatus.SETTLED) epochMillisToIsoString(now) else group.settledAt,
                is_deleted = if (group.isDeleted) 1L else 0L,
                hlc_map = "{\"status\":\"$cgHlc\"}",
                dirty_fields = "[\"status\"]",
                is_dirty = 1L,
                server_version = 0L,
                created_at = now,
                updated_at = now,
            )
        }
    }

    fun reassignSplitClearingGroup(
        splitId: String,
        oldGroupId: String,
        newGroupId: String,
    ): Unit = store.database.transaction {
        val now = store.timeProvider.nowEpochMillis()
        val oldLinkId = "scg:$splitId:$oldGroupId"
        val oldLinkHlc = store.clock.next()
        store.queries.upsertFinanceSplitClearingGroup(
            id = oldLinkId,
            split_id = splitId,
            clearing_group_id = oldGroupId,
            is_deleted = 1L,
            hlc_map = "{\"deleted\":\"$oldLinkHlc\"}",
            dirty_fields = "[\"deleted\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )

        val newLinkId = "scg:$splitId:$newGroupId"
        val newLinkHlc = store.clock.next()
        store.queries.upsertFinanceSplitClearingGroup(
            id = newLinkId,
            split_id = splitId,
            clearing_group_id = newGroupId,
            is_deleted = 0L,
            hlc_map = "{\"link\":\"$newLinkHlc\"}",
            dirty_fields = "[\"link\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )

        refreshClearingGroupStatus(oldGroupId, now)
        refreshClearingGroupStatus(newGroupId, now)
    }

    fun deleteFinanceTransaction(id: String): Unit = store.database.transaction {
        val existing = store.queries.selectFinanceTransactionById(id, ::mapFinanceTransactionRow).executeAsOneOrNull()
            ?: return@transaction
        val now = store.timeProvider.nowEpochMillis()
        val hlc = store.clock.next()

        store.queries.upsertFinanceTransaction(
            id = existing.id,
            tx_date = existing.txDate,
            tx_time = existing.txTime,
            payee = existing.payee,
            summary = existing.summary,
            status = existing.status.name,
            source_plan_id = existing.sourcePlanId,
            period_index = existing.periodIndex?.toLong(),
            is_deleted = 1L,
            hlc_map = "{\"deleted\":\"$hlc\"}",
            dirty_fields = "[\"deleted\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )

        val splits = store.queries.selectFinanceSplitsForTransaction(id, ::mapFinanceSplitRow).executeAsList()
        for (split in splits) {
            val splitHlc = store.clock.next()
            store.queries.upsertFinanceSplit(
                id = split.id,
                transaction_id = split.transactionId,
                account_id = split.accountId,
                amount_minor = split.amountMinor,
                currency = split.currency,
                weight_amount_minor = split.weightAmountMinor,
                weight_currency = split.weightCurrency,
                narration = split.narration,
                sort_order = split.sortOrder.toLong(),
                is_deleted = 1L,
                hlc_map = "{\"deleted\":\"$splitHlc\"}",
                dirty_fields = "[\"deleted\"]",
                is_dirty = 1L,
                server_version = 0L,
                created_at = now,
                updated_at = now,
            )

            val links = store.queries.selectAllActiveFinanceSplitClearingGroups(::mapFinanceSplitClearingGroupRow)
                .executeAsList().filter { it.splitId == split.id }
            for (link in links) {
                val linkHlc = store.clock.next()
                store.queries.upsertFinanceSplitClearingGroup(
                    id = link.id,
                    split_id = link.splitId,
                    clearing_group_id = link.clearingGroupId,
                    is_deleted = 1L,
                    hlc_map = "{\"deleted\":\"$linkHlc\"}",
                    dirty_fields = "[\"deleted\"]",
                    is_dirty = 1L,
                    server_version = 0L,
                    created_at = now,
                    updated_at = now,
                )
            }
        }
    }

    fun createFinanceAmortizationPlan(
        name: String,
        principalMinor: Long,
        currency: String = "CNY",
        totalPeriods: Int,
        repaymentMethod: AmortizationMethod = AmortizationMethod.EQUAL_INSTALLMENT,
        annualRateBps: Int = 0,
        feePerPeriodMinor: Long = 0L,
        startDate: String,
        repaymentDay: Int,
        liabilityAccountId: String,
        fundingAccountId: String? = null,
        interestAccountId: String,
        recordInitialDisbursement: Boolean = false,
        existingDisbursementSplitId: String? = null,
    ): String = store.database.transactionWithResult {
        val now = store.timeProvider.nowEpochMillis()
        val planId = store.idGenerator.randomId()
        val clearingGroupId = "cg-loan:$planId"
        val hlc = store.clock.next()

        // 1. Create lifecycle clearing group for principal tracking
        store.queries.upsertFinanceClearingGroup(
            id = clearingGroupId,
            name = "$name 还款追踪",
            group_type = ClearingGroupType.LOAN_LIFECYCLE.name,
            anchor_currency = currency,
            target_account_id = liabilityAccountId,
            statement_cycle = null,
            due_date = null,
            status = ClearingGroupStatus.ACTIVE.name,
            settled_at = null,
            is_deleted = 0L,
            hlc_map = "{\"core\":\"$hlc\"}",
            dirty_fields = "[\"core\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )

        // 2. Insert plan
        store.queries.upsertFinanceAmortizationPlan(
            id = planId,
            name = name,
            principal_minor = principalMinor,
            currency = currency,
            total_periods = totalPeriods.toLong(),
            repayment_method = repaymentMethod.name,
            annual_rate_bps = annualRateBps.toLong(),
            fee_per_period_minor = feePerPeriodMinor,
            start_date = startDate,
            repayment_day = repaymentDay.toLong(),
            liability_account_id = liabilityAccountId,
            funding_account_id = fundingAccountId,
            interest_account_id = interestAccountId,
            lifecycle_clearing_group_id = clearingGroupId,
            is_deleted = 0L,
            hlc_map = "{\"core\":\"$hlc\"}",
            dirty_fields = "[\"core\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )

        // 3. Disbursement handling
        if (existingDisbursementSplitId != null) {
            val linkId = store.idGenerator.randomId()
            val linkHlc = store.clock.next()
            store.queries.upsertFinanceSplitClearingGroup(
                id = linkId,
                split_id = existingDisbursementSplitId,
                clearing_group_id = clearingGroupId,
                is_deleted = 0L,
                hlc_map = "{\"core\":\"$linkHlc\"}",
                dirty_fields = "[\"core\"]",
                is_dirty = 1L,
                server_version = 0L,
                created_at = now,
                updated_at = now,
            )
        } else if (recordInitialDisbursement && fundingAccountId != null) {
            createFinanceTransaction(
                txDate = startDate,
                summary = "$name 本金到账",
                splits = listOf(
                    FinanceSplitDraft(
                        accountId = fundingAccountId,
                        amountMinor = principalMinor,
                        currency = currency,
                        narration = "贷款到账",
                    ),
                    FinanceSplitDraft(
                        accountId = liabilityAccountId,
                        amountMinor = -principalMinor,
                        currency = currency,
                        narration = "新增负债",
                        clearingGroupIds = listOf(clearingGroupId),
                    ),
                ),
                status = TransactionStatus.CLEARED,
                sourcePlanId = planId,
                periodIndex = 0,
            )
        }

        // 4. Generate scheduled installment transactions
        val schedule = AmortizationGenerator.generateSchedule(
            principalMinor = principalMinor,
            totalPeriods = totalPeriods,
            method = repaymentMethod,
            annualRateBps = annualRateBps,
            feePerPeriodMinor = feePerPeriodMinor,
            repaymentDay = repaymentDay,
            firstRepaymentDate = startDate,
        )

        for (item in schedule) {
            val splits = mutableListOf<FinanceSplitDraft>()
            splits.add(
                FinanceSplitDraft(
                    accountId = liabilityAccountId,
                    amountMinor = item.principalMinor,
                    currency = currency,
                    narration = "本金还款",
                    clearingGroupIds = listOf(clearingGroupId),
                ),
            )

            val totalExtraExpense = item.interestMinor + item.feeMinor
            if (totalExtraExpense > 0L) {
                splits.add(
                    FinanceSplitDraft(
                        accountId = interestAccountId,
                        amountMinor = totalExtraExpense,
                        currency = currency,
                        narration = if (item.feeMinor > 0L) "利息与手续费" else "利息支出",
                    ),
                )
            }

            if (fundingAccountId != null) {
                splits.add(
                    FinanceSplitDraft(
                        accountId = fundingAccountId,
                        amountMinor = -item.totalPaymentMinor,
                        currency = currency,
                        narration = "月供支出",
                    ),
                )
            }

            createFinanceTransaction(
                txDate = item.dueDate,
                summary = "$name (第 ${item.periodIndex}/$totalPeriods 期)",
                splits = splits,
                status = TransactionStatus.SCHEDULED,
                sourcePlanId = planId,
                periodIndex = item.periodIndex,
            )
        }

        refreshClearingGroupStatus(clearingGroupId, now)
        planId
    }

    fun deleteFinanceAmortizationPlan(id: String): Unit = store.database.transaction {
        val existing = store.queries.selectFinanceAmortizationPlanById(id, ::mapFinanceAmortizationPlanRow).executeAsOneOrNull()
            ?: return@transaction
        val now = store.timeProvider.nowEpochMillis()
        val hlc = store.clock.next()

        store.queries.upsertFinanceAmortizationPlan(
            id = existing.id,
            name = existing.name,
            principal_minor = existing.principalMinor,
            currency = existing.currency,
            total_periods = existing.totalPeriods.toLong(),
            repayment_method = existing.repaymentMethod.name,
            annual_rate_bps = existing.annualRateBps.toLong(),
            fee_per_period_minor = existing.feePerPeriodMinor,
            start_date = existing.startDate,
            repayment_day = existing.repaymentDay.toLong(),
            liability_account_id = existing.liabilityAccountId,
            funding_account_id = existing.fundingAccountId,
            interest_account_id = existing.interestAccountId,
            lifecycle_clearing_group_id = existing.lifecycleClearingGroupId,
            is_deleted = 1L,
            hlc_map = "{\"deleted\":\"$hlc\"}",
            dirty_fields = "[\"deleted\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )

        // Soft delete scheduled transactions belonging to this plan
        val txs = financeTransactions().filter { it.sourcePlanId == id && it.status == TransactionStatus.SCHEDULED }
        for (tx in txs) {
            deleteFinanceTransaction(tx.id)
        }

        // Soft delete associated lifecycle clearing group
        val group = financeClearingGroup(existing.lifecycleClearingGroupId)
        if (group != null) {
            val groupHlc = store.clock.next()
            store.queries.upsertFinanceClearingGroup(
                id = group.id,
                name = group.name,
                group_type = group.groupType.name,
                anchor_currency = group.anchorCurrency,
                target_account_id = group.targetAccountId,
                statement_cycle = group.statementCycle,
                due_date = group.dueDate,
                status = group.status.name,
                settled_at = group.settledAt,
                is_deleted = 1L,
                hlc_map = "{\"deleted\":\"$groupHlc\"}",
                dirty_fields = "[\"deleted\"]",
                is_dirty = 1L,
                server_version = 0L,
                created_at = now,
                updated_at = now,
            )
        }
    }

    internal fun mapFinanceTransactionRow(
        id: String,
        tx_date: String,
        tx_time: String?,
        payee: String?,
        summary: String,
        status: String,
        source_plan_id: String?,
        period_index: Long?,
        is_deleted: Long,
        hlc_map: String,
        dirty_fields: String,
        is_dirty: Long,
        server_version: Long,
        created_at: Long,
        updated_at: Long,
    ): FinanceTransaction = FinanceTransaction(
        id = id,
        txDate = tx_date,
        txTime = tx_time,
        payee = payee,
        summary = summary,
        status = parseTransactionStatus(status),
        sourcePlanId = source_plan_id,
        periodIndex = period_index?.toInt(),
        isDeleted = is_deleted == 1L,
    )

    internal fun mapFinanceSplitRow(
        id: String,
        transaction_id: String,
        account_id: String,
        amount_minor: Long,
        currency: String,
        weight_amount_minor: Long,
        weight_currency: String,
        narration: String,
        sort_order: Long,
        is_deleted: Long,
        hlc_map: String,
        dirty_fields: String,
        is_dirty: Long,
        server_version: Long,
        created_at: Long,
        updated_at: Long,
    ): FinanceSplit = FinanceSplit(
        id = id,
        transactionId = transaction_id,
        accountId = account_id,
        amountMinor = amount_minor,
        currency = currency,
        weightAmountMinor = weight_amount_minor,
        weightCurrency = weight_currency,
        narration = narration,
        sortOrder = sort_order.toInt(),
        isDeleted = is_deleted == 1L,
    )

    internal fun mapFinanceClearingGroupRow(
        id: String,
        name: String,
        group_type: String,
        anchor_currency: String,
        target_account_id: String?,
        statement_cycle: String?,
        due_date: String?,
        status: String,
        settled_at: String?,
        is_deleted: Long,
        hlc_map: String,
        dirty_fields: String,
        is_dirty: Long,
        server_version: Long,
        created_at: Long,
        updated_at: Long,
    ): FinanceClearingGroup = FinanceClearingGroup(
        id = id,
        name = name,
        groupType = parseClearingGroupType(group_type),
        anchorCurrency = anchor_currency,
        targetAccountId = target_account_id,
        statementCycle = statement_cycle,
        dueDate = due_date,
        status = parseClearingGroupStatus(status),
        settledAt = settled_at,
        isDeleted = is_deleted == 1L,
    )

    internal fun mapFinanceExchangeRateRow(
        id: String,
        base_currency: String,
        quote_currency: String,
        rate_date: String,
        rate_micros: Long,
        source: String,
        is_deleted: Long,
        hlc_map: String,
        dirty_fields: String,
        is_dirty: Long,
        server_version: Long,
        created_at: Long,
        updated_at: Long,
    ): FinanceExchangeRate = FinanceExchangeRate(
        id = id,
        baseCurrency = base_currency,
        quoteCurrency = quote_currency,
        rateDate = rate_date,
        rateMicros = rate_micros,
        source = parseExchangeRateSource(source),
        isDeleted = is_deleted == 1L,
    )

    internal fun mapFinanceSplitClearingGroupRow(
        id: String,
        split_id: String,
        clearing_group_id: String,
        is_deleted: Long,
        hlc_map: String,
        dirty_fields: String,
        is_dirty: Long,
        server_version: Long,
        created_at: Long,
        updated_at: Long,
    ): FinanceSplitClearingGroup = FinanceSplitClearingGroup(
        id = id,
        splitId = split_id,
        clearingGroupId = clearing_group_id,
        isDeleted = is_deleted == 1L,
    )

    internal fun mapFinanceAmortizationPlanRow(
        id: String,
        name: String,
        principal_minor: Long,
        currency: String,
        total_periods: Long,
        repayment_method: String,
        annual_rate_bps: Long,
        fee_per_period_minor: Long,
        start_date: String,
        repayment_day: Long,
        liability_account_id: String,
        funding_account_id: String?,
        interest_account_id: String,
        lifecycle_clearing_group_id: String,
        is_deleted: Long,
        hlc_map: String,
        dirty_fields: String,
        is_dirty: Long,
        server_version: Long,
        created_at: Long,
        updated_at: Long,
    ): FinanceAmortizationPlan = FinanceAmortizationPlan(
        id = id,
        name = name,
        principalMinor = principal_minor,
        currency = currency,
        totalPeriods = total_periods.toInt(),
        repaymentMethod = parseAmortizationMethod(repayment_method),
        annualRateBps = annual_rate_bps.toInt(),
        feePerPeriodMinor = fee_per_period_minor,
        startDate = start_date,
        repaymentDay = repayment_day.toInt(),
        liabilityAccountId = liability_account_id,
        fundingAccountId = funding_account_id,
        interestAccountId = interest_account_id,
        lifecycleClearingGroupId = lifecycle_clearing_group_id,
        isDeleted = is_deleted == 1L,
    )

    internal fun parseAmortizationMethod(raw: String): AmortizationMethod =
        runCatching { AmortizationMethod.valueOf(raw) }.getOrElse {
            when (raw.lowercase().replace("_", "")) {
                "equalprincipal" -> AmortizationMethod.EQUAL_PRINCIPAL
                "equalinstallment" -> AmortizationMethod.EQUAL_INSTALLMENT
                "interestfirst" -> AmortizationMethod.INTEREST_FIRST
                "flatfee" -> AmortizationMethod.FLAT_FEE
                else -> AmortizationMethod.EQUAL_INSTALLMENT
            }
        }

    internal fun parseTransactionStatus(raw: String): TransactionStatus =
        runCatching { TransactionStatus.valueOf(raw) }.getOrElse {
            when (raw.lowercase()) {
                "draft" -> TransactionStatus.DRAFT
                "scheduled" -> TransactionStatus.SCHEDULED
                "cleared" -> TransactionStatus.CLEARED
                "void" -> TransactionStatus.VOID
                else -> TransactionStatus.CLEARED
            }
        }

    internal fun parseClearingGroupType(raw: String): ClearingGroupType =
        runCatching { ClearingGroupType.valueOf(raw) }.getOrElse {
            when (raw.lowercase()) {
                "instantbatch", "instant_batch" -> ClearingGroupType.INSTANT_BATCH
                "creditstatement", "credit_statement" -> ClearingGroupType.CREDIT_STATEMENT
                "loanlifecycle", "loan_lifecycle" -> ClearingGroupType.LOAN_LIFECYCLE
                "reimbursement" -> ClearingGroupType.REIMBURSEMENT
                else -> ClearingGroupType.INSTANT_BATCH
            }
        }

    internal fun parseClearingGroupStatus(raw: String): ClearingGroupStatus =
        runCatching { ClearingGroupStatus.valueOf(raw) }.getOrElse {
            when (raw.lowercase()) {
                "active" -> ClearingGroupStatus.ACTIVE
                "settled" -> ClearingGroupStatus.SETTLED
                "overdrawn" -> ClearingGroupStatus.OVERDRAWN
                else -> ClearingGroupStatus.ACTIVE
            }
        }

    internal fun parseExchangeRateSource(raw: String): ExchangeRateSource =
        runCatching { ExchangeRateSource.valueOf(raw) }.getOrElse {
            when (raw.lowercase()) {
                "manual" -> ExchangeRateSource.MANUAL
                "implicitfromsplit", "implicit_from_split" -> ExchangeRateSource.IMPLICIT_FROM_SPLIT
                "remotefeed", "remote_feed" -> ExchangeRateSource.REMOTE_FEED
                else -> ExchangeRateSource.MANUAL
            }
        }
}
