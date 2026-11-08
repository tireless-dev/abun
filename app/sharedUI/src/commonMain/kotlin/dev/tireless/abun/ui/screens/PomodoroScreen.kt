package dev.tireless.abun.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroTaskUpdate
import dev.tireless.abun.formatRemaining
import dev.tireless.abun.label
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.ui.SegmentedTickBar
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.SwissHairlineDivider
import dev.tireless.abun.ui.components.PomodoroRow
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

private fun TaskStatus.isOpen(): Boolean = this == TaskStatus.PENDING || this == TaskStatus.IN_PROGRESS || this == TaskStatus.UNKNOWN

@Composable
internal fun PomodoroScreen(
    state: AppUiState,
    liveNow: Long,
    onOpenStart: () -> Unit,
    onStartPomodoro: ((String?, PomodoroPhase) -> Unit)? = null,
    onCompletePomodoro: ((String, PomodoroTaskUpdate) -> Unit)? = null,
    onStopPomodoro: ((String) -> Unit)? = null,
) {
    val active = state.activePomodoroSession
    var selectedPhase by remember { mutableStateOf(PomodoroPhase.FOCUS) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var taskSelectorOpen by remember { mutableStateOf(false) }

    val openTasks = remember(state.taskView.tasks) {
        state.taskView.tasks.filter { it.status.isOpen() }
    }
    val selectedTask = remember(selectedTaskId, openTasks) {
        openTasks.firstOrNull { it.id == selectedTaskId }
    }

    val currentPhase = active?.phase ?: selectedPhase
    val phaseDurationMinutes = when (currentPhase) {
        PomodoroPhase.FOCUS -> state.preferences.focusMinutes
        PomodoroPhase.SHORT_BREAK -> state.preferences.shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> state.preferences.longBreakMinutes
    }

    val remainingMillis = active?.let { (it.endsAtEpochMillis - liveNow).coerceAtLeast(0L) } ?: 0L
    val totalMillis = (active?.durationMinutes ?: phaseDurationMinutes) * 60_000L
    val elapsedMillis = if (active != null) (totalMillis - remainingMillis).coerceAtLeast(0L) else 0L
    val filledSegments = if (active != null && totalMillis > 0) {
        ((elapsedMillis.toDouble() / totalMillis.toDouble()) * 25).toInt().coerceIn(0, 25)
    } else {
        0
    }

    val timerReadout = if (active != null) {
        formatRemaining(remainingMillis)
    } else {
        "${phaseDurationMinutes.toString().padStart(2, '0')}:00"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tasks-panel-pomodoro")
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.lgDp),
    ) {
        // Unboxed Chronograph Hero directly on canvas
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header with Timer label & active phase pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ThemeTokens.colors.tasksAccent),
                    )
                    Text(
                        text = "Timer",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                    Text(
                        text = "· POMODORO CHRONOGRAPH",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = ThemeTokens.colors.onSurfaceVariant,
                        ),
                    )
                }

                Surface(
                    color = ThemeTokens.colors.tasksSoft,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = currentPhase.label().uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = ThemeTokens.colors.tasksAccent,
                        ),
                    )
                }
            }

            // Giant 54.sp bold monospace timer readout
            Text(
                text = timerReadout,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    color = ThemeTokens.colors.textPrimary,
                ),
            )

            // 25-Segment Tick Bar
            SegmentedTickBar(
                totalSegments = 25,
                filledSegments = filledSegments,
                filledColor = ThemeTokens.colors.tasksAccent,
                segmentHeight = 8.dp,
            )

            // Active/Selected Task binding card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ThemeTokens.colors.surface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ThemeTokens.colors.cardBorder),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Crisp indigo left accent bar
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(64.dp)
                            .background(ThemeTokens.colors.tasksAccent),
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "BOUND TASK · 当前绑定任务",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = ThemeTokens.colors.textSecondary,
                            ),
                        )
                        if (active != null) {
                            Text(
                                text = active.taskTitle ?: "No task bound",
                                style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Medium),
                                color = ThemeTokens.colors.textPrimary,
                            )
                        } else {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { taskSelectorOpen = true }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = selectedTask?.title ?: "No task",
                                        style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Medium),
                                        color = ThemeTokens.colors.textPrimary,
                                    )
                                    Icon(
                                        imageVector = Lucide.ChevronDown,
                                        contentDescription = "Select task",
                                        modifier = Modifier.size(16.dp),
                                        tint = ThemeTokens.colors.textSecondary,
                                    )
                                }
                                DropdownMenu(
                                    expanded = taskSelectorOpen,
                                    onDismissRequest = { taskSelectorOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("No task", style = ThemeTokens.type.body) },
                                        onClick = {
                                            selectedTaskId = null
                                            taskSelectorOpen = false
                                        },
                                    )
                                    openTasks.forEach { task ->
                                        DropdownMenuItem(
                                            text = { Text(task.title, style = ThemeTokens.type.body) },
                                            onClick = {
                                                selectedTaskId = task.id
                                                taskSelectorOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Phase selector row (when idle)
            if (active == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ThemeTokens.colors.subnavTrack)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        PomodoroPhase.FOCUS to "Focus",
                        PomodoroPhase.SHORT_BREAK to "Short break",
                        PomodoroPhase.LONG_BREAK to "Long break",
                    ).forEach { (phase, label) ->
                        val isSelected = phase == selectedPhase
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) ThemeTokens.colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { selectedPhase = phase }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) ThemeTokens.colors.textPrimary else ThemeTokens.colors.textSecondary,
                                ),
                            )
                        }
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active == null) {
                    Button(
                        onClick = {
                            if (onStartPomodoro != null) {
                                onStartPomodoro(selectedTaskId, selectedPhase)
                            } else {
                                onOpenStart()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Start Pomodoro", style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                } else {
                    Button(
                        onClick = {
                            if (onCompletePomodoro != null) {
                                onCompletePomodoro(active.id, PomodoroTaskUpdate.NONE)
                            } else {
                                onOpenStart()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Complete session", style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                    OutlinedButton(
                        onClick = {
                            if (onStopPomodoro != null) {
                                onStopPomodoro(active.id)
                            } else {
                                onOpenStart()
                            }
                        },
                    ) {
                        Text("Cancel session", style = ThemeTokens.type.body.withMaterialContentColor())
                    }
                }
            }
        }

        // Session Log Bento Card
        SwissBentoCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "SESSION LOG · 专注记录",
                accentDotColor = ThemeTokens.colors.tasksAccent,
                trailingText = "${state.recentPomodoroSessions.size} SESSIONS",
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (state.recentPomodoroSessions.isEmpty()) {
                Text(
                    text = "No sessions.",
                    style = ThemeTokens.type.bodyMuted,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                state.recentPomodoroSessions.forEachIndexed { index, session ->
                    if (index > 0) {
                        SwissHairlineDivider()
                    }
                    PomodoroRow(session = session)
                }
            }
        }
    }
}
