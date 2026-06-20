# Auth Flow Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align login, session restore, sync gating, and preferences account behavior behind one shared Kotlin Multiplatform auth flow for Android, iOS, and desktop.

**Architecture:** Keep platform-specific code limited to storage and runtime wiring in the `LoginPreferenceStore` providers, while making `AbunAppController` and `AuthSessionManager` the canonical shared auth state machine. Drive both the login guide and the preferences account section from the same shared `AuthViewState` semantics, and surface readable auth-related sync failures through shared logic.

**Tech Stack:** Kotlin Multiplatform, shared Compose UI, SQLDelight, Ktor client, Gradle JVM tests, Compose desktop UI tests

---

## File Map

- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AbunAppController.kt`
  - Normalize login-guide, restore, verify, logout, and sync-error transitions around one shared auth lifecycle.
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AuthSessionManager.kt`
  - Keep persisted sessions normalized, clear invalid sessions consistently, and return readable auth-expiry signals.
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/DomainModels.kt`
  - Add any minimal shared auth-state fields needed to align guide and preferences behavior without platform branching.
- Modify: `app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt`
  - Add TDD coverage for the shared lifecycle and readable invalid-session behavior.
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
  - Align guide copy and preferences account panel behavior with the shared auth lifecycle.
- Modify: `app/sharedUI/src/commonTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt`
  - Add shared UI state tests for auth/account semantics.
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt`
  - Add Compose assertions for readable guest/authenticated account states.
- Modify: `docs/base/technical-architecture.md`
  - Record that auth behavior is shared-first and platform storage is only an adapter boundary.
- Modify: `docs/base/sync-architecture.md`
  - Record auth-driven sync gating and readable invalid-session failure behavior.
- Modify: `docs/base/information-architecture.md`
  - Clarify user-facing account-state semantics if wording needs to match the implementation.

### Task 1: Lock The Shared Auth Lifecycle With Failing Tests

**Files:**
- Modify: `app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt`
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AbunAppController.kt`
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AuthSessionManager.kt`

- [ ] **Step 1: Write the failing shared-logic tests**

```kotlin
@Test
fun `debug preset login exchanges a real session before sync is enabled`() = runTest {
    val controller = testController(
        debugAuthPreset = DebugAuthPreset(
            email = TestSharedAccount.EMAIL,
            otp = TestSharedAccount.OTP,
            accessToken = "debug-access-token",
            userId = "debug-user",
            accessTokenExpiresAtEpochMillis = Long.MAX_VALUE,
            refreshToken = "debug-refresh-token",
            refreshTokenExpiresAtEpochMillis = Long.MAX_VALUE,
        ),
    )

    controller.verifyEmailOtp(TestSharedAccount.OTP)
    waitFor { controller.state.value.auth.mode == AuthMode.AUTHENTICATED }

    assertTrue(controller.state.value.syncState.syncReady)
    assertNull(controller.state.value.auth.errorMessage)
    assertFalse(TestLoginPreferenceStore.lastStoredSessionMatchesPlaceholderTokens())
}

@Test
fun `invalid refresh token returns guest mode with readable auth error`() = runTest {
    val loginPreferenceStore = TestLoginPreferenceStore().apply {
        setAuthSession(
            AuthSession(
                userId = "user-1",
                accessToken = "expired-access",
                accessTokenExpiresAtEpochMillis = 0L,
                refreshToken = "revoked-refresh",
                refreshTokenExpiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )
    }

    val controller = testController(loginPreferenceStore = loginPreferenceStore)
    waitFor { controller.state.value.auth.mode == AuthMode.GUEST }

    assertEquals("Session expired, please log in again.", controller.state.value.auth.errorMessage)
    assertEquals(false, controller.state.value.syncState.syncReady)
}
```

- [ ] **Step 2: Run the shared-logic test target to verify it fails**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest"`

Expected: FAIL with the new auth lifecycle assertions, likely around placeholder-session persistence and missing readable guest-mode error handling.

- [ ] **Step 3: Implement the minimal shared auth lifecycle changes**

```kotlin
private suspend fun completeLogin(session: AuthSession) {
    loginPreferenceStore.setLoginOmitted(false)
    authSessionManager.completeLogin(session)
    _state.value = _state.value.copy(
        auth = _state.value.auth.copy(
            showGuide = false,
            mode = AuthMode.AUTHENTICATED,
            otpRequested = false,
            isSubmitting = false,
            errorMessage = null,
        ),
        syncState = _state.value.syncState.copy(syncReady = true, errorMessage = null),
    )
    requestSync()
}

private fun transitionToGuest(showGuide: Boolean, errorMessage: String? = null) {
    _state.value = _state.value.copy(
        auth = _state.value.auth.copy(
            showGuide = showGuide,
            mode = AuthMode.GUEST,
            otpRequested = false,
            isSubmitting = false,
            errorMessage = errorMessage,
        ),
        syncState = _state.value.syncState.copy(syncReady = false, isSyncing = false),
    )
}
```

