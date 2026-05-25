package dev.tireless.abun.app

import dev.tireless.abun.db.AbunDatabase
import dev.tireless.abun.sync.HybridLogicalClock
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class DemoAuthProvider(
    private val token: String = "demo-user",
) : AuthProvider {
    override suspend fun bearerToken(): String = token
}

class DefaultTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

    override fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}

fun createDatabase(factory: DatabaseDriverFactory): AbunDatabase = AbunDatabase(factory.createDriver())

fun epochMillisToIsoString(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis).toString()

fun isoStringToEpochMillis(value: String): Long = Instant.parse(value).toEpochMilliseconds()

fun createHybridClock(nodeIdProvider: DeviceNodeIdProvider, timeProvider: TimeProvider): HybridLogicalClock =
    HybridLogicalClock(nodeIdProvider.nodeId()) { timeProvider.nowEpochMillis() }

class StableStringIdGenerator : IdGenerator {
    override fun randomId(): String = deterministicId("random", Clock.System.now().toString())

    override fun deterministicId(namespace: String, seed: String): String {
        val raw = "$namespace:$seed"
        return raw.encodeToByteArray()
            .joinToString(separator = "") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).takeLast(2) }
            .take(48)
            .padEnd(48, '0')
    }
}

class DefaultHttpClientFactory(
    private val client: HttpClient,
) {
    fun create(): HttpClient = client
}
