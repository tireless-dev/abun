package dev.tireless.abun.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tireless.abun.app.AppModule
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
fun FloatingCommandPill(
    selectedTab: AppTab,
    enabledModules: Set<AppModule>,
    primaryActionLabel: String,
    onPrimaryActionClick: () -> Unit,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val workspaceLabel = when (selectedTab) {
        AppTab.TODAY -> ThemeTokens.strings.nav.dashboard
        AppTab.TASKS -> ThemeTokens.strings.nav.tasks
        AppTab.FINANCE -> ThemeTokens.strings.nav.cockpit
        AppTab.NOTES -> ThemeTokens.strings.nav.notes
        AppTab.THINGS -> ThemeTokens.strings.nav.things
        AppTab.SETTINGS -> ThemeTokens.strings.nav.settings
    }

    Surface(
        modifier = modifier
            .heightIn(min = 46.dp)
            .testTag("floating-command-pill"),
        shape = RoundedCornerShape(999.dp),
        color = ThemeTokens.colors.pillSurface,
        border = BorderStroke(1.dp, Color(0xFF282C32)),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Left half: Clickable module/tab switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .testTag("floating-command-pill-switcher")
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Colored dots strictly for enabled modules in order
                AppModule.entries.filter { it in enabledModules }.forEach { module ->
                    val dotColor = when (module) {
                        AppModule.TASKS -> ThemeTokens.colors.tasksAccent
                        AppModule.FINANCE -> ThemeTokens.colors.financeAccent
                        AppModule.NOTES -> ThemeTokens.colors.notesAccent
                        AppModule.THINGS -> ThemeTokens.colors.thingsAccent
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }

                Text(
                    text = workspaceLabel,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    ),
                )

                Text(
                    text = if (isExpanded) "▴" else "▾",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA0A4AA),
                    ),
                )
            }

            // Right half: High-contrast white pill action button
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .testTag("floating-command-pill-action")
                    .clickable(onClick = onPrimaryActionClick),
                shape = RoundedCornerShape(999.dp),
                color = Color.White,
                contentColor = Color(0xFF111315),
            ) {
                Text(
                    text = primaryActionLabel,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111315),
                    ),
                )
            }
        }
    }
}

@Composable
fun FloatingCommandSheet(
    enabledModules: Set<AppModule>,
    currentTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    openTasksCount: Int = 0,
    financeSummary: String? = null,
    onQuickCaptureTask: (() -> Unit)? = null,
    onQuickCaptureExpense: (() -> Unit)? = null,
    onQuickCaptureNote: (() -> Unit)? = null,
    onQuickCaptureThing: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .testTag("floating-command-sheet")
            .widthIn(min = 320.dp, max = 460.dp),
        shape = RoundedCornerShape(20.dp),
        color = ThemeTokens.colors.surface,
        border = BorderStroke(1.dp, ThemeTokens.colors.cardBorder),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Section 1: QUICK CAPTURE · 全局快速捕获
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ThemeTokens.strings.actions.quickCaptureHeader,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                    Text(
                        text = "ESC",
                        modifier = Modifier
                            .clickable { onDismiss?.invoke() }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }

                val enabledList = AppModule.entries.filter { it in enabledModules }
                if (enabledList.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        enabledList.forEach { module ->
                            QuickCaptureTile(
                                module = module,
                                onClick = {
                                    when (module) {
                                        AppModule.TASKS -> onQuickCaptureTask?.invoke() ?: onSelectTab(AppTab.TASKS)
                                        AppModule.FINANCE -> onQuickCaptureExpense?.invoke() ?: onSelectTab(AppTab.FINANCE)
                                        AppModule.NOTES -> onQuickCaptureNote?.invoke() ?: onSelectTab(AppTab.NOTES)
                                        AppModule.THINGS -> onQuickCaptureThing?.invoke() ?: onSelectTab(AppTab.THINGS)
                                    }
                                    onDismiss?.invoke()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            SwissHairlineDivider()

            // Section 2: WORKSPACES · 已启用模块
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = ThemeTokens.strings.actions.workspacesHeader,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                    Text(
                        text = "SWIPE PILL TO CYCLE",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = ThemeTokens.colors.textTertiary,
                        ),
                    )
                }

                // Full-width Dashboard 总览 Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("command-sheet-dashboard")
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelectTab(AppTab.TODAY)
                            onDismiss?.invoke()
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (currentTab == AppTab.TODAY) ThemeTokens.colors.surface else ThemeTokens.colors.canvas,
                    border = if (currentTab == AppTab.TODAY) {
                        BorderStroke(1.5.dp, ThemeTokens.colors.textPrimary)
                    } else {
                        BorderStroke(1.dp, ThemeTokens.colors.cardBorder)
                    },
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ThemeTokens.colors.textPrimary),
                            )
                            Text(
                                text = ThemeTokens.strings.nav.dashboard,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTokens.colors.textPrimary,
                                ),
                            )
                        }
                        Text(
                            text = if (currentTab == AppTab.TODAY) "ACTIVE" else "HOME",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ThemeTokens.colors.textSecondary,
                            ),
                        )
                    }
                }

                // 2x2 Module Grid
                val enabledList = AppModule.entries.filter { it in enabledModules }
                val rows = enabledList.chunked(2)
                rows.forEach { rowModules ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowModules.forEach { module ->
                            WorkspaceBentoItem(
                                module = module,
                                isSelected = when (module) {
                                    AppModule.TASKS -> currentTab == AppTab.TASKS
                                    AppModule.FINANCE -> currentTab == AppTab.FINANCE
                                    AppModule.NOTES -> currentTab == AppTab.NOTES
                                    AppModule.THINGS -> currentTab == AppTab.THINGS
                                },
                                metric = when (module) {
                                    AppModule.TASKS -> "$openTasksCount open"
                                    AppModule.FINANCE -> financeSummary ?: "Runway"
                                    AppModule.NOTES -> "Journal"
                                    AppModule.THINGS -> "Assets"
                                },
                                onClick = {
                                    val targetTab = when (module) {
                                        AppModule.TASKS -> AppTab.TASKS
                                        AppModule.FINANCE -> AppTab.FINANCE
                                        AppModule.NOTES -> AppTab.NOTES
                                        AppModule.THINGS -> AppTab.THINGS
                                    }
                                    onSelectTab(targetTab)
                                    onDismiss?.invoke()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowModules.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            SwissHairlineDivider()

            // Footer row: Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .testTag("command-sheet-settings")
                    .clickable {
                        onSelectTab(AppTab.SETTINGS)
                        onDismiss?.invoke()
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "⚙ ${ThemeTokens.strings.settings.modulesCard} (${enabledModules.size}/4)",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = ThemeTokens.colors.textSecondary,
                    ),
                )
                Text(
                    text = ThemeTokens.strings.actions.configureModules,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.textPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun QuickCaptureTile(
    module: AppModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = ThemeTokens.strings
    val (label, symbol, accentColor, softBg, contentDesc) = when (module) {
        AppModule.TASKS -> QuickCaptureInfo(s.actions.quickCaptureTask, "+", ThemeTokens.colors.tasksAccent, ThemeTokens.colors.tasksSoft, "+ ${s.actions.quickCaptureTask}")
        AppModule.FINANCE -> QuickCaptureInfo(s.actions.quickCaptureExpense, "¥", ThemeTokens.colors.financeAccent, ThemeTokens.colors.financeSoft, "+ ${s.actions.quickCaptureExpense}")
        AppModule.NOTES -> QuickCaptureInfo(s.actions.quickCaptureNote, "✎", ThemeTokens.colors.notesAccent, ThemeTokens.colors.notesSoft, "+ ${s.actions.quickCaptureNote}")
        AppModule.THINGS -> QuickCaptureInfo(s.actions.quickCaptureThing, "◈", ThemeTokens.colors.thingsAccent, ThemeTokens.colors.thingsSoft, "+ ${s.actions.quickCaptureThing}")
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc },
        shape = RoundedCornerShape(12.dp),
        color = ThemeTokens.colors.canvas,
        border = BorderStroke(1.dp, ThemeTokens.colors.hairline),
        tonalElevation = 0.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(softBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    ),
                )
            }
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ThemeTokens.colors.textPrimary,
                ),
            )
        }
    }
}

