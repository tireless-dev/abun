package dev.tireless.abun.ui.sheets

import dev.tireless.abun.app.AppTab
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

internal data class TaskSaveDraft(
    val title: String,
    val detail: String?,
    val parentId: String?,
    val startNotBefore: String?,
    val endNotAfter: String?,
    val estimatedDuration: String?,
)

internal enum class TaskCreateSource {
    TASKS,
    DAY,
}

internal data class TaskCreateContext(
    val source: TaskCreateSource,
    val selectedDate: String,
)

internal data class TaskCreateDateOption(
    val label: String,
    val date: String,
)

internal enum class DurationPreset(
    val label: String,
    val isoDuration: String?,
) {
    NONE("No estimate", null),
    MINUTES_15("15m", "PT15M"),
    MINUTES_30("30m", "PT30M"),
    HOUR_1("1h", "PT1H"),
    HOUR_2("2h", "PT2H"),
    CUSTOM("Custom", null),
}

internal data class TaskCreateDraft(
    val title: String = "",
    val detail: String = "",
    val hasSchedule: Boolean,
    val startDate: String?,
    val durationPreset: DurationPreset,
    val customDurationMinutes: String = "",
)

internal fun normalizeTaskSaveDraft(
    title: String,
    detail: String,
    parentId: String?,
    startNotBefore: String,
    endNotAfter: String,
    estimatedDuration: String,
): TaskSaveDraft = TaskSaveDraft(
    title = title.trim(),
    detail = detail.ifBlank { null },
    parentId = parentId,
    startNotBefore = startNotBefore.ifBlank { null },
    endNotAfter = endNotAfter.ifBlank { null },
    estimatedDuration = estimatedDuration.ifBlank { null },
)

internal fun taskCreateContextFor(selectedTab: AppTab, selectedDate: String): TaskCreateContext = TaskCreateContext(
    source = if (selectedTab == AppTab.TODAY) TaskCreateSource.DAY else TaskCreateSource.TASKS,
    selectedDate = selectedDate,
)

internal fun defaultTaskCreateDraft(context: TaskCreateContext): TaskCreateDraft = TaskCreateDraft(
    hasSchedule = context.source == TaskCreateSource.DAY,
    startDate = if (context.source == TaskCreateSource.DAY) context.selectedDate else null,
    durationPreset = DurationPreset.NONE,
)

internal fun taskCreateDateOptions(context: TaskCreateContext): List<TaskCreateDateOption> {
    val anchor = LocalDate.parse(context.selectedDate)
    return when (context.source) {
        TaskCreateSource.DAY -> listOf(
            TaskCreateDateOption("Selected day", context.selectedDate),
            TaskCreateDateOption("Next day", anchor.plus(1, DateTimeUnit.DAY).toString()),
            TaskCreateDateOption("In 1 week", anchor.plus(7, DateTimeUnit.DAY).toString()),
        )
        TaskCreateSource.TASKS -> listOf(
            TaskCreateDateOption("Today", context.selectedDate),
            TaskCreateDateOption("Tomorrow", anchor.plus(1, DateTimeUnit.DAY).toString()),
            TaskCreateDateOption("In 1 week", anchor.plus(7, DateTimeUnit.DAY).toString()),
        )
    }
}

internal fun normalizeTaskCreateDraft(draft: TaskCreateDraft): TaskSaveDraft {
    val normalizedStartDate = draft.startDate?.takeIf { draft.hasSchedule && it.isNotBlank() }
    return TaskSaveDraft(
        title = draft.title.trim(),
        detail = draft.detail.ifBlank { null },
        parentId = null,
        startNotBefore = normalizedStartDate?.let(::taskCreateStartOfDayIso),
        endNotAfter = null,
        estimatedDuration = normalizedStartDate?.let {
            when (draft.durationPreset) {
                DurationPreset.NONE -> null
                DurationPreset.CUSTOM -> draft.customDurationMinutes
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { minutes -> minutes > 0 }
                    ?.let { minutes -> "PT${minutes}M" }
                else -> draft.durationPreset.isoDuration
            }
        },
    )
}

internal fun taskCreateStartOfDayIso(date: String): String =
    LocalDate.parse(date).atStartOfDayIn(TimeZone.currentSystemDefault()).toString()
