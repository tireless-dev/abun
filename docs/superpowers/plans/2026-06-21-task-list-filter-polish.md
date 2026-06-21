# Task List Filter Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the task list screen chrome by removing redundant headings and the panel container while making the filter selector icon-first with label visibility only for the selected option.

**Architecture:** Keep the change inside the shared Compose task surface so Desktop and Android inherit the same behavior. Cover the visual behavior with a shared UI Compose test first, then make the minimal `App.kt` changes, and finally update task docs to describe the lighter filter presentation.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Material 3, Lucide Compose icons, JVM Compose UI tests, markdown docs

---

### Task 1: Lock the desired task filter behavior with a failing shared UI test

**Files:**
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `task list screen keeps only selected filter label visible`() = runDesktopComposeUiTest {
    // Render TaskListScreen with selected filter = All active and at least one backlog task.
    // Assert "Task list" and "All active" header title do not render as standalone headings.
    // Assert the selected filter text is visible.
    // Assert "Backlog" text is absent before selection.
    // Click the backlog segmented button by content description.
    // Assert "Backlog" text becomes visible after selection.
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskListScreenTest`
Expected: FAIL because the current task list screen still renders the extra heading text and always shows labels for every segmented button.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// Add per-filter icon mapping.
// Update segmented buttons to render Icon for every filter and Text only for the selected one.
// Remove the SectionHeader and surrounding Panel from TaskListScreen.
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskListScreenTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt
git commit -m "feat: simplify task list filters"
```

### Task 2: Align product and technical docs with the updated shared task surface chrome

**Files:**
- Modify: `docs/tasks/functionality-design.md`
- Modify: `docs/tasks/technical-design.md`

- [ ] **Step 1: Write the doc update**

```md
- note that the Tasks surface now uses a compact icon-first first-level filter selector
- note that only the active filter exposes its text label while inactive filters stay icon-only
- note that the list content sits directly on the shared surface rather than inside a nested task-list panel
```

- [ ] **Step 2: Verify docs reflect the implementation**

Run: `rg -n "icon-first|icon only|nested task-list panel|selected filter" docs/tasks/functionality-design.md docs/tasks/technical-design.md`
Expected: matching lines in both documents describing the shipped behavior

- [ ] **Step 3: Commit**

```bash
git add docs/tasks/functionality-design.md docs/tasks/technical-design.md
git commit -m "docs: align task list filter chrome"
```
