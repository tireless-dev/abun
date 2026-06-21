package dev.tireless.abun

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LucideAdoptionTest {
    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { current ->
        current.parentFile
    }.first { candidate ->
        candidate.resolve("gradle.properties").exists()
    }

    @Test
    fun `shared UI source renders lucide plus icon instead of text plus`() {
        val appSource = repoRoot
            .resolve("app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt")
            .readText()

        assertTrue(appSource.contains("Lucide"), "Expected App.kt to reference a Lucide icon")
        assertTrue(appSource.contains("Icon("), "Expected App.kt to render a Material3 Icon")
        assertFalse(appSource.contains("icon = { Text(\"+\") }"), "Expected App.kt to stop using a text plus for the FAB icon")
    }

    @Test
    fun `docs mention lucide as shared icon family`() {
        val designDoc = repoRoot.resolve("docs/base/shared-ui-design-system.md").readText()
        val architectureDoc = repoRoot.resolve("docs/base/technical-architecture.md").readText()
        val informationDoc = repoRoot.resolve("docs/base/information-architecture.md").readText()

        assertTrue(designDoc.contains("Lucide"), "Expected shared UI design system doc to mention Lucide")
        assertTrue(architectureDoc.contains("Lucide"), "Expected base technical architecture doc to mention Lucide")
        assertTrue(informationDoc.contains("Lucide"), "Expected information architecture doc to mention Lucide")
    }

    @Test
    fun `shared UI docs mention top app bar dropdown selectors`() {
        val designDoc = repoRoot.resolve("docs/base/shared-ui-design-system.md").readText()
        val architectureDoc = repoRoot.resolve("docs/base/technical-architecture.md").readText()
        val tasksDoc = repoRoot.resolve("docs/tasks/functionality-design.md").readText()

        assertTrue(designDoc.contains("top-app-bar dropdown selectors"), "Expected shared UI design system doc to mention top-app-bar dropdown selectors")
        assertTrue(architectureDoc.contains("top app bar chrome may host lightweight shared selectors"), "Expected technical architecture doc to mention shared top bar selectors")
        assertTrue(tasksDoc.contains("title bar selector"), "Expected tasks functionality doc to mention the title bar selector")
    }

    @Test
    fun `tasks screen source removes in-body task subtab segmented control`() {
        val appSource = repoRoot
            .resolve("app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt")
            .readText()

        assertFalse(appSource.contains("TaskSubTab.entries.forEachIndexed"), "Expected TaskSubTab loop to move out of TasksScreen")
        assertFalse(appSource.contains("SingleChoiceSegmentedButtonRow(\n        modifier = Modifier.fillMaxWidth(),\n    ) {\n        TaskSubTab.entries"), "Expected TasksScreen segmented selector to be removed")
    }

    @Test
    fun `top app bar source uses tasks subtab selector with lucide icons`() {
        val appSource = repoRoot
            .resolve("app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt")
            .readText()
        val selectorSource = repoRoot
            .resolve("app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/TaskTopBarSubtabSelector.kt")
            .readText()

        assertTrue(appSource.contains("TaskTopBarSubtabSelector("), "Expected App.kt to use the shared top-bar selector")
        assertTrue(appSource.contains("Lucide.ListTodo"), "Expected Tasks icon mapping to use Lucide.ListTodo")
        assertTrue(appSource.contains("Lucide.CalendarCheck"), "Expected Routines icon mapping to use Lucide.CalendarCheck")
        assertTrue(appSource.contains("Lucide.Timer"), "Expected Pomodoro icon mapping to use Lucide.Timer")
        assertTrue(selectorSource.contains("Lucide.ChevronLeft"), "Expected collapsed selector chevron to use Lucide.ChevronLeft")
        assertTrue(selectorSource.contains("Lucide.ChevronDown"), "Expected expanded selector chevron to use Lucide.ChevronDown")
    }
}
