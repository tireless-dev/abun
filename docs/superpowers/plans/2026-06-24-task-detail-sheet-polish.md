# Task Detail Sheet Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine task cards and the task detail sheet so task detail opens in read-only mode, edits happen in place, and delete is confirmed before execution.

**Architecture:** Keep the existing shared `TaskActionsSheet` entry point, but split it into view and edit modes within the same modal sheet. Update the shared task-row presentation in `TaskRows.kt`, add focused shared-UI tests around the new view/edit flow, and align task product/technical docs with the implemented behavior.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Material 3, shared UI JVM tests, Gradle

---

### Task 1: Lock in the new task-row presentation with failing tests

**Files:**
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `task list shows click to open detail affordance instead of manage`() {
    composeRule.setContent {
        AppTheme {
            TaskListScreen(
                state = populatedTaskState(),
                isPomodoroActive = false,
                onSelectTaskFilter = {},
                onOpenTask = {},
            )
        }
    }

    composeRule.onNodeWithText("Click to open detail").assertExists()
    composeRule.onAllNodesWithText("Manage").assertCountEquals(0)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskListScreenTest`
Expected: FAIL because the current row still renders `Manage`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
Text(
    if (disabled) "Pomodoro active" else "Click to open detail",
    style = ThemeTokens.type.bodyMuted,
)
```

Also replace the status tag call with a compact state-dot presentation tied to `TaskStatus`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskListScreenTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/TaskRows.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt
git commit -m "feat: lighten task row detail affordance"
```

### Task 2: Add a failing test for read-only task detail and in-place edit mode

**Files:**
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `task detail opens read only and enters edit mode in place`() {
    composeRule.setContent {
        AppTheme {
            TaskActionsSheet(
                task = sampleOpenTask(),
                history = emptyList(),
                availableParents = emptyList(),
                availableRoutines = emptyList(),
                isPomodoroActive = false,
                onDismiss = {},
                onSaveTask = { _, _, _, _, _, _, _ -> },
                onProgress = {},
                onComplete = {},
                onSkip = {},
                onPostpone = { _, _, _, _, _ -> },
                onDelete = {},
                onStartPomodoro = {},
            )
        }
    }

    composeRule.onNodeWithText("Title").assertDoesNotExist()
    composeRule.onNodeWithContentDescription("Edit task").assertExists()
    composeRule.onNodeWithText("Save").assertDoesNotExist()

    composeRule.onNodeWithContentDescription("Edit task").performClick()

    composeRule.onNodeWithText("Save").assertExists()
    composeRule.onNodeWithText("Cancel").assertExists()
    composeRule.onNodeWithText("Delete").assertExists()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest`
Expected: FAIL because the sheet currently opens in always-editable mode with save controls visible immediately.

- [ ] **Step 3: Write minimal implementation**

```kotlin
var isEditing by remember(task.id) { mutableStateOf(false) }

if (isEditing) {
    EditableTaskFields(...)
    EditModeActions(...)
} else {
    ReadOnlyTaskFields(...)
    IconButton(onClick = { isEditing = true }) { ... }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskActionsSheet.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt
git commit -m "feat: add in-place task detail edit mode"
```

### Task 3: Add a failing test for delete confirmation

**Files:**
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `delete requires confirmation before callback`() {
    var deleteCount = 0

    composeRule.setContent {
        AppTheme {
            TaskActionsSheet(
                task = sampleOpenTask(),
                history = emptyList(),
                availableParents = emptyList(),
                availableRoutines = emptyList(),
                isPomodoroActive = false,
                onDismiss = {},
                onSaveTask = { _, _, _, _, _, _, _ -> },
                onProgress = {},
                onComplete = {},
                onSkip = {},
                onPostpone = { _, _, _, _, _ -> },
                onDelete = { deleteCount += 1 },
                onStartPomodoro = {},
            )
        }
    }

    composeRule.onNodeWithContentDescription("Edit task").performClick()
    composeRule.onNodeWithText("Delete").performClick()
    composeRule.onNodeWithText("Confirm delete").assertExists()
    assertEquals(0, deleteCount)

    composeRule.onNodeWithText("Delete task").performClick()
    assertEquals(1, deleteCount)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest`
Expected: FAIL because delete currently fires directly with no confirmation dialog.

- [ ] **Step 3: Write minimal implementation**

```kotlin
var showDeleteConfirmation by remember(task.id) { mutableStateOf(false) }

if (showDeleteConfirmation) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirmation = false },
        confirmButton = { Button(onClick = onDelete) { Text("Delete task") } },
        dismissButton = { OutlinedButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } },
        title = { Text("Confirm delete") },
        text = { Text("This task will be removed from active views.") },
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskActionsSheet.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt
git commit -m "feat: confirm task deletion from edit mode"
```

### Task 4: Implement the sheet sizing and polish the shared UI behavior

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskActionsSheet.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/TaskRows.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `task detail sheet hides large title and keeps progress actions in read only mode`() {
    composeRule.setContent { ... }

    composeRule.onAllNodesWithText("Open task").assertCountEquals(1)
    composeRule.onNodeWithText("Progress").assertExists()
    composeRule.onNodeWithText("Save").assertDoesNotExist()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest`
Expected: FAIL because the title/header and edit controls do not match the new mode split yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
ModalBottomSheet(
    onDismissRequest = onDismiss,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .verticalScroll(rememberScrollState())
            .padding(ThemeTokens.spacing.lgDp),
    ) { ... }
}
```

Use a compact top row instead of a section-title header, keep action buttons in read-only mode, and ensure edit-mode controls only appear after toggling edit.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskActionsSheet.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/TaskRows.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt
git commit -m "feat: polish task detail sheet presentation"
```

### Task 5: Update task docs and run verification

**Files:**
- Modify: `docs/tasks/functionality-design.md`
- Modify: `docs/tasks/technical-design.md`

- [ ] **Step 1: Update product-facing task behavior docs**

```markdown
- clicking a task row opens a read-only detail sheet
- editing is an explicit in-place mode entered from the top-right edit affordance
- delete is available only from edit mode and requires confirmation
```

- [ ] **Step 2: Update technical design notes**

```markdown
- reviewed on 2026-06-24: `TaskActionsSheet.kt` now uses a single shared modal with read-only and edit modes instead of an always-editable form, and destructive deletion is gated by a confirmation dialog in the presentation layer
```

- [ ] **Step 3: Run focused verification**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest --tests dev.tireless.abun.TaskListScreenTest`
Expected: PASS

- [ ] **Step 4: Run required project verification**

Run: `./gradlew :app:sharedUI:jvmTest :app:desktopApp:test :core:jvmTest`
Expected: PASS

- [ ] **Step 5: Run desktop app for manual verification**

Run: `./gradlew :app:desktopApp:run`
Expected: application launches so the shared task card and detail sheet can be checked on desktop

- [ ] **Step 6: Commit**

```bash
git add docs/tasks/functionality-design.md docs/tasks/technical-design.md
git commit -m "docs: align task detail workflow docs"
```
