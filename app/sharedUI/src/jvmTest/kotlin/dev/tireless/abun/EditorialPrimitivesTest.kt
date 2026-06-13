package dev.tireless.abun

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.EditorialScreen
import dev.tireless.abun.ui.EditorialSection
import dev.tireless.abun.ui.editorialStatusColors
import dev.tireless.abun.ui.theme.AppTheme
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.sync.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EditorialPrimitivesTest {
    @Test
    fun `editorial screen and card render provided content`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(themePreference = ThemePreference.LIGHT) {
                EditorialScreen {
                    EditorialSection(
                        eyebrow = "Review",
                        title = "Tasks",
                        supportingText = "Shared editorial shell",
                    ) {
                        EditorialCard(
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "editorial-card",
                        ) {
                            Column(Modifier.fillMaxWidth()) {}
                        }
                    }
                }
            }
        }

        onNodeWithTag("editorial-card").assertExists()
    }

    @Test
    fun `editorial status colors use muted tonal surfaces`() = runDesktopComposeUiTest {
        var pendingContainer = Color.Unspecified
        var completedLabel = Color.Unspecified

        setContent {
            AppTheme(themePreference = ThemePreference.LIGHT) {
                pendingContainer = editorialStatusColors(TaskStatus.PENDING, ThemeTokens.colors).container
                completedLabel = editorialStatusColors(TaskStatus.COMPLETED, ThemeTokens.colors).content
            }
        }

        waitForIdle()
        assertEquals(Color(0xFFF4EFE2), pendingContainer)
        assertEquals(Color(0xFF3D6B55), completedLabel)
    }
}
