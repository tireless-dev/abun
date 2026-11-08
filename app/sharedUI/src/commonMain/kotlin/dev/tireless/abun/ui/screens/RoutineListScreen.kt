package dev.tireless.abun.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tireless.abun.app.AppUiState
import dev.tireless.abun.app.RoutineListItemView
import dev.tireless.abun.ui.SwissBentoCard
import dev.tireless.abun.ui.SwissCardHeader
import dev.tireless.abun.ui.SwissHairlineDivider
import dev.tireless.abun.ui.SwissMetricData
import dev.tireless.abun.ui.SwissMetricStrip
import dev.tireless.abun.ui.components.RoutineRow
import dev.tireless.abun.ui.theme.ThemeTokens
import dev.tireless.abun.ui.theme.withMaterialContentColor

@Composable
internal fun RoutineListScreen(
    state: AppUiState,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (RoutineListItemView) -> Unit,
    onRunRoutine: (RoutineListItemView) -> Unit,
) {
    val routines = state.taskView.routines
    val activeCount = remember(routines) { routines.count { it.isActive } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.lgDp),
    ) {
        // Frameless Swiss Precision Routine Adherence Hero directly on canvas
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Routine support",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = ThemeTokens.colors.textSecondary,
                        ),
                    )
                    Text(
                        text = "ROUTINE ADHERENCE · 习惯达成率",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = ThemeTokens.colors.onSurfaceVariant,
                        ),
                    )
                }
                Button(onClick = onCreateRoutine) {
                    Text("Create", style = ThemeTokens.type.body.withMaterialContentColor())
                }
            }

            Text(
                text = "$activeCount ACTIVE",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = ThemeTokens.colors.textPrimary,
                    letterSpacing = (-0.5).sp,
                ),
            )

            SwissMetricStrip(
                metrics = listOf(
                    SwissMetricData(label = "TOTAL ROUTINES", value = "${routines.size}"),
                    SwissMetricData(label = "ACTIVE STREAK", value = "7D STREAK", valueColor = ThemeTokens.colors.tasksAccent),
                    SwissMetricData(label = "ROLLOVER", value = state.preferences.rolloverTime),
                ),
            )
        }

        // Active routines bento card
        SwissBentoCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tasks-panel-routines"),
            contentPadding = PaddingValues(16.dp),
        ) {
            SwissCardHeader(
                title = "ACTIVE ROUTINES · 循环例程",
                accentDotColor = ThemeTokens.colors.tasksAccent,
                trailingText = "7D STREAK",
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (routines.isEmpty()) {
                Text(
                    text = "No routines.",
                    style = ThemeTokens.type.bodyMuted,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                routines.forEachIndexed { index, routine ->
                    if (index > 0) {
                        SwissHairlineDivider()
                    }
                    RoutineRow(
                        routine = routine,
                        onOpen = onOpenRoutine,
                        onRun = onRunRoutine,
                    )
                }
            }
        }
    }
}
