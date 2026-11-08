package dev.tireless.abun.app

import dev.tireless.abun.sync.PomodoroPhaseWire
import dev.tireless.abun.sync.PomodoroSessionStateWire
import dev.tireless.abun.sync.PomodoroTaskUpdateWire
import dev.tireless.abun.sync.TaskEventType

class PomodoroStore(private val store: LocalStore) {
    fun activePomodoroSession(
        preferences: PreferencesViewState = store.preferenceStore.preferences(),
        nowEpochMillis: Long = store.timeProvider.nowEpochMillis(),
    ): PomodoroSessionView? {
        val tasksById = store.taskStore.allTasks().associateBy(TaskListItemView::id)
        return store.queries.selectActivePomodoroSession(::mapPomodoroSessionRow).executeAsOneOrNull()?.let {
            toPomodoroSessionView(it, tasksById, nowEpochMillis)
        }
    }

    fun recentPomodoroSessions(
        limit: Int = 8,
        preferences: PreferencesViewState = store.preferenceStore.preferences(),
        nowEpochMillis: Long = store.timeProvider.nowEpochMillis(),
    ): List<PomodoroSessionView> {
        val tasksById = store.taskStore.allTasks().associateBy(TaskListItemView::id)
        return store.queries.selectRecentPomodoroSessions(limit.toLong(), ::mapPomodoroSessionRow).executeAsList().map {
            toPomodoroSessionView(it, tasksById, nowEpochMillis)
        }
    }

    fun pomodoroStartableTasks(currentDate: String = store.timeProvider.today().toString()): List<TaskListItemView> =
        store.taskStore.openTasksForDate(currentDate)

    fun startPomodoroSession(
        taskId: String?,
        phase: PomodoroPhase,
        preferences: PreferencesViewState = store.preferenceStore.preferences(),
    ): PomodoroSessionView? {
        val existing = activePomodoroSession(preferences, store.timeProvider.nowEpochMillis())
        if (existing != null) return existing
        val currentDate = store.timeProvider.today().toString()
        if (taskId != null && !store.taskStore.isValidPomodoroTask(taskId, currentDate)) return null
        val now = store.timeProvider.nowEpochMillis()
        val durationMinutes = durationForPhase(phase, preferences)
        val row = MutableSyncRow(
            entity = LocalPomodoroSession(
                id = store.idGenerator.randomId(),
                taskId = taskId,
                phase = phase,
                state = PomodoroSessionState.ACTIVE,
                startedAt = now,
                endsAt = now + durationMinutes * MILLIS_PER_MINUTE,
                completedAt = null,
                durationMinutes = durationMinutes,
                note = null,
                taskUpdate = PomodoroTaskUpdate.NONE,
                isDeleted = false,
                serverVersion = 0,
                createdAt = now,
                updatedAt = now,
            ),
            hlcMap = mapOf(
                "task" to store.clock.next(),
                "timing" to store.clock.next(),
                "state" to store.clock.next(),
            ),
            dirtyFields = listOf("task", "timing", "state"),
        )
        persistPomodoroSession(row.entity, row.hlcMap, row.dirtyFields, isDirty = true)
        if (taskId != null && phase == PomodoroPhase.FOCUS) {
            store.taskStore.appendTaskEvent(taskId, currentDate, TaskEventType.PROGRESSED, null)
        }
        val tasksById = store.taskStore.allTasks().associateBy(TaskListItemView::id)
        return toPomodoroSessionView(row, tasksById, now)
    }

    fun completePomodoroSession(
        sessionId: String,
        note: String?,
        taskUpdate: PomodoroTaskUpdate,
        journalDate: String,
    ) = store.database.transaction {
        val existing = store.queries.selectPomodoroSessionById(sessionId, ::mapPomodoroSessionRow).executeAsOneOrNull() ?: return@transaction
        val now = store.timeProvider.nowEpochMillis()
        val updated = existing.entity.copy(
            state = PomodoroSessionState.COMPLETED,
            completedAt = now,
            note = note?.trim()?.ifBlank { null },
            taskUpdate = taskUpdate,
            updatedAt = now,
        )
        val updatedHlc = existing.hlcMap + mapOf(
            "state" to store.clock.next(existing.hlcMap["state"]),
            "note" to store.clock.next(existing.hlcMap["note"]),
            "outcome" to store.clock.next(existing.hlcMap["outcome"]),
        )
        val dirty = (existing.dirtyFields + listOf("state", "note", "outcome")).distinct()
        persistPomodoroSession(updated, updatedHlc, dirty, isDirty = true)
        existing.entity.taskId?.let { taskId ->
            if (store.taskStore.isValidPomodoroTask(taskId, store.timeProvider.today().toString())) {
                when (taskUpdate) {
                    PomodoroTaskUpdate.NONE -> Unit
                    PomodoroTaskUpdate.PROGRESS -> store.taskStore.appendTaskEvent(taskId, journalDate, TaskEventType.PROGRESSED, note)
                    PomodoroTaskUpdate.COMPLETE -> store.taskStore.appendTaskEvent(taskId, journalDate, TaskEventType.COMPLETED, note)
                    PomodoroTaskUpdate.CANCEL -> store.taskStore.appendTaskEvent(taskId, journalDate, TaskEventType.CANCELLED, note)
                }
            }
        }
    }

