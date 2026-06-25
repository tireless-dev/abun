# Tasks Functionality Design

## Purpose

This document describes the user-facing behavior of the tasks module. It is the PRD-level reference for how task planning, daily execution, and historical review should work from a user perspective.

UI implementation note:
- current shared task surfaces use direct Material 3 components for sheets, forms, segmented selectors, buttons, and app chrome
- current task surfaces follow the shared editorial Material 3 language: neutral-first color, spacious 8/16/24/32 rhythm, flat outlined cards, muted status tags, and low-noise app chrome
- top-level shared UI routing now uses Compose Navigation for `Day`, `Tasks`, and `Settings`, while the task subtabs inside `Tasks` remain state-driven
- shared implementation ownership is now split by responsibility: route surfaces live in `ui/screens`, reusable task and routine presentation lives in `ui/components`, and create/edit modal flows live in `ui/sheets`
- the `Tasks` first-level filter selector is compact and icon-first: every option keeps its icon visible, while only the active filter expands to show its text label
- the task-instance list now sits directly on the shared page surface instead of inside a nested task-list card with duplicate headings
- this is an implementation detail only; the user-facing workflows below are unchanged by the shared UI package reorganization

## Primary User Jobs

1. Capture a task quickly.
2. See what matters on a selected day.
3. Track progress without losing history.
4. Use routines for repeated work.
5. Plan work using flexible date windows instead of rigid priority systems.
6. Run pomodoro sessions tied to work.

## Product Surfaces

### Dashboard

`Dashboard` is the primary execution surface for the tasks module.

- default selected date: today
- alternate selected dates: past and future days
- purpose: show what is open for that day and what happened on that day
- visual treatment: calm editorial workspace with compact sectioned panels for open work, timeline history, and pomodoro state
- header treatment: the selected date is the primary title, without a separate subtitle or duplicate surface heading
- pomodoro treatment: the pomodoro panel owns the timer action instead of the summary card, and an active session keeps that action separate from the countdown label

This surface replaces the idea of a purely live “today dashboard.” It is a date-scoped workspace.

### Tasks

`Tasks` is the management surface.

- purpose: create, edit, structure, postpone, and review tasks as records
- hierarchy is visible here in tree mode
- backlog lives here
- shared visual language: task/routine rows appear as flat outlined cards with muted metadata and action buttons kept secondary to the record content
- task rows use a lightweight state indicator and a low-emphasis detail affordance instead of a large primary manage button
- first-level filter chrome stays lightweight: inactive filters shrink to icon-only pills, and the active filter expands to icon plus label for orientation without repeating a separate state heading

Default behavior:

- shows one unified active task tree/list
- includes backlog tasks and scheduled tasks in the same management surface
- hides completed tasks by default
- clicking a task row opens task detail
- task detail opens in read-only mode first
- read-only task detail does not show execution buttons
- editing is an explicit in-place mode entered from the top-right edit affordance
- delete is available from edit mode and requires confirmation before execution
- tree expand/collapse uses a separate affordance

Primary filters:

- `All active`
- `Backlog`
- `Scheduled`
- `Routine-derived`
- `Completed`

Filter rule:

- these are mutually exclusive first-level filters, not combinable filters

Subtab structure:

- `Tasks`: task instances and task management
- `Routines`: routine definitions only

### Report/Review

`Report/Review` is the reflection surface.

- purpose: review completed work, missed routine occurrences, and pomodoro history
- pomodoro history belongs here rather than on the core task-management surface

## Dashboard Workspace Model

For any selected date, the `Dashboard` surface contains two lists:

### Open tasks

This is the actionable list for the selected date.

Rules:

- excludes completed items by default
- excludes unscheduled backlog-only tasks
- excludes expired routine occurrences from later days
- includes tasks that are relevant or available for the selected date
- uses muted labels/tags to explain why a task is shown without adding bright status noise
- should be sortable by urgency and relevance without requiring explicit priority fields

### Day timeline

This is the chronological record of what happened on the selected date.

Rules:

