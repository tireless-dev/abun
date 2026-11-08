package dev.tireless.abun.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    contentPadding: PaddingValues = PaddingValues(ThemeTokens.spacing.screenPaddingDp),
    verticalSpacing: Dp = ThemeTokens.spacing.lgDp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ThemeTokens.colors.canvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    contentPadding: PaddingValues = PaddingValues(ThemeTokens.spacing.lgDp),
    contentSpacing: Dp = ThemeTokens.spacing.mdDp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = if (testTag == null) modifier else modifier.testTag(testTag)
    Surface(
        modifier = cardModifier.fillMaxWidth(),
        color = ThemeTokens.colors.surface,
        contentColor = ThemeTokens.colors.textPrimary,
        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
        border = BorderStroke(1.dp, ThemeTokens.colors.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
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
fun EditorialPill(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ThemeTokens.colors.surfaceMuted,
    textColor: Color = ThemeTokens.colors.textSecondary,
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
            ),
        )
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

@Composable
fun SwissBentoCard(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    val cardModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }
    Surface(
        modifier = cardModifier,
        color = ThemeTokens.colors.surface,
        contentColor = ThemeTokens.colors.textPrimary,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ThemeTokens.colors.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun SwissCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    accentDotColor: Color? = null,
    trailingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (accentDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentDotColor),
                )
            }
            Text(
                text = title.uppercase(),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = ThemeTokens.colors.onSurfaceVariant,
                ),
            )
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (trailingText != null) {
            Text(
                text = trailingText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = ThemeTokens.colors.textSecondary,
                ),
            )
        }
    }
}

@Composable
fun SwissHairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        thickness = 1.dp,
        color = ThemeTokens.colors.hairline,
        modifier = modifier,
    )
}

data class SwissMetricData(
    val label: String,
    val value: String,
    val valueColor: Color? = null,
)

@Composable
fun SwissMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ThemeTokens.colors.textPrimary,
) {
    Column(
        modifier = modifier.padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
                color = ThemeTokens.colors.textSecondary,
            ),
            maxLines = 1,
        )
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            ),
            maxLines = 1,
        )
    }
}

@Composable
fun SwissMetricStrip(
    metrics: List<SwissMetricData>,
    modifier: Modifier = Modifier,
) {
    val cardBorder = ThemeTokens.colors.cardBorder
    val hairline = ThemeTokens.colors.hairline
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = cardBorder)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metrics.forEachIndexed { index, metric ->
                if (index > 0) {
                    VerticalDivider(
                        thickness = 1.dp,
                        color = hairline,
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
                SwissMetricCell(
                    label = metric.label,
                    value = metric.value,
                    valueColor = metric.valueColor ?: ThemeTokens.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = cardBorder)
    }
}

@Composable
fun SwissMetricStrip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val cardBorder = ThemeTokens.colors.cardBorder
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = cardBorder)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        HorizontalDivider(thickness = 1.dp, color = cardBorder)
    }
}

@Composable
fun SegmentedTickBar(
    totalSegments: Int,
    filledSegments: Int,
    filledColor: Color,
    modifier: Modifier = Modifier,
    activeIndex: Int? = null,
    activeColor: Color = filledColor,
    emptyColor: Color = ThemeTokens.colors.subnavTrack,
    segmentHeight: Dp = 6.dp,
    segmentSpacing: Dp = 2.dp,
    segmentCornerRadius: Dp = 1.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(segmentSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val total = totalSegments.coerceAtLeast(1)
        val filled = filledSegments.coerceIn(0, total)
        for (i in 0 until total) {
            val color = when {
                i == activeIndex -> activeColor
                i < filled -> filledColor
                else -> emptyColor
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(segmentHeight)
                    .clip(RoundedCornerShape(segmentCornerRadius))
                    .background(color),
            )
        }
    }
}

@Composable
fun <T> TopBarSegmentedSubnav(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    trackColor: Color = ThemeTokens.colors.subnavTrack,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(trackColor)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            val pillBackground = if (isSelected) ThemeTokens.colors.surface else Color.Transparent
            val textColor = if (isSelected) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textSecondary
            val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(pillBackground)
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelProvider(item),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = fontWeight,
                        color = textColor,
                    ),
                )
            }
        }
    }
}
