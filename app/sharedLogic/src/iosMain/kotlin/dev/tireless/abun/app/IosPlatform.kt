package dev.tireless.abun.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.tireless.abun.db.AbunDatabase

class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver = NativeSqliteDriver(
        schema = AbunDatabase.Schema,
        name = "abun.db",
    )
}

class IosNodeIdProvider : DeviceNodeIdProvider {
    override fun nodeId(): String = "ios-device"
}
