package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.Clock9
import com.composables.icons.lucide.Diamond
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ListTodo
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Timer
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
import dev.tireless.abun.ui.components.JournalTimeline
import dev.tireless.abun.ui.components.StatusPill
import dev.tireless.abun.ui.navigation.AppNavHost
import dev.tireless.abun.ui.navigation.appTabForRoute
import dev.tireless.abun.ui.navigation.routeForTab
import dev.tireless.abun.ui.screens.DayScreen
import dev.tireless.abun.ui.screens.GuideScreenContent
import dev.tireless.abun.ui.screens.TasksScreen
import dev.tireless.abun.ui.sheets.CompletePomodoroSheet
import dev.tireless.abun.ui.sheets.CreateRoutineSheet
import dev.tireless.abun.ui.sheets.CreateTaskSheet
import dev.tireless.abun.ui.sheets.RoutineActionsSheet
import dev.tireless.abun.ui.sheets.StartPomodoroSheet
import dev.tireless.abun.ui.sheets.TaskActionsSheet
import dev.tireless.abun.ui.sheets.TaskCreateContext
import dev.tireless.abun.ui.sheets.TaskSaveDraft
import dev.tireless.abun.ui.sheets.normalizeTaskSaveDraft
import dev.tireless.abun.ui.sheets.taskCreateContextFor
import dev.tireless.abun.ui.TaskTopBarSubtabOption
import dev.tireless.abun.ui.TaskTopBarSubtabSelector
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
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val liveNow by rememberLiveNow()
    val activeSession = state.activePomodoroSession
    val activeRemaining = activeSession?.let { it.endsAtEpochMillis - liveNow } ?: 0L
    val isPomodoroActive = activeSession != null && activeRemaining > 0
    var currentSheet by remember { mutableStateOf<OverlaySheet?>(null) }
    var selectedTask by remember { mutableStateOf<TaskListItemView?>(null) }
    var selectedRoutine by remember { mutableStateOf<RoutineListItemView?>(null) }
    var createTaskContext by remember { mutableStateOf(taskCreateContextFor(state.selectedTab, state.selectedDate)) }
    var isTaskTopBarSelectorExpanded by remember { mutableStateOf(false) }
    val selectedTaskHistory = selectedTask?.let { controller.taskHistory(it.id) }.orEmpty()

    LaunchedEffect(state.activePomodoroSession?.id, activeRemaining) {
        if (state.activePomodoroSession != null && activeRemaining <= 0) {
            currentSheet = OverlaySheet.COMPLETE_POMODORO
            controller.selectTaskSubTab(TaskSubTab.POMODORO)
        }
    }

    LaunchedEffect(currentRoute) {
        val routeTab = appTabForRoute(currentRoute)
        if (routeTab != state.selectedTab) {
            controller.selectTab(routeTab)
        }
    }

    LaunchedEffect(state.selectedTab, currentRoute) {
        val targetRoute = routeForTab(state.selectedTab)
        if (currentRoute != null && currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
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
                        if (state.selectedTab == AppTab.TASKS) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = state.selectedTab.tabLabel(),
                                    style = ThemeTokens.type.title,
                                )
                                TaskTopBarSubtabSelector(
                                    currentLabel = state.selectedTaskSubTab.label(),
                                    currentIcon = state.selectedTaskSubTab.icon(),
                                    expanded = isTaskTopBarSelectorExpanded,
                                    onExpandedChange = { isTaskTopBarSelectorExpanded = it },
                                    options = TaskSubTab.entries.map { taskSubTab ->
                                        TaskTopBarSubtabOption(
                                            label = taskSubTab.label(),
                                            icon = taskSubTab.icon(),
                                            onClick = { controller.selectTaskSubTab(taskSubTab) },
                                        )
                                    },
                                )
                            }
                        } else {
                            Text(
                                text = state.selectedTab.tabLabel(),
                                style = ThemeTokens.type.title,
                            )
                        }
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
                                onClick = {
                                    navController.navigate(routeForTab(tab)) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                    }
                                },
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
                        icon = {
                            Icon(
                                imageVector = Lucide.Plus,
                                contentDescription = null,
                            )
                        },
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
                AppNavHost(
                    navController = navController,
                    state = state,
                    liveNow = liveNow,
                    isPomodoroActive = isPomodoroActive,
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
                    onUpdateThemePreference = controller::updateThemePreference,
                    onUpdatePreferences = controller::updatePreferences,
                    onReopenLogin = controller::reopenLogin,
                    onLogout = controller::logout,
                )
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

internal fun taskListFilterEmptyState(filter: TaskListFilter): String = when (filter) {
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

private fun TaskSubTab.icon(): ImageVector = when (this) {
    TaskSubTab.TASKS -> Lucide.ListTodo
    TaskSubTab.ROUTINES -> Lucide.CalendarCheck
    TaskSubTab.POMODORO -> Lucide.Timer
}

internal fun TaskListFilter.label(): String = when (this) {
    TaskListFilter.ALL_ACTIVE -> "All active"
    TaskListFilter.BACKLOG -> "Backlog"
    TaskListFilter.SCHEDULED -> "Scheduled"
    TaskListFilter.ROUTINE_DERIVED -> "Routine-derived"
    TaskListFilter.COMPLETED -> "Completed"
}

internal fun TaskListFilter.icon(): ImageVector = when (this) {
    TaskListFilter.ALL_ACTIVE -> Lucide.Diamond
    TaskListFilter.BACKLOG -> Lucide.Inbox
    TaskListFilter.SCHEDULED -> Lucide.CalendarClock
    TaskListFilter.ROUTINE_DERIVED -> Lucide.Clock9
    TaskListFilter.COMPLETED -> Lucide.CheckCheck
}

internal fun taskListFilterFromLabel(label: String): TaskListFilter = when (label) {
    "Backlog" -> TaskListFilter.BACKLOG
    "Scheduled" -> TaskListFilter.SCHEDULED
    "Routine-derived" -> TaskListFilter.ROUTINE_DERIVED
    "Completed" -> TaskListFilter.COMPLETED
    else -> TaskListFilter.ALL_ACTIVE
}

internal fun PomodoroPhase.label(): String = when (this) {
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

internal fun DateFormatPreference.label(): String = when (this) {
    DateFormatPreference.ISO -> "ISO"
    DateFormatPreference.MONTH_DAY -> "Month day"
    DateFormatPreference.WEEKDAY_MONTH_DAY -> "Weekday"
}

internal fun dateFormatFromLabel(label: String): DateFormatPreference = when (label) {
    "ISO" -> DateFormatPreference.ISO
    "Month day" -> DateFormatPreference.MONTH_DAY
    "Weekday" -> DateFormatPreference.WEEKDAY_MONTH_DAY
    else -> DateFormatPreference.ISO
}

internal fun ThemePreference.label(): String = when (this) {
    ThemePreference.SYSTEM -> "System"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}

internal fun themePreferenceFromLabel(label: String): ThemePreference = when (label) {
    "System" -> ThemePreference.SYSTEM
    "Light" -> ThemePreference.LIGHT
    "Dark" -> ThemePreference.DARK
    else -> ThemePreference.SYSTEM
}

internal fun formatRemaining(remainingMillis: Long): String {
    val safe = if (remainingMillis > 0L) remainingMillis else 0L
    val totalSeconds = safe / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes}m ${seconds.toString().padStart(2, '0')}s"
}
