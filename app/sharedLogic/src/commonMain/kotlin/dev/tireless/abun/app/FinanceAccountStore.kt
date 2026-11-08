package dev.tireless.abun.app

import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.BudgetScopeType
import dev.tireless.abun.finance.FinanceAccount
import dev.tireless.abun.finance.FinanceBudgetRule
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.finance.TransactionStatus

val DEFAULT_FIVE_FINANCE_ACCOUNTS: List<FinanceAccount> = listOf(
    FinanceAccount(
        id = "acc-root-assets",
        path = "Assets",
        name = "资产",
        category = AccountCategory.ASSET,
        currency = "CNY",
        includeInNetWorth = true,
        liquidTier = LiquidTier.LIQUID,
    ),
    FinanceAccount(
        id = "acc-root-liabilities",
        path = "Liabilities",
        name = "负债",
        category = AccountCategory.LIABILITY,
        currency = "CNY",
        includeInNetWorth = true,
        liquidTier = LiquidTier.NONE,
    ),
    FinanceAccount(
        id = "acc-root-income",
        path = "Income",
        name = "收入",
        category = AccountCategory.INCOME,
        currency = "CNY",
        includeInNetWorth = false,
        liquidTier = LiquidTier.NONE,
    ),
    FinanceAccount(
        id = "acc-root-expenses",
        path = "Expenses",
        name = "支出",
        category = AccountCategory.EXPENSE,
        currency = "CNY",
        includeInNetWorth = false,
        liquidTier = LiquidTier.NONE,
    ),
    FinanceAccount(
        id = "acc-root-equity",
        path = "Equity",
        name = "权益",
        category = AccountCategory.EQUITY,
        currency = "CNY",
        includeInNetWorth = false,
        liquidTier = LiquidTier.NONE,
    ),
)
val defaultFiveFinanceAccounts: List<FinanceAccount> get() = DEFAULT_FIVE_FINANCE_ACCOUNTS
val DEFAULT_FIVE_FINANCE_ACCOUNT_IDS: Set<String> = DEFAULT_FIVE_FINANCE_ACCOUNTS.map { it.id }.toSet()

val PREDEFINED_FINANCE_SUB_ACCOUNTS: List<FinanceAccount> = listOf(
    FinanceAccount(
        id = "acc-assets-bank-default",
        path = "Assets:Bank:Default",
        name = "默认银行账户",
        category = AccountCategory.ASSET,
        currency = "CNY",
        includeInNetWorth = true,
        liquidTier = LiquidTier.LIQUID,
    ),
    FinanceAccount(
        id = "acc-liabilities-cc-default",
        path = "Liabilities:CreditCard:Default",
        name = "默认信用卡",
        category = AccountCategory.LIABILITY,
        currency = "CNY",
        includeInNetWorth = true,
        liquidTier = LiquidTier.NONE,
        statementDay = 15,
        dueDay = 5,
    ),
    FinanceAccount(
        id = "acc-liabilities-loan-default",
        path = "Liabilities:Loan:Default",
        name = "默认分期/贷款",
        category = AccountCategory.LIABILITY,
        currency = "CNY",
        includeInNetWorth = true,
        liquidTier = LiquidTier.NONE,
    ),
    FinanceAccount(
        id = "acc-expenses-food",
        path = "Expenses:Food",
        name = "餐饮日常",
        category = AccountCategory.EXPENSE,
        currency = "CNY",
        includeInNetWorth = false,
        liquidTier = LiquidTier.NONE,
    ),
    FinanceAccount(
        id = "acc-expenses-housing",
        path = "Expenses:Housing",
        name = "居住房租",
        category = AccountCategory.EXPENSE,
        currency = "CNY",
        includeInNetWorth = false,
        liquidTier = LiquidTier.NONE,
    ),
    FinanceAccount(
        id = "acc-expenses-interest",
        path = "Expenses:Finance:Interest",
        name = "利息与手续费",
        category = AccountCategory.EXPENSE,
        currency = "CNY",
        includeInNetWorth = false,
        liquidTier = LiquidTier.NONE,
    ),
    FinanceAccount(
        id = "acc-expenses-adjustment",
        path = "Expenses:Adjustment:Unallocated",
        name = "平账调整差额",
        category = AccountCategory.EXPENSE,
        currency = "CNY",
        includeInNetWorth = false,
        liquidTier = LiquidTier.NONE,
    ),
    FinanceAccount(
        id = "acc-income-salary",
        path = "Income:Salary",
        name = "工资薪水",
        category = AccountCategory.INCOME,
        currency = "CNY",
        includeInNetWorth = false,
        liquidTier = LiquidTier.NONE,
    ),
)
val predefinedFinanceSubAccounts: List<FinanceAccount> get() = PREDEFINED_FINANCE_SUB_ACCOUNTS

