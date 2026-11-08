package dev.tireless.abun.app

import dev.tireless.abun.sync.SyncConflictResolver
import dev.tireless.abun.sync.SyncFinanceAccount
import dev.tireless.abun.sync.SyncFinanceAmortizationPlan
import dev.tireless.abun.sync.SyncFinanceBudgetRule
import dev.tireless.abun.sync.SyncFinanceClearingGroup
import dev.tireless.abun.sync.SyncFinanceExchangeRate
import dev.tireless.abun.sync.SyncFinanceSplit
import dev.tireless.abun.sync.SyncFinanceSplitClearingGroup
import dev.tireless.abun.sync.SyncFinanceTransaction

class FinanceSyncStore(private val store: LocalStore) {

    // ============================================================================
    // Dirty Row Extractors
    // ============================================================================

    fun dirtyFinanceAccounts(): List<SyncFinanceAccount> =
        store.queries.selectDirtyFinanceAccounts().executeAsList().map { row ->
            SyncFinanceAccount(
                id = row.id,
                path = row.path,
                name = row.name,
                category = row.category,
                currency = row.currency,
                includeInNetWorth = row.include_in_net_worth != 0L,
                isPassThrough = row.is_pass_through != 0L,
                passThroughExpenseAccountId = row.pass_through_expense_account_id,
                liquidTier = row.liquid_tier,
                statementDay = row.statement_day?.toInt(),
                dueDay = row.due_day?.toInt(),
                isArchived = row.is_archived != 0L,
                isDeleted = row.is_deleted != 0L,
                hlcMap = JsonCodecs.decodeMap(row.hlc_map),
                dirtyFields = JsonCodecs.decodeList(row.dirty_fields),
                serverVersion = row.server_version,
                serverUpdatedAt = epochMillisToIsoString(row.updated_at),
                createdAt = epochMillisToIsoString(row.created_at),
            )
        }

    fun dirtyFinanceExchangeRates(): List<SyncFinanceExchangeRate> =
        store.queries.selectDirtyFinanceExchangeRates().executeAsList().map { row ->
            SyncFinanceExchangeRate(
                id = row.id,
                baseCurrency = row.base_currency,
                quoteCurrency = row.quote_currency,
                rateDate = row.rate_date,
                rateMicros = row.rate_micros,
                source = row.source,
                isDeleted = row.is_deleted != 0L,
                hlcMap = JsonCodecs.decodeMap(row.hlc_map),
                dirtyFields = JsonCodecs.decodeList(row.dirty_fields),
                serverVersion = row.server_version,
                serverUpdatedAt = epochMillisToIsoString(row.updated_at),
                createdAt = epochMillisToIsoString(row.created_at),
            )
        }

    fun dirtyFinanceClearingGroups(): List<SyncFinanceClearingGroup> =
        store.queries.selectDirtyFinanceClearingGroups().executeAsList().map { row ->
            SyncFinanceClearingGroup(
                id = row.id,
                name = row.name,
                groupType = row.group_type,
                anchorCurrency = row.anchor_currency,
                targetAccountId = row.target_account_id,
                statementCycle = row.statement_cycle,
                dueDate = row.due_date,
                status = row.status,
                settledAt = row.settled_at,
                isDeleted = row.is_deleted != 0L,
                hlcMap = JsonCodecs.decodeMap(row.hlc_map),
                dirtyFields = JsonCodecs.decodeList(row.dirty_fields),
                serverVersion = row.server_version,
                serverUpdatedAt = epochMillisToIsoString(row.updated_at),
                createdAt = epochMillisToIsoString(row.created_at),
            )
        }

