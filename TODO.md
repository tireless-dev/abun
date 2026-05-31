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

- [-] Task model: planning fields, backlog, and day workspace
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
  - [ ] Historical deletion handling in day reconstruction
  - [ ] Controller/UI wiring for `Day` workspace and task surfaces

- [ ] Task events: replace legacy vocabulary and add postponed payload
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

## P1

- [ ] Tasks surface: unified tree, filters, and task detail
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

- [ ] Routines: definition model, editor, and lifecycle
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

- [ ] Routine occurrences: execution, bounded postpone, skip, and miss
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

## P2

- [ ] Pomodoro integration
  Goal:
  Keep pomodoro aligned with the new task execution model without forcing it into task-event semantics.
  Scope:
  - keep pomodoro history in `Report/Review`
  - allow pomodoro start to create `Progressed` only for valid open tasks
  - keep pomodoro separate from task-event payload design
  Acceptance criteria:
  - pomodoro does not distort task-event vocabulary
  - invalid tasks cannot be started directly from pomodoro actions

## References

- `docs/index.md`
- `docs/backlog.md`
- `docs/tasks/model-design.md`
- `docs/tasks/functionality-design.md`
- `docs/tasks/technical-design.md`
