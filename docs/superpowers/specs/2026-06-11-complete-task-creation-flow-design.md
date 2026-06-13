# Complete Task Creation Flow Design

## Purpose

Define a complete quick-create flow for normal task creation in Abun that is fast to use, context-aware, and aligned with the shared task model.

This feature is not about multi-step task setup. It is about making the existing single-sheet creation flow complete enough for daily use while preserving the "capture quickly, refine later" product direction.

## Goals

- Keep task creation as a single-sheet quick-capture flow.
- Replace raw timestamp and duration text entry with human-friendly scheduling controls.
- Make creation behavior depend on the surface where create was opened.
- Keep backlog creation easy.
- Preserve the existing shared task write contract and event ledger model.
- Support Desktop and Android through shared-first implementation.

## Non-Goals

- Multi-step guided task creation.
- Parent selection during creation.
- Routine creation or routine-derived task creation changes.
- A web-specific creation flow.
- Replacing task-detail editing; post-create refinement still belongs in task detail.

## User-Facing Behavior

### Sheet scope

The create flow remains a single sheet for normal tasks.

Fields in v1:

- `title`
- `detail`
- scheduling controls

Fields intentionally excluded from creation:

- `parent task`
- direct `end_not_after` editing for the default path, unless introduced as part of the human-friendly scheduling controls

### Completion behavior

After successful creation:

- the task is created immediately in local state
- the sheet closes
- the user returns to the current surface
- the flow does not automatically open task detail

### Creation contexts

The flow behaves differently depending on where it was opened.

#### Tasks surface

Default:

- create starts as backlog
- no scheduling is prefilled

#### Day surface

Default:

- create starts as scheduled
- `start_not_before` is prefilled to the selected date
- this means "available starting on this day," not "due on this day"

### Clearing schedule

If the user removes scheduling before tapping Create:

- the task is created as backlog
- this is true even when the sheet was opened from the Day surface

## Scheduling UX

Scheduling must be human-first rather than transport-format-first.

The user should not type raw ISO 8601 timestamps or durations into normal quick-create controls.

The v1 scheduling UX should include:

- a simple affordance to add, change, or clear the scheduled start date
- a date-oriented control for the start date
- optional time selection only if it fits cleanly into existing app patterns
- a simple duration control using presets and/or a lightweight custom duration path

The persisted domain output still maps onto the existing task planning fields:

- `start_not_before`
- `end_not_after`
- `estimated_duration`

The quick-create flow may omit some planning fields from direct editing while still producing valid task records. In particular, the first version does not need to expose the full planning model as raw fields.

## Domain And State Rules

### Context-aware defaults

The create sheet must receive creation context from the caller.

Required context:

- source surface: `Tasks` or `Day`
- selected date when launched from `Day`

### Normalized output

The UI layer should convert user-friendly draft state into the existing task creation contract used by shared logic.

At minimum, the normalized draft must produce:

- `title`
- `detail`
- `start_not_before`
- `end_not_after`
- `estimated_duration`

Backlog creation is represented by leaving planning fields unset.

### Ledger behavior

The feature does not change the logical transaction for normal task creation:

1. insert task
2. insert `CREATED` event
3. mark both records dirty for sync

The feature changes creation defaults and UI ergonomics, not the underlying event model.

## Architecture Approach

### Shared-first implementation

Implementation should remain shared-first:

- shared UI for the create sheet and its state handling
- shared normalization logic for converting quick-create draft state into task creation inputs
- no web-app dependency

### Boundaries

The implementation should separate:

- creation-context defaults
- scheduling draft state and normalization
- visual controls used by the sheet

This keeps the task-creation behavior testable without relying only on end-to-end UI interaction.

## Documentation Impact

Before implementation starts, this feature is specified in this design doc.

When implementation is complete, the following docs should be updated to match actual behavior:

- [docs/tasks/functionality-design.md](/Users/jerry/Workspace/_tools/abun/docs/tasks/functionality-design.md)
- [docs/tasks/technical-design.md](/Users/jerry/Workspace/_tools/abun/docs/tasks/technical-design.md)

Expected doc changes after implementation:

- quick-create fields and defaults
- Day-vs-Tasks creation-context behavior
- scheduling-clear-to-backlog rule
- any shared normalization rules that become part of the task-creation contract

## Testing Strategy

Follow TDD for each implementation slice.

Required test coverage themes:

- creation-context defaults for `Tasks` and `Day`
- clearing schedule produces backlog output
- normalized draft output maps to the expected planning fields
- existing local task creation behavior still persists the task row and `CREATED` event correctly

Likely test surfaces:

- shared logic/common tests for normalization and creation-default rules
- shared UI JVM tests for create-sheet behavior where practical

## Validation Strategy

Primary validation path:

- `./gradlew :app:sharedUI:jvmTest`
- `./gradlew :app:desktopApp:test`
- `./gradlew :core:commonTest`
- `./gradlew :app:desktopApp:run`

Manual validation focus:

- create from `Tasks` and confirm backlog default
- create from `Day` and confirm selected-date scheduling default
- clear scheduling from `Day` and confirm backlog result
- confirm the sheet closes back to the current surface after create

## Open Decisions Settled

- quick-create stays single-sheet
- create closes back to the current surface
- no parent assignment in create
- default scheduling is context-aware
- Day default means `start_not_before` on the selected day
- clearing schedule creates backlog
- scheduling UI must be human-friendly rather than raw-string based

## Success Criteria

The feature is successful if:

1. creating from `Tasks` feels like fast backlog capture
2. creating from `Day` feels like fast date-scoped capture
3. the UI no longer depends on raw ISO text entry for normal scheduling
4. the shared task model and event ledger remain intact
5. Desktop and Android can share the same creation behavior
