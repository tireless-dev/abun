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

The current implementation uses this topological pull order across all 14 sync scopes:

1. preferences
2. routines
3. tasks
4. alarms
5. task events
6. pomodoro sessions
7. finance accounts
8. finance exchange rates
9. finance clearing groups
10. finance transactions
11. finance splits
12. finance split clearing groups
13. finance amortization plans
14. finance budget rules

The exact same topological order is used for push.

## Chunked Batching & Breakpoint Resumption

For initial imports, large ledger migrations, and routine sync:

### Chunked Push (`pushPaged`)
- Dirty records are queried in chunks (default 50 items per batch) to fit within HTTP payload and execution timeout limits.
- After each batch is accepted by the server, the client immediately merges accepted field updates and clears dirty flags in local SQLite before fetching the next chunk.
- If network drops or an error occurs on batch $N$, records in batches $1$ through $N-1$ remain marked clean in local SQLite. Retrying `syncNow()` resumes directly from batch $N$ without re-pushing already accepted items.
- A progress guard detects if a batch produces zero net decrease in remaining dirty records, breaking the loop and logging an error to prevent infinite spinning.

### Paged Pull (`pullPaged`)
- Pull requests are paginated (default 500 items per page).
- After each non-empty page is merged into SQLite, the local scope cursor is updated and committed immediately to `sync_state`.
- If pull is interrupted on page $M$, retrying resumes directly from page $M$'s last saved cursor without re-fetching pages $1$ through $M-1$.

### Server Batch Optimizations
- All item writes within a batch are executed within a single database transaction (`withTransaction`).
- Existing records are fetched in a single query via bulk lookup (`id = ANY($2)`), avoiding $O(N)$ individual select queries.
- Sequential server version numbers are reserved upfront in a single roundtrip via `reserveServerVersions(db, count)`, eliminating per-item version generation roundtrips over Hyperdrive/Supabase connections.

### Automatic Mutation Triggering
All client-side mutative operations in `AbunAppController` (including tasks, routines, alarms, pomodoros, preferences, finance accounts, transactions, splits, clearing groups, amortization plans, and budget rules) invoke `requestSync()`. This debounces local modifications (default 750ms) before initiating a background sync loop, ensuring changes are promptly pushed to the cloud and propagated across clients without requiring manual sync triggers or app restarts.

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
- `GET|POST /api/sync/finance-accounts`
- `GET|POST /api/sync/finance-exchange-rates`
- `GET|POST /api/sync/finance-clearing-groups`
- `GET|POST /api/sync/finance-transactions`
- `GET|POST /api/sync/finance-splits`
- `GET|POST /api/sync/finance-split-clearing-groups`
- `GET|POST /api/sync/finance-amortization-plans`
- `GET|POST /api/sync/finance-budget-rules`

Sync and direct business calls now have an auth precondition:

- `/api/sync/*` and `/api/*` require a valid access JWT
- shared auth/session logic is the source of truth for login, restore, refresh, logout, and sync gating before these requests run
- the client must obtain a guaranteed-valid access token before each sync/API request
- the client should refresh proactively shortly before access-token expiry
- one defensive retry is allowed for unexpected `401` responses caused by drift or server-side revocation
- debug OTP shortcuts may prefill credentials in development, but they must still complete a real `/auth/request` plus `/auth/verify` exchange before the first sync cycle starts
- if refresh fails or the session is revoked, the client clears the local auth session, returns to guest mode, disables sync readiness, keeps local SQLite data available for later re-sync after login, and exposes a readable re-login message through shared app state

This guest fallback is an auth-state transition only. It does not delete local task data or reset local SQLite state.

When an authenticated session is established (via OTP verification or startup restore), the client reconciles local SQLite state against `lastSyncedUserId` persisted in platform preferences:

- **First login from pure Guest mode** (`lastSyncedUserId == null` and no local rows have `server_version > 0`): local dirty rows are preserved and pushed into the newly authenticated account during the initial sync cycle.
- **Re-login as the same user** (`lastSyncedUserId == session.userId`): existing synced and offline dirty rows are preserved and synced incrementally.
- **Account switch or legacy unattributed synced rows** (`lastSyncedUserId != session.userId`, or `lastSyncedUserId == null` while local SQLite contains already-synced `server_version > 0` records): local SQLite tables are cleared, default starter accounts/rules are re-seeded, and all `sync_state` cursors reset to `0` before pulling the authenticated user's data.

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
