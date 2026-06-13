# Prioritized Backlog

This backlog is global and temporary. Once the documentation set is reviewed, these items can move into Git issues.

## P0

1. Review the new base and tasks docs against the current implementation and remove contradictions.
2. Decide the exact boundary between task history, Today agenda, and pomodoro behavior in the tasks module.
3. Add a remote-change sync trigger that tells idle clients to pull without bypassing the normal sync flow. `[TBI]`
4. Replace the legacy task scheduling model with the new day-workspace and backlog model in shared logic and UI.

## P1

1. Document which task workflows are already implemented end to end in the desktop app and which remain partial.
2. Add a dedicated desktop-native window size preset or toggle so the app can switch cleanly between portrait mobile-validation framing and a wider desktop-oriented layout.
2. Clarify how routine execution is triggered and how deterministic IDs are enforced across devices.

## P2

1. Split task submodules further if the module grows too broad:
   - core tasks
   - routines
   - pomodoro
2. Create first-pass placeholders with actual design questions for `finance`.
3. Create first-pass placeholders with actual design questions for `notes`.

## Task PRD Breakdown

These are issue-sized slices derived from the current task PRD and should be moved into Git issues.

### P0: Task model and day workspace

1. Define and migrate task planning fields:
   - `start_not_before`
   - `end_not_after`
   - `estimated_duration`
   - `detail`
2. Implement backlog semantics:
   - tasks with no date window live only in `Tasks`
   - backlog items do not appear in `Day`
3. Implement day-workspace reconstruction:
   - `Open tasks`
   - `Day timeline`
   - past-date historical reconstruction
   - future-date projection
4. Implement derived visibility rules from planning window and `estimated_duration`.
5. Implement normal-task deletion as:
   - soft delete on task row
   - `Deleted` history event
   - removal from active views after deletion date

### P0: Task event model

1. Replace legacy task-event semantics with the new event vocabulary:
   - `Created`
   - `Progressed`
   - `Completed`
   - `Postponed`
   - `Deleted`
   - `Missed`
   - `Skipped`
2. Implement `Postponed` as the only structured event payload for now:
   - previous `start_not_before`
   - new `start_not_before`
   - previous `end_not_after`
   - new `end_not_after`
3. Update task detail history and day timeline queries to use only meaningful business events.

### P1: Tasks surface

1. Implement `Tasks` / `Routines` subtabs inside the tasks tab.
2. Implement the unified `Tasks` tree/list with first-level filters:
   - `All active`
   - `Backlog`
   - `Scheduled`
   - `Routine-derived`
   - `Completed`
3. Implement normal-task detail:
   - editable title
   - editable detail
   - parent selection list
   - editable planning fields
   - complete, postpone, delete actions
   - full per-task event history
4. Implement routine-derived occurrence detail:
   - read-only fields
   - origin routine context
   - next-occurrence boundary
   - complete / postpone-if-allowed / skip actions
   - per-occurrence history

### P1: Routines

1. Implement routine definition model updates:
   - `template_title`
   - `template_detail`
   - `recurrence_rule`
   - `default_start_not_before`
   - `default_estimated_duration`
   - `is_active`
2. Store recurrence in iCalendar format and render/edit it as human-readable UI.
3. Implement routine detail editor:
   - edit title/detail
   - edit recurrence rule
   - edit default start/duration
   - activate/deactivate
   - delete
4. Preserve existing occurrences when a routine is deactivated or deleted.

### P1: Routine occurrence execution rules

1. Implement manual routine execution.
2. Preserve routine-derived occurrence identity for review/history.
3. Implement occurrence postponement bounded by next-occurrence schedule math.
4. Implement automatic `Missed` event creation after rollover.
5. Allow `Skipped` only before rollover.

### P2: Pomodoro integration

1. Keep pomodoro history in `Report/Review`.
2. Allow pomodoro start to create `Progressed` only for valid open tasks.
3. Keep pomodoro separate from task-event payload design.
