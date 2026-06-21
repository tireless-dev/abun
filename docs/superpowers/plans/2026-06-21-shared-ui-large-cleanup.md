# Shared UI Large Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce `App.kt` to app-shell orchestration only by moving the remaining screen content, reusable UI pieces, and modal sheets into dedicated `ui/screens`, `ui/components`, and `ui/sheets` packages without changing behavior.

**Architecture:** The cleanup keeps the current top-level Compose Navigation and state-driven task subtabs intact while relocating responsibilities into focused packages. Shared display primitives move into `ui/components`, modal flows and their UI support code move into `ui/sheets`, and `SettingsScreenContent` moves into the settings screen file so `App.kt` stops serving as a catch-all UI container.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Material 3, Jetpack Compose Navigation, JVM Compose UI tests, Roborazzi screenshot tests, markdown docs

---

### Task 1: Move settings screen content out of `App.kt`

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/SettingsScreen.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ThemePreferenceSupportTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt`

- [ ] **Step 1: Write the failing import updates**

```kotlin
// Update tests to import:
import dev.tireless.abun.ui.screens.SettingsScreenContent
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.ThemePreferenceSupportTest --tests dev.tireless.abun.GuideScreenDebugAuthTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: FAIL because `ui.screens.SettingsScreenContent` does not exist yet.

- [ ] **Step 3: Move the minimal implementation**

```kotlin
// app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/SettingsScreen.kt
package dev.tireless.abun.ui.screens

// Keep the current SettingsScreen wrapper and move SettingsScreenContent here.
// Import any remaining helpers from App.kt temporarily if they are not yet extracted.
```

```kotlin
// app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt
// Remove SettingsScreenContent from App.kt and import it from ui.screens where needed.
```

- [ ] **Step 4: Re-run the targeted tests**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.ThemePreferenceSupportTest --tests dev.tireless.abun.GuideScreenDebugAuthTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/SettingsScreen.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ThemePreferenceSupportTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt
git commit -m "refactor: move settings screen content"
```

### Task 2: Extract reusable presentation pieces into `ui/components`

**Files:**
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/SurfaceSections.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/TaskRows.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/Timeline.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/RoutineRow.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components/PomodoroRow.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/DayScreen.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/TaskListScreen.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/RoutineListScreen.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/PomodoroScreen.kt`

- [ ] **Step 1: Write the failing import updates**

```kotlin
// Update screen files to import:
import dev.tireless.abun.ui.components.Panel
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.components.TaskStack
import dev.tireless.abun.ui.components.RoutineRow
import dev.tireless.abun.ui.components.PomodoroRow
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskListScreenTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: FAIL because the `ui.components` symbols do not exist yet.

- [ ] **Step 3: Move the reusable components**

```kotlin
// SurfaceSections.kt
package dev.tireless.abun.ui.components

// Move Panel, SectionHeader, MetricRow here.
```

```kotlin
// TaskRows.kt
package dev.tireless.abun.ui.components

// Move TaskStack, TaskRow, StatusPill here.
```

```kotlin
// Timeline.kt
package dev.tireless.abun.ui.components

// Move JournalTimeline here.
```

```kotlin
// RoutineRow.kt / PomodoroRow.kt
package dev.tireless.abun.ui.components

// Move the row composables into focused files.
```

- [ ] **Step 4: Re-run the targeted tests**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskListScreenTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/components app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/DayScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/TaskListScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/RoutineListScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/PomodoroScreen.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt
git commit -m "refactor: move shared ui components"
```

### Task 3: Extract task creation sheet and its support helpers into `ui/sheets`

**Files:**
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/CreateTaskSheet.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskSheetSupport.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/CreateTaskSheetTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskSaveDraftTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt`

- [ ] **Step 1: Write the failing import updates**

```kotlin
// Update tests to import:
import dev.tireless.abun.ui.sheets.CreateTaskSheet
import dev.tireless.abun.ui.sheets.CreateTaskSheetContent
import dev.tireless.abun.ui.sheets.defaultTaskCreateDraft
import dev.tireless.abun.ui.sheets.normalizeTaskCreateDraft
import dev.tireless.abun.ui.sheets.normalizeTaskSaveDraft
import dev.tireless.abun.ui.sheets.taskCreateContextFor
import dev.tireless.abun.ui.sheets.taskCreateStartOfDayIso
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.CreateTaskSheetTest --tests dev.tireless.abun.TaskSaveDraftTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: FAIL because the sheet package symbols do not exist yet.

- [ ] **Step 3: Move the sheet and support code**

```kotlin
// CreateTaskSheet.kt
package dev.tireless.abun.ui.sheets

// Move CreateTaskSheet and CreateTaskSheetContent here.
```

```kotlin
// TaskSheetSupport.kt
package dev.tireless.abun.ui.sheets

// Move normalizeTaskSaveDraft, taskCreateContextFor, defaultTaskCreateDraft,
// taskCreateDateOptions, normalizeTaskCreateDraft, taskCreateStartOfDayIso here.
```

- [ ] **Step 4: Re-run the targeted tests**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.CreateTaskSheetTest --tests dev.tireless.abun.TaskSaveDraftTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/CreateTaskSheet.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskSheetSupport.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/CreateTaskSheetTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskSaveDraftTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt
git commit -m "refactor: move task create sheet"
```

### Task 4: Extract remaining sheets into `ui/sheets`

**Files:**
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/CreateRoutineSheet.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskActionsSheet.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/RoutineActionsSheet.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/PomodoroSheets.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/RoutineSheetSupport.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt`

- [ ] **Step 1: Write the failing import updates**

```kotlin
// Update screenshot tests to import all remaining sheet composables from ui.sheets.
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: FAIL because the new `ui.sheets` symbols do not exist yet.

- [ ] **Step 3: Move the remaining sheets and support**

```kotlin
// CreateRoutineSheet.kt
package dev.tireless.abun.ui.sheets

// Move CreateRoutineSheet here.
```

```kotlin
// TaskActionsSheet.kt / RoutineActionsSheet.kt / PomodoroSheets.kt
package dev.tireless.abun.ui.sheets

// Move the modal sheet composables into focused files.
```

```kotlin
// RoutineSheetSupport.kt
package dev.tireless.abun.ui.sheets

// Move recurrence editor support and routine normalization helpers that are sheet-only.
```

- [ ] **Step 4: Re-run the targeted tests**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt
git commit -m "refactor: move remaining sheets"
```

### Task 5: Final docs and verification cleanup

**Files:**
- Modify: `docs/tasks/technical-design.md`
- Modify: `docs/tasks/functionality-design.md`

- [ ] **Step 1: Update docs**

```md
- `App.kt` is now shell-only
- `ui/components` owns reusable presentation primitives
- `ui/sheets` owns modal flows and related UI support
- shared UI behavior is unchanged
```

- [ ] **Step 2: Run full verification**

Run: `./gradlew :app:sharedUI:jvmTest`
Expected: PASS

Run: `./gradlew :app:desktopApp:test`
Expected: PASS

- [ ] **Step 3: Verify docs contain the new structure**

Run: `rg -n "shell-only|ui/components|ui/sheets|unchanged" docs/tasks/functionality-design.md docs/tasks/technical-design.md`
Expected: matching lines in the updated docs

- [ ] **Step 4: Commit**

```bash
git add docs/tasks/technical-design.md docs/tasks/functionality-design.md
git commit -m "docs: align shared ui cleanup"
```
