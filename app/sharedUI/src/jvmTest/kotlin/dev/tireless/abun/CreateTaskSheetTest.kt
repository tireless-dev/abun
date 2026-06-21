package dev.tireless.abun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.tireless.abun.ui.sheets.CreateTaskSheet
import dev.tireless.abun.ui.sheets.TaskCreateContext
import dev.tireless.abun.ui.sheets.TaskCreateSource
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class CreateTaskSheetTest {
    @Test
    fun `tasks context defaults to backlog quick capture`() = runDesktopComposeUiTest {
        setContent {
            CreateTaskSheet(
                context = TaskCreateContext(
                    source = TaskCreateSource.TASKS,
                    selectedDate = "2026-06-11",
                ),
                onDismiss = {},
                onCreate = {},
            )
        }

        onNodeWithText("Task title").fetchSemanticsNode()
        onNodeWithText("Detail").fetchSemanticsNode()
        onNodeWithText("Add schedule").fetchSemanticsNode()
        onNodeWithText("This task will go to backlog.").fetchSemanticsNode()
    }

    @Test
    fun `day context defaults to selected day schedule`() = runDesktopComposeUiTest {
        setContent {
            CreateTaskSheet(
                context = TaskCreateContext(
                    source = TaskCreateSource.DAY,
                    selectedDate = "2026-06-11",
                ),
                onDismiss = {},
                onCreate = {},
            )
        }

        onNodeWithText("Task title").fetchSemanticsNode()
        onNodeWithText("Detail").fetchSemanticsNode()
        onNodeWithText("Starts on").fetchSemanticsNode()
        onNodeWithText("Estimated duration").fetchSemanticsNode()
        onNodeWithText("No estimate").fetchSemanticsNode()
        onNodeWithText("Clear schedule").fetchSemanticsNode()
        onNodeWithText("Selected day").fetchSemanticsNode()
        onNodeWithText("2026-06-11").fetchSemanticsNode()
    }
}
