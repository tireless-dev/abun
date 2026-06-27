# Navigation Compose Sheet Routing Design

## Purpose

Unify shared UI navigation so both screen-level routes and bottom-sheet overlays use Navigation Compose.

This change removes the current split between:

- top-level screen navigation handled by `NavHost`
- sheet visibility handled by local mutable state in `App.kt`

After this change, all shared UI navigation should use the same Navigation Compose system, including future sheet-based flows.

## Current State

- `App.kt` uses `rememberNavController()` and `AppNavHost(...)` for top-level `Day`, `Tasks`, and `Settings` routes
- the app still manages overlay sheets through local state:
  - `currentSheet`
  - `selectedTask`
  - `selectedRoutine`
  - `createTaskContext`
- task detail, routine detail, task creation, routine creation, and pomodoro sheets are opened by mutating local state instead of navigating
- this creates two separate navigation systems in the same shared UI layer

## Decision

Move all current overlay sheets onto Navigation Compose in the same shared graph.

The graph should cover:

- screen routes:
  - `day`
  - `tasks`
  - `settings`
- sheet routes:
  - `create-task`
  - `create-routine`
  - `task-detail/{taskId}`
  - `routine-detail/{routineId}`
  - `start-pomodoro`
  - `complete-pomodoro`

Bottom sheets should remain visually and behaviorally bottom sheets.

Navigation Compose becomes the source of truth for:

- which screen is active
- which sheet is open
- which entity-scoped sheet is targeted

Local composable state may still handle transient sheet-internal UI state such as:

- edit mode
- delete confirmation
- temporary text input

## Why This Approach

- it gives the shared UI one navigation system instead of two
- it matches the explicit requirement that bottom-sheet navigation also use Jetpack Navigation
- it makes future sheet work follow an already-set pattern instead of introducing more local overlay state
- it avoids a larger full-app route rewrite because the existing top-level `NavHost` can be extended rather than replaced

## Route Design

### Screen Routes

Keep the current top-level screen routes:

- `day`
- `tasks`
- `settings`

The bottom tab bar continues to navigate to these routes through `NavController`.

### Sheet Routes

Add sheet destinations for:

- `create-task`
- `create-routine`
- `task-detail/{taskId}`
- `routine-detail/{routineId}`
- `start-pomodoro`
- `complete-pomodoro`

Entity-based sheets should use stable IDs in the route:

- task detail uses `taskId`
- routine detail uses `routineId`

Do not pass full task or routine objects through navigation arguments.

## Data Resolution

### Task Detail Route

For `task-detail/{taskId}`:

- resolve the task from `state.taskView.tasks`
- resolve task history from the controller using that ID
- derive available parent candidates from current task state
- derive routine metadata from `state.taskView.routines`

If the task cannot be found:

- pop the sheet route from the back stack
- do not keep a broken or empty detail sheet open

### Routine Detail Route

For `routine-detail/{routineId}`:

- resolve the routine from `state.taskView.routines`

If the routine cannot be found:

- pop the sheet route from the back stack

### Create and Pomodoro Routes

For non-entity sheet routes:

- `create-task` derives create context from current selected top-level state
- `create-routine` does not require route arguments
- `start-pomodoro` derives available open tasks from current state
- `complete-pomodoro` derives the active session from current state

## Sheet Presentation

Each sheet destination should render as a bottom-anchored modal sheet rather than a full screen.

Shared behavior:

- open by navigating to the sheet route
- dismiss by popping the nav back stack
- preserve the current product behavior of each sheet
- keep task detail read-only first, then in-place edit mode
- keep delete confirmation local to the task detail sheet

Desktop-specific note:

- the sheet host should stay bottom-anchored
- the visible sheet should keep the current 80% height treatment for task detail

## App Structure Changes

### `App.kt`

Remove the local overlay-router state from `App.kt`:

- `OverlaySheet`
- `currentSheet`
- `selectedTask`
- `selectedRoutine`
- direct `when (currentSheet)` rendering

Replace those flows with navigation calls such as:

- open task detail: navigate to `task-detail/<id>`
- open routine detail: navigate to `routine-detail/<id>`
- open create task: navigate to `create-task`
- open create routine: navigate to `create-routine`
- open start pomodoro: navigate to `start-pomodoro`
- open complete pomodoro: navigate to `complete-pomodoro`

### `AppNavHost.kt`

Expand `AppNavHost(...)` to include both screen destinations and sheet destinations.

The graph should:

- keep current screen destinations
- add route argument parsing for task and routine detail routes
- resolve route IDs into state-backed view objects
- call `navController.popBackStack()` on dismiss and successful completion

## Future Navigation Rule

After this migration, shared UI navigation should follow this rule:

- if a user-visible surface opens and closes like a navigable destination, it should use Navigation Compose

This includes future bottom sheets unless there is a compelling technical reason to do otherwise.

Local mutable UI state should be reserved for transient internal behavior, not destination visibility.

## Error Handling

- invalid task ID route: pop back immediately
- invalid routine ID route: pop back immediately
- complete pomodoro route without an active session: pop back immediately
- duplicate navigation triggers should not stack repeated copies of the same sheet route unnecessarily

## Testing

Add or update shared UI tests to cover:

- opening task detail through navigation instead of local overlay state
- opening routine detail through navigation instead of local overlay state
- create and pomodoro sheets as nav-driven destinations
- dismissing a sheet pops the back stack
- invalid sheet IDs pop safely
- task detail still opens read-only, enters edit mode in place, confirms delete, and remains bottom-anchored

Validation commands:

- `./gradlew :app:sharedUI:jvmTest`
- `./gradlew :app:desktopApp:test`
- `./gradlew :core:jvmTest`
- `./gradlew :app:desktopApp:run`

## Documentation Updates

Update these docs during implementation:

- `docs/tasks/technical-design.md`
  - note that task detail sheet visibility is now navigation-driven
- `docs/tasks/functionality-design.md`
  - note that opening task detail is now a navigated bottom-sheet destination
- relevant shared architecture notes if shared navigation boundaries change materially

## Risks

- migrating every sheet at once increases the implementation surface
- route/data resolution mistakes could cause sheets to pop unexpectedly if ID handling is inconsistent
- mixing screen and sheet destinations in one graph needs clean route naming to avoid future confusion

These risks are acceptable because the long-term navigation model becomes simpler and more consistent.
