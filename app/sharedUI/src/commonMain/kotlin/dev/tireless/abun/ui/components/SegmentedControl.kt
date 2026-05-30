package dev.tireless.abun.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            Box(
                modifier = Modifier
                    .background(
                        if (active) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.background,
                        RoundedCornerShape(ThemeTokens.radii.largeDp),
                    )
                    .border(BorderStroke(1.dp, ThemeTokens.colors.border), RoundedCornerShape(ThemeTokens.radii.largeDp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.smDp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(option, style = ThemeTokens.type.body)
            }
        }
    }
}
