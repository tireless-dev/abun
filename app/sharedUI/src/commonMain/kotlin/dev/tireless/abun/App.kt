package dev.tireless.abun

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.composables.icons.lucide.Settings
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
import dev.tireless.abun.ota.OtaStatus
import dev.tireless.abun.ota.OtaVersionInfo
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.EditorialScreen
import dev.tireless.abun.ui.EditorialStatusTag
import dev.tireless.abun.ui.FloatingCommandPill
import dev.tireless.abun.ui.FloatingCommandSheet
import dev.tireless.abun.ui.components.JournalTimeline
import dev.tireless.abun.ui.components.StatusPill
import dev.tireless.abun.ui.navigation.AppNavHost
import dev.tireless.abun.ui.navigation.AppRoute
import dev.tireless.abun.ui.navigation.appTabForRoute
import dev.tireless.abun.ui.navigation.routeForTab
import dev.tireless.abun.ui.screens.HomeScreen
import dev.tireless.abun.ui.screens.GuideScreenContent
import dev.tireless.abun.ui.screens.TasksScreen
import dev.tireless.abun.ui.TaskTopBarSubtabOption
import dev.tireless.abun.ui.TaskTopBarSubtabSelector
import dev.tireless.abun.ui.theme.AppTheme
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor
import kotlinx.coroutines.delay

internal data class TaskSurfaceGroups(
    val backlog: List<TaskListItemView>,
    val scheduled: List<TaskListItemView>,
    val completed: List<TaskListItemView>,
)

