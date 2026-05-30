package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun Button(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(label)
    }
}

@Composable
fun Fab(label: String, onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick, containerColor = ThemeTokens.colors.primary, contentColor = Color.White) {
        Text("+", style = ThemeTokens.type.sectionTitle, modifier = Modifier.padding(horizontal = ThemeTokens.spacing.smDp))
        Text(label, style = ThemeTokens.type.label)
    }
}
