package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.AuthViewState
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
}
