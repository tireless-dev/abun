package dev.tireless.abun

import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.AuthMode
import dev.tireless.abun.app.AuthViewState
import dev.tireless.abun.app.SyncStateView
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedUICommonTest {

    @Test
    fun `guide screen otp defaults to shared test otp`() {
        assertEquals(TestSharedAccount.OTP, defaultGuideOtpCode())
    }

    @Test
    fun `guest preferences show local only badge`() {
        val badge = syncStatusBadge(
            AppUiState(
                selectedDate = "2026-05-30",
                auth = AuthViewState(mode = AuthMode.GUEST),
                syncState = SyncStateView(),
            ),
        )

        assertEquals(SyncBadgeState.LOCAL_ONLY, badge.state)
        assertEquals("Local-only", badge.label)
        assertEquals(null, badge.detail)
    }

    @Test
    fun `syncing preferences show syncing badge`() {
        val badge = syncStatusBadge(
            AppUiState(
                selectedDate = "2026-05-30",
                auth = AuthViewState(mode = AuthMode.AUTHENTICATED),
                syncState = SyncStateView(isSyncing = true),
            ),
        )

        assertEquals(SyncBadgeState.SYNCING, badge.state)
        assertEquals("Syncing", badge.label)
    }

    @Test
    fun `failed sync preferences show error badge and detail`() {
        val badge = syncStatusBadge(
            AppUiState(
                selectedDate = "2026-05-30",
                auth = AuthViewState(mode = AuthMode.AUTHENTICATED),
                syncState = SyncStateView(errorMessage = "Network down"),
            ),
        )

        assertEquals(SyncBadgeState.ERROR, badge.state)
        assertEquals("Sync error", badge.label)
        assertEquals("Network down", badge.detail)
    }

    @Test
    fun `successful sync preferences show synced badge and timestamp`() {
        val badge = syncStatusBadge(
            AppUiState(
                selectedDate = "2026-05-30",
                auth = AuthViewState(mode = AuthMode.AUTHENTICATED),
                syncState = SyncStateView(lastSyncedAt = "2026-05-30T14:20:00Z"),
            ),
        )

        assertEquals(SyncBadgeState.SYNCED, badge.state)
        assertEquals("Synced", badge.label)
        assertEquals("Last synced: 2026-05-30T14:20:00Z", badge.detail)
    }
}
