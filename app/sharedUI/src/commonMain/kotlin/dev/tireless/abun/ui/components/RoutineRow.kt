package dev.tireless.abun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.ui.sheets.describeRecurrenceRule
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun RoutineRow(
    routine: RoutineListItemView,
    onOpen: (RoutineListItemView) -> Unit,
    onRun: (RoutineListItemView) -> Unit,
    onDelete: ((RoutineListItemView) -> Unit)? = null,
) {
    val recurrenceLabel = formatRecurrenceShort(routine.recurrenceRule)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("routine-row-${routine.id}")
            .clickable { onOpen(routine) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (routine.isActive) ThemeTokens.colors.tasksAccent else ThemeTokens.colors.textTertiary),
                    )
                    Text(
                        text = routine.templateTitle,
                        style = ThemeTokens.type.cardTitle,
                        color = ThemeTokens.colors.textPrimary,
                    )
                }
                routine.templateDetail?.let {
                    Text(text = it, style = ThemeTokens.type.bodyMuted)
                }
            }

            // 7-day adherence dot matrix
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 7) {
                    val isDayActive = routine.isActive && i < 6 // 6 days completed in streak
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDayActive) ThemeTokens.colors.tasksAccent else ThemeTokens.colors.subnavTrack,
                            ),
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Surface(
                color = ThemeTokens.colors.tasksSoft,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = recurrenceLabel,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.tasksAccent,
                    ),
                )
            }

            routine.defaultStartNotBefore?.let {
                Surface(
                    color = ThemeTokens.colors.surfaceMuted,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "START $it",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }
            }

            routine.defaultEstimatedDuration?.let {
                Surface(
                    color = ThemeTokens.colors.surfaceMuted,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "DUR $it",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }
            }

            Surface(
                color = if (routine.isActive) ThemeTokens.colors.surfaceMuted else ThemeTokens.colors.surfaceMuted,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = if (routine.isActive) "ACTIVE" else "PAUSED",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (routine.isActive) ThemeTokens.colors.tasksAccent else ThemeTokens.colors.textTertiary,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onRun(routine) },
                enabled = routine.isActive,
            ) {
                Text("Run today", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            OutlinedButton(
                onClick = { onOpen(routine) },
            ) {
                Text("Edit", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            if (onDelete != null) {
                OutlinedButton(
                    onClick = { onDelete(routine) },
                ) {
                    Text("Delete", style = ThemeTokens.type.body.copy(color = ThemeTokens.colors.error))
                }
            }
        }
    }
}

private fun formatRecurrenceShort(rule: String): String {
    return when {
        rule.contains("FREQ=DAILY", ignoreCase = true) -> "DAILY"
        rule.contains("FREQ=WEEKLY", ignoreCase = true) && rule.contains("BYDAY=MO,TU,WE,TH,FR", ignoreCase = true) -> "WEEKDAYS"
        rule.contains("FREQ=WEEKLY", ignoreCase = true) -> "WEEKLY"
        rule.contains("FREQ=MONTHLY", ignoreCase = true) -> "MONTHLY"
        else -> describeRecurrenceRule(rule).uppercase()
    }
}