    fun dirtyFinanceTransactions(): List<SyncFinanceTransaction> =
        store.queries.selectDirtyFinanceTransactions().executeAsList().map { row ->
            SyncFinanceTransaction(
                id = row.id,
                txDate = row.tx_date,
                txTime = row.tx_time,
                payee = row.payee,
                summary = row.summary,
                status = row.status,
                sourcePlanId = row.source_plan_id,
                periodIndex = row.period_index?.toInt(),
                isDeleted = row.is_deleted != 0L,
                hlcMap = JsonCodecs.decodeMap(row.hlc_map),
                dirtyFields = JsonCodecs.decodeList(row.dirty_fields),
                serverVersion = row.server_version,
                serverUpdatedAt = epochMillisToIsoString(row.updated_at),
                createdAt = epochMillisToIsoString(row.created_at),
            )
        }

    fun dirtyFinanceSplits(): List<SyncFinanceSplit> =
        store.queries.selectDirtyFinanceSplits().executeAsList().map { row ->
            SyncFinanceSplit(
                id = row.id,
                transactionId = row.transaction_id,
                accountId = row.account_id,
                amountMinor = row.amount_minor,
                currency = row.currency,
                weightAmountMinor = row.weight_amount_minor,
                weightCurrency = row.weight_currency,
                narration = row.narration,
                sortOrder = row.sort_order.toInt(),
                isDeleted = row.is_deleted != 0L,
                hlcMap = JsonCodecs.decodeMap(row.hlc_map),
                dirtyFields = JsonCodecs.decodeList(row.dirty_fields),
                serverVersion = row.server_version,
                serverUpdatedAt = epochMillisToIsoString(row.updated_at),
                createdAt = epochMillisToIsoString(row.created_at),
            )
        }

    fun dirtyFinanceSplitClearingGroups(): List<SyncFinanceSplitClearingGroup> =
        store.queries.selectDirtyFinanceSplitClearingGroups().executeAsList().map { row ->
            SyncFinanceSplitClearingGroup(
                id = row.id,
                splitId = row.split_id,
                clearingGroupId = row.clearing_group_id,
                isDeleted = row.is_deleted != 0L,
                hlcMap = JsonCodecs.decodeMap(row.hlc_map),
                dirtyFields = JsonCodecs.decodeList(row.dirty_fields),
                serverVersion = row.server_version,
                serverUpdatedAt = epochMillisToIsoString(row.updated_at),
                createdAt = epochMillisToIsoString(row.created_at),
            )
        }

    fun dirtyFinanceAmortizationPlans(): List<SyncFinanceAmortizationPlan> =
        store.queries.selectDirtyFinanceAmortizationPlans().executeAsList().map { row ->
            SyncFinanceAmortizationPlan(
                id = row.id,
                name = row.name,
                principalMinor = row.principal_minor,
                currency = row.currency,
                totalPeriods = row.total_periods.toInt(),
                repaymentMethod = row.repayment_method,
                annualRateBps = row.annual_rate_bps.toInt(),
                feePerPeriodMinor = row.fee_per_period_minor,
                startDate = row.start_date,
                repaymentDay = row.repayment_day.toInt(),
                liabilityAccountId = row.liability_account_id,
                fundingAccountId = row.funding_account_id,
                interestAccountId = row.interest_account_id,
                lifecycleClearingGroupId = row.lifecycle_clearing_group_id,
                isDeleted = row.is_deleted != 0L,
                hlcMap = JsonCodecs.decodeMap(row.hlc_map),
                dirtyFields = JsonCodecs.decodeList(row.dirty_fields),
                serverVersion = row.server_version,
                serverUpdatedAt = epochMillisToIsoString(row.updated_at),
                createdAt = epochMillisToIsoString(row.created_at),
            )
        }

    fun dirtyFinanceBudgetRules(): List<SyncFinanceBudgetRule> =
        store.queries.selectDirtyFinanceBudgetRules().executeAsList().map { row ->
            SyncFinanceBudgetRule(
                id = row.id,
                name = row.name,
                scopeType = row.scope_type,
                accountPrefix = row.account_prefix,
                excludedPrefixesJson = row.excluded_prefixes_json,
                monthlyLimitMinor = row.monthly_limit_minor,
                currency = row.currency,
                includeInRunway = row.include_in_runway != 0L,
                isDeleted = row.is_deleted != 0L,
                hlcMap = JsonCodecs.decodeMap(row.hlc_map),
                dirtyFields = JsonCodecs.decodeList(row.dirty_fields),
                serverVersion = row.server_version,
                serverUpdatedAt = epochMillisToIsoString(row.updated_at),
                createdAt = epochMillisToIsoString(row.created_at),
            )
        }

