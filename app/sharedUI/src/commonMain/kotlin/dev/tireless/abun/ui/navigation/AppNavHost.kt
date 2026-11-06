package dev.tireless.abun.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.FinanceLedgerFilter
import dev.tireless.abun.app.FinanceSplitDraft
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.AmortizationMethod
import dev.tireless.abun.finance.BudgetScopeType
import dev.tireless.abun.finance.LiquidTier
import dev.tireless.abun.finance.TransactionStatus
import dev.tireless.abun.ui.screens.ClearingHubScreen
import dev.tireless.abun.ui.screens.FinanceCockpitScreen
import dev.tireless.abun.ui.screens.FinanceLedgerScreen
import dev.tireless.abun.ui.screens.HomeScreen
import dev.tireless.abun.ui.screens.SettingsScreen
import dev.tireless.abun.ui.screens.TasksScreen
import dev.tireless.abun.ui.sheets.AmortizationWizardSheet
import dev.tireless.abun.ui.sheets.BudgetRuleSheet
import dev.tireless.abun.ui.sheets.CombinedRepaymentSheet
import dev.tireless.abun.ui.sheets.CompletePomodoroSheet
import dev.tireless.abun.ui.sheets.CreateFinanceTransactionSheet
import dev.tireless.abun.ui.sheets.CreateRoutineSheet
import dev.tireless.abun.ui.sheets.CreateTaskSheet
import dev.tireless.abun.ui.sheets.FinanceAccountsSheet
import dev.tireless.abun.ui.sheets.RoutineActionsSheet
import dev.tireless.abun.ui.sheets.StartPomodoroSheet
import dev.tireless.abun.ui.sheets.TaskActionsSheet
import dev.tireless.abun.ui.sheets.taskCreateContextFor

