# Task Detail Sheet Polish Design

## Purpose

Refine task cards and the task detail sheet so the `Tasks` surface feels lighter, more readable, and more deliberate while preserving the existing task action model.

## Current State

- task cards in the shared task list use a muted status tag plus a prominent `Manage` button
- opening a task launches a modal sheet that immediately exposes editable fields
- the detail sheet uses a large title at the top
- task deletion is a single-tap action without an explicit confirmation dialog in the shared UI layer
- edit and action controls are mixed together in one always-editable presentation

## Decision

Keep a single task detail sheet, but split it into two in-place modes:

- default mode: read-only detail view
- edit mode: editable form in the same sheet after tapping a top-right edit icon

At the same time, simplify the list card presentation:

- replace the shared status tag with a small colored state indicator
- remove the large `Manage` button
- replace it with a lightweight `Click to open detail` affordance

The task detail sheet should:

- occupy about 80% of the available screen height
- omit the large title header
- show an edit icon in the top-right corner while in read-only mode
- show `Cancel`, `Save`, and a danger `Delete` action while in edit mode
- require confirmation before delete is executed

## Why This Approach

- it reduces the visual weight of routine task browsing without removing discoverability
- it makes read-only viewing the default, which better matches the user intent when opening a detail surface from a list
- it preserves the existing modal entry point and action model instead of introducing a second sheet or route
- it keeps edit affordances explicit and scoped, which is especially helpful on smaller Android layouts

## Interaction Design

### Task Card

- each task row remains a flat outlined card
- the row still shows the task title and routine metadata
- task status is represented by a compact colored circle paired with the status label text
- the former large `Manage` button is removed
- a lighter secondary text affordance communicates that the user can open the detail surface
- clicking the affordance opens the task detail sheet through the existing task-open callback

### Read-Only Detail Sheet

- the sheet opens in read-only mode
- there is no large title at the top of the sheet
- the first row contains lightweight context information and an edit icon button aligned to the top-right
- task fields render as read-only values instead of enabled inputs
- task history remains visible
- progress-oriented task actions such as `Progress`, `Complete`, `Skip`, `Postpone`, and `Pomodoro` remain available in read-only mode when currently valid
- destructive record editing actions are not shown in read-only mode

### In-Place Edit Mode

- tapping the edit icon switches the same sheet into edit mode without dismissing it
- editable fields match the current task-edit contract for normal tasks
- routine-derived task records remain non-editable for fields that are already locked by the current business rules
- the top-right edit icon is replaced by edit-mode actions instead of opening a second modal
- edit mode surfaces:
  - `Cancel` to discard unsaved changes and return to read-only mode
  - `Save` to persist the edited task draft through the existing save callback
  - a danger `Delete` action to remove the task

### Delete Confirmation

- pressing `Delete` does not immediately call the delete callback
- a confirmation dialog appears first
- confirming the dialog executes the existing delete flow
- dismissing the dialog returns the user to edit mode with no data loss

## Layout and Sizing

- the task detail sheet should use an 80% max-height treatment so it reads as a structured workspace rather than an unconstrained long form
- overflowing content should remain scrollable within the sheet body
- the removal of the oversized title should reduce top-heavy spacing and let task content start earlier

## Architecture Notes

- the sheet remains implemented in the shared Compose UI layer
- no domain-model or sync-contract change is required
- the task action callback surface can stay intact; the implementation only reorganizes when edit and delete actions appear
- the delete confirmation is a presentation-layer safeguard and does not change persistence semantics

## Testing

This change should add or update focused shared UI tests covering:

- the task-row presentation change from status tag plus `Manage` button to state dot plus `Click to open detail`
- task detail defaulting to read-only mode
- toggling into in-place edit mode from the top-right edit affordance
- edit mode showing `Cancel`, `Save`, and `Delete`
- delete requiring confirmation before the delete callback executes

Validation commands:

- `./gradlew :app:sharedUI:jvmTest`
- `./gradlew :app:desktopApp:test`
- `./gradlew :core:jvmTest`
- `./gradlew :app:desktopApp:run`

## Documentation Updates

Update the following docs during implementation:

- `docs/tasks/functionality-design.md`
  - note that task rows open a read-only detail sheet first and switch into edit mode explicitly
- `docs/tasks/technical-design.md`
  - note that shared task detail presentation is now mode-based within a single sheet
- relevant shared UI notes if the task-row status treatment or lightweight inline affordance becomes part of the broader shared presentation language

## Risks

- reducing the button prominence may slightly lower first-time discoverability if the affordance is too subtle
- mixing read-only task actions with edit-mode actions in one sheet can become confusing if the mode transition is not visually clear

These risks are acceptable if the affordance copy stays explicit and the edit-state chrome changes clearly when editing begins.
