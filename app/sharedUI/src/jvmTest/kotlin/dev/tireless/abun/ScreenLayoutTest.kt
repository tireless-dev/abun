package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.ui.screens.HomeScreen
import dev.tireless.abun.ui.screens.SettingsScreenContent
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScreenLayoutTest {
    @Test
    fun `home screen stacks its panels vertically`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(themePreference = ThemePreference.LIGHT) {
                HomeScreen(
                    state = screenshotState(),
                    liveNow = ScreenshotNow,
                    onOpenTask = {},
                    onStartPomodoro = {},
                )
            }
        }

        val dayHeaderTop = onNodeWithTag("day-panel-summary").fetchSemanticsNode().boundsInRoot.top
        val timelineHeaderTop = onNodeWithTag("day-panel-timeline").fetchSemanticsNode().boundsInRoot.top
        val pomodoroHeaderTop = onNodeWithTag("day-panel-pomodoro").fetchSemanticsNode().boundsInRoot.top

        assertTrue(timelineHeaderTop > dayHeaderTop, "Day timeline should be below the dashboard summary panel")
        assertTrue(pomodoroHeaderTop > timelineHeaderTop, "Pomodoro should be below the timeline panel")
    }

    @Test
    fun `home screen moves pomodoro action into pomodoro panel`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(themePreference = ThemePreference.LIGHT) {
                HomeScreen(
                    state = screenshotState(activePomodoroSession = activePomodoroSession()),
                    liveNow = ScreenshotNow,
                    onOpenTask = {},
                    onStartPomodoro = {},
                )
            }
        }

        onNodeWithText("Complete or stop").assertIsDisplayed()
    }

    @Test
    fun `home screen shows start button in pomodoro panel when idle`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(themePreference = ThemePreference.LIGHT) {
                HomeScreen(
                    state = screenshotState(activePomodoroSession = null),
                    liveNow = ScreenshotNow,
                    onOpenTask = {},
                    onStartPomodoro = {},
                )
            }
        }

        onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun `settings screen stacks its panels vertically`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(themePreference = ThemePreference.LIGHT) {
                SettingsScreenContent(
                    state = screenshotState(),
                    onUpdateThemePreference = {},
                    onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                    onReopenLogin = {},
                    onLogout = {},
                )
            }
        }

        val cloudTop = onNodeWithTag("settings-panel-cloud").fetchSemanticsNode().boundsInRoot.top
        val defaultsTaskTop = onNodeWithTag("settings-panel-task-defaults").fetchSemanticsNode().boundsInRoot.top
        val themeTop = onNodeWithTag("settings-panel-theme").fetchSemanticsNode().boundsInRoot.top

        assertTrue(defaultsTaskTop > cloudTop, "Task defaults should be below sync status")
        assertTrue(themeTop > defaultsTaskTop, "Theme should be below task defaults")
    }
}
