package dev.tireless.abun.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.StructuredRecurrence
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.ui.components.JournalTimeline
import dev.tireless.abun.ui.components.StatusPill
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
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
    val canEdit = !isRoutineDerived

    var isEditing by remember(task.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(task.id) { mutableStateOf(false) }
    var title by remember(task.id) { mutableStateOf(task.title) }
    var detail by remember(task.id) { mutableStateOf(task.detail.orEmpty()) }
    var parentId by remember(task.id) { mutableStateOf(task.parentId) }
    var startNotBefore by remember(task.id) { mutableStateOf(task.startNotBefore.orEmpty()) }
    var endNotAfter by remember(task.id) { mutableStateOf(task.endNotAfter.orEmpty()) }
    var estimatedDuration by remember(task.id) { mutableStateOf(task.estimatedDuration.orEmpty()) }

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

    fun resetDraft() {
        title = task.title
        detail = task.detail.orEmpty()
        parentId = task.parentId
        startNotBefore = task.startNotBefore.orEmpty()
        endNotAfter = task.endNotAfter.orEmpty()
        estimatedDuration = task.estimatedDuration.orEmpty()
    }

    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(windowHeight * 0.8f)
                    .testTag("task-detail-sheet"),
                shape = RoundedCornerShape(
                    topStart = ThemeTokens.radii.largeDp,
                    topEnd = ThemeTokens.radii.largeDp,
                ),
                color = ThemeTokens.colors.surface,
                contentColor = ThemeTokens.colors.textPrimary,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .testTag("task-detail-sheet-content")
                        .verticalScroll(rememberScrollState())
                        .padding(ThemeTokens.spacing.lgDp),
                    verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
                            StatusPill(task.status)
                            routine?.let {
                                Text("Routine: ${it.templateTitle}", style = ThemeTokens.type.bodyMuted)
                            }
                            nextOccurrence?.let {
                                Text("Next occurrence: $it", style = ThemeTokens.type.bodyMuted)
                            }
                        }
                        if (!isEditing && canEdit) {
                            IconButton(
                                modifier = Modifier.testTag("task-detail-edit-button"),
                                onClick = { isEditing = true },
                            ) {
                                Icon(
                                    imageVector = Lucide.Pencil,
                                    contentDescription = "Edit task",
                                    modifier = Modifier.padding(6.dp),
                                    tint = ThemeTokens.colors.textPrimary,
                                )
                            }
                        }
                    }

                    if (isEditing) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    resetDraft()
                                    isEditing = false
                                },
                            ) {
                                Text("Cancel", style = ThemeTokens.type.body.withMaterialContentColor())
                            }
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
                                    isEditing = false
                                },
                                enabled = !isPomodoroActive && title.isNotBlank(),
                            ) {
                                Text("Save", style = ThemeTokens.type.body.withMaterialContentColor())
                            }
                            Button(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !isPomodoroActive,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ThemeTokens.colors.error,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("Delete", style = ThemeTokens.type.body.withMaterialContentColor())
                            }
                        }
                        EditableTaskDetailFields(
                            title = title,
                            onTitleChange = { title = it },
                            detail = detail,
                            onDetailChange = { detail = it },
                            availableParents = availableParents,
                            parentId = parentId,
                            onParentChange = { parentId = it },
                            startNotBefore = startNotBefore,
                            onStartNotBeforeChange = { startNotBefore = it },
                            endNotAfter = endNotAfter,
                            onEndNotAfterChange = { endNotAfter = it },
                            estimatedDuration = estimatedDuration,
                            onEstimatedDurationChange = { estimatedDuration = it },
                        )
                    } else {
                        ReadOnlyTaskDetailFields(
                            task = task,
                            parentTitle = availableParents.firstOrNull { it.id == task.parentId }?.title,
                        )
                        Text("History", style = ThemeTokens.type.label)
                        JournalTimeline(history)
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Confirm delete", style = ThemeTokens.type.cardTitle) },
            text = { Text("Delete this task? This action removes it from active views.", style = ThemeTokens.type.body) },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ThemeTokens.colors.error,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Delete task", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            },
        )
    }
}

@Composable
private fun ReadOnlyTaskDetailFields(
    task: TaskListItemView,
    parentTitle: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp)) {
        TaskDetailValue(label = "Task", value = task.title)
        task.detail?.takeIf { it.isNotBlank() }?.let { TaskDetailValue(label = "Detail", value = it) }
        parentTitle?.let { TaskDetailValue(label = "Parent task", value = it) }
        task.startNotBefore?.takeIf { it.isNotBlank() }?.let { TaskDetailValue(label = "Start not before", value = it) }
        task.endNotAfter?.takeIf { it.isNotBlank() }?.let { TaskDetailValue(label = "End not after", value = it) }
        task.estimatedDuration?.takeIf { it.isNotBlank() }?.let { TaskDetailValue(label = "Estimated duration", value = it) }
    }
}

@Composable
private fun EditableTaskDetailFields(
    title: String,
    onTitleChange: (String) -> Unit,
    detail: String,
    onDetailChange: (String) -> Unit,
    availableParents: List<TaskListItemView>,
    parentId: String?,
    onParentChange: (String?) -> Unit,
    startNotBefore: String,
    onStartNotBeforeChange: (String) -> Unit,
    endNotAfter: String,
    onEndNotAfterChange: (String) -> Unit,
    estimatedDuration: String,
    onEstimatedDurationChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Title", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )
    OutlinedTextField(
        value = detail,
        onValueChange = onDetailChange,
        label = { Text("Detail", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )

    Text("Parent task", style = ThemeTokens.type.label)
    val parentOptions = listOf("No parent") + availableParents.map { it.title }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        parentOptions.forEachIndexed { index, label ->
            SegmentedButton(
                selected = label == (availableParents.firstOrNull { it.id == parentId }?.title ?: "No parent"),
                onClick = { onParentChange(availableParents.firstOrNull { it.title == label }?.id) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = parentOptions.size),
            ) {
                Text(label, style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    }

    OutlinedTextField(
        value = startNotBefore,
        onValueChange = onStartNotBeforeChange,
        label = { Text("Start not before", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )
    OutlinedTextField(
        value = endNotAfter,
        onValueChange = onEndNotAfterChange,
        label = { Text("End not after", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )
    OutlinedTextField(
        value = estimatedDuration,
        onValueChange = onEstimatedDurationChange,
        label = { Text("Estimated duration", style = ThemeTokens.type.label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = ThemeTokens.type.body,
        singleLine = true,
    )
}

@Composable
private fun TaskDetailValue(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
        Text(label, style = ThemeTokens.type.label)
        Text(value, style = ThemeTokens.type.body)
    }
}