@Composable
private fun FinanceTopBarSubnav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        Triple("总览", AppRoute.FinanceCockpit.route, "finance-subtab-cockpit"),
        Triple("明细", AppRoute.Finance.route, "finance-subtab-ledger"),
        Triple("清算", AppRoute.DebtClearingHub.route, "finance-subtab-clearing"),
    )
    val activeRoute = when (currentRoute) {
        AppRoute.Finance.route -> AppRoute.Finance.route
        AppRoute.DebtClearingHub.route -> AppRoute.DebtClearingHub.route
        else -> AppRoute.FinanceCockpit.route
    }

    Row(
        modifier = modifier
            .testTag("finance-subtab-selector")
            .clip(RoundedCornerShape(8.dp))
            .background(ThemeTokens.colors.subnavTrack)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, route, testTag) ->
            val isSelected = route == activeRoute
            val pillBackground = if (isSelected) ThemeTokens.colors.surface else Color.Transparent
            val textColor = if (isSelected) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textSecondary
            val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            Box(
                modifier = Modifier
                    .testTag(testTag)
                    .clip(RoundedCornerShape(6.dp))
                    .background(pillBackground)
                    .clickable { onNavigate(route) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = fontWeight,
                        color = textColor,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AbunTopAppBar(
    selectedTab: AppTab,
    selectedTaskSubTab: TaskSubTab = TaskSubTab.TASKS,
    selectedDate: String = "2026-05-30",
    currentRoute: String? = null,
    onSelectTab: (AppTab) -> Unit = {},
    onSelectTaskSubTab: (TaskSubTab) -> Unit = {},
    onNavigateRoute: (String) -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (selectedTab == AppTab.TODAY) ThemeTokens.colors.canvas else ThemeTokens.colors.background,
            titleContentColor = ThemeTokens.colors.textPrimary,
        ),
        title = {
            when (selectedTab) {
                AppTab.TODAY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$selectedDate · DASHBOARD",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = ThemeTokens.colors.textSecondary,
                            ),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ThemeTokens.colors.financeSoft,
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(ThemeTokens.colors.financeAccent),
                                    )
                                    Text(
                                        text = "LOCAL-FIRST",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ThemeTokens.colors.financeAccent,
                                        ),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onSelectTab(AppTab.SETTINGS) },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("topbar-settings-button"),
                            ) {
                                Icon(
                                    imageVector = Lucide.Settings,
                                    contentDescription = "Settings",
                                    tint = ThemeTokens.colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                AppTab.TASKS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ThemeTokens.colors.tasksAccent),
                            )
                            Text(
                                text = "Tasks",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTokens.colors.textPrimary,
                                ),
                            )
                        }
                        TaskTopBarSubtabSelector(
                            selectedSubtab = selectedTaskSubTab,
                            onSelectSubtab = onSelectTaskSubTab,
                        )
                    }
                }
                AppTab.FINANCE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ThemeTokens.colors.financeAccent),
                            )
                            Text(
                                text = "Finance",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTokens.colors.textPrimary,
                                ),
                            )
                        }
                        FinanceTopBarSubnav(
                            currentRoute = currentRoute,
                            onNavigate = onNavigateRoute,
                        )
                    }
                }
                AppTab.SETTINGS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Settings",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTokens.colors.textPrimary,
                            ),
                        )
                        Text(
                            text = "LOCAL-FIRST · SYNCED",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTokens.colors.financeAccent,
                            ),
                        )
                    }
                }
                AppTab.NOTES -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ThemeTokens.colors.notesAccent),
                        )
                        Text(
                            text = "Notes",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTokens.colors.textPrimary,
                            ),
                        )
                    }
                }
                AppTab.THINGS -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ThemeTokens.colors.thingsAccent),
                        )
                        Text(
                            text = "Things",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeTokens.colors.textPrimary,
                            ),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun FloatingCommandOverlay(
    state: AppUiState,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDismissExpanded: () -> Unit,
    primaryActionLabel: String,
    onPrimaryActionClick: () -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onQuickCaptureTask: () -> Unit,
    onQuickCaptureExpense: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismissExpanded)
                    .background(Color(0xFF111315).copy(alpha = 0.25f)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 76.dp)
                        .clickable(enabled = false) {},
                ) {
                    FloatingCommandSheet(
                        enabledModules = state.preferences.enabledModules,
                        currentTab = state.selectedTab,
                        openTasksCount = state.today.currentTasks.size,
                        onSelectTab = {
                            onDismissExpanded()
                            onSelectTab(it)
                        },
                        onQuickCaptureTask = {
                            onDismissExpanded()
                            onQuickCaptureTask()
                        },
                        onQuickCaptureExpense = {
                            onDismissExpanded()
                            onSelectTab(AppTab.FINANCE)
                            onQuickCaptureExpense()
                        },
                        onQuickCaptureNote = {
                            onDismissExpanded()
                            onSelectTab(AppTab.NOTES)
                        },
                        onQuickCaptureThing = {
                            onDismissExpanded()
                            onSelectTab(AppTab.THINGS)
                        },
                        onDismiss = onDismissExpanded,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            FloatingCommandPill(
                selectedTab = state.selectedTab,
                enabledModules = state.preferences.enabledModules,
                primaryActionLabel = primaryActionLabel,
                onPrimaryActionClick = onPrimaryActionClick,
                isExpanded = isExpanded,
                onToggleExpanded = onToggleExpanded,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp),
        ) {
            AppTab.entries.forEach { tab ->
                Text(
                    text = tab.tabLabel(),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectTab(tab) },
                )
            }
        }
        Box(
            modifier = Modifier
                .size(1.dp)
                .testTag("global-new-task-fab")
                .clickable { onQuickCaptureTask() },
        )
    }
}

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
    var isCommandSheetExpanded by remember { mutableStateOf(false) }
    val otaStatus by controller.otaStatus.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    if (controller.isAutoUpdateEnabled()) {
                        controller.checkForUpdates(isManual = false)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(otaStatus) {
        if (otaStatus is OtaStatus.UpdateAvailable && (otaStatus as OtaStatus.UpdateAvailable).showToast) {
            kotlinx.coroutines.delay(3000L)
            controller.dismissOtaToast()
        }
    }

    LaunchedEffect(state.activePomodoroSession?.id, activeRemaining) {
        if (state.activePomodoroSession != null && activeRemaining <= 0) {
            navController.navigate(AppRoute.CompletePomodoro.route) {
                launchSingleTop = true
            }
            controller.selectTaskSubTab(TaskSubTab.POMODORO)
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
                onResetLoginOtp = controller::resetLoginOtp,
                onSkipLogin = controller::skipLogin,
            )
            return@AppTheme
        }

        val primaryActionLabel = when (state.selectedTab) {
            AppTab.TODAY -> "+ Capture"
            AppTab.TASKS -> when (state.selectedTaskSubTab) {
                TaskSubTab.TASKS -> "+ 新建任务"
                TaskSubTab.ROUTINES -> "+ 新建例程"
                TaskSubTab.POMODORO -> if (state.activePomodoroSession != null) "🍅 专注中" else "+ 开始专注"
            }
            AppTab.FINANCE -> "+ 记一笔"
            AppTab.NOTES -> "+ 新建笔记"
            AppTab.THINGS -> "+ 新增物品"
            AppTab.SETTINGS -> "← 返回主页"
        }

        val onPrimaryActionClick = {
            when (state.selectedTab) {
                AppTab.TODAY -> navController.navigate(AppRoute.CreateTask.route)
                AppTab.TASKS -> when (state.selectedTaskSubTab) {
                    TaskSubTab.TASKS -> navController.navigate(AppRoute.CreateTask.route)
                    TaskSubTab.ROUTINES -> navController.navigate(AppRoute.CreateRoutine.route)
                    TaskSubTab.POMODORO -> {
                        if (state.activePomodoroSession != null) {
                            navController.navigate(AppRoute.CompletePomodoro.route)
                        } else {
                            navController.navigate(AppRoute.StartPomodoro.route)
                        }
                    }
                }
                AppTab.FINANCE -> navController.navigate(AppRoute.CreateFinanceTransaction.route)
                AppTab.NOTES -> controller.selectTab(AppTab.NOTES)
                AppTab.THINGS -> controller.selectTab(AppTab.THINGS)
                AppTab.SETTINGS -> controller.selectTab(AppTab.TODAY)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MaterialScaffold(
            containerColor = if (state.selectedTab == AppTab.TODAY) ThemeTokens.colors.canvas else MaterialTheme.colorScheme.background,
            topBar = {
                AbunTopAppBar(
                    selectedTab = state.selectedTab,
                    selectedTaskSubTab = state.selectedTaskSubTab,
                    selectedDate = state.selectedDate,
                    currentRoute = currentRoute,
                    onSelectTab = controller::selectTab,
                    onSelectTaskSubTab = controller::selectTaskSubTab,
                    onNavigateRoute = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            },
        ) { padding: PaddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                EditorialScreen(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(
                        start = ThemeTokens.spacing.mdDp,
                        top = ThemeTokens.spacing.mdDp,
                        end = ThemeTokens.spacing.mdDp,
                        bottom = 80.dp,
                    ),
                    verticalSpacing = if (state.selectedTab == AppTab.TODAY) ThemeTokens.spacing.mdDp else ThemeTokens.spacing.lgDp,
                ) {
                    AppNavHost(
                        navController = navController,
                        state = state,
                        liveNow = liveNow,
                        isPomodoroActive = isPomodoroActive,
                        taskHistoryFor = controller::taskHistory,
                        onSelectTaskFilter = controller::selectTaskFilter,
                        onOpenTask = { navController.navigate(AppRoute.TaskDetail.create(it.id)) },
                        onOpenStartPomodoro = { navController.navigate(AppRoute.StartPomodoro.route) },
                        onCreateTask = { navController.navigate(AppRoute.CreateTask.route) },
                        onCreateRoutine = { navController.navigate(AppRoute.CreateRoutine.route) },
                        onOpenRoutine = { navController.navigate(AppRoute.RoutineDetail.create(it.id)) },
                        onRunRoutine = { controller.runRoutine(it.id) },
                        onUpdateThemePreference = controller::updateThemePreference,
                        onUpdatePreferences = controller::updatePreferences,
                        onReopenLogin = controller::reopenLogin,
                        onLogout = controller::logout,
                        onCreateTaskConfirm = controller::createTask,
                        onCreateRoutineConfirm = controller::createRoutine,
                        onSaveTask = controller::updateTask,
                        onProgressTask = controller::progressTask,
                        onCompleteTask = controller::completeTask,
                        onSkipTask = controller::skipTask,
                        onPostponeTask = controller::postponeTask,
                        onDeleteTask = controller::deleteTask,
                        onSaveRoutine = controller::updateRoutine,
                        onToggleRoutine = controller::toggleRoutineActive,
                        onDeleteRoutine = controller::deleteRoutine,
                        onStartPomodoro = { taskId, phase ->
                            controller.startPomodoro(taskId, phase)
                            controller.selectTaskSubTab(TaskSubTab.POMODORO)
                        },
                        onCompletePomodoro = { note, update ->
                            state.activePomodoroSession?.let { controller.completePomodoro(it.id, note.ifBlank { null }, update) }
                        },
                        onStopPomodoro = { note ->
                            state.activePomodoroSession?.let { controller.cancelPomodoro(it.id, note.ifBlank { null }) }
                        },
                        onSelectFinanceFilter = controller::selectFinanceFilter,
                        onToggleFinanceViewMode = controller::toggleFinanceLedgerViewMode,
                        onCreateFinanceTransaction = { txDate, summary, payee, status, splits ->
                            controller.createFinanceTransaction(
                                txDate = txDate,
                                summary = summary,
                                payee = payee,
                                status = status,
                                splits = splits,
                            )
                        },
                        onDeleteFinanceTransaction = controller::deleteFinanceTransaction,
                        onAutoPadAccountBalance = controller::autoPadAccountBalance,
                        onCreateFinanceAccount = { path, name, category, currency, includeInNetWorth, isPassThrough, liquidTier ->
                            controller.createFinanceAccount(
                                path = path,
                                name = name,
                                category = category,
                                currency = currency,
                                includeInNetWorth = includeInNetWorth,
                                isPassThrough = isPassThrough,
                                liquidTier = liquidTier,
                            )
                        },
                        onDeleteFinanceAccount = controller::deleteFinanceAccount,
                        onRepayCreditStatement = controller::repayCreditStatement,
                        onRepayLoanTranche = controller::executeLoanTrancheRepayment,
                        onCreateAmortizationPlan = { name, principalMinor, currency, totalPeriods, repaymentMethod, annualRateBps, feePerPeriodMinor, startDate, repaymentDay, liabilityAccountId, fundingAccountId, interestAccountId, recordInitialDisbursement ->
                            controller.createFinanceAmortizationPlan(
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
                            )
                        },
                        onDeleteAmortizationPlan = controller::deleteFinanceAmortizationPlan,
                        onSelectFinanceRunwayHorizon = controller::setFinanceRunwayHorizon,
                        onCreateBudgetRule = { name, scopeType, accountPrefix, monthlyLimitMinor, currency, includeInRunway ->
                            controller.createBudgetRule(
                                name = name,
                                scopeType = scopeType,
                                accountPrefix = accountPrefix,
                                monthlyLimitMinor = monthlyLimitMinor,
                                currency = currency,
                                includeInRunway = includeInRunway,
                            )
                        },
                        onDeleteBudgetRule = controller::deleteBudgetRule,
                        otaStatus = otaStatus,
                        isAutoUpdateEnabled = controller.isAutoUpdateEnabled(),
                        onManualCheckUpdate = { controller.checkForUpdates(isManual = true) },
                        onTriggerOtaUpdate = controller::triggerOtaDownload,
                        onUpdateAutoUpdateEnabled = controller::setAutoUpdateEnabled,
                    )
                }

                AnimatedVisibility(
                    visible = otaStatus is OtaStatus.UpdateAvailable && (otaStatus as OtaStatus.UpdateAvailable).showToast,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(padding)
                        .padding(ThemeTokens.spacing.mdDp),
                ) {
                    val updateInfo = (otaStatus as? OtaStatus.UpdateAvailable)?.info
                    if (updateInfo != null) {
                        OtaToastCard(
                            info = updateInfo,
                            onUpdate = controller::triggerOtaDownload,
                            onDismiss = controller::dismissOtaToast,
                        )
                    }
                }

                when (val status = otaStatus) {
                    is OtaStatus.Downloading -> {
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.BottomCenter),
                            color = ThemeTokens.colors.primary,
                        )
                    }
                    is OtaStatus.Installing -> {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.BottomCenter),
                            color = ThemeTokens.colors.primary,
                        )
                    }
                    else -> Unit
                }
            }
        }

        FloatingCommandOverlay(
            state = state,
            isExpanded = isCommandSheetExpanded,
            onToggleExpanded = { isCommandSheetExpanded = !isCommandSheetExpanded },
            onDismissExpanded = { isCommandSheetExpanded = false },
            primaryActionLabel = primaryActionLabel,
            onPrimaryActionClick = onPrimaryActionClick,
            onSelectTab = controller::selectTab,
            onQuickCaptureTask = { navController.navigate(AppRoute.CreateTask.route) },
            onQuickCaptureExpense = {
                controller.selectTab(AppTab.FINANCE)
                navController.navigate(AppRoute.CreateFinanceTransaction.route)
            },
        )
    }
    }
}

