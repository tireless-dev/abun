package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import dev.tireless.abun.app.AppTab
import dev.tireless.abun.ui.layout.Scaffold
import dev.tireless.abun.ui.layout.ScreenContainer
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
    Scaffold(
        title = title,
        selectedTab = selectedTab.tabLabelForScreenshot(),
        tabs = AppTab.entries.map { it.tabLabelForScreenshot() },
        onSelectTab = {},
        floatingActionLabel = if (selectedTab == AppTab.TODAY || selectedTab == AppTab.TASKS) "Task" else null,
        onFloatingAction = {},
    ) { padding: PaddingValues ->
        ScreenContainer(
            modifier = Modifier
                .background(ThemeTokens.colors.background)
                .padding(padding),
            applyVerticalSafeInsets = false,
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
