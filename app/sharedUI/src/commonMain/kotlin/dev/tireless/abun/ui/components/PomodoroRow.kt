package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tireless.abun.app.PomodoroPhase
import dev.tireless.abun.app.PomodoroSessionState
import dev.tireless.abun.app.PomodoroSessionView
import dev.tireless.abun.label
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
internal fun PomodoroRow(session: PomodoroSessionView) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = session.taskTitle ?: "Standalone timer",
                style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Medium),
                color = ThemeTokens.colors.textPrimary,
            )
            Text(
                text = "${session.durationMinutes}m",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTokens.colors.textSecondary,
                ),
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val isFocus = session.phase == PomodoroPhase.FOCUS
            Surface(
                color = if (isFocus) ThemeTokens.colors.tasksSoft else ThemeTokens.colors.surfaceMuted,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = session.phase.label().uppercase(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocus) ThemeTokens.colors.tasksAccent else ThemeTokens.colors.textSecondary,
                    ),
                )
            }

            val stateColor = when (session.state) {
                PomodoroSessionState.COMPLETED -> ThemeTokens.colors.success
                PomodoroSessionState.CANCELLED -> ThemeTokens.colors.error
                PomodoroSessionState.ACTIVE -> ThemeTokens.colors.tasksAccent
            }
            Surface(
                color = ThemeTokens.colors.surfaceMuted,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = session.state.name,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = stateColor,
                    ),
                )
            }

            session.note?.let {
                Text(
                    text = it,
                    style = ThemeTokens.type.bodyMuted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
