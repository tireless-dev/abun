package dev.tireless.abun.ui.layout

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Scaffold(
    title: String,
    selectedTab: String,
    tabs: List<String>,
    onSelectTab: (String) -> Unit,
    floatingActionLabel: String? = null,
    onFloatingAction: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = ThemeTokens.type.title,
                    )
                },
            )
        },
        bottomBar = {
            TabRow(
                selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(tab) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (floatingActionLabel != null && onFloatingAction != null) {
                ExtendedFloatingActionButton(
                    onClick = onFloatingAction,
                    icon = { Text("+") },
                    text = { Text(floatingActionLabel) },
                )
            }
        },
        content = content,
    )
}
