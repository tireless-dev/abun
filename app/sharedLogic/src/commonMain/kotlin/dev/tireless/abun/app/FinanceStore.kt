package dev.tireless.abun.app

import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.AmortizationGenerator
import dev.tireless.abun.finance.AmortizationMethod
import dev.tireless.abun.finance.BudgetEvaluator
import dev.tireless.abun.finance.BudgetScopeType
import dev.tireless.abun.finance.CashflowRunwayEngine
import dev.tireless.abun.finance.ClearingGroupEvaluator
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.finance.ExchangeRateSource
import dev.tireless.abun.finance.FinanceAccount
import dev.tireless.abun.finance.FinanceAmortizationPlan
import dev.tireless.abun.finance.FinanceBudgetRule
import dev.tireless.abun.finance.FinanceClearingGroup
import dev.tireless.abun.finance.FinanceCurrencies
import dev.tireless.abun.finance.FinanceExchangeRate
import dev.tireless.abun.finance.FinanceSplit
import dev.tireless.abun.finance.FinanceSplitClearingGroup
import dev.tireless.abun.finance.FinanceTransaction
import dev.tireless.abun.finance.FinanceValidators
import dev.tireless.abun.finance.LiquidTier
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

fun LocalStore.seedDefaultFinanceAccountsIfEmpty() {
    if (queries.selectAllFinanceAccounts(::mapFinanceAccountRow).executeAsList().isNotEmpty()) {
        return
    }
    val starterAccounts = listOf(
        FinanceAccount(
            id = "acc-assets-bank-default",
            path = "Assets:Bank:Default",
            name = "默认银行账户",
            category = AccountCategory.ASSET,
            currency = "CNY",
            includeInNetWorth = true,
            isPassThrough = false,
            liquidTier = LiquidTier.LIQUID,
        ),
        FinanceAccount(
            id = "acc-liabilities-cc-default",
            path = "Liabilities:CreditCard:Default",
            name = "默认信用卡",
            category = AccountCategory.LIABILITY,
            currency = "CNY",
            includeInNetWorth = true,
            isPassThrough = false,
            liquidTier = LiquidTier.NONE,
            statementDay = 5,
            dueDay = 25,
        ),
        FinanceAccount(
            id = "acc-liabilities-loan-default",
            path = "Liabilities:Loan:Default",
            name = "默认贷款",
            category = AccountCategory.LIABILITY,
            currency = "CNY",
            includeInNetWorth = true,
            isPassThrough = false,
            liquidTier = LiquidTier.NONE,
        ),
        FinanceAccount(
            id = "acc-expenses-food",
            path = "Expenses:Food",
            name = "餐饮美食",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
        ),
        FinanceAccount(
            id = "acc-expenses-housing",
            path = "Expenses:Housing",
            name = "居住房租",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
        ),
        FinanceAccount(
            id = "acc-expenses-interest",
            path = "Expenses:Finance:Interest",
            name = "贷款利息",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
        ),
        FinanceAccount(
            id = "acc-expenses-adjustment",
            path = "Expenses:Adjustment:Unallocated",
            name = "平账差额调节",
            category = AccountCategory.EXPENSE,
            currency = "CNY",
        ),
        FinanceAccount(
            id = "acc-income-salary",
            path = "Income:Salary",
            name = "薪资收入",
            category = AccountCategory.INCOME,
            currency = "CNY",
        ),
    )
    database.transaction {
        val now = timeProvider.nowEpochMillis()
        for (acc in starterAccounts) {
            val hlc = clock.next()
            queries.upsertFinanceAccount(
                id = acc.id,
                path = acc.path,
                name = acc.name,
                category = acc.category.name,
                currency = acc.currency,
                include_in_net_worth = if (acc.includeInNetWorth) 1L else 0L,
                is_pass_through = if (acc.isPassThrough) 1L else 0L,
                pass_through_expense_account_id = acc.passThroughExpenseAccountId,
                liquid_tier = acc.liquidTier.name,
                statement_day = acc.statementDay?.toLong(),
                due_day = acc.dueDay?.toLong(),
                is_archived = if (acc.isArchived) 1L else 0L,
                is_deleted = if (acc.isDeleted) 1L else 0L,
                hlc_map = "{\"core\":\"$hlc\"}",
                dirty_fields = "[]",
                is_dirty = 0L,
                server_version = 0L,
                created_at = now,
                updated_at = now,
            )
        }
    }
    seedDefaultBudgetRuleIfEmpty()
}

