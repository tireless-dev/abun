package dev.tireless.abun.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun InlineError(message: String, modifier: Modifier = Modifier) {
    Text(message, modifier = modifier, color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
}