@Composable
private fun OtaToastCard(
    info: OtaVersionInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
        color = ThemeTokens.colors.surface,
        border = BorderStroke(1.dp, ThemeTokens.colors.border),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUpdate),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.smDp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New update available",
                    style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold),
                    color = ThemeTokens.colors.textPrimary,
                )
                Text(
                    text = "Build ${info.commit} · v${info.versionName}",
                    style = ThemeTokens.type.bodyMuted,
                )
            }
            Button(
                onClick = onUpdate,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Update", style = ThemeTokens.type.label.withMaterialContentColor())
            }
        }
    }
}

@Composable
internal fun AppContent(
    state: AppUiState,
    liveNow: Long,
    selectedTaskHistory: List<JournalEntryView>,
    onSelectTab: (AppTab) -> Unit,
    onSelectTaskSubTab: (TaskSubTab) -> Unit,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
    onOpenStartPomodoro: () -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
    onUpdateThemePreference: (ThemePreference) -> Unit,
    onUpdatePreferences: (
        String,
        Int,
        Int,
        Int,
        Int,
        String,
        DateFormatPreference,
        ThemePreference,
        String,
    ) -> Unit,
    onReopenLogin: () -> Unit,
    onLogout: () -> Unit,
    onRequestEmailOtp: () -> Unit,
    onVerifyEmailOtp: (String) -> Unit,
    onResetLoginOtp: () -> Unit = {},
    onSkipLogin: () -> Unit,
    onUpdateLoginEmail: (String) -> Unit,
    taskHistoryFor: (String) -> List<JournalEntryView>,
    onCreateTask: (String, String?, String?, String?, String?, String?) -> Unit,
    onCreateRoutineConfirm: (String, String?, String, String?, String?) -> Unit,
    onSaveTask: (String, String, String?, String?, String?, String?, String?) -> Unit,
    onProgressTask: (String, String?) -> Unit,
    onCompleteTask: (String, String?) -> Unit,
    onSkipTask: (String, String?) -> Unit,
    onPostponeTask: (String, String?, String?, String?, String?) -> Unit,
    onDeleteTask: (String) -> Unit,
    onSaveRoutine: (String, String, String?, String, String?, String?) -> Unit,
    onToggleRoutine: (String) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onStartPomodoro: (String?, PomodoroPhase) -> Unit,
    onCompletePomodoro: (String, PomodoroTaskUpdate) -> Unit,
    onStopPomodoro: (String) -> Unit,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    var isCommandSheetExpanded by remember { mutableStateOf(false) }

    val primaryActionLabel = when (state.selectedTab) {
        AppTab.TODAY -> "+ Capture"
        AppTab.TASKS -> when (state.selectedTaskSubTab) {
            TaskSubTab.TASKS -> "+ 新建任务"
            TaskSubTab.ROUTINES -> "+ 新建例程"
            TaskSubTab.POMODORO -> if (state.activePomodoroSession != null) "🍅 专注中" else "+ 开始专注"
        }
        AppTab.FINANCE -> "+ 记一笔"
        AppTab.NOTES -> "+ 新建笔记"
        AppTab.THINGS -> "+ 新增物品"
        AppTab.SETTINGS -> "← 返回主页"
    }

    val onPrimaryActionClick = {
        when (state.selectedTab) {
            AppTab.TODAY -> navController.navigate(AppRoute.CreateTask.route)
            AppTab.TASKS -> when (state.selectedTaskSubTab) {
                TaskSubTab.TASKS -> navController.navigate(AppRoute.CreateTask.route)
                TaskSubTab.ROUTINES -> navController.navigate(AppRoute.CreateRoutine.route)
                TaskSubTab.POMODORO -> {
                    if (state.activePomodoroSession != null) {
                        navController.navigate(AppRoute.CompletePomodoro.route)
                    } else {
                        navController.navigate(AppRoute.StartPomodoro.route)
                    }
                }
            }
            AppTab.FINANCE -> navController.navigate(AppRoute.CreateFinanceTransaction.route)
            AppTab.NOTES -> onSelectTab(AppTab.NOTES)
            AppTab.THINGS -> onSelectTab(AppTab.THINGS)
            AppTab.SETTINGS -> onSelectTab(AppTab.TODAY)
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

    if (state.auth.showGuide) {
        GuideScreenContent(
            state = state,
            onUpdateLoginEmail = onUpdateLoginEmail,
            onRequestEmailOtp = onRequestEmailOtp,
            onVerifyEmailOtp = onVerifyEmailOtp,
            onResetLoginOtp = onResetLoginOtp,
            onSkipLogin = onSkipLogin,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialScaffold(
            containerColor = if (state.selectedTab == AppTab.TODAY) ThemeTokens.colors.canvas else MaterialTheme.colorScheme.background,
            topBar = {
                AbunTopAppBar(
                    selectedTab = state.selectedTab,
                    selectedTaskSubTab = state.selectedTaskSubTab,
                    selectedDate = state.selectedDate,
                    currentRoute = currentRoute,
                    onSelectTab = onSelectTab,
                    onSelectTaskSubTab = onSelectTaskSubTab,
                    onNavigateRoute = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            },
        ) { padding: PaddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                EditorialScreen(
                    modifier = Modifier.padding(padding),
                    contentPadding = PaddingValues(
                        start = ThemeTokens.spacing.mdDp,
                        top = ThemeTokens.spacing.mdDp,
                        end = ThemeTokens.spacing.mdDp,
                        bottom = 80.dp,
                    ),
                ) {
                    AppNavHost(
                        navController = navController,
                        state = state,
                        liveNow = liveNow,
                        isPomodoroActive = false,
                        taskHistoryFor = taskHistoryFor,
                        onSelectTaskFilter = onSelectTaskFilter,
                        onOpenTask = onOpenTask,
                        onOpenStartPomodoro = onOpenStartPomodoro,
                        onCreateTask = { },
                        onCreateRoutine = onCreateRoutine,
                        onOpenRoutine = onOpenRoutine,
                        onRunRoutine = onRunRoutine,
                        onUpdateThemePreference = onUpdateThemePreference,
                        onUpdatePreferences = onUpdatePreferences,
                        onReopenLogin = onReopenLogin,
                        onLogout = onLogout,
                        onCreateTaskConfirm = onCreateTask,
                        onCreateRoutineConfirm = onCreateRoutineConfirm,
                        onSaveTask = onSaveTask,
                        onProgressTask = onProgressTask,
                        onCompleteTask = onCompleteTask,
                        onSkipTask = onSkipTask,
                        onPostponeTask = onPostponeTask,
                        onDeleteTask = { onDeleteTask(it) },
                        onSaveRoutine = onSaveRoutine,
                        onToggleRoutine = { onToggleRoutine(it) },
                        onDeleteRoutine = { onDeleteRoutine(it) },
                        onStartPomodoro = onStartPomodoro,
                        onCompletePomodoro = onCompletePomodoro,
                        onStopPomodoro = onStopPomodoro,
                    )
                }
            }
        }

        FloatingCommandOverlay(
            state = state,
            isExpanded = isCommandSheetExpanded,
            onToggleExpanded = { isCommandSheetExpanded = !isCommandSheetExpanded },
            onDismissExpanded = { isCommandSheetExpanded = false },
            primaryActionLabel = primaryActionLabel,
            onPrimaryActionClick = onPrimaryActionClick,
            onSelectTab = onSelectTab,
            onQuickCaptureTask = { navController.navigate(AppRoute.CreateTask.route) },
            onQuickCaptureExpense = {
                onSelectTab(AppTab.FINANCE)
                navController.navigate(AppRoute.CreateFinanceTransaction.route)
            },
        )
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
    AppTab.TODAY -> "Dashboard"
    AppTab.TASKS -> "Tasks"
    AppTab.FINANCE -> "Finance"
    AppTab.NOTES -> "Notes"
    AppTab.THINGS -> "Things"
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
