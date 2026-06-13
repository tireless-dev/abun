package dev.tireless.abun.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.theme.AppColors
import dev.tireless.abun.ui.theme.ThemeTokens

@Immutable
data class EditorialStatusColors(
    val container: Color,
    val content: Color,
)

@Composable
fun EditorialScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ThemeTokens.colors.background)
            .padding(ThemeTokens.spacing.screenPaddingDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.lgDp),
        content = content,
    )
}

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = if (testTag == null) modifier else modifier.testTag(testTag)
    Surface(
        modifier = cardModifier.fillMaxWidth(),
        color = ThemeTokens.colors.surface,
        contentColor = ThemeTokens.colors.textPrimary,
        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
        border = BorderStroke(1.dp, ThemeTokens.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
            content = content,
        )
    }
}

@Composable
fun EditorialSection(
    eyebrow: String,
    title: String,
    supportingText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
            Text(
                text = eyebrow,
                style = ThemeTokens.type.label.copy(fontWeight = FontWeight.Medium),
                color = ThemeTokens.colors.textTertiary,
            )
            Text(text = title, style = ThemeTokens.type.sectionTitle)
            supportingText?.let {
                Text(text = it, style = ThemeTokens.type.bodyMuted)
            }
        }
        content()
    }
}

@Composable
fun EditorialStatusTag(
    status: TaskStatus,
    modifier: Modifier = Modifier,
) {
    val colors = editorialStatusColors(status, ThemeTokens.colors)
    Text(
        text = status.name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase),
        modifier = modifier
            .clip(RoundedCornerShape(ThemeTokens.radii.smallDp))
            .background(colors.container)
            .border(1.dp, ThemeTokens.colors.border, RoundedCornerShape(ThemeTokens.radii.smallDp))
            .padding(horizontal = ThemeTokens.spacing.smDp, vertical = ThemeTokens.spacing.xsDp),
        style = ThemeTokens.type.label.copy(fontWeight = FontWeight.Medium),
        color = colors.content,
    )
}

fun editorialStatusColors(
    status: TaskStatus,
    colors: AppColors,
): EditorialStatusColors = when (status) {
    TaskStatus.IN_PROGRESS -> EditorialStatusColors(
        container = Color(0xFFE8EEF8),
        content = Color(0xFF435D93),
    )
    TaskStatus.PENDING -> EditorialStatusColors(
        container = Color(0xFFF4EFE2),
        content = Color(0xFF7D6432),
    )
    TaskStatus.COMPLETED -> EditorialStatusColors(
        container = Color(0xFFEAF2EC),
        content = Color(0xFF3D6B55),
    )
    TaskStatus.CANCELLED -> EditorialStatusColors(
        container = Color(0xFFF5E8E8),
        content = colors.error,
    )
    TaskStatus.UNKNOWN -> EditorialStatusColors(
        container = colors.surfaceMuted,
        content = colors.textSecondary,
    )
}
