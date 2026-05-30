package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

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
