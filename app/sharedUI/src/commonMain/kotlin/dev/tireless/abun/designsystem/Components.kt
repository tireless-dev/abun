package dev.tireless.abun.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = ThemeTokens.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(spacing.screenPaddingDp),
        verticalArrangement = Arrangement.spacedBy(spacing.mdDp),
        content = content,
    )
}

@Composable
fun AppScaffold(
    title: String,
    selectedTab: String,
    tabs: List<String>,
    onSelectTab: (String) -> Unit,
    floatingActionLabel: String? = null,
    onFloatingAction: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title = title) },
        bottomBar = {
            AppBottomTabs(
                tabs = tabs,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
            )
        },
        floatingActionButton = {
            if (floatingActionLabel != null && onFloatingAction != null) {
                AppFab(label = floatingActionLabel, onClick = onFloatingAction)
            }
        },
        content = content,
    )
}

@Composable
fun AppTopBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surface)
            .padding(horizontal = ThemeTokens.spacing.lgDp, vertical = ThemeTokens.spacing.mdDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ThemeTokens.type.title, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppBottomTabs(
    tabs: List<String>,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surface)
            .padding(horizontal = ThemeTokens.spacing.smDp, vertical = ThemeTokens.spacing.smDp),
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
    ) {
        tabs.forEach { tab ->
            val selected = selectedTab == tab
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectTab(tab) },
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (selected) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.surface,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ThemeTokens.spacing.smDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tab, style = ThemeTokens.type.body)
                }
            }
        }
    }
}

@Composable
fun AppFab(label: String, onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, containerColor = ThemeTokens.colors.primary, contentColor = Color.White) {
        Text("+", style = ThemeTokens.type.sectionTitle, modifier = Modifier.padding(horizontal = ThemeTokens.spacing.smDp))
        Text(label, style = ThemeTokens.type.label)
    }
}

@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SectionCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.mdDp),
            contentPadding = PaddingValues(ThemeTokens.spacing.lgDp),
        ) {
            content()
        }
    }
}

@Composable
fun AppButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label)
    }
}

@Composable
fun AppTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) })
}

@Composable
fun AppSegmentedControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
    ) {
        options.forEach { option ->
            val active = option == selected
            OutlinedCard(
                modifier = Modifier.clickable { onSelect(option) },
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (active) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.surface,
                ),
            ) {
                Text(
                    option,
                    modifier = Modifier.padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.smDp),
                    style = ThemeTokens.type.body,
                )
            }
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(ThemeTokens.spacing.mdDp),
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
        colors = CardDefaults.outlinedCardColors(containerColor = ThemeTokens.colors.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            content = content,
        )
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, style = ThemeTokens.type.sectionTitle)
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(message, style = ThemeTokens.type.body)
    }
}

@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    Text(message, modifier = modifier, color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
}

@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        content = content,
    )
}

@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = ThemeTokens.colors.border)
}
