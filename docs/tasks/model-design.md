# Tasks Model Design

## Purpose

This document defines the domain model for the tasks module.

## Module Scope

The tasks module currently includes:

- tasks
- task events
- routines
- task-facing pomodoro sessions
- task-oriented day workspace and timeline views

If this module grows further, these areas may become submodules.

## Core Domain Position

The tasks module is log-first rather than state-first.

The stable identity is the task record. History is represented by task events. Current status is derived from history instead of stored directly on the task row.

## Entities

### Task

Represents stable task identity and mutable task metadata.

Core fields:

- `id`
- `parent_id`
- `routine_id`
- `title`
- `start_not_before`
- `end_not_after`
- `estimated_duration`
- `is_deleted`

The task record does not own lifecycle status.

Planning field formats:

- `start_not_before`: ISO 8601 timestamp with timezone
- `end_not_after`: ISO 8601 timestamp with timezone
- `estimated_duration`: ISO 8601 duration

### Task event

Represents historical task activity.

Core fields:

- `id`
- `task_id`
- `journal_date`
- `event_type`
- `content`
- `event_time`
- `is_deleted`

Task events are append-only during normal operation.

Event vocabulary:

- `Created`
- `Progressed`
- `Completed`
- `Postponed`
- `Deleted`
- `Missed`
- `Skipped`

Applicability rules:

- `Created`: normal tasks and routine-derived occurrences
- `Progressed`: normal tasks and routine-derived occurrences
- `Completed`: normal tasks and routine-derived occurrences
- `Postponed`: normal tasks and routine-derived occurrences
- `Deleted`: normal tasks only
- `Missed`: routine-derived occurrences only
- `Skipped`: routine-derived occurrences only

### Routine

Represents a reusable task template plus its schedule definition.

Core fields:

- `id`
- `template_title`
- `template_detail`
- `recurrence_rule`
- `default_start_not_before`
- `default_estimated_duration`
- `is_active`

Routine notes:

- recurrence is stored in iCalendar format
- routine definitions do not require a default `end_not_after`
- routine-derived tasks are top-level occurrences

### Pomodoro session

Represents a focus or break session that may be linked to a task.

Core fields:

- `id`
- `task_id`
- `phase`
- `state`
- `started_at`
- `ends_at`
- `completed_at`
- `duration_minutes`
- `note`
- `task_update`

## Derived Concepts

### Current task status

Task status is derived from the latest relevant task event.

Working mapping:

- latest `Created` -> `pending`
- latest `Progressed` -> `in_progress`
- latest `Completed` -> `completed`
- latest `Deleted` -> excluded from active views
- latest `Skipped` -> skipped
- latest `Missed` -> missed

### Parent task status

Parent tasks may derive summary status from child activity, but this remains derived behavior and should not be written back as a canonical task status field.

Display rule:

- task management views show parent-child hierarchy in tree mode
- other views hide hierarchy by default

### Journal entry

A journal entry is a physical task event rendered for a selected day. The journal should preserve what actually happened on that day rather than projecting current state backward onto history.

### Day workspace

The day workspace is date-scoped. It is not only a live today dashboard.

For a selected date it contains:

- `Open tasks`
- `Day timeline`

Past dates are reconstructed historically. Future dates are projected from current planning state.

### Backlog task

A backlog task is a normal task with neither `start_not_before` nor `end_not_after`.

Backlog tasks live in the `Tasks` management surface and do not appear in any day workspace until scheduled.

### Routine-derived occurrence

A routine-derived task is an occurrence instance created from a routine definition.

Rules:

- it preserves routine identity for review and reporting
- it is top-level rather than attached under a parent task
- it may be postponed multiple times only while staying before the next occurrence boundary
- after rollover, an unfinished occurrence becomes `Missed` automatically

## Model Constraints

- A task may appear in many daily journals through separate events.
- Completing a task must not rewrite older journals.
- Task history should remain readable even as current task status changes.
- Routine and pomodoro records may influence task behavior, but they do not replace the task-event ledger.
- A routine-created task follows the normal task lifecycle for that occurrence and does not automatically persist into the next routine round.
- A task with neither `start_not_before` nor `end_not_after` remains in backlog until scheduled.
- Deleting a normal task removes it from current active views but does not erase past historical reconstruction before the deletion date.