- backed by physical task events
- preserves historical truth for past dates
- records meaningful actions such as create, progress, complete, postpone, skip, and miss
- should not be reconstructed from current task state alone
- should not include ordinary metadata edits such as title-only edits or minor planning-field adjustments

Visible timeline event set:

- `Created`
- `Progressed`
- `Completed`
- `Postponed`
- `Deleted`
- `Missed`
- `Skipped`

## Time and Date Semantics

### Selected date behavior

The `Dashboard` workspace supports three modes of use:

- past-date reconstruction
- current-day execution
- future-date projection

### Past dates

Past dates must be reconstructed using only the state and events effective on that date.

Rules:

- later changes must not rewrite what that day looked like
- postponed-away tasks should no longer remain in that day’s open-task list
- the postpone action should still appear in that day’s day timeline

### Current date

Current date behavior is the normal live execution mode.

### Future dates

Future dates are forecast views.

Rules:

- open tasks may appear if their planning window makes them relevant on that date
- day timeline is usually empty unless something is already assigned to that future date

## Planning Model

The tasks module uses a date-window planning model instead of explicit manual priority.

Relevant task concepts:

- `start_not_before`
- `end_not_after`
- `estimated_duration`
- routine occurrence date
- routine rollover cutoff from preferences

Derived planning rule:

- if `estimated_duration` is absent, treat it as zero duration

Backlog rule:

- a task with neither `start_not_before` nor `end_not_after` is a backlog task
- backlog tasks belong to the `Tasks` management surface
- backlog tasks do not appear in any `Dashboard` view until scheduled

No explicit priority field is required.

Tags may be introduced later, but they are not part of this scheduling model.

## Core Workflows

### Create task

User outcome:

- a new task exists immediately in local state
- the task appears in backlog or day-based task views based on its planning fields
- a corresponding `CREATED` event exists in the journal/history model

Quick-create fields:

- `title`
- `detail`
- scheduling controls

Quick-create exclusions:

- no parent selection during creation
- no raw timestamp or ISO duration entry in the normal create flow

Default creation rule:

- creating a task from `Tasks` creates it in backlog by default
- creating a task from `Day` defaults to a scheduled task with `start_not_before` on the selected day
- clearing scheduling before create returns the task to backlog behavior, even when launched from `Day`
- after create, the sheet closes and returns the user to the current surface
- scheduling remains optional and further refinement still belongs in task detail

### Define or update planning window

User outcome:

- a task can become available from `start_not_before`
- a task can express an expected completion boundary through `end_not_after`
- a task can carry `estimated_duration` for scheduling pressure hints
- a task can enter a day view before `end_not_after` when its estimated duration implies it should start earlier

Rules:

- planning fields are mutable
- only meaningful scheduling changes should generate history events
- `start_not_before` uses ISO 8601 timestamp with timezone
- `end_not_after` uses ISO 8601 timestamp with timezone
- `estimated_duration` uses ISO 8601 duration
- adding a date window moves a task from backlog into relevant day views
- removing a date window returns a task to backlog

### Manage tasks in the `Tasks` surface

User outcome:

- backlog and scheduled tasks can be managed from one unified tree/list
- hierarchy remains visible for structural editing
- active work stays in focus because completed tasks are hidden by default
- the user can switch between first-level filtered views without leaving the `Tasks` surface

Quick list behavior:

- opening a task row enters task detail
- list rows are not the place for completion
- deliberate scheduling changes belong in task detail

### Edit normal task detail

Editable fields:

- `title`
- `detail`
- `parent task`, selected from an explicit selection list
- `start_not_before`
- `end_not_after`
- `estimated_duration`

Allowed actions:

- `complete`
- `postpone`
- `delete`

History section:

- task detail includes a full event-history section for that task
- this history spans all days, not only the currently selected day
- it is the per-task complement to the day-scoped `Day timeline`

Interaction rules:

- `complete` is a deliberate detail action, not a quick list action
- `delete` is a secondary danger action and uses soft delete behavior underneath
- changing parent is allowed from task detail through explicit selection, not inline editing in the list

