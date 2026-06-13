# Login Omission Preference Design

## Purpose

Persist the user's "Skip for now" login choice locally so the onboarding login guide does not reappear on the next app start, while still allowing the user to reopen login from Settings whenever they want to enable sync.

## Problem

The current shared auth guide is controlled only by in-memory UI state. If the user skips login, the app enters guest mode for the current session, but the guide reappears after the next launch. That breaks the intended "local-only for now" flow and makes guest mode feel non-persistent.

## Goals

- Persist login omission as a local shared preference for the KMP app.
- Apply the behavior consistently across Desktop and Android through shared logic.
- Keep the omission state local-only and not part of synced server preferences.
- Allow the user to reopen login from Settings at any time.

## Non-Goals

- Redesign the OTP login flow.
- Add platform-native preference storage outside the shared local store.
- Sync login omission state to the server or other devices.
- Add integration tests.

## User-Facing Behavior

### Initial startup

- If the user has never skipped login, the app shows the login guide on startup.
- If the user previously chose "Skip for now," the app starts directly in guest mode with the login guide hidden.

### Skip for now

- Tapping "Skip for now" hides the login guide immediately.
- The app remains usable in local-only guest mode.
- The app stores a local preference indicating that the startup login guide was omitted.

### Reopen login

- Settings includes an auth-focused action that reopens the login guide.
- Reopening login clears the local omission preference so the guide is no longer suppressed.
- The guide should appear immediately in the current session after the user chooses the Settings action.

### Successful login

- Completing login clears any previously stored omission preference.
- After successful login, future launches should continue through the authenticated flow rather than treating the user as a skipped guest.

## Architecture

### Persistence boundary

Use the existing shared SQLDelight-backed `preference` table in `app/sharedLogic` as the persistence boundary for this feature.

- Add a dedicated local preference key for login omission.
- Read and write that key from shared logic rather than platform-specific storage.
- Treat the key as local UI/runtime state, not as part of synced `PreferencesViewState`.

### Controller behavior

`AbunAppController` becomes responsible for:

- reading the stored omission value during initialization
- deriving the initial `AuthViewState.showGuide` value from that local preference when no debug login preset is active
- persisting omission when login is skipped
- clearing omission when login is reopened from Settings
- clearing omission after successful login

### UI behavior

`app/sharedUI` should:

- keep the existing guide presentation model based on `state.auth.showGuide`
- keep the existing skip button in the guide
- add a small Settings affordance for reopening login while in guest mode

The Settings affordance should follow the existing shared editorial Material 3 patterns and avoid feature-local styling.

## Data Model

Add one local preference key in shared logic:

- `app.auth.login_omitted`

Stored values:

- `"true"` when the user has chosen to skip login
- `null` when omission is not active

This state is intentionally separate from synced app preferences because it represents a per-device onboarding dismissal choice rather than a cross-device product preference.

## State Rules

- Debug auth preset continues to override the ordinary startup guide flow for development.
- Guest mode with omitted login must still show the existing local-only status copy in the app.
- Reopening login does not authenticate the user by itself; it only restores the guide.
- Logging in successfully should result in `showGuide = false` and `mode = AUTHENTICATED`.

## Testing

Implementation should follow TDD with failing tests first.

Required shared-logic coverage:

- skipping login persists the omission flag
- a new controller instance hides the guide when the omission flag exists
- reopening login from Settings clears the omission flag and shows the guide
- successful login clears a previously stored omission flag

Relevant shared UI tests may be added if needed to verify the Settings affordance, but the primary behavior should be covered in shared logic tests.

## Documentation Alignment

Before the feature is complete:

- update base technical architecture docs to describe login-guide omission as a shared local preference stored in the client persistence layer
- update base information architecture docs to note that guest-mode onboarding dismissal is a local-only behavior and not a synced ownership concept

If implementation details remain incomplete, mark them `[TBI]` rather than leaving the docs ambiguous.
