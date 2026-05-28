package dev.tireless.abun.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

@Immutable
data class AppSpacing(
    val xs: Int,
    val sm: Int,
    val md: Int,
    val lg: Int,
    val xl: Int,
    val screenPadding: Int,
) {
    val xsDp get() = xs.dp
    val smDp get() = sm.dp
    val mdDp get() = md.dp
    val lgDp get() = lg.dp
    val xlDp get() = xl.dp
    val screenPaddingDp get() = screenPadding.dp
}

@Immutable
data class AppRadii(
    val small: Int,
    val medium: Int,
    val large: Int,
) {
    val smallDp get() = small.dp
    val mediumDp get() = medium.dp
    val largeDp get() = large.dp
}

@Immutable
data class AppType(
    val display: TextStyle,
    val title: TextStyle,
    val sectionTitle: TextStyle,
    val body: TextStyle,
    val bodyMuted: TextStyle,
    val label: TextStyle,
)

private val LightColors = AppColors(
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

private val DarkColors = AppColors(
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

private val LocalAppColors = staticCompositionLocalOf { LightColors }
private val LocalAppSpacing = staticCompositionLocalOf {
    AppSpacing(xs = 4, sm = 8, md = 12, lg = 16, xl = 24, screenPadding = 16)
}
private val LocalAppRadii = staticCompositionLocalOf { AppRadii(small = 8, medium = 12, large = 16) }
private val LocalAppType = staticCompositionLocalOf {
    AppType(
        display = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        sectionTitle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        bodyMuted = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
        label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )
}

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
    val spacing = AppSpacing(xs = 4, sm = 8, md = 12, lg = 16, xl = 24, screenPadding = 16)
    val radii = AppRadii(small = 8, medium = 12, large = 16)

    val m3Colors = tokens.toColorScheme(darkTheme)
    val typography = tokens.toTypography()
    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(radii.smallDp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(radii.smallDp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(radii.mediumDp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(radii.largeDp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(radii.largeDp),
    )

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppColors provides tokens,
        LocalAppSpacing provides spacing,
        LocalAppRadii provides radii,
        LocalAppType provides AppType(
            display = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = tokens.textPrimary),
            title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary),
            sectionTitle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = tokens.textPrimary),
            body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, color = tokens.textPrimary),
            bodyMuted = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = tokens.textSecondary),
            label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = tokens.textSecondary),
        ),
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

private fun AppColors.toTypography(): Typography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = textPrimary),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = textPrimary),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = textSecondary),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, color = textPrimary),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, color = textPrimary),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = textSecondary),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = textPrimary),
)
