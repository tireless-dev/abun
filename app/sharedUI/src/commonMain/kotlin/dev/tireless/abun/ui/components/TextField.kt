package dev.tireless.abun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun TextField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppText(label, style = ThemeTokens.type.label, modifier = Modifier.padding(bottom = ThemeTokens.spacing.xsDp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (enabled) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.background,
                    RoundedCornerShape(ThemeTokens.radii.mediumDp)
                )
                .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.mdDp),
            textStyle = if (enabled) ThemeTokens.type.body else ThemeTokens.type.bodyMuted,
            singleLine = true,
            enabled = enabled,
        )
    }
}
