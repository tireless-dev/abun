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
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.components.ActionRow
import dev.tireless.abun.ui.components.AppText
import dev.tireless.abun.ui.components.Button
import dev.tireless.abun.ui.components.EmptyState
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
import kotlin.time.Clock

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
                    )
                    AppTab.SETTINGS -> SettingsScreen(state, controller)
                }
            }
        }

        when (currentSheet) {
            OverlaySheet.CREATE_TASK -> CreateTaskSheet(
                onDismiss = { currentSheet = null },
                onCreate = {
                    controller.createTask(it)
                    currentSheet = null
                },
            )
            OverlaySheet.CREATE_ROUTINE -> CreateRoutineSheet(
                onDismiss = { currentSheet = null },
                onCreate = { title, cron, timezone ->
                    controller.createRoutine(title, cron, timezone)
                    currentSheet = null
                },
            )
            OverlaySheet.TASK_ACTIONS -> TaskActionsSheet(
                task = selectedTask,
                availableParents = state.taskView.tasks.filter { candidate ->
                    candidate.id != selectedTask?.id && candidate.routineId == null
                },
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
                onCancelTask = { note ->
                    selectedTask?.let { controller.cancelTask(it.id, note.ifBlank { null }) }
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
    var otpCode by remember { mutableStateOf("") }
    ScreenContainer(modifier = Modifier.background(ThemeTokens.colors.background)) {
        Panel {
            AppText("abun", style = ThemeTokens.type.title.copy(fontWeight = FontWeight.Bold), color = ThemeTokens.colors.primary)
            AppText("Sign in", style = ThemeTokens.type.display)
            AppText("Login with email OTP to enable cloud sync, or skip for local-only mode.", style = ThemeTokens.type.bodyMuted)
            TextField(value = state.auth.email, onValueChange = onUpdateLoginEmail, label = "Email")
            Button(label = if (state.auth.otpRequested) "Resend OTP" else "Send OTP", onClick = onRequestEmailOtp, enabled = !state.auth.isSubmitting)
            if (state.auth.otpRequested) {
                TextField(value = otpCode, onValueChange = { otpCode = it }, label = "OTP code")
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
        TaskSubTab.ROUTINES -> RoutineListScreen(state = state, onCreateRoutine = onCreateRoutine, onOpenRoutine = onOpenRoutine)
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
                RoutineRow(routine, onOpenRoutine)
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
    ) -> Unit,
) {
    var titlePrefix by remember(state.preferences) { mutableStateOf(state.preferences.titlePrefix) }
    var focusMinutes by remember(state.preferences) { mutableStateOf(state.preferences.focusMinutes.toString()) }
    var shortBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.shortBreakMinutes.toString()) }
    var longBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.longBreakMinutes.toString()) }
    var timezoneOverride by remember(state.preferences) { mutableStateOf(state.preferences.timezoneOverride) }
    var selectedDateFormat by remember(state.preferences) { mutableStateOf(state.preferences.dateFormat) }

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
private fun RoutineRow(routine: RoutineListItemView, onOpen: (RoutineListItemView) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surfaceElevated, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .padding(ThemeTokens.spacing.mdDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
    ) {
        AppText(routine.templateTitle, style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold))
        AppText("${routine.cronSchedule} • ${routine.timezone}", style = ThemeTokens.type.bodyMuted)
        AppText(if (routine.isActive) "Active" else "Paused", style = ThemeTokens.type.label)
        Button(label = "Manage", onClick = { onOpen(routine) })
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
internal fun CreateTaskSheet(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var draftTask by remember { mutableStateOf("") }
    Sheet(onDismiss = onDismiss) {
        SectionTitle("Create task")
        TextField(value = draftTask, onValueChange = { draftTask = it }, label = "Task title")
        ActionRow {
            Button(label = "Cancel", onClick = onDismiss)
            Button(label = "Create", onClick = { onCreate(draftTask) }, enabled = draftTask.isNotBlank())
        }
    }
}

@Composable
internal fun CreateRoutineSheet(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var cron by remember { mutableStateOf("0 9 * * *") }
    var timezone by remember { mutableStateOf("UTC") }
    Sheet(onDismiss = onDismiss) {
        SectionTitle("Create routine")
        TextField(value = title, onValueChange = { title = it }, label = "Routine title")
        TextField(value = cron, onValueChange = { cron = it }, label = "Cron schedule")
        TextField(value = timezone, onValueChange = { timezone = it }, label = "Timezone")
        ActionRow {
            Button(label = "Cancel", onClick = onDismiss)
            Button(label = "Create", onClick = { onCreate(title, cron, timezone) }, enabled = title.isNotBlank() && cron.isNotBlank())
        }
    }
}

@Composable
internal fun TaskActionsSheet(
    task: TaskListItemView?,
    availableParents: List<TaskListItemView>,
    isPomodoroActive: Boolean,
    onDismiss: () -> Unit,
    onSaveTask: (String, String, String?, String?, String?, String?, String?) -> Unit,
    onProgress: (String) -> Unit,
    onComplete: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onDelete: () -> Unit,
    onStartPomodoro: () -> Unit,
) {
    if (task == null) return
    var title by remember(task.id) { mutableStateOf(task.title) }
    var detail by remember(task.id) { mutableStateOf(task.detail.orEmpty()) }
    var parentId by remember(task.id) { mutableStateOf(task.parentId) }
    var startNotBefore by remember(task.id) { mutableStateOf(task.startNotBefore.orEmpty()) }
    var endNotAfter by remember(task.id) { mutableStateOf(task.endNotAfter.orEmpty()) }
    var estimatedDuration by remember(task.id) { mutableStateOf(task.estimatedDuration.orEmpty()) }
    var note by remember(task.id) { mutableStateOf("") }
    Sheet(onDismiss = onDismiss) {
        SectionTitle(task.title)
        StatusPill(task.status)
        TextField(value = title, onValueChange = { title = it }, label = "Title")
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
            Button(label = "Close", onClick = onDismiss)
            Button(
                label = "Save",
                onClick = {
                    onSaveTask(
                        task.id,
                        title,
                        detail.ifBlank { null },
                        parentId,
                        startNotBefore.ifBlank { null },
                        endNotAfter.ifBlank { null },
                        estimatedDuration.ifBlank { null },
                    )
                },
                enabled = !isPomodoroActive && title.isNotBlank(),
            )
        }
        TextField(value = note, onValueChange = { note = it }, label = "Task note")
        if (isPomodoroActive) {
            InlineError("Pomodoro is active. Task edits are temporarily disabled.")
        } else {
            ActionRow {
                Button(label = "Progress", onClick = { onProgress(note) })
                Button(label = "Complete", onClick = { onComplete(note) })
                Button(label = "Cancel task", onClick = { onCancelTask(note) })
                Button(label = "Pomodoro", onClick = onStartPomodoro, enabled = task.status.isOpen())
                Button(label = "Delete", onClick = onDelete)
            }
        }
    }
}

@Composable
internal fun RoutineActionsSheet(
    routine: RoutineListItemView?,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    if (routine == null) return
    Sheet(onDismiss = onDismiss) {
        SectionTitle(routine.templateTitle)
        AppText("${routine.cronSchedule} • ${routine.timezone}", style = ThemeTokens.type.bodyMuted)
        ActionRow {
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
    val openTasks = state.taskView.tasks.filter { it.status.isOpen() }
    var selectedTaskId by remember(openTasks) { mutableStateOf<String?>(openTasks.firstOrNull()?.id) }
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
            selected = openTasks.firstOrNull { it.id == selectedTaskId }?.title ?: "No task",
            onSelect = { label -> selectedTaskId = openTasks.firstOrNull { it.title == label }?.id },
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
