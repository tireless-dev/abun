package dev.tireless.abun.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3F4EAE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E7FF),
    onPrimaryContainer = Color(0xFF202A61),
    secondary = Color(0xFF4F8F83),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEDE9),
    onSecondaryContainer = Color(0xFF17312C),
    tertiary = Color(0xFFC79A52),
    onTertiary = Color(0xFF2E210C),
    tertiaryContainer = Color(0xFFF3E4CB),
    onTertiaryContainer = Color(0xFF5E4520),
    error = Color(0xFFB45151),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF5E8E8),
    onErrorContainer = Color(0xFF5D2424),
    background = Color(0xFFFAFAF7),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF4F3EE),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD8D8D2),
    outlineVariant = Color(0xFFE7E6DF),
    surfaceContainer = Color(0xFFF7F6F1),
    surfaceContainerHigh = Color(0xFFF2F1EB),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF212D66),
    primaryContainer = Color(0xFF313F8D),
    onPrimaryContainer = Color(0xFFE1E6FF),
    secondary = Color(0xFF8ABAAF),
    onSecondary = Color(0xFF16342F),
    secondaryContainer = Color(0xFF234742),
    onSecondaryContainer = Color(0xFFD7ECE6),
    tertiary = Color(0xFFE0BC7E),
    onTertiary = Color(0xFF46310A),
    tertiaryContainer = Color(0xFF62471A),
    onTertiaryContainer = Color(0xFFF7E2B9),
    error = Color(0xFFE6A1A1),
    onError = Color(0xFF522222),
    errorContainer = Color(0xFF6A3030),
    onErrorContainer = Color(0xFFFFE5E5),
    background = Color(0xFF111315),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF181A1D),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1F2226),
    onSurfaceVariant = Color(0xFFA0A4AA),
    outline = Color(0xFF34373B),
    outlineVariant = Color(0xFF2A2D31),
    surfaceContainer = Color(0xFF202327),
    surfaceContainerHigh = Color(0xFF26292E),
)

internal fun appColors(colorScheme: ColorScheme): AppColors = AppColors(
    background = colorScheme.background,
    surface = colorScheme.surface,
    surfaceElevated = colorScheme.surfaceContainer,
    surfaceMuted = colorScheme.surfaceContainerHigh,
    textPrimary = colorScheme.onSurface,
    textSecondary = colorScheme.onSurfaceVariant,
    textTertiary = colorScheme.outline,
    border = colorScheme.outlineVariant,
    borderStrong = colorScheme.outline,
    primary = colorScheme.primary,
    success = Color(0xFF3D6B55),
    warning = Color(0xFF7D6432),
    error = colorScheme.error,
)
