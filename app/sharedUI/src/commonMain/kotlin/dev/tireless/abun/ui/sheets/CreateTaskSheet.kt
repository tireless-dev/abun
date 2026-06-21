package dev.tireless.abun.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CreateTaskSheet(
    context: TaskCreateContext,
    onDismiss: () -> Unit,
    onCreate: (TaskSaveDraft) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
        ) {
            CreateTaskSheetContent(
                context = context,
                onCreate = onCreate,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
internal fun CreateTaskSheetContent(
    context: TaskCreateContext,
    onCreate: (TaskSaveDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(context) { mutableStateOf(defaultTaskCreateDraft(context)) }
    val dateOptions = remember(context) { taskCreateDateOptions(context) }
    val selectedDateLabel = dateOptions.firstOrNull { it.date == draft.startDate }?.label ?: dateOptions.first().label
    val createEnabled = draft.title.isNotBlank() &&
        (!draft.hasSchedule || !draft.startDate.isNullOrBlank()) &&
        (draft.durationPreset != DurationPreset.CUSTOM || draft.customDurationMinutes.trim().toIntOrNull()?.let { it > 0 } == true)

    Text("Create task", style = ThemeTokens.type.sectionTitle)
    OutlinedTextField(
        value = draft.title,
        onValueChange = { draft = draft.copy(title = it) },
        label = { Text("Task title", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.detail,
        onValueChange = { draft = draft.copy(detail = it) },
        label = { Text("Detail", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )
    if (draft.hasSchedule) {
        Text("Starts on", style = ThemeTokens.type.label)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            dateOptions.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.label == selectedDateLabel,
                    onClick = { draft = draft.copy(startDate = option.date) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = dateOptions.size),
                ) {
                    Text(option.label, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        Text(draft.startDate.orEmpty(), style = ThemeTokens.type.bodyMuted)
        Text("Estimated duration", style = ThemeTokens.type.label)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DurationPreset.entries.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = preset.label == draft.durationPreset.label,
                    onClick = {
                        draft = draft.copy(
                            durationPreset = preset,
                            customDurationMinutes = if (preset == DurationPreset.CUSTOM) draft.customDurationMinutes else "",
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DurationPreset.entries.size),
                ) {
                    Text(preset.label, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        if (draft.durationPreset == DurationPreset.CUSTOM) {
            OutlinedTextField(
                value = draft.customDurationMinutes,
                onValueChange = { draft = draft.copy(customDurationMinutes = it) },
                label = { Text("Custom minutes", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
        }
        OutlinedButton(
            onClick = { draft = draft.copy(hasSchedule = false, startDate = null, durationPreset = DurationPreset.NONE, customDurationMinutes = "") },
        ) {
            Text("Clear schedule", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    } else {
        Text("This task will go to backlog.", style = ThemeTokens.type.bodyMuted)
        OutlinedButton(
            onClick = {
                draft = draft.copy(
                    hasSchedule = true,
                    startDate = draft.startDate ?: context.selectedDate,
                )
            },
        ) {
            Text("Add schedule", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
    ) {
        OutlinedButton(onClick = onDismiss) {
            Text("Cancel", style = ThemeTokens.type.body.withMaterialContentColor())
        }
        Button(
            onClick = { onCreate(normalizeTaskCreateDraft(draft)) },
            enabled = createEnabled,
        ) {
            Text("Create", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
}
