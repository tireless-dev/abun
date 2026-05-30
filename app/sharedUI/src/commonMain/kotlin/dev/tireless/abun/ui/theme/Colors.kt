package dev.tireless.abun.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val primary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

internal val LightColors = AppColors(
    background = Color(0xFFF7F8F5),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1F4EE),
    textPrimary = Color(0xFF1C211A),
    textSecondary = Color(0xFF41493F),
    textTertiary = Color(0xFF6A7467),
    border = Color(0xFFD9DFD4),
    primary = Color(0xFF35693F),
    success = Color(0xFF2F7D4B),
    warning = Color(0xFF9A6A21),
    error = Color(0xFFB3261E),
)

internal val DarkColors = AppColors(
    background = Color(0xFF131511),
    surface = Color(0xFF1A1E18),
    surfaceElevated = Color(0xFF232821),
    textPrimary = Color(0xFFE5EADD),
    textSecondary = Color(0xFFBEC7B8),
    textTertiary = Color(0xFF97A392),
    border = Color(0xFF364133),
    primary = Color(0xFF90CF9D),
    success = Color(0xFF75D495),
    warning = Color(0xFFF0C06D),
    error = Color(0xFFFFB4AB),
)
