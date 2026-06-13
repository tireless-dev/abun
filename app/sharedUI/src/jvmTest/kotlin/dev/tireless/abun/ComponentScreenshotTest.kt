package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.components.ActionRow
import dev.tireless.abun.ui.components.EmptyState
import dev.tireless.abun.ui.components.InlineError
import dev.tireless.abun.ui.components.Section
import dev.tireless.abun.ui.components.SectionDivider
import dev.tireless.abun.ui.components.SectionTitle
import dev.tireless.abun.ui.components.Sheet
import dev.tireless.abun.ui.layout.Scaffold
import dev.tireless.abun.ui.layout.ScreenContainer
import dev.tireless.abun.ui.theme.ThemeTokens
import kotlin.test.Test

class ComponentScreenshotTest {
    @Test
    fun componentGallery() = captureScreenshot("components/component_gallery") {
        ScreenContainer(applyVerticalSafeInsets = false) {
            Text("Display sample", style = ThemeTokens.type.display)
            Text("Primary body copy for shared UI.", style = ThemeTokens.type.body)
            Text("Muted supporting copy", style = ThemeTokens.type.bodyMuted)
            InlineError("Inline error message")
            SectionDivider()
            Section {
                SectionTitle("Section title")
                Text("Plain section content with custom spacing.", style = ThemeTokens.type.body)
            }
            EmptyState("No rows to show.")
            OutlinedTextField(
                value = "hello@abun.dev",
                onValueChange = {},
                label = { Text("Email", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
            )
            ActionRow {
                Button(onClick = {}) { Text("Enabled", style = ThemeTokens.type.body) }
                Button(onClick = {}, enabled = false) { Text("Disabled", style = ThemeTokens.type.body) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ExtendedFloatingActionButton(
                    onClick = {},
                    icon = { Text("+") },
                    text = { Text("Task", style = ThemeTokens.type.label) },
                )
            }
        }
    }

    @Test
    fun sheet() = captureScreenshot("components/sheet") {
        Section {
            CreateTaskSheetContent(
                context = TaskCreateContext(
                    source = TaskCreateSource.TASKS,
                    selectedDate = "2026-06-11",
                ),
                onDismiss = {},
                onCreate = {},
            )
        } 
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
            Text("A framed app shell with tabs and floating action.", style = ThemeTokens.type.body)
        }
    }
}
