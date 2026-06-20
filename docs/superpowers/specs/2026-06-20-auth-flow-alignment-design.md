# Auth Flow Alignment Design

## Summary

Abun should expose one shared authentication process across Android, iOS, and desktop. Platform code may continue to own storage and environment wiring, but the user-visible lifecycle and the core decision logic must be the same everywhere:

- launch and attempt session restore
- show guest or authenticated state
- request OTP
- verify OTP
- start sync only after a real authenticated session exists
- expose the same account state from preferences
- log out back to the same guest flow

This design treats the Kotlin Multiplatform shared logic as the source of truth for auth behavior and limits platform-specific code to persistence, device/runtime integration, and debug-build toggles.

## Problem

The current codebase already centralizes most auth logic in shared code, but there are still behavior seams that can drift across platforms:

- platform providers inject debug OTP presets separately
- login guide and preferences account actions are connected by convention rather than an explicit shared auth lifecycle
- local auth persistence is platform-specific and can preserve stale sessions differently unless the shared restore path normalizes them
- readable auth and sync failures are improving, but the design does not yet explicitly define the same account-state semantics for all entry points

This has already surfaced in practice through Android-only confusion around debug OTP and invalid refresh tokens after login.

## Goals

- Define one shared auth lifecycle for Android, iOS, and desktop.
- Ensure debug OTP uses the same main path as production OTP on every platform.
- Keep platform-specific preference storage implementations behind a shared interface.
- Make the login guide and preferences account section reflect the same underlying auth state.
- Standardize readable failures for auth restore, refresh, login, logout, and sync follow-through.
- Preserve local data when auth expires or is revoked.

## Non-Goals

- Replacing OTP email as the authentication method.
- Introducing new server auth endpoints.
- Syncing auth sessions across devices.
- Forcing pixel-identical platform UI.
- Refactoring unrelated preferences fields or navigation structure.

## Current State

### Shared

- `AbunAppController` already owns most auth UI transitions.
- `AuthSessionManager` already owns session persistence, restore, refresh, and logout behavior.
- `AuthRemoteApi` already performs OTP request, OTP verify, refresh, and logout.
- Shared UI already renders the login guide and the preferences account section from `AppUiState.auth`.

### Platform-specific

- Android, iOS, and JVM each implement `LoginPreferenceStore` differently.
- Each platform currently decides whether debug auth preset injection is enabled.
- Platform storage remains the location of the persisted login omission flag, theme preference, and current device auth session.

## Proposed Design

### 1. Shared Auth Lifecycle Is Canonical

All platforms must follow the same shared state machine in `sharedLogic`:

1. App launch initializes auth state from shared logic.
2. Shared restore checks the persisted local session.
3. If the session is valid, the app enters authenticated mode and enables sync.
4. If the access token is stale but refresh is still valid, shared logic refreshes it before sync continues.
5. If the session is expired or revoked, shared logic clears it and enters guest mode without deleting local app data.
6. OTP request and OTP verify always run through the same shared auth orchestration.
7. Successful OTP verification persists a real server-backed session before sync becomes ready.
8. Logout revokes the current device session when possible, clears local session state, and returns to guest mode.

No platform may bypass this flow by constructing authenticated UI state directly from local-only debug placeholders.

### 2. Debug OTP Is a Shared Convenience, Not a Separate Auth Mode

Debug preset behavior should be defined as:

- prefill the shared test email
- prefill or hint the shared test OTP
- optionally auto-show the OTP step
- still perform the same shared request/verify/session exchange path as production login

This keeps debug ergonomics while preventing platform divergence and placeholder refresh-token bugs.

### 3. Login Guide and Preferences Must Reflect the Same Account State

The login guide and preferences account panel should be two views over the same shared auth state, not separate workflows.

Required shared semantics:

- `guest`
  - login guide may be shown or omitted based on local preference
  - preferences shows the account CTA to open login
- `requesting_otp`
  - guide disables duplicate submit actions
  - preferences, if open later, reflects the same underlying guest-not-authenticated state
- `verifying_otp`
  - guide disables duplicate verify actions
- `authenticated`
  - guide is hidden
  - preferences shows signed-in/session controls
  - sync is allowed to run
