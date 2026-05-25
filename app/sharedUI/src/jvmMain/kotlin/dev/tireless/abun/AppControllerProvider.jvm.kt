package dev.tireless.abun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AppDependencies
import dev.tireless.abun.app.DefaultTimeProvider
import dev.tireless.abun.app.DemoAuthProvider
import dev.tireless.abun.app.JvmDatabaseDriverFactory
import dev.tireless.abun.app.JvmNodeIdProvider
import dev.tireless.abun.app.StableStringIdGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

@Composable
actual fun rememberAbunAppController(): AbunAppController = remember {
    AbunAppController.create(
        AppDependencies(
            databaseDriverFactory = JvmDatabaseDriverFactory(),
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) { json(dev.tireless.abun.app.AppJson) }
            },
            authProvider = DemoAuthProvider(),
            nodeIdProvider = JvmNodeIdProvider(),
            idGenerator = StableStringIdGenerator(),
            timeProvider = DefaultTimeProvider(),
            serverBaseUrl = "http://127.0.0.1:8080",
        ),
    )
}
