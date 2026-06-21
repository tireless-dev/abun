# Tasks Top Bar Subtab Selector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Tasks sub-tab switching into the scaffold title bar using a compact shared dropdown selector with Lucide icons, and remove the old in-body segmented selector.

**Architecture:** The change stays in shared KMP UI. A small shared top-bar dropdown selector composable is added under `app/sharedUI`, then `App.kt` uses it only for `AppTab.TASKS`. The existing `TaskSubTab` state and controller methods remain the source of truth, while tests verify that the body selector is removed and the title bar now owns the interaction.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, Lucide via `compose-icons`, JVM source/screenshot tests, Roborazzi.

---

### Task 1: Add Regression Tests For Top-Bar Ownership

**Files:**
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/LucideAdoptionTest.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/LucideAdoptionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `tasks screen source removes in-body task subtab segmented control`() {
    val appSource = repoRoot
        .resolve("app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt")
        .readText()

    assertFalse(appSource.contains("TaskSubTab.entries.forEachIndexed"))
    assertFalse(appSource.contains("SingleChoiceSegmentedButtonRow(\n        modifier = Modifier.fillMaxWidth(),\n    ) {\n        TaskSubTab.entries"))
}

@Test
fun `top app bar source uses tasks subtab selector with lucide icons`() {
    val appSource = repoRoot
        .resolve("app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt")
        .readText()

    assertTrue(appSource.contains("TaskTopBarSubtabSelector("))
    assertTrue(appSource.contains("Lucide.ListTodo"))
    assertTrue(appSource.contains("Lucide.CalendarCheck"))
    assertTrue(appSource.contains("Lucide.Timer"))
    assertTrue(appSource.contains("Lucide.ChevronLeft"))
    assertTrue(appSource.contains("Lucide.ChevronDown"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.LucideAdoptionTest"`
Expected: FAIL because the segmented control still exists in `TasksScreen(...)` and the top-bar selector does not yet exist

- [ ] **Step 3: Write minimal implementation**

No production implementation yet. Keep the new failing tests in place.

- [ ] **Step 4: Run test to verify it still fails for the intended reason**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.LucideAdoptionTest"`
Expected: FAIL on the new selector assertions, proving the missing behavior is real

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/LucideAdoptionTest.kt
git commit -m "test: cover tasks top bar subtab selector"
```

### Task 2: Add Shared Top-Bar Dropdown Selector Component

**Files:**
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/TaskTopBarSubtabSelector.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/LucideAdoptionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `shared selector source exists for tasks top bar dropdown`() {
    val selectorSource = repoRoot
        .resolve("app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/TaskTopBarSubtabSelector.kt")

    assertTrue(selectorSource.exists(), "Expected shared selector source file to exist")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.LucideAdoptionTest.shared selector source exists for tasks top bar dropdown"`
Expected: FAIL because the selector file does not yet exist

- [ ] **Step 3: Write minimal implementation**

```kotlin
internal data class TaskTopBarSubtabOption(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun TaskTopBarSubtabSelector(
    currentLabel: String,
    currentIcon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<TaskTopBarSubtabOption>,
)
```

Implement the selector with:

- a compact clickable row showing current icon + current label + `Lucide.ChevronLeft` or `Lucide.ChevronDown`
- `DropdownMenu` and `DropdownMenuItem`
- shared theme typography/colors

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.LucideAdoptionTest.shared selector source exists for tasks top bar dropdown"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/TaskTopBarSubtabSelector.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/LucideAdoptionTest.kt
git commit -m "feat: add shared tasks top bar selector"
```

### Task 3: Move Task Subtab Switching Into The Top App Bar

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/LucideAdoptionTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `tasks top bar selector replaces tasks screen segmented selector`() {
    val appSource = repoRoot
        .resolve("app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt")
        .readText()

    assertTrue(appSource.contains("AppTab.TASKS -> \"Tasks\""))
    assertTrue(appSource.contains("TaskTopBarSubtabSelector("))
    assertFalse(appSource.contains("onSelectPanel: (TaskSubTab) -> Unit"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.LucideAdoptionTest.tasks top bar selector replaces tasks screen segmented selector"`
Expected: FAIL because `TasksScreen(...)` still accepts `onSelectPanel`

- [ ] **Step 3: Write minimal implementation**

Update `App.kt` to:

- render a custom `TopAppBar` title row for `AppTab.TASKS`
- track selector expanded state near scaffold state
- map `TaskSubTab` to `Lucide.ListTodo`, `Lucide.CalendarCheck`, and `Lucide.Timer`
- remove `onSelectPanel` from `TasksScreen(...)`
- remove the `SingleChoiceSegmentedButtonRow` for `TaskSubTab` selection from `TasksScreen(...)`

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.LucideAdoptionTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/LucideAdoptionTest.kt
git commit -m "feat: move tasks subtab switcher to top bar"
```

### Task 4: Refresh Screenshots And Docs

**Files:**
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ScreenshotTestSupport.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt`
- Modify: `docs/base/shared-ui-design-system.md`
- Modify: `docs/base/technical-architecture.md`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `shared UI docs mention top app bar dropdown selectors`() {
    val designDoc = repoRoot.resolve("docs/base/shared-ui-design-system.md").readText()
    val architectureDoc = repoRoot.resolve("docs/base/technical-architecture.md").readText()

    assertTrue(designDoc.contains("top-app-bar dropdown selectors"))
    assertTrue(architectureDoc.contains("top app bar chrome may host lightweight shared selectors"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.LucideAdoptionTest.shared UI docs mention top app bar dropdown selectors"`
Expected: FAIL because the docs do not yet mention the new pattern

- [ ] **Step 3: Write minimal implementation**

Update screenshot helpers/tests to support Tasks screenshots with the selector visible in the title bar, and update the docs with short explicit notes about compact top-app-bar dropdown selectors for sub-view switching.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.LucideAdoptionTest.shared UI docs mention top app bar dropdown selectors"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ScreenshotTestSupport.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt docs/base/shared-ui-design-system.md docs/base/technical-architecture.md app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/LucideAdoptionTest.kt
git commit -m "docs: align tasks top bar selector pattern"
```

### Task 5: Full Verification

**Files:**
- Modify: `app/sharedUI/src/jvmTest/screenshots/screens/tasks_empty.png`
- Modify: `app/sharedUI/src/jvmTest/screenshots/screens/tasks_populated.png`
- Modify: `app/sharedUI/src/jvmTest/screenshots/screens/pomodoro_inactive.png`
- Modify: `app/sharedUI/src/jvmTest/screenshots/screens/pomodoro_active.png`
- Modify: any additional updated screenshot fixtures produced by the shared UI JVM tests

- [ ] **Step 1: Run shared UI JVM verification**

Run: `./gradlew :app:sharedUI:jvmTest`
Expected: PASS

- [ ] **Step 2: Run desktop and core verification**

Run: `./gradlew :app:desktopApp:test :core:jvmTest`
Expected: PASS

- [ ] **Step 3: Run desktop app for visual sanity**

Run: `./gradlew :app:desktopApp:run`
Expected: Tasks shows `Tasks` as the main title and the active sub-tab selector on the right with the requested Lucide icons and chevrons

- [ ] **Step 4: Commit**

```bash
git add app/sharedUI/src/jvmTest/screenshots
git commit -m "test: refresh tasks top bar selector screenshots"
```
