package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AgendaTaskItemView
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.designsystem.ActionRow
import dev.tireless.abun.designsystem.AppButton
import dev.tireless.abun.designsystem.AppScaffold
import dev.tireless.abun.designsystem.AppSegmentedControl
import dev.tireless.abun.designsystem.AppSheet
import dev.tireless.abun.designsystem.AppTextField
import dev.tireless.abun.designsystem.AppTheme
import dev.tireless.abun.designsystem.EmptyState
import dev.tireless.abun.designsystem.InlineError
import dev.tireless.abun.designsystem.ScreenContainer
import dev.tireless.abun.designsystem.SectionCard
import dev.tireless.abun.designsystem.SectionTitle
import dev.tireless.abun.designsystem.ThemeTokens
import dev.tireless.abun.sync.TaskStatus
import kotlinx.coroutines.delay
import kotlin.time.Clock

private enum class OverlaySheet {
    CREATE_TASK,
    TASK_ACTIONS,
    START_FOCUS,
    COMPLETE_FOCUS,
}

@Composable
@Preview
fun App() {
    val controller = rememberAbunAppController()
    val state by controller.state.collectAsState()
    val liveNow by rememberLiveNow()
    val activeSession = state.activePomodoroSession
    val activeRemaining = activeSession?.let { it.endsAtEpochMillis - liveNow } ?: 0L
    val isFocusModeActive = activeSession != null && activeRemaining > 0
    var currentSheet by remember { mutableStateOf<OverlaySheet?>(null) }
    var selectedTaskForAction by remember { mutableStateOf<TaskListItemView?>(null) }

    LaunchedEffect(state.activePomodoroSession?.id, activeRemaining) {
        if (state.activePomodoroSession != null && activeRemaining <= 0) {
            currentSheet = OverlaySheet.COMPLETE_FOCUS
            controller.selectTab(AppTab.FOCUS)
        }
    }

    AppTheme {
        if (state.auth.showGuide) {
            GuideScreen(state, controller)
            return@AppTheme
        }

        val title = when (state.selectedTab) {
            AppTab.TODAY -> "Today"
            AppTab.TASKS -> "Tasks"
            AppTab.FOCUS -> "Focus"
            AppTab.SETTINGS -> "Settings"
        }

        val fabEnabled = state.selectedTab == AppTab.TODAY || state.selectedTab == AppTab.TASKS
        AppScaffold(
            title = title,
            selectedTab = state.selectedTab.tabLabel(),
            tabs = AppTab.entries.map { it.tabLabel() },
            onSelectTab = { tabLabel -> controller.selectTab(appTabFromLabel(tabLabel)) },
            floatingActionLabel = if (fabEnabled && !isFocusModeActive) "Task" else null,
            onFloatingAction = { currentSheet = OverlaySheet.CREATE_TASK },
        ) { padding ->
            ScreenContainer(
                modifier = Modifier
                    .background(ThemeTokens.colors.background)
                    .padding(padding),
            ) {
                state.syncState.lastSyncedAt?.let { androidx.compose.material3.Text("Last synced: $it", style = ThemeTokens.type.bodyMuted) }
                state.syncState.errorMessage?.let { InlineError(it) }
                if (state.auth.mode == AuthMode.GUEST) {
                    androidx.compose.material3.Text("Local-only mode. Login anytime to sync.", style = ThemeTokens.type.bodyMuted)
                }

                when (state.selectedTab) {
                    AppTab.TODAY -> TodayScreen(state, isFocusModeActive) { currentSheet = OverlaySheet.START_FOCUS }
                    AppTab.TASKS -> TasksScreen(
                        state = state,
                        isFocusModeActive = isFocusModeActive,
                        onOpenActions = {
                            selectedTaskForAction = it
                            currentSheet = OverlaySheet.TASK_ACTIONS
                        },
                    )
                    AppTab.FOCUS -> FocusScreen(state, liveNow) { currentSheet = OverlaySheet.START_FOCUS }
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
            OverlaySheet.TASK_ACTIONS -> TaskActionsSheet(
                task = selectedTaskForAction,
                isFocusModeActive = isFocusModeActive,
                onDismiss = { currentSheet = null },
                onProgress = {
                    selectedTaskForAction?.let { controller.progressTask(it.id) }
                    currentSheet = null
                },
                onComplete = {
                    selectedTaskForAction?.let { controller.completeTask(it.id) }
                    currentSheet = null
                },
                onDelete = {
                    selectedTaskForAction?.let { controller.deleteTask(it.id) }
                    currentSheet = null
                },
                onStartFocus = {
                    selectedTaskForAction?.let { controller.startPomodoro(it.id, PomodoroPhase.FOCUS) }
                    controller.selectTab(AppTab.FOCUS)
                    currentSheet = null
                },
            )
            OverlaySheet.START_FOCUS -> StartFocusSheet(
                state = state,
                hasActive = activeSession != null && activeRemaining > 0,
                onDismiss = { currentSheet = null },
                onStart = { taskId, phase ->
                    controller.startPomodoro(taskId, phase)
                    controller.selectTab(AppTab.FOCUS)
                    currentSheet = null
                },
            )
            OverlaySheet.COMPLETE_FOCUS -> CompleteFocusSheet(
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
private fun GuideScreen(state: AppUiState, controller: AbunAppController) {
    var otpCode by remember { mutableStateOf("") }
    ScreenContainer(modifier = Modifier.background(ThemeTokens.colors.background)) {
        androidx.compose.material3.Text("Welcome to abun", style = ThemeTokens.type.title)
        androidx.compose.material3.Text("Login with email OTP to enable cloud sync, or skip for local-only mode.", style = ThemeTokens.type.body)
        AppTextField(value = state.auth.email, onValueChange = controller::updateLoginEmail, label = "Email")
        AppButton(label = if (state.auth.otpRequested) "Resend OTP" else "Send OTP", onClick = controller::requestEmailOtp, enabled = !state.auth.isSubmitting)
        if (state.auth.otpRequested) {
            AppTextField(value = otpCode, onValueChange = { otpCode = it }, label = "OTP code")
            AppButton(label = "Verify and Login", onClick = { controller.verifyEmailOtp(otpCode) }, enabled = !state.auth.isSubmitting)
        }
        AppButton(label = "Skip for now", onClick = controller::skipLogin, enabled = !state.auth.isSubmitting)
        state.auth.errorMessage?.let { InlineError(it) }
    }
}

@Composable
private fun TodayScreen(state: AppUiState, isFocusModeActive: Boolean, onStartFocus: () -> Unit) {
    SectionCard {
        SectionTitle("Summary")
        androidx.compose.material3.Text("Date: ${state.selectedDate}", style = ThemeTokens.type.body)
        androidx.compose.material3.Text("Current tasks: ${state.today.currentTasks.size}", style = ThemeTokens.type.body)
        androidx.compose.material3.Text("Upcoming tasks: ${state.today.upcomingTasks.size}", style = ThemeTokens.type.body)
        if (!isFocusModeActive) {
            AppButton(label = "Start focus", onClick = onStartFocus)
        }
    }
    AgendaSection("Current", state.today.currentTasks)
    AgendaSection("Upcoming", state.today.upcomingTasks)
    JournalSection(state.today.journalEntries)
}

@Composable
private fun AgendaSection(title: String, items: List<AgendaTaskItemView>) {
    SectionTitle(title)
    if (items.isEmpty()) {
        EmptyState("No tasks in this section.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        items.forEach { item ->
            SectionCard {
                androidx.compose.material3.Text(item.title, style = ThemeTokens.type.sectionTitle)
                androidx.compose.material3.Text(statusLabel(item.status), style = ThemeTokens.type.body)
                item.triggerTimeLabel?.let { androidx.compose.material3.Text(it, style = ThemeTokens.type.bodyMuted) }
            }
        }
    }
}

@Composable
private fun JournalSection(entries: List<JournalEntryView>) {
    SectionTitle("Today journal")
    if (entries.isEmpty()) {
        EmptyState("No journal entries yet.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        items(entries, key = { it.eventId }) { entry ->
            SectionCard {
                androidx.compose.material3.Text(entry.title, style = ThemeTokens.type.body)
                androidx.compose.material3.Text("${entry.eventType.name} at ${entry.eventTimeLabel}", style = ThemeTokens.type.bodyMuted)
                entry.content?.let { androidx.compose.material3.Text(it, style = ThemeTokens.type.bodyMuted) }
            }
        }
    }
}

@Composable
private fun TasksScreen(
    state: AppUiState,
    isFocusModeActive: Boolean,
    onOpenActions: (TaskListItemView) -> Unit,
) {
    SectionTitle("Tasks")
    if (state.taskView.tasks.isEmpty()) {
        EmptyState("No tasks yet.")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
            items(state.taskView.tasks, key = { it.id }) { task ->
                SectionCard {
                    androidx.compose.material3.Text(task.title, style = ThemeTokens.type.sectionTitle)
                    androidx.compose.material3.Text(statusLabel(task.status), style = ThemeTokens.type.bodyMuted)
                    if (!isFocusModeActive) {
                        AppButton(label = "Manage", onClick = { onOpenActions(task) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusScreen(state: AppUiState, liveNow: Long, onOpenStart: () -> Unit) {
    SectionCard {
        SectionTitle("Pomodoro")
        val active = state.activePomodoroSession
        if (active == null) {
            androidx.compose.material3.Text("No active timer.", style = ThemeTokens.type.body)
            AppButton(label = "Start timer", onClick = onOpenStart)
        } else {
            val remaining = active.endsAtEpochMillis - liveNow
            androidx.compose.material3.Text(active.taskTitle ?: "Standalone focus", style = ThemeTokens.type.body)
            androidx.compose.material3.Text("Mode: ${active.phase.label()}", style = ThemeTokens.type.bodyMuted)
            androidx.compose.material3.Text("Remaining: ${formatRemaining(remaining)}", style = ThemeTokens.type.bodyMuted)
            if (remaining > 0) {
                androidx.compose.material3.Text("Focus mode active. Editing is limited.", style = ThemeTokens.type.label)
            }
        }
    }

    if (state.recentPomodoroSessions.isNotEmpty()) {
        SectionTitle("Recent sessions")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
            items(state.recentPomodoroSessions, key = { it.id }) { session ->
                SectionCard {
                    androidx.compose.material3.Text(session.taskTitle ?: "Standalone", style = ThemeTokens.type.body)
                    androidx.compose.material3.Text("${session.phase.label()} • ${session.durationMinutes}m", style = ThemeTokens.type.bodyMuted)
                    session.note?.let { androidx.compose.material3.Text(it, style = ThemeTokens.type.bodyMuted) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppUiState, controller: AbunAppController) {
    var titlePrefix by remember(state.preferences) { mutableStateOf(state.preferences.titlePrefix) }
    var defaultAlarmLead by remember(state.preferences) { mutableStateOf(state.preferences.defaultAlarmLeadMinutes.toString()) }
    var focusMinutes by remember(state.preferences) { mutableStateOf(state.preferences.focusMinutes.toString()) }
    var shortBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.shortBreakMinutes.toString()) }
    var longBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.longBreakMinutes.toString()) }
    var timezoneOverride by remember(state.preferences) { mutableStateOf(state.preferences.timezoneOverride) }
    var selectedDateFormat by remember(state.preferences) { mutableStateOf(state.preferences.dateFormat) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        item {
            SectionCard {
                SectionTitle("Task defaults")
                AppTextField(value = titlePrefix, onValueChange = { titlePrefix = it }, label = "Title prefix")
                AppTextField(value = defaultAlarmLead, onValueChange = { defaultAlarmLead = it }, label = "Default alarm lead minutes")
            }
        }
        item {
            SectionCard {
                SectionTitle("Pomodoro defaults")
                AppTextField(value = focusMinutes, onValueChange = { focusMinutes = it }, label = "Focus minutes")
                AppTextField(value = shortBreakMinutes, onValueChange = { shortBreakMinutes = it }, label = "Short break minutes")
                AppTextField(value = longBreakMinutes, onValueChange = { longBreakMinutes = it }, label = "Long break minutes")
            }
        }
        item {
            SectionCard {
                SectionTitle("App preferences")
                AppTextField(value = timezoneOverride, onValueChange = { timezoneOverride = it }, label = "Timezone override")
                AppSegmentedControl(
                    options = DateFormatPreference.entries.map { it.label() },
                    selected = selectedDateFormat.label(),
                    onSelect = { label -> selectedDateFormat = dateFormatFromLabel(label) },
                )
                AppButton(
                    label = "Save settings",
                    onClick = {
                        controller.updatePreferences(
                            titlePrefix = titlePrefix,
                            defaultAlarmLeadMinutes = defaultAlarmLead.toIntOrNull() ?: state.preferences.defaultAlarmLeadMinutes,
                            focusMinutes = focusMinutes.toIntOrNull() ?: state.preferences.focusMinutes,
                            shortBreakMinutes = shortBreakMinutes.toIntOrNull() ?: state.preferences.shortBreakMinutes,
                            longBreakMinutes = longBreakMinutes.toIntOrNull() ?: state.preferences.longBreakMinutes,
                            timezoneOverride = timezoneOverride,
                            dateFormat = selectedDateFormat,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CreateTaskSheet(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var draftTask by remember { mutableStateOf("") }
    AppSheet(onDismiss = onDismiss) {
        SectionTitle("Create task")
        AppTextField(value = draftTask, onValueChange = { draftTask = it }, label = "Task title")
        ActionRow {
            AppButton(label = "Cancel", onClick = onDismiss)
            AppButton(label = "Create", onClick = { onCreate(draftTask) }, enabled = draftTask.isNotBlank())
        }
    }
}

@Composable
private fun TaskActionsSheet(
    task: TaskListItemView?,
    isFocusModeActive: Boolean,
    onDismiss: () -> Unit,
    onProgress: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: () -> Unit,
) {
    if (task == null) return
    AppSheet(onDismiss = onDismiss) {
        SectionTitle(task.title)
        androidx.compose.material3.Text(statusLabel(task.status), style = ThemeTokens.type.bodyMuted)
        if (isFocusModeActive) {
            InlineError("Focus mode is active. Task edits are temporarily disabled.")
        } else {
            ActionRow {
                AppButton(label = "Progress", onClick = onProgress)
                AppButton(label = "Complete", onClick = onComplete)
                AppButton(label = "Delete", onClick = onDelete)
                AppButton(label = "Start focus", onClick = onStartFocus)
            }
        }
    }
}

@Composable
private fun StartFocusSheet(
    state: AppUiState,
    hasActive: Boolean,
    onDismiss: () -> Unit,
    onStart: (String?, PomodoroPhase) -> Unit,
) {
    var selectedTaskId by remember(state.taskView.tasks) { mutableStateOf<String?>(state.taskView.tasks.firstOrNull()?.id) }
    var selectedPhase by remember { mutableStateOf(PomodoroPhase.FOCUS) }

    AppSheet(onDismiss = onDismiss) {
        SectionTitle("Start focus")
        if (hasActive) {
            InlineError("A timer is already active. Stop or complete it first.")
            AppButton(label = "Close", onClick = onDismiss)
            return@AppSheet
        }
        androidx.compose.material3.Text("Task", style = ThemeTokens.type.label)
        AppSegmentedControl(
            options = listOf("No task") + state.taskView.tasks.map { it.title },
            selected = state.taskView.tasks.firstOrNull { it.id == selectedTaskId }?.title ?: "No task",
            onSelect = { label ->
                selectedTaskId = state.taskView.tasks.firstOrNull { it.title == label }?.id
            },
        )
        androidx.compose.material3.Text("Mode", style = ThemeTokens.type.label)
        AppSegmentedControl(
            options = PomodoroPhase.entries.map { it.label() },
            selected = selectedPhase.label(),
            onSelect = { label -> selectedPhase = pomodoroPhaseFromLabel(label) },
        )
        ActionRow {
            AppButton(label = "Cancel", onClick = onDismiss)
            AppButton(label = "Start", onClick = { onStart(selectedTaskId, selectedPhase) })
        }
    }
}

@Composable
private fun CompleteFocusSheet(
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

    AppSheet(onDismiss = onDismiss) {
        SectionTitle(if (remaining <= 0) "Session complete" else "Active focus")
        androidx.compose.material3.Text(active.taskTitle ?: "Standalone focus", style = ThemeTokens.type.body)
        AppTextField(value = note, onValueChange = { note = it }, label = "Log note")
        if (active.taskId != null) {
            AppSegmentedControl(
                options = PomodoroTaskUpdate.entries.map { it.label() },
                selected = taskUpdate.label(),
                onSelect = { label -> taskUpdate = pomodoroTaskUpdateFromLabel(label) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp), modifier = Modifier.fillMaxWidth()) {
            AppButton(label = "Stop", onClick = { onStop(note) })
            AppButton(label = if (remaining <= 0) "Save session" else "Save & close", onClick = { onSave(note, taskUpdate) })
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

private fun AppTab.tabLabel(): String = when (this) {
    AppTab.TODAY -> "Today"
    AppTab.TASKS -> "Tasks"
    AppTab.FOCUS -> "Focus"
    AppTab.SETTINGS -> "Settings"
}

private fun appTabFromLabel(label: String): AppTab = when (label) {
    "Today" -> AppTab.TODAY
    "Tasks" -> AppTab.TASKS
    "Focus" -> AppTab.FOCUS
    "Settings" -> AppTab.SETTINGS
    else -> AppTab.TODAY
}

private fun PomodoroPhase.label(): String = when (this) {
    PomodoroPhase.FOCUS -> "Focus"
    PomodoroPhase.SHORT_BREAK -> "Short break"
    PomodoroPhase.LONG_BREAK -> "Long break"
}

private fun pomodoroPhaseFromLabel(label: String): PomodoroPhase = when (label) {
    "Focus" -> PomodoroPhase.FOCUS
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
