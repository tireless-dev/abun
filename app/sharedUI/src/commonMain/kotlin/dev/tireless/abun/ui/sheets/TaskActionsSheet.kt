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
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.StructuredRecurrence
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.taskDetailActionLabels
import dev.tireless.abun.ui.components.JournalTimeline
import dev.tireless.abun.ui.components.StatusPill
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TaskActionsSheet(
    task: TaskListItemView?,
    history: List<JournalEntryView>,
    availableParents: List<TaskListItemView>,
    availableRoutines: List<RoutineListItemView>,
    isPomodoroActive: Boolean,
    onDismiss: () -> Unit,
    onSaveTask: (String, String, String?, String?, String?, String?, String?) -> Unit,
    onProgress: (String) -> Unit,
    onComplete: (String) -> Unit,
    onSkip: (String) -> Unit,
    onPostpone: (String, String?, String?, String?, String?) -> Unit,
    onDelete: () -> Unit,
    onStartPomodoro: () -> Unit,
) {
    if (task == null) return
    val routine = remember(task.id) { availableRoutines.find { it.id == task.routineId } }
    val isRoutineDerived = routine != null

    var title by remember(task.id) { mutableStateOf(task.title) }
    var detail by remember(task.id) { mutableStateOf(task.detail.orEmpty()) }
    var parentId by remember(task.id) { mutableStateOf(task.parentId) }
    var startNotBefore by remember(task.id) { mutableStateOf(task.startNotBefore.orEmpty()) }
    var endNotAfter by remember(task.id) { mutableStateOf(task.endNotAfter.orEmpty()) }
    var estimatedDuration by remember(task.id) { mutableStateOf(task.estimatedDuration.orEmpty()) }
    var note by remember(task.id) { mutableStateOf("") }

    val nextOccurrence = remember(routine, startNotBefore) {
        if (routine == null) return@remember null
        val structured = StructuredRecurrence.fromRRule(routine.recurrenceRule)
        val after = try {
            if (startNotBefore.isNotBlank()) {
                Instant.parse(startNotBefore).toLocalDateTime(TimeZone.currentSystemDefault())
            } else {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }
        } catch (_: Exception) {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }
        structured.nextOccurrence(after)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThemeTokens.spacing.lgDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
        ) {
            Text(if (isRoutineDerived) "Routine: ${task.title}" else task.title, style = ThemeTokens.type.sectionTitle)
            if (isRoutineDerived) {
                Text("Routine: ${routine.templateTitle}", style = ThemeTokens.type.bodyMuted)
                nextOccurrence?.let {
                    Text("Next occurrence: $it", style = ThemeTokens.type.bodyMuted)
                }
            }
            StatusPill(task.status)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
                singleLine = true,
                enabled = !isRoutineDerived,
            )
            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it },
                label = { Text("Detail", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
                singleLine = true,
                enabled = !isRoutineDerived,
            )

            if (!isRoutineDerived) {
                Text("Parent task", style = ThemeTokens.type.label)
                val parentOptions = listOf("No parent") + availableParents.map { it.title }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    parentOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = label == (availableParents.firstOrNull { it.id == parentId }?.title ?: "No parent"),
                            onClick = { parentId = availableParents.firstOrNull { it.title == label }?.id },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = parentOptions.size),
                        ) {
                            Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
                        }
                    }
                }
            }

            OutlinedTextField(
                value = startNotBefore,
                onValueChange = { startNotBefore = it },
                label = { Text("Start not before", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
                singleLine = true,
                enabled = !isRoutineDerived,
            )
            OutlinedTextField(
                value = endNotAfter,
                onValueChange = { endNotAfter = it },
                label = { Text("End not after", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
                singleLine = true,
                enabled = !isRoutineDerived,
            )
            OutlinedTextField(
                value = estimatedDuration,
                onValueChange = { estimatedDuration = it },
                label = { Text("Estimated duration", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = if (isRoutineDerived) ThemeTokens.type.bodyMuted else ThemeTokens.type.body,
                singleLine = true,
                enabled = !isRoutineDerived,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Close", style = ThemeTokens.type.body.withMaterialContentColor())
                }
                if (!isRoutineDerived) {
                    Button(
                        onClick = {
                            val draft = normalizeTaskSaveDraft(
                                title = title,
                                detail = detail,
                                parentId = parentId,
                                startNotBefore = startNotBefore,
                                endNotAfter = endNotAfter,
                                estimatedDuration = estimatedDuration,
                            )
                            onSaveTask(
                                task.id,
                                draft.title,
                                draft.detail,
                                draft.parentId,
                                draft.startNotBefore,
                                draft.endNotAfter,
                                draft.estimatedDuration,
                            )
                        },
                        enabled = !isPomodoroActive && title.isNotBlank(),
                    ) {
                        Text("Save", style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }
            if (taskDetailActionLabels(task).any { it != "Delete task" }) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Task note", style = ThemeTokens.type.label) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
            }
            Text("History", style = ThemeTokens.type.label)
            JournalTimeline(history)
            if (isPomodoroActive) {
                Text("Pomodoro is active. Task edits are temporarily disabled.", color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
            } else {
                val actions = taskDetailActionLabels(task)
                if ("Progress" in actions || "Complete" in actions || "Skip" in actions || "Postpone" in actions || "Pomodoro" in actions) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                    ) {
                        if ("Progress" in actions) Button(onClick = { onProgress(note) }) { Text("Progress", style = ThemeTokens.type.body.withMaterialContentColor()) }
                        if ("Complete" in actions) Button(onClick = { onComplete(note) }) { Text("Complete", style = ThemeTokens.type.body.withMaterialContentColor()) }
                        if ("Skip" in actions) Button(onClick = { onSkip(note) }) { Text("Skip", style = ThemeTokens.type.body.withMaterialContentColor()) }
                        if ("Postpone" in actions) {
                            Button(
                                onClick = {
                                    onPostpone(
                                        task.id,
                                        startNotBefore.ifBlank { null },
                                        endNotAfter.ifBlank { null },
                                        estimatedDuration.ifBlank { null },
                                        note.ifBlank { null },
                                    )
                                },
                            ) {
                                Text("Postpone", style = ThemeTokens.type.body.withMaterialContentColor())
                            }
                        }
                        if ("Pomodoro" in actions) {
                            Button(onClick = onStartPomodoro, enabled = task.status.isOpen()) {
                                Text("Pomodoro", style = ThemeTokens.type.body.withMaterialContentColor())
                            }
                        }
                    }
                }
                if ("Delete task" in actions) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                    ) {
                        Button(onClick = onDelete) { Text("Delete task", style = ThemeTokens.type.body.withMaterialContentColor()) }
                    }
                }
            }
        }
    }
}

private fun dev.tireless.abun.sync.TaskStatus.isOpen(): Boolean =
    this == dev.tireless.abun.sync.TaskStatus.PENDING ||
        this == dev.tireless.abun.sync.TaskStatus.IN_PROGRESS ||
        this == dev.tireless.abun.sync.TaskStatus.UNKNOWN
