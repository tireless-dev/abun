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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
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
                    .background(ThemeTokens.colors.background),
            ) {
                content()
            }
        }
    }

    waitForIdle()
    onRoot().captureRoboImage(
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
                title = {
                    Text(
                        text = title,
                        style = ThemeTokens.type.title,
                    )
                },
            )
        },
        bottomBar = {
            TabRow(selectedTabIndex = AppTab.entries.indexOf(selectedTab).coerceAtLeast(0)) {
                AppTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {},
                        text = { Text(tab.tabLabelForScreenshot()) },
                    )
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
