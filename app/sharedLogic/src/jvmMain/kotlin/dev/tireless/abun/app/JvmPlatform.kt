package dev.tireless.abun.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.tireless.abun.db.AbunDatabase
import java.sql.Connection
import java.sql.DriverManager

class JvmDatabaseDriverFactory(
    private val jdbcUrl: String = "jdbc:sqlite:abun.db",
) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver = JdbcSqliteDriver(jdbcUrl).also {
        runCatching { AbunDatabase.Schema.create(it) }
        migrateLegacyTaskSchema()
    }

    private fun migrateLegacyTaskSchema() {
        DriverManager.getConnection(jdbcUrl).use { connection ->
            if (!connection.tableExists("task")) return
            connection.ensureColumn("task", "detail", "TEXT")
            connection.ensureColumn("task", "start_not_before", "TEXT")
            connection.ensureColumn("task", "end_not_after", "TEXT")
            connection.ensureColumn("task", "estimated_duration", "TEXT")
            if (connection.tableExists("task_event")) {
                connection.ensureColumn("task_event", "postponed_json", "TEXT")
            }
        }
    }
}

class JvmNodeIdProvider : DeviceNodeIdProvider {
    override fun nodeId(): String = "desktop-device"
}

private fun Connection.tableExists(tableName: String): Boolean =
    prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").use { statement ->
        statement.setString(1, tableName)
        statement.executeQuery().use { result -> result.next() }
    }

private fun Connection.ensureColumn(tableName: String, columnName: String, columnDefinition: String) {
    if (columnNames(tableName).contains(columnName)) return
    createStatement().use { statement ->
        statement.executeUpdate("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
    }
}

private fun Connection.columnNames(tableName: String): Set<String> =
    createStatement().use { statement ->
        statement.executeQuery("PRAGMA table_info($tableName)").use { result ->
            buildSet {
                while (result.next()) {
                    add(result.getString("name"))
                }
            }
        }
    }
