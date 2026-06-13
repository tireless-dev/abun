package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.ui.theme.resolveDarkTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ThemePreferenceSupportTest {
    @Test
    fun `theme preference resolves against system setting`() {
        assertEquals(false, resolveDarkTheme(ThemePreference.SYSTEM, isSystemDarkTheme = false))
        assertEquals(true, resolveDarkTheme(ThemePreference.SYSTEM, isSystemDarkTheme = true))
        assertEquals(false, resolveDarkTheme(ThemePreference.LIGHT, isSystemDarkTheme = true))
        assertEquals(true, resolveDarkTheme(ThemePreference.DARK, isSystemDarkTheme = false))
    }

    @Test
    fun `settings screen exposes appearance theme options`() = runDesktopComposeUiTest {
        setContent {
            SettingsScreenContent(
                state = screenshotState(),
                onUpdateThemePreference = {},
                onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
            )
        }

        onNodeWithText("Appearance").fetchSemanticsNode()
        onNodeWithText("Theme").fetchSemanticsNode()
        onNodeWithText("System").fetchSemanticsNode()
        onNodeWithText("Light").fetchSemanticsNode()
        onNodeWithText("Dark").fetchSemanticsNode()
    }
}
