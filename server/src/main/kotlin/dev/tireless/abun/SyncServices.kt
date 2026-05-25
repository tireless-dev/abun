package dev.tireless.abun

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.tireless.abun.sync.HybridLogicalClock
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncConflictResolver
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import dev.tireless.abun.sync.TaskEventType
import dev.tireless.abun.sync.TaskStatus
import dev.tireless.abun.sync.TaskStatusDeriver
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.Properties
import java.util.UUID
import javax.sql.DataSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val serverJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
private val stringListSerializer = ListSerializer(String.serializer())

class AppServices private constructor(
    private val database: ServerDatabase,
    clock: () -> Instant = Instant::now,
) : AutoCloseable {
    private val serverClock = HybridLogicalClock(nodeId = "server") { clock().toEpochMilli() }

    internal val routines = RoutineSyncService(database)
    internal val tasks = TaskSyncService(database, serverClock)
    internal val alarms = AlarmSyncService(database)
    internal val taskEvents = TaskEventSyncService(database)

    override fun close() {
        database.close()
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): AppServices =
            AppServices(ServerDatabase.fromConfig(ServerDatabaseConfig.fromEnvironment(environment)))

        fun forDataSource(dataSource: DataSource): AppServices = AppServices(ServerDatabase(dataSource))
    }
}

internal data class ServerDatabaseConfig(
    val jdbcUrl: String,
    val username: String? = null,
    val password: String? = null,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String>): ServerDatabaseConfig {
            val rawUrl = environment["SUPABASE_DB_URL"]
                ?: environment["DATABASE_URL"]
                ?: error("Missing SUPABASE_DB_URL or DATABASE_URL")
            val jdbcUrl = normalizeJdbcUrl(rawUrl)
            return ServerDatabaseConfig(
                jdbcUrl = jdbcUrl,
                username = environment["SUPABASE_DB_USER"] ?: environment["DATABASE_USER"],
                password = environment["SUPABASE_DB_PASSWORD"] ?: environment["DATABASE_PASSWORD"],
            )
        }

        internal fun normalizeJdbcUrl(rawUrl: String): String {
            if (rawUrl.startsWith("jdbc:")) return rawUrl
            val base = when {
                rawUrl.startsWith("postgresql://") -> "jdbc:$rawUrl"
                rawUrl.startsWith("postgres://") -> "jdbc:postgresql://${rawUrl.removePrefix("postgres://")}"
                else -> error("Unsupported database URL: $rawUrl")
            }
            return if ("sslmode=" in base) base else "$base${if ('?' in base) '&' else '?'}sslmode=require"
        }
    }
}

internal class ServerDatabase(
    private val dataSource: DataSource,
) : AutoCloseable {
    init {
        bootstrap()
    }

    fun <T> read(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = true
        block(connection)
    }

    fun <T> tx(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        }
    }

    fun nextServerVersion(connection: Connection): Long {
        connection.prepareStatement("SELECT next_value FROM sync_server_version WHERE id = 1 FOR UPDATE").use { statement ->
            statement.executeQuery().use { result ->
                result.next()
                val next = result.getLong("next_value") + 1
                connection.prepareStatement("UPDATE sync_server_version SET next_value = ? WHERE id = 1").use { update ->
                    update.setLong(1, next)
                    update.executeUpdate()
                }
                return next
            }
        }
    }

    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }

    private fun bootstrap() {
        val resource = checkNotNull(javaClass.getResourceAsStream("/db/schema.sql")) { "Missing /db/schema.sql" }
        val sql = resource.bufferedReader().readText()
        dataSource.connection.use { connection ->
            connection.autoCommit = true
            val isH2 = connection.metaData.databaseProductName.equals("H2", ignoreCase = true)
            sql.split(";")
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { isH2 && it.contains("ON CONFLICT", ignoreCase = true) }
                .forEach { statement ->
                    connection.createStatement().use { it.execute(statement) }
                }
            if (isH2) {
                connection.createStatement().use {
                    it.execute("MERGE INTO sync_server_version KEY(id) VALUES (1, 0)")
                }
            }
        }
    }

    companion object {
        fun fromConfig(config: ServerDatabaseConfig): ServerDatabase {
            val hikari = HikariConfig().apply {
                jdbcUrl = config.jdbcUrl
                config.username?.let { username = it }
                config.password?.let { password = it }
                maximumPoolSize = 5
                isAutoCommit = false
            }
            return ServerDatabase(HikariDataSource(hikari))
        }
    }
}

