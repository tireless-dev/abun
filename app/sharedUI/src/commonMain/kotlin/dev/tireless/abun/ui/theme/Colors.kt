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
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val primary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF35693F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F0CB),
    onPrimaryContainer = Color(0xFF0D200F),
    secondary = Color(0xFF526350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF3C6569),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0EAEF),
    onTertiaryContainer = Color(0xFF001F22),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF73796F),
    outlineVariant = Color(0xFFC2C9BC),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CF9D),
    onPrimary = Color(0xFF003916),
    primaryContainer = Color(0xFF1D5129),
    onPrimaryContainer = Color(0xFFACECB8),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD5E8D0),
    tertiary = Color(0xFFA4CED3),
    onTertiary = Color(0xFF04363A),
    tertiaryContainer = Color(0xFF234D51),
    onTertiaryContainer = Color(0xFFC0EAEF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131511),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF1A1E18),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BC),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
)

internal fun appColors(colorScheme: ColorScheme): AppColors = AppColors(
    background = colorScheme.background,
    surface = colorScheme.surface,
    surfaceElevated = colorScheme.surfaceContainer,
    textPrimary = colorScheme.onSurface,
    textSecondary = colorScheme.onSurfaceVariant,
    textTertiary = colorScheme.outline,
    border = colorScheme.outlineVariant,
    primary = colorScheme.primary,
    success = Color(0xFF2F7D4B),
    warning = Color(0xFF9A6A21),
    error = colorScheme.error,
)
