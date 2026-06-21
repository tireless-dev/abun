package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {
    @Test
    fun `app shell shows one top level screen title for selected tab`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                App()
            }
        }

        onAllNodesWithText("Day").assertCountEquals(1)
    }
}
