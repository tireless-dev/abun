package dev.tireless.abun

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class Material3MigrationTest {
    @Test
    fun `app theme exposes static material3 palette`() = runDesktopComposeUiTest {
        var primary = Color.Unspecified
        var surface = Color.Unspecified

        setContent {
            AppTheme(darkTheme = false) {
                primary = MaterialTheme.colorScheme.primary
                surface = MaterialTheme.colorScheme.surface
            }
        }

        waitForIdle()
        assertEquals(Color(0xFF35693F), primary)
        assertEquals(Color(0xFFFFFFFF), surface)
    }
}
