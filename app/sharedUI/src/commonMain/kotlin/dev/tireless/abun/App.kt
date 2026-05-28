package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AgendaTaskItemView
import dev.tireless.abun.app.AlarmListItemView
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.designsystem.ActionRow
import dev.tireless.abun.designsystem.AppTheme
import dev.tireless.abun.designsystem.ThemeTokens
import dev.tireless.abun.designsystem.EmptyState
import dev.tireless.abun.designsystem.InlineError
import dev.tireless.abun.designsystem.ScreenContainer
import dev.tireless.abun.designsystem.SectionCard
import dev.tireless.abun.designsystem.SectionTitle
import dev.tireless.abun.sync.TaskStatus
import kotlinx.coroutines.delay
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    val controller = rememberAbunAppController()
    val state by controller.state.collectAsState()
    val liveNow by rememberLiveNow()
    val activeTimerLabel = state.activePomodoroSession?.let { formatRemaining(it.endsAtEpochMillis - liveNow) }
    val pendingDeletes = remember { mutableStateListOf<PendingDelete>() }
    var pendingNow by remember { mutableLongStateOf(liveNow) }

    fun requestDelete(kind: PendingDeleteKind, id: String, label: String) {
        val key = "$kind:$id"
        if (pendingDeletes.any { it.key == key }) return
        pendingDeletes += PendingDelete(key = key, kind = kind, id = id, label = label, executeAtMillis = Clock.System.now().toEpochMilliseconds() + 5_000L)
    }

    fun undoDelete(key: String) {
        pendingDeletes.removeAll { it.key == key }
    }

    LaunchedEffect(pendingDeletes.size) {
        while (pendingDeletes.isNotEmpty()) {
            delay(250)
            val now = Clock.System.now().toEpochMilliseconds()
            pendingNow = now
            val due = pendingDeletes.filter { it.executeAtMillis <= now }
            due.forEach { pending ->
                when (pending.kind) {
                    PendingDeleteKind.TASK -> controller.deleteTask(pending.id)
                    PendingDeleteKind.ROUTINE -> controller.deleteRoutine(pending.id)
                    PendingDeleteKind.ALARM -> controller.deleteAlarm(pending.id)
                }
            }
            if (due.isNotEmpty()) pendingDeletes.removeAll(due.toSet())
        }
    }

    AppTheme {
        if (state.auth.showGuide) {
            GuideScreen(state, controller)
            return@AppTheme
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (state.isPreferencesOpen) "Preferences" else "abun", fontWeight = FontWeight.Bold) },
                    actions = {
                        if (!state.isPreferencesOpen) {
                            TextButton(onClick = controller::openPomodoroDialog) {
                                Text(activeTimerLabel?.let { "Timer $it" } ?: "Timer")
                            }
                        }
                        TextButton(onClick = controller::syncNow) {
                            Text(if (state.syncState.isSyncing) "Syncing..." else "Sync")
                        }
                        TextButton(onClick = if (state.isPreferencesOpen) controller::closePreferences else controller::openPreferences) {
                            Text(if (state.isPreferencesOpen) "Close" else "Settings")
                        }
                    },
                )
            },
            bottomBar = {
                if (!state.isPreferencesOpen) {
                    NavigationBar {
                        NavigationBarItem(selected = state.selectedTab == AppTab.TODAY, onClick = { controller.selectTab(AppTab.TODAY) }, label = { Text("Today") }, icon = {})
                        NavigationBarItem(selected = state.selectedTab == AppTab.TASKS, onClick = { controller.selectTab(AppTab.TASKS) }, label = { Text("Task") }, icon = {})
                    }
                }
            },
        ) { padding ->
            ScreenContainer(
                modifier = Modifier
                    .background(ThemeTokens.colors.background)
                    .padding(padding),
            ) {
                state.syncState.lastSyncedAt?.let { Text("Last synced: $it", style = ThemeTokens.type.bodyMuted) }
                state.syncState.errorMessage?.let { InlineError(it) }
                if (state.auth.mode == AuthMode.GUEST) {
                    Text("Local-only mode. Login anytime to sync.", style = ThemeTokens.type.bodyMuted)
                }
                if (pendingDeletes.isNotEmpty()) {
                    SectionCard {
                        SectionTitle("Pending deletes")
                        pendingDeletes.forEach { pending ->
                            Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp), modifier = Modifier.fillMaxWidth()) {
                                val remaining = ((pending.executeAtMillis - pendingNow).coerceAtLeast(0L) + 999L) / 1_000L
                                Text("${pending.label} (${remaining}s)", modifier = Modifier.weight(1f), style = ThemeTokens.type.body)
                                TextButton(onClick = { undoDelete(pending.key) }) { Text("Undo") }
                            }
                        }
                    }
                }

                when {
                    state.isPreferencesOpen -> PreferencesScreen(state, controller)
                    state.selectedTab == AppTab.TODAY -> TodayScreen(state)
                    else -> TaskScreen(
                        state = state,
                        controller = controller,
                        onDeleteTask = { id, title -> requestDelete(PendingDeleteKind.TASK, id, "Task \"$title\"") },
                        onDeleteRoutine = { id, title -> requestDelete(PendingDeleteKind.ROUTINE, id, "Routine \"$title\"") },
                        onDeleteAlarm = { id, title -> requestDelete(PendingDeleteKind.ALARM, id, "Alarm for $title") },
                    )
                }
            }

            if (state.isPomodoroDialogOpen) {
                PomodoroDialog(state, controller, liveNow)
            }
        }
    }
}

