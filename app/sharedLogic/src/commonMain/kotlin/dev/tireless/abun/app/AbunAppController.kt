package dev.tireless.abun.app

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AbunAppController(
    dependencies: AppDependencies,
) {
    private val timeProvider = dependencies.timeProvider
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = LocalStore(
        database = createDatabase(dependencies.databaseDriverFactory),
        timeProvider = dependencies.timeProvider,
        idGenerator = dependencies.idGenerator,
        clock = createHybridClock(dependencies.nodeIdProvider, dependencies.timeProvider),
    )
    private val syncEngine = SyncEngine(
        localStore = store,
        remoteApi = SyncRemoteApi(
            baseUrl = dependencies.serverBaseUrl,
            client = dependencies.httpClient,
            authProvider = dependencies.authProvider,
        ),
    )

    private val _state = MutableStateFlow(AppUiState(selectedDate = dependencies.timeProvider.today().toString()))
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var scheduledSyncJob: Job? = null
    private var isSyncing = false
    private var syncRequestedWhileRunning = false

    init {
        refresh()
        requestSync(immediate = true)
    }

    fun createTask(title: String) {
        if (title.isBlank()) return
        store.createTask(title.trim(), state.value.selectedDate)
        refresh()
        requestSync()
    }

    fun progressTask(taskId: String) {
        store.progressTask(taskId, state.value.selectedDate)
        refresh()
        requestSync()
    }

    fun completeTask(taskId: String) {
        store.completeTask(taskId, state.value.selectedDate)
        refresh()
        requestSync()
    }

    fun deleteTask(taskId: String) {
        store.deleteTask(taskId)
        refresh()
        requestSync()
    }

    fun syncNow() {
        requestSync(immediate = true)
    }

    private fun requestSync(immediate: Boolean = false) {
        if (isSyncing) {
            syncRequestedWhileRunning = true
            return
        }
        scheduledSyncJob?.cancel()
        scheduledSyncJob = scope.launch {
            if (!immediate) {
                delay(AUTO_SYNC_DEBOUNCE_MS)
            }
            runSyncLoop()
        }
    }

    private suspend fun runSyncLoop() {
        if (isSyncing) {
            syncRequestedWhileRunning = true
            return
        }
        isSyncing = true
        try {
            do {
                syncRequestedWhileRunning = false
                _state.value = _state.value.copy(syncState = _state.value.syncState.copy(isSyncing = true, errorMessage = null))
                runCatching {
                    syncEngine.syncNow()
                }.onSuccess {
                    refresh(lastSyncedAt = timeProvider.nowEpochMillis().let(::epochMillisToIsoString))
                }.onFailure {
                    _state.value = _state.value.copy(
                        syncState = _state.value.syncState.copy(isSyncing = false, errorMessage = it.message ?: "Sync failed"),
                    )
                }
            } while (syncRequestedWhileRunning)
        } finally {
            isSyncing = false
            scheduledSyncJob = null
        }
    }

    fun refresh(lastSyncedAt: String? = _state.value.syncState.lastSyncedAt) {
        _state.value = _state.value.copy(
            tasks = store.allTasks(),
            journalEntries = store.journal(_state.value.selectedDate),
            syncState = _state.value.syncState.copy(isSyncing = false, lastSyncedAt = lastSyncedAt),
        )
    }

    companion object {
        private const val AUTO_SYNC_DEBOUNCE_MS = 750L

        fun create(dependencies: AppDependencies): AbunAppController = AbunAppController(dependencies)
    }
}
