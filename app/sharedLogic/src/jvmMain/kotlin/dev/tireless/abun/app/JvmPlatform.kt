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
            if (connection.tableExists("routine")) {
                connection.ensureColumn("routine", "template_detail", "TEXT")
                connection.ensureColumn("routine", "recurrence_rule", "TEXT")
                connection.ensureColumn("routine", "default_start_not_before", "TEXT")
                connection.ensureColumn("routine", "default_estimated_duration", "TEXT")
                connection.copyRoutineColumnIfMissing(
                    targetColumn = "recurrence_rule",
                    sourceColumn = "cron_schedule",
                )
            }
            if (connection.tableExists("task")) {
                connection.ensureColumn("task", "detail", "TEXT")
                connection.ensureColumn("task", "start_not_before", "TEXT")
                connection.ensureColumn("task", "end_not_after", "TEXT")
                connection.ensureColumn("task", "estimated_duration", "TEXT")
            }
            if (connection.tableExists("task_event")) {
                connection.ensureColumn("task_event", "postponed_json", "TEXT")
            }
            ensureFinanceTables(connection)
        }
    }
}

private fun ensureFinanceTables(connection: Connection) {
    connection.createStatement().use { statement ->
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS finance_account (
                id TEXT NOT NULL PRIMARY KEY,
                path TEXT NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                currency TEXT NOT NULL DEFAULT 'CNY',
                include_in_net_worth INTEGER NOT NULL DEFAULT 1,
                is_pass_through INTEGER NOT NULL DEFAULT 0,
                pass_through_expense_account_id TEXT,
                liquid_tier TEXT NOT NULL DEFAULT 'Liquid',
                statement_day INTEGER,
                due_day INTEGER,
                is_archived INTEGER NOT NULL DEFAULT 0,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                hlc_map TEXT NOT NULL DEFAULT '{}',
                dirty_fields TEXT NOT NULL DEFAULT '[]',
                is_dirty INTEGER NOT NULL DEFAULT 0,
                server_version INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS finance_exchange_rate (
                id TEXT NOT NULL PRIMARY KEY,
                base_currency TEXT NOT NULL,
                quote_currency TEXT NOT NULL,
                rate_date TEXT NOT NULL,
                rate_micros INTEGER NOT NULL,
                source TEXT NOT NULL DEFAULT 'Manual',
                is_deleted INTEGER NOT NULL DEFAULT 0,
                hlc_map TEXT NOT NULL DEFAULT '{}',
                dirty_fields TEXT NOT NULL DEFAULT '[]',
                is_dirty INTEGER NOT NULL DEFAULT 0,
                server_version INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS finance_transaction (
                id TEXT NOT NULL PRIMARY KEY,
                tx_date TEXT NOT NULL,
                tx_time TEXT,
                payee TEXT,
                summary TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'Cleared',
                source_plan_id TEXT,
                period_index INTEGER,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                hlc_map TEXT NOT NULL DEFAULT '{}',
                dirty_fields TEXT NOT NULL DEFAULT '[]',
                is_dirty INTEGER NOT NULL DEFAULT 0,
                server_version INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS finance_split (
                id TEXT NOT NULL PRIMARY KEY,
                transaction_id TEXT NOT NULL,
                account_id TEXT NOT NULL,
                amount_minor INTEGER NOT NULL,
                currency TEXT NOT NULL DEFAULT 'CNY',
                weight_amount_minor INTEGER NOT NULL,
                weight_currency TEXT NOT NULL DEFAULT 'CNY',
                narration TEXT NOT NULL DEFAULT '',
                sort_order INTEGER NOT NULL DEFAULT 0,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                hlc_map TEXT NOT NULL DEFAULT '{}',
                dirty_fields TEXT NOT NULL DEFAULT '[]',
                is_dirty INTEGER NOT NULL DEFAULT 0,
                server_version INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS finance_clearing_group (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                group_type TEXT NOT NULL,
                anchor_currency TEXT NOT NULL DEFAULT 'CNY',
                target_account_id TEXT,
                statement_cycle TEXT,
                due_date TEXT,
                status TEXT NOT NULL DEFAULT 'Active',
                settled_at TEXT,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                hlc_map TEXT NOT NULL DEFAULT '{}',
                dirty_fields TEXT NOT NULL DEFAULT '[]',
                is_dirty INTEGER NOT NULL DEFAULT 0,
                server_version INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS finance_split_clearing_group (
                id TEXT NOT NULL PRIMARY KEY,
                split_id TEXT NOT NULL,
                clearing_group_id TEXT NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                hlc_map TEXT NOT NULL DEFAULT '{}',
                dirty_fields TEXT NOT NULL DEFAULT '[]',
                is_dirty INTEGER NOT NULL DEFAULT 0,
                server_version INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent(),
        )
        statement.executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS finance_amortization_plan (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                principal_minor INTEGER NOT NULL,
                currency TEXT NOT NULL DEFAULT 'CNY',
                total_periods INTEGER NOT NULL,
                repayment_method TEXT NOT NULL,
                annual_rate_bps INTEGER NOT NULL DEFAULT 0,
                fee_per_period_minor INTEGER NOT NULL DEFAULT 0,
                start_date TEXT NOT NULL,
                repayment_day INTEGER NOT NULL,
                liability_account_id TEXT NOT NULL,
                funding_account_id TEXT,
                interest_account_id TEXT NOT NULL,
                lifecycle_clearing_group_id TEXT NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                hlc_map TEXT NOT NULL DEFAULT '{}',
                dirty_fields TEXT NOT NULL DEFAULT '[]',
                is_dirty INTEGER NOT NULL DEFAULT 0,
                server_version INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS finance_budget_rule (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                scope_type TEXT NOT NULL,
                account_prefix TEXT NOT NULL DEFAULT 'Expenses:',
                excluded_prefixes_json TEXT NOT NULL DEFAULT '[]',
                monthly_limit_minor INTEGER NOT NULL,
                currency TEXT NOT NULL DEFAULT 'CNY',
                include_in_runway INTEGER NOT NULL DEFAULT 1,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                hlc_map TEXT NOT NULL DEFAULT '{}',
                dirty_fields TEXT NOT NULL DEFAULT '[]',
                is_dirty INTEGER NOT NULL DEFAULT 0,
                server_version INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            );
            """.trimIndent(),
        )
    }
}

private fun Connection.copyRoutineColumnIfMissing(targetColumn: String, sourceColumn: String) {
    val columns = columnNames("routine")
    if (!columns.contains(targetColumn) || !columns.contains(sourceColumn)) return
    createStatement().use { statement ->
        statement.executeUpdate(
            """
            UPDATE routine
            SET $targetColumn = COALESCE($targetColumn, $sourceColumn)
            WHERE $targetColumn IS NULL AND $sourceColumn IS NOT NULL
            """.trimIndent(),
        )
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
