# Cloudflare Workers Server Migration Design

## Goal

> Historical note: this design document was written before the Worker cutover. It describes the migration target and still references the retired Ktor server and the pre-cutover route layout.

Replace the current JVM-only `:server` module with a Cloudflare Workers backend while preserving full application functionality for the Kotlin Multiplatform clients, keeping PostgreSQL on Supabase as the source of truth, retaining the current custom email OTP flow, and using Bun for local package management, scripts, and test execution.

Target stack after migration:

- Kotlin Multiplatform clients
- ReactJS web client
- Cloudflare Workers runtime in production
- Supabase PostgreSQL database
- Bun for local TypeScript/Workers tooling

## Scope

In scope:

- Replace the current Ktor/Netty/JDBC server implementation
- Preserve `/auth`, `/sync`, and `/api` functionality
- Preserve sync conflict behavior and server-version cursor semantics
- Preserve custom OTP auth behavior
- Keep PostgreSQL and the existing database-oriented domain model
- Add a Worker-native test suite and local Bun-based workflow
- Remove the current `:server` module only after parity is proven

Out of scope:

- Migrating auth to Supabase Auth
- Replacing PostgreSQL with D1 or another Cloudflare-native database
- Automated integration/browser testing for the web app
- UI redesign of Desktop, Android, or React clients

## Current State

The current backend is implemented in the `:server` Gradle module and is tightly bound to the JVM:

- Ktor routing and server lifecycle in `server/src/main/kotlin/dev/tireless/abun/Application.kt`
- Netty runtime via `ktor-server-netty`
- JDBC, HikariCP, and `java.sql` transactions in `server/src/main/kotlin/dev/tireless/abun/SyncServices.kt`
- Environment configuration through `System.getenv()`

This backend cannot be deployed to Cloudflare Workers as-is because Workers run on `workerd`, not the JVM. The API contract is relatively clean, however, and the KMP clients already depend on a stable HTTP surface through `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/SyncRemoteApi.kt`.

## Chosen Approach

Build a new Worker-native backend beside the current server, preserve the external HTTP contract, prove parity with tests, then switch clients by configuration and remove the old `:server` module.

This is preferred over a big-bang rewrite because:

- Desktop and Android clients can continue using the same wire contract
- The highest-risk change stays concentrated in the backend
- Manual verification can continue through the Desktop app
- The future React client can target the same API surface

## Runtime And Tooling Decisions

### Production Runtime

- Deploy on Cloudflare Workers
- Use the standard Workers runtime (`workerd`)
- Pin a current Workers compatibility date in Wrangler configuration
- Use the latest stable Wrangler v4 line that is compatible with the repo at implementation time

### Local Tooling

- Use Bun for package installation, task running, and test execution
- Use local `wrangler` installed through Bun
- Generate Worker runtime types with `wrangler types`

### Database

- Keep Supabase PostgreSQL as the source of truth
- Connect from the Worker to Postgres using a Worker-compatible approach
- Preserve transactional behavior for sync writes and OTP verification flows

## Target Architecture

### Backend Package Layout

Introduce a new Worker application outside the current JVM server path, for example under a dedicated `workers/` or `server-workers/` directory. The exact directory name can be finalized in the implementation plan, but it should be clearly separate from the KMP and desktop/android modules.

The Worker app should contain:

- Request routing for `/auth`, `/sync`, and `/api`
- Environment binding definitions and configuration loading
- Postgres access layer
- Domain services mirroring the current server responsibilities
- Serialization and validation helpers
- Worker-side tests

### Service Decomposition

Split the Worker backend into focused units:

- `auth` service: custom OTP request, verification, token parsing, user bootstrap
- `sync` services: resource-specific pull/push operations and conflict resolution
- `business api` services: CRUD endpoints that shape data for higher-level app workflows
- `db` layer: query execution, transactions, schema bootstrap/migration hooks if needed
- `http` layer: route mapping, auth extraction, error-to-response translation

The Worker should not become a single large file. The existing Kotlin server already mixes runtime wiring with domain logic; the migration is an opportunity to enforce sharper boundaries without changing behavior.

## HTTP Contract

The public API contract should remain stable during migration.

Required route groups:

- `/auth/otp/request`
- `/auth/otp/verify`
- `/sync/preferences`
- `/sync/routines`
- `/sync/tasks`
- `/sync/alarms`
- `/sync/task-events`
- `/sync/pomodoro-sessions`
- `/api/preferences`
- `/api/routines`
- `/api/tasks`
- `/api/alarms`
- `/api/task-events`
- `/api/pomodoro-sessions`

Behavioral requirements:

- Preserve request/response JSON structure expected by `SyncRemoteApi`
- Preserve authorization header behavior
- Preserve HTTP status semantics for validation, unauthorized access, and not found cases
- Preserve batch push/pull conventions for sync resources
- Preserve business API semantics for create, patch, delete, and derived task status/event flows

The migration should be contract-preserving first. Any API cleanup should be treated as a later, separate change.

## Authentication Design

The current custom email OTP flow will be preserved.

Requirements:

- Keep `POST /auth/otp/request`
- Keep `POST /auth/otp/verify`
- Keep deterministic shared test account behavior
- Keep user creation on successful verification
- Keep bearer-token style user identity propagation for existing clients

