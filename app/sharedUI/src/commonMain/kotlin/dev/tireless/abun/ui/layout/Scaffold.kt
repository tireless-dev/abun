package dev.tireless.abun.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.tireless.abun.ui.components.Fab
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun Scaffold(
    title: String,
    selectedTab: String,
    tabs: List<String>,
    onSelectTab: (String) -> Unit,
    floatingActionLabel: String? = null,
    onFloatingAction: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    MaterialScaffold(
        topBar = { TopBar(title = title) },
        bottomBar = {
            BottomTabs(
                tabs = tabs,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
            )
        },
        floatingActionButton = {
            if (floatingActionLabel != null && onFloatingAction != null) {
                Fab(label = floatingActionLabel, onClick = onFloatingAction)
            }
        },
        content = content,
    )
}

@Composable
private fun TopBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surface)
            .statusBarsPadding()
            .padding(horizontal = ThemeTokens.spacing.lgDp, vertical = ThemeTokens.spacing.mdDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ThemeTokens.type.title, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomTabs(
    tabs: List<String>,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ThemeTokens.colors.surface)
            .navigationBarsPadding()
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
