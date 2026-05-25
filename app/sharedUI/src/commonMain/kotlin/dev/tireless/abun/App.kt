package dev.tireless.abun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.tireless.abun.sync.TaskStatus

@Composable
@Preview
fun App() {
    val controller = rememberAbunAppController()
    val state by controller.state.collectAsState()
    var draftTitle by remember { mutableStateOf("") }

    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .safeContentPadding()
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("abun", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Daily journal: ${state.selectedDate}", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("New task") },
                )
                Button(
                    onClick = {
                        controller.createTask(draftTitle)
                        draftTitle = ""
                    },
                ) {
                    Text("Add")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = controller::syncNow) {
                    Text(if (state.syncState.isSyncing) "Syncing..." else "Sync Now")
                }
                state.syncState.lastSyncedAt?.let {
                    Text("Last synced: $it", style = MaterialTheme.typography.bodySmall)
                }
            }

            state.syncState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Text("Tasks", style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.tasks, key = { it.id }) { task ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (task.status) {
                                    TaskStatus.PENDING -> "Pending"
                                    TaskStatus.IN_PROGRESS -> "In progress"
                                    TaskStatus.COMPLETED -> "Completed"
                                    TaskStatus.CANCELLED -> "Cancelled"
                                    TaskStatus.UNKNOWN -> "Unknown"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { controller.progressTask(task.id) }) {
                                    Text("Progress")
                                }
                                Button(onClick = { controller.completeTask(task.id) }) {
                                    Text("Complete")
                                }
                                Button(onClick = { controller.deleteTask(task.id) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Journal", style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.journalEntries, key = { it.eventId }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.title, fontWeight = FontWeight.SemiBold)
                            Text("${entry.eventType.name} at ${entry.eventTimeLabel}")
                            entry.content?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
