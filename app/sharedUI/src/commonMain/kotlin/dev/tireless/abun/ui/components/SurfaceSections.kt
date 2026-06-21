package dev.tireless.abun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
internal fun Panel(
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EditorialCard(testTag = testTag, content = content)
}

@Composable
internal fun SectionHeader(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        Text(eyebrow, style = ThemeTokens.type.label, color = ThemeTokens.colors.textTertiary)
        Text(title, style = ThemeTokens.type.sectionTitle)
    }
}

@Composable
internal fun MetricRow(items: List<Pair<String, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, value) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(ThemeTokens.colors.surfaceMuted, RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(ThemeTokens.radii.mediumDp))
                    .padding(ThemeTokens.spacing.mdDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                Text(value, style = ThemeTokens.type.cardTitle)
                Text(label, style = ThemeTokens.type.bodyMuted)
            }
        }
    }
}
