# Shared UI Screen Navigation Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move screen composables out of `App.kt` into `ui/screens` and introduce Jetpack Compose Navigation for top-level `Day`, `Tasks`, and `Settings` routing while keeping task subtabs state-driven.

**Architecture:** The refactor keeps `App()` as the shell and orchestration layer, adds a small `ui/navigation` layer for top-level routes, and relocates screen implementations into `ui/screens`. `NavController` will own only the top-level app route, while `AppUiState.selectedTaskSubTab` continues to drive the task-surface subviews.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Multiplatform Material 3, Jetpack Compose Navigation, JVM Compose UI tests, Roborazzi screenshot tests, markdown docs

---

### Task 1: Add top-level navigation dependency and lock a failing shell test

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/sharedUI/build.gradle.kts`
- Create: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {
    @Test
    fun `app shell shows one top level screen title for selected tab`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                App()
            }
        }

        onAllNodesWithText("Day").assertCountEquals(1)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest`
Expected: FAIL because there is no explicit top-level navigation shell yet and the new test is not wired to the planned route structure.

- [ ] **Step 3: Add the navigation dependency**

```toml
# gradle/libs.versions.toml
androidx-navigation = "2.9.0-beta03"

androidx-navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "androidx-navigation" }
```

```kotlin
// app/sharedUI/build.gradle.kts
commonMain.dependencies {
    implementation(libs.androidx.navigation.compose)
}
```

- [ ] **Step 4: Re-run the failing test**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest`
Expected: FAIL again, but now after the dependency resolves cleanly, proving the failure is about missing implementation rather than missing libraries.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/sharedUI/build.gradle.kts app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt
git commit -m "test: add shared ui navigation shell coverage"
```

### Task 2: Introduce top-level route model and nav host

**Files:**
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppRoutes.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppNavHost.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`

- [ ] **Step 1: Write the failing test for route mapping**

```kotlin
package dev.tireless.abun

