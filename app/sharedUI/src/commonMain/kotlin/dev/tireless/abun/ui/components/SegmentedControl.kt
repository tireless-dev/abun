package dev.tireless.abun.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun SegmentedControl(
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
