package dev.tireless.abun

import dev.tireless.abun.ui.theme.CommandResult
import dev.tireless.abun.ui.theme.detectDesktopSystemDarkTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSystemThemeDetectorTest {
    @Test
    fun `mac desktop detector reports dark when AppleInterfaceStyle is Dark`() {
        assertTrue(
            detectDesktopSystemDarkTheme(
                osName = "Mac OS X",
                commandRunner = { CommandResult(exitCode = 0, stdout = "Dark\n") },
            ),
        )
    }

    @Test
    fun `mac desktop detector reports light when AppleInterfaceStyle is absent`() {
        assertFalse(
            detectDesktopSystemDarkTheme(
                osName = "Mac OS X",
                commandRunner = { CommandResult(exitCode = 1, stdout = "") },
            ),
        )
    }

    @Test
    fun `non mac desktop detector falls back to light`() {
        assertFalse(
            detectDesktopSystemDarkTheme(
                osName = "Linux",
                commandRunner = { error("should not run") },
            ),
        )
    }
}
