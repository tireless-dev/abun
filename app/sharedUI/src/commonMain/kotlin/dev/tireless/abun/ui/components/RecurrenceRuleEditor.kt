package dev.tireless.abun.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tireless.abun.RoutineRecurrenceEditorState
import dev.tireless.abun.RoutineRecurrencePreset
import dev.tireless.abun.buildRecurrenceRule
import dev.tireless.abun.recurrenceEditorStateFor
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun RecurrenceRuleEditor(
    rule: String,
    onRuleChange: (String) -> Unit
) {
    var state by remember(rule) { mutableStateOf(recurrenceEditorStateFor(rule)) }

    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        AppText("Recurrence", style = ThemeTokens.type.label)
        SegmentedControl(
            options = listOf("Daily", "Weekdays", "Custom"),
            selected = state.preset.label(),
            onSelect = { label ->
                val next = state.copy(preset = recurrencePresetFromLabel(label))
                state = next
                onRuleChange(buildRecurrenceRule(next))
            },
        )

        if (state.preset == RoutineRecurrencePreset.CUSTOM) {
            TextField(
                value = state.customRule,
                onValueChange = {
                    state = state.copy(customRule = it)
                    onRuleChange(it)
                },
                label = "Recurrence rule (RRULE)",
            )
        } else {
            TextField(
                value = state.time,
                onValueChange = {
                    val next = state.copy(time = it)
                    state = next
                    onRuleChange(buildRecurrenceRule(next))
                },
                label = "Time (HH:MM)",
            )
        }
    }
}

private fun RoutineRecurrencePreset.label(): String = when (this) {
    RoutineRecurrencePreset.DAILY -> "Daily"
    RoutineRecurrencePreset.WEEKDAYS -> "Weekdays"
    RoutineRecurrencePreset.CUSTOM -> "Custom"
}

private fun recurrencePresetFromLabel(label: String): RoutineRecurrencePreset = when (label) {
    "Daily" -> RoutineRecurrencePreset.DAILY
    "Weekdays" -> RoutineRecurrencePreset.WEEKDAYS
    else -> RoutineRecurrencePreset.CUSTOM
}
