package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigation.compose.rememberNavController
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.navigation.AppNavHost
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {
    @Test
    fun `app nav host renders the selected top level route`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                AppNavHost(
                    navController = rememberNavController(),
                    state = screenshotState(selectedTab = AppTab.SETTINGS),
                    liveNow = ScreenshotNow,
                    isPomodoroActive = false,
                    onSelectTaskFilter = {},
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                    onUpdateThemePreference = {},
                    onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                    onReopenLogin = {},
                    onLogout = {},
                )
            }
        }

        onNodeWithText("Appearance").fetchSemanticsNode()
    }
}
