package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AgendaTaskItemView
import dev.tireless.abun.app.AlarmListItemView
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroSessionView
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.TaskSubTab
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

    MaterialTheme {
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
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .safeContentPadding()
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.syncState.lastSyncedAt?.let { Text("Last synced: $it", style = MaterialTheme.typography.bodySmall) }
                state.syncState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                when {
                    state.isPreferencesOpen -> PreferencesScreen(state, controller)
                    state.selectedTab == AppTab.TODAY -> TodayScreen(state)
                    else -> TaskScreen(state, controller)
                }
            }

            if (state.isPomodoroDialogOpen) {
                PomodoroDialog(state, controller, liveNow)
            }
        }
    }
}

@Composable
private fun TodayScreen(state: AppUiState) {
    Text("Today: ${state.selectedDate}", style = MaterialTheme.typography.titleMedium)
    AgendaSection("Current", state.today.currentTasks)
    AgendaSection("Upcoming", state.today.upcomingTasks)
    JournalSection(state.today.journalEntries)
}

@Composable
private fun AgendaSection(title: String, items: List<AgendaTaskItemView>) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    if (items.isEmpty()) {
        EmptyCard("No tasks in this section.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(statusLabel(item.status))
                    item.triggerTimeLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun JournalSection(entries: List<JournalEntryView>) {
    Text("Today journal", style = MaterialTheme.typography.titleLarge)
    if (entries.isEmpty()) {
        EmptyCard("No journal entries yet.")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries, key = { it.eventId }) { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold)
                    Text("${entry.eventType.name} at ${entry.eventTimeLabel}")
                    entry.content?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun TaskScreen(state: AppUiState, controller: AbunAppController) {
    TabRow(selectedTabIndex = state.selectedTaskSubTab.ordinal) {
        TaskSubTab.entries.forEach { tab ->
            Tab(selected = state.selectedTaskSubTab == tab, onClick = { controller.selectTaskSubTab(tab) }, text = { Text(tab.title()) })
        }
    }
    when (state.selectedTaskSubTab) {
        TaskSubTab.TASKS -> TasksSubTab(state, controller)
        TaskSubTab.ALARMS -> AlarmsSubTab(state, controller)
    }
}

@Composable
private fun TasksSubTab(state: AppUiState, controller: AbunAppController) {
    var draftTask by remember { mutableStateOf("") }
    var routineTitle by remember { mutableStateOf("") }
    var routineCron by remember { mutableStateOf("0 9 * * *") }
    var routineTimezone by remember { mutableStateOf(state.preferences.timezoneOverride) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HeaderTimerSummary(state, controller)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add task", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = draftTask, onValueChange = { draftTask = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Task title") })
                    Button(onClick = {
                        controller.createTask(draftTask)
                        draftTask = ""
                    }) { Text("Create task") }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add routine", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = routineTitle, onValueChange = { routineTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Routine title") })
                    OutlinedTextField(value = routineCron, onValueChange = { routineCron = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cron schedule") })
                    OutlinedTextField(value = routineTimezone, onValueChange = { routineTimezone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Timezone") })
                    Button(onClick = {
                        controller.createRoutine(routineTitle, routineCron, routineTimezone)
                        routineTitle = ""
                    }) { Text("Create routine") }
                }
            }
        }
        item { Text("Tasks", style = MaterialTheme.typography.titleLarge) }
        if (state.taskView.tasks.isEmpty()) {
            item { EmptyCard("No tasks yet.") }
        } else {
            items(state.taskView.tasks, key = { it.id }) { task -> TaskCard(task, controller) }
        }
        item { Text("Routines", style = MaterialTheme.typography.titleLarge) }
        if (state.taskView.routines.isEmpty()) {
            item { EmptyCard("No routines yet.") }
        } else {
            items(state.taskView.routines, key = { it.id }) { routine -> RoutineCard(routine, controller) }
        }
    }
}

@Composable
private fun HeaderTimerSummary(state: AppUiState, controller: AbunAppController) {
    val liveNow by rememberLiveNow()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pomodoro", style = MaterialTheme.typography.titleMedium)
            val active = state.activePomodoroSession
            if (active == null) {
                Text("No active timer.")
                Button(onClick = controller::openPomodoroDialog) { Text("Start timer") }
            } else {
                Text(active.taskTitle ?: "Solo focus")
                Text(formatRemaining(active.endsAtEpochMillis - liveNow))
                Button(onClick = controller::openPomodoroDialog) { Text("Open timer") }
            }
        }
    }
}