@Composable
fun AppNavHost(
    navController: NavHostController,
    state: AppUiState,
    liveNow: Long,
    isPomodoroActive: Boolean,
    taskHistoryFor: (String) -> List<JournalEntryView>,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
    onOpenStartPomodoro: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
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
    onCreateTaskConfirm: (String, String?, String?, String?, String?, String?) -> Unit,
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
    onSelectFinanceFilter: (FinanceLedgerFilter, String?, String?) -> Unit = { _, _, _ -> },
    onToggleFinanceViewMode: () -> Unit = {},
    onCreateFinanceTransaction: (String, String, String?, TransactionStatus, List<FinanceSplitDraft>) -> Unit = { _, _, _, _, _ -> },
    onDeleteFinanceTransaction: (String) -> Unit = {},
    onAutoPadAccountBalance: (String, Long) -> Unit = { _, _ -> },
    onCreateFinanceAccount: (String, String, AccountCategory, String, Boolean, Boolean, LiquidTier) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteFinanceAccount: (String) -> Unit = {},
    onRepayCreditStatement: (clearingGroupId: String, fundingAccountId: String, date: String) -> Unit = { _, _, _ -> },
    onRepayLoanTranche: (transactionId: String) -> Unit = {},
    onCreateAmortizationPlan: (
        name: String,
        principalMinor: Long,
        currency: String,
        totalPeriods: Int,
        repaymentMethod: AmortizationMethod,
        annualRateBps: Int,
        feePerPeriodMinor: Long,
        startDate: String,
        repaymentDay: Int,
        liabilityAccountId: String,
        fundingAccountId: String?,
        interestAccountId: String,
        recordInitialDisbursement: Boolean,
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onDeleteAmortizationPlan: (String) -> Unit = {},
    onSelectFinanceRunwayHorizon: (Int) -> Unit = {},
    onCreateBudgetRule: (String, BudgetScopeType, String, Long, String, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteBudgetRule: (String) -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = routeForTab(state.selectedTab),
    ) {
        composable(AppRoute.Day.route) {
            HomeScreen(
                state = state,
                liveNow = liveNow,
                onOpenTask = onOpenTask,
                onStartPomodoro = onOpenStartPomodoro,
            )
        }
        composable(AppRoute.Tasks.route) {
            TasksScreen(
                state = state,
                liveNow = liveNow,
                isPomodoroActive = isPomodoroActive,
                onSelectTaskFilter = onSelectTaskFilter,
                onOpenTask = onOpenTask,
                onOpenStartPomodoro = onOpenStartPomodoro,
                onCreateRoutine = onCreateRoutine,
                onOpenRoutine = onOpenRoutine,
                onRunRoutine = onRunRoutine,
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                state = state,
                onUpdateThemePreference = onUpdateThemePreference,
                onUpdatePreferences = onUpdatePreferences,
                onReopenLogin = onReopenLogin,
                onLogout = onLogout,
            )
        }
        composable(AppRoute.FinanceCockpit.route) {
            FinanceCockpitScreen(
                state = state,
                onOpenLedger = { navController.navigate(AppRoute.Finance.route) },
                onOpenClearingHub = { navController.navigate(AppRoute.DebtClearingHub.route) },
                onOpenCreateTransaction = { navController.navigate(AppRoute.CreateFinanceTransaction.route) },
                onOpenCreateBudgetRule = { navController.navigate(AppRoute.CreateBudgetRule.route) },
                onSelectHorizon = onSelectFinanceRunwayHorizon,
                onDeleteBudgetRule = onDeleteBudgetRule,
            )
        }
        composable(AppRoute.Finance.route) {
            FinanceLedgerScreen(
                state = state,
                onSelectFilter = onSelectFinanceFilter,
                onToggleViewMode = onToggleFinanceViewMode,
                onOpenCreateTransaction = { navController.navigate(AppRoute.CreateFinanceTransaction.route) },
                onOpenAccounts = { navController.navigate(AppRoute.FinanceAccounts.route) },
                onOpenCockpit = { navController.navigate(AppRoute.FinanceCockpit.route) },
                onOpenClearingHub = { navController.navigate(AppRoute.DebtClearingHub.route) },
                onDeleteTransaction = onDeleteFinanceTransaction,
            )
        }
        composable(AppRoute.DebtClearingHub.route) {
            ClearingHubScreen(
                state = state,
                onOpenLedger = { navController.navigate(AppRoute.Finance.route) },
                onOpenCockpit = { navController.navigate(AppRoute.FinanceCockpit.route) },
                onOpenCombinedRepayment = { navController.navigate(AppRoute.CombinedRepayment.route) },
                onOpenAmortizationWizard = { navController.navigate(AppRoute.AmortizationWizard.route) },
                onRepayStatement = { clearingGroupId ->
                    navController.navigate(AppRoute.CombinedRepayment.route)
                },
                onRepayLoanTranche = onRepayLoanTranche,
                onDeleteAmortizationPlan = onDeleteAmortizationPlan,
            )
        }
        composable(AppRoute.CreateBudgetRule.route) {
            BudgetRuleSheet(
                state = state,
                onDismiss = { navController.popBackStack() },
                onCreate = { name, scopeType, accountPrefix, monthlyLimitMinor, currency, includeInRunway ->
                    onCreateBudgetRule(name, scopeType, accountPrefix, monthlyLimitMinor, currency, includeInRunway)
                    navController.popBackStack()
                },
            )
        }
        composable(AppRoute.CombinedRepayment.route) {
            CombinedRepaymentSheet(
                state = state,
                onDismiss = { navController.popBackStack() },
                onRepayStatement = onRepayCreditStatement,
                onRepayLoanTranche = onRepayLoanTranche,
            )
        }
        composable(AppRoute.AmortizationWizard.route) {
            AmortizationWizardSheet(
                state = state,
                onDismiss = { navController.popBackStack() },
                onCreate = onCreateAmortizationPlan,
            )
        }
        composable(AppRoute.CreateFinanceTransaction.route) {
            CreateFinanceTransactionSheet(
                state = state,
                onDismiss = { navController.popBackStack() },
                onCreate = { txDate, summary, payee, status, splits ->
                    onCreateFinanceTransaction(txDate, summary, payee, status, splits)
                },
            )
        }
        composable(AppRoute.FinanceAccounts.route) {
            FinanceAccountsSheet(
                state = state,
                onDismiss = { navController.popBackStack() },
                onAutoPad = onAutoPadAccountBalance,
                onCreateAccount = onCreateFinanceAccount,
                onDeleteAccount = onDeleteFinanceAccount,
            )
        }
        composable(AppRoute.CreateTask.route) {
            CreateTaskSheet(
                context = taskCreateContextFor(state.selectedTab, state.selectedDate),
                onDismiss = { navController.popBackStack() },
                onCreate = { draft ->
                    onCreateTaskConfirm(
                        draft.title,
                        draft.detail,
                        draft.parentId,
                        draft.startNotBefore,
                        draft.endNotAfter,
                        draft.estimatedDuration,
                    )
                    navController.popBackStack()
                },
            )
        }
        composable(AppRoute.CreateRoutine.route) {
            CreateRoutineSheet(
                onDismiss = { navController.popBackStack() },
                onCreate = { title, detail, recurrenceRule, defaultStartNotBefore, defaultEstimatedDuration ->
                    onCreateRoutineConfirm(title, detail, recurrenceRule, defaultStartNotBefore, defaultEstimatedDuration)
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = AppRoute.TaskDetail.route,
            arguments = listOf(navArgument(AppRoute.TaskDetail.ARG_TASK_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.read {
                getStringOrNull(AppRoute.TaskDetail.ARG_TASK_ID)
            }
            val task = state.taskView.tasks.firstOrNull { it.id == taskId }
            if (task == null) {
                LaunchedEffect(taskId) { navController.popBackStack() }
            } else {
                TaskActionsSheet(
                    task = task,
                    history = taskHistoryFor(task.id),
                    availableParents = state.taskView.tasks.filter { candidate ->
                        candidate.id != task.id && candidate.routineId == null
                    },
                    availableRoutines = state.taskView.routines,
                    isPomodoroActive = isPomodoroActive,
                    onDismiss = { navController.popBackStack() },
                    onSaveTask = { taskRouteId, title, detail, parentId, startNotBefore, endNotAfter, estimatedDuration ->
                        onSaveTask(taskRouteId, title, detail, parentId, startNotBefore, endNotAfter, estimatedDuration)
                        navController.popBackStack()
                    },
                    onProgress = { note ->
                        onProgressTask(task.id, note.ifBlank { null })
                        navController.popBackStack()
                    },
                    onComplete = { note ->
                        onCompleteTask(task.id, note.ifBlank { null })
                        navController.popBackStack()
                    },
                    onSkip = { note ->
                        onSkipTask(task.id, note.ifBlank { null })
                        navController.popBackStack()
                    },
                    onPostpone = { taskRouteId, startNotBefore, endNotAfter, estimatedDuration, note ->
                        onPostponeTask(taskRouteId, startNotBefore, endNotAfter, estimatedDuration, note)
                        navController.popBackStack()
                    },
                    onDelete = {
                        onDeleteTask(task.id)
                        navController.popBackStack()
                    },
                    onStartPomodoro = {
                        onStartPomodoro(task.id, PomodoroPhase.FOCUS)
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(
            route = AppRoute.RoutineDetail.route,
            arguments = listOf(navArgument(AppRoute.RoutineDetail.ARG_ROUTINE_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val routineId = backStackEntry.arguments?.read {
                getStringOrNull(AppRoute.RoutineDetail.ARG_ROUTINE_ID)
            }
            val routine = state.taskView.routines.firstOrNull { it.id == routineId }
            if (routine == null) {
                LaunchedEffect(routineId) { navController.popBackStack() }
            } else {
                RoutineActionsSheet(
                    routine = routine,
                    onDismiss = { navController.popBackStack() },
                    onSave = { routeRoutineId, title, detail, recurrenceRule, defaultStartNotBefore, defaultEstimatedDuration ->
                        onSaveRoutine(routeRoutineId, title, detail, recurrenceRule, defaultStartNotBefore, defaultEstimatedDuration)
                        navController.popBackStack()
                    },
                    onToggle = {
                        onToggleRoutine(routine.id)
                        navController.popBackStack()
                    },
                    onDelete = {
                        onDeleteRoutine(routine.id)
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(AppRoute.StartPomodoro.route) {
            StartPomodoroSheet(
                state = state,
                hasActive = state.activePomodoroSession != null,
                onDismiss = { navController.popBackStack() },
                onStart = { taskId, phase ->
                    onStartPomodoro(taskId, phase)
                    navController.popBackStack()
                },
            )
        }
        composable(AppRoute.CompletePomodoro.route) {
            if (state.activePomodoroSession == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                CompletePomodoroSheet(
                    state = state,
                    liveNow = liveNow,
                    onDismiss = { navController.popBackStack() },
                    onSave = { note, update ->
                        onCompletePomodoro(note, update)
                        navController.popBackStack()
                    },
                    onStop = { note ->
                        onStopPomodoro(note)
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
