# Tasks Top Bar Subtab Selector Design

## Purpose

Move the Tasks screen sub-navigation from the in-body segmented control into the scaffold title bar and implement it with a reusable shared dropdown trigger component.

## Current State

- The Tasks screen currently renders `TaskSubTab` selection inside `TasksScreen(...)` using a `SingleChoiceSegmentedButtonRow`.
- The shared scaffold top app bar currently renders a simple title string based on the selected top-level `AppTab`.
- This means the Tasks sub-view state is controlled inside the body instead of in the app chrome, even though it changes the entire visible surface between Tasks, Routines, and Pomodoro.

## Decision

Adopt a small reusable shared top-bar dropdown trigger component and use it in the `TopAppBar` when `AppTab.TASKS` is selected.

The Tasks title bar layout becomes:

- static main title: `Tasks`
- expanding spacer
- current sub-tab selector trigger with icon, label, and chevron

The selector options are:

- `Tasks` with Lucide `list-todo`
- `Routines` with Lucide `calendar-check`
- `Pomodoro` with Lucide `timer`

Chevron behavior:

- collapsed: Lucide `chevron-left`
- expanded: Lucide `chevron-down`

## Why This Approach

- it keeps the Tasks sub-view switch close to the app chrome where it better matches the scope of the view change
- it removes duplicated navigation by replacing the in-body segmented control rather than mirroring it
- it introduces a narrowly scoped shared UI primitive that can be reused for future top-bar selectors without creating broad wrappers around Material 3
- it stays aligned with the editorial shared UI direction: compact, information-focused, and low-decoration

## Interaction Design

### Top App Bar Behavior

- For `AppTab.TASKS`, the title area shows `Tasks` on the left and the selector trigger on the right.
- The trigger always shows the currently selected `TaskSubTab` label and icon.
- Tapping the trigger opens a dropdown menu anchored to the trigger.
- Selecting a menu item updates `state.selectedTaskSubTab` through the existing `controller.selectTaskSubTab(...)` path.
- The dropdown closes after selection.

### Non-Tasks Tabs

- `Today` and `Settings` keep their current top app bar title behavior with no selector.

### Body Behavior

- Remove the in-body `SingleChoiceSegmentedButtonRow` for `TaskSubTab` selection from `TasksScreen(...)`.
- The body should render only the currently selected sub-surface.

## Shared Component Shape

Create a small shared component in `app/sharedUI` for compact top-bar dropdown selection.

Responsibilities:

- render the active icon, active label, and current chevron state
- manage or accept expanded state
- render dropdown items with icon + label
- invoke `onSelect(...)` when an item is chosen

Constraints:

- use Material 3 menu primitives directly
- use shared theme tokens for spacing, typography, and colors
- avoid decorative fills, pills, or feature-local styling
- keep the API narrow and focused on compact top-bar selection

## Architecture Notes

- Shared iconography should come from Lucide in `commonMain`.
- The new selector should live in `app/sharedUI` rather than remain embedded in `App.kt`, but it should stay lightweight rather than becoming a general-purpose design-system wrapper layer.
- Existing `TaskSubTab` state and controller methods remain the source of truth; this change is presentational and compositional, not a state-model redesign.

## Testing

This change should follow TDD.

Planned failing tests:

- a shared UI test proving the old segmented `TaskSubTab` selector is removed from `TasksScreen(...)`
- a shared UI test proving the top app bar source now contains the Tasks selector component and Lucide icons for the three sub-tabs plus the chevrons
- screenshot test updates for the scaffold and tasks surfaces affected by the new title bar

Validation commands:

- `./gradlew :app:sharedUI:jvmTest`
- `./gradlew :app:desktopApp:test`
- `./gradlew :core:jvmTest`
- `./gradlew :app:desktopApp:run`

## Documentation Updates

Update the following docs as part of implementation:

- `docs/base/shared-ui-design-system.md`
  - allow compact top-app-bar dropdown selectors as a shared navigation pattern for sub-view switching
- `docs/base/technical-architecture.md`
  - note that shared top app bar chrome may host lightweight shared selectors for view-level sub-navigation when the switched surface is screen-scoped
- relevant product-facing docs if the Tasks module documentation needs to reflect that Tasks, Routines, and Pomodoro switching now lives in the title bar rather than the screen body

## Risks

- moving the selector into the top bar slightly reduces the immediate visual prominence of alternate sub-views compared with the segmented control
- a reusable selector component can become too generic if expanded beyond this narrow use

These risks are acceptable if the component stays compact and the active label/icon remain visible at all times.
