package dev.tireless.abun

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.tireless.abun.app.DeviceNodeIdProvider
import dev.tireless.abun.app.IdGenerator
import dev.tireless.abun.app.LocalStore
import dev.tireless.abun.app.TimeProvider
import dev.tireless.abun.app.createHybridClock
import dev.tireless.abun.db.AbunDatabase
import dev.tireless.abun.finance.AccountCategory
import dev.tireless.abun.finance.ClearingGroupStatus
import dev.tireless.abun.finance.ClearingGroupType
import dev.tireless.abun.sync.SyncFinanceAccount
import dev.tireless.abun.sync.SyncFinanceClearingGroup
import dev.tireless.abun.sync.SyncFinanceTransaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceSyncTest {

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
    fun `created account is dirty and clearAccepted cleans dirty status`() {
        val store = newTestStore()

        val accId = store.financeAccountStore.createFinanceAccount(
            path = "Liabilities:CreditCard:CMB",
            name = "招商银行信用卡",
            category = AccountCategory.LIABILITY,
            currency = "CNY",
            statementDay = 5,
            dueDay = 25,
        )

        val dirty = store.financeSyncStore.dirtyFinanceAccounts()
        assertTrue(dirty.any { it.id == accId })
        val dirtyAcc = dirty.first { it.id == accId }
        assertEquals("Liabilities:CreditCard:CMB", dirtyAcc.path)
        assertEquals(listOf("core"), dirtyAcc.dirtyFields)

        // Simulate server response accepting "core"
        val acceptedResponse = listOf(
            dirtyAcc.copy(
                acceptedFields = listOf("core"),
                rejectedFields = emptyList(),
                serverVersion = 42L,
            ),
        )
        store.financeSyncStore.mergeRemoteFinanceAccounts(acceptedResponse, clearAccepted = true)

        // Dirty list should no longer contain this account
        assertFalse(store.financeSyncStore.dirtyFinanceAccounts().any { it.id == accId })
        val dbRow = store.queries.selectFinanceAccountById(accId).executeAsOneOrNull()
        assertNotNull(dbRow)
        assertEquals(42L, dbRow.server_version)
    }

    @Test
    fun `remote account pull merges new account locally`() {
        val store = newTestStore()

        val remoteAccount = SyncFinanceAccount(
            id = "acc-remote-boc",
            path = "Assets:Checking:BOC",
            name = "中国银行活期",
            category = "ASSET",
            currency = "CNY",
            includeInNetWorth = true,
            serverVersion = 10L,
            hlcMap = mapOf("core" to "1748120001000-0001-remoteDevice"),
            createdAt = "2026-05-20T00:00:00Z",
        )

        store.financeSyncStore.mergeRemoteFinanceAccounts(listOf(remoteAccount))

        val local = store.financeAccountStore.financeAccount("acc-remote-boc")
        assertNotNull(local)
        assertEquals("Assets:Checking:BOC", local.path)
        assertEquals("中国银行活期", local.name)

        val dbRow = store.queries.selectFinanceAccountById("acc-remote-boc").executeAsOneOrNull()
        assertNotNull(dbRow)
        assertEquals(10L, dbRow.server_version)

        // Pulled item must not be dirty
        assertFalse(store.financeSyncStore.dirtyFinanceAccounts().any { it.id == "acc-remote-boc" })
    }

    @Test
    fun `HLC conflict resolution prioritizes newer HLC and ignores older HLC`() {
        val store = newTestStore()

        // 1. Initial remote account with HLC = 1500
        val v1 = SyncFinanceAccount(
            id = "acc-conflict-test",
            path = "Expenses:Food:Dinner",
            name = "晚餐消费 (版本1)",
            category = "EXPENSE",
            currency = "CNY",
            serverVersion = 1L,
            hlcMap = mapOf("core" to "1748120001500-0001-nodeA"),
        )
        store.financeSyncStore.mergeRemoteFinanceAccounts(listOf(v1))
        assertEquals("晚餐消费 (版本1)", store.financeAccountStore.financeAccount("acc-conflict-test")?.name)

        // 2. Incoming with older HLC = 1200 should be ignored
        val vOlder = SyncFinanceAccount(
            id = "acc-conflict-test",
            path = "Expenses:Food:Dinner",
            name = "陈旧名字 (版本0)",
            category = "EXPENSE",
            currency = "CNY",
            serverVersion = 2L,
            hlcMap = mapOf("core" to "1748120001200-0001-nodeB"),
        )
        store.financeSyncStore.mergeRemoteFinanceAccounts(listOf(vOlder))
        // Name should stay as version 1!
        assertEquals("晚餐消费 (版本1)", store.financeAccountStore.financeAccount("acc-conflict-test")?.name)

        // 3. Incoming with newer HLC = 2000 should overwrite
        val vNewer = SyncFinanceAccount(
            id = "acc-conflict-test",
            path = "Expenses:Food:Dinner",
            name = "最新名字 (版本2)",
            category = "EXPENSE",
            currency = "CNY",
            serverVersion = 3L,
            hlcMap = mapOf("core" to "1748120002000-0001-nodeC"),
        )
        store.financeSyncStore.mergeRemoteFinanceAccounts(listOf(vNewer))
        assertEquals("最新名字 (版本2)", store.financeAccountStore.financeAccount("acc-conflict-test")?.name)
    }

    @Test
    fun `remote soft delete marks local account as deleted`() {
        val store = newTestStore()

        val accId = store.financeAccountStore.createFinanceAccount(
            path = "Assets:Cash:Wallet",
            name = "随身钱包",
            category = AccountCategory.ASSET,
            currency = "CNY",
        )
        assertNotNull(store.financeAccountStore.financeAccount(accId))

        // Remote deletion arrives with newer HLC
        val deleteEvent = SyncFinanceAccount(
            id = accId,
            path = "Assets:Cash:Wallet",
            name = "随身钱包",
            category = "ASSET",
            isDeleted = true,
            serverVersion = 5L,
            hlcMap = mapOf("deleted" to "1748120009999-0001-remoteDevice"),
        )
        store.financeSyncStore.mergeRemoteFinanceAccounts(listOf(deleteEvent))

        // Account should now be soft deleted (financeAccount returns null for active accounts)
        assertNull(store.financeAccountStore.financeAccount(accId))
    }

    @Test
    fun `clearing group and transaction sync merges cleanly`() {
        val store = newTestStore()

        val group = SyncFinanceClearingGroup(
            id = "cg-cycle-1",
            name = "2026-05 信用卡清算",
            groupType = ClearingGroupType.CREDIT_STATEMENT.name,
            anchorCurrency = "CNY",
            statementCycle = "2026-05",
            dueDate = "2026-05-25",
            status = ClearingGroupStatus.ACTIVE.name,
            serverVersion = 1L,
            hlcMap = mapOf("core" to "1748120001000-0001-nodeA"),
        )
        store.financeSyncStore.mergeRemoteFinanceClearingGroups(listOf(group))

        val localGroup = store.financeLedgerStore.financeClearingGroup("cg-cycle-1")
        assertNotNull(localGroup)
        assertEquals("2026-05 信用卡清算", localGroup.name)
        assertEquals("2026-05", localGroup.statementCycle)

        val tx = SyncFinanceTransaction(
            id = "tx-lunch-1",
            txDate = "2026-05-24",
            summary = "工作餐",
            status = "Cleared",
            serverVersion = 2L,
            hlcMap = mapOf("header" to "1748120001000-0001-nodeA"),
        )
        store.financeSyncStore.mergeRemoteFinanceTransactions(listOf(tx))

        val localTx = store.financeLedgerStore.financeTransaction("tx-lunch-1")
        assertNotNull(localTx)
        assertEquals("工作餐", localTx.summary)
        assertEquals("2026-05-24", localTx.txDate)
    }
}