fun LocalStore.seedDefaultBudgetRuleIfEmpty() {
    if (queries.selectAllFinanceBudgetRules(::mapFinanceBudgetRuleRow).executeAsList().isNotEmpty()) {
        return
    }
    val defaultRule = FinanceBudgetRule(
        id = "budget-rule-global-default",
        name = "日常支出护栏",
        scopeType = BudgetScopeType.GLOBAL_DISCRETIONARY,
        accountPrefix = "Expenses:",
        excludedPrefixesJson = """["Expenses:Finance:Interest"]""",
        monthlyLimitMinor = 300000L,
        currency = "CNY",
        includeInRunway = true,
    )
    val now = timeProvider.nowEpochMillis()
    queries.upsertFinanceBudgetRule(
        id = defaultRule.id,
        name = defaultRule.name,
        scope_type = defaultRule.scopeType.name,
        account_prefix = defaultRule.accountPrefix,
        excluded_prefixes_json = defaultRule.excludedPrefixesJson,
        monthly_limit_minor = defaultRule.monthlyLimitMinor,
        currency = defaultRule.currency,
        include_in_runway = if (defaultRule.includeInRunway) 1L else 0L,
        is_deleted = 0L,
        hlc_map = "{}",
        dirty_fields = "[]",
        is_dirty = 0L,
        server_version = 0L,
        created_at = now,
        updated_at = now,
    )
}

fun LocalStore.financeBudgetRules(): List<FinanceBudgetRule> =
    queries.selectAllFinanceBudgetRules(::mapFinanceBudgetRuleRow).executeAsList()

fun LocalStore.financeBudgetRule(id: String): FinanceBudgetRule? =
    queries.selectFinanceBudgetRuleById(id, ::mapFinanceBudgetRuleRow).executeAsOneOrNull()
        ?.takeUnless { it.isDeleted }

fun LocalStore.createBudgetRule(
    name: String,
    scopeType: BudgetScopeType,
    accountPrefix: String = "Expenses:",
    excludedPrefixesJson: String = "[]",
    monthlyLimitMinor: Long,
    currency: String = "CNY",
    includeInRunway: Boolean = true,
): FinanceBudgetRule = database.transactionWithResult {
    val id = "budget-${idGenerator.randomId()}"
    val now = timeProvider.nowEpochMillis()
    val rule = FinanceBudgetRule(
        id = id,
        name = name.trim(),
        scopeType = scopeType,
        accountPrefix = accountPrefix.trim(),
        excludedPrefixesJson = excludedPrefixesJson,
        monthlyLimitMinor = monthlyLimitMinor,
        currency = currency,
        includeInRunway = includeInRunway,
    )
    queries.upsertFinanceBudgetRule(
        id = rule.id,
        name = rule.name,
        scope_type = rule.scopeType.name,
        account_prefix = rule.accountPrefix,
        excluded_prefixes_json = rule.excludedPrefixesJson,
        monthly_limit_minor = rule.monthlyLimitMinor,
        currency = rule.currency,
        include_in_runway = if (rule.includeInRunway) 1L else 0L,
        is_deleted = 0L,
        hlc_map = "{}",
        dirty_fields = "[]",
        is_dirty = 1L,
        server_version = 0L,
        created_at = now,
        updated_at = now,
    )
    rule
}

fun LocalStore.deleteBudgetRule(id: String): Unit = database.transaction {
    val existing = queries.selectFinanceBudgetRuleById(id, ::mapFinanceBudgetRuleRow).executeAsOneOrNull()
        ?: return@transaction
    val now = timeProvider.nowEpochMillis()
    val hlc = clock.next()
    queries.upsertFinanceBudgetRule(
        id = existing.id,
        name = existing.name,
        scope_type = existing.scopeType.name,
        account_prefix = existing.accountPrefix,
        excluded_prefixes_json = existing.excludedPrefixesJson,
        monthly_limit_minor = existing.monthlyLimitMinor,
        currency = existing.currency,
        include_in_runway = if (existing.includeInRunway) 1L else 0L,
        is_deleted = 1L,
        hlc_map = "{\"deleted\":\"$hlc\"}",
        dirty_fields = "[\"is_deleted\"]",
        is_dirty = 1L,
        server_version = 0L,
        created_at = now,
        updated_at = now,
    )
}

