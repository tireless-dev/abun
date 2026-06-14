# Login Omission Preference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist "Skip for now" locally so the login guide stays hidden across restarts, while allowing the user to reopen login from Settings.

**Architecture:** Use the existing shared SQLDelight-backed local preference store in `app/sharedLogic` for a local-only `app.auth.login_omitted` flag. Extend `AbunAppController` to derive startup guide visibility from that flag, update it on skip/reopen/login success, and expose a shared Settings action that restores the guide.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, shared Compose UI, Kotlin test, Gradle

---

### Task 1: Add local store support for the login omission flag

**Files:**
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/LocalStore.kt`
- Test: `app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `login omission preference persists in local store`() {
    val store = testStore()

    assertFalse(store.isLoginOmitted())

    store.setLoginOmitted(true)
    assertTrue(store.isLoginOmitted())

    store.setLoginOmitted(false)
    assertFalse(store.isLoginOmitted())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest.login omission preference persists in local store"`

Expected: FAIL because `LocalStore` does not yet define `isLoginOmitted()` or `setLoginOmitted(Boolean)`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
private const val PREF_APP_AUTH_LOGIN_OMITTED = "app.auth.login_omitted"

fun isLoginOmitted(): Boolean =
    queries.selectPreferenceByKey(PREF_APP_AUTH_LOGIN_OMITTED, ::mapPreferenceRow)
        .executeAsOneOrNull()
        ?.entity
        ?.value == "true"

fun setLoginOmitted(isOmitted: Boolean) {
    persistPreferenceEntry(
        key = PREF_APP_AUTH_LOGIN_OMITTED,
        value = if (isOmitted) "true" else null,
        valueType = PreferenceValueType.BOOLEAN,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest.login omission preference persists in local store"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/LocalStore.kt app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt
git commit -m "feat: add local login omission preference"
```

### Task 2: Drive startup, skip, reopen, and login success from the shared preference

**Files:**
- Modify: `app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AbunAppController.kt`
- Test: `app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `skipping login hides guide and next controller start stays omitted`() = runTest {
    val factory = testDatabaseDriverFactory()
    val first = testController(databaseDriverFactory = factory)

    first.skipLogin()

    assertFalse(first.state.value.auth.showGuide)

    val second = testController(databaseDriverFactory = factory)

    assertFalse(second.state.value.auth.showGuide)
    assertEquals(AuthMode.GUEST, second.state.value.auth.mode)
}

@Test
fun `reopen login from settings clears omission and shows guide`() = runTest {
    val factory = testDatabaseDriverFactory()
    val controller = testController(databaseDriverFactory = factory)

    controller.skipLogin()
    controller.reopenLogin()

    assertTrue(controller.state.value.auth.showGuide)

    val second = testController(databaseDriverFactory = factory)
    assertTrue(second.state.value.auth.showGuide)
}

@Test
fun `successful login clears stored omission`() = runTest {
    val factory = testDatabaseDriverFactory()
    val controller = testController(
        databaseDriverFactory = factory,
        debugAuthPreset = DebugAuthPreset(
            email = "abun@tireless.dev",
            otp = "424242",
            accessToken = "debug-token",
            userId = "debug-user",
        ),
    )

    controller.skipLogin()
    controller.verifyEmailOtp("424242")
    waitFor { controller.state.value.auth.mode == AuthMode.AUTHENTICATED }

    val second = testController(databaseDriverFactory = factory)
    assertTrue(second.state.value.auth.showGuide)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest.skipping login hides guide and next controller start stays omitted" --tests "dev.tireless.abun.SharedLogicDesktopTest.reopen login from settings clears omission and shows guide" --tests "dev.tireless.abun.SharedLogicDesktopTest.successful login clears stored omission"`

Expected: FAIL because controller startup ignores stored omission and `reopenLogin()` does not exist.

- [ ] **Step 3: Write minimal implementation**

```kotlin
private val _state = MutableStateFlow(
    AppUiState(
        selectedDate = dependencies.timeProvider.today().toString(),
        syncState = SyncStateView(syncReady = false),
        auth = debugAuthPreset?.let {
            AuthViewState(
                email = it.email,
                otpRequested = true,
                prefilledOtp = it.otp,
                debugOtpHint = "Debug OTP: ${it.otp}",
            )
        } ?: AuthViewState(showGuide = !store.isLoginOmitted()),
    ),
)

fun skipLogin() {
    store.setLoginOmitted(true)
    _state.value = _state.value.copy(
        auth = _state.value.auth.copy(showGuide = false, mode = AuthMode.GUEST, errorMessage = null),
        syncState = _state.value.syncState.copy(syncReady = true),
    )
}

fun reopenLogin() {
    store.setLoginOmitted(false)
    _state.value = _state.value.copy(
        auth = _state.value.auth.copy(showGuide = true, otpRequested = false, errorMessage = null),
    )
}

private fun completeLogin(accessToken: String) {
    store.setLoginOmitted(false)
    authProvider.updateToken(accessToken)
    _state.value = _state.value.copy(
        auth = _state.value.auth.copy(showGuide = false, mode = AuthMode.AUTHENTICATED, isSubmitting = false, otpRequested = false, errorMessage = null),
        syncState = _state.value.syncState.copy(syncReady = true),
    )
    requestSync(immediate = true)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:sharedLogic:jvmTest --tests "dev.tireless.abun.SharedLogicDesktopTest.skipping login hides guide and next controller start stays omitted" --tests "dev.tireless.abun.SharedLogicDesktopTest.reopen login from settings clears omission and shows guide" --tests "dev.tireless.abun.SharedLogicDesktopTest.successful login clears stored omission"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/sharedLogic/src/commonMain/kotlin/dev/tireless/abun/app/AbunAppController.kt app/sharedLogic/src/jvmTest/kotlin/dev/tireless/abun/SharedLogicDesktopTest.kt
git commit -m "feat: persist login omission across restarts"
```

### Task 3: Add the Settings affordance and align docs

**Files:**
- Modify: `app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt`
- Modify: `docs/base/technical-architecture.md`
- Modify: `docs/base/information-architecture.md`
- Test: `app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt`

- [ ] **Step 1: Write the failing UI test**

```kotlin
@Test
fun `settings shows reopen login action for guest mode`() {
    runComposeUiTest {
        setContent {
            SettingsScreenContent(
                state = screenshotAppState(
                    auth = AuthViewState(showGuide = false, mode = AuthMode.GUEST),
                ),
                onUpdateThemePreference = {},
                onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                onReopenLogin = {},
            )
        }

        onNodeWithText("Login anytime").assertExists()
        onNodeWithText("Open login").assertExists()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.GuideScreenDebugAuthTest.settings shows reopen login action for guest mode"`

Expected: FAIL because `SettingsScreenContent` does not render the guest login action or accept `onReopenLogin`.

- [ ] **Step 3: Write minimal implementation and doc updates**

```kotlin
private fun SettingsScreen(state: AppUiState, controller: AbunAppController) {
    SettingsScreenContent(
        state = state,
        onUpdateThemePreference = controller::updateThemePreference,
        onUpdatePreferences = controller::updatePreferences,
        onReopenLogin = controller::reopenLogin,
    )
}

if (state.auth.mode == AuthMode.GUEST) {
    Panel {
        SectionHeader("Account", "Login")
        Text("Login anytime to enable cloud sync on this device.", style = ThemeTokens.type.bodyMuted)
        Button(onClick = onReopenLogin) {
            Text("Open login", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
}
```

```md
- The client persists local-only runtime preferences such as login-guide omission in the shared local preference store when the state should survive app restarts without syncing to the server.
```

```md
- Guest-mode onboarding dismissal is a per-device local behavior. It does not change server-side ownership rules and is not treated as a synced cross-device preference.
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:sharedUI:jvmTest --tests "dev.tireless.abun.GuideScreenDebugAuthTest.settings shows reopen login action for guest mode"`

Expected: PASS

- [ ] **Step 5: Run feature verification**

Run: `./gradlew :app:sharedUI:jvmTest :app:desktopApp:test :core:jvmTest`

Expected: PASS

Run: `./gradlew :app:desktopApp:run`

Expected: Desktop app launches and the login guide can be skipped, stays hidden after restart, and can be reopened from Settings in guest mode.

- [ ] **Step 6: Commit**

```bash
git add app/sharedUI/src/commonMain/kotlin/dev/tireless/abun/App.kt app/sharedUI/src/jvmTest/kotlin/dev/tireless/abun/GuideScreenDebugAuthTest.kt docs/base/technical-architecture.md docs/base/information-architecture.md
git commit -m "feat: add reopen login settings action"
```
