package dev.tireless.abun

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class Material3WrapperRemovalTest {
    @Test
    fun `pass through material3 wrappers are removed`() {
        val repoRoot = generateSequence(File(System.getProperty("user.dir"))) { current ->
            current.parentFile
        }.first { candidate ->
            candidate.resolve("gradle.properties").exists()
        }
        val redundantWrappers = listOf(
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/Button.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/SegmentedControl.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/Text.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/TextField.kt",
        )

        redundantWrappers.forEach { relativePath ->
            assertFalse(
                repoRoot.resolve(relativePath).exists(),
                "Expected redundant wrapper to be removed: $relativePath",
            )
        }
    }
}
