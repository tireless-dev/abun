package dev.tireless.abun.ui.navigation

import dev.tireless.abun.app.AppTab

sealed class AppRoute(val route: String) {
    data object Day : AppRoute("day")
    data object Tasks : AppRoute("tasks")
    data object Finance : AppRoute("finance")
    data object Notes : AppRoute("notes")
    data object Things : AppRoute("things")
    data object Settings : AppRoute("settings")
    data object CreateTask : AppRoute("create-task")
    data object CreateRoutine : AppRoute("create-routine")
    data object StartPomodoro : AppRoute("start-pomodoro")
    data object CompletePomodoro : AppRoute("complete-pomodoro")
    data object CreateFinanceTransaction : AppRoute("create-finance-transaction")
    data object FinanceAccounts : AppRoute("finance-accounts")
    data object DebtClearingHub : AppRoute("debt-clearing-hub")
    data object CombinedRepayment : AppRoute("combined-repayment")
    data object AmortizationWizard : AppRoute("amortization-wizard")
    data object FinanceCockpit : AppRoute("finance-cockpit")
    data object CreateBudgetRule : AppRoute("create-budget-rule")
    data object TaskDetail : AppRoute("task-detail/{taskId}") {
        const val ARG_TASK_ID = "taskId"
        fun create(taskId: String): String = "task-detail/$taskId"
    }
    data object RoutineDetail : AppRoute("routine-detail/{routineId}") {
        const val ARG_ROUTINE_ID = "routineId"
        fun create(routineId: String): String = "routine-detail/$routineId"
    }

    companion object {
        const val FinanceCockpitRoute = "finance-cockpit"
        const val FinanceLedgerRoute = "finance"
        const val ClearingHubRoute = "debt-clearing-hub"
        const val NotesRoute = "notes"
        const val ThingsRoute = "things"
    }
}

val FinanceCockpitRoute: String = AppRoute.FinanceCockpit.route
val FinanceLedgerRoute: String = AppRoute.Finance.route
val ClearingHubRoute: String = AppRoute.DebtClearingHub.route
val NotesRoute: String = AppRoute.Notes.route
val ThingsRoute: String = AppRoute.Things.route

fun routeForTab(tab: AppTab): String = when (tab) {
    AppTab.TODAY -> AppRoute.Day.route
    AppTab.TASKS -> AppRoute.Tasks.route
    AppTab.FINANCE -> AppRoute.FinanceCockpit.route
    AppTab.NOTES -> AppRoute.Notes.route
    AppTab.THINGS -> AppRoute.Things.route
    AppTab.SETTINGS -> AppRoute.Settings.route
}

fun appTabForRoute(route: String?): AppTab = when {
    route == null -> AppTab.TODAY
    route == AppRoute.Tasks.route -> AppTab.TASKS
    route == AppRoute.Finance.route -> AppTab.FINANCE
    route == AppRoute.FinanceCockpit.route -> AppTab.FINANCE
    route == AppRoute.Notes.route -> AppTab.NOTES
    route == AppRoute.Things.route -> AppTab.THINGS
    route == AppRoute.CreateBudgetRule.route -> AppTab.FINANCE
    route == AppRoute.CreateFinanceTransaction.route -> AppTab.FINANCE
    route == AppRoute.FinanceAccounts.route -> AppTab.FINANCE
    route == AppRoute.DebtClearingHub.route -> AppTab.FINANCE
    route == AppRoute.CombinedRepayment.route -> AppTab.FINANCE
    route == AppRoute.AmortizationWizard.route -> AppTab.FINANCE
    route == AppRoute.Settings.route -> AppTab.SETTINGS
    route.startsWith("task-detail/") -> AppTab.TASKS
    route.startsWith("routine-detail/") -> AppTab.TASKS
    route == AppRoute.CreateTask.route -> AppTab.TASKS
    route == AppRoute.CreateRoutine.route -> AppTab.TASKS
    route == AppRoute.StartPomodoro.route -> AppTab.TASKS
    route == AppRoute.CompletePomodoro.route -> AppTab.TASKS
    else -> AppTab.TODAY
}
