package dev.tireless.abun.app

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
    private val authProvider = dependencies.authProvider as? MutableAuthProvider
        ?: error("App requires MutableAuthProvider for onboarding/auth flow")
    private val store = LocalStore(
        database = createDatabase(dependencies.databaseDriverFactory),
        timeProvider = dependencies.timeProvider,
        idGenerator = dependencies.idGenerator,
        clock = createHybridClock(dependencies.nodeIdProvider, dependencies.timeProvider),
    )
    private val remoteApi = SyncRemoteApi(
        baseUrl = dependencies.serverBaseUrl,
        client = dependencies.httpClient,
        authProvider = dependencies.authProvider,
    )
    private val syncEngine = SyncEngine(
        localStore = store,
        remoteApi = remoteApi,
    )

    private val _state = MutableStateFlow(
        AppUiState(selectedDate = dependencies.timeProvider.today().toString()),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var scheduledSyncJob: Job? = null
    private var isSyncing = false
    private var syncRequestedWhileRunning = false

    init {
        authProvider.updateToken(null)
        refresh()
    }

    fun skipLogin() {
        _state.value = _state.value.copy(
            auth = _state.value.auth.copy(
                showGuide = false,
                mode = AuthMode.GUEST,
                errorMessage = null,
            ),
            syncState = _state.value.syncState.copy(syncReady = true),
        )
    }

    fun updateLoginEmail(email: String) {
        _state.value = _state.value.copy(auth = _state.value.auth.copy(email = email, errorMessage = null))
    }

    fun requestEmailOtp() {
        val email = _state.value.auth.email.trim()
        if (email.isBlank()) {
            _state.value = _state.value.copy(auth = _state.value.auth.copy(errorMessage = "Email is required"))
            return
        }
        scope.launch {
            _state.value = _state.value.copy(auth = _state.value.auth.copy(isSubmitting = true, errorMessage = null))
            runCatching { remoteApi.requestOtp(email) }
                .onSuccess {
                    _state.value = _state.value.copy(auth = _state.value.auth.copy(isSubmitting = false, otpRequested = true))
                }
                .onFailure {
                    _state.value = _state.value.copy(auth = _state.value.auth.copy(isSubmitting = false, errorMessage = it.message ?: "Failed to request OTP"))
                }
        }
    }

    fun verifyEmailOtp(code: String) {
        val email = _state.value.auth.email.trim()
        if (email.isBlank() || code.isBlank()) {
            _state.value = _state.value.copy(auth = _state.value.auth.copy(errorMessage = "Email and OTP are required"))
            return
        }
        scope.launch {
            _state.value = _state.value.copy(auth = _state.value.auth.copy(isSubmitting = true, errorMessage = null))
            runCatching { remoteApi.verifyOtp(email, code) }
                .onSuccess { response ->
                    authProvider.updateToken(response.accessToken)
                    _state.value = _state.value.copy(
                        auth = _state.value.auth.copy(
                            showGuide = false,
                            mode = AuthMode.AUTHENTICATED,
                            isSubmitting = false,
                            otpRequested = false,
                            errorMessage = null,
                        ),
                        syncState = _state.value.syncState.copy(syncReady = true),
                    )
                    requestSync(immediate = true)
                }
                .onFailure {
                    _state.value = _state.value.copy(auth = _state.value.auth.copy(isSubmitting = false, errorMessage = it.message ?: "OTP verification failed"))
                }
        }
    }

    fun selectTab(tab: AppTab) {
        _state.value = _state.value.copy(selectedTab = tab)
    }

    fun selectTaskSubTab(tab: TaskSubTab) {
        _state.value = _state.value.copy(selectedTaskSubTab = tab, selectedTab = AppTab.TASKS)
    }

    fun selectTaskFilter(filter: TaskListFilter) {
        _state.value = _state.value.copy(selectedTaskFilter = filter, selectedTab = AppTab.TASKS, selectedTaskSubTab = TaskSubTab.TASKS)
    }

    fun openPreferences() {
        _state.value = _state.value.copy(isPreferencesOpen = true)
    }

    fun closePreferences() {
        _state.value = _state.value.copy(isPreferencesOpen = false)
    }

    fun openPomodoroDialog() {
        _state.value = _state.value.copy(isPomodoroDialogOpen = true)
    }

    fun closePomodoroDialog() {
        _state.value = _state.value.copy(isPomodoroDialogOpen = false)
    }

    fun createTask(title: String) {
        val preparedTitle = applyTaskDefaults(title)
        if (preparedTitle == null) return
        store.createTask(preparedTitle, state.value.selectedDate)
        refresh()
        requestSync()
    }

    fun progressTask(taskId: String, note: String? = null) {
        store.progressTask(taskId, state.value.selectedDate, note)
        refresh()
        requestSync()
    }

    fun completeTask(taskId: String, note: String? = null) {
        store.completeTask(taskId, state.value.selectedDate, note)
        refresh()
        requestSync()
    }

    fun postponeTask(
        taskId: String,
        startNotBefore: String? = null,
        endNotAfter: String? = null,
        estimatedDuration: String? = null,
        note: String? = null,
    ) {
        store.postponeTask(taskId, state.value.selectedDate, startNotBefore, endNotAfter, estimatedDuration, note)
        refresh()
        requestSync()
    }

    fun updateTask(
        taskId: String,
        title: String,
        detail: String? = null,
        parentId: String? = null,
        startNotBefore: String? = null,
        endNotAfter: String? = null,
        estimatedDuration: String? = null,
    ) {
        if (title.isBlank()) return
        store.updateTask(taskId, title, detail, parentId, startNotBefore, endNotAfter, estimatedDuration)
        refresh()
        requestSync()
    }

    fun cancelTask(taskId: String, note: String? = null) {
        store.cancelTask(taskId, state.value.selectedDate, note)
        refresh()
        requestSync()
    }

    fun skipTask(taskId: String, note: String? = null) {
        store.skipTask(taskId, state.value.selectedDate, note)
        refresh()
        requestSync()
    }

    fun deleteTask(taskId: String) {
        store.deleteTask(taskId, state.value.selectedDate)
        refresh()
        requestSync()
    }

    fun taskHistory(taskId: String): List<JournalEntryView> = store.taskHistory(taskId, state.value.preferences)

    fun createRoutine(
        templateTitle: String,
        templateDetail: String?,
        recurrenceRule: String,
        defaultStartNotBefore: String?,
        defaultEstimatedDuration: String?,
    ) {
        if (templateTitle.isBlank() || recurrenceRule.isBlank()) return
        store.createRoutine(
            templateTitle = templateTitle,
            templateDetail = templateDetail,
            recurrenceRule = recurrenceRule,
            defaultStartNotBefore = defaultStartNotBefore,
            defaultEstimatedDuration = defaultEstimatedDuration,
        )
        refresh()
        requestSync()
    }

    fun updateRoutine(
        routineId: String,
        templateTitle: String,
        templateDetail: String?,
        recurrenceRule: String,
        defaultStartNotBefore: String?,
        defaultEstimatedDuration: String?,
    ) {
        if (templateTitle.isBlank() || recurrenceRule.isBlank()) return
        store.updateRoutine(
            routineId = routineId,
            templateTitle = templateTitle,
            templateDetail = templateDetail,
            recurrenceRule = recurrenceRule,
            defaultStartNotBefore = defaultStartNotBefore,
            defaultEstimatedDuration = defaultEstimatedDuration,
        )
        refresh()
        requestSync()
    }

    fun toggleRoutineActive(routineId: String) {
        store.toggleRoutineActive(routineId)
        refresh()
        requestSync()
    }

    fun deleteRoutine(routineId: String) {
        store.deleteRoutine(routineId)
        refresh()
        requestSync()
    }

    fun runRoutine(routineId: String) {
        val today = timeProvider.today().toString()
        store.runRoutine(routineId, today)
        refresh()
        requestSync()
    }

    fun createAlarm(taskId: String, triggerTimeIso: String) {
        if (taskId.isBlank() || triggerTimeIso.isBlank()) return
        runCatching { store.createAlarm(taskId, triggerTimeIso) }
            .onSuccess {
                refresh()
                requestSync()
            }
    }

    fun updateAlarm(alarmId: String, triggerTimeIso: String) {
        if (triggerTimeIso.isBlank()) return
        runCatching { store.updateAlarm(alarmId, triggerTimeIso) }
            .onSuccess {
                refresh()
                requestSync()
            }
    }

    fun toggleAlarmActive(alarmId: String) {
        store.toggleAlarmActive(alarmId)
        refresh()
        requestSync()
    }

    fun deleteAlarm(alarmId: String) {
        store.deleteAlarm(alarmId)
        refresh()
        requestSync()
    }

    fun startPomodoro(taskId: String? = null, phase: PomodoroPhase = PomodoroPhase.FOCUS) {
        store.startPomodoroSession(taskId, phase, state.value.preferences)
        _state.value = _state.value.copy(isPomodoroDialogOpen = true)
        refresh()
        requestSync()
    }

    fun completePomodoro(sessionId: String, note: String?, taskUpdate: PomodoroTaskUpdate) {
        store.completePomodoroSession(sessionId, note, taskUpdate, state.value.selectedDate)
        _state.value = _state.value.copy(isPomodoroDialogOpen = false)
        refresh()
        requestSync()
    }

    fun cancelPomodoro(sessionId: String, note: String?) {
        store.cancelPomodoroSession(sessionId, note)
        _state.value = _state.value.copy(isPomodoroDialogOpen = false)
        refresh()
        requestSync()
    }

    fun updatePreferences(
        titlePrefix: String,
        defaultAlarmLeadMinutes: Int,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        timezoneOverride: String,
        dateFormat: DateFormatPreference,
        rolloverTime: String,
    ) {
        store.updatePreferences(
            titlePrefix = titlePrefix,
            defaultAlarmLeadMinutes = defaultAlarmLeadMinutes,
            focusMinutes = focusMinutes,
            shortBreakMinutes = shortBreakMinutes,
            longBreakMinutes = longBreakMinutes,
            timezoneOverride = timezoneOverride,
            dateFormat = dateFormat,
            rolloverTime = rolloverTime,
        )
        refresh()
        requestSync()
    }

    fun syncNow() {
        if (_state.value.auth.mode != AuthMode.AUTHENTICATED) return
        requestSync(immediate = true)
    }

    fun suggestedAlarmTriggerTimeIso(): String {
        val leadMinutes = state.value.preferences.defaultAlarmLeadMinutes
        val now = timeProvider.nowEpochMillis() + leadMinutes * 60_000L
        return epochMillisToIsoString(now)
    }

    private fun applyTaskDefaults(title: String): String? {
        val trimmed = title.trim()
        if (trimmed.isBlank() && state.value.preferences.blankTitlePolicy == BlankTitlePolicy.REJECT_BLANK) return null
        val prefix = state.value.preferences.titlePrefix.trim()
        return if (prefix.isBlank()) trimmed else "$prefix $trimmed".trim()
    }

    private fun requestSync(immediate: Boolean = false) {
        if (_state.value.auth.mode != AuthMode.AUTHENTICATED) {
            _state.value = _state.value.copy(syncState = _state.value.syncState.copy(isSyncing = false, syncReady = true))
            return
        }
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
        store.autoMarkMissedTasks()
        val preferences = store.preferences()
        val tasks = store.allTasks(_state.value.selectedDate)
        val openTasks = store.openTasksForDate(_state.value.selectedDate)
        val alarms = store.alarms(preferences)
        _state.value = _state.value.copy(
            today = deriveTodayViewState(
                openTasks = openTasks,
                journalEntries = store.journal(_state.value.selectedDate, preferences),
            ),
            taskView = TaskViewState(
                tasks = tasks,
                routines = store.routines(),
                alarms = alarms,
            ),
            activePomodoroSession = store.activePomodoroSession(preferences, timeProvider.nowEpochMillis()),
            recentPomodoroSessions = store.recentPomodoroSessions(preferences = preferences, nowEpochMillis = timeProvider.nowEpochMillis()),
            preferences = preferences,
            syncState = _state.value.syncState.copy(isSyncing = false, lastSyncedAt = lastSyncedAt),
        )
    }

    companion object {
        private const val AUTO_SYNC_DEBOUNCE_MS = 750L

        fun create(dependencies: AppDependencies): AbunAppController = AbunAppController(dependencies)
    }
}

private fun AlarmListItemView.toAgendaItem(task: TaskListItemView): AgendaTaskItemView = AgendaTaskItemView(
    taskId = task.id,
    title = task.title,
    status = task.status,
    triggerTimeLabel = triggerTimeLabel,
    triggerTimeEpochMillis = isoStringToEpochMillis(triggerTimeIso),
)

internal fun deriveTodayViewState(
    openTasks: List<TaskListItemView>,
    journalEntries: List<JournalEntryView>,
): TodayViewState {
    return TodayViewState(
        currentTasks = openTasks.map {
            AgendaTaskItemView(
                taskId = it.id,
                title = it.title,
                status = it.status,
            )
        },
        upcomingTasks = emptyList(),
        journalEntries = journalEntries,
    )
}
