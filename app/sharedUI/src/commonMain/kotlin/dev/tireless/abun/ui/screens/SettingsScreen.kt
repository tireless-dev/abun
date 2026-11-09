package dev.tireless.abun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tireless.abun.SyncStatusPanel
import dev.tireless.abun.app.AppModule
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.DateFormatPreference
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.core.ota.BuildInfo
import dev.tireless.abun.dateFormatFromLabel
import dev.tireless.abun.label
import dev.tireless.abun.ota.OtaStatus
import dev.tireless.abun.themePreferenceFromLabel
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.SwissHairlineDivider
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun SettingsScreen(
    state: AppUiState,
    otaStatus: OtaStatus = OtaStatus.Idle,
    isAutoUpdateEnabled: Boolean = true,
    onManualCheckUpdate: () -> Unit = {},
    onTriggerOtaUpdate: () -> Unit = {},
    onUpdateAutoUpdateEnabled: (Boolean) -> Unit = {},
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
    onToggleModule: (AppModule, Boolean) -> Unit = { _, _ -> },
    onLoadPredefinedFinanceAccounts: () -> Unit = {},
    onResetFinanceToDefaultAccounts: () -> Unit = {},
) {
    SettingsScreenContent(
        state = state,
        otaStatus = otaStatus,
        isAutoUpdateEnabled = isAutoUpdateEnabled,
        onManualCheckUpdate = onManualCheckUpdate,
        onTriggerOtaUpdate = onTriggerOtaUpdate,
        onUpdateAutoUpdateEnabled = onUpdateAutoUpdateEnabled,
        onUpdateThemePreference = onUpdateThemePreference,
        onUpdatePreferences = onUpdatePreferences,
        onReopenLogin = onReopenLogin,
        onLogout = onLogout,
        onToggleModule = onToggleModule,
        onLoadPredefinedFinanceAccounts = onLoadPredefinedFinanceAccounts,
        onResetFinanceToDefaultAccounts = onResetFinanceToDefaultAccounts,
    )
}

