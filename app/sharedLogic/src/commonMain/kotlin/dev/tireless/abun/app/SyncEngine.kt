package dev.tireless.abun.app

class SyncEngine(
    private val localStore: LocalStore,
    private val remoteApi: SyncRemoteApi,
    private val logger: AppLogger = DefaultAppLogger,
) {
    suspend fun syncNow() {
        logger.info(message = "sync.started")
        pullPreferences()
        pullRoutines()
        pullTasks()
        pullAlarms()
        pullTaskEvents()
        pullPomodoroSessions()
        pullFinanceAccounts()
        pullFinanceExchangeRates()
        pullFinanceClearingGroups()
        pullFinanceTransactions()
        pullFinanceSplits()
        pullFinanceSplitClearingGroups()
        pullFinanceAmortizationPlans()
        pullFinanceBudgetRules()
        pushPreferences()
        pushRoutines()
        pushTasks()
        pushAlarms()
        pushTaskEvents()
        pushPomodoroSessions()
        pushFinanceAccounts()
        pushFinanceExchangeRates()
        pushFinanceClearingGroups()
        pushFinanceTransactions()
        pushFinanceSplits()
        pushFinanceSplitClearingGroups()
        pushFinanceAmortizationPlans()
        pushFinanceBudgetRules()
        logger.info(message = "sync.completed")
    }

    private suspend fun pullPreferences() = pullPaged(SyncScope.PREFERENCES, remoteApi::pullPreferences, localStore::mergeRemotePreferences)
    private suspend fun pullRoutines() = pullPaged(SyncScope.ROUTINES, remoteApi::pullRoutines, localStore::mergeRemoteRoutines)
    private suspend fun pullTasks() = pullPaged(SyncScope.TASKS, remoteApi::pullTasks, localStore::mergeRemoteTasks)
    private suspend fun pullAlarms() = pullPaged(SyncScope.ALARMS, remoteApi::pullAlarms, localStore::mergeRemoteAlarms)
    private suspend fun pullTaskEvents() = pullPaged(SyncScope.TASK_EVENTS, remoteApi::pullTaskEvents, localStore::mergeRemoteTaskEvents)
    private suspend fun pullPomodoroSessions() = pullPaged(SyncScope.POMODORO_SESSIONS, remoteApi::pullPomodoroSessions, localStore::mergeRemotePomodoroSessions)
    private suspend fun pullFinanceAccounts() = pullPaged(SyncScope.FINANCE_ACCOUNTS, remoteApi::pullFinanceAccounts, localStore::mergeRemoteFinanceAccounts)
    private suspend fun pullFinanceExchangeRates() = pullPaged(SyncScope.FINANCE_EXCHANGE_RATES, remoteApi::pullFinanceExchangeRates, localStore::mergeRemoteFinanceExchangeRates)
    private suspend fun pullFinanceClearingGroups() = pullPaged(SyncScope.FINANCE_CLEARING_GROUPS, remoteApi::pullFinanceClearingGroups, localStore::mergeRemoteFinanceClearingGroups)
    private suspend fun pullFinanceTransactions() = pullPaged(SyncScope.FINANCE_TRANSACTIONS, remoteApi::pullFinanceTransactions, localStore::mergeRemoteFinanceTransactions)
    private suspend fun pullFinanceSplits() = pullPaged(SyncScope.FINANCE_SPLITS, remoteApi::pullFinanceSplits, localStore::mergeRemoteFinanceSplits)
    private suspend fun pullFinanceSplitClearingGroups() = pullPaged(SyncScope.FINANCE_SPLIT_CLEARING_GROUPS, remoteApi::pullFinanceSplitClearingGroups, localStore::mergeRemoteFinanceSplitClearingGroups)
    private suspend fun pullFinanceAmortizationPlans() = pullPaged(SyncScope.FINANCE_AMORTIZATION_PLANS, remoteApi::pullFinanceAmortizationPlans, localStore::mergeRemoteFinanceAmortizationPlans)
    private suspend fun pullFinanceBudgetRules() = pullPaged(SyncScope.FINANCE_BUDGET_RULES, remoteApi::pullFinanceBudgetRules, localStore::mergeRemoteFinanceBudgetRules)

    private suspend fun pushPreferences() {
        val local = localStore.dirtyPreferences()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.PREFERENCES.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemotePreferences(runSyncStep("pushing", SyncScope.PREFERENCES) { remoteApi.pushPreferences(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushRoutines() {
        val local = localStore.dirtyRoutines()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.ROUTINES.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteRoutines(runSyncStep("pushing", SyncScope.ROUTINES) { remoteApi.pushRoutines(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushTasks() {
        val local = localStore.dirtyTasks()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.TASKS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteTasks(runSyncStep("pushing", SyncScope.TASKS) { remoteApi.pushTasks(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushAlarms() {
        val local = localStore.dirtyAlarms()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.ALARMS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteAlarms(runSyncStep("pushing", SyncScope.ALARMS) { remoteApi.pushAlarms(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushTaskEvents() {
        val local = localStore.dirtyTaskEvents()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.TASK_EVENTS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteTaskEvents(runSyncStep("pushing", SyncScope.TASK_EVENTS) { remoteApi.pushTaskEvents(local) })
        }
    }

    private suspend fun pushPomodoroSessions() {
        val local = localStore.dirtyPomodoroSessions()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.POMODORO_SESSIONS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemotePomodoroSessions(runSyncStep("pushing", SyncScope.POMODORO_SESSIONS) { remoteApi.pushPomodoroSessions(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceAccounts() {
        val local = localStore.dirtyFinanceAccounts()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_ACCOUNTS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteFinanceAccounts(runSyncStep("pushing", SyncScope.FINANCE_ACCOUNTS) { remoteApi.pushFinanceAccounts(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceExchangeRates() {
        val local = localStore.dirtyFinanceExchangeRates()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_EXCHANGE_RATES.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteFinanceExchangeRates(runSyncStep("pushing", SyncScope.FINANCE_EXCHANGE_RATES) { remoteApi.pushFinanceExchangeRates(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceClearingGroups() {
        val local = localStore.dirtyFinanceClearingGroups()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_CLEARING_GROUPS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteFinanceClearingGroups(runSyncStep("pushing", SyncScope.FINANCE_CLEARING_GROUPS) { remoteApi.pushFinanceClearingGroups(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceTransactions() {
        val local = localStore.dirtyFinanceTransactions()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_TRANSACTIONS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteFinanceTransactions(runSyncStep("pushing", SyncScope.FINANCE_TRANSACTIONS) { remoteApi.pushFinanceTransactions(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceSplits() {
        val local = localStore.dirtyFinanceSplits()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_SPLITS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteFinanceSplits(runSyncStep("pushing", SyncScope.FINANCE_SPLITS) { remoteApi.pushFinanceSplits(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceSplitClearingGroups() {
        val local = localStore.dirtyFinanceSplitClearingGroups()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_SPLIT_CLEARING_GROUPS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteFinanceSplitClearingGroups(runSyncStep("pushing", SyncScope.FINANCE_SPLIT_CLEARING_GROUPS) { remoteApi.pushFinanceSplitClearingGroups(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceAmortizationPlans() {
        val local = localStore.dirtyFinanceAmortizationPlans()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_AMORTIZATION_PLANS.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteFinanceAmortizationPlans(runSyncStep("pushing", SyncScope.FINANCE_AMORTIZATION_PLANS) { remoteApi.pushFinanceAmortizationPlans(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceBudgetRules() {
        val local = localStore.dirtyFinanceBudgetRules()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_BUDGET_RULES.wireName, "itemCount" to local.size.toString()))
            localStore.mergeRemoteFinanceBudgetRules(runSyncStep("pushing", SyncScope.FINANCE_BUDGET_RULES) { remoteApi.pushFinanceBudgetRules(local) }, clearAccepted = true)
        }
    }

    private suspend fun <T> pullPaged(
        scope: SyncScope,
        pull: suspend (cursor: Long, limit: Int) -> dev.tireless.abun.sync.PullResponse<T>,
        merge: (items: List<T>) -> Unit,
    ) {
        var cursor = localStore.syncCursor(scope)
        do {
            logger.info(message = "sync.pull.started", context = mapOf("scope" to scope.wireName, "cursor" to cursor.toString()))
            val response = runSyncStep("pulling", scope) { pull(cursor, 500) }
            if (response.items.isNotEmpty()) {
                merge(response.items)
                cursor = response.nextCursor
                localStore.updateSyncCursor(scope, cursor)
            }
        } while (response.hasMore)
    }

    private suspend fun <T> runSyncStep(
        action: String,
        scope: SyncScope,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: Throwable) {
        throw SyncOperationException(action = action, scope = scope, cause = error)
    }
}

class SyncOperationException(
    val action: String,
    val scope: SyncScope,
    cause: Throwable,
) : Exception(cause.message ?: "Sync failed", cause)
