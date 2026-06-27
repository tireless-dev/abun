# Navigation Compose Sheet Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move all shared UI overlay sheets onto Navigation Compose so screens and bottom sheets use one navigation system.

**Architecture:** Extend the existing shared `NavHost` to include both screen routes and sheet routes, using route arguments for entity-backed sheets and state/controller lookups inside the nav graph. Remove the local overlay router from `App.kt`, keep sheet-internal transient state local, and preserve current sheet behavior while making open/dismiss/back-stack transitions nav-driven.

**Tech Stack:** Kotlin Multiplatform, Jetpack Navigation Compose, Jetpack Compose Material 3, desktop Compose UI tests, Gradle

---

### Task 1: Lock in nav-driven sheet expectations with failing tests

**Files:**
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `opening a task navigates to task detail sheet route`() = runDesktopComposeUiTest {
    setContent {
        AppTheme {
            AppContent(
                state = screenshotState(selectedTab = AppTab.TASKS, selectedTaskSubTab = TaskSubTab.TASKS),
                liveNow = ScreenshotNow,
                selectedTaskHistory = emptyList(),
                onSelectTab = {},
                onSelectTaskSubTab = {},
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
                onRunRoutine = {},
                onUpdateThemePreference = {},
                onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                onReopenLogin = {},
                onLogout = {},
                onRequestEmailOtp = {},
                onVerifyEmailOtp = {},
                onSkipLogin = {},
                onUpdateLoginEmail = {},
                taskHistoryFor = { emptyList() },
                onCreateTask = { _, _, _, _, _, _ -> },
                onCreateRoutineConfirm = { _, _, _, _, _ -> },
                onSaveTask = { _, _, _, _, _, _, _ -> },
                onProgressTask = { _, _ -> },
                onCompleteTask = { _, _ -> },
                onSkipTask = { _, _ -> },
                onPostponeTask = { _, _, _, _, _ -> },
                onDeleteTask = {},
                onSaveRoutine = { _, _, _, _, _, _ -> },
                onToggleRoutine = {},
                onDeleteRoutine = {},
                onStartPomodoro = { _, _ -> },
                onCompletePomodoro = { _, _ -> },
                onStopPomodoro = {},
            )
        }
    }

    onNodeWithTag("task-row-task-1").performClick()
    onNodeWithTag("task-detail-sheet").assertExists()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest`
Expected: FAIL because sheet visibility is still driven by local overlay state instead of nav destinations.

- [ ] **Step 3: Write minimal implementation**

```kotlin
onOpenTask = { task ->
    navController.navigate(AppRoute.taskDetail(task.id))
}
```

Also add matching route assertions for create and pomodoro sheet entry points.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt
git commit -m "test: cover nav driven sheet routes"
```

### Task 2: Expand shared route definitions and nav host for sheet destinations

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppRoutes.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppNavHost.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `app nav host resolves task detail route by id`() = runDesktopComposeUiTest {
    setContent {
        AppTheme {
            val navController = rememberNavController()
            LaunchedEffect(Unit) { navController.navigate(AppRoute.taskDetail("task-1")) }
            AppNavHost(
                navController = navController,
                state = screenshotState(selectedTab = AppTab.TASKS),
                liveNow = ScreenshotNow,
                isPomodoroActive = false,
                taskHistoryFor = { emptyList() },
                onSelectTaskFilter = {},
                onOpenTask = {},
                onOpenStartPomodoro = {},
                onCreateTask = {},
                onCreateRoutine = {},
                onOpenRoutine = {},
                onRunRoutine = {},
                onUpdateThemePreference = {},
                onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                onReopenLogin = {},
                onLogout = {},
                onDismissSheet = {},
                onCreateTaskConfirm = { _, _, _, _, _, _ -> },
                onCreateRoutineConfirm = { _, _, _, _, _ -> },
                onSaveTask = { _, _, _, _, _, _, _ -> },
                onProgressTask = { _, _ -> },
                onCompleteTask = { _, _ -> },
                onSkipTask = { _, _ -> },
                onPostponeTask = { _, _, _, _, _ -> },
                onDeleteTask = {},
                onSaveRoutine = { _, _, _, _, _, _ -> },
                onToggleRoutine = {},
                onDeleteRoutine = {},
                onStartPomodoro = { _, _ -> },
                onCompletePomodoro = { _, _ -> },
                onStopPomodoro = {},
            )
        }
    }

    onNodeWithTag("task-detail-sheet").assertExists()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest`
Expected: FAIL because the route graph does not include sheet destinations or route argument resolution yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
sealed class AppRoute(val route: String) {
    data object Day : AppRoute("day")
    data object Tasks : AppRoute("tasks")
    data object Settings : AppRoute("settings")
    data object CreateTask : AppRoute("create-task")
    data object CreateRoutine : AppRoute("create-routine")
    data object StartPomodoro : AppRoute("start-pomodoro")
    data object CompletePomodoro : AppRoute("complete-pomodoro")
    data object TaskDetail : AppRoute("task-detail/{taskId}")
    data object RoutineDetail : AppRoute("routine-detail/{routineId}")

    fun taskDetail(taskId: String) = "task-detail/$taskId"
    fun routineDetail(routineId: String) = "routine-detail/$routineId"
}
```

Extend `AppNavHost(...)` with sheet destinations that resolve IDs from `state` and pop on invalid data.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppRoutes.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppNavHost.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt
git commit -m "feat: add nav graph destinations for sheets"
```

### Task 3: Remove local overlay routing from `App.kt`

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `create task action opens nav backed sheet`() = runDesktopComposeUiTest {
    setContent {
        AppTheme {
            App()
        }
    }

    onNodeWithText("Task").performClick()
    onNodeWithText("Task title").assertExists()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest`
Expected: FAIL because `App.kt` still switches overlays with `currentSheet`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// delete OverlaySheet enum and local selectedTask/selectedRoutine/currentSheet state

onOpenTask = { navController.navigate(AppRoute.taskDetail(it.id)) }
onOpenRoutine = { navController.navigate(AppRoute.routineDetail(it.id)) }
onCreateRoutine = { navController.navigate(AppRoute.CreateRoutine.route) }
onOpenStartPomodoro = { navController.navigate(AppRoute.StartPomodoro.route) }
```

Also move the pomodoro-complete auto-open flow onto `navController.navigate(AppRoute.CompletePomodoro.route)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt
git commit -m "refactor: replace local overlay sheet router"
```

### Task 4: Keep task detail and other sheets working on nav-backed dismiss/save flows

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppNavHost.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskActionsSheet.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/CreateTaskSheet.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/CreateRoutineSheet.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/RoutineActionsSheet.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/PomodoroSheets.kt`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `dismissing nav backed task detail pops the sheet`() = runDesktopComposeUiTest {
    setContent {
        AppTheme {
            // host content navigated to task detail route
        }
    }

    onNodeWithText("Cancel").assertCountEquals(0)
    onNodeWithContentDescription("Edit task").performClick()
    onNodeWithText("Cancel").performClick()
    onNodeWithContentDescription("Edit task").assertExists()
}
```

Add similar coverage for create/dismiss and invalid ID pop behavior.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest --tests dev.tireless.abun.AppNavigationTest`
Expected: FAIL because save/dismiss flows are still coupled to local overlay state assumptions.

- [ ] **Step 3: Write minimal implementation**

```kotlin
TaskActionsSheet(
    ...,
    onDismiss = { navController.popBackStack() },
    onSaveTask = { ...; navController.popBackStack() },
    onDelete = { ...; navController.popBackStack() },
)
```

Apply the same nav-pop completion behavior to all other sheets.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskDetailActionsTest --tests dev.tireless.abun.AppNavigationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppNavHost.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/TaskActionsSheet.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/CreateTaskSheet.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/CreateRoutineSheet.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/RoutineActionsSheet.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/sheets/PomodoroSheets.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskDetailActionsTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt
git commit -m "feat: make sheet actions navigation driven"
```

### Task 5: Align docs and run full verification

**Files:**
- Modify: `docs/tasks/functionality-design.md`
- Modify: `docs/tasks/technical-design.md`

- [ ] **Step 1: Update product-facing docs**

```markdown
- task detail now opens as a nav-driven bottom-sheet destination
- shared sheet visibility is navigation-driven rather than local overlay state
```

- [ ] **Step 2: Update technical design notes**

```markdown
- reviewed on 2026-06-25: screen routes and shared sheet routes now live in one Navigation Compose graph, replacing the older `currentSheet` overlay router in `App.kt`
```

- [ ] **Step 3: Run focused verification**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest --tests dev.tireless.abun.TaskDetailActionsTest`
Expected: PASS

- [ ] **Step 4: Run required project verification**

Run: `./gradlew :app:sharedUI:jvmTest :app:desktopApp:test :core:jvmTest`
Expected: PASS

- [ ] **Step 5: Run desktop app for manual verification**

Run: `./gradlew :app:desktopApp:run`
Expected: app launches with screen navigation and sheet destinations functioning from the shared nav graph

- [ ] **Step 6: Commit**

```bash
git add docs/tasks/functionality-design.md docs/tasks/technical-design.md
git commit -m "docs: record nav driven sheet routing"
```