### View routine-derived task detail

Routine-derived tasks are execution instances, not freely editable planning records.

Visible context:

- origin routine
- current occurrence date or window
- next occurrence boundary
- whether postpone is currently allowed

Allowed actions:

- `complete`
- `postpone`, only when schedule math allows it without overlapping the next occurrence
- `skip`, only before rollover

History section:

- routine-derived task detail includes the full event history for that occurrence
- history is available even though the occurrence detail is otherwise mostly read-only

Not allowed:

- edit title
- edit detail
- edit planning fields
- reparent
- delete
- detach from routine

Routine-derived postponement rules:

- the occurrence keeps its routine identity
- postponement is an occurrence-level exception, not a conversion into a normal task
- multiple postpones are allowed
- every postponement must stay before the next occurrence boundary
- once the occurrence can no longer stay before the next occurrence, postponement is no longer allowed

### Progress task

User outcome:

- the user can record progress without mutating past history
- the current derived status may move to `in_progress`

### Complete task

User outcome:

- the user can mark completion from the current moment
- past journal entries stay unchanged
- current derived status becomes `completed`

### Create subtask

User outcome:

- a child task is attached to a parent task
- the child owns its own lifecycle history
- parent progress may reflect child progress in derived views
- task management views show hierarchy in tree mode
- other views hide hierarchy by default

### Postpone task

User outcome:

- a task can be moved out of one day and into a later date window
- the current planning fields reflect the new schedule
- the history records that the task was postponed

Rules:

- postponement updates mutable planning fields on the task
- postponement also creates a task event because it is historically meaningful
- after postponement, the old day no longer shows the task in open tasks
- the old day still shows the postpone action in the day timeline
- the postpone event should record both old and new scheduling values for history review

Postpone event payload should include, when relevant:

- previous `start_not_before`
- new `start_not_before`
- previous `end_not_after`
- new `end_not_after`

### Manage routines

User outcome:

- recurring work can be defined once and generated consistently
- routine execution is manual
- generated tasks are idempotent across devices when the same routine instance is produced
- a routine-created task becomes a routine-derived execution occurrence after creation
- a routine-created task does not carry into the next routine round automatically
- routine definitions are managed in the `Routines` subtab
- routine-created task instances may appear in the `Tasks` subtab when relevant, but routine definitions remain separate from task-instance management

Routine definition fields:

- required:
  - `template_title`
  - recurrence rule in iCalendar format
  - active/inactive, defaulting to active
- optional:
  - `template_detail`
  - default `start_not_before`
  - default `estimated_duration`

Routine definition constraints:

- routine-derived tasks are top-level, not attached under a parent task
- routine definitions do not require a default `end_not_after`
- the `Routines` subtab manages routine definitions only, not past occurrences
- recurrence is stored in machine-readable iCalendar format and presented in a human-readable UI form

Routine detail behavior:

- opening a routine row enters routine detail/editor
- routine editing is deliberate, not inline in the list

Routine detail actions:

- edit title
- edit detail
- edit recurrence rule
- edit default `start_not_before`
- edit default `estimated_duration`
- activate/deactivate
- delete

Routine lifecycle rules:

- deactivation stops future generation and is reversible
- deletion soft-deletes the routine definition
- deactivation or deletion does not remove already-created occurrences
- existing occurrences remain available for execution and review/history

### Handle routine rollover

User outcome:

- a routine occurrence is valid only for its own occurrence window
- once the configured rollover time passes, an unfinished occurrence is no longer treated like a normal open task

Rules:

- rollover cutoff is configured in preferences, for example `02:00`
- after rollover, the routine occurrence disappears from later day views
- the original occurrence day remains reconstructible
- the system automatically records a `MISSED` event for an expired routine occurrence
- expired routine occurrences may be marked missed or skipped, but are not finished later like normal tasks

### Review a selected day

User outcome:

- the selected date shows what was open for that day
- the selected date shows what actually happened on that day
- the user can inspect yesterday, today, tomorrow, or another chosen day through the same surface

### Run pomodoro session

