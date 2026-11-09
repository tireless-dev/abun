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

    private suspend fun pushPreferences() = pushPaged(
        SyncScope.PREFERENCES,
        localStore.preferenceStore::dirtyPreferences,
        remoteApi::pushPreferences,
        { localStore.preferenceStore.mergeRemotePreferences(it, clearAccepted = true) },
    )

    private suspend fun pushRoutines() = pushPaged(
        SyncScope.ROUTINES,
        localStore.taskSyncStore::dirtyRoutines,
        remoteApi::pushRoutines,
        { localStore.taskSyncStore.mergeRemoteRoutines(it, clearAccepted = true) },
    )

    private suspend fun pushTasks() = pushPaged(
        SyncScope.TASKS,
        localStore.taskSyncStore::dirtyTasks,
        remoteApi::pushTasks,
        { localStore.taskSyncStore.mergeRemoteTasks(it, clearAccepted = true) },
    )

    private suspend fun pushAlarms() = pushPaged(
        SyncScope.ALARMS,
        localStore.taskSyncStore::dirtyAlarms,
        remoteApi::pushAlarms,
        { localStore.taskSyncStore.mergeRemoteAlarms(it, clearAccepted = true) },
    )

    private suspend fun pushTaskEvents() = pushPaged(
        SyncScope.TASK_EVENTS,
        localStore.taskSyncStore::dirtyTaskEvents,
        remoteApi::pushTaskEvents,
        localStore.taskSyncStore::mergeRemoteTaskEvents,
    )

    private suspend fun pushPomodoroSessions() = pushPaged(
        SyncScope.POMODORO_SESSIONS,
        localStore.taskSyncStore::dirtyPomodoroSessions,
        remoteApi::pushPomodoroSessions,
        { localStore.taskSyncStore.mergeRemotePomodoroSessions(it, clearAccepted = true) },
    )

    private suspend fun pushFinanceAccounts() = pushPaged(
        SyncScope.FINANCE_ACCOUNTS,
        localStore.financeSyncStore::dirtyFinanceAccounts,
        remoteApi::pushFinanceAccounts,
        { localStore.financeSyncStore.mergeRemoteFinanceAccounts(it, clearAccepted = true) },
    )

    private suspend fun pushFinanceExchangeRates() = pushPaged(
        SyncScope.FINANCE_EXCHANGE_RATES,
        localStore.financeSyncStore::dirtyFinanceExchangeRates,
        remoteApi::pushFinanceExchangeRates,
        { localStore.financeSyncStore.mergeRemoteFinanceExchangeRates(it, clearAccepted = true) },
    )

    private suspend fun pushFinanceClearingGroups() = pushPaged(
        SyncScope.FINANCE_CLEARING_GROUPS,
        localStore.financeSyncStore::dirtyFinanceClearingGroups,
        remoteApi::pushFinanceClearingGroups,
        { localStore.financeSyncStore.mergeRemoteFinanceClearingGroups(it, clearAccepted = true) },
    )

    private suspend fun pushFinanceTransactions() = pushPaged(
        SyncScope.FINANCE_TRANSACTIONS,
        localStore.financeSyncStore::dirtyFinanceTransactions,
        remoteApi::pushFinanceTransactions,
        { localStore.financeSyncStore.mergeRemoteFinanceTransactions(it, clearAccepted = true) },
    )

    private suspend fun pushFinanceSplits() = pushPaged(
        SyncScope.FINANCE_SPLITS,
        localStore.financeSyncStore::dirtyFinanceSplits,
        remoteApi::pushFinanceSplits,
        { localStore.financeSyncStore.mergeRemoteFinanceSplits(it, clearAccepted = true) },
    )

    private suspend fun pushFinanceSplitClearingGroups() = pushPaged(
        SyncScope.FINANCE_SPLIT_CLEARING_GROUPS,
        localStore.financeSyncStore::dirtyFinanceSplitClearingGroups,
        remoteApi::pushFinanceSplitClearingGroups,
        { localStore.financeSyncStore.mergeRemoteFinanceSplitClearingGroups(it, clearAccepted = true) },
    )

    private suspend fun pushFinanceAmortizationPlans() = pushPaged(
        SyncScope.FINANCE_AMORTIZATION_PLANS,
        localStore.financeSyncStore::dirtyFinanceAmortizationPlans,
        remoteApi::pushFinanceAmortizationPlans,
        { localStore.financeSyncStore.mergeRemoteFinanceAmortizationPlans(it, clearAccepted = true) },
    )

    private suspend fun pushFinanceBudgetRules() = pushPaged(
        SyncScope.FINANCE_BUDGET_RULES,
        localStore.financeSyncStore::dirtyFinanceBudgetRules,
        remoteApi::pushFinanceBudgetRules,
        { localStore.financeSyncStore.mergeRemoteFinanceBudgetRules(it, clearAccepted = true) },
    )

    private suspend fun <T> pushPaged(
        scope: SyncScope,
        fetchDirty: () -> List<T>,
        push: suspend (items: List<T>) -> List<T>,
        merge: (items: List<T>) -> Unit,
        chunkSize: Int = 50,
    ) {
        while (true) {
            val dirty = fetchDirty()
            if (dirty.isEmpty()) break

            val batch = dirty.take(chunkSize)
            logger.info(
                message = "sync.push.started",
                context = mapOf(
                    "scope" to scope.wireName,
                    "itemCount" to batch.size.toString(),
                    "remaining" to dirty.size.toString(),
                ),
            )

            val remoteItems = runSyncStep("pushing", scope) { push(batch) }
            merge(remoteItems)

            val remainingDirty = fetchDirty()
            if (remainingDirty.size >= dirty.size) {
                logger.error(
                    message = "sync.push.no_progress",
                    context = mapOf(
                        "scope" to scope.wireName,
                        "batchSize" to batch.size.toString(),
                        "previousRemaining" to dirty.size.toString(),
                        "currentRemaining" to remainingDirty.size.toString(),
                    ),
                )
                break
            }
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
            }
            if (response.nextCursor > cursor) {
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
        logger.error(
            message = "sync.step.failed",
            context = mapOf("action" to action, "scope" to scope.wireName),
            throwable = error,
        )
        throw SyncOperationException(action = action, scope = scope, cause = error)
    }
}

class SyncOperationException(
    val action: String,
    val scope: SyncScope,
    cause: Throwable,
) : Exception(cause.message ?: "Sync failed", cause)
