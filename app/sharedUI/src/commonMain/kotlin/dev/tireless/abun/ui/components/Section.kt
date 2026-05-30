package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun Section(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(ThemeTokens.spacing.mdDp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    AppText(title, modifier = modifier, style = ThemeTokens.type.sectionTitle)
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Section(modifier = modifier) {
        AppText(message, style = ThemeTokens.type.body)
    }
}