- `session_expired_or_revoked`
  - local session is cleared
  - app returns to guest semantics
  - the error shown to the user is readable and action-oriented

This does not require identical layouts, but it does require identical state meanings and transition rules.

### 4. Platform Boundary Stays Narrow

Platform code may continue to vary only in these areas:

- preference/session storage backend
- SQLDelight driver creation
- node/device identity provider
- debug-build enablement flag
- platform HTTP engine wiring

Platform code should not decide:

- whether a debug OTP login creates a fake or real session
- whether a stale refresh token is tolerated
- whether sync is allowed before a real session exists
- what auth lifecycle state the user is in

### 5. Readable Error Model

Shared logic should surface readable errors for app-facing auth and sync scenarios.

Minimum expected categories:

- OTP request failure
- OTP verification failure
- session restore failure
- refresh token expired or revoked
- logout failure that still results in a safe local logout
- sync failure caused by missing or invalid auth

User-facing wording should favor clear next steps, for example:

- session expired, please log in again
- sync could not continue because this device session is no longer valid
- OTP verification failed, please request a new code and try again

Underlying logs can remain structured and more detailed.

## Shared State Model

The current `AuthViewState` can continue to back the UI, but the implementation should behave as one state machine with these conceptual states:

- `guest_hidden_guide`
- `guest_showing_guide`
- `requesting_otp`
- `otp_requested`
- `verifying_otp`
- `authenticated`
- `auth_error`

The important requirement is not the exact type name but that transitions are owned in shared logic and tested there.

## Transition Rules

### Startup

- If a valid persisted session exists, enter `authenticated`.
- If only refresh is valid, refresh in shared logic, then enter `authenticated`.
- If neither token is valid, clear the session and enter guest mode.
- If a legacy debug placeholder session is found, clear it and treat the user as guest.

### Login Guide

- Empty email blocks OTP request with a readable local validation message.
- Request OTP enters a submitting state and then `otp_requested` on success.
- Verify OTP enters a submitting state and persists a real session on success.
- After successful login, clear auth errors, hide the guide, mark sync ready, and trigger sync through the normal shared path.

### Preferences Account Section

- Guest users can reopen the login guide.
- Authenticated users can log out.
- If auth expires during sync or restore, the account section returns to guest semantics automatically.

### Logout

- Attempt remote logout when a session exists.
- Clear local session regardless of remote logout result.
- Return to guest mode with sync disabled until a new login succeeds.

## Testing Strategy

Implementation should stay TDD-first and focus on shared tests plus shared UI tests.

Shared logic tests should cover:

- identical debug OTP behavior regardless of injected platform storage
- restore with valid, refreshable, expired, revoked, and legacy-debug sessions
- guide-to-authenticated transition after real OTP verification
- logout transition back to guest mode
- readable auth and sync errors after invalid refresh tokens
- preferences reopening login without creating an authenticated state by itself

Shared UI tests should cover:

- guest account panel in preferences
- authenticated account panel in preferences
- guide screen showing debug OTP hint without implying a separate auth mode
- readable error copy rendering in the guide and sync status surfaces

## Documentation Impact

Implementation following this design should update:

- `docs/base/technical-architecture.md`
  - shared auth state machine ownership
  - narrowed platform responsibility
- `docs/base/information-architecture.md`
  - account-state semantics if wording changes
- `docs/base/sync-architecture.md`
  - auth-driven sync gating and readable failure expectations

## Risks

- If the UI continues to infer account state from loosely related flags instead of explicit shared transitions, drift can return even after cleanup.
- If debug OTP keeps special-case network behavior outside the shared happy path, one platform can regress again while others appear correct.
- If readable errors are only added at the sync layer and not the auth layer, users may still see confusing login outcomes.

## Recommended Implementation Order

1. Add or tighten shared tests that describe the desired cross-platform auth lifecycle.
2. Normalize `AbunAppController` and `AuthSessionManager` around the single shared state machine.
3. Remove any remaining fake-session assumptions from debug OTP handling.
4. Align preferences account behavior and copy with the shared auth states.
5. Update architecture and product docs to match the resulting behavior.
