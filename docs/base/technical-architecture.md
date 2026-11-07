# Base Technical Architecture

## Purpose

This document defines the shared technical shape used by all modules.

## Current Stack

- Kotlin Multiplatform app
- SQLDelight-backed local SQLite database
- shared logic and shared Compose UI
- shared Compose UI uses Material 3 primitives directly for screens, sheets, buttons, text fields, segmented choices, and app chrome, with a small shared editorial design-language layer for screen padding, section framing, flat outlined surfaces, and muted status tags
- shared Compose UI uses Lucide through the Compose Icons KMP artifact for app-owned iconography rendered from `commonMain`, while continuing to rely on Material 3 primitives for controls and app chrome
- Cloudflare Worker with PostgreSQL persistence through Hyperdrive-compatible access connected to the portfolio's central Supabase staging project (`slhqajiltwxbhxlscazz`), with all tables strictly scoped to the `abun` schema and bound to `auth.users(id)`
- Centralized database schema ownership hosted in `../supabase` repository
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
- treating the shared auth/session controller and session manager as the source of truth for login, startup restore, refresh, logout, and guest fallback behavior across Android, iOS, and Desktop
- restoring a persisted auth session on startup, silently refreshing it when only the access token is stale, falling back to guest mode when the refresh session is no longer usable, clearing legacy debug-placeholder sessions instead of treating them as real sync credentials, and surfacing a readable re-login message in shared app state when a revoked or invalid refresh token forces sign-out
- running pull-then-push sync cycles

### Backend

The Worker backend is responsible for:

- authenticating the user and binding identities to central Supabase `auth.users(id)`
- issuing short-lived signed access JWTs plus rotating refresh tokens
- storing revocable per-device sessions for refresh-token rotation and logout in `abun.user_session`
- enforcing ownership boundaries across all task and double-entry finance resources
- validating incoming mutations
- storing canonical synced records in the dedicated `abun` PostgreSQL schema
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

OTP email is the primary auth method, unified with Supabase Auth (`/auth/v1/otp`, `/auth/v1/verify`, `/auth/v1/token?grant_type=refresh_token`, `/auth/v1/user`). The Worker delegates OTP email delivery and session issuance directly to the central Supabase Auth service without local mock OTP backdoors or test email exemptions. Client and worker unit tests mock the Supabase Auth HTTP contract directly.

All persistent product tables reside in the dedicated `abun` PostgreSQL schema and reference the centralized `auth.users(id)` directly. Hyperdrive connection pooling accelerates transactional queries from Cloudflare Workers to the hosted database.

Debug login presets are completely removed from all platforms; all clients (desktop, Android, iOS) present a clean sign-in screen starting with empty email and OTP inputs. The sign-in flow uses a two-step editorial experience: step 1 accepts an email to request a one-time passcode from Supabase Auth; step 2 verifies the OTP with options to change the email or resend the code. A successful shared login clears any stored login omission, hides the guide, resets auth errors, marks sync ready, and enters the normal shared sync path. Alternatively, users may skip the sign-in screen to continue in local-only guest mode.

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
- The Desktop runtime should resolve `ThemePreference.SYSTEM` through a JVM platform adapter that matches the host OS appearance, rather than assuming Compose Desktop’s generic dark-theme signal is correct on every platform.

## Shared UI Boundary

- `app/sharedUI` is the cross-platform source of truth for the app’s visual system across Desktop and Android.
- New shared UI work should prefer shared theme tokens plus the minimal editorial primitives before introducing feature-local spacing, surface, or chip styling.
- The shared layer should stay intentionally small: it exists to enforce consistent layout and surface language, not to wrap every Material 3 control.
- Shared top app bar chrome may host lightweight shared selectors for screen-scoped sub-navigation when the switched content replaces the main body surface rather than filtering a small local region.
- Shared appearance mode is preference-driven and must support `System`, `Light`, and `Dark` from the shared settings flow rather than through platform-specific toggles.