fun LocalStore.financeAccounts(): List<FinanceAccount> =
    queries.selectAllFinanceAccounts(::mapFinanceAccountRow).executeAsList()

fun LocalStore.financeAccount(id: String): FinanceAccount? =
    queries.selectFinanceAccountById(id, ::mapFinanceAccountRow).executeAsOneOrNull()
        ?.takeUnless { it.isDeleted }

fun LocalStore.createFinanceAccount(
    path: String,
    name: String,
    category: AccountCategory,
    currency: String = "CNY",
    includeInNetWorth: Boolean = true,
    isPassThrough: Boolean = false,
    passThroughExpenseAccountId: String? = null,
    liquidTier: LiquidTier = LiquidTier.LIQUID,
    statementDay: Int? = null,
    dueDay: Int? = null,
): String = database.transactionWithResult {
    val now = timeProvider.nowEpochMillis()
    val accountId = idGenerator.randomId()
    val hlc = clock.next()
    queries.upsertFinanceAccount(
        id = accountId,
        path = path,
        name = name,
        category = category.name,
        currency = currency,
        include_in_net_worth = if (includeInNetWorth) 1L else 0L,
        is_pass_through = if (isPassThrough) 1L else 0L,
        pass_through_expense_account_id = passThroughExpenseAccountId,
        liquid_tier = liquidTier.name,
        statement_day = statementDay?.toLong(),
        due_day = dueDay?.toLong(),
        is_archived = 0L,
        is_deleted = 0L,
        hlc_map = "{\"core\":\"$hlc\"}",
        dirty_fields = "[\"core\"]",
        is_dirty = 1L,
        server_version = 0L,
        created_at = now,
        updated_at = now,
    )
    accountId
}

fun LocalStore.updateFinanceAccount(
    id: String,
    path: String,
    name: String,
    category: AccountCategory,
    currency: String,
    includeInNetWorth: Boolean,
    isPassThrough: Boolean,
    passThroughExpenseAccountId: String?,
    liquidTier: LiquidTier,
    statementDay: Int?,
    dueDay: Int?,
    isArchived: Boolean,
): Unit = database.transaction {
    val existing = queries.selectFinanceAccountById(id, ::mapFinanceAccountRow).executeAsOneOrNull()
        ?: return@transaction
    val now = timeProvider.nowEpochMillis()
    val hlc = clock.next()
    queries.upsertFinanceAccount(
        id = id,
        path = path,
        name = name,
        category = category.name,
        currency = currency,
        include_in_net_worth = if (includeInNetWorth) 1L else 0L,
        is_pass_through = if (isPassThrough) 1L else 0L,
        pass_through_expense_account_id = passThroughExpenseAccountId,
        liquid_tier = liquidTier.name,
        statement_day = statementDay?.toLong(),
        due_day = dueDay?.toLong(),
        is_archived = if (isArchived) 1L else 0L,
        is_deleted = if (existing.isDeleted) 1L else 0L,
        hlc_map = "{\"core\":\"$hlc\"}",
        dirty_fields = "[\"core\"]",
        is_dirty = 1L,
        server_version = 0L,
        created_at = now,
        updated_at = now,
    )
}

fun LocalStore.deleteFinanceAccount(id: String): Unit = database.transaction {
    val existing = queries.selectFinanceAccountById(id, ::mapFinanceAccountRow).executeAsOneOrNull()
        ?: return@transaction
    val now = timeProvider.nowEpochMillis()
    val hlc = clock.next()
    queries.upsertFinanceAccount(
        id = existing.id,
        path = existing.path,
        name = existing.name,
        category = existing.category.name,
        currency = existing.currency,
        include_in_net_worth = if (existing.includeInNetWorth) 1L else 0L,
        is_pass_through = if (existing.isPassThrough) 1L else 0L,
        pass_through_expense_account_id = existing.passThroughExpenseAccountId,
        liquid_tier = existing.liquidTier.name,
        statement_day = existing.statementDay?.toLong(),
        due_day = existing.dueDay?.toLong(),
        is_archived = if (existing.isArchived) 1L else 0L,
        is_deleted = 1L,
        hlc_map = "{\"deleted\":\"$hlc\"}",
        dirty_fields = "[\"deleted\"]",
        is_dirty = 1L,
        server_version = 0L,
        created_at = now,
        updated_at = now,
    )
}

