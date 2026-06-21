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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun RoutineActionsSheet(
    routine: RoutineListItemView?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, String?, String?) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    if (routine == null) return
    var title by remember(routine.id) { mutableStateOf(routine.templateTitle) }
    var detail by remember(routine.id) { mutableStateOf(routine.templateDetail.orEmpty()) }
    var recurrenceRule by remember(routine.id) { mutableStateOf(routine.recurrenceRule) }
    var defaultStartNotBefore by remember(routine.id) { mutableStateOf(routine.defaultStartNotBefore.orEmpty()) }
    var defaultEstimatedDuration by remember(routine.id) { mutableStateOf(routine.defaultEstimatedDuration.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Text(routine.templateTitle, style = ThemeTokens.type.sectionTitle)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Routine title", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it },
                label = { Text("Routine detail", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            RoutineRecurrenceEditor(rule = recurrenceRule, onRuleChange = { recurrenceRule = it })
            Text(describeRecurrenceRule(recurrenceRule), style = ThemeTokens.type.bodyMuted)
            OutlinedTextField(
                value = defaultStartNotBefore,
                onValueChange = { defaultStartNotBefore = it },
                label = { Text("Default start not before", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            OutlinedTextField(
                value = defaultEstimatedDuration,
                onValueChange = { defaultEstimatedDuration = it },
                label = { Text("Default estimated duration", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                Button(
                    onClick = {
                        val draft = normalizeRoutineSaveDraft(
                            id = routine.id,
                            title = title,
                            detail = detail,
                            recurrenceRule = recurrenceRule,
                            defaultStartNotBefore = defaultStartNotBefore,
                            defaultEstimatedDuration = defaultEstimatedDuration,
                        )
                        onSave(
                            draft.id,
                            draft.title,
                            draft.detail,
                            draft.recurrenceRule,
                            draft.defaultStartNotBefore,
                            draft.defaultEstimatedDuration,
                        )
                    },
                    enabled = title.isNotBlank() && recurrenceRule.isNotBlank(),
                ) {
                    Text("Save", style = ThemeTokens.type.body.withMaterialContentColor())
                }
                Button(onClick = onToggle) {
                    Text(if (routine.isActive) "Pause" else "Activate", style = ThemeTokens.type.body.withMaterialContentColor())
                }
                Button(onClick = onDelete) { Text("Delete", style = ThemeTokens.type.body.withMaterialContentColor()) }
                OutlinedButton(onClick = onDismiss) { Text("Close", style = ThemeTokens.type.body.withMaterialContentColor()) }
            }
        }
    }
}
