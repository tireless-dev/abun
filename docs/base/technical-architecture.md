# Base Technical Architecture

## Purpose

This document defines the shared technical shape used by all modules.

## Current Stack

- Kotlin Multiplatform app
- SQLDelight-backed local SQLite database
- shared logic and shared Compose UI
- shared Compose UI uses Material 3 primitives directly for common controls such as buttons, text, segmented choices, and text fields
- shared primary action buttons should map to the active Material 3 `primary` and `onPrimary` tokens rather than reusing text colors as button containers
- server-side HTTP API with PostgreSQL persistence
- local-first sync engine shared across synced resources

## Server Runtime Status

The buildable server implementation currently checked into the repo is the JVM `:server` module:

- Ktor/Netty routing in [Application.kt](/Users/jerry/Workspace/_tools/abun/server/src/main/kotlin/dev/tireless/abun/Application.kt)
- JDBC/Hikari/PostgreSQL persistence in [SyncServices.kt](/Users/jerry/Workspace/_tools/abun/server/src/main/kotlin/dev/tireless/abun/SyncServices.kt)
- schema bootstrap in [schema.sql](/Users/jerry/Workspace/_tools/abun/server/src/main/resources/db/schema.sql)

The Cloudflare Workers migration is the intended replacement path for the JVM server, but Worker source is not currently present as a committed, buildable implementation under `workers/api`. Generated Wrangler cache files under `workers/api/.wrangler` are not architectural source of truth.

Until the Worker source, tests, and deployment configuration are committed, architecture docs should describe Cloudflare Workers as a migration target rather than the active implementation.

## Layering

### Client

The client is responsible for:

- rendering UI from local state
- validating and applying local writes
- persisting module data in SQLite
- tracking dirty state for synced resources
- running pull-then-push sync cycles

### Server

The server-side API is responsible for:

- authenticating the user
- enforcing ownership boundaries
- validating incoming mutations
- storing canonical synced records
- assigning server-side sync metadata

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

- The server-side API must bind synced data to the authenticated user.
- Client-provided ownership metadata is never authoritative.

### Deletion strategy

- Prefer soft deletes for synced records.
- Sync must propagate deleted records as part of the normal resource stream.

### Implementation status marker

When a design concept is planned but incomplete, mark it with `[TBI]` in the relevant doc rather than leaving the state implicit.

## Module Boundary Rule

Base docs define shared mechanisms. Module docs define domain meaning. If a statement answers "what does this concept mean to the user?", it usually belongs in the module doc. If it answers "how do all modules persist, sync, identify, or scope records?", it belongs in `base/`.

## Desktop Validation Runtime

- The Desktop target is the primary fast validation surface for shared mobile UI behavior.
- The Desktop app should launch in a portrait phone-like window by default and apply a desktop-specific minimum size guardrail so responsive mobile layouts can be checked without starting the Android target.
- The Desktop runtime should include an SLF4J backend so JVM dependencies such as Ktor/CIO emit normal logs instead of falling back to the no-op logger.
