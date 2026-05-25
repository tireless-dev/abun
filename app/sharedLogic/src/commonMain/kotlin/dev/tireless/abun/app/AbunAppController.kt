package dev.tireless.abun.app

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    init {
        refresh()
    }

    fun createTask(title: String) {
        if (title.isBlank()) return
        store.createTask(title.trim(), state.value.selectedDate)
        refresh()
    }

    fun progressTask(taskId: String) {
        store.progressTask(taskId, state.value.selectedDate)
        refresh()
    }

    fun completeTask(taskId: String) {
        store.completeTask(taskId, state.value.selectedDate)
        refresh()
    }

    fun deleteTask(taskId: String) {
        store.deleteTask(taskId)
        refresh()
    }

    fun syncNow() {
        scope.launch {
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
        fun create(dependencies: AppDependencies): AbunAppController = AbunAppController(dependencies)
    }
}
