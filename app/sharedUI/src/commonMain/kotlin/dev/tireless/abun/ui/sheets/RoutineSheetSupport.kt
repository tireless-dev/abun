package dev.tireless.abun.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

internal data class RoutineSaveDraft(
    val id: String,
    val title: String,
    val detail: String?,
    val recurrenceRule: String,
    val defaultStartNotBefore: String?,
    val defaultEstimatedDuration: String?,
)

internal enum class RoutineRecurrencePreset {
    DAILY,
    WEEKDAYS,
    CUSTOM,
}

internal data class RoutineRecurrenceEditorState(
    val preset: RoutineRecurrencePreset,
    val time: String,
    val customRule: String,
)

@Composable
internal fun RoutineRecurrenceEditor(
    rule: String,
    onRuleChange: (String) -> Unit,
) {
    var state by remember(rule) { mutableStateOf(recurrenceEditorStateFor(rule)) }

    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        Text("Recurrence", style = ThemeTokens.type.label)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            RoutineRecurrencePreset.entries.forEachIndexed { index, preset ->
                val label = preset.label()
                SegmentedButton(
                    selected = label == state.preset.label(),
                    onClick = {
                        val next = state.copy(preset = recurrencePresetFromLabel(label))
                        state = next
                        onRuleChange(buildRecurrenceRule(next))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = RoutineRecurrencePreset.entries.size),
                ) {
                    Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
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
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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

internal fun normalizeRoutineSaveDraft(
    id: String,
    title: String,
    detail: String,
    recurrenceRule: String,
    defaultStartNotBefore: String,
    defaultEstimatedDuration: String,
): RoutineSaveDraft = RoutineSaveDraft(
    id = id,
    title = title,
    detail = detail.ifBlank { null },
    recurrenceRule = recurrenceRule,
    defaultStartNotBefore = defaultStartNotBefore.ifBlank { null },
    defaultEstimatedDuration = defaultEstimatedDuration.ifBlank { null },
)

internal fun recurrenceEditorStateFor(rule: String): RoutineRecurrenceEditorState {
    val normalized = rule.removePrefix("RRULE:")
    val parts = normalized.split(";")
        .mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
        }
        .toMap()
    val time = recurrenceTimeLabel(parts["BYHOUR"], parts["BYMINUTE"]).orEmpty()
    return when {
        parts["FREQ"] == "DAILY" && time.isNotBlank() -> RoutineRecurrenceEditorState(
            preset = RoutineRecurrencePreset.DAILY,
            time = time,
            customRule = rule,
        )
        parts["FREQ"] == "WEEKLY" &&
            parts["BYDAY"] == "MO,TU,WE,TH,FR" &&
            time.isNotBlank() -> RoutineRecurrenceEditorState(
            preset = RoutineRecurrencePreset.WEEKDAYS,
            time = time,
            customRule = rule,
        )
        else -> RoutineRecurrenceEditorState(
            preset = RoutineRecurrencePreset.CUSTOM,
            time = "",
            customRule = rule,
        )
    }
}

internal fun buildRecurrenceRule(state: RoutineRecurrenceEditorState): String = when (state.preset) {
    RoutineRecurrencePreset.DAILY -> buildPresetRecurrenceRule(
        prefix = "FREQ=DAILY",
        time = state.time,
    )
    RoutineRecurrencePreset.WEEKDAYS -> buildPresetRecurrenceRule(
        prefix = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
        time = state.time,
    )
    RoutineRecurrencePreset.CUSTOM -> state.customRule
}

internal fun describeRecurrenceRule(rule: String): String {
    val normalized = rule.removePrefix("RRULE:")
    val parts = normalized.split(";")
        .mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
        }
        .toMap()
    val time = recurrenceTimeLabel(parts["BYHOUR"], parts["BYMINUTE"])
    return when (parts["FREQ"]) {
        "DAILY" -> listOf("Every day", time?.let { "at $it" }).filterNotNull().joinToString(" ")
        "WEEKLY" -> {
            val daySummary = parts["BYDAY"]
                ?.split(",")
                ?.mapNotNull(::weekdayLabel)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: "week"
            listOf("Every $daySummary", time?.let { "at $it" }).filterNotNull().joinToString(" ")
        }
        else -> "Custom recurrence"
    }
}

private fun recurrenceTimeLabel(hour: String?, minute: String?): String? {
    val parsedHour = hour?.toIntOrNull() ?: return null
    val parsedMinute = minute?.toIntOrNull() ?: return null
    val hh = if (parsedHour < 10) "0$parsedHour" else "$parsedHour"
    val mm = if (parsedMinute < 10) "0$parsedMinute" else "$parsedMinute"
    return "$hh:$mm"
}

private fun buildPresetRecurrenceRule(
    prefix: String,
    time: String,
): String {
    val parts = time.split(":")
    if (parts.size != 2) return ""
    val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return ""
    val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return ""
    val paddedMinute = minute.toString().padStart(2, '0')
    return "RRULE:$prefix;BYHOUR=$hour;BYMINUTE=$paddedMinute"
}

private fun weekdayLabel(code: String): String? = when (code) {
    "MO" -> "Mon"
    "TU" -> "Tue"
    "WE" -> "Wed"
    "TH" -> "Thu"
    "FR" -> "Fri"
    "SA" -> "Sat"
    "SU" -> "Sun"
    else -> null
}
