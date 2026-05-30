package dev.tireless.abun.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun TextField(value: String, onValueChange: (String) -> Unit, label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppText(label, style = ThemeTokens.type.label, modifier = Modifier.padding(bottom = ThemeTokens.spacing.xsDp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, ThemeTokens.colors.border), RoundedCornerShape(ThemeTokens.radii.mediumDp))
                .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.mdDp),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
    }
}
