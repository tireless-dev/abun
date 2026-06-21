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
}
