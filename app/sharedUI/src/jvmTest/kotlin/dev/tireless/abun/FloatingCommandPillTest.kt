package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.AppModule
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.FloatingCommandPill
import dev.tireless.abun.ui.FloatingCommandSheet
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FloatingCommandPillTest {

    @Test
    fun `floating command pill displays workspace label and action button`() = runDesktopComposeUiTest {
        var switcherClicked = false
        var actionClicked = false

        setContent {
            AppTheme {
                FloatingCommandPill(
                    selectedTab = AppTab.TODAY,
                    enabledModules = setOf(AppModule.TASKS, AppModule.FINANCE),
                    primaryActionLabel = "+ Capture",
                    onPrimaryActionClick = { actionClicked = true },
                    isExpanded = false,
                    onToggleExpanded = { switcherClicked = true },
                )
            }
        }

        onNodeWithTag("floating-command-pill-switcher").assertIsDisplayed()
        onNodeWithText("Dashboard").assertIsDisplayed()
        onNodeWithTag("floating-command-pill-action").assertIsDisplayed()
        onNodeWithText("+ Capture").assertIsDisplayed()

        onNodeWithTag("floating-command-pill-switcher").performClick()
        assertTrue(switcherClicked)

        onNodeWithTag("floating-command-pill-action").performClick()
        assertTrue(actionClicked)
    }

    @Test
    fun `floating command sheet shows only enabled module cards and handles navigation`() = runDesktopComposeUiTest {
        var selectedTab: AppTab? = null

        setContent {
            AppTheme {
                FloatingCommandSheet(
                    enabledModules = setOf(AppModule.TASKS),
                    currentTab = AppTab.TODAY,
                    onSelectTab = { selectedTab = it },
                    openTasksCount = 5,
                )
            }
        }

        onNodeWithTag("floating-command-sheet").assertIsDisplayed()
        onNodeWithTag("command-sheet-dashboard").assertIsDisplayed()
        onNodeWithTag("command-sheet-module-TASKS").assertIsDisplayed()
        onNodeWithTag("command-sheet-module-FINANCE").assertDoesNotExist()
        onNodeWithTag("command-sheet-module-NOTES").assertDoesNotExist()
        onNodeWithTag("command-sheet-module-THINGS").assertDoesNotExist()
        onNodeWithTag("command-sheet-settings").assertIsDisplayed()

        onNodeWithTag("command-sheet-module-TASKS").performClick()
        assertEquals(AppTab.TASKS, selectedTab)

        onNodeWithTag("command-sheet-settings").performClick()
        assertEquals(AppTab.SETTINGS, selectedTab)
    }
}