class FinanceAccountStore(private val store: LocalStore) {
    fun ensureDefaultFinanceAccounts() {
        val now = store.timeProvider.nowEpochMillis()
        store.database.transaction {
            for (acc in DEFAULT_FIVE_FINANCE_ACCOUNTS) {
                val existing = store.queries.selectFinanceAccountById(acc.id).executeAsOneOrNull()
                if (existing == null) {
                    val hlc = store.clock.next()
                    store.queries.upsertFinanceAccount(
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
                        is_archived = 0L,
                        is_deleted = 0L,
                        hlc_map = "{\"core\":\"$hlc\"}",
                        dirty_fields = "[]",
                        is_dirty = 0L,
                        server_version = 0L,
                        created_at = now,
                        updated_at = now,
                    )
                } else if (existing.is_deleted != 0L) {
                    val hlc = store.clock.next()
                    val needsDirty = existing.server_version > 0L
                    store.queries.upsertFinanceAccount(
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
                        is_archived = 0L,
                        is_deleted = 0L,
                        hlc_map = "{\"core\":\"$hlc\"}",
                        dirty_fields = if (needsDirty) "[\"core\"]" else "[]",
                        is_dirty = if (needsDirty) 1L else 0L,
                        server_version = existing.server_version,
                        created_at = existing.created_at,
                        updated_at = now,
                    )
                }
            }
        }
    }

    fun seedDefaultFinanceAccountsIfEmpty() {
        ensureDefaultFinanceAccounts()
    }

    fun canLoadPredefinedFinanceAccounts(): Boolean {
        val activeAccounts = store.queries.selectAllFinanceAccounts(::mapFinanceAccountRow).executeAsList()
        val activeAccountIds = activeAccounts.map { it.id }.toSet()
        if (activeAccountIds != DEFAULT_FIVE_FINANCE_ACCOUNT_IDS) {
            return false
        }
        return store.queries.hasActiveFinanceRecords().executeAsOne() == 0L
    }

    fun loadPredefinedFinanceAccounts(): Boolean {
        if (!canLoadPredefinedFinanceAccounts()) {
            return false
        }
        ensureDefaultFinanceAccounts()
        val now = store.timeProvider.nowEpochMillis()
        store.database.transaction {
            for (acc in PREDEFINED_FINANCE_SUB_ACCOUNTS) {
                val existing = store.queries.selectFinanceAccountById(acc.id).executeAsOneOrNull()
                val serverVersion = existing?.server_version ?: 0L
                val createdAt = existing?.created_at ?: now
                val hlc = store.clock.next()
                store.queries.upsertFinanceAccount(
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
                    is_archived = 0L,
                    is_deleted = 0L,
                    hlc_map = "{\"core\":\"$hlc\"}",
                    dirty_fields = "[\"core\"]",
                    is_dirty = 1L,
                    server_version = serverVersion,
                    created_at = createdAt,
                    updated_at = now,
                )
            }
        }
        return true
    }

    fun resetFinanceToDefaultAccounts() {
        val now = store.timeProvider.nowEpochMillis()
        store.database.transaction {
            val hlc = store.clock.next()
            val deletedHlcMap = "{\"deleted\":\"$hlc\"}"

            store.queries.softDeleteSyncedFinanceSplitClearingGroups(deletedHlcMap, now)
            store.queries.deleteLocalFinanceSplitClearingGroups()

            store.queries.softDeleteSyncedFinanceSplits(deletedHlcMap, now)
            store.queries.deleteLocalFinanceSplits()

            store.queries.softDeleteSyncedFinanceTransactions(deletedHlcMap, now)
            store.queries.deleteLocalFinanceTransactions()

            store.queries.softDeleteSyncedFinanceClearingGroups(deletedHlcMap, now)
            store.queries.deleteLocalFinanceClearingGroups()

            store.queries.softDeleteSyncedFinanceAmortizationPlans(deletedHlcMap, now)
            store.queries.deleteLocalFinanceAmortizationPlans()

            store.queries.softDeleteSyncedFinanceBudgetRules(deletedHlcMap, now)
            store.queries.deleteLocalFinanceBudgetRules()

            store.queries.softDeleteSyncedFinanceExchangeRates(deletedHlcMap, now)
            store.queries.deleteLocalFinanceExchangeRates()

            store.queries.softDeleteSyncedNonDefaultFinanceAccounts(deletedHlcMap, now)
            store.queries.deleteLocalNonDefaultFinanceAccounts()

            ensureDefaultFinanceAccounts()
        }
    }

    fun resetFinanceToDefaultFive() = resetFinanceToDefaultAccounts()