@Composable
internal fun SettingsScreenContent(
    state: AppUiState,
    otaStatus: OtaStatus = OtaStatus.Idle,
    isAutoUpdateEnabled: Boolean = true,
    onManualCheckUpdate: () -> Unit = {},
    onTriggerOtaUpdate: () -> Unit = {},
    onUpdateAutoUpdateEnabled: (Boolean) -> Unit = {},
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
    onToggleModule: (AppModule, Boolean) -> Unit = { _, _ -> },
    onLoadPredefinedFinanceAccounts: () -> Unit = {},
    onResetFinanceToDefaultAccounts: () -> Unit = {},
) {
    var titlePrefix by remember(state.preferences) { mutableStateOf(state.preferences.titlePrefix) }
    var focusMinutes by remember(state.preferences) { mutableStateOf(state.preferences.focusMinutes.toString()) }
    var shortBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.shortBreakMinutes.toString()) }
    var longBreakMinutes by remember(state.preferences) { mutableStateOf(state.preferences.longBreakMinutes.toString()) }
    var timezoneOverride by remember(state.preferences) { mutableStateOf(state.preferences.timezoneOverride) }
    var selectedDateFormat by remember(state.preferences) { mutableStateOf(state.preferences.dateFormat) }
    var selectedThemePreference by remember(state.preferences) { mutableStateOf(state.preferences.themePreference) }
    var rolloverTime by remember(state.preferences) { mutableStateOf(state.preferences.rolloverTime) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.lgDp),
    ) {
        // Section 1: MODULES Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-modules"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "MODULES · 功能模块开关",
                trailingText = "${state.preferences.enabledModules.size} OF 4 ENABLED",
            )
            Spacer(modifier = Modifier.height(10.dp))

            val moduleRows = listOf(
                ModuleRowConfig(
                    module = AppModule.TASKS,
                    dotColor = ThemeTokens.colors.tasksAccent,
                    title = "Tasks · 任务与番茄",
                    subtitle = "Multi-level tasks, Routines & Timeblocks",
                ),
                ModuleRowConfig(
                    module = AppModule.FINANCE,
                    dotColor = ThemeTokens.colors.financeAccent,
                    title = "Finance · 复式记账",
                    subtitle = "Beancount ledger, Guardrails & Clearing",
                ),
                ModuleRowConfig(
                    module = AppModule.NOTES,
                    dotColor = ThemeTokens.colors.notesAccent,
                    title = "Notes · 双链笔记",
                    subtitle = "Daily journal, Markdown & [[Backlinks]]",
                ),
                ModuleRowConfig(
                    module = AppModule.THINGS,
                    dotColor = ThemeTokens.colors.thingsAccent,
                    title = "Things · 物品与数字资产",
                    subtitle = "Physical inventory & Digital subscriptions",
                ),
            )

            moduleRows.forEachIndexed { index, config ->
                if (index > 0) {
                    SwissHairlineDivider()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(config.dotColor),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = config.title,
                                style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Medium),
                                color = ThemeTokens.colors.textPrimary,
                            )
                            Text(
                                text = config.subtitle,
                                style = ThemeTokens.type.label,
                                color = ThemeTokens.colors.textSecondary,
                            )
                        }
                    }

                    Switch(
                        checked = config.module in state.preferences.enabledModules,
                        onCheckedChange = { onToggleModule(config.module, it) },
                        modifier = Modifier.testTag("module-toggle-${config.module.name.lowercase()}"),
                    )
                }
            }
        }

        // Section 3: ACCOUNT & SYNC Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-cloud"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "CLOUD & SYNC · 账户与同步",
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Cloud", "Sync status")
            SyncStatusPanel(state)
        }

        // Section 2: TASK & FOCUS DEFAULTS Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-task-defaults"),
            contentPadding = PaddingValues(14.dp),
        ) {
            SwissCardHeader(
                title = "TASK & FOCUS DEFAULTS · 任务与专注设置",
                accentDotColor = ThemeTokens.colors.tasksAccent,
            )
            Spacer(modifier = Modifier.height(6.dp))
            SectionHeader("Defaults", "Task")
            OutlinedTextField(
                value = titlePrefix,
                onValueChange = { titlePrefix = it },
                label = { Text("Task title prefix", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = focusMinutes,
                    onValueChange = { focusMinutes = it },
                    label = { Text("Focus minutes", style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = shortBreakMinutes,
                    onValueChange = { shortBreakMinutes = it },
                    label = { Text("Short break", style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = longBreakMinutes,
                    onValueChange = { longBreakMinutes = it },
                    label = { Text("Long break", style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
            }
        }

        // Section 2c: APPEARANCE Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-theme"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "APPEARANCE · 外观主题",
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
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

        // Section 2d: APP PREFERENCES Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-preferences"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "APP PREFERENCES · 全局偏好",
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                label = { Text("Day rollover time", style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-date-format-selector"),
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
                Text("Save settings", style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }

        // Section Finance Account Presets
        if (AppModule.FINANCE in state.preferences.enabledModules) {
            SwissBentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-panel-finance-presets"),
                contentPadding = PaddingValues(16.dp),
            ) {
                SwissCardHeader(
                    title = "FINANCE PRESETS · 财务账户预设",
                    accentDotColor = ThemeTokens.colors.financeAccent,
                    trailingText = if (state.financeView.canLoadPredefinedAccounts) "DEFAULT 5" else "CONFIGURED",
                )
                Spacer(modifier = Modifier.height(10.dp))
                val statusText = if (state.financeView.canLoadPredefinedAccounts) {
                    "当前仅有基础五类根账户，可加载预设子账户以快速上手记账。"
                } else {
                    "已配置子账户或已有记账记录。如需重置，可清除子账户与记录并恢复为基础五类根账户。"
                }
                Text(
                    text = statusText,
                    style = ThemeTokens.type.body,
                    color = ThemeTokens.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onLoadPredefinedFinanceAccounts,
                        enabled = state.financeView.canLoadPredefinedAccounts,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("finance-load-predefined-button"),
                    ) {
                        Text("加载预设子账户")
                    }
                    OutlinedButton(
                        onClick = onResetFinanceToDefaultAccounts,
                        enabled = !state.financeView.canLoadPredefinedAccounts,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("finance-reset-default-five-button"),
                    ) {
                        Text("重置为基础五类账户")
                    }
                }
            }
        }

        // Section Account Session/Login
        if (state.auth.mode == dev.tireless.abun.app.AuthMode.GUEST) {
            SwissBentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-panel-login"),
                contentPadding = PaddingValues(16.dp),
            ) {
                SwissCardHeader(
                    title = "ACCOUNT · 访客与登录",
                    accentDotColor = ThemeTokens.colors.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
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
            SwissBentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-panel-session"),
                contentPadding = PaddingValues(16.dp),
            ) {
                SwissCardHeader(
                    title = "ACCOUNT · 会话状态",
                    accentDotColor = ThemeTokens.colors.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader("Account", "Session")
                Text(
                    "This device is signed in and can sync with your server account.",
                    style = ThemeTokens.type.bodyMuted,
                )
                OutlinedButton(onClick = onLogout) {
                    Text("Log out", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }

        // Section Software Updates
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-updates"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "SOFTWARE · 软件更新",
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Software", "Updates")
            Text(
                text = "Current Build: ${BuildInfo.BUILD_IDENTIFIER}",
                style = ThemeTokens.type.bodyMuted,
            )
            state.apiVersion?.let { apiVersion ->
                Text(
                    text = "API Version: v$apiVersion",
                    style = ThemeTokens.type.bodyMuted,
                )
            }

            when (val status = otaStatus) {
                is OtaStatus.UpdateAvailable -> {
                    EditorialCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
                            Text(
                                text = "New update available: v${status.info.versionName} (${status.info.versionCode}-${status.info.commit})",
                                style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold),
                            )
                            if (status.info.changelog.isNotBlank()) {
                                Text(
                                    text = status.info.changelog,
                                    style = ThemeTokens.type.bodyMuted,
                                )
                            }
                            Button(
                                onClick = onTriggerOtaUpdate,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Update now", style = ThemeTokens.type.body.withMaterialContentColor())
                            }
                        }
                    }
                }
                is OtaStatus.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                        Text(
                            text = "Downloading update: ${(status.progress * 100).toInt()}%",
                            style = ThemeTokens.type.body,
                        )
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = ThemeTokens.colors.primary,
                        )
                    }
                }
                is OtaStatus.Installing -> {
                    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                        Text(
                            text = "Installing update...",
                            style = ThemeTokens.type.body,
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = ThemeTokens.colors.primary,
                        )
                    }
                }
                is OtaStatus.Checking -> {
                    Text(
                        text = "Checking for updates...",
                        style = ThemeTokens.type.bodyMuted,
                    )
                }
                is OtaStatus.UpToDate -> {
                    Text(
                        text = "Your app is up to date.",
                        style = ThemeTokens.type.bodyMuted,
                    )
                }
                is OtaStatus.Error -> {
                    Text(
                        text = status.message,
                        style = ThemeTokens.type.body.copy(color = ThemeTokens.colors.error),
                    )
                }
                OtaStatus.Idle -> Unit
            }

            val isActionDisabled = otaStatus is OtaStatus.Checking || otaStatus is OtaStatus.Downloading || otaStatus is OtaStatus.Installing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onManualCheckUpdate,
                    enabled = !isActionDisabled,
                ) {
                    Text("Check for updates", style = ThemeTokens.type.body.withMaterialContentColor())
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                ) {
                    Text("Auto check", style = ThemeTokens.type.label)
                    Switch(
                        checked = isAutoUpdateEnabled,
                        onCheckedChange = onUpdateAutoUpdateEnabled,
                    )
                }
            }
        }
    }
}

private data class ModuleRowConfig(
    val module: AppModule,
    val dotColor: Color,
    val title: String,
    val subtitle: String,
)
