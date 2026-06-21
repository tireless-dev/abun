# Shared UI Large Cleanup Design

## Goal

Reduce `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt` to app-shell responsibilities only by moving remaining screen content, reusable UI pieces, and modal sheet flows into dedicated packages without changing user-facing behavior.

## Scope

This cleanup covers:

- moving `SettingsScreenContent` out of `App.kt`
- moving reusable presentation pieces into a dedicated `ui/components` package
- moving modal sheet composables into a dedicated `ui/sheets` package
- moving sheet-local helper and normalization code out of `App.kt` where practical
- updating tests and docs to match the new structure

This cleanup does not cover:

- changing shared logic, sync, or persistence behavior
- changing top-level navigation structure
- changing task subtab behavior
- redesigning visuals or workflows

## Problem

After the top-level navigation refactor, `App.kt` still mixes several layers of responsibility:

- app shell composition
- settings-screen content
- shared presentation components
- multiple modal sheet flows
- task/routine create/edit helper logic

The file is smaller than before, but it still concentrates too much unrelated UI behavior in one place. That makes future changes harder to isolate and keeps import and helper churn higher than necessary.

## Options Considered

### Option 1: Split by responsibility

Use separate packages for:

- `ui/screens`
- `ui/components`
- `ui/sheets`

Pros:

- cleanest separation of concerns
- keeps `App.kt` focused on shell orchestration
- improves discoverability
- lowers future edit scope per file

Cons:

- moderate file-move churn
- requires careful helper placement decisions

### Option 2: Split by feature area

Group UI by domain buckets such as tasks, pomodoro, settings, and auth.

Pros:

- strong domain grouping
- good long-term fit if feature folders expand

Cons:

- larger structural change immediately after the nav refactor
- harder to scope as a cleanup-only pass

### Option 3: Minimal file-count cleanup

Create only a few broad files such as `Sheets.kt` and `ScreenHelpers.kt`.

Pros:

- quickest move set
- smallest short-term diff

Cons:

- risks recreating the same concentration problem in new files
- weaker boundaries

## Selected Approach

Implement Option 1.

## Architecture

### `App.kt`

After cleanup, `App.kt` should keep only shell-level responsibilities:

- controller creation and state collection
- theme application
- top-level nav sync
- scaffold chrome
- FAB logic
- sheet selection/orchestration state
- sheet dispatch based on selected overlay
- app-level effects such as pomodoro completion routing

It should no longer contain:

- top-level screen content implementations
- reusable row/panel primitives
- sheet form bodies and action flows
- draft normalization helpers that belong to the sheet layer

### `ui/screens`

This package remains the home for top-level screen entry composables.

Required cleanup in this pass:

- move `SettingsScreenContent` into the settings screen file
- keep the screen file focused on settings UI rather than app-shell concerns

### `ui/components`

This package should hold reusable shared presentation pieces used by multiple screens or sheets.

Expected extracted components:

- `Panel`
- `SectionHeader`
- `MetricRow`
- `TaskStack`
- `JournalTimeline`
- `TaskRow`
- `StatusPill`
- `RoutineRow`
- `PomodoroRow`

Component placement rule:

- if the composable is generic UI structure or repeated display content, it belongs here
- if it is tightly tied to a single screen or sheet, it should stay near that screen or sheet instead

### `ui/sheets`

This package should hold modal sheet composables and closely related support code.

Expected extracted sheets:

- `CreateTaskSheet`
- `CreateRoutineSheet`
- `TaskActionsSheet`
- `RoutineActionsSheet`
- `StartPomodoroSheet`
- `CompletePomodoroSheet`

Expected support extraction:

- task create draft normalization helpers
- recurrence editor support helpers if they are only used by routine sheets
- label/selection helpers that are sheet-only

Support placement rule:

- if logic only exists to support sheet UI, it should move with sheets
- if logic is genuinely shared across screens and sheets, it may live in a narrowly scoped support file rather than `App.kt`

## File Responsibilities

### `ui/components/*`

Each file should hold a coherent set of reusable presentation pieces.

Examples:

- `SurfaceSections.kt` for `Panel`, `SectionHeader`, `MetricRow`
- `TaskRows.kt` for `TaskStack`, `TaskRow`, `StatusPill`
- `Timeline.kt` for `JournalTimeline`
- `RoutineRow.kt`
- `PomodoroRow.kt`

The exact split can stay pragmatic, but no file should become a new monolith.

### `ui/sheets/*`

Each sheet can live in its own file or share a tight feature file when the responsibilities are obviously paired.

Reasonable pairings:

- `CreateTaskSheet.kt`
- `CreateRoutineSheet.kt`
- `TaskActionsSheet.kt`
- `RoutineActionsSheet.kt`
- `PomodoroSheets.kt` for start/complete if that stays focused and small

### Helper files

If helper functions remain necessary, they should be moved into focused support files such as:

- `ui/sheets/TaskSheetSupport.kt`
- `ui/sheets/RoutineSheetSupport.kt`

Avoid generic catch-all names unless the file remains truly small and cohesive.

## Testing Strategy

Follow TDD.

Suggested execution order:

1. update tests/imports to target the extracted settings screen and component/sheet package paths
2. run targeted tests and confirm compile or behavior failures
3. move the minimal code to satisfy those tests
4. rerun targeted tests
5. finish with full verification

Primary verification commands:

- `./gradlew :app:sharedUI:jvmTest`
- `./gradlew :app:desktopApp:test`

Existing screenshot and Compose UI tests should keep working after import updates. No new integration tests are required.

## Documentation Updates

Update technical docs to reflect:

- `App.kt` is now shell-only
- `ui/components` owns reusable shared presentation pieces
- `ui/sheets` owns modal sheet flows
- `ui/screens` owns top-level screen entry composables

Product functionality docs should only receive a brief implementation note that the cleanup preserved behavior if any mention is needed.

## Risks

### Helper misplacement

If helpers move into the wrong package, new cross-package dependencies can become noisy or circular.

Mitigation:

- use clear placement rules
- keep helpers close to the layer that owns them
- avoid moving domain logic into generic UI component packages

### New mini-monolith files

Moving everything from `App.kt` into one or two new files would not actually solve the maintainability problem.

Mitigation:

- split by responsibility
- keep files cohesive and moderate in size

### Broad import churn

This cleanup will touch many imports and test references.

Mitigation:

- move in small batches
- run targeted tests after each batch

## Success Criteria

The cleanup is successful when:

- `App.kt` is focused on shell orchestration only
- `SettingsScreenContent` no longer lives in `App.kt`
- reusable presentation primitives live under `ui/components`
- modal sheets live under `ui/sheets`
- shared UI and desktop tests pass
- user-facing behavior is unchanged
