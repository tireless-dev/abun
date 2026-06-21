package dev.tireless.abun.ui.screens

import androidx.compose.runtime.Composable
import dev.tireless.abun.SettingsScreenContent
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.ThemePreference

@Composable
internal fun SettingsScreen(
    state: AppUiState,
    onUpdateThemePreference: (ThemePreference) -> Unit,
    onUpdatePreferences: (
        titlePrefix: String,
        defaultAlarmLeadMinutes: Int,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        timezoneOverride: String,
        dateFormat: DateFormatPreference,
        themePreference: ThemePreference,
        rolloverTime: String,
    ) -> Unit,
    onReopenLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    SettingsScreenContent(
        state = state,
        onUpdateThemePreference = onUpdateThemePreference,
        onUpdatePreferences = onUpdatePreferences,
        onReopenLogin = onReopenLogin,
        onLogout = onLogout,
    )
}
