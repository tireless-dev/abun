package dev.tireless.abun.app

class SyncEngine(
    private val localStore: LocalStore,
    private val remoteApi: SyncRemoteApi,
) {
    suspend fun syncNow() {
        pullRoutines()
        pullTasks()
        pullAlarms()
        pullTaskEvents()
        pushRoutines()
        pushTasks()
        pushAlarms()
        pushTaskEvents()
    }

    private suspend fun pullRoutines() = pullPaged(SyncScope.ROUTINES, remoteApi::pullRoutines, localStore::mergeRemoteRoutines)
    private suspend fun pullTasks() = pullPaged(SyncScope.TASKS, remoteApi::pullTasks, localStore::mergeRemoteTasks)
    private suspend fun pullAlarms() = pullPaged(SyncScope.ALARMS, remoteApi::pullAlarms, localStore::mergeRemoteAlarms)
    private suspend fun pullTaskEvents() = pullPaged(SyncScope.TASK_EVENTS, remoteApi::pullTaskEvents, localStore::mergeRemoteTaskEvents)

    private suspend fun pushRoutines() {
        val local = localStore.dirtyRoutines()
        if (local.isNotEmpty()) localStore.mergeRemoteRoutines(remoteApi.pushRoutines(local), clearAccepted = true)
    }

    private suspend fun pushTasks() {
        val local = localStore.dirtyTasks()
        if (local.isNotEmpty()) localStore.mergeRemoteTasks(remoteApi.pushTasks(local), clearAccepted = true)
    }

    private suspend fun pushAlarms() {
        val local = localStore.dirtyAlarms()
        if (local.isNotEmpty()) localStore.mergeRemoteAlarms(remoteApi.pushAlarms(local), clearAccepted = true)
    }

    private suspend fun pushTaskEvents() {
        val local = localStore.dirtyTaskEvents()
        if (local.isNotEmpty()) localStore.mergeRemoteTaskEvents(remoteApi.pushTaskEvents(local))
    }

    private suspend fun <T> pullPaged(
        scope: SyncScope,
        pull: suspend (cursor: Long, limit: Int) -> dev.tireless.abun.sync.PullResponse<T>,
        merge: (items: List<T>) -> Unit,
    ) {
        var cursor = localStore.syncCursor(scope)
        do {
            val response = pull(cursor, 500)
            if (response.items.isNotEmpty()) {
                merge(response.items)
                cursor = response.nextCursor
                localStore.updateSyncCursor(scope, cursor)
            }
        } while (response.hasMore)
    }
}
