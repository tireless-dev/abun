package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.TaskSubTab
import dev.tireless.abun.app.ThemePreference
import dev.tireless.abun.ota.OtaStatus
import dev.tireless.abun.ui.EditorialScreen
import dev.tireless.abun.ui.FloatingCommandPill
import dev.tireless.abun.ui.FloatingCommandSheet
import dev.tireless.abun.ui.navigation.AppRoute
import dev.tireless.abun.ui.screens.ClearingHubScreen
import dev.tireless.abun.ui.screens.FinanceCockpitScreen
import dev.tireless.abun.ui.screens.FinanceLedgerScreen
import dev.tireless.abun.ui.screens.FinanceSettingsScreen
import dev.tireless.abun.ui.screens.HomeScreen
import dev.tireless.abun.ui.screens.SettingsScreen
import dev.tireless.abun.ui.screens.TasksScreen
import dev.tireless.abun.ui.theme.AppTheme
import dev.tireless.abun.ui.theme.ThemeTokens
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SnapshotDumpTest {

    @Test
    fun dumpToday() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = screenshotState(selectedTab = AppTab.TODAY)
        dumpSnapshot("today") {
            SnapshotScreenFrame(state = state) {
                HomeScreen(
                    state = state,
                    liveNow = ScreenshotNow,
                    onOpenTask = {},
                    onStartPomodoro = {},
                )
            }
        }
    }

    @Test
    fun dumpCommandSheetExpanded() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = screenshotState(selectedTab = AppTab.TODAY)
        dumpSnapshot("command_sheet_expanded") {
            SnapshotScreenFrame(
                state = state,
                isCommandSheetExpanded = true,
            ) {
                HomeScreen(
                    state = state,
                    liveNow = ScreenshotNow,
                    onOpenTask = {},
                    onStartPomodoro = {},
                )
            }
        }
    }

    @Test
    fun dumpTasksPopulated() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = screenshotState(selectedTab = AppTab.TASKS, selectedTaskSubTab = TaskSubTab.TASKS)
        dumpSnapshot("tasks_populated") {
            SnapshotScreenFrame(state = state) {
                TasksScreen(
                    state = state,
                    liveNow = ScreenshotNow,
                    isPomodoroActive = false,
                    onSelectTaskFilter = {},
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                )
            }
        }
    }

    @Test
    fun dumpPomodoroActive() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val activeSession = activePomodoroSession()
        val state = screenshotState(
            selectedTab = AppTab.TASKS,
            selectedTaskSubTab = TaskSubTab.POMODORO,
            activePomodoroSession = activeSession,
        )
        dumpSnapshot("pomodoro_active") {
            SnapshotScreenFrame(state = state) {
                TasksScreen(
                    state = state,
                    liveNow = ScreenshotNow,
                    isPomodoroActive = true,
                    onSelectTaskFilter = {},
                    onOpenTask = {},
                    onOpenStartPomodoro = {},
                    onCreateRoutine = {},
                    onOpenRoutine = {},
                    onRunRoutine = {},
                )
            }
        }
    }

    @Test
    fun dumpFinanceCockpit() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = populatedFinanceScreenshotState(selectedTab = AppTab.FINANCE)
        dumpSnapshot("finance_cockpit") {
            SnapshotScreenFrame(
                state = state,
                currentRoute = AppRoute.FinanceCockpit.route,
            ) {
                FinanceCockpitScreen(
                    state = state,
                    onOpenLedger = {},
                    onOpenClearingHub = {},
                    onOpenCreateTransaction = {},
                    onOpenCreateBudgetRule = {},
                    onSelectHorizon = {},
                    onDeleteBudgetRule = {},
                )
            }
        }
    }

    @Test
    fun dumpFinanceLedger() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = populatedFinanceScreenshotState(selectedTab = AppTab.FINANCE)
        dumpSnapshot("finance_ledger") {
            SnapshotScreenFrame(
                state = state,
                currentRoute = AppRoute.Finance.route,
            ) {
                FinanceLedgerScreen(
                    state = state,
                    onSelectFilter = { _, _, _ -> },
                    onToggleViewMode = {},
                    onOpenCreateTransaction = {},
                    onOpenAccounts = {},
                    onOpenCockpit = {},
                    onOpenClearingHub = {},
                    onDeleteTransaction = {},
                )
            }
        }
    }

    @Test
    fun dumpFinanceClearing() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = populatedFinanceScreenshotState(selectedTab = AppTab.FINANCE)
        dumpSnapshot("finance_clearing") {
            SnapshotScreenFrame(
                state = state,
                currentRoute = AppRoute.DebtClearingHub.route,
            ) {
                ClearingHubScreen(
                    state = state,
                    onOpenLedger = {},
                    onOpenCockpit = {},
                    onOpenCombinedRepayment = {},
                    onOpenAmortizationWizard = {},
                    onRepayStatement = {},
                    onRepayLoanTranche = {},
                    onDeleteAmortizationPlan = {},
                )
            }
        }
    }

    @Test
    fun dumpFinanceSettings() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = populatedFinanceScreenshotState(selectedTab = AppTab.FINANCE)
        dumpSnapshot("finance_settings") {
            SnapshotScreenFrame(
                state = state,
                currentRoute = AppRoute.FinanceSettings.route,
            ) {
                FinanceSettingsScreen(
                    state = state,
                    onUpdateAccount = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onCreateAccount = { _, _, _, _, _, _, _ -> },
                    onDeleteAccount = {},
                    onAutoPad = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun dumpTaskSettings() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = screenshotState(selectedTab = AppTab.TASKS, selectedTaskSubTab = TaskSubTab.SETTINGS)
        dumpSnapshot("task_settings") {
            SnapshotScreenFrame(state = state) {
                dev.tireless.abun.ui.screens.TaskSettingsScreen(
                    state = state,
                    onUpdateTaskPreferences = { _, _, _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun dumpSettings() {
        if (System.getProperty("abun.dumpSnapshots") != "true") return
        val state = screenshotState(selectedTab = AppTab.SETTINGS)
        dumpSnapshot("settings") {
            SnapshotScreenFrame(
                state = state,
                currentRoute = AppRoute.Settings.route,
            ) {
                SettingsScreen(
                    state = state,
                    otaStatus = OtaStatus.Idle,
                    isAutoUpdateEnabled = true,
                    onManualCheckUpdate = {},
                    onTriggerOtaUpdate = {},
                    onUpdateAutoUpdateEnabled = {},
                    onUpdateThemePreference = {},
                    onUpdatePreferences = { _, _, _, _, _, _, _, _, _ -> },
                    onReopenLogin = {},
                    onLogout = {},
                )
            }
        }
    }

    private fun dumpSnapshot(
        name: String,
        content: @Composable () -> Unit,
    ) = runDesktopComposeUiTest {
        if (System.getProperty("abun.dumpSnapshots") != "true") return@runDesktopComposeUiTest

        setContent {
            AppTheme(themePreference = ThemePreference.LIGHT) {
                content()
            }
        }

        waitForIdle()

        val outDir = File(System.getProperty("abun.snapshotsDir") ?: "build/snapshots")
        outDir.mkdirs()
        val outputFile = File(outDir, "$name.png")

        val awtImage = onNodeWithTag(SnapshotRootTag).captureToImage().toAwtImage()
        ImageIO.write(awtImage, "png", outputFile)
    }

    @Composable
    private fun SnapshotScreenFrame(
        state: AppUiState,
        currentRoute: String? = null,
        isCommandSheetExpanded: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        val primaryActionLabel = when (state.selectedTab) {
            AppTab.TODAY -> "+ Capture"
            AppTab.TASKS -> when (state.selectedTaskSubTab) {
                TaskSubTab.TASKS -> "+ 新建任务"
                TaskSubTab.ROUTINES -> "+ 新建例程"
                TaskSubTab.POMODORO -> if (state.activePomodoroSession != null) "🍅 专注中" else "+ 开始专注"
                TaskSubTab.SETTINGS -> "← 任务列表"
            }
            AppTab.FINANCE -> "+ 记一笔"
            AppTab.NOTES -> "+ 新建笔记"
            AppTab.THINGS -> "+ 新增物品"
            AppTab.SETTINGS -> "← 返回主页"
        }

        Box(
            modifier = Modifier
                .size(390.dp, 844.dp)
                .testTag(SnapshotRootTag)
                .background(if (state.selectedTab == AppTab.TODAY) ThemeTokens.colors.canvas else ThemeTokens.colors.background),
        ) {
            Scaffold(
                containerColor = if (state.selectedTab == AppTab.TODAY) ThemeTokens.colors.canvas else MaterialTheme.colorScheme.background,
                topBar = {
                    if (state.selectedTab != AppTab.TODAY) {
                        AbunTopAppBar(
                            selectedTab = state.selectedTab,
                            selectedTaskSubTab = state.selectedTaskSubTab,
                            selectedDate = state.selectedDate,
                            currentRoute = currentRoute,
                        )
                    }
                },
            ) { padding: PaddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                    EditorialScreen(
                        modifier = Modifier.padding(padding),
                        contentPadding = PaddingValues(
                            start = ThemeTokens.spacing.mdDp,
                            top = if (state.selectedTab == AppTab.TODAY) 20.dp else ThemeTokens.spacing.mdDp,
                            end = ThemeTokens.spacing.mdDp,
                            bottom = 96.dp,
                        ),
                        verticalSpacing = if (state.selectedTab == AppTab.TODAY) ThemeTokens.spacing.mdDp else ThemeTokens.spacing.lgDp,
                    ) {
                        content()
                    }
                }
            }

            if (isCommandSheetExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF111315).copy(alpha = 0.25f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 88.dp),
                    ) {
                        FloatingCommandSheet(
                            enabledModules = state.preferences.enabledModules,
                            currentTab = state.selectedTab,
                            openTasksCount = state.today.currentTasks.size,
                            onSelectTab = {},
                            onQuickCaptureTask = {},
                            onQuickCaptureExpense = {},
                            onQuickCaptureNote = {},
                            onQuickCaptureThing = {},
                            onDismiss = {},
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            ) {
                FloatingCommandPill(
                    selectedTab = state.selectedTab,
                    enabledModules = state.preferences.enabledModules,
                    primaryActionLabel = primaryActionLabel,
                    onPrimaryActionClick = {},
                    isExpanded = isCommandSheetExpanded,
                    onToggleExpanded = {},
                )
            }
        }
    }

    companion object {
        private const val SnapshotRootTag = "snapshot-root"
    }
}
