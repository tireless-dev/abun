package dev.tireless.abun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AppDependencies
import dev.tireless.abun.app.DEFAULT_SERVER_BASE_URL
import dev.tireless.abun.app.DefaultTimeProvider
import dev.tireless.abun.app.DemoAuthProvider
import dev.tireless.abun.app.IosDatabaseDriverFactory
import dev.tireless.abun.app.IosNodeIdProvider
import dev.tireless.abun.app.StableStringIdGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

@Composable
actual fun rememberAbunAppController(): AbunAppController = remember {
    AbunAppController.create(
        AppDependencies(
            databaseDriverFactory = IosDatabaseDriverFactory(),
            httpClient = HttpClient(Darwin) {
                install(ContentNegotiation) { json(dev.tireless.abun.app.AppJson) }
            },
            authProvider = DemoAuthProvider(),
            nodeIdProvider = IosNodeIdProvider(),
            idGenerator = StableStringIdGenerator(),
            timeProvider = DefaultTimeProvider(),
            serverBaseUrl = DEFAULT_SERVER_BASE_URL,
        ),
    )
}
