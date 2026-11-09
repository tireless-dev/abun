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
import dev.tireless.abun.app.LanguagePreference
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
import dev.tireless.abun.ui.i18n.languageOptionLabel
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
    onUpdateThemePreference: (ThemePreference) -> Unit = {},
    onUpdateLanguagePreference: (LanguagePreference) -> Unit = {},
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
        onUpdateLanguagePreference = onUpdateLanguagePreference,
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
    onUpdateThemePreference: (ThemePreference) -> Unit = {},
    onUpdateLanguagePreference: (LanguagePreference) -> Unit = {},
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
    val s = ThemeTokens.strings
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
                title = s.settings.modulesCard,
                trailingText = s.settings.modulesEnabledCount(state.preferences.enabledModules.size, 4),
            )
            Spacer(modifier = Modifier.height(10.dp))

            val moduleRows = listOf(
                ModuleRowConfig(
                    module = AppModule.TASKS,
                    dotColor = ThemeTokens.colors.tasksAccent,
                    title = s.nav.tasks,
                    subtitle = when (ThemeTokens.language) {
                        dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Multi-level tasks, Routines & Timeblocks"
                        dev.tireless.abun.ui.i18n.AppLanguage.SC -> "多级任务、例程与时间块"
                        dev.tireless.abun.ui.i18n.AppLanguage.TC -> "多級任務、例程與時間塊"
                    },
                ),
                ModuleRowConfig(
                    module = AppModule.FINANCE,
                    dotColor = ThemeTokens.colors.financeAccent,
                    title = s.nav.cockpit,
                    subtitle = when (ThemeTokens.language) {
                        dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Beancount ledger, Guardrails & Clearing"
                        dev.tireless.abun.ui.i18n.AppLanguage.SC -> "复式记账、预算护栏与结算"
                        dev.tireless.abun.ui.i18n.AppLanguage.TC -> "複式記賬、預算護欄與結算"
                    },
                ),
                ModuleRowConfig(
                    module = AppModule.NOTES,
                    dotColor = ThemeTokens.colors.notesAccent,
                    title = s.nav.notes,
                    subtitle = when (ThemeTokens.language) {
                        dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Daily journal, Markdown & [[Backlinks]]"
                        dev.tireless.abun.ui.i18n.AppLanguage.SC -> "每日闪念、Markdown 与双链笔记"
                        dev.tireless.abun.ui.i18n.AppLanguage.TC -> "每日閃念、Markdown 與雙鏈筆記"
                    },
                ),
                ModuleRowConfig(
                    module = AppModule.THINGS,
                    dotColor = ThemeTokens.colors.thingsAccent,
                    title = s.nav.things,
                    subtitle = when (ThemeTokens.language) {
                        dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Physical inventory & Digital subscriptions"
                        dev.tireless.abun.ui.i18n.AppLanguage.SC -> "实体物品清单与数字资产管理"
                        dev.tireless.abun.ui.i18n.AppLanguage.TC -> "實體物品清單與數位資產管理"
                    },
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

        // Section 2: CLOUD & SYNC Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-cloud"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = s.settings.cloudCard,
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(s.settings.cloudCard, s.settings.syncStatus)
            SyncStatusPanel(state)
        }

        // Section 3: TASK & FOCUS DEFAULTS Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-task-defaults"),
            contentPadding = PaddingValues(14.dp),
        ) {
            SwissCardHeader(
                title = s.settings.tasksCard,
                accentDotColor = ThemeTokens.colors.tasksAccent,
            )
            Spacer(modifier = Modifier.height(6.dp))
            SectionHeader(s.settings.tasksCard, s.nav.tasks)
            OutlinedTextField(
                value = titlePrefix,
                onValueChange = { titlePrefix = it },
                label = { Text(s.settings.taskTitlePrefix, style = ThemeTokens.type.label) },
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
                    label = { Text(s.settings.focusMinutes, style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = shortBreakMinutes,
                    onValueChange = { shortBreakMinutes = it },
                    label = { Text(s.settings.shortBreakMinutes, style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = longBreakMinutes,
                    onValueChange = { longBreakMinutes = it },
                    label = { Text(s.settings.longBreakMinutes, style = ThemeTokens.type.label) },
                    modifier = Modifier.weight(1f),
                    textStyle = ThemeTokens.type.body,
                    singleLine = true,
                )
            }
        }

        // Section 4: APPEARANCE & LANGUAGE Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-theme"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = s.settings.appearanceCard,
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(
                eyebrow = when (ThemeTokens.language) {
                    dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Appearance"
                    dev.tireless.abun.ui.i18n.AppLanguage.SC -> "外观"
                    dev.tireless.abun.ui.i18n.AppLanguage.TC -> "外觀"
                },
                title = when (ThemeTokens.language) {
                    dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Theme"
                    dev.tireless.abun.ui.i18n.AppLanguage.SC -> "主题"
                    dev.tireless.abun.ui.i18n.AppLanguage.TC -> "主題"
                },
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThemePreference.entries.forEachIndexed { index, themePreference ->
                    val option = when (themePreference) {
                        ThemePreference.SYSTEM -> s.settings.themeSystem
                        ThemePreference.LIGHT -> s.settings.themeLight
                        ThemePreference.DARK -> s.settings.themeDark
                    }
                    SegmentedButton(
                        modifier = Modifier.testTag("theme-option-${themePreference.name.lowercase()}"),
                        selected = themePreference == selectedThemePreference,
                        onClick = {
                            selectedThemePreference = themePreference
                            onUpdateThemePreference(themePreference)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemePreference.entries.size),
                    ) {
                        Text(option, style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            SectionHeader(
                eyebrow = when (ThemeTokens.language) {
                    dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Language"
                    dev.tireless.abun.ui.i18n.AppLanguage.SC -> "界面语言"
                    dev.tireless.abun.ui.i18n.AppLanguage.TC -> "界面語言"
                },
                title = when (ThemeTokens.language) {
                    dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Display"
                    dev.tireless.abun.ui.i18n.AppLanguage.SC -> "显示模式"
                    dev.tireless.abun.ui.i18n.AppLanguage.TC -> "顯示模式"
                },
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-language-selector"),
            ) {
                LanguagePreference.entries.forEachIndexed { index, langPref ->
                    val optionLabel = languageOptionLabel(langPref, ThemeTokens.language)
                    SegmentedButton(
                        modifier = Modifier.testTag("language-option-${langPref.name.lowercase()}"),
                        selected = langPref == state.preferences.languagePreference,
                        onClick = {
                            onUpdateLanguagePreference(langPref)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = LanguagePreference.entries.size),
                    ) {
                        Text(optionLabel, style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }
        }

        // Section 5: APP PREFERENCES Bento Card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-preferences"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = s.settings.title,
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(s.settings.title, s.settings.dayRolloverTime)
            OutlinedTextField(
                value = timezoneOverride,
                onValueChange = { timezoneOverride = it },
                label = { Text(s.settings.timezoneOverride, style = ThemeTokens.type.label) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = ThemeTokens.type.body,
                singleLine = true,
            )
            OutlinedTextField(
                value = rolloverTime,
                onValueChange = { rolloverTime = it },
                label = { Text(s.settings.dayRolloverTime, style = ThemeTokens.type.label) },
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
                Text(s.settings.saveSettings, style = ThemeTokens.type.body.withMaterialContentColor())
            }
        }

        // Section 6: Finance Account Presets
        if (AppModule.FINANCE in state.preferences.enabledModules) {
            SwissBentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-panel-finance-presets"),
                contentPadding = PaddingValues(16.dp),
            ) {
                SwissCardHeader(
                    title = s.settings.financeCard,
                    accentDotColor = ThemeTokens.colors.financeAccent,
                    trailingText = if (state.financeView.canLoadPredefinedAccounts) "DEFAULT 5" else "CONFIGURED",
                )
                Spacer(modifier = Modifier.height(10.dp))
                val statusText = if (state.financeView.canLoadPredefinedAccounts) {
                    s.settings.financePresetsStatusDefault
                } else {
                    s.settings.financePresetsStatusConfigured
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
                        Text(s.settings.loadPredefinedAccounts)
                    }
                    OutlinedButton(
                        onClick = onResetFinanceToDefaultAccounts,
                        enabled = !state.financeView.canLoadPredefinedAccounts,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("finance-reset-default-five-button"),
                    ) {
                        Text(s.settings.resetToDefaultFiveAccounts)
                    }
                }
            }
        }

        // Section 7: Account Session/Login
        if (state.auth.mode == dev.tireless.abun.app.AuthMode.GUEST) {
            SwissBentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-panel-login"),
                contentPadding = PaddingValues(16.dp),
            ) {
                SwissCardHeader(
                    title = s.settings.accountInfo,
                    accentDotColor = ThemeTokens.colors.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(s.settings.accountInfo, s.settings.accountLogin)
                Text(
                    state.auth.errorMessage ?: s.settings.guestAccountDesc,
                    style = ThemeTokens.type.bodyMuted,
                )
                Button(onClick = onReopenLogin) {
                    Text(s.settings.signIn, style = ThemeTokens.type.body.withMaterialContentColor())
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
                    title = s.settings.accountInfo,
                    accentDotColor = ThemeTokens.colors.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(s.settings.accountInfo, s.settings.signedInAs)
                Text(
                    s.settings.signedInAccountDesc,
                    style = ThemeTokens.type.bodyMuted,
                )
                OutlinedButton(onClick = onLogout) {
                    Text(s.settings.signOut, style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }
        }

        // Section 8: Software Updates
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-panel-updates"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = s.settings.softwareUpdatesCard,
                accentDotColor = ThemeTokens.colors.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(s.settings.softwareUpdatesCard, s.settings.version)
            Text(
                text = "${s.settings.currentBuild}: ${BuildInfo.BUILD_IDENTIFIER}",
                style = ThemeTokens.type.bodyMuted,
            )
            state.apiVersion?.let { apiVersion ->
                Text(
                    text = "${s.settings.apiVersion}: v$apiVersion",
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
                                text = "${s.actions.updateAvailable}: v${status.info.versionName} (${status.info.versionCode}-${status.info.commit})",
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
                                Text(s.actions.updateNow, style = ThemeTokens.type.body.withMaterialContentColor())
                            }
                        }
                    }
                }
                is OtaStatus.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.xsDp)) {
                        Text(
                            text = "${s.actions.installingUpdate} ${(status.progress * 100).toInt()}%",
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
                            text = s.actions.installingUpdate,
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
                        text = s.common.loading,
                        style = ThemeTokens.type.bodyMuted,
                    )
                }
                is OtaStatus.UpToDate -> {
                    Text(
                        text = s.common.success,
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
                    Text(s.settings.checkForUpdates, style = ThemeTokens.type.body.withMaterialContentColor())
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp),
                ) {
                    Text(s.settings.autoCheckUpdates, style = ThemeTokens.type.label)
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
