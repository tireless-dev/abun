package dev.tireless.abun.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.label
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun StartPomodoroSheet(
    state: AppUiState,
    hasActive: Boolean,
    onDismiss: () -> Unit,
    onStart: (String?, PomodoroPhase) -> Unit,
) {
    val openTasks = state.pomodoroStartTasks
    var selectedTaskId by remember(openTasks) { mutableStateOf<String?>(openTasks.firstOrNull()?.taskId) }
    var selectedPhase by remember { mutableStateOf(PomodoroPhase.FOCUS) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Text("Start pomodoro", style = ThemeTokens.type.sectionTitle)
            if (hasActive) {
                Text("A timer is already active. Complete or stop it first.", color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
                Button(onClick = onDismiss) { Text("Close", style = ThemeTokens.type.body.withMaterialContentColor()) }
                return@Column
            }
            Text("Task", style = ThemeTokens.type.label)
            val taskOptions = listOf("No task") + openTasks.map { it.title }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                taskOptions.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = label == (openTasks.firstOrNull { it.taskId == selectedTaskId }?.title ?: "No task"),
                        onClick = { selectedTaskId = openTasks.firstOrNull { it.title == label }?.taskId },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = taskOptions.size),
                    ) {
                        Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }
            Text("Mode", style = ThemeTokens.type.label)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PomodoroPhase.entries.forEachIndexed { index, phase ->
                    val label = phase.label()
                    SegmentedButton(
                        selected = label == selectedPhase.label(),
                        onClick = { selectedPhase = pomodoroPhaseFromLabel(label) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = PomodoroPhase.entries.size),
                    ) {
                        Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel", style = ThemeTokens.type.body.withMaterialContentColor()) }
                Button(onClick = { onStart(selectedTaskId, selectedPhase) }) {
                    Text("Start", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CompletePomodoroSheet(
    state: AppUiState,
    liveNow: Long,
    onDismiss: () -> Unit,
    onSave: (String, PomodoroTaskUpdate) -> Unit,
    onStop: (String) -> Unit,
) {
    val active = state.activePomodoroSession ?: return
    val remaining = active.endsAtEpochMillis - liveNow
    var note by remember(active.id) { mutableStateOf(active.note.orEmpty()) }
    var taskUpdate by remember(active.id) { mutableStateOf(PomodoroTaskUpdate.NONE) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Text(if (remaining <= 0) "Session complete" else "Active pomodoro", style = ThemeTokens.type.sectionTitle)
            Text(active.taskTitle ?: "Standalone timer", style = ThemeTokens.type.body)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Session note", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            if (active.taskId != null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PomodoroTaskUpdate.entries.forEachIndexed { index, update ->
                        val label = update.label()
                        SegmentedButton(
                            selected = label == taskUpdate.label(),
                            onClick = { taskUpdate = pomodoroTaskUpdateFromLabel(label) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = PomodoroTaskUpdate.entries.size),
                        ) {
                            Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onStop(note) }) { Text("Stop", style = ThemeTokens.type.body.withMaterialContentColor()) }
                Button(onClick = { onSave(note, taskUpdate) }) {
                    Text(if (remaining <= 0) "Save" else "Complete", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
    }
}

private fun pomodoroPhaseFromLabel(label: String): PomodoroPhase = when (label) {
    "Work" -> PomodoroPhase.FOCUS
    "Short break" -> PomodoroPhase.SHORT_BREAK
    "Long break" -> PomodoroPhase.LONG_BREAK
    else -> PomodoroPhase.FOCUS
}

private fun PomodoroTaskUpdate.label(): String = when (this) {
    PomodoroTaskUpdate.NONE -> "No change"
    PomodoroTaskUpdate.PROGRESS -> "Progress"
    PomodoroTaskUpdate.COMPLETE -> "Complete"
    PomodoroTaskUpdate.CANCEL -> "Cancel task"
}

private fun pomodoroTaskUpdateFromLabel(label: String): PomodoroTaskUpdate = when (label) {
    "No change" -> PomodoroTaskUpdate.NONE
    "Progress" -> PomodoroTaskUpdate.PROGRESS
    "Complete" -> PomodoroTaskUpdate.COMPLETE
    "Cancel task" -> PomodoroTaskUpdate.CANCEL
    else -> PomodoroTaskUpdate.NONE
}
