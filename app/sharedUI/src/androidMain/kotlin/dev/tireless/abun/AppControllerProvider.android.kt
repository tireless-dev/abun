package dev.tireless.abun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.content.pm.ApplicationInfo
import androidx.compose.ui.platform.LocalContext
import dev.tireless.abun.app.AbunAppController
import dev.tireless.abun.app.AndroidDatabaseDriverFactory
import dev.tireless.abun.app.AndroidNodeIdProvider
import dev.tireless.abun.app.AppDependencies
import dev.tireless.abun.app.DefaultTimeProvider
import dev.tireless.abun.app.DebugAuthPreset
import dev.tireless.abun.app.DemoAuthProvider
import dev.tireless.abun.app.StableStringIdGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

@Composable
actual fun rememberAbunAppController(): AbunAppController {
    val context = LocalContext.current
    return remember {
        AbunAppController.create(
            AppDependencies(
                databaseDriverFactory = AndroidDatabaseDriverFactory(context),
                httpClient = HttpClient(OkHttp) {
                    install(ContentNegotiation) { json(dev.tireless.abun.app.AppJson) }
                },
                authProvider = DemoAuthProvider(),
                nodeIdProvider = AndroidNodeIdProvider(),
                idGenerator = StableStringIdGenerator(),
                timeProvider = DefaultTimeProvider(),
                serverBaseUrl = "http://10.0.2.2:8080",
                debugAuthPreset = debugAuthPreset(
                    enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                ),
            ),
        )
    }
}

private fun debugAuthPreset(enabled: Boolean): DebugAuthPreset? = if (enabled) {
    DebugAuthPreset(
        email = "abun@tireless.dev",
        otp = "424242",
        accessToken = "debug-access-token",
        userId = "debug-user",
    )
} else {
    null
}
