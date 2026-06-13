package dev.tireless.abun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopWindowSizingTest {
    @Test
    fun `default desktop window uses portrait phone ratio with mobile guardrails`() {
        assertEquals(430f, DesktopWindowSizing.defaultWidthDp)
        assertEquals(932f, DesktopWindowSizing.defaultHeightDp)
        assertEquals(360f, DesktopWindowSizing.minimumWidthDp)
        assertEquals(780f, DesktopWindowSizing.minimumHeightDp)
        assertTrue(
            DesktopWindowSizing.defaultHeightDp > DesktopWindowSizing.defaultWidthDp,
            "Desktop validation window should launch in portrait orientation",
        )
    }
}
