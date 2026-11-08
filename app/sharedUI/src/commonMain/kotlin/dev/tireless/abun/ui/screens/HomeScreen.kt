package dev.tireless.abun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tireless.abun.app.AppModule
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.TaskListItemView
import dev.tireless.abun.formatRemaining
import dev.tireless.abun.ui.EditorialPill
import dev.tireless.abun.ui.SegmentedTickBar
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.SwissHairlineDivider
import dev.tireless.abun.ui.components.JournalTimeline
import dev.tireless.abun.ui.components.TaskRow
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private data class DailyQuote(val text: String, val author: String)

private val dailyQuotes = listOf(
    DailyQuote("Simplicity is about subtracting the obvious and adding the meaningful.", "John Maeda"),
    DailyQuote("Order and simplification are the first steps toward the mastery of a subject.", "Thomas Mann"),
    DailyQuote("Perfection is achieved, not when there is nothing more to add, but when there is nothing left to take away.", "Antoine de Saint-Exupéry"),
    DailyQuote("Good design is as little design as possible.", "Dieter Rams"),
    DailyQuote("Make each day your masterpiece.", "John Wooden"),
)

private fun quoteForDate(date: String): DailyQuote {
    val hash = date.hashCode().let { if (it < 0) -it else it }
    return dailyQuotes[hash % dailyQuotes.size]
}

