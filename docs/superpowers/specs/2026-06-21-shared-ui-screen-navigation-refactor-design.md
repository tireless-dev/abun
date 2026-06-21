# Shared UI Screen Navigation Refactor Design

## Goal

Reduce the size and responsibility load of `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt` by moving screen composables into a dedicated `ui/screens` package and introducing Jetpack Compose Navigation for top-level app routing, while keeping the existing task subtab model state-driven.

## Scope

This refactor covers:

- top-level shared UI screen extraction from `App.kt`
- top-level Compose Navigation setup for `Day`, `Tasks`, and `Settings`
- retention of the current `selectedTaskSubTab` state model for `Tasks`, `Routines`, and `Pomodoro`
- documentation updates for the new UI structure

This refactor does not cover:

- moving overlay sheets into navigation destinations
- changing task subtab behavior to nested navigation
- changing shared logic or persistence contracts
- redesigning the visual UI

## Problem

`App.kt` currently mixes several responsibilities:

- app shell composition
- top-level screen selection
- multiple full-screen composables
- modal/sheet orchestration
- screen-local layout details

That makes the file harder to reason about and raises the cost of future changes. The current top-level routing is driven by `AppUiState.selectedTab`, but there is no explicit screen navigation layer in shared UI. At the same time, task subtabs already work well as local state within the `Tasks` surface and do not need a full nested navigation migration yet.

## Decision

Use a hybrid navigation structure:

- top-level app surfaces use Jetpack Compose Navigation
- task subtabs remain driven by `AppUiState.selectedTaskSubTab`

This keeps the structural benefits of route-based navigation for the main app shell without expanding scope into nested task navigation.

## Options Considered

### Option 1: Top-level NavHost with state-backed task subtabs

Use Compose Navigation only for `Day`, `Tasks`, and `Settings`, while preserving the current state-driven task subtab model.

Pros:

- materially reduces `App.kt` responsibility
- introduces route boundaries where they help most
- keeps existing task surface behavior intact
- minimizes controller and test churn

Cons:

- temporarily mixes route-based top-level navigation with state-based subtabs
- requires a small sync layer between nav state and `AppUiState.selectedTab`

### Option 2: Full nested navigation

Use top-level navigation plus nested navigation inside `Tasks`.

Pros:

- more uniform navigation architecture
- cleaner long-term route model

Cons:

- significantly larger migration
- higher regression risk
- more changes to controller assumptions and tests

### Option 3: File extraction only

Move screens out of `App.kt` but keep all navigation state-driven.

Pros:

- lowest implementation risk
- simplest short-term change

Cons:

- does not satisfy the requirement to keep navigation using Jetpack Compose Navigation
- misses the chance to establish top-level route boundaries

## Selected Approach

Implement Option 1.

## Architecture

### App shell responsibilities

`App()` remains the composition root and keeps only cross-screen responsibilities:

- controller creation and state collection
- theme application
- app-level effects such as pomodoro completion handling
- top app bar
- bottom app bar
- floating action button
- overlay sheet orchestration
- navigation/controller state bridging

### Screen extraction

Move the current screen composables into `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/screens`.

Planned files:

- `DayScreen.kt`
- `TasksScreen.kt`
- `TaskListScreen.kt`
- `RoutineListScreen.kt`
- `PomodoroScreen.kt`
- `SettingsScreen.kt`

These files should contain screen-level composables only, not global shell orchestration.

### Navigation layer

Add a small navigation package, likely under:

- `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/ui/navigation`

Planned components:

- top-level route model
- `AppNavHost`
- route-to-`AppTab` mapping helpers

The navigation layer owns only the main app surfaces:

- `Day`
- `Tasks`
- `Settings`

### State synchronization

Top-level navigation and shared state must stay aligned.

Rules:

- bottom tab presses should navigate through `NavController`
- route changes should reflect the intended `AppTab`
- if controller state changes the selected tab programmatically, navigation should follow
- the sync must avoid infinite route/state update loops

Practical approach:

- derive current route from `NavController`
- map routes to `AppTab`
- use a guarded `LaunchedEffect` or equivalent sync logic so that:
  - route changes update controller tab state when needed
  - controller tab changes navigate when needed

### Task surface boundaries

The `Tasks` route still hosts the current state-driven subtab model:

- `Tasks`
- `Routines`
- `Pomodoro`

The existing top bar subtab selector remains unchanged in behavior and continues to use:

- `state.selectedTaskSubTab`
- `controller.selectTaskSubTab(...)`

### Overlay sheets

Overlay sheets remain app-shell concerns in this refactor.

They continue to be opened from shared shell state and receive callbacks into the controller. This avoids coupling the initial navigation refactor to modal routing.

## File Responsibilities

### `App.kt`

After the refactor, `App.kt` should contain:

- `App()`
- app-shell helper composables that are truly cross-screen
- overlay sheet routing/state
- shared utility functions still used across many screens, if not worth moving yet

It should no longer hold the main screen implementations.

### `ui/screens/*`

Each screen file should own one screen or tightly related screen cluster.

Examples:

- `DayScreen.kt`: the day workspace content
- `TasksScreen.kt`: task-surface switching across task subtabs
- `TaskListScreen.kt`: task-instance filter bar and task list
- `RoutineListScreen.kt`: routines management list
- `PomodoroScreen.kt`: current timer and recent sessions
- `SettingsScreen.kt`: settings content and controller-bound wrapper if still needed

### `ui/navigation/*`

This package should hold:

- route declarations
- navigation host setup
- helpers for translating between routes and `AppTab`

## Testing

Follow TDD.

Expected testing strategy:

- add a failing shared UI test that proves top-level navigation behavior still renders the correct screen shell
- keep existing screen tests passing after extraction
- add or update tests only where behavior changes or where the new structure needs protection

Primary verification:

- `./gradlew :app:sharedUI:jvmTest`

If navigation dependencies or shared UI structure affect compilation broadly, also run:

- `./gradlew :app:desktopApp:test`

## Documentation Updates

Update the relevant technical documentation to reflect:

- top-level shared UI now uses Compose Navigation
- task subtabs remain state-driven within the `Tasks` route
- screen composables live under `ui/screens`

Product functionality docs should only be updated if the user-facing behavior changes. Since this refactor is intended to preserve behavior, documentation changes there should be minimal and explicitly state that behavior is unchanged if touched at all.

## Risks

### Route/state drift

If nav route and `AppUiState.selectedTab` get out of sync, the app can render inconsistent chrome or route loops.

Mitigation:

- keep route mapping simple
- use guarded synchronization logic
- cover expected transitions with UI tests

### Over-scoping into nested navigation

Trying to migrate task subtabs into navigation at the same time would increase risk substantially.

Mitigation:

- keep task subtabs state-driven in this refactor

### Screen extraction churn

Moving large composables can cause import churn and helper-function breakage.

Mitigation:

- move screen-by-screen
- keep shared helpers in place initially
- avoid unrelated cleanup in the same change

## Success Criteria

The refactor is successful when:

- top-level app routing is handled through Compose Navigation
- `App.kt` no longer contains the main screen implementations
- screen composables live in `ui/screens`
- task subtabs still behave exactly as before
- shared UI tests pass
- Desktop and Android shared UI code paths remain supported
