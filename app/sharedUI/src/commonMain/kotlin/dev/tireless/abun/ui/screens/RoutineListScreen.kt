package dev.tireless.abun.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.ui.components.Panel
import dev.tireless.abun.ui.components.RoutineRow
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun RoutineListScreen(
    state: AppUiState,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader("Routine support", "Routines")
            Button(onClick = onCreateRoutine) {
                Text("Create", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
        if (state.taskView.routines.isEmpty()) {
            Text("No routines.", style = ThemeTokens.type.body)
        } else {
            state.taskView.routines.forEach { routine ->
                RoutineRow(routine, onOpenRoutine, onRunRoutine)
            }
        }
    }
}