    fun seedDefaultBudgetRuleIfEmpty() {
        if (store.queries.selectAllFinanceBudgetRules(::mapFinanceBudgetRuleRow).executeAsList().isNotEmpty()) {
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
        val now = store.timeProvider.nowEpochMillis()
        store.queries.upsertFinanceBudgetRule(
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

    fun financeBudgetRules(): List<FinanceBudgetRule> =
        store.queries.selectAllFinanceBudgetRules(::mapFinanceBudgetRuleRow).executeAsList()

    fun financeBudgetRule(id: String): FinanceBudgetRule? =
        store.queries.selectFinanceBudgetRuleById(id, ::mapFinanceBudgetRuleRow).executeAsOneOrNull()
            ?.takeUnless { it.isDeleted }

    fun createBudgetRule(
        name: String,
        scopeType: BudgetScopeType,
        accountPrefix: String = "Expenses:",
        excludedPrefixesJson: String = "[]",
        monthlyLimitMinor: Long,
        currency: String = "CNY",
        includeInRunway: Boolean = true,
    ): FinanceBudgetRule = store.database.transactionWithResult {
        val id = "budget-${store.idGenerator.randomId()}"
        val now = store.timeProvider.nowEpochMillis()
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
        store.queries.upsertFinanceBudgetRule(
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

    fun deleteBudgetRule(id: String): Unit = store.database.transaction {
        val existing = store.queries.selectFinanceBudgetRuleById(id, ::mapFinanceBudgetRuleRow).executeAsOneOrNull()
            ?: return@transaction
        val now = store.timeProvider.nowEpochMillis()
        val hlc = store.clock.next()
        store.queries.upsertFinanceBudgetRule(
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

    fun financeAccounts(): List<FinanceAccount> =
        store.queries.selectAllFinanceAccounts(::mapFinanceAccountRow).executeAsList()

    fun financeAccount(id: String): FinanceAccount? =
        store.queries.selectFinanceAccountById(id, ::mapFinanceAccountRow).executeAsOneOrNull()
            ?.takeUnless { it.isDeleted }

    fun createFinanceAccount(
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
    ): String = store.database.transactionWithResult {
        val now = store.timeProvider.nowEpochMillis()
        val accountId = store.idGenerator.randomId()
        val hlc = store.clock.next()
        store.queries.upsertFinanceAccount(
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

    fun updateFinanceAccount(
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
    ): Unit = store.database.transaction {
        val existing = store.queries.selectFinanceAccountById(id, ::mapFinanceAccountRow).executeAsOneOrNull()
            ?: return@transaction
        val now = store.timeProvider.nowEpochMillis()
        val hlc = store.clock.next()
        store.queries.upsertFinanceAccount(
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

    fun deleteFinanceAccount(id: String): Unit = store.database.transaction {
        if (id in DEFAULT_FIVE_FINANCE_ACCOUNT_IDS) {
            return@transaction
        }
        val existing = store.queries.selectFinanceAccountById(id).executeAsOneOrNull()
            ?: return@transaction
        val now = store.timeProvider.nowEpochMillis()
        val hlc = store.clock.next()
        store.queries.upsertFinanceAccount(
            id = existing.id,
            path = existing.path,
            name = existing.name,
            category = existing.category,
            currency = existing.currency,
            include_in_net_worth = existing.include_in_net_worth,
            is_pass_through = existing.is_pass_through,
            pass_through_expense_account_id = existing.pass_through_expense_account_id,
            liquid_tier = existing.liquid_tier,
            statement_day = existing.statement_day,
            due_day = existing.due_day,
            is_archived = existing.is_archived,
            is_deleted = 1L,
            hlc_map = "{\"deleted\":\"$hlc\"}",
            dirty_fields = "[\"deleted\"]",
            is_dirty = 1L,
            server_version = existing.server_version,
            created_at = existing.created_at,
            updated_at = now,
        )
    }

    fun autoPadAccountBalance(
        accountId: String,
        actualBalanceMinor: Long,
        adjustmentDate: String,
    ): String? {
        val targetAccount = store.queries.selectFinanceAccountById(accountId, ::mapFinanceAccountRow).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Account not found: $accountId")

        val accountSplits = store.queries.selectFinanceSplitsForAccount(accountId, store.financeLedgerStore::mapFinanceSplitRow).executeAsList()
        val txIds = accountSplits.map { it.transactionId }.toSet()
        val clearedTxIds = txIds.filter { id ->
            store.queries.selectFinanceTransactionById(id, store.financeLedgerStore::mapFinanceTransactionRow).executeAsOneOrNull()?.status == TransactionStatus.CLEARED
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

        return store.financeLedgerStore.createFinanceTransaction(
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

    internal fun parseAccountCategory(raw: String): AccountCategory =
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

    internal fun parseLiquidTier(raw: String): LiquidTier =
        runCatching { LiquidTier.valueOf(raw) }.getOrElse {
            when (raw.lowercase()) {
                "liquid" -> LiquidTier.LIQUID
                "semiliquid", "semi_liquid" -> LiquidTier.SEMI_LIQUID
                "illiquid" -> LiquidTier.ILLIQUID
                else -> LiquidTier.NONE
            }
        }

    internal fun parseBudgetScopeType(raw: String): BudgetScopeType =
        runCatching { BudgetScopeType.valueOf(raw) }.getOrElse {
            when (raw.lowercase()) {
                "accountsubtree", "account_subtree" -> BudgetScopeType.ACCOUNT_SUBTREE
                else -> BudgetScopeType.GLOBAL_DISCRETIONARY
            }
        }
}
