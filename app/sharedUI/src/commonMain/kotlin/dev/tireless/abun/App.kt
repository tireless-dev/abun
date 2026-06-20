package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroSessionView
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.RecurrenceFrequency
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.StructuredRecurrence
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.ThemePreference
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.EditorialScreen
import dev.tireless.abun.ui.EditorialStatusTag
import dev.tireless.abun.ui.theme.AppTheme
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor
import kotlinx.coroutines.delay

private enum class OverlaySheet {
    CREATE_TASK,
    CREATE_ROUTINE,
    TASK_ACTIONS,
    ROUTINE_ACTIONS,
    START_POMODORO,
    COMPLETE_POMODORO,
}

internal data class TaskSurfaceGroups(
    val backlog: List<TaskListItemView>,
    val scheduled: List<TaskListItemView>,
    val completed: List<TaskListItemView>,
)

@Composable
@Preview
fun App() {
    val controller = rememberAbunAppController()
    val state by controller.state.collectAsState()
    val liveNow by rememberLiveNow()
    val activeSession = state.activePomodoroSession
    val activeRemaining = activeSession?.let { it.endsAtEpochMillis - liveNow } ?: 0L
    val isPomodoroActive = activeSession != null && activeRemaining > 0
    var currentSheet by remember { mutableStateOf<OverlaySheet?>(null) }
    var selectedTask by remember { mutableStateOf<TaskListItemView?>(null) }
    var selectedRoutine by remember { mutableStateOf<RoutineListItemView?>(null) }
    var createTaskContext by remember { mutableStateOf(taskCreateContextFor(state.selectedTab, state.selectedDate)) }
    val selectedTaskHistory = selectedTask?.let { controller.taskHistory(it.id) }.orEmpty()

    LaunchedEffect(state.activePomodoroSession?.id, activeRemaining) {
        if (state.activePomodoroSession != null && activeRemaining <= 0) {
            currentSheet = OverlaySheet.COMPLETE_POMODORO
            controller.selectTaskSubTab(TaskSubTab.POMODORO)
        }
    }

    AppTheme(themePreference = state.preferences.themePreference) {
        if (state.auth.showGuide) {
            GuideScreenContent(
                state = state,
                onUpdateLoginEmail = controller::updateLoginEmail,
                onRequestEmailOtp = controller::requestEmailOtp,
                onVerifyEmailOtp = controller::verifyEmailOtp,
                onSkipLogin = controller::skipLogin,
            )
            return@AppTheme
        }

        val fabLabel = when (state.selectedTab) {
            AppTab.TODAY, AppTab.TASKS -> "Task"
            else -> null
        }
        MaterialScaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ThemeTokens.colors.background,
                        titleContentColor = ThemeTokens.colors.textPrimary,
                    ),
                    title = {
                        Text(
                            text = state.selectedTab.tabLabel(),
                            style = ThemeTokens.type.title,
                        )
                    },
                )
            },
            bottomBar = {
                Surface(color = ThemeTokens.colors.surface, contentColor = ThemeTokens.colors.textSecondary) {
                    SecondaryTabRow(
                        selectedTabIndex = AppTab.entries.indexOf(state.selectedTab).coerceAtLeast(0),
                        containerColor = ThemeTokens.colors.surface,
                        contentColor = ThemeTokens.colors.textSecondary,
                        divider = { HorizontalDivider(color = ThemeTokens.colors.border) },
                    ) {
                        AppTab.entries.forEach { tab ->
                            Tab(
                                selected = state.selectedTab == tab,
                                onClick = { controller.selectTab(tab) },
                                selectedContentColor = ThemeTokens.colors.textPrimary,
                                unselectedContentColor = ThemeTokens.colors.textSecondary,
                                text = { Text(tab.tabLabel(), style = ThemeTokens.type.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (fabLabel != null && !isPomodoroActive) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            createTaskContext = taskCreateContextFor(state.selectedTab, state.selectedDate)
                            currentSheet = OverlaySheet.CREATE_TASK
                        },
                        containerColor = ThemeTokens.colors.surface,
                        contentColor = ThemeTokens.colors.textPrimary,
                        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                        icon = { Text("+") },
                        text = { Text(fabLabel, style = ThemeTokens.type.label.withMaterialContentColor()) },
                    )
                }
            },
        ) { padding: PaddingValues ->
            EditorialScreen(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (state.selectedTab) {
                    AppTab.TODAY -> TodayScreen(
                        state = state,
                        liveNow = liveNow,
                        onOpenTask = {
                            selectedTask = it
                            currentSheet = OverlaySheet.TASK_ACTIONS
                        },
                        onStartPomodoro = { currentSheet = OverlaySheet.START_POMODORO },
                    )
                    AppTab.TASKS -> TasksScreen(
                        state = state,
                        liveNow = liveNow,
                        isPomodoroActive = isPomodoroActive,
                        onSelectPanel = controller::selectTaskSubTab,
                        onSelectTaskFilter = controller::selectTaskFilter,
                        onOpenTask = {
                            selectedTask = it
                            currentSheet = OverlaySheet.TASK_ACTIONS
                        },
                        onOpenStartPomodoro = { currentSheet = OverlaySheet.START_POMODORO },
                        onCreateRoutine = { currentSheet = OverlaySheet.CREATE_ROUTINE },
                        onOpenRoutine = {
                            selectedRoutine = it
                            currentSheet = OverlaySheet.ROUTINE_ACTIONS
                        },
                        onRunRoutine = { controller.runRoutine(it.id) },
                    )
                    AppTab.SETTINGS -> SettingsScreen(state, controller)
                }
            }
        }

        when (currentSheet) {
            OverlaySheet.CREATE_TASK -> CreateTaskSheet(
                context = createTaskContext,
                onDismiss = { currentSheet = null },
                onCreate = { draft ->
                    controller.createTask(
                        draft.title,
                        draft.detail,
                        draft.parentId,
                        draft.startNotBefore,
                        draft.endNotAfter,
                        draft.estimatedDuration,
                    )
                    currentSheet = null
                },
            )
            OverlaySheet.CREATE_ROUTINE -> CreateRoutineSheet(
                onDismiss = { currentSheet = null },
                onCreate = { title, detail, recurrenceRule, defaultStartNotBefore, defaultEstimatedDuration ->
                    controller.createRoutine(title, detail, recurrenceRule, defaultStartNotBefore, defaultEstimatedDuration)
                    currentSheet = null
                },
            )
            OverlaySheet.TASK_ACTIONS -> TaskActionsSheet(
                task = selectedTask,
                history = selectedTaskHistory,
                availableParents = state.taskView.tasks.filter { candidate ->
                    candidate.id != selectedTask?.id && candidate.routineId == null
                },
                availableRoutines = state.taskView.routines,
                isPomodoroActive = isPomodoroActive,
                onDismiss = { currentSheet = null },
                onSaveTask = { taskId, title, detail, parentId, startNotBefore, endNotAfter, estimatedDuration ->
                    controller.updateTask(taskId, title, detail, parentId, startNotBefore, endNotAfter, estimatedDuration)
                    currentSheet = null
                },
                onProgress = { note ->
                    selectedTask?.let { controller.progressTask(it.id, note.ifBlank { null }) }
                    currentSheet = null
                },
                onComplete = { note ->
                    selectedTask?.let { controller.completeTask(it.id, note.ifBlank { null }) }
                    currentSheet = null
                },
                onSkip = { note ->
                    selectedTask?.let { controller.skipTask(it.id, note.ifBlank { null }) }
                    currentSheet = null
                },
                onPostpone = { taskId, startNotBefore, endNotAfter, estimatedDuration, note ->
                    controller.postponeTask(taskId, startNotBefore, endNotAfter, estimatedDuration, note)
                    currentSheet = null
                },
                onDelete = {
                    selectedTask?.let { controller.deleteTask(it.id) }
                    currentSheet = null
                },
                onStartPomodoro = {
                    selectedTask?.let { controller.startPomodoro(it.id, PomodoroPhase.FOCUS) }
                    controller.selectTaskSubTab(TaskSubTab.POMODORO)
                    currentSheet = null
                },
            )
            OverlaySheet.ROUTINE_ACTIONS -> RoutineActionsSheet(
                routine = selectedRoutine,
                onDismiss = { currentSheet = null },
                onSave = { routineId, title, detail, recurrenceRule, defaultStartNotBefore, defaultEstimatedDuration ->
                    controller.updateRoutine(
                        routineId = routineId,
                        templateTitle = title,
                        templateDetail = detail,
                        recurrenceRule = recurrenceRule,
                        defaultStartNotBefore = defaultStartNotBefore,
                        defaultEstimatedDuration = defaultEstimatedDuration,
                    )
                    currentSheet = null
                },
                onToggle = {
                    selectedRoutine?.let { controller.toggleRoutineActive(it.id) }
                    currentSheet = null
                },
                onDelete = {
                    selectedRoutine?.let { controller.deleteRoutine(it.id) }
                    currentSheet = null
                },
            )
            OverlaySheet.START_POMODORO -> StartPomodoroSheet(
                state = state,
                hasActive = activeSession != null && activeRemaining > 0,
                onDismiss = { currentSheet = null },
                onStart = { taskId, phase ->
                    controller.startPomodoro(taskId, phase)
                    controller.selectTaskSubTab(TaskSubTab.POMODORO)
                    currentSheet = null
                },
            )
            OverlaySheet.COMPLETE_POMODORO -> CompletePomodoroSheet(
                state = state,
                liveNow = liveNow,
                onDismiss = { currentSheet = null },
                onSave = { note, update ->
                    state.activePomodoroSession?.let { controller.completePomodoro(it.id, note.ifBlank { null }, update) }
                    currentSheet = null
                },
                onStop = { note ->
                    state.activePomodoroSession?.let { controller.cancelPomodoro(it.id, note.ifBlank { null }) }
                    currentSheet = null
                },
            )
            null -> Unit
        }
    }
}

