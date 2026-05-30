package dev.tireless.abun.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalAppColors = staticCompositionLocalOf { LightColors }
private val LocalAppSpacing = staticCompositionLocalOf { DefaultSpacing }
private val LocalAppRadii = staticCompositionLocalOf { DefaultRadii }
private val LocalAppType = staticCompositionLocalOf { appType(LightColors) }

@Stable
object ThemeTokens {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    val radii: AppRadii
        @Composable
        @ReadOnlyComposable
        get() = LocalAppRadii.current

    val type: AppType
        @Composable
        @ReadOnlyComposable
        get() = LocalAppType.current
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkColors else LightColors

    val m3Colors = tokens.toColorScheme(darkTheme)
    val typography = materialTypography(tokens)
    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(DefaultRadii.smallDp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(DefaultRadii.smallDp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(DefaultRadii.mediumDp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(DefaultRadii.largeDp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(DefaultRadii.largeDp),
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppColors provides tokens,
        LocalAppSpacing provides DefaultSpacing,
        LocalAppRadii provides DefaultRadii,
        LocalAppType provides appType(tokens),
    ) {
        MaterialTheme(colorScheme = m3Colors, typography = typography, shapes = shapes, content = content)
    }
}

private fun AppColors.toColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF0F1D12),
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textSecondary,
            error = error,
            onError = Color(0xFF3A0502),
            outline = border,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceElevated,
            onSurfaceVariant = textSecondary,
            error = error,
            onError = Color.White,
            outline = border,
        )
    }
