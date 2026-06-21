package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.sheets.CreateTaskSheetContent
import dev.tireless.abun.ui.sheets.TaskCreateContext
import dev.tireless.abun.ui.sheets.TaskCreateSource
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor
import kotlin.test.Test

class ComponentScreenshotTest {
    @Test
    fun componentGallery() = captureScreenshot("components/component_gallery") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeTokens.colors.background)
                .padding(ThemeTokens.spacing.screenPaddingDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
        ) {
            Text("Display sample", style = ThemeTokens.type.display)
            Text("Primary body copy for shared UI.", style = ThemeTokens.type.body)
            Text("Muted supporting copy", style = ThemeTokens.type.bodyMuted)
            Text("Inline error message", color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
            HorizontalDivider()
            EditorialCard {
                Text("Section title", style = ThemeTokens.type.sectionTitle)
                Text("Plain section content with custom spacing.", style = ThemeTokens.type.body)
            }
            Text("No rows to show.", style = ThemeTokens.type.body)
            OutlinedTextField(
                value = "hello@abun.dev",
                onValueChange = {},
                label = { Text("Email", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                Button(onClick = {}) { Text("Enabled", style = ThemeTokens.type.body.withMaterialContentColor()) }
                Button(onClick = {}, enabled = false) { Text("Disabled", style = ThemeTokens.type.body.withMaterialContentColor()) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ExtendedFloatingActionButton(
                    onClick = {},
                    icon = { Icon(imageVector = Lucide.Plus, contentDescription = null) },
                    text = { Text("Task", style = ThemeTokens.type.label.withMaterialContentColor()) },
                )
            }
        }
    }

    @Test
    fun sheet() = captureScreenshot("components/sheet") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
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
        MaterialScaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ThemeTokens.colors.background,
                        titleContentColor = ThemeTokens.colors.textPrimary,
                    ),
                    title = { Text("Today", style = ThemeTokens.type.title) },
                )
            },
            bottomBar = {
                Surface(color = ThemeTokens.colors.surface, contentColor = ThemeTokens.colors.textSecondary) {
                    SecondaryTabRow(
                        selectedTabIndex = AppTab.entries.indexOf(AppTab.TODAY),
                        containerColor = ThemeTokens.colors.surface,
                        contentColor = ThemeTokens.colors.textSecondary,
                        divider = { HorizontalDivider(color = ThemeTokens.colors.border) },
                    ) {
                        AppTab.entries.forEach { tab ->
                            Tab(
                                selected = tab == AppTab.TODAY,
                                onClick = {},
                                selectedContentColor = ThemeTokens.colors.textPrimary,
                                unselectedContentColor = ThemeTokens.colors.textSecondary,
                                text = { Text(tab.tabLabelForScreenshot(), style = ThemeTokens.type.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = {},
                    icon = { Icon(imageVector = Lucide.Plus, contentDescription = null) },
                    text = { Text("Task", style = ThemeTokens.type.label.withMaterialContentColor()) },
                )
            },
        ) { _ ->
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
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
            Text("Summary", style = ThemeTokens.type.sectionTitle)
            Text("A framed app shell with tabs and floating action.", style = ThemeTokens.type.body)
        }
    }
}
