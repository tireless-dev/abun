package dev.tireless.abun.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.tireless.abun.Panel
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.ui.EditorialScreen
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun GuideScreenContent(
    state: AppUiState,
    onUpdateLoginEmail: (String) -> Unit,
    onRequestEmailOtp: () -> Unit,
    onVerifyEmailOtp: (String) -> Unit,
    onSkipLogin: () -> Unit,
) {
    var otpCode by remember(state.auth.prefilledOtp) { mutableStateOf(state.auth.prefilledOtp) }
    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .fillMaxSize(),
    ) {
        EditorialScreen {
            Panel {
                Text("abun", style = ThemeTokens.type.title.copy(fontWeight = FontWeight.Bold), color = ThemeTokens.colors.primary)
                Text("Sign in", style = ThemeTokens.type.display)
                Text("Login with email OTP to enable cloud sync, or skip for local-only mode.", style = ThemeTokens.type.bodyMuted)
                OutlinedTextField(
                    value = state.auth.email,
                    onValueChange = onUpdateLoginEmail,
                    label = { Text("Email", style = ThemeTokens.type.label) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
                Button(onClick = onRequestEmailOtp, enabled = !state.auth.isSubmitting) {
                    Text(if (state.auth.otpRequested) "Resend OTP" else "Send OTP", style = ThemeTokens.type.body.withMaterialContentColor())
                }
                if (state.auth.otpRequested) {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it },
                        label = { Text("OTP code", style = ThemeTokens.type.label) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = ThemeTokens.type.body,
                        singleLine = true,
                    )
                    state.auth.debugOtpHint?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
                    Button(onClick = { onVerifyEmailOtp(otpCode) }, enabled = !state.auth.isSubmitting) {
                        Text("Verify and login", style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
                Button(onClick = onSkipLogin, enabled = !state.auth.isSubmitting) {
                    Text("Skip for now", style = ThemeTokens.type.body.withMaterialContentColor())
                }
                state.auth.errorMessage?.let {
                    Text(it, color = ThemeTokens.colors.error, style = ThemeTokens.type.body)
                }
            }
        }
    }
}