@Composable
private fun GuideScreen(state: AppUiState, controller: AbunAppController) {
    var otpCode by remember { mutableStateOf("") }
    ScreenContainer(modifier = Modifier.background(ThemeTokens.colors.background)) {
        Text("Welcome to abun", style = ThemeTokens.type.title)
        Text("Login with email OTP to enable cloud sync, or skip for local-only mode.", style = ThemeTokens.type.body)
        OutlinedTextField(
            value = state.auth.email,
            onValueChange = controller::updateLoginEmail,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
        )
        Button(onClick = controller::requestEmailOtp, enabled = !state.auth.isSubmitting) {
            Text(if (state.auth.otpRequested) "Resend OTP" else "Send OTP")
        }
        if (state.auth.otpRequested) {
            OutlinedTextField(
                value = otpCode,
                onValueChange = { otpCode = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OTP code") },
            )
            Button(onClick = { controller.verifyEmailOtp(otpCode) }, enabled = !state.auth.isSubmitting) {
                Text("Verify and Login")
            }
        }
        TextButton(onClick = controller::skipLogin, enabled = !state.auth.isSubmitting) {
            Text("Skip for now")
        }
        state.auth.errorMessage?.let { InlineError(it) }
    }
}

@Composable
private fun TodayScreen(state: AppUiState) {
    Text("Today: ${state.selectedDate}", style = ThemeTokens.type.sectionTitle)
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
                Text(item.title, style = ThemeTokens.type.sectionTitle)
                Text(statusLabel(item.status), style = ThemeTokens.type.body)
                item.triggerTimeLabel?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
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
                Text(entry.title, fontWeight = FontWeight.SemiBold, style = ThemeTokens.type.body)
                Text("${entry.eventType.name} at ${entry.eventTimeLabel}", style = ThemeTokens.type.bodyMuted)
                entry.content?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
            }
        }
    }
}

@Composable
private fun TaskScreen(
    state: AppUiState,
    controller: AbunAppController,
    onDeleteTask: (id: String, title: String) -> Unit,
    onDeleteRoutine: (id: String, title: String) -> Unit,
    onDeleteAlarm: (id: String, title: String) -> Unit,
) {
    TabRow(selectedTabIndex = state.selectedTaskSubTab.ordinal) {
        TaskSubTab.entries.forEach { tab ->
            Tab(selected = state.selectedTaskSubTab == tab, onClick = { controller.selectTaskSubTab(tab) }, text = { Text(tab.title()) })
        }
    }
    when (state.selectedTaskSubTab) {
        TaskSubTab.TASKS -> TasksSubTab(state, controller, onDeleteTask, onDeleteRoutine)
        TaskSubTab.ALARMS -> AlarmsSubTab(state, controller, onDeleteAlarm)
    }
}

