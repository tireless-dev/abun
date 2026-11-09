package dev.tireless.abun.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun TaskSettingsScreen(
    state: AppUiState,
    onUpdateTaskPreferences: (
        titlePrefix: String,
        defaultAlarmLeadMinutes: Int,
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
    ) -> Unit,
) {
    val s = ThemeTokens.strings
    var titlePrefix by remember(state.preferences) { mutableStateOf(state.preferences.titlePrefix) }
    var defaultAlarmLeadMinutes by remember(state.preferences) { mutableStateOf(state.preferences.defaultAlarmLeadMinutes.toString()) }
    var focusMinutes by remember(state.preferences) { mutableStateOf(state.preferences.focusMinutes.toString()) }
    var shortBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.shortBreakMinutes.toString()) }
    var longBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.longBreakMinutes.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task-settings-screen")
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.lgDp),
    ) {
        // Card 1: Task Defaults
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-task-defaults"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = s.taskSettings.defaultsCard,
                accentDotColor = ThemeTokens.colors.tasksAccent,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = titlePrefix,
                onValueChange = { titlePrefix = it },
                label = { Text(s.taskSettings.taskTitlePrefix, style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth().testTag("task-settings-title-prefix-field"),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
                colors = ThemeTokens.outlinedTextFieldColors(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = defaultAlarmLeadMinutes,
                onValueChange = { defaultAlarmLeadMinutes = it },
                label = { Text(s.taskSettings.defaultAlarmLeadMinutes, style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth().testTag("task-settings-alarm-lead-field"),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
                colors = ThemeTokens.outlinedTextFieldColors(),
            )
        }

        // Card 2: Focus & Pomodoro Defaults
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-focus-defaults"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = s.taskSettings.focusCard,
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = focusMinutes,
                    onValueChange = { focusMinutes = it },
                    label = { Text(s.taskSettings.focusMinutes, style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f).testTag("task-settings-focus-minutes-field"),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                    colors = ThemeTokens.outlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = shortBreakMinutes,
                    onValueChange = { shortBreakMinutes = it },
                    label = { Text(s.taskSettings.shortBreakMinutes, style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f).testTag("task-settings-short-break-field"),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                    colors = ThemeTokens.outlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = longBreakMinutes,
                    onValueChange = { longBreakMinutes = it },
                    label = { Text(s.taskSettings.longBreakMinutes, style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f).testTag("task-settings-long-break-field"),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                    colors = ThemeTokens.outlinedTextFieldColors(),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onUpdateTaskPreferences(
                        titlePrefix,
                        defaultAlarmLeadMinutes.toIntOrNull() ?: state.preferences.defaultAlarmLeadMinutes,
                        focusMinutes.toIntOrNull() ?: state.preferences.focusMinutes,
                        shortBreakMinutes.toIntOrNull() ?: state.preferences.shortBreakMinutes,
                        longBreakMinutes.toIntOrNull() ?: state.preferences.longBreakMinutes,
                    )
                },
                modifier = Modifier.testTag("task-settings-save-button"),
                colors = ThemeTokens.buttonColors(),
            ) {
                Text(s.taskSettings.saveSettings, style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }
    }
}
