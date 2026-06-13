package dev.tireless.abun.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Immutable
data class AppType(
    val display: TextStyle,
    val title: TextStyle,
    val sectionTitle: TextStyle,
    val body: TextStyle,
    val bodyMuted: TextStyle,
    val label: TextStyle,
)

internal val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

internal fun appType(typography: Typography, colors: ColorScheme): AppType = AppType(
    display = typography.displaySmall.copy(color = colors.onSurface),
    title = typography.headlineSmall.copy(color = colors.onSurface),
    sectionTitle = typography.titleMedium.copy(color = colors.onSurface),
    body = typography.bodyMedium.copy(color = colors.onSurface),
    bodyMuted = typography.bodySmall.copy(color = colors.onSurfaceVariant),
    label = typography.labelMedium.copy(color = colors.onSurfaceVariant),
)

internal fun TextStyle.withMaterialContentColor(): TextStyle = copy(color = Color.Unspecified)
