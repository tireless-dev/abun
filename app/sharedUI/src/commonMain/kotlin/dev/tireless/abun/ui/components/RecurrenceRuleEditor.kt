package dev.tireless.abun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tireless.abun.app.RecurrenceFrequency
import dev.tireless.abun.app.StructuredRecurrence
import dev.tireless.abun.ui.theme.ThemeTokens

private val WEEKDAYS = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
private val WEEKDAY_LABELS = mapOf(
    "MO" to "Mon",
    "TU" to "Tue",
    "WE" to "Wed",
    "TH" to "Thu",
    "FR" to "Fri",
    "SA" to "Sat",
    "SU" to "Sun"
)

@Composable
fun RecurrenceRuleEditor(
    rule: String,
    onRuleChange: (String) -> Unit
) {
    val structured = remember(rule) { StructuredRecurrence.fromRRule(rule) }

    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        AppText("Frequency", style = ThemeTokens.type.label)
        SegmentedControl(
            options = listOf("Daily", "Weekly"),
            selected = if (structured.frequency == RecurrenceFrequency.DAILY) "Daily" else "Weekly",
            onSelect = {
                val newFreq = if (it == "Daily") RecurrenceFrequency.DAILY else RecurrenceFrequency.WEEKLY
                onRuleChange(structured.copy(frequency = newFreq).toRRule())
            }
        )

        if (structured.frequency == RecurrenceFrequency.WEEKLY) {
            AppText("On days", style = ThemeTokens.type.label)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                WEEKDAYS.forEach { day ->
                    val active = structured.byDay.contains(day)
                    Box(
                        modifier = Modifier
                            .background(
                                if (active) ThemeTokens.colors.surfaceElevated else ThemeTokens.colors.background,
                                RoundedCornerShape(ThemeTokens.radii.largeDp),
                            )
                            .clickable {
                                val newByDay = if (active) {
                                    structured.byDay - day
                                } else {
                                    structured.byDay + day
                                }
                                onRuleChange(structured.copy(byDay = newByDay).toRRule())
                            }
                            .padding(horizontal = ThemeTokens.spacing.mdDp, vertical = ThemeTokens.spacing.smDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(WEEKDAY_LABELS[day] ?: day, style = ThemeTokens.type.body)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = structured.byHour?.toString() ?: "",
                    onValueChange = {
                        val hour = it.toIntOrNull()?.coerceIn(0, 23)
                        onRuleChange(structured.copy(byHour = hour).toRRule())
                    },
                    label = "Hour (0-23)"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                TextField(
                    value = structured.byMinute?.toString() ?: "",
                    onValueChange = {
                        val minute = it.toIntOrNull()?.coerceIn(0, 59)
                        onRuleChange(structured.copy(byMinute = minute).toRRule())
                    },
                    label = "Minute (0-59)"
                )
            }
        }
    }
}
