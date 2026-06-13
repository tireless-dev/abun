package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.theme.AppTheme
import dev.tireless.abun.ui.theme.ThemeTokens
import io.github.takahirom.roborazzi.captureRoboImage

private val screenshotOptions = RoborazziOptions(
    recordOptions = RoborazziOptions.RecordOptions(resizeScale = 1.0),
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0F),
)
private const val ScreenshotRootTag = "screenshot-root"

@OptIn(ExperimentalTestApi::class)
internal fun captureScreenshot(
    name: String,
    width: Int = 390,
    height: Int = 844,
    content: @Composable () -> Unit,
) = runDesktopComposeUiTest {
    setContent {
        AppTheme(darkTheme = false) {
            Box(
                modifier = Modifier
                    .size(width.dp, height.dp)
                    .testTag(ScreenshotRootTag)
                    .background(ThemeTokens.colors.background),
            ) {
                content()
            }
        }
    }

    waitForIdle()
    onNodeWithTag(ScreenshotRootTag).captureRoboImage(
        filePath = "src/jvmTest/screenshots/$name.png",
        roborazziOptions = screenshotOptions,
    )
}

@Composable
internal fun ScreenshotScreenFrame(
    title: String,
    selectedTab: AppTab,
    content: @Composable () -> Unit,
) {
    MaterialScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ThemeTokens.colors.background,
                    titleContentColor = ThemeTokens.colors.textPrimary,
                ),
                title = {
                    Text(
                        text = title,
                        style = ThemeTokens.type.title,
                    )
                },
            )
        },
        bottomBar = {
            Surface(color = ThemeTokens.colors.surface, contentColor = ThemeTokens.colors.textSecondary) {
                SecondaryTabRow(
                    selectedTabIndex = AppTab.entries.indexOf(selectedTab).coerceAtLeast(0),
                    containerColor = ThemeTokens.colors.surface,
                    contentColor = ThemeTokens.colors.textSecondary,
                    divider = { HorizontalDivider(color = ThemeTokens.colors.border) },
                ) {
                    AppTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = {},
                            selectedContentColor = ThemeTokens.colors.textPrimary,
                            unselectedContentColor = ThemeTokens.colors.textSecondary,
                            text = { Text(tab.tabLabelForScreenshot(), style = ThemeTokens.type.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == AppTab.TODAY || selectedTab == AppTab.TASKS) {
                ExtendedFloatingActionButton(
                    onClick = {},
                    icon = { Text("+") },
                    text = { Text("Task") },
                )
            }
        },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeTokens.colors.background)
                .padding(padding)
                .padding(ThemeTokens.spacing.screenPaddingDp),
            verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.mdDp),
        ) {
            content()
        }
    }
}

internal fun AppTab.tabLabelForScreenshot(): String = when (this) {
    AppTab.TODAY -> "Day"
    AppTab.TASKS -> "Tasks"
    AppTab.SETTINGS -> "Settings"
}