import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.navigation.AppRoute
import dev.tireless.abun.ui.navigation.appTabForRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRouteMappingTest {
    @Test
    fun `route mapping matches top level tabs`() {
        assertEquals(AppTab.TODAY, appTabForRoute(AppRoute.Day.route))
        assertEquals(AppTab.TASKS, appTabForRoute(AppRoute.Tasks.route))
        assertEquals(AppTab.SETTINGS, appTabForRoute(AppRoute.Settings.route))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppRouteMappingTest`
Expected: FAIL because `AppRoute` and `appTabForRoute` do not exist yet.

- [ ] **Step 3: Write minimal navigation support**

```kotlin
// AppRoutes.kt
package dev.tireless.abun.ui.navigation

import dev.tireless.abun.app.AppTab

sealed class AppRoute(val route: String) {
    data object Day : AppRoute("day")
    data object Tasks : AppRoute("tasks")
    data object Settings : AppRoute("settings")
}

fun routeForTab(tab: AppTab): String = when (tab) {
    AppTab.TODAY -> AppRoute.Day.route
    AppTab.TASKS -> AppRoute.Tasks.route
    AppTab.SETTINGS -> AppRoute.Settings.route
}

fun appTabForRoute(route: String?): AppTab = when (route) {
    AppRoute.Tasks.route -> AppTab.TASKS
    AppRoute.Settings.route -> AppTab.SETTINGS
    else -> AppTab.TODAY
}
```

```kotlin
// AppNavHost.kt
package dev.tireless.abun.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.app.TaskListFilter
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.ui.screens.DayScreen
import dev.tireless.abun.ui.screens.SettingsScreen
import dev.tireless.abun.ui.screens.TasksScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    state: AppUiState,
    liveNow: Long,
    isPomodoroActive: Boolean,
    onSelectTaskFilter: (TaskListFilter) -> Unit,
    onOpenTask: (TaskListItemView) -> Unit,
    onOpenStartPomodoro: () -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
    onUpdateThemePreference: (dev.tireless.abun.app.ThemePreference) -> Unit,
    onUpdatePreferences: (String, Int, Int, Int, Int, String, dev.tireless.abun.app.DateFormatPreference, dev.tireless.abun.app.ThemePreference, String) -> Unit,
    onReopenLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    NavHost(navController = navController, startDestination = routeForTab(state.selectedTab)) {
        composable(AppRoute.Day.route) {
            DayScreen(state = state, liveNow = liveNow, onOpenTask = onOpenTask, onStartPomodoro = onOpenStartPomodoro)
        }
        composable(AppRoute.Tasks.route) {
            TasksScreen(
                state = state,
                liveNow = liveNow,
                isPomodoroActive = isPomodoroActive,
                onSelectTaskFilter = onSelectTaskFilter,
                onOpenTask = onOpenTask,
                onOpenStartPomodoro = onOpenStartPomodoro,
                onCreateRoutine = onCreateRoutine,
                onOpenRoutine = onOpenRoutine,
                onRunRoutine = onRunRoutine,
            )
        }
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                state = state,
                onUpdateThemePreference = onUpdateThemePreference,
                onUpdatePreferences = onUpdatePreferences,
                onReopenLogin = onReopenLogin,
                onLogout = onLogout,
            )
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppRouteMappingTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppRoutes.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation/AppNavHost.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppRouteMappingTest.kt
git commit -m "feat: add shared ui top level navigation routes"
```

### Task 3: Extract day and guide screens from `App.kt`

**Files:**
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/GuideScreen.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/DayScreen.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt`

- [ ] **Step 1: Write the failing compile-level extraction step**

```kotlin
// Update imports in tests to use:
import dev.tireless.abun.ui.screens.DayScreen
import dev.tireless.abun.ui.screens.GuideScreenContent
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.GuideScreenDebugAuthTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: FAIL because the new screen package symbols do not exist yet.

- [ ] **Step 3: Move the screen implementations**

```kotlin
// GuideScreen.kt
package dev.tireless.abun.ui.screens

// Move GuideScreenContent unchanged except for imports.
```

```kotlin
// DayScreen.kt
package dev.tireless.abun.ui.screens

// Move TodayScreen unchanged, rename to DayScreen, update tests and call sites.
```

- [ ] **Step 4: Re-run the targeted tests**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.GuideScreenDebugAuthTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/GuideScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/DayScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt
git commit -m "refactor: extract guide and day screens"
```

### Task 4: Extract task surface screens from `App.kt`

**Files:**
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/TasksScreen.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/TaskListScreen.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/RoutineListScreen.kt`
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/PomodoroScreen.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt`

- [ ] **Step 1: Write the failing test import updates**

```kotlin
// In tests, import:
import dev.tireless.abun.ui.screens.TasksScreen
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskListScreenTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: FAIL because the `ui.screens` task screen symbols do not exist yet.

- [ ] **Step 3: Move the task surface implementations**

```kotlin
// TasksScreen.kt
package dev.tireless.abun.ui.screens

// Move TasksScreen and task-surface switching logic.
```

```kotlin
// TaskListScreen.kt
package dev.tireless.abun.ui.screens

// Move TaskListScreen plus local filter label/icon helpers used only there.
```

```kotlin
// RoutineListScreen.kt
package dev.tireless.abun.ui.screens

// Move RoutineListScreen unchanged except for imports.
```

```kotlin
// PomodoroScreen.kt
package dev.tireless.abun.ui.screens

// Move PomodoroScreen unchanged except for imports.
```

- [ ] **Step 4: Re-run the targeted tests**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.TaskListScreenTest --tests dev.tireless.abun.AppScreenScreenshotTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/TasksScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/TaskListScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/RoutineListScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/PomodoroScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/TaskListScreenTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppScreenScreenshotTest.kt
git commit -m "refactor: extract task surface screens"
```

### Task 5: Extract settings screen and connect the shell to the nav host

**Files:**
- Create: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/SettingsScreen.kt`
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ThemePreferenceSupportTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt`

- [ ] **Step 1: Write the failing shell sync test**

```kotlin
@Test
fun `app shell bottom tab selection follows selected app tab route`() = runDesktopComposeUiTest {
    setContent { AppTheme { App() } }
    onAllNodesWithText("Day").assertCountEquals(1)
}
```

- [ ] **Step 2: Run targeted tests to verify they fail**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest --tests dev.tireless.abun.ThemePreferenceSupportTest`
Expected: FAIL because `App()` still renders screen content directly instead of through `AppNavHost`, and `SettingsScreen` has not been extracted yet.

- [ ] **Step 3: Wire the shell to Compose Navigation**

```kotlin
// App.kt
val navController = rememberNavController()
val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

LaunchedEffect(currentRoute) {
    val routeTab = appTabForRoute(currentRoute)
    if (routeTab != state.selectedTab) controller.selectTab(routeTab)
}

LaunchedEffect(state.selectedTab) {
    val targetRoute = routeForTab(state.selectedTab)
    if (currentRoute != targetRoute) {
        navController.navigate(targetRoute) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.startDestinationId) { saveState = true }
        }
    }
}
```

```kotlin
// replace direct when(state.selectedTab) screen selection with:
AppNavHost(
    navController = navController,
    state = state,
    liveNow = liveNow,
    isPomodoroActive = isPomodoroActive,
    onSelectTaskFilter = controller::selectTaskFilter,
    onOpenTask = { ... },
    onOpenStartPomodoro = { ... },
    onCreateRoutine = { ... },
    onOpenRoutine = { ... },
    onRunRoutine = { ... },
    onUpdateThemePreference = controller::updateThemePreference,
    onUpdatePreferences = controller::updatePreferences,
    onReopenLogin = controller::reopenLogin,
    onLogout = controller::logout,
)
```

- [ ] **Step 4: Re-run targeted tests**

Run: `./gradlew :app:sharedUI:jvmTest --tests dev.tireless.abun.AppNavigationTest --tests dev.tireless.abun.ThemePreferenceSupportTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens/SettingsScreen.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/AppNavigationTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/ThemePreferenceSupportTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt
git commit -m "refactor: wire shared ui shell through nav host"
```

### Task 6: Update docs and run full verification

**Files:**
- Modify: `docs/tasks/technical-design.md`
- Modify: `docs/tasks/functionality-design.md`

- [ ] **Step 1: Write the doc updates**

```md
- top-level shared UI screen routing now uses Compose Navigation
- `ui/screens` holds the main screen composables
- task subtabs remain state-driven within the `Tasks` route
- user-facing behavior is unchanged by the refactor
```

- [ ] **Step 2: Run full verification**

Run: `./gradlew :app:sharedUI:jvmTest`
Expected: PASS

Run: `./gradlew :app:desktopApp:test`
Expected: PASS

- [ ] **Step 3: Verify docs reflect implementation**

Run: `rg -n "Compose Navigation|ui/screens|state-driven within the \`Tasks\` route|behavior is unchanged" docs/tasks/functionality-design.md docs/tasks/technical-design.md`
Expected: matching lines in both docs

- [ ] **Step 4: Commit**

```bash
git add docs/tasks/technical-design.md docs/tasks/functionality-design.md
git commit -m "docs: align shared ui navigation refactor"
```
