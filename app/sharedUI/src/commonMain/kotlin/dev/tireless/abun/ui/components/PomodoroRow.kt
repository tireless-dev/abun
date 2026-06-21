package dev.tireless.abun.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.tireless.abun.app.PomodoroSessionView
import dev.tireless.abun.label
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
internal fun PomodoroRow(session: PomodoroSessionView) {
    EditorialCard {
        Text(session.phase.label(), style = ThemeTokens.type.cardTitle)
        Text(session.taskTitle ?: "Standalone timer", style = ThemeTokens.type.bodyMuted)
        Text("${session.state.name.lowercase()} • ${session.durationMinutes}m", style = ThemeTokens.type.label)
        session.note?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
    }
}
