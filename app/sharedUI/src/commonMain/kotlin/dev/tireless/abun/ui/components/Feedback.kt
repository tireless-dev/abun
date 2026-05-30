package dev.tireless.abun.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    AppText(message, modifier = modifier, color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
}