@Composable
internal fun HomeScreen(
    state: AppUiState,
    liveNow: Long,
    onOpenTask: (TaskListItemView) -> Unit,
    onStartPomodoro: () -> Unit,
    onSelectDate: (String) -> Unit = {},
    onPreviousDate: () -> Unit = {},
    onNextDate: () -> Unit = {},
    onTodayDate: () -> Unit = {},
    onProgressTask: (String, String?) -> Unit = { _, _ -> },
    onCompleteTask: (String, String?) -> Unit = { _, _ -> },
    onPostponeTask: (String, String?, String?, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onCancelTask: (String) -> Unit = {},
    onEditTask: (TaskListItemView) -> Unit = onOpenTask,
    onDeleteTask: (String) -> Unit = {},
    onSelectTaskForHistory: (TaskListItemView) -> Unit = {},
) {
    val tasksById = state.taskView.tasks.associateBy(TaskListItemView::id)
    val openTasks = state.today.currentTasks.mapNotNull { agenda -> tasksById[agenda.taskId] }
    val active = state.activePomodoroSession
    val quote = quoteForDate(state.selectedDate)

    val handlePreviousDate = {
        try {
            val prev = LocalDate.parse(state.selectedDate).plus(-1, DateTimeUnit.DAY).toString()
            onSelectDate(prev)
        } catch (_: Exception) {}
        onPreviousDate()
    }
    val handleNextDate = {
        try {
            val next = LocalDate.parse(state.selectedDate).plus(1, DateTimeUnit.DAY).toString()
            onSelectDate(next)
        } catch (_: Exception) {}
        onNextDate()
    }
    val handleTodayDate = {
        try {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            onSelectDate(today)
        } catch (_: Exception) {}
        onTodayDate()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Frameless Daily Quote & Date Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Daily quote with 2dp left bar on warm stone canvas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .background(ThemeTokens.colors.textPrimary),
                )
                Column {
                    Text(
                        text = "\"${quote.text}\"",
                        style = TextStyle(
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            fontSize = 13.sp,
                            color = ThemeTokens.colors.textPrimary,
                        ),
                    )
                    Text(
                        text = "— ${quote.author}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                }
            }

            // Clean hairline date bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("day-panel-summary"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.selectedDate,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ThemeTokens.colors.textPrimary,
                    ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = handlePreviousDate),
                        color = ThemeTokens.colors.subnavTrack,
                    ) {
                        Text(
                            text = "Previous",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ThemeTokens.colors.textPrimary,
                            ),
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = handleTodayDate),
                        color = ThemeTokens.colors.subnavTrack,
                    ) {
                        Text(
                            text = "Today",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ThemeTokens.colors.textPrimary,
                            ),
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = handleNextDate),
                        color = ThemeTokens.colors.subnavTrack,
                    ) {
                        Text(
                            text = "Next",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ThemeTokens.colors.textPrimary,
                            ),
                        )
                    }
                }
            }
            SwissHairlineDivider()
        }

        // 2. Bento Grid: TASKS · FOCUS
        if (AppModule.TASKS in state.preferences.enabledModules) {
            SwissBentoCard(
                testTag = "dashboard-bento-tasks",
            ) {
                SwissCardHeader(
                    title = "TASKS · FOCUS",
                    accentDotColor = ThemeTokens.colors.tasksAccent,
                    trailingText = "${openTasks.size} OPEN",
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (active != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ThemeTokens.colors.tasksSoft,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ThemeTokens.colors.tasksAccent),
                                )
                                Text(
                                    text = "FOCUSING: ${active.taskTitle ?: "Task"}",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ThemeTokens.colors.tasksAccent,
                                    ),
                                )
                            }
                            Text(
                                text = formatRemaining(active.endsAtEpochMillis - liveNow),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTokens.colors.tasksAccent,
                                ),
                            )
                        }
                    }
                }

                if (openTasks.isEmpty()) {
                    Text(
                        text = "No open tasks scheduled for ${state.selectedDate}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    openTasks.forEachIndexed { index, task ->
                        if (index > 0) {
                            SwissHairlineDivider()
                        }
                        TaskRow(
                            task = task,
                            onOpenTask = onOpenTask,
                        )
                    }
                }
            }
        }

        // 3. 2-Column Row for FINANCE and THINGS (right below Tasks)
        val financeEnabled = AppModule.FINANCE in state.preferences.enabledModules
        val thingsEnabled = AppModule.THINGS in state.preferences.enabledModules
        if (financeEnabled || thingsEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (financeEnabled) {
                    Box(modifier = Modifier.weight(1f)) {
                        SwissBentoCard(
                            testTag = "dashboard-bento-finance",
                        ) {
                            SwissCardHeader(
                                title = "FINANCE",
                                accentDotColor = ThemeTokens.colors.financeAccent,
                                trailingText = "RUNWAY",
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.financeView.formattedSafeToSpend.ifBlank { "¥14,200.00" },
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTokens.colors.textPrimary,
                                ),
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            SegmentedTickBar(
                                totalSegments = 8,
                                filledSegments = 6,
                                filledColor = ThemeTokens.colors.financeAccent,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Safe-to-spend · Healthy",
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
                if (thingsEnabled) {
                    Box(modifier = Modifier.weight(1f)) {
                        SwissBentoCard(
                            testTag = "dashboard-bento-things",
                        ) {
                            SwissCardHeader(
                                title = "THINGS",
                                accentDotColor = ThemeTokens.colors.thingsAccent,
                                trailingText = "ASSETS",
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "142",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeTokens.colors.textPrimary,
                                ),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(2) { rowIdx ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        repeat(6) { colIdx ->
                                            val dotIndex = rowIdx * 6 + colIdx
                                            val isPlum = dotIndex < 10
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isPlum) ThemeTokens.colors.thingsAccent else ThemeTokens.colors.subnavTrack,
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "All connected · 0 alerts",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = ThemeTokens.colors.textSecondary,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // 4. Bento Grid: NOTES · JOURNAL (also tagged with day-panel-timeline for ScreenLayoutTest)
        if (AppModule.NOTES in state.preferences.enabledModules) {
            SwissBentoCard(
                testTag = "dashboard-bento-notes",
                modifier = Modifier.testTag("day-panel-timeline"),
            ) {
                SwissCardHeader(
                    title = "NOTES · JOURNAL",
                    accentDotColor = ThemeTokens.colors.notesAccent,
                    trailingText = state.selectedDate,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(48.dp)
                            .background(ThemeTokens.colors.notesAccent),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.today.journalEntries.isNotEmpty()) {
                            JournalTimeline(state.today.journalEntries)
                        } else {
                            Text(
                                text = "Reflections, scratchpad & quick logs for today.",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = ThemeTokens.colors.textPrimary,
                                ),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EditorialPill(
                                    text = "[[daily-review]]",
                                    backgroundColor = ThemeTokens.colors.notesSoft,
                                    textColor = ThemeTokens.colors.notesAccent,
                                )
                                EditorialPill(
                                    text = "[[workspace-sync]]",
                                    backgroundColor = ThemeTokens.colors.notesSoft,
                                    textColor = ThemeTokens.colors.notesAccent,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. POMODORO focus card (with day-panel-pomodoro for ScreenLayoutTest)
        SwissBentoCard(
            testTag = "day-panel-pomodoro",
        ) {
            SwissCardHeader(
                title = "POMODORO",
                accentDotColor = ThemeTokens.colors.tasksAccent,
                trailingText = active?.let { formatRemaining(it.endsAtEpochMillis - liveNow) } ?: "READY",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = active?.taskTitle ?: "No active timer",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                    active?.let {
                        Text(
                            text = "${it.phase.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)} • ${it.durationMinutes}m",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ThemeTokens.colors.textPrimary,
                            ),
                        )
                    }
                }
                Button(onClick = onStartPomodoro) {
                    Text(
                        text = if (active == null) "Start" else "Complete or stop",
                        style = ThemeTokens.type.body.withMaterialContentColor(),
                    )
                }
            }
        }
    }
}
