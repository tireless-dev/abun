package dev.tireless.abun.app

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.tireless.abun.db.AbunDatabase

class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = AbunDatabase.Schema,
        context = context,
        name = "abun.db",
    )
}

class AndroidNodeIdProvider : DeviceNodeIdProvider {
    override fun nodeId(): String = "android-device"
}
