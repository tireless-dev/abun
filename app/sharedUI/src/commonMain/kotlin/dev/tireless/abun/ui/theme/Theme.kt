package dev.tireless.abun.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.tireless.abun.app.ThemePreference

private val LocalAppSpacing = staticCompositionLocalOf { DefaultSpacing }
private val LocalAppRadii = staticCompositionLocalOf { DefaultRadii }

@Stable
object ThemeTokens {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = appColors(MaterialTheme.colorScheme)

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
        get() = appType(MaterialTheme.typography, MaterialTheme.colorScheme)
}

@Composable
fun AppTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(themePreference, isSystemInDarkTheme())
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val shapes = Shapes(
        small = RoundedCornerShape(DefaultRadii.smallDp),
        medium = RoundedCornerShape(DefaultRadii.mediumDp),
        large = RoundedCornerShape(DefaultRadii.largeDp),
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppSpacing provides DefaultSpacing,
        LocalAppRadii provides DefaultRadii,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = shapes,
            content = content,
        )
    }
}

fun resolveDarkTheme(themePreference: ThemePreference, isSystemDarkTheme: Boolean): Boolean = when (themePreference) {
    ThemePreference.SYSTEM -> isSystemDarkTheme
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}
