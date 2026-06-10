package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.components.ActionRow
import dev.tireless.abun.ui.components.AppText
import dev.tireless.abun.ui.components.Button
import dev.tireless.abun.ui.components.EmptyState
import dev.tireless.abun.ui.components.RecurrenceRuleEditor
import dev.tireless.abun.ui.components.InlineError
import dev.tireless.abun.ui.components.Section
import dev.tireless.abun.ui.components.SectionTitle
import dev.tireless.abun.ui.components.SegmentedControl
import dev.tireless.abun.ui.components.Sheet
import dev.tireless.abun.ui.components.TextField
import dev.tireless.abun.ui.layout.Scaffold
import dev.tireless.abun.ui.layout.ScreenContainer
import dev.tireless.abun.ui.theme.AppTheme
import dev.tireless.abun.ui.theme.ThemeTokens
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
    val selectedTaskHistory = selectedTask?.let { controller.taskHistory(it.id) }.orEmpty()

    LaunchedEffect(state.activePomodoroSession?.id, activeRemaining) {
        if (state.activePomodoroSession != null && activeRemaining <= 0) {
            currentSheet = OverlaySheet.COMPLETE_POMODORO
            controller.selectTaskSubTab(TaskSubTab.POMODORO)
        }
    }

    AppTheme {
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
        Scaffold(
            title = state.selectedTab.tabLabel(),
            selectedTab = state.selectedTab.tabLabel(),
            tabs = AppTab.entries.map { it.tabLabel() },
            onSelectTab = { tabLabel -> controller.selectTab(appTabFromLabel(tabLabel)) },
            floatingActionLabel = if (fabLabel != null && !isPomodoroActive) fabLabel else null,
            onFloatingAction = { currentSheet = OverlaySheet.CREATE_TASK },
        ) { padding ->
            ScreenContainer(
                modifier = Modifier
                    .background(ThemeTokens.colors.background)
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                applyVerticalSafeInsets = false,
            ) {
                StatusStrip(state)
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
                availableParents = state.taskView.tasks.filter { it.routineId == null },
                onDismiss = { currentSheet = null },
                onCreate = { title, detail, parentId, startNotBefore, endNotAfter, estimatedDuration ->
                    controller.createTask(title, detail, parentId, startNotBefore, endNotAfter, estimatedDuration)
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
    ScreenContainer(modifier = Modifier.background(ThemeTokens.colors.background)) {
        Panel {
            AppText("abun", style = ThemeTokens.type.title.copy(fontWeight = FontWeight.Bold), color = ThemeTokens.colors.primary)
            AppText("Sign in", style = ThemeTokens.type.display)
            AppText("Login with email OTP to enable cloud sync, or skip for local-only mode.", style = ThemeTokens.type.bodyMuted)
            TextField(value = state.auth.email, onValueChange = onUpdateLoginEmail, label = "Email")
            Button(label = if (state.auth.otpRequested) "Resend OTP" else "Send OTP", onClick = onRequestEmailOtp, enabled = !state.auth.isSubmitting)
            if (state.auth.otpRequested) {
                TextField(value = otpCode, onValueChange = { otpCode = it }, label = "OTP code")
                state.auth.debugOtpHint?.let { AppText(it, style = ThemeTokens.type.bodyMuted) }
                Button(label = "Verify and login", onClick = { onVerifyEmailOtp(otpCode) }, enabled = !state.auth.isSubmitting)
            }
            Button(label = "Skip for now", onClick = onSkipLogin, enabled = !state.auth.isSubmitting)
            state.auth.errorMessage?.let { InlineError(it) }
        }
    }
}

@Composable
private fun StatusStrip(state: AppUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
        state.syncState.lastSyncedAt?.let { AppText("Last synced: $it", style = ThemeTokens.type.bodyMuted) }
        state.syncState.errorMessage?.let { InlineError(it) }
        if (state.auth.mode == AuthMode.GUEST) {
            AppText("Local-only mode. Login anytime to sync.", style = ThemeTokens.type.bodyMuted)
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
                AppText("Daily desk", style = ThemeTokens.type.label)
                AppText("Day", style = ThemeTokens.type.title)
                AppText(state.selectedDate, style = ThemeTokens.type.bodyMuted)
            }
            Button(label = if (active == null) "Start" else formatRemaining(active.endsAtEpochMillis - liveNow), onClick = onStartPomodoro)
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
        AppText(active?.taskTitle ?: "No active timer", style = ThemeTokens.type.bodyMuted)
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
    SegmentedControl(
        options = TaskSubTab.entries.map { it.label() },
        selected = state.selectedTaskSubTab.label(),
        onSelect = { onSelectPanel(taskSubTabFromLabel(it)) },
    )

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
        SegmentedControl(
            options = TaskListFilter.entries.map { it.label() },
            selected = state.selectedTaskFilter.label(),
            onSelect = { onSelectTaskFilter(taskListFilterFromLabel(it)) },
        )
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
            Button(label = "Create", onClick = onCreateRoutine)
        }
        if (state.taskView.routines.isEmpty()) {
            EmptyState("No routines.")
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
        AppText("Timer", style = ThemeTokens.type.label)
        AppText(active?.let { formatRemaining(it.endsAtEpochMillis - liveNow) } ?: "00:00", style = ThemeTokens.type.display)
        AppText(active?.taskTitle ?: "No active timer", style = ThemeTokens.type.bodyMuted)
        if (active == null) {
            Button(label = "Start", onClick = onOpenStart)
        } else {
            AppText("${active.phase.label()} • ${active.durationMinutes}m", style = ThemeTokens.type.bodyMuted)
            Button(label = "Complete or stop", onClick = onOpenStart)
        }
    }

    Panel {
        SectionHeader("History", "Recent sessions")
        if (state.recentPomodoroSessions.isEmpty()) {
            EmptyState("No sessions.")
        } else {
            state.recentPomodoroSessions.forEach { session ->
                PomodoroRow(session)
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, controller: AbunAppController) {
    SettingsScreenContent(state = state, onUpdatePreferences = controller::updatePreferences)
}

@Composable
internal fun SettingsScreenContent(
    state: AppUiState,
    onUpdatePreferences: (
        titlePrefix: String,
        defaultAlarmLeadMinutes: Int,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        timezoneOverride: String,
        dateFormat: DateFormatPreference,
        rolloverTime: String,
    ) -> Unit,
) {
    var titlePrefix by remember(state.preferences) { mutableStateOf(state.preferences.titlePrefix) }
    var focusMinutes by remember(state.preferences) { mutableStateOf(state.preferences.focusMinutes.toString()) }
    var shortBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.shortBreakMinutes.toString()) }
    var longBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.longBreakMinutes.toString()) }
    var timezoneOverride by remember(state.preferences) { mutableStateOf(state.preferences.timezoneOverride) }
    var selectedDateFormat by remember(state.preferences) { mutableStateOf(state.preferences.dateFormat) }
    var rolloverTime by remember(state.preferences) { mutableStateOf(state.preferences.rolloverTime) }

    Panel {
        SectionHeader("Defaults", "Task")
        TextField(value = titlePrefix, onValueChange = { titlePrefix = it }, label = "Title prefix")
    }
    Panel {
        SectionHeader("Defaults", "Pomodoro")
        TextField(value = focusMinutes, onValueChange = { focusMinutes = it }, label = "Pomodoro minutes")
        TextField(value = shortBreakMinutes, onValueChange = { shortBreakMinutes = it }, label = "Short break minutes")
        TextField(value = longBreakMinutes, onValueChange = { longBreakMinutes = it }, label = "Long break minutes")
    }
    Panel {
        SectionHeader("App", "Preferences")
        TextField(value = timezoneOverride, onValueChange = { timezoneOverride = it }, label = "Timezone override")
        TextField(value = rolloverTime, onValueChange = { rolloverTime = it }, label = "Rollover time (HH:MM)")
        SegmentedControl(
            options = DateFormatPreference.entries.map { it.label() },
            selected = selectedDateFormat.label(),
            onSelect = { label -> selectedDateFormat = dateFormatFromLabel(label) },
        )
        Button(
            label = "Save",
            onClick = {
                onUpdatePreferences(
                    titlePrefix,
                    state.preferences.defaultAlarmLeadMinutes,
                    focusMinutes.toIntOrNull() ?: state.preferences.focusMinutes,
                    shortBreakMinutes.toIntOrNull() ?: state.preferences.shortBreakMinutes,
                    longBreakMinutes.toIntOrNull() ?: state.preferences.longBreakMinutes,
                    timezoneOverride,
                    selectedDateFormat,
                    rolloverTime,
                )
            },
        )
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surface, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .padding(ThemeTokens.spacing.mdDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        content = content,
    )
}

@Composable
private fun SectionHeader(eyebrow: String, title: String) {
    Column {
        AppText(eyebrow, style = ThemeTokens.type.label.copy(fontWeight = FontWeight.Bold))
        AppText(title, style = ThemeTokens.type.sectionTitle)
    }
}

@Composable
private fun MetricRow(items: List<Pair<String, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(ThemeTokens.colors.surfaceElevated, RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .padding(ThemeTokens.spacing.smDp),
            ) {
                AppText(value, style = ThemeTokens.type.title.copy(fontWeight = FontWeight.Bold))
                AppText(label, style = ThemeTokens.type.bodyMuted)
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
        EmptyState(empty)
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
        EmptyState("No history for this date.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        entries.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ThemeTokens.colors.surfaceElevated, RoundedCornerShape(ThemeTokens.radii.smallDp))
                    .padding(ThemeTokens.spacing.mdDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
            ) {
                AppText(entry.title, style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold))
                AppText("${taskEventLabel(entry.eventType)} • ${entry.eventTimeLabel}", style = ThemeTokens.type.bodyMuted)
                entry.content?.takeIf(String::isNotBlank)?.let { AppText(it, style = ThemeTokens.type.bodyMuted) }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surfaceElevated, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .padding(ThemeTokens.spacing.mdDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
    ) {
        AppText(task.title, style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
        ) {
            StatusPill(task.status)
            task.routineId?.let { AppText("Routine", style = ThemeTokens.type.bodyMuted) }
        }
        if (!compact) {
            Button(label = if (disabled) "Pomodoro active" else "Manage", onClick = { onOpenTask(task) }, enabled = !disabled)
        } else {
            Button(label = "Open", onClick = { onOpenTask(task) }, enabled = !disabled)
        }
    }
}

@Composable
private fun StatusPill(status: TaskStatus) {
    val background = when (status) {
        TaskStatus.IN_PROGRESS -> Color(0xFFEAF6FA)
        TaskStatus.PENDING -> Color(0xFFFBF6DD)
        TaskStatus.COMPLETED -> Color(0xFFEAF6EA)
        TaskStatus.CANCELLED -> Color(0xFFFFEFEF)
        TaskStatus.UNKNOWN -> ThemeTokens.colors.surfaceElevated
    }
    val color = when (status) {
        TaskStatus.IN_PROGRESS -> Color(0xFF24586B)
        TaskStatus.PENDING -> Color(0xFF695411)
        TaskStatus.COMPLETED -> ThemeTokens.colors.success
        TaskStatus.CANCELLED -> ThemeTokens.colors.error
        TaskStatus.UNKNOWN -> ThemeTokens.colors.textSecondary
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = ThemeTokens.spacing.smDp, vertical = ThemeTokens.spacing.xsDp),
    ) {
        AppText(statusLabel(status), style = ThemeTokens.type.label.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@Composable
private fun RoutineRow(
    routine: RoutineListItemView,
    onOpen: (RoutineListItemView) -> Unit,
    onRun: (RoutineListItemView) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surfaceElevated, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .padding(ThemeTokens.spacing.mdDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
    ) {
        AppText(routine.templateTitle, style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold))
        routine.templateDetail?.let { AppText(it, style = ThemeTokens.type.bodyMuted) }
        AppText(describeRecurrenceRule(routine.recurrenceRule), style = ThemeTokens.type.bodyMuted)
        routine.defaultStartNotBefore?.let { AppText("Default start: $it", style = ThemeTokens.type.label) }
        routine.defaultEstimatedDuration?.let { AppText("Default duration: $it", style = ThemeTokens.type.label) }
        AppText(if (routine.isActive) "Active" else "Paused", style = ThemeTokens.type.label)
        ActionRow {
            Button(label = "Run today", onClick = { onRun(routine) }, enabled = routine.isActive)
            Button(label = "Manage", onClick = { onOpen(routine) })
        }
    }
}

@Composable
private fun PomodoroRow(session: PomodoroSessionView) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surfaceElevated, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .padding(ThemeTokens.spacing.mdDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
    ) {
        AppText(session.phase.label(), style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold))
        AppText(session.taskTitle ?: "Standalone timer", style = ThemeTokens.type.bodyMuted)
        AppText("${session.state.name.lowercase()} • ${session.durationMinutes}m", style = ThemeTokens.type.label)
        session.note?.let { AppText(it, style = ThemeTokens.type.bodyMuted) }
    }
}

@Composable
internal fun CreateTaskSheet(
    availableParents: List<TaskListItemView>,
    onDismiss: () -> Unit,
    onCreate: (String, String?, String?, String?, String?, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var parentId by remember { mutableStateOf<String?>(null) }
    var startNotBefore by remember { mutableStateOf("") }
    var endNotAfter by remember { mutableStateOf("") }
    var estimatedDuration by remember { mutableStateOf("") }
    Sheet(onDismiss = onDismiss) {
        SectionTitle("Create task")
        TextField(value = title, onValueChange = { title = it }, label = "Task title")
        TextField(value = detail, onValueChange = { detail = it }, label = "Detail")
        AppText("Parent task", style = ThemeTokens.type.label)
        SegmentedControl(
            options = listOf("No parent") + availableParents.map { it.title },
            selected = availableParents.firstOrNull { it.id == parentId }?.title ?: "No parent",
            onSelect = { label ->
                parentId = availableParents.firstOrNull { it.title == label }?.id
            },
        )
        TextField(value = startNotBefore, onValueChange = { startNotBefore = it }, label = "Start not before")
        TextField(value = endNotAfter, onValueChange = { endNotAfter = it }, label = "End not after")
        TextField(value = estimatedDuration, onValueChange = { estimatedDuration = it }, label = "Estimated duration")
        ActionRow {
            Button(label = "Cancel", onClick = onDismiss)
            Button(
                label = "Create",
                onClick = {
                    val draft = normalizeTaskSaveDraft(
                        title = title,
                        detail = detail,
                        parentId = parentId,
                        startNotBefore = startNotBefore,
                        endNotAfter = endNotAfter,
                        estimatedDuration = estimatedDuration,
                    )
                    onCreate(
                        draft.title,
                        draft.detail,
                        draft.parentId,
                        draft.startNotBefore,
                        draft.endNotAfter,
                        draft.estimatedDuration,
                    )
                },
                enabled = title.isNotBlank(),
            )
        }
    }
}

@Composable
internal fun CreateRoutineSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?, String, String?, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var recurrenceRule by remember { mutableStateOf("RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0") }
    var defaultStartNotBefore by remember { mutableStateOf("") }
    var defaultEstimatedDuration by remember { mutableStateOf("") }
    Sheet(onDismiss = onDismiss) {
        SectionTitle("Create routine")
        TextField(value = title, onValueChange = { title = it }, label = "Routine title")
        TextField(value = detail, onValueChange = { detail = it }, label = "Routine detail")
        RecurrenceRuleEditor(rule = recurrenceRule, onRuleChange = { recurrenceRule = it })
        AppText(describeRecurrenceRule(recurrenceRule), style = ThemeTokens.type.bodyMuted)
        TextField(value = defaultStartNotBefore, onValueChange = { defaultStartNotBefore = it }, label = "Default start not before")
        TextField(value = defaultEstimatedDuration, onValueChange = { defaultEstimatedDuration = it }, label = "Default estimated duration")
        ActionRow {
            Button(label = "Cancel", onClick = onDismiss)
            Button(
                label = "Create",
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
            )
        }
    }
}

@Composable
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

    Sheet(onDismiss = onDismiss) {
        SectionTitle(if (isRoutineDerived) "Routine: ${task.title}" else task.title)
        if (isRoutineDerived) {
            AppText("Routine: ${routine?.templateTitle}", style = ThemeTokens.type.bodyMuted)
            nextOccurrence?.let {
                AppText("Next occurrence: $it", style = ThemeTokens.type.bodyMuted)
            }
        }
        StatusPill(task.status)
        TextField(value = title, onValueChange = { title = it }, label = "Title", enabled = !isRoutineDerived)
        TextField(value = detail, onValueChange = { detail = it }, label = "Detail", enabled = !isRoutineDerived)

        if (!isRoutineDerived) {
            AppText("Parent task", style = ThemeTokens.type.label)
            SegmentedControl(
                options = listOf("No parent") + availableParents.map { it.title },
                selected = availableParents.firstOrNull { it.id == parentId }?.title ?: "No parent",
                onSelect = { label ->
                    parentId = availableParents.firstOrNull { it.title == label }?.id
                },
            )
        }

        TextField(value = startNotBefore, onValueChange = { startNotBefore = it }, label = "Start not before", enabled = !isRoutineDerived)
        TextField(value = endNotAfter, onValueChange = { endNotAfter = it }, label = "End not after", enabled = !isRoutineDerived)
        TextField(value = estimatedDuration, onValueChange = { estimatedDuration = it }, label = "Estimated duration", enabled = !isRoutineDerived)

        ActionRow {
            Button(label = "Close", onClick = onDismiss)
            if (!isRoutineDerived) {
                Button(
                    label = "Save",
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
                )
            }
        }
        if (taskDetailActionLabels(task).any { it != "Delete task" }) {
            TextField(value = note, onValueChange = { note = it }, label = "Task note")
        }
        AppText("History", style = ThemeTokens.type.label)
        JournalTimeline(history)
        if (isPomodoroActive) {
            InlineError("Pomodoro is active. Task edits are temporarily disabled.")
        } else {
            val actions = taskDetailActionLabels(task)
            if ("Progress" in actions || "Complete" in actions || "Skip" in actions || "Postpone" in actions || "Pomodoro" in actions) {
                ActionRow {
                    if ("Progress" in actions) Button(label = "Progress", onClick = { onProgress(note) })
                    if ("Complete" in actions) Button(label = "Complete", onClick = { onComplete(note) })
                    if ("Skip" in actions) Button(label = "Skip", onClick = { onSkip(note) })
                    if ("Postpone" in actions) {
                        Button(
                            label = "Postpone",
                            onClick = {
                                onPostpone(
                                    task.id,
                                    startNotBefore.ifBlank { null },
                                    endNotAfter.ifBlank { null },
                                    estimatedDuration.ifBlank { null },
                                    note.ifBlank { null },
                                )
                            },
                        )
                    }
                    if ("Pomodoro" in actions) Button(label = "Pomodoro", onClick = onStartPomodoro, enabled = task.status.isOpen())
                }
            }
            if ("Delete task" in actions) {
                ActionRow {
                    Button(label = "Delete task", onClick = onDelete)
                }
            }
        }
    }
}

@Composable
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
    Sheet(onDismiss = onDismiss) {
        SectionTitle(routine.templateTitle)
        TextField(value = title, onValueChange = { title = it }, label = "Routine title")
        TextField(value = detail, onValueChange = { detail = it }, label = "Routine detail")
        RecurrenceRuleEditor(rule = recurrenceRule, onRuleChange = { recurrenceRule = it })
        AppText(describeRecurrenceRule(recurrenceRule), style = ThemeTokens.type.bodyMuted)
        TextField(
            value = defaultStartNotBefore,
            onValueChange = { defaultStartNotBefore = it },
            label = "Default start not before",
        )
        TextField(
            value = defaultEstimatedDuration,
            onValueChange = { defaultEstimatedDuration = it },
            label = "Default estimated duration",
        )
        ActionRow {
            Button(
                label = "Save",
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
            )
            Button(label = if (routine.isActive) "Pause" else "Activate", onClick = onToggle)
            Button(label = "Delete", onClick = onDelete)
            Button(label = "Close", onClick = onDismiss)
        }
    }
}

@Composable
internal fun StartPomodoroSheet(
    state: AppUiState,
    hasActive: Boolean,
    onDismiss: () -> Unit,
    onStart: (String?, PomodoroPhase) -> Unit,
) {
    val openTasks = state.pomodoroStartTasks
    var selectedTaskId by remember(openTasks) { mutableStateOf<String?>(openTasks.firstOrNull()?.taskId) }
    var selectedPhase by remember { mutableStateOf(PomodoroPhase.FOCUS) }

    Sheet(onDismiss = onDismiss) {
        SectionTitle("Start pomodoro")
        if (hasActive) {
            InlineError("A timer is already active. Complete or stop it first.")
            Button(label = "Close", onClick = onDismiss)
            return@Sheet
        }
        AppText("Task", style = ThemeTokens.type.label)
        SegmentedControl(
            options = listOf("No task") + openTasks.map { it.title },
            selected = openTasks.firstOrNull { it.taskId == selectedTaskId }?.title ?: "No task",
            onSelect = { label -> selectedTaskId = openTasks.firstOrNull { it.title == label }?.taskId },
        )
        AppText("Mode", style = ThemeTokens.type.label)
        SegmentedControl(
            options = PomodoroPhase.entries.map { it.label() },
            selected = selectedPhase.label(),
            onSelect = { label -> selectedPhase = pomodoroPhaseFromLabel(label) },
        )
        ActionRow {
            Button(label = "Cancel", onClick = onDismiss)
            Button(label = "Start", onClick = { onStart(selectedTaskId, selectedPhase) })
        }
    }
}

@Composable
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

    Sheet(onDismiss = onDismiss) {
        SectionTitle(if (remaining <= 0) "Session complete" else "Active pomodoro")
        AppText(active.taskTitle ?: "Standalone timer", style = ThemeTokens.type.body)
        TextField(value = note, onValueChange = { note = it }, label = "Session note")
        if (active.taskId != null) {
            SegmentedControl(
                options = PomodoroTaskUpdate.entries.map { it.label() },
                selected = taskUpdate.label(),
                onSelect = { label -> taskUpdate = pomodoroTaskUpdateFromLabel(label) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp), modifier = Modifier.fillMaxWidth()) {
            Button(label = "Stop", onClick = { onStop(note) })
            Button(label = if (remaining <= 0) "Save" else "Complete", onClick = { onSave(note, taskUpdate) })
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

private fun appTabFromLabel(label: String): AppTab = when (label) {
    "Day" -> AppTab.TODAY
    "Tasks" -> AppTab.TASKS
    "Settings" -> AppTab.SETTINGS
    else -> AppTab.TODAY
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

private fun formatRemaining(remainingMillis: Long): String {
    val safe = if (remainingMillis > 0L) remainingMillis else 0L
    val totalSeconds = safe / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}m ${seconds.toString().padStart(2, '0')}s"
}
