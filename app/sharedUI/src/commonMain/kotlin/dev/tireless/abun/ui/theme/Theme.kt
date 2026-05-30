package dev.tireless.abun.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

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

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppColors provides tokens,
        LocalAppSpacing provides DefaultSpacing,
        LocalAppRadii provides DefaultRadii,
        LocalAppType provides appType(tokens),
    ) {
        content()
    }
}
