package dev.tireless.abun.app

import app.cash.sqldelight.db.SqlDriver

class JsDatabaseDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver = error("Client sync is not supported on JS/web in v1.")
}

class JsNodeIdProvider : DeviceNodeIdProvider {
    override fun nodeId(): String = "web-device"
}
