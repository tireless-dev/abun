package dev.tireless.abun.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import dev.tireless.abun.app.LanguagePreference
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.ui.i18n.AppLanguage
import dev.tireless.abun.ui.i18n.AppStrings
import dev.tireless.abun.ui.i18n.EnStrings
import dev.tireless.abun.ui.i18n.resolveAppLanguage
import dev.tireless.abun.ui.i18n.stringsForLanguage

private val LocalAppSpacing = staticCompositionLocalOf { DefaultSpacing }
private val LocalAppRadii = staticCompositionLocalOf { DefaultRadii }
private val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnStrings }
private val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

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

    val strings: AppStrings
        @Composable
        @ReadOnlyComposable
        get() = LocalAppStrings.current

    val language: AppLanguage
        @Composable
        @ReadOnlyComposable
        get() = LocalAppLanguage.current

    @Composable
    fun switchColors(): SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = colors.surface,
        checkedTrackColor = colors.textPrimary,
        checkedBorderColor = colors.textPrimary,
        uncheckedThumbColor = colors.textTertiary,
        uncheckedTrackColor = colors.subnavTrack,
        uncheckedBorderColor = colors.border,
    )

    @Composable
    fun segmentedButtonColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colors.surface,
        activeContentColor = colors.textPrimary,
        activeBorderColor = colors.borderStrong,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = colors.textSecondary,
        inactiveBorderColor = colors.border,
    )

    @Composable
    fun outlinedTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.textPrimary,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.textPrimary,
        unfocusedLabelColor = colors.textSecondary,
        cursorColor = colors.textPrimary,
        focusedTextColor = colors.textPrimary,
        unfocusedTextColor = colors.textPrimary,
        focusedContainerColor = colors.surface,
        unfocusedContainerColor = colors.surface,
    )

    @Composable
    fun buttonColors(): ButtonColors = ButtonDefaults.buttonColors(
        containerColor = colors.textPrimary,
        contentColor = colors.surface,
    )

    @Composable
    fun outlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
        contentColor = colors.textPrimary,
        containerColor = Color.Transparent,
    )
}

@Composable
fun AppTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    languagePreference: LanguagePreference = LanguagePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(themePreference, platformSystemDarkTheme())
    val resolvedLang = resolveAppLanguage(languagePreference)
    val appStrings = stringsForLanguage(resolvedLang)
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val shapes = Shapes(
        small = RoundedCornerShape(DefaultRadii.smallDp),
        medium = RoundedCornerShape(DefaultRadii.mediumDp),
        large = RoundedCornerShape(DefaultRadii.largeDp),
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppSpacing provides DefaultSpacing,
        LocalAppRadii provides DefaultRadii,
        LocalAppStrings provides appStrings,
        LocalAppLanguage provides resolvedLang,
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
