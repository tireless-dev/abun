package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.describeRecurrenceRule
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun RoutineRow(
    routine: RoutineListItemView,
    onOpen: (RoutineListItemView) -> Unit,
    onRun: (RoutineListItemView) -> Unit,
) {
    EditorialCard {
        Text(routine.templateTitle, style = ThemeTokens.type.cardTitle)
        routine.templateDetail?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
        Text(describeRecurrenceRule(routine.recurrenceRule), style = ThemeTokens.type.bodyMuted)
        routine.defaultStartNotBefore?.let { Text("Default start: $it", style = ThemeTokens.type.label) }
        routine.defaultEstimatedDuration?.let { Text("Default duration: $it", style = ThemeTokens.type.label) }
        Text(if (routine.isActive) "Active" else "Paused", style = ThemeTokens.type.label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Button(onClick = { onRun(routine) }, enabled = routine.isActive) {
                Text("Run today", style = ThemeTokens.type.body.withMaterialContentColor())
            }
            Button(onClick = { onOpen(routine) }) {
                Text("Manage", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    }
}