fun LocalStore.financeTransactions(): List<FinanceTransaction> =
    queries.selectAllFinanceTransactions(::mapFinanceTransactionRow).executeAsList()

fun LocalStore.financeTransaction(id: String): FinanceTransaction? =
    queries.selectFinanceTransactionById(id, ::mapFinanceTransactionRow).executeAsOneOrNull()
        ?.takeUnless { it.isDeleted }

fun LocalStore.financeSplitsForTransaction(transactionId: String): List<FinanceSplit> =
    queries.selectFinanceSplitsForTransaction(transactionId, ::mapFinanceSplitRow).executeAsList()

fun LocalStore.financeSplitsForAccount(accountId: String): List<FinanceSplit> =
    queries.selectFinanceSplitsForAccount(accountId, ::mapFinanceSplitRow).executeAsList()

fun LocalStore.financeClearingGroups(): List<FinanceClearingGroup> =
    queries.selectAllFinanceClearingGroups(::mapFinanceClearingGroupRow).executeAsList()

fun LocalStore.financeClearingGroup(id: String): FinanceClearingGroup? =
    queries.selectFinanceClearingGroupById(id, ::mapFinanceClearingGroupRow).executeAsOneOrNull()
        ?.takeUnless { it.isDeleted }

fun LocalStore.clearingGroupsForSplit(splitId: String): List<FinanceClearingGroup> =
    queries.selectClearingGroupsForSplit(splitId, ::mapFinanceClearingGroupRow).executeAsList()

fun LocalStore.financeAmortizationPlans(): List<FinanceAmortizationPlan> =
    queries.selectAllFinanceAmortizationPlans(::mapFinanceAmortizationPlanRow).executeAsList()

fun LocalStore.financeAmortizationPlan(id: String): FinanceAmortizationPlan? =
    queries.selectFinanceAmortizationPlanById(id, ::mapFinanceAmortizationPlanRow).executeAsOneOrNull()
        ?.takeUnless { it.isDeleted }

