# Base Sync Architecture

## Purpose

This document defines the shared sync architecture used across Abun modules. It should be read as the foundational technical contract for local-first resources.

## Core Model

Abun uses a pull-then-push local-first sync model:

1. Pull server changes.
2. Merge server changes into local SQLite.
3. Push local dirty changes.
4. Merge server-confirmed rows into local SQLite.
5. Clear accepted local dirty state.

Never push before pulling in the same sync cycle.

## Conflict Resolution

### Mutable resources

Mutable resources use field-group Last-Write-Wins with Hybrid Logical Clock tokens.

Expected behavior:

- each logical field group has its own HLC token
- incoming values replace local or server values only when the incoming HLC wins
- dirty field groups are tracked explicitly

### Append-only resources

Append-only resources use idempotent insert semantics.

Expected behavior:

- if the record does not exist, insert it
- if the record already exists for that user, return the canonical record
- do not merge or overwrite historical payload during normal sync

## Resource Ordering

Sync order must respect foreign-key and logical dependencies.

The current implementation uses this pull order:

1. preferences
2. routines
3. tasks
4. alarms
5. task events
6. pomodoro sessions

The same order is used for push.

## API Families

### Sync APIs

Used by the local-first client:

- batch-oriented
- cursor-based pull
- dirty-resource push
- include sync metadata required for merge
- mounted at `/api/sync/*` on the shared Worker origin

Current Kotlin clients call these routes through [SyncRemoteApi.kt](/Users/jerry/Workspace/_tools/abun/app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/SyncRemoteApi.kt):

- `POST /auth/request`
- `POST /auth/verify`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET|POST /api/sync/preferences`
- `GET|POST /api/sync/routines`
- `GET|POST /api/sync/tasks`
- `GET|POST /api/sync/alarms`
- `GET|POST /api/sync/task-events`
- `GET|POST /api/sync/pomodoro-sessions`

Sync and direct business calls now have an auth precondition:

- `/api/sync/*` and `/api/*` require a valid access JWT
- shared auth/session logic is the source of truth for login, restore, refresh, logout, and sync gating before these requests run
- the client must obtain a guaranteed-valid access token before each sync/API request
- the client should refresh proactively shortly before access-token expiry
- one defensive retry is allowed for unexpected `401` responses caused by drift or server-side revocation
- debug OTP shortcuts may prefill credentials in development, but they must still complete a real `/auth/request` plus `/auth/verify` exchange before the first sync cycle starts
- if refresh fails or the session is revoked, the client clears the local auth session, returns to guest mode, disables sync readiness, keeps local SQLite data available for later re-sync after login, and exposes a readable re-login message through shared app state

This guest fallback is an auth-state transition only. It does not delete local task data or reset local SQLite state.

### Business APIs

Used by direct-access or non-local-first clients:

- resource-oriented
- server-validated
- should update sync metadata so synced clients later observe the change through pull
- mounted at `/api/*` on the shared Worker origin

## Realtime Policy

Realtime notifications are allowed only as pull triggers. They may indicate that something changed, but they must not replace the standard pull-and-merge flow.

`[TBI]` Remote-change notifications for idle clients are planned but not fully implemented.

## Current Implementation Notes

The current codebase already includes:

- shared sync models and HLC support in `core`
- client sync orchestration in [SyncEngine.kt](/Users/jerry/Workspace/_tools/abun/app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/SyncEngine.kt)
- local schema and sync cursors in [AbunDatabase.sq](/Users/jerry/Workspace/_tools/abun/app/sharedLogic/src/commonMain/sqldelight/dev/tireless/abun/db/AbunDatabase.sq)
- Worker-hosted sync and business APIs for multiple resources
- persisted per-device auth sessions plus shared token-refresh handling in the client auth/session manager
- additive Worker schema bootstrap steps for evolved sync columns so already-provisioned databases can accept newer task and routine payloads without a manual reset

This document is intentionally shorter than the retired sync spec. Module-specific sync details should live in the relevant module technical design when they carry domain meaning.