@Composable
private fun TaskCard(task: TaskListItemView, controller: AbunAppController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(statusLabel(task.status))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { controller.progressTask(task.id) }) { Text("Progress") }
                Button(onClick = { controller.completeTask(task.id) }) { Text("Complete") }
                Button(onClick = { controller.startPomodoro(task.id, PomodoroPhase.FOCUS) }) { Text("Timer") }
                Button(onClick = { controller.deleteTask(task.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun RoutineCard(routine: RoutineListItemView, controller: AbunAppController) {
    var title by remember(routine.id, routine.templateTitle) { mutableStateOf(routine.templateTitle) }
    var cron by remember(routine.id, routine.cronSchedule) { mutableStateOf(routine.cronSchedule) }
    var timezone by remember(routine.id, routine.timezone) { mutableStateOf(routine.timezone) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") })
            OutlinedTextField(value = cron, onValueChange = { cron = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cron") })
            OutlinedTextField(value = timezone, onValueChange = { timezone = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Timezone") })
            Text(if (routine.isActive) "Active" else "Inactive", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { controller.updateRoutine(routine.id, title, cron, timezone) }) { Text("Save") }
                Button(onClick = { controller.toggleRoutineActive(routine.id) }) { Text(if (routine.isActive) "Deactivate" else "Activate") }
                Button(onClick = { controller.deleteRoutine(routine.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun AlarmsSubTab(state: AppUiState, controller: AbunAppController) {
    var selectedTaskId by remember(state.taskView.tasks) { mutableStateOf(state.taskView.tasks.firstOrNull()?.id.orEmpty()) }
    var triggerTimeIso by remember { mutableStateOf(controller.suggestedAlarmTriggerTimeIso()) }
    LaunchedEffect(state.preferences.defaultAlarmLeadMinutes) {
        if (triggerTimeIso.isBlank()) triggerTimeIso = controller.suggestedAlarmTriggerTimeIso()
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add alarm", style = MaterialTheme.typography.titleMedium)
                    Text("Select task")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.taskView.tasks.forEach { task ->
                            Button(onClick = { selectedTaskId = task.id }) {
                                Text(if (selectedTaskId == task.id) "[${task.title}]" else task.title)
                            }
                        }
                    }
                    OutlinedTextField(value = triggerTimeIso, onValueChange = { triggerTimeIso = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Trigger time ISO") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { triggerTimeIso = controller.suggestedAlarmTriggerTimeIso() }) { Text("Use default") }
                        Button(onClick = {
                            controller.createAlarm(selectedTaskId, triggerTimeIso)
                            triggerTimeIso = controller.suggestedAlarmTriggerTimeIso()
                        }, enabled = selectedTaskId.isNotBlank()) { Text("Create alarm") }
                    }
                }
            }
        }
        item { Text("Alarms", style = MaterialTheme.typography.titleLarge) }
        if (state.taskView.alarms.isEmpty()) {
            item { EmptyCard("No alarms yet.") }
        } else {
            items(state.taskView.alarms, key = { it.id }) { alarm -> AlarmCard(alarm, controller) }
        }
    }
}

@Composable
private fun AlarmCard(alarm: AlarmListItemView, controller: AbunAppController) {
    var triggerTimeIso by remember(alarm.id, alarm.triggerTimeIso) { mutableStateOf(alarm.triggerTimeIso) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(alarm.taskTitle, style = MaterialTheme.typography.titleMedium)
            Text(if (alarm.isActive) "Active" else "Inactive")
            Text(alarm.triggerTimeLabel, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = triggerTimeIso, onValueChange = { triggerTimeIso = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Trigger time ISO") })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { controller.updateAlarm(alarm.id, triggerTimeIso) }) { Text("Save") }
                Button(onClick = { controller.toggleAlarmActive(alarm.id) }) { Text(if (alarm.isActive) "Deactivate" else "Activate") }
                Button(onClick = { controller.deleteAlarm(alarm.id) }) { Text("Delete") }
            }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (active == null) {
                    Text("Start a new timer with or without a task.")
                    Text("Task")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { selectedTaskId = null }) { Text(if (selectedTaskId == null) "[No task]" else "No task") }
                        state.taskView.tasks.forEach { task ->
                            Button(onClick = { selectedTaskId = task.id }) {
                                Text(if (selectedTaskId == task.id) "[${task.title}]" else task.title)
                            }
                        }
                    }
                    Text("Mode")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PomodoroPhase.entries.forEach { phase ->
                            Button(onClick = { selectedPhase = phase }) {
                                Text(if (selectedPhase == phase) "[${phase.label()}]" else phase.label())
                            }
                        }
                    }
                } else {
                    val remaining = active.endsAtEpochMillis - liveNow
                    Text(active.taskTitle ?: "Solo focus")
                    Text("Mode: ${active.phase.label()}")
                    Text("Remaining: ${formatRemaining(remaining)}")
                    if (remaining <= 0 || active.isOverdue) {
                        Text("Timer finished. Save notes and optionally update the related task.")
                        OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Notes") })
                        if (active.taskId != null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PomodoroTaskUpdate.entries.forEach { option ->
                                    Button(onClick = { taskUpdate = option }) {
                                        Text(if (taskUpdate == option) "[${option.label()}]" else option.label())
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Only one timer can be active at a time.")
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Text("Blank titles: reject", style = MaterialTheme.typography.bodySmall)
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
                Text("Date format", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
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
