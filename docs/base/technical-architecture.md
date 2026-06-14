# Base Technical Architecture

## Purpose

This document defines the shared technical shape used by all modules.

## Current Stack

- Kotlin Multiplatform app
- SQLDelight-backed local SQLite database
- shared logic and shared Compose UI
- shared Compose UI uses Material 3 primitives directly for screens, sheets, buttons, text fields, segmented choices, and app chrome, with a small shared editorial design-language layer for screen padding, section framing, flat outlined surfaces, and muted status tags
- Cloudflare Worker with PostgreSQL persistence through Hyperdrive-compatible access
- local-first sync engine shared across synced resources

Shared UI design-system reference:

- editorial Material 3 tokens and primitive guidance live in [shared-ui-design-system.md](/Users/jerry/Workspace/_tools/abun/docs/base/shared-ui-design-system.md)

## Layering

### Client

The client is responsible for:

- rendering UI from local state
- validating and applying local writes
- persisting module data in SQLite
- persisting local-only runtime preferences such as login-guide omission, device theme preference, and the current per-device auth session in platform preference storage when the state must survive app restarts without syncing to the server
- tracking dirty state for synced resources
- restoring a persisted auth session on startup, silently refreshing it when only the access token is stale, and falling back to guest mode when the refresh session is no longer usable
- running pull-then-push sync cycles

### Backend

The Worker backend is responsible for:

- authenticating the user
- issuing short-lived signed access JWTs plus rotating refresh tokens
- storing revocable per-device sessions for refresh-token rotation and logout
- enforcing ownership boundaries
- validating incoming mutations
- storing canonical synced records
- assigning server-side sync metadata

The current production shape serves both site and API concerns from the same origin:

- `/` landing page
- `/app` web application shell
- `/mobile` mobile/download page
- `/auth/*` authentication and session lifecycle APIs
- `/api/sync/*` local-first sync APIs
- `/api/*` direct business APIs

Current auth/session endpoints:

- `POST /auth/request`
- `POST /auth/verify`
- `POST /auth/refresh`
- `POST /auth/logout`

OTP email is the only supported auth method today, but it is carried behind the generic auth contract with `method = "otp_email"`.

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
- Access JWT validation must remain session-aware so revoked device sessions stop authorizing `/api/*` and `/api/sync/*` calls even before the client clears local state.
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

## Shared UI Boundary

- `app/sharedUI` is the cross-platform source of truth for the app’s visual system across Desktop and Android.
- New shared UI work should prefer shared theme tokens plus the minimal editorial primitives before introducing feature-local spacing, surface, or chip styling.
- The shared layer should stay intentionally small: it exists to enforce consistent layout and surface language, not to wrap every Material 3 control.
- Shared appearance mode is preference-driven and must support `System`, `Light`, and `Dark` from the shared settings flow rather than through platform-specific toggles.
