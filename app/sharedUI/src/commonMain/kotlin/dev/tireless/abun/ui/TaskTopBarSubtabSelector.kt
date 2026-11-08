package dev.tireless.abun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.ui.theme.ThemeTokens

typealias TaskSubtab = TaskSubTab

@Composable
fun TaskTopBarSubtabSelector(
    selectedSubtab: TaskSubtab,
    onSelectSubtab: (TaskSubtab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .testTag("tasks-subtab-selector")
            .clip(RoundedCornerShape(8.dp))
            .background(ThemeTokens.colors.subnavTrack)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskSubtab.entries.forEach { subtab ->
            val isSelected = subtab == selectedSubtab
            val pillBackground = if (isSelected) ThemeTokens.colors.surface else Color.Transparent
            val textColor = if (isSelected) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textSecondary
            val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            val label = when (subtab) {
                TaskSubtab.TASKS -> "任务"
                TaskSubtab.ROUTINES -> "例程"
                TaskSubtab.POMODORO -> "番茄"
            }
            Box(
                modifier = Modifier
                    .testTag("tasks-subtab-option-${subtab.name.lowercase()}")
                    .clip(RoundedCornerShape(6.dp))
                    .background(pillBackground)
                    .clickable { onSelectSubtab(subtab) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
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

internal data class TaskTopBarSubtabOption(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun TaskTopBarSubtabSelector(
    currentLabel: String,
    currentIcon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<TaskTopBarSubtabOption>,
) {
    Row(
        modifier = Modifier.clickable { onExpandedChange(!expanded) },
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = currentIcon,
            contentDescription = null,
            tint = ThemeTokens.colors.textPrimary,
        )
        Text(
            text = currentLabel,
            style = ThemeTokens.type.label,
            color = ThemeTokens.colors.textPrimary,
        )
        Icon(
            imageVector = if (expanded) Lucide.ChevronDown else Lucide.ChevronLeft,
            contentDescription = null,
            tint = ThemeTokens.colors.textSecondary,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = ThemeTokens.type.body,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.padding(horizontal = ThemeTokens.spacing.xsDp),
                    onClick = {
                        onExpandedChange(false)
                        option.onClick()
                    },
                )
            }
        }
    }
}
