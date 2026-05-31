package dev.tireless.abun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun Button(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val textColor = if (enabled) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textTertiary
    AppText(
        text = label,
        modifier = Modifier
            .background(ThemeTokens.colors.surfaceElevated, RoundedCornerShape(ThemeTokens.radii.largeDp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ThemeTokens.spacing.lgDp, vertical = ThemeTokens.spacing.smDp),
        style = ThemeTokens.type.body.copy(fontWeight = FontWeight.SemiBold),
        color = textColor,
    )
}

@Composable
fun Fab(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(ThemeTokens.colors.primary, RoundedCornerShape(ThemeTokens.radii.largeDp))
            .clickable(onClick = onClick)
            .padding(horizontal = ThemeTokens.spacing.lgDp, vertical = ThemeTokens.spacing.mdDp),
    ) {
        AppText("+", style = ThemeTokens.type.sectionTitle, color = Color.White, modifier = Modifier.padding(horizontal = ThemeTokens.spacing.smDp))
        AppText(label, style = ThemeTokens.type.label, color = Color.White)
    }
}