- [ ] **Step 4: Run the shared-logic test target to verify it passes**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest"`

Expected: PASS for the new auth lifecycle tests and the existing shared-logic suite.

- [ ] **Step 5: Commit**

```bash
git add app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AbunAppController.kt app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AuthSessionManager.kt
git commit -m "test: lock shared auth lifecycle"
```

### Task 2: Normalize Shared Auth State And Readable Errors

**Files:**
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/DomainModels.kt`
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AbunAppController.kt`
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AuthSessionManager.kt`
- Modify: `app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt`

- [ ] **Step 1: Write the failing state-and-error tests**

```kotlin
@Test
fun `reopen login from settings preserves shared debug convenience but stays guest`() = runTest {
    val controller = testController(
        debugAuthPreset = DebugAuthPreset(
            email = TestSharedAccount.EMAIL,
            otp = TestSharedAccount.OTP,
            accessToken = "debug-access-token",
            userId = "debug-user",
            accessTokenExpiresAtEpochMillis = Long.MAX_VALUE,
            refreshToken = "debug-refresh-token",
            refreshTokenExpiresAtEpochMillis = Long.MAX_VALUE,
        ),
    )

    controller.skipLogin()
    controller.reopenLogin()

    assertTrue(controller.state.value.auth.showGuide)
    assertEquals(AuthMode.GUEST, controller.state.value.auth.mode)
    assertEquals(TestSharedAccount.OTP, controller.state.value.auth.prefilledOtp)
}

@Test
fun `expired restore clears persisted session and keeps local data available`() = runTest {
    val loginPreferenceStore = TestLoginPreferenceStore().apply {
        setAuthSession(
            AuthSession(
                userId = "user-1",
                accessToken = "expired-access",
                accessTokenExpiresAtEpochMillis = 0L,
                refreshToken = "expired-refresh",
                refreshTokenExpiresAtEpochMillis = 0L,
            ),
        )
    }

    val controller = testController(loginPreferenceStore = loginPreferenceStore)
    waitFor { controller.state.value.auth.mode == AuthMode.GUEST }

    assertNull(loginPreferenceStore.authSession())
    assertEquals("Session expired, please log in again.", controller.state.value.auth.errorMessage)
}
```

- [ ] **Step 2: Run the focused test target to verify it fails**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest.reopen login from settings preserves shared debug convenience but stays guest" --tests "dev.tireless.abun.SharedLogicDesktopTest.expired restore clears persisted session and keeps local data available"`

Expected: FAIL because `AuthViewState` transitions and restore error propagation are not fully normalized yet.

- [ ] **Step 3: Implement the minimal state and error normalization**

```kotlin
data class AuthViewState(
    val showGuide: Boolean = true,
    val mode: AuthMode = AuthMode.GUEST,
    val email: String = TestSharedAccount.EMAIL,
    val otpRequested: Boolean = false,
    val prefilledOtp: String = "",
    val debugOtpHint: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val sessionStatusMessage: String? = null,
)

private fun AuthSessionExpiredException.toUserFacingMessage(): String =
    message ?: "Session expired, please log in again."
```

- [ ] **Step 4: Run the full shared-logic test target to verify it passes**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest"`

Expected: PASS with the new restore/error coverage and no regressions in existing tests.

- [ ] **Step 5: Commit**

```bash
git add app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/DomainModels.kt app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AbunAppController.kt app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AuthSessionManager.kt app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt
git commit -m "feat: unify shared auth state transitions"
```

### Task 3: Align Guide And Preferences Account UI With Shared Auth Semantics

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `app/sharedUI/src/commonTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt`
- Modify: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt`

- [ ] **Step 1: Write the failing UI tests**

```kotlin
@Test
fun `guest preferences show login call to action`() {
    val badge = syncStatusBadge(
        AppUiState(
            selectedDate = "2026-06-20",
            auth = AuthViewState(mode = AuthMode.GUEST, showGuide = false),
            syncState = SyncStateView(syncReady = false),
        ),
    )

    assertEquals(SyncBadgeState.LOCAL_ONLY, badge.state)
}

