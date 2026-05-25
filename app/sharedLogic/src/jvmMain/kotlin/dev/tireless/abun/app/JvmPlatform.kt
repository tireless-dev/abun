package dev.tireless.abun.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.tireless.abun.db.AbunDatabase

class JvmDatabaseDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver = JdbcSqliteDriver("jdbc:sqlite:abun.db").also {
        runCatching { AbunDatabase.Schema.create(it) }
    }
}

class JvmNodeIdProvider : DeviceNodeIdProvider {
    override fun nodeId(): String = "desktop-device"
}