    fun cancelPomodoroSession(sessionId: String, note: String?) = store.database.transaction {
        val existing = store.queries.selectPomodoroSessionById(sessionId, ::mapPomodoroSessionRow).executeAsOneOrNull() ?: return@transaction
        val now = store.timeProvider.nowEpochMillis()
        val updated = existing.entity.copy(
            state = PomodoroSessionState.CANCELLED,
            completedAt = now,
            note = note?.trim()?.ifBlank { null },
            updatedAt = now,
        )
        val updatedHlc = existing.hlcMap + mapOf(
            "state" to store.clock.next(existing.hlcMap["state"]),
            "note" to store.clock.next(existing.hlcMap["note"]),
        )
        val dirty = (existing.dirtyFields + listOf("state", "note")).distinct()
        persistPomodoroSession(updated, updatedHlc, dirty, isDirty = true)
    }

    internal fun persistPomodoroSession(session: LocalPomodoroSession, hlcMap: Map<String, String>, dirtyFields: List<String>, isDirty: Boolean) {
        store.queries.upsertPomodoroSession(
            session.id,
            session.taskId,
            session.phase.name,
            session.state.name,
            session.startedAt,
            session.endsAt,
            session.completedAt,
            session.durationMinutes.toLong(),
            session.note,
            session.taskUpdate.name,
            session.isDeleted.toLong(),
            JsonCodecs.encodeMap(hlcMap),
            JsonCodecs.encodeList(dirtyFields),
            isDirty.toLong(),
            session.serverVersion,
            session.createdAt,
            session.updatedAt,
        )
    }

    internal fun mapPomodoroSessionRow(
        id: String,
        task_id: String?,
        phase: String,
        state: String,
        started_at: Long,
        ends_at: Long,
        completed_at: Long?,
        duration_minutes: Long,
        note: String?,
        task_update: String,
        is_deleted: Long,
        hlc_map: String,
        dirty_fields: String,
        is_dirty: Long,
        server_version: Long,
        created_at: Long,
        updated_at: Long,
    ): MutableSyncRow<LocalPomodoroSession> = MutableSyncRow(
        LocalPomodoroSession(id, task_id, enumValueOf(phase), enumValueOf(state), started_at, ends_at, completed_at, duration_minutes.toInt(), note, enumValueOf(task_update), is_deleted != 0L, server_version, created_at, updated_at),
        JsonCodecs.decodeMap(hlc_map),
        if (is_dirty != 0L) JsonCodecs.decodeList(dirty_fields) else emptyList(),
    )

    private fun toPomodoroSessionView(
        row: MutableSyncRow<LocalPomodoroSession>,
        tasksById: Map<String, TaskListItemView>,
        nowEpochMillis: Long,
    ): PomodoroSessionView = PomodoroSessionView(
        id = row.entity.id,
        taskId = row.entity.taskId,
        taskTitle = row.entity.taskId?.let(tasksById::get)?.title,
        phase = row.entity.phase,
        state = row.entity.state,
        startedAtEpochMillis = row.entity.startedAt,
        endsAtEpochMillis = row.entity.endsAt,
        completedAtEpochMillis = row.entity.completedAt,
        durationMinutes = row.entity.durationMinutes,
        note = row.entity.note,
        taskUpdate = row.entity.taskUpdate,
        isOverdue = row.entity.state == PomodoroSessionState.ACTIVE && row.entity.endsAt <= nowEpochMillis,
    )

    private fun durationForPhase(phase: PomodoroPhase, preferences: PreferencesViewState): Int = when (phase) {
        PomodoroPhase.FOCUS -> preferences.focusMinutes
        PomodoroPhase.SHORT_BREAK -> preferences.shortBreakMinutes
        PomodoroPhase.LONG_BREAK -> preferences.longBreakMinutes
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

internal data class LocalPomodoroSession(
    val id: String,
    val taskId: String?,
    val phase: PomodoroPhase,
    val state: PomodoroSessionState,
    val startedAt: Long,
    val endsAt: Long,
    val completedAt: Long?,
    val durationMinutes: Int,
    val note: String?,
    val taskUpdate: PomodoroTaskUpdate,
    val isDeleted: Boolean,
    val serverVersion: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

internal fun PomodoroPhase.toWire(): PomodoroPhaseWire = PomodoroPhaseWire.valueOf(name)
internal fun PomodoroPhaseWire.toApp(): PomodoroPhase = PomodoroPhase.valueOf(name)
internal fun PomodoroSessionState.toWire(): PomodoroSessionStateWire = PomodoroSessionStateWire.valueOf(name)
internal fun PomodoroSessionStateWire.toApp(): PomodoroSessionState = PomodoroSessionState.valueOf(name)
internal fun PomodoroTaskUpdate.toWire(): PomodoroTaskUpdateWire = PomodoroTaskUpdateWire.valueOf(name)
internal fun PomodoroTaskUpdateWire.toApp(): PomodoroTaskUpdate = PomodoroTaskUpdate.valueOf(name)

private fun Boolean.toLong(): Long = if (this) 1 else 0
