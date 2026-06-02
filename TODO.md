# TODO

This file is the execution tracker for Codex work on Abun.

## Operating Rule

- Work from top to bottom.
- Pick the first unchecked item that is not blocked.
- Keep changes scoped to the selected item.
- Update this file when an item starts, changes scope, is blocked, or is completed.
- If a task reveals new sub-work, add it under the relevant item instead of creating a separate tracker elsewhere.

Status legend:

- `[ ]` not started
- `[-]` in progress
- `[x]` done
- `[!]` blocked / needs decision

## P0

- [x] Task model: planning fields, backlog, and day workspace
  Goal:
  Replace the legacy scheduling model with the new planning-window and day-workspace model.
  Scope:
  - add and migrate `start_not_before`, `end_not_after`, `estimated_duration`, `detail`
  - implement backlog semantics for undated tasks
  - implement `Day` workspace with `Open tasks` and `Day timeline`
  - support past-date historical reconstruction and future-date projection
  - apply derived visibility rules from planning window and duration
  - remove deleted tasks from active views after deletion date while preserving historical views
  Acceptance criteria:
  - undated tasks stay in `Backlog` and do not appear in `Day`
  - `Day` supports today, past dates, and future dates
  - open-task visibility matches `docs/tasks/functionality-design.md`
  - deleted tasks remain visible in historical views before deletion date
  Sub-work:
  - [x] Shared schema/sync model for task planning fields and detail
  - [x] Local backlog/day query derivation with planning-window visibility
  - [x] Historical deletion handling in day reconstruction
  - [x] Controller/UI wiring for `Day` workspace and task surfaces
    - [x] `Day` controller/UI wiring for open tasks and day timeline
    - [x] `Tasks` surface wiring for backlog/scheduled management

- [x] Task events: replace legacy vocabulary and add postponed payload
  Goal:
  Replace the legacy task-event semantics with the new product-facing event vocabulary.
  Scope:
  - use `Created`, `Progressed`, `Completed`, `Postponed`, `Deleted`, `Missed`, `Skipped`
  - apply event-type applicability rules for normal tasks vs routine-derived occurrences
  - implement `Postponed` as the only structured event payload for now
  - update task detail history and `Day timeline` queries to use meaningful business events only
  Acceptance criteria:
  - legacy event naming no longer drives task UI logic
  - `Postponed` records previous and new `start_not_before` / `end_not_after`
  - `Day timeline` and per-task history render the new event model consistently
  Sub-work:
  - [x] Shared event vocabulary and postponed payload schema
  - [x] Local store event write/read migration to new semantics
  - [x] Server sync and business API support for new event model
  - [x] Timeline/history UI migration to the new business events

## P1

- [x] Tasks surface: unified tree, filters, and task detail
  Goal:
  Implement the new `Tasks` surface and normal-task detail flow.
  Scope:
  - add `Tasks` / `Routines` subtabs
  - build unified task tree/list with filters:
    - `All active`
    - `Backlog`
    - `Scheduled`
    - `Routine-derived`
    - `Completed`
  - hide completed tasks by default
  - open task detail from row click and keep expand/collapse separate
  - implement normal-task detail editing for `title`, `detail`, `parent`, `start_not_before`, `end_not_after`, `estimated_duration`
  - implement `complete`, `postpone`, and `delete` actions
  - include full per-task event history in detail
  Acceptance criteria:
  - `Tasks` matches `docs/tasks/functionality-design.md`
  - completion is detail-only, not a quick list action
  - delete is soft-delete backed and secondary/danger scoped
  Sub-work:
  - [x] Split `Tasks` / `Routines` management subtabs and add first-level task filters
  - [x] Full normal-task create flow for `title`, `detail`, `parent`, and planning fields
  - [x] Normal-task detail editor shell for mutable task fields
  - [x] Task-detail history section
  - [x] Detail lifecycle actions (`complete`, `delete`, progress cleanup)
  - [x] Postpone flow with planning-window update and history event

- [x] Routines: definition model, editor, and lifecycle
  Goal:
  Implement routine definitions as a separate management surface with the new recurrence model.
  Scope:
  - add routine fields:
    - `template_title`
    - `template_detail`
    - `recurrence_rule`
    - `default_start_not_before`
    - `default_estimated_duration`
    - `is_active`
  - store recurrence in iCalendar format and render/edit it in a human-readable UI
  - add routine detail/editor for title, detail, recurrence rule, default start/duration, activate/deactivate, delete
  - keep routines separate from task-instance management
  - preserve existing occurrences when a routine is deactivated or deleted
  Acceptance criteria:
  - `Routines` manages routine definitions only
  - routine deletion/deactivation affects future generation only
  - existing occurrences remain reviewable and executable as defined
  Sub-work:
  - [x] Shared schema/sync/business API support for routine detail and defaults
  - [x] Routine create/detail editor for title, detail, recurrence, and default planning fields
  - [x] Routine activate/deactivate and delete lifecycle in the new detail flow
  - [x] Human-readable recurrence rule presentation
  - [x] Structured recurrence rule editor

- [x] Routine occurrences: execution, bounded postpone, skip, and miss
  Goal:
  Implement routine-derived occurrence behavior so execution history stays consistent with the new model.
  Scope:
  - support manual routine execution
  - preserve routine-derived occurrence identity for review/history
  - implement occurrence postponement bounded by next-occurrence schedule math
  - allow multiple postpones while still staying before the next occurrence boundary
  - allow `Skip` only before rollover
  - automatically create `Missed` after rollover when unfinished
  - keep occurrence detail mostly read-only with origin routine and next-occurrence context
  Acceptance criteria:
  - routine occurrences are not converted into normal tasks by postpone
  - postpone availability is explained by next-occurrence boundary
  - `Missed` and `Skipped` match `docs/tasks/functionality-design.md`
  Sub-work:
  - [x] Routine-derived detail action set distinguishes occurrences from normal tasks
  - [x] Occurrence context exposes rollover-aware skip availability
  - [x] Occurrence execution identity and inherited routine defaults
  - [x] Bounded postpone availability against next-occurrence boundary
  - [x] Skip and missed lifecycle rules

## P2

- [x] Pomodoro integration
  Goal:
  Keep pomodoro aligned with the new task execution model without forcing it into task-event semantics.
  Scope:
  - keep pomodoro history in `Report/Review`
  - allow pomodoro start to create `Progressed` only for valid open tasks
  - keep pomodoro separate from task-event payload design
  Acceptance criteria:
  - pomodoro does not distort task-event vocabulary
  - invalid tasks cannot be started directly from pomodoro actions
  Sub-work:
  - [x] Pomodoro task selection uses current valid open tasks only
  - [x] Focus-session start records `Progressed` for valid linked tasks

## References

- `docs/index.md`
- `docs/backlog.md`
- `docs/tasks/model-design.md`
- `docs/tasks/functionality-design.md`
- `docs/tasks/technical-design.md`
