package dev.tireless.abun.app

import dev.tireless.abun.db.AbunDatabase

internal data class MutableSyncRow<T>(
    val entity: T,
    val hlcMap: Map<String, String>,
    val dirtyFields: List<String>,
)

class LocalStore(
    internal val database: AbunDatabase,
    internal val timeProvider: TimeProvider,
    internal val idGenerator: IdGenerator,
    internal val clock: dev.tireless.abun.sync.HybridLogicalClock,
) {
    internal val queries = database.abunDatabaseQueries

    val preferenceStore = PreferenceStore(this)
    val taskStore = TaskStore(this)
    val routineAlarmStore = RoutineAlarmStore(this)
    val pomodoroStore = PomodoroStore(this)
    val taskSyncStore = TaskSyncStore(this)
    val financeAccountStore = FinanceAccountStore(this)
    val financeLedgerStore = FinanceLedgerStore(this)
    val financeViewDeriver = FinanceViewDeriver(this)
    val financeSyncStore = FinanceSyncStore(this)

    init {
        SyncScope.entries.forEach { queries.initSyncState(it.wireName) }
        preferenceStore.migrateLegacyPreferencesIfNeeded()
        if (AppModule.FINANCE in preferenceStore.preferences().enabledModules) {
            financeAccountStore.ensureDefaultFinanceAccounts()
        }
    }

    fun syncCursor(scope: SyncScope): Long = queries.selectSyncState(scope.wireName).executeAsOne().last_server_version

    fun updateSyncCursor(scope: SyncScope, version: Long) {
        queries.upsertSyncState(scope.wireName, version)
    }

    fun hasPreviouslySyncedData(): Boolean =
        queries.hasSyncedRecords().executeAsOne() != 0L

    fun resetForAccountSwitch() {
        database.transaction {
            queries.deleteAllFinanceSplitClearingGroups()
            queries.deleteAllFinanceSplits()
            queries.deleteAllFinanceTransactions()
            queries.deleteAllFinanceAmortizationPlans()
            queries.deleteAllFinanceClearingGroups()
            queries.deleteAllFinanceExchangeRates()
            queries.deleteAllFinanceBudgetRules()
            queries.deleteAllFinanceAccounts()
            queries.deleteAllPomodoroSessions()
            queries.deleteAllAlarms()
            queries.deleteAllTaskEvents()
            queries.deleteAllTasks()
            queries.deleteAllRoutines()
            queries.deleteAllPreferences()
            queries.deleteAllAppPreferences()
            SyncScope.entries.forEach { queries.upsertSyncState(it.wireName, 0) }
        }
        if (AppModule.FINANCE in preferenceStore.preferences().enabledModules) {
            financeAccountStore.ensureDefaultFinanceAccounts()
        }
    }
}
