package dev.tireless.abun

import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.navigation.AppRoute
import dev.tireless.abun.ui.navigation.appTabForRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRouteMappingTest {
    @Test
    fun `route mapping matches top level tabs`() {
        assertEquals(AppTab.TODAY, appTabForRoute(AppRoute.Day.route))
        assertEquals(AppTab.TASKS, appTabForRoute(AppRoute.Tasks.route))
        assertEquals(AppTab.SETTINGS, appTabForRoute(AppRoute.Settings.route))
    }

    @Test
    fun `sheet and detail routes remain attached to tasks tab`() {
        assertEquals(AppTab.TASKS, appTabForRoute(AppRoute.CreateTask.route))
        assertEquals(AppTab.TASKS, appTabForRoute(AppRoute.CreateRoutine.route))
        assertEquals(AppTab.TASKS, appTabForRoute(AppRoute.StartPomodoro.route))
        assertEquals(AppTab.TASKS, appTabForRoute(AppRoute.CompletePomodoro.route))
        assertEquals(AppTab.TASKS, appTabForRoute(AppRoute.TaskDetail.create("task-1")))
        assertEquals(AppTab.TASKS, appTabForRoute(AppRoute.RoutineDetail.create("routine-1")))
    }
}