@Composable
internal fun GuideScreenContent(
    state: AppUiState,
    onUpdateLoginEmail: (String) -> Unit,
    onRequestEmailOtp: () -> Unit,
    onVerifyEmailOtp: (String) -> Unit,
    onSkipLogin: () -> Unit,
) {
    var otpCode by remember(state.auth.prefilledOtp) { mutableStateOf(state.auth.prefilledOtp) }
    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .fillMaxSize(),
    ) {
        EditorialScreen {
            Panel {
            Text("abun", style = ThemeTokens.type.title.copy(fontWeight = FontWeight.Bold), color = ThemeTokens.colors.primary)
            Text("Sign in", style = ThemeTokens.type.display)
            Text("Login with email OTP to enable cloud sync, or skip for local-only mode.", style = ThemeTokens.type.bodyMuted)
            OutlinedTextField(
                value = state.auth.email,
                onValueChange = onUpdateLoginEmail,
                label = { Text("Email", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            Button(onClick = onRequestEmailOtp, enabled = !state.auth.isSubmitting) {
                Text(if (state.auth.otpRequested) "Resend OTP" else "Send OTP", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            if (state.auth.otpRequested) {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it },
                    label = { Text("OTP code", style = ThemeTokens.type.label) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
                state.auth.debugOtpHint?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
                Button(onClick = { onVerifyEmailOtp(otpCode) }, enabled = !state.auth.isSubmitting) {
                    Text("Verify and login", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
            Button(onClick = onSkipLogin, enabled = !state.auth.isSubmitting) {
                Text("Skip for now", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            state.auth.errorMessage?.let {
                Text(it, color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
            }
        }
        }
    }
}

@Composable
private fun StatusStrip(state: AppUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        state.syncState.lastSyncedAt?.let { Text("Last synced: $it", style = ThemeTokens.type.bodyMuted) }
        state.syncState.errorMessage?.let { Text(it, color = ThemeTokens.colors.error, style = ThemeTokens.type.body) }
        if (state.auth.mode == AuthMode.GUEST) {
            Text("Local-only mode. Login anytime to sync.", style = ThemeTokens.type.bodyMuted)
        }
    }
}
@Composable
internal fun TodayScreen(
    state: AppUiState,
    liveNow: Long,
    onOpenTask: (TaskListItemView) -> Unit,
    onStartPomodoro: () -> Unit,
) {
    val tasksById = state.taskView.tasks.associateBy(TaskListItemView::id)
    val openTasks = state.today.currentTasks.mapNotNull { agenda -> tasksById[agenda.taskId] }
    val runningTasks = openTasks.count { it.status == TaskStatus.IN_PROGRESS }
    val active = state.activePomodoroSession

    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("Daily desk", style = ThemeTokens.type.label)
                Text("Day", style = ThemeTokens.type.title)
                Text(state.selectedDate, style = ThemeTokens.type.bodyMuted)
            }
            Button(onClick = onStartPomodoro) {
                Text(if (active == null) "Start" else formatRemaining(active.endsAtEpochMillis - liveNow), style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
        MetricRow(
            listOf(
                "Open" to openTasks.size.toString(),
                "Running" to runningTasks.toString(),
                "Routines" to state.taskView.routines.size.toString(),
            ),
        )
        TaskStack(
            tasks = openTasks,
            empty = "No open tasks for this date.",
            onOpenTask = onOpenTask,
        )
    }

    Panel {
        SectionHeader("Day timeline", "${state.today.journalEntries.size} events")
        JournalTimeline(state.today.journalEntries)
    }

    Panel {
        SectionHeader("Pomodoro", active?.let { formatRemaining(it.endsAtEpochMillis - liveNow) } ?: "Ready")
        Text(active?.taskTitle ?: "No active timer", style = ThemeTokens.type.bodyMuted)
    }
}

@Composable
internal fun TasksScreen(
    state: AppUiState,
    liveNow: Long,
    isPomodoroActive: Boolean,
    onSelectPanel: (TaskSubTab) -> Unit,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
    onOpenStartPomodoro: () -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        TaskSubTab.entries.forEachIndexed { index, taskSubTab ->
            val option = taskSubTab.label()
            SegmentedButton(
                selected = option == state.selectedTaskSubTab.label(),
                onClick = { onSelectPanel(taskSubTabFromLabel(option)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TaskSubTab.entries.size),
            ) {
                Text(option, style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    }

    when (state.selectedTaskSubTab) {
        TaskSubTab.TASKS -> TaskListScreen(
            state = state,
            isPomodoroActive = isPomodoroActive,
            onSelectTaskFilter = onSelectTaskFilter,
            onOpenTask = onOpenTask,
        )
        TaskSubTab.ROUTINES -> RoutineListScreen(
            state = state,
            onCreateRoutine = onCreateRoutine,
            onOpenRoutine = onOpenRoutine,
            onRunRoutine = onRunRoutine,
        )
        TaskSubTab.POMODORO -> PomodoroScreen(state, liveNow = liveNow, onOpenStart = onOpenStartPomodoro)
    }
}

@Composable
private fun TaskListScreen(
    state: AppUiState,
    isPomodoroActive: Boolean,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
) {
    val filteredTasks = filterTasksForSurface(state.taskView.tasks, state.selectedTaskFilter)
    Panel {
        SectionHeader("Task list", taskListFilterTitle(state.selectedTaskFilter))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            TaskListFilter.entries.forEachIndexed { index, filter ->
                val option = filter.label()
                SegmentedButton(
                    selected = option == state.selectedTaskFilter.label(),
                    onClick = { onSelectTaskFilter(taskListFilterFromLabel(option)) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TaskListFilter.entries.size),
                ) {
                    Text(option, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        TaskStack(
            tasks = filteredTasks,
            empty = taskListFilterEmptyState(state.selectedTaskFilter),
            onOpenTask = onOpenTask,
            disabled = isPomodoroActive,
        )
    }
}

@Composable
private fun RoutineListScreen(
    state: AppUiState,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Routine support", "Routines")
            Button(onClick = onCreateRoutine) {
                Text("Create", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
        if (state.taskView.routines.isEmpty()) {
            Text("No routines.", style = ThemeTokens.type.body)
        } else {
            state.taskView.routines.forEach { routine ->
                RoutineRow(routine, onOpenRoutine, onRunRoutine)
            }
        }
    }
}

@Composable
internal fun PomodoroScreen(state: AppUiState, liveNow: Long, onOpenStart: () -> Unit) {
    val active = state.activePomodoroSession
    Panel {
        Text("Timer", style = ThemeTokens.type.label)
        Text(active?.let { formatRemaining(it.endsAtEpochMillis - liveNow) } ?: "00:00", style = ThemeTokens.type.display)
        Text(active?.taskTitle ?: "No active timer", style = ThemeTokens.type.bodyMuted)
        if (active == null) {
            Button(onClick = onOpenStart) { Text("Start", style = ThemeTokens.type.body.withMaterialContentColor()) }
        } else {
            Text("${active.phase.label()} • ${active.durationMinutes}m", style = ThemeTokens.type.bodyMuted)
            Button(onClick = onOpenStart) { Text("Complete or stop", style = ThemeTokens.type.body.withMaterialContentColor()) }
        }
    }

    Panel {
        SectionHeader("History", "Recent sessions")
        if (state.recentPomodoroSessions.isEmpty()) {
            Text("No sessions.", style = ThemeTokens.type.body)
        } else {
            state.recentPomodoroSessions.forEach { session ->
                PomodoroRow(session)
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, controller: AbunAppController) {
    SettingsScreenContent(
        state = state,
        onUpdateThemePreference = controller::updateThemePreference,
        onUpdatePreferences = controller::updatePreferences,
        onReopenLogin = controller::reopenLogin,
        onLogout = controller::logout,
    )
}

@Composable
internal fun SettingsScreenContent(
    state: AppUiState,
    onUpdateThemePreference: (ThemePreference) -> Unit,
    onUpdatePreferences: (
        titlePrefix: String,
        defaultAlarmLeadMinutes: Int,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        timezoneOverride: String,
        dateFormat: DateFormatPreference,
        themePreference: ThemePreference,
        rolloverTime: String,
    ) -> Unit,
    onReopenLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    var titlePrefix by remember(state.preferences) { mutableStateOf(state.preferences.titlePrefix) }
    var focusMinutes by remember(state.preferences) { mutableStateOf(state.preferences.focusMinutes.toString()) }
    var shortBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.shortBreakMinutes.toString()) }
    var longBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.longBreakMinutes.toString()) }
    var timezoneOverride by remember(state.preferences) { mutableStateOf(state.preferences.timezoneOverride) }
    var selectedDateFormat by remember(state.preferences) { mutableStateOf(state.preferences.dateFormat) }
    var selectedThemePreference by remember(state.preferences) { mutableStateOf(state.preferences.themePreference) }
    var rolloverTime by remember(state.preferences) { mutableStateOf(state.preferences.rolloverTime) }

    Panel {
        SectionHeader("Cloud", "Sync status")
        SyncStatusPanel(state)
    }
    Panel {
        SectionHeader("Defaults", "Task")
        OutlinedTextField(
            value = titlePrefix,
            onValueChange = { titlePrefix = it },
            label = { Text("Title prefix", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
    }
    Panel {
        SectionHeader("Defaults", "Pomodoro")
        OutlinedTextField(
            value = focusMinutes,
            onValueChange = { focusMinutes = it },
            label = { Text("Pomodoro minutes", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = shortBreakMinutes,
            onValueChange = { shortBreakMinutes = it },
            label = { Text("Short break minutes", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = longBreakMinutes,
            onValueChange = { longBreakMinutes = it },
            label = { Text("Long break minutes", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
    }
    Panel {
        SectionHeader("Appearance", "Theme")
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            ThemePreference.entries.forEachIndexed { index, themePreference ->
                val option = themePreference.label()
                SegmentedButton(
                    modifier = Modifier.testTag("theme-option-${themePreference.name.lowercase()}"),
                    selected = option == selectedThemePreference.label(),
                    onClick = {
                        selectedThemePreference = themePreferenceFromLabel(option)
                        onUpdateThemePreference(selectedThemePreference)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemePreference.entries.size),
                ) {
                    Text(option, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
    }
    Panel {
        SectionHeader("App", "Preferences")
        OutlinedTextField(
            value = timezoneOverride,
            onValueChange = { timezoneOverride = it },
            label = { Text("Timezone override", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = rolloverTime,
            onValueChange = { rolloverTime = it },
            label = { Text("Rollover time (HH:MM)", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            DateFormatPreference.entries.forEachIndexed { index, dateFormatPreference ->
                val option = dateFormatPreference.label()
                SegmentedButton(
                    selected = option == selectedDateFormat.label(),
                    onClick = { selectedDateFormat = dateFormatFromLabel(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DateFormatPreference.entries.size),
                ) {
                    Text(option, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        Button(
            onClick = {
                onUpdatePreferences(
                    titlePrefix,
                    state.preferences.defaultAlarmLeadMinutes,
                    focusMinutes.toIntOrNull() ?: state.preferences.focusMinutes,
                    shortBreakMinutes.toIntOrNull() ?: state.preferences.shortBreakMinutes,
                    longBreakMinutes.toIntOrNull() ?: state.preferences.longBreakMinutes,
                    timezoneOverride,
                    selectedDateFormat,
                    selectedThemePreference,
                    rolloverTime,
                )
            },
        ) {
            Text("Save", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
    if (state.auth.mode == AuthMode.GUEST) {
        Panel {
            SectionHeader("Account", "Login")
            Text(
                state.auth.errorMessage ?: "Login anytime to enable cloud sync on this device.",
                style = ThemeTokens.type.bodyMuted,
            )
            Button(onClick = onReopenLogin) {
                Text("Open login", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    } else {
        Panel {
            SectionHeader("Account", "Session")
            Text("This device is signed in and can sync with your server account.", style = ThemeTokens.type.bodyMuted)
            OutlinedButton(onClick = onLogout) {
                Text("Log out", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    EditorialCard(content = content)
}

@Composable
private fun SectionHeader(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        Text(eyebrow, style = ThemeTokens.type.label, color = ThemeTokens.colors.textTertiary)
        Text(title, style = ThemeTokens.type.sectionTitle)
    }
}

@Composable
private fun MetricRow(items: List<Pair<String, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(ThemeTokens.colors.surfaceMuted, RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .padding(ThemeTokens.spacing.mdDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                Text(value, style = ThemeTokens.type.cardTitle)
                Text(label, style = ThemeTokens.type.bodyMuted)
            }
        }
    }
}

@Composable
private fun TaskStack(
    tasks: List<TaskListItemView>,
    empty: String,
    onOpenTask: (TaskListItemView) -> Unit,
    disabled: Boolean = false,
    compact: Boolean = false,
) {
    if (tasks.isEmpty()) {
        Text(empty, style = ThemeTokens.type.body)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        tasks.forEach { task ->
            TaskRow(task = task, compact = compact, disabled = disabled, onOpenTask = onOpenTask)
        }
    }
}

@Composable
private fun JournalTimeline(entries: List<JournalEntryView>) {
    if (entries.isEmpty()) {
        Text("No history for this date.", style = ThemeTokens.type.body)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        entries.forEach { entry ->
            EditorialCard {
                Text(entry.title, style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold))
                Text("${taskEventLabel(entry.eventType)} • ${entry.eventTimeLabel}", style = ThemeTokens.type.bodyMuted)
                entry.content?.takeIf(String::isNotBlank)?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskListItemView,
    compact: Boolean,
    disabled: Boolean,
    onOpenTask: (TaskListItemView) -> Unit,
) {
    EditorialCard {
        Text(task.title, style = ThemeTokens.type.cardTitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
        ) {
            StatusPill(task.status)
            task.routineId?.let { Text("Routine", style = ThemeTokens.type.bodyMuted) }
        }
        if (!compact) {
            Button(onClick = { onOpenTask(task) }, enabled = !disabled) {
                Text(if (disabled) "Pomodoro active" else "Manage", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        } else {
            Button(onClick = { onOpenTask(task) }, enabled = !disabled) {
                Text("Open", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    }
}

@Composable
private fun StatusPill(status: TaskStatus) {
    EditorialStatusTag(status = status)
}

@Composable
private fun RoutineRow(
    routine: RoutineListItemView,
    onOpen: (RoutineListItemView) -> Unit,
    onRun: (RoutineListItemView) -> Unit,
) {
    EditorialCard {
        Text(routine.templateTitle, style = ThemeTokens.type.cardTitle)
        routine.templateDetail?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
        Text(describeRecurrenceRule(routine.recurrenceRule), style = ThemeTokens.type.bodyMuted)
        routine.defaultStartNotBefore?.let { Text("Default start: $it", style = ThemeTokens.type.label) }
        routine.defaultEstimatedDuration?.let { Text("Default duration: $it", style = ThemeTokens.type.label) }
        Text(if (routine.isActive) "Active" else "Paused", style = ThemeTokens.type.label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Button(onClick = { onRun(routine) }, enabled = routine.isActive) {
                Text("Run today", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            Button(onClick = { onOpen(routine) }) {
                Text("Manage", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    }
}

@Composable
private fun PomodoroRow(session: PomodoroSessionView) {
    EditorialCard {
        Text(session.phase.label(), style = ThemeTokens.type.cardTitle)
        Text(session.taskTitle ?: "Standalone timer", style = ThemeTokens.type.bodyMuted)
        Text("${session.state.name.lowercase()} • ${session.durationMinutes}m", style = ThemeTokens.type.label)
        session.note?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CreateTaskSheet(
    context: TaskCreateContext,
    onDismiss: () -> Unit,
    onCreate: (TaskSaveDraft) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
        ) {
        CreateTaskSheetContent(
            context = context,
            onCreate = onCreate,
            onDismiss = onDismiss,
        )
        }
    }
}

@Composable
internal fun CreateTaskSheetContent(
    context: TaskCreateContext,
    onCreate: (TaskSaveDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(context) { mutableStateOf(defaultTaskCreateDraft(context)) }
    val dateOptions = remember(context) { taskCreateDateOptions(context) }
    val selectedDateLabel = dateOptions.firstOrNull { it.date == draft.startDate }?.label ?: dateOptions.first().label
    val createEnabled = draft.title.isNotBlank() &&
        (!draft.hasSchedule || !draft.startDate.isNullOrBlank()) &&
        (draft.durationPreset != DurationPreset.CUSTOM || draft.customDurationMinutes.trim().toIntOrNull()?.let { it > 0 } == true)
    Text("Create task", style = ThemeTokens.type.sectionTitle)
    OutlinedTextField(
        value = draft.title,
        onValueChange = { draft = draft.copy(title = it) },
        label = { Text("Task title", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.detail,
        onValueChange = { draft = draft.copy(detail = it) },
        label = { Text("Detail", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )
    if (draft.hasSchedule) {
        Text("Starts on", style = ThemeTokens.type.label)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            dateOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.label == selectedDateLabel,
                    onClick = { draft = draft.copy(startDate = option.date) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = dateOptions.size),
                ) {
                    Text(option.label, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        Text(draft.startDate.orEmpty(), style = ThemeTokens.type.bodyMuted)
        Text("Estimated duration", style = ThemeTokens.type.label)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DurationPreset.entries.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = preset.label == draft.durationPreset.label,
                    onClick = {
                        draft = draft.copy(
                            durationPreset = preset,
                            customDurationMinutes = if (preset == DurationPreset.CUSTOM) draft.customDurationMinutes else "",
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DurationPreset.entries.size),
                ) {
                    Text(preset.label, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        if (draft.durationPreset == DurationPreset.CUSTOM) {
            OutlinedTextField(
                value = draft.customDurationMinutes,
                onValueChange = { draft = draft.copy(customDurationMinutes = it) },
                label = { Text("Custom minutes", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
        }
        OutlinedButton(
            onClick = { draft = draft.copy(hasSchedule = false, startDate = null, durationPreset = DurationPreset.NONE, customDurationMinutes = "") },
        ) {
            Text("Clear schedule", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    } else {
        Text("This task will go to backlog.", style = ThemeTokens.type.bodyMuted)
        OutlinedButton(
            onClick = {
                draft = draft.copy(
                    hasSchedule = true,
                    startDate = draft.startDate ?: context.selectedDate,
                )
            },
        ) {
            Text("Add schedule", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
    ) {
        OutlinedButton(onClick = onDismiss) {
            Text("Cancel", style = ThemeTokens.type.body.withMaterialContentColor())
        }
        Button(
            onClick = { onCreate(normalizeTaskCreateDraft(draft)) },
            enabled = createEnabled,
        ) {
            Text("Create", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CreateRoutineSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?, String, String?, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var recurrenceRule by remember { mutableStateOf("RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0") }
    var defaultStartNotBefore by remember { mutableStateOf("") }
    var defaultEstimatedDuration by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
        Text("Create routine", style = ThemeTokens.type.sectionTitle)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Routine title", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it },
            label = { Text("Routine detail", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        RoutineRecurrenceEditor(rule = recurrenceRule, onRuleChange = { recurrenceRule = it })
        Text(describeRecurrenceRule(recurrenceRule), style = ThemeTokens.type.bodyMuted)
        OutlinedTextField(
            value = defaultStartNotBefore,
            onValueChange = { defaultStartNotBefore = it },
            label = { Text("Default start not before", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = defaultEstimatedDuration,
            onValueChange = { defaultEstimatedDuration = it },
            label = { Text("Default estimated duration", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            Button(
                onClick = {
                    onCreate(
                        title,
                        detail.ifBlank { null },
                        recurrenceRule,
                        defaultStartNotBefore.ifBlank { null },
                        defaultEstimatedDuration.ifBlank { null },
                    )
                },
                enabled = title.isNotBlank() && recurrenceRule.isNotBlank(),
            ) {
                Text("Create", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TaskActionsSheet(
    task: TaskListItemView?,
    history: List<JournalEntryView>,
    availableParents: List<TaskListItemView>,
    availableRoutines: List<RoutineListItemView>,
    isPomodoroActive: Boolean,
    onDismiss: () -> Unit,
    onSaveTask: (String, String, String?, String?, String?, String?, String?) -> Unit,
    onProgress: (String) -> Unit,
    onComplete: (String) -> Unit,
    onSkip: (String) -> Unit,
    onPostpone: (String, String?, String?, String?, String?) -> Unit,
    onDelete: () -> Unit,
    onStartPomodoro: () -> Unit,
) {
    if (task == null) return
    val routine = remember(task.id) { availableRoutines.find { it.id == task.routineId } }
    val isRoutineDerived = routine != null

    var title by remember(task.id) { mutableStateOf(task.title) }
    var detail by remember(task.id) { mutableStateOf(task.detail.orEmpty()) }
    var parentId by remember(task.id) { mutableStateOf(task.parentId) }
    var startNotBefore by remember(task.id) { mutableStateOf(task.startNotBefore.orEmpty()) }
    var endNotAfter by remember(task.id) { mutableStateOf(task.endNotAfter.orEmpty()) }
    var estimatedDuration by remember(task.id) { mutableStateOf(task.estimatedDuration.orEmpty()) }
    var note by remember(task.id) { mutableStateOf("") }

    val nextOccurrence = remember(routine, startNotBefore) {
        if (routine == null) return@remember null
        val structured = StructuredRecurrence.fromRRule(routine.recurrenceRule)
        val after = try {
            if (startNotBefore.isNotBlank()) {
                Instant.parse(startNotBefore).toLocalDateTime(TimeZone.currentSystemDefault())
            } else {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }
        } catch (e: Exception) {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }
        structured.nextOccurrence(after)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
        Text(if (isRoutineDerived) "Routine: ${task.title}" else task.title, style = ThemeTokens.type.sectionTitle)
        if (isRoutineDerived) {
            Text("Routine: ${routine?.templateTitle}", style = ThemeTokens.type.bodyMuted)
            nextOccurrence?.let {
                Text("Next occurrence: $it", style = ThemeTokens.type.bodyMuted)
            }
        }
        StatusPill(task.status)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
            singleLine = true,
            enabled = !isRoutineDerived,
        )
        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it },
            label = { Text("Detail", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
            singleLine = true,
            enabled = !isRoutineDerived,
        )

        if (!isRoutineDerived) {
            Text("Parent task", style = ThemeTokens.type.label)
            val parentOptions = listOf("No parent") + availableParents.map { it.title }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                parentOptions.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = label == (availableParents.firstOrNull { it.id == parentId }?.title ?: "No parent"),
                        onClick = { parentId = availableParents.firstOrNull { it.title == label }?.id },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = parentOptions.size),
                    ) {
                        Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }
        }

        OutlinedTextField(
            value = startNotBefore,
            onValueChange = { startNotBefore = it },
            label = { Text("Start not before", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
            singleLine = true,
            enabled = !isRoutineDerived,
        )
        OutlinedTextField(
            value = endNotAfter,
            onValueChange = { endNotAfter = it },
            label = { Text("End not after", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
            singleLine = true,
            enabled = !isRoutineDerived,
        )
        OutlinedTextField(
            value = estimatedDuration,
            onValueChange = { estimatedDuration = it },
            label = { Text("Estimated duration", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
            singleLine = true,
            enabled = !isRoutineDerived,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Close", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            if (!isRoutineDerived) {
                Button(
                    onClick = {
                        val draft = normalizeTaskSaveDraft(
                            title = title,
                            detail = detail,
                            parentId = parentId,
                            startNotBefore = startNotBefore,
                            endNotAfter = endNotAfter,
                            estimatedDuration = estimatedDuration,
                        )
                        onSaveTask(
                            task.id,
                            draft.title,
                            draft.detail,
                            draft.parentId,
                            draft.startNotBefore,
                            draft.endNotAfter,
                            draft.estimatedDuration,
                        )
                    },
                    enabled = !isPomodoroActive && title.isNotBlank(),
                ) {
                    Text("Save", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        if (taskDetailActionLabels(task).any { it != "Delete task" }) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Task note", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
        }
        Text("History", style = ThemeTokens.type.label)
        JournalTimeline(history)
        if (isPomodoroActive) {
            Text("Pomodoro is active. Task edits are temporarily disabled.", color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
        } else {
            val actions = taskDetailActionLabels(task)
            if ("Progress" in actions || "Complete" in actions || "Skip" in actions || "Postpone" in actions || "Pomodoro" in actions) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                    verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                ) {
                    if ("Progress" in actions) Button(onClick = { onProgress(note) }) { Text("Progress", style = ThemeTokens.type.body.withMaterialContentColor()) }
                    if ("Complete" in actions) Button(onClick = { onComplete(note) }) { Text("Complete", style = ThemeTokens.type.body.withMaterialContentColor()) }
                    if ("Skip" in actions) Button(onClick = { onSkip(note) }) { Text("Skip", style = ThemeTokens.type.body.withMaterialContentColor()) }
                    if ("Postpone" in actions) {
                        Button(
                            onClick = {
                                onPostpone(
                                    task.id,
                                    startNotBefore.ifBlank { null },
                                    endNotAfter.ifBlank { null },
                                    estimatedDuration.ifBlank { null },
                                    note.ifBlank { null },
                                )
                            },
                        ) {
                            Text("Postpone", style = ThemeTokens.type.body.withMaterialContentColor())
                        }
                    }
                    if ("Pomodoro" in actions) {
                        Button(onClick = onStartPomodoro, enabled = task.status.isOpen()) {
                            Text("Pomodoro", style = ThemeTokens.type.body.withMaterialContentColor())
                        }
                    }
                }
            }
            if ("Delete task" in actions) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                    verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                ) {
                    Button(onClick = onDelete) { Text("Delete task", style = ThemeTokens.type.body.withMaterialContentColor()) }
                }
            }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RoutineActionsSheet(
    routine: RoutineListItemView?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, String?, String?) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    if (routine == null) return
    var title by remember(routine.id) { mutableStateOf(routine.templateTitle) }
    var detail by remember(routine.id) { mutableStateOf(routine.templateDetail.orEmpty()) }
    var recurrenceRule by remember(routine.id) { mutableStateOf(routine.recurrenceRule) }
    var defaultStartNotBefore by remember(routine.id) { mutableStateOf(routine.defaultStartNotBefore.orEmpty()) }
    var defaultEstimatedDuration by remember(routine.id) { mutableStateOf(routine.defaultEstimatedDuration.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
        Text(routine.templateTitle, style = ThemeTokens.type.sectionTitle)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Routine title", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it },
            label = { Text("Routine detail", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        RoutineRecurrenceEditor(rule = recurrenceRule, onRuleChange = { recurrenceRule = it })
        Text(describeRecurrenceRule(recurrenceRule), style = ThemeTokens.type.bodyMuted)
        OutlinedTextField(
            value = defaultStartNotBefore,
            onValueChange = { defaultStartNotBefore = it },
            label = { Text("Default start not before", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = defaultEstimatedDuration,
            onValueChange = { defaultEstimatedDuration = it },
            label = { Text("Default estimated duration", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Button(
                onClick = {
                    val draft = normalizeRoutineSaveDraft(
                        id = routine.id,
                        title = title,
                        detail = detail,
                        recurrenceRule = recurrenceRule,
                        defaultStartNotBefore = defaultStartNotBefore,
                        defaultEstimatedDuration = defaultEstimatedDuration,
                    )
                    onSave(
                        draft.id,
                        draft.title,
                        draft.detail,
                        draft.recurrenceRule,
                        draft.defaultStartNotBefore,
                        draft.defaultEstimatedDuration,
                    )
                },
                enabled = title.isNotBlank() && recurrenceRule.isNotBlank(),
            ) {
                Text("Save", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            Button(onClick = onToggle) {
                Text(if (routine.isActive) "Pause" else "Activate", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            Button(onClick = onDelete) { Text("Delete", style = ThemeTokens.type.body.withMaterialContentColor()) }
            OutlinedButton(onClick = onDismiss) { Text("Close", style = ThemeTokens.type.body.withMaterialContentColor()) }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun StartPomodoroSheet(
    state: AppUiState,
    hasActive: Boolean,
    onDismiss: () -> Unit,
    onStart: (String?, PomodoroPhase) -> Unit,
) {
    val openTasks = state.pomodoroStartTasks
    var selectedTaskId by remember(openTasks) { mutableStateOf<String?>(openTasks.firstOrNull()?.taskId) }
    var selectedPhase by remember { mutableStateOf(PomodoroPhase.FOCUS) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
        Text("Start pomodoro", style = ThemeTokens.type.sectionTitle)
        if (hasActive) {
            Text("A timer is already active. Complete or stop it first.", color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
            Button(onClick = onDismiss) { Text("Close", style = ThemeTokens.type.body.withMaterialContentColor()) }
            return@Column
        }
        Text("Task", style = ThemeTokens.type.label)
        val taskOptions = listOf("No task") + openTasks.map { it.title }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            taskOptions.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = label == (openTasks.firstOrNull { it.taskId == selectedTaskId }?.title ?: "No task"),
                    onClick = { selectedTaskId = openTasks.firstOrNull { it.title == label }?.taskId },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = taskOptions.size),
                ) {
                    Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        Text("Mode", style = ThemeTokens.type.label)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            PomodoroPhase.entries.forEachIndexed { index, phase ->
                val label = phase.label()
                SegmentedButton(
                    selected = label == selectedPhase.label(),
                    onClick = { selectedPhase = pomodoroPhaseFromLabel(label) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PomodoroPhase.entries.size),
                ) {
                    Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            OutlinedButton(onClick = onDismiss) { Text("Cancel", style = ThemeTokens.type.body.withMaterialContentColor()) }
            Button(onClick = { onStart(selectedTaskId, selectedPhase) }) {
                Text("Start", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CompletePomodoroSheet(
    state: AppUiState,
    liveNow: Long,
    onDismiss: () -> Unit,
    onSave: (String, PomodoroTaskUpdate) -> Unit,
    onStop: (String) -> Unit,
) {
    val active = state.activePomodoroSession ?: return
    val remaining = active.endsAtEpochMillis - liveNow
    var note by remember(active.id) { mutableStateOf(active.note.orEmpty()) }
    var taskUpdate by remember(active.id) { mutableStateOf(PomodoroTaskUpdate.NONE) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
        Text(if (remaining <= 0) "Session complete" else "Active pomodoro", style = ThemeTokens.type.sectionTitle)
        Text(active.taskTitle ?: "Standalone timer", style = ThemeTokens.type.body)
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Session note", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        if (active.taskId != null) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PomodoroTaskUpdate.entries.forEachIndexed { index, update ->
                    val label = update.label()
                    SegmentedButton(
                        selected = label == taskUpdate.label(),
                        onClick = { taskUpdate = pomodoroTaskUpdateFromLabel(label) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PomodoroTaskUpdate.entries.size),
                    ) {
                        Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onStop(note) }) { Text("Stop", style = ThemeTokens.type.body.withMaterialContentColor()) }
            Button(onClick = { onSave(note, taskUpdate) }) {
                Text(if (remaining <= 0) "Save" else "Complete", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
        }
    }
}

@Composable
private fun rememberLiveNow() = produceState(initialValue = Clock.System.now().toEpochMilliseconds()) {
    while (true) {
        value = Clock.System.now().toEpochMilliseconds()
        delay(1_000)
    }
}

private fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.PENDING -> "Pending"
    TaskStatus.IN_PROGRESS -> "In progress"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.CANCELLED -> "Cancelled"
    TaskStatus.UNKNOWN -> "Unknown"
}

internal fun taskEventLabel(eventType: TaskEventType): String = when (eventType) {
    TaskEventType.CREATED -> "Created"
    TaskEventType.PROGRESSED -> "Progressed"
    TaskEventType.COMPLETED -> "Completed"
    TaskEventType.POSTPONED -> "Postponed"
    TaskEventType.DELETED -> "Deleted"
    TaskEventType.MISSED -> "Missed"
    TaskEventType.SKIPPED -> "Skipped"
    TaskEventType.MIGRATED -> "Migrated"
    TaskEventType.ALARM_FIRED -> "Alarm fired"
    TaskEventType.CANCELLED -> "Cancelled"
}

private fun TaskStatus.isOpen(): Boolean = this == TaskStatus.PENDING || this == TaskStatus.IN_PROGRESS || this == TaskStatus.UNKNOWN

internal fun groupTasksForSurface(tasks: List<TaskListItemView>): TaskSurfaceGroups {
    val backlog = tasks.filter { it.status.isOpen() && it.startNotBefore == null && it.endNotAfter == null }
    val scheduled = tasks.filter { it.status.isOpen() && (it.startNotBefore != null || it.endNotAfter != null) }
    val completed = tasks.filterNot { it.status.isOpen() }
    return TaskSurfaceGroups(
        backlog = backlog,
        scheduled = scheduled,
        completed = completed,
    )
}

internal fun filterTasksForSurface(tasks: List<TaskListItemView>, filter: TaskListFilter): List<TaskListItemView> = when (filter) {
    TaskListFilter.ALL_ACTIVE -> tasks.filter { it.status.isOpen() }
    TaskListFilter.BACKLOG -> tasks.filter { it.status.isOpen() && it.startNotBefore == null && it.endNotAfter == null }
    TaskListFilter.SCHEDULED -> tasks.filter { it.status.isOpen() && (it.startNotBefore != null || it.endNotAfter != null) }
    TaskListFilter.ROUTINE_DERIVED -> tasks.filter { it.status.isOpen() && it.routineId != null }
    TaskListFilter.COMPLETED -> tasks.filterNot { it.status.isOpen() }
}

internal fun taskDetailActionLabels(task: TaskListItemView): List<String> =
    if (task.status.isOpen() && task.routineId != null) {
        if (task.routineCanExecute == false) {
            emptyList()
        } else {
            buildList {
                add("Complete")
                if (task.routineCanPostpone != false) add("Postpone")
                if (task.routineCanSkip != false) add("Skip")
                add("Pomodoro")
            }
        }
    } else if (task.status.isOpen()) {
        listOf("Progress", "Complete", "Postpone", "Pomodoro", "Delete task")
    } else {
        listOf("Delete task")
    }

internal data class TaskSaveDraft(
    val title: String,
    val detail: String?,
    val parentId: String?,
    val startNotBefore: String?,
    val endNotAfter: String?,
    val estimatedDuration: String?,
)

internal enum class TaskCreateSource {
    TASKS,
    DAY,
}

internal data class TaskCreateContext(
    val source: TaskCreateSource,
    val selectedDate: String,
)

internal data class TaskCreateDateOption(
    val label: String,
    val date: String,
)

internal enum class DurationPreset(
    val label: String,
    val isoDuration: String?,
) {
    NONE("No estimate", null),
    MINUTES_15("15m", "PT15M"),
    MINUTES_30("30m", "PT30M"),
    HOUR_1("1h", "PT1H"),
    HOUR_2("2h", "PT2H"),
    CUSTOM("Custom", null),
}

internal data class TaskCreateDraft(
    val title: String = "",
    val detail: String = "",
    val hasSchedule: Boolean,
    val startDate: String?,
    val durationPreset: DurationPreset,
    val customDurationMinutes: String = "",
)

internal data class RoutineSaveDraft(
    val id: String,
    val title: String,
    val detail: String?,
    val recurrenceRule: String,
    val defaultStartNotBefore: String?,
    val defaultEstimatedDuration: String?,
)

internal enum class RoutineRecurrencePreset {
    DAILY,
    WEEKDAYS,
    CUSTOM,
}

internal data class RoutineRecurrenceEditorState(
    val preset: RoutineRecurrencePreset,
    val time: String,
    val customRule: String,
)

@Composable
private fun RoutineRecurrenceEditor(
    rule: String,
    onRuleChange: (String) -> Unit,
) {
    var state by remember(rule) { mutableStateOf(recurrenceEditorStateFor(rule)) }

    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        Text("Recurrence", style = ThemeTokens.type.label)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            RoutineRecurrencePreset.entries.forEachIndexed { index, preset ->
                val label = preset.label()
                SegmentedButton(
                    selected = label == state.preset.label(),
                    onClick = {
                        val next = state.copy(preset = recurrencePresetFromLabel(label))
                        state = next
                        onRuleChange(buildRecurrenceRule(next))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = RoutineRecurrencePreset.entries.size),
                ) {
                    Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }

        if (state.preset == RoutineRecurrencePreset.CUSTOM) {
            OutlinedTextField(
                value = state.customRule,
                onValueChange = {
                    state = state.copy(customRule = it)
                    onRuleChange(it)
                },
                label = { Text("Recurrence rule (RRULE)", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
        } else {
            OutlinedTextField(
                value = state.time,
                onValueChange = {
                    val next = state.copy(time = it)
                    state = next
                    onRuleChange(buildRecurrenceRule(next))
                },
                label = { Text("Time (HH:MM)", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
        }
    }
}

private fun RoutineRecurrencePreset.label(): String = when (this) {
    RoutineRecurrencePreset.DAILY -> "Daily"
    RoutineRecurrencePreset.WEEKDAYS -> "Weekdays"
    RoutineRecurrencePreset.CUSTOM -> "Custom"
}

private fun recurrencePresetFromLabel(label: String): RoutineRecurrencePreset = when (label) {
    "Daily" -> RoutineRecurrencePreset.DAILY
    "Weekdays" -> RoutineRecurrencePreset.WEEKDAYS
    else -> RoutineRecurrencePreset.CUSTOM
}

internal fun normalizeTaskSaveDraft(
    title: String,
    detail: String,
    parentId: String?,
    startNotBefore: String,
    endNotAfter: String,
    estimatedDuration: String,
): TaskSaveDraft = TaskSaveDraft(
    title = title.trim(),
    detail = detail.ifBlank { null },
    parentId = parentId,
    startNotBefore = startNotBefore.ifBlank { null },
    endNotAfter = endNotAfter.ifBlank { null },
    estimatedDuration = estimatedDuration.ifBlank { null },
)

internal fun taskCreateContextFor(selectedTab: AppTab, selectedDate: String): TaskCreateContext = TaskCreateContext(
    source = if (selectedTab == AppTab.TODAY) TaskCreateSource.DAY else TaskCreateSource.TASKS,
    selectedDate = selectedDate,
)

internal fun defaultTaskCreateDraft(context: TaskCreateContext): TaskCreateDraft = TaskCreateDraft(
    hasSchedule = context.source == TaskCreateSource.DAY,
    startDate = if (context.source == TaskCreateSource.DAY) context.selectedDate else null,
    durationPreset = DurationPreset.NONE,
)

internal fun taskCreateDateOptions(context: TaskCreateContext): List<TaskCreateDateOption> {
    val anchor = LocalDate.parse(context.selectedDate)
    return when (context.source) {
        TaskCreateSource.DAY -> listOf(
            TaskCreateDateOption("Selected day", context.selectedDate),
            TaskCreateDateOption("Next day", anchor.plus(1, DateTimeUnit.DAY).toString()),
            TaskCreateDateOption("In 1 week", anchor.plus(7, DateTimeUnit.DAY).toString()),
        )
        TaskCreateSource.TASKS -> listOf(
            TaskCreateDateOption("Today", context.selectedDate),
            TaskCreateDateOption("Tomorrow", anchor.plus(1, DateTimeUnit.DAY).toString()),
            TaskCreateDateOption("In 1 week", anchor.plus(7, DateTimeUnit.DAY).toString()),
        )
    }
}

internal fun normalizeTaskCreateDraft(draft: TaskCreateDraft): TaskSaveDraft {
    val normalizedStartDate = draft.startDate?.takeIf { draft.hasSchedule && it.isNotBlank() }
    return TaskSaveDraft(
        title = draft.title.trim(),
        detail = draft.detail.ifBlank { null },
        parentId = null,
        startNotBefore = normalizedStartDate?.let(::taskCreateStartOfDayIso),
        endNotAfter = null,
        estimatedDuration = normalizedStartDate?.let {
            when (draft.durationPreset) {
                DurationPreset.NONE -> null
                DurationPreset.CUSTOM -> draft.customDurationMinutes
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { minutes -> minutes > 0 }
                    ?.let { minutes -> "PT${minutes}M" }
                else -> draft.durationPreset.isoDuration
            }
        },
    )
}

internal fun taskCreateStartOfDayIso(date: String): String =
    LocalDate.parse(date).atStartOfDayIn(TimeZone.currentSystemDefault()).toString()

internal fun normalizeRoutineSaveDraft(
    id: String,
    title: String,
    detail: String,
    recurrenceRule: String,
    defaultStartNotBefore: String,
    defaultEstimatedDuration: String,
): RoutineSaveDraft = RoutineSaveDraft(
    id = id,
    title = title,
    detail = detail.ifBlank { null },
    recurrenceRule = recurrenceRule,
    defaultStartNotBefore = defaultStartNotBefore.ifBlank { null },
    defaultEstimatedDuration = defaultEstimatedDuration.ifBlank { null },
)

internal fun recurrenceEditorStateFor(rule: String): RoutineRecurrenceEditorState {
    val normalized = rule.removePrefix("RRULE:")
    val parts = normalized.split(";")
        .mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
        }
        .toMap()
    val time = recurrenceTimeLabel(parts["BYHOUR"], parts["BYMINUTE"]).orEmpty()
    return when {
        parts["FREQ"] == "DAILY" && time.isNotBlank() -> RoutineRecurrenceEditorState(
            preset = RoutineRecurrencePreset.DAILY,
            time = time,
            customRule = rule,
        )
        parts["FREQ"] == "WEEKLY" &&
            parts["BYDAY"] == "MO,TU,WE,TH,FR" &&
            time.isNotBlank() -> RoutineRecurrenceEditorState(
            preset = RoutineRecurrencePreset.WEEKDAYS,
            time = time,
            customRule = rule,
        )
        else -> RoutineRecurrenceEditorState(
            preset = RoutineRecurrencePreset.CUSTOM,
            time = "",
            customRule = rule,
        )
    }
}

internal fun buildRecurrenceRule(state: RoutineRecurrenceEditorState): String = when (state.preset) {
    RoutineRecurrencePreset.DAILY -> buildPresetRecurrenceRule(
        prefix = "FREQ=DAILY",
        time = state.time,
    )
    RoutineRecurrencePreset.WEEKDAYS -> buildPresetRecurrenceRule(
        prefix = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
        time = state.time,
    )
    RoutineRecurrencePreset.CUSTOM -> state.customRule
}

internal fun describeRecurrenceRule(rule: String): String {
    val normalized = rule.removePrefix("RRULE:")
    val parts = normalized.split(";")
        .mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
        }
        .toMap()
    val time = recurrenceTimeLabel(parts["BYHOUR"], parts["BYMINUTE"])
    return when (parts["FREQ"]) {
        "DAILY" -> listOf("Every day", time?.let { "at $it" }).filterNotNull().joinToString(" ")
        "WEEKLY" -> {
            val daySummary = parts["BYDAY"]
                ?.split(",")
                ?.mapNotNull(::weekdayLabel)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: "week"
            listOf("Every $daySummary", time?.let { "at $it" }).filterNotNull().joinToString(" ")
        }
        else -> "Custom recurrence"
    }
}

private fun recurrenceTimeLabel(hour: String?, minute: String?): String? {
    val parsedHour = hour?.toIntOrNull() ?: return null
    val parsedMinute = minute?.toIntOrNull() ?: return null
    val hh = if (parsedHour < 10) "0$parsedHour" else "$parsedHour"
    val mm = if (parsedMinute < 10) "0$parsedMinute" else "$parsedMinute"
    return "$hh:$mm"
}

private fun buildPresetRecurrenceRule(
    prefix: String,
    time: String,
): String {
    val parts = time.split(":")
    if (parts.size != 2) return ""
    val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return ""
    val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return ""
    val paddedMinute = minute.toString().padStart(2, '0')
    return "RRULE:$prefix;BYHOUR=$hour;BYMINUTE=$paddedMinute"
}

private fun weekdayLabel(code: String): String? = when (code) {
    "MO" -> "Mon"
    "TU" -> "Tue"
    "WE" -> "Wed"
    "TH" -> "Thu"
    "FR" -> "Fri"
    "SA" -> "Sat"
    "SU" -> "Sun"
    else -> null
}

private fun taskListFilterTitle(filter: TaskListFilter): String = when (filter) {
    TaskListFilter.ALL_ACTIVE -> "All active"
    TaskListFilter.BACKLOG -> "Backlog"
    TaskListFilter.SCHEDULED -> "Scheduled"
    TaskListFilter.ROUTINE_DERIVED -> "Routine-derived"
    TaskListFilter.COMPLETED -> "Completed"
}

private fun taskListFilterEmptyState(filter: TaskListFilter): String = when (filter) {
    TaskListFilter.ALL_ACTIVE -> "No active tasks."
    TaskListFilter.BACKLOG -> "No backlog tasks."
    TaskListFilter.SCHEDULED -> "No scheduled tasks."
    TaskListFilter.ROUTINE_DERIVED -> "No routine-derived tasks."
    TaskListFilter.COMPLETED -> "No completed tasks."
}

private fun AppTab.tabLabel(): String = when (this) {
    AppTab.TODAY -> "Day"
    AppTab.TASKS -> "Tasks"
    AppTab.SETTINGS -> "Settings"
}

private fun TaskSubTab.label(): String = when (this) {
    TaskSubTab.TASKS -> "Tasks"
    TaskSubTab.ROUTINES -> "Routines"
    TaskSubTab.POMODORO -> "Pomodoro"
}

private fun taskSubTabFromLabel(label: String): TaskSubTab = when (label) {
    "Routines" -> TaskSubTab.ROUTINES
    "Pomodoro" -> TaskSubTab.POMODORO
    else -> TaskSubTab.TASKS
}

private fun TaskListFilter.label(): String = when (this) {
    TaskListFilter.ALL_ACTIVE -> "All active"
    TaskListFilter.BACKLOG -> "Backlog"
    TaskListFilter.SCHEDULED -> "Scheduled"
    TaskListFilter.ROUTINE_DERIVED -> "Routine-derived"
    TaskListFilter.COMPLETED -> "Completed"
}

private fun taskListFilterFromLabel(label: String): TaskListFilter = when (label) {
    "Backlog" -> TaskListFilter.BACKLOG
    "Scheduled" -> TaskListFilter.SCHEDULED
    "Routine-derived" -> TaskListFilter.ROUTINE_DERIVED
    "Completed" -> TaskListFilter.COMPLETED
    else -> TaskListFilter.ALL_ACTIVE
}

private fun PomodoroPhase.label(): String = when (this) {
    PomodoroPhase.FOCUS -> "Work"
    PomodoroPhase.SHORT_BREAK -> "Short break"
    PomodoroPhase.LONG_BREAK -> "Long break"
}

private fun pomodoroPhaseFromLabel(label: String): PomodoroPhase = when (label) {
    "Work" -> PomodoroPhase.FOCUS
    "Short break" -> PomodoroPhase.SHORT_BREAK
    "Long break" -> PomodoroPhase.LONG_BREAK
    else -> PomodoroPhase.FOCUS
}

private fun PomodoroTaskUpdate.label(): String = when (this) {
    PomodoroTaskUpdate.NONE -> "No change"
    PomodoroTaskUpdate.PROGRESS -> "Progress"
    PomodoroTaskUpdate.COMPLETE -> "Complete"
    PomodoroTaskUpdate.CANCEL -> "Cancel task"
}

private fun pomodoroTaskUpdateFromLabel(label: String): PomodoroTaskUpdate = when (label) {
    "No change" -> PomodoroTaskUpdate.NONE
    "Progress" -> PomodoroTaskUpdate.PROGRESS
    "Complete" -> PomodoroTaskUpdate.COMPLETE
    "Cancel task" -> PomodoroTaskUpdate.CANCEL
    else -> PomodoroTaskUpdate.NONE
}

private fun DateFormatPreference.label(): String = when (this) {
    DateFormatPreference.ISO -> "ISO"
    DateFormatPreference.MONTH_DAY -> "Month day"
    DateFormatPreference.WEEKDAY_MONTH_DAY -> "Weekday"
}

private fun dateFormatFromLabel(label: String): DateFormatPreference = when (label) {
    "ISO" -> DateFormatPreference.ISO
    "Month day" -> DateFormatPreference.MONTH_DAY
    "Weekday" -> DateFormatPreference.WEEKDAY_MONTH_DAY
    else -> DateFormatPreference.ISO
}

private fun ThemePreference.label(): String = when (this) {
    ThemePreference.SYSTEM -> "System"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}

private fun themePreferenceFromLabel(label: String): ThemePreference = when (label) {
    "System" -> ThemePreference.SYSTEM
    "Light" -> ThemePreference.LIGHT
    "Dark" -> ThemePreference.DARK
    else -> ThemePreference.SYSTEM
}

private fun formatRemaining(remainingMillis: Long): String {
    val safe = if (remainingMillis > 0L) remainingMillis else 0L
    val totalSeconds = safe / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}m ${seconds.toString().padStart(2, '0')}s"
}
