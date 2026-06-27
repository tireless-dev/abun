package dev.tireless.abun.ui.navigation

import dev.tireless.abun.app.AppTab

sealed class AppRoute(val route: String) {
    data object Day : AppRoute("day")
    data object Tasks : AppRoute("tasks")
    data object Settings : AppRoute("settings")
    data object CreateTask : AppRoute("create-task")
    data object CreateRoutine : AppRoute("create-routine")
    data object StartPomodoro : AppRoute("start-pomodoro")
    data object CompletePomodoro : AppRoute("complete-pomodoro")
    data object TaskDetail : AppRoute("task-detail/{taskId}") {
        const val ARG_TASK_ID = "taskId"
        fun create(taskId: String): String = "task-detail/$taskId"
    }
    data object RoutineDetail : AppRoute("routine-detail/{routineId}") {
        const val ARG_ROUTINE_ID = "routineId"
        fun create(routineId: String): String = "routine-detail/$routineId"
    }
}

fun routeForTab(tab: AppTab): String = when (tab) {
    AppTab.TODAY -> AppRoute.Day.route
    AppTab.TASKS -> AppRoute.Tasks.route
    AppTab.SETTINGS -> AppRoute.Settings.route
}

fun appTabForRoute(route: String?): AppTab = when {
    route == null -> AppTab.TODAY
    route == AppRoute.Tasks.route -> AppTab.TASKS
    route == AppRoute.Settings.route -> AppTab.SETTINGS
    route.startsWith("task-detail/") -> AppTab.TASKS
    route.startsWith("routine-detail/") -> AppTab.TASKS
    route == AppRoute.CreateTask.route -> AppTab.TASKS
    route == AppRoute.CreateRoutine.route -> AppTab.TASKS
    route == AppRoute.StartPomodoro.route -> AppTab.TASKS
    route == AppRoute.CompletePomodoro.route -> AppTab.TASKS
    else -> AppTab.TODAY
}
