# Base Technical Architecture

## Purpose

This document defines the shared technical shape used by all modules.

## Current Stack

- Kotlin Multiplatform app
- SQLDelight-backed local SQLite database
- shared logic and shared Compose UI
- Cloudflare Worker with PostgreSQL persistence through Hyperdrive-compatible access
- local-first sync engine shared across synced resources

## Layering

### Client

The client is responsible for:

- rendering UI from local state
- validating and applying local writes
- persisting module data in SQLite
- tracking dirty state for synced resources
- running pull-then-push sync cycles

### Backend

The Worker backend is responsible for:

- authenticating the user
- enforcing ownership boundaries
- validating incoming mutations
- storing canonical synced records
- assigning server-side sync metadata

The current production shape serves both site and API concerns from the same origin:

- `/` landing page
- `/app` web application shell
- `/mobile` mobile/download page
- `/api/auth/*` OTP authentication
- `/api/sync/*` local-first sync APIs
- `/api/*` direct business APIs

## Resource Categories

Shared technical behavior falls into two categories:

### Mutable resources

These use field-group conflict resolution and dirty tracking.

Examples in the current implementation include:

- preferences
- routines
- tasks
- alarms
- pomodoro sessions

### Append-only resources

These are inserted idempotently and not merged field by field during normal operation.

Current example:

- task events

## Cross-Module Rules

### Identity strategy

- Use stable IDs for all persistent records.
- Use deterministic IDs for generated records where duplicate generation is possible across devices.
- Keep ID rules documented in the module technical design when they are module-specific.

### Ownership strategy

- The backend must bind synced data to the authenticated user.
- Client-provided ownership metadata is never authoritative.

### Deletion strategy

- Prefer soft deletes for synced records.
- Sync must propagate deleted records as part of the normal resource stream.

### Implementation status marker

When a design concept is planned but incomplete, mark it with `[TBI]` in the relevant doc rather than leaving the state implicit.

## Module Boundary Rule

Base docs define shared mechanisms. Module docs define domain meaning. If a statement answers "what does this concept mean to the user?", it usually belongs in the module doc. If it answers "how do all modules persist, sync, identify, or scope records?", it belongs in `base/`.