@Composable
private fun TasksSubTab(
    state: AppUiState,
    controller: AbunAppController,
    onDeleteTask: (id: String, title: String) -> Unit,
    onDeleteRoutine: (id: String, title: String) -> Unit,
) {
    var draftTask by remember { mutableStateOf("") }
    var routineTitle by remember { mutableStateOf("") }
    var routineCron by remember { mutableStateOf("0 9 * * *") }
    var routineTimezone by remember { mutableStateOf(state.preferences.timezoneOverride) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        item {
            HeaderTimerSummary(state, controller)
        }
        item {
            SectionCard {
                Text("Add task", style = ThemeTokens.type.sectionTitle)
                OutlinedTextField(value = draftTask, onValueChange = { draftTask = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Task title") })
                Button(onClick = {
                    controller.createTask(draftTask)
                    draftTask = ""
                }) { Text("Create task") }
            }
        }
        item {
            SectionCard {
                Text("Add routine", style = ThemeTokens.type.sectionTitle)
                OutlinedTextField(value = routineTitle, onValueChange = { routineTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Routine title") })
                OutlinedTextField(value = routineCron, onValueChange = { routineCron = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cron schedule") })
                OutlinedTextField(value = routineTimezone, onValueChange = { routineTimezone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Timezone") })
                Button(onClick = {
                    controller.createRoutine(routineTitle, routineCron, routineTimezone)
                    routineTitle = ""
                }) { Text("Create routine") }
            }
        }
        item { SectionTitle("Tasks") }
        if (state.taskView.tasks.isEmpty()) {
            item { EmptyState("No tasks yet.") }
        } else {
            items(state.taskView.tasks, key = { it.id }) { task -> TaskCard(task, controller, onDeleteTask) }
        }
        item { SectionTitle("Routines") }
        if (state.taskView.routines.isEmpty()) {
            item { EmptyState("No routines yet.") }
        } else {
            items(state.taskView.routines, key = { it.id }) { routine -> RoutineCard(routine, controller, onDeleteRoutine) }
        }
    }
}

@Composable
private fun HeaderTimerSummary(state: AppUiState, controller: AbunAppController) {
    val liveNow by rememberLiveNow()
    SectionCard {
        Text("Pomodoro", style = ThemeTokens.type.sectionTitle)
        val active = state.activePomodoroSession
        if (active == null) {
            Text("No active timer.", style = ThemeTokens.type.body)
            Button(onClick = controller::openPomodoroDialog) { Text("Start timer") }
        } else {
            Text(active.taskTitle ?: "Solo focus", style = ThemeTokens.type.body)
            Text(formatRemaining(active.endsAtEpochMillis - liveNow), style = ThemeTokens.type.bodyMuted)
            Button(onClick = controller::openPomodoroDialog) { Text("Open timer") }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskListItemView,
    controller: AbunAppController,
    onDeleteTask: (id: String, title: String) -> Unit,
) {
    SectionCard {
        Text(task.title, style = ThemeTokens.type.sectionTitle)
        Text(statusLabel(task.status), style = ThemeTokens.type.body)
        ActionRow {
            Button(onClick = { controller.progressTask(task.id) }) { Text("Progress") }
            Button(onClick = { controller.completeTask(task.id) }) { Text("Complete") }
            Button(onClick = { controller.startPomodoro(task.id, PomodoroPhase.FOCUS) }) { Text("Timer") }
            Button(onClick = { onDeleteTask(task.id, task.title) }) { Text("Delete") }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: RoutineListItemView,
    controller: AbunAppController,
    onDeleteRoutine: (id: String, title: String) -> Unit,
) {
    var title by remember(routine.id, routine.templateTitle) { mutableStateOf(routine.templateTitle) }
    var cron by remember(routine.id, routine.cronSchedule) { mutableStateOf(routine.cronSchedule) }
    var timezone by remember(routine.id, routine.timezone) { mutableStateOf(routine.timezone) }
    SectionCard {
        OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") })
        OutlinedTextField(value = cron, onValueChange = { cron = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cron") })
        OutlinedTextField(value = timezone, onValueChange = { timezone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Timezone") })
        Text(if (routine.isActive) "Active" else "Inactive", style = ThemeTokens.type.bodyMuted)
        ActionRow {
            Button(onClick = { controller.updateRoutine(routine.id, title, cron, timezone) }) { Text("Save") }
            Button(onClick = { controller.toggleRoutineActive(routine.id) }) { Text(if (routine.isActive) "Deactivate" else "Activate") }
            Button(onClick = { onDeleteRoutine(routine.id, routine.templateTitle) }) { Text("Delete") }
        }
    }
}

@Composable
private fun AlarmsSubTab(
    state: AppUiState,
    controller: AbunAppController,
    onDeleteAlarm: (id: String, taskTitle: String) -> Unit,
) {
    var selectedTaskId by remember(state.taskView.tasks) { mutableStateOf(state.taskView.tasks.firstOrNull()?.id.orEmpty()) }
    var triggerTimeIso by remember { mutableStateOf(controller.suggestedAlarmTriggerTimeIso()) }
    LaunchedEffect(state.preferences.defaultAlarmLeadMinutes) {
        if (triggerTimeIso.isBlank()) triggerTimeIso = controller.suggestedAlarmTriggerTimeIso()
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        item {
            SectionCard {
                Text("Add alarm", style = ThemeTokens.type.sectionTitle)
                Text("Select task", style = ThemeTokens.type.body)
                ActionRow {
                    state.taskView.tasks.forEach { task ->
                        Button(onClick = { selectedTaskId = task.id }) {
                            Text(if (selectedTaskId == task.id) "[${task.title}]" else task.title)
                        }
                    }
                }
                OutlinedTextField(value = triggerTimeIso, onValueChange = { triggerTimeIso = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Trigger time ISO") })
                Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
                    Button(onClick = { triggerTimeIso = controller.suggestedAlarmTriggerTimeIso() }) { Text("Use default") }
                    Button(onClick = {
                        controller.createAlarm(selectedTaskId, triggerTimeIso)
                        triggerTimeIso = controller.suggestedAlarmTriggerTimeIso()
                    }, enabled = selectedTaskId.isNotBlank()) { Text("Create alarm") }
                }
            }
        }
        item { SectionTitle("Alarms") }
        if (state.taskView.alarms.isEmpty()) {
            item { EmptyState("No alarms yet.") }
        } else {
            items(state.taskView.alarms, key = { it.id }) { alarm -> AlarmCard(alarm, controller, onDeleteAlarm) }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmListItemView,
    controller: AbunAppController,
    onDeleteAlarm: (id: String, taskTitle: String) -> Unit,
) {
    var triggerTimeIso by remember(alarm.id, alarm.triggerTimeIso) { mutableStateOf(alarm.triggerTimeIso) }
    SectionCard {
        Text(alarm.taskTitle, style = ThemeTokens.type.sectionTitle)
        Text(if (alarm.isActive) "Active" else "Inactive", style = ThemeTokens.type.body)
        Text(alarm.triggerTimeLabel, style = ThemeTokens.type.bodyMuted)
        OutlinedTextField(value = triggerTimeIso, onValueChange = { triggerTimeIso = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Trigger time ISO") })
        ActionRow {
            Button(onClick = { controller.updateAlarm(alarm.id, triggerTimeIso) }) { Text("Save") }
            Button(onClick = { controller.toggleAlarmActive(alarm.id) }) { Text(if (alarm.isActive) "Deactivate" else "Activate") }
            Button(onClick = { onDeleteAlarm(alarm.id, alarm.taskTitle) }) { Text("Delete") }
        }
    }
}

@Composable
private fun PomodoroDialog(state: AppUiState, controller: AbunAppController, liveNow: Long) {
    val active = state.activePomodoroSession
    var selectedTaskId by remember(state.taskView.tasks, active) { mutableStateOf(active?.taskId) }
    var selectedPhase by remember(active) { mutableStateOf(active?.phase ?: PomodoroPhase.FOCUS) }
    var note by remember(active?.id) { mutableStateOf(active?.note.orEmpty()) }
    var taskUpdate by remember(active?.id) { mutableStateOf(PomodoroTaskUpdate.NONE) }

    AlertDialog(
        onDismissRequest = controller::closePomodoroDialog,
        title = { Text(if (active == null) "Start pomodoro" else "Pomodoro timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
                if (active == null) {
                    Text("Start a new timer with or without a task.", style = ThemeTokens.type.body)
                    Text("Task", style = ThemeTokens.type.label)
                    ActionRow {
                        Button(onClick = { selectedTaskId = null }) { Text(if (selectedTaskId == null) "[No task]" else "No task") }
                        state.taskView.tasks.forEach { task ->
                            Button(onClick = { selectedTaskId = task.id }) {
                                Text(if (selectedTaskId == task.id) "[${task.title}]" else task.title)
                            }
                        }
                    }
                    Text("Mode", style = ThemeTokens.type.label)
                    ActionRow {
                        PomodoroPhase.entries.forEach { phase ->
                            Button(onClick = { selectedPhase = phase }) {
                                Text(if (selectedPhase == phase) "[${phase.label()}]" else phase.label())
                            }
                        }
                    }
                } else {
                    val remaining = active.endsAtEpochMillis - liveNow
                    Text(active.taskTitle ?: "Solo focus", style = ThemeTokens.type.body)
                    Text("Mode: ${active.phase.label()}", style = ThemeTokens.type.bodyMuted)
                    Text("Remaining: ${formatRemaining(remaining)}", style = ThemeTokens.type.bodyMuted)
                    if (remaining <= 0 || active.isOverdue) {
                        Text("Timer finished. Save notes and optionally update the related task.", style = ThemeTokens.type.body)
                        OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes") })
                        if (active.taskId != null) {
                            ActionRow {
                                PomodoroTaskUpdate.entries.forEach { option ->
                                    Button(onClick = { taskUpdate = option }) {
                                        Text(if (taskUpdate == option) "[${option.label()}]" else option.label())
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Only one timer can be active at a time.", style = ThemeTokens.type.body)
                    }
                }
            }
        },
        confirmButton = {
            if (active == null) {
                Button(onClick = { controller.startPomodoro(selectedTaskId, selectedPhase) }) { Text("Start") }
            } else {
                val remaining = active.endsAtEpochMillis - liveNow
                if (remaining <= 0 || active.isOverdue) {
                    Button(onClick = { controller.completePomodoro(active.id, note, if (active.taskId == null) PomodoroTaskUpdate.NONE else taskUpdate) }) { Text("Save session") }
                } else {
                    Button(onClick = controller::closePomodoroDialog) { Text("Hide") }
                }
            }
        },
        dismissButton = {
            if (active == null) {
                TextButton(onClick = controller::closePomodoroDialog) { Text("Cancel") }
            } else {
                TextButton(onClick = {
                    controller.cancelPomodoro(active.id, if (note.isBlank()) null else note)
                }) { Text("Stop timer") }
            }
        },
    )
}

@Composable
private fun PreferencesScreen(state: AppUiState, controller: AbunAppController) {
    var titlePrefix by remember(state.preferences) { mutableStateOf(state.preferences.titlePrefix) }
    var defaultAlarmLead by remember(state.preferences) { mutableStateOf(state.preferences.defaultAlarmLeadMinutes.toString()) }
    var focusMinutes by remember(state.preferences) { mutableStateOf(state.preferences.focusMinutes.toString()) }
    var shortBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.shortBreakMinutes.toString()) }
    var longBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.longBreakMinutes.toString()) }
    var timezoneOverride by remember(state.preferences) { mutableStateOf(state.preferences.timezoneOverride) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        item {
            PreferenceSection("Task defaults") {
                OutlinedTextField(value = titlePrefix, onValueChange = {
                    titlePrefix = it
                    updatePrefs(controller, state, titlePrefix, defaultAlarmLead, focusMinutes, shortBreakMinutes, longBreakMinutes, timezoneOverride, state.preferences.dateFormat)
                }, modifier = Modifier.fillMaxWidth(), label = { Text("Title prefix") })
                OutlinedTextField(value = defaultAlarmLead, onValueChange = {
                    defaultAlarmLead = it
                    if (it.toIntOrNull() != null) updatePrefs(controller, state, titlePrefix, defaultAlarmLead, focusMinutes, shortBreakMinutes, longBreakMinutes, timezoneOverride, state.preferences.dateFormat)
                }, modifier = Modifier.fillMaxWidth(), label = { Text("Default alarm lead minutes") })
                Text("Blank titles: reject", style = ThemeTokens.type.bodyMuted)
            }
        }
        item {
            PreferenceSection("Pomodoro defaults") {
                NumericPreferenceField("Focus minutes", focusMinutes) {
                    focusMinutes = it
                    if (it.toIntOrNull() != null) updatePrefs(controller, state, titlePrefix, defaultAlarmLead, focusMinutes, shortBreakMinutes, longBreakMinutes, timezoneOverride, state.preferences.dateFormat)
                }
                NumericPreferenceField("Short break minutes", shortBreakMinutes) {
                    shortBreakMinutes = it
                    if (it.toIntOrNull() != null) updatePrefs(controller, state, titlePrefix, defaultAlarmLead, focusMinutes, shortBreakMinutes, longBreakMinutes, timezoneOverride, state.preferences.dateFormat)
                }
                NumericPreferenceField("Long break minutes", longBreakMinutes) {
                    longBreakMinutes = it
                    if (it.toIntOrNull() != null) updatePrefs(controller, state, titlePrefix, defaultAlarmLead, focusMinutes, shortBreakMinutes, longBreakMinutes, timezoneOverride, state.preferences.dateFormat)
                }
            }
        }
        item {
            PreferenceSection("App preferences") {
                OutlinedTextField(value = timezoneOverride, onValueChange = {
                    timezoneOverride = it
                    updatePrefs(controller, state, titlePrefix, defaultAlarmLead, focusMinutes, shortBreakMinutes, longBreakMinutes, timezoneOverride, state.preferences.dateFormat)
                }, modifier = Modifier.fillMaxWidth(), label = { Text("Timezone override") })
                Text("Date format", style = ThemeTokens.type.label)
                ActionRow {
                    DateFormatPreference.entries.forEach { preference ->
                        Button(onClick = {
                            updatePrefs(controller, state, titlePrefix, defaultAlarmLead, focusMinutes, shortBreakMinutes, longBreakMinutes, timezoneOverride, preference)
                        }) {
                            Text(if (state.preferences.dateFormat == preference) "[${preference.label()}]" else preference.label())
                        }
                    }
                }
            }
        }
    }
}

private fun updatePrefs(
    controller: AbunAppController,
    state: AppUiState,
    titlePrefix: String,
    defaultAlarmLead: String,
    focusMinutes: String,
    shortBreakMinutes: String,
    longBreakMinutes: String,
    timezoneOverride: String,
    dateFormat: DateFormatPreference,
) {
    controller.updatePreferences(
        titlePrefix = titlePrefix,
        defaultAlarmLeadMinutes = defaultAlarmLead.toIntOrNull() ?: state.preferences.defaultAlarmLeadMinutes,
        focusMinutes = focusMinutes.toIntOrNull() ?: state.preferences.focusMinutes,
        shortBreakMinutes = shortBreakMinutes.toIntOrNull() ?: state.preferences.shortBreakMinutes,
        longBreakMinutes = longBreakMinutes.toIntOrNull() ?: state.preferences.longBreakMinutes,
        timezoneOverride = timezoneOverride,
        dateFormat = dateFormat,
    )
}

@Composable
private fun NumericPreferenceField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) })
}

@Composable
private fun PreferenceSection(title: String, content: @Composable () -> Unit) {
    SectionCard {
        Text(title, style = ThemeTokens.type.sectionTitle)
        content()
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

private fun TaskSubTab.title(): String = when (this) {
    TaskSubTab.TASKS -> "Tasks"
    TaskSubTab.ALARMS -> "Alarms"
}

private fun PomodoroPhase.label(): String = when (this) {
    PomodoroPhase.FOCUS -> "Focus"
    PomodoroPhase.SHORT_BREAK -> "Short break"
    PomodoroPhase.LONG_BREAK -> "Long break"
}

private fun PomodoroTaskUpdate.label(): String = when (this) {
    PomodoroTaskUpdate.NONE -> "No change"
    PomodoroTaskUpdate.PROGRESS -> "Progress"
    PomodoroTaskUpdate.COMPLETE -> "Complete"
    PomodoroTaskUpdate.CANCEL -> "Cancel task"
}

private fun DateFormatPreference.label(): String = when (this) {
    DateFormatPreference.ISO -> "ISO"
    DateFormatPreference.MONTH_DAY -> "Month day"
    DateFormatPreference.WEEKDAY_MONTH_DAY -> "Weekday"
}

private fun formatRemaining(remainingMillis: Long): String {
    val safe = if (remainingMillis > 0L) remainingMillis else 0L
    val totalSeconds = safe / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}m ${seconds.toString().padStart(2, '0')}s"
}

private enum class PendingDeleteKind {
    TASK,
    ROUTINE,
    ALARM,
}

private data class PendingDelete(
    val key: String,
    val kind: PendingDeleteKind,
    val id: String,
    val label: String,
    val executeAtMillis: Long,
)
