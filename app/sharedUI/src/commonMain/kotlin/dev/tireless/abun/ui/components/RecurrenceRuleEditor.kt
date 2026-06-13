package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.tireless.abun.RoutineRecurrenceEditorState
import dev.tireless.abun.RoutineRecurrencePreset
import dev.tireless.abun.buildRecurrenceRule
import dev.tireless.abun.recurrenceEditorStateFor
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecurrenceRuleEditor(
    rule: String,
    onRuleChange: (String) -> Unit
) {
    var state by remember(rule) { mutableStateOf(recurrenceEditorStateFor(rule)) }

    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        Text("Recurrence", style = ThemeTokens.type.label)
        SingleChoiceSegmentedButtonRow {
            listOf("Daily", "Weekdays", "Custom").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = state.preset.label() == label,
                    onClick = {
                        val next = state.copy(preset = recurrencePresetFromLabel(label))
                        state = next
                        onRuleChange(buildRecurrenceRule(next))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                ) {
                    Text(label, style = ThemeTokens.type.body)
                }
            }
        }

        if (state.preset == RoutineRecurrencePreset.CUSTOM) {
            OutlinedTextField(
                value = state.customRule,
                onValueChange = {
                    state = state.copy(customRule = it)
                    onRuleChange(it)
                },
                label = { Text("Recurrence rule (RRULE)", style = ThemeTokens.type.label) },
                modifier = Modifier,
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
        } else {
            OutlinedTextField(
                value = state.time,
                onValueChange = {
                    val next = state.copy(time = it)
                    state = next
                    onRuleChange(buildRecurrenceRule(next))
                },
                label = { Text("Time (HH:MM)", style = ThemeTokens.type.label) },
                modifier = Modifier,
                textStyle = ThemeTokens.type.body,
                singleLine = true,
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
