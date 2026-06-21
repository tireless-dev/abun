package dev.tireless.abun

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDebugRuntimeTest {
    @Test
    fun `desktop debug runtime honors explicit true property`() {
        assertTrue(
            isDesktopDebugRuntime(
                debugProperty = "true",
                workingDirectory = "/tmp/not-a-repo",
            ),
        )
    }

    @Test
    fun `desktop debug runtime honors explicit false property`() {
        assertFalse(
            isDesktopDebugRuntime(
                debugProperty = "false",
                workingDirectory = System.getProperty("user.dir"),
            ),
        )
    }

    @Test
    fun `desktop debug runtime defaults to true inside local repo checkout`() {
        assertTrue(
            isDesktopDebugRuntime(
                debugProperty = null,
                workingDirectory = System.getProperty("user.dir"),
            ),
        )
    }

    @Test
    fun `repo root discovery walks parent directories`() {
        val root = findRepoRoot("${
            System.getProperty("user.dir")
        }/app/sharedUI/build/tmp")

        assertTrue(root != null)
    }
}