    // ============================================================================
    // Remote Merge Functions
    // ============================================================================

    fun mergeRemoteFinanceAccounts(items: List<SyncFinanceAccount>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemoteFinanceAccount(remote, clearAccepted) }
    }

    private fun mergeRemoteFinanceAccount(remote: SyncFinanceAccount, clearAccepted: Boolean) {
        val existing = store.queries.selectFinanceAccountById(remote.id).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val current = existing ?: run {
            store.queries.upsertFinanceAccount(
                id = remote.id,
                path = remote.path,
                name = remote.name,
                category = remote.category,
                currency = remote.currency,
                include_in_net_worth = if (remote.includeInNetWorth) 1L else 0L,
                is_pass_through = if (remote.isPassThrough) 1L else 0L,
                pass_through_expense_account_id = remote.passThroughExpenseAccountId,
                liquid_tier = remote.liquidTier,
                statement_day = remote.statementDay?.toLong(),
                due_day = remote.dueDay?.toLong(),
                is_archived = if (remote.isArchived) 1L else 0L,
                is_deleted = if (remote.isDeleted) 1L else 0L,
                hlc_map = JsonCodecs.encodeMap(remote.hlcMap),
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = remote.serverVersion,
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: now,
                updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
            )
            return
        }

        var merged = current
        val mergedHlc = JsonCodecs.decodeMap(current.hlc_map).toMutableMap()
        val stillDirty = (if (current.is_dirty != 0L) JsonCodecs.decodeList(current.dirty_fields) else emptyList()).toMutableSet()

        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = mergedHlc[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "deleted", "is_deleted" -> {
                    merged = merged.copy(is_deleted = if (remote.isDeleted) 1L else 0L)
                }
                else -> {
                    merged = merged.copy(
                        path = remote.path,
                        name = remote.name,
                        category = remote.category,
                        currency = remote.currency,
                        include_in_net_worth = if (remote.includeInNetWorth) 1L else 0L,
                        is_pass_through = if (remote.isPassThrough) 1L else 0L,
                        pass_through_expense_account_id = remote.passThroughExpenseAccountId,
                        liquid_tier = remote.liquidTier,
                        statement_day = remote.statementDay?.toLong(),
                        due_day = remote.dueDay?.toLong(),
                        is_archived = if (remote.isArchived) 1L else 0L,
                        is_deleted = if (remote.isDeleted) 1L else 0L,
                    )
                }
            }
            mergedHlc[field] = incomingHlc
        }

        if (clearAccepted) remote.acceptedFields?.forEach(stillDirty::remove)
        val isDirty = stillDirty.isNotEmpty()

        store.queries.upsertFinanceAccount(
            id = merged.id,
            path = merged.path,
            name = merged.name,
            category = merged.category,
            currency = merged.currency,
            include_in_net_worth = merged.include_in_net_worth,
            is_pass_through = merged.is_pass_through,
            pass_through_expense_account_id = merged.pass_through_expense_account_id,
            liquid_tier = merged.liquid_tier,
            statement_day = merged.statement_day,
            due_day = merged.due_day,
            is_archived = merged.is_archived,
            is_deleted = merged.is_deleted,
            hlc_map = JsonCodecs.encodeMap(mergedHlc),
            dirty_fields = JsonCodecs.encodeList(stillDirty.toList()),
            is_dirty = if (isDirty) 1L else 0L,
            server_version = remote.serverVersion,
            created_at = merged.created_at,
            updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
        )
    }

    fun mergeRemoteFinanceExchangeRates(items: List<SyncFinanceExchangeRate>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemoteFinanceExchangeRate(remote, clearAccepted) }
    }

    private fun mergeRemoteFinanceExchangeRate(remote: SyncFinanceExchangeRate, clearAccepted: Boolean) {
        val existing = store.queries.selectFinanceExchangeRateById(remote.id).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val current = existing ?: run {
            store.queries.upsertFinanceExchangeRate(
                id = remote.id,
                base_currency = remote.baseCurrency,
                quote_currency = remote.quoteCurrency,
                rate_date = remote.rateDate,
                rate_micros = remote.rateMicros,
                source = remote.source,
                is_deleted = if (remote.isDeleted) 1L else 0L,
                hlc_map = JsonCodecs.encodeMap(remote.hlcMap),
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = remote.serverVersion,
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: now,
                updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
            )
            return
        }

        var merged = current
        val mergedHlc = JsonCodecs.decodeMap(current.hlc_map).toMutableMap()
        val stillDirty = (if (current.is_dirty != 0L) JsonCodecs.decodeList(current.dirty_fields) else emptyList()).toMutableSet()

        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = mergedHlc[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "deleted", "is_deleted" -> merged = merged.copy(is_deleted = if (remote.isDeleted) 1L else 0L)
                else -> {
                    merged = merged.copy(
                        base_currency = remote.baseCurrency,
                        quote_currency = remote.quoteCurrency,
                        rate_date = remote.rateDate,
                        rate_micros = remote.rateMicros,
                        source = remote.source,
                        is_deleted = if (remote.isDeleted) 1L else 0L,
                    )
                }
            }
            mergedHlc[field] = incomingHlc
        }

        if (clearAccepted) remote.acceptedFields?.forEach(stillDirty::remove)
        val isDirty = stillDirty.isNotEmpty()

        store.queries.upsertFinanceExchangeRate(
            id = merged.id,
            base_currency = merged.base_currency,
            quote_currency = merged.quote_currency,
            rate_date = merged.rate_date,
            rate_micros = merged.rate_micros,
            source = merged.source,
            is_deleted = merged.is_deleted,
            hlc_map = JsonCodecs.encodeMap(mergedHlc),
            dirty_fields = JsonCodecs.encodeList(stillDirty.toList()),
            is_dirty = if (isDirty) 1L else 0L,
            server_version = remote.serverVersion,
            created_at = merged.created_at,
            updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
        )
    }

    fun mergeRemoteFinanceClearingGroups(items: List<SyncFinanceClearingGroup>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemoteFinanceClearingGroup(remote, clearAccepted) }
    }

    private fun mergeRemoteFinanceClearingGroup(remote: SyncFinanceClearingGroup, clearAccepted: Boolean) {
        val existing = store.queries.selectFinanceClearingGroupById(remote.id).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val current = existing ?: run {
            store.queries.upsertFinanceClearingGroup(
                id = remote.id,
                name = remote.name,
                group_type = remote.groupType,
                anchor_currency = remote.anchorCurrency,
                target_account_id = remote.targetAccountId,
                statement_cycle = remote.statementCycle,
                due_date = remote.dueDate,
                status = remote.status,
                settled_at = remote.settledAt,
                is_deleted = if (remote.isDeleted) 1L else 0L,
                hlc_map = JsonCodecs.encodeMap(remote.hlcMap),
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = remote.serverVersion,
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: now,
                updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
            )
            return
        }

        var merged = current
        val mergedHlc = JsonCodecs.decodeMap(current.hlc_map).toMutableMap()
        val stillDirty = (if (current.is_dirty != 0L) JsonCodecs.decodeList(current.dirty_fields) else emptyList()).toMutableSet()

        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = mergedHlc[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "deleted", "is_deleted" -> merged = merged.copy(is_deleted = if (remote.isDeleted) 1L else 0L)
                else -> {
                    merged = merged.copy(
                        name = remote.name,
                        group_type = remote.groupType,
                        anchor_currency = remote.anchorCurrency,
                        target_account_id = remote.targetAccountId,
                        statement_cycle = remote.statementCycle,
                        due_date = remote.dueDate,
                        status = remote.status,
                        settled_at = remote.settledAt,
                        is_deleted = if (remote.isDeleted) 1L else 0L,
                    )
                }
            }
            mergedHlc[field] = incomingHlc
        }

        if (clearAccepted) remote.acceptedFields?.forEach(stillDirty::remove)
        val isDirty = stillDirty.isNotEmpty()

        store.queries.upsertFinanceClearingGroup(
            id = merged.id,
            name = merged.name,
            group_type = merged.group_type,
            anchor_currency = merged.anchor_currency,
            target_account_id = merged.target_account_id,
            statement_cycle = merged.statement_cycle,
            due_date = merged.due_date,
            status = merged.status,
            settled_at = merged.settled_at,
            is_deleted = merged.is_deleted,
            hlc_map = JsonCodecs.encodeMap(mergedHlc),
            dirty_fields = JsonCodecs.encodeList(stillDirty.toList()),
            is_dirty = if (isDirty) 1L else 0L,
            server_version = remote.serverVersion,
            created_at = merged.created_at,
            updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
        )
    }

    fun mergeRemoteFinanceTransactions(items: List<SyncFinanceTransaction>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemoteFinanceTransaction(remote, clearAccepted) }
    }

    private fun mergeRemoteFinanceTransaction(remote: SyncFinanceTransaction, clearAccepted: Boolean) {
        val existing = store.queries.selectFinanceTransactionById(remote.id).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val current = existing ?: run {
            store.queries.upsertFinanceTransaction(
                id = remote.id,
                tx_date = remote.txDate,
                tx_time = remote.txTime,
                payee = remote.payee,
                summary = remote.summary,
                status = remote.status,
                source_plan_id = remote.sourcePlanId,
                period_index = remote.periodIndex?.toLong(),
                is_deleted = if (remote.isDeleted) 1L else 0L,
                hlc_map = JsonCodecs.encodeMap(remote.hlcMap),
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = remote.serverVersion,
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: now,
                updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
            )
            return
        }

        var merged = current
        val mergedHlc = JsonCodecs.decodeMap(current.hlc_map).toMutableMap()
        val stillDirty = (if (current.is_dirty != 0L) JsonCodecs.decodeList(current.dirty_fields) else emptyList()).toMutableSet()

        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = mergedHlc[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "deleted", "is_deleted" -> merged = merged.copy(is_deleted = if (remote.isDeleted) 1L else 0L)
                else -> {
                    merged = merged.copy(
                        tx_date = remote.txDate,
                        tx_time = remote.txTime,
                        payee = remote.payee,
                        summary = remote.summary,
                        status = remote.status,
                        source_plan_id = remote.sourcePlanId,
                        period_index = remote.periodIndex?.toLong(),
                        is_deleted = if (remote.isDeleted) 1L else 0L,
                    )
                }
            }
            mergedHlc[field] = incomingHlc
        }

        if (clearAccepted) remote.acceptedFields?.forEach(stillDirty::remove)
        val isDirty = stillDirty.isNotEmpty()

        store.queries.upsertFinanceTransaction(
            id = merged.id,
            tx_date = merged.tx_date,
            tx_time = merged.tx_time,
            payee = merged.payee,
            summary = merged.summary,
            status = merged.status,
            source_plan_id = merged.source_plan_id,
            period_index = merged.period_index,
            is_deleted = merged.is_deleted,
            hlc_map = JsonCodecs.encodeMap(mergedHlc),
            dirty_fields = JsonCodecs.encodeList(stillDirty.toList()),
            is_dirty = if (isDirty) 1L else 0L,
            server_version = remote.serverVersion,
            created_at = merged.created_at,
            updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
        )
    }

    fun mergeRemoteFinanceSplits(items: List<SyncFinanceSplit>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemoteFinanceSplit(remote, clearAccepted) }
    }

    private fun mergeRemoteFinanceSplit(remote: SyncFinanceSplit, clearAccepted: Boolean) {
        val existing = store.queries.selectFinanceSplitById(remote.id).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val current = existing ?: run {
            store.queries.upsertFinanceSplit(
                id = remote.id,
                transaction_id = remote.transactionId,
                account_id = remote.accountId,
                amount_minor = remote.amountMinor,
                currency = remote.currency,
                weight_amount_minor = remote.weightAmountMinor,
                weight_currency = remote.weightCurrency,
                narration = remote.narration,
                sort_order = remote.sortOrder.toLong(),
                is_deleted = if (remote.isDeleted) 1L else 0L,
                hlc_map = JsonCodecs.encodeMap(remote.hlcMap),
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = remote.serverVersion,
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: now,
                updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
            )
            return
        }

        var merged = current
        val mergedHlc = JsonCodecs.decodeMap(current.hlc_map).toMutableMap()
        val stillDirty = (if (current.is_dirty != 0L) JsonCodecs.decodeList(current.dirty_fields) else emptyList()).toMutableSet()

        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = mergedHlc[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "deleted", "is_deleted" -> merged = merged.copy(is_deleted = if (remote.isDeleted) 1L else 0L)
                else -> {
                    merged = merged.copy(
                        transaction_id = remote.transactionId,
                        account_id = remote.accountId,
                        amount_minor = remote.amountMinor,
                        currency = remote.currency,
                        weight_amount_minor = remote.weightAmountMinor,
                        weight_currency = remote.weightCurrency,
                        narration = remote.narration,
                        sort_order = remote.sortOrder.toLong(),
                        is_deleted = if (remote.isDeleted) 1L else 0L,
                    )
                }
            }
            mergedHlc[field] = incomingHlc
        }

        if (clearAccepted) remote.acceptedFields?.forEach(stillDirty::remove)
        val isDirty = stillDirty.isNotEmpty()

        store.queries.upsertFinanceSplit(
            id = merged.id,
            transaction_id = merged.transaction_id,
            account_id = merged.account_id,
            amount_minor = merged.amount_minor,
            currency = merged.currency,
            weight_amount_minor = merged.weight_amount_minor,
            weight_currency = merged.weight_currency,
            narration = merged.narration,
            sort_order = merged.sort_order,
            is_deleted = merged.is_deleted,
            hlc_map = JsonCodecs.encodeMap(mergedHlc),
            dirty_fields = JsonCodecs.encodeList(stillDirty.toList()),
            is_dirty = if (isDirty) 1L else 0L,
            server_version = remote.serverVersion,
            created_at = merged.created_at,
            updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
        )
    }

    fun mergeRemoteFinanceSplitClearingGroups(items: List<SyncFinanceSplitClearingGroup>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemoteFinanceSplitClearingGroup(remote, clearAccepted) }
    }

    private fun mergeRemoteFinanceSplitClearingGroup(remote: SyncFinanceSplitClearingGroup, clearAccepted: Boolean) {
        val existing = store.queries.selectFinanceSplitClearingGroupById(remote.id).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val current = existing ?: run {
            store.queries.upsertFinanceSplitClearingGroup(
                id = remote.id,
                split_id = remote.splitId,
                clearing_group_id = remote.clearingGroupId,
                is_deleted = if (remote.isDeleted) 1L else 0L,
                hlc_map = JsonCodecs.encodeMap(remote.hlcMap),
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = remote.serverVersion,
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: now,
                updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
            )
            return
        }

        var merged = current
        val mergedHlc = JsonCodecs.decodeMap(current.hlc_map).toMutableMap()
        val stillDirty = (if (current.is_dirty != 0L) JsonCodecs.decodeList(current.dirty_fields) else emptyList()).toMutableSet()

        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = mergedHlc[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "deleted", "is_deleted" -> merged = merged.copy(is_deleted = if (remote.isDeleted) 1L else 0L)
                else -> {
                    merged = merged.copy(
                        split_id = remote.splitId,
                        clearing_group_id = remote.clearingGroupId,
                        is_deleted = if (remote.isDeleted) 1L else 0L,
                    )
                }
            }
            mergedHlc[field] = incomingHlc
        }

        if (clearAccepted) remote.acceptedFields?.forEach(stillDirty::remove)
        val isDirty = stillDirty.isNotEmpty()

        store.queries.upsertFinanceSplitClearingGroup(
            id = merged.id,
            split_id = merged.split_id,
            clearing_group_id = merged.clearing_group_id,
            is_deleted = merged.is_deleted,
            hlc_map = JsonCodecs.encodeMap(mergedHlc),
            dirty_fields = JsonCodecs.encodeList(stillDirty.toList()),
            is_dirty = if (isDirty) 1L else 0L,
            server_version = remote.serverVersion,
            created_at = merged.created_at,
            updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
        )
    }

    fun mergeRemoteFinanceAmortizationPlans(items: List<SyncFinanceAmortizationPlan>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemoteFinanceAmortizationPlan(remote, clearAccepted) }
    }

    private fun mergeRemoteFinanceAmortizationPlan(remote: SyncFinanceAmortizationPlan, clearAccepted: Boolean) {
        val existing = store.queries.selectFinanceAmortizationPlanById(remote.id).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val current = existing ?: run {
            store.queries.upsertFinanceAmortizationPlan(
                id = remote.id,
                name = remote.name,
                principal_minor = remote.principalMinor,
                currency = remote.currency,
                total_periods = remote.totalPeriods.toLong(),
                repayment_method = remote.repaymentMethod,
                annual_rate_bps = remote.annualRateBps.toLong(),
                fee_per_period_minor = remote.feePerPeriodMinor,
                start_date = remote.startDate,
                repayment_day = remote.repaymentDay.toLong(),
                liability_account_id = remote.liabilityAccountId,
                funding_account_id = remote.fundingAccountId,
                interest_account_id = remote.interestAccountId,
                lifecycle_clearing_group_id = remote.lifecycleClearingGroupId,
                is_deleted = if (remote.isDeleted) 1L else 0L,
                hlc_map = JsonCodecs.encodeMap(remote.hlcMap),
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = remote.serverVersion,
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: now,
                updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
            )
            return
        }

        var merged = current
        val mergedHlc = JsonCodecs.decodeMap(current.hlc_map).toMutableMap()
        val stillDirty = (if (current.is_dirty != 0L) JsonCodecs.decodeList(current.dirty_fields) else emptyList()).toMutableSet()

        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = mergedHlc[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "deleted", "is_deleted" -> merged = merged.copy(is_deleted = if (remote.isDeleted) 1L else 0L)
                else -> {
                    merged = merged.copy(
                        name = remote.name,
                        principal_minor = remote.principalMinor,
                        currency = remote.currency,
                        total_periods = remote.totalPeriods.toLong(),
                        repayment_method = remote.repaymentMethod,
                        annual_rate_bps = remote.annualRateBps.toLong(),
                        fee_per_period_minor = remote.feePerPeriodMinor,
                        start_date = remote.startDate,
                        repayment_day = remote.repaymentDay.toLong(),
                        liability_account_id = remote.liabilityAccountId,
                        funding_account_id = remote.fundingAccountId,
                        interest_account_id = remote.interestAccountId,
                        lifecycle_clearing_group_id = remote.lifecycleClearingGroupId,
                        is_deleted = if (remote.isDeleted) 1L else 0L,
                    )
                }
            }
            mergedHlc[field] = incomingHlc
        }

        if (clearAccepted) remote.acceptedFields?.forEach(stillDirty::remove)
        val isDirty = stillDirty.isNotEmpty()

        store.queries.upsertFinanceAmortizationPlan(
            id = merged.id,
            name = merged.name,
            principal_minor = merged.principal_minor,
            currency = merged.currency,
            total_periods = merged.total_periods,
            repayment_method = merged.repayment_method,
            annual_rate_bps = merged.annual_rate_bps,
            fee_per_period_minor = merged.fee_per_period_minor,
            start_date = merged.start_date,
            repayment_day = merged.repayment_day,
            liability_account_id = merged.liability_account_id,
            funding_account_id = merged.funding_account_id,
            interest_account_id = merged.interest_account_id,
            lifecycle_clearing_group_id = merged.lifecycle_clearing_group_id,
            is_deleted = merged.is_deleted,
            hlc_map = JsonCodecs.encodeMap(mergedHlc),
            dirty_fields = JsonCodecs.encodeList(stillDirty.toList()),
            is_dirty = if (isDirty) 1L else 0L,
            server_version = remote.serverVersion,
            created_at = merged.created_at,
            updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
        )
    }

    fun mergeRemoteFinanceBudgetRules(items: List<SyncFinanceBudgetRule>, clearAccepted: Boolean = false) = store.database.transaction {
        items.forEach { remote -> mergeRemoteFinanceBudgetRule(remote, clearAccepted) }
    }

    private fun mergeRemoteFinanceBudgetRule(remote: SyncFinanceBudgetRule, clearAccepted: Boolean) {
        val existing = store.queries.selectFinanceBudgetRuleById(remote.id).executeAsOneOrNull()
        val now = store.timeProvider.nowEpochMillis()
        val current = existing ?: run {
            store.queries.upsertFinanceBudgetRule(
                id = remote.id,
                name = remote.name,
                scope_type = remote.scopeType,
                account_prefix = remote.accountPrefix,
                excluded_prefixes_json = remote.excludedPrefixesJson,
                monthly_limit_minor = remote.monthlyLimitMinor,
                currency = remote.currency,
                include_in_runway = if (remote.includeInRunway) 1L else 0L,
                is_deleted = if (remote.isDeleted) 1L else 0L,
                hlc_map = JsonCodecs.encodeMap(remote.hlcMap),
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = remote.serverVersion,
                created_at = remote.createdAt?.let(::isoStringToEpochMillis) ?: now,
                updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
            )
            return
        }

        var merged = current
        val mergedHlc = JsonCodecs.decodeMap(current.hlc_map).toMutableMap()
        val stillDirty = (if (current.is_dirty != 0L) JsonCodecs.decodeList(current.dirty_fields) else emptyList()).toMutableSet()

        for ((field, incomingHlc) in remote.hlcMap) {
            val existingHlc = mergedHlc[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) continue
            when (field) {
                "deleted", "is_deleted" -> merged = merged.copy(is_deleted = if (remote.isDeleted) 1L else 0L)
                else -> {
                    merged = merged.copy(
                        name = remote.name,
                        scope_type = remote.scopeType,
                        account_prefix = remote.accountPrefix,
                        excluded_prefixes_json = remote.excludedPrefixesJson,
                        monthly_limit_minor = remote.monthlyLimitMinor,
                        currency = remote.currency,
                        include_in_runway = if (remote.includeInRunway) 1L else 0L,
                        is_deleted = if (remote.isDeleted) 1L else 0L,
                    )
                }
            }
            mergedHlc[field] = incomingHlc
        }

        if (clearAccepted) remote.acceptedFields?.forEach(stillDirty::remove)
        val isDirty = stillDirty.isNotEmpty()

        store.queries.upsertFinanceBudgetRule(
            id = merged.id,
            name = merged.name,
            scope_type = merged.scope_type,
            account_prefix = merged.account_prefix,
            excluded_prefixes_json = merged.excluded_prefixes_json,
            monthly_limit_minor = merged.monthly_limit_minor,
            currency = merged.currency,
            include_in_runway = merged.include_in_runway,
            is_deleted = merged.is_deleted,
            hlc_map = JsonCodecs.encodeMap(mergedHlc),
            dirty_fields = JsonCodecs.encodeList(stillDirty.toList()),
            is_dirty = if (isDirty) 1L else 0L,
            server_version = remote.serverVersion,
            created_at = merged.created_at,
            updated_at = remote.serverUpdatedAt?.let(::isoStringToEpochMillis) ?: now,
        )
    }
}
