package dev.tireless.abun.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

data class ActionButtonStyle(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
)

fun primaryActionButtonStyle(colorScheme: ColorScheme): ActionButtonStyle = ActionButtonStyle(
    containerColor = colorScheme.primary,
    contentColor = colorScheme.onPrimary,
    disabledContainerColor = colorScheme.surfaceContainer,
    disabledContentColor = colorScheme.outline,
)

val ThemeTokens.primaryActionButtonStyle: ActionButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = primaryActionButtonStyle(MaterialTheme.colorScheme)
