package dev.tireless.abun.app

import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.AmortizationMethod
import dev.tireless.abun.finance.BudgetScopeType
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.finance.TransactionStatus
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
    private val loginPreferenceStore = dependencies.loginPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val authRemoteApi = AuthRemoteApi(
        baseUrl = dependencies.serverBaseUrl,
        client = dependencies.httpClient,
    )
    private val authSessionManager = AuthSessionManager(
        loginPreferenceStore = loginPreferenceStore,
        authRemoteApi = authRemoteApi,
        timeProvider = dependencies.timeProvider,
    )
    private val store = LocalStore(
        database = createDatabase(dependencies.databaseDriverFactory),
        timeProvider = dependencies.timeProvider,
        idGenerator = dependencies.idGenerator,
        clock = createHybridClock(dependencies.nodeIdProvider, dependencies.timeProvider),
    )
    private val remoteApi = SyncRemoteApi(
        baseUrl = dependencies.serverBaseUrl,
        client = dependencies.httpClient,
        accessTokenProvider = authSessionManager,
    )
    private val syncEngine = SyncEngine(
        localStore = store,
        remoteApi = remoteApi,
    )

    private val _state = MutableStateFlow(
        AppUiState(
            selectedDate = dependencies.timeProvider.today().toString(),
            syncState = SyncStateView(syncReady = initialSyncReady()),
            auth = initialAuthViewState(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var scheduledSyncJob: Job? = null
    private var isSyncing = false
    private var syncRequestedWhileRunning = false

    init {
        refresh()
        scope.launch {
            restoreSessionOnStartup()
        }
    }

    fun skipLogin() {
        loginPreferenceStore.setLoginOmitted(true)
        _state.value = _state.value.copy(
            auth = _state.value.auth.copy(
                showGuide = false,
                mode = AuthMode.GUEST,
                errorMessage = null,
            ),
            syncState = _state.value.syncState.copy(syncReady = true),
        )
    }

    fun reopenLogin() {
        loginPreferenceStore.setLoginOmitted(false)
        _state.value = _state.value.copy(
            auth = _state.value.auth.copy(
                showGuide = true,
                mode = AuthMode.GUEST,
                otpRequested = false,
                isSubmitting = false,
                errorMessage = null,
            ),
            syncState = _state.value.syncState.copy(syncReady = false),
        )
    }

    fun updateLoginEmail(email: String) {
        _state.value = _state.value.copy(auth = _state.value.auth.copy(email = email, errorMessage = null))
    }

    fun resetLoginOtp() {
        _state.value = _state.value.copy(
            auth = _state.value.auth.copy(
                otpRequested = false,
                errorMessage = null,
            ),
        )
    }

    fun requestEmailOtp() {
        val email = _state.value.auth.email.trim()
        if (email.isBlank()) {
            _state.value = _state.value.copy(auth = _state.value.auth.copy(errorMessage = "Email is required"))
            return
        }
        scope.launch {
            _state.value = _state.value.copy(auth = _state.value.auth.copy(isSubmitting = true, errorMessage = null))
            runCatching { authRemoteApi.requestOtp(email) }
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
            runCatching { authRemoteApi.verifyOtp(email, code) }
                .onSuccess { response -> completeLogin(response.toAuthSession()) }
                .onFailure {
                    _state.value = _state.value.copy(auth = _state.value.auth.copy(isSubmitting = false, errorMessage = it.message ?: "OTP verification failed"))
                }
        }
    }

    fun logout() {
        scope.launch {
            authSessionManager.logout()
            transitionToGuest(showGuide = true)
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

    fun createTask(
        title: String,
        detail: String? = null,
        parentId: String? = null,
        startNotBefore: String? = null,
        endNotAfter: String? = null,
        estimatedDuration: String? = null,
    ) {
        val preparedTitle = applyTaskDefaults(title)
        if (preparedTitle == null) return
        store.createTask(
            title = preparedTitle,
            journalDate = state.value.selectedDate,
            parentId = parentId,
            detail = detail,
            startNotBefore = startNotBefore,
            endNotAfter = endNotAfter,
            estimatedDuration = estimatedDuration,
        )
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
        store.startPomodoroSession(taskId, phase, state.value.preferences) ?: return
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
        themePreference: ThemePreference,
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
            themePreference = themePreference,
            rolloverTime = rolloverTime,
        )
        refresh()
        requestSync()
    }

    fun updateThemePreference(themePreference: ThemePreference) {
        loginPreferenceStore.setThemePreference(themePreference)
        refresh()
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
                    if (it is AuthSessionExpiredException) {
                        transitionToGuest(showGuide = true, authError = it.message)
                    } else {
                        _state.value = _state.value.copy(
                            syncState = _state.value.syncState.copy(isSyncing = false, errorMessage = it.message ?: "Sync failed"),
                        )
                    }
                }
            } while (syncRequestedWhileRunning)
        } finally {
            isSyncing = false
            scheduledSyncJob = null
        }
    }

    fun refresh(lastSyncedAt: String? = _state.value.syncState.lastSyncedAt) {
        store.autoMarkMissedTasks()
        val preferences = store.preferences().copy(themePreference = loginPreferenceStore.themePreference())
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
            pomodoroStartTasks = store.pomodoroStartableTasks().map {
                AgendaTaskItemView(taskId = it.id, title = it.title, status = it.status)
            },
            preferences = preferences,
            syncState = _state.value.syncState.copy(isSyncing = false, lastSyncedAt = lastSyncedAt),
            financeView = store.deriveFinanceViewState(
                selectedFilter = _state.value.financeView.selectedFilter,
                filterAccountId = _state.value.financeView.filterAccountId,
                filterClearingGroupId = _state.value.financeView.filterClearingGroupId,
                viewMode = _state.value.financeView.viewMode,
                runwayHorizonDays = _state.value.financeView.runwayHorizonDays,
            ),
        )
    }

    fun selectFinanceFilter(
        filter: FinanceLedgerFilter,
        accountId: String? = null,
        clearingGroupId: String? = null,
    ) {
        _state.value = _state.value.copy(
            financeView = store.deriveFinanceViewState(
                selectedFilter = filter,
                filterAccountId = accountId,
                filterClearingGroupId = clearingGroupId,
                viewMode = _state.value.financeView.viewMode,
                runwayHorizonDays = _state.value.financeView.runwayHorizonDays,
            ),
        )
    }

    fun toggleFinanceLedgerViewMode() {
        val nextMode = if (_state.value.financeView.viewMode == FinanceLedgerViewMode.STANDARD) {
            FinanceLedgerViewMode.BEANCOUNT
        } else {
            FinanceLedgerViewMode.STANDARD
        }
        _state.value = _state.value.copy(
            financeView = _state.value.financeView.copy(viewMode = nextMode),
        )
    }

    fun createFinanceTransaction(
        txDate: String,
        summary: String = "",
        payee: String? = null,
        txTime: String? = null,
        status: TransactionStatus = TransactionStatus.CLEARED,
        splits: List<FinanceSplitDraft>,
    ) {
        store.createFinanceTransaction(
            txDate = txDate,
            summary = summary,
            payee = payee,
            txTime = txTime,
            status = status,
            splits = splits,
        )
        refresh()
    }

    fun deleteFinanceTransaction(id: String) {
        store.deleteFinanceTransaction(id)
        refresh()
    }

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
    ) {
        store.createFinanceAccount(
            path = path,
            name = name,
            category = category,
            currency = currency,
            includeInNetWorth = includeInNetWorth,
            isPassThrough = isPassThrough,
            passThroughExpenseAccountId = passThroughExpenseAccountId,
            liquidTier = liquidTier,
            statementDay = statementDay,
            dueDay = dueDay,
        )
        refresh()
    }

    fun deleteFinanceAccount(id: String) {
        store.deleteFinanceAccount(id)
        refresh()
    }

    fun autoPadAccountBalance(
        accountId: String,
        actualBalanceMinor: Long,
        adjustmentDate: String = _state.value.selectedDate,
    ) {
        store.autoPadAccountBalance(
            accountId = accountId,
            actualBalanceMinor = actualBalanceMinor,
            adjustmentDate = adjustmentDate,
        )
        refresh()
    }

    fun createFinanceClearingGroup(
        name: String,
        groupType: ClearingGroupType,
        anchorCurrency: String = "CNY",
        targetAccountId: String? = null,
        statementCycle: String? = null,
        dueDate: String? = null,
    ) {
        store.createFinanceClearingGroup(
            name = name,
            groupType = groupType,
            anchorCurrency = anchorCurrency,
            targetAccountId = targetAccountId,
            statementCycle = statementCycle,
            dueDate = dueDate,
        )
        refresh()
    }

    fun createFinanceAmortizationPlan(
        name: String,
        principalMinor: Long,
        currency: String = "CNY",
        totalPeriods: Int,
        repaymentMethod: AmortizationMethod = AmortizationMethod.EQUAL_INSTALLMENT,
        annualRateBps: Int = 0,
        feePerPeriodMinor: Long = 0L,
        startDate: String,
        repaymentDay: Int,
        liabilityAccountId: String,
        fundingAccountId: String? = null,
        interestAccountId: String,
        recordInitialDisbursement: Boolean = false,
        existingDisbursementSplitId: String? = null,
    ): String {
        val planId = store.createFinanceAmortizationPlan(
            name = name,
            principalMinor = principalMinor,
            currency = currency,
            totalPeriods = totalPeriods,
            repaymentMethod = repaymentMethod,
            annualRateBps = annualRateBps,
            feePerPeriodMinor = feePerPeriodMinor,
            startDate = startDate,
            repaymentDay = repaymentDay,
            liabilityAccountId = liabilityAccountId,
            fundingAccountId = fundingAccountId,
            interestAccountId = interestAccountId,
            recordInitialDisbursement = recordInitialDisbursement,
            existingDisbursementSplitId = existingDisbursementSplitId,
        )
        refresh()
        return planId
    }

    fun deleteFinanceAmortizationPlan(id: String) {
        store.deleteFinanceAmortizationPlan(id)
        refresh()
    }

    fun repayCreditStatement(
        clearingGroupId: String,
        fundingAccountId: String,
        repaymentDate: String = _state.value.selectedDate,
        amountMinor: Long? = null,
    ) {
        val cg = store.financeClearingGroup(clearingGroupId) ?: return
        val targetAccId = cg.targetAccountId ?: return
        val viewState = _state.value.financeView
        val cgItem = viewState.clearingGroups.find { it.group.id == clearingGroupId }
        val debtMinor = -(cgItem?.evaluation?.realizedBalance ?: 0L)
        val repayAmt = amountMinor ?: debtMinor
        if (repayAmt <= 0L) return

        store.createFinanceTransaction(
            txDate = repaymentDate,
            summary = "${cg.name} 还款",
            status = TransactionStatus.CLEARED,
            splits = listOf(
                FinanceSplitDraft(
                    accountId = fundingAccountId,
                    amountMinor = -repayAmt,
                    currency = cg.anchorCurrency,
                    narration = "信用卡还款支出",
                ),
                FinanceSplitDraft(
                    accountId = targetAccId,
                    amountMinor = repayAmt,
                    currency = cg.anchorCurrency,
                    narration = "账单还款",
                    clearingGroupIds = listOf(clearingGroupId),
                ),
            ),
        )
        refresh()
    }

    fun executeLoanTrancheRepayment(transactionId: String) {
        val tx = store.financeTransaction(transactionId) ?: return
        if (tx.status != TransactionStatus.SCHEDULED) return
        val now = timeProvider.nowEpochMillis()
        val hlc = store.clock.next()
        store.queries.upsertFinanceTransaction(
            id = tx.id,
            tx_date = tx.txDate,
            tx_time = tx.txTime,
            payee = tx.payee,
            summary = tx.summary,
            status = TransactionStatus.CLEARED.name,
            source_plan_id = tx.sourcePlanId,
            period_index = tx.periodIndex?.toLong(),
            is_deleted = 0L,
            hlc_map = "{\"core\":\"$hlc\"}",
            dirty_fields = "[\"status\"]",
            is_dirty = 1L,
            server_version = 0L,
            created_at = now,
            updated_at = now,
        )
        val sourcePlanId = tx.sourcePlanId
        if (sourcePlanId != null) {
            val plan = store.financeAmortizationPlan(sourcePlanId)
            if (plan != null) {
                store.refreshClearingGroupStatus(plan.lifecycleClearingGroupId, now)
            }
        }
        refresh()
    }

    fun reassignSplitClearingGroup(splitId: String, oldGroupId: String, newGroupId: String) {
        store.reassignSplitClearingGroup(splitId, oldGroupId, newGroupId)
        refresh()
    }

    fun setFinanceRunwayHorizon(horizonDays: Int) {
        _state.value = _state.value.copy(
            financeView = store.deriveFinanceViewState(
                selectedFilter = _state.value.financeView.selectedFilter,
                filterAccountId = _state.value.financeView.filterAccountId,
                filterClearingGroupId = _state.value.financeView.filterClearingGroupId,
                viewMode = _state.value.financeView.viewMode,
                runwayHorizonDays = horizonDays,
            ),
        )
    }

    fun createBudgetRule(
        name: String,
        scopeType: BudgetScopeType,
        accountPrefix: String = "Expenses:",
        excludedPrefixesJson: String = "[]",
        monthlyLimitMinor: Long,
        currency: String = "CNY",
        includeInRunway: Boolean = true,
    ) {
        store.createBudgetRule(
            name = name,
            scopeType = scopeType,
            accountPrefix = accountPrefix,
            excludedPrefixesJson = excludedPrefixesJson,
            monthlyLimitMinor = monthlyLimitMinor,
            currency = currency,
            includeInRunway = includeInRunway,
        )
        refresh()
    }

    fun deleteBudgetRule(id: String) {
        store.deleteBudgetRule(id)
        refresh()
    }

    companion object {
        private const val AUTO_SYNC_DEBOUNCE_MS = 750L

        fun create(dependencies: AppDependencies): AbunAppController = AbunAppController(dependencies)
    }

    private suspend fun completeLogin(session: AuthSession) {
        loginPreferenceStore.setLoginOmitted(false)
        authSessionManager.completeLogin(session)
        _state.value = _state.value.copy(
            auth = _state.value.auth.copy(
                showGuide = false,
                mode = AuthMode.AUTHENTICATED,
                isSubmitting = false,
                otpRequested = false,
                errorMessage = null,
            ),
            syncState = _state.value.syncState.copy(syncReady = true, errorMessage = null),
        )
        requestSync(immediate = true)
    }

    private suspend fun restoreSessionOnStartup() {
        val restored = runCatching { authSessionManager.restoreSession() }
            .getOrElse { error ->
                if (error is AuthSessionExpiredException) {
                    transitionToGuest(showGuide = true, authError = error.message)
                    return
                }
                throw error
            }
        if (restored != null) {
            _state.value = _state.value.copy(
                auth = _state.value.auth.copy(
                    showGuide = false,
                    mode = AuthMode.AUTHENTICATED,
                    errorMessage = null,
                ),
                syncState = _state.value.syncState.copy(syncReady = true, errorMessage = null),
            )
            requestSync(immediate = true)
            return
        }

        if (authSessionManager.storedSession() == null && _state.value.auth.mode == AuthMode.GUEST) {
            return
        }

        transitionToGuest(showGuide = true)
    }

    private fun transitionToGuest(showGuide: Boolean, authError: String? = null) {
        loginPreferenceStore.setLoginOmitted(false)
        _state.value = _state.value.copy(
            auth = _state.value.auth.copy(
                showGuide = showGuide,
                mode = AuthMode.GUEST,
                otpRequested = false,
                isSubmitting = false,
                errorMessage = authError,
            ),
            syncState = _state.value.syncState.copy(syncReady = false, isSyncing = false, errorMessage = null),
        )
    }

    private fun initialAuthViewState(): AuthViewState {
        val storedSession = authSessionManager.storedSession()
        val now = timeProvider.nowEpochMillis()
        if (storedSession != null && storedSession.refreshTokenExpiresAtEpochMillis > now) {
            return AuthViewState(
                showGuide = false,
                mode = AuthMode.AUTHENTICATED,
                email = "",
                otpRequested = false,
            )
        }

        return AuthViewState(showGuide = !loginPreferenceStore.isLoginOmitted())
    }

    private fun initialSyncReady(): Boolean {
        val storedSession = authSessionManager.storedSession() ?: return false
        val now = timeProvider.nowEpochMillis()
        return storedSession.accessTokenExpiresAtEpochMillis > now
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
