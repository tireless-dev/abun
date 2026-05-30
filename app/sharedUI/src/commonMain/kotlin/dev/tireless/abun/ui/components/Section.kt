package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun Section(
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
    Section(modifier = modifier) {
        Text(message, style = ThemeTokens.type.body)
    }
}
