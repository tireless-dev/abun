package dev.tireless.abun.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.formatRemaining
import dev.tireless.abun.label
import dev.tireless.abun.ui.components.Panel
import dev.tireless.abun.ui.components.PomodoroRow
import dev.tireless.abun.ui.components.SectionHeader
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun PomodoroScreen(
    state: AppUiState,
    liveNow: Long,
    onOpenStart: () -> Unit,
) {
    val active = state.activePomodoroSession
    Panel {
        Text("Timer", style = ThemeTokens.type.label)
        Text(active?.let { formatRemaining(it.endsAtEpochMillis - liveNow) } ?: "00:00", style = ThemeTokens.type.display)
        Text(active?.taskTitle ?: "No active timer", style = ThemeTokens.type.bodyMuted)
        if (active == null) {
            Button(onClick = onOpenStart) { Text("Start", style = ThemeTokens.type.body.withMaterialContentColor()) }
        } else {
            Text("${active.phase.label()} • ${active.durationMinutes}m", style = ThemeTokens.type.bodyMuted)
            Button(onClick = onOpenStart) { Text("Complete or stop", style = ThemeTokens.type.body.withMaterialContentColor()) }
        }
    }

    Panel {
        SectionHeader("History", "Recent sessions")
        if (state.recentPomodoroSessions.isEmpty()) {
            Text("No sessions.", style = ThemeTokens.type.body)
        } else {
            state.recentPomodoroSessions.forEach { session ->
                PomodoroRow(session)
            }
        }
    }
}
