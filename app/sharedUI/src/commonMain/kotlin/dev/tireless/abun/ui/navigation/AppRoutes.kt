package dev.tireless.abun.ui.navigation

import dev.tireless.abun.app.AppTab

sealed class AppRoute(val route: String) {
    data object Day : AppRoute("day")
    data object Tasks : AppRoute("tasks")
    data object Settings : AppRoute("settings")
}

fun routeForTab(tab: AppTab): String = when (tab) {
    AppTab.TODAY -> AppRoute.Day.route
    AppTab.TASKS -> AppRoute.Tasks.route
    AppTab.SETTINGS -> AppRoute.Settings.route
}

fun appTabForRoute(route: String?): AppTab = when (route) {
    AppRoute.Tasks.route -> AppTab.TASKS
    AppRoute.Settings.route -> AppTab.SETTINGS
    else -> AppTab.TODAY
}
