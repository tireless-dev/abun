package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.components.ActionRow
import dev.tireless.abun.ui.components.AppText
import dev.tireless.abun.ui.components.Button
import dev.tireless.abun.ui.components.EmptyState
import dev.tireless.abun.ui.components.Fab
import dev.tireless.abun.ui.components.InlineError
import dev.tireless.abun.ui.components.Section
import dev.tireless.abun.ui.components.SectionDivider
import dev.tireless.abun.ui.components.SectionTitle
import dev.tireless.abun.ui.components.SegmentedControl
import dev.tireless.abun.ui.components.Sheet
import dev.tireless.abun.ui.components.TextField
import dev.tireless.abun.ui.layout.Scaffold
import dev.tireless.abun.ui.layout.ScreenContainer
import dev.tireless.abun.ui.theme.ThemeTokens
import kotlin.test.Test

class ComponentScreenshotTest {
    @Test
    fun componentGallery() = captureScreenshot("components/component_gallery") {
        ScreenContainer(applyVerticalSafeInsets = false) {
            AppText("Display sample", style = ThemeTokens.type.display)
            AppText("Primary body copy for shared UI.", style = ThemeTokens.type.body)
            AppText("Muted supporting copy", style = ThemeTokens.type.bodyMuted)
            InlineError("Inline error message")
            SectionDivider()
            Section {
                SectionTitle("Section title")
                AppText("Plain section content with custom spacing.", style = ThemeTokens.type.body)
            }
            EmptyState("No rows to show.")
            TextField(value = "hello@abun.dev", onValueChange = {}, label = "Email")
            SegmentedControl(
                options = listOf("Today", "Week", "Month"),
                selected = "Week",
                onSelect = {},
            )
            ActionRow {
                Button(label = "Enabled", onClick = {})
                Button(label = "Disabled", onClick = {}, enabled = false)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Fab(label = "Task", onClick = {})
            }
        }
    }

    @Test
    fun sheet() = captureScreenshot("components/sheet") {
        CreateTaskSheet(
            availableParents = listOf(),
            onDismiss = {},
            onCreate = { _, _, _, _, _, _ -> },
        )
    }

    @Test
    fun scaffold() = captureScreenshot("components/scaffold") {
        Scaffold(
            title = "Today",
            selectedTab = AppTab.TODAY.tabLabelForScreenshot(),
            tabs = AppTab.entries.map { it.tabLabelForScreenshot() },
            onSelectTab = {},
            floatingActionLabel = "Task",
            onFloatingAction = {},
        ) {
            ComponentScaffoldBody()
        }
    }
}

@Composable
private fun ComponentScaffoldBody() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeTokens.colors.background)
            .padding(ThemeTokens.spacing.screenPaddingDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
    ) {
        Section {
            SectionTitle("Summary")
            AppText("A framed app shell with tabs and floating action.", style = ThemeTokens.type.body)
        }
    }
}
