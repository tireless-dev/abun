package dev.tireless.abun.ui.layout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tireless.abun.ui.components.AppText
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
    Box(modifier = Modifier.fillMaxSize().background(ThemeTokens.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(title = title)
            Box(modifier = Modifier.weight(1f)) {
                content(PaddingValues())
            }
            BottomTabs(
                tabs = tabs,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
            )
        }
        if (floatingActionLabel != null && onFloatingAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = ThemeTokens.spacing.lgDp, bottom = 72.dp),
            ) {
                Fab(label = floatingActionLabel, onClick = onFloatingAction)
            }
        }
    }
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
        AppText(title, style = ThemeTokens.type.title.copy(fontWeight = FontWeight.Bold))
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.surface,
                        RoundedCornerShape(ThemeTokens.radii.mediumDp),
                    )
                    .border(BorderStroke(1.dp, ThemeTokens.colors.border), RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .clickable { onSelectTab(tab) }
                    .padding(vertical = ThemeTokens.spacing.smDp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(tab, style = ThemeTokens.type.body)
            }
        }
    }
}