User outcome:

- a focus or break session can be started and completed
- the session may optionally update the linked task with no change, progress, completion, or cancellation
- pomodoro history belongs in a report/review subtab rather than the core task-management surface

## Non-Goals

- The tasks module is not a traditional mutable status board where history is rewritten to match the latest state.
- The day timeline is not a synthetic feed generated from current task state alone.
- The planning model does not use manual priority fields.
- Routine occurrences are not long-lived reusable tasks that stay open across future rounds.

## Open Task Labels

Open-task cards should use labels/chips rather than rigid grouping so one task can express multiple overlapping states.

Initial label set:

- `Due today`
- `Routine`
- `Expires at rollover`
- `Target: <date>`

Labels should explain why a task appears in the selected day’s open list.

`Available now` is the default open-task state and does not need a label.

Schedule pressure and overdue are visual states, not labels. They should be communicated primarily through color or equivalent visual emphasis, not chips.

Visual state meaning:

- `Tight`: the remaining scheduling window is getting narrow relative to `estimated_duration`
- `Critical`: delaying further is likely to miss `end_not_after`
- `Overdue`: the selected date is later than `end_not_after` and the task is still unfinished

## Inclusion Rules For Open Tasks

### Normal tasks

A normal task may appear in open tasks for a selected date when:

- it is available by `start_not_before`
- or it is expected on that date through `end_not_after`
- or it is overdue from an earlier `end_not_after`

Normal overdue state is derived and does not require an automatic event.

Tasks with neither `start_not_before` nor `end_not_after` do not appear in open tasks for any day.

Effective visibility start for a normal task:

- if both `start_not_before` and `end_not_after` exist, visibility starts at the later of:
  - `start_not_before`
  - `end_not_after - estimated_duration`
- if only `end_not_after` exists, visibility starts at `end_not_after - estimated_duration`
- if only `start_not_before` exists, visibility starts at `start_not_before`
- if neither exists, the task stays in backlog

If `estimated_duration` is absent, treat it as zero duration for this calculation.

## Schedule Pressure

Schedule pressure is derived from:

- `start_not_before`
- `end_not_after`
- `estimated_duration`
- selected date and time

Meaning:

- if there is ample remaining window between `start_not_before` and `end_not_after`, pressure is low
- if the remaining window is getting narrow relative to `estimated_duration`, the task should show a tighter visual hint
- if delaying further likely causes the task to miss `end_not_after`, the task should show a critical visual hint

This pressure signal is visual only and does not change the default sort order.

### Routine-derived tasks

A routine-derived task may appear in open tasks for its occurrence date when:

- it has been created for that routine occurrence
- the occurrence has not yet expired past the configured rollover cutoff

After expiry:

- it disappears from later day views
- it remains visible in the original day’s reconstruction
- it is represented in history by an automatic `MISSED` event if left unfinished

## Default Sort Order For Open Tasks

When the selected day shows a mixed set of open tasks, the default ranking should be:

1. overdue normal tasks
2. tasks with `end_not_after` on the selected date
3. routine-derived tasks for the selected date
4. available-now tasks with only `start_not_before`

Additional rules:

- expired routine occurrences do not appear in open tasks
- completed tasks do not appear in open tasks
- items outside the selected day’s relevance window do not appear

## Settled Product Decisions

- Canonical day surface name is `Day`, with `Today` as the default selected date.
- `Day` contains two lists: `Open tasks` and `Day timeline`.
- Undated tasks live in `Backlog` under `Tasks`, not in `Day`.
- Past dates are true reconstructions based on effective state and events from that date.
- Future dates are forecast views based on current planning state.
- Task management views show tasks in tree mode.
- Other views hide hierarchy by default.
- The `Tasks` tab uses a title bar selector to switch between `Tasks`, `Routines`, and `Pomodoro`.
- Routine execution is manual.
- A routine-created task remains a routine-derived occurrence for that occurrence only.
- Routine occurrences expire after a configurable rollover time and are not finished later like normal tasks.
- Pomodoro history belongs in a report/review subtab.
