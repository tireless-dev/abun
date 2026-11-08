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

    private suspend fun pullPreferences() = pullPaged(SyncScope.PREFERENCES, remoteApi::pullPreferences, localStore.preferenceStore::mergeRemotePreferences)
    private suspend fun pullRoutines() = pullPaged(SyncScope.ROUTINES, remoteApi::pullRoutines, localStore.taskSyncStore::mergeRemoteRoutines)
    private suspend fun pullTasks() = pullPaged(SyncScope.TASKS, remoteApi::pullTasks, localStore.taskSyncStore::mergeRemoteTasks)
    private suspend fun pullAlarms() = pullPaged(SyncScope.ALARMS, remoteApi::pullAlarms, localStore.taskSyncStore::mergeRemoteAlarms)
    private suspend fun pullTaskEvents() = pullPaged(SyncScope.TASK_EVENTS, remoteApi::pullTaskEvents, localStore.taskSyncStore::mergeRemoteTaskEvents)
    private suspend fun pullPomodoroSessions() = pullPaged(SyncScope.POMODORO_SESSIONS, remoteApi::pullPomodoroSessions, localStore.taskSyncStore::mergeRemotePomodoroSessions)
    private suspend fun pullFinanceAccounts() = pullPaged(SyncScope.FINANCE_ACCOUNTS, remoteApi::pullFinanceAccounts, localStore.financeSyncStore::mergeRemoteFinanceAccounts)
    private suspend fun pullFinanceExchangeRates() = pullPaged(SyncScope.FINANCE_EXCHANGE_RATES, remoteApi::pullFinanceExchangeRates, localStore.financeSyncStore::mergeRemoteFinanceExchangeRates)
    private suspend fun pullFinanceClearingGroups() = pullPaged(SyncScope.FINANCE_CLEARING_GROUPS, remoteApi::pullFinanceClearingGroups, localStore.financeSyncStore::mergeRemoteFinanceClearingGroups)
    private suspend fun pullFinanceTransactions() = pullPaged(SyncScope.FINANCE_TRANSACTIONS, remoteApi::pullFinanceTransactions, localStore.financeSyncStore::mergeRemoteFinanceTransactions)
    private suspend fun pullFinanceSplits() = pullPaged(SyncScope.FINANCE_SPLITS, remoteApi::pullFinanceSplits, localStore.financeSyncStore::mergeRemoteFinanceSplits)
    private suspend fun pullFinanceSplitClearingGroups() = pullPaged(SyncScope.FINANCE_SPLIT_CLEARING_GROUPS, remoteApi::pullFinanceSplitClearingGroups, localStore.financeSyncStore::mergeRemoteFinanceSplitClearingGroups)
    private suspend fun pullFinanceAmortizationPlans() = pullPaged(SyncScope.FINANCE_AMORTIZATION_PLANS, remoteApi::pullFinanceAmortizationPlans, localStore.financeSyncStore::mergeRemoteFinanceAmortizationPlans)
    private suspend fun pullFinanceBudgetRules() = pullPaged(SyncScope.FINANCE_BUDGET_RULES, remoteApi::pullFinanceBudgetRules, localStore.financeSyncStore::mergeRemoteFinanceBudgetRules)

    private suspend fun pushPreferences() {
        val local = localStore.preferenceStore.dirtyPreferences()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.PREFERENCES.wireName, "itemCount" to local.size.toString()))
            localStore.preferenceStore.mergeRemotePreferences(runSyncStep("pushing", SyncScope.PREFERENCES) { remoteApi.pushPreferences(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushRoutines() {
        val local = localStore.taskSyncStore.dirtyRoutines()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.ROUTINES.wireName, "itemCount" to local.size.toString()))
            localStore.taskSyncStore.mergeRemoteRoutines(runSyncStep("pushing", SyncScope.ROUTINES) { remoteApi.pushRoutines(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushTasks() {
        val local = localStore.taskSyncStore.dirtyTasks()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.TASKS.wireName, "itemCount" to local.size.toString()))
            localStore.taskSyncStore.mergeRemoteTasks(runSyncStep("pushing", SyncScope.TASKS) { remoteApi.pushTasks(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushAlarms() {
        val local = localStore.taskSyncStore.dirtyAlarms()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.ALARMS.wireName, "itemCount" to local.size.toString()))
            localStore.taskSyncStore.mergeRemoteAlarms(runSyncStep("pushing", SyncScope.ALARMS) { remoteApi.pushAlarms(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushTaskEvents() {
        val local = localStore.taskSyncStore.dirtyTaskEvents()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.TASK_EVENTS.wireName, "itemCount" to local.size.toString()))
            localStore.taskSyncStore.mergeRemoteTaskEvents(runSyncStep("pushing", SyncScope.TASK_EVENTS) { remoteApi.pushTaskEvents(local) })
        }
    }

    private suspend fun pushPomodoroSessions() {
        val local = localStore.taskSyncStore.dirtyPomodoroSessions()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.POMODORO_SESSIONS.wireName, "itemCount" to local.size.toString()))
            localStore.taskSyncStore.mergeRemotePomodoroSessions(runSyncStep("pushing", SyncScope.POMODORO_SESSIONS) { remoteApi.pushPomodoroSessions(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceAccounts() {
        val local = localStore.financeSyncStore.dirtyFinanceAccounts()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_ACCOUNTS.wireName, "itemCount" to local.size.toString()))
            localStore.financeSyncStore.mergeRemoteFinanceAccounts(runSyncStep("pushing", SyncScope.FINANCE_ACCOUNTS) { remoteApi.pushFinanceAccounts(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceExchangeRates() {
        val local = localStore.financeSyncStore.dirtyFinanceExchangeRates()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_EXCHANGE_RATES.wireName, "itemCount" to local.size.toString()))
            localStore.financeSyncStore.mergeRemoteFinanceExchangeRates(runSyncStep("pushing", SyncScope.FINANCE_EXCHANGE_RATES) { remoteApi.pushFinanceExchangeRates(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceClearingGroups() {
        val local = localStore.financeSyncStore.dirtyFinanceClearingGroups()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_CLEARING_GROUPS.wireName, "itemCount" to local.size.toString()))
            localStore.financeSyncStore.mergeRemoteFinanceClearingGroups(runSyncStep("pushing", SyncScope.FINANCE_CLEARING_GROUPS) { remoteApi.pushFinanceClearingGroups(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceTransactions() {
        val local = localStore.financeSyncStore.dirtyFinanceTransactions()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_TRANSACTIONS.wireName, "itemCount" to local.size.toString()))
            localStore.financeSyncStore.mergeRemoteFinanceTransactions(runSyncStep("pushing", SyncScope.FINANCE_TRANSACTIONS) { remoteApi.pushFinanceTransactions(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceSplits() {
        val local = localStore.financeSyncStore.dirtyFinanceSplits()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_SPLITS.wireName, "itemCount" to local.size.toString()))
            localStore.financeSyncStore.mergeRemoteFinanceSplits(runSyncStep("pushing", SyncScope.FINANCE_SPLITS) { remoteApi.pushFinanceSplits(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceSplitClearingGroups() {
        val local = localStore.financeSyncStore.dirtyFinanceSplitClearingGroups()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_SPLIT_CLEARING_GROUPS.wireName, "itemCount" to local.size.toString()))
            localStore.financeSyncStore.mergeRemoteFinanceSplitClearingGroups(runSyncStep("pushing", SyncScope.FINANCE_SPLIT_CLEARING_GROUPS) { remoteApi.pushFinanceSplitClearingGroups(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceAmortizationPlans() {
        val local = localStore.financeSyncStore.dirtyFinanceAmortizationPlans()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_AMORTIZATION_PLANS.wireName, "itemCount" to local.size.toString()))
            localStore.financeSyncStore.mergeRemoteFinanceAmortizationPlans(runSyncStep("pushing", SyncScope.FINANCE_AMORTIZATION_PLANS) { remoteApi.pushFinanceAmortizationPlans(local) }, clearAccepted = true)
        }
    }

    private suspend fun pushFinanceBudgetRules() {
        val local = localStore.financeSyncStore.dirtyFinanceBudgetRules()
        if (local.isNotEmpty()) {
            logger.info(message = "sync.push.started", context = mapOf("scope" to SyncScope.FINANCE_BUDGET_RULES.wireName, "itemCount" to local.size.toString()))
            localStore.financeSyncStore.mergeRemoteFinanceBudgetRules(runSyncStep("pushing", SyncScope.FINANCE_BUDGET_RULES) { remoteApi.pushFinanceBudgetRules(local) }, clearAccepted = true)
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
