# Tasks Technical Design

## Purpose

This document defines the technical design specific to the tasks module.

## Storage Model

The tasks module currently uses the following local tables:

- `task`
- `task_event`
- `routine`
- `pomodoro_session`

The current local schema lives in [AbunDatabase.sq](/Users/jerry/Workspace/_tools/abun/app/sharedLogic/src/commonMain/sqldelight/dev/tireless/abun/db/AbunDatabase.sq).

## Task Ledger Rules

### Task row

The task row is the stable identity record.

Rules:

- no canonical status column
- no canonical completion timestamp field
- planning fields such as `start_not_before`, `end_not_after`, and `estimated_duration` live on the mutable task row
- mutable fields use HLC-based sync metadata

### Task event row

The task event row is the historical ledger record.

Rules:

- append-only during normal operation
- synced as idempotent inserts
- should not use field-level merge for ordinary sync
- historical event editing is allowed as a separate capability and should use an explicit edit path rather than reusing append-only event sync semantics

Event contract:

- plain events:
  - `Created`
  - `Progressed`
  - `Completed`
  - `Deleted`
  - `Missed`
  - `Skipped`
- structured events:
  - `Postponed`

`Postponed` payload should capture scheduling transition details, including when relevant:

- previous `start_not_before`
- new `start_not_before`
- previous `end_not_after`
- new `end_not_after`

## Local Write Rules

### Create task

Single logical transaction:

1. insert task
2. insert `CREATED` task event
3. mark both records dirty for sync

### Progress task

Insert a `PROGRESSED` task event. Do not mutate the task lifecycle state directly.

### Complete task

Insert a `COMPLETED` task event. Do not rewrite historical journal rows.

### Delete normal task

1. set task `is_deleted`
2. sync deletion through mutable-resource rules
3. append a `Deleted` task event for historical review

Deletion removes the task from current active views, but historical views before the deletion date remain reconstructible.

### Create subtask

1. insert child task with `parent_id`
2. insert child `CREATED` event
3. mark new records dirty

### Execute routine

1. derive deterministic task ID for the routine instance
2. derive deterministic `CREATED` event ID
3. insert missing records only
4. mark inserted records dirty

Routine execution is manual. Generation may be triggered by client or server paths, but the user-facing model stays the same: create the task for the specific occurrence, then let it follow the normal task lifecycle.

Generated occurrence shape:

- preserve routine linkage and occurrence identity
- keep the occurrence top-level
- inherit routine defaults where applicable

### Complete pomodoro session

1. update the pomodoro session row as a mutable synced resource
2. optionally apply the configured task update behavior
3. if the task is updated, do so through task-event insertion rather than direct task status mutation

Pomodoro is separate from the task-event schema. Starting a pomodoro may create `Progressed` only when the linked task is still a valid open task at that moment.

## Query Rules

### Current task status

Derive from the latest non-deleted task event for that task.

### Daily journal

Render from the physical task events for the selected day joined with their tasks.

### Day workspace reconstruction

For a selected date:

- `Open tasks` are derived from planning fields, deletion state, completion state, and routine occurrence validity effective on that date
- `Day timeline` is derived from physical task events for that date only
- later edits must not rewrite earlier reconstructed day views

### Agenda and Today views

These may compose task and journal data for convenience, but they should preserve the distinction between:

- current task state
- scheduled reminders
- historical journal entries

Hierarchy display rule:

- task management views should render parent-child structure in tree mode
- day and other non-management views should hide hierarchy by default

## Sync Rules

### Mutable resources

`task`, `routine`, and `pomodoro_session` use field-group HLC sync rules inherited from the base sync architecture.

### Append-only resource

`task_event` uses idempotent append-only sync.

Expected push semantics:

- insert when absent
- return canonical event when present
- never overwrite existing historical content during normal sync

Event payload changes should never be merged in place through ordinary append-only sync. Any future event-editing path must be explicit and separate.

## Deterministic ID Rules

The current design requires deterministic IDs for generated records where duplicate creation is possible:

- routine-generated task
- routine-generated `CREATED` event

Exact namespacing and UUID strategy should remain consistent across client and server implementations.

## Planning Field Contract

Mutable planning fields on `task`:

- `start_not_before`
- `end_not_after`
- `estimated_duration`

Formats:

- `start_not_before`: ISO 8601 timestamp with timezone
- `end_not_after`: ISO 8601 timestamp with timezone
- `estimated_duration`: ISO 8601 duration

These fields participate in ordinary mutable-resource sync and may be changed without violating the append-only event history rule.

Derived visibility rules:

- if both `start_not_before` and `end_not_after` exist, task visibility begins at the later of:
  - `start_not_before`
  - `end_not_after - estimated_duration`
- if only `end_not_after` exists, visibility begins at `end_not_after - estimated_duration`
- if only `start_not_before` exists, visibility begins at `start_not_before`
- if neither exists, the task belongs to backlog

If `estimated_duration` is absent, treat it as zero duration for visibility calculation.

## Implementation Notes

The current codebase already contains:

- task-oriented view models in [DomainModels.kt](/Users/jerry/Workspace/_tools/abun/app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/DomainModels.kt)
- local persistence and merge behavior in shared logic
- server APIs and database schema for synced task resources

## Settled Technical Decisions

- Parent-task rollup status is currently derived on demand. There is no materialized view yet.
- Historical event editing is allowed, but it should use a dedicated editing path instead of ordinary append-only event sync.
- Routine generation may be initiated by both client and server-side paths.
- Routine recurrence is stored in iCalendar format and edited through a higher-level human-readable UI.
- Routine-derived occurrence postponement is allowed only while staying before the next occurrence boundary.
- Normal task deletion should use both mutable soft delete state and a `Deleted` history event.

## Open Technical Questions

- `[TBI]` Define the storage and sync contract for historical event editing.
- `[TBI]` Decide whether parent-task rollup status needs caching once task trees become large.
- `[TBI]` Define how client-triggered and server-triggered routine generation avoid duplicate occurrence creation while preserving deterministic IDs.
