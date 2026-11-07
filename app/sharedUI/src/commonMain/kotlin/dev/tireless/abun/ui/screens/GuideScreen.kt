package dev.tireless.abun.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.ShieldCheck
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun GuideScreenContent(
    state: AppUiState,
    onUpdateLoginEmail: (String) -> Unit,
    onRequestEmailOtp: () -> Unit,
    onVerifyEmailOtp: (String) -> Unit,
    onResetLoginOtp: () -> Unit = {},
    onSkipLogin: () -> Unit,
) {
    var otpCode by remember(state.auth.prefilledOtp) { mutableStateOf(state.auth.prefilledOtp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeTokens.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ThemeTokens.spacing.screenPaddingDp, vertical = ThemeTokens.spacing.xlDp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.lgDp),
        ) {
            // Header / Brand
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
            ) {
                Surface(
                    shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                    color = ThemeTokens.colors.primary.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, ThemeTokens.colors.primary.copy(alpha = 0.20f)),
                ) {
                    Text(
                        text = "ABUN",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = ThemeTokens.type.label.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.5.sp,
                        ),
                        color = ThemeTokens.colors.primary,
                    )
                }
                Text(
                    text = if (state.auth.otpRequested) "Check your inbox" else "Sign in",
                    style = ThemeTokens.type.display,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (state.auth.otpRequested) {
                        "We sent a 6-digit verification code to your email. Enter it below to continue."
                    } else {
                        "Sign in with your email to enable cloud sync across all your devices, or continue in local-only mode."
                    },
                    style = ThemeTokens.type.bodyMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = ThemeTokens.spacing.xsDp),
                )
            }

            // Main Card
            EditorialCard(
                modifier = Modifier.fillMaxWidth(),
                testTag = "guide-login-card",
                contentPadding = PaddingValues(ThemeTokens.spacing.lgDp),
                contentSpacing = ThemeTokens.spacing.mdDp,
            ) {
                // Error Notice Banner
                state.auth.errorMessage?.let { errorText ->
                    Surface(
                        shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                        color = ThemeTokens.colors.error.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, ThemeTokens.colors.error.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().testTag("guide-error-banner"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Lucide.CircleAlert,
                                contentDescription = null,
                                tint = ThemeTokens.colors.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = errorText,
                                style = ThemeTokens.type.bodyMuted.copy(color = ThemeTokens.colors.error),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (!state.auth.otpRequested) {
                    // Step 1: Email Input
                    OutlinedTextField(
                        value = state.auth.email,
                        onValueChange = onUpdateLoginEmail,
                        label = { Text("Email address", style = ThemeTokens.type.label) },
                        placeholder = { Text("you@example.com", style = ThemeTokens.type.bodyMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Lucide.Mail,
                                contentDescription = null,
                                tint = ThemeTokens.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("guide-email-input"),
                        textStyle = ThemeTokens.type.body,
                        singleLine = true,
                        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ThemeTokens.colors.primary,
                            unfocusedBorderColor = ThemeTokens.colors.border,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (state.auth.email.isNotBlank() && !state.auth.isSubmitting) {
                                    onRequestEmailOtp()
                                }
                            },
                        ),
                    )

                    Button(
                        onClick = onRequestEmailOtp,
                        enabled = !state.auth.isSubmitting && state.auth.email.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("guide-send-otp-btn"),
                        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
                    ) {
                        if (state.auth.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Sending code...",
                                style = ThemeTokens.type.body.withMaterialContentColor(),
                            )
                        } else {
                            Text(
                                text = "Send verification code",
                                style = ThemeTokens.type.body.withMaterialContentColor().copy(fontWeight = FontWeight.Medium),
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Lucide.ArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                } else {
                    // Step 2: OTP Verification
                    // Email Summary Surface with Change Button
                    Surface(
                        shape = RoundedCornerShape(ThemeTokens.radii.smallDp),
                        color = ThemeTokens.colors.surfaceMuted,
                        border = BorderStroke(1.dp, ThemeTokens.colors.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Lucide.Mail,
                                    contentDescription = null,
                                    tint = ThemeTokens.colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = state.auth.email,
                                    style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                )
                            }
                            TextButton(
                                onClick = onResetLoginOtp,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "Change",
                                    style = ThemeTokens.type.label.copy(fontWeight = FontWeight.Medium),
                                    color = ThemeTokens.colors.primary,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            if (filtered.length <= 6) {
                                otpCode = filtered
                            }
                        },
                        label = { Text("OTP code", style = ThemeTokens.type.label) },
                        placeholder = { Text("123456", style = ThemeTokens.type.bodyMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Lucide.KeyRound,
                                contentDescription = null,
                                tint = ThemeTokens.colors.textSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("guide-otp-input"),
                        textStyle = ThemeTokens.type.cardTitle.copy(
                            letterSpacing = 6.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ThemeTokens.colors.primary,
                            unfocusedBorderColor = ThemeTokens.colors.border,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (otpCode.isNotBlank() && !state.auth.isSubmitting) {
                                    onVerifyEmailOtp(otpCode)
                                }
                            },
                        ),
                    )

                    Button(
                        onClick = { onVerifyEmailOtp(otpCode) },
                        enabled = !state.auth.isSubmitting && otpCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("guide-verify-btn"),
                        shape = RoundedCornerShape(ThemeTokens.radii.mediumDp),
                    ) {
                        if (state.auth.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Verifying...",
                                style = ThemeTokens.type.body.withMaterialContentColor(),
                            )
                        } else {
                            Icon(
                                imageVector = Lucide.ShieldCheck,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Verify and login",
                                style = ThemeTokens.type.body.withMaterialContentColor().copy(fontWeight = FontWeight.Medium),
                            )
                        }
                    }

                    // Resend Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Didn't receive the email?",
                            style = ThemeTokens.type.bodyMuted,
                        )
                        TextButton(
                            onClick = onRequestEmailOtp,
                            enabled = !state.auth.isSubmitting,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "Resend OTP",
                                style = ThemeTokens.type.bodyMuted.copy(fontWeight = FontWeight.SemiBold),
                                color = ThemeTokens.colors.primary,
                            )
                        }
                    }
                }
            }

            // Bottom Local-only Guest Option (Understated TextButton)
            TextButton(
                onClick = onSkipLogin,
                enabled = !state.auth.isSubmitting,
                modifier = Modifier.testTag("guide-skip-btn"),
            ) {
                Text(
                    text = "Skip for now · Continue as guest",
                    style = ThemeTokens.type.bodyMuted.copy(color = ThemeTokens.colors.textSecondary),
                )
            }
        }
    }
}
