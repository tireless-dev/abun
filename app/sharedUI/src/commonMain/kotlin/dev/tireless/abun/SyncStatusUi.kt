package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.ui.components.AppText
import dev.tireless.abun.ui.theme.ThemeTokens

internal enum class SyncBadgeState {
    LOCAL_ONLY,
    SYNCING,
    SYNCED,
    ERROR,
}

internal data class SyncStatusBadge(
    val state: SyncBadgeState,
    val label: String,
    val detail: String? = null,
)

internal fun defaultGuideOtpCode(): String = TestSharedAccount.OTP

internal fun syncStatusBadge(state: AppUiState): SyncStatusBadge = when {
    state.auth.mode == AuthMode.GUEST -> SyncStatusBadge(
        state = SyncBadgeState.LOCAL_ONLY,
        label = "Local-only",
    )
    state.syncState.isSyncing -> SyncStatusBadge(
        state = SyncBadgeState.SYNCING,
        label = "Syncing",
    )
    !state.syncState.errorMessage.isNullOrBlank() -> SyncStatusBadge(
        state = SyncBadgeState.ERROR,
        label = "Sync error",
        detail = state.syncState.errorMessage,
    )
    else -> SyncStatusBadge(
        state = SyncBadgeState.SYNCED,
        label = "Synced",
        detail = state.syncState.lastSyncedAt?.let { "Last synced: $it" },
    )
}

@Composable
internal fun SyncStatusPanel(state: AppUiState) {
    val badge = syncStatusBadge(state)
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        Row {
            SyncBadge(badge)
        }
        badge.detail?.let {
            AppText(it, style = ThemeTokens.type.bodyMuted)
        }
    }
}

@Composable
private fun SyncBadge(badge: SyncStatusBadge) {
    val (background, text) = when (badge.state) {
        SyncBadgeState.LOCAL_ONLY -> ThemeTokens.colors.warning.copy(alpha = 0.16f) to ThemeTokens.colors.warning
        SyncBadgeState.SYNCING -> ThemeTokens.colors.primary.copy(alpha = 0.16f) to ThemeTokens.colors.primary
        SyncBadgeState.SYNCED -> ThemeTokens.colors.success.copy(alpha = 0.16f) to ThemeTokens.colors.success
        SyncBadgeState.ERROR -> ThemeTokens.colors.error.copy(alpha = 0.16f) to ThemeTokens.colors.error
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = ThemeTokens.spacing.smDp, vertical = ThemeTokens.spacing.xsDp),
    ) {
        AppText(badge.label, style = ThemeTokens.type.label.copy(fontWeight = FontWeight.Bold), color = text)
    }
}
