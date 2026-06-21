package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.AuthViewState
import dev.tireless.abun.ui.screens.GuideScreenContent
import dev.tireless.abun.ui.screens.SettingsScreenContent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GuideScreenDebugAuthTest {
    @Test
    fun `guide screen shows prefilled debug credentials`() = runDesktopComposeUiTest {
        setContent {
            GuideScreenContent(
                state = screenshotState(
                    auth = AuthViewState(
                        showGuide = true,
                        mode = AuthMode.GUEST,
                        email = "abun@tireless.dev",
                        otpRequested = true,
                        prefilledOtp = "424242",
                        debugOtpHint = "Debug OTP: 424242",
                    ),
                ),
                onUpdateLoginEmail = {},
                onRequestEmailOtp = {},
                onVerifyEmailOtp = {},
                onSkipLogin = {},
            )
        }

        onNodeWithText("Sign in").fetchSemanticsNode()
        onNodeWithText("OTP code").fetchSemanticsNode()
        onNodeWithText("Debug OTP: 424242").fetchSemanticsNode()
    }

    @Test
    fun `settings shows reopen login action for guest mode`() = runDesktopComposeUiTest {
        setContent {
            SettingsScreenContent(
                state = screenshotState(
                    auth = AuthViewState(showGuide = false, mode = AuthMode.GUEST),
                ),
                onUpdateThemePreference = {},
                onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                onReopenLogin = {},
                onLogout = {},
            )
        }

        onNodeWithText("Login anytime to enable cloud sync on this device.").fetchSemanticsNode()
        onNodeWithText("Open login").fetchSemanticsNode()
    }

    @Test
    fun `settings shows readable relogin message for guest auth expiry`() = runDesktopComposeUiTest {
        setContent {
            SettingsScreenContent(
                state = screenshotState(
                    auth = AuthViewState(
                        showGuide = false,
                        mode = AuthMode.GUEST,
                        errorMessage = "Your session expired. Please log in again.",
                    ),
                ),
                onUpdateThemePreference = {},
                onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                onReopenLogin = {},
                onLogout = {},
            )
        }

        onNodeWithText("Your session expired. Please log in again.").fetchSemanticsNode()
        onNodeWithText("Open login").fetchSemanticsNode()
    }

    @Test
    fun `settings shows signed in session state for authenticated mode`() = runDesktopComposeUiTest {
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
}