internal abstract class BaseMutableSyncService<T>(
    private val database: ServerDatabase,
) {
    fun list(userId: String, cursor: Long, limit: Int): PullResponse<T> = database.read { connection ->
        val items = selectAfterCursor(connection, userId, cursor, limit)
        PullResponse(
            items = items,
            nextCursor = items.lastOrNull()?.let(::serverVersionOf) ?: cursor,
            hasMore = items.size == limit,
        )
    }

    fun all(userId: String): List<T> = database.read { connection -> selectAll(connection, userId) }

    fun get(userId: String, id: String): T? = database.read { connection -> selectOne(connection, userId, id, false) }

    fun push(userId: String, items: List<T>): List<T> = items.map { incoming ->
        database.tx { connection ->
            val existing = selectOne(connection, userId, idOf(incoming), true)
            if (existing == null) {
                insertNew(connection, userId, incoming)
            } else {
                mergeExisting(connection, userId, existing, incoming)
            }
        }
    }

    protected fun now(): String = Instant.now().toString()
    protected fun nextVersion(connection: Connection): Long = database.nextServerVersion(connection)

    protected abstract fun idOf(item: T): String
    protected abstract fun serverVersionOf(item: T): Long
    protected abstract fun selectAfterCursor(connection: Connection, userId: String, cursor: Long, limit: Int): List<T>
    protected abstract fun selectAll(connection: Connection, userId: String): List<T>
    protected abstract fun selectOne(connection: Connection, userId: String, id: String, forUpdate: Boolean): T?
    protected abstract fun insertNew(connection: Connection, userId: String, incoming: T): T
    protected abstract fun mergeExisting(connection: Connection, userId: String, existing: T, incoming: T): T
}