private data class QuickCaptureInfo(
    val label: String,
    val symbol: String,
    val accentColor: Color,
    val softBg: Color,
    val contentDesc: String,
)

@Composable
private fun WorkspaceBentoItem(
    module: AppModule,
    isSelected: Boolean,
    metric: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tag = "command-sheet-module-${module.name}"
    val s = ThemeTokens.strings
    val (name, accentColor, description) = when (module) {
        AppModule.TASKS -> Triple(s.nav.tasks, ThemeTokens.colors.tasksAccent, when (ThemeTokens.language) {
            dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Tasks & Pomodoro"
            dev.tireless.abun.ui.i18n.AppLanguage.SC -> "多级任务与番茄钟"
            dev.tireless.abun.ui.i18n.AppLanguage.TC -> "多級任務與番茄鐘"
        })
        AppModule.FINANCE -> Triple(s.nav.cockpit, ThemeTokens.colors.financeAccent, when (ThemeTokens.language) {
            dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Ledger & Cash Flow"
            dev.tireless.abun.ui.i18n.AppLanguage.SC -> "复式记账与现金流"
            dev.tireless.abun.ui.i18n.AppLanguage.TC -> "複式記賬與現金流"
        })
        AppModule.NOTES -> Triple(s.nav.notes, ThemeTokens.colors.notesAccent, when (ThemeTokens.language) {
            dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Notes & Journal"
            dev.tireless.abun.ui.i18n.AppLanguage.SC -> "双链笔记与闪念"
            dev.tireless.abun.ui.i18n.AppLanguage.TC -> "雙鏈筆記與閃念"
        })
        AppModule.THINGS -> Triple(s.nav.things, ThemeTokens.colors.thingsAccent, when (ThemeTokens.language) {
            dev.tireless.abun.ui.i18n.AppLanguage.EN -> "Items & Assets"
            dev.tireless.abun.ui.i18n.AppLanguage.SC -> "物品与数字资产"
            dev.tireless.abun.ui.i18n.AppLanguage.TC -> "物品與數位資產"
        })
    }

    Surface(
        modifier = modifier
            .testTag(tag)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) ThemeTokens.colors.surface else ThemeTokens.colors.canvas,
        border = if (isSelected) {
            BorderStroke(1.5.dp, ThemeTokens.colors.textPrimary)
        } else {
            BorderStroke(1.dp, ThemeTokens.colors.hairline)
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                    Text(
                        text = name,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ThemeTokens.colors.textPrimary,
                        ),
                    )
                }
                Text(
                    text = metric,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    ),
                )
            }
            Text(
                text = description,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = ThemeTokens.colors.textSecondary,
                ),
                maxLines = 1,
            )
        }
    }
}
