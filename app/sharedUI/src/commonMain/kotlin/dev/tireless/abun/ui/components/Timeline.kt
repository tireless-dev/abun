package dev.tireless.abun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import dev.tireless.abun.app.JournalEntryView
import dev.tireless.abun.taskEventLabel
import dev.tireless.abun.ui.EditorialCard
import dev.tireless.abun.ui.theme.ThemeTokens

@Composable
internal fun JournalTimeline(entries: List<JournalEntryView>) {
    if (entries.isEmpty()) {
        Text("No history for this date.", style = ThemeTokens.type.body)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.smDp)) {
        entries.forEach { entry ->
            EditorialCard {
                Text(entry.title, style = ThemeTokens.type.body.copy(fontWeight = FontWeight.Bold))
                Text("${taskEventLabel(entry.eventType)} • ${entry.eventTimeLabel}", style = ThemeTokens.type.bodyMuted)
                entry.content?.takeIf(String::isNotBlank)?.let { Text(it, style = ThemeTokens.type.bodyMuted) }
            }
        }
    }
}