@Test
fun `settings shows signed in session copy for authenticated mode`() = runDesktopComposeUiTest {
    setContent {
        SettingsScreenContent(
            state = screenshotState(
                auth = AuthViewState(showGuide = false, mode = AuthMode.AUTHENTICATED),
            ),
            onUpdateThemePreference = {},
            onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
            onReopenLogin = {},
            onLogout = {},
        )
    }

    onNodeWithText("This device is signed in and can sync with your server account.").fetchSemanticsNode()
    onNodeWithText("Log out").fetchSemanticsNode()
}
```

- [ ] **Step 2: Run the shared UI and desktop UI tests to verify they fail**

Run: `./gradlew :app:sharedUI:allTests --tests "dev.tireless.abun.SharedUICommonTest" --tests "dev.tireless.abun.GuideScreenDebugAuthTest"`

Expected: FAIL if the shared UI copy or visibility rules do not yet match the unified account semantics.

- [ ] **Step 3: Implement the minimal UI alignment**

```kotlin
if (state.auth.mode == AuthMode.GUEST) {
    Panel {
        SectionHeader("Account", "Login")
        Text(
            state.auth.errorMessage ?: "Login anytime to enable cloud sync on this device.",
            style = ThemeTokens.type.bodyMuted,
        )
        Button(onClick = onReopenLogin) {
            Text("Open login", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
} else {
    Panel {
        SectionHeader("Account", "Session")
        Text("This device is signed in and can sync with your server account.", style = ThemeTokens.type.bodyMuted)
        OutlinedButton(onClick = onLogout) {
            Text("Log out", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
}
```

- [ ] **Step 4: Run the shared UI and desktop UI tests to verify they pass**

Run: `./gradlew :app:sharedUI:allTests --tests "dev.tireless.abun.SharedUICommonTest" --tests "dev.tireless.abun.GuideScreenDebugAuthTest"`

Expected: PASS for the shared auth/account UI coverage.

- [ ] **Step 5: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/commonTest/kotlin/dev/tireless/abun/SharedUICommonTest.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt
git commit -m "feat: align shared auth surfaces"
```

### Task 4: Update Architecture Docs And Run Final Verification

**Files:**
- Modify: `docs/base/technical-architecture.md`
- Modify: `docs/base/sync-architecture.md`
- Modify: `docs/base/information-architecture.md`

- [ ] **Step 1: Write the doc changes that reflect the implemented behavior**

```markdown
- the shared auth state machine in `app/sharedLogic` is the source of truth for restore, login, refresh, logout, and sync gating across Android, iOS, and desktop
- platform auth code is limited to preference/session persistence and runtime wiring, and must not create platform-specific authenticated shortcuts
- debug OTP presets may prefill shared test credentials in debug builds, but every platform must exchange a real server-backed session before sync is enabled
- when a device session expires or is revoked, the client clears only the local auth session, preserves local records, returns to guest mode, and shows a readable re-login message
```

- [ ] **Step 2: Run the full verification suite**

Run: `./gradlew :app:sharedLogic:jvmTest :app:sharedUI:jvmTest :app:desktopApp:test :core:jvmTest`

Expected: PASS across shared logic, shared UI, desktop, and core tests.

- [ ] **Step 3: Review git diff to ensure docs and code match**

Run: `git diff -- app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AbunAppController.kt app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AuthSessionManager.kt app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt docs/base/technical-architecture.md docs/base/sync-architecture.md docs/base/information-architecture.md`

Expected: Diff shows one shared auth process, aligned guide/preferences copy, and updated base docs with no platform-specific auth divergence.

- [ ] **Step 4: Commit**

```bash
git add docs/base/technical-architecture.md docs/base/sync-architecture.md docs/base/information-architecture.md
git commit -m "docs: align auth flow architecture"
```

## Self-Review

### Spec coverage

- Shared lifecycle across Android, iOS, and desktop: covered by Tasks 1 and 2.
- Debug OTP following the same main path as production: covered by Tasks 1 and 2.
- Login guide and preferences reflecting the same account state: covered by Task 3.
- Readable auth and sync failures: covered by Tasks 1, 2, and 3.
- Documentation alignment: covered by Task 4.

No spec gaps found.

### Placeholder scan

- No `TODO`, `TBD`, or deferred “implement later” placeholders remain.
- Every task includes exact file paths, commands, and concrete code snippets.
- No step references an undefined subsystem outside the files listed in the file map.

### Type consistency

- `AuthViewState`, `AuthMode`, `SyncStateView`, `AbunAppController`, and `AuthSessionManager` names match the current codebase.
- The plan keeps the shared entry points centered on `reopenLogin`, `verifyEmailOtp`, `completeLogin`, and `transitionToGuest`, which already exist or are directly implied by the current files.

