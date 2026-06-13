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
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/ActionRow.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/Feedback.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/Section.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/Sheet.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/RecurrenceRuleEditor.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/layout/Scaffold.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/layout/ScreenContainer.kt",
            "app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/theme/Buttons.kt",
        )

        redundantWrappers.forEach { relativePath ->
            assertFalse(
                repoRoot.resolve(relativePath).exists(),
                "Expected redundant wrapper to be removed: $relativePath",
            )
        }
    }
}