fun LocalStore.createFinanceClearingGroup(
    name: String,
    groupType: ClearingGroupType,
    anchorCurrency: String = "CNY",
    targetAccountId: String? = null,
    statementCycle: String? = null,
    dueDate: String? = null,
): String = database.transactionWithResult {
    val now = timeProvider.nowEpochMillis()
    val groupId = idGenerator.randomId()
    val hlc = clock.next()
    queries.upsertFinanceClearingGroup(
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

fun LocalStore.createFinanceTransaction(
    txDate: String,
    summary: String = "",
    payee: String? = null,
    txTime: String? = null,
    status: TransactionStatus = TransactionStatus.CLEARED,
    sourcePlanId: String? = null,
    periodIndex: Int? = null,
    splits: List<FinanceSplitDraft>,
): String = database.transactionWithResult {
    val now = timeProvider.nowEpochMillis()
    val txId = idGenerator.randomId()
    val txHlc = clock.next()

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
            id = draft.id ?: idGenerator.randomId(),
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

    queries.upsertFinanceTransaction(
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
        val splitHlc = clock.next()
        queries.upsertFinanceSplit(
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
            val account = queries.selectFinanceAccountById(split.accountId, ::mapFinanceAccountRow).executeAsOneOrNull()
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
                val existingGroup = queries.selectFinanceClearingGroupById(cycleInfo.deterministicGroupId, ::mapFinanceClearingGroupRow).executeAsOneOrNull()
                if (existingGroup == null) {
                    val groupHlc = clock.next()
                    queries.upsertFinanceClearingGroup(
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
            val linkHlc = clock.next()
            queries.upsertFinanceSplitClearingGroup(
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
            val fxHlc = clock.next()
            queries.upsertFinanceExchangeRate(
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

fun LocalStore.refreshClearingGroupStatus(cgId: String, now: Long = timeProvider.nowEpochMillis()): Unit = database.transaction {
    val group = queries.selectFinanceClearingGroupById(cgId, ::mapFinanceClearingGroupRow).executeAsOneOrNull()
        ?: return@transaction
    val groupSplits = queries.selectFinanceSplitsForClearingGroup(cgId, ::mapFinanceSplitRow).executeAsList()
    val txIds = groupSplits.map { it.transactionId }.toSet()
    val txMap = txIds.mapNotNull { id -> queries.selectFinanceTransactionById(id, ::mapFinanceTransactionRow).executeAsOneOrNull() }
        .associateBy { it.id }

    val eval = ClearingGroupEvaluator.evaluate(group, groupSplits, txMap)
    if (eval.derivedStatus != group.status) {
        val cgHlc = clock.next()
        queries.upsertFinanceClearingGroup(
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

fun LocalStore.reassignSplitClearingGroup(
    splitId: String,
    oldGroupId: String,
    newGroupId: String,
): Unit = database.transaction {
    val now = timeProvider.nowEpochMillis()
    val oldLinkId = "scg:$splitId:$oldGroupId"
    val oldLinkHlc = clock.next()
    queries.upsertFinanceSplitClearingGroup(
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
    val newLinkHlc = clock.next()
    queries.upsertFinanceSplitClearingGroup(
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

fun LocalStore.autoPadAccountBalance(
    accountId: String,
    actualBalanceMinor: Long,
    adjustmentDate: String,
): String? {
    val targetAccount = queries.selectFinanceAccountById(accountId, ::mapFinanceAccountRow).executeAsOneOrNull()
        ?: throw IllegalArgumentException("Account not found: $accountId")

    val accountSplits = queries.selectFinanceSplitsForAccount(accountId, ::mapFinanceSplitRow).executeAsList()
    val txIds = accountSplits.map { it.transactionId }.toSet()
    val clearedTxIds = txIds.filter { id ->
        queries.selectFinanceTransactionById(id, ::mapFinanceTransactionRow).executeAsOneOrNull()?.status == TransactionStatus.CLEARED
    }.toSet()

    val recordedBalanceMinor = accountSplits
        .filter { it.transactionId in clearedTxIds }
        .sumOf { it.amountMinor }

    val deltaMinor = actualBalanceMinor - recordedBalanceMinor
    if (deltaMinor == 0L) {
        return null
    }

    val allAccounts = financeAccounts()
    val adjustmentAccount = allAccounts.find { it.path == "Expenses:Adjustment:Unallocated" }
        ?: allAccounts.firstOrNull { it.category == AccountCategory.EXPENSE }
        ?: throw IllegalStateException("No expense account available for adjustment")

    return createFinanceTransaction(
        txDate = adjustmentDate,
        summary = "余额对齐自动平账 (${targetAccount.name})",
        status = TransactionStatus.CLEARED,
        splits = listOf(
            FinanceSplitDraft(
                accountId = targetAccount.id,
                amountMinor = deltaMinor,
                currency = targetAccount.currency,
                narration = "余额对齐自动平账",
            ),
            FinanceSplitDraft(
                accountId = adjustmentAccount.id,
                amountMinor = -deltaMinor,
                currency = targetAccount.currency,
                narration = "余额对齐自动平账",
            ),
        ),
    )
}

fun LocalStore.deleteFinanceTransaction(id: String): Unit = database.transaction {
    val existing = queries.selectFinanceTransactionById(id, ::mapFinanceTransactionRow).executeAsOneOrNull()
        ?: return@transaction
    val now = timeProvider.nowEpochMillis()
    val hlc = clock.next()

    queries.upsertFinanceTransaction(
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

    val splits = queries.selectFinanceSplitsForTransaction(id, ::mapFinanceSplitRow).executeAsList()
    for (split in splits) {
        val splitHlc = clock.next()
        queries.upsertFinanceSplit(
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

        val links = queries.selectAllActiveFinanceSplitClearingGroups(::mapFinanceSplitClearingGroupRow)
            .executeAsList().filter { it.splitId == split.id }
        for (link in links) {
            val linkHlc = clock.next()
            queries.upsertFinanceSplitClearingGroup(
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

fun LocalStore.createFinanceAmortizationPlan(
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
): String = database.transactionWithResult {
    val now = timeProvider.nowEpochMillis()
    val planId = idGenerator.randomId()
    val clearingGroupId = "cg-loan:$planId"
    val hlc = clock.next()

    // 1. Create lifecycle clearing group for principal tracking
    queries.upsertFinanceClearingGroup(
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
    queries.upsertFinanceAmortizationPlan(
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
        val linkId = idGenerator.randomId()
        val linkHlc = clock.next()
        queries.upsertFinanceSplitClearingGroup(
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
        // Principal repayment to liability account (+Principal reduces liability)
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

fun LocalStore.deleteFinanceAmortizationPlan(id: String): Unit = database.transaction {
    val existing = queries.selectFinanceAmortizationPlanById(id, ::mapFinanceAmortizationPlanRow).executeAsOneOrNull()
        ?: return@transaction
    val now = timeProvider.nowEpochMillis()
    val hlc = clock.next()

    queries.upsertFinanceAmortizationPlan(
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
        val groupHlc = clock.next()
        queries.upsertFinanceClearingGroup(
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

fun LocalStore.deriveFinanceViewState(
    selectedFilter: FinanceLedgerFilter = FinanceLedgerFilter.ALL,
    filterAccountId: String? = null,
    filterClearingGroupId: String? = null,
    viewMode: FinanceLedgerViewMode = FinanceLedgerViewMode.STANDARD,
    runwayHorizonDays: Int = 90,
): FinanceViewState {
    val accounts = queries.selectAllFinanceAccounts(::mapFinanceAccountRow).executeAsList()
    val accountsById = accounts.associateBy { it.id }

    val allSplits = queries.selectAllActiveFinanceSplits(::mapFinanceSplitRow).executeAsList()
    val splitsByAccount = allSplits.groupBy { it.accountId }
    val splitsByTx = allSplits.groupBy { it.transactionId }

    val allClearingGroups = queries.selectAllFinanceClearingGroups(::mapFinanceClearingGroupRow).executeAsList()
    val cgMap = allClearingGroups.associateBy { it.id }

    val allLinks = queries.selectAllActiveFinanceSplitClearingGroups(::mapFinanceSplitClearingGroupRow).executeAsList()
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

    val allTxs = queries.selectAllFinanceTransactions(::mapFinanceTransactionRow).executeAsList()
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

    val allPlans = queries.selectAllFinanceAmortizationPlans(::mapFinanceAmortizationPlanRow).executeAsList()
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

    val allBudgetRules = queries.selectAllFinanceBudgetRules(::mapFinanceBudgetRuleRow).executeAsList()
    val today = timeProvider.today()
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
    )
}

// Row Mapping Helpers
internal fun mapFinanceAccountRow(
    id: String,
    path: String,
    name: String,
    category: String,
    currency: String,
    include_in_net_worth: Long,
    is_pass_through: Long,
    pass_through_expense_account_id: String?,
    liquid_tier: String,
    statement_day: Long?,
    due_day: Long?,
    is_archived: Long,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): FinanceAccount = FinanceAccount(
    id = id,
    path = path,
    name = name,
    category = parseAccountCategory(category),
    currency = currency,
    includeInNetWorth = include_in_net_worth == 1L,
    isPassThrough = is_pass_through == 1L,
    passThroughExpenseAccountId = pass_through_expense_account_id,
    liquidTier = parseLiquidTier(liquid_tier),
    statementDay = statement_day?.toInt(),
    dueDay = due_day?.toInt(),
    isArchived = is_archived == 1L,
    isDeleted = is_deleted == 1L,
)

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

private fun parseAmortizationMethod(raw: String): AmortizationMethod =
    runCatching { AmortizationMethod.valueOf(raw) }.getOrElse {
        when (raw.lowercase().replace("_", "")) {
            "equalprincipal" -> AmortizationMethod.EQUAL_PRINCIPAL
            "equalinstallment" -> AmortizationMethod.EQUAL_INSTALLMENT
            "interestfirst" -> AmortizationMethod.INTEREST_FIRST
            "flatfee" -> AmortizationMethod.FLAT_FEE
            else -> AmortizationMethod.EQUAL_INSTALLMENT
        }
    }

private fun parseAccountCategory(raw: String): AccountCategory =
    runCatching { AccountCategory.valueOf(raw) }.getOrElse {
        when (raw.lowercase()) {
            "asset" -> AccountCategory.ASSET
            "liability" -> AccountCategory.LIABILITY
            "income" -> AccountCategory.INCOME
            "expense" -> AccountCategory.EXPENSE
            "equity" -> AccountCategory.EQUITY
            else -> AccountCategory.ASSET
        }
    }

private fun parseLiquidTier(raw: String): LiquidTier =
    runCatching { LiquidTier.valueOf(raw) }.getOrElse {
        when (raw.lowercase()) {
            "liquid" -> LiquidTier.LIQUID
            "semiliquid", "semi_liquid" -> LiquidTier.SEMI_LIQUID
            "illiquid" -> LiquidTier.ILLIQUID
            else -> LiquidTier.NONE
        }
    }

private fun parseTransactionStatus(raw: String): TransactionStatus =
    runCatching { TransactionStatus.valueOf(raw) }.getOrElse {
        when (raw.lowercase()) {
            "draft" -> TransactionStatus.DRAFT
            "scheduled" -> TransactionStatus.SCHEDULED
            "cleared" -> TransactionStatus.CLEARED
            "void" -> TransactionStatus.VOID
            else -> TransactionStatus.CLEARED
        }
    }

private fun parseClearingGroupType(raw: String): ClearingGroupType =
    runCatching { ClearingGroupType.valueOf(raw) }.getOrElse {
        when (raw.lowercase()) {
            "instantbatch", "instant_batch" -> ClearingGroupType.INSTANT_BATCH
            "creditstatement", "credit_statement" -> ClearingGroupType.CREDIT_STATEMENT
            "loanlifecycle", "loan_lifecycle" -> ClearingGroupType.LOAN_LIFECYCLE
            "reimbursement" -> ClearingGroupType.REIMBURSEMENT
            else -> ClearingGroupType.INSTANT_BATCH
        }
    }

private fun parseClearingGroupStatus(raw: String): ClearingGroupStatus =
    runCatching { ClearingGroupStatus.valueOf(raw) }.getOrElse {
        when (raw.lowercase()) {
            "active" -> ClearingGroupStatus.ACTIVE
            "settled" -> ClearingGroupStatus.SETTLED
            "overdrawn" -> ClearingGroupStatus.OVERDRAWN
            else -> ClearingGroupStatus.ACTIVE
        }
    }

private fun parseExchangeRateSource(raw: String): ExchangeRateSource =
    runCatching { ExchangeRateSource.valueOf(raw) }.getOrElse {
        when (raw.lowercase()) {
            "manual" -> ExchangeRateSource.MANUAL
            "implicitfromsplit", "implicit_from_split" -> ExchangeRateSource.IMPLICIT_FROM_SPLIT
            "remotefeed", "remote_feed" -> ExchangeRateSource.REMOTE_FEED
            else -> ExchangeRateSource.MANUAL
        }
    }

internal fun mapFinanceBudgetRuleRow(
    id: String,
    name: String,
    scope_type: String,
    account_prefix: String,
    excluded_prefixes_json: String,
    monthly_limit_minor: Long,
    currency: String,
    include_in_runway: Long,
    is_deleted: Long,
    hlc_map: String,
    dirty_fields: String,
    is_dirty: Long,
    server_version: Long,
    created_at: Long,
    updated_at: Long,
): FinanceBudgetRule = FinanceBudgetRule(
    id = id,
    name = name,
    scopeType = parseBudgetScopeType(scope_type),
    accountPrefix = account_prefix,
    excludedPrefixesJson = excluded_prefixes_json,
    monthlyLimitMinor = monthly_limit_minor,
    currency = currency,
    includeInRunway = include_in_runway == 1L,
    isDeleted = is_deleted == 1L,
)

private fun parseBudgetScopeType(raw: String): BudgetScopeType =
    runCatching { BudgetScopeType.valueOf(raw) }.getOrElse {
        when (raw.lowercase()) {
            "accountsubtree", "account_subtree" -> BudgetScopeType.ACCOUNT_SUBTREE
            else -> BudgetScopeType.GLOBAL_DISCRETIONARY
        }
    }

