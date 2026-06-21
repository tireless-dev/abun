package dev.tireless.abun.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.tireless.abun.Panel
import dev.tireless.abun.SectionHeader
import dev.tireless.abun.SyncStatusPanel
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.dateFormatFromLabel
import dev.tireless.abun.label
import dev.tireless.abun.themePreferenceFromLabel
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

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

@Composable
internal fun SettingsScreenContent(
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
    var titlePrefix by remember(state.preferences) { mutableStateOf(state.preferences.titlePrefix) }
    var focusMinutes by remember(state.preferences) { mutableStateOf(state.preferences.focusMinutes.toString()) }
    var shortBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.shortBreakMinutes.toString()) }
    var longBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.longBreakMinutes.toString()) }
    var timezoneOverride by remember(state.preferences) { mutableStateOf(state.preferences.timezoneOverride) }
    var selectedDateFormat by remember(state.preferences) { mutableStateOf(state.preferences.dateFormat) }
    var selectedThemePreference by remember(state.preferences) { mutableStateOf(state.preferences.themePreference) }
    var rolloverTime by remember(state.preferences) { mutableStateOf(state.preferences.rolloverTime) }

    Panel {
        SectionHeader("Cloud", "Sync status")
        SyncStatusPanel(state)
    }
    Panel {
        SectionHeader("Defaults", "Task")
        OutlinedTextField(
            value = titlePrefix,
            onValueChange = { titlePrefix = it },
            label = { Text("Title prefix", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
    }
    Panel {
        SectionHeader("Defaults", "Pomodoro")
        OutlinedTextField(
            value = focusMinutes,
            onValueChange = { focusMinutes = it },
            label = { Text("Pomodoro minutes", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = shortBreakMinutes,
            onValueChange = { shortBreakMinutes = it },
            label = { Text("Short break minutes", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = longBreakMinutes,
            onValueChange = { longBreakMinutes = it },
            label = { Text("Long break minutes", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
    }
    Panel {
        SectionHeader("Appearance", "Theme")
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            ThemePreference.entries.forEachIndexed { index, themePreference ->
                val option = themePreference.label()
                SegmentedButton(
                    modifier = Modifier.testTag("theme-option-${themePreference.name.lowercase()}"),
                    selected = option == selectedThemePreference.label(),
                    onClick = {
                        selectedThemePreference = themePreferenceFromLabel(option)
                        onUpdateThemePreference(selectedThemePreference)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemePreference.entries.size),
                ) {
                    Text(option, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
    }
    Panel {
        SectionHeader("App", "Preferences")
        OutlinedTextField(
            value = timezoneOverride,
            onValueChange = { timezoneOverride = it },
            label = { Text("Timezone override", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        OutlinedTextField(
            value = rolloverTime,
            onValueChange = { rolloverTime = it },
            label = { Text("Rollover time (HH:MM)", style = ThemeTokens.type.label) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = ThemeTokens.type.body,
            singleLine = true,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            DateFormatPreference.entries.forEachIndexed { index, dateFormatPreference ->
                val option = dateFormatPreference.label()
                SegmentedButton(
                    selected = option == selectedDateFormat.label(),
                    onClick = { selectedDateFormat = dateFormatFromLabel(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = DateFormatPreference.entries.size),
                ) {
                    Text(option, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }
        Button(
            onClick = {
                onUpdatePreferences(
                    titlePrefix,
                    state.preferences.defaultAlarmLeadMinutes,
                    focusMinutes.toIntOrNull() ?: state.preferences.focusMinutes,
                    shortBreakMinutes.toIntOrNull() ?: state.preferences.shortBreakMinutes,
                    longBreakMinutes.toIntOrNull() ?: state.preferences.longBreakMinutes,
                    timezoneOverride,
                    selectedDateFormat,
                    selectedThemePreference,
                    rolloverTime,
                )
            },
        ) {
            Text("Save", style = ThemeTokens.type.body.withMaterialContentColor())
        }
    }
    if (state.auth.mode == dev.tireless.abun.app.AuthMode.GUEST) {
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
}
