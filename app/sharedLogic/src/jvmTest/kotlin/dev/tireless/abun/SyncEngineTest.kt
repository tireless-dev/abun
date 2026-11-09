package dev.tireless.abun

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.tireless.abun.app.AccessTokenProvider
import dev.tireless.abun.app.DeviceNodeIdProvider
import dev.tireless.abun.app.IdGenerator
import dev.tireless.abun.app.LocalStore
import dev.tireless.abun.app.SyncEngine
import dev.tireless.abun.app.SyncOperationException
import dev.tireless.abun.app.SyncRemoteApi
import dev.tireless.abun.app.SyncScope
import dev.tireless.abun.app.TimeProvider
import dev.tireless.abun.app.createHybridClock
import dev.tireless.abun.db.AbunDatabase
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.sync.PullResponse
import dev.tireless.abun.sync.SyncAlarm
import dev.tireless.abun.sync.SyncFinanceAccount
import dev.tireless.abun.sync.SyncFinanceAmortizationPlan
import dev.tireless.abun.sync.SyncFinanceBudgetRule
import dev.tireless.abun.sync.SyncFinanceClearingGroup
import dev.tireless.abun.sync.SyncFinanceExchangeRate
import dev.tireless.abun.sync.SyncFinanceSplit
import dev.tireless.abun.sync.SyncFinanceSplitClearingGroup
import dev.tireless.abun.sync.SyncFinanceTransaction
import dev.tireless.abun.sync.SyncPomodoroSession
import dev.tireless.abun.sync.SyncPreference
import dev.tireless.abun.sync.SyncRoutine
import dev.tireless.abun.sync.SyncTask
import dev.tireless.abun.sync.SyncTaskEvent
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyncEngineTest {

    private fun testTimeProvider(): TimeProvider = object : TimeProvider {
        private var now = 1_748_120_000_000L
        override fun nowEpochMillis(): Long = now++
        override fun today(): LocalDate = LocalDate.parse("2026-05-25")
    }

    private fun testIdGenerator(): IdGenerator = object : IdGenerator {
        private var index = 0
        override fun randomId(): String = "id-${++index}"
        override fun deterministicId(namespace: String, seed: String): String = "$namespace:$seed"
    }

    private fun dummyTokenProvider(): AccessTokenProvider = object : AccessTokenProvider {
        override suspend fun validAccessToken(forceRefresh: Boolean): String = "test-token"
    }

    private open class BaseMockSyncRemoteApi : SyncRemoteApi("http://localhost", HttpClient(), object : AccessTokenProvider {
        override suspend fun validAccessToken(forceRefresh: Boolean): String = "test-token"
    }) {
        override suspend fun pullPreferences(cursor: Long, limit: Int) = PullResponse<SyncPreference>(emptyList(), cursor, false)
        override suspend fun pullRoutines(cursor: Long, limit: Int) = PullResponse<SyncRoutine>(emptyList(), cursor, false)
        override suspend fun pullTasks(cursor: Long, limit: Int) = PullResponse<SyncTask>(emptyList(), cursor, false)
        override suspend fun pullAlarms(cursor: Long, limit: Int) = PullResponse<SyncAlarm>(emptyList(), cursor, false)
        override suspend fun pullTaskEvents(cursor: Long, limit: Int) = PullResponse<SyncTaskEvent>(emptyList(), cursor, false)
        override suspend fun pullPomodoroSessions(cursor: Long, limit: Int) = PullResponse<SyncPomodoroSession>(emptyList(), cursor, false)
        override suspend fun pullFinanceAccounts(cursor: Long, limit: Int) = PullResponse<SyncFinanceAccount>(emptyList(), cursor, false)
        override suspend fun pullFinanceExchangeRates(cursor: Long, limit: Int) = PullResponse<SyncFinanceExchangeRate>(emptyList(), cursor, false)
        override suspend fun pullFinanceClearingGroups(cursor: Long, limit: Int) = PullResponse<SyncFinanceClearingGroup>(emptyList(), cursor, false)
        override suspend fun pullFinanceTransactions(cursor: Long, limit: Int) = PullResponse<SyncFinanceTransaction>(emptyList(), cursor, false)
        override suspend fun pullFinanceSplits(cursor: Long, limit: Int) = PullResponse<SyncFinanceSplit>(emptyList(), cursor, false)
        override suspend fun pullFinanceSplitClearingGroups(cursor: Long, limit: Int) = PullResponse<SyncFinanceSplitClearingGroup>(emptyList(), cursor, false)
        override suspend fun pullFinanceAmortizationPlans(cursor: Long, limit: Int) = PullResponse<SyncFinanceAmortizationPlan>(emptyList(), cursor, false)
        override suspend fun pullFinanceBudgetRules(cursor: Long, limit: Int) = PullResponse<SyncFinanceBudgetRule>(emptyList(), cursor, false)

        override suspend fun pushPreferences(items: List<SyncPreference>) = items
        override suspend fun pushRoutines(items: List<SyncRoutine>) = items
        override suspend fun pushTasks(items: List<SyncTask>) = items
        override suspend fun pushAlarms(items: List<SyncAlarm>) = items
        override suspend fun pushTaskEvents(items: List<SyncTaskEvent>) = items
        override suspend fun pushPomodoroSessions(items: List<SyncPomodoroSession>) = items
        override suspend fun pushFinanceAccounts(items: List<SyncFinanceAccount>) = items
        override suspend fun pushFinanceExchangeRates(items: List<SyncFinanceExchangeRate>) = items
        override suspend fun pushFinanceClearingGroups(items: List<SyncFinanceClearingGroup>) = items
        override suspend fun pushFinanceTransactions(items: List<SyncFinanceTransaction>) = items
        override suspend fun pushFinanceSplits(items: List<SyncFinanceSplit>) = items
        override suspend fun pushFinanceSplitClearingGroups(items: List<SyncFinanceSplitClearingGroup>) = items
        override suspend fun pushFinanceAmortizationPlans(items: List<SyncFinanceAmortizationPlan>) = items
        override suspend fun pushFinanceBudgetRules(items: List<SyncFinanceBudgetRule>) = items
    }

    private fun newTestStore(): LocalStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            AbunDatabase.Schema.create(it)
        }
        val database = AbunDatabase(driver)
        return LocalStore(
            database = database,
            timeProvider = testTimeProvider(),
            idGenerator = testIdGenerator(),
            clock = createHybridClock(object : DeviceNodeIdProvider {
                override fun nodeId(): String = "test-node"
            }, testTimeProvider()),
        )
    }

    @Test
    fun `pushPaged pushes in chunks and resumes from failure point without re-pushing accepted items`() = kotlinx.coroutines.test.runTest {
        val store = newTestStore()

        // Clear any initial dirty accounts
        val initialDirty = store.financeSyncStore.dirtyFinanceAccounts()
        if (initialDirty.isNotEmpty()) {
            store.financeSyncStore.mergeRemoteFinanceAccounts(
                initialDirty.map { it.copy(acceptedFields = listOf("core")) },
                clearAccepted = true,
            )
        }
        assertEquals(0, store.financeSyncStore.dirtyFinanceAccounts().size)

        // Create 120 dirty accounts
        for (i in 1..120) {
            store.financeAccountStore.createFinanceAccount(
                path = "Assets:Bank:Account$i",
                name = "Account $i",
                category = AccountCategory.ASSET,
            )
        }

        assertEquals(120, store.financeSyncStore.dirtyFinanceAccounts().size)

        var pushCallCount = 0
        val batchesReceived = mutableListOf<Int>()
        var failOnSecondBatch = true

        val mockApi = object : BaseMockSyncRemoteApi() {
            override suspend fun pushFinanceAccounts(items: List<SyncFinanceAccount>): List<SyncFinanceAccount> {
                pushCallCount++
                batchesReceived.add(items.size)
                if (pushCallCount == 2 && failOnSecondBatch) {
                    throw RuntimeException("Network timeout on batch 2")
                }
                return items.map {
                    it.copy(
                        serverVersion = pushCallCount.toLong(),
                        acceptedFields = listOf("core"),
                        rejectedFields = emptyList(),
                    )
                }
            }
        }

        val engine = SyncEngine(localStore = store, remoteApi = mockApi)

        // First attempt: should succeed on batch 1 (50 items), then fail on batch 2
        assertFailsWith<SyncOperationException> {
            engine.syncNow()
        }

        assertEquals(2, pushCallCount)
        assertEquals(listOf(50, 50), batchesReceived)

        // First 50 items were accepted and merged before the error, so 70 dirty items remain
        val remainingDirty = store.financeSyncStore.dirtyFinanceAccounts()
        assertEquals(70, remainingDirty.size)

        // Retry: failOnSecondBatch = false
        failOnSecondBatch = false
        batchesReceived.clear()

        engine.syncNow()

        // It should have pushed remaining 70 items in two batches: 50 then 20
        assertEquals(listOf(50, 20), batchesReceived)
        assertEquals(0, store.financeSyncStore.dirtyFinanceAccounts().size)
    }

    @Test
    fun `pullPaged pulls in pages and resumes from last saved cursor on failure`() = kotlinx.coroutines.test.runTest {
        val store = newTestStore()
        assertEquals(0L, store.syncCursor(SyncScope.FINANCE_ACCOUNTS))

        var pullCallCount = 0
        val requestedCursors = mutableListOf<Long>()
        var failOnSecondPull = true

        val mockApi = object : BaseMockSyncRemoteApi() {
            override suspend fun pullFinanceAccounts(cursor: Long, limit: Int): PullResponse<SyncFinanceAccount> {
                pullCallCount++
                requestedCursors.add(cursor)
                if (pullCallCount == 2 && failOnSecondPull) {
                    throw RuntimeException("Network error on page 2")
                }
                return when (cursor) {
                    0L -> PullResponse(
                        items = (1..500).map { i ->
                            SyncFinanceAccount(
                                id = "acc-$i",
                                path = "Assets:Bank:P$i",
                                name = "P$i",
                                category = "ASSET",
                                currency = "CNY",
                                serverVersion = i.toLong(),
                                serverUpdatedAt = "2026-05-25T00:00:00Z",
                                hlcMap = mapOf("core" to "2026-05-25T00:00:00Z:0001:node"),
                            )
                        },
                        nextCursor = 500L,
                        hasMore = true,
                    )
                    500L -> PullResponse(
                        items = (501..700).map { i ->
                            SyncFinanceAccount(
                                id = "acc-$i",
                                path = "Assets:Bank:P$i",
                                name = "P$i",
                                category = "ASSET",
                                currency = "CNY",
                                serverVersion = i.toLong(),
                                serverUpdatedAt = "2026-05-25T00:00:00Z",
                                hlcMap = mapOf("core" to "2026-05-25T00:00:00Z:0001:node"),
                            )
                        },
                        nextCursor = 700L,
                        hasMore = false,
                    )
                    else -> PullResponse(emptyList(), nextCursor = cursor, hasMore = false)
                }
            }
        }

        val engine = SyncEngine(localStore = store, remoteApi = mockApi)

        // First attempt: pulls page 1 (cursor 0 -> nextCursor 500), then fails on page 2 (cursor 500)
        assertFailsWith<SyncOperationException> {
            engine.syncNow()
        }

        // Verify cursor was persisted after page 1 succeeded
        assertEquals(500L, store.syncCursor(SyncScope.FINANCE_ACCOUNTS))

        // Retry: should resume directly from cursor 500L, NOT from 0L
        failOnSecondPull = false
        requestedCursors.clear()

        engine.syncNow()

        assertEquals(listOf(500L), requestedCursors)
        assertEquals(700L, store.syncCursor(SyncScope.FINANCE_ACCOUNTS))
    }
}
