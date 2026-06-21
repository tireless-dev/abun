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
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CreateRoutineSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?, String, String?, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var recurrenceRule by remember { mutableStateOf("RRULE:FREQ=DAILY;BYHOUR=9;BYMINUTE=0") }
    var defaultStartNotBefore by remember { mutableStateOf("") }
    var defaultEstimatedDuration by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Text("Create routine", style = ThemeTokens.type.sectionTitle)
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
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel", style = ThemeTokens.type.body.withMaterialContentColor())
                }
                Button(
                    onClick = {
                        onCreate(
                            title,
                            detail.ifBlank { null },
                            recurrenceRule,
                            defaultStartNotBefore.ifBlank { null },
                            defaultEstimatedDuration.ifBlank { null },
                        )
                    },
                    enabled = title.isNotBlank() && recurrenceRule.isNotBlank(),
                ) {
                    Text("Create", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
    }
}
