package dev.tireless.abun

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.ui.theme.AppTypography
import dev.tireless.abun.ui.theme.AppTheme
import dev.tireless.abun.ui.theme.LightColorScheme
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.appType
import dev.tireless.abun.ui.theme.withMaterialContentColor
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class Material3MigrationTest {
    @Test
    fun `app theme exposes static material3 palette`() = runDesktopComposeUiTest {
        var primary = Color.Unspecified
        var surface = Color.Unspecified
        var background = Color.Unspecified

        setContent {
            AppTheme(darkTheme = false) {
                primary = MaterialTheme.colorScheme.primary
                surface = MaterialTheme.colorScheme.surface
                background = MaterialTheme.colorScheme.background
            }
        }

        waitForIdle()
        assertEquals(Color(0xFF3F4EAE), primary)
        assertEquals(Color(0xFFFFFFFF), surface)
        assertEquals(Color(0xFFFAFAF7), background)
    }

    @Test
    fun `interactive text styles inherit material content color`() {
        val interactiveBody = appType(AppTypography, LightColorScheme).body.withMaterialContentColor()

        assertEquals(Color.Unspecified, interactiveBody.color)
        assertEquals(AppTypography.bodyMedium.fontSize, interactiveBody.fontSize)
    }

    @Test
    fun `theme tokens expose editorial spacing radii and typography`() = runDesktopComposeUiTest {
        var sectionTitleSize = 0f
        var bodySize = 0f
        var captionSize = 0f
        var screenPadding = -1
        var largeSpacing = -1
        var largeRadius = -1

        setContent {
            AppTheme(darkTheme = false) {
                sectionTitleSize = ThemeTokens.type.sectionTitle.fontSize.value
                bodySize = ThemeTokens.type.body.fontSize.value
                captionSize = ThemeTokens.type.label.fontSize.value
                screenPadding = ThemeTokens.spacing.screenPadding
                largeSpacing = ThemeTokens.spacing.xl
                largeRadius = ThemeTokens.radii.large
            }
        }

        waitForIdle()
        assertEquals(24f, sectionTitleSize)
        assertEquals(16f, bodySize)
        assertEquals(12f, captionSize)
        assertEquals(24, screenPadding)
        assertEquals(32, largeSpacing)
        assertEquals(20, largeRadius)
    }
}
