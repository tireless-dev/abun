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
        pushPreferences()
        pushRoutines()
        pushTasks()
        pushAlarms()
        pushTaskEvents()
        pushPomodoroSessions()
        logger.info(message = "sync.completed")
    }

    private suspend fun pullPreferences() = pullPaged(SyncScope.PREFERENCES, remoteApi::pullPreferences, localStore::mergeRemotePreferences)
    private suspend fun pullRoutines() = pullPaged(SyncScope.ROUTINES, remoteApi::pullRoutines, localStore::mergeRemoteRoutines)
    private suspend fun pullTasks() = pullPaged(SyncScope.TASKS, remoteApi::pullTasks, localStore::mergeRemoteTasks)
    private suspend fun pullAlarms() = pullPaged(SyncScope.ALARMS, remoteApi::pullAlarms, localStore::mergeRemoteAlarms)
    private suspend fun pullTaskEvents() = pullPaged(SyncScope.TASK_EVENTS, remoteApi::pullTaskEvents, localStore::mergeRemoteTaskEvents)
    private suspend fun pullPomodoroSessions() = pullPaged(SyncScope.POMODORO_SESSIONS, remoteApi::pullPomodoroSessions, localStore::mergeRemotePomodoroSessions)

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