internal class RoutineSyncService(
    private val database: ServerDatabase,
) : BaseMutableSyncService<SyncRoutine>(database) {
    override fun idOf(item: SyncRoutine): String = item.id
    override fun serverVersionOf(item: SyncRoutine): Long = item.serverVersion

    override fun selectAfterCursor(connection: Connection, userId: String, cursor: Long, limit: Int): List<SyncRoutine> =
        connection.prepareStatement(
            """
            SELECT * FROM routine
            WHERE user_id = ? AND server_version > ?
            ORDER BY server_version ASC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.setLong(2, cursor)
            statement.setInt(3, limit)
            statement.executeQuery().use(::readRoutines)
        }

    override fun selectAll(connection: Connection, userId: String): List<SyncRoutine> =
        connection.prepareStatement("SELECT * FROM routine WHERE user_id = ? ORDER BY server_version ASC").use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use(::readRoutines)
        }

    override fun selectOne(connection: Connection, userId: String, id: String, forUpdate: Boolean): SyncRoutine? =
        connection.prepareStatement(
            "SELECT * FROM routine WHERE user_id = ? AND id = ?${if (forUpdate) " FOR UPDATE" else ""}",
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toSyncRoutine() else null }
        }

    override fun insertNew(connection: Connection, userId: String, incoming: SyncRoutine): SyncRoutine {
        val now = now()
        val version = nextVersion(connection)
        val canonical = incoming.copy(
            acceptedFields = incoming.dirtyFields.distinct(),
            rejectedFields = emptyList(),
            serverVersion = version,
            serverUpdatedAt = now,
            createdAt = incoming.createdAt ?: now,
        )
        connection.prepareStatement(
            """
            INSERT INTO routine(
                id, user_id, template_title, cron_schedule, timezone, is_active, is_deleted, hlc_map,
                server_version, server_updated_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, canonical.id)
            statement.setString(2, userId)
            statement.setString(3, canonical.templateTitle)
            statement.setString(4, canonical.cronSchedule)
            statement.setString(5, canonical.timezone)
            statement.setBoolean(6, canonical.isActive)
            statement.setBoolean(7, canonical.isDeleted)
            statement.setString(8, encodeMap(canonical.hlcMap))
            statement.setLong(9, canonical.serverVersion)
            statement.setString(10, canonical.serverUpdatedAt)
            statement.setString(11, canonical.createdAt)
            statement.executeUpdate()
        }
        return canonical
    }

    override fun mergeExisting(connection: Connection, userId: String, existing: SyncRoutine, incoming: SyncRoutine): SyncRoutine {
        var merged = existing
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (field in incoming.dirtyFields.distinct()) {
            val incomingHlc = incoming.hlcMap[field]
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) {
                rejected += field
                continue
            }
            when (field) {
                "template" -> {
                    merged = merged.copy(templateTitle = incoming.templateTitle, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                "schedule" -> {
                    merged = merged.copy(cronSchedule = incoming.cronSchedule, timezone = incoming.timezone, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                "active" -> {
                    merged = merged.copy(isActive = incoming.isActive, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                "delete" -> {
                    merged = merged.copy(isDeleted = incoming.isDeleted, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                else -> rejected += field
            }
        }
        if (accepted.isNotEmpty()) {
            val now = now()
            merged = merged.copy(
                acceptedFields = accepted,
                rejectedFields = rejected,
                serverVersion = nextVersion(connection),
                serverUpdatedAt = now,
                createdAt = existing.createdAt ?: now,
            )
            updateRoutine(connection, userId, merged)
        } else {
            merged = existing.copy(acceptedFields = emptyList(), rejectedFields = rejected)
        }
        return merged
    }

    private fun updateRoutine(connection: Connection, userId: String, routine: SyncRoutine) {
        connection.prepareStatement(
            """
            UPDATE routine SET template_title = ?, cron_schedule = ?, timezone = ?, is_active = ?, is_deleted = ?,
                hlc_map = ?, server_version = ?, server_updated_at = ?
            WHERE user_id = ? AND id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, routine.templateTitle)
            statement.setString(2, routine.cronSchedule)
            statement.setString(3, routine.timezone)
            statement.setBoolean(4, routine.isActive)
            statement.setBoolean(5, routine.isDeleted)
            statement.setString(6, encodeMap(routine.hlcMap))
            statement.setLong(7, routine.serverVersion)
            statement.setString(8, routine.serverUpdatedAt)
            statement.setString(9, userId)
            statement.setString(10, routine.id)
            statement.executeUpdate()
        }
    }

    private fun readRoutines(resultSet: ResultSet): List<SyncRoutine> {
        val items = mutableListOf<SyncRoutine>()
        while (resultSet.next()) items += resultSet.toSyncRoutine()
        return items
    }
}

internal class TaskSyncService(
    private val database: ServerDatabase,
    private val serverClock: HybridLogicalClock,
) : BaseMutableSyncService<SyncTask>(database) {
    override fun idOf(item: SyncTask): String = item.id
    override fun serverVersionOf(item: SyncTask): Long = item.serverVersion

    override fun selectAfterCursor(connection: Connection, userId: String, cursor: Long, limit: Int): List<SyncTask> =
        connection.prepareStatement(
            """
            SELECT * FROM task
            WHERE user_id = ? AND server_version > ?
            ORDER BY server_version ASC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.setLong(2, cursor)
            statement.setInt(3, limit)
            statement.executeQuery().use(::readTasks)
        }

    override fun selectAll(connection: Connection, userId: String): List<SyncTask> =
        connection.prepareStatement("SELECT * FROM task WHERE user_id = ? ORDER BY server_version ASC").use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use(::readTasks)
        }

    override fun selectOne(connection: Connection, userId: String, id: String, forUpdate: Boolean): SyncTask? =
        connection.prepareStatement(
            "SELECT * FROM task WHERE user_id = ? AND id = ?${if (forUpdate) " FOR UPDATE" else ""}",
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toSyncTask() else null }
        }

    override fun insertNew(connection: Connection, userId: String, incoming: SyncTask): SyncTask {
        validateTaskOwnership(connection, userId, incoming.parentId, incoming.routineId)
        val now = now()
        val version = nextVersion(connection)
        val canonical = incoming.copy(
            acceptedFields = incoming.dirtyFields.distinct(),
            rejectedFields = emptyList(),
            serverVersion = version,
            serverUpdatedAt = now,
            createdAt = incoming.createdAt ?: now,
        )
        connection.prepareStatement(
            """
            INSERT INTO task(
                id, user_id, parent_id, routine_id, title, is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, canonical.id)
            statement.setString(2, userId)
            statement.setNullableString(3, canonical.parentId)
            statement.setNullableString(4, canonical.routineId)
            statement.setString(5, canonical.title)
            statement.setBoolean(6, canonical.isDeleted)
            statement.setString(7, encodeMap(canonical.hlcMap))
            statement.setLong(8, canonical.serverVersion)
            statement.setString(9, canonical.serverUpdatedAt)
            statement.setString(10, canonical.createdAt)
            statement.executeUpdate()
        }
        return canonical
    }

    override fun mergeExisting(connection: Connection, userId: String, existing: SyncTask, incoming: SyncTask): SyncTask {
        validateTaskOwnership(connection, userId, incoming.parentId, incoming.routineId)
        var merged = existing
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (field in incoming.dirtyFields.distinct()) {
            val incomingHlc = incoming.hlcMap[field]
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) {
                rejected += field
                continue
            }
            when (field) {
                "title" -> {
                    merged = merged.copy(title = incoming.title, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                "parent" -> {
                    merged = merged.copy(parentId = incoming.parentId, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                "routine" -> {
                    merged = merged.copy(routineId = incoming.routineId, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                "delete" -> {
                    merged = merged.copy(isDeleted = incoming.isDeleted, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                else -> rejected += field
            }
        }
        if (accepted.isNotEmpty()) {
            val now = now()
            merged = merged.copy(
                acceptedFields = accepted,
                rejectedFields = rejected,
                serverVersion = nextVersion(connection),
                serverUpdatedAt = now,
                createdAt = existing.createdAt ?: now,
            )
            updateTask(connection, userId, merged)
        } else {
            merged = existing.copy(acceptedFields = emptyList(), rejectedFields = rejected)
        }
        return merged
    }

    fun createFromBusinessApi(userId: String, request: TaskUpsertRequest): SyncTask {
        val id = request.id ?: UUID.randomUUID().toString()
        val titleHlc = serverClock.next()
        return push(
            userId,
            listOf(
                SyncTask(
                    id = id,
                    parentId = request.parentId,
                    routineId = request.routineId,
                    title = request.title,
                    hlcMap = buildMap {
                        put("title", titleHlc)
                        request.parentId?.let { put("parent", serverClock.next(titleHlc)) }
                        request.routineId?.let { put("routine", serverClock.next(titleHlc)) }
                    },
                    dirtyFields = buildList {
                        add("title")
                        if (request.parentId != null) add("parent")
                        if (request.routineId != null) add("routine")
                    },
                ),
            ),
        ).single()
    }

    fun patchFromBusinessApi(userId: String, id: String, request: TaskPatchRequest): SyncTask? = database.tx { connection ->
        val existing = selectOne(connection, userId, id, true) ?: return@tx null
        val dirty = mutableListOf<String>()
        val hlc = existing.hlcMap.toMutableMap()
        var title = existing.title
        var parentId = existing.parentId
        var routineId = existing.routineId
        request.title?.let {
            title = it
            hlc["title"] = serverClock.next(hlc["title"])
            dirty += "title"
        }
        if (request.parentId != existing.parentId) {
            parentId = request.parentId
            hlc["parent"] = serverClock.next(hlc["parent"])
            dirty += "parent"
        }
        if (request.routineId != existing.routineId) {
            routineId = request.routineId
            hlc["routine"] = serverClock.next(hlc["routine"])
            dirty += "routine"
        }
        if (dirty.isEmpty()) return@tx existing
        mergeExisting(
            connection = connection,
            userId = userId,
            existing = existing,
            incoming = existing.copy(title = title, parentId = parentId, routineId = routineId, hlcMap = hlc, dirtyFields = dirty),
        )
    }

    fun softDeleteFromBusinessApi(userId: String, id: String): SyncTask? = database.tx { connection ->
        val existing = selectOne(connection, userId, id, true) ?: return@tx null
        mergeExisting(
            connection = connection,
            userId = userId,
            existing = existing,
            incoming = existing.copy(
                isDeleted = true,
                hlcMap = existing.hlcMap + ("delete" to serverClock.next(existing.hlcMap["delete"])),
                dirtyFields = listOf("delete"),
            ),
        )
    }

    private fun updateTask(connection: Connection, userId: String, task: SyncTask) {
        connection.prepareStatement(
            """
            UPDATE task SET parent_id = ?, routine_id = ?, title = ?, is_deleted = ?, hlc_map = ?, server_version = ?, server_updated_at = ?
            WHERE user_id = ? AND id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setNullableString(1, task.parentId)
            statement.setNullableString(2, task.routineId)
            statement.setString(3, task.title)
            statement.setBoolean(4, task.isDeleted)
            statement.setString(5, encodeMap(task.hlcMap))
            statement.setLong(6, task.serverVersion)
            statement.setString(7, task.serverUpdatedAt)
            statement.setString(8, userId)
            statement.setString(9, task.id)
            statement.executeUpdate()
        }
    }

    private fun validateTaskOwnership(connection: Connection, userId: String, parentId: String?, routineId: String?) {
        parentId?.let {
            require(recordExists(connection, "task", userId, it)) { "parent_id does not belong to current user" }
        }
        routineId?.let {
            require(recordExists(connection, "routine", userId, it)) { "routine_id does not belong to current user" }
        }
    }

    private fun readTasks(resultSet: ResultSet): List<SyncTask> {
        val items = mutableListOf<SyncTask>()
        while (resultSet.next()) items += resultSet.toSyncTask()
        return items
    }
}

internal class AlarmSyncService(
    private val database: ServerDatabase,
) : BaseMutableSyncService<SyncAlarm>(database) {
    override fun idOf(item: SyncAlarm): String = item.id
    override fun serverVersionOf(item: SyncAlarm): Long = item.serverVersion

    override fun selectAfterCursor(connection: Connection, userId: String, cursor: Long, limit: Int): List<SyncAlarm> =
        connection.prepareStatement(
            """
            SELECT * FROM alarm
            WHERE user_id = ? AND server_version > ?
            ORDER BY server_version ASC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.setLong(2, cursor)
            statement.setInt(3, limit)
            statement.executeQuery().use(::readAlarms)
        }

    override fun selectAll(connection: Connection, userId: String): List<SyncAlarm> =
        connection.prepareStatement("SELECT * FROM alarm WHERE user_id = ? ORDER BY server_version ASC").use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use(::readAlarms)
        }

    override fun selectOne(connection: Connection, userId: String, id: String, forUpdate: Boolean): SyncAlarm? =
        connection.prepareStatement(
            "SELECT * FROM alarm WHERE user_id = ? AND id = ?${if (forUpdate) " FOR UPDATE" else ""}",
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toSyncAlarm() else null }
        }

    override fun insertNew(connection: Connection, userId: String, incoming: SyncAlarm): SyncAlarm {
        require(recordExists(connection, "task", userId, incoming.taskId)) { "task_id does not belong to current user" }
        val now = now()
        val version = nextVersion(connection)
        val canonical = incoming.copy(
            acceptedFields = incoming.dirtyFields.distinct(),
            rejectedFields = emptyList(),
            serverVersion = version,
            serverUpdatedAt = now,
            createdAt = incoming.createdAt ?: now,
        )
        connection.prepareStatement(
            """
            INSERT INTO alarm(
                id, user_id, task_id, trigger_time, is_active, is_deleted, hlc_map, server_version, server_updated_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, canonical.id)
            statement.setString(2, userId)
            statement.setString(3, canonical.taskId)
            statement.setString(4, canonical.triggerTime)
            statement.setBoolean(5, canonical.isActive)
            statement.setBoolean(6, canonical.isDeleted)
            statement.setString(7, encodeMap(canonical.hlcMap))
            statement.setLong(8, canonical.serverVersion)
            statement.setString(9, canonical.serverUpdatedAt)
            statement.setString(10, canonical.createdAt)
            statement.executeUpdate()
        }
        return canonical
    }

    override fun mergeExisting(connection: Connection, userId: String, existing: SyncAlarm, incoming: SyncAlarm): SyncAlarm {
        require(recordExists(connection, "task", userId, incoming.taskId)) { "task_id does not belong to current user" }
        var merged = existing
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (field in incoming.dirtyFields.distinct()) {
            val incomingHlc = incoming.hlcMap[field]
            val existingHlc = existing.hlcMap[field]
            if (!SyncConflictResolver.shouldAcceptIncoming(incomingHlc, existingHlc)) {
                rejected += field
                continue
            }
            when (field) {
                "trigger" -> {
                    merged = merged.copy(triggerTime = incoming.triggerTime, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                "active" -> {
                    merged = merged.copy(isActive = incoming.isActive, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                "delete" -> {
                    merged = merged.copy(isDeleted = incoming.isDeleted, hlcMap = merged.hlcMap + (field to incomingHlc!!))
                    accepted += field
                }
                else -> rejected += field
            }
        }
        if (accepted.isNotEmpty()) {
            val now = now()
            merged = merged.copy(
                acceptedFields = accepted,
                rejectedFields = rejected,
                serverVersion = nextVersion(connection),
                serverUpdatedAt = now,
                createdAt = existing.createdAt ?: now,
            )
            connection.prepareStatement(
                """
                UPDATE alarm SET task_id = ?, trigger_time = ?, is_active = ?, is_deleted = ?, hlc_map = ?, server_version = ?, server_updated_at = ?
                WHERE user_id = ? AND id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, merged.taskId)
                statement.setString(2, merged.triggerTime)
                statement.setBoolean(3, merged.isActive)
                statement.setBoolean(4, merged.isDeleted)
                statement.setString(5, encodeMap(merged.hlcMap))
                statement.setLong(6, merged.serverVersion)
                statement.setString(7, merged.serverUpdatedAt)
                statement.setString(8, userId)
                statement.setString(9, merged.id)
                statement.executeUpdate()
            }
        } else {
            merged = existing.copy(acceptedFields = emptyList(), rejectedFields = rejected)
        }
        return merged
    }

    private fun readAlarms(resultSet: ResultSet): List<SyncAlarm> {
        val items = mutableListOf<SyncAlarm>()
        while (resultSet.next()) items += resultSet.toSyncAlarm()
        return items
    }
}

internal class TaskEventSyncService(
    private val database: ServerDatabase,
) {
    fun list(userId: String, cursor: Long, limit: Int): PullResponse<SyncTaskEvent> = database.read { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM task_event
            WHERE user_id = ? AND server_version > ?
            ORDER BY server_version ASC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.setLong(2, cursor)
            statement.setInt(3, limit)
            statement.executeQuery().use { rs ->
                val items = mutableListOf<SyncTaskEvent>()
                while (rs.next()) items += rs.toSyncTaskEvent()
                PullResponse(
                    items = items,
                    nextCursor = items.lastOrNull()?.serverVersion ?: cursor,
                    hasMore = items.size == limit,
                )
            }
        }
    }

    fun push(userId: String, items: List<SyncTaskEvent>): List<SyncTaskEvent> = items.map { incoming ->
        database.tx { connection ->
            require(recordExists(connection, "task", userId, incoming.taskId)) { "task_id does not belong to current user" }
            val existing = connection.prepareStatement(
                "SELECT * FROM task_event WHERE user_id = ? AND id = ? FOR UPDATE",
            ).use { statement ->
                statement.setString(1, userId)
                statement.setString(2, incoming.id)
                statement.executeQuery().use { rs -> if (rs.next()) rs.toSyncTaskEvent() else null }
            }
            if (existing != null) {
                existing.copy(accepted = false)
            } else {
                val now = Instant.now().toString()
                val canonical = incoming.copy(
                    accepted = true,
                    serverVersion = database.nextServerVersion(connection),
                    serverUpdatedAt = now,
                    createdAt = incoming.createdAt ?: now,
                )
                connection.prepareStatement(
                    """
                    INSERT INTO task_event(
                        id, user_id, task_id, journal_date, event_type, content, event_time, is_deleted, server_version, server_updated_at, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, canonical.id)
                    statement.setString(2, userId)
                    statement.setString(3, canonical.taskId)
                    statement.setString(4, canonical.journalDate)
                    statement.setString(5, canonical.eventType.name)
                    statement.setNullableString(6, canonical.content)
                    statement.setString(7, canonical.eventTime)
                    statement.setBoolean(8, canonical.isDeleted)
                    statement.setLong(9, canonical.serverVersion)
                    statement.setString(10, canonical.serverUpdatedAt)
                    statement.setString(11, canonical.createdAt)
                    statement.executeUpdate()
                }
                canonical
            }
        }
    }

    fun createFromBusinessApi(userId: String, request: TaskEventCreateRequest): SyncTaskEvent =
        push(
            userId,
            listOf(
                SyncTaskEvent(
                    id = request.id ?: UUID.randomUUID().toString(),
                    taskId = request.taskId,
                    journalDate = request.journalDate,
                    eventType = request.eventType,
                    content = request.content,
                    eventTime = request.eventTime,
                ),
            ),
        ).single()

    fun journal(userId: String, date: String): List<JournalEntry> = database.read { connection ->
        connection.prepareStatement(
            """
            SELECT task_id, id, event_type, content, event_time
            FROM task_event
            WHERE user_id = ? AND journal_date = ? AND is_deleted = FALSE
            ORDER BY event_time ASC, created_at ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, date)
            statement.executeQuery().use { rs ->
                val items = mutableListOf<JournalEntry>()
                while (rs.next()) {
                    items += JournalEntry(
                        taskId = rs.getString("task_id"),
                        eventId = rs.getString("id"),
                        eventType = TaskEventType.valueOf(rs.getString("event_type")),
                        content = rs.getString("content"),
                        eventTime = rs.getString("event_time"),
                    )
                }
                items
            }
        }
    }

    fun deriveStatus(userId: String, taskId: String): TaskStatus? = database.read { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM task_event
            WHERE user_id = ? AND task_id = ? AND is_deleted = FALSE
            ORDER BY event_time DESC, created_at DESC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, taskId)
            statement.executeQuery().use { rs ->
                val items = mutableListOf<SyncTaskEvent>()
                while (rs.next()) items += rs.toSyncTaskEvent()
                if (items.isEmpty()) null else TaskStatusDeriver.fromEvents(items)
            }
        }
    }
}

@Serializable
data class JournalEntry(
    val taskId: String,
    val eventId: String,
    val eventType: TaskEventType,
    val content: String?,
    val eventTime: String,
)

private fun ResultSet.toSyncRoutine(): SyncRoutine = SyncRoutine(
    id = getString("id"),
    templateTitle = getString("template_title"),
    cronSchedule = getString("cron_schedule"),
    timezone = getString("timezone"),
    isActive = getBoolean("is_active"),
    isDeleted = getBoolean("is_deleted"),
    hlcMap = decodeMap(getString("hlc_map")),
    serverVersion = getLong("server_version"),
    serverUpdatedAt = getString("server_updated_at"),
    createdAt = getString("created_at"),
)

private fun ResultSet.toSyncTask(): SyncTask = SyncTask(
    id = getString("id"),
    parentId = getString("parent_id"),
    routineId = getString("routine_id"),
    title = getString("title"),
    isDeleted = getBoolean("is_deleted"),
    hlcMap = decodeMap(getString("hlc_map")),
    serverVersion = getLong("server_version"),
    serverUpdatedAt = getString("server_updated_at"),
    createdAt = getString("created_at"),
)

private fun ResultSet.toSyncAlarm(): SyncAlarm = SyncAlarm(
    id = getString("id"),
    taskId = getString("task_id"),
    triggerTime = getString("trigger_time"),
    isActive = getBoolean("is_active"),
    isDeleted = getBoolean("is_deleted"),
    hlcMap = decodeMap(getString("hlc_map")),
    serverVersion = getLong("server_version"),
    serverUpdatedAt = getString("server_updated_at"),
    createdAt = getString("created_at"),
)

private fun ResultSet.toSyncTaskEvent(): SyncTaskEvent = SyncTaskEvent(
    id = getString("id"),
    taskId = getString("task_id"),
    journalDate = getString("journal_date"),
    eventType = TaskEventType.valueOf(getString("event_type")),
    content = getString("content"),
    eventTime = getString("event_time"),
    isDeleted = getBoolean("is_deleted"),
    serverVersion = getLong("server_version"),
    serverUpdatedAt = getString("server_updated_at"),
    createdAt = getString("created_at"),
)

private fun encodeMap(value: Map<String, String>): String = serverJson.encodeToString(stringMapSerializer, value)

private fun decodeMap(value: String?): Map<String, String> = value?.let {
    runCatching { serverJson.decodeFromString(stringMapSerializer, it) }.getOrDefault(emptyMap())
} ?: emptyMap()

private fun recordExists(connection: Connection, table: String, userId: String, id: String): Boolean =
    connection.prepareStatement("SELECT 1 FROM $table WHERE user_id = ? AND id = ?").use { statement ->
        statement.setString(1, userId)
        statement.setString(2, id)
        statement.executeQuery().use { it.next() }
    }

private fun java.sql.PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value == null) setNull(index, java.sql.Types.VARCHAR) else setString(index, value)
}
