package dev.tireless.abun.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import dev.tireless.abun.ui.theme.ThemeTokens

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