Implementation notes:

- Recreate the current `otp_code` and `user_account` interactions in Worker-compatible SQL
- Preserve the current token format initially for compatibility
- Encapsulate token parsing behind a small auth boundary so a future move to Supabase Auth is possible without rewriting the whole HTTP layer

## Persistence Design

Supabase PostgreSQL remains the system of record. The Worker persistence layer must preserve the semantics currently implemented through JDBC transactions.

Requirements:

- Support transactional writes for sync merges and OTP verification
- Preserve the `sync_server_version` monotonic counter behavior
- Preserve per-user scoping for all sync and business resources
- Preserve soft delete behavior
- Preserve resource-specific merge logic and accepted/rejected field tracking

Important constraint:

The current server assumes row-level transactional correctness. The Worker implementation must not weaken this behavior by replacing transactional SQL with eventually consistent or partially ordered writes.

## Sync Behavior

The sync protocol is the most important compatibility boundary.

Must preserve:

- Cursor-based incremental pull
- Batch push endpoints returning canonical server state
- Hybrid logical clock conflict resolution behavior
- `acceptedFields` and `rejectedFields` semantics
- Resource ordering and `nextCursor` behavior
- Task event derived status behavior

Design choice:

The Kotlin shared sync models remain the source of truth for client behavior. The Worker implementation should mirror those semantics exactly, using parity tests against the current server behavior as the reference.

## Client Impact

### Kotlin Multiplatform Clients

The KMP clients should need minimal code changes if the API contract is preserved.

Expected changes:

- Base URL configuration updates
- Possible auth configuration cleanup if environment names change
- No protocol rewrite in `SyncRemoteApi`

### ReactJS Client

The future React client should target the same backend contract. This migration should avoid introducing mobile-only or desktop-only assumptions into the API layer.

## Error Handling

The Worker backend should preserve the practical semantics of the current Ktor `StatusPages` setup.

Requirements:

- Validation failures return `400`
- Unauthorized cases return `401`
- Missing resources return `404`
- Unexpected failures return `500` with safe error bodies

Implementation rule:

Centralize HTTP error mapping in the Worker entry layer so resource services remain mostly transport-agnostic.

## Testing Strategy

This repo requires TDD, and the migration should preserve that discipline.

Test layers:

- Worker unit tests for auth, sync merge rules, and business resource behaviors
- Worker route/component tests for `/auth`, `/sync`, and `/api`
- Contract/parity tests that compare Worker behavior to the current server expectations
- Existing Kotlin tests retained where they still validate shared client/domain behavior

Verification focus:

- No automated integration tests are required for this migration
- Manual final verification should use the Desktop app
- Web app dev server and web screenshots remain out of scope unless explicitly requested

## Rollout Plan

### Phase 1: Freeze Behavior

- Audit and document the current Ktor server behavior
- Expand tests where behavior is implicit or under-specified
- Identify all schema and route dependencies

### Phase 2: Build Worker Backend In Parallel

- Scaffold the Worker app with Bun + Wrangler
- Implement route groups and persistence incrementally under TDD
- Point the Worker at Supabase Postgres
- Achieve route-by-route parity with the current server

### Phase 3: Switch Clients By Configuration

- Keep client API calls stable
- Update environment/configuration to point KMP clients and React to the Worker backend
- Verify manual sync and CRUD behavior through the Desktop app

### Phase 4: Remove Legacy JVM Server

- Delete the `:server` module only after parity and manual verification succeed
- Remove unused Gradle includes and JVM server dependencies
- Retain historical schema/docs as needed, but eliminate dead runtime paths

## Risks And Mitigations

### Transaction Semantics Risk

Risk:
Worker-compatible Postgres access may behave differently from the current JDBC transaction model.

Mitigation:
Choose a transaction-capable Postgres access strategy up front, and write parity tests for the exact flows that rely on atomicity.

### Sync Regression Risk

Risk:
Small differences in merge or cursor behavior could silently corrupt cross-device sync expectations.

Mitigation:
Freeze the contract with tests before removal of the JVM server and compare Worker responses against known-good cases.

### Auth Compatibility Risk

Risk:
Changing token or OTP behavior could break existing clients.

Mitigation:
Preserve the current token format and shared test account semantics in the first migration.

### Scope Creep Risk

Risk:
Bundling React client work, auth redesign, and backend rewrite together would slow delivery and blur failures.

Mitigation:
Treat this as a backend replacement first. React integration should consume the preserved contract rather than redefine it.

## Success Criteria

The migration is complete when:

- The Worker backend implements all current `/auth`, `/sync`, and `/api` functionality
- Supabase PostgreSQL remains the source of truth
- Custom OTP auth still works end to end
- Desktop and Android clients work against the Worker backend without protocol changes
- Manual Desktop verification passes
- The legacy `:server` module is removed
- Bun is the local tooling path for Worker development

## Open Implementation Decisions To Finalize In The Plan

These choices should be resolved in the implementation plan rather than left ambiguous during coding:

- Exact Worker project directory and package layout
- Exact Worker-compatible Postgres client/library choice
- Whether schema bootstrap stays in app code or moves fully to SQL migrations
- How parity fixtures are stored and organized
- Exact cutover sequence for local, staging, and production environments
